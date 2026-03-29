package com.budget.controller;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.budget.model.Budget;
import com.budget.model.CategorieBudget;
import com.budget.service.BudgetService;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;
import net.rgielen.fxweaver.core.FxmlView;

/**
 * Controleur pour la gestion des budgets.
 * Le tableau est entierement editable :
 * - Double-cliquer sur "Budget alloue" pour modifier un budget existant.
 * - La derniere ligne permet d'ajouter un budget personnalise.
 * - Chaque ligne a une croix (X) pour supprimer le budget.
 */
@Component
@FxmlView("/fxml/budget.fxml")
public class BudgetController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);
    private static final String NEW_ROW_PLACEHOLDER = "\u2795 Nouveau budget...";

    @FXML
    private TableView<BudgetRow> budgetTable;

    @FXML
    private TableColumn<BudgetRow, String> categorieColumn;

    @FXML
    private TableColumn<BudgetRow, String> budgetColumn;

    @FXML
    private TableColumn<BudgetRow, Double> depenseColumn;

    @FXML
    private TableColumn<BudgetRow, Double> resteColumn;

    @FXML
    private TableColumn<BudgetRow, String> progressColumn;

    @FXML
    private TableColumn<BudgetRow, Void> actionColumn;

    @FXML
    private Label messageLabel;

    @FXML
    private Label nonAlloueLabel;

    private final BudgetService budgetService;
    private ObservableList<BudgetRow> budgetRows = FXCollections.observableArrayList();

    @Autowired
    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configurerTable();
        chargerBudgets();
    }

    /**
     * Configure les colonnes du tableau avec edition inline.
     */
    private void configurerTable() {
        budgetTable.setEditable(true);

        // Style de la ligne d'ajout (derniere ligne) en italique gris
        budgetTable.setRowFactory(tv -> new TableRow<BudgetRow>() {
            @Override
            protected void updateItem(BudgetRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isNewRow()) {
                    setStyle("-fx-font-style: italic;");
                } else {
                    setStyle("");
                }
            }
        });

        // ===== Categorie : editable UNIQUEMENT pour la ligne d'ajout =====
        categorieColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCategorie()));
        categorieColumn.setEditable(true);
        categorieColumn.setCellFactory(col ->
            new TextFieldTableCell<BudgetRow, String>(new DefaultStringConverter()) {
                private boolean focusListenerAdded = false;

                private void showFieldError(TextField tf, String message) {
                    tf.setStyle("-fx-border-color: #F44336; -fx-border-width: 2; -fx-border-radius: 3;");
                    Tooltip tip = new Tooltip(message);
                    tip.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 12;");
                    tf.setTooltip(tip);
                }

                private void clearFieldError(TextField tf) {
                    tf.setStyle("");
                    tf.setTooltip(null);
                }

                @Override
                public void commitEdit(String newValue) {
                    String trimmed = newValue != null ? newValue.trim() : "";
                    if (trimmed.isEmpty() || trimmed.contains("Nouveau")) {
                        // Si c'est une new row non modifiée, annuler silencieusement
                        int idx = getIndex();
                        if (idx >= 0 && idx < getTableView().getItems().size()) {
                            BudgetRow row = getTableView().getItems().get(idx);
                            if (row != null && row.isNewRow() && !row.isPartiallyFilled()) {
                                if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                                row.setEditingStarted(false);
                                super.cancelEdit();
                                afficherMessage("", false);
                                return;
                            }
                        }
                        if (getGraphic() instanceof TextField) {
                            showFieldError((TextField) getGraphic(), "Le nom du budget ne peut pas être vide");
                            afficherMessage("Le nom du budget ne peut pas être vide", true);
                        }
                        return; // Reste en édition
                    }
                    if (trimmed.length() > 100) {
                        if (getGraphic() instanceof TextField) {
                            showFieldError((TextField) getGraphic(), "Le nom ne peut pas dépasser 100 caractères");
                            afficherMessage("Le nom ne peut pas dépasser 100 caractères", true);
                        }
                        return; // Reste en édition
                    }
                    if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                    super.commitEdit(newValue);
                }

                @Override
                public void cancelEdit() {
                    if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                    super.cancelEdit();
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        BudgetRow row = getTableView().getItems().get(idx);
                        if (row != null && row.isNewRow()) {
                            row.setEditingStarted(false);
                            if (row.isPartiallyFilled()) {
                                Platform.runLater(() -> chargerBudgets());
                            }
                        }
                    }
                }

                @Override
                public void startEdit() {
                    int idx = getIndex();
                    if (idx < 0 || idx >= getTableView().getItems().size()) return;
                    BudgetRow row = getTableView().getItems().get(idx);
                    if (row == null || !row.isNewRow()) return;
                    super.startEdit();
                    if (isEditing()) {
                        row.setEditingStarted(true);
                        if (getGraphic() instanceof TextField) {
                            TextField tf = (TextField) getGraphic();
                            if (!focusListenerAdded) {
                                tf.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                                    if (!isNowFocused && isEditing()) {
                                        commitEdit(tf.getText());
                                    }
                                });
                                focusListenerAdded = true;
                            }
                            if (tf.getText() != null && tf.getText().contains("Nouveau")) {
                                tf.setText("");
                                tf.setPromptText("Nom du budget");
                            }
                        }
                    }
                }
            }
        );
        categorieColumn.setOnEditCommit(event -> {
            BudgetRow row = event.getRowValue();
            if (row.isNewRow()) {
                String nom = event.getNewValue().trim();
                if (!nom.isEmpty() && !nom.contains("Nouveau")) {
                    row.setCategorie(nom);
                    budgetTable.refresh();
                    afficherMessage("Nom défini : '" + nom + "'. Définissez maintenant le budget alloué.", false);
                    Platform.runLater(() -> {
                        int rowIndex = budgetTable.getItems().indexOf(row);
                        if (rowIndex >= 0) {
                            budgetTable.getSelectionModel().select(rowIndex);
                            budgetTable.edit(rowIndex, budgetColumn);
                        }
                    });
                }
            }
        });

        // ===== Budget alloue : EDITABLE pour toutes les lignes =====
        budgetColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getBudgetStr()));
        budgetColumn.setEditable(true);
        budgetColumn.setCellFactory(col ->
            new TextFieldTableCell<BudgetRow, String>(new DefaultStringConverter()) {
                private boolean focusListenerAdded = false;

                private void showFieldError(TextField tf, String message) {
                    tf.setStyle("-fx-border-color: #F44336; -fx-border-width: 2; -fx-border-radius: 3;");
                    Tooltip tip = new Tooltip(message);
                    tip.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 12;");
                    tf.setTooltip(tip);
                }

                private void clearFieldError(TextField tf) {
                    tf.setStyle("");
                    tf.setTooltip(null);
                }

                private String validateBudgetValue(String value) {
                    if (value == null || value.trim().isEmpty()) return null; // vide = annuler
                    String v = value.trim();
                    boolean isPct = v.contains("%");
                    String numPart = isPct
                            ? v.substring(0, v.indexOf("%")).trim()
                            : v.replace("€", "").replace(",", ".").trim();
                    if (numPart.contains("(")) numPart = numPart.substring(0, numPart.indexOf("(")).trim();
                    try {
                        BigDecimal val = new BigDecimal(numPart);
                        if (isPct && (val.compareTo(BigDecimal.ZERO) < 0 || val.compareTo(BigDecimal.valueOf(100)) > 0))
                            return "Le pourcentage doit être entre 0 et 100";
                        if (!isPct && val.compareTo(BigDecimal.ZERO) < 0)
                            return "Le montant doit être positif";
                        return null; // valide
                    } catch (NumberFormatException e) {
                        return "Valeur invalide. Utilisez un nombre (ex: 500) ou un pourcentage (ex: 15%)";
                    }
                }

                @Override
                public void commitEdit(String newValue) {
                    String trimmed = newValue != null ? newValue.trim() : "";
                    if (trimmed.isEmpty()) {
                        // Vide = annuler silencieusement
                        if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                        super.commitEdit(newValue);
                        return;
                    }
                    String erreur = validateBudgetValue(trimmed);
                    if (erreur != null) {
                        if (getGraphic() instanceof TextField) {
                            showFieldError((TextField) getGraphic(), erreur);
                            afficherMessage(erreur, true);
                        }
                        return; // Reste en édition
                    }
                    if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                    super.commitEdit(newValue);
                }

                @Override
                public void cancelEdit() {
                    if (getGraphic() instanceof TextField) clearFieldError((TextField) getGraphic());
                    super.cancelEdit();
                    // Si on annule sur une new row, réinitialiser
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        BudgetRow row = getTableView().getItems().get(idx);
                        if (row != null && row.isNewRow()) {
                            Platform.runLater(() -> chargerBudgets());
                        }
                    }
                }

                @Override
                public void startEdit() {
                    int idx = getIndex();
                    if (idx < 0 || idx >= getTableView().getItems().size()) return;
                    BudgetRow row = getTableView().getItems().get(idx);
                    if (row != null && row.isNewRow()
                            && (row.getCategorie().contains("Nouveau") || row.getCategorie().trim().isEmpty())) {
                        afficherMessage("Définissez d'abord le nom (double-cliquez sur la catégorie)", true);
                        return;
                    }
                    super.startEdit();
                    if (isEditing() && getGraphic() instanceof TextField) {
                        TextField tf = (TextField) getGraphic();
                        if (!focusListenerAdded) {
                            tf.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                                if (!isNowFocused && isEditing()) {
                                    commitEdit(tf.getText());
                                }
                            });
                            focusListenerAdded = true;
                        }
                        if (row != null && row.isNewRow()) {
                            tf.setText("");
                            tf.setPromptText("ex: 500 ou 15%");
                        }
                    }
                }
            }
        );
        budgetColumn.setOnEditCommit(event -> {
            BudgetRow row = event.getRowValue();
            String newValue = event.getNewValue().trim();
            if (newValue.isEmpty()) {
                chargerBudgets();
                return;
            }
            if (row.isNewRow()) {
                creerBudget(row.getCategorie(), newValue);
            } else {
                modifierBudget(row, newValue);
            }
        });

        // ===== Depense : non editable =====
        depenseColumn.setCellValueFactory(cellData ->
                new SimpleDoubleProperty(cellData.getValue().getDepense()).asObject());
        depenseColumn.setCellFactory(col -> new TableCell<BudgetRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int idx = getIndex();
                    BudgetRow row = (idx >= 0 && idx < getTableView().getItems().size())
                            ? getTableView().getItems().get(idx) : null;
                    setText(row != null && row.isNewRow() ? "" : String.format("%.2f €", item));
                }
            }
        });
        depenseColumn.setEditable(false);

        // ===== Reste : non editable, colore =====
        resteColumn.setCellValueFactory(cellData ->
                new SimpleDoubleProperty(cellData.getValue().getReste()).asObject());
        resteColumn.setCellFactory(col -> new TableCell<BudgetRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    int idx = getIndex();
                    BudgetRow row = (idx >= 0 && idx < getTableView().getItems().size())
                            ? getTableView().getItems().get(idx) : null;
                    if (row != null && row.isNewRow()) {
                        setText("");
                        setStyle("");
                    } else {
                        setText(String.format("%.2f €", item));
                        setStyle(item < 0 ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
                    }
                }
            }
        });
        resteColumn.setEditable(false);

        // ===== Utilisation : non editable, colore =====
        progressColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getProgressStr()));
        progressColumn.setCellFactory(col -> new TableCell<BudgetRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    int idx = getIndex();
                    BudgetRow row = (idx >= 0 && idx < getTableView().getItems().size())
                            ? getTableView().getItems().get(idx) : null;
                    if (row != null && row.isNewRow()) {
                        setText("");
                        setStyle("");
                    } else {
                        setText(item);
                        double pct = row.getProgressPct();
                        if (pct > 100) {
                            setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                        } else if (pct > 80) {
                            setStyle("-fx-text-fill: #FF9800;");
                        } else {
                            setStyle("-fx-text-fill: #4CAF50;");
                        }
                    }
                }
            }
        });
        progressColumn.setEditable(false);

        // ===== Action : croix rouge pour supprimer (budgets personnalisés uniquement) =====
        actionColumn.setCellFactory(col -> new TableCell<BudgetRow, Void>() {
            private final Label deleteLabel = new Label("X");
            private ChangeListener<Boolean> editingListener;
            private BudgetRow currentRow;
            {
                deleteLabel.setStyle(
                    "-fx-text-fill: #F44336; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
                deleteLabel.setOnMouseEntered(e -> deleteLabel.setStyle(
                    "-fx-text-fill: #FFFFFF; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand; " +
                    "-fx-background-color: #F44336; -fx-background-radius: 50; -fx-padding: 0 4 0 4;"));
                deleteLabel.setOnMouseExited(e -> deleteLabel.setStyle(
                    "-fx-text-fill: #F44336; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;"));
                deleteLabel.setOnMouseClicked(event -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        BudgetRow row = getTableView().getItems().get(idx);
                        supprimerBudget(row);
                    }
                });
            }

            private void updateDisplay(BudgetRow row) {
                if (row != null && ((!row.isNewRow() && !row.isObligatoire())
                        || (row.isNewRow() && (row.isEditingStarted() || row.isPartiallyFilled())))) {
                    setGraphic(deleteLabel);
                    setAlignment(Pos.CENTER);
                } else {
                    setGraphic(null);
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                // Nettoyer l'ancien listener
                if (currentRow != null && editingListener != null) {
                    currentRow.editingStartedProperty().removeListener(editingListener);
                }
                if (empty) {
                    setGraphic(null);
                    currentRow = null;
                } else {
                    int idx = getIndex();
                    BudgetRow row = (idx >= 0 && idx < getTableView().getItems().size())
                            ? getTableView().getItems().get(idx) : null;
                    currentRow = row;
                    // Ecouter les changements de editingStarted pour les new rows
                    if (row != null && row.isNewRow()) {
                        editingListener = (obs, oldVal, newVal) -> updateDisplay(row);
                        row.editingStartedProperty().addListener(editingListener);
                    }
                    updateDisplay(row);
                }
            }
        });
        actionColumn.setEditable(false);
        actionColumn.setSortable(false);

        budgetTable.setItems(budgetRows);
    }

    /**
     * Supprime un budget via le bouton X de la ligne.
     */
    private void supprimerBudget(BudgetRow row) {
        if (row.isNewRow()) {
            chargerBudgets();
            afficherMessage("Ajout annulé", false);
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Suppression de budget");
        confirmation.setContentText("Souhaitez-vous vraiment supprimer ce budget ?");

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (budgetService.supprimerBudget(row.getCategorie(), row.isObligatoire())) {
                afficherMessage("Budget '" + row.getCategorie() + "' supprimé", false);
                chargerBudgets();
            } else {
                afficherMessage("Erreur lors de la suppression", true);
            }
        }
    }

    /**
     * Cree un nouveau budget personnalise via la derniere ligne du tableau.
     */
    private void creerBudget(String nom, String valeurStr) {
        try {
            boolean isPourcentage = valeurStr.contains("%");
            String cleanVal = isPourcentage
                    ? valeurStr.substring(0, valeurStr.indexOf("%")).trim()
                    : valeurStr.replace("€", "").replace(",", ".").trim();
            BigDecimal valeur = new BigDecimal(cleanVal);

            if (isPourcentage) {
                if (valeur.compareTo(BigDecimal.ZERO) < 0 || valeur.compareTo(BigDecimal.valueOf(100)) > 0) return;
                budgetService.ajouterBudgetPersonnalisePourcentage(nom, valeur);
            } else {
                if (valeur.compareTo(BigDecimal.ZERO) < 0) return;
                budgetService.ajouterBudgetPersonnalise(nom, valeur);
            }
            afficherMessage("Budget '" + nom + "' ajouté avec succès !", false);
        } catch (NumberFormatException e) {
            afficherMessage("Valeur invalide", true);
        } catch (Exception e) {
            log.error("Erreur lors de la création du budget '{}'", nom, e);
            afficherMessage("Une erreur est survenue lors de la création du budget", true);
        }
        chargerBudgets();
    }

    /**
     * Modifie un budget existant via edition inline du tableau.
     */
    private void modifierBudget(BudgetRow row, String newValue) {
        try {
            boolean isPourcentage = newValue.contains("%");
            String valStr = newValue;

            if (isPourcentage) {
                valStr = valStr.substring(0, valStr.indexOf("%")).trim();
                if (valStr.contains("(")) {
                    valStr = valStr.substring(0, valStr.indexOf("(")).trim();
                }
            } else {
                valStr = valStr.replace("€", "").replace(",", ".").trim();
            }

            BigDecimal valeur = new BigDecimal(valStr);

            if (row.isObligatoire()) {
                CategorieBudget cat = findCategorie(row.getCategorie());
                if (cat != null) {
                    if (isPourcentage) {
                        budgetService.definirBudgetPourcentage(cat, valeur);
                    } else {
                        budgetService.definirBudgetMontant(cat, valeur);
                    }
                }
            } else {
                if (isPourcentage) {
                    budgetService.modifierBudgetPersonnalisePourcentage(row.getCategorie(), valeur);
                } else {
                    budgetService.modifierBudgetPersonnaliseMontant(row.getCategorie(), valeur);
                }
            }
            afficherMessage("Budget '" + row.getCategorie() + "' mis à jour", false);
        } catch (NumberFormatException e) {
            afficherMessage("Valeur invalide. Utilisez un nombre (ex: 500) ou un pourcentage (ex: 15%)", true);
        } catch (Exception e) {
            log.error("Erreur lors de la modification du budget '{}'", row.getCategorie(), e);
            afficherMessage("Une erreur est survenue lors de la modification du budget", true);
        }
        chargerBudgets();
    }

    /**
     * Trouve une categorie obligatoire par son libelle.
     */
    private CategorieBudget findCategorie(String nom) {
        for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
            if (cat.getLibelle().equals(nom)) return cat;
        }
        return null;
    }

    /**
     * Charge les budgets dans le tableau et ajoute la ligne d'ajout en bas.
     */
    public void chargerBudgets() {
        budgetRows.clear();
        BigDecimal recette = budgetService.getRecette();

        Collection<Budget> budgets = budgetService.getTousBudgets();
        for (Budget budget : budgets) {
            BigDecimal alloue = budget.getMontantEffectif(recette);
            BigDecimal depense = budgetService.getTotalDepensesParBudget(budget);
            BigDecimal reste = alloue.subtract(depense);
            double alloueD = alloue.doubleValue();
            double depenseD = depense.doubleValue();
            double pct = alloueD > 0 ? (depenseD / alloueD) * 100 : 0;

            String budgetStr;
            if (!budget.estDefini()) {
                budgetStr = "Non défini";
            } else if (budget.estEnPourcentage()) {
                budgetStr = String.format("%.1f%% (%.2f €)", budget.getPourcentage(), alloue);
            } else {
                budgetStr = String.format("%.2f €", alloue);
            }

            budgetRows.add(new BudgetRow(
                    budget.getNom(),
                    budgetStr,
                    depenseD,
                    reste.doubleValue(),
                    String.format("%.0f%%", pct),
                    pct,
                    budget.isObligatoire()
            ));
        }

        // Ajouter la ligne d'ajout en derniere position
        budgetRows.add(BudgetRow.newRow());

        // Afficher le montant non alloue
        BigDecimal nonAlloue = budgetService.getMontantNonAlloue();
        BigDecimal pctNonAlloue = budgetService.getPourcentageNonAlloue();
        nonAlloueLabel.setText(String.format("%.2f € (%.1f%%)", nonAlloue, pctNonAlloue));
        nonAlloueLabel.setStyle(nonAlloue.compareTo(BigDecimal.ZERO) < 0
                ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
    }

    /**
     * Affiche un message.
     */
    private void afficherMessage(String message, boolean erreur) {
        messageLabel.setText(message);
        messageLabel.setStyle(erreur ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
    }

    /**
     * Classe interne pour les lignes du tableau.
     */
    public static class BudgetRow {
        private String categorie;
        private String budgetStr;
        private final Double depense;
        private final Double reste;
        private final String progressStr;
        private final double progressPct;
        private final boolean obligatoire;
        private final boolean newRow;
        private final SimpleBooleanProperty editingStarted = new SimpleBooleanProperty(false);

        public BudgetRow(String categorie, String budgetStr, Double depense, Double reste,
                         String progressStr, double progressPct, boolean obligatoire) {
            this.categorie = categorie;
            this.budgetStr = budgetStr;
            this.depense = depense;
            this.reste = reste;
            this.progressStr = progressStr;
            this.progressPct = progressPct;
            this.obligatoire = obligatoire;
            this.newRow = false;
        }

        private BudgetRow(boolean isNew) {
            this.categorie = NEW_ROW_PLACEHOLDER;
            this.budgetStr = "";
            this.depense = 0.0;
            this.reste = 0.0;
            this.progressStr = "";
            this.progressPct = 0;
            this.obligatoire = false;
            this.newRow = true;
        }

        public static BudgetRow newRow() {
            return new BudgetRow(true);
        }

        public String getCategorie() { return categorie; }
        public void setCategorie(String categorie) { this.categorie = categorie; }
        public String getBudgetStr() { return budgetStr; }
        public void setBudgetStr(String budgetStr) { this.budgetStr = budgetStr; }
        public Double getDepense() { return depense; }
        public Double getReste() { return reste; }
        public String getProgressStr() { return progressStr; }
        public double getProgressPct() { return progressPct; }
        public boolean isObligatoire() { return obligatoire; }
        public boolean isNewRow() { return newRow; }
        public SimpleBooleanProperty editingStartedProperty() { return editingStarted; }
        public boolean isEditingStarted() { return editingStarted.get(); }
        public void setEditingStarted(boolean val) { editingStarted.set(val); }
        public boolean isPartiallyFilled() {
            return newRow && !NEW_ROW_PLACEHOLDER.equals(categorie)
                    && categorie != null && !categorie.trim().isEmpty();
        }
    }
}
