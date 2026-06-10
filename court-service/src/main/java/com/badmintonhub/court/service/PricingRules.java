package com.badmintonhub.court.service;

import com.badmintonhub.court.entity.CourtPricingRule;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pure matching logic shared by {@code PricingServiceImpl} (single-slot lookups) and
 * {@code SlotServiceImpl} (grid, where rules are pre-loaded once per sport to avoid N+1 queries).
 */
public final class PricingRules {

    private PricingRules() {}

    /** WEEKEND = Sat/Sun (T7-CN); otherwise WEEKDAY (T2-T6). */
    public static DayType dayTypeOf(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) ? DayType.WEEKEND : DayType.WEEKDAY;
    }

    /**
     * Price for one 30-min cell = matching rule's {@code price_per_hour ÷ 2} (rounded to whole VND).
     * Returns {@code null} when no rule covers the cell so the grid can render it priceless gracefully.
     */
    public static BigDecimal priceForSlot(List<CourtPricingRule> rules, LocalDate date,
                                          LocalTime start, CustomerType customerType) {
        DayType dayType = dayTypeOf(date);
        return rules.stream()
                .filter(r -> r.getCustomerType() == customerType
                        && r.getDayType() == dayType
                        && !start.isBefore(r.getStartTime())   // start >= rule.startTime
                        && start.isBefore(r.getEndTime()))     // start <  rule.endTime
                .findFirst()
                .map(r -> r.getPricePerHour().divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
