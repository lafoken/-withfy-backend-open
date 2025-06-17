package com.withfy.storageservice.dto;

public record FileUploadResponse(String objectKey, String bucketName, String publicUrl) {}

