package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.ManualRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ManualRefundRepository extends JpaRepository<ManualRefund, UUID> {
}
