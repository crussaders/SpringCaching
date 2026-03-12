package com.example.springcaching.service;

import com.example.springcaching.config.CacheNames;
import com.example.springcaching.dto.ProductRequest;
import com.example.springcaching.dto.ProductResponse;
import com.example.springcaching.exception.ResourceNotFoundException;
import com.example.springcaching.model.Category;
import com.example.springcaching.model.Product;
import com.example.springcaching.repository.CategoryRepository;
import com.example.springcaching.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Product service demonstrating Spring Cache annotations backed by <b>Caffeine</b>
 * (the primary {@code CacheManager}).
 *
 * <h2>Annotation quick-reference</h2>
 * <ul>
 *   <li>{@code @Cacheable} – check the cache first; invoke method only on a miss,
 *       then store the result.</li>
 *   <li>{@code @CachePut}  – <em>always</em> invoke the method and update the cache
 *       with the new value (useful for updates).</li>
 *   <li>{@code @CacheEvict} – remove one or all entries from a cache (useful for
 *       deletes or stale-data invalidation).</li>
 *   <li>{@code @Caching}   – group multiple cache operations on a single method.</li>
 * </ul>
 *
 * <h2>Cache key generation</h2>
 * <p>Spring uses a {@link org.springframework.cache.interceptor.KeyGenerator} to build
 * cache keys from method arguments. The default generator uses all parameters.
 * Custom keys use SpEL expressions: {@code #id}, {@code #request.name}, etc.
 *
 * <h2>Caffeine vs Redis</h2>
 * <p>This service uses the default (Caffeine) cache manager – ideal for single-instance
 * deployments. The {@link ProductSearchService} shows the same operations routed to
 * Redis for distributed use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // READ operations – @Cacheable
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a product by its ID.
     *
     * <p><b>@Cacheable</b>: On the first call with a given {@code id} the method
     * executes and the result is stored under key {@code id} in the
     * {@code productById} cache.  Subsequent calls with the same {@code id}
     * return the cached value <em>without</em> hitting the database.
     *
     * <p>The SpEL expression {@code "#id"} extracts the method parameter value as
     * the cache key.
     *
     * @param id product ID
     * @return product data
     */
    @Cacheable(value = CacheNames.PRODUCT_BY_ID, key = "#id")
    public ProductResponse getProductById(Long id) {
        log.info("Cache MISS – fetching product {} from database", id);
        Product product = productRepository.findByIdWithCategory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return toResponse(product);
    }

    /**
     * Returns all products.
     *
     * <p><b>@Cacheable</b> with a fixed key {@code "'all'"} (note the single quotes
     * inside the SpEL string literal) – the entire list is stored as one cache entry.
     * This is a good pattern for small reference datasets that rarely change.
     */
    @Cacheable(value = CacheNames.PRODUCTS_ALL, key = "'all'")
    public List<ProductResponse> getAllProducts() {
        log.info("Cache MISS – fetching all products from database");
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns products belonging to a specific category.
     *
     * <p><b>@Cacheable</b> with key {@code "#categoryId"} – one cache entry per
     * category ID, so different categories never share the same slot.
     */
    @Cacheable(value = CacheNames.PRODUCTS_BY_CATEGORY, key = "#categoryId")
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.info("Cache MISS – fetching products for category {} from database", categoryId);
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
        return productRepository.findByCategoryId(categoryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE operations – @CachePut & @CacheEvict
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new product.
     *
     * <p><b>@Caching</b> groups multiple annotations:
     * <ol>
     *   <li>{@code @CachePut} on {@code productById} – immediately stores the new
     *       product so the next {@code getProductById} call is a cache HIT.</li>
     *   <li>{@code @CacheEvict} on {@code productsAll} – invalidates the "all
     *       products" list so it is refreshed on the next read.</li>
     *   <li>{@code @CacheEvict} on {@code productsByCategory} – invalidates the
     *       per-category list for the category of the new product.</li>
     * </ol>
     */
    @Transactional
    @Caching(
            put  = { @CachePut(value = CacheNames.PRODUCT_BY_ID, key = "#result.id") },
            evict = {
                    @CacheEvict(value = CacheNames.PRODUCTS_ALL, allEntries = true),
                    @CacheEvict(value = CacheNames.PRODUCTS_BY_CATEGORY, key = "#request.categoryId")
            }
    )
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating product: {}", request.getName());
        Product product = toEntity(request);
        Product saved = productRepository.save(product);
        log.info("Product created with id={}, cache updated", saved.getId());
        return toResponse(saved);
    }

    /**
     * Updates an existing product.
     *
     * <p><b>@CachePut</b> – always executes the method (unlike {@code @Cacheable})
     * and writes the fresh result back to the cache. This ensures the cache is never
     * stale after an update.
     *
     * <p>We also evict list caches because the updated product may affect list views.
     */
    @Transactional
    @Caching(
            put  = { @CachePut(value = CacheNames.PRODUCT_BY_ID, key = "#id") },
            evict = {
                    @CacheEvict(value = CacheNames.PRODUCTS_ALL, allEntries = true),
                    @CacheEvict(value = CacheNames.PRODUCTS_BY_CATEGORY, allEntries = true)
            }
    )
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        log.info("Updating product id={}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Product id={} updated, cache refreshed", saved.getId());
        return toResponse(saved);
    }

    /**
     * Deletes a product.
     *
     * <p><b>@Caching + @CacheEvict</b> – removes the individual entry from
     * {@code productById} and invalidates the two list caches.
     * {@code allEntries = true} nukes <em>all</em> entries in that cache region
     * rather than just one key.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_BY_ID, key = "#id"),
            @CacheEvict(value = CacheNames.PRODUCTS_ALL, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCTS_BY_CATEGORY, allEntries = true)
    })
    public void deleteProduct(Long id) {
        log.info("Deleting product id={}, cache evicted", id);
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", id);
        }
        productRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Product toEntity(ProductRequest req) {
        Category category = req.getCategoryId() != null
                ? categoryRepository.findById(req.getCategoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()))
                : null;

        return Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .price(req.getPrice())
                .stockQuantity(req.getStockQuantity() != null ? req.getStockQuantity() : 0)
                .category(category)
                .build();
    }

    ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
