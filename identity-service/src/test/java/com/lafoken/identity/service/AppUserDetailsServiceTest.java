package com.lafoken.identity.service;

import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    private AppUser localUser;
    private AppUser googleUser;
    private AppUser inactiveUser;
    private AppUser userWithNoRoles;
    private AppUser userWithNullPasswordLocal;

    @BeforeEach
    void setUp() {
        localUser = AppUser.builder().id(UUID.randomUUID()).email("local@example.com").hashedPassword("hashedPass").roles("ROLE_USER,ROLE_CUSTOM").isActive(true).authProvider(AuthProvider.LOCAL).build();
        googleUser = AppUser.builder().id(UUID.randomUUID()).email("google@example.com").roles("ROLE_USER").isActive(true).authProvider(AuthProvider.GOOGLE).build();
        inactiveUser = AppUser.builder().id(UUID.randomUUID()).email("inactive@example.com").hashedPassword("pass").roles("ROLE_USER").isActive(false).authProvider(AuthProvider.LOCAL).build();
        userWithNoRoles = AppUser.builder().id(UUID.randomUUID()).email("noroles@example.com").hashedPassword("pass").roles("").isActive(true).authProvider(AuthProvider.LOCAL).build();
        userWithNullPasswordLocal = AppUser.builder().id(UUID.randomUUID()).email("nullpass@example.com").hashedPassword(null).roles("ROLE_USER").isActive(true).authProvider(AuthProvider.LOCAL).build();

    }

    @Test
    void findByUsername_whenLocalUserExistsAndActive_shouldReturnUserDetails() {
        when(appUserRepository.findByEmail("local@example.com")).thenReturn(Mono.just(localUser));

        Mono<UserDetails> result = appUserDetailsService.findByUsername("local@example.com");

        StepVerifier.create(result)
            .assertNext(userDetails -> {
                assertEquals("local@example.com", userDetails.getUsername());
                assertEquals("hashedPass", userDetails.getPassword());
                assertTrue(userDetails.isEnabled());
                assertTrue(userDetails.isAccountNonLocked());
                assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
                assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOM")));
            })
            .verifyComplete();
    }

    @Test
    void findByUsername_whenGoogleUserExistsAndActive_shouldReturnUserDetailsWithOAuthPlaceholderPassword() {
        when(appUserRepository.findByEmail("google@example.com")).thenReturn(Mono.just(googleUser));

        Mono<UserDetails> result = appUserDetailsService.findByUsername("google@example.com");

        StepVerifier.create(result)
            .assertNext(userDetails -> {
                assertEquals("google@example.com", userDetails.getUsername());
                assertTrue(userDetails.getPassword().contains("OAuthUserUsedOnlyForSpringSecurityInternal"));
                assertTrue(userDetails.isEnabled());
                 assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
            })
            .verifyComplete();
    }

    @Test
    void findByUsername_whenLocalUserHasNullPassword_shouldUsePlaceholderPassword() {
        when(appUserRepository.findByEmail("nullpass@example.com")).thenReturn(Mono.just(userWithNullPasswordLocal));

        Mono<UserDetails> result = appUserDetailsService.findByUsername("nullpass@example.com");

        StepVerifier.create(result)
            .assertNext(userDetails -> {
                assertEquals("nullpass@example.com", userDetails.getUsername());
                assertTrue(userDetails.getPassword().contains("OAuthUserUsedOnlyForSpringSecurityInternal"));
                assertTrue(userDetails.isEnabled());
            })
            .verifyComplete();
    }

    @Test
    void findByUsername_whenUserIsInactive_shouldReturnDisabledUserDetails() {
        when(appUserRepository.findByEmail("inactive@example.com")).thenReturn(Mono.just(inactiveUser));

        Mono<UserDetails> result = appUserDetailsService.findByUsername("inactive@example.com");

        StepVerifier.create(result)
            .assertNext(userDetails -> {
                assertEquals("inactive@example.com", userDetails.getUsername());
                assertFalse(userDetails.isEnabled());
                assertFalse(userDetails.isAccountNonLocked());
            })
            .verifyComplete();
    }

    @Test
    void findByUsername_whenUserHasNoRoles_shouldAssignDefaultRoleUser() {
        when(appUserRepository.findByEmail("noroles@example.com")).thenReturn(Mono.just(userWithNoRoles));

        Mono<UserDetails> result = appUserDetailsService.findByUsername("noroles@example.com");

        StepVerifier.create(result)
            .assertNext(userDetails -> {
                assertTrue(userDetails.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_USER")));
                assertEquals(1, userDetails.getAuthorities().size());
            })
            .verifyComplete();
    }


    @Test
    void findByUsername_whenUserNotFound_shouldThrowUsernameNotFoundException() {
        when(appUserRepository.findByEmail("unknown@example.com")).thenReturn(Mono.empty());

        Mono<UserDetails> result = appUserDetailsService.findByUsername("unknown@example.com");

        StepVerifier.create(result)
            .expectError(UsernameNotFoundException.class)
            .verify();
    }
}
