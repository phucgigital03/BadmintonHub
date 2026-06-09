package com.badmintonhub.user.service;

import com.badmintonhub.user.dto.request.UpdateUserRequest;
import com.badmintonhub.user.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * User profile management — read, update, list, soft delete.
 * Implemented by {@code service.impl.UserServiceImpl}.
 */
public interface UserService {

    UserResponse getById(UUID id);

    UserResponse update(UUID id, UpdateUserRequest req);

    Page<UserResponse> list(Pageable pageable);

    /** Soft delete — sets {@code deleted_at}; never hard-deletes (Never-Violate rule #8). */
    void softDelete(UUID id);
}
