package com.budget.model;

/**
 * Types de factures disponibles, classés par catégorie de budget.
 */
public enum TypeFacture {
    // Charges incompressibles
    LOYER("Loyer", CategorieBudget.CHARGES_INCOMPRESSIBLES),
    ASSURANCE("Assurance", CategorieBudget.CHARGES_INCOMPRESSIBLES),
    CHARGES_COPRO("Charges copropriété", CategorieBudget.CHARGES_INCOMPRESSIBLES),
    EMPRUNT("Emprunt / Crédit", CategorieBudget.CHARGES_INCOMPRESSIBLES),
    IMPOTS("Impôts et taxes", CategorieBudget.CHARGES_INCOMPRESSIBLES),

    // Charges courantes
    ALIMENTATION("Alimentation", CategorieBudget.CHARGES_COURANTES),
    ELECTRICITE("Électricité", CategorieBudget.CHARGES_COURANTES),
    EAU("Eau", CategorieBudget.CHARGES_COURANTES),
    GAZ("Gaz", CategorieBudget.CHARGES_COURANTES),
    INTERNET("Internet / Téléphone", CategorieBudget.CHARGES_COURANTES),
    ABONNEMENT("Abonnements / Streaming", CategorieBudget.CHARGES_COURANTES),
    LOISIRS("Loisirs / Jeux vidéo", CategorieBudget.CHARGES_COURANTES),
    TRANSPORT("Transport", CategorieBudget.CHARGES_COURANTES),
    SANTE("Santé", CategorieBudget.CHARGES_COURANTES),

    // Épargne
    LIVRET_A("Livret A", CategorieBudget.EPARGNE),
    ASSURANCE_VIE("Assurance vie", CategorieBudget.EPARGNE),
    PEA("PEA", CategorieBudget.EPARGNE),
    AUTRE_EPARGNE("Autre épargne", CategorieBudget.EPARGNE);

    private final String libelle;
    private final CategorieBudget categorie;

    TypeFacture(String libelle, CategorieBudget categorie) {
        this.libelle = libelle;
        this.categorie = categorie;
    }

    public String getLibelle() {
        return libelle;
    }

    public CategorieBudget getCategorie() {
        return categorie;
    }

    /**
     * Retourne les types de facture pour une catégorie donnée.
     */
    public static TypeFacture[] parCategorie(CategorieBudget categorie) {
        return java.util.Arrays.stream(values())
                .filter(t -> t.categorie == categorie)
                .toArray(TypeFacture[]::new);
    }

    /**
     * Affiche les types disponibles par catégorie.
     */
    public static void afficherParCategorie() {
        for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
            System.out.println("\n=== " + cat.getLibelle().toUpperCase() + " ===");
            TypeFacture[] types = parCategorie(cat);
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < types.length; i++) {
                sb.append(types[i].name());
                if (i < types.length - 1) {
                    sb.append(", ");
                }
            }
            System.out.println(sb);
        }
    }

    /**
     * Trouve un type par son nom (insensible à la casse).
     */
    public static TypeFacture fromString(String nom) {
        for (TypeFacture type : values()) {
            if (type.name().equalsIgnoreCase(nom)) {
                return type;
            }
        }
        return null;
    }
}
