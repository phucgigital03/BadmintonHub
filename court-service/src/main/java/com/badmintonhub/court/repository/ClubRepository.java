package com.badmintonhub.court.repository;

import com.badmintonhub.court.entity.Club;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {

    Page<Club> findByIsActiveTrue(Pageable pageable);

    Page<Club> findByDistrictAndIsActiveTrue(String district, Pageable pageable);

    boolean existsByName(String name);
}
