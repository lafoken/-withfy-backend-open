package com.withfy.userservice.exception;

import org.springframework.http.HttpStatus;

public class StorageServiceException extends AppException {
    public StorageServiceException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
     public StorageServiceException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
