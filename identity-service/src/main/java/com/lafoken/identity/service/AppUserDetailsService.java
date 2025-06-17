package com.lafoken.identity.service;

import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppUserDetailsService implements ReactiveUserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(AppUserDetailsService.class);
    private final AppUserRepository appUserRepository;
    private static final String OAUTH_PASSWORD_PLACEHOLDER = "{noop}OAuthUserUsedOnlyForSpringSecurityInternal";

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        log.info("AppUserDetailsService: Attempting to find user by email: {}", username);
        return appUserRepository.findByEmail(username)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("AppUserDetailsService: User not found with email: {}", username);
                return Mono.error(new UsernameNotFoundException("User not found with email: " + username));
            }))
            .map(appUser -> {
                log.info("AppUserDetailsService: User found: {}, Roles from DB: '{}', isActive: {}, isEmailVerified: {}, authProvider: {}",
                         appUser.getEmail(), appUser.getRoles(), appUser.isActive(), appUser.isEmailVerified(), appUser.getAuthProvider());
                return buildUserDetails(appUser);
            });
    }

    private UserDetails buildUserDetails(AppUser appUser) {
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
        log.info("AppUserDetailsService: For user {}, authorities created: {}", appUser.getEmail(), authorities);


        String passwordToUse;
        if (appUser.getAuthProvider() != null && appUser.getAuthProvider() != AuthProvider.LOCAL) {
            passwordToUse = OAUTH_PASSWORD_PLACEHOLDER;
        } else if (appUser.getHashedPassword() == null && appUser.getAuthProvider() == AuthProvider.LOCAL) {
            log.warn("AppUserDetailsService: LOCAL user {} has null password!", appUser.getEmail());
            passwordToUse = OAUTH_PASSWORD_PLACEHOLDER;
        }
        else {
            passwordToUse = appUser.getHashedPassword();
        }

        if (passwordToUse == null) {
             log.warn("AppUserDetailsService: passwordToUse ended up null for user {}, falling back to placeholder.", appUser.getEmail());
            passwordToUse = OAUTH_PASSWORD_PLACEHOLDER;
        }

        boolean isDisabled = !appUser.isActive();

        log.info("AppUserDetailsService: Building UserDetails for {}. isActive: {}, isDisabled flag for UserDetails: {}",
                 appUser.getEmail(), appUser.isActive(), isDisabled);

        return User.builder()
            .username(appUser.getEmail())
            .password(passwordToUse)
            .authorities(authorities)
            .accountExpired(false)
            .accountLocked(isDisabled)
            .credentialsExpired(false)
            .disabled(isDisabled)
            .build();
    }
}
