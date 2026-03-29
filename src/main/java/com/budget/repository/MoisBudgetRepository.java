package com.budget.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.budget.entity.MoisBudget;

@Repository
public interface MoisBudgetRepository extends JpaRepository<MoisBudget, Long> {
    Optional<MoisBudget> findByMois(YearMonth mois);
    List<MoisBudget> findAllByOrderByMoisDesc();
    Optional<MoisBudget> findFirstByOrderByMoisDesc();  // Dernier mois
}






