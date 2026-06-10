package com.badmintonhub.court.repository;

import com.badmintonhub.court.entity.CourtPricingRule;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
import com.badmintonhub.court.entity.enums.Sport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourtPricingRuleRepository extends JpaRepository<CourtPricingRule, UUID> {

    List<CourtPricingRule> findByClub_IdAndSport(UUID clubId, Sport sport);

    /** Guards the uk_pricing_dimension unique constraint before insert. */
    boolean existsByClub_IdAndSportAndDayTypeAndStartTimeAndCustomerType(
            UUID clubId, Sport sport, DayType dayType, LocalTime startTime, CustomerType customerType);
}
