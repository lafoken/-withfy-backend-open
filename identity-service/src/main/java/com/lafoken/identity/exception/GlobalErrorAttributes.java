package com.lafoken.identity.exception;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        Map<String, Object> errorAttributes = new HashMap<>();

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred";
        List<String> details = null;
        Map<String, String> validationErrors = null;

        if (error instanceof AppException appException) {
            status = appException.getStatus();
            message = appException.getMessage();
        } else if (error instanceof ResponseStatusException rse) {
            status = (HttpStatus) rse.getStatusCode();
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (error != null) {
            message = error.getMessage() != null ? error.getMessage() : "Internal Server Error";
        }

        errorAttributes.put("timestamp", LocalDateTime.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", message);
        errorAttributes.put("path", request.path());

        if (details != null && !details.isEmpty()) {
            errorAttributes.put("details", details);
        }
        if (validationErrors != null && !validationErrors.isEmpty()) {
            errorAttributes.put("validationErrors", validationErrors);
        }

        return errorAttributes;
    }
}
