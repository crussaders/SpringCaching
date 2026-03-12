package com.example.springcaching.service;

import com.example.springcaching.config.CacheNames;
import com.example.springcaching.dto.CategoryRequest;
import com.example.springcaching.dto.CategoryResponse;
import com.example.springcaching.dto.ProductResponse;
import com.example.springcaching.exception.ResourceNotFoundException;
import com.example.springcaching.model.Category;
import com.example.springcaching.repository.CategoryRepository;
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
 * Category service demonstrating Spring Cache annotations.
 *
 * <p>Categories change infrequently, making them excellent caching candidates.
 * This service uses the primary (Caffeine) cache manager, but the concepts are
 * identical for any cache provider.
 *
 * <h2>Condition and Unless</h2>
 * <p>Spring supports two filtering expressions on cache annotations:
 * <ul>
 *   <li>{@code condition} – evaluated <em>before</em> the method; cache is bypassed when {@code false}.</li>
 *   <li>{@code unless}    – evaluated <em>after</em> the method; result is NOT cached when {@code true}.
 *       Useful for skipping null or error results.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductService productService;

    // ─────────────────────────────────────────────────────────────────────────
    // READ  –  @Cacheable
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches a category by ID.
     *
     * <p>Uses {@code unless = "#result == null"} to prevent caching null returns –
     * although in this case we throw instead of returning null, the pattern is shown
     * for educational purposes.
     */
    @Cacheable(value = CacheNames.CATEGORY_BY_ID, key = "#id", unless = "#result == null")
    public CategoryResponse getCategoryById(Long id) {
        log.info("Cache MISS – fetching category {} from database", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return toResponse(category, true);
    }

    /**
     * Returns all categories.
     *
     * <p>Demonstrates {@code condition} – only cache when there is at least one
     * category (avoids caching an empty list on first startup before data is loaded).
     */
    @Cacheable(value = CacheNames.CATEGORIES_ALL, key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        log.info("Cache MISS – fetching all categories from database");
        return categoryRepository.findAll()
                .stream()
                .map(c -> toResponse(c, false))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE  –  @CachePut / @CacheEvict
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @Caching(
            put  = { @CachePut(value = CacheNames.CATEGORY_BY_ID, key = "#result.id") },
            evict = { @CacheEvict(value = CacheNames.CATEGORIES_ALL, allEntries = true) }
    )
    public CategoryResponse createCategory(CategoryRequest request) {
        log.info("Creating category: {}", request.getName());
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        Category saved = categoryRepository.save(category);
        log.info("Category created id={}, cache updated", saved.getId());
        return toResponse(saved, false);
    }

    @Transactional
    @Caching(
            put  = { @CachePut(value = CacheNames.CATEGORY_BY_ID, key = "#id") },
            evict = { @CacheEvict(value = CacheNames.CATEGORIES_ALL, allEntries = true) }
    )
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        log.info("Updating category id={}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        Category saved = categoryRepository.save(category);
        log.info("Category id={} updated, cache refreshed", saved.getId());
        return toResponse(saved, false);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.CATEGORY_BY_ID, key = "#id"),
            @CacheEvict(value = CacheNames.CATEGORIES_ALL, allEntries = true)
    })
    public void deleteCategory(Long id) {
        log.info("Deleting category id={}, cache evicted", id);
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", id);
        }
        categoryRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CategoryResponse toResponse(Category c, boolean includeProducts) {
        List<ProductResponse> products = includeProducts
                ? c.getProducts().stream().map(productService::toResponse).toList()
                : List.of();

        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .productCount(c.getProducts().size())
                .products(products)
                .build();
    }
}
