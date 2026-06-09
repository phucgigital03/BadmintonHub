package com.badmintonhub.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// GlobalExceptionHandler (in common) is auto-registered via common's CommonWebAutoConfiguration —
// no scanBasePackages needed.
@SpringBootApplication
@EnableJpaAuditing
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
