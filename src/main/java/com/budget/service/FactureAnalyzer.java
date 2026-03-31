package com.budget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.budget.entity.FournisseurMapping;
import com.budget.model.TypeFacture;
import com.budget.repository.FournisseurMappingRepository;

/**
 * Analyseur de texte de factures.
 * A partir du texte brut extrait d'un PDF ou d'une image, tente de detecter :
 * - le montant (via des regex sur "Net a payer", "Total TTC", etc.)
 * - la date (via des regex sur differents formats de date)
 * - le fournisseur et le type (via la table d'apprentissage locale + dictionnaire statique)
 */
@Service
public class FactureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(FactureAnalyzer.class);

    private final FournisseurMappingRepository mappingRepository;
    private final BudgetService budgetService;

    // Dictionnaire statique des fournisseurs connus.
    // Cle = mot-cle en minuscules, Valeur = [nom propre, type de facture]
    private final Map<String, FournisseurInfo> dictionnaireFournisseurs = new LinkedHashMap<>();

    public FactureAnalyzer(FournisseurMappingRepository mappingRepository,
                           BudgetService budgetService) {
        this.mappingRepository = mappingRepository;
        this.budgetService = budgetService;
        initialiserDictionnaire();
    }

    /**
     * Remplit le dictionnaire statique avec les fournisseurs francais courants.
     * C'est le "fallback" utilise quand la table d'apprentissage locale
     * ne contient pas encore le fournisseur.
     */
    private void initialiserDictionnaire() {
        // Electricite
        ajouterAuDictionnaire("edf", "EDF", TypeFacture.ELECTRICITE);
        ajouterAuDictionnaire("engie", "Engie", TypeFacture.ELECTRICITE);
        ajouterAuDictionnaire("total energies", "Total Energies", TypeFacture.ELECTRICITE);
        ajouterAuDictionnaire("totalenergies", "Total Energies", TypeFacture.ELECTRICITE);
        ajouterAuDictionnaire("direct energie", "Direct Energie", TypeFacture.ELECTRICITE);

        // Gaz
        ajouterAuDictionnaire("grdf", "GRDF", TypeFacture.GAZ);

        // Eau
        ajouterAuDictionnaire("veolia", "Veolia", TypeFacture.EAU);
        ajouterAuDictionnaire("suez", "Suez", TypeFacture.EAU);
        ajouterAuDictionnaire("saur", "Saur", TypeFacture.EAU);
        ajouterAuDictionnaire("eau de paris", "Eau de Paris", TypeFacture.EAU);

        // Internet / Telephone
        ajouterAuDictionnaire("sfr", "SFR", TypeFacture.INTERNET);
        ajouterAuDictionnaire("orange", "Orange", TypeFacture.INTERNET);
        ajouterAuDictionnaire("free", "Free", TypeFacture.INTERNET);
        ajouterAuDictionnaire("bouygues", "Bouygues Telecom", TypeFacture.INTERNET);
        ajouterAuDictionnaire("bouygues telecom", "Bouygues Telecom", TypeFacture.INTERNET);
        ajouterAuDictionnaire("proximus", "Proximus", TypeFacture.INTERNET);
        ajouterAuDictionnaire("voo", "VOO", TypeFacture.INTERNET);
        ajouterAuDictionnaire("telenet", "Telenet", TypeFacture.INTERNET);

        // Assurance
        ajouterAuDictionnaire("axa", "AXA", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("maif", "MAIF", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("matmut", "Matmut", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("groupama", "Groupama", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("allianz", "Allianz", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("macif", "MACIF", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("ethias", "Ethias", TypeFacture.ASSURANCE);
        ajouterAuDictionnaire("ag insurance", "AG Insurance", TypeFacture.ASSURANCE);

        // Transport
        ajouterAuDictionnaire("sncf", "SNCF", TypeFacture.TRANSPORT);
        ajouterAuDictionnaire("ratp", "RATP", TypeFacture.TRANSPORT);
        ajouterAuDictionnaire("navigo", "Navigo", TypeFacture.TRANSPORT);
        ajouterAuDictionnaire("sncb", "SNCB", TypeFacture.TRANSPORT);
        ajouterAuDictionnaire("stib", "STIB", TypeFacture.TRANSPORT);

        // Alimentation
        ajouterAuDictionnaire("carrefour", "Carrefour", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("leclerc", "Leclerc", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("auchan", "Auchan", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("lidl", "Lidl", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("intermarche", "Intermarché", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("colruyt", "Colruyt", TypeFacture.ALIMENTATION);
        ajouterAuDictionnaire("delhaize", "Delhaize", TypeFacture.ALIMENTATION);

        // Sante
        ajouterAuDictionnaire("cpam", "CPAM", TypeFacture.SANTE);
        ajouterAuDictionnaire("ameli", "Ameli", TypeFacture.SANTE);
        ajouterAuDictionnaire("mutuelle", "Mutuelle", TypeFacture.SANTE);

        // Loyer
        ajouterAuDictionnaire("loyer", "Loyer", TypeFacture.LOYER);

        // Impots
        ajouterAuDictionnaire("impots.gouv", "Impôts", TypeFacture.IMPOTS);
        ajouterAuDictionnaire("dgfip", "Impôts", TypeFacture.IMPOTS);
        ajouterAuDictionnaire("tresor public", "Trésor Public", TypeFacture.IMPOTS);
    }

    private void ajouterAuDictionnaire(String motCle, String fournisseur, TypeFacture type) {
        dictionnaireFournisseurs.put(motCle, new FournisseurInfo(fournisseur, type));
    }

    // ==================== ANALYSE PRINCIPALE ====================

    /**
     * Analyse le texte brut d'une facture et retourne un resultat
     * avec le montant, la date, le fournisseur et le type detectes.
     *
     * @param texte le texte brut extrait du document
     * @return un objet FactureAnalyzeResult avec les champs detectes (peuvent etre null)
     */
    public FactureAnalyzeResult analyser(String texte) {
        if (texte == null || texte.isBlank()) {
            return new FactureAnalyzeResult(null, null, null, null, false);
        }

        BigDecimal montant = extraireMontant(texte);
        LocalDate date = extraireDate(texte);
        FournisseurInfo fournisseurInfo = detecterFournisseur(texte);

        String fournisseur = fournisseurInfo != null ? fournisseurInfo.fournisseur : null;
        TypeFacture type = fournisseurInfo != null ? fournisseurInfo.type : null;
        boolean depuisDictionnaire = fournisseurInfo != null && fournisseurInfo.depuisDictionnaire;

        log.info("Analyse terminee : montant={}, date={}, fournisseur={}, type={}",
                montant, date, fournisseur, type);

        return new FactureAnalyzeResult(montant, date, fournisseur, type, depuisDictionnaire);
    }

    // ==================== EXTRACTION DU MONTANT ====================

    /**
     * Cherche un montant dans le texte en essayant plusieurs patterns
     * par ordre de priorite.
     *
     * Priorite :
     * 1. "Net a payer"  -> c'est le montant final a payer
     * 2. "Total TTC"    -> le total toutes taxes comprises
     * 3. "Montant TTC"  -> variante
     * 4. "TOTAL A PAYER"-> variante en majuscules
     * 5. Fallback       -> le plus grand montant trouve dans le texte
     */
    private BigDecimal extraireMontant(String texte) {
        // Liste de regex, de la plus specifique a la plus generique
        String[] patternsMonant = {
            "(?i)Net\\s*[aà]\\s*payer\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})",
            "(?i)Total\\s*TTC\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})",
            "(?i)Montant\\s*TTC\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})",
            "(?i)TOTAL\\s*[AÀ]\\s*PAYER\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})",
            "(?i)Montant\\s*total\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})",
            "(?i)Solde\\s*[aà]\\s*payer\\s*[:=\\s]*([\\d][\\d\\s]*[,.]\\d{2})"
        };

        for (String regex : patternsMonant) {
            BigDecimal montant = trouverMontant(texte, regex);
            if (montant != null) {
                return montant;
            }
        }

        // Fallback : chercher tous les montants et prendre le plus grand
        return trouverPlusGrandMontant(texte);
    }

    private BigDecimal trouverMontant(String texte, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(texte);
        if (matcher.find()) {
            try {
                String montantStr = matcher.group(1)
                        .replaceAll("\\s", "")    // Retirer les espaces (ex: "1 234,56" -> "1234,56")
                        .replace(",", ".");        // Remplacer la virgule par un point pour Java
                return new BigDecimal(montantStr);
            } catch (NumberFormatException e) {
                log.debug("Montant non parsable : {}", matcher.group(1));
            }
        }
        return null;
    }

    /**
     * Cherche tous les nombres ressemblant a des montants dans le texte
     * et retourne le plus grand. Utilise en dernier recours.
     */
    private BigDecimal trouverPlusGrandMontant(String texte) {
        Pattern pattern = Pattern.compile("(\\d[\\d\\s]*[,.]\\d{2})\\s*(?:€|EUR|euros?)?");
        Matcher matcher = pattern.matcher(texte);
        BigDecimal max = null;

        while (matcher.find()) {
            try {
                String montantStr = matcher.group(1)
                        .replaceAll("\\s", "")
                        .replace(",", ".");
                BigDecimal val = new BigDecimal(montantStr);
                if (val.compareTo(BigDecimal.ZERO) > 0
                        && val.compareTo(new BigDecimal("99999")) < 0) {
                    if (max == null || val.compareTo(max) > 0) {
                        max = val;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignorer les faux positifs
            }
        }
        return max;
    }

    // ==================== EXTRACTION DE LA DATE ====================

    /**
     * Cherche une date dans le texte en essayant plusieurs patterns.
     *
     * Priorite :
     * 1. "Date de facture : 15/03/2026"  -> la plus explicite
     * 2. "Facture du 15 mars 2026"       -> format textuel
     * 3. Premier format jj/mm/aaaa trouve -> generique
     * 4. Fallback -> date du jour
     */
    private LocalDate extraireDate(String texte) {
        // Pattern 1 : "Date (de facture|d'émission) : jj/mm/aaaa"
        Pattern p1 = Pattern.compile(
            "(?i)Date\\s*(?:de\\s*facture|d[''](?:é|e)mission)?\\s*[:=\\s]*(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})");
        Matcher m1 = p1.matcher(texte);
        if (m1.find()) {
            LocalDate date = parserDate(m1.group(1));
            if (date != null) return date;
        }

        // Pattern 2 : "Facture du 15 mars 2026"
        Pattern p2 = Pattern.compile(
            "(?i)Facture\\s+du\\s+(\\d{1,2})\\s+(janvier|f[eé]vrier|mars|avril|mai|juin|juillet|ao[uû]t|septembre|octobre|novembre|d[eé]cembre)\\s+(\\d{4})");
        Matcher m2 = p2.matcher(texte);
        if (m2.find()) {
            LocalDate date = parserDateTextuelle(m2.group(1), m2.group(2), m2.group(3));
            if (date != null) return date;
        }

        // Pattern 3 : premier jj/mm/aaaa dans le texte
        Pattern p3 = Pattern.compile("(\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4})");
        Matcher m3 = p3.matcher(texte);
        if (m3.find()) {
            LocalDate date = parserDate(m3.group(1));
            if (date != null) return date;
        }

        // Fallback : date du jour
        return LocalDate.now();
    }

    private LocalDate parserDate(String dateStr) {
        // Normalise les separateurs
        String normalise = dateStr.replace("-", "/").replace(".", "/");
        try {
            return LocalDate.parse(normalise, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate parserDateTextuelle(String jour, String moisTexte, String annee) {
        Map<String, Integer> moisMap = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("février", 2), Map.entry("fevrier", 2),
            Map.entry("mars", 3), Map.entry("avril", 4), Map.entry("mai", 5),
            Map.entry("juin", 6), Map.entry("juillet", 7),
            Map.entry("août", 8), Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("octobre", 10),
            Map.entry("novembre", 11), Map.entry("décembre", 12), Map.entry("decembre", 12)
        );
        try {
            Integer mois = moisMap.get(moisTexte.toLowerCase());
            if (mois != null) {
                return LocalDate.of(Integer.parseInt(annee), mois, Integer.parseInt(jour));
            }
        } catch (Exception e) {
            log.debug("Date textuelle non parsable : {} {} {}", jour, moisTexte, annee);
        }
        return null;
    }

    // ==================== DETECTION DU FOURNISSEUR ====================

    /**
     * Detecte le fournisseur dans le texte.
     *
     * Strategie en 2 etapes :
     * 1. On consulte la TABLE D'APPRENTISSAGE locale (fournisseur_mapping)
     *    -> Si un mot-cle correspond, on utilise le fournisseur et type associes.
     *       C'est prioritaire car l'utilisateur a deja confirme cette association.
     *
     * 2. Si rien dans la table, on consulte le DICTIONNAIRE STATIQUE
     *    -> On cherche chaque mot-cle du dictionnaire dans le texte.
     */
    private FournisseurInfo detecterFournisseur(String texte) {
        String texteLower = texte.toLowerCase();

        // Etape 1 : table d'apprentissage locale (prioritaire)
        List<FournisseurMapping> mappings = mappingRepository.findAll();
        for (FournisseurMapping mapping : mappings) {
            if (texteLower.contains(mapping.getMotCle().toLowerCase())) {
                log.info("Fournisseur detecte via table locale : {} (mot-cle: {})",
                        mapping.getFournisseur(), mapping.getMotCle());
                return new FournisseurInfo(mapping.getFournisseur(), mapping.getType(), true);
            }
        }

        // Etape 2 : dictionnaire statique
        for (Map.Entry<String, FournisseurInfo> entry : dictionnaireFournisseurs.entrySet()) {
            if (texteLower.contains(entry.getKey())) {
                log.info("Fournisseur detecte via dictionnaire statique : {} (mot-cle: {})",
                        entry.getValue().fournisseur, entry.getKey());
                FournisseurInfo info = entry.getValue();
                return new FournisseurInfo(info.fournisseur, info.type, true);
            }
        }

        return null;
    }

    // ==================== APPRENTISSAGE ====================

    /**
     * Enregistre ou met a jour une association mot-cle -> fournisseur + type.
     * Appelee quand l'utilisateur corrige un fournisseur lors de l'import.
     *
     * @param motCle      le mot-cle a associer (en minuscules)
     * @param fournisseur le nom propre du fournisseur
     * @param type        le type de facture
     */
    public void apprendreFournisseur(String motCle, String fournisseur, TypeFacture type) {
        String motCleNormalise = motCle.toLowerCase().trim();
        Optional<FournisseurMapping> existant = mappingRepository.findByMotCle(motCleNormalise);

        FournisseurMapping mapping;
        if (existant.isPresent()) {
            mapping = existant.get();
            mapping.setFournisseur(fournisseur);
            mapping.setType(type);
        } else {
            mapping = new FournisseurMapping(motCleNormalise, fournisseur, type);
        }

        mappingRepository.save(mapping);
        log.info("Fournisseur appris : '{}' -> {} ({})", motCleNormalise, fournisseur, type);
    }

    /**
     * Verifie si une facture similaire existe deja pour le mois courant.
     */
    public boolean existeDoublon(String fournisseur, BigDecimal montant, LocalDate date) {
        return budgetService.existeDoublon(fournisseur, montant, date);
    }

    // ==================== CLASSES INTERNES ====================

    /**
     * Resultat de l'analyse d'une facture.
     * Tous les champs peuvent etre null si la detection echoue.
     */
    public static class FactureAnalyzeResult {
        private final BigDecimal montant;
        private final LocalDate date;
        private final String fournisseur;
        private final TypeFacture type;
        private final boolean depuisDictionnaire;

        public FactureAnalyzeResult(BigDecimal montant, LocalDate date,
                                     String fournisseur, TypeFacture type,
                                     boolean depuisDictionnaire) {
            this.montant = montant;
            this.date = date;
            this.fournisseur = fournisseur;
            this.type = type;
            this.depuisDictionnaire = depuisDictionnaire;
        }

        public BigDecimal getMontant() { return montant; }
        public LocalDate getDate() { return date; }
        public String getFournisseur() { return fournisseur; }
        public TypeFacture getType() { return type; }
        public boolean isDepuisDictionnaire() { return depuisDictionnaire; }
    }

    /**
     * Informations sur un fournisseur detecte.
     */
    private static class FournisseurInfo {
        final String fournisseur;
        final TypeFacture type;
        final boolean depuisDictionnaire;

        FournisseurInfo(String fournisseur, TypeFacture type) {
            this(fournisseur, type, false);
        }

        FournisseurInfo(String fournisseur, TypeFacture type, boolean depuisDictionnaire) {
            this.fournisseur = fournisseur;
            this.type = type;
            this.depuisDictionnaire = depuisDictionnaire;
        }
    }
}
