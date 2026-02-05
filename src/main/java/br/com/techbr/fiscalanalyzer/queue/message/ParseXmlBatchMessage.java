package br.com.techbr.fiscalanalyzer.queue.message;

import java.util.List;

public record ParseXmlBatchMessage(
        long importacaoId,
        List<Long> importItemIds
) {}
