package com.badmintonhub.court.dto.response;

import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
import com.badmintonhub.court.entity.enums.Sport;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record PricingRuleResponse(
        UUID id,
        Sport sport,
        DayType dayType,
        LocalTime startTime,
        LocalTime endTime,
        CustomerType customerType,
        BigDecimal pricePerHour
) {}
