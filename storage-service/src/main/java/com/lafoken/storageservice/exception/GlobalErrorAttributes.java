package com.withfy.storageservice.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.validation.BindingResult;
import org.springframework.context.support.DefaultMessageSourceResolvable;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        Map<String, Object> errorAttributes = new HashMap<>();

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred in storage service";
        List<String> details = null;
        Map<String, String> validationErrors = null;


        if (error instanceof AppException appException) {
            status = appException.getStatus();
            message = appException.getMessage();
        } else if (error instanceof WebExchangeBindException webExchangeBindException) {
            status = HttpStatus.BAD_REQUEST;
            message = "Validation Failed";
            BindingResult bindingResult = webExchangeBindException.getBindingResult();
            validationErrors = bindingResult.getFieldErrors().stream()
                    .collect(Collectors.toMap(
                            org.springframework.validation.FieldError::getField,
                            fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value",
                            (existing, replacement) -> existing + "; " + replacement
                    ));
        } else if (error instanceof ResponseStatusException rse) {
            status = (HttpStatus) rse.getStatusCode();
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (error != null) {
            message = error.getMessage() != null ? error.getMessage() : "Internal Server Error in storage service";
        }

        ErrorResponse errorResponsePayload;

        if (validationErrors != null) {
            errorResponsePayload = new ErrorResponse(
                status,
                request.path(),
                validationErrors
            );
        } else if (details != null) {
            errorResponsePayload = new ErrorResponse(
                status,
                message,
                request.path(),
                details
            );
        } else {
            errorResponsePayload = new ErrorResponse(
                status,
                message,
                request.path()
            );
        }

        errorAttributes.put("timestamp", errorResponsePayload.timestamp().toString());
        errorAttributes.put("status", errorResponsePayload.status());
        errorAttributes.put("error", errorResponsePayload.error());
        errorAttributes.put("message", errorResponsePayload.message());
        errorAttributes.put("path", errorResponsePayload.path());

        if (errorResponsePayload.details() != null) {
            errorAttributes.put("details", errorResponsePayload.details());
        }
        if (errorResponsePayload.validationErrors() != null) {
            errorAttributes.put("validationErrors", errorResponsePayload.validationErrors());
        }

        return errorAttributes;
    }
}
