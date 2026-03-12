package com.example.springcaching.service;

import com.example.springcaching.config.CacheNames;
import com.example.springcaching.dto.ProductResponse;
import com.example.springcaching.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Demonstrates <b>Redis-backed distributed caching</b> for product search results.
 *
 * <h2>Why a separate service?</h2>
 * <p>Spring's {@code @Cacheable} routes to the <em>primary</em> {@link org.springframework.cache.CacheManager}
 * by default (Caffeine in this application). To use Redis we explicitly specify
 * {@code cacheManager = "redisCacheManager"} on every annotation.
 *
 * <h2>Use-case</h2>
 * <p>Search results are a good candidate for Redis because:
 * <ul>
 *   <li>They are expensive (full-table scan or search index query).</li>
 *   <li>They must be consistent across multiple application instances.</li>
 *   <li>A short TTL (2 min) is acceptable – users tolerate slightly stale search results.</li>
 * </ul>
 *
 * <h2>Redis key naming</h2>
 * <p>Spring prefixes Redis keys with the cache name: {@code productSearch::laptops}.
 * You can inspect keys with {@code redis-cli KEYS "productSearch*"}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductSearchService {

    private final ProductRepository productRepository;
    private final ProductService productService;

    /**
     * Searches products by name fragment and caches results in <b>Redis</b>.
     *
     * <p>Key: the search query string (lowercased by SpEL {@code #query.toLowerCase()}).
     * TTL: 2 minutes (configured in {@link com.example.springcaching.config.RedisConfig}).
     *
     * <p>If Redis is unavailable, Spring will fall through to the database and
     * log a warning (depending on {@code spring.cache.redis.enable-statistics} setting).
     *
     * @param query partial product name to search for
     * @return matching products
     */
    @Cacheable(
            value = CacheNames.PRODUCT_SEARCH,
            key = "#query.toLowerCase()",
            cacheManager = "redisCacheManager"
    )
    public List<ProductResponse> searchProducts(String query) {
        log.info("Redis Cache MISS – searching database for '{}'", query);
        return productRepository.findByNameContainingIgnoreCase(query)
                .stream()
                .map(productService::toResponse)
                .toList();
    }

    /**
     * Clears the entire product-search cache in Redis.
     *
     * <p>Call this after bulk imports or price updates to ensure users always
     * see fresh search results.
     */
    @CacheEvict(
            value = CacheNames.PRODUCT_SEARCH,
            allEntries = true,
            cacheManager = "redisCacheManager"
    )
    public void clearSearchCache() {
        log.info("Redis Cache EVICT – all product search results cleared");
    }
}
