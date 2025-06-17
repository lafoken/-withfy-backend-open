 
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private StripeCustomerRepository stripeCustomerRepository;

    @Mock
    private StorageServiceClient storageServiceClient;

    @InjectMocks
    private UserProfileService userProfileService;

    private UUID testUserId;
    private String testUserEmail;
    private String testUserFullName;
    private UserRegisteredEvent userRegisteredEvent;
    private UserProfile userProfile;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserEmail = "test@example.com";
        testUserFullName = "Test User";

        userRegisteredEvent = new UserRegisteredEvent(testUserId.toString(), testUserEmail, testUserFullName, "LOCAL");
        userProfile = UserProfile.builder()
            .id(testUserId)
            .email(testUserEmail)
            .fullName(testUserFullName)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        ReflectionTestUtils.setField(userProfileService, "imagesBucketName", "test-images-bucket");
        lenient().when(userProfileRepository.insertProfile(any(UserProfile.class))).thenReturn(Mono.empty());
        lenient().when(stripeCustomerRepository.insertStripeCustomer(any(StripeCustomer.class))).thenReturn(Mono.empty());
    }

    @Test
    void handleUserRegisteredEvent_whenValidEvent_shouldSaveProfileAndStripeCustomer() {
        Mono<Void> result = userProfileService.handleUserRegisteredEvent(userRegisteredEvent);

        StepVerifier.create(result)
            .verifyComplete();

        verify(userProfileRepository, times(1)).insertProfile(argThat(profile ->
            profile.getId().equals(testUserId) && profile.getEmail().equals(testUserEmail)
        ));
        verify(stripeCustomerRepository, times(1)).insertStripeCustomer(argThat(customer ->
            customer.getId().equals(testUserId)
        ));
    }

    @Test
    void handleUserRegisteredEvent_whenEventIsNull_shouldThrowInvalidRequestException() {
        Mono<Void> result = userProfileService.handleUserRegisteredEvent(null);

        StepVerifier.create(result)
            .expectError(InvalidRequestException.class)
            .verify();
    }

    @Test
    void handleUserRegisteredEvent_whenUserIdIsInvalid_shouldThrowInvalidRequestException() {
        UserRegisteredEvent invalidEvent = new UserRegisteredEvent("invalid-uuid", testUserEmail, testUserFullName, "LOCAL");
        Mono<Void> result = userProfileService.handleUserRegisteredEvent(invalidEvent);

        StepVerifier.create(result)
            .expectError(InvalidRequestException.class)
            .verify();
    }

    @Test
    void getUserProfile_whenUserExists_shouldReturnProfile() {
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));

        Mono<UserProfileResponse> result = userProfileService.getUserProfile(testUserId.toString());

        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.id().equals(testUserId.toString()) &&
                response.email().equals(testUserEmail) &&
                response.fullName().equals(testUserFullName)
            )
            .verifyComplete();
    }

    @Test
    void getUserProfile_whenUserNotFound_shouldThrowUserProfileNotFoundException() {
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.empty());

        Mono<UserProfileResponse> result = userProfileService.getUserProfile(testUserId.toString());

        StepVerifier.create(result)
            .expectError(UserProfileNotFoundException.class)
            .verify();
    }

    @Test
    void getUserProfile_whenInvalidUserIdFormat_shouldThrowInvalidRequestException() {
        Mono<UserProfileResponse> result = userProfileService.getUserProfile("invalid-uuid-format");

        StepVerifier.create(result)
            .expectError(InvalidRequestException.class)
            .verify();
    }

    @Test
    void updateUserProfile_whenUserExistsAndRequestIsValid_shouldUpdateAndReturnProfile() {
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest("Updated Name", "http://new.avatar.url/image.png");
        UserProfile updatedProfile = UserProfile.builder()
            .id(testUserId).email(testUserEmail).fullName("Updated Name").avatarUrl("http://new.avatar.url/image.png")
            .createdAt(userProfile.getCreatedAt()).updatedAt(LocalDateTime.now()).build();

        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));
        when(userProfileRepository.updateProfile(eq(testUserId), any(UpdateUserProfileRequest.class), any(LocalDateTime.class)))
            .thenReturn(Mono.just(1));
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(updatedProfile));

        Mono<UserProfileResponse> result = userProfileService.updateUserProfile(testUserId.toString(), updateRequest);

        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.fullName().equals("Updated Name") &&
                response.avatarUrl().equals("http://new.avatar.url/image.png")
            )
            .verifyComplete();

        verify(userProfileRepository, times(1)).updateProfile(eq(testUserId), any(UpdateUserProfileRequest.class), any(LocalDateTime.class));
    }

    @Test
    void updateUserProfile_whenOnlyFullNameProvided_shouldUpdateFullName() {
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest("Updated FullName Only", null);
        UserProfile profileAfterUpdate = UserProfile.builder()
            .id(testUserId).email(testUserEmail).fullName("Updated FullName Only").avatarUrl(userProfile.getAvatarUrl())
            .createdAt(userProfile.getCreatedAt()).updatedAt(LocalDateTime.now()).build();

        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));
        when(userProfileRepository.updateProfile(eq(testUserId), any(UpdateUserProfileRequest.class), any(LocalDateTime.class)))
            .thenReturn(Mono.just(1));
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(profileAfterUpdate));


        Mono<UserProfileResponse> result = userProfileService.updateUserProfile(testUserId.toString(), updateRequest);

        StepVerifier.create(result)
            .expectNextMatches(response ->
                response.fullName().equals("Updated FullName Only") &&
                response.avatarUrl() == userProfile.getAvatarUrl()
            )
            .verifyComplete();

        verify(userProfileRepository).updateProfile(eq(testUserId), argThat(req -> "Updated FullName Only".equals(req.fullName()) && req.avatarUrl() == null), any(LocalDateTime.class));

    }

    @Test
    void updateUserProfile_whenUserNotFound_shouldThrowUserProfileNotFoundException() {
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest("New Name", null);
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.empty());

        Mono<UserProfileResponse> result = userProfileService.updateUserProfile(testUserId.toString(), updateRequest);

        StepVerifier.create(result)
            .expectError(UserProfileNotFoundException.class)
            .verify();
    }

    @Test
    void uploadAvatar_whenValidFile_shouldUploadAndUpdateProfile() {
        FilePart mockFilePart = mock(FilePart.class, withSettings().strictness(Strictness.LENIENT));
        when(mockFilePart.filename()).thenReturn("avatar.png");
        String newAvatarUrl = "http://localhost:9000/test-images-bucket/avatars/" + testUserId + "/new-avatar.png";
        StorageServiceClient.FileUploadResponse uploadResponse = new StorageServiceClient.FileUploadResponse(
            "avatars/" + testUserId + "/new-avatar.png",
            "test-images-bucket",
            newAvatarUrl
        );
        UserProfile profileWithNewAvatar = UserProfile.builder()
            .id(testUserId).email(testUserEmail).fullName(testUserFullName).avatarUrl(newAvatarUrl).build();

        when(storageServiceClient.uploadFile(eq("test-images-bucket"), anyString(), eq(mockFilePart)))
            .thenReturn(Mono.just(uploadResponse));

        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));
        when(userProfileRepository.updateProfile(eq(testUserId), argThat(req -> newAvatarUrl.equals(req.avatarUrl())), any(LocalDateTime.class)))
            .thenReturn(Mono.just(1));
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(profileWithNewAvatar));


        Mono<UserProfileResponse> result = userProfileService.uploadAvatar(testUserId.toString(), mockFilePart);

        StepVerifier.create(result)
            .expectNextMatches(response -> response.avatarUrl().equals(newAvatarUrl))
            .verifyComplete();

        verify(storageServiceClient, times(1)).uploadFile(eq("test-images-bucket"), anyString(), eq(mockFilePart));
    }

    @Test
    void uploadAvatar_whenStorageServiceFails_shouldThrowStorageServiceException() {
        FilePart mockFilePart = mock(FilePart.class, withSettings().strictness(Strictness.LENIENT));
        when(mockFilePart.filename()).thenReturn("avatar.png");
        when(storageServiceClient.uploadFile(anyString(), anyString(), any(FilePart.class)))
            .thenReturn(Mono.error(new RuntimeException("MinIO error")));

        Mono<UserProfileResponse> result = userProfileService.uploadAvatar(testUserId.toString(), mockFilePart);

        StepVerifier.create(result)
            .expectError(StorageServiceException.class)
            .verify();
    }

    @Test
    void deleteAvatar_whenUserHasAvatar_shouldDeleteFromStorageAndUpdateProfile() {
        userProfile.setAvatarUrl("http://localhost:9000/test-images-bucket/avatars/" + testUserId + "/old-avatar.png");
        UserProfile profileToBeSavedWithNullAvatar = UserProfile.builder()
            .id(testUserId)
            .email(userProfile.getEmail())
            .fullName(userProfile.getFullName())
            .avatarUrl(null)
            .billingAddress(userProfile.getBillingAddress())
            .paymentMethod(userProfile.getPaymentMethod())
            .createdAt(userProfile.getCreatedAt())
            .updatedAt(userProfile.getUpdatedAt())
            .build();

        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));
        when(storageServiceClient.deleteFile(eq("test-images-bucket"), startsWith("avatars/" + testUserId + "/"))).thenReturn(Mono.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(Mono.just(profileToBeSavedWithNullAvatar));

        Mono<Void> result = userProfileService.deleteAvatar(testUserId.toString());

        StepVerifier.create(result).verifyComplete();

        verify(storageServiceClient, times(1)).deleteFile(eq("test-images-bucket"), startsWith("avatars/" + testUserId + "/"));
        verify(userProfileRepository, times(1)).save(argThat(savedProfile ->
            savedProfile.getId().equals(testUserId) && savedProfile.getAvatarUrl() == null
        ));
    }

    @Test
    void deleteAvatar_whenUserHasNoAvatar_shouldCompleteWithoutStorageCall() {
        userProfile.setAvatarUrl(null);
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.just(userProfile));

        Mono<Void> result = userProfileService.deleteAvatar(testUserId.toString());

        StepVerifier.create(result).verifyComplete();

        verify(storageServiceClient, never()).deleteFile(anyString(), anyString());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void deleteAvatar_whenUserNotFound_shouldThrowUserProfileNotFoundException() {
        when(userProfileRepository.findById(testUserId)).thenReturn(Mono.empty());

        Mono<Void> result = userProfileService.deleteAvatar(testUserId.toString());

        StepVerifier.create(result)
            .expectError(UserProfileNotFoundException.class)
            .verify();
    }


    @Test
    void updateUserStripeCustomerId_whenCustomerExists_shouldUpdateExisting() {
        String stripeId = "cus_test123";
        StripeCustomer existingCustomer = StripeCustomer.builder().id(testUserId).stripeCustomerId("cus_old").build();
        when(stripeCustomerRepository.findById(testUserId)).thenReturn(Mono.just(existingCustomer));
        when(stripeCustomerRepository.save(any(StripeCustomer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<Void> result = userProfileService.updateUserStripeCustomerId(testUserId.toString(), stripeId);

        StepVerifier.create(result).verifyComplete();
        verify(stripeCustomerRepository).save(argThat(c -> c.getStripeCustomerId().equals(stripeId) && c.getId().equals(testUserId)));
    }

    @Test
    void updateUserStripeCustomerId_whenCustomerDoesNotExist_shouldCreateNew() {
        String stripeId = "cus_test456";
        when(stripeCustomerRepository.findById(testUserId)).thenReturn(Mono.empty());
        when(stripeCustomerRepository.save(any(StripeCustomer.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Mono<Void> result = userProfileService.updateUserStripeCustomerId(testUserId.toString(), stripeId);

        StepVerifier.create(result).verifyComplete();
        verify(stripeCustomerRepository).save(argThat(c -> c.getStripeCustomerId().equals(stripeId) && c.getId().equals(testUserId)));
    }
}
