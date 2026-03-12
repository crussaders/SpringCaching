package com.example.springcaching.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis Cache configuration (distributed caching).
 *
 * <h2>What is Redis?</h2>
 * <p>Redis (Remote Dictionary Server) is an open-source, in-memory data structure store
 * used as a database, cache, message broker, and streaming engine.
 *
 * <h2>Why distributed caching?</h2>
 * <p>When multiple instances of an application run behind a load balancer, a local in-memory
 * cache (Caffeine, EhCache heap tier) is only visible to the JVM that populated it.
 * Redis solves this by acting as a shared external cache accessible by every instance.
 *
 * <h2>Spring integration</h2>
 * <p>Spring Boot auto-configures a {@link LettuceConnectionFactory} when
 * {@code spring.data.redis.*} properties are set. We build a
 * {@link RedisCacheManager} on top of that factory, using JSON serialization
 * for human-readable keys and values.
 *
 * <h2>Cache regions (TTL)</h2>
 * <ul>
 *   <li>{@code productSearch} – 2 minutes (search results change often)</li>
 *   <li>default – 5 minutes</li>
 * </ul>
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.data.redis.host")
@org.springframework.context.annotation.Profile("!test")
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    // ── Connection ──────────────────────────────────────────────────────────

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
    }

    // ── RedisTemplate (for direct Redis operations) ──────────────────────────

    /**
     * Generic {@link RedisTemplate} with String keys and JSON values.
     * Use this bean when you need to perform raw Redis commands (SET, GET, INCR, …)
     * rather than Spring Cache abstractions.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    // ── CacheManager ─────────────────────────────────────────────────────────

    /**
     * Redis-backed {@link CacheManager}.
     *
     * <p>Named {@code redisCacheManager} so it can be referenced explicitly in
     * {@code @Cacheable(cacheManager = "redisCacheManager")}.
     */
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = defaultRedisCacheConfig();

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                CacheNames.PRODUCT_SEARCH, defaultRedisCacheConfig().entryTtl(Duration.ofMinutes(2))
        );

        CacheManager manager = RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();

        log.info("Redis CacheManager configured (host={}:{})", redisHost, redisPort);
        return manager;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RedisCacheConfiguration defaultRedisCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer()));
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Store type information so deserialisation works without knowing the concrete type
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
