package com.doller.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handle(ApiException ex, HttpServletRequest request) {
        log.warn("api_exception path={} method={} traceId={} message={}",
                request.getRequestURI(),
                request.getMethod(),
                MDC.get(TraceLoggingFilter.TRACE_ID_MDC_KEY),
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "BUSINESS_ERROR", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("validation_exception path={} method={} traceId={} fields={}",
                request.getRequestURI(),
                request.getMethod(),
                MDC.get(TraceLoggingFilter.TRACE_ID_MDC_KEY),
                fields.keySet());
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "Invalid request", "fields", fields));
    }
}
