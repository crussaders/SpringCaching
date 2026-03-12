# Spring Boot Caching Demo

> A production-grade Spring Boot application that demonstrates **all major caching strategies** used in real-world Java applications, backed by **Oracle Database** with Flyway migrations, **Redis**, and **Caffeine**.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [What Is Caching?](#3-what-is-caching)
4. [Types of Caching Demonstrated](#4-types-of-caching-demonstrated)
5. [Spring Cache Annotations](#5-spring-cache-annotations)
6. [Project Structure](#6-project-structure)
7. [Getting Started](#7-getting-started)
8. [API Reference](#8-api-reference)
9. [Database Schema & Flyway](#9-database-schema--flyway)
10. [Cache Configuration Details](#10-cache-configuration-details)
11. [Observability – Actuator & Metrics](#11-observability--actuator--metrics)
12. [Interview Questions & Answers](#12-interview-questions--answers)

---

## 1. Project Overview

| Attribute         | Value                                                   |
|-------------------|---------------------------------------------------------|
| Framework         | Spring Boot 3.2.x                                       |
| Java              | 17                                                      |
| Database          | Oracle Database Free 23c (Docker) / H2 (dev/test)       |
| Migrations        | Flyway 10                                               |
| Primary Cache     | Caffeine 3 (local, single-instance)                     |
| Distributed Cache | Redis 7 (multi-instance)                                |
| Secondary Cache   | EhCache 3 (JSR-107, enterprise-grade local cache)       |
| API Docs          | SpringDoc / Swagger UI                                  |
| Metrics           | Spring Boot Actuator                                    |

The domain model is a simple **Product Catalogue**: `Category` → `Product` (one-to-many). CRUD operations on both entities are fully cached, making it easy to observe each cache annotation in action via Swagger UI.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────┐
│                   HTTP Client / Browser               │
└─────────────────────────┬────────────────────────────┘
                          │
            ┌─────────────▼─────────────┐
            │    Spring MVC Controllers  │
            └─────────────┬─────────────┘
                          │
           ┌──────────────▼──────────────┐
           │    Service Layer (cached)    │
           │  ProductService       ───────►  Caffeine CacheManager (primary)
           │  CategoryService      ───────►  Caffeine CacheManager (primary)
           │  ProductSearchService ───────►  Redis CacheManager (distributed)
           └──────────────┬──────────────┘
                          │
           ┌──────────────▼──────────────┐
           │   Spring Data JPA           │
           └──────────────┬──────────────┘
                          │
         ┌────────────────▼────────────────┐
         │  Oracle DB (via Docker)          │
         │  H2 (test/dev profile)           │
         │  Flyway migrations               │
         └─────────────────────────────────┘

External: Redis 7  ──► Distributed search-result cache
```

---

## 3. What Is Caching?

**Caching** is the technique of storing the result of an expensive operation in fast-access storage so that subsequent requests for the same result are served much faster without repeating the expensive work.

### Cache hit vs miss

```
Request ──► Cache lookup
               │
         ┌─────┴─────┐
         │ Cache HIT  │  → Return cached value immediately (fast path)
         └─────┬─────┘
               │ no entry
         ┌─────▼─────┐
         │ Cache MISS │  → Execute method, store result, return value
         └───────────┘
```

### Cache invalidation strategies

1. **TTL (Time-To-Live)** – automatically expire entries after a fixed duration.
2. **Write-through** – update the cache every time the underlying data changes.
3. **Explicit eviction** – programmatically remove entries on delete/update.

This demo uses all three strategies.

---

## 4. Types of Caching Demonstrated

### 4.1 Spring Cache Abstraction

Spring's **cache abstraction** (`spring-boot-starter-cache`) provides a unified programming model on top of any cache implementation. You write `@Cacheable`, `@CachePut`, and `@CacheEvict` on service methods, and Spring handles the rest. This decouples your business logic from the cache technology.

### 4.2 Caffeine Cache (Local, In-Process)

**[Caffeine](https://github.com/ben-manes/caffeine)** is a high-performance, in-memory caching library for Java. It uses the **W-TinyLFU** eviction policy – a near-optimal admission algorithm that tracks both recency and frequency of access.

**Configured caches:**

| Cache                 | Max Entries | TTL        |
|-----------------------|-------------|------------|
| `productById`         | 1 000       | 10 minutes |
| `productsAll`         | 1 000       | 10 minutes |
| `productsByCategory`  | 1 000       | 10 minutes |
| `categoryById`        | 1 000       | 10 minutes |
| `categoriesAll`       | 1 000       | 10 minutes |

**When to use:** Single-instance applications (monolith / single-node microservice) where low latency and high throughput are needed without network hops.

```java
@Bean @Primary
public CacheManager caffeineCacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats());
    return manager;
}
```

### 4.3 EhCache 3 (Local, JSR-107)

**[EhCache](https://www.ehcache.org/)** is an enterprise-grade, open-source caching library implementing JSR-107 (JCache – the standard Java caching API).

**Key differentiators vs Caffeine:**
- **Multiple storage tiers:** heap → off-heap → disk (can survive JVM restarts).
- **JSR-107 compliance** – interchangeable with any JCache provider.
- **Fine-grained XML/programmatic configuration per cache.**

```
EhCache Tiered Storage:
  Heap (fast, GC-managed)
     ↓ overflow
  Off-Heap (fast, not GC-managed)
     ↓ overflow
  Disk (persistent)
```

Configured in `src/main/resources/ehcache.xml`. Services explicitly request it:
```java
@Cacheable(value = "ehcache-products-by-id", cacheManager = "jCacheCacheManager")
```

**When to use:** Applications needing off-heap storage, disk persistence, or fine-grained JSR-107 compliance.

### 4.4 Redis Cache (Distributed)

**[Redis](https://redis.io/)** is an open-source, in-memory key-value store. For caching it acts as a **shared external cache** accessible by every application instance.

**Why distributed caching?**
When multiple instances run behind a load balancer, local in-memory caches (Caffeine/EhCache heap) are only visible within the JVM that populated them. Redis solves this:

```
┌─────────────────────────────────────────────────────┐
│                  Load Balancer                       │
└──────────┬──────────────────────┬───────────────────┘
           │                      │
   ┌───────▼──────┐        ┌──────▼───────┐
   │  App Instance│        │  App Instance│
   │      A       │        │      B       │
   └───────┬──────┘        └──────┬───────┘
           └──────────┬───────────┘
                      │
              ┌───────▼───────┐
              │     Redis     │  ← shared distributed cache
              └───────────────┘
```

**Configured caches:**

| Cache           | Backend | TTL       |
|-----------------|---------|-----------|
| `productSearch` | Redis   | 2 minutes |

Services route to Redis with `cacheManager = "redisCacheManager"`:
```java
@Cacheable(value = "productSearch", key = "#query.toLowerCase()",
           cacheManager = "redisCacheManager")
```

**When to use:** Multi-instance deployments (microservices, horizontally-scaled apps).

---

## 5. Spring Cache Annotations

### 5.1 @Cacheable

Checks the cache before invoking the method. Returns the cached value on a hit; on a miss, executes the method and stores the result.

```java
@Cacheable(value = "productById", key = "#id")
public ProductResponse getProductById(Long id) {
    // Runs ONLY on cache miss
    return productRepository.findByIdWithCategory(id)...;
}
```

**SpEL key expressions:**
```java
key = "#id"                     // single parameter
key = "#request.categoryId"    // nested property
key = "'all'"                   // fixed string literal
key = "#query.toLowerCase()"   // method call on parameter
```

**Filtering:**
```java
condition = "#id > 0"          // skip caching when false (evaluated BEFORE)
unless = "#result == null"     // skip storing when true (evaluated AFTER)
```

### 5.2 @CachePut

**Always** invokes the method and writes the result to the cache. Use this for UPDATE operations to keep the cache fresh.

```java
@CachePut(value = "productById", key = "#id")
public ProductResponse updateProduct(Long id, ProductRequest request) {
    // Always executes AND updates cache
    ...
}
```

> **Key difference from @Cacheable:** `@Cacheable` skips the method on a cache hit. `@CachePut` always runs the method.

### 5.3 @CacheEvict

Removes entries from the cache. Use after DELETE operations.

```java
@CacheEvict(value = "productById", key = "#id")          // evict single entry
@CacheEvict(value = "productsAll", allEntries = true)     // evict ALL entries
@CacheEvict(value = "productsAll", beforeInvocation = true) // evict BEFORE method
```

### 5.4 @Caching

Groups multiple cache annotations on a single method.

```java
@Caching(
    put   = { @CachePut(value = "productById", key = "#result.id") },
    evict = {
        @CacheEvict(value = "productsAll", allEntries = true),
        @CacheEvict(value = "productsByCategory", key = "#request.categoryId")
    }
)
public ProductResponse createProduct(ProductRequest request) { ... }
```

---

## 6. Project Structure

```
spring-caching-demo/
├── src/
│   ├── main/
│   │   ├── java/com/example/springcaching/
│   │   │   ├── SpringCachingApplication.java        # Entry point (@EnableCaching)
│   │   │   ├── config/
│   │   │   │   ├── CacheNames.java                  # Cache name constants
│   │   │   │   ├── CaffeineCacheConfig.java          # Caffeine (primary) setup
│   │   │   │   ├── RedisConfig.java                  # Redis setup + RedisTemplate
│   │   │   │   ├── FallbackCacheConfig.java          # In-memory fallback (no Redis)
│   │   │   │   └── OpenApiConfig.java                # Swagger config
│   │   │   ├── controller/
│   │   │   │   ├── ProductController.java
│   │   │   │   ├── CategoryController.java
│   │   │   │   └── CacheAdminController.java         # Cache inspection/clear
│   │   │   ├── service/
│   │   │   │   ├── ProductService.java               # Caffeine cache (CRUD)
│   │   │   │   ├── CategoryService.java              # Caffeine cache (CRUD)
│   │   │   │   └── ProductSearchService.java         # Redis cache (search)
│   │   │   ├── repository/, model/, dto/, exception/
│   │   └── resources/
│   │       ├── application.yml                       # Default (H2 + Caffeine)
│   │       ├── application-oracle.yml                # Oracle profile
│   │       ├── ehcache.xml                           # EhCache 3 configuration
│   │       └── db/migration/
│   │           ├── V1__create_schema.sql
│   │           └── V2__seed_data.sql
│   └── test/ ...
├── docker/oracle-init/01_create_user.sql             # Oracle user init script
├── docker-compose.yml                               # Full stack (Oracle+Redis+App)
├── docker-compose-dev.yml                           # Dev (H2+Redis+App)
├── Dockerfile                                       # Multi-stage build
└── pom.xml
```

---

## 7. Getting Started

### 7.1 Prerequisites

| Tool           | Version |
|----------------|---------|
| Java           | 17+     |
| Maven          | 3.9+    |
| Docker         | 24+     |
| Docker Compose | 2.20+   |

### 7.2 Run with H2 + Redis (Development – fastest)

```bash
# Start Redis only
docker compose -f docker-compose-dev.yml up -d redis

# Run app (uses H2 in-memory DB by default)
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console  
  JDBC URL: `jdbc:h2:mem:springcachingdb`

### 7.3 Run Full Stack with Oracle + Redis

> ⚠️ Oracle Free requires ~2 GB RAM. First startup takes 60–90 seconds.

```bash
# Pull Oracle image (accept licence on registry.oracle.com)
docker login container-registry.oracle.com
docker pull container-registry.oracle.com/database/free:latest

# Start everything
docker compose up -d --build

# Watch logs
docker compose logs -f oracle-db   # wait for "DATABASE IS READY"
docker compose logs -f app
```

Swagger UI: http://localhost:8080/swagger-ui.html

### 7.4 Environment Variables

| Variable          | Default        | Description              |
|-------------------|----------------|--------------------------|
| `ORACLE_HOST`     | `localhost`    | Oracle DB hostname        |
| `ORACLE_PORT`     | `1521`         | Oracle listener port      |
| `ORACLE_SERVICE`  | `FREEPDB1`     | Oracle PDB name           |
| `ORACLE_USER`     | `caching_user` | App DB username           |
| `ORACLE_PASSWORD` | `Caching123`   | App DB password           |
| `REDIS_HOST`      | `localhost`    | Redis hostname            |
| `REDIS_PORT`      | `6379`         | Redis port                |

---

## 8. API Reference

Full interactive docs: **http://localhost:8080/swagger-ui.html**

### Products

| Method | Path                              | Cache Action                    |
|--------|-----------------------------------|---------------------------------|
| GET    | `/api/v1/products`                | `@Cacheable` Caffeine           |
| GET    | `/api/v1/products/{id}`           | `@Cacheable` Caffeine           |
| GET    | `/api/v1/products/category/{id}`  | `@Cacheable` Caffeine           |
| GET    | `/api/v1/products/search?query=x` | `@Cacheable` **Redis**          |
| POST   | `/api/v1/products`                | `@CachePut` + `@CacheEvict`     |
| PUT    | `/api/v1/products/{id}`           | `@CachePut` + `@CacheEvict`     |
| DELETE | `/api/v1/products/{id}`           | `@CacheEvict`                   |
| DELETE | `/api/v1/products/search/cache`   | `@CacheEvict` Redis (all)       |

### Categories

| Method | Path                         | Cache Action                    |
|--------|------------------------------|---------------------------------|
| GET    | `/api/v1/categories`         | `@Cacheable` Caffeine           |
| GET    | `/api/v1/categories/{id}`    | `@Cacheable` Caffeine           |
| POST   | `/api/v1/categories`         | `@CachePut` + `@CacheEvict`     |
| PUT    | `/api/v1/categories/{id}`    | `@CachePut` + `@CacheEvict`     |
| DELETE | `/api/v1/categories/{id}`    | `@CacheEvict`                   |

### Cache Admin

| Method | Path                                | Description                  |
|--------|-------------------------------------|------------------------------|
| GET    | `/api/v1/cache-admin`               | List all cache names         |
| DELETE | `/api/v1/cache-admin/caffeine`      | Clear all Caffeine caches    |
| DELETE | `/api/v1/cache-admin/redis`         | Clear all Redis caches       |
| DELETE | `/api/v1/cache-admin/{mgr}/{name}`  | Clear a specific named cache |

### Actuator

| Path                                                              | Description               |
|-------------------------------------------------------------------|---------------------------|
| `/actuator/health`                                                | Health (DB, Redis, etc.)  |
| `/actuator/caches`                                                | List all Spring caches    |
| `/actuator/metrics/cache.gets?tag=cache:productById&tag=result:hit` | Cache hit count         |

---

## 9. Database Schema & Flyway

### Why Flyway?

Flyway versions schema changes in SQL migration files. Benefits:
- Tracked in `flyway_schema_history` table – know exactly what ran when.
- Same SQL on developer laptops, CI, and production.
- Works with Oracle, H2, PostgreSQL, MySQL, and more.

### Migrations

| File                    | Description                          |
|-------------------------|--------------------------------------|
| `V1__create_schema.sql` | Creates `categories` + `products` tables and sequences |
| `V2__seed_data.sql`     | Inserts 4 categories and 11 products |

### Schema (Oracle / H2-compatible)

```sql
CREATE TABLE categories (
    id          NUMBER(19)    PRIMARY KEY,
    name        VARCHAR2(100) NOT NULL UNIQUE,
    description VARCHAR2(500)
);

CREATE TABLE products (
    id             NUMBER(19)    PRIMARY KEY,
    name           VARCHAR2(200) NOT NULL,
    description    VARCHAR2(1000),
    price          NUMBER(10,2)  NOT NULL,
    stock_quantity NUMBER(10)    DEFAULT 0,
    category_id    NUMBER(19)    REFERENCES categories(id),
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
```

---

## 10. Cache Configuration Details

### Multiple CacheManagers

| Bean Name              | Backed By    | Default? |
|------------------------|--------------|----------|
| `caffeineCacheManager` | Caffeine     | ✅ Yes (`@Primary`) |
| `redisCacheManager`    | Redis        | ❌ No (explicit) |

Services use the primary cache manager unless they specify otherwise:
```java
// Uses caffeineCacheManager (default)
@Cacheable(value = "productById", key = "#id")

// Uses redisCacheManager explicitly
@Cacheable(value = "productSearch", cacheManager = "redisCacheManager")
```

### Redis key format

```
cacheName::cacheKey
productSearch::laptop
productSearch::phone
```

### Caffeine statistics

Enabled via `.recordStats()`. View via Actuator:
```bash
curl "http://localhost:8080/actuator/metrics/cache.gets?tag=cache:productById&tag=result:hit"
curl "http://localhost:8080/actuator/metrics/cache.gets?tag=cache:productById&tag=result:miss"
```

---

## 11. Observability – Actuator & Metrics

| Metric           | Description                             |
|------------------|-----------------------------------------|
| `cache.gets`     | Total GET ops (tagged by cache + result)|
| `cache.puts`     | Total PUT operations                    |
| `cache.evictions`| Total eviction operations               |
| `cache.size`     | Current number of entries               |

```bash
# List all caches
curl http://localhost:8080/actuator/caches

# Full metrics list
curl http://localhost:8080/actuator/metrics | grep cache
```

---

## 12. Interview Questions & Answers

### Core Caching Concepts

**Q1. What is caching and why is it used?**
Caching stores results of expensive operations in fast-access memory. Subsequent requests for the same result are served from cache without repeating the work. Benefits: reduced latency, lower DB load, higher throughput.

---

**Q2. What is the difference between a cache hit and a cache miss?**
A **cache hit** means the data was found in cache – no underlying operation needed. A **cache miss** means the data was not in cache – the underlying operation runs, the result is stored, then returned.

---

**Q3. What is cache eviction and what policies exist?**
Eviction is removal of cache entries to free space or because data is stale:
- **LRU** (Least Recently Used) – evict oldest-accessed entry.
- **LFU** (Least Frequently Used) – evict least-accessed entry.
- **TTL** (Time-To-Live) – expire after a fixed duration from write.
- **TTI** (Time-To-Idle) – expire after a fixed duration of inactivity.
- **W-TinyLFU** (Caffeine) – near-optimal hybrid of recency + frequency.

---

**Q4. What is the difference between TTL and TTI?**
- **TTL**: Entry expires a fixed time *after being written*, regardless of access.
- **TTI**: Entry expires a fixed time *after the last access*. Frequently accessed entries never expire under TTI.

---

**Q5. What is cache stampede / thundering herd?**
When a popular cache entry expires, many concurrent requests all miss the cache and simultaneously hammer the backend to recompute the value. Prevention: probabilistic early expiration, request coalescing, background refresh, or short-extending stale TTL.

---

**Q6. What cache invalidation strategies exist?**
- **Cache-aside** (lazy loading): check cache first; miss → load from DB → populate cache.
- **Write-through**: update cache and DB simultaneously.
- **Write-behind** (write-back): update cache immediately; DB updated asynchronously.
- **Refresh-ahead**: proactively refresh cache entries before expiry.

---

### Spring Cache Abstraction

**Q7. How do you enable caching in Spring Boot?**
Add `spring-boot-starter-cache` dependency and annotate with `@EnableCaching` (typically on the main class or a `@Configuration` class).

---

**Q8. What are the main Spring Cache annotations?**
- `@Cacheable` – return cached value if present; skip method on hit.
- `@CachePut` – always invoke method and update cache.
- `@CacheEvict` – remove one or all entries from the cache.
- `@Caching` – combine multiple cache annotations on one method.
- `@CacheConfig` – class-level defaults for cache names/key generators.

---

**Q9. What is the difference between @Cacheable and @CachePut?**
Both store results in the cache. `@Cacheable` *short-circuits*: if a cached value exists, the method body is **skipped**. `@CachePut` **always executes** the method body and then writes the result to cache. Use `@CachePut` for update operations.

---

**Q10. How does Spring generate cache keys by default?**
Using `SimpleKeyGenerator`:
- No parameters → `SimpleKey.EMPTY`
- One parameter → the parameter value itself
- Multiple parameters → `SimpleKey(p1, p2, ...)`

Custom keys use SpEL: `key = "#id"`, `key = "#user.name"`, `key = "'static'"`.

---

**Q11. What does `allEntries = true` mean in @CacheEvict?**
Removes ALL entries in the named cache rather than just the entry matching the current key. Use when a write operation could affect many cached entries (e.g., creating a product invalidates the full "all products" list).

---

**Q12. What is the `unless` attribute in @Cacheable?**
A SpEL expression evaluated *after* the method executes. If `true`, the result is **not** stored in cache. Example: `unless = "#result == null"` prevents caching null values.

---

**Q13. What is the `condition` attribute in @Cacheable?**
A SpEL expression evaluated *before* the method executes. If `false`, caching is skipped entirely (no check, no store). Example: `condition = "#id > 0"` – skip caching for non-positive IDs.

---

**Q14. How do you use multiple CacheManagers in Spring?**
Mark one as `@Primary` for the default. Specify others explicitly per annotation:
```java
@Cacheable(value = "myCache", cacheManager = "redisCacheManager")
```

---

**Q15. Why must cached objects implement Serializable?**
Caches that store data outside the JVM heap (Redis, EhCache disk/off-heap) must serialize objects to bytes. Java serialization requires `implements Serializable`. Heap-only caches (Caffeine in-memory) work without it.

---

### Caffeine Cache

**Q16. What is Caffeine and how does it differ from Guava Cache?**
Caffeine is Guava Cache's successor, rewritten for Java 8+. Key improvements: W-TinyLFU eviction (vs LRU in Guava), asynchronous loading/refreshing, higher throughput via lock-free internals, and built-in statistics.

---

**Q17. What is W-TinyLFU (Caffeine's eviction policy)?**
W-TinyLFU combines a small *window* LRU cache for new items with a *main* LFU-based region for frequent items. A frequency sketch (TinyLFU) decides whether a window item should replace a main-region item. This achieves near-optimal hit rates across diverse access patterns.

---

**Q18. How do you configure per-cache TTL and size in Caffeine?**
Register individual `CaffeineCache` beans or use `CaffeineCacheManager#registerCustomCache`:
```java
return new CaffeineCache("productById",
    Caffeine.newBuilder().maximumSize(500).expireAfterWrite(10, MINUTES).build());
```

---

### Redis Cache

**Q19. What is Redis and why use it for distributed caching?**
Redis is an in-memory key-value store with sub-millisecond latency. It supports TTL natively, rich data structures, persistence, replication, and clustering. For caching, it acts as a shared external store accessible by all application instances simultaneously.

---

**Q20. How does Spring Boot integrate with Redis for caching?**
Add `spring-boot-starter-data-redis`. Spring Boot auto-configures a `LettuceConnectionFactory`. Build a `RedisCacheManager` with `RedisCacheConfiguration` for TTL and serialization, then use `@Cacheable(cacheManager = "redisCacheManager")`.

---

**Q21. What is the difference between Lettuce and Jedis?**
Both are Java Redis clients. **Lettuce** (Spring Boot default) uses Netty for non-blocking I/O, is thread-safe with a single connection, and supports reactive programming. **Jedis** is synchronous, requires connection pooling (one connection per thread), and has a simpler API. Prefer Lettuce for Spring WebFlux and high-concurrency.

---

**Q22. How do you set TTL per cache region in Redis?**
```java
Map<String, RedisCacheConfiguration> config = Map.of(
    "productSearch", defaultConfig().entryTtl(Duration.ofMinutes(2)),
    "productsAll",   defaultConfig().entryTtl(Duration.ofMinutes(10))
);
RedisCacheManager.builder(factory).withInitialCacheConfigurations(config).build();
```

---

**Q23. Why store type information (`@class`) in Redis JSON values?**
When deserializing JSON from Redis, Jackson needs the concrete class name to instantiate the correct type. Without it, deserialization returns a raw `LinkedHashMap`. Embedding `@class` via `activateDefaultTyping` with `ObjectMapper.DefaultTyping.NON_FINAL` enables correct polymorphic deserialization.

---

### EhCache

**Q24. What is EhCache 3 and what is JSR-107?**
EhCache 3 is an enterprise-grade Java caching library implementing **JSR-107 (JCache)** – the standard Java caching API (`javax.cache`). Any JSR-107-compliant provider can be swapped in transparently. Spring's `JCacheCacheManager` wraps any JSR-107 provider.

---

**Q25. What are EhCache's storage tiers?**
EhCache 3 supports three tiers that can be combined:
1. **Heap** – JVM heap; fastest; limited by `-Xmx`.
2. **Off-heap** – direct memory; fast; not GC-managed; larger than heap.
3. **Disk** – persistent across JVM restarts; slowest.

---

### Cache Design & Best Practices

**Q26. What types of data should NOT be cached?**
- Rapidly changing data where stale values cause incorrect behaviour.
- User-specific sensitive data without proper key isolation.
- Very large objects that cause heap/memory pressure.
- Data that is cheap to fetch (simple single-field lookup).

---

**Q27. What is the difference between local and distributed caching?**

| Aspect      | Local (Caffeine/EhCache)     | Distributed (Redis)            |
|-------------|------------------------------|--------------------------------|
| Location    | Same JVM as application      | Separate network store         |
| Multi-node  | Each instance has own copy   | All instances share one store  |
| Consistency | Not consistent across nodes  | Consistent across all nodes    |
| Latency     | Sub-microsecond              | ~1–5 ms network round-trip     |
| Persistence | Lost on JVM restart          | Survives application restarts  |

---

**Q28. How do you handle cache invalidation in microservices?**
- **Event-driven**: Publish a domain event (Kafka/RabbitMQ). Each service subscribes and evicts its local cache.
- **Shared Redis**: Use a single Redis cluster; services write through it so all read consistent data.
- **Short TTL**: Accept brief inconsistency with short TTLs.
- **Cache-busting key**: Include a version/ETag in the cache key; increment on update.

---

**Q29. What is the N+1 problem and how does caching help?**
N+1 occurs when fetching N parent entities and issuing one extra query per entity to load related data (N+1 total queries). Caching helps by storing related data after the first load. However, the better primary solution is eager loading (`JOIN FETCH` in JPQL); caching is a complementary optimization.

---

**Q30. How do you test Spring Cache annotations?**
1. Use `@SpringBootTest` with a real `CacheManager` (no mocking).
2. Clear caches in `@BeforeEach` to avoid test order dependency.
3. Verify `@Cacheable`: call twice, confirm DB is only hit once; check `cache.get(key)` is populated.
4. Verify `@CachePut`: call the method, inspect `cache.get(key)` for the new value.
5. Verify `@CacheEvict`: populate cache, call evicting method, confirm `cache.get(key)` returns `null`.

See `ProductServiceCacheTest.java` for concrete examples.

---

## Quick Reference

### Annotation summary

| Annotation      | Method runs?    | Cache updated?          | Use for       |
|-----------------|-----------------|-------------------------|---------------|
| `@Cacheable`    | Only on miss    | Only on miss            | Read ops      |
| `@CachePut`     | Always          | Always (with new value) | Write ops     |
| `@CacheEvict`   | Always          | Entry removed           | Delete ops    |
| `@Caching`      | (combined)      | (combined)              | Complex ops   |

### Provider comparison

| Provider   | Type        | Eviction Policy | Multi-instance | Persistence |
|------------|-------------|-----------------|----------------|-------------|
| Caffeine   | Local       | W-TinyLFU       | ❌ No          | ❌ No       |
| EhCache 3  | Local       | LRU + TTL/TTI   | ❌ No          | ✅ (disk)   |
| Redis      | Distributed | TTL             | ✅ Yes         | ✅ (AOF/RDB)|

---

## License

This project is provided under the [Apache 2.0 License](LICENSE) for educational purposes.
