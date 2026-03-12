package com.example.springcaching.controller;

import com.example.springcaching.dto.ProductRequest;
import com.example.springcaching.dto.ProductResponse;
import com.example.springcaching.service.ProductSearchService;
import com.example.springcaching.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for products.
 *
 * <p>Cache behaviour is fully transparent to this controller – all caching logic
 * lives in {@link ProductService} and {@link ProductSearchService}.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product CRUD + search with caching")
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    // ── GET all ──────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "Get all products",
            description = "Results are cached in Caffeine under key 'all'. " +
                    "Cache is evicted on any create/update/delete."
    )
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // ── GET by ID ────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a product by ID",
            description = "Cached in Caffeine under key = product ID. " +
                    "@CachePut refreshes the entry on updates."
    )
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable @Parameter(description = "Product ID") Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // ── GET by category ───────────────────────────────────────────────────────

    @GetMapping("/category/{categoryId}")
    @Operation(
            summary = "Get products by category",
            description = "Cached per category ID. Evicted when any product in that category changes."
    )
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
    }

    // ── GET search (Redis) ────────────────────────────────────────────────────

    @GetMapping("/search")
    @Operation(
            summary = "Search products by name (Redis cache)",
            description = "Results cached in Redis (distributed). TTL = 2 minutes. " +
                    "Use ?query=laptop to search."
    )
    public ResponseEntity<List<ProductResponse>> searchProducts(
            @RequestParam @Parameter(description = "Partial product name") String query) {
        return ResponseEntity.ok(productSearchService.searchProducts(query));
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(
            summary = "Create a product",
            description = "Stores the new product in cache via @CachePut. " +
                    "List caches are evicted."
    )
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(request));
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a product",
            description = "Uses @CachePut to refresh the cache entry for this product."
    )
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a product",
            description = "Uses @CacheEvict to remove the product from all relevant caches."
    )
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ── Cache management ──────────────────────────────────────────────────────

    @DeleteMapping("/search/cache")
    @Operation(
            summary = "Clear Redis search cache",
            description = "Evicts all search results from Redis. " +
                    "Useful after bulk data imports."
    )
    public ResponseEntity<Void> clearSearchCache() {
        productSearchService.clearSearchCache();
        return ResponseEntity.noContent().build();
    }
}
