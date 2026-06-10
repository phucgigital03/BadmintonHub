package com.badmintonhub.court.repository;

import com.badmintonhub.court.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    /** All slots for the given courts on a single date — the grid query. */
    List<TimeSlot> findByCourt_IdInAndDateOrderByCourt_CourtNumberAscStartTimeAsc(
            Collection<UUID> courtIds, LocalDate date);

    Optional<TimeSlot> findByCourt_IdAndDateAndStartTime(UUID courtId, LocalDate date, LocalTime startTime);

    /** Idempotency guard for slot generation: skip a (court, date) that already has slots. */
    boolean existsByCourt_IdAndDate(UUID courtId, LocalDate date);
}
