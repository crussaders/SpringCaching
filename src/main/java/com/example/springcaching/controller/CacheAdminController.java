package com.example.springcaching.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility controller that exposes cache management operations.
 *
 * <p>In production, use Spring Boot Actuator's {@code /actuator/caches} endpoint
 * instead. This controller exists purely for the demo / educational purpose.
 */
@RestController
@RequestMapping("/api/v1/cache-admin")
@RequiredArgsConstructor
@Tag(name = "Cache Admin", description = "Inspect and clear Spring caches")
public class CacheAdminController {

    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;

    /**
     * Lists all cache names managed by each cache manager.
     */
    @GetMapping
    @Operation(summary = "List all cache names")
    public ResponseEntity<Map<String, Collection<String>>> listCaches() {
        Map<String, Collection<String>> info = new HashMap<>();
        info.put("caffeine", caffeineCacheManager.getCacheNames());
        info.put("redis", redisCacheManager.getCacheNames());
        return ResponseEntity.ok(info);
    }

    /**
     * Clears ALL entries in ALL Caffeine-managed caches.
     *
     * <p><b>⚠ Use with caution in production</b> – this causes a thundering-herd
     * problem if every request suddenly misses the cache at the same time.
     */
    @DeleteMapping("/caffeine")
    @Operation(summary = "Clear all Caffeine caches")
    public ResponseEntity<Void> clearAllCaffeineCaches() {
        caffeineCacheManager.getCacheNames().forEach(name -> {
            var cache = caffeineCacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears ALL entries in ALL Redis-managed caches.
     */
    @DeleteMapping("/redis")
    @Operation(summary = "Clear all Redis caches")
    public ResponseEntity<Void> clearAllRedisCaches() {
        redisCacheManager.getCacheNames().forEach(name -> {
            var cache = redisCacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears a specific cache by name from a specific manager.
     *
     * @param manager  {@code caffeine} or {@code redis}
     * @param cacheName name of the cache to clear
     */
    @DeleteMapping("/{manager}/{cacheName}")
    @Operation(summary = "Clear a specific cache")
    public ResponseEntity<String> clearSpecificCache(
            @PathVariable String manager,
            @PathVariable String cacheName) {

        CacheManager cm = "redis".equalsIgnoreCase(manager) ? redisCacheManager : caffeineCacheManager;
        var cache = cm.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }
        cache.clear();
        return ResponseEntity.ok("Cache '" + cacheName + "' cleared from " + manager);
    }
}
