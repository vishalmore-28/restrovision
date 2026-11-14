package com.restrovision.repository;

import com.restrovision.domain.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findByRestaurantIdOrderByTableCodeAsc(Long restaurantId);
    Optional<RestaurantTable> findByRestaurantIdAndTableId(Long restaurantId, Long tableId);
    Optional<RestaurantTable> findByRestaurantIdAndTableCode(Long restaurantId, String tableCode);
    List<RestaurantTable> findByRestaurantIdAndStatus(Long restaurantId, String status);
    List<RestaurantTable> findByRestaurantIdAndAssignedWaiterId(Long restaurantId, Long waiterId);
    Optional<RestaurantTable> findByQrToken(String qrToken);
}
