package com.lafoken.identity.service;

import com.lafoken.identity.config.JwtProperties;
import com.lafoken.identity.dto.*;
import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.entity.RefreshToken;
import com.lafoken.identity.event.UserRegisteredEvent;
import com.lafoken.identity.exception.EmailAlreadyExistsException;
import com.lafoken.identity.exception.InvalidCredentialsException;
import com.lafoken.identity.exception.TokenRefreshException;
import com.lafoken.identity.exception.UserNotFoundException;
import com.lafoken.identity.repository.AppUserRepository;
import com.lafoken.identity.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lafoken.identity.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveAuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final EventProducerService eventProducerService;
    private final WebClient.Builder webClientBuilder;
    private final String userServiceUrl;

    public AuthService(AppUserRepository appUserRepository,
                       PasswordEncoder passwordEncoder,
                       ReactiveAuthenticationManager userDetailsAuthenticationManager,
                       TokenProvider tokenProvider,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtProperties jwtProperties,
                       EventProducerService eventProducerService,
                       WebClient.Builder webClientBuilder,
                       @Value("${user-service.url}") String userServiceUrl
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = userDetailsAuthenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.eventProducerService = eventProducerService;
        this.webClientBuilder = webClientBuilder;
        this.userServiceUrl = userServiceUrl;
    }

    @Transactional
    public Mono<UserRegistrationResponse> registerUser(UserRegistrationRequest registrationRequest) {
        return appUserRepository.existsByEmail(registrationRequest.email())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new EmailAlreadyExistsException("Email '" + registrationRequest.email() + "' is already taken."));
                }
                AppUser newUser = AppUser.builder()
                    .email(registrationRequest.email())
                    .hashedPassword(passwordEncoder.encode(registrationRequest.password()))
                    .fullName(registrationRequest.fullName())
                    .isActive(true)
                    .isEmailVerified(true)
                    .authProvider(AuthProvider.LOCAL)
                    .roles("ROLE_USER")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
                return appUserRepository.save(newUser)
                    .doOnSuccess(savedUser -> {
                        UserRegisteredEvent event = new UserRegisteredEvent(
                            savedUser.getId().toString(),
                            savedUser.getEmail(),
                            savedUser.getFullName(),
                            savedUser.getAuthProvider().toString()
                        );
                        eventProducerService.sendUserRegisteredEvent(event);
                    })
                    .map(savedUser -> new UserRegistrationResponse(
                        savedUser.getId().toString(),
                        savedUser.getEmail(),
                        savedUser.getFullName(),
                        "User registered successfully."
                    ));
            });
    }

    public Mono<AuthResponse> loginUser(LoginRequest loginRequest) {
        Authentication authenticationToken =
            new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password());

        return authenticationManager.authenticate(authenticationToken)
            .flatMap(authentication -> {
                org.springframework.security.core.userdetails.UserDetails userDetails =
                    (org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal();

                log.info("AuthService.loginUser: Authenticated successfully. Principal: {}, Authorities from Authentication object: {}",
                    userDetails.getUsername(), authentication.getAuthorities());

                return appUserRepository.findByEmail(userDetails.getUsername())
                    .switchIfEmpty(Mono.error(new UserNotFoundException("User details not found after authentication for email: " + userDetails.getUsername())))
                    .flatMap(appUser -> {
                         if (appUser.getAuthProvider() != AuthProvider.LOCAL) {
                            return Mono.error(new InvalidCredentialsException("Please login using your " + appUser.getAuthProvider().toString() + " account."));
                        }
                         if (!appUser.isActive()) {
                             return Mono.error(new InvalidCredentialsException("User account is inactive."));
                         }
                        String accessToken = tokenProvider.createAccessToken(appUser.getEmail(), appUser.getId().toString(), authentication.getAuthorities());
                        return createAndSaveRefreshToken(appUser.getId())
                            .map(refreshToken -> new AuthResponse(
                                accessToken,
                                refreshToken.getToken(),
                                "Bearer",
                                jwtProperties.accessTokenExpirationMs() / 1000,
                                appUser.getId().toString()
                            ));
                    });
            })
            .onErrorMap(ex -> !(ex instanceof AppException), ex -> new InvalidCredentialsException("Invalid email or password."))
            .doOnError(InvalidCredentialsException.class, e -> log.warn("Login failed for email {}: {}", loginRequest.email(), e.getMessage()))
            .doOnError(UserNotFoundException.class, e -> log.warn("Login failed for email {}: {}", loginRequest.email(), e.getMessage()))
            .doOnError(error -> {
                if (!(error instanceof AppException)) {
                    log.error("Unexpected error during login for email {}: ", loginRequest.email(), error);
                }
            });
    }

    private Mono<RefreshToken> createAndSaveRefreshToken(UUID userId) {
        return refreshTokenRepository.deleteByUserId(userId)
            .then(Mono.defer(() -> {
                RefreshToken refreshToken = RefreshToken.builder()
                    .userId(userId)
                    .token(UUID.randomUUID().toString())
                    .expiryDate(LocalDateTime.now().plus(jwtProperties.refreshTokenExpirationMs(), ChronoUnit.MILLIS))
                    .createdAt(LocalDateTime.now())
                    .build();
                return refreshTokenRepository.save(refreshToken);
            }));
    }

    public Mono<AuthResponse> refreshToken(String oldRefreshToken) {
        return refreshTokenRepository.findByToken(oldRefreshToken)
            .switchIfEmpty(Mono.error(new TokenRefreshException("Invalid or non-existent refresh token.")))
            .flatMap(refreshToken -> {
                if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                    return refreshTokenRepository.delete(refreshToken)
                        .then(Mono.error(new TokenRefreshException("Refresh token expired.")));
                }
                return appUserRepository.findById(refreshToken.getUserId())
                    .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for refresh token.")))
                     .flatMap(appUser -> {
                        if (!appUser.isActive()) {
                            return Mono.error(new TokenRefreshException("User account is inactive."));
                        }
                        String rolesString = appUser.getRoles();
                        List<GrantedAuthority> authorities;
                        if (rolesString != null && !rolesString.isBlank()) {
                            authorities = Arrays.stream(rolesString.split(","))
                                .map(String::trim)
                                .filter(role -> !role.isEmpty())
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());
                        } else {
                            authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                        }
                        log.info("AuthService.refreshToken: User {} has authorities for new access token: {}", appUser.getEmail(), authorities);
                        String newAccessToken = tokenProvider.createAccessToken(appUser.getEmail(), appUser.getId().toString(), authorities);

                        return refreshTokenRepository.delete(refreshToken)
                            .then(createAndSaveRefreshToken(appUser.getId()))
                            .map(newRt -> new AuthResponse(
                                newAccessToken,
                                newRt.getToken(),
                                "Bearer",
                                jwtProperties.accessTokenExpirationMs() / 1000,
                                appUser.getId().toString()
                            ));
                    });
            });
    }

    public Mono<Void> logoutUser(String refreshTokenValue) {
        return refreshTokenRepository.findByToken(refreshTokenValue)
            .flatMap(refreshTokenRepository::delete)
            .then();
    }

     public Mono<CurrentUserResponse> getCurrentUser(String email) {
        return appUserRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with email: " + email)))
            .flatMap(appUser -> {
                WebClient client = webClientBuilder.baseUrl(this.userServiceUrl).build();
                String rolesForHeader = appUser.getRoles() != null ? appUser.getRoles() : "ROLE_USER";
                return client.get()
                    .uri("/api/v1/user/profile/me")
                    .header("X-User-ID", appUser.getId().toString())
                    .header("X-User-Email", appUser.getEmail())
                    .header("X-User-Roles", rolesForHeader)
                    .retrieve()
                    .bodyToMono(UserProfileServiceResponse.class)
                    .map(userProfileDetails -> {
                        List<String> rolesList = Arrays.stream(rolesForHeader.split(","))
                                .map(String::trim)
                                .filter(role -> !role.isEmpty())
                                .collect(Collectors.toList());
                        return new CurrentUserResponse(
                            appUser.getId().toString(),
                            appUser.getEmail(),
                            userProfileDetails.fullName() != null ? userProfileDetails.fullName() : appUser.getFullName(),
                            rolesList,
                            appUser.getAuthProvider() != null ? appUser.getAuthProvider().toString() : AuthProvider.LOCAL.toString()
                        );
                    })
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch profile details from user-service for user {}: {}. Using data from identity-service.", email, e.getMessage());
                        List<String> rolesList = Arrays.stream(rolesForHeader.split(","))
                            .map(String::trim)
                            .filter(role -> !role.isEmpty())
                            .collect(Collectors.toList());
                        return Mono.just(new CurrentUserResponse(
                            appUser.getId().toString(),
                            appUser.getEmail(),
                            appUser.getFullName(),
                            rolesList,
                            appUser.getAuthProvider() != null ? appUser.getAuthProvider().toString() : AuthProvider.LOCAL.toString()
                        ));
                    });
            });
    }
}
