package com.lafoken.identity.controller;

import com.lafoken.identity.dto.*;
import com.lafoken.identity.service.AuthService;
import com.lafoken.identity.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpResponse;
import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/identity/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;


    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationRequest registrationRequest) {
        return authService.registerUser(registrationRequest);
    }

    @PostMapping("/login")
    public Mono<AuthResponse> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.loginUser(loginRequest);
    }

    @PostMapping("/refresh")
    public Mono<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return authService.refreshToken(refreshTokenRequest.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logoutUser(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
         return authService.logoutUser(refreshTokenRequest.refreshToken());
    }

    @GetMapping("/me")
    public Mono<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal Principal principal) {
        if (principal == null || principal.getName() == null) {
            return Mono.error(new com.lafoken.identity.exception.InvalidCredentialsException("Authentication required."));
        }
        return authService.getCurrentUser(principal.getName());
    }


    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        return passwordResetService.initiatePasswordReset(forgotPasswordRequest);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return passwordResetService.resetPassword(resetPasswordRequest)
            .then(Mono.empty());
    }

    @GetMapping("/oauth2/login/google")
    @ResponseStatus(HttpStatus.FOUND)
    public Mono<Void> oauth2LoginGoogle(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setLocation(URI.create("/oauth2/authorization/google"));
        return response.setComplete();
    }
}
