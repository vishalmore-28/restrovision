package com.restrovision.repository;

import com.restrovision.domain.AiEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiEventRepository extends JpaRepository<AiEvent, Long> {
    Optional<AiEvent> findByRestaurantIdAndEventUid(Long restaurantId, String eventUid);
    List<AiEvent> findByRestaurantIdOrderByOccurredAtDesc(Long restaurantId);
    List<AiEvent> findTop50ByRestaurantIdOrderByOccurredAtDesc(Long restaurantId);
}
