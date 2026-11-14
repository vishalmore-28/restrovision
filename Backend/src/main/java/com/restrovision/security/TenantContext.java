package com.restrovision.security;

public class TenantContext {
    private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = ThreadLocal.withInitial(() -> 1L);

    public static void setRestaurantId(Long restaurantId) {
        if (restaurantId != null) {
            CURRENT_RESTAURANT_ID.set(restaurantId);
        }
    }

    public static Long getRestaurantId() {
        return CURRENT_RESTAURANT_ID.get();
    }

    public static void clear() {
        CURRENT_RESTAURANT_ID.set(1L);
    }
}
