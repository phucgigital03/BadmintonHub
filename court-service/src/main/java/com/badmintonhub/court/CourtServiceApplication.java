package com.badmintonhub.court;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableKafka
public class CourtServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourtServiceApplication.class, args);
    }
}
