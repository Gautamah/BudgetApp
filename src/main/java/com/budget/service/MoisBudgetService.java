package com.budget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budget.entity.BudgetMois;
import com.budget.entity.Facture;
import com.budget.entity.MoisBudget;
import com.budget.model.CategorieBudget;
import com.budget.repository.BudgetMoisRepository;
import com.budget.repository.MoisBudgetRepository;

/**
 * Service pour gérer les mois de budget et le report automatique des charges incompressibles.
 */
@Service
public class MoisBudgetService {

    private static final Logger log = LoggerFactory.getLogger(MoisBudgetService.class);

    private final MoisBudgetRepository moisBudgetRepository;
    private final BudgetMoisRepository budgetMoisRepository;
    private YearMonth moisCourant;  // Cache du mois courant

    public MoisBudgetService(MoisBudgetRepository moisBudgetRepository, 
                            BudgetMoisRepository budgetMoisRepository) {
        this.moisBudgetRepository = moisBudgetRepository;
        this.budgetMoisRepository = budgetMoisRepository;
    }

    /**
     * Récupère ou crée le mois actif (celui sélectionné par l'utilisateur, ou le mois réel par défaut).
     */
    @Transactional
    public MoisBudget getMoisCourant() {
        if (moisCourant == null) {
            moisCourant = YearMonth.now();
        }
        MoisBudget mois = moisBudgetRepository.findByMois(moisCourant)
            .orElseGet(() -> creerNouveauMois(moisCourant));
        appliquerPourcentagesParDefaut(mois);
        return mois;
    }

    /**
     * Recupere ou cree le MoisBudget pour un mois donne, sans changer le mois affiche.
     */
    @Transactional
    public MoisBudget getMoisPour(YearMonth mois) {
        return moisBudgetRepository.findByMois(mois)
            .orElseGet(() -> creerNouveauMois(mois));
    }

    /**
     * Change le mois actif (pour navigation).
     */
    @Transactional
    public MoisBudget changerMois(YearMonth mois) {
        this.moisCourant = mois;
        MoisBudget moisBudget = moisBudgetRepository.findByMois(mois)
            .orElseGet(() -> creerNouveauMois(mois));
        appliquerPourcentagesParDefaut(moisBudget);
        return moisBudget;
    }

    /**
     * Récupère un mois spécifique.
     */
    public Optional<MoisBudget> getMois(YearMonth mois) {
        return moisBudgetRepository.findByMois(mois);
    }

    /**
     * Liste tous les mois disponibles.
     */
    @Transactional(readOnly = true)
    public List<MoisBudget> getTousLesMois() {
        List<MoisBudget> mois = moisBudgetRepository.findAllByOrderByMoisDesc();
        // Force l'initialisation des collections lazy pour éviter LazyInitializationException
        for (MoisBudget m : mois) {
            if (m.getFactures() != null) {
                m.getFactures().size(); // Force le chargement
            }
            if (m.getBudgets() != null) {
                m.getBudgets().size(); // Force le chargement
            }
        }
        return mois;
    }
    
    /**
     * Calcule le total des dépenses pour un mois donné.
     */
    @Transactional(readOnly = true)
    public BigDecimal getDepensesMois(YearMonth mois) {
        return moisBudgetRepository.findByMois(mois)
            .map(m -> m.getFactures().stream()
                .map(Facture::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Applique les pourcentages par défaut (règle 50/30/20) aux budgets obligatoires
     * qui n'ont ni montant ni pourcentage défini.
     */
    private void appliquerPourcentagesParDefaut(MoisBudget mois) {
        boolean modified = false;
        for (BudgetMois budget : mois.getBudgets()) {
            if (budget.getObligatoire() && !budget.estDefini()
                    && budget.getCategorie().getPourcentageDefaut() != null) {
                budget.setPourcentage(budget.getCategorie().getPourcentageDefaut());
                modified = true;
            }
        }
        if (modified) {
            moisBudgetRepository.save(mois);
            log.info("Pourcentages par défaut (50/30/20) appliqués aux budgets non définis");
        }
    }

    /**
     * Crée un nouveau mois en reportant depuis le mois précédent :
     * - La recette
     * - TOUS les budgets actifs (obligatoires avec montants + personnalisés)
     * - Les factures de charges incompressibles (même type, fournisseur, montant)
     */
    @Transactional
    private MoisBudget creerNouveauMois(YearMonth mois) {
        log.info("Création d'un nouveau mois budgétaire : {}", mois);

        MoisBudget nouveauMois = new MoisBudget();
        nouveauMois.setMois(mois);
        nouveauMois.setRecette(BigDecimal.ZERO);
        nouveauMois.setDateCreation(LocalDateTime.now());
        nouveauMois.setDateModification(LocalDateTime.now());
        
        // Récupérer le mois précédent
        Optional<MoisBudget> moisPrecedent = moisBudgetRepository
            .findByMois(mois.minusMonths(1));
        
        if (moisPrecedent.isPresent()) {
            MoisBudget precedent = moisPrecedent.get();
            
            // 1. Reporter la recette du mois précédent
            nouveauMois.setRecette(precedent.getRecette());
            log.debug("Report de la recette : {}", precedent.getRecette());
            
            // 2. Reporter TOUS les budgets actifs du mois précédent
            List<BudgetMois> budgetsPrecedents = budgetMoisRepository
                .findByMoisBudgetAndActifTrue(precedent);
            
            for (BudgetMois budgetPrecedent : budgetsPrecedents) {
                BudgetMois nouveauBudget = new BudgetMois();
                nouveauBudget.setMoisBudget(nouveauMois);
                nouveauBudget.setCategorie(budgetPrecedent.getCategorie());
                nouveauBudget.setNomPersonnalise(budgetPrecedent.getNomPersonnalise());
                nouveauBudget.setObligatoire(budgetPrecedent.getObligatoire());
                nouveauBudget.setActif(true);
                nouveauBudget.setReporte(true);
                
                // Reporter les montants/pourcentages si définis
                if (budgetPrecedent.estDefini()) {
                    nouveauBudget.setMontantFixe(budgetPrecedent.getMontantFixe());
                    nouveauBudget.setPourcentage(budgetPrecedent.getPourcentage());
                }
                
                nouveauMois.getBudgets().add(nouveauBudget);
            }
            log.debug("{} budgets reportés du mois précédent", budgetsPrecedents.size());
            
            // S'assurer que les catégories obligatoires existent même si absentes du mois précédent
            for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
                boolean existe = nouveauMois.getBudgets().stream()
                    .anyMatch(b -> b.getCategorie() == cat);
                if (!existe) {
                    BudgetMois budget = new BudgetMois();
                    budget.setMoisBudget(nouveauMois);
                    budget.setCategorie(cat);
                    budget.setObligatoire(true);
                    budget.setReporte(false);
                    budget.setPourcentage(cat.getPourcentageDefaut());
                    nouveauMois.getBudgets().add(budget);
                }
            }
            
            // 3. Reporter les factures de charges incompressibles
            LocalDate premierJourNouveauMois = mois.atDay(1);
            int facturesReportees = 0;
            for (Facture facturePrecedente : precedent.getFactures()) {
                if (facturePrecedente.getType().getCategorie() == CategorieBudget.CHARGES_INCOMPRESSIBLES) {
                    Facture nouvelleFacture = new Facture();
                    nouvelleFacture.setMoisBudget(nouveauMois);
                    nouvelleFacture.setType(facturePrecedente.getType());
                    nouvelleFacture.setFournisseur(facturePrecedente.getFournisseur());
                    nouvelleFacture.setMontant(facturePrecedente.getMontant());
                    nouvelleFacture.setDateFacture(premierJourNouveauMois);
                    nouvelleFacture.setDateCreation(LocalDateTime.now());
                    nouvelleFacture.setDateModification(LocalDateTime.now());
                    nouveauMois.getFactures().add(nouvelleFacture);
                    facturesReportees++;
                }
            }
            log.debug("{} factures incompressibles reportées", facturesReportees);
            
        } else {
            // Premier mois : initialiser les budgets obligatoires avec pourcentages par défaut (règle 50/30/20)
            for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
                BudgetMois budget = new BudgetMois();
                budget.setMoisBudget(nouveauMois);
                budget.setCategorie(cat);
                budget.setObligatoire(true);
                budget.setReporte(false);
                budget.setPourcentage(cat.getPourcentageDefaut());
                nouveauMois.getBudgets().add(budget);
            }
        }
        
        return moisBudgetRepository.save(nouveauMois);
    }
    
    /**
     * Met à jour la recette du mois courant.
     */
    @Transactional
    public void mettreAJourRecette(BigDecimal nouvelleRecette) {
        MoisBudget moisCourant = getMoisCourant();
        moisCourant.setRecette(nouvelleRecette);
        moisCourant.setDateModification(LocalDateTime.now());
        moisBudgetRepository.save(moisCourant);
        log.info("Recette mise à jour : {}", nouvelleRecette);
    }

    /**
     * Récupère le mois actuellement actif (pour affichage).
     */
    public YearMonth getMoisActif() {
        if (moisCourant == null) {
            moisCourant = YearMonth.now();
        }
        return moisCourant;
    }

    /**
     * Définit le mois actif.
     */
    @Transactional
    public void setMoisActif(YearMonth mois) {
        this.moisCourant = mois;
        // S'assurer que le mois existe dans la base
        moisBudgetRepository.findByMois(mois)
            .orElseGet(() -> creerNouveauMois(mois));
    }

    /**
     * Copie les budgets du mois précédent vers le mois courant.
     */
    @Transactional
    public void copierBudgetsDepuisMoisPrecedent() {
        MoisBudget moisActuel = getMoisCourant();
        YearMonth moisPrecedentYM = moisActuel.getMois().minusMonths(1);
        
        Optional<MoisBudget> moisPrecedentOpt = moisBudgetRepository.findByMois(moisPrecedentYM);
        if (moisPrecedentOpt.isEmpty()) {
            throw new IllegalStateException("Aucun mois précédent trouvé pour la copie");
        }
        
        MoisBudget moisPrecedent = moisPrecedentOpt.get();
        List<BudgetMois> budgetsPrecedents = budgetMoisRepository.findByMoisBudget(moisPrecedent);
        
        for (BudgetMois budgetPrecedent : budgetsPrecedents) {
            // Ne pas copier les charges incompressibles (déjà reportées automatiquement)
            if (budgetPrecedent.getCategorie() != CategorieBudget.CHARGES_INCOMPRESSIBLES) {
                // Vérifier si ce budget n'existe pas déjà
                boolean existe = moisActuel.getBudgets().stream()
                    .anyMatch(b -> {
                        if (budgetPrecedent.getCategorie() == CategorieBudget.PERSONNALISE) {
                            return b.getCategorie() == CategorieBudget.PERSONNALISE 
                                && budgetPrecedent.getNomPersonnalise() != null
                                && budgetPrecedent.getNomPersonnalise().equals(b.getNomPersonnalise());
                        }
                        return b.getCategorie() == budgetPrecedent.getCategorie();
                    });
                
                if (!existe) {
                    BudgetMois nouveauBudget = new BudgetMois();
                    nouveauBudget.setMoisBudget(moisActuel);
                    nouveauBudget.setCategorie(budgetPrecedent.getCategorie());
                    nouveauBudget.setNomPersonnalise(budgetPrecedent.getNomPersonnalise());
                    nouveauBudget.setMontantFixe(budgetPrecedent.getMontantFixe());
                    nouveauBudget.setPourcentage(budgetPrecedent.getPourcentage());
                    nouveauBudget.setObligatoire(budgetPrecedent.getObligatoire());
                    nouveauBudget.setReporte(false);
                    moisActuel.getBudgets().add(nouveauBudget);
                } else {
                    // Mettre à jour le budget existant
                    moisActuel.getBudgets().stream()
                        .filter(b -> {
                            if (budgetPrecedent.getCategorie() == CategorieBudget.PERSONNALISE) {
                                return b.getCategorie() == CategorieBudget.PERSONNALISE 
                                    && budgetPrecedent.getNomPersonnalise() != null
                                    && budgetPrecedent.getNomPersonnalise().equals(b.getNomPersonnalise());
                            }
                            return b.getCategorie() == budgetPrecedent.getCategorie();
                        })
                        .findFirst()
                        .ifPresent(b -> {
                            b.setMontantFixe(budgetPrecedent.getMontantFixe());
                            b.setPourcentage(budgetPrecedent.getPourcentage());
                        });
                }
            }
        }
        
        moisBudgetRepository.save(moisActuel);
    }
}
