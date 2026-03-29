package com.budget.entity;

import jakarta.persistence.*;
import com.budget.model.CategorieBudget;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Représente un budget pour un mois donné.
 */
@Entity
@Table(name = "budget_mois")
public class BudgetMois {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "mois_budget_id")
    private MoisBudget moisBudget;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieBudget categorie;
    
    @Column(nullable = true, length = 100)
    private String nomPersonnalise;  // Pour les budgets personnalisés
    
    @Column(nullable = true, precision = 19, scale = 2)
    private BigDecimal montantFixe;
    
    @Column(nullable = true, precision = 5, scale = 2)
    private BigDecimal pourcentage;
    
    @Column(nullable = false)
    private Boolean obligatoire;
    
    @Column(nullable = false)
    private Boolean reporte;  // true si reporté du mois précédent

    @Column(nullable = true)
    private Boolean actif = true;  // false = soft-deleted (gardé pour historique)

    // Constructeurs
    public BudgetMois() {
        this.obligatoire = false;
        this.reporte = false;
        this.actif = true;
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MoisBudget getMoisBudget() {
        return moisBudget;
    }

    public void setMoisBudget(MoisBudget moisBudget) {
        this.moisBudget = moisBudget;
    }

    public CategorieBudget getCategorie() {
        return categorie;
    }

    public void setCategorie(CategorieBudget categorie) {
        this.categorie = categorie;
    }

    public String getNomPersonnalise() {
        return nomPersonnalise;
    }

    public void setNomPersonnalise(String nomPersonnalise) {
        this.nomPersonnalise = nomPersonnalise;
    }

    public BigDecimal getMontantFixe() {
        return montantFixe;
    }

    public void setMontantFixe(BigDecimal montantFixe) {
        this.montantFixe = montantFixe;
    }

    public BigDecimal getPourcentage() {
        return pourcentage;
    }

    public void setPourcentage(BigDecimal pourcentage) {
        this.pourcentage = pourcentage;
    }

    public Boolean getObligatoire() {
        return obligatoire;
    }

    public void setObligatoire(Boolean obligatoire) {
        this.obligatoire = obligatoire;
    }

    public Boolean getReporte() {
        return reporte;
    }

    public void setReporte(Boolean reporte) {
        this.reporte = reporte;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    /**
     * Vérifie si le budget est actif (null traité comme true pour compatibilité).
     */
    public boolean estActif() {
        return actif == null || actif;
    }

    /**
     * Retourne le nom du budget (catégorie ou nom personnalisé).
     */
    public String getNom() {
        if (nomPersonnalise != null && !nomPersonnalise.isEmpty()) {
            return nomPersonnalise;
        }
        return categorie.getLibelle();
    }

    /**
     * Vérifie si le budget est défini (montant ou pourcentage).
     */
    public boolean estDefini() {
        return montantFixe != null || pourcentage != null;
    }

    /**
     * Calcule le montant effectif du budget en fonction de la recette.
     * Utilise BigDecimal pour la précision financière.
     */
    public BigDecimal getMontantEffectif(BigDecimal recette) {
        if (pourcentage != null) {
            return recette.multiply(pourcentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (montantFixe != null) {
            return montantFixe;
        }
        return BigDecimal.ZERO;
    }
}
