package com.example.springcaching.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback configuration that provides a simple in-memory {@link CacheManager}
 * named {@code redisCacheManager} when Redis is not available (e.g., in tests
 * or local dev without Docker).
 *
 * <p>This bean is created only when no other {@code redisCacheManager} bean
 * exists (i.e., when {@link RedisConfig} is not active because
 * {@code spring.data.redis.host} is not set).
 *
 * <p>In production, Redis must be running and {@code spring.data.redis.host}
 * must be configured so that {@link RedisConfig} provides the real Redis-backed
 * cache manager.
 */
@Configuration
@Slf4j
public class FallbackCacheConfig {

    /**
     * No-op in-memory fallback for the Redis cache manager.
     * Functionally equivalent to the default Spring Cache but logs a warning.
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisCacheManager")
    public CacheManager redisCacheManager() {
        log.warn("Redis is not configured (spring.data.redis.host not set). " +
                "Using in-memory fallback for redisCacheManager. " +
                "This is NOT suitable for production.");
        return new ConcurrentMapCacheManager(CacheNames.PRODUCT_SEARCH);
    }
}
