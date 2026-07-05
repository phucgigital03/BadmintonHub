package com.badmintonhub.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables Mongo auditing so {@code @CreatedDate}/{@code @LastModifiedDate} on {@code Conversation}
 * are populated automatically.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
