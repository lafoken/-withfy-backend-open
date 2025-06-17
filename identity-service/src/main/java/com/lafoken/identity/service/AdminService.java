package com.lafoken.identity.service;

import com.lafoken.identity.dto.AdminUserView;
import com.lafoken.identity.dto.PageResponse;
import com.lafoken.identity.event.UserBannedEvent;
import com.lafoken.identity.exception.UserNotFoundException;
import com.lafoken.identity.repository.AppUserRepository;
import com.lafoken.identity.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.lafoken.identity.dto.AdminInitRequest;
import com.lafoken.identity.entity.AppUser;
import com.lafoken.identity.entity.AuthProvider;
import com.lafoken.identity.exception.EmailAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.lafoken.identity.exception.OperationNotAllowedException;

@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EventProducerService eventProducerService;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_ROLE_TO_GRANT = "ROLE_ADMIN";


    public AdminService(AppUserRepository appUserRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        EventProducerService eventProducerService,
                        PasswordEncoder passwordEncoder
                        ) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.eventProducerService = eventProducerService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Mono<AdminUserView> grantAdminRoleToUser(String userIdString) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for userId when granting admin role: {}", userIdString);
            return Mono.error(new IllegalArgumentException("Invalid user ID format."));
        }

        log.info("Attempting to grant admin role to user with ID: {}", userId);

        return appUserRepository.findById(userId)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("User not found with ID: {} when attempting to grant admin role.", userId);
                return Mono.error(new UserNotFoundException("User not found with ID: " + userIdString));
            }))
            .flatMap(user -> {
                if (user.getRoles() != null && user.getRoles().contains(ADMIN_ROLE_TO_GRANT)) {
                    log.info("User {} already has admin role. No changes made.", user.getEmail());
                    return Mono.just(user);
                }

                user.addRole(ADMIN_ROLE_TO_GRANT);
                user.setUpdatedAt(LocalDateTime.now());
                return appUserRepository.save(user)
                    .doOnSuccess(updatedUser -> log.info("Admin role granted to user: {}", updatedUser.getEmail()));
            })
            .map(AdminUserView::fromEntity);
    }


    public Flux<AdminUserView> getAllUsers() {
        log.info("AdminService: Fetching all users using custom query (non-paged).");
        return appUserRepository.findAllUsersWithDetails()
            .map(appUser -> {
                AdminUserView view = AdminUserView.fromEntity(appUser);
                log.debug("AdminService: Mapped user {} to AdminUserView: {}", appUser.getEmail(), view);
                return view;
            })
            .doOnComplete(() -> log.info("AdminService: Finished fetching and mapping users via custom query (non-paged)."))
            .doOnError(error -> log.error("AdminService: Error fetching users via custom query (non-paged): ", error))
            .switchIfEmpty(Flux.defer(() -> {
                log.warn("AdminService: findAllUsersWithDetails returned an empty Flux.");
                return Flux.empty();
            }));
    }

    public Mono<PageResponse<AdminUserView>> getAllUsersPaged(Pageable pageable) {
        log.info("AdminService: Fetching paged users. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Mono<List<AdminUserView>> contentMono = appUserRepository.findAllUsersPaged(pageable)
            .map(AdminUserView::fromEntity)
            .collectList();

        Mono<Long> totalElementsMono = appUserRepository.countAllUsers();

        return Mono.zip(contentMono, totalElementsMono)
            .map(tuple -> {
                List<AdminUserView> content = tuple.getT1();
                long totalElements = tuple.getT2();
                int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());
                boolean isLast = pageable.getPageNumber() >= totalPages - 1;
                boolean isFirst = pageable.getPageNumber() == 0;

                log.info("AdminService: Paged users fetched. TotalElements: {}, TotalPages: {}", totalElements, totalPages);
                return new PageResponse<>(
                        content,
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        totalElements,
                        totalPages,
                        isLast,
                        isFirst
                );
            });
    }


    @Transactional
    public Mono<Void> banUser(String userIdString) {
        UUID userId = UUID.fromString(userIdString);
        return appUserRepository.findById(userId)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with ID: " + userIdString)))
            .flatMap(user -> {
                if (!user.isActive()) {
                    log.info("User {} is already banned.", userIdString);
                    return Mono.empty();
                }
                user.setActive(false);
                user.setUpdatedAt(LocalDateTime.now());
                return appUserRepository.save(user)
                    .then(refreshTokenRepository.deleteByUserId(userId))
                    .doOnSuccess(v -> {
                        log.info("User {} banned successfully. Refresh tokens deleted.", userIdString);
                        eventProducerService.sendUserBannedEvent(new UserBannedEvent(userIdString));
                    });
            })
            .then();
    }

    @Transactional
    public Mono<Void> unbanUser(String userIdString) {
        UUID userId = UUID.fromString(userIdString);
        return appUserRepository.findById(userId)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found with ID: " + userIdString)))
            .flatMap(user -> {
                if (user.isActive()) {
                    log.info("User {} is already active.", userIdString);
                    return Mono.empty();
                }
                user.setActive(true);
                user.setUpdatedAt(LocalDateTime.now());
                return appUserRepository.save(user)
                    .doOnSuccess(u -> log.info("User {} unbanned successfully.", userIdString));
            })
            .then();
    }
}
