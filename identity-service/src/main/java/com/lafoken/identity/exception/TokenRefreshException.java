package com.lafoken.identity.exception;

import org.springframework.http.HttpStatus;

public class TokenRefreshException extends AppException {
    public TokenRefreshException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
