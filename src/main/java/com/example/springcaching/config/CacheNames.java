package com.example.springcaching.config;

/**
 * Central registry of all cache name constants.
 *
 * <p>Using constants prevents typos in {@code @Cacheable}, {@code @CacheEvict}, etc.
 */
public final class CacheNames {

    // ─────────────────────────────────────────────────────────────────────────
    // Caffeine-backed caches  (local, in-process, TTL-based)
    // ─────────────────────────────────────────────────────────────────────────

    /** Single product by ID – expires 10 minutes after write, max 500 entries. */
    public static final String PRODUCT_BY_ID = "productById";

    /** All products list – expires 5 minutes after write, max 1 entry. */
    public static final String PRODUCTS_ALL = "productsAll";

    /** Products by category – expires 5 minutes after write, max 200 entries. */
    public static final String PRODUCTS_BY_CATEGORY = "productsByCategory";

    // ─────────────────────────────────────────────────────────────────────────
    // EhCache-backed caches  (local, JSR-107, configurable persistence)
    // ─────────────────────────────────────────────────────────────────────────

    /** Single category by ID – managed by EhCache. */
    public static final String CATEGORY_BY_ID = "categoryById";

    /** All categories list – managed by EhCache. */
    public static final String CATEGORIES_ALL = "categoriesAll";

    // ─────────────────────────────────────────────────────────────────────────
    // Redis-backed caches  (distributed, out-of-process, TTL-based)
    // ─────────────────────────────────────────────────────────────────────────

    /** Product search results – stored in Redis for distributed access. */
    public static final String PRODUCT_SEARCH = "productSearch";

    private CacheNames() { /* utility class */ }
}
