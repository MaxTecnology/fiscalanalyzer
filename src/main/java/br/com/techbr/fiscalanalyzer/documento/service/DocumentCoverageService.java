package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageBackfillResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCoverageSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentAdditionalInfo;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentDuplicate;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentPayment;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentAdditionalInfoRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentDuplicateRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentPaymentRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import br.com.techbr.fiscalanalyzer.xml.parser.NfeXmlParser;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfe;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeAdditionalInfo;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfeDuplicate;
import br.com.techbr.fiscalanalyzer.xml.parser.ParsedNfePayment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentCoverageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCoverageService.class);
    private static final Set<ImportItemStatus> ELIGIBLE_ITEM_STATUSES = EnumSet.of(
            ImportItemStatus.PARSEADO,
            ImportItemStatus.DUPLICADO
    );

    private final ImportItemRepository importItemRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository;
    private final FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository;
    private final FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository;
    private final TenantEmpresaValidationService tenantEmpresaValidationService;
    private final StorageService storageService;
    private final NfeXmlParser parser = new NfeXmlParser();
    private final Counter processedCounter;
    private final Counter updatedCounter;
    private final Counter skippedCounter;
    private final Counter failedCounter;

    public DocumentCoverageService(
            ImportItemRepository importItemRepository,
            FiscalDocumentRepository fiscalDocumentRepository,
            FiscalDocumentPaymentRepository fiscalDocumentPaymentRepository,
            FiscalDocumentDuplicateRepository fiscalDocumentDuplicateRepository,
            FiscalDocumentAdditionalInfoRepository fiscalDocumentAdditionalInfoRepository,
            TenantEmpresaValidationService tenantEmpresaValidationService,
            StorageService storageService,
            MeterRegistry meterRegistry
    ) {
        this.importItemRepository = importItemRepository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalDocumentPaymentRepository = fiscalDocumentPaymentRepository;
        this.fiscalDocumentDuplicateRepository = fiscalDocumentDuplicateRepository;
        this.fiscalDocumentAdditionalInfoRepository = fiscalDocumentAdditionalInfoRepository;
        this.tenantEmpresaValidationService = tenantEmpresaValidationService;
        this.storageService = storageService;
        this.processedCounter = meterRegistry.counter("document.coverage.backfill.processed");
        this.updatedCounter = meterRegistry.counter("document.coverage.backfill.updated");
        this.skippedCounter = meterRegistry.counter("document.coverage.backfill.skipped");
        this.failedCounter = meterRegistry.counter("document.coverage.backfill.failed");
    }

    public DocumentCoverageSummaryResponse summarize(Long tenantId, Long empresaId) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);

        long totalDocuments = fiscalDocumentRepository.countByTenantIdAndEmpresaId(tenantId, empresaId);
        long missingIdeCoverage = fiscalDocumentRepository.countMissingIdeCoverage(tenantId, empresaId);
        long missingEmitCoverage = fiscalDocumentRepository.countMissingEmitCoverage(tenantId, empresaId);
        long missingDestCoverage = fiscalDocumentRepository.countMissingDestCoverage(tenantId, empresaId);
        long missingTotalsCoverage = fiscalDocumentRepository.countMissingTotalsCoverage(tenantId, empresaId);
        long missingProtocolCoverage = fiscalDocumentRepository.countMissingProtocolCoverage(tenantId, empresaId);
        long documentsWithoutPayments = fiscalDocumentRepository.countWithoutPaymentCoverage(tenantId, empresaId);
        long documentsWithoutAdditionalInfo = fiscalDocumentRepository.countWithoutAdditionalInfoCoverage(tenantId, empresaId);

        return new DocumentCoverageSummaryResponse(
                tenantId,
                empresaId,
                totalDocuments,
                missingIdeCoverage,
                missingEmitCoverage,
                missingDestCoverage,
                missingTotalsCoverage,
                missingProtocolCoverage,
                documentsWithoutPayments,
                documentsWithoutAdditionalInfo
        );
    }

    public DocumentCoverageBackfillResponse backfill(Long tenantId, Long empresaId, Integer limit) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        int safeLimit = Math.min(Math.max(limit == null ? 500 : limit, 1), 5000);

        List<ImportItemRepository.CoverageBackfillCandidateProjection> candidates =
                importItemRepository.findCoverageBackfillCandidates(
                        tenantId,
                        empresaId,
                        ELIGIBLE_ITEM_STATUSES,
                        PageRequest.of(0, safeLimit)
                );

        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (ImportItemRepository.CoverageBackfillCandidateProjection candidate : candidates) {
            processed++;
            try {
                var documentOpt = fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(
                        tenantId, empresaId, candidate.getAccessKey()
                );
                if (documentOpt.isEmpty()) {
                    skipped++;
                    continue;
                }

                FiscalDocument document = documentOpt.get();
                ParsedNfe parsed = parseCandidate(candidate);
                applyParsed(document, parsed);
                fiscalDocumentRepository.save(document);

                fiscalDocumentPaymentRepository.deleteByDocumentId(document.getId());
                fiscalDocumentDuplicateRepository.deleteByDocumentId(document.getId());
                fiscalDocumentAdditionalInfoRepository.deleteByDocumentId(document.getId());

                savePayments(document, parsed.payments());
                saveDuplicates(document, parsed.duplicates());
                saveAdditionalInfos(document, parsed.additionalInfos());

                updated++;
            } catch (Exception ex) {
                failed++;
                log.warn("document.coverage.backfill.item_failed tenantId={} empresaId={} importItemId={} message={}",
                        tenantId, empresaId, candidate.getImportItemId(), ex.getMessage());
            }
        }

        processedCounter.increment(processed);
        updatedCounter.increment(updated);
        skippedCounter.increment(skipped);
        failedCounter.increment(failed);

        log.info("document.coverage.backfill.completed tenantId={} empresaId={} processed={} updated={} skipped={} failed={}",
                tenantId, empresaId, processed, updated, skipped, failed);

        return new DocumentCoverageBackfillResponse(
                tenantId,
                empresaId,
                safeLimit,
                processed,
                updated,
                skipped,
                failed
        );
    }

    private ParsedNfe parseCandidate(ImportItemRepository.CoverageBackfillCandidateProjection candidate) {
        if (StringUtils.hasText(candidate.getStorageObjectKey())) {
            try (InputStream in = storageService.get(candidate.getStorageObjectKey())) {
                return parser.parse(in);
            } catch (ValidationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new InfraException("Falha ao ler XML direto do storage", ex);
            }
        }

        if (candidate.getSourceType() == ImportacaoSourceType.ZIP
                && StringUtils.hasText(candidate.getArquivoPath())
                && StringUtils.hasText(candidate.getXmlPath())) {
            return parseZipEntry(candidate.getArquivoPath(), candidate.getXmlPath());
        }

        throw new ValidationException("Nao foi possivel determinar fonte do XML para backfill");
    }

    private ParsedNfe parseZipEntry(String zipObjectKey, String zipEntryName) {
        try (InputStream zipStream = storageService.get(zipObjectKey);
             ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (!zipEntryName.equals(entry.getName())) continue;
                return parser.parse(zis);
            }
            throw new ValidationException("XML nao encontrado no ZIP para backfill");
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfraException("Falha ao ler ZIP/XML no backfill", ex);
        }
    }

    private void applyParsed(FiscalDocument doc, ParsedNfe parsed) {
        doc.setModel(parsed.model());
        doc.setAccessKey(parsed.accessKey());
        doc.setNumeroNota(parsed.numeroNota());
        doc.setSerie(parsed.serie());
        doc.setNaturezaOperacao(parsed.naturezaOperacao());
        doc.setIssueDate(parsed.issueDate());
        doc.setIssueDateTime(parsed.issueDateTime());
        doc.setAmbiente(parsed.ambiente());
        doc.setFinalidadeEmissao(parsed.finalidadeEmissao());
        doc.setConsumidorFinal(parsed.consumidorFinal());
        doc.setPresencaComprador(parsed.presencaComprador());
        doc.setOperationType(parsed.operationType());
        doc.setEmitCnpj(parsed.emitCnpj());
        doc.setEmitNome(parsed.emitName());
        doc.setEmitIe(parsed.emitIe());
        doc.setEmitUf(parsed.emitUf());
        doc.setDestDocumento(parsed.destDocument());
        doc.setDestCnpj(parsed.destCnpj());
        doc.setDestNome(parsed.destName());
        doc.setDestIe(parsed.destIe());
        doc.setDestUf(parsed.destUf());
        doc.setTotalProducts(parsed.totalProducts());
        doc.setTotalAmount(parsed.totalAmount());
        doc.setTotalFrete(parsed.totalFrete());
        doc.setTotalDesconto(parsed.totalDesconto());
        doc.setTotalOutros(parsed.totalOutros());
        doc.setTotalIpi(parsed.totalIpi());
        doc.setTotalTributos(parsed.totalTributos());
        doc.setTotalIcms(parsed.totalIcms());
        doc.setTotalPis(parsed.totalPis());
        doc.setTotalCofins(parsed.totalCofins());
        doc.setProtocoloNumero(parsed.protocoloNumero());
        doc.setProtocoloStatus(parsed.protocoloStatus());
        doc.setProtocoloMotivo(parsed.protocoloMotivo());
        doc.setProtocoloRecebimento(parsed.protocoloRecebimento());
        doc.setQrCodeUrl(parsed.qrCodeUrl());
        doc.setFaturaNumero(parsed.faturaNumero());
        doc.setFaturaValorOriginal(parsed.faturaValorOriginal());
        doc.setFaturaValorDesconto(parsed.faturaValorDesconto());
        doc.setFaturaValorLiquido(parsed.faturaValorLiquido());
    }

    private void savePayments(FiscalDocument doc, List<ParsedNfePayment> parsedPayments) {
        if (parsedPayments == null || parsedPayments.isEmpty()) return;
        List<FiscalDocumentPayment> entities = new ArrayList<>();
        for (ParsedNfePayment parsed : parsedPayments) {
            FiscalDocumentPayment payment = new FiscalDocumentPayment();
            payment.setDocument(doc);
            payment.setPaymentIndicator(parsed.paymentIndicator());
            payment.setPaymentType(parsed.paymentType());
            payment.setPaymentAmount(parsed.paymentAmount());
            payment.setCardIntegrationType(parsed.cardIntegrationType());
            payment.setCardCnpj(parsed.cardCnpj());
            payment.setCardBrand(parsed.cardBrand());
            payment.setCardAuthorizationCode(parsed.cardAuthorizationCode());
            entities.add(payment);
        }
        fiscalDocumentPaymentRepository.saveAll(entities);
    }

    private void saveDuplicates(FiscalDocument doc, List<ParsedNfeDuplicate> parsedDuplicates) {
        if (parsedDuplicates == null || parsedDuplicates.isEmpty()) return;
        List<FiscalDocumentDuplicate> entities = new ArrayList<>();
        for (ParsedNfeDuplicate parsed : parsedDuplicates) {
            FiscalDocumentDuplicate duplicate = new FiscalDocumentDuplicate();
            duplicate.setDocument(doc);
            duplicate.setDuplicateNumber(parsed.duplicateNumber());
            duplicate.setDueDate(parsed.dueDate());
            duplicate.setAmount(parsed.amount());
            entities.add(duplicate);
        }
        fiscalDocumentDuplicateRepository.saveAll(entities);
    }

    private void saveAdditionalInfos(FiscalDocument doc, List<ParsedNfeAdditionalInfo> parsedInfos) {
        if (parsedInfos == null || parsedInfos.isEmpty()) return;
        List<FiscalDocumentAdditionalInfo> entities = new ArrayList<>();
        for (ParsedNfeAdditionalInfo parsed : parsedInfos) {
            FiscalDocumentAdditionalInfo info = new FiscalDocumentAdditionalInfo();
            info.setDocument(doc);
            info.setInfoType(parsed.infoType());
            info.setFieldName(parsed.fieldName());
            info.setTextValue(parsed.textValue());
            entities.add(info);
        }
        fiscalDocumentAdditionalInfoRepository.saveAll(entities);
    }
}
