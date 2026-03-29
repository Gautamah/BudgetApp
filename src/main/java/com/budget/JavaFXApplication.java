package com.budget;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.rgielen.fxweaver.core.FxWeaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.budget.controller.MainController;

import java.net.URL;

/**
 * Point d'entree JavaFX de l'application.
 * Integre JavaFX avec Spring Boot via FxWeaver.
 */
public class JavaFXApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(JavaFXApplication.class);

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() throws Exception {
        // Initialiser le contexte Spring Boot
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.springContext = new SpringApplicationBuilder()
                .sources(BudgetApplication.class)
                .run(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Utiliser FxWeaver pour charger la vue principale
        FxWeaver fxWeaver = springContext.getBean(FxWeaver.class);
        Parent root = fxWeaver.loadView(MainController.class);

        // Creer la scene avec les styles CSS
        Scene scene = new Scene(root, 1200, 800);

        // Charger le CSS avec verification de l'existence du fichier
        URL cssUrl = getClass().getResource("/css/main.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            log.warn("Fichier CSS /css/main.css introuvable, l'application démarrera sans styles personnalisés");
        }

        // Configurer et afficher la fenetre principale
        primaryStage.setTitle("Gestionnaire de Budget");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Fermer proprement le contexte Spring
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}
