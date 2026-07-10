package com.staffsense.demo.repository;

import com.staffsense.demo.model.AttritionPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttritionPredictionRepository extends JpaRepository<AttritionPrediction, Long> {
    List<AttritionPrediction> findByUserId(Long userId);
}
