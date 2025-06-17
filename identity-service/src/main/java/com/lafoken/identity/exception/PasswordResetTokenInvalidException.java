package com.lafoken.identity.exception;

import org.springframework.http.HttpStatus;

public class PasswordResetTokenInvalidException extends AppException {
    public PasswordResetTokenInvalidException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
