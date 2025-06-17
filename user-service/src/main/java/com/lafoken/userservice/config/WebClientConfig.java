package com.withfy.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${storage.service.url}")
    private String storageServiceUrl;

    @Bean
    public WebClient storageServiceWebClient(WebClient.Builder builder) {
        return builder.baseUrl(storageServiceUrl).build();
    }
}

