package com.withfy.storageservice.controller;

import com.withfy.storageservice.dto.FileUploadResponse;
import com.withfy.storageservice.dto.FileUrlResponse;
import com.withfy.storageservice.exception.InvalidStorageRequestException;
import com.withfy.storageservice.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FileUploadResponse> uploadFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectKey") String objectKey,
            @RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono
                .switchIfEmpty(Mono.error(new InvalidStorageRequestException("File part 'file' is missing.")))
                .flatMap(filePart -> fileStorageService.uploadFile(bucketName, objectKey, filePart));
    }

    @GetMapping("/url")
    public Mono<FileUrlResponse> getFileUrl(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectKey") String objectKey) {
        return fileStorageService.getPublicUrl(bucketName, objectKey)
                .map(FileUrlResponse::new);
    }

    @DeleteMapping("/object")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteFile(
            @RequestParam("bucketName") String bucketName,
            @RequestParam("objectKey") String objectKey) {
        return fileStorageService.deleteFile(bucketName, objectKey);
    }
}
