package com.badmintonhub.booking.repository;

import com.badmintonhub.booking.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    void deleteByProcessedAtBefore(LocalDateTime cutoff);
}
