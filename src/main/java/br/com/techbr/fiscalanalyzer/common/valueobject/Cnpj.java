package br.com.techbr.fiscalanalyzer.common.valueobject;

import java.util.Objects;

public final class Cnpj {
    private final String value; // 14 dígitos, somente números

    private Cnpj(String value) {
        this.value = value;
    }

    public static Cnpj of(String raw) {
        if (raw == null) throw new IllegalArgumentException("CNPJ cannot be null");
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 14) throw new IllegalArgumentException("Invalid CNPJ: " + raw);
        return new Cnpj(digits);
    }

    public String value() {
        return value;
    }

    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) { return (o instanceof Cnpj c) && Objects.equals(value, c.value); }
    @Override public int hashCode() { return Objects.hash(value); }
}
