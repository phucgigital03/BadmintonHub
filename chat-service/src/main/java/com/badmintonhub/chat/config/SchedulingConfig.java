package com.badmintonhub.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A shared {@link ThreadPoolTaskScheduler} — used both for {@code @Scheduled} jobs (unread reconcile) and by
 * {@code WebSocketSessionTracker} to schedule per-token session closes (§G.2). Declared here (not in
 * WebSocketConfig) so the tracker's dependency on it doesn't form a cycle with WebSocketConfig's dependency
 * on the STOMP interceptor.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("chat-sched-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
