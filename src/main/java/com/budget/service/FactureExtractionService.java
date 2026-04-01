package com.budget.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Service responsable de l'extraction de texte depuis des fichiers PDF et images.
 * Utilise PDFBox pour les PDF texte, et Tess4j (Tesseract OCR) pour les images
 * et les PDF scannes.
 */
@Service
public class FactureExtractionService {

    private static final Logger log = LoggerFactory.getLogger(FactureExtractionService.class);

    // Seuil minimum de caracteres pour considerer qu'un PDF contient du "vrai" texte.
    // En dessous, on considere que c'est un scan et on bascule sur l'OCR.
    private static final int SEUIL_TEXTE_MINIMUM = 50;

    // Resolution en DPI pour convertir une page PDF en image avant OCR.
    // 300 DPI est un bon compromis entre qualite et performance.
    private static final float DPI_RENDU = 300f;

    private Tesseract tesseract;
    private Path tessdataDir;

    /**
     * Methode executee automatiquement au demarrage de l'application (grace a @PostConstruct).
     * Elle prepare le moteur OCR Tesseract :
     * 1. Copie le fichier de langue fra.traineddata depuis les ressources du JAR
     *    vers un dossier temporaire sur le disque
     * 2. Configure Tesseract pour utiliser ce dossier et la langue francaise
     */
    @PostConstruct
    public void initialiser() {
        try {
            // On cree un dossier temporaire pour y mettre le fichier de langue
            tessdataDir = Path.of(System.getProperty("java.io.tmpdir"), "budget-tessdata", "tessdata");
            Files.createDirectories(tessdataDir);

            // On copie fra.traineddata depuis les ressources embarquees dans le JAR
            // vers le dossier temporaire (Tesseract a besoin d'un fichier sur le disque)
            Path fraFile = tessdataDir.resolve("fra.traineddata");
            if (!Files.exists(fraFile)) {
                try (InputStream is = getClass().getResourceAsStream("/tessdata/fra.traineddata")) {
                    if (is != null) {
                        Files.copy(is, fraFile, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Fichier tessdata copie vers : {}", fraFile);
                    } else {
                        log.warn("fra.traineddata introuvable dans les ressources");
                    }
                }
            }

            tesseract = new Tesseract();
            String datapath = tessdataDir.toAbsolutePath().toString();
            tesseract.setDatapath(datapath);
            tesseract.setLanguage("fra");
            tesseract.setOcrEngineMode(1);

            log.info("Tesseract OCR initialise (datapath: {}, langue: fra)", datapath);

        } catch (IOException e) {
            log.error("Erreur lors de l'initialisation de Tesseract", e);
        }
    }

    /**
     * Point d'entree principal : extrait le texte d'un fichier PDF ou image.
     *
     * @param fichier le fichier a lire (PDF, JPG ou PNG)
     * @return le texte extrait, ou une chaine vide si l'extraction echoue
     */
    public String extraireTexte(File fichier) {
        String nom = fichier.getName().toLowerCase();

        if (nom.endsWith(".pdf")) {
            return extraireTextePDF(fichier);
        } else if (nom.endsWith(".jpg") || nom.endsWith(".jpeg") || nom.endsWith(".png")) {
            return extraireTexteImage(fichier);
        } else {
            log.warn("Format de fichier non supporte : {}", nom);
            return "";
        }
    }

    /**
     * Extrait le texte d'un fichier PDF.
     * Strategie en 2 temps :
     * 1. On essaie d'extraire le texte directement (PDF "natif")
     * 2. Si le texte est trop court (< 50 chars), c'est probablement un scan,
     *    alors on convertit chaque page en image et on fait de l'OCR
     */
    private String extraireTextePDF(File fichier) {
        try (PDDocument document = Loader.loadPDF(fichier)) {

            // Etape 1 : extraction directe du texte
            PDFTextStripper stripper = new PDFTextStripper();
            String texte = stripper.getText(document);

            // Si le texte est assez long, c'est un PDF avec du "vrai" texte
            if (texte != null && texte.trim().length() >= SEUIL_TEXTE_MINIMUM) {
                log.info("PDF texte detecte ({} caracteres)", texte.trim().length());
                return texte.trim();
            }

            // Etape 2 : le texte est trop court -> c'est probablement un scan
            // On convertit chaque page en image puis on fait de l'OCR
            log.info("PDF scanne detecte, passage en mode OCR ({} pages)", document.getNumberOfPages());
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI_RENDU);
                String texteOcr = faireOCR(image);
                sb.append(texteOcr).append("\n");
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Erreur lors de la lecture du PDF : {}", fichier.getName(), e);
            return "";
        }
    }

    /**
     * Extrait le texte d'une image (JPG/PNG) via OCR.
     */
    private String extraireTexteImage(File fichier) {
        try {
            BufferedImage image = ImageIO.read(fichier);
            if (image == null) {
                log.error("Impossible de lire l'image : {}", fichier.getName());
                return "";
            }
            return faireOCR(image);
        } catch (IOException e) {
            log.error("Erreur lors de la lecture de l'image : {}", fichier.getName(), e);
            return "";
        }
    }

    /**
     * Effectue l'OCR sur une image en memoire.
     * C'est ici que Tesseract "lit" les pixels de l'image et en deduit du texte.
     */
    private String faireOCR(BufferedImage image) {
        if (tesseract == null) {
            log.warn("Tesseract non initialise — import manuel requis");
            return "";
        }
        try {
            String texte = tesseract.doOCR(image);
            return texte != null ? texte.trim() : "";
        } catch (TesseractException e) {
            log.error("Erreur OCR", e);
            return "";
        } catch (Error e) {
            log.error("Erreur native Tesseract (OCR indisponible) : {}", e.getMessage());
            return "";
        }
    }
}
