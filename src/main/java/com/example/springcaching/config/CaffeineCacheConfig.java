package com.example.springcaching.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache configuration.
 *
 * <h2>What is Caffeine?</h2>
 * <p>Caffeine is a high-performance, near-optimal caching library for Java.
 * It is the successor to Guava's cache and is designed for maximum throughput
 * and minimum latency in a single JVM process.
 *
 * <h2>Key features</h2>
 * <ul>
 *   <li>W-TinyLFU eviction policy – near-optimal hit-rate in most workloads</li>
 *   <li>Time-based expiry (TTL after write or after last access)</li>
 *   <li>Size-based eviction (max entries or max weight)</li>
 *   <li>Asynchronous loading and refresh</li>
 *   <li>Statistics collection (hit/miss ratios)</li>
 * </ul>
 *
 * <h2>When to use?</h2>
 * <p>Caffeine is ideal for single-instance applications where you need fast,
 * in-memory caching without network overhead. For multi-instance deployments
 * use Redis (see {@link RedisConfig}).
 */
@Configuration
@Slf4j
public class CaffeineCacheConfig {

    /**
     * Primary {@link CacheManager} backed by Caffeine.
     *
     * <p>This manager handles all cache regions defined in {@link CacheNames}
     * except those that are explicitly routed to Redis.
     *
     * <p>Per-cache Caffeine specs:
     * <ul>
     *   <li>{@code productById}       – max 500 entries, expire 10 min after write</li>
     *   <li>{@code productsAll}       – max 1 entry (a single list),  expire 5 min after write</li>
     *   <li>{@code productsByCategory}– max 200 entries, expire 5 min after write</li>
     *   <li>{@code categoryById}      – max 200 entries, expire 10 min after write</li>
     *   <li>{@code categoriesAll}     – max 1 entry, expire 5 min after write</li>
     * </ul>
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Record statistics so Actuator can expose hit/miss metrics
        manager.setCaffeine(defaultCaffeineSpec());

        // Pre-register all known caches so Spring can validate cache names on start-up
        manager.setCacheNames(java.util.List.of(
                CacheNames.PRODUCT_BY_ID,
                CacheNames.PRODUCTS_ALL,
                CacheNames.PRODUCTS_BY_CATEGORY,
                CacheNames.CATEGORY_BY_ID,
                CacheNames.CATEGORIES_ALL
        ));

        log.info("Caffeine CacheManager configured with caches: {}", manager.getCacheNames());
        return manager;
    }

    /**
     * Default Caffeine spec – a sensible starting point.
     * Override individual caches in {@code application.yml} for production tuning.
     */
    private Caffeine<Object, Object> defaultCaffeineSpec() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats();
    }
}
