package com.example.springcaching.service;

import com.example.springcaching.config.CacheNames;
import com.example.springcaching.dto.ProductRequest;
import com.example.springcaching.dto.ProductResponse;
import com.example.springcaching.model.Category;
import com.example.springcaching.model.Product;
import com.example.springcaching.repository.CategoryRepository;
import com.example.springcaching.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that Spring Cache annotations work as expected.
 *
 * <p>These tests use an H2 in-memory database (test profile) and the real
 * Caffeine {@link CacheManager}, so they exercise the full caching stack
 * without any mocking.
 *
 * <h2>Test strategy</h2>
 * <ol>
 *   <li>Verify that {@code @Cacheable} stores results in the cache after first call.</li>
 *   <li>Verify that a second call does NOT hit the database (cache hit).</li>
 *   <li>Verify that {@code @CachePut} updates the cache on write.</li>
 *   <li>Verify that {@code @CacheEvict} removes entries from the cache.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductServiceCacheTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager caffeineCacheManager;  // primary cache manager

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category savedCategory;

    @BeforeEach
    void setUp() {
        // Clear caches between tests to avoid order-dependency
        caffeineCacheManager.getCacheNames().forEach(name -> {
            Cache cache = caffeineCacheManager.getCache(name);
            if (cache != null) cache.clear();
        });

        // Persist a category for use in tests
        if (categoryRepository.findByName("Test Electronics").isEmpty()) {
            savedCategory = categoryRepository.save(
                    Category.builder().name("Test Electronics").description("Test").build());
        } else {
            savedCategory = categoryRepository.findByName("Test Electronics").get();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @Cacheable tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@Cacheable – result is stored in cache after first call")
    void cacheable_storesResultAfterFirstCall() {
        // Given – a persisted product
        Product product = productRepository.save(Product.builder()
                .name("Cache Test Laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category(savedCategory)
                .build());

        // When – first call (cache miss → DB hit)
        ProductResponse response = productService.getProductById(product.getId());

        // Then – result is now in the cache
        Cache productByIdCache = caffeineCacheManager.getCache(CacheNames.PRODUCT_BY_ID);
        assertThat(productByIdCache).isNotNull();
        Cache.ValueWrapper cached = productByIdCache.get(product.getId());
        assertThat(cached).isNotNull();
        assertThat(((ProductResponse) cached.get()).getName()).isEqualTo("Cache Test Laptop");

        // Clean up
        productRepository.delete(product);
    }

    @Test
    @DisplayName("@Cacheable – second call returns cached result (no DB hit)")
    void cacheable_secondCallReturnsCachedResult() {
        // Given
        Product product = productRepository.save(Product.builder()
                .name("Cache Hit Product")
                .price(new BigDecimal("199.99"))
                .stockQuantity(5)
                .category(savedCategory)
                .build());

        // When – populate cache
        productService.getProductById(product.getId());

        // Manually mutate DB WITHOUT going through service (bypasses @CachePut)
        product.setName("DB Updated Name");
        productRepository.save(product);

        // Then – service still returns the OLD cached name
        ProductResponse cached = productService.getProductById(product.getId());
        assertThat(cached.getName()).isEqualTo("Cache Hit Product");

        // Clean up
        productRepository.deleteById(product.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @CachePut tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@CachePut – cache is updated after createProduct")
    void cachePut_cacheUpdatedAfterCreate() {
        // When
        ProductRequest request = ProductRequest.builder()
                .name("New Widget")
                .price(new BigDecimal("29.99"))
                .stockQuantity(100)
                .categoryId(savedCategory.getId())
                .build();

        ProductResponse created = productService.createProduct(request);

        // Then – created product is in cache
        Cache cache = caffeineCacheManager.getCache(CacheNames.PRODUCT_BY_ID);
        assertThat(cache).isNotNull();
        assertThat(cache.get(created.getId())).isNotNull();

        // Clean up
        productRepository.deleteById(created.getId());
    }

    @Test
    @DisplayName("@CachePut – cache is refreshed after updateProduct")
    void cachePut_cacheRefreshedAfterUpdate() {
        // Given – create and cache a product
        Product product = productRepository.save(Product.builder()
                .name("Old Name")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .category(savedCategory)
                .build());
        productService.getProductById(product.getId()); // populate cache

        // When – update through service (@CachePut)
        ProductRequest updateRequest = ProductRequest.builder()
                .name("New Name")
                .price(new BigDecimal("60.00"))
                .stockQuantity(20)
                .categoryId(savedCategory.getId())
                .build();
        productService.updateProduct(product.getId(), updateRequest);

        // Then – cache has the NEW value
        Cache cache = caffeineCacheManager.getCache(CacheNames.PRODUCT_BY_ID);
        assertThat(cache).isNotNull();
        Cache.ValueWrapper wrapper = cache.get(product.getId());
        assertThat(wrapper).isNotNull();
        assertThat(((ProductResponse) wrapper.get()).getName()).isEqualTo("New Name");

        // Clean up
        productRepository.deleteById(product.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // @CacheEvict tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@CacheEvict – cache entry removed after deleteProduct")
    void cacheEvict_entryRemovedAfterDelete() {
        // Given – product in cache
        Product product = productRepository.save(Product.builder()
                .name("Doomed Product")
                .price(new BigDecimal("9.99"))
                .stockQuantity(1)
                .category(savedCategory)
                .build());
        productService.getProductById(product.getId()); // populate cache

        Cache cache = caffeineCacheManager.getCache(CacheNames.PRODUCT_BY_ID);
        assertThat(cache).isNotNull();
        assertThat(cache.get(product.getId())).isNotNull(); // in cache before delete

        // When
        productService.deleteProduct(product.getId());

        // Then – entry is gone from cache
        assertThat(cache.get(product.getId())).isNull();
    }

    @Test
    @DisplayName("@CacheEvict(allEntries) – all-products cache cleared after create")
    void cacheEvict_allEntriesClearedAfterCreate() {
        // Given – populate 'productsAll' cache
        productService.getAllProducts();
        Cache allCache = caffeineCacheManager.getCache(CacheNames.PRODUCTS_ALL);
        assertThat(allCache).isNotNull();
        assertThat(allCache.get("all")).isNotNull();

        // When – create a new product (should evict 'productsAll')
        ProductRequest request = ProductRequest.builder()
                .name("Cache Evict Test Product")
                .price(new BigDecimal("1.00"))
                .stockQuantity(1)
                .categoryId(savedCategory.getId())
                .build();
        ProductResponse created = productService.createProduct(request);

        // Then – 'all' entry is gone
        assertThat(allCache.get("all")).isNull();

        // Clean up
        productRepository.deleteById(created.getId());
    }
}
