package com.badmintonhub.escrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class EscrowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EscrowServiceApplication.class, args);
    }
}
