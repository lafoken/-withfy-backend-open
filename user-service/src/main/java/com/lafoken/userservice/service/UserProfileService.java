package com.withfy.userservice.service;

import com.withfy.userservice.client.StorageServiceClient;
import com.withfy.userservice.dto.UpdateUserProfileRequest;
import com.withfy.userservice.dto.UserRegisteredEvent;
import com.withfy.userservice.dto.UserProfileResponse;
import com.withfy.userservice.entity.StripeCustomer;
import com.withfy.userservice.entity.UserProfile;
import com.withfy.userservice.exception.InvalidRequestException;
import com.withfy.userservice.exception.StorageServiceException;
import com.withfy.userservice.exception.UserProfileNotFoundException;
import com.withfy.userservice.repository.StripeCustomerRepository;
import com.withfy.userservice.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final StorageServiceClient storageServiceClient;

    @Value("${storage.service.bucket.images}")
    private String imagesBucketName;

    public UserProfileService(UserProfileRepository userProfileRepository,
                              StripeCustomerRepository stripeCustomerRepository,
                              StorageServiceClient storageServiceClient) {
        this.userProfileRepository = userProfileRepository;
        this.stripeCustomerRepository = stripeCustomerRepository;
        this.storageServiceClient = storageServiceClient;
    }

    @Transactional
    public Mono<Void> handleUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent: {}", event);
        if (event == null || event.userId() == null || event.email() == null) {
            log.error("Received invalid UserRegisteredEvent: {}", event);
            return Mono.error(new InvalidRequestException("UserRegisteredEvent is invalid or missing required fields."));
        }
        UUID userId;
        try {
            userId = UUID.fromString(event.userId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId in UserRegisteredEvent: {}", event.userId());
            return Mono.error(new InvalidRequestException("Invalid userId format in UserRegisteredEvent."));
        }


        UserProfile userProfile = UserProfile.builder()
            .id(userId)
            .email(event.email())
            .fullName(event.fullName())
            .avatarUrl(null)
            .billingAddress(null)
            .paymentMethod(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        StripeCustomer stripeCustomer = StripeCustomer.builder()
            .id(userId)
            .stripeCustomerId(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return userProfileRepository.insertProfile(userProfile)
            .doOnSuccess(v -> log.info("UserProfile inserted for userId: {}", userProfile.getId()))
            .then(stripeCustomerRepository.insertStripeCustomer(stripeCustomer))
            .doOnSuccess(v -> log.info("StripeCustomer inserted for userId: {}", stripeCustomer.getId()))
            .then();
    }

    public Mono<UserProfileResponse> getUserProfile(String userIdString) {
        log.info("Attempting to get profile for userId: {}", userIdString);
        UUID userId;
        try {
            userId = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRequestException("Invalid userId format: " + userIdString));
        }
        return userProfileRepository.findById(userId)
            .map(UserProfileResponse::fromEntity)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("UserProfile not found for ID (getUserProfile): {}", userIdString);
                return Mono.error(new UserProfileNotFoundException("User profile not found for ID: " + userIdString));
            }));
    }

    @Transactional
    public Mono<UserProfileResponse> updateUserProfile(String userIdString, UpdateUserProfileRequest request) {
        log.info("Attempting to update profile for userId: {} with request: {}", userIdString, request);
        UUID userUuid;
         try {
            userUuid = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRequestException("Invalid userId format: " + userIdString));
        }
        LocalDateTime now = LocalDateTime.now();

        if (request.fullName() == null && request.avatarUrl() == null) {
            log.info("No fields to update for userId: {}. Returning current profile.", userIdString);
            return this.getUserProfile(userIdString);
        }

        return userProfileRepository.findById(userUuid)
            .switchIfEmpty(Mono.error(new UserProfileNotFoundException("User profile not found for update for ID: " + userIdString)))
            .flatMap(profile -> {
                String newAvatarFullUrl = request.avatarUrl();
                String oldAvatarFullUrl = profile.getAvatarUrl();
                Mono<Void> deleteOldAvatarMono = Mono.empty();
                String objectKeyForDeletionAttempt = null;

                if (StringUtils.hasText(oldAvatarFullUrl) &&
                    ((StringUtils.hasText(newAvatarFullUrl) && !newAvatarFullUrl.equals(oldAvatarFullUrl)) || !StringUtils.hasText(newAvatarFullUrl))) {
                    String tempKey = oldAvatarFullUrl;
                    if (oldAvatarFullUrl.startsWith("http")) {
                        try {
                            String prefixToRemove = "/" + imagesBucketName + "/";
                            int keyStartIndex = oldAvatarFullUrl.indexOf(prefixToRemove);
                            if (keyStartIndex != -1) {
                                tempKey = oldAvatarFullUrl.substring(keyStartIndex + prefixToRemove.length());
                            } else {
                                log.warn("Could not reliably extract object key from old avatar URL: {}", oldAvatarFullUrl);
                                tempKey = null;
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing old avatar URL to extract object key, skipping deletion: {}", oldAvatarFullUrl, e);
                            tempKey = null;
                        }
                    }
                    if (StringUtils.hasText(tempKey) && !tempKey.startsWith("http")) {
                        objectKeyForDeletionAttempt = tempKey;
                    }
                }

                if (StringUtils.hasText(objectKeyForDeletionAttempt)) {
                    final String finalObjectKeyToDelete = objectKeyForDeletionAttempt;
                    log.info("Attempting to delete old avatar. ObjectKey: {}", finalObjectKeyToDelete);
                    deleteOldAvatarMono = storageServiceClient.deleteFile(imagesBucketName, finalObjectKeyToDelete)
                        .doOnError(e -> log.warn("Failed to delete old avatar {} for user {} from storage. Continuing update. Error: {}", finalObjectKeyToDelete, userIdString, e.getMessage()))
                        .onErrorResume(e -> Mono.empty());
                }

                UpdateUserProfileRequest requestToSave = new UpdateUserProfileRequest(
                    request.fullName(),
                    newAvatarFullUrl
                );

                return deleteOldAvatarMono.then(
                    userProfileRepository.updateProfile(userUuid, requestToSave, now)
                    .flatMap(updatedCount -> {
                        log.info("Rows updated by updateProfile for user {}: {}", userIdString, updatedCount);
                        if (updatedCount > 0) {
                            return userProfileRepository.findById(userUuid);
                        } else {
                            log.warn("Update returned 0 rows for user {}. Profile might not exist or no changes made.", userIdString);
                            return userProfileRepository.findById(userUuid);
                        }
                    })
                );
            })
            .map(UserProfileResponse::fromEntity);
    }

    @Transactional
    public Mono<UserProfileResponse> uploadAvatar(String userIdString, FilePart filePart) {
        if (filePart == null) {
            return Mono.error(new InvalidRequestException("Avatar file part cannot be null."));
        }
        String originalFilename = filePart.filename();
        if (!StringUtils.hasText(originalFilename)) {
            return Mono.error(new InvalidRequestException("Avatar filename cannot be empty."));
        }
        String extension = "";
        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            extension = originalFilename.substring(i);
        }
        String objectKey = "avatars/" + userIdString + "/" + UUID.randomUUID().toString() + extension;

        return storageServiceClient.uploadFile(imagesBucketName, objectKey, filePart)
            .onErrorMap(e -> new StorageServiceException("Failed to upload avatar to storage service for user " + userIdString, e))
            .flatMap(uploadResponse -> {
                log.info("Avatar uploaded to storage service. ObjectKey: {}, Full Public URL from storage service: {}", uploadResponse.objectKey(), uploadResponse.publicUrl());
                UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest(null, uploadResponse.publicUrl());
                return updateUserProfile(userIdString, updateRequest);
            });
    }

    @Transactional
    public Mono<Void> deleteAvatar(String userIdString) {
         UUID userUuid;
         try {
            userUuid = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRequestException("Invalid userId format: " + userIdString));
        }
        return userProfileRepository.findById(userUuid)
            .switchIfEmpty(Mono.error(new UserProfileNotFoundException("User profile not found for avatar deletion: " + userIdString)))
            .flatMap(profile -> {
                String avatarUrlFromDb = profile.getAvatarUrl();
                if (StringUtils.hasText(avatarUrlFromDb)) {
                    String objectKeyToDelete = null;
                    if (avatarUrlFromDb.startsWith("http")) {
                        try {
                            String prefixToRemove = "/" + imagesBucketName + "/";
                            int keyStartIndex = avatarUrlFromDb.indexOf(prefixToRemove);
                            if(keyStartIndex != -1) {
                                objectKeyToDelete = avatarUrlFromDb.substring(keyStartIndex + prefixToRemove.length());
                            } else {
                                log.warn("Cannot extract object key from avatar URL for deletion: {}", avatarUrlFromDb);
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing avatar URL to extract object key for deletion: {}", avatarUrlFromDb, e);
                        }
                    } else {
                        objectKeyToDelete = avatarUrlFromDb;
                    }

                    Mono<Void> deleteFromStorageMono = Mono.empty();
                    if (StringUtils.hasText(objectKeyToDelete)) {
                        final String finalObjectKeyToDelete = objectKeyToDelete;
                        log.info("Attempting to delete avatar from storage for user {}: objectKey {}", userIdString, finalObjectKeyToDelete);
                        deleteFromStorageMono = storageServiceClient.deleteFile(imagesBucketName, finalObjectKeyToDelete)
                            .doOnError(e -> log.warn("Failed to delete avatar {} for user {} from storage. Error: {}", finalObjectKeyToDelete, userIdString, e.getMessage()))
                            .onErrorResume(e -> Mono.empty());
                    } else {
                         log.warn("Could not determine object key for avatar URL: {}. Skipping MinIO delete, only clearing DB.", avatarUrlFromDb);
                    }

                    return deleteFromStorageMono.then(
                        Mono.fromRunnable(() -> {
                            profile.setAvatarUrl(null);
                            profile.setUpdatedAt(LocalDateTime.now());
                        })
                        .then(userProfileRepository.save(profile))
                        .then()
                    );
                }
                log.info("No avatar URL in DB to delete for user {}", userIdString);
                return Mono.empty();
            }).then();
    }

    @Transactional
    public Mono<Void> updateUserStripeCustomerId(String userIdString, String stripeCustomerId) {
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            return Mono.error(new InvalidRequestException("Invalid userId format: " + userIdString));
        }
        if (!StringUtils.hasText(stripeCustomerId)) {
            return Mono.error(new InvalidRequestException("Stripe Customer ID cannot be empty."));
        }

        log.info("Attempting to update Stripe Customer ID for userId {} to {}", userIdString, stripeCustomerId);
        return stripeCustomerRepository.findById(userUuid)
            .flatMap(customer -> {
                customer.setStripeCustomerId(stripeCustomerId);
                customer.setUpdatedAt(LocalDateTime.now());
                return stripeCustomerRepository.save(customer);
            })
            .switchIfEmpty(Mono.defer(() -> {
                log.info("No existing StripeCustomer found for userId {}, creating new one.", userIdString);
                StripeCustomer newCustomer = StripeCustomer.builder()
                    .id(userUuid)
                    .stripeCustomerId(stripeCustomerId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                return stripeCustomerRepository.save(newCustomer);
            }))
            .doOnSuccess(c -> log.info("Successfully updated/created StripeCustomer for userId {} with Stripe ID {}", userIdString, stripeCustomerId))
            .doOnError(e -> log.error("Error updating StripeCustomer for userId {}", userIdString, e))
            .then();
    }

    @Transactional
    public Mono<Void> handleUserBannedEvent(String userIdString) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for userId in UserBannedEvent: {}", userIdString);
            return Mono.error(new InvalidRequestException("Invalid userId format in UserBannedEvent."));
        }
        log.info("Handling UserBannedEvent for userId: {}. Deleting user profile and related data (excluding avatar for now based on previous decision).", userId);

        return stripeCustomerRepository.deleteById(userId)
            .doOnSuccess(v -> log.info("Deleted StripeCustomer for banned user: {}", userId))
            .onErrorResume(e -> {
                log.warn("Error deleting StripeCustomer for banned user {}: {}", userId, e.getMessage());
                return Mono.empty();
            })
            .then(userProfileRepository.deleteById(userId)
                .doOnSuccess(v -> log.info("Deleted UserProfile for banned user: {}", userId))
                .onErrorResume(e -> {
                    log.warn("Error deleting UserProfile for banned user {}: {}", userId, e.getMessage());
                    return Mono.empty();
                }))
            .then();
    }
}
