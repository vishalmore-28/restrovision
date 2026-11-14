package com.restrovision.security;

import org.springframework.security.access.AccessDeniedException;

public class TenantContext {
    private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = new ThreadLocal<>();

    public static void setRestaurantId(Long restaurantId) {
        if (restaurantId != null) {
            CURRENT_RESTAURANT_ID.set(restaurantId);
        } else {
            CURRENT_RESTAURANT_ID.remove();
        }
    }

    public static Long getRestaurantId() {
        return CURRENT_RESTAURANT_ID.get();
    }

    public static Long getRequiredRestaurantId() {
        Long id = CURRENT_RESTAURANT_ID.get();
        if (id == null) {
            throw new AccessDeniedException("No tenant context associated with current request");
        }
        return id;
    }

    public static void clear() {
        CURRENT_RESTAURANT_ID.remove();
    }
}
