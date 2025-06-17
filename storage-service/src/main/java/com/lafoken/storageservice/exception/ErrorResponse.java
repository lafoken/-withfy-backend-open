package com.withfy.storageservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details,
    Map<String, String> validationErrors
) {
    public ErrorResponse(HttpStatus httpStatus, String message, String path) {
        this(LocalDateTime.now(), httpStatus.value(), httpStatus.getReasonPhrase(), message, path, null, null);
    }

    public ErrorResponse(HttpStatus httpStatus, String message, String path, List<String> details) {
        this(LocalDateTime.now(), httpStatus.value(), httpStatus.getReasonPhrase(), message, path, details, null);
    }

    public ErrorResponse(HttpStatus httpStatus, String path, Map<String, String> validationErrors) {
        this(LocalDateTime.now(), httpStatus.value(), httpStatus.getReasonPhrase(), "Validation Failed", path, null, validationErrors);
    }
}
