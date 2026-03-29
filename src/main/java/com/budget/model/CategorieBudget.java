package com.budget.model;

import java.math.BigDecimal;

/**
 * Catégories de budget disponibles.
 * Les 3 premières sont obligatoires, PERSONNALISE permet d'en créer d'autres.
 * Pourcentages par défaut basés sur la règle 50/30/20 (Elizabeth Warren).
 */
public enum CategorieBudget {
    CHARGES_INCOMPRESSIBLES("Charges incompressibles", true, new BigDecimal("50")),
    CHARGES_COURANTES("Charges courantes", true, new BigDecimal("30")),
    EPARGNE("Épargne", true, new BigDecimal("20")),
    PERSONNALISE("Personnalisé", false, null);

    private final String libelle;
    private final boolean obligatoire;
    private final BigDecimal pourcentageDefaut;

    CategorieBudget(String libelle, boolean obligatoire, BigDecimal pourcentageDefaut) {
        this.libelle = libelle;
        this.obligatoire = obligatoire;
        this.pourcentageDefaut = pourcentageDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isObligatoire() {
        return obligatoire;
    }

    public BigDecimal getPourcentageDefaut() {
        return pourcentageDefaut;
    }

    /**
     * Retourne les catégories obligatoires.
     */
    public static CategorieBudget[] getObligatoires() {
        return new CategorieBudget[]{
                CHARGES_INCOMPRESSIBLES,
                CHARGES_COURANTES,
                EPARGNE
        };
    }
}










