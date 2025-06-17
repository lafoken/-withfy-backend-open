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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ReactiveAuthenticationManager authenticationManager;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private EventProducerService eventProducerService;

    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient.Builder webClientBuilder;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient webClient;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock(strictness = org.mockito.Mock.Strictness.LENIENT)
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private AuthService authService;

    private UserRegistrationRequest registrationRequest;
    private LoginRequest loginRequest;
    private AppUser sampleUser;
    private AppUser sampleOAuthUser;
    private AppUser sampleInactiveUser;
    private RefreshToken sampleRefreshTokenEntity;
    private String sampleJwtToken;
    private String sampleOldRefreshTokenValue;
    private final String USER_SERVICE_URL = "http://fake-user-service";


    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest("test@example.com", "password123", "Test User");
        loginRequest = new LoginRequest("test@example.com", "password123");

        sampleUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .hashedPassword("hashedPassword")
                .fullName("Test User")
                .isActive(true)
                .isEmailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .roles("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleOAuthUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("oauth@example.com")
                .fullName("OAuth User")
                .isActive(true)
                .isEmailVerified(true)
                .authProvider(AuthProvider.GOOGLE)
                .roles("ROLE_USER")
                 .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleInactiveUser = AppUser.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .hashedPassword("hashedPassword")
                .fullName("Inactive User")
                .isActive(false)
                .isEmailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .roles("ROLE_USER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleJwtToken = "sample.jwt.token";
        sampleOldRefreshTokenValue = "old-refresh-token";
        sampleRefreshTokenEntity = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(sampleUser.getId())
                .token(sampleOldRefreshTokenValue)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        lenient().when(jwtProperties.accessTokenExpirationMs()).thenReturn(3600000L);
        lenient().when(jwtProperties.refreshTokenExpirationMs()).thenReturn(604800000L);

        lenient().when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        authService = new AuthService(appUserRepository, passwordEncoder, authenticationManager, tokenProvider, refreshTokenRepository, jwtProperties, eventProducerService, webClientBuilder, USER_SERVICE_URL);
    }

    @Test
    void registerUser_whenEmailDoesNotExist_shouldSaveUserAndReturnResponse() {
        when(appUserRepository.existsByEmail(registrationRequest.email())).thenReturn(Mono.just(false));
        when(passwordEncoder.encode(registrationRequest.password())).thenReturn("encodedPassword");
        AppUser savedUser = AppUser.builder().id(UUID.randomUUID()).email(registrationRequest.email()).fullName(registrationRequest.fullName()).authProvider(AuthProvider.LOCAL).roles("ROLE_USER").build();
        when(appUserRepository.save(any(AppUser.class))).thenReturn(Mono.just(savedUser));
        doNothing().when(eventProducerService).sendUserRegisteredEvent(any(UserRegisteredEvent.class));

        Mono<UserRegistrationResponse> result = authService.registerUser(registrationRequest);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.email().equals(registrationRequest.email()) &&
                        response.message().equals("User registered successfully."))
                .verifyComplete();
        verify(appUserRepository, times(1)).save(any(AppUser.class));
        verify(eventProducerService, times(1)).sendUserRegisteredEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void registerUser_whenEmailExists_shouldThrowEmailAlreadyExistsException() {
        when(appUserRepository.existsByEmail(registrationRequest.email())).thenReturn(Mono.just(true));

        Mono<UserRegistrationResponse> result = authService.registerUser(registrationRequest);

        StepVerifier.create(result)
                .expectError(EmailAlreadyExistsException.class)
                .verify();
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void loginUser_withValidCredentials_shouldReturnAuthResponse() {
        org.springframework.security.core.userdetails.User userDetails =
            new org.springframework.security.core.userdetails.User(sampleUser.getEmail(), sampleUser.getHashedPassword(), Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication successfulAuth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(Mono.just(successfulAuth));
        when(appUserRepository.findByEmail(sampleUser.getEmail())).thenReturn(Mono.just(sampleUser));
        when(tokenProvider.createAccessToken(eq(sampleUser.getEmail()), eq(sampleUser.getId().toString()), any())).thenReturn(sampleJwtToken);

        RefreshToken newRefreshToken = RefreshToken.builder().id(UUID.randomUUID()).token("new-refresh-token").userId(sampleUser.getId()).expiryDate(LocalDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.deleteByUserId(sampleUser.getId())).thenReturn(Mono.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(Mono.just(newRefreshToken));

        Mono<AuthResponse> result = authService.loginUser(loginRequest);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.accessToken().equals(sampleJwtToken) &&
                                               response.refreshToken().equals("new-refresh-token") &&
                                               response.userId().equals(sampleUser.getId().toString()))
                .verifyComplete();
    }

    @Test
    void loginUser_withInvalidPassword_shouldThrowInvalidCredentialsException() {
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(Mono.error(new org.springframework.security.core.AuthenticationException("Bad credentials"){}));

        Mono<AuthResponse> result = authService.loginUser(loginRequest);

        StepVerifier.create(result)
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void loginUser_forNonExistentUser_shouldThrowInvalidCredentialsException() {
         when(authenticationManager.authenticate(any(Authentication.class)))
            .thenReturn(Mono.error(new org.springframework.security.core.AuthenticationException("User not found") {}));

        Mono<AuthResponse> result = authService.loginUser(new LoginRequest("nonexistent@example.com", "password"));
        StepVerifier.create(result)
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void loginUser_forInactiveUser_shouldThrowInvalidCredentialsException() {
        org.springframework.security.core.userdetails.User userDetails =
            new org.springframework.security.core.userdetails.User(sampleInactiveUser.getEmail(), sampleInactiveUser.getHashedPassword(), Collections.emptyList());
        Authentication successfulAuth = new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(Mono.just(successfulAuth));
        when(appUserRepository.findByEmail(sampleInactiveUser.getEmail())).thenReturn(Mono.just(sampleInactiveUser));

        Mono<AuthResponse> result = authService.loginUser(new LoginRequest(sampleInactiveUser.getEmail(), "password"));

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof InvalidCredentialsException &&
                                               throwable.getMessage().contains("User account is inactive."))
                .verify();
    }

    @Test
    void loginUser_forOAuthUser_shouldThrowInvalidCredentialsException() {
        org.springframework.security.core.userdetails.User userDetails =
            new org.springframework.security.core.userdetails.User(sampleOAuthUser.getEmail(), "noop", Collections.emptyList());
        Authentication successfulAuth = new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());

        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(Mono.just(successfulAuth));
        when(appUserRepository.findByEmail(sampleOAuthUser.getEmail())).thenReturn(Mono.just(sampleOAuthUser));

        Mono<AuthResponse> result = authService.loginUser(new LoginRequest(sampleOAuthUser.getEmail(), "password"));

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof InvalidCredentialsException &&
                                               throwable.getMessage().contains("Please login using your GOOGLE account."))
                .verify();
    }


    @Test
    void refreshToken_withValidToken_shouldReturnNewAuthResponse() {
        when(refreshTokenRepository.findByToken(sampleOldRefreshTokenValue)).thenReturn(Mono.just(sampleRefreshTokenEntity));
        when(appUserRepository.findById(sampleUser.getId())).thenReturn(Mono.just(sampleUser));
        when(tokenProvider.createAccessToken(eq(sampleUser.getEmail()), eq(sampleUser.getId().toString()), any())).thenReturn("new.jwt.token");

        RefreshToken newRefreshTokenEntity = RefreshToken.builder().id(UUID.randomUUID()).token("very-new-refresh-token").userId(sampleUser.getId()).expiryDate(LocalDateTime.now().plusHours(1)).build();
        when(refreshTokenRepository.delete(sampleRefreshTokenEntity)).thenReturn(Mono.empty());
        when(refreshTokenRepository.deleteByUserId(sampleUser.getId())).thenReturn(Mono.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(Mono.just(newRefreshTokenEntity));


        Mono<AuthResponse> result = authService.refreshToken(sampleOldRefreshTokenValue);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.accessToken().equals("new.jwt.token") &&
                                               response.refreshToken().equals("very-new-refresh-token"))
                .verifyComplete();
    }

    @Test
    void refreshToken_withExpiredToken_shouldThrowTokenRefreshException() {
        RefreshToken expiredToken = RefreshToken.builder().token("expired").userId(UUID.randomUUID()).expiryDate(LocalDateTime.now().minusDays(1)).build();
        when(refreshTokenRepository.findByToken("expired")).thenReturn(Mono.just(expiredToken));
        when(refreshTokenRepository.delete(expiredToken)).thenReturn(Mono.empty());

        Mono<AuthResponse> result = authService.refreshToken("expired");

        StepVerifier.create(result)
                .expectError(TokenRefreshException.class)
                .verify();
    }

    @Test
    void refreshToken_withNonExistentToken_shouldThrowTokenRefreshException() {
        when(refreshTokenRepository.findByToken("non-existent-token")).thenReturn(Mono.empty());

        Mono<AuthResponse> result = authService.refreshToken("non-existent-token");

        StepVerifier.create(result)
            .expectErrorSatisfies(error -> {
                assertInstanceOf(TokenRefreshException.class, error);
                assertTrue(error.getMessage().contains("Invalid or non-existent refresh token."));
            })
            .verify();
    }


    @Test
    void refreshToken_forInactiveUser_shouldThrowTokenRefreshException() {
        when(refreshTokenRepository.findByToken(sampleOldRefreshTokenValue)).thenReturn(Mono.just(sampleRefreshTokenEntity));
        when(appUserRepository.findById(sampleUser.getId())).thenReturn(Mono.just(sampleInactiveUser));

        Mono<AuthResponse> result = authService.refreshToken(sampleOldRefreshTokenValue);

        StepVerifier.create(result)
            .expectErrorMatches(throwable -> throwable instanceof TokenRefreshException &&
                                           throwable.getMessage().contains("User account is inactive."))
            .verify();
    }


    @Test
    void getCurrentUser_whenUserExistsAndProfileFetcherWorks_shouldReturnCombinedData() {
        UserProfileServiceResponse profileResponse = new UserProfileServiceResponse(sampleUser.getId().toString(), sampleUser.getEmail(), "Profile Full Name", "avatar.url", null, null);
        when(appUserRepository.findByEmail(sampleUser.getEmail())).thenReturn(Mono.just(sampleUser));
        when(responseSpec.bodyToMono(UserProfileServiceResponse.class)).thenReturn(Mono.just(profileResponse));

        Mono<CurrentUserResponse> result = authService.getCurrentUser(sampleUser.getEmail());

        StepVerifier.create(result)
                .expectNextMatches(response -> response.userId().equals(sampleUser.getId().toString()) &&
                                          response.fullName().equals("Profile Full Name") &&
                                          response.roles().contains("ROLE_USER") &&
                                          response.authProvider().equals(AuthProvider.LOCAL.toString())
                                          )
                .verifyComplete();
    }

    @Test
    void getCurrentUser_whenUserProfileServiceFails_shouldReturnDataFromIdentity() {
        when(appUserRepository.findByEmail(sampleUser.getEmail())).thenReturn(Mono.just(sampleUser));
        when(responseSpec.bodyToMono(UserProfileServiceResponse.class)).thenReturn(Mono.error(new RuntimeException("User service down")));

        Mono<CurrentUserResponse> result = authService.getCurrentUser(sampleUser.getEmail());

        StepVerifier.create(result)
                .expectNextMatches(response -> response.userId().equals(sampleUser.getId().toString()) &&
                                          response.fullName().equals(sampleUser.getFullName()) &&
                                          response.roles().contains("ROLE_USER"))
                .verifyComplete();
    }


    @Test
    void getCurrentUser_whenUserDoesNotExist_shouldThrowUserNotFoundException() {
        when(appUserRepository.findByEmail("unknown@example.com")).thenReturn(Mono.empty());

        Mono<CurrentUserResponse> result = authService.getCurrentUser("unknown@example.com");

        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void logoutUser_withValidToken_shouldDeleteToken() {
        when(refreshTokenRepository.findByToken(sampleOldRefreshTokenValue)).thenReturn(Mono.just(sampleRefreshTokenEntity));
        when(refreshTokenRepository.delete(sampleRefreshTokenEntity)).thenReturn(Mono.empty());

        Mono<Void> result = authService.logoutUser(sampleOldRefreshTokenValue);

        StepVerifier.create(result).verifyComplete();
        verify(refreshTokenRepository).delete(sampleRefreshTokenEntity);
    }

    @Test
    void logoutUser_withNonExistentToken_shouldCompleteSilently() {
        when(refreshTokenRepository.findByToken("non-existent")).thenReturn(Mono.empty());

        Mono<Void> result = authService.logoutUser("non-existent");

        StepVerifier.create(result).verifyComplete();
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }
}
