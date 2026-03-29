package com.budget.controller;

import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.budget.entity.MoisBudget;
import com.budget.model.Budget;
import com.budget.service.BudgetService;
import com.budget.service.MoisBudgetService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import net.rgielen.fxweaver.core.FxmlView;

/**
 * Controleur du tableau de bord : fusionne recette, resume et historique des mois.
 */
@Component
@FxmlView("/fxml/tableau-de-bord.fxml")
public class TableauDeBordController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(TableauDeBordController.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    // --- Recette ---
    @FXML private Label recetteActuelleLabel;
    @FXML private TextField recetteField;
    @FXML private Label recetteMessageLabel;
    @FXML private HBox recetteDisplayBox;
    @FXML private HBox recetteEditBox;

    // --- Indicateurs ---
    @FXML private Label totalDepenseLabel;
    @FXML private Label resteGlobalLabel;
    @FXML private Label totalAlloueLabel;
    @FXML private Label nonAlloueLabel;

    // --- Tableau recapitulatif ---
    @FXML private TableView<ResumeRow> resumeTable;
    @FXML private TableColumn<ResumeRow, String> categorieColumn;
    @FXML private TableColumn<ResumeRow, Double> budgetColumn;
    @FXML private TableColumn<ResumeRow, Double> depenseColumn;
    @FXML private TableColumn<ResumeRow, Double> resteColumn;
    @FXML private TableColumn<ResumeRow, String> utilisationColumn;

    // --- Historique des mois ---
    @FXML private TableView<MoisRow> moisTable;
    @FXML private TableColumn<MoisRow, String> moisColumn;
    @FXML private TableColumn<MoisRow, String> moisRecetteColumn;
    @FXML private TableColumn<MoisRow, String> moisDepensesColumn;
    @FXML private TableColumn<MoisRow, String> moisStatutColumn;

    private final BudgetService budgetService;
    private final MoisBudgetService moisBudgetService;

    @Autowired
    public TableauDeBordController(BudgetService budgetService, MoisBudgetService moisBudgetService) {
        this.budgetService = budgetService;
        this.moisBudgetService = moisBudgetService;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configurerRecetteField();
        configurerResumeTable();
        configurerMoisTable();
        actualiserDonnees();
    }

    // ==================== RECETTE ====================

    private void configurerRecetteField() {
        recetteField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                recetteField.setText(oldVal);
            }
        });
        recetteField.textProperty().addListener((obs, oldVal, newVal) ->
            recetteMessageLabel.setText(""));
        recetteField.setOnAction(e -> sauvegarderRecette());
    }

    @FXML
    private void editerRecette() {
        BigDecimal recette = budgetService.getRecette();
        recetteField.setText(recette.compareTo(BigDecimal.ZERO) > 0
                ? recette.toPlainString() : "");
        recetteMessageLabel.setText("");
        recetteDisplayBox.setVisible(false);
        recetteDisplayBox.setManaged(false);
        recetteEditBox.setVisible(true);
        recetteEditBox.setManaged(true);
        recetteField.requestFocus();
        recetteField.selectAll();
    }

    @FXML
    private void annulerEditionRecette() {
        recetteEditBox.setVisible(false);
        recetteEditBox.setManaged(false);
        recetteDisplayBox.setVisible(true);
        recetteDisplayBox.setManaged(true);
        recetteMessageLabel.setText("");
    }

    @FXML
    private void sauvegarderRecette() {
        String text = recetteField.getText().trim();
        if (text.isEmpty()) {
            afficherRecetteMessage("Veuillez saisir un montant", true);
            return;
        }
        try {
            BigDecimal montant = new BigDecimal(text);
            if (montant.compareTo(BigDecimal.ZERO) <= 0) {
                afficherRecetteMessage("Le montant doit être positif", true);
                return;
            }
            budgetService.definirRecette(montant);
            annulerEditionRecette();
            actualiserDonnees();
        } catch (NumberFormatException e) {
            afficherRecetteMessage("Montant invalide", true);
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de la recette", e);
            afficherRecetteMessage("Une erreur est survenue", true);
        }
    }

    private void actualiserRecette() {
        BigDecimal recette = budgetService.getRecette();
        if (recette.compareTo(BigDecimal.ZERO) > 0) {
            recetteActuelleLabel.setText(String.format("%.2f €", recette));
            recetteActuelleLabel.setStyle("-fx-text-fill: #4CAF50;");
        } else {
            recetteActuelleLabel.setText("Non définie");
            recetteActuelleLabel.setStyle("-fx-text-fill: #757575;");
        }
    }

    private void afficherRecetteMessage(String message, boolean erreur) {
        recetteMessageLabel.setText(message);
        recetteMessageLabel.setStyle(erreur ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
    }

    // ==================== RESUME (graphiques + indicateurs) ====================

    private void configurerResumeTable() {
        categorieColumn.setCellValueFactory(new PropertyValueFactory<>("categorie"));
        budgetColumn.setCellValueFactory(new PropertyValueFactory<>("budget"));
        depenseColumn.setCellValueFactory(new PropertyValueFactory<>("depense"));
        resteColumn.setCellValueFactory(new PropertyValueFactory<>("reste"));
        utilisationColumn.setCellValueFactory(new PropertyValueFactory<>("utilisation"));

        budgetColumn.setCellFactory(col -> new TableCell<ResumeRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.2f €", item));
            }
        });

        depenseColumn.setCellFactory(col -> new TableCell<ResumeRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%.2f €", item));
            }
        });

        resteColumn.setCellFactory(col -> new TableCell<ResumeRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%.2f €", item));
                    setStyle(item < 0 ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
                }
            }
        });

        utilisationColumn.setCellFactory(col -> new TableCell<ResumeRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ResumeRow row = getTableView().getItems().get(getIndex());
                    double pct = row.getUtilisationPct();
                    if (pct > 100) {
                        setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                    } else if (pct > 80) {
                        setStyle("-fx-text-fill: #FF9800;");
                    } else {
                        setStyle("-fx-text-fill: #4CAF50;");
                    }
                }
            }
        });
    }

    private void actualiserResume() {
        BigDecimal recette = budgetService.getRecette();
        Collection<Budget> budgets = budgetService.getTousBudgetsAvecHistorique();

        ObservableList<ResumeRow> tableData = FXCollections.observableArrayList();

        for (Budget budget : budgets) {
            if (!budget.estDefini()) continue;

            BigDecimal alloue = budget.getMontantEffectif(recette);
            BigDecimal depense = budgetService.getTotalDepensesParBudget(budget);
            BigDecimal reste = alloue.subtract(depense);
            double alloueD = alloue.doubleValue();
            double depenseD = depense.doubleValue();
            double resteD = reste.doubleValue();
            double pct = alloueD > 0 ? (depenseD / alloueD) * 100 : 0;

            tableData.add(new ResumeRow(
                    budget.getNom(), alloueD, depenseD, resteD,
                    String.format("%.0f%%", pct), pct));
        }

        resumeTable.setItems(tableData);

        // Indicateurs
        BigDecimal totalDepenses = budgetService.getTotalDepenses();
        totalDepenseLabel.setText(String.format("%.2f €", totalDepenses));

        BigDecimal totalAlloue = budgetService.getTotalBudgetsAlloues();
        totalAlloueLabel.setText(String.format("%.2f €", totalAlloue));

        BigDecimal resteGlobal = budgetService.getResteGlobal();
        resteGlobalLabel.setText(String.format("%.2f €", resteGlobal));
        if (resteGlobal.compareTo(BigDecimal.ZERO) < 0) {
            resteGlobalLabel.setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
        } else if (resteGlobal.compareTo(recette.multiply(BigDecimal.valueOf(0.1))) < 0) {
            resteGlobalLabel.setStyle("-fx-text-fill: #FF9800;");
        } else {
            resteGlobalLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        }

        BigDecimal nonAlloue = budgetService.getMontantNonAlloue();
        BigDecimal pctNonAlloue = budgetService.getPourcentageNonAlloue();
        nonAlloueLabel.setText(String.format("%.2f € (%.1f%%)", nonAlloue, pctNonAlloue));
    }

    // ==================== HISTORIQUE DES MOIS ====================

    private void configurerMoisTable() {
        moisColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getMois()));
        moisRecetteColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRecette()));
        moisDepensesColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDepenses()));
        moisStatutColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatut()));

        moisStatutColumn.setCellFactory(col -> new TableCell<MoisRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(item.contains("Actif")
                            ? "-fx-text-fill: #2196F3; -fx-font-weight: bold;" : "");
                }
            }
        });
    }

    private void actualiserHistorique() {
        MoisBudget moisActif = moisBudgetService.getMoisCourant();
        List<MoisBudget> tousLesMois = moisBudgetService.getTousLesMois();
        ObservableList<MoisRow> rows = FXCollections.observableArrayList();

        for (MoisBudget mois : tousLesMois) {
            String moisStr = capitaliser(mois.getMois().format(MONTH_FMT));
            BigDecimal recetteVal = mois.getRecette() != null ? mois.getRecette() : BigDecimal.ZERO;
            BigDecimal depenses = mois.getFactures().stream()
                    .map(f -> f.getMontant() != null ? f.getMontant() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String statut = mois.getMois().equals(moisActif.getMois()) ? "✓ Actif" : "";
            rows.add(new MoisRow(moisStr,
                    String.format("%.2f €", recetteVal),
                    String.format("%.2f €", depenses),
                    statut));
        }
        moisTable.setItems(rows);
    }

    // ==================== ACTUALISATION GLOBALE ====================

    public void actualiserDonnees() {
        actualiserRecette();
        actualiserResume();
        actualiserHistorique();
    }

    // ==================== UTILITAIRES ====================

    private static String capitaliser(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ==================== CLASSES INTERNES ====================

    public static class ResumeRow {
        private final String categorie;
        private final Double budget;
        private final Double depense;
        private final Double reste;
        private final String utilisation;
        private final double utilisationPct;

        public ResumeRow(String categorie, Double budget, Double depense, Double reste,
                         String utilisation, double utilisationPct) {
            this.categorie = categorie;
            this.budget = budget;
            this.depense = depense;
            this.reste = reste;
            this.utilisation = utilisation;
            this.utilisationPct = utilisationPct;
        }

        public String getCategorie() { return categorie; }
        public Double getBudget() { return budget; }
        public Double getDepense() { return depense; }
        public Double getReste() { return reste; }
        public String getUtilisation() { return utilisation; }
        public double getUtilisationPct() { return utilisationPct; }
    }

    public static class MoisRow {
        private final String mois;
        private final String recette;
        private final String depenses;
        private final String statut;

        public MoisRow(String mois, String recette, String depenses, String statut) {
            this.mois = mois;
            this.recette = recette;
            this.depenses = depenses;
            this.statut = statut;
        }

        public String getMois() { return mois; }
        public String getRecette() { return recette; }
        public String getDepenses() { return depenses; }
        public String getStatut() { return statut; }
    }
}
