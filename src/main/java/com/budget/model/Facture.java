package com.budget.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représente une facture avec son type, fournisseur et montant.
 * Le type détermine automatiquement la catégorie de budget.
 * Utilise BigDecimal pour la précision financière.
 */
public class Facture {
    private final String id;
    private TypeFacture type;
    private String fournisseur;
    private BigDecimal montant;
    private LocalDate date;

    public Facture(TypeFacture type, String fournisseur, BigDecimal montant) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.fournisseur = fournisseur;
        this.montant = montant;
        this.date = LocalDate.now();
    }

    /**
     * Constructeur avec ID personnalisé (pour conversion depuis entité JPA).
     */
    public Facture(String id, TypeFacture type, String fournisseur, BigDecimal montant) {
        this.id = id;
        this.type = type;
        this.fournisseur = fournisseur;
        this.montant = montant;
        this.date = LocalDate.now();
    }

    // Getters
    public String getId() {
        return id;
    }

    public TypeFacture getType() {
        return type;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public LocalDate getDate() {
        return date;
    }

    /**
     * Retourne la catégorie de budget associée à cette facture.
     */
    public CategorieBudget getCategorie() {
        return type.getCategorie();
    }

    // Setters
    public void setType(TypeFacture type) {
        this.type = type;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return String.format("[%s] %-12s | %-20s | %10.2f EUR",
                id, type.name(), fournisseur, montant);
    }

    /**
     * Retourne une description complète avec la catégorie.
     */
    public String toStringAvecCategorie() {
        return String.format("[%s] %s - %s (%s) - %.2f EUR",
                id, type.getLibelle(), fournisseur, getCategorie().getLibelle(), montant);
    }
}
