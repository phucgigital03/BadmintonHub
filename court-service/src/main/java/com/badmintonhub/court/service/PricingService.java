package com.badmintonhub.court.service;

import com.badmintonhub.court.dto.request.CreatePricingRuleRequest;
import com.badmintonhub.court.dto.response.PricingRuleResponse;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.Sport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface PricingService {

    PricingRuleResponse createRule(UUID clubId, CreatePricingRuleRequest req);

    List<PricingRuleResponse> listRules(UUID clubId, Sport sport);

    /** Snapshot price for a single 30-min cell; null when no rule matches. */
    BigDecimal priceForSlot(UUID clubId, Sport sport, LocalDate date, LocalTime start, CustomerType customerType);
}
