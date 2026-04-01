package com.budget.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BudgetTest {

    @Test
    void budgetObligatoire_creationAvecCategorie() {
        Budget budget = new Budget(CategorieBudget.CHARGES_INCOMPRESSIBLES);

        assertEquals("Charges incompressibles", budget.getNom());
        assertEquals(CategorieBudget.CHARGES_INCOMPRESSIBLES, budget.getCategorie());
        assertTrue(budget.isObligatoire());
        assertFalse(budget.estDefini());
    }

    @Test
    void budgetPersonnalise_creationAvecNom() {
        Budget budget = new Budget("Transport");

        assertEquals("Transport", budget.getNom());
        assertEquals(CategorieBudget.PERSONNALISE, budget.getCategorie());
        assertFalse(budget.isObligatoire());
        assertFalse(budget.estDefini());
    }

    @Test
    void definirMontant_fixeMontantEtEffacePourcentage() {
        Budget budget = new Budget(CategorieBudget.CHARGES_COURANTES);
        budget.definirPourcentage(new BigDecimal("30"));
        budget.definirMontant(new BigDecimal("500"));

        assertTrue(budget.estDefini());
        assertFalse(budget.estEnPourcentage());
        assertEquals(new BigDecimal("500"), budget.getMontantFixe());
        assertNull(budget.getPourcentage());
    }

    @Test
    void definirPourcentage_fixePourcentageEtEffaceMontant() {
        Budget budget = new Budget(CategorieBudget.EPARGNE);
        budget.definirMontant(new BigDecimal("200"));
        budget.definirPourcentage(new BigDecimal("20"));

        assertTrue(budget.estDefini());
        assertTrue(budget.estEnPourcentage());
        assertEquals(new BigDecimal("20"), budget.getPourcentage());
        assertNull(budget.getMontantFixe());
    }

    @Test
    void definirMontant_negatif_exception() {
        Budget budget = new Budget(CategorieBudget.CHARGES_COURANTES);
        assertThrows(IllegalArgumentException.class,
                () -> budget.definirMontant(new BigDecimal("-100")));
    }

    @Test
    void definirPourcentage_horsLimites_exception() {
        Budget budget = new Budget(CategorieBudget.EPARGNE);
        assertThrows(IllegalArgumentException.class,
                () -> budget.definirPourcentage(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> budget.definirPourcentage(new BigDecimal("101")));
    }

    @Test
    void getMontantEffectif_avecPourcentage() {
        Budget budget = new Budget(CategorieBudget.CHARGES_INCOMPRESSIBLES);
        budget.definirPourcentage(new BigDecimal("50"));

        BigDecimal recette = new BigDecimal("3000");
        assertEquals(new BigDecimal("1500.00"), budget.getMontantEffectif(recette));
    }

    @Test
    void getMontantEffectif_avecMontantFixe() {
        Budget budget = new Budget(CategorieBudget.CHARGES_COURANTES);
        budget.definirMontant(new BigDecimal("800"));

        assertEquals(new BigDecimal("800"), budget.getMontantEffectif(new BigDecimal("3000")));
    }

    @Test
    void getMontantEffectif_nonDefini_retourneZero() {
        Budget budget = new Budget(CategorieBudget.EPARGNE);
        assertEquals(BigDecimal.ZERO, budget.getMontantEffectif(new BigDecimal("3000")));
    }

    @Test
    void getMontantEffectif_pourcentageAvecArrondi() {
        Budget budget = new Budget(CategorieBudget.CHARGES_INCOMPRESSIBLES);
        budget.definirPourcentage(new BigDecimal("33.33"));

        BigDecimal recette = new BigDecimal("1000");
        assertEquals(new BigDecimal("333.30"), budget.getMontantEffectif(recette));
    }
}
