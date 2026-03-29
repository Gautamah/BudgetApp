package com.budget.controller;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.budget.model.CategorieBudget;
import com.budget.model.Facture;
import com.budget.model.TypeFacture;
import com.budget.service.BudgetService;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import net.rgielen.fxweaver.core.FxmlView;

/**
 * Controleur pour la gestion des factures.
 * Ajout via la derniere ligne du tableau, suppression via la croix X par ligne.
 * Edition inline : double-cliquer sur Type, Fournisseur, Montant ou Date.
 */
@Component
@FxmlView("/fxml/facture.fxml")
public class FactureController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(FactureController.class);
    private static final String NEW_ROW_PLACEHOLDER = "\u2795 Nouvelle facture...";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private TableView<FactureRow> factureTable;

    @FXML
    private TableColumn<FactureRow, Void> actionColumn;

    @FXML
    private TableColumn<FactureRow, String> typeColumn;

    @FXML
    private TableColumn<FactureRow, String> fournisseurColumn;

    @FXML
    private TableColumn<FactureRow, String> montantColumn;

    @FXML
    private TableColumn<FactureRow, String> dateColumn;

    @FXML
    private TableColumn<FactureRow, String> categorieColumn;

    @FXML
    private ComboBox<CategorieBudget> filtreCategorie;

    @FXML
    private Label messageLabel;

    @FXML
    private Label totalLabel;

    private final BudgetService budgetService;
    private final ObservableList<FactureRow> factureRows = FXCollections.observableArrayList();
    private final List<TypeFacture> tousLesTypes = new ArrayList<>();
    private ObservableList<String> typeLabels;

    @Autowired
    public FactureController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialiser la liste des types
        for (CategorieBudget cat : CategorieBudget.getObligatoires()) {
            for (TypeFacture type : TypeFacture.parCategorie(cat)) {
                tousLesTypes.add(type);
            }
        }
        typeLabels = FXCollections.observableArrayList();
        for (TypeFacture type : tousLesTypes) {
            typeLabels.add(type.getLibelle());
        }

        configurerTable();
        configurerFiltre();
        chargerFactures();
    }

    // ==================== CONFIGURATION DU TABLEAU ====================

    private void configurerTable() {
        factureTable.setEditable(true);

        // Style de la ligne d'ajout (derniere ligne) en italique
        factureTable.setRowFactory(tv -> new TableRow<FactureRow>() {
            @Override
            protected void updateItem(FactureRow item, boolean empty) {
                super.updateItem(item, empty);
                if (item != null && item.isNewRow()) {
                    setStyle("-fx-font-style: italic;");
                } else {
                    setStyle("");
                }
            }
        });

        configurerActionColumn();
        configurerTypeColumn();
        configurerFournisseurColumn();
        configurerMontantColumn();
        configurerDateColumn();
        configurerCategorieColumn();

        factureTable.setItems(factureRows);
    }

    /**
     * Colonne X de suppression (toutes les lignes sauf la ligne d'ajout).
     */
    private void configurerActionColumn() {
        actionColumn.setCellFactory(col -> new TableCell<FactureRow, Void>() {
            private final Label deleteLabel = new Label("X");
            private ChangeListener<Boolean> editingListener;
            private FactureRow currentRow;
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
                        FactureRow row = getTableView().getItems().get(idx);
                        supprimerFacture(row);
                    }
                });
            }

            private void updateDisplay(FactureRow row) {
                if (row != null && (!row.isNewRow()
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
                    FactureRow row = (idx >= 0 && idx < getTableView().getItems().size())
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
    }

    /**
     * Colonne Type : ComboBox editable.
     */
    private void configurerTypeColumn() {
        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTypeLibelle()));
        typeColumn.setEditable(true);
        typeColumn.setCellFactory(ComboBoxTableCell.forTableColumn(typeLabels));
        typeColumn.setOnEditStart(event -> {
            FactureRow row = event.getRowValue();
            if (row != null && row.isNewRow()) {
                row.setEditingStarted(true);
            }
        });
        typeColumn.setOnEditCancel(event -> {
            FactureRow row = event.getRowValue();
            if (row != null && row.isNewRow()) {
                if (row.isPartiallyFilled()) {
                    Platform.runLater(() -> chargerFactures());
                } else {
                    row.setEditingStarted(false);
                }
            }
        });
        typeColumn.setOnEditCommit(event -> {
            FactureRow row = event.getRowValue();
            String newTypeLibelle = event.getNewValue();
            TypeFacture newType = findTypeByLibelle(newTypeLibelle);
            if (newType == null) {
                afficherMessage("Type invalide", true);
                chargerFactures();
                return;
            }

            if (row.isNewRow()) {
                row.setType(newType);
                factureTable.refresh();
                afficherMessage("Type défini : " + newType.getLibelle() + ". Saisissez le fournisseur.", false);
                Platform.runLater(() -> {
                    int idx = factureRows.indexOf(row);
                    if (idx >= 0) {
                        factureTable.getSelectionModel().select(idx);
                        factureTable.edit(idx, fournisseurColumn);
                    }
                });
            } else {
                try {
                    budgetService.modifierFacture(row.getId(), newType,
                            row.getFournisseur(), BigDecimal.valueOf(row.getMontant()), row.getDate());
                    afficherMessage("Type mis à jour → " + newType.getCategorie().getLibelle(), false);
                    chargerFactures();
                } catch (Exception e) {
                    log.error("Erreur lors de la modification du type de facture", e);
                    afficherMessage("Une erreur est survenue lors de la modification", true);
                    chargerFactures();
                }
            }
        });
    }

    /**
     * Colonne Fournisseur : TextField editable.
     */
    private void configurerFournisseurColumn() {
        fournisseurColumn.setCellValueFactory(cellData -> {
            FactureRow row = cellData.getValue();
            if (row.isNewRow() && !row.isFournisseurDefini()) return new SimpleStringProperty("");
            return new SimpleStringProperty(row.getFournisseur());
        });
        fournisseurColumn.setEditable(true);
        fournisseurColumn.setCellFactory(col ->
            new TextFieldTableCell<FactureRow, String>(new DefaultStringConverter()) {
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
                    if (trimmed.isEmpty()) {
                        if (getGraphic() instanceof TextField) {
                            showFieldError((TextField) getGraphic(), "Le fournisseur ne peut pas être vide");
                            afficherMessage("Le fournisseur ne peut pas être vide", true);
                        }
                        return; // Reste en édition
                    }
                    if (trimmed.length() > 100) {
                        if (getGraphic() instanceof TextField) {
                            showFieldError((TextField) getGraphic(), "Le fournisseur ne peut pas dépasser 100 caractères");
                            afficherMessage("Le fournisseur ne peut pas dépasser 100 caractères", true);
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
                        FactureRow row = getTableView().getItems().get(idx);
                        if (row != null && row.isNewRow() && row.isPartiallyFilled()) {
                            Platform.runLater(() -> chargerFactures());
                        }
                    }
                }

                @Override
                public void startEdit() {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FactureRow row = getTableView().getItems().get(idx);
                        if (row.isNewRow() && !row.isTypeDefini()) {
                            afficherMessage("Sélectionnez d'abord le type (double-cliquez sur Type)", true);
                            return;
                        }
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
                        FactureRow row = getTableView().getItems().get(getIndex());
                        if (row.isNewRow()) {
                            tf.setText("");
                            tf.setPromptText("Fournisseur");
                        }
                    }
                }
            }
        );
        fournisseurColumn.setOnEditCommit(event -> {
            FactureRow row = event.getRowValue();
            String newFournisseur = event.getNewValue().trim();
            if (newFournisseur.isEmpty()) return;

            if (row.isNewRow()) {
                row.setFournisseur(newFournisseur);
                factureTable.refresh();
                afficherMessage("Fournisseur défini. Saisissez le montant.", false);
                Platform.runLater(() -> {
                    int idx = factureRows.indexOf(row);
                    if (idx >= 0) {
                        factureTable.getSelectionModel().select(idx);
                        factureTable.edit(idx, montantColumn);
                    }
                });
            } else {
                try {
                    budgetService.modifierFacture(row.getId(), row.getType(),
                            newFournisseur, BigDecimal.valueOf(row.getMontant()), row.getDate());
                    afficherMessage("Fournisseur mis à jour", false);
                    chargerFactures();
                } catch (Exception e) {
                    log.error("Erreur lors de la modification du fournisseur", e);
                    afficherMessage("Une erreur est survenue lors de la modification", true);
                    chargerFactures();
                }
            }
        });
    }

    /**
     * Colonne Montant : TextField editable.
     */
    private void configurerMontantColumn() {
        montantColumn.setCellValueFactory(cellData -> {
            FactureRow row = cellData.getValue();
            if (row.isNewRow()) return new SimpleStringProperty("");
            return new SimpleStringProperty(String.format("%.2f €", row.getMontant()));
        });
        montantColumn.setEditable(true);
        montantColumn.setCellFactory(col ->
            new TextFieldTableCell<FactureRow, String>(new DefaultStringConverter()) {
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

                private String validateMontant(String value) {
                    if (value == null || value.trim().isEmpty()) return "Le montant ne peut pas être vide";
                    try {
                        BigDecimal val = new BigDecimal(
                                value.replace("€", "").replace(",", ".").trim());
                        if (val.compareTo(BigDecimal.ZERO) <= 0) return "Le montant doit être positif";
                        return null; // valide
                    } catch (NumberFormatException e) {
                        return "Montant invalide. Utilisez un nombre (ex: 150 ou 29.99)";
                    }
                }

                @Override
                public void commitEdit(String newValue) {
                    String erreur = validateMontant(newValue);
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
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FactureRow row = getTableView().getItems().get(idx);
                        if (row != null && row.isNewRow() && row.isPartiallyFilled()) {
                            Platform.runLater(() -> chargerFactures());
                        }
                    }
                }

                @Override
                public void startEdit() {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FactureRow row = getTableView().getItems().get(idx);
                        if (row.isNewRow() && (!row.isTypeDefini() || !row.isFournisseurDefini())) {
                            afficherMessage("Renseignez d'abord le type et le fournisseur", true);
                            return;
                        }
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
                        FactureRow row = getTableView().getItems().get(getIndex());
                        if (row.isNewRow()) {
                            tf.setText("");
                            tf.setPromptText("ex: 150.00");
                        }
                    }
                }
            }
        );
        montantColumn.setOnEditCommit(event -> {
            FactureRow row = event.getRowValue();
            try {
                BigDecimal newMontant = new BigDecimal(
                        event.getNewValue().replace("€", "").replace(",", ".").trim());
                if (newMontant.compareTo(BigDecimal.ZERO) <= 0) return;

                if (row.isNewRow()) {
                    creerFacture(row.getType(), row.getFournisseur(), newMontant);
                } else {
                    budgetService.modifierFacture(row.getId(), row.getType(),
                            row.getFournisseur(), newMontant, row.getDate());
                    afficherMessage("Montant mis à jour", false);
                    chargerFactures();
                }
            } catch (NumberFormatException e) {
                afficherMessage("Montant invalide", true);
            } catch (Exception e) {
                log.error("Erreur lors de la modification du montant", e);
                afficherMessage("Une erreur est survenue lors de la modification", true);
                chargerFactures();
            }
        });
    }

    /**
     * Colonne Date : DatePicker editable.
     */
    private void configurerDateColumn() {
        dateColumn.setCellValueFactory(cellData -> {
            FactureRow row = cellData.getValue();
            if (row.isNewRow()) return new SimpleStringProperty("");
            LocalDate date = row.getDate();
            return new SimpleStringProperty(date != null ? date.format(DATE_FORMAT) : "N/A");
        });
        dateColumn.setEditable(true);
        dateColumn.setCellFactory(col -> new DatePickerCell());
    }

    /**
     * Cellule personnalisée utilisant un DatePicker pour l'édition de date.
     */
    private class DatePickerCell extends TableCell<FactureRow, String> {
        private DatePicker datePicker;

        private DatePicker createDatePicker() {
            DatePicker dp = new DatePicker();
            dp.setConverter(new StringConverter<LocalDate>() {
                @Override
                public String toString(LocalDate date) {
                    return date != null ? date.format(DATE_FORMAT) : "";
                }

                @Override
                public LocalDate fromString(String string) {
                    try {
                        return (string != null && !string.isEmpty())
                                ? LocalDate.parse(string.trim(), DATE_FORMAT) : null;
                    } catch (Exception e) {
                        return null;
                    }
                }
            });
            dp.setEditable(true);
            dp.setMaxWidth(Double.MAX_VALUE);
            dp.setPrefWidth(160);

            dp.setOnAction(e -> {
                if (dp.getValue() != null) {
                    commitEdit(dp.getValue().format(DATE_FORMAT));
                }
            });

            dp.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused && isEditing()) {
                    if (dp.getValue() != null) {
                        commitEdit(dp.getValue().format(DATE_FORMAT));
                    } else {
                        cancelEdit();
                    }
                }
            });

            return dp;
        }

        @Override
        public void startEdit() {
            int idx = getIndex();
            if (idx >= 0 && idx < getTableView().getItems().size()) {
                FactureRow row = getTableView().getItems().get(idx);
                if (row.isNewRow()) return;
            }
            super.startEdit();
            if (datePicker == null) {
                datePicker = createDatePicker();
            }
            try {
                datePicker.setValue(LocalDate.parse(getItem(), DATE_FORMAT));
            } catch (Exception e) {
                datePicker.setValue(LocalDate.now());
            }
            setText(null);
            setGraphic(datePicker);
            Platform.runLater(() -> {
                datePicker.requestFocus();
                datePicker.show();
            });
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        public void commitEdit(String newValue) {
            super.commitEdit(newValue);
            FactureRow row = getTableView().getItems().get(getIndex());
            if (row.isNewRow()) return;
            try {
                LocalDate newDate = LocalDate.parse(newValue.trim(), DATE_FORMAT);
                budgetService.modifierFacture(row.getId(), row.getType(),
                        row.getFournisseur(), BigDecimal.valueOf(row.getMontant()), newDate);
                afficherMessage("Date mise à jour", false);
                chargerFactures();
            } catch (DateTimeParseException e) {
                afficherMessage("Format de date invalide (jj/mm/aaaa)", true);
                chargerFactures();
            } catch (Exception e) {
                log.error("Erreur lors de la modification de la date", e);
                afficherMessage("Une erreur est survenue lors de la modification", true);
                chargerFactures();
            }
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                setText(null);
                setGraphic(datePicker);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }

    /**
     * Colonne Categorie : non editable, derivee du type.
     */
    private void configurerCategorieColumn() {
        categorieColumn.setCellValueFactory(cellData -> {
            FactureRow row = cellData.getValue();
            if (row.isNewRow() && !row.isTypeDefini()) return new SimpleStringProperty("");
            return new SimpleStringProperty(row.getCategorieLibelle());
        });
        categorieColumn.setEditable(false);
    }

    // ==================== FILTRE ====================

    private void configurerFiltre() {
        ObservableList<CategorieBudget> categories = FXCollections.observableArrayList();
        categories.add(null); // Option "Toutes"
        categories.addAll(CategorieBudget.getObligatoires());
        filtreCategorie.setItems(categories);
        filtreCategorie.setCellFactory(lv -> new ListCell<CategorieBudget>() {
            @Override
            protected void updateItem(CategorieBudget item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "Toutes les catégories" : item.getLibelle()));
            }
        });
        filtreCategorie.setButtonCell(new ListCell<CategorieBudget>() {
            @Override
            protected void updateItem(CategorieBudget item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item == null ? "Toutes les catégories" : item.getLibelle()));
            }
        });
        filtreCategorie.setOnAction(e -> filtrerFactures());
    }

    // ==================== ACTIONS ====================

    /**
     * Cree une facture depuis la ligne d'ajout du tableau.
     */
    private void creerFacture(TypeFacture type, String fournisseur, BigDecimal montant) {
        try {
            budgetService.ajouterFacture(type, fournisseur, montant, LocalDate.now());
            afficherMessage("Facture ajoutée : " + type.getLibelle() + " - " + fournisseur
                    + " (" + String.format("%.2f €", montant) + ")", false);
        } catch (Exception e) {
            log.error("Erreur lors de la création de la facture", e);
            afficherMessage("Une erreur est survenue lors de la création de la facture", true);
        }
        chargerFactures();
    }

    /**
     * Supprime une facture via le bouton X de la ligne.
     */
    private void supprimerFacture(FactureRow row) {
        if (row.isNewRow()) {
            chargerFactures();
            afficherMessage("Ajout annulé", false);
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Suppression de facture");
        confirmation.setContentText("Souhaitez-vous vraiment supprimer cette facture ?");

        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (budgetService.supprimerFacture(row.getId())) {
                afficherMessage("Facture supprimée", false);
                chargerFactures();
            } else {
                afficherMessage("Erreur lors de la suppression", true);
            }
        }
    }

    // ==================== CHARGEMENT DES DONNEES ====================

    private void chargerFactures() {
        factureRows.clear();
        for (Facture f : budgetService.getFactures()) {
            factureRows.add(new FactureRow(f));
        }
        factureRows.add(FactureRow.newRow());
        actualiserTotal();
    }

    @FXML
    private void filtrerFactures() {
        CategorieBudget categorie = filtreCategorie.getValue();
        factureRows.clear();
        List<Facture> liste = (categorie == null)
                ? budgetService.getFactures()
                : budgetService.getFacturesParCategorie(categorie);
        for (Facture f : liste) {
            factureRows.add(new FactureRow(f));
        }
        factureRows.add(FactureRow.newRow());
        actualiserTotal();
    }

    private void actualiserTotal() {
        double total = factureRows.stream()
                .filter(r -> !r.isNewRow())
                .mapToDouble(FactureRow::getMontant)
                .sum();
        long count = factureRows.stream().filter(r -> !r.isNewRow()).count();
        totalLabel.setText(String.format("Total: %.2f € (%d facture(s))", total, count));
    }

    // ==================== UTILITAIRES ====================

    private TypeFacture findTypeByLibelle(String libelle) {
        for (TypeFacture type : tousLesTypes) {
            if (type.getLibelle().equals(libelle)) {
                return type;
            }
        }
        return null;
    }

    private void afficherMessage(String message, boolean erreur) {
        messageLabel.setText(message);
        messageLabel.setStyle(erreur ? "-fx-text-fill: #F44336;" : "-fx-text-fill: #4CAF50;");
    }

    // ==================== CLASSE INTERNE ====================

    /**
     * Ligne du tableau : encapsule une facture existante ou une ligne d'ajout.
     */
    public static class FactureRow {
        private String id;
        private TypeFacture type;
        private String typeLibelle;
        private String fournisseur;
        private double montant;
        private LocalDate date;
        private String categorieLibelle;
        private final boolean newRow;
        private final SimpleBooleanProperty editingStarted = new SimpleBooleanProperty(false);

        /** Constructeur pour une facture existante. */
        public FactureRow(Facture facture) {
            this.id = facture.getId();
            this.type = facture.getType();
            this.typeLibelle = facture.getType().getLibelle();
            this.fournisseur = facture.getFournisseur();
            this.montant = facture.getMontant().doubleValue();
            this.date = facture.getDate();
            this.categorieLibelle = facture.getCategorie().getLibelle();
            this.newRow = false;
        }

        /** Constructeur prive pour la ligne d'ajout. */
        private FactureRow(boolean isNew) {
            this.id = null;
            this.type = null;
            this.typeLibelle = NEW_ROW_PLACEHOLDER;
            this.fournisseur = "";
            this.montant = 0;
            this.date = LocalDate.now();
            this.categorieLibelle = "";
            this.newRow = true;
        }

        public static FactureRow newRow() {
            return new FactureRow(true);
        }

        public String getId() { return id; }
        public TypeFacture getType() { return type; }
        public void setType(TypeFacture type) {
            this.type = type;
            this.typeLibelle = type.getLibelle();
            this.categorieLibelle = type.getCategorie().getLibelle();
        }
        public String getTypeLibelle() { return typeLibelle; }
        public String getFournisseur() { return fournisseur; }
        public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }
        public double getMontant() { return montant; }
        public void setMontant(double montant) { this.montant = montant; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public String getCategorieLibelle() { return categorieLibelle; }
        public boolean isNewRow() { return newRow; }
        public SimpleBooleanProperty editingStartedProperty() { return editingStarted; }
        public boolean isEditingStarted() { return editingStarted.get(); }
        public void setEditingStarted(boolean val) { editingStarted.set(val); }
        public boolean isTypeDefini() { return type != null; }
        public boolean isFournisseurDefini() { return fournisseur != null && !fournisseur.isEmpty(); }
        public boolean isPartiallyFilled() {
            return newRow && (isTypeDefini() || isFournisseurDefini());
        }
    }
}
