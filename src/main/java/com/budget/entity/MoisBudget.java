package com.budget.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import com.budget.model.YearMonthAttributeConverter;

/**
 * Représente un mois de budget avec sa recette, ses budgets et ses factures.
 */
@Entity
@Table(name = "mois_budget")
public class MoisBudget {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    @Convert(converter = YearMonthAttributeConverter.class)
    private YearMonth mois;  // Format: 2024-12
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal recette;
    
    @OneToMany(mappedBy = "moisBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BudgetMois> budgets = new ArrayList<>();
    
    @OneToMany(mappedBy = "moisBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Facture> factures = new ArrayList<>();
    
    @Column(nullable = false)
    private LocalDateTime dateCreation;
    
    @Column(nullable = false)
    private LocalDateTime dateModification;

    // Constructeurs
    public MoisBudget() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public YearMonth getMois() {
        return mois;
    }

    public void setMois(YearMonth mois) {
        this.mois = mois;
    }

    public BigDecimal getRecette() {
        return recette;
    }

    public void setRecette(BigDecimal recette) {
        this.recette = recette;
    }

    public List<BudgetMois> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<BudgetMois> budgets) {
        this.budgets = budgets;
    }

    public List<Facture> getFactures() {
        return factures;
    }

    public void setFactures(List<Facture> factures) {
        this.factures = factures;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateModification() {
        return dateModification;
    }

    public void setDateModification(LocalDateTime dateModification) {
        this.dateModification = dateModification;
    }
}
