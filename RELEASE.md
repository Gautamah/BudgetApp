# Processus de Release - Gestionnaire de Budget

## Prerequis

- JDK 17+ installe et dans le PATH
- Maven 3.8+ installe et dans le PATH
- WiX Toolset 3.x installe et dans le PATH (pour generer le MSI)
- Un repository GitHub pour heberger le projet et les releases

## Etapes pour publier une nouvelle version

### 1. Incrementer la version

Dans `pom.xml`, mettre a jour les deux endroits :

```xml
<version>X.Y.Z</version>
...
<app.version>X.Y.Z</app.version>
```

### 2. Construire l'installateur

```bash
mvn clean package -Pinstaller -DskipTests
```

Le MSI sera genere dans : `target/jpackage/Gestionnaire de Budget-X.Y.Z.msi`

### 3. Creer une release GitHub

1. Aller sur la page GitHub du projet
2. Cliquer sur "Releases" > "Create a new release"
3. Tag : `vX.Y.Z` (ex: `v1.1.0`)
4. Titre : `Version X.Y.Z`
5. Description : noter les changements
6. Joindre le fichier MSI en piece jointe
7. Publier la release

### 4. Mettre a jour le fichier de verification

Modifier `update.properties` a la racine du repo :

```properties
latestVersion=X.Y.Z
downloadUrl=https://github.com/{OWNER}/{REPO}/releases/download/vX.Y.Z/Gestionnaire_de_Budget-X.Y.Z.msi
releaseNotes=Description des changements
```

Commiter et pousser ce fichier sur la branche `main`.

### 5. Configurer l'URL de verification (premiere fois uniquement)

Dans `src/main/resources/application.properties`, configurer l'URL :

```properties
app.update.url=https://raw.githubusercontent.com/{OWNER}/{REPO}/main/update.properties
```

Remplacer `{OWNER}` et `{REPO}` par les valeurs reelles.

## Build de developpement (sans installateur)

```bash
mvn clean package -DskipTests
java -jar target/budget-cli-1.0.0-exec.jar
```

Ou via le plugin JavaFX :

```bash
mvn javafx:run
```

## Structure des fichiers de build

```
target/
  budget-cli-1.0.0.jar          <- JAR standard (classes + resources)
  budget-cli-1.0.0-exec.jar     <- Fat JAR Spring Boot (executable)
  jpackage-input/               <- Staging pour jpackage (JAR + dependances)
  jpackage/
    Gestionnaire de Budget-1.0.0.msi  <- Installateur Windows
```
