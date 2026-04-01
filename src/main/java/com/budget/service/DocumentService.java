package com.budget.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Service de gestion des documents (fichiers PDF/images) associes aux factures.
 *
 * Les fichiers importes sont copies dans un dossier de stockage permanent
 * sur le disque de l'utilisateur : {user.home}/.budget-app/documents/
 *
 * Structure du dossier :
 *   documents/
 *     2026/
 *       03/
 *         12_Proximus_2026-03-15.pdf
 *         13_EDF_2026-03-20.jpg
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // Dossier racine de stockage des documents
    private Path dossierRacine;

    /**
     * Au demarrage, on cree le dossier de stockage s'il n'existe pas.
     */
    @PostConstruct
    public void initialiser() {
        // On utilise le dossier personnel de l'utilisateur (ex: C:\Users\falch)
        // et on cree un sous-dossier .budget-app/documents/
        dossierRacine = Path.of(System.getProperty("user.home"), ".budget-app", "documents");
        try {
            Files.createDirectories(dossierRacine);
            log.info("Dossier de stockage des documents : {}", dossierRacine);
        } catch (IOException e) {
            log.error("Impossible de creer le dossier de stockage", e);
        }
    }

    /**
     * Stocke un document (le copie dans le dossier de stockage).
     *
     * @param fichierSource le fichier original (celui que l'utilisateur a selectionne)
     * @param factureId     l'ID de la facture en base
     * @param fournisseur   le nom du fournisseur (pour nommer le fichier)
     * @param dateFacture   la date de la facture (pour organiser en sous-dossiers)
     * @return le chemin relatif du fichier stocke (ex: "2026/03/12_Proximus_2026-03-15.pdf")
     */
    public String stockerDocument(File fichierSource, String factureId, String fournisseur, LocalDate dateFacture) {
        try {
            // On organise les fichiers par annee/mois
            String annee = String.valueOf(dateFacture.getYear());
            String mois = String.format("%02d", dateFacture.getMonthValue());

            Path sousDossier = dossierRacine.resolve(annee).resolve(mois);
            Files.createDirectories(sousDossier);

            // On construit un nom de fichier propre :
            // {id}_{fournisseur}_{date}.{extension}
            String extension = getExtension(fichierSource.getName());
            String nomNettoye = fournisseur.replaceAll("[^a-zA-Z0-9àâäéèêëïîôùûüç_-]", "_");
            String nomFichier = factureId + "_" + nomNettoye + "_" + dateFacture + "." + extension;

            Path destination = sousDossier.resolve(nomFichier);
            Files.copy(fichierSource.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);

            // On retourne le chemin RELATIF (par rapport a dossierRacine)
            String cheminRelatif = annee + "/" + mois + "/" + nomFichier;
            log.info("Document stocke : {}", cheminRelatif);
            return cheminRelatif;

        } catch (IOException e) {
            log.error("Erreur lors du stockage du document", e);
            return null;
        }
    }

    /**
     * Retourne le fichier absolu a partir du chemin relatif stocke en base.
     */
    public File getDocument(String documentPath) {
        return dossierRacine.resolve(documentPath).toFile();
    }

    /**
     * Ouvre le document avec l'application par defaut du systeme.
     * (ex: Adobe Reader pour un PDF, visionneuse pour une image)
     */
    public void ouvrirDocument(String documentPath) {
        try {
            File fichier = getDocument(documentPath);
            if (!fichier.exists()) {
                log.warn("Document introuvable : {}", documentPath);
                return;
            }
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", fichier.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", fichier.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", fichier.getAbsolutePath()).start();
            }
        } catch (IOException e) {
            log.error("Erreur lors de l'ouverture du document", e);
        }
    }

    /**
     * Imprime le document via le systeme d'exploitation.
     */
    public void imprimerDocument(String documentPath) {
        try {
            File fichier = getDocument(documentPath);
            if (!fichier.exists()) {
                log.warn("Document introuvable : {}", documentPath);
                return;
            }
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "Start-Process -FilePath '" + fichier.getAbsolutePath() + "' -Verb Print");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean ok = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!ok || p.exitValue() != 0) {
                    log.warn("Verb Print echoue, ouverture du fichier a la place");
                    ouvrirDocument(documentPath);
                }
            } else if (os.contains("mac")) {
                new ProcessBuilder("lpr", fichier.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("lp", fichier.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'impression, tentative d'ouverture", e);
            ouvrirDocument(documentPath);
        }
    }

    /**
     * Copie le document vers une destination choisie par l'utilisateur.
     */
    public void copierDocument(String documentPath, File destination) {
        try {
            File source = getDocument(documentPath);
            if (source.exists()) {
                Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Document copie vers : {}", destination);
            }
        } catch (IOException e) {
            log.error("Erreur lors de la copie du document", e);
        }
    }

    /**
     * Supprime le document du disque (appele quand on supprime une facture).
     */
    public void supprimerDocument(String documentPath) {
        try {
            File fichier = getDocument(documentPath);
            if (fichier.exists()) {
                Files.delete(fichier.toPath());
                log.info("Document supprime : {}", documentPath);
            }
        } catch (IOException e) {
            log.error("Erreur lors de la suppression du document", e);
        }
    }

    private String getExtension(String nomFichier) {
        int dot = nomFichier.lastIndexOf('.');
        return dot >= 0 ? nomFichier.substring(dot + 1).toLowerCase() : "pdf";
    }
}
