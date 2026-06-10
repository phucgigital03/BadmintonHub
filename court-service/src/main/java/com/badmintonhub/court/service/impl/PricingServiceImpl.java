package com.badmintonhub.court.service.impl;

import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.court.dto.request.CreatePricingRuleRequest;
import com.badmintonhub.court.dto.response.PricingRuleResponse;
import com.badmintonhub.court.entity.Club;
import com.badmintonhub.court.entity.CourtPricingRule;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.Sport;
import com.badmintonhub.court.repository.ClubRepository;
import com.badmintonhub.court.repository.CourtPricingRuleRepository;
import com.badmintonhub.court.service.PricingRules;
import com.badmintonhub.court.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final ClubRepository clubRepository;
    private final CourtPricingRuleRepository pricingRuleRepository;

    @Override
    @Transactional
    public PricingRuleResponse createRule(UUID clubId, CreatePricingRuleRequest req) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("CLUB_NOT_FOUND", "Không tìm thấy CLB"));

        boolean exists = pricingRuleRepository.existsByClub_IdAndSportAndDayTypeAndStartTimeAndCustomerType(
                clubId, req.sport(), req.dayType(), req.startTime(), req.customerType());
        if (exists) {
            throw new ConflictException("PRICING_RULE_EXISTS",
                    "Đã có bảng giá cho khung này (club + môn + ngày + giờ + loại khách)");
        }

        CourtPricingRule rule = new CourtPricingRule();
        rule.setClub(club);
        rule.setSport(req.sport());
        rule.setDayType(req.dayType());
        rule.setStartTime(req.startTime());
        rule.setEndTime(req.endTime());
        rule.setCustomerType(req.customerType());
        rule.setPricePerHour(req.pricePerHour());
        return toResponse(pricingRuleRepository.save(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listRules(UUID clubId, Sport sport) {
        return pricingRuleRepository.findByClub_IdAndSport(clubId, sport).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal priceForSlot(UUID clubId, Sport sport, LocalDate date, LocalTime start, CustomerType customerType) {
        List<CourtPricingRule> rules = pricingRuleRepository.findByClub_IdAndSport(clubId, sport);
        return PricingRules.priceForSlot(rules, date, start, customerType);
    }

    private PricingRuleResponse toResponse(CourtPricingRule r) {
        return new PricingRuleResponse(
                r.getId(), r.getSport(), r.getDayType(), r.getStartTime(), r.getEndTime(),
                r.getCustomerType(), r.getPricePerHour());
    }
}
