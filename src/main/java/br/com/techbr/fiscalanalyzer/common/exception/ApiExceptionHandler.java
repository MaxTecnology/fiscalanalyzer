package br.com.techbr.fiscalanalyzer.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
            ValidationException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            BindException.class,
            ConversionFailedException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String message = resolveValidationMessage(ex);
        log.warn("api.validation.error type={} message={}", ex.getClass().getSimpleName(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDataAccessUsage(InvalidDataAccessApiUsageException ex) {
        String message = "Parametros de consulta invalidos";
        log.warn("api.validation.query_error message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(InfraException.class)
    public ResponseEntity<ErrorResponse> handleInfra(InfraException ex) {
        log.error("api.infra.error message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INFRA_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableEntityException ex) {
        log.warn("api.unprocessable.error message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("UNPROCESSABLE_ENTITY", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("api.auth.unauthorized message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("AUTH_UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        log.warn("api.auth.forbidden message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("AUTH_FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("api.conflict.error message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        log.warn("api.rate_limit.exceeded message={} retryAfter={}", ex.getMessage(), ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponse("RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("api.unexpected.error message={}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Erro interno inesperado"));
    }

    private String resolveValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "Parametro '%s' invalido: valor '%s'".formatted(
                    mismatch.getName(),
                    mismatch.getValue()
            );
        }
        if (ex instanceof MissingServletRequestParameterException missingParameter) {
            return "Parametro obrigatorio ausente: " + missingParameter.getParameterName();
        }
        if (ex instanceof MissingServletRequestPartException missingPart) {
            return "Parte obrigatoria ausente: " + missingPart.getRequestPartName();
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Corpo da requisicao invalido";
        }
        if (ex instanceof ConstraintViolationException violation && violation.getConstraintViolations() != null
                && !violation.getConstraintViolations().isEmpty()) {
            return violation.getConstraintViolations().iterator().next().getMessage();
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Dados de entrada invalidos";
        }
        return message;
    }
}
