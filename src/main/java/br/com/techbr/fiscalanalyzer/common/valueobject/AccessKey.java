package br.com.techbr.fiscalanalyzer.common.valueobject;

import java.util.Objects;

public final class AccessKey {
    private final String value; // 44 chars

    private AccessKey(String value) {
        this.value = value;
    }

    public static AccessKey of(String raw) {
        if (raw == null) throw new IllegalArgumentException("AccessKey cannot be null");
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 44) throw new IllegalArgumentException("Invalid access key: " + raw);
        return new AccessKey(digits);
    }

    public String value() {
        return value;
    }

    @Override public String toString() { return value; }
    @Override public boolean equals(Object o) { return (o instanceof AccessKey k) && Objects.equals(value, k.value); }
    @Override public int hashCode() { return Objects.hash(value); }
}
