package com.withfy.storageservice.exception;

import org.springframework.http.HttpStatus;

public class InvalidStorageRequestException extends AppException {
    public InvalidStorageRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
