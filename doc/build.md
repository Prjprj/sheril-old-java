# Build du moteur Java

Le moteur de jeu (`src/main/java/`) se compile et se packages avec **Maven**. `libs/` et le `javac` manuel ont disparu ; `scripts/create-jar.sh` reste le point d'entrée habituel, il ne fait plus qu'appeler Maven.

## Prérequis

- JDK 21
- Maven 3.9+ (l'image Docker `engine` du `docker-compose.yml` embarque déjà les deux : `maven:3.9-eclipse-temurin-21`)

## Arborescence

```
pom.xml                       # groupId=zigzag, artifactId=sheril
src/main/java/                # code du moteur (ex-sources/)
  Start.java, Test.java       #   paquet par défaut, inchangé
  zIgzAg/...                  #   paquets applicatifs
src/test/java/                # tests unitaires (JUnit 5)
target/                       # sortie Maven (gitignored)
  sheril.jar                  #   jar assemblé (fat-jar, dépendances incluses)
  site/                       #   site généré (mvn site)
    apidocs/                  #     Javadoc
    jacoco/                   #     rapport de couverture
```

## Commandes

| Commande | Effet |
|---|---|
| `mvn compile` | Compile le code (`target/classes/`) |
| `mvn test` | Compile + exécute les tests JUnit + génère le rapport de couverture (`target/site/jacoco/index.html`) |
| `mvn package` | Idem `test` + assemble le fat-jar (`target/sheril.jar`) |
| `mvn site` | Génère le site du projet (`target/site/index.html`), avec Javadoc et rapport de couverture liés |
| `mvn clean verify site` | Build complet — c'est ce que la CI exécute sur chaque PR |
| `./scripts/create-jar.sh` | `mvn package` + copie `target/sheril.jar` vers `./sheril.jar` (voir plus bas) |

## `sheril.jar` reste committé à la racine

`sheril.jar` continue d'être versionné dans Git au même chemin qu'avant la migration, régénéré par `scripts/create-jar.sh`. C'est un choix délibéré pour ne rien changer côté `README.md`, `scripts/init.sh` et `docker-compose.yml`, qui référencent tous `sheril.jar` à la racine sans passer par `target/`. Ce n'est pas la pratique Maven idiomatique (ne pas committer les artefacts de build), mais changer ça touche trois fichiers utilisés au quotidien pour un gain marginal — à faire séparément si voulu, pas dans le cadre de cette migration mécanique.

## Dépendances

Trois dépendances externes, toutes montées à la version la plus haute publiée sur Maven Central **ne nécessitant aucune modification de code** par rapport aux JAR historiquement vendorés dans `libs/` :

| Dépendance | Ancienne version (`libs/`) | Nouvelle version | Pourquoi cette borne |
|---|---|---|---|
| `mysql:mysql-connector-java` | 5.1.7 | **5.1.49** | Le code charge explicitement `org.gjt.mm.mysql.Driver` (`SessionMysql.java`) — cette classe de compatibilité n'existe que dans la lignée 5.1.x. Elle est retirée à partir de 6.x, où le driver moderne s'appelle `com.mysql.cj.jdbc.Driver`. 5.1.49 est la dernière version 5.1.x publiée, vérifiée conserver `org.gjt.mm.mysql.Driver.class`. Passer à 8.x nécessiterait de modifier `SessionMysql.java`/`SessionSQL.java` (voir `EVOLUTIONS.md`, item P4-03) — hors scope ici. |
| `com.sun.mail:javax.mail` | 1.4.4 (`mail.jar`) | **1.6.2** | Le code importe `javax.mail.*` (`Mail.java`, `ProductionOrdres.java`), pas `jakarta.mail.*`. 1.6.2 est le dernier artefact publié sous ce namespace avant le renommage `jakarta.mail` en 2.x — aucun changement d'import requis. |
| `com.sun.activation:javax.activation` | 1.1.1 (`activation.jar`) | **1.2.0** | Même logique : `javax.activation.*` importé directement (`Mail.java`), dernier artefact publié sous ce namespace avant `jakarta.activation`. Retiré du JDK depuis Java 11, doit rester une dépendance explicite. |

### JAR supprimés (morts, pas remplacés)

- **`libs/pircbot.jar`** : aucun import `org.jibble`/pircbot nulle part dans le code — dépendance jamais utilisée.
- **`libs/jakarta.mail-2.0.1.jar`** : mauvais namespace pour le code actuel (qui utilise `javax.mail`, pas `jakarta.mail`) — était déjà mort en pratique avant même cette migration.

## Tests

`src/test/java/zIgzAg/sql/SessionSQLTest.java` : test basique de `SessionSQL` (construction de fragments SQL utilisée par `ReceptionOrdres`/`ProductionOrdres`/`InputSQLWriter`). Première infrastructure de test du projet — pose les bases (JUnit 5, JaCoCo) pour une suite plus complète (voir `EVOLUTIONS.md`, items P3-39/P3-40, qui prévoient Mockito et Testcontainers pour tester combat/ordres/budget).

## CI

`.github/workflows/compile.yml` ("Build & Test") exécute `mvn -B verify site` sur chaque pull request : compilation, tests, couverture, packaging et génération du site en une seule commande.
