package br.com.techbr.fiscalanalyzer.importacao.service;

import br.com.techbr.fiscalanalyzer.importacao.model.ImportItem;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportItemStatus;
import br.com.techbr.fiscalanalyzer.importacao.model.Importacao;
import br.com.techbr.fiscalanalyzer.importacao.model.ImportacaoStatus;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportItemRepository;
import br.com.techbr.fiscalanalyzer.importacao.repository.ImportacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Trata mensagens que esgotaram as tentativas de retry e foram encaminhadas para a DLQ.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Marcar {@code import_item} individual como {@code FALHA_PERMANENTE} (falha de parse).</li>
 *   <li>Marcar {@code importacao} inteira como {@code FALHA} (falha de extração).</li>
 *   <li>Verificar se a importacao pode ser finalizada após cada item permanentemente falho.</li>
 * </ul>
 */
@Service
public class DlqHandlerService {

    private static final Logger log = LoggerFactory.getLogger(DlqHandlerService.class);

    private static final Set<ImportItemStatus> PROCESSED_STATUSES = EnumSet.of(
            ImportItemStatus.PARSEADO,
            ImportItemStatus.DUPLICADO,
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.CONCLUIDO,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );

    private static final Set<ImportItemStatus> ERROR_STATUSES = EnumSet.of(
            ImportItemStatus.FALHA_PARSE,
            ImportItemStatus.ERRO,
            ImportItemStatus.FALHA_PERMANENTE
    );

    private final ImportItemRepository importItemRepository;
    private final ImportacaoRepository importacaoRepository;

    public DlqHandlerService(ImportItemRepository importItemRepository,
                             ImportacaoRepository importacaoRepository) {
        this.importItemRepository = importItemRepository;
        this.importacaoRepository = importacaoRepository;
    }

    /**
     * Marca um {@code import_item} como {@code FALHA_PERMANENTE} e verifica finalização
     * da importação.
     *
     * <p>Chamado pelo consumer da DLQ de parse após esgotamento de retries.
     */
    @Transactional
    public void markItemFalhaPermanente(long importacaoId, long importItemId, String causa) {
        Optional<ImportItem> itemOpt = importItemRepository.findById(importItemId);
        if (itemOpt.isEmpty()) {
            log.warn("dlq.handler.item_nao_encontrado importacaoId={} importItemId={}", importacaoId, importItemId);
            return;
        }

        ImportItem item = itemOpt.get();
        if (ImportItemStatus.FALHA_PERMANENTE == item.getStatus()) {
            log.info("dlq.handler.item_ja_permanente importacaoId={} importItemId={}", importacaoId, importItemId);
            return;
        }

        item.setStatus(ImportItemStatus.FALHA_PERMANENTE);
        item.setErroCodigo("DLQ_PERMANENTE");
        item.setErroMensagem(causa != null ? causa : "Esgotadas todas as tentativas de retry");
        importItemRepository.save(item);

        log.error("dlq.handler.item_falha_permanente importacaoId={} importItemId={} causa={}",
                importacaoId, importItemId, causa);

        finalizarSeCompleto(importacaoId);
    }

    /**
     * Marca a {@code importacao} inteira como {@code FALHA} quando a extração do ZIP
     * esgotou as tentativas.
     *
     * <p>Chamado pelo consumer da DLQ de extração.
     */
    @Transactional
    public void markImportacaoFalha(long importacaoId, String causa) {
        Optional<Importacao> importacaoOpt = importacaoRepository.findById(importacaoId);
        if (importacaoOpt.isEmpty()) {
            log.warn("dlq.handler.importacao_nao_encontrada importacaoId={}", importacaoId);
            return;
        }

        Importacao importacao = importacaoOpt.get();
        importacao.setStatus(ImportacaoStatus.FALHA);
        importacao.setErroCodigo("DLQ_EXTRACT_PERMANENTE");
        importacao.setErroMensagem(causa != null ? causa : "Esgotadas todas as tentativas de extração");
        importacaoRepository.save(importacao);

        log.error("dlq.handler.importacao_falha_permanente importacaoId={} causa={}", importacaoId, causa);
    }

    private void finalizarSeCompleto(long importacaoId) {
        Optional<Importacao> importacaoOpt = importacaoRepository.findByIdForUpdate(importacaoId);
        if (importacaoOpt == null || importacaoOpt.isEmpty()) {
            importacaoOpt = importacaoRepository.findById(importacaoId);
            if (importacaoOpt.isEmpty()) {
                return;
            }
        }

        Importacao importacao = importacaoOpt.get();
        long totalItems = importItemRepository.countByImportacaoId(importacaoId);
        long processed = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, PROCESSED_STATUSES);
        long errors = importItemRepository.countByImportacaoIdAndStatusIn(importacaoId, ERROR_STATUSES);

        importacao.setTotalProcessado((int) processed);
        importacao.setTotalErros((int) errors);

        if (totalItems > 0 && processed >= totalItems) {
            importacao.setStatus(ImportacaoStatus.CONCLUIDO);
            log.info("dlq.handler.importacao_concluida importacaoId={}", importacaoId);
        }

        importacaoRepository.save(importacao);
    }
}
