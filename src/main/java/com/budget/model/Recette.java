package com.budget.model;

/**
 * Représente la recette (argent disponible) de l'utilisateur.
 */
public class Recette {
    private double montant;

    public Recette() {
        this.montant = 0.0;
    }

    public Recette(double montant) {
        this.montant = montant;
    }

    public double getMontant() {
        return montant;
    }

    public void setMontant(double montant) {
        if (montant < 0) {
            throw new IllegalArgumentException("La recette ne peut pas être négative");
        }
        this.montant = montant;
    }

    public boolean estDefinie() {
        return montant > 0;
    }

    @Override
    public String toString() {
        return String.format("%.2f EUR", montant);
    }
}










