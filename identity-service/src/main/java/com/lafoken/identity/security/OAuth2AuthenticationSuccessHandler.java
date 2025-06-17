package com.lafoken.identity.security;

import com.lafoken.identity.config.JwtProperties;
import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.entity.RefreshToken;
import com.lafoken.identity.event.UserRegisteredEvent;
import com.lafoken.identity.repository.AppUserRepository;
import com.lafoken.identity.repository.RefreshTokenRepository;
import com.lafoken.identity.service.EventProducerService;
import com.lafoken.identity.service.TokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OAuth2AuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final TokenProvider tokenProvider;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final String frontendSuccessRedirectUri;
    private final String frontendErrorRedirectUriPath;
    private final EventProducerService eventProducerService;

    public OAuth2AuthenticationSuccessHandler(TokenProvider tokenProvider,
                                           AppUserRepository appUserRepository,
                                           RefreshTokenRepository refreshTokenRepository,
                                           JwtProperties jwtProperties,
                                           @Value("${app.oauth2.redirect-uri.success}") String frontendSuccessRedirectUri,
                                           EventProducerService eventProducerService) {
        this.tokenProvider = tokenProvider;
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.frontendSuccessRedirectUri = frontendSuccessRedirectUri;
        this.eventProducerService = eventProducerService;

        URI successUri = URI.create(frontendSuccessRedirectUri);
        this.frontendErrorRedirectUriPath = successUri.resolve("/").toString();
    }

    @Override
    @Transactional
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            log.error("Illegal authentication type received in OAuth2SuccessHandler: {}", authentication.getClass().getName());
            return redirectToErrorPage(webFilterExchange, "internal_error", "Invalid authentication process.");
        }

        OAuth2User oauthUser = ((OAuth2AuthenticationToken) authentication).getPrincipal();
        Map<String, Object> attributes = oauthUser.getAttributes();
        String email = (String) attributes.get("email");
        String fullName = (String) attributes.get("name");

        if (email == null || email.isBlank()) {
            log.error("Email not found in OAuth2 user attributes.");
            return redirectToErrorPage(webFilterExchange, "oauth_email_missing", "Email could not be retrieved from provider.");
        }

        log.info("OAuth2 Authentication successful. Processing user: {}", email);

        return appUserRepository.findByEmail(email)
            .flatMap(existingUser -> processExistingUser(webFilterExchange, existingUser, fullName, email))
            .switchIfEmpty(Mono.defer(() -> processNewUser(webFilterExchange, email, fullName)))
            .flatMap(appUser -> generateTokensAndRedirect(webFilterExchange, appUser))
            .onErrorResume(e -> {
                log.error("Error during OAuth2 user processing for email {}: {}", email, e.getMessage(), e);
                return redirectToErrorPage(webFilterExchange, "processing_error", "An error occurred while processing your login.");
            });
    }

    private Mono<AppUser> processExistingUser(WebFilterExchange webFilterExchange, AppUser existingUser, String fullName, String email) {
        log.info("Existing user found: {}. Current isActive status: {}", email, existingUser.isActive());
        if (!existingUser.isActive()) {
            log.warn("OAuth2 login attempt for a banned/inactive user: {}", email);
            return redirectToErrorPage(webFilterExchange, "account_banned", "This account has been banned or deactivated.")
                   .then(Mono.empty());
        }
        boolean needsUpdate = false;
        if (fullName != null && !fullName.equals(existingUser.getFullName())) {
            existingUser.setFullName(fullName);
            needsUpdate = true;
        }
        if (existingUser.getAuthProvider() != AuthProvider.GOOGLE) {
            existingUser.setAuthProvider(AuthProvider.GOOGLE);
            needsUpdate = true;
        }
        if (!existingUser.isEmailVerified()) {
            existingUser.setEmailVerified(true);
            needsUpdate = true;
        }
        if (existingUser.getRoles() == null || existingUser.getRoles().isBlank()) {
            existingUser.setRoles("ROLE_USER");
             needsUpdate = true;
        }

        if (needsUpdate) {
            existingUser.setUpdatedAt(LocalDateTime.now());
            return appUserRepository.save(existingUser);
        }
        return Mono.just(existingUser);
    }

    private Mono<AppUser> processNewUser(WebFilterExchange webFilterExchange, String email, String fullName) {
        log.info("No existing user found with email {}, creating new user.", email);
        AppUser newUser = AppUser.builder()
            .email(email)
            .fullName(fullName)
            .hashedPassword(null)
            .isActive(true)
            .isEmailVerified(true)
            .authProvider(AuthProvider.GOOGLE)
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
            });
    }

    private Mono<Void> generateTokensAndRedirect(WebFilterExchange webFilterExchange, AppUser appUser) {
        String rolesString = appUser.getRoles() != null ? appUser.getRoles() : "ROLE_USER";
        String accessToken = tokenProvider.createAccessToken(appUser.getEmail(), appUser.getId().toString(),
                Arrays.stream(rolesString.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));

        return createAndSaveRefreshToken(appUser.getId())
            .flatMap(refreshToken -> {
                String redirectUrl = UriComponentsBuilder.fromUriString(frontendSuccessRedirectUri)
                    .queryParam("accessToken", accessToken)
                    .queryParam("refreshToken", refreshToken.getToken())
                    .queryParam("userId", appUser.getId().toString())
                    .queryParam("expiresIn", jwtProperties.accessTokenExpirationMs() / 1000)
                    .build().toUriString();

                log.info("Redirecting successfully authenticated OAuth2 user {} to: {}", appUser.getEmail(), redirectUrl);
                webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
                webFilterExchange.getExchange().getResponse().getHeaders().setLocation(URI.create(redirectUrl));
                return webFilterExchange.getExchange().getResponse().setComplete();
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

    private Mono<Void> redirectToErrorPage(WebFilterExchange webFilterExchange, String errorCode, String errorMessage) {
        String errorRedirectUrl = UriComponentsBuilder.fromUriString(this.frontendErrorRedirectUriPath)
            .queryParam("error", errorCode)
            .queryParam("message", errorMessage)
            .build().toUriString();
        log.warn("Redirecting OAuth2 user to error page: {}", errorRedirectUrl);
        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        webFilterExchange.getExchange().getResponse().getHeaders().setLocation(URI.create(errorRedirectUrl));
        return webFilterExchange.getExchange().getResponse().setComplete();
    }
}
