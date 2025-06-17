package com.withfy.userservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageServiceClient {

    @Qualifier("storageServiceWebClient")
    private final WebClient webClient;

    public static record FileUploadResponse(String objectKey, String bucketName, String publicUrl) {}

    public Mono<FileUploadResponse> uploadFile(String bucketName, String objectKey, FilePart filePart) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/storage/upload")
                        .queryParam("bucketName", bucketName)
                        .queryParam("objectKey", objectKey)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", filePart))
                .retrieve()
                .bodyToMono(FileUploadResponse.class)
                .doOnError(e -> log.error("Error uploading to storage service for {}/{}: {}", bucketName, objectKey, e.getMessage(), e))
                .onErrorMap(WebClientResponseException.class, e ->
                        new RuntimeException(String.format("Storage service upload failed for %s with status %s: %s", objectKey, e.getStatusCode(), e.getResponseBodyAsString()), e))
                .onErrorMap(e -> !(e instanceof RuntimeException && e.getMessage().startsWith("Storage service upload failed")),
                        e -> new RuntimeException("Unexpected error during storage service upload for " + objectKey, e));
    }

    public Mono<Void> deleteFile(String bucketName, String objectKey) {
        return webClient.delete()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/storage/object")
                        .queryParam("bucketName", bucketName)
                        .queryParam("objectKey", objectKey)
                        .build())
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Error deleting from storage service for {}/{}: {}", bucketName, objectKey, e.getMessage(), e))
                .onErrorMap(WebClientResponseException.class, e ->
                        new RuntimeException(String.format("Storage service delete failed for %s with status %s: %s", objectKey, e.getStatusCode(), e.getResponseBodyAsString()), e))
                .onErrorMap(e -> !(e instanceof RuntimeException && e.getMessage().startsWith("Storage service delete failed")),
                        e -> new RuntimeException("Unexpected error during storage service delete for " + objectKey, e));
    }
}
