package com.restrovision.repository;

import com.restrovision.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByRestaurantId(Long restaurantId);
    Optional<Camera> findByRestaurantIdAndCameraId(Long restaurantId, Long cameraId);
}
