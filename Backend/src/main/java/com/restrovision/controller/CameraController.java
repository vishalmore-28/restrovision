package com.restrovision.controller;

import com.restrovision.domain.Camera;
import com.restrovision.domain.CameraZone;
import com.restrovision.repository.CameraRepository;
import com.restrovision.repository.CameraZoneRepository;
import com.restrovision.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cameras")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class CameraController {

    private final CameraRepository cameraRepository;
    private final CameraZoneRepository zoneRepository;

    public CameraController(CameraRepository cameraRepository, CameraZoneRepository zoneRepository) {
        this.cameraRepository = cameraRepository;
        this.zoneRepository = zoneRepository;
    }

    @GetMapping
    public ResponseEntity<List<Camera>> getCameras() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(cameraRepository.findByRestaurantId(restaurantId));
    }

    @GetMapping("/{id}/zones")
    public ResponseEntity<List<CameraZone>> getZones(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(zoneRepository.findByRestaurantIdAndCameraId(restaurantId, id));
    }

    @PostMapping("/{id}/zones")
    public ResponseEntity<CameraZone> createZone(@PathVariable Long id, @RequestBody CameraZone zone) {
        Long restaurantId = TenantContext.getRestaurantId();
        zone.setRestaurantId(restaurantId);
        zone.setCameraId(id);
        return ResponseEntity.ok(zoneRepository.save(zone));
    }

    @PutMapping("/{id}/maintenance")
    public ResponseEntity<Camera> setMaintenance(@PathVariable Long id, @RequestParam boolean enabled) {
        Long restaurantId = TenantContext.getRestaurantId();
        Camera cam = cameraRepository.findByRestaurantIdAndCameraId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found"));
        cam.setState(enabled ? "MAINTENANCE" : "CONNECTED");
        return ResponseEntity.ok(cameraRepository.save(cam));
    }
}
