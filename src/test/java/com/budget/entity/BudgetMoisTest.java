package com.budget.entity;

import com.budget.model.CategorieBudget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BudgetMoisTest {

    @Test
    void nouveauBudget_valeursParDefaut() {
        BudgetMois budget = new BudgetMois();

        assertFalse(budget.getObligatoire());
        assertFalse(budget.getReporte());
        assertTrue(budget.estActif());
        assertFalse(budget.estDefini());
    }

    @Test
    void estDefini_avecMontantFixe() {
        BudgetMois budget = new BudgetMois();
        budget.setMontantFixe(new BigDecimal("500"));

        assertTrue(budget.estDefini());
    }

    @Test
    void estDefini_avecPourcentage() {
        BudgetMois budget = new BudgetMois();
        budget.setPourcentage(new BigDecimal("30"));

        assertTrue(budget.estDefini());
    }

    @Test
    void getMontantEffectif_avecPourcentage() {
        BudgetMois budget = new BudgetMois();
        budget.setPourcentage(new BigDecimal("50"));

        BigDecimal recette = new BigDecimal("2000");
        assertEquals(new BigDecimal("1000.00"), budget.getMontantEffectif(recette));
    }

    @Test
    void getMontantEffectif_avecMontantFixe() {
        BudgetMois budget = new BudgetMois();
        budget.setMontantFixe(new BigDecimal("750.50"));

        assertEquals(new BigDecimal("750.50"), budget.getMontantEffectif(new BigDecimal("3000")));
    }

    @Test
    void getMontantEffectif_nonDefini_retourneZero() {
        BudgetMois budget = new BudgetMois();
        assertEquals(BigDecimal.ZERO, budget.getMontantEffectif(new BigDecimal("3000")));
    }

    @Test
    void getNom_categorieObligatoire() {
        BudgetMois budget = new BudgetMois();
        budget.setCategorie(CategorieBudget.CHARGES_INCOMPRESSIBLES);

        assertEquals("Charges incompressibles", budget.getNom());
    }

    @Test
    void getNom_budgetPersonnalise() {
        BudgetMois budget = new BudgetMois();
        budget.setCategorie(CategorieBudget.PERSONNALISE);
        budget.setNomPersonnalise("Transport");

        assertEquals("Transport", budget.getNom());
    }

    @Test
    void estActif_nullTraiteCommeTrue() {
        BudgetMois budget = new BudgetMois();
        budget.setActif(null);

        assertTrue(budget.estActif());
    }

    @Test
    void softDelete() {
        BudgetMois budget = new BudgetMois();
        assertTrue(budget.estActif());

        budget.setActif(false);
        assertFalse(budget.estActif());
    }
}
