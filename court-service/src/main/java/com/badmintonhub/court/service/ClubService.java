package com.badmintonhub.court.service;

import com.badmintonhub.court.dto.request.CreateClubRequest;
import com.badmintonhub.court.dto.response.ClubResponse;
import com.badmintonhub.court.entity.enums.Sport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ClubService {

    ClubResponse create(CreateClubRequest req, UUID createdBy);

    ClubResponse getById(UUID id);

    /**
     * Geo/venue search: filter by district + sport (cached under {@code clubs:{district}:{sport}}),
     * then Haversine-filter by radius when lat/lng/radiusKm are all supplied. Returns a page.
     */
    Page<ClubResponse> search(String district, Sport sport, Double lat, Double lng, Double radiusKm, Pageable pageable);
}
