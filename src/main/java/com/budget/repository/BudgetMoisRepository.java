package com.budget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.budget.entity.BudgetMois;
import com.budget.entity.MoisBudget;
import com.budget.model.CategorieBudget;

@Repository
public interface BudgetMoisRepository extends JpaRepository<BudgetMois, Long> {
    List<BudgetMois> findByMoisBudget(MoisBudget moisBudget);
    List<BudgetMois> findByMoisBudgetAndActifTrue(MoisBudget moisBudget);
    Optional<BudgetMois> findByMoisBudgetAndCategorie(MoisBudget moisBudget, CategorieBudget categorie);
    Optional<BudgetMois> findByMoisBudgetAndCategorieAndActifTrue(MoisBudget moisBudget, CategorieBudget categorie);
    Optional<BudgetMois> findByMoisBudgetAndNomPersonnalise(MoisBudget moisBudget, String nom);
    Optional<BudgetMois> findByMoisBudgetAndNomPersonnaliseAndActifTrue(MoisBudget moisBudget, String nom);
}





