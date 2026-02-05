package br.com.techbr.fiscalanalyzer.common.enums;

public enum OperationType {
    ENTRADA('E'),
    SAIDA('S');

    private final char code;

    OperationType(char code) {
        this.code = code;
    }

    public char getCode() {
        return code;
    }

    public static OperationType fromCode(char code) {
        return switch (code) {
            case 'E' -> ENTRADA;
            case 'S' -> SAIDA;
            default -> throw new IllegalArgumentException("Unsupported operation type: " + code);
        };
    }
}
