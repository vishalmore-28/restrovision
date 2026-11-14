package com.restrovision.repository;

import com.restrovision.domain.AiFeedbackLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AiFeedbackLabelRepository extends JpaRepository<AiFeedbackLabel, Long> {
    List<AiFeedbackLabel> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
