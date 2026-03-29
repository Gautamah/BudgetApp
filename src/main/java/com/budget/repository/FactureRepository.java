package com.budget.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.budget.entity.Facture;
import com.budget.entity.MoisBudget;
import com.budget.model.TypeFacture;

@Repository
public interface FactureRepository extends JpaRepository<Facture, Long> {
    List<Facture> findByMoisBudget(MoisBudget moisBudget);
    List<Facture> findByMoisBudgetAndType(MoisBudget moisBudget, TypeFacture type);
    List<Facture> findByMoisBudgetOrderByDateFactureDesc(MoisBudget moisBudget);
}






