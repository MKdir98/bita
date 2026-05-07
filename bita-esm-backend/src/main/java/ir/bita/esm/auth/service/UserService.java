package ir.bita.esm.auth.service;

import ir.bita.esm.auth.dto.*;
import ir.bita.esm.auth.entity.Role;
import ir.bita.esm.auth.entity.User;
import ir.bita.esm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Lists all users with pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String search, Pageable pageable) {
        Page<User> users;
        if (search != null && !search.isBlank()) {
            users = userRepository.searchUsers(search, pageable);
        } else {
            users = userRepository.findByDeletedFalse(pageable);
        }
        return users.map(this::toResponse);
    }

    /**
     * Gets a user by ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        return toResponse(user);
    }

    /**
     * Creates a new user.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Check if mobile number already exists
        if (userRepository.existsByMobileNumber(request.getMobileNumber())) {
            throw new IllegalArgumentException("User with this mobile number already exists");
        }

        User user = User.builder()
                .mobileNumber(request.getMobileNumber())
                .fullName(request.getFullName())
                .roles(request.getRoles() != null ? request.getRoles() : new HashSet<>())
                .build();

        // Add default role if no roles specified
        if (user.getRoles().isEmpty()) {
            user.addRole(Role.VIEWER);
        }

        user = userRepository.save(user);
        log.info("Created new user with id {} and mobile {}", user.getId(), user.getMobileNumber());
        return toResponse(user);
    }

    /**
     * Updates an existing user.
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (request.getRoles() != null) {
            user.setRoles(request.getRoles());
        }

        user = userRepository.save(user);
        log.info("Updated user with id {}", id);
        return toResponse(user);
    }

    /**
     * Deletes a user (soft delete).
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        user.softDelete();
        userRepository.save(user);
        log.info("Deleted user with id {}", id);
    }

    /**
     * Gets the current user's profile.
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(user);
    }

    /**
     * Updates the current user's profile.
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        user = userRepository.save(user);
        log.info("Updated profile for user {}", userId);
        return toResponse(user);
    }

    /**
     * Lists all available roles.
     */
    public List<RoleResponse> listRoles() {
        return Arrays.stream(Role.values())
                .map(role -> RoleResponse.builder()
                        .name(role.name())
                        .description(getRoleDescription(role))
                        .build())
                .collect(Collectors.toList());
    }

    private String getRoleDescription(Role role) {
        return switch (role) {
            case ADMIN -> "Full administrative access";
            case SERVICE_MANAGER -> "Can manage services and routes";
            case CLIENT_MANAGER -> "Can manage clients and credentials";
            case ACCESS_MANAGER -> "Can manage service access permissions";
            case VIEWER -> "Read-only access";
        };
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .mobileNumber(user.getMobileNumber())
                .fullName(user.getFullName())
                .active(user.isActive())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
