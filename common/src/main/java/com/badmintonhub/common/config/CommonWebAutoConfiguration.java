package com.badmintonhub.common.config;

import com.badmintonhub.common.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-registers shared web infrastructure for every servlet service that depends on {@code common}.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so services do NOT need {@code scanBasePackages} to pick up the {@link GlobalExceptionHandler}
 * {@code @RestControllerAdvice} (which lives outside their own base package). This guarantees
 * {@code ApiException}s render as {@code {code, message, timestamp}} instead of a generic 500.</p>
 *
 * <p>{@code @ConditionalOnWebApplication(SERVLET)} keeps it out of reactive contexts (e.g. the gateway,
 * which builds its own error JSON). {@code @ConditionalOnMissingBean} lets a service override the handler.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
