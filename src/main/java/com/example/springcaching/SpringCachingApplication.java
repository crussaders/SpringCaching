package com.example.springcaching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Spring Boot application demonstrating all types of caching:
 * <ul>
 *   <li>Simple in-memory caching with {@code @Cacheable}, {@code @CacheEvict}, {@code @CachePut}</li>
 *   <li>Caffeine Cache – high-performance, in-process local cache with TTL / size eviction</li>
 *   <li>EhCache 3 – enterprise-grade, JSR-107-compliant local cache with persistence options</li>
 *   <li>Redis Cache – distributed, out-of-process cache for multi-instance deployments</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
public class SpringCachingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCachingApplication.class, args);
    }
}
