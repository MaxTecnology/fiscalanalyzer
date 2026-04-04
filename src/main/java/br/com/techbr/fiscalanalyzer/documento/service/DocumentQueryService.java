package br.com.techbr.fiscalanalyzer.documento.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentCodeStatsResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportFormat;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentExportRequest;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentListItemResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentPartyResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.DocumentSummaryResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentDetailResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentEventResponse;
import br.com.techbr.fiscalanalyzer.documento.dto.FiscalDocumentItemDetailResponse;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocument;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentEvent;
import br.com.techbr.fiscalanalyzer.documento.model.FiscalDocumentStatus;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentEventRepository;
import br.com.techbr.fiscalanalyzer.documento.repository.FiscalDocumentRepository;
import br.com.techbr.fiscalanalyzer.identity.service.TenantEmpresaValidationService;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoSourceType;
import br.com.techbr.fiscalanalyzer.item.model.FiscalItem;
import br.com.techbr.fiscalanalyzer.item.repository.FiscalItemRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DocumentQueryService {

    private static final int MAX_EXPORT_ROWS = 100_000;
    private static final int EXPORT_PAGE_SIZE = 2_000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;

    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalItemRepository fiscalItemRepository;
    private final FiscalDocumentEventRepository fiscalDocumentEventRepository;
    private final TenantEmpresaValidationService tenantEmpresaValidationService;
    private final StorageService storageService;
    private final DanfeGeneratorService danfeGeneratorService;

    public DocumentQueryService(FiscalDocumentRepository fiscalDocumentRepository,
                                FiscalItemRepository fiscalItemRepository,
                                FiscalDocumentEventRepository fiscalDocumentEventRepository,
                                TenantEmpresaValidationService tenantEmpresaValidationService,
                                StorageService storageService,
                                DanfeGeneratorService danfeGeneratorService) {
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalItemRepository = fiscalItemRepository;
        this.fiscalDocumentEventRepository = fiscalDocumentEventRepository;
        this.tenantEmpresaValidationService = tenantEmpresaValidationService;
        this.storageService = storageService;
        this.danfeGeneratorService = danfeGeneratorService;
    }

    @Transactional(readOnly = true)
    public Page<DocumentListItemResponse> listDocuments(Long tenantId,
                                                        Long empresaId,
                                                        Short model,
                                                        String operationType,
                                                        FiscalDocumentStatus statusDocumento,
                                                        String emitCnpj,
                                                        String destCnpj,
                                                        LocalDate issueDateFrom,
                                                        LocalDate issueDateTo,
                                                        Long importacaoId,
                                                        Pageable pageable) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        validateDateRange(issueDateFrom, issueDateTo);
        Short normalizedModel = normalizeModel(model);
        String normalizedOperationType = normalizeOperationType(operationType);
        Specification<FiscalDocument> specification = buildSearchSpecification(
                tenantId,
                empresaId,
                normalizedModel,
                normalizedOperationType,
                statusDocumento,
                normalizeDocument(emitCnpj),
                normalizeDocument(destCnpj),
                issueDateFrom,
                issueDateTo,
                importacaoId
        );

        Page<FiscalDocument> page = fiscalDocumentRepository.findAll(specification, pageable);

        return page.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public FiscalDocumentDetailResponse findByAccessKey(Long tenantId, Long empresaId, String accessKey) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        String normalizedAccessKey = normalizeAccessKey(accessKey);

        FiscalDocument doc = findDocument(tenantId, empresaId, normalizedAccessKey);
        List<FiscalItem> items = fiscalItemRepository.findByDocumentIdOrderByItemNumberAsc(doc.getId());

        return new FiscalDocumentDetailResponse(
                doc.getModel(),
                doc.getAccessKey(),
                doc.getIssueDate(),
                doc.getOperationType(),
                doc.getStatusDocumento() != null ? doc.getStatusDocumento().name() : null,
                doc.getEmitCnpj(),
                doc.getEmitNome(),
                doc.getEmitIe(),
                doc.getEmitUf(),
                doc.getEmitMunicipio(),
                doc.getDestCnpj(),
                doc.getDestNome(),
                doc.getDestIe(),
                doc.getDestUf(),
                doc.getDestMunicipio(),
                doc.getNumeroNota(),
                doc.getSerie(),
                doc.getNaturezaOperacao(),
                doc.getTotalProducts(),
                doc.getTotalAmount(),
                doc.getTotalIcms(),
                doc.getTotalPis(),
                doc.getTotalCofins(),
                doc.getTotalIpi(),
                doc.getTotalIss(),
                doc.getTotalFrete(),
                doc.getTotalDesconto(),
                doc.getTotalOutros(),
                doc.getTotalTributos(),
                getImportacaoId(doc),
                doc.getXmlPath(),
                doc.getXmlHash(),
                doc.getProtocoloNumero(),
                doc.getProtocoloStatus(),
                doc.getProtocoloMotivo(),
                doc.getProtocoloRecebimento(),
                doc.getCancelledAt(),
                doc.getCancelProtocol(),
                doc.getCancelReason(),
                items.stream().map(this::toDetailItem).toList()
        );
    }

    @Transactional(readOnly = true)
    public DocumentSummaryResponse summarize(Long tenantId,
                                             Long empresaId,
                                             LocalDate issueDateFrom,
                                             LocalDate issueDateTo) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        validateDateRange(issueDateFrom, issueDateTo);

        long totalDocumentos = fiscalDocumentRepository
                .countByTenantIdAndEmpresaIdAndIssueDateBetween(tenantId, empresaId, issueDateFrom, issueDateTo);
        long totalNFe = fiscalDocumentRepository
                .countByTenantIdAndEmpresaIdAndIssueDateBetweenAndModel(tenantId, empresaId, issueDateFrom, issueDateTo, (short) 55);
        long totalNFCe = fiscalDocumentRepository
                .countByTenantIdAndEmpresaIdAndIssueDateBetweenAndModel(tenantId, empresaId, issueDateFrom, issueDateTo, (short) 65);
        long totalCanceladas = fiscalDocumentRepository
                .countByTenantIdAndEmpresaIdAndIssueDateBetweenAndStatusDocumento(
                        tenantId,
                        empresaId,
                        issueDateFrom,
                        issueDateTo,
                        FiscalDocumentStatus.CANCELADA
                );

        BigDecimal valorTotalEntradas = safeBigDecimal(fiscalDocumentRepository
                .sumTotalAmountByOperationType(tenantId, empresaId, issueDateFrom, issueDateTo, "E"));
        BigDecimal valorTotalSaidas = safeBigDecimal(fiscalDocumentRepository
                .sumTotalAmountByOperationType(tenantId, empresaId, issueDateFrom, issueDateTo, "S"));
        BigDecimal icmsTotalSaidas = safeBigDecimal(fiscalDocumentRepository
                .sumTotalIcmsByOperationType(tenantId, empresaId, issueDateFrom, issueDateTo, "S"));

        return new DocumentSummaryResponse(
                totalDocumentos,
                totalNFe,
                totalNFCe,
                totalCanceladas,
                valorTotalEntradas,
                valorTotalSaidas,
                icmsTotalSaidas,
                issueDateFrom,
                issueDateTo
        );
    }

    @Transactional(readOnly = true)
    public List<FiscalDocumentEventResponse> listEvents(Long tenantId, Long empresaId, String accessKey) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        String normalizedAccessKey = normalizeAccessKey(accessKey);

        List<FiscalDocumentEvent> events = fiscalDocumentEventRepository.findTimeline(tenantId, empresaId, normalizedAccessKey);
        return events.stream().map(this::toEventResponse).toList();
    }

    @Transactional(readOnly = true)
    public DocumentXmlDownload downloadXml(Long tenantId, Long empresaId, String accessKey) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        String normalizedAccessKey = normalizeAccessKey(accessKey);

        FiscalDocument doc = findDocument(tenantId, empresaId, normalizedAccessKey);
        byte[] payload = readXmlBytes(doc);
        return new DocumentXmlDownload(payload, normalizedAccessKey + ".xml", "application/xml");
    }

    @Transactional(readOnly = true)
    public DocumentDanfeDownload downloadDanfe(Long tenantId, Long empresaId, String accessKey) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        String normalizedAccessKey = normalizeAccessKey(accessKey);

        FiscalDocument doc = findDocument(tenantId, empresaId, normalizedAccessKey);
        byte[] xmlPayload = readXmlBytes(doc);
        String xml = new String(xmlPayload, StandardCharsets.UTF_8);
        byte[] danfePdf = danfeGeneratorService.generatePdf(xml, doc.getModel(), doc.getQrCodeUrl());
        return new DocumentDanfeDownload(danfePdf, normalizedAccessKey + "-danfe.pdf", "application/pdf");
    }

    @Transactional(readOnly = true)
    public DocumentExportFile exportDocuments(DocumentExportRequest request) {
        if (request == null) {
            throw new ValidationException("payload export ausente");
        }

        tenantEmpresaValidationService.validateAtivo(request.tenantId(), request.empresaId());
        validateDateRange(request.issueDateFrom(), request.issueDateTo());

        Short normalizedModel = normalizeModel(request.model());
        String normalizedOperationType = normalizeOperationType(request.operationType());
        String normalizedEmitCnpj = normalizeDocument(request.emitCnpj());
        String normalizedDestCnpj = normalizeDocument(request.destCnpj());
        DocumentExportFormat format = request.format() == null ? DocumentExportFormat.CSV : request.format();

        List<DocumentExportRow> rows = fetchExportRows(
                request.tenantId(),
                request.empresaId(),
                normalizedModel,
                normalizedOperationType,
                request.statusDocumento(),
                normalizedEmitCnpj,
                normalizedDestCnpj,
                request.issueDateFrom(),
                request.issueDateTo(),
                request.importacaoId()
        );

        byte[] payload;
        String extension;
        String contentType;

        switch (format) {
            case CSV -> {
                payload = buildCsv(rows);
                extension = "csv";
                contentType = "text/csv";
            }
            case XLSX -> {
                payload = buildXlsx(rows);
                extension = "xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            case PDF -> {
                payload = buildPdf(rows);
                extension = "pdf";
                contentType = "application/pdf";
            }
            default -> throw new ValidationException("formato de exportacao invalido");
        }

        String fileName = "documents-%d-%d-%s.%s".formatted(
                request.tenantId(),
                request.empresaId(),
                LocalDate.now().format(DATE_FORMAT),
                extension
        );

        return new DocumentExportFile(payload, fileName, contentType);
    }

    @Transactional(readOnly = true)
    public List<DocumentPartyResponse> listEmitters(Long tenantId, Long empresaId, String q, Integer limit) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        int safeLimit = clampLimit(limit);
        String query = normalizeSearch(q);

        return fiscalDocumentRepository.findDistinctEmitters(tenantId, empresaId, query, PageRequest.of(0, safeLimit)).stream()
                .map(p -> new DocumentPartyResponse(p.getCnpj(), p.getRazaoSocial()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentPartyResponse> listReceivers(Long tenantId, Long empresaId, String q, Integer limit) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        int safeLimit = clampLimit(limit);
        String query = normalizeSearch(q);

        return fiscalDocumentRepository.findDistinctReceivers(tenantId, empresaId, query, PageRequest.of(0, safeLimit)).stream()
                .map(p -> new DocumentPartyResponse(p.getCnpj(), p.getRazaoSocial()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentCodeStatsResponse> statsByCfop(Long tenantId,
                                                       Long empresaId,
                                                       LocalDate issueDateFrom,
                                                       LocalDate issueDateTo,
                                                       Integer limit) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        validateDateRange(issueDateFrom, issueDateTo);
        int safeLimit = clampLimit(limit);

        return fiscalItemRepository.summarizeByCfop(tenantId, empresaId, issueDateFrom, issueDateTo, PageRequest.of(0, safeLimit))
                .stream()
                .map(p -> new DocumentCodeStatsResponse(p.getCodigo(), safeLong(p.getTotalItens()), safeBigDecimal(p.getTotalValor())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentCodeStatsResponse> statsByNcm(Long tenantId,
                                                      Long empresaId,
                                                      LocalDate issueDateFrom,
                                                      LocalDate issueDateTo,
                                                      Integer limit) {
        tenantEmpresaValidationService.validateAtivo(tenantId, empresaId);
        validateDateRange(issueDateFrom, issueDateTo);
        int safeLimit = clampLimit(limit);

        return fiscalItemRepository.summarizeByNcm(tenantId, empresaId, issueDateFrom, issueDateTo, PageRequest.of(0, safeLimit))
                .stream()
                .map(p -> new DocumentCodeStatsResponse(p.getCodigo(), safeLong(p.getTotalItens()), safeBigDecimal(p.getTotalValor())))
                .toList();
    }

    private List<DocumentExportRow> fetchExportRows(Long tenantId,
                                                    Long empresaId,
                                                    Short model,
                                                    String operationType,
                                                    FiscalDocumentStatus statusDocumento,
                                                    String emitCnpj,
                                                    String destCnpj,
                                                    LocalDate issueDateFrom,
                                                    LocalDate issueDateTo,
                                                    Long importacaoId) {
        List<DocumentExportRow> rows = new ArrayList<>();
        int page = 0;
        Specification<FiscalDocument> specification = buildSearchSpecification(
                tenantId,
                empresaId,
                model,
                operationType,
                statusDocumento,
                emitCnpj,
                destCnpj,
                issueDateFrom,
                issueDateTo,
                importacaoId
        );

        while (true) {
            Page<FiscalDocument> batch = fiscalDocumentRepository.findAll(
                    specification,
                    PageRequest.of(page, EXPORT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "createdAt")))
            );

            for (FiscalDocument doc : batch.getContent()) {
                rows.add(toExportRow(doc));
                if (rows.size() >= MAX_EXPORT_ROWS) {
                    throw new ValidationException("export excede limite de " + MAX_EXPORT_ROWS + " registros");
                }
            }

            if (!batch.hasNext()) {
                break;
            }
            page++;
        }

        return rows;
    }

    private DocumentExportRow toExportRow(FiscalDocument doc) {
        return new DocumentExportRow(
                doc.getAccessKey(),
                doc.getSerie(),
                doc.getNumeroNota(),
                doc.getIssueDate(),
                doc.getEmitCnpj(),
                doc.getEmitNome(),
                doc.getDestCnpj(),
                doc.getDestNome(),
                doc.getOperationType(),
                doc.getModel(),
                doc.getTotalAmount(),
                doc.getTotalIcms(),
                doc.getTotalPis(),
                doc.getTotalCofins(),
                doc.getStatusDocumento() != null ? doc.getStatusDocumento().name() : null
        );
    }

    private byte[] buildCsv(List<DocumentExportRow> rows) {
        StringBuilder sb = new StringBuilder(16_384);
        sb.append('\uFEFF');
        sb.append("access_key;serie;numero;issue_date;emit_cnpj;emit_razao_social;dest_cnpj;dest_razao_social;operation_type;model;total_amount;total_icms;total_pis;total_cofins;status_documento\n");

        for (DocumentExportRow row : rows) {
            appendCsvField(sb, row.accessKey());
            appendCsvField(sb, row.serie());
            appendCsvField(sb, safeString(row.numero()));
            appendCsvField(sb, safeDate(row.issueDate()));
            appendCsvField(sb, row.emitCnpj());
            appendCsvField(sb, row.emitRazaoSocial());
            appendCsvField(sb, row.destCnpj());
            appendCsvField(sb, row.destRazaoSocial());
            appendCsvField(sb, row.operationType());
            appendCsvField(sb, safeString(row.model()));
            appendCsvField(sb, safeDecimal(row.totalAmount()));
            appendCsvField(sb, safeDecimal(row.totalIcms()));
            appendCsvField(sb, safeDecimal(row.totalPis()));
            appendCsvField(sb, safeDecimal(row.totalCofins()));
            appendCsvFieldLast(sb, row.statusDocumento());
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvField(StringBuilder sb, String value) {
        sb.append(escapeCsv(value)).append(';');
    }

    private void appendCsvFieldLast(StringBuilder sb, String value) {
        sb.append(escapeCsv(value)).append('\n');
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private byte[] buildXlsx(List<DocumentExportRow> rows) {
        String[] headers = new String[]{
                "access_key", "serie", "numero", "issue_date", "emit_cnpj", "emit_razao_social",
                "dest_cnpj", "dest_razao_social", "operation_type", "model", "total_amount",
                "total_icms", "total_pis", "total_cofins", "status_documento"
        };

        List<List<String>> table = new ArrayList<>(rows.size() + 1);
        table.add(List.of(headers));
        for (DocumentExportRow row : rows) {
            table.add(List.of(
                    safeString(row.accessKey()),
                    safeString(row.serie()),
                    safeString(row.numero()),
                    safeDate(row.issueDate()),
                    safeString(row.emitCnpj()),
                    safeString(row.emitRazaoSocial()),
                    safeString(row.destCnpj()),
                    safeString(row.destRazaoSocial()),
                    safeString(row.operationType()),
                    safeString(row.model()),
                    safeDecimal(row.totalAmount()),
                    safeDecimal(row.totalIcms()),
                    safeDecimal(row.totalPis()),
                    safeDecimal(row.totalCofins()),
                    safeString(row.statusDocumento())
            ));
        }

        String sheetXml = buildSheetXml(table);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            addZipEntry(zos, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);

            addZipEntry(zos, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);

            addZipEntry(zos, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets>
                        <sheet name="documents" sheetId="1" r:id="rId1"/>
                      </sheets>
                    </workbook>
                    """);

            addZipEntry(zos, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);

            addZipEntry(zos, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                      <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                      <borders count="1"><border/></borders>
                      <cellStyleXfs count="1"><xf/></cellStyleXfs>
                      <cellXfs count="1"><xf xfId="0"/></cellXfs>
                    </styleSheet>
                    """);

            addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml);
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new InfraException("Falha ao gerar XLSX", e);
        }
    }

    private String buildSheetXml(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder(32_768);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetData>");

        for (int r = 0; r < rows.size(); r++) {
            int rowNumber = r + 1;
            sb.append("<row r=\"").append(rowNumber).append("\">");
            List<String> cells = rows.get(r);
            for (int c = 0; c < cells.size(); c++) {
                String ref = columnRef(c) + rowNumber;
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                        .append(xmlEscape(cells.get(c)))
                        .append("</t></is></c>");
            }
            sb.append("</row>");
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private String columnRef(int index) {
        int value = index;
        StringBuilder ref = new StringBuilder();
        do {
            int rem = value % 26;
            ref.insert(0, (char) ('A' + rem));
            value = (value / 26) - 1;
        } while (value >= 0);
        return ref.toString();
    }

    private String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void addZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private byte[] buildPdf(List<DocumentExportRow> rows) {
        List<String> lines = new ArrayList<>();
        lines.add("FiscalAnalyzer - Exportacao de Documentos");
        lines.add("Gerado em: " + Instant.now());
        lines.add("Registros: " + rows.size());
        lines.add(" ");

        for (DocumentExportRow row : rows) {
            lines.add("Chave: " + safeString(row.accessKey()));
            lines.add("Data: " + safeDate(row.issueDate())
                    + "  Modelo: " + safeString(row.model())
                    + "  Numero: " + safeString(row.numero())
                    + "  Serie: " + safeString(row.serie())
                    + "  Tipo: " + safeString(row.operationType())
                    + "  Status: " + safeString(row.statusDocumento()));
            lines.add("Emit: " + safeString(row.emitCnpj()) + " - " + trimForPdf(row.emitRazaoSocial(), 80));
            lines.add("Dest: " + safeString(row.destCnpj()) + " - " + trimForPdf(row.destRazaoSocial(), 80));
            lines.add("Totais -> vNF: " + safeDecimal(row.totalAmount())
                    + "  ICMS: " + safeDecimal(row.totalIcms())
                    + "  PIS: " + safeDecimal(row.totalPis())
                    + "  COFINS: " + safeDecimal(row.totalCofins()));
            lines.add("-");
        }

        List<String> pages = paginateLines(lines, 42);
        return assemblePdf(pages);
    }

    private List<String> paginateLines(List<String> lines, int linesPerPage) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            List<String> wrapped = wrapLine(sanitizeForPdf(line), 110);
            for (String w : wrapped) {
                if (lineCount >= linesPerPage) {
                    pages.add(page.toString());
                    page = new StringBuilder();
                    lineCount = 0;
                }
                page.append(w).append('\n');
                lineCount++;
            }
        }

        if (!page.isEmpty()) {
            pages.add(page.toString());
        }

        if (pages.isEmpty()) {
            pages.add("Sem dados para exportacao\n");
        }

        return pages;
    }

    private List<String> wrapLine(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }

    private String sanitizeForPdf(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '\r' || ch == '\n') {
                out.append(' ');
            } else if (ch < 32 || ch > 126) {
                out.append('?');
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private byte[] assemblePdf(List<String> pagesContent) {
        int totalPages = pagesContent.size();
        int catalogId = 1;
        int pagesId = 2;
        int fontId = 3;
        int firstPageId = 4;
        int firstContentId = firstPageId + totalPages;
        int maxId = firstContentId + totalPages - 1;

        List<PdfObject> objects = new ArrayList<>();

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < totalPages; i++) {
            kids.append(firstPageId + i).append(" 0 R ");
        }

        objects.add(new PdfObject(catalogId, "<< /Type /Catalog /Pages %d 0 R >>".formatted(pagesId)));
        objects.add(new PdfObject(pagesId, "<< /Type /Pages /Count %d /Kids [%s] >>".formatted(totalPages, kids.toString().trim())));
        objects.add(new PdfObject(fontId, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));

        for (int i = 0; i < totalPages; i++) {
            int pageId = firstPageId + i;
            int contentId = firstContentId + i;

            String pageObj = "<< /Type /Page /Parent %d 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 %d 0 R >> >> /Contents %d 0 R >>"
                    .formatted(pagesId, fontId, contentId);
            objects.add(new PdfObject(pageId, pageObj));

            String stream = buildPdfContentStream(pagesContent.get(i), i + 1, totalPages);
            String contentObj = "<< /Length %d >>\nstream\n%s\nendstream"
                    .formatted(stream.getBytes(StandardCharsets.ISO_8859_1).length, stream);
            objects.add(new PdfObject(contentId, contentObj));
        }

        objects.sort((a, b) -> Integer.compare(a.id(), b.id()));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));

            int[] offsets = new int[maxId + 1];
            for (PdfObject object : objects) {
                offsets[object.id()] = out.size();
                out.write((object.id() + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(object.body().getBytes(StandardCharsets.ISO_8859_1));
                out.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
            }

            int xrefOffset = out.size();
            out.write(("xref\n0 " + (maxId + 1) + "\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
            for (int i = 1; i <= maxId; i++) {
                out.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.ISO_8859_1));
            }

            out.write(("trailer\n<< /Size " + (maxId + 1) + " /Root " + catalogId + " 0 R >>\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write(("startxref\n" + xrefOffset + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1));

            return out.toByteArray();
        } catch (Exception e) {
            throw new InfraException("Falha ao gerar PDF", e);
        }
    }

    private String buildPdfContentStream(String content, int pageNumber, int totalPages) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n");
        sb.append("/F1 10 Tf\n");
        sb.append("40 810 Td\n");

        String[] lines = content.split("\\n", -1);
        int emitted = 0;
        for (String line : lines) {
            if (emitted > 0) {
                sb.append("0 -16 Td\n");
            }
            sb.append("(").append(escapePdfText(line)).append(") Tj\n");
            emitted++;
        }

        sb.append("0 -20 Td\n");
        sb.append("(Pagina ").append(pageNumber).append("/").append(totalPages).append(") Tj\n");
        sb.append("ET");
        return sb.toString();
    }

    private String escapePdfText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private String trimForPdf(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private FiscalDocument findDocument(Long tenantId, Long empresaId, String accessKey) {
        return fiscalDocumentRepository.findByTenantIdAndEmpresaIdAndAccessKey(tenantId, empresaId, accessKey)
                .orElseThrow(() -> new ValidationException("Documento nao encontrado"));
    }

    private Specification<FiscalDocument> buildSearchSpecification(Long tenantId,
                                                                   Long empresaId,
                                                                   Short model,
                                                                   String operationType,
                                                                   FiscalDocumentStatus statusDocumento,
                                                                   String emitCnpj,
                                                                   String destCnpj,
                                                                   LocalDate issueDateFrom,
                                                                   LocalDate issueDateTo,
                                                                   Long importacaoId) {
        Specification<FiscalDocument> specification = (root, query, cb) -> cb.and(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("empresaId"), empresaId)
        );

        if (model != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("model"), model));
        }
        if (StringUtils.hasText(operationType)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("operationType"), operationType));
        }
        if (statusDocumento != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("statusDocumento"), statusDocumento));
        }
        if (StringUtils.hasText(emitCnpj)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("emitCnpj"), emitCnpj));
        }
        if (StringUtils.hasText(destCnpj)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("destCnpj"), destCnpj));
        }
        if (issueDateFrom != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("issueDate"), issueDateFrom));
        }
        if (issueDateTo != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("issueDate"), issueDateTo));
        }
        if (importacaoId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("importacao").get("id"), importacaoId));
        }

        return specification;
    }

    private DocumentListItemResponse toListItem(FiscalDocument doc) {
        return new DocumentListItemResponse(
                doc.getAccessKey(),
                doc.getModel(),
                doc.getSerie(),
                doc.getNumeroNota(),
                doc.getIssueDate(),
                doc.getOperationType(),
                doc.getStatusDocumento() != null ? doc.getStatusDocumento().name() : null,
                doc.getEmitCnpj(),
                doc.getEmitNome(),
                doc.getDestCnpj(),
                doc.getDestNome(),
                doc.getTotalAmount(),
                getImportacaoId(doc)
        );
    }

    private FiscalDocumentItemDetailResponse toDetailItem(FiscalItem item) {
        return new FiscalDocumentItemDetailResponse(
                item.getItemNumber(),
                item.getProductCode(),
                item.getProductDescription(),
                item.getNcm(),
                item.getCfop(),
                item.getUnidade(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalValue(),
                item.getIcmsRate(),
                item.getIcmsValue(),
                item.getPisRate(),
                item.getPisValue(),
                item.getCofinsRate(),
                item.getCofinsValue()
        );
    }

    private FiscalDocumentEventResponse toEventResponse(FiscalDocumentEvent event) {
        return new FiscalDocumentEventResponse(
                mapEventType(event.getEventType()),
                event.getEventType(),
                firstNonBlank(event.getEventDescription(), event.getEventStatusReason()),
                firstInstant(event.getEventDatetime(), event.getCreatedAt()),
                firstNonBlank(event.getEventProtocol(), event.getReferenceProtocol()),
                event.getEventStatus(),
                event.getEventStatusReason()
        );
    }

    private byte[] readXmlBytes(FiscalDocument document) {
        Importacao importacao = document.getImportacao();
        if (importacao != null
                && importacao.getSourceType() == ImportacaoSourceType.ZIP
                && StringUtils.hasText(importacao.getArquivoPath())) {
            return readXmlFromZip(importacao.getArquivoPath(), document.getXmlPath());
        }

        if (!StringUtils.hasText(document.getXmlPath())) {
            throw new ValidationException("xml_path ausente para download");
        }
        try (InputStream in = storageService.get(document.getXmlPath())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new InfraException("Falha ao baixar XML do storage", e);
        }
    }

    private byte[] readXmlFromZip(String zipObjectKey, String zipEntryName) {
        if (!StringUtils.hasText(zipEntryName)) {
            throw new ValidationException("xml_path ausente para entrada do ZIP");
        }
        try (InputStream zipInput = storageService.get(zipObjectKey);
             ZipInputStream zis = new ZipInputStream(zipInput)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!zipEntryName.equals(entry.getName())) {
                    continue;
                }
                return readAllBytes(zis);
            }
            throw new ValidationException("XML nao encontrado no ZIP da importacao");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new InfraException("Falha ao baixar XML do ZIP", e);
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private Long getImportacaoId(FiscalDocument doc) {
        return doc.getImportacao() != null ? doc.getImportacao().getId() : null;
    }

    private Short normalizeModel(Short model) {
        if (model == null) {
            return null;
        }
        if (model != 55 && model != 65) {
            throw new ValidationException("model invalido: " + model);
        }
        return model;
    }

    private String normalizeOperationType(String operationType) {
        if (!StringUtils.hasText(operationType)) {
            return null;
        }
        String normalized = operationType.trim().toUpperCase(Locale.ROOT);
        if (!"E".equals(normalized) && !"S".equals(normalized)) {
            throw new ValidationException("operationType invalido: " + operationType);
        }
        return normalized;
    }

    private String normalizeDocument(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\D", "");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private String normalizeAccessKey(String accessKey) {
        if (!StringUtils.hasText(accessKey)) {
            throw new ValidationException("accessKey invalido");
        }
        String normalized = accessKey.replaceAll("\\D", "");
        if (normalized.length() != 44) {
            throw new ValidationException("accessKey invalido");
        }
        return normalized;
    }

    private String normalizeSearch(String q) {
        if (!StringUtils.hasText(q)) {
            return null;
        }
        String normalized = q.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int clampLimit(Integer limit) {
        int candidate = limit == null ? 20 : limit;
        return Math.min(Math.max(candidate, 1), 200);
    }

    private void validateDateRange(LocalDate issueDateFrom, LocalDate issueDateTo) {
        if (issueDateFrom != null && issueDateTo != null && issueDateFrom.isAfter(issueDateTo)) {
            throw new ValidationException("periodo invalido: issueDateFrom > issueDateTo");
        }
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMAT);
    }

    private String safeDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String mapEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return "OUTRO";
        }
        return switch (eventType) {
            case "110111" -> "CANCELAMENTO";
            case "110110" -> "CCE";
            case "110140" -> "EPEC";
            default -> "OUTRO";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private Instant firstInstant(Instant first, Instant second) {
        return first != null ? first : second;
    }

    public record DocumentXmlDownload(byte[] payload,
                                      String fileName,
                                      String contentType) {
    }

    public record DocumentExportFile(byte[] payload,
                                     String fileName,
                                     String contentType) {
    }

    public record DocumentDanfeDownload(byte[] payload,
                                        String fileName,
                                        String contentType) {
    }

    private record DocumentExportRow(String accessKey,
                                     String serie,
                                     Integer numero,
                                     LocalDate issueDate,
                                     String emitCnpj,
                                     String emitRazaoSocial,
                                     String destCnpj,
                                     String destRazaoSocial,
                                     String operationType,
                                     Short model,
                                     BigDecimal totalAmount,
                                     BigDecimal totalIcms,
                                     BigDecimal totalPis,
                                     BigDecimal totalCofins,
                                     String statusDocumento) {
    }

    private record PdfObject(int id, String body) {
    }
}
