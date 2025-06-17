package com.withfy.userservice.exception;

import org.springframework.http.HttpStatus;

public class UserProfileNotFoundException extends AppException {
    public UserProfileNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
