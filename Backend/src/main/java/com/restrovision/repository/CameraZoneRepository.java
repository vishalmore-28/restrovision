package com.restrovision.repository;

import com.restrovision.domain.CameraZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CameraZoneRepository extends JpaRepository<CameraZone, Long> {
    List<CameraZone> findByRestaurantId(Long restaurantId);
    List<CameraZone> findByRestaurantIdAndCameraId(Long restaurantId, Long cameraId);
    Optional<CameraZone> findByRestaurantIdAndZoneId(Long restaurantId, Long zoneId);
    List<CameraZone> findByRestaurantIdAndTableId(Long restaurantId, Long tableId);
}
