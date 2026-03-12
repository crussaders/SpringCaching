package com.example.springcaching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test – verifies that the Spring context loads without errors.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpringCachingApplicationTest {

    @Test
    void contextLoads() {
        // If the context loads, this test passes.
    }
}
