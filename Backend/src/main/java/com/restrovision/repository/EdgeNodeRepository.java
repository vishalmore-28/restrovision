package com.restrovision.repository;

import com.restrovision.domain.EdgeNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeNodeRepository extends JpaRepository<EdgeNode, String> {
    List<EdgeNode> findByRestaurantId(Long restaurantId);
    Optional<EdgeNode> findByRestaurantIdAndEdgeNodeId(Long restaurantId, String edgeNodeId);
}
