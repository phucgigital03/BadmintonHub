package com.badmintonhub.court.dto.request;

import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
import com.badmintonhub.court.entity.enums.Sport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * One multi-dimensional pricing rule. The tuple
 * (clubId, sport, dayType, startTime, customerType) must be unique (uk_pricing_dimension).
 */
public record CreatePricingRuleRequest(
        @NotNull Sport sport,
        @NotNull DayType dayType,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull CustomerType customerType,
        @NotNull @Positive BigDecimal pricePerHour
) {}
