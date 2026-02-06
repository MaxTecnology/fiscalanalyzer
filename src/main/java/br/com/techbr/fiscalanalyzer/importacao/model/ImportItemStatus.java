package br.com.techbr.fiscalanalyzer.importacao.model;

public enum ImportItemStatus {
    PENDENTE,
    PENDENTE_PARSE,
    PROCESSANDO,
    PARSEADO,
    DUPLICADO,
    FALHA_PARSE,
    CONCLUIDO,
    ERRO
}
