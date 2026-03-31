package com.budget.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.budget.service.BudgetService;
import com.budget.service.MoisBudgetService;
import com.budget.service.UpdateService;
import com.budget.service.UpdateService.UpdateInfo;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;

@Component
@FxmlView("/fxml/main.fxml")
public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    @FXML private TabPane tabPane;
    @FXML private Label moisLabel;
    @FXML private Label recetteLabel;
    @FXML private Label depenseLabel;
    @FXML private Label resteLabel;
    @FXML private HBox updateBanner;
    @FXML private Label updateLabel;

    private final FxWeaver fxWeaver;
    private final BudgetService budgetService;
    private final MoisBudgetService moisBudgetService;
    private final BudgetController budgetController;
    private final TableauDeBordController tableauDeBordController;
    private final UpdateService updateService;

    private UpdateInfo latestUpdate;

    @Autowired
    public MainController(FxWeaver fxWeaver, BudgetService budgetService,
                          MoisBudgetService moisBudgetService, BudgetController budgetController,
                          TableauDeBordController tableauDeBordController, UpdateService updateService) {
        this.fxWeaver = fxWeaver;
        this.budgetService = budgetService;
        this.moisBudgetService = moisBudgetService;
        this.budgetController = budgetController;
        this.tableauDeBordController = tableauDeBordController;
        this.updateService = updateService;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        chargerOnglets();
        actualiserEnTete();
        verifierMiseAJour();
    }

    // ==================== ONGLETS ====================

    private void chargerOnglets() {
        Parent dashboardView = fxWeaver.loadView(TableauDeBordController.class);
        Tab dashboardTab = new Tab("Tableau de bord", dashboardView);
        dashboardTab.setClosable(false);

        Parent budgetView = fxWeaver.loadView(BudgetController.class);
        Tab budgetTab = new Tab("Budgets", budgetView);
        budgetTab.setClosable(false);

        Parent factureView = fxWeaver.loadView(FactureController.class);
        Tab factureTab = new Tab("Factures", factureView);
        factureTab.setClosable(false);

        tabPane.getTabs().addAll(dashboardTab, budgetTab, factureTab);

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            actualiserEnTete();
            if (newTab == dashboardTab) {
                tableauDeBordController.actualiserDonnees();
            } else if (newTab == budgetTab) {
                budgetController.chargerBudgets();
            }
        });
    }

    // ==================== NAVIGATION MENSUELLE ====================

    @FXML
    private void moisPrecedent() {
        YearMonth actuel = moisBudgetService.getMoisActif();
        moisBudgetService.setMoisActif(actuel.minusMonths(1));
        rafraichirTout();
    }

    @FXML
    private void moisSuivant() {
        YearMonth actuel = moisBudgetService.getMoisActif();
        moisBudgetService.setMoisActif(actuel.plusMonths(1));
        rafraichirTout();
    }

    @FXML
    private void ouvrirSelecteurMois() {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        ObservableList<YearMonth> moisDisponibles = FXCollections.observableArrayList();
        YearMonth now = YearMonth.now();
        for (int i = -12; i <= 3; i++) {
            moisDisponibles.add(now.plusMonths(i));
        }

        ListView<YearMonth> listView = new ListView<>(moisDisponibles);
        listView.setPrefHeight(250);
        listView.setPrefWidth(200);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(YearMonth item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(capitaliser(item.format(MONTH_FMT)));
                    if (item.equals(moisBudgetService.getMoisActif())) {
                        setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                moisBudgetService.setMoisActif(newVal);
                popup.hide();
                rafraichirTout();
            }
        });

        VBox container = new VBox(listView);
        container.setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2); "
                + "-fx-background-radius: 4;");
        container.setPadding(new Insets(4));
        popup.getContent().add(container);

        Bounds bounds = moisLabel.localToScreen(moisLabel.getBoundsInLocal());
        popup.show(moisLabel.getScene().getWindow(),
                bounds.getMinX() - 60,
                bounds.getMaxY() + 5);
    }

    // ==================== RAFRAICHISSEMENT ====================

    private void rafraichirTout() {
        actualiserEnTete();
        tableauDeBordController.actualiserDonnees();
        budgetController.chargerBudgets();
    }

    public void actualiserEnTete() {
        String mois = moisBudgetService.getMoisCourant().getMois().format(MONTH_FMT);
        moisLabel.setText(capitaliser(mois));

        BigDecimal recette = budgetService.getRecette();
        recetteLabel.setText(String.format("%.2f €", recette));

        BigDecimal depenses = budgetService.getTotalDepenses();
        depenseLabel.setText(String.format("%.2f €", depenses));

        BigDecimal reste = budgetService.getResteGlobal();
        resteLabel.setText(String.format("%.2f €", reste));

        if (reste.compareTo(BigDecimal.ZERO) < 0) {
            resteLabel.setStyle("-fx-text-fill: #F44336;");
        } else if (reste.compareTo(recette.multiply(BigDecimal.valueOf(0.1))) < 0) {
            resteLabel.setStyle("-fx-text-fill: #FF9800;");
        } else {
            resteLabel.setStyle("-fx-text-fill: #4CAF50;");
        }
    }

    // ==================== MISE A JOUR ====================

    private void verifierMiseAJour() {
        updateService.checkForUpdate().thenAccept(info -> {
            if (info != null) {
                Platform.runLater(() -> afficherBandeauMaj(info));
            }
        });
    }

    private void afficherBandeauMaj(UpdateInfo info) {
        this.latestUpdate = info;
        updateLabel.setText("Version " + info.version() + " disponible");
        updateBanner.setVisible(true);
        updateBanner.setManaged(true);
    }

    @FXML
    private void ouvrirDialogMiseAJour() {
        if (latestUpdate == null) return;

        String notes = (latestUpdate.releaseNotes() == null || latestUpdate.releaseNotes().isBlank())
                ? "Une nouvelle version est disponible."
                : latestUpdate.releaseNotes();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Mise a jour");
        alert.setHeaderText("Version " + latestUpdate.version() + " disponible");
        alert.setContentText(notes + "\n\nVoulez-vous telecharger et installer cette mise a jour ?");

        ButtonType downloadBtn = new ButtonType("Telecharger", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn = new ButtonType("Plus tard", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(downloadBtn, laterBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == downloadBtn) {
            lancerTelechargement();
        }
    }

    private void lancerTelechargement() {
        // On extrait le nom du fichier directement depuis l'URL de telechargement.
        // Ex: "https://.../Gestionnaire.de.Budget-1.0.0.msi" -> "Gestionnaire.de.Budget-1.0.0.msi"
        // Comme ca, peu importe le format du nom ou la version, ca s'adapte automatiquement.
        String url = latestUpdate.downloadUrl();
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            fileName = "BudgetApp-" + latestUpdate.version() + ".msi";
        }
        Path downloadPath = Paths.get(System.getProperty("user.home"), "Downloads", fileName);

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateService.downloadUpdate(latestUpdate.downloadUrl(), downloadPath,
                        progress -> updateProgress(progress, 1.0));
                return null;
            }
        };

        downloadTask.progressProperty().addListener((obs, oldVal, newVal) -> {
            double pct = newVal.doubleValue() * 100;
            if (pct > 0) {
                updateLabel.setText(String.format("Telechargement : %.0f%%", pct));
            }
        });

        downloadTask.setOnSucceeded(e -> {
            updateLabel.setText("Telechargement termine !");
            try {
                updateService.launchInstaller(downloadPath);
                Platform.exit();
            } catch (IOException ex) {
                log.error("Erreur au lancement de l'installateur", ex);
                updateLabel.setText("Telechargement termine. Lancez " + fileName + " manuellement.");
            }
        });

        downloadTask.setOnFailed(e -> {
            Throwable ex = downloadTask.getException();
            log.error("Erreur lors du telechargement", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : "Erreur inconnue";
            updateLabel.setText("Erreur : " + msg);
        });

        updateLabel.setText("Telechargement en cours...");
        new Thread(downloadTask, "update-download").start();
    }

    @FXML
    private void fermerBandeauMaj() {
        updateBanner.setVisible(false);
        updateBanner.setManaged(false);
    }

    // ==================== UTILITAIRES ====================

    private static String capitaliser(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
