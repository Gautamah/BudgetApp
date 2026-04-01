package com.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budget.entity.BudgetMois;
import com.budget.entity.Facture;
import com.budget.entity.MoisBudget;
import com.budget.model.Budget;
import com.budget.model.CategorieBudget;
import com.budget.model.TypeFacture;
import com.budget.repository.BudgetMoisRepository;
import com.budget.repository.FactureRepository;

/**
 * Service de gestion des recettes, budgets et factures avec persistance.
 * Utilise BigDecimal pour toutes les opérations financières.
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private static final int MAX_NOM_LENGTH = 100;
    private static final int MAX_FOURNISSEUR_LENGTH = 100;

    private final MoisBudgetService moisBudgetService;
    private final BudgetMoisRepository budgetMoisRepository;
    private final FactureRepository factureRepository;

    public BudgetService(MoisBudgetService moisBudgetService,
                        BudgetMoisRepository budgetMoisRepository,
                        FactureRepository factureRepository) {
        this.moisBudgetService = moisBudgetService;
        this.budgetMoisRepository = budgetMoisRepository;
        this.factureRepository = factureRepository;
    }

    // ==================== RECETTE ====================

    @Transactional
    public void definirRecette(BigDecimal montant) {
        moisBudgetService.mettreAJourRecette(montant);
    }

    public BigDecimal getRecette() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        return moisCourant.getRecette() != null ? moisCourant.getRecette() : BigDecimal.ZERO;
    }

    public boolean isRecetteDefinie() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        return moisCourant.getRecette() != null && moisCourant.getRecette().compareTo(BigDecimal.ZERO) > 0;
    }

    // ==================== BUDGETS ====================

    /**
     * Convertit une entité BudgetMois en modèle Budget pour compatibilité.
     */
    private Budget convertirBudget(BudgetMois budgetMois, BigDecimal recette) {
        Budget budget = new Budget(budgetMois.getCategorie());
        if (budgetMois.getNomPersonnalise() != null) {
            budget.setNom(budgetMois.getNomPersonnalise());
        }
        if (budgetMois.getMontantFixe() != null) {
            budget.definirMontant(budgetMois.getMontantFixe());
        } else if (budgetMois.getPourcentage() != null) {
            budget.definirPourcentage(budgetMois.getPourcentage());
        }
        return budget;
    }

    public Budget getBudget(String nom) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> budgetMois = budgetMoisRepository
            .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
        if (budgetMois.isPresent()) {
            return convertirBudget(budgetMois.get(), getRecette());
        }
        // Chercher par catégorie
        for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
            if (cat.getLibelle().equals(nom)) {
                return getBudgetParCategorie(cat);
            }
        }
        return null;
    }

    public Budget getBudgetParCategorie(CategorieBudget categorie) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> budgetMois = budgetMoisRepository
            .findByMoisBudgetAndCategorie(moisCourant, categorie);
        if (budgetMois.isPresent()) {
            return convertirBudget(budgetMois.get(), getRecette());
        }
        return null;
    }

    /**
     * Retourne tous les budgets ACTIFS du mois courant.
     */
    public Collection<Budget> getTousBudgets() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<BudgetMois> budgetsMois = budgetMoisRepository.findByMoisBudget(moisCourant);
        BigDecimal recette = getRecette();
        return budgetsMois.stream()
            .filter(BudgetMois::estActif)
            .map(b -> convertirBudget(b, recette))
            .collect(Collectors.toList());
    }

    /**
     * Retourne tous les budgets (actifs + inactifs avec factures liées) pour l'historique/résumé.
     */
    public Collection<Budget> getTousBudgetsAvecHistorique() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<BudgetMois> budgetsMois = budgetMoisRepository.findByMoisBudget(moisCourant);
        BigDecimal recette = getRecette();
        return budgetsMois.stream()
            .filter(b -> b.estActif() || budgetADesFacturesLiees(b))
            .map(b -> convertirBudget(b, recette))
            .collect(Collectors.toList());
    }

    public List<Budget> getBudgetsObligatoires() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<BudgetMois> budgetsMois = budgetMoisRepository.findByMoisBudget(moisCourant);
        BigDecimal recette = getRecette();
        return budgetsMois.stream()
            .filter(b -> b.getObligatoire() && b.estActif())
            .map(b -> convertirBudget(b, recette))
            .collect(Collectors.toList());
    }

    public List<Budget> getBudgetsPersonnalises() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<BudgetMois> budgetsMois = budgetMoisRepository.findByMoisBudget(moisCourant);
        BigDecimal recette = getRecette();
        return budgetsMois.stream()
            .filter(b -> !b.getObligatoire() && b.estActif())
            .map(b -> convertirBudget(b, recette))
            .collect(Collectors.toList());
    }

    /**
     * Définit un budget obligatoire en montant.
     */
    @Transactional
    public void definirBudgetMontant(CategorieBudget categorie, BigDecimal montant) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        BigDecimal totalBudget = getTotalBudgetsAlloues();
        BigDecimal recette = getRecette();
        
        // Soustraire l'allocation existante pour cette catégorie (cas de mise à jour)
        Optional<BudgetMois> budgetMoisOpt = budgetMoisRepository
            .findByMoisBudgetAndCategorie(moisCourant, categorie);
        if (budgetMoisOpt.isPresent() && budgetMoisOpt.get().estDefini()) {
            totalBudget = totalBudget.subtract(budgetMoisOpt.get().getMontantEffectif(recette));
        }
        
        if (totalBudget.add(montant).compareTo(recette) > 0) {
            throw new IllegalArgumentException("Le total des budgets dépasse le montant de la recette");
        }
        
        BudgetMois budgetMois;
        if (budgetMoisOpt.isPresent()) {
            budgetMois = budgetMoisOpt.get();
        } else {
            budgetMois = new BudgetMois();
            budgetMois.setMoisBudget(moisCourant);
            budgetMois.setCategorie(categorie);
            budgetMois.setObligatoire(categorie.isObligatoire());
            budgetMois.setReporte(false);
        }
        
        budgetMois.setMontantFixe(montant);
        budgetMois.setPourcentage(null);
        budgetMoisRepository.save(budgetMois);
        log.info("Budget {} défini en montant : {}", categorie.getLibelle(), montant);
    }

    /**
     * Définit un budget obligatoire en pourcentage.
     */
    @Transactional
    public void definirBudgetPourcentage(CategorieBudget categorie, BigDecimal pourcentage) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> budgetMoisOpt = budgetMoisRepository
            .findByMoisBudgetAndCategorie(moisCourant, categorie);
        
        BudgetMois budgetMois;
        if (budgetMoisOpt.isPresent()) {
            budgetMois = budgetMoisOpt.get();
        } else {
            budgetMois = new BudgetMois();
            budgetMois.setMoisBudget(moisCourant);
            budgetMois.setCategorie(categorie);
            budgetMois.setObligatoire(categorie.isObligatoire());
            budgetMois.setReporte(false);
        }
        
        budgetMois.setPourcentage(pourcentage);
        budgetMois.setMontantFixe(null);
        budgetMoisRepository.save(budgetMois);
        log.info("Budget {} défini en pourcentage : {}%", categorie.getLibelle(), pourcentage);
    }

    /**
     * Ajoute un budget personnalisé en montant.
     * Valide la longueur du nom (max 100 caractères).
     */
    @Transactional
    public Budget ajouterBudgetPersonnalise(String nom, BigDecimal montant) {
        validateNomLength(nom);

        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> existing = budgetMoisRepository
            .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Un budget avec ce nom existe déjà");
        }

        BudgetMois budgetMois = new BudgetMois();
        budgetMois.setMoisBudget(moisCourant);
        budgetMois.setCategorie(CategorieBudget.PERSONNALISE);
        budgetMois.setNomPersonnalise(nom);
        budgetMois.setMontantFixe(montant);
        budgetMois.setPourcentage(null);
        budgetMois.setObligatoire(false);
        budgetMois.setReporte(false);
        
        budgetMoisRepository.save(budgetMois);
        log.info("Budget personnalisé '{}' ajouté : {} €", nom, montant);
        return convertirBudget(budgetMois, getRecette());
    }

    /**
     * Ajoute un budget personnalisé en pourcentage.
     * Valide la longueur du nom (max 100 caractères).
     */
    @Transactional
    public Budget ajouterBudgetPersonnalisePourcentage(String nom, BigDecimal pourcentage) {
        validateNomLength(nom);

        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> existing = budgetMoisRepository
            .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Un budget avec ce nom existe déjà");
        }

        BudgetMois budgetMois = new BudgetMois();
        budgetMois.setMoisBudget(moisCourant);
        budgetMois.setCategorie(CategorieBudget.PERSONNALISE);
        budgetMois.setNomPersonnalise(nom);
        budgetMois.setPourcentage(pourcentage);
        budgetMois.setMontantFixe(null);
        budgetMois.setObligatoire(false);
        budgetMois.setReporte(false);
        
        budgetMoisRepository.save(budgetMois);
        log.info("Budget personnalisé '{}' ajouté : {}%", nom, pourcentage);
        return convertirBudget(budgetMois, getRecette());
    }

    /**
     * Supprime ou désactive un budget.
     * - Si des factures sont liées : soft-delete (désactivation pour garder l'historique).
     * - Si aucune facture liée : suppression définitive.
     */
    @Transactional
    public boolean supprimerBudget(String nom, boolean estObligatoire) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        BudgetMois budgetMois = null;

        if (estObligatoire) {
            for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
                if (cat.getLibelle().equals(nom)) {
                    Optional<BudgetMois> opt = budgetMoisRepository
                        .findByMoisBudgetAndCategorie(moisCourant, cat);
                    if (opt.isPresent()) {
                        budgetMois = opt.get();
                    }
                    break;
                }
            }
        } else {
            Optional<BudgetMois> opt = budgetMoisRepository
                .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
            if (opt.isPresent()) {
                budgetMois = opt.get();
            }
        }

        if (budgetMois == null) return false;

        if (budgetADesFacturesLiees(budgetMois)) {
            budgetMois.setActif(false);
            budgetMoisRepository.save(budgetMois);
            log.info("Budget '{}' désactivé (soft-delete, factures liées)", nom);
        } else {
            // On retire le budget de la liste du parent AVANT de le supprimer.
            // C'est necessaire car MoisBudget a cascade=ALL + orphanRemoval=true :
            // si on supprime le budget directement sans le retirer de la liste du parent,
            // JPA garde l'ancienne reference en memoire et le budget "reapparait".
            moisCourant.getBudgets().remove(budgetMois);
            budgetMoisRepository.flush();
            log.info("Budget '{}' supprimé définitivement", nom);
        }
        return true;
    }

    /**
     * Vérifie si un budget a des factures liées pour le mois courant.
     */
    public boolean budgetADesFacturesLiees(BudgetMois budgetMois) {
        MoisBudget moisBudget = budgetMois.getMoisBudget();
        List<Facture> factures = factureRepository.findByMoisBudget(moisBudget);

        if (budgetMois.getCategorie() == CategorieBudget.PERSONNALISE) {
            return false;
        } else {
            return factures.stream()
                .anyMatch(f -> f.getCategorie() == budgetMois.getCategorie());
        }
    }

    /**
     * Modifie un budget personnalisé en montant.
     */
    @Transactional
    public void modifierBudgetPersonnaliseMontant(String nom, BigDecimal montant) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> budgetMois = budgetMoisRepository
            .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
        if (budgetMois.isPresent()) {
            BudgetMois b = budgetMois.get();
            b.setMontantFixe(montant);
            b.setPourcentage(null);
            budgetMoisRepository.save(b);
            log.info("Budget personnalisé '{}' modifié : {} €", nom, montant);
        }
    }

    /**
     * Modifie un budget personnalisé en pourcentage.
     */
    @Transactional
    public void modifierBudgetPersonnalisePourcentage(String nom, BigDecimal pourcentage) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        Optional<BudgetMois> budgetMois = budgetMoisRepository
            .findByMoisBudgetAndNomPersonnalise(moisCourant, nom);
        if (budgetMois.isPresent()) {
            BudgetMois b = budgetMois.get();
            b.setPourcentage(pourcentage);
            b.setMontantFixe(null);
            budgetMoisRepository.save(b);
            log.info("Budget personnalisé '{}' modifié : {}%", nom, pourcentage);
        }
    }

    /**
     * Vérifie si tous les budgets obligatoires sont définis.
     */
    public boolean tousBudgetsObligatoiresDefinis() {
        return getBudgetsObligatoires().stream()
            .allMatch(Budget::estDefini);
    }

    /**
     * Calcule le total des budgets alloués (actifs uniquement).
     */
    public BigDecimal getTotalBudgetsAlloues() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<BudgetMois> budgetsMois = budgetMoisRepository.findByMoisBudget(moisCourant);
        BigDecimal recette = getRecette();
        return budgetsMois.stream()
            .filter(b -> b.estActif() && b.estDefini())
            .map(b -> b.getMontantEffectif(recette))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcule le montant non alloué.
     */
    public BigDecimal getMontantNonAlloue() {
        return getRecette().subtract(getTotalBudgetsAlloues());
    }

    /**
     * Calcule le pourcentage non alloué.
     */
    public BigDecimal getPourcentageNonAlloue() {
        BigDecimal recette = getRecette();
        if (recette.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return getMontantNonAlloue()
                .multiply(BigDecimal.valueOf(100))
                .divide(recette, 1, RoundingMode.HALF_UP);
    }

    // ==================== FACTURES ====================

    /**
     * Ajoute une nouvelle facture avec date optionnelle.
     * Valide la longueur du fournisseur (max 100 caractères).
     */
    @Transactional
    public com.budget.model.Facture ajouterFacture(TypeFacture type, String fournisseur, BigDecimal montant, LocalDate dateFacture) {
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }
        if (fournisseur == null || fournisseur.trim().isEmpty()) {
            throw new IllegalArgumentException("Le fournisseur ne peut pas être vide");
        }
        validateFournisseurLength(fournisseur);

        LocalDate date = dateFacture != null ? dateFacture : LocalDate.now();
        MoisBudget mois = moisBudgetService.getMoisPour(YearMonth.from(date));
        Facture facture = new Facture();
        facture.setMoisBudget(mois);
        facture.setType(type);
        facture.setFournisseur(fournisseur.trim());
        facture.setMontant(montant);
        facture.setDateFacture(date);
        
        facture.setDateCreation(LocalDateTime.now());
        facture.setDateModification(LocalDateTime.now());
        
        Facture factureSauvegardee = factureRepository.save(facture);
        log.info("Facture ajoutée : {} - {} ({} €)", type.getLibelle(), fournisseur, montant);
        return convertirFacture(factureSauvegardee);
    }

    /**
     * Ajoute une facture avec la date du jour par défaut.
     */
    @Transactional
    public com.budget.model.Facture ajouterFacture(TypeFacture type, String fournisseur, BigDecimal montant) {
        return ajouterFacture(type, fournisseur, montant, null);
    }

    /**
     * Supprime une facture par son ID.
     */
    @Transactional
    public boolean supprimerFacture(String id) {
        try {
            Long factureId = Long.parseLong(id);
            Optional<Facture> facture = factureRepository.findById(factureId);
            if (facture.isPresent()) {
                factureRepository.delete(facture.get());
                log.info("Facture {} supprimée", id);
                return true;
            }
        } catch (NumberFormatException e) {
            log.warn("Tentative de suppression avec un ID invalide : {}", id);
        }
        return false;
    }

    /**
     * Modifie une facture existante.
     */
    @Transactional
    public void modifierFacture(String id, TypeFacture type, String fournisseur, BigDecimal montant, LocalDate date) {
        try {
            Long factureId = Long.parseLong(id);
            Optional<Facture> factureOpt = factureRepository.findById(factureId);
            if (factureOpt.isPresent()) {
                validateFournisseurLength(fournisseur);
                Facture facture = factureOpt.get();
                facture.setType(type);
                facture.setFournisseur(fournisseur);
                facture.setMontant(montant);
                facture.setDateFacture(date);
                facture.setDateModification(LocalDateTime.now());
                factureRepository.save(facture);
                log.info("Facture {} modifiée", id);
            }
        } catch (NumberFormatException e) {
            log.warn("Tentative de modification avec un ID invalide : {}", id);
        }
    }

    /**
     * Retourne toutes les factures du mois courant.
     */
    public List<com.budget.model.Facture> getFactures() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<Facture> factures = factureRepository.findByMoisBudget(moisCourant);
        return convertirFactures(factures);
    }

    /**
     * Convertit les entités Facture en modèles Facture pour compatibilité.
     */
    private List<com.budget.model.Facture> convertirFactures(List<Facture> factures) {
        return factures.stream()
            .map(this::convertirFacture)
            .collect(Collectors.toList());
    }

    private com.budget.model.Facture convertirFacture(Facture facture) {
        String id = facture.getId() != null ? facture.getId().toString() : null;
        com.budget.model.Facture f = new com.budget.model.Facture(
            id != null ? id : UUID.randomUUID().toString().substring(0, 8),
            facture.getType(),
            facture.getFournisseur(),
            facture.getMontant()
        );
        f.setDate(facture.getDateFacture());
        f.setDocumentPath(facture.getDocumentPath());
        return f;
    }

    /**
     * Retourne les factures d'une catégorie.
     */
    public List<com.budget.model.Facture> getFacturesParCategorie(CategorieBudget categorie) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<Facture> factures = factureRepository.findByMoisBudget(moisCourant);
        return factures.stream()
            .filter(f -> f.getCategorie() == categorie)
            .map(this::convertirFacture)
            .collect(Collectors.toList());
    }

    /**
     * Retourne les factures d'un budget.
     */
    public List<com.budget.model.Facture> getFacturesParBudget(Budget budget) {
        if (budget.getCategorie() == CategorieBudget.PERSONNALISE) {
            return new ArrayList<>();
        }
        return getFacturesParCategorie(budget.getCategorie());
    }

    /**
     * Calcule le total des dépenses pour une catégorie.
     */
    public BigDecimal getTotalDepensesParCategorie(CategorieBudget categorie) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<Facture> factures = factureRepository.findByMoisBudget(moisCourant);
        return factures.stream()
            .filter(f -> f.getCategorie() == categorie)
            .map(Facture::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcule le total des dépenses pour un budget.
     */
    public BigDecimal getTotalDepensesParBudget(Budget budget) {
        if (budget.getCategorie() == CategorieBudget.PERSONNALISE) {
            return BigDecimal.ZERO;
        }
        return getTotalDepensesParCategorie(budget.getCategorie());
    }

    /**
     * Calcule le reste disponible pour un budget.
     */
    public BigDecimal getResteParBudget(Budget budget) {
        BigDecimal alloue = budget.getMontantEffectif(getRecette());
        BigDecimal depense = getTotalDepensesParBudget(budget);
        return alloue.subtract(depense);
    }

    /**
     * Calcule le total de toutes les dépenses.
     */
    public BigDecimal getTotalDepenses() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<Facture> factures = factureRepository.findByMoisBudget(moisCourant);
        return factures.stream()
            .map(Facture::getMontant)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcule le reste global (recette - dépenses).
     */
    public BigDecimal getResteGlobal() {
        return getRecette().subtract(getTotalDepenses());
    }

    /**
     * Retourne le nombre de factures.
     */
    public int getNombreFactures() {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        return factureRepository.findByMoisBudget(moisCourant).size();
    }

    /**
     * Met a jour le chemin du document associe a une facture.
     */
    @Transactional
    public void mettreAJourDocumentPath(String id, String documentPath) {
        try {
            Long factureId = Long.parseLong(id);
            Optional<Facture> factureOpt = factureRepository.findById(factureId);
            if (factureOpt.isPresent()) {
                Facture facture = factureOpt.get();
                facture.setDocumentPath(documentPath);
                facture.setDateModification(LocalDateTime.now());
                factureRepository.save(facture);
                log.info("Document associe a la facture {} : {}", id, documentPath);
            }
        } catch (NumberFormatException e) {
            log.warn("ID invalide pour mise a jour document : {}", id);
        }
    }

    /**
     * Verifie si une facture similaire existe deja pour le mois courant.
     */
    public boolean existeDoublon(String fournisseur, BigDecimal montant, LocalDate date) {
        MoisBudget moisCourant = moisBudgetService.getMoisCourant();
        List<Facture> factures = factureRepository.findByMoisBudget(moisCourant);
        return factures.stream().anyMatch(f ->
            f.getFournisseur().equalsIgnoreCase(fournisseur)
            && f.getMontant().compareTo(montant) == 0
            && f.getDateFacture().equals(date)
        );
    }

    // ==================== VALIDATION ====================

    /**
     * Vérifie si l'application est prête à enregistrer des factures.
     */
    public boolean estPretPourFactures() {
        return isRecetteDefinie() && tousBudgetsObligatoiresDefinis();
    }

    /**
     * Retourne les messages d'erreur si pas prêt.
     */
    public List<String> getMessagesValidation() {
        List<String> messages = new ArrayList<>();
        if (!isRecetteDefinie()) {
            messages.add("La recette n'est pas définie. Utilisez 'recette-definir'");
        }
        for (Budget budget : getBudgetsObligatoires()) {
            if (!budget.estDefini()) {
                messages.add("Le budget '" + budget.getNom() + "' n'est pas défini");
            }
        }
        return messages;
    }

    /**
     * Valide la longueur du nom d'un budget.
     */
    private void validateNomLength(String nom) {
        if (nom != null && nom.length() > MAX_NOM_LENGTH) {
            throw new IllegalArgumentException(
                    "Le nom du budget ne peut pas dépasser " + MAX_NOM_LENGTH + " caractères");
        }
    }

    /**
     * Valide la longueur du nom d'un fournisseur.
     */
    private void validateFournisseurLength(String fournisseur) {
        if (fournisseur != null && fournisseur.length() > MAX_FOURNISSEUR_LENGTH) {
            throw new IllegalArgumentException(
                    "Le nom du fournisseur ne peut pas dépasser " + MAX_FOURNISSEUR_LENGTH + " caractères");
        }
    }
}
