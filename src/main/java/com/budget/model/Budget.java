package com.budget.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Représente un budget (enveloppe) pour une catégorie de dépenses.
 * Peut être défini en montant fixe ou en pourcentage de la recette.
 * Utilise BigDecimal pour la précision financière.
 */
public class Budget {
    private final String id;
    private String nom;
    private CategorieBudget categorie;
    private BigDecimal montantFixe;      // Montant en euros (si défini en montant)
    private BigDecimal pourcentage;       // Pourcentage de la recette (si défini en %)
    private final boolean obligatoire;

    /**
     * Constructeur pour un budget obligatoire (lié à une catégorie).
     */
    public Budget(CategorieBudget categorie) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.categorie = categorie;
        this.nom = categorie.getLibelle();
        this.obligatoire = categorie.isObligatoire();
    }

    /**
     * Constructeur pour un budget personnalisé.
     */
    public Budget(String nom) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.categorie = CategorieBudget.PERSONNALISE;
        this.nom = nom;
        this.obligatoire = false;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public CategorieBudget getCategorie() {
        return categorie;
    }

    public BigDecimal getMontantFixe() {
        return montantFixe;
    }

    public BigDecimal getPourcentage() {
        return pourcentage;
    }

    public boolean isObligatoire() {
        return obligatoire;
    }

    public boolean estDefini() {
        return montantFixe != null || pourcentage != null;
    }

    public boolean estEnPourcentage() {
        return pourcentage != null;
    }

    // Setters
    public void setNom(String nom) {
        this.nom = nom;
    }

    /**
     * Définit le budget en montant fixe.
     */
    public void definirMontant(BigDecimal montant) {
        if (montant.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le montant ne peut pas être négatif");
        }
        this.montantFixe = montant;
        this.pourcentage = null;
    }

    /**
     * Définit le budget en pourcentage de la recette.
     */
    public void definirPourcentage(BigDecimal pourcentage) {
        if (pourcentage.compareTo(BigDecimal.ZERO) < 0 || pourcentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Le pourcentage doit être entre 0 et 100");
        }
        this.pourcentage = pourcentage;
        this.montantFixe = null;
    }

    /**
     * Calcule le montant effectif du budget en fonction de la recette.
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

    /**
     * Retourne une description du budget (montant ou pourcentage).
     */
    public String getDescription(BigDecimal recette) {
        if (pourcentage != null) {
            return String.format("%.1f%% (%.2f EUR)", pourcentage, getMontantEffectif(recette));
        } else if (montantFixe != null) {
            return String.format("%.2f EUR", montantFixe);
        }
        return "Non défini";
    }

    @Override
    public String toString() {
        return nom;
    }
}
