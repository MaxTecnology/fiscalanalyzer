package br.com.techbr.fiscalanalyzer.common.enums;

public enum ModelType {
    NFE_55((short) 55),
    NFCE_65((short) 65);

    private final short code;

    ModelType(short code) {
        this.code = code;
    }

    public short getCode() {
        return code;
    }

    public static ModelType fromCode(short code) {
        return switch (code) {
            case 55 -> NFE_55;
            case 65 -> NFCE_65;
            default -> throw new IllegalArgumentException("Unsupported model: " + code);
        };
    }
}
