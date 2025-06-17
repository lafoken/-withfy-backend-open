package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
import com.lafoken.identity.dto.ForgotPasswordRequest;
import com.lafoken.identity.dto.ResetPasswordRequest;
import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.PasswordResetToken;
import com.lafoken.identity.exception.PasswordResetTokenInvalidException;
import com.lafoken.identity.exception.UserNotFoundException;
import com.lafoken.identity.repository.AppUserRepository;
import com.lafoken.identity.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private JwtProperties jwtProperties;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private AppUser sampleUser;
    private PasswordResetToken sampleTokenEntity;
    private String validTokenValue = "valid-token";
    private final String FRONTEND_URL = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        sampleUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Test User")
                .hashedPassword("oldHashedPassword")
                .build();

        sampleTokenEntity = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(sampleUser.getId())
                .token(validTokenValue)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .build();

        lenient().when(jwtProperties.passwordResetTokenExpirationMs()).thenReturn(3600000L);
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", FRONTEND_URL);
    }

    @Test
    void initiatePasswordReset_whenUserExists_shouldSaveTokenAndSendEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(sampleUser.getEmail());
        when(appUserRepository.findByEmail(sampleUser.getEmail())).thenReturn(Mono.just(sampleUser));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(Mono.just(sampleTokenEntity));
        when(emailService.sendPasswordResetEmail(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

        Mono<Void> result = passwordResetService.initiatePasswordReset(request);

        StepVerifier.create(result).verifyComplete();
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(sampleUser.getEmail()), anyString(), eq(sampleUser.getFullName()));
    }

    @Test
    void initiatePasswordReset_whenUserDoesNotExist_shouldCompleteSilently() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");
        when(appUserRepository.findByEmail("unknown@example.com")).thenReturn(Mono.empty());

        Mono<Void> result = passwordResetService.initiatePasswordReset(request);

        StepVerifier.create(result).verifyComplete();
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_withValidToken_shouldResetPasswordAndDeleteToken() {
        ResetPasswordRequest request = new ResetPasswordRequest(validTokenValue, "newPassword123");
        when(passwordResetTokenRepository.findByToken(validTokenValue)).thenReturn(Mono.just(sampleTokenEntity));
        when(appUserRepository.findById(sampleTokenEntity.getUserId())).thenReturn(Mono.just(sampleUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newHashedPassword");

        AppUser updatedUser = AppUser.builder().id(sampleUser.getId()).hashedPassword("newHashedPassword").build();
        when(appUserRepository.save(any(AppUser.class))).thenReturn(Mono.just(updatedUser));
        when(passwordResetTokenRepository.delete(sampleTokenEntity)).thenReturn(Mono.empty());

        Mono<Void> result = passwordResetService.resetPassword(request);

        StepVerifier.create(result).verifyComplete();
        verify(appUserRepository).save(argThat(user -> "newHashedPassword".equals(user.getHashedPassword())));
        verify(passwordResetTokenRepository).delete(sampleTokenEntity);
    }

    @Test
    void resetPassword_withInvalidToken_shouldThrowException() {
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "newPassword123");
        when(passwordResetTokenRepository.findByToken("invalid-token")).thenReturn(Mono.empty());

        Mono<Void> result = passwordResetService.resetPassword(request);

        StepVerifier.create(result)
                .expectError(PasswordResetTokenInvalidException.class)
                .verify();
    }

    @Test
    void resetPassword_withExpiredToken_shouldThrowExceptionAndDeleteToken() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
            .id(UUID.randomUUID()).userId(sampleUser.getId()).token("expired-token")
            .expiryDate(LocalDateTime.now().minusHours(1)).build();
        ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "newPassword123");

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Mono.just(expiredToken));
        when(passwordResetTokenRepository.delete(expiredToken)).thenReturn(Mono.empty());


        Mono<Void> result = passwordResetService.resetPassword(request);

        StepVerifier.create(result)
                .expectError(PasswordResetTokenInvalidException.class)
                .verify();
        verify(passwordResetTokenRepository).delete(expiredToken);
    }

    @Test
    void resetPassword_whenUserForTokenNotFound_shouldThrowException() {
        ResetPasswordRequest request = new ResetPasswordRequest(validTokenValue, "newPassword123");
        when(passwordResetTokenRepository.findByToken(validTokenValue)).thenReturn(Mono.just(sampleTokenEntity));
        when(appUserRepository.findById(sampleTokenEntity.getUserId())).thenReturn(Mono.empty());

        Mono<Void> result = passwordResetService.resetPassword(request);

        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();
    }
}
