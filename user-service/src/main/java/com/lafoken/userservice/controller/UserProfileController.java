package com.withfy.userservice.controller;

import com.withfy.userservice.dto.AvatarUploadResponse;
import com.withfy.userservice.dto.StripeCustomerUpdateRequest;
import com.withfy.userservice.dto.UpdateUserProfileRequest;
import com.withfy.userservice.dto.UserProfileResponse;
import com.withfy.userservice.exception.InvalidRequestException;
import com.withfy.userservice.service.UserProfileService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/user")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);
    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/profile/me")
    public Mono<UserProfileResponse> getCurrentUserProfile(@RequestHeader("X-User-ID") String userId) {
         if (userId == null || userId.isBlank()) {
             throw new InvalidRequestException("X-User-ID header is missing or blank.");
        }
        return userProfileService.getUserProfile(userId);
    }

    @PatchMapping("/profile/me")
    public Mono<UserProfileResponse> updateUserProfile(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidRequestException("X-User-ID header is missing or blank.");
        }
        return userProfileService.updateUserProfile(userId, request);
    }

    @PostMapping(value = "/profile/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AvatarUploadResponse> uploadAvatar(
            @RequestHeader("X-User-ID") String userId,
            @RequestPart("avatarFile") Mono<FilePart> filePartMono) {
        if (userId == null || userId.isBlank()) {
             throw new InvalidRequestException("X-User-ID header is missing or blank.");
        }
        return filePartMono
            .switchIfEmpty(Mono.error(new InvalidRequestException("Avatar file (FilePart) is required.")))
            .flatMap(filePart -> userProfileService.uploadAvatar(userId, filePart))
            .map(userProfile -> new AvatarUploadResponse(userProfile.avatarUrl()));
    }

    @DeleteMapping("/profile/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteAvatar(@RequestHeader("X-User-ID") String userId) {
         if (userId == null || userId.isBlank()) {
            throw new InvalidRequestException("X-User-ID header is missing or blank.");
        }
        return userProfileService.deleteAvatar(userId);
    }

    @PatchMapping("/internal/stripe-customer/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> updateUserStripeCustomerId(
            @PathVariable String userId,
            @Valid @RequestBody StripeCustomerUpdateRequest request,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String internalToken) {

        log.info("Received internal request to update stripe_customer_id for userId {}: {}", userId, request.stripeCustomerId());

        if (userId == null || userId.isBlank() || request.stripeCustomerId() == null || request.stripeCustomerId().isBlank()) {
            log.warn("Missing userId or stripeCustomerId in request to update stripe customer ID. UserID: {}, StripeID: {}", userId, request.stripeCustomerId());
             throw new InvalidRequestException("UserId and StripeCustomerId are required.");
        }
        return userProfileService.updateUserStripeCustomerId(userId, request.stripeCustomerId());
    }
}
