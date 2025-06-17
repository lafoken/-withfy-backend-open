package com.withfy.storageservice.service;

import com.withfy.storageservice.config.StorageConfigProperties;
import com.withfy.storageservice.dto.FileUploadResponse;
import com.withfy.storageservice.exception.InvalidStorageRequestException;
import com.withfy.storageservice.exception.MinioOperationException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final MinioClient minioClient;
    private final StorageConfigProperties storageConfigProperties;

    public Mono<FileUploadResponse> uploadFile(String bucketName, String objectKey, FilePart filePart) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey) || filePart == null) {
            return Mono.error(new InvalidStorageRequestException("Bucket name, object key, and file part are required."));
        }
        if (filePart.filename().isEmpty()){
            return Mono.error(new InvalidStorageRequestException("File part is empty or filename is missing."));
        }

        return Mono.fromCallable(() -> {
            try (PipedInputStream pipedInputStream = new PipedInputStream();
                 PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {

                DataBufferUtils.write(filePart.content(), pipedOutputStream)
                    .subscribe(DataBufferUtils.releaseConsumer(),
                               e -> {
                                   try { pipedOutputStream.close(); } catch (Exception ignored) {}
                                   log.error("Error writing file part to stream for {}", objectKey, e);
                               },
                               () -> {
                                   try { pipedOutputStream.close(); } catch (Exception ignored) {}
                               });

                long size = -1;
                long partSize = 10 * 1024 * 1024;
                String contentType = Objects.requireNonNullElse(
                        filePart.headers().getContentType(),
                        MediaType.APPLICATION_OCTET_STREAM
                ).toString();

                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(pipedInputStream, size, partSize)
                        .contentType(contentType)
                        .build());

                String publicUrl = String.join("/", storageConfigProperties.publicUrlPrefix(), bucketName, objectKey);
                log.info("File uploaded successfully: {}/{}, public URL: {}", bucketName, objectKey, publicUrl);
                return new FileUploadResponse(objectKey, bucketName, publicUrl);
            } catch (Exception e) {
                log.error("Error uploading file {} to bucket {}: {}", objectKey, bucketName, e.getMessage(), e);
                throw new MinioOperationException("Error uploading file to MinIO: " + objectKey, e);
            }
        }).onErrorMap(e -> !(e instanceof MinioOperationException || e instanceof InvalidStorageRequestException) ,
                      e -> new MinioOperationException("Unexpected error during upload for " + objectKey, e));
    }

    public Mono<String> getPublicUrl(String bucketName, String objectKey) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey)) {
            return Mono.error(new InvalidStorageRequestException("Bucket name and object key are required for getting URL."));
        }
        String fullUrl = String.join("/", storageConfigProperties.publicUrlPrefix(), bucketName, objectKey);
        log.debug("Generated public URL for {}/{}: {}", bucketName, objectKey, fullUrl);
        return Mono.just(fullUrl);
    }

    public Mono<Void> deleteFile(String bucketName, String objectKey) {
         if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey)) {
            return Mono.error(new InvalidStorageRequestException("Bucket name and object key are required for deletion."));
        }
        return Mono.fromRunnable(() -> {
            try {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build());
                log.info("File deleted successfully: {}/{}", bucketName, objectKey);
            } catch (Exception e) {
                log.error("Error deleting file {} from bucket {}: {}", objectKey, bucketName, e.getMessage(), e);
                throw new MinioOperationException("Error deleting file from MinIO: " + objectKey, e);
            }
        }).onErrorMap(e -> !(e instanceof MinioOperationException || e instanceof InvalidStorageRequestException) ,
                      e -> new MinioOperationException("Unexpected error during deletion for " + objectKey, e))
          .then();
    }
}
