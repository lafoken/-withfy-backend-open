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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtProperties jwtProperties;

    @Value("${frontend.url}")
    private String frontendUrl;

    public PasswordResetService(AppUserRepository appUserRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                JwtProperties jwtProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public Mono<Void> initiatePasswordReset(ForgotPasswordRequest request) {
        return appUserRepository.findByEmail(request.email())
            .flatMap(user -> {
                String tokenValue = UUID.randomUUID().toString();
                PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(tokenValue)
                    .expiryDate(LocalDateTime.now().plus(jwtProperties.passwordResetTokenExpirationMs(), ChronoUnit.MILLIS))
                    .createdAt(LocalDateTime.now())
                    .build();

                return passwordResetTokenRepository.save(resetToken)
                     .then(emailService.sendPasswordResetEmail(user.getEmail(), tokenValue, user.getFullName() != null ? user.getFullName() : user.getEmail()));
            })
            .switchIfEmpty(Mono.defer(() -> {
                return Mono.empty();
            }))
            .then();
    }

    @Transactional
    public Mono<Void> resetPassword(ResetPasswordRequest request) {
        return passwordResetTokenRepository.findByToken(request.token())
            .switchIfEmpty(Mono.error(new PasswordResetTokenInvalidException("Invalid or expired password reset token.")))
            .flatMap(token -> {
                if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
                    return passwordResetTokenRepository.delete(token)
                        .then(Mono.error(new PasswordResetTokenInvalidException("Password reset token has expired.")));
                }
                return appUserRepository.findById(token.getUserId())
                    .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for this token.")))
                    .flatMap(user -> {
                        user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
                        user.setUpdatedAt(LocalDateTime.now());
                        return appUserRepository.save(user)
                            .then(passwordResetTokenRepository.delete(token));
                    });
            })
            .then();
    }
}
