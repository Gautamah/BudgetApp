package com.budget.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    @Value("${app.update.url:}")
    private String updateUrl;

    @Value("${app.update.enabled:true}")
    private boolean updateEnabled;

    private String currentVersion;

    @PostConstruct
    public void init() {
        loadCurrentVersion();
    }

    private void loadCurrentVersion() {
        try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                currentVersion = props.getProperty("app.version", "0.0.0");
            } else {
                currentVersion = "0.0.0";
            }
        } catch (IOException e) {
            log.warn("Impossible de lire version.properties", e);
            currentVersion = "0.0.0";
        }
        log.info("Version courante : {}", currentVersion);
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Verifie de maniere asynchrone si une mise a jour est disponible.
     * Le fichier distant est au format .properties avec les cles :
     * latestVersion, downloadUrl, releaseNotes
     */
    public CompletableFuture<UpdateInfo> checkForUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            if (!updateEnabled || updateUrl == null || updateUrl.isBlank()) {
                log.debug("Verification de mise a jour desactivee ou URL non configuree");
                return null;
            }
            try {
                HttpURLConnection conn = openConnection(updateUrl);
                if (conn.getResponseCode() != 200) {
                    log.debug("Serveur de mise a jour indisponible (HTTP {})", conn.getResponseCode());
                    return null;
                }

                Properties props = new Properties();
                try (InputStream is = conn.getInputStream()) {
                    props.load(is);
                }

                String latestVersion = props.getProperty("latestVersion");
                String downloadUrl = props.getProperty("downloadUrl");
                String releaseNotes = props.getProperty("releaseNotes", "");

                if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                    log.info("Mise a jour disponible : {} -> {}", currentVersion, latestVersion);
                    return new UpdateInfo(latestVersion, downloadUrl, releaseNotes);
                }

                log.debug("Application a jour (version {})", currentVersion);
                return null;
            } catch (Exception e) {
                log.debug("Verification de mise a jour echouee : {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Telecharge le fichier MSI depuis l'URL donnee.
     * Le callback de progression recoit une valeur entre 0.0 et 1.0.
     * Gere les redirections HTTP (courantes avec GitHub Releases).
     */
    public void downloadUpdate(String downloadUrl, Path targetPath, Consumer<Double> progressCallback)
            throws IOException {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("URL de téléchargement non configurée");
        }

        HttpURLConnection conn = openConnection(downloadUrl);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);

        // GitHub Releases redirige souvent (302) vers un CDN.
        // HttpURLConnection suit les redirections automatiquement dans le meme protocole,
        // mais on gere aussi manuellement au cas ou.
        int responseCode = conn.getResponseCode();
        int maxRedirects = 5;
        while ((responseCode == 301 || responseCode == 302 || responseCode == 307)
                && maxRedirects-- > 0) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl == null) break;
            log.debug("Redirection vers : {}", redirectUrl);
            conn = openConnection(redirectUrl);
            conn.setReadTimeout(60000);
            responseCode = conn.getResponseCode();
        }

        if (responseCode != 200) {
            throw new IOException("Serveur indisponible (HTTP " + responseCode
                    + "). La mise à jour n'est peut-être pas encore publiée.");
        }

        long totalSize = conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(targetPath)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0 && progressCallback != null) {
                    progressCallback.accept((double) downloaded / totalSize);
                }
            }
        }
        log.info("MSI telecharge : {}", targetPath);
    }

    /**
     * Lance l'installateur MSI telecharge via msiexec.
     */
    public void launchInstaller(Path msiPath) throws IOException {
        log.info("Lancement de l'installateur : {}", msiPath);
        new ProcessBuilder("msiexec", "/i", msiPath.toString())
                .inheritIO()
                .start();
    }

    static boolean isNewerVersion(String remote, String local) {
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        for (int i = 0; i < 3; i++) {
            if (r[i] > l[i]) return true;
            if (r[i] < l[i]) return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        int[] parts = {0, 0, 0};
        String[] tokens = version.split("\\.");
        for (int i = 0; i < Math.min(tokens.length, 3); i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return parts;
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "BudgetApp/" + currentVersion);
        return conn;
    }

    public record UpdateInfo(String version, String downloadUrl, String releaseNotes) {
    }
}
