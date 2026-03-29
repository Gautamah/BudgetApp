package com.budget;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree de l'application Spring Boot Budget.
 * Lance l'interface graphique JavaFX.
 */
@SpringBootApplication
public class BudgetApplication {

    public static void main(String[] args) {
        // Lancer JavaFX qui initialisera Spring Boot
        Application.launch(JavaFXApplication.class, args);
    }
}







