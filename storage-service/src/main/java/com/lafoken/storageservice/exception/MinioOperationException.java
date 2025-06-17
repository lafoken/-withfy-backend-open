package com.withfy.storageservice.exception;

import org.springframework.http.HttpStatus;

public class MinioOperationException extends AppException {
    public MinioOperationException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
     public MinioOperationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
