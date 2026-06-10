package com.badmintonhub.court.repository;

import com.badmintonhub.court.entity.Court;
import com.badmintonhub.court.entity.enums.Sport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourtRepository extends JpaRepository<Court, UUID> {

    List<Court> findByClub_IdAndIsActiveTrueOrderByCourtNumber(UUID clubId);

    List<Court> findByClub_IdAndSportAndIsActiveTrueOrderByCourtNumber(UUID clubId, Sport sport);

    List<Court> findByIsActiveTrue();
}
