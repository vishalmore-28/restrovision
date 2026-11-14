package com.restrovision.repository;

import com.restrovision.domain.CustomerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {
    List<CustomerFeedback> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<CustomerFeedback> findTop20ByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
