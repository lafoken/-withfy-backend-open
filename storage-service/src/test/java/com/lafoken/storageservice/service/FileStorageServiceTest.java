package com.withfy.storageservice.service;

import com.withfy.storageservice.config.StorageConfigProperties;
import com.withfy.storageservice.dto.FileUploadResponse;
import com.withfy.storageservice.exception.InvalidStorageRequestException;
import com.withfy.storageservice.exception.MinioOperationException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StorageConfigProperties storageConfigProperties;

    @InjectMocks
    private FileStorageService fileStorageService;

    private FilePart mockFilePart;
    private final String BUCKET_NAME = "test-bucket";
    private final String OBJECT_KEY = "test-object.txt";
    private final String PUBLIC_URL_PREFIX = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        mockFilePart = mock(FilePart.class, withSettings().strictness(Strictness.LENIENT));
        lenient().when(mockFilePart.filename()).thenReturn("test-file.txt");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        lenient().when(mockFilePart.headers()).thenReturn(headers);

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap("test content".getBytes());
        lenient().when(mockFilePart.content()).thenReturn(Flux.just(dataBuffer));
        storageConfigProperties = mock(StorageConfigProperties.class, withSettings().strictness(Strictness.LENIENT));
        lenient().when(storageConfigProperties.publicUrlPrefix()).thenReturn(PUBLIC_URL_PREFIX);
        fileStorageService = new FileStorageService(minioClient, storageConfigProperties);

    }

    @Test
    void uploadFile_whenValidInput_shouldSucceed() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        Mono<FileUploadResponse> result = fileStorageService.uploadFile(BUCKET_NAME, OBJECT_KEY, mockFilePart);

        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.bucketName().equals(BUCKET_NAME) &&
                response.objectKey().equals(OBJECT_KEY) &&
                response.publicUrl().equals(PUBLIC_URL_PREFIX + "/" + BUCKET_NAME + "/" + OBJECT_KEY)
            )
            .verifyComplete();

        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_whenFilePartIsNull_shouldThrowInvalidStorageRequestException() throws Exception {
        Mono<FileUploadResponse> result = fileStorageService.uploadFile(BUCKET_NAME, OBJECT_KEY, null);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_whenBucketNameIsEmpty_shouldThrowInvalidStorageRequestException() throws Exception {
        Mono<FileUploadResponse> result = fileStorageService.uploadFile("", OBJECT_KEY, mockFilePart);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_whenObjectKeyIsEmpty_shouldThrowInvalidStorageRequestException() throws Exception {
        Mono<FileUploadResponse> result = fileStorageService.uploadFile(BUCKET_NAME, "", mockFilePart);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_whenFilenameIsEmpty_shouldThrowInvalidStorageRequestException() throws Exception {
        when(mockFilePart.filename()).thenReturn("");
        Mono<FileUploadResponse> result = fileStorageService.uploadFile(BUCKET_NAME, OBJECT_KEY, mockFilePart);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }


    @Test
    void uploadFile_whenMinioClientThrowsException_shouldThrowMinioOperationException() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new IOException("MinIO network error"));

        Mono<FileUploadResponse> result = fileStorageService.uploadFile(BUCKET_NAME, OBJECT_KEY, mockFilePart);

        StepVerifier.create(result)
            .expectErrorSatisfies(throwable -> {
                assertInstanceOf(MinioOperationException.class, throwable);
                assertTrue(throwable.getMessage().contains("Error uploading file to MinIO"));
            })
            .verify();
    }

    @Test
    void getPublicUrl_whenValidInput_shouldReturnCorrectUrl() {
        Mono<String> result = fileStorageService.getPublicUrl(BUCKET_NAME, OBJECT_KEY);

        StepVerifier.create(result)
            .expectNext(PUBLIC_URL_PREFIX + "/" + BUCKET_NAME + "/" + OBJECT_KEY)
            .verifyComplete();
    }

    @Test
    void getPublicUrl_whenBucketNameIsEmpty_shouldThrowInvalidStorageRequestException() {
        Mono<String> result = fileStorageService.getPublicUrl("", OBJECT_KEY);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
    }

    @Test
    void getPublicUrl_whenObjectKeyIsEmpty_shouldThrowInvalidStorageRequestException() {
        Mono<String> result = fileStorageService.getPublicUrl(BUCKET_NAME, "");

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
    }

    @Test
    void deleteFile_whenValidInput_shouldCompleteSuccessfully() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        Mono<Void> result = fileStorageService.deleteFile(BUCKET_NAME, OBJECT_KEY);

        StepVerifier.create(result)
            .verifyComplete();

        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_whenBucketNameIsEmpty_shouldThrowInvalidStorageRequestException() throws Exception {
        Mono<Void> result = fileStorageService.deleteFile("", OBJECT_KEY);

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_whenObjectKeyIsEmpty_shouldThrowInvalidStorageRequestException() throws Exception {
        Mono<Void> result = fileStorageService.deleteFile(BUCKET_NAME, "");

        StepVerifier.create(result)
            .expectError(InvalidStorageRequestException.class)
            .verify();
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_whenMinioClientThrowsException_shouldThrowMinioOperationException() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        doThrow(new IOException("MinIO network error on delete")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        Mono<Void> result = fileStorageService.deleteFile(BUCKET_NAME, OBJECT_KEY);

        StepVerifier.create(result)
             .expectErrorSatisfies(throwable -> {
                assertInstanceOf(MinioOperationException.class, throwable);
                assertTrue(throwable.getMessage().contains("Error deleting file from MinIO"));
            })
            .verify();
    }
}
