package com.restrovision.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextSafetyTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("BUG-004: Default tenant context on a fresh thread is null (no unsafe fallback to 1L)")
    void testDefaultTenantContextIsNull() {
        assertThat(TenantContext.getRestaurantId())
                .as("Initial tenant context must be null rather than hardcoded 1L")
                .isNull();
    }

    @Test
    @DisplayName("BUG-004: getRequiredRestaurantId throws AccessDeniedException when context is unset")
    void testRequiredTenantThrowsWhenNull() {
        assertThatThrownBy(TenantContext::getRequiredRestaurantId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tenant context associated with current request");
    }

    @Test
    @DisplayName("BUG-004: Setting and clearing tenant context behaves correctly")
    void testSetAndClearTenantContext() {
        TenantContext.setRestaurantId(42L);
        assertThat(TenantContext.getRestaurantId()).isEqualTo(42L);
        assertThat(TenantContext.getRequiredRestaurantId()).isEqualTo(42L);

        TenantContext.clear();
        assertThat(TenantContext.getRestaurantId()).isNull();

        // Setting null should also clear context
        TenantContext.setRestaurantId(99L);
        assertThat(TenantContext.getRestaurantId()).isEqualTo(99L);
        TenantContext.setRestaurantId(null);
        assertThat(TenantContext.getRestaurantId()).isNull();
    }

    @Test
    @DisplayName("BUG-004: Thread pool reuse with finally cleanup does NOT leak tenant context across requests")
    void testThreadPoolReuseDoesNotLeakTenant() throws Exception {
        // Create a fixed thread pool of 2 threads to guarantee thread reuse
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Step 1: Execute simulated requests on pool threads with finally cleanup
            for (int i = 0; i < 10; i++) {
                final long tenantId = 100L + i;
                Future<?> future = pool.submit(() -> {
                    try {
                        TenantContext.setRestaurantId(tenantId);
                        assertThat(TenantContext.getRestaurantId()).isEqualTo(tenantId);
                    } finally {
                        TenantContext.clear();
                    }
                });
                future.get(2, TimeUnit.SECONDS);
            }

            // Step 2: Now submit unauthenticated tasks to the same recycled threads
            // NONE of them must observe any lingering tenant context!
            List<Future<Long>> lingeringChecks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                lingeringChecks.add(pool.submit(TenantContext::getRestaurantId));
            }

            for (Future<Long> check : lingeringChecks) {
                Long observed = check.get(2, TimeUnit.SECONDS);
                assertThat(observed)
                        .as("Pooled thread must not contain residual tenant context from prior requests")
                        .isNull();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("BUG-004: Multi-tenant thread isolation ensures concurrent threads do not overwrite each other")
    void testMultiTenantIsolationAcrossThreads() throws Exception {
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicBoolean mismatchFound = new AtomicBoolean(false);

        try {
            for (int i = 0; i < threadCount; i++) {
                final long expectedTenant = 1000L + i;
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        TenantContext.setRestaurantId(expectedTenant);
                        // Small jitter to test race conditions
                        Thread.sleep(20);
                        Long actualTenant = TenantContext.getRestaurantId();
                        if (actualTenant == null || actualTenant != expectedTenant) {
                            mismatchFound.set(true);
                        }
                    } catch (Exception e) {
                        mismatchFound.set(true);
                    } finally {
                        TenantContext.clear();
                        finishLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
            assertThat(finished).isTrue();
            assertThat(mismatchFound.get()).as("No cross-thread tenant contamination must occur").isFalse();
        } finally {
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.SECONDS);
        }
    }
}
