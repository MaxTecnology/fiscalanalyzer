package br.com.techbr.fiscalanalyzer.common.exception;

public class InfraException extends RuntimeException {
    public InfraException(String message, Throwable cause) {
        super(message, cause);
    }
}
