package org.pronto.pronto.exception;

import lombok.extern.slf4j.Slf4j;
import org.pronto.pronto.dto.ErrorResponse;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/** Translates exceptions into {code, message, details?, missing?} once, for the whole API. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String CONFLICT = "CONFLICT";
    private static final String INVALID_RULE = "INVALID_RULE";
    private static final String MISSING_TEMPLATE_VARIABLE = "MISSING_TEMPLATE_VARIABLE";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    private static final String VALIDATION_FAILED = "request validation failed";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return reject(HttpStatus.NOT_FOUND, error(NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return reject(HttpStatus.CONFLICT, error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataConflict(DataIntegrityViolationException e) {
        log.warn("data integrity violation cause={}", e.getMostSpecificCause().getMessage());
        return reject(HttpStatus.CONFLICT, error(CONFLICT, "request conflicts with existing data"));
    }

    /** Two editors saved the same rule at once; the version check in RuleService covers the common case. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> staleVersion(OptimisticLockingFailureException e) {
        return reject(HttpStatus.CONFLICT, error(CONFLICT, "the record was changed by someone else; reload and retry"));
    }

    @ExceptionHandler(InvalidRuleException.class)
    public ResponseEntity<ErrorResponse> invalidRule(InvalidRuleException e) {
        return reject(HttpStatus.BAD_REQUEST, error(INVALID_RULE, e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e) {
        return reject(HttpStatus.BAD_REQUEST, error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MissingTemplateVariableException.class)
    public ResponseEntity<ErrorResponse> missingVariable(MissingTemplateVariableException e) {
        ErrorResponse body = ErrorResponse.builder()
                .code(MISSING_TEMPLATE_VARIABLE)
                .message(e.getMessage())
                .missing(e.getMissing())
                .build();
        return reject(HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.internalServerError().body(error(INTERNAL_ERROR, "unexpected error"));
    }

    /** Spring MVC's own 4xx (bad JSON, failed @Valid, missing params, 404/405) in our error shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        List<String> details = detailsOf(ex);
        String message = body instanceof ProblemDetail problem ? problem.getDetail() : ex.getMessage();
        ErrorResponse error = ErrorResponse.builder()
                .code(resolved == null ? String.valueOf(status.value()) : resolved.name())
                // field errors are in details; the raw exception message leaks Java signatures
                .message(details == null ? message : VALIDATION_FAILED)
                .details(details)
                .build();
        log.warn("request rejected status={} code={} message={}", status.value(), error.getCode(), error.getMessage());
        return super.handleExceptionInternal(ex, error, headers, status, request);
    }

    private static List<String> detailsOf(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException e) {
            return e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .toList();
        }
        if (ex instanceof HandlerMethodValidationException e) {
            return e.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage).toList();
        }
        return null;
    }

    private static ErrorResponse error(String code, String message) {
        return ErrorResponse.builder().code(code).message(message).build();
    }

    private static ResponseEntity<ErrorResponse> reject(HttpStatus status, ErrorResponse body) {
        log.warn("request rejected status={} code={} message={}", status.value(), body.getCode(), body.getMessage());
        return ResponseEntity.status(status).body(body);
    }
}
