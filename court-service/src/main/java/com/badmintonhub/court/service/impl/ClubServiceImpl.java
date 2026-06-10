package com.badmintonhub.court.service.impl;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.court.dto.request.CreateClubRequest;
import com.badmintonhub.court.dto.response.ClubResponse;
import com.badmintonhub.court.entity.Club;
import com.badmintonhub.court.entity.enums.Sport;
import com.badmintonhub.court.repository.ClubRepository;
import com.badmintonhub.court.repository.CourtRepository;
import com.badmintonhub.court.service.ClubService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClubServiceImpl implements ClubService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final ClubRepository clubRepository;
    private final CourtRepository courtRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClubResponse create(CreateClubRequest req, UUID createdBy) {
        Club club = new Club();
        club.setName(req.name());
        club.setAddress(req.address());
        club.setDistrict(req.district());
        club.setLatitude(req.latitude());
        club.setLongitude(req.longitude());
        club.setImages(req.images());
        club.setCreatedBy(createdBy);
        // isActive=true, rating=0, totalReviews=0 are entity defaults
        return toResponse(clubRepository.save(club));
    }

    @Override
    @Transactional(readOnly = true)
    public ClubResponse getById(UUID id) {
        return toResponse(clubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CLUB_NOT_FOUND", "Không tìm thấy CLB")));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClubResponse> search(String district, Sport sport, Double lat, Double lng, Double radiusKm, Pageable pageable) {
        List<ClubResponse> base = cachedBaseList(district, sport);

        List<ClubResponse> filtered = base;
        if (lat != null && lng != null && radiusKm != null) {
            final double rlat = lat, rlng = lng, radius = radiusKm;
            filtered = base.stream()
                    .filter(c -> c.latitude() != null && c.longitude() != null
                            && haversineKm(rlat, rlng, c.latitude().doubleValue(), c.longitude().doubleValue()) <= radius)
                    .toList();
        }

        int start = (int) pageable.getOffset();
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    /** District+sport filtered list, cached 60s (fail-open on any Redis error). Haversine applied after. */
    private List<ClubResponse> cachedBaseList(String district, Sport sport) {
        String key = "clubs:" + (StringUtils.hasText(district) ? district : "all")
                + ":" + (sport != null ? sport.name() : "all");
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<ClubResponse>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis read failed for {}, querying DB: {}", key, e.toString());
        }

        List<ClubResponse> list = queryBase(district, sport);

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(list), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for {}: {}", key, e.toString());
        }
        return list;
    }

    private List<ClubResponse> queryBase(String district, Sport sport) {
        List<Club> clubs = StringUtils.hasText(district)
                ? clubRepository.findByDistrictAndIsActiveTrue(district, Pageable.unpaged()).getContent()
                : clubRepository.findByIsActiveTrue(Pageable.unpaged()).getContent();

        if (sport != null) {
            clubs = clubs.stream()
                    .filter(c -> !courtRepository
                            .findByClub_IdAndSportAndIsActiveTrueOrderByCourtNumber(c.getId(), sport).isEmpty())
                    .toList();
        }
        return clubs.stream().map(this::toResponse).toList();
    }

    private ClubResponse toResponse(Club c) {
        return new ClubResponse(
                c.getId(), c.getName(), c.getAddress(), c.getDistrict(),
                c.getLatitude(), c.getLongitude(), c.getImages(),
                c.getRating(), c.getTotalReviews(), c.isActive());
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
