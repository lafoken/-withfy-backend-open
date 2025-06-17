package com.withfy.storageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    String url,
    String accessKey,
    String secretKey,
    BucketProperties bucket
) {
    public record BucketProperties(String images, String songs) {}
}

