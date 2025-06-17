package com.lafoken.identity.exception;

import org.springframework.http.HttpStatus;

public class OperationNotAllowedException extends AppException {
    public OperationNotAllowedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
