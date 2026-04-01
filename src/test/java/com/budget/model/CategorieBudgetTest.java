package com.budget.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CategorieBudgetTest {

    @Test
    void categoriesObligatoires_sontTrois() {
        CategorieBudget[] obligatoires = CategorieBudget.getObligatoires();
        assertEquals(3, obligatoires.length);
    }

    @Test
    void categoriesObligatoires_ontPourcentagesParDefaut() {
        assertEquals(new BigDecimal("50"), CategorieBudget.CHARGES_INCOMPRESSIBLES.getPourcentageDefaut());
        assertEquals(new BigDecimal("30"), CategorieBudget.CHARGES_COURANTES.getPourcentageDefaut());
        assertEquals(new BigDecimal("20"), CategorieBudget.EPARGNE.getPourcentageDefaut());
    }

    @Test
    void pourcentagesParDefaut_totalisent100() {
        BigDecimal total = BigDecimal.ZERO;
        for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
            total = total.add(cat.getPourcentageDefaut());
        }
        assertEquals(new BigDecimal("100"), total);
    }

    @Test
    void personnalise_nEstPasObligatoire() {
        assertFalse(CategorieBudget.PERSONNALISE.isObligatoire());
        assertNull(CategorieBudget.PERSONNALISE.getPourcentageDefaut());
    }

    @Test
    void libelles_sontCorrects() {
        assertEquals("Charges incompressibles", CategorieBudget.CHARGES_INCOMPRESSIBLES.getLibelle());
        assertEquals("Charges courantes", CategorieBudget.CHARGES_COURANTES.getLibelle());
        assertEquals("Épargne", CategorieBudget.EPARGNE.getLibelle());
        assertEquals("Personnalisé", CategorieBudget.PERSONNALISE.getLibelle());
    }
}
