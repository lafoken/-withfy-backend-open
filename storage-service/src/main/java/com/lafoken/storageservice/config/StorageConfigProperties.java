package com.withfy.storageservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.service")
public record StorageConfigProperties(
    String publicUrlPrefix
) {}

