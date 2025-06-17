package com.lafoken.identity.controller;

import com.lafoken.identity.dto.AdminUserView;
import com.lafoken.identity.dto.IsAdminResponse;
import com.lafoken.identity.dto.PageResponse;
import com.lafoken.identity.service.AdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import com.lafoken.identity.dto.AdminInitRequest;
import com.lafoken.identity.exception.OperationNotAllowedException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/identity/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PageResponse<AdminUserView>> getAllUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return adminService.getAllUsersPaged(pageable);
    }

    @PostMapping("/users/{userId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> banUser(@PathVariable String userId) {
        return adminService.banUser(userId);
    }

    @PostMapping("/users/{userId}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> unbanUser(@PathVariable String userId) {
        return adminService.unbanUser(userId);
    }

    @GetMapping("/check-admin-role")
    @PreAuthorize("isAuthenticated()")
    public Mono<IsAdminResponse> checkAdminRole() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getAuthorities)
            .map(authorities -> {
                boolean isAdmin = authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
                return new IsAdminResponse(isAdmin);
            })
            .defaultIfEmpty(new IsAdminResponse(false));
    }

    @PostMapping("/users/{userId}/grant-admin")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<AdminUserView> grantAdminRole(@PathVariable String userId) {
        return adminService.grantAdminRoleToUser(userId);
    }
}
