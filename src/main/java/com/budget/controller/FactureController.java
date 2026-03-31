package com.budget.controller;

import java.io.File;
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
import com.budget.service.DocumentService;
import com.budget.service.FactureAnalyzer;
import com.budget.service.FactureAnalyzer.FactureAnalyzeResult;
import com.budget.service.FactureExtractionService;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
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
    private TableColumn<FactureRow, Void> documentColumn;

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

    @FXML
    private Button btnImporter;

    private final BudgetService budgetService;
    private final FactureExtractionService extractionService;
    private final FactureAnalyzer factureAnalyzer;
    private final DocumentService documentService;
    private final ObservableList<FactureRow> factureRows = FXCollections.observableArrayList();
    private final List<TypeFacture> tousLesTypes = new ArrayList<>();
    private ObservableList<String> typeLabels;

    @Autowired
    public FactureController(BudgetService budgetService,
                             FactureExtractionService extractionService,
                             FactureAnalyzer factureAnalyzer,
                             DocumentService documentService) {
        this.budgetService = budgetService;
        this.extractionService = extractionService;
        this.factureAnalyzer = factureAnalyzer;
        this.documentService = documentService;
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
        configurerDocumentColumn();
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
     * Colonne Document : affiche une icone si un document est attache a la facture.
     * Au clic : menu contextuel avec Voir / Enregistrer sous / Imprimer.
     * Si aucun document, affiche un "+" pour en attacher un.
     */
    private void configurerDocumentColumn() {
        documentColumn.setCellFactory(col -> new TableCell<FactureRow, Void>() {
            private final Label docLabel = new Label("📎");
            private final Label attachLabel = new Label("+");
            {
                // Style de l'icone "trombone" (document present)
                docLabel.setStyle("-fx-font-size: 14px; -fx-cursor: hand;");
                docLabel.setTooltip(new Tooltip("Document attaché — cliquer pour les options"));
                docLabel.setOnMouseClicked(event -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FactureRow row = getTableView().getItems().get(idx);
                        afficherMenuDocument(row, docLabel);
                    }
                });

                // Style de l'icone "+" (pas de document, permet d'en attacher un)
                attachLabel.setStyle(
                    "-fx-font-size: 14px; -fx-cursor: hand; -fx-text-fill: #9E9E9E;");
                attachLabel.setTooltip(new Tooltip("Attacher un document"));
                attachLabel.setOnMouseClicked(event -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < getTableView().getItems().size()) {
                        FactureRow row = getTableView().getItems().get(idx);
                        attacherDocument(row);
                    }
                });
                attachLabel.setOnMouseEntered(e -> attachLabel.setStyle(
                    "-fx-font-size: 14px; -fx-cursor: hand; -fx-text-fill: #2196F3;"));
                attachLabel.setOnMouseExited(e -> attachLabel.setStyle(
                    "-fx-font-size: 14px; -fx-cursor: hand; -fx-text-fill: #9E9E9E;"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                int idx = getIndex();
                if (idx >= 0 && idx < getTableView().getItems().size()) {
                    FactureRow row = getTableView().getItems().get(idx);
                    if (row.isNewRow()) {
                        setGraphic(null);
                    } else if (row.hasDocument()) {
                        setGraphic(docLabel);
                    } else {
                        setGraphic(attachLabel);
                    }
                    setAlignment(Pos.CENTER);
                } else {
                    setGraphic(null);
                }
            }
        });
        documentColumn.setEditable(false);
        documentColumn.setSortable(false);
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
     * Si un document est attache, il est egalement supprime du disque.
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
            // Supprimer le document associe du disque si present
            if (row.hasDocument()) {
                documentService.supprimerDocument(row.getDocumentPath());
            }
            if (budgetService.supprimerFacture(row.getId())) {
                afficherMessage("Facture supprimée", false);
                chargerFactures();
            } else {
                afficherMessage("Erreur lors de la suppression", true);
            }
        }
    }

    // ==================== IMPORT DE FACTURE ====================

    /**
     * Methode appelee quand l'utilisateur clique sur le bouton "Importer (PDF/Image)".
     *
     * Deroulement :
     * 1. Ouvre un FileChooser pour selectionner un fichier
     * 2. Lance l'extraction de texte dans un thread separe (pour ne pas bloquer l'interface)
     * 3. Analyse le texte extrait
     * 4. Affiche un dialog de previsualisation editable
     */
    @FXML
    private void importerFacture() {
        // Etape 1 : ouvrir le selecteur de fichier
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer une facture (PDF ou Image)");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Tous les formats supportés", "*.pdf", "*.jpg", "*.jpeg", "*.png"),
            new FileChooser.ExtensionFilter("PDF", "*.pdf"),
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png")
        );

        File fichier = fileChooser.showOpenDialog(factureTable.getScene().getWindow());
        if (fichier == null) return;

        // Etape 2 : afficher un indicateur de chargement
        afficherMessage("⏳ Analyse en cours...", false);
        btnImporter.setDisable(true);

        // On utilise un Task JavaFX pour faire le travail lourd (extraction + OCR)
        // dans un thread separe. Sinon l'interface serait "gelee" pendant l'OCR.
        Task<FactureAnalyzeResult> task = new Task<>() {
            private String texteExtrait;

            @Override
            protected FactureAnalyzeResult call() {
                texteExtrait = extractionService.extraireTexte(fichier);
                return factureAnalyzer.analyser(texteExtrait);
            }

            @Override
            protected void succeeded() {
                btnImporter.setDisable(false);
                FactureAnalyzeResult result = getValue();
                afficherMessage("Analyse terminée", false);
                afficherDialogPrevisualisation(result, texteExtrait, fichier);
            }

            @Override
            protected void failed() {
                btnImporter.setDisable(false);
                afficherMessage("Erreur lors de l'analyse du document", true);
                log.error("Erreur import facture", getException());
            }
        };

        new Thread(task).start();
    }

    /**
     * Affiche un dialog de previsualisation qui montre les informations extraites
     * et permet a l'utilisateur de les corriger avant de confirmer.
     */
    private void afficherDialogPrevisualisation(FactureAnalyzeResult result,
                                                 String texteExtrait,
                                                 File fichierOriginal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Import de facture — Prévisualisation");
        dialog.setHeaderText("Vérifiez et corrigez les informations extraites");
        dialog.setResizable(true);

        // Boutons du dialog
        ButtonType confirmerType = new ButtonType("Confirmer", ButtonBar.ButtonData.OK_DONE);
        ButtonType annulerType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmerType, annulerType);

        // Construction du formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        // Champ Type (ComboBox)
        Label lblType = new Label("Type :");
        ComboBox<String> comboType = new ComboBox<>(typeLabels);
        comboType.setPrefWidth(250);
        if (result.getType() != null) {
            comboType.setValue(result.getType().getLibelle());
        }

        // Champ Fournisseur (TextField)
        Label lblFournisseur = new Label("Fournisseur :");
        TextField txtFournisseur = new TextField();
        txtFournisseur.setPrefWidth(250);
        if (result.getFournisseur() != null) {
            txtFournisseur.setText(result.getFournisseur());
        }
        txtFournisseur.setPromptText("Nom du fournisseur");

        // Champ Montant (TextField)
        Label lblMontant = new Label("Montant (€) :");
        TextField txtMontant = new TextField();
        txtMontant.setPrefWidth(250);
        if (result.getMontant() != null) {
            txtMontant.setText(result.getMontant().toPlainString());
        }
        txtMontant.setPromptText("ex: 125.50");

        // Champ Date (DatePicker)
        Label lblDate = new Label("Date :");
        DatePicker datePicker = new DatePicker();
        datePicker.setPrefWidth(250);
        datePicker.setValue(result.getDate() != null ? result.getDate() : LocalDate.now());

        // Zone de texte brut (lecture seule, pour reference)
        Label lblTexte = new Label("Texte extrait (référence) :");
        TextArea txtTexte = new TextArea(texteExtrait != null ? texteExtrait : "(aucun texte extrait)");
        txtTexte.setEditable(false);
        txtTexte.setPrefRowCount(8);
        txtTexte.setWrapText(true);
        txtTexte.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        // Avertissement doublon
        Label lblDoublon = new Label();
        lblDoublon.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold; -fx-padding: 5 0;");
        lblDoublon.setWrapText(true);
        verifierDoublon(result, lblDoublon);

        // Placement dans la grille
        grid.add(lblType, 0, 0);
        grid.add(comboType, 1, 0);
        grid.add(lblFournisseur, 0, 1);
        grid.add(txtFournisseur, 1, 1);
        grid.add(lblMontant, 0, 2);
        grid.add(txtMontant, 1, 2);
        grid.add(lblDate, 0, 3);
        grid.add(datePicker, 1, 3);
        grid.add(lblDoublon, 0, 4, 2, 1);
        grid.add(lblTexte, 0, 5, 2, 1);
        grid.add(txtTexte, 0, 6, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(550);

        // Desactiver le bouton Confirmer tant que les champs obligatoires sont vides
        Button btnConfirmer = (Button) dialog.getDialogPane().lookupButton(confirmerType);
        Runnable updateBtnState = () -> {
            boolean typeOk = comboType.getValue() != null && !comboType.getValue().isEmpty();
            boolean fournisseurOk = !txtFournisseur.getText().trim().isEmpty();
            boolean montantOk = estMontantValide(txtMontant.getText());
            btnConfirmer.setDisable(!(typeOk && fournisseurOk && montantOk));
        };
        comboType.valueProperty().addListener((o, a, b) -> updateBtnState.run());
        txtFournisseur.textProperty().addListener((o, a, b) -> updateBtnState.run());
        txtMontant.textProperty().addListener((o, a, b) -> updateBtnState.run());
        updateBtnState.run();

        // Afficher le dialog et traiter la reponse
        dialog.showAndWait().ifPresent(response -> {
            if (response == confirmerType) {
                confirmerImport(comboType, txtFournisseur, txtMontant, datePicker,
                        result, fichierOriginal);
            }
        });
    }

    /**
     * Verifie si une facture similaire existe deja et affiche un avertissement.
     */
    private void verifierDoublon(FactureAnalyzeResult result, Label lblDoublon) {
        if (result.getFournisseur() != null && result.getMontant() != null && result.getDate() != null) {
            if (factureAnalyzer.existeDoublon(result.getFournisseur(), result.getMontant(), result.getDate())) {
                lblDoublon.setText("⚠ Une facture similaire existe déjà pour ce mois "
                    + "(même fournisseur, montant et date)");
            }
        }
    }

    /**
     * Valide et cree la facture apres confirmation dans le dialog.
     */
    private void confirmerImport(ComboBox<String> comboType, TextField txtFournisseur,
                                  TextField txtMontant, DatePicker datePicker,
                                  FactureAnalyzeResult resultOriginal, File fichierOriginal) {
        try {
            TypeFacture type = findTypeByLibelle(comboType.getValue());
            String fournisseur = txtFournisseur.getText().trim();
            BigDecimal montant = new BigDecimal(
                    txtMontant.getText().replace(",", ".").replace("€", "").trim());
            LocalDate date = datePicker.getValue();

            if (type == null || fournisseur.isEmpty() || montant.compareTo(BigDecimal.ZERO) <= 0) {
                afficherMessage("Veuillez remplir tous les champs correctement", true);
                return;
            }

            // 1. Creer la facture en base
            Facture facture = budgetService.ajouterFacture(type, fournisseur, montant, date);

            // 2. Stocker le document et associer le chemin a la facture
            String docPath = documentService.stockerDocument(
                    fichierOriginal, facture.getId(), fournisseur, date);
            if (docPath != null) {
                budgetService.mettreAJourDocumentPath(facture.getId(), docPath);
            }

            // 3. Apprentissage : si le fournisseur/type ne vient pas du dictionnaire,
            //    ou si l'utilisateur a corrige les valeurs, on enregistre l'association.
            boolean fournisseurModifie = resultOriginal.getFournisseur() == null
                    || !fournisseur.equals(resultOriginal.getFournisseur());
            boolean typeModifie = resultOriginal.getType() == null
                    || type != resultOriginal.getType();

            if (fournisseurModifie || typeModifie || !resultOriginal.isDepuisDictionnaire()) {
                String motCle = fournisseur.toLowerCase().trim();
                factureAnalyzer.apprendreFournisseur(motCle, fournisseur, type);
            }

            // 4. Rafraichir l'affichage
            chargerFactures();
            afficherMessage("Facture importée : " + type.getLibelle() + " - " + fournisseur
                    + " (" + String.format("%.2f €", montant) + ")", false);

        } catch (NumberFormatException e) {
            afficherMessage("Montant invalide", true);
        } catch (Exception e) {
            log.error("Erreur lors de l'import de la facture", e);
            afficherMessage("Erreur lors de l'import : " + e.getMessage(), true);
        }
    }

    private boolean estMontantValide(String texte) {
        if (texte == null || texte.trim().isEmpty()) return false;
        try {
            BigDecimal val = new BigDecimal(texte.replace(",", ".").replace("€", "").trim());
            return val.compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== GESTION DES DOCUMENTS ====================

    /**
     * Affiche un menu contextuel avec les options pour un document attache.
     */
    private void afficherMenuDocument(FactureRow row, Label anchor) {
        ContextMenu menu = new ContextMenu();

        MenuItem voir = new MenuItem("📄 Voir le document");
        voir.setOnAction(e -> documentService.ouvrirDocument(row.getDocumentPath()));

        MenuItem enregistrer = new MenuItem("💾 Enregistrer sous...");
        enregistrer.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le document");
            fc.setInitialFileName(new File(row.getDocumentPath()).getName());
            File dest = fc.showSaveDialog(factureTable.getScene().getWindow());
            if (dest != null) {
                documentService.copierDocument(row.getDocumentPath(), dest);
                afficherMessage("Document enregistré", false);
            }
        });

        MenuItem imprimer = new MenuItem("🖨 Imprimer");
        imprimer.setOnAction(e -> documentService.imprimerDocument(row.getDocumentPath()));

        menu.getItems().addAll(voir, enregistrer, imprimer);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * Permet d'attacher un document a une facture existante saisie manuellement.
     */
    private void attacherDocument(FactureRow row) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Attacher un document à cette facture");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.jpg", "*.jpeg", "*.png"));

        File fichier = fc.showOpenDialog(factureTable.getScene().getWindow());
        if (fichier == null) return;

        String docPath = documentService.stockerDocument(
                fichier, row.getId(), row.getFournisseur(), row.getDate());
        if (docPath != null) {
            budgetService.mettreAJourDocumentPath(row.getId(), docPath);
            chargerFactures();
            afficherMessage("Document attaché à la facture", false);
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
        private String documentPath;
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
            this.documentPath = facture.getDocumentPath();
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
            this.documentPath = null;
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
        public String getDocumentPath() { return documentPath; }
        public boolean hasDocument() { return documentPath != null && !documentPath.isEmpty(); }
        public boolean isTypeDefini() { return type != null; }
        public boolean isFournisseurDefini() { return fournisseur != null && !fournisseur.isEmpty(); }
        public boolean isPartiallyFilled() {
            return newRow && (isTypeDefini() || isFournisseurDefini());
        }
    }
}
