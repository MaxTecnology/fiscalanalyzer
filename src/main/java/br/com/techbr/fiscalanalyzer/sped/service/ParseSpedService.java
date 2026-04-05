package br.com.techbr.fiscalanalyzer.sped.service;

import br.com.techbr.fiscalanalyzer.common.exception.InfraException;
import br.com.techbr.fiscalanalyzer.common.exception.ValidationException;
import br.com.techbr.fiscalanalyzer.queue.message.ParseSpedMessage;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC100;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC170;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalC190;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalE110;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalE111;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFile;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalFileStatus;
import br.com.techbr.fiscalanalyzer.sped.model.SpedFiscalParticipant;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC100Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC170Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalC190Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE110Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalE111Repository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalFileRepository;
import br.com.techbr.fiscalanalyzer.sped.repository.SpedFiscalParticipantRepository;
import br.com.techbr.fiscalanalyzer.storage.service.StorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ParseSpedService {

    private static final Logger log = LoggerFactory.getLogger(ParseSpedService.class);
    private static final DateTimeFormatter SPED_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final SpedFiscalFileRepository spedFiscalFileRepository;
    private final SpedFiscalParticipantRepository participantRepository;
    private final SpedFiscalC100Repository c100Repository;
    private final SpedFiscalC170Repository c170Repository;
    private final SpedFiscalC190Repository c190Repository;
    private final SpedFiscalE110Repository e110Repository;
    private final SpedFiscalE111Repository e111Repository;
    private final StorageService storageService;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer parseTimer;
    private final MeterRegistry meterRegistry;

    public ParseSpedService(SpedFiscalFileRepository spedFiscalFileRepository,
                            SpedFiscalParticipantRepository participantRepository,
                            SpedFiscalC100Repository c100Repository,
                            SpedFiscalC170Repository c170Repository,
                            SpedFiscalC190Repository c190Repository,
                            SpedFiscalE110Repository e110Repository,
                            SpedFiscalE111Repository e111Repository,
                            StorageService storageService,
                            MeterRegistry meterRegistry) {
        this.spedFiscalFileRepository = spedFiscalFileRepository;
        this.participantRepository = participantRepository;
        this.c100Repository = c100Repository;
        this.c170Repository = c170Repository;
        this.c190Repository = c190Repository;
        this.e110Repository = e110Repository;
        this.e111Repository = e111Repository;
        this.storageService = storageService;
        this.meterRegistry = meterRegistry;
        this.successCounter = meterRegistry.counter("sped.parse.success");
        this.failureCounter = meterRegistry.counter("sped.parse.failure");
        this.parseTimer = Timer.builder("sped.parse.duration").register(meterRegistry);
    }

    @Transactional
    public void process(ParseSpedMessage message, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String corr = StringUtils.hasText(correlationId) ? correlationId : UUID.randomUUID().toString();

        SpedFiscalFile file = spedFiscalFileRepository.findById(message.spedFileId())
                .orElseThrow(() -> new ValidationException("SpedFiscalFile nao encontrado: " + message.spedFileId()));

        if (file.getStatus() == SpedFiscalFileStatus.PROCESSADO) {
            return;
        }

        file.setStatus(SpedFiscalFileStatus.PROCESSANDO);
        file.setErroCodigo(null);
        file.setErroMensagem(null);
        spedFiscalFileRepository.save(file);

        try {
            cleanupFileData(file.getId());
            ParseStats stats = withSpedStream(
                    message,
                    stream -> parseAndPersistAll(stream, file),
                    "Falha ao ler SPED no storage"
            );

            file.setLayoutVersion(stats.layoutVersion);
            file.setPeriodoInicio(stats.periodoInicio);
            file.setPeriodoFim(stats.periodoFim);
            file.setLineCountDeclared(stats.lineCountDeclared);
            file.setLineCountProcessed(stats.lineCountProcessed);
            file.setStatus(SpedFiscalFileStatus.PROCESSADO);
            file.setErroCodigo(null);
            file.setErroMensagem(null);
            spedFiscalFileRepository.save(file);

            successCounter.increment();
            log.info(
                    "sped.parse.success spedFileId={} correlationId={} sourceType={} objectKey={} zipEntry={} layout={} periodoInicio={} periodoFim={} participants={} c100={} c170={} c190={} e110={} e111={}",
                    file.getId(), corr, file.getSourceType(), file.getStorageObjectKey(), file.getZipEntryName(),
                    stats.layoutVersion, stats.periodoInicio, stats.periodoFim,
                    stats.participantsCount, stats.c100Count, stats.c170Count, stats.c190Count, stats.e110Count, stats.e111Count
            );
        } catch (ValidationException ex) {
            markFailure(file, "FALHA_PARSE", ex.getMessage(), corr);
            failureCounter.increment();
        } catch (InfraException ex) {
            log.error("sped.parse.retry spedFileId={} correlationId={} objectKey={} zipEntry={} message={}",
                    file.getId(), corr, file.getStorageObjectKey(), file.getZipEntryName(), ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            throw new InfraException("Falha ao processar arquivo SPED", ex);
        } finally {
            sample.stop(parseTimer);
        }
    }

    private void cleanupFileData(Long fileId) {
        e111Repository.deleteByE110FileId(fileId);
        e110Repository.deleteByFileId(fileId);
        c170Repository.deleteByC100FileId(fileId);
        c190Repository.deleteByC100FileId(fileId);
        participantRepository.deleteByFileId(fileId);
        c100Repository.deleteByFileId(fileId);
    }

    private ParseStats parseAndPersistAll(InputStream stream, SpedFiscalFile file) {
        ParseStats stats = new ParseStats();
        int lineNumber = 0;
        SpedFiscalC100 currentC100 = null;
        SpedFiscalE110 currentE110 = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String normalizedLine = normalizeLine(line, lineNumber);
                if (normalizedLine.isBlank()) {
                    continue;
                }

                stats.lineCountProcessed++;
                String[] fields = normalizedLine.split("\\|", -1);
                String register = field(fields, 1);

                switch (register) {
                    case "0000" -> parseHeaderRecord(stats, lineNumber, fields);
                    case "0150" -> {
                        participantRepository.save(mapParticipant(file, lineNumber, fields));
                        stats.participantsCount++;
                    }
                    case "C100" -> {
                        currentC100 = c100Repository.save(mapC100(file, lineNumber, fields));
                        stats.c100Count++;
                    }
                    case "C170" -> {
                        if (currentC100 == null) {
                            throw new ValidationException("sped_c170_sem_c100_linha_" + lineNumber);
                        }
                        c170Repository.save(mapC170(file, currentC100, lineNumber, fields));
                        stats.c170Count++;
                    }
                    case "C190" -> {
                        if (currentC100 == null) {
                            throw new ValidationException("sped_c190_sem_c100_linha_" + lineNumber);
                        }
                        c190Repository.save(mapC190(file, currentC100, lineNumber, fields));
                        stats.c190Count++;
                    }
                    case "E110" -> {
                        currentE110 = e110Repository.save(mapE110(file, lineNumber, fields));
                        stats.e110Count++;
                    }
                    case "E111" -> {
                        if (currentE110 == null) {
                            throw new ValidationException("sped_e111_sem_e110_linha_" + lineNumber);
                        }
                        e111Repository.save(mapE111(file, currentE110, lineNumber, fields));
                        stats.e111Count++;
                    }
                    case "9999" -> stats.lineCountDeclared = parseSpedInteger(field(fields, 2), lineNumber, "sped_qtd_lin_invalida_linha_");
                    default -> {
                        // intentionally ignored
                    }
                }
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfraException("Falha ao persistir blocos do SPED", ex);
        }

        validateParsedStats(stats);
        return stats;
    }

    private void parseHeaderRecord(ParseStats stats, int lineNumber, String[] fields) {
        stats.layoutVersion = normalize(field(fields, 2));
        stats.periodoInicio = parseSpedDate(field(fields, 4), lineNumber, "sped_periodo_inicio_invalido_linha_");
        stats.periodoFim = parseSpedDate(field(fields, 5), lineNumber, "sped_periodo_fim_invalido_linha_");
    }

    private void validateParsedStats(ParseStats stats) {
        if (stats.lineCountProcessed == 0) {
            throw new ValidationException("sped_arquivo_vazio");
        }
        if (!StringUtils.hasText(stats.layoutVersion) || stats.periodoInicio == null || stats.periodoFim == null) {
            throw new ValidationException("sped_registro_0000_obrigatorio_ausente");
        }
        if (stats.lineCountDeclared == null) {
            throw new ValidationException("sped_registro_9999_obrigatorio_ausente");
        }
        if (!stats.lineCountDeclared.equals(stats.lineCountProcessed)) {
            throw new ValidationException("sped_qtd_lin_divergente");
        }
    }

    private SpedFiscalParticipant mapParticipant(SpedFiscalFile file, int lineNumber, String[] fields) {
        SpedFiscalParticipant entity = new SpedFiscalParticipant();
        entity.setFile(file);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setCodPart(required(normalize(field(fields, 2)), "sped_0150_cod_part_obrigatorio_linha_" + lineNumber));
        entity.setNome(normalize(field(fields, 3)));
        entity.setCodPais(normalize(field(fields, 4)));
        entity.setCnpj(normalizeDigits(field(fields, 5), 14));
        entity.setCpf(normalizeDigits(field(fields, 6), 11));
        entity.setIe(normalize(field(fields, 7)));
        entity.setCodMun(normalize(field(fields, 8)));
        entity.setSuframa(normalize(field(fields, 9)));
        entity.setEndereco(normalize(field(fields, 10)));
        entity.setNumero(normalize(field(fields, 11)));
        entity.setComplemento(normalize(field(fields, 12)));
        entity.setBairro(normalize(field(fields, 13)));
        return entity;
    }

    private SpedFiscalC100 mapC100(SpedFiscalFile file, int lineNumber, String[] fields) {
        SpedFiscalC100 entity = new SpedFiscalC100();
        entity.setFile(file);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setIndOper(normalize(field(fields, 2)));
        entity.setIndEmit(normalize(field(fields, 3)));
        entity.setCodPart(normalize(field(fields, 4)));
        entity.setCodMod(normalize(field(fields, 5)));
        entity.setCodSit(normalize(field(fields, 6)));
        entity.setSerie(normalize(field(fields, 7)));
        entity.setNumDoc(normalize(field(fields, 8)));
        entity.setChvNfe(normalizeAccessKey(field(fields, 9)));
        entity.setDtDoc(parseSpedDate(field(fields, 10), lineNumber, "sped_c100_data_invalida_linha_"));
        entity.setDtEs(parseSpedDate(field(fields, 11), lineNumber, "sped_c100_data_invalida_linha_"));
        entity.setVlDoc(parseSpedDecimal(field(fields, 12), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlDesc(parseSpedDecimal(field(fields, 14), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlMerc(parseSpedDecimal(field(fields, 16), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlBcIcms(parseSpedDecimal(field(fields, 21), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlIcms(parseSpedDecimal(field(fields, 22), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlBcIcmsSt(parseSpedDecimal(field(fields, 23), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlIcmsSt(parseSpedDecimal(field(fields, 24), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlIpi(parseSpedDecimal(field(fields, 25), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlPis(parseSpedDecimal(field(fields, 26), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlCofins(parseSpedDecimal(field(fields, 27), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlPisSt(parseSpedDecimal(field(fields, 28), lineNumber, "sped_c100_decimal_invalido_linha_"));
        entity.setVlCofinsSt(parseSpedDecimal(field(fields, 29), lineNumber, "sped_c100_decimal_invalido_linha_"));
        return entity;
    }

    private SpedFiscalC170 mapC170(SpedFiscalFile file, SpedFiscalC100 c100, int lineNumber, String[] fields) {
        SpedFiscalC170 entity = new SpedFiscalC170();
        entity.setC100(c100);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setNumItem(parseSpedInteger(field(fields, 2), lineNumber, "sped_c170_num_item_invalido_linha_"));
        entity.setCodItem(normalize(field(fields, 3)));
        entity.setDescrCompl(normalize(field(fields, 4)));
        entity.setQtd(parseSpedDecimal(field(fields, 5), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setUnid(normalize(field(fields, 6)));
        entity.setVlItem(parseSpedDecimal(field(fields, 7), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setVlDesc(parseSpedDecimal(field(fields, 8), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setCstIcms(normalize(field(fields, 10)));
        entity.setCfop(normalize(field(fields, 11)));
        entity.setVlBcIcms(parseSpedDecimal(field(fields, 13), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setAliqIcms(parseSpedDecimal(field(fields, 14), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setVlIcms(parseSpedDecimal(field(fields, 15), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setCstIpi(normalize(field(fields, 20)));
        entity.setVlBcIpi(parseSpedDecimal(field(fields, 22), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setAliqIpi(parseSpedDecimal(field(fields, 23), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setVlIpi(parseSpedDecimal(field(fields, 24), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setCstPis(normalize(field(fields, 25)));
        entity.setVlBcPis(parseSpedDecimal(field(fields, 26), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setAliqPis(parseSpedDecimal(field(fields, 27), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setVlPis(parseSpedDecimal(field(fields, 28), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setCstCofins(normalize(field(fields, 29)));
        entity.setVlBcCofins(parseSpedDecimal(field(fields, 30), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setAliqCofins(parseSpedDecimal(field(fields, 31), lineNumber, "sped_c170_decimal_invalido_linha_"));
        entity.setVlCofins(parseSpedDecimal(field(fields, 32), lineNumber, "sped_c170_decimal_invalido_linha_"));
        return entity;
    }

    private SpedFiscalC190 mapC190(SpedFiscalFile file, SpedFiscalC100 c100, int lineNumber, String[] fields) {
        SpedFiscalC190 entity = new SpedFiscalC190();
        entity.setC100(c100);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setCstIcms(normalize(field(fields, 2)));
        entity.setCfop(normalize(field(fields, 3)));
        entity.setAliqIcms(parseSpedDecimal(field(fields, 4), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlOpr(parseSpedDecimal(field(fields, 5), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlBcIcms(parseSpedDecimal(field(fields, 6), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlIcms(parseSpedDecimal(field(fields, 7), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlBcIcmsSt(parseSpedDecimal(field(fields, 8), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlIcmsSt(parseSpedDecimal(field(fields, 9), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlRedBc(parseSpedDecimal(field(fields, 10), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setVlIpi(parseSpedDecimal(field(fields, 11), lineNumber, "sped_c190_decimal_invalido_linha_"));
        entity.setCodObs(normalize(field(fields, 12)));
        return entity;
    }

    private SpedFiscalE110 mapE110(SpedFiscalFile file, int lineNumber, String[] fields) {
        SpedFiscalE110 entity = new SpedFiscalE110();
        entity.setFile(file);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setVlTotDebitos(parseSpedDecimal(field(fields, 2), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlAjDebitos(parseSpedDecimal(field(fields, 3), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlTotAjDebitos(parseSpedDecimal(field(fields, 4), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlEstornosCred(parseSpedDecimal(field(fields, 5), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlTotCreditos(parseSpedDecimal(field(fields, 6), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlAjCreditos(parseSpedDecimal(field(fields, 7), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlTotAjCreditos(parseSpedDecimal(field(fields, 8), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlEstornosDeb(parseSpedDecimal(field(fields, 9), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlSldCredorAnterior(parseSpedDecimal(field(fields, 10), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlSldApurado(parseSpedDecimal(field(fields, 11), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlTotDed(parseSpedDecimal(field(fields, 12), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlIcmsRecolher(parseSpedDecimal(field(fields, 13), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setVlSldCredorTransportar(parseSpedDecimal(field(fields, 14), lineNumber, "sped_e110_decimal_invalido_linha_"));
        entity.setDebEsp(parseSpedDecimal(field(fields, 15), lineNumber, "sped_e110_decimal_invalido_linha_"));
        return entity;
    }

    private SpedFiscalE111 mapE111(SpedFiscalFile file, SpedFiscalE110 e110, int lineNumber, String[] fields) {
        SpedFiscalE111 entity = new SpedFiscalE111();
        entity.setE110(e110);
        entity.setTenantId(file.getTenantId());
        entity.setEmpresaId(file.getEmpresaId());
        entity.setLinhaOrigem(lineNumber);
        entity.setCodAjApur(normalize(field(fields, 2)));
        entity.setDescrComplAj(normalize(field(fields, 3)));
        entity.setVlAjApur(parseSpedDecimal(field(fields, 4), lineNumber, "sped_e111_decimal_invalido_linha_"));
        return entity;
    }

    private <T> T withSpedStream(ParseSpedMessage message, SpedStreamParser<T> parser, String infraErrorMessage) {
        if (StringUtils.hasText(message.zipEntryName())) {
            return parseFromZip(message.objectKey(), message.zipEntryName(), parser, infraErrorMessage);
        }
        return parseDirect(message.objectKey(), parser, infraErrorMessage);
    }

    private <T> T parseDirect(String objectKey, SpedStreamParser<T> parserFn, String infraErrorMessage) {
        try (InputStream stream = storageService.get(objectKey)) {
            return parserFn.parse(stream);
        } catch (ValidationException | InfraException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfraException(infraErrorMessage, ex);
        }
    }

    private <T> T parseFromZip(String zipObjectKey, String zipEntryName, SpedStreamParser<T> parserFn, String infraErrorMessage) {
        String normalizedTarget = normalizePath(zipEntryName);
        try (InputStream raw = storageService.get(zipObjectKey);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String current = normalizePath(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                if (!normalizedTarget.equals(current)) {
                    continue;
                }
                return parserFn.parse(zis);
            }
            throw new ValidationException("zip_entry_nao_encontrada");
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfraException(infraErrorMessage, ex);
        }
    }

    private void markFailure(SpedFiscalFile file, String code, String message, String correlationId) {
        file.setStatus(SpedFiscalFileStatus.FALHA);
        file.setErroCodigo(code);
        file.setErroMensagem(trim(message, 500));
        spedFiscalFileRepository.save(file);
        log.warn("sped.parse.validation spedFileId={} correlationId={} code={} message={}",
                file.getId(), correlationId, code, message);
    }

    private String normalizeLine(String line, int lineNumber) {
        if (line == null) {
            return "";
        }
        String normalized = line.strip();
        if (lineNumber == 1 && !normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('\\', '/');
    }

    private String field(String[] fields, int idx) {
        if (idx < 0 || idx >= fields.length) {
            return "";
        }
        return fields[idx];
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String required(String value, String errorCode) {
        if (!StringUtils.hasText(value)) {
            throw new ValidationException(errorCode);
        }
        return value;
    }

    private String normalizeDigits(String value, int expectedLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != expectedLength) {
            return null;
        }
        return digits;
    }

    private String normalizeAccessKey(String value) {
        return normalizeDigits(value, 44);
    }

    private LocalDate parseSpedDate(String rawValue, int lineNumber, String errorPrefix) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return LocalDate.parse(rawValue.trim(), SPED_DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(errorPrefix + lineNumber);
        }
    }

    private Integer parseSpedInteger(String rawValue, int lineNumber, String errorPrefix) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(errorPrefix + lineNumber);
        }
    }

    private BigDecimal parseSpedDecimal(String rawValue, int lineNumber, String errorPrefix) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return new BigDecimal(rawValue.trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            throw new ValidationException(errorPrefix + lineNumber);
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    @FunctionalInterface
    private interface SpedStreamParser<T> {
        T parse(InputStream stream) throws Exception;
    }

    private static final class ParseStats {
        private String layoutVersion;
        private LocalDate periodoInicio;
        private LocalDate periodoFim;
        private Integer lineCountDeclared;
        private int lineCountProcessed;
        private long participantsCount;
        private long c100Count;
        private long c170Count;
        private long c190Count;
        private long e110Count;
        private long e111Count;
    }
}
