package com.lafoken.identity.service;

import com.lafoken.identity.dto.AdminUserView;
import com.lafoken.identity.dto.PageResponse;
import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.event.UserBannedEvent;
import com.lafoken.identity.exception.EmailAlreadyExistsException;
import com.lafoken.identity.exception.OperationNotAllowedException;
import com.lafoken.identity.exception.UserNotFoundException;
import com.lafoken.identity.repository.AppUserRepository;
import com.lafoken.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EventProducerService eventProducerService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    private AppUser user1;
    private AppUser user2;
    private AppUser inactiveUser;
    private AppUser adminUser;
    private final String ADMIN_EMAIL = "oleksandrhybalo@gmail.com";

    @BeforeEach
    void setUp() {
        user1 = AppUser.builder().id(UUID.randomUUID()).email("user1@example.com").fullName("User One").isActive(true).roles("ROLE_USER").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        user2 = AppUser.builder().id(UUID.randomUUID()).email("user2@example.com").fullName("User Two").isActive(true).roles("ROLE_USER").createdAt(LocalDateTime.now().minusDays(1)).updatedAt(LocalDateTime.now().minusDays(1)).build();
        inactiveUser = AppUser.builder().id(UUID.randomUUID()).email("inactive@example.com").fullName("Inactive User").isActive(false).roles("ROLE_USER").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        adminUser = AppUser.builder().id(UUID.randomUUID()).email(ADMIN_EMAIL).fullName("Admin User").isActive(true).roles("ROLE_ADMIN,ROLE_USER").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void getAllUsersPaged_shouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 1);
        List<AppUser> appUserList = Collections.singletonList(user1);
        Flux<AppUser> appUserFlux = Flux.fromIterable(appUserList);

        when(appUserRepository.findAllUsersPaged(pageable)).thenReturn(appUserFlux);
        when(appUserRepository.countAllUsers()).thenReturn(Mono.just(1L));

        Mono<PageResponse<AdminUserView>> result = adminService.getAllUsersPaged(pageable);

        StepVerifier.create(result)
                .expectNextMatches(pageResponse ->
                        pageResponse.content().size() == 1 &&
                        pageResponse.content().get(0).email().equals(user1.getEmail()) &&
                        pageResponse.pageNumber() == 0 &&
                        pageResponse.pageSize() == 1 &&
                        pageResponse.totalElements() == 1 &&
                        pageResponse.totalPages() == 1
                )
                .verifyComplete();
    }

    @Test
    void banUser_whenUserExistsAndIsActive_shouldBanUserAndEmitEvent() {
        when(appUserRepository.findById(user1.getId())).thenReturn(Mono.just(user1));
        when(appUserRepository.save(any(AppUser.class))).thenReturn(Mono.just(user1));
        when(refreshTokenRepository.deleteByUserId(user1.getId())).thenReturn(Mono.empty());
        doNothing().when(eventProducerService).sendUserBannedEvent(any(UserBannedEvent.class));

        Mono<Void> result = adminService.banUser(user1.getId().toString());

        StepVerifier.create(result).verifyComplete();

        verify(appUserRepository).save(argThat(user -> !user.isActive()));
        verify(refreshTokenRepository).deleteByUserId(user1.getId());
        verify(eventProducerService).sendUserBannedEvent(any(UserBannedEvent.class));
    }

    @Test
    void banUser_whenUserNotFound_shouldThrowUserNotFoundException() {
        UUID nonExistentUserId = UUID.randomUUID();
        when(appUserRepository.findById(nonExistentUserId)).thenReturn(Mono.empty());

        Mono<Void> result = adminService.banUser(nonExistentUserId.toString());

        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void unbanUser_whenUserExistsAndIsInactive_shouldUnbanUser() {
        when(appUserRepository.findById(inactiveUser.getId())).thenReturn(Mono.just(inactiveUser));
        when(appUserRepository.save(any(AppUser.class))).thenReturn(Mono.just(inactiveUser));

        Mono<Void> result = adminService.unbanUser(inactiveUser.getId().toString());

        StepVerifier.create(result).verifyComplete();
        verify(appUserRepository).save(argThat(AppUser::isActive));
    }

    @Test
    void unbanUser_whenUserNotFound_shouldThrowUserNotFoundException() {
        UUID nonExistentUserId = UUID.randomUUID();
        when(appUserRepository.findById(nonExistentUserId)).thenReturn(Mono.empty());

        Mono<Void> result = adminService.unbanUser(nonExistentUserId.toString());

        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void initializeFixedAdminUser_whenAdminDoesNotExistAndNoOtherAdmins_shouldCreateAdmin() {
        when(appUserRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(Mono.just(false));
        when(appUserRepository.findAllUsersWithDetails()).thenReturn(Flux.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashedAdminPassword");
        when(appUserRepository.insertProfile(any(AppUser.class))).thenReturn(Mono.empty());
        when(appUserRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Mono.just(adminUser));


        Mono<AdminUserView> result = adminService.initializeFixedAdminUser();

        StepVerifier.create(result)
            .expectNextMatches(view -> view.email().equals(ADMIN_EMAIL) && view.roles().contains("ROLE_ADMIN"))
            .verifyComplete();

        verify(appUserRepository).insertProfile(argThat(user ->
            user.getEmail().equals(ADMIN_EMAIL) &&
            user.getRoles().contains("ROLE_ADMIN")
        ));
    }

    @Test
    void initializeFixedAdminUser_whenAdminAlreadyExists_shouldThrowEmailAlreadyExistsException() {
        when(appUserRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(Mono.just(true));

        Mono<AdminUserView> result = adminService.initializeFixedAdminUser();

        StepVerifier.create(result)
            .expectError(EmailAlreadyExistsException.class)
            .verify();
    }

    @Test
    void initializeFixedAdminUser_whenOtherAdminsExist_shouldThrowOperationNotAllowedException() {
        AppUser existingAdmin = AppUser.builder().id(UUID.randomUUID()).email("otheradmin@example.com").roles("ROLE_ADMIN").build();
        when(appUserRepository.existsByEmail(ADMIN_EMAIL)).thenReturn(Mono.just(false));
        when(appUserRepository.findAllUsersWithDetails()).thenReturn(Flux.just(existingAdmin));

        Mono<AdminUserView> result = adminService.initializeFixedAdminUser();

        StepVerifier.create(result)
            .expectError(OperationNotAllowedException.class)
            .verify();
    }
}
