package com.badmintonhub.court.repository;

import com.badmintonhub.court.entity.ClubReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClubReviewRepository extends JpaRepository<ClubReview, UUID> {
}
