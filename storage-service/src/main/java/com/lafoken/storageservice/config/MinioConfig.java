package com.withfy.storageservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.url())
                .credentials(minioProperties.accessKey(), minioProperties.secretKey())
                .build();
        try {
            createBucketIfNotExists(client, minioProperties.bucket().images(), true);
            createBucketIfNotExists(client, minioProperties.bucket().songs(), true);
        } catch (Exception e) {
            log.error("Error initializing MinIO buckets", e);
            throw new RuntimeException("Could not initialize MinIO buckets", e);
        }
        return client;
    }

    private void createBucketIfNotExists(MinioClient client, String bucketName, boolean makePublicReadable) throws Exception {
        boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket '{}' created successfully.", bucketName);
        } else {
            log.info("Bucket '{}' already exists.", bucketName);
        }

        if (makePublicReadable) {
            setPublicReadPolicy(client, bucketName);
        }
    }

    private void setPublicReadPolicy(MinioClient client, String bucketName) throws Exception {
        String policyJson = """
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                }
            ]
        }
        """.formatted(bucketName);

        client.setBucketPolicy(
            SetBucketPolicyArgs.builder()
                .bucket(bucketName)
                .config(policyJson)
                .build()
        );
        log.info("Public read policy set for bucket '{}'", bucketName);
    }
}

