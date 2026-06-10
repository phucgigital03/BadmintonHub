package com.badmintonhub.court.service.impl;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.court.dto.response.ClubGridResponse;
import com.badmintonhub.court.dto.response.CourtSlotsResponse;
import com.badmintonhub.court.dto.response.SlotResponse;
import com.badmintonhub.court.entity.Court;
import com.badmintonhub.court.entity.CourtPricingRule;
import com.badmintonhub.court.entity.TimeSlot;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
import com.badmintonhub.court.entity.enums.SlotStatus;
import com.badmintonhub.court.entity.enums.Sport;
import com.badmintonhub.court.repository.ClubRepository;
import com.badmintonhub.court.repository.CourtPricingRuleRepository;
import com.badmintonhub.court.repository.CourtRepository;
import com.badmintonhub.court.repository.TimeSlotRepository;
import com.badmintonhub.court.service.PricingRules;
import com.badmintonhub.court.service.PricingService;
import com.badmintonhub.court.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    /** Daily bookable window + cell size (authoritative: ERD / UC_Visual_Day_Booking). */
    private static final LocalTime DAY_START = LocalTime.of(5, 0);
    private static final LocalTime DAY_END = LocalTime.of(22, 0);
    private static final int CELL_MINUTES = 30;
    private static final CustomerType GRID_CUSTOMER_TYPE = CustomerType.WALK_IN;

    private final ClubRepository clubRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final CourtPricingRuleRepository pricingRuleRepository;
    private final PricingService pricingService;

    @Override
    @Transactional
    public int generateForClub(UUID clubId, LocalDate from, LocalDate to) {
        if (!clubRepository.existsById(clubId)) {
            throw new ResourceNotFoundException("CLUB_NOT_FOUND", "Không tìm thấy CLB");
        }
        int created = 0;
        for (Court court : courtRepository.findByClub_IdAndIsActiveTrueOrderByCourtNumber(clubId)) {
            created += generateForCourt(court, from, to);
        }
        return created;
    }

    @Override
    @Transactional
    public int generateForAllActiveCourts(LocalDate from, LocalDate to) {
        int created = 0;
        for (Court court : courtRepository.findByIsActiveTrue()) {
            created += generateForCourt(court, from, to);
        }
        return created;
    }

    /** Idempotent per (court, date): skips any date that already has slots. */
    private int generateForCourt(Court court, LocalDate from, LocalDate to) {
        int created = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (timeSlotRepository.existsByCourt_IdAndDate(court.getId(), date)) {
                continue;
            }
            List<TimeSlot> daySlots = new ArrayList<>();
            for (LocalTime start = DAY_START; start.isBefore(DAY_END); start = start.plusMinutes(CELL_MINUTES)) {
                TimeSlot slot = new TimeSlot();
                slot.setCourt(court);
                slot.setDate(date);
                slot.setStartTime(start);
                slot.setEndTime(start.plusMinutes(CELL_MINUTES));
                slot.setStatus(SlotStatus.AVAILABLE);
                daySlots.add(slot);
            }
            timeSlotRepository.saveAll(daySlots);
            created += daySlots.size();
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public ClubGridResponse getGrid(UUID clubId, LocalDate date, Sport sport) {
        if (!clubRepository.existsById(clubId)) {
            throw new ResourceNotFoundException("CLUB_NOT_FOUND", "Không tìm thấy CLB");
        }
        DayType dayType = PricingRules.dayTypeOf(date);

        List<Court> courts = (sport != null)
                ? courtRepository.findByClub_IdAndSportAndIsActiveTrueOrderByCourtNumber(clubId, sport)
                : courtRepository.findByClub_IdAndIsActiveTrueOrderByCourtNumber(clubId);
        if (courts.isEmpty()) {
            return new ClubGridResponse(date, dayType, List.of());
        }

        List<UUID> courtIds = courts.stream().map(Court::getId).toList();
        Map<UUID, List<TimeSlot>> slotsByCourt = timeSlotRepository
                .findByCourt_IdInAndDateOrderByCourt_CourtNumberAscStartTimeAsc(courtIds, date).stream()
                .collect(Collectors.groupingBy(s -> s.getCourt().getId()));

        // Pre-load pricing rules once per distinct sport (avoids N+1 across the whole grid).
        Map<Sport, List<CourtPricingRule>> rulesBySport = courts.stream()
                .map(Court::getSport).distinct()
                .collect(Collectors.toMap(Function.identity(),
                        sp -> pricingRuleRepository.findByClub_IdAndSport(clubId, sp)));

        List<CourtSlotsResponse> rows = courts.stream().map(court -> {
            List<CourtPricingRule> rules = rulesBySport.getOrDefault(court.getSport(), List.of());
            List<SlotResponse> slots = slotsByCourt.getOrDefault(court.getId(), List.of()).stream()
                    .map(s -> new SlotResponse(
                            s.getId(), s.getDate(), s.getStartTime(), s.getEndTime(), s.getStatus(),
                            PricingRules.priceForSlot(rules, date, s.getStartTime(), GRID_CUSTOMER_TYPE),
                            s.getEventId(), s.getBookingId(), s.getMatchId(), s.getEnrollmentId()))
                    .toList();
            return new CourtSlotsResponse(
                    court.getId(), court.getCourtNumber(), court.getSport(), court.getType(), slots);
        }).toList();

        return new ClubGridResponse(date, dayType, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponse getSlot(UUID courtId, UUID slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Không tìm thấy ô thời gian"));
        Court court = slot.getCourt();
        if (!court.getId().equals(courtId)) {
            throw new ResourceNotFoundException("SLOT_NOT_FOUND", "Ô thời gian không thuộc sân này");
        }
        BigDecimal price = pricingService.priceForSlot(
                court.getClub().getId(), court.getSport(), slot.getDate(), slot.getStartTime(), GRID_CUSTOMER_TYPE);
        return new SlotResponse(
                slot.getId(), slot.getDate(), slot.getStartTime(), slot.getEndTime(), slot.getStatus(),
                price, slot.getEventId(), slot.getBookingId(), slot.getMatchId(), slot.getEnrollmentId());
    }
}
