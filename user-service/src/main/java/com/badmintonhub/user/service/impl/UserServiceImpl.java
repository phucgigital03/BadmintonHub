package com.badmintonhub.user.service.impl;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.user.dto.request.UpdateUserRequest;
import com.badmintonhub.user.dto.response.UserResponse;
import com.badmintonhub.user.entity.User;
import com.badmintonhub.user.repository.UserRepository;
import com.badmintonhub.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return toResponse(findActive(id));
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest req) {
        User user = findActive(id);
        if (StringUtils.hasText(req.fullName())) {
            user.setFullName(req.fullName());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        User user = findActive(id);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user); // @Where filters it out on subsequent reads; never hard delete
    }

    // ----- helpers -----

    private User findActive(UUID id) {
        // @Where(deleted_at IS NULL) means soft-deleted users are already invisible here.
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
    }

    private UserResponse toResponse(User user) {
        Set<String> authorities = user.getRoles().stream()
                .map(r -> "ROLE_" + r.getName().name())
                .collect(Collectors.toSet());
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                authorities,
                user.isEmailVerified());
    }
}
