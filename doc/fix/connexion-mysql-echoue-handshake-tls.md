# Échec de connexion MySQL au déroulement du tour (handshake TLS rejeté)

- **Fichier modifié** : `src/main/java/zIgzAg/sql/SessionMysql.java`
- **Méthode en cause** : `SessionMysql.getConnection`
- **Nature** : bug d'intégration driver JDBC / configuration TLS, masqué
  par une gestion d'erreur défaillante (NPE au lieu d'un échec propre) —
  atteignable en jeu normal, à chaque déroulement de tour dans
  l'environnement Docker fourni (`docker-compose.yml`).

## 1. Comportement observé

Signalé lors de l'exécution de `Start newRound` dans le conteneur
`engine` (image `maven:3.9-eclipse-temurin-21`, connecté à `db`
= `mysql:5` sur `db:3306`) :

```
SQLException: Communications link failure

The last packet successfully received from the server was 70 milliseconds ago. ...
SQLState:     08S01
VendorError:  0
java.lang.NullPointerException: Cannot invoke "java.sql.Connection.createStatement()" because "c" is null
	at zIgzAg.sql.SessionMysql.listeTables(SessionMysql.java:48)
	at zIgzAg.jeu.oceane.ReceptionOrdres.chargerDescriptionTables(ReceptionOrdres.java:111)
	at zIgzAg.jeu.oceane.ReceptionOrdres.<init>(ReceptionOrdres.java:56)
	at zIgzAg.jeu.oceane.DeroulementDuTour.main(DeroulementDuTour.java:63)
	at Start.newRound(Start.java:95)
	at Start.main(Start.java:43)
```

Systématique (reproduit à chaque relance de `Start newRound`), pas un
incident réseau ponctuel : le conteneur `db` était démarré et prêt
depuis plus de 20 minutes à chaque échec (pas de course au démarrage),
et le chemin réseau `engine` → `db:3306` (résolution DNS, TCP) a été
vérifié fonctionnel indépendamment.

## 2. Cause racine

`SessionMysql.getConnection` construit l'URL JDBC avec `useSSL=false` :

```java
String inter = "jdbc:mysql://" + host + "/" + base + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
```

Dans ce contexte précis (uniquement lors du déroulement complet du tour
via `DeroulementDuTour`/`ReceptionOrdres`, jamais reproduit avec une
connexion isolée utilisant la même URL et le même jar), le driver
`mysql-connector-java 5.1.49` négocie malgré tout une connexion TLS.
Activé avec `-Djavax.net.debug=ssl:handshake`, le run réel montre :

```
com.mysql.jdbc.ExportControlled.transformSocketToSSLSocket(ExportControlled.java:187)
com.mysql.jdbc.MysqlIO.negotiateSSLConnection(MysqlIO.java:4869)
...
javax.net.ssl.SSLHandshakeException: (handshake_failure) Received fatal alert: handshake_failure
```

Le client (JDK 21) propose une suite TLS 1.2 moderne
(`x25519, secp256r1, ...`, `signature_algorithms` étendus) que MySQL 5.7
— dont le SSL est fourni par yaSSL, aux capacités limitées — rejette
avec une alerte fatale `handshake_failure`. La connexion socket est donc
fermée avant la fin du handshake MySQL, ce que le driver remonte comme
`Communications link failure`.

`getConnection` catch la `SQLException`, l'affiche, mais **retourne
`null`** sans jamais le signaler autrement :

```java
try {
    c = DriverManager.getConnection(inter);
} catch (SQLException e) {
    System.out.println("SQLException: " + e.getMessage());
    ...
}
return c;
```

`ReceptionOrdres` (constructeur) ne vérifie jamais que la connexion
retournée n'est pas `null` avant de l'utiliser, d'où le
`NullPointerException` dans `listeTables` — un symptôme opaque qui masque
la vraie cause (échec TLS) au lieu de l'exposer clairement.

## 3. Correctif appliqué

Renforcer explicitement la désactivation TLS côté client (`useSSL=false`
seul n'a pas suffi à empêcher la tentative de négociation SSL dans ce
contexte), et transformer l'échec de connexion en arrêt propre et lisible
au lieu d'un NPE :

```diff
-		String inter = "jdbc:mysql://" + host + "/" + base + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
+		String inter = "jdbc:mysql://" + host + "/" + base
+				+ "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&requireSSL=false&verifyServerCertificate=false";
 		if (login != null)
 			inter = inter + "&user=" + login + "&password=" + motDePasse;
 		try {
 			c = DriverManager.getConnection(inter);
 		} catch (SQLException e) {
 			System.out.println("SQLException: " + e.getMessage());
 			System.out.println("SQLState:     " + e.getSQLState());
 			System.out.println("VendorError:  " + e.getErrorCode());
 		}
 
+		if (c == null) {
+			System.err.println("Impossible d'établir la connexion à la base de données (" + host + "/" + base + ").");
+			System.exit(-1);
+		}
+
 		return c;
 	}
```

## 4. Vérification effectuée

Reproduction et correction validées avec le vrai chemin de code, dans le
conteneur `engine` (pas un test isolé) :

- **Avant correctif** : `Start newRound` échoue systématiquement dès
  `ReceptionOrdres` avec la stack trace ci-dessus (reproduit 3 fois de
  suite).
- **Avec `-Djavax.net.debug=ssl:handshake`** : confirmation directe que
  `negotiateSSLConnection` est appelé et que le serveur répond
  `handshake_failure` (voir §2).
- **Après correctif, rebuild du jar** (`mvn clean package`) et relance de
  `Start newRound` dans le même conteneur : plus de `handshake_failure`,
  plus de NPE — le dump SQL est créé (`Création du dump des données de la
  base dans dump.sql... Le dump des données a été créé avec succès.`),
  les tables d'ordres sont listées et traitées normalement, les combats
  sont résolus.
- Une connexion isolée avec la même URL/même jar (hors `DeroulementDuTour`)
  a systématiquement réussi (15/15 tentatives), avant et après correctif —
  confirme que le driver et le réseau fonctionnent en eux-mêmes ; seule la
  combinaison avec le contexte réel de `DeroulementDuTour` déclenchait la
  tentative TLS.

## 5. Portée et limites du correctif

- Corrige l'échec de connexion MySQL au déroulement du tour dans
  l'environnement Docker fourni (MySQL 5.7 / yaSSL + JDK 21 côté client).
- Le contrôle `c == null` ajouté transforme tout futur échec de connexion
  (quelle qu'en soit la cause : réseau, identifiants, serveur indisponible)
  en message d'erreur clair et arrêt immédiat, au lieu d'un
  `NullPointerException` — un filet de sécurité générique, pas une
  correction de la cause TLS elle-même.
- Ne corrige pas et n'investigue pas pourquoi le driver tente une
  négociation TLS précisément dans le contexte de `DeroulementDuTour` et
  jamais en connexion isolée (même URL, même jar) — cause exacte de ce
  déclenchement contextuel non élucidée ; hypothèse non retenue faute de
  vérification : pas de piste testée pointant vers un défaut de build/jar
  (le driver identique, chargé depuis le même jar, se comporte de façon
  fiable en isolation).
- Ne modifie aucune autre méthode de `SessionMysql` ; les autres appels
  `getConnection` du code (`ProductionOrdres`) bénéficient du même
  correctif car ils passent tous par cette même méthode.
