# Évolutions du projet Sheril

Priorisation : P1 (critique/sécurité) → P2 (stabilité) → P3 (maintenabilité) → P4 (modernisation stack) → P5 (qualité long terme)

---

## P1 — Sécurité

### P1-01 — Hacher les mots de passe

**Problème :** Les mots de passe sont stockés en clair dans `aa_registre.MOT_DE_PASSE VARCHAR(20)`.

**Action :**
1. Modifier le schéma MySQL (`divers/base_sheril.sql`) : `MOT_DE_PASSE VARCHAR(255)`
2. Dans `php/connexion.php` et `php/register.php` : remplacer la comparaison directe par `password_verify($password, $hash)` et le stockage par `password_hash($password, PASSWORD_BCRYPT)`
3. Script de migration one-shot pour hacher les mots de passe existants en base
4. Dans `php/includes/auth.php` : la requête SQL ne doit plus comparer `MOT_DE_PASSE` en SQL — récupérer le hash et vérifier en PHP

**Fichiers concernés :** `php/includes/auth.php`, `php/connexion.php`, `php/register.php`, `divers/base_sheril.sql`

---

### P1-02 — Remplacer les requêtes SQL concaténées par des requêtes préparées (PDO)

**Problème :** Toutes les requêtes PHP utilisent `mysql_real_escape_string()` + concaténation de chaîne. `mysql_compat.php` est un polyfill vers `mysqli` mais sans parameterized queries.

**Action :**
1. Créer `php/includes/db.php` : retourne une instance PDO singleton (`new PDO("mysql:host=$host;dbname=$base", $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION])`)
2. Remplacer dans `php/includes/auth.php` toutes les requêtes par `$pdo->prepare(...)->execute([...])`
3. Faire de même pour `php/forum/`, `php/ordres/`, `php/register.php`, `php/stats.php`
4. Supprimer l'usage de `mysql_compat.php` une fois toutes les requêtes migrées

**Fichiers concernés :** Tous les fichiers PHP contenant `mysql_query(` — grep pour identifier exhaustivement

---

### P1-03 — Ajouter la protection CSRF sur les formulaires d'ordres

**Problème :** Aucun token CSRF sur les formulaires de `php/ordres/`. Un attaquant peut faire passer des ordres au nom d'un joueur connecté.

**Action :**
1. Dans `php/includes/top.php` (ou `auth.php`) : générer un token CSRF en session au login (`$_SESSION['csrf_token'] = bin2hex(random_bytes(32))`)
2. Ajouter un champ caché `<input type="hidden" name="csrf_token" value="<?= $_SESSION['csrf_token'] ?>">` dans tous les formulaires POST
3. Créer une fonction `csrf_verify()` qui compare `$_POST['csrf_token']` avec `$_SESSION['csrf_token']` et stoppe l'exécution si invalide
4. Appeler `csrf_verify()` en tête de chaque script recevant un POST (`ordres/*.php`, `register.php`, `connexion.php`, `forum/post.php`)

**Fichiers concernés :** `php/includes/top.php`, `php/includes/auth.php`, tous les `.php` avec formulaires POST

---

### P1-04 — Régénérer l'ID de session après login

**Problème :** `php/includes/auth.php::auth_login()` ne régénère pas l'ID de session après authentification, ce qui permet une attaque par session fixation.

**Action :** Ajouter `session_regenerate_id(true);` immédiatement après `$_SESSION['commandant_num'] = $rf['NUMERO'];` dans `auth_login()`.

**Fichiers concernés :** `php/includes/auth.php`

---

### P1-05 — [FRONT] Corriger le XSS dans l'éditeur Quill du forum

**Problème :** Dans `php/forum/view_topic.php`, le contenu de l'éditeur Quill est soumis en HTML brut : `bodyInput.value = quill.root.innerHTML`. Si le PHP de réception n'échappe pas ce contenu à l'affichage (et il ne le fait pas systématiquement), n'importe quel utilisateur peut injecter du JavaScript exécuté chez tous les lecteurs du topic.

**Action :**
1. Côté PHP (`php/forum/post.php` ou le script qui reçoit le POST) : appliquer `htmlspecialchars()` sur le contenu reçu avant tout stockage en base, ou utiliser une bibliothèque de sanitization HTML (ex. HTMLPurifier) qui autorise le HTML de mise en forme (gras, listes) mais bloque les balises dangereuses (`<script>`, `onclick`, etc.)
2. Côté JS dans `view_topic.php` : ajouter une validation avant soumission pour bloquer les formulaires vides (`if (quill.getText().trim().length === 0) { event.preventDefault(); return; }`)
3. À l'affichage des messages, s'assurer que le HTML stocké passe par HTMLPurifier avant d'être rendu

**Fichiers concernés :** `php/forum/view_topic.php`, `php/forum/post.php`, ajouter HTMLPurifier via Composer

---

### P1-12 — [UX] Supprimer les FRAMESET dans `ordres.php3` et migrer vers un layout CSS

**Problème :** `php/ordres/ordres.php3` utilise des `<FRAMESET>` HTML4, supprimés depuis HTML5 et totalement inopérants sur mobile :
```html
<FRAMESET frameborder="0" cols="20%,*" framespacing="0" border="false">
  <FRAME src="./menu.php3" name="men" id="menu"></FRAME>
  <FRAME src="./" name="fenetre" id="fenetre"></FRAME>
</FRAMESET>
```
Conséquences directes : la console d'ordres est **inaccessible sur smartphone et tablette**, impossible à naviguer au clavier, non-indexable, et incompatible avec la CSP (P2-12). C'est la fonctionnalité centrale du jeu qui touche chaque joueur à chaque tour.

**Action :**
1. Remplacer `php/ordres/ordres.php3` et `php/ordres/menu.php3` par un seul fichier `php/ordres/ordres.php` utilisant un layout CSS Flexbox :
   ```html
   <div class="console-layout">
     <aside class="console-menu">
       <?php include 'menu.php'; ?>
     </aside>
     <main class="console-content" id="contenu">
       <?php
       $action = $_GET['table'] ?? null;
       if ($action && file_exists("fr/choix/{$action}.php")) {
           include "fr/choix/{$action}.php";
       } else {
           include 'accueil-ordres.php';
       }
       ?>
     </main>
   </div>
   ```
2. Le CSS associé dans `styles.sass` : `.console-layout { display: flex; gap: 1rem; }` avec `@media (max-width: 768px) { flex-direction: column; }` pour mobile
3. Mettre à jour tous les liens du site pointant vers `ordres.php3` pour qu'ils pointent vers `ordres.php`
4. Les fichiers `.php3` dans `php/ordres/` (menu.php3, body.txt, etc.) doivent être renommés en `.php` et nettoyés des balises HTML4 obsolètes (`<FONT>`, majuscules, `<CENTER>`)
5. Mettre à jour `Dockerfile` : supprimer la ligne `AddType application/x-httpd-php .php3` une fois la migration terminée

**Fichiers concernés :** `php/ordres/ordres.php3`, `php/ordres/menu.php3`, `Dockerfile`, `php/includes/top.php` (liens nav), `php/assets/css/styles.sass`

---

### P1-09 — [SÉCU] Contrôle d'accès brisé (IDOR) sur les endpoints d'ordres

**Problème :** Plusieurs endpoints PHP acceptent un identifiant de commandant issu de paramètres GET/POST sans vérifier que cet identifiant correspond bien à la session de l'utilisateur connecté. La session stocke `commandant_num` mais le code de certaines pages fait confiance à des paramètres extérieurs.

Exemple dans `php/ordres/division.php:50` :
```php
mysql($base, "DELETE FROM diviser_flotte WHERE id = {$_GET['identifier']} AND NUMERO=$commandant");
```
`$_GET['identifier']` n'est pas casté en entier (`intval()` absent) — un attaquant peut passer `identifier=1 OR 1=1` et supprimer toutes les divisions de tous les joueurs.

Plus grave : la valeur `$commandant` elle-même est lue depuis `$_SESSION['commandant_num']` mais n'est **jamais re-vérifiée en base** — si un attaquant parvient à modifier son cookie de session (forgery), il peut agir au nom de n'importe quel commandant.

**Action :**
1. Dans tous les fichiers de `php/ordres/` : passer chaque paramètre GET/POST numérique par `intval()` avant toute utilisation dans une requête SQL
2. Créer une fonction `get_commandant_verified(PDO $pdo): int` dans `php/includes/auth.php` qui lit `$_SESSION['commandant_num']` ET effectue un `SELECT COUNT(*) FROM aa_registre WHERE NUMERO = ?` pour confirmer que ce numéro existe toujours en base — appeler cette fonction en tête de chaque script d'ordre
3. Pour les opérations sensibles (supprimer un ordre, passer une flotte), vérifier que l'entité manipulée appartient bien au commandant connecté avant toute modification : `WHERE id = ? AND NUMERO = ?` avec les deux paramètres liés
4. Grep systématique pour `$_GET[` et `$_POST[` dans `php/ordres/` pour identifier toutes les occurrences non protégées

**Fichiers concernés :** `php/ordres/division.php`, `php/ordres/list_ordres.php`, `php/ordres/marche_galactique.php`, `php/includes/auth.php`, et tous les fichiers dans `php/ordres/`

---

### P1-10 — [SÉCU] Secret OAuth Discord hardcodé en clair dans le code source

**Problème :** `php/auth/callback.php` contient un secret client OAuth Discord hardcodé directement dans le code versionné :
```php
$client_secret = "p5_42sz1lt47itNhECzt55kL3nF4S__7";
```
Ce secret est exposé à quiconque a accès au dépôt Git. Il permet à un attaquant de s'authentifier en tant qu'application Discord, d'usurper les webhooks, et potentiellement de compromettre le compte Discord associé à l'application.

**Action :**
1. **Immédiatement** : révoquer le secret client dans la console développeur Discord (https://discord.com/developers/applications) et en générer un nouveau
2. Déplacer le nouveau secret dans `php/secure/connect.txt` (non versionné) ou dans une variable d'environnement Docker
3. Dans `php/auth/callback.php` : remplacer la valeur hardcodée par `$client_secret = $_ENV['DISCORD_CLIENT_SECRET'] ?? getenv('DISCORD_CLIENT_SECRET')`
4. Vérifier dans l'historique Git si d'autres secrets sont présents (`git log -p --all | grep -E "(secret|password|token|key)\s*=\s*['\"]"`) et les révoquer également
5. Ajouter `.env` et `php/secure/connect.txt` dans `.gitignore` (vérifier qu'ils n'ont jamais été committés)

**Fichiers concernés :** `php/auth/callback.php`, `php/secure/connect.txt.sample`, `.gitignore`

---

### P1-11 — [SÉCU] IDOR et path traversal dans `download.php` — accès aux rapports d'autres joueurs

**Problème :** `php/auth/download.php` permet à un joueur connecté de télécharger n'importe quel rapport de tour, y compris ceux des autres joueurs. Deux bugs combinés :

1. **Bug logique bitwise** : la validation du numéro de tour utilise `&` (ET bit-à-bit) au lieu de `&&` (ET logique) :
   ```php
   $tour = ($givenTurn > 0 & $givenTurn <= $currentTurn) ? $givenTurn : $currentTour;
   ```
   Des valeurs comme `turn=2` avec certaines combinaisons peuvent contourner la validation.

2. **IDOR** : le numéro du joueur `$num` est lu depuis `$_SESSION['commandant_num']`, mais si la session est corrompue ou si un autre paramètre contrôle le chemin, le fichier téléchargé appartient potentiellement à un autre joueur.

3. **Information disclosure** : en cas de fichier introuvable, le code affiche le chemin complet du serveur : `exit("Fichier introuvable " . $file)`.

**Action :**
1. Remplacer `&` par `&&` ligne de validation du tour
2. Valider strictement que le fichier téléchargé correspond au commandant de la session : construire le chemin uniquement avec `$_SESSION['commandant_num']` (ne pas utiliser de paramètre GET pour le numéro du joueur)
3. Utiliser `realpath()` pour canonicaliser le chemin et vérifier qu'il reste dans le répertoire attendu :
   ```php
   $expectedDir = realpath(__DIR__ . "/../rapports/$tour/");
   $filePath = realpath($file);
   if (!$filePath || strpos($filePath, $expectedDir) !== 0) { http_response_code(403); exit; }
   ```
4. Remplacer le message d'erreur par un message générique sans chemin : `http_response_code(404); exit("Fichier non trouvé");`

**Fichiers concernés :** `php/auth/download.php`

---

### P1-14 — [CVE] Supprimer les dépendances JAR obsolètes et redondantes

**Problème :** Le répertoire `libs/` contient plusieurs JAR problématiques :
- `mail.jar` (`javax.mail`, legacy pré-2019) **coexiste** avec `jakarta.mail-2.0.1.jar` → conflit de classpath garanti, comportements imprévisibles lors de l'envoi d'emails, CVE-2015-5254 (DoS sur parsing MimeMessage)
- `pircbot.jar` : bibliothèque IRC abandonnée vers 2006, sans SSL/TLS, sans validation d'hostname — si utilisée pour des notifications IRC, toute communication est interceptable en clair
- `activation.jar` (`javax.activation`) : inclus nativement dans JDK 11+, inutile en JAR externe, CVE-2021-21342 (désérialisation)

**Action :**
1. Grep dans `sources/` pour identifier les imports de chaque JAR : `grep -r "pircbot\|javax.mail\|activation" sources/ --include="*.java"`
2. Supprimer `mail.jar` (javax.mail) — utiliser uniquement `jakarta.mail-2.0.1.jar` (ou 2.1.3+ disponible)
3. Supprimer `activation.jar` — aucun `import` dans les sources ne devrait en avoir besoin avec JDK 21
4. Si `pircbot.jar` est encore actif : remplacer par `kitteh-irc-client-library` (maintenu, TLS natif) ou supprimer si inutilisé
5. Une fois Maven adopté (P3-04) : gérer toutes ces dépendances via `pom.xml` avec versions explicites et vérification CVE automatique

**Fichiers concernés :** `libs/mail.jar` (suppression), `libs/activation.jar` (suppression), `libs/pircbot.jar` (suppression ou remplacement), `sources/` (corriger les imports)

---

### P1-15 — [RGPD] Supprimer l'envoi de mots de passe en clair par email

**Problème :** `php/plop.php` envoie le mot de passe de l'utilisateur **en clair dans le corps d'un email** :
```php
$password = $row['MOT_DE_PASSE'];
$message .= "Mot de passe : $password\n\n";
```
Combiné avec le stockage en clair en base (P1-01), cela crée une double exposition : les mots de passe circulent en clair dans les emails et sont potentiellement stockés dans les serveurs de messagerie des joueurs indéfiniment. C'est une violation directe de l'Art. 32 RGPD et une pratique formellement interdite par la CNIL.

**Action :**
1. **Supprimer immédiatement** tout code qui lit `MOT_DE_PASSE` depuis la base pour l'inclure dans un email — grep `MOT_DE_PASSE` dans `php/plop.php` et tout autre fichier PHP
2. Remplacer par un **lien de réinitialisation à usage unique** : générer un token `bin2hex(random_bytes(32))`, le stocker haché en base dans une table `aa_reset_password (token_hash, commandant_id, expires_at)`, envoyer uniquement l'URL de réinitialisation
3. Le lien expire après 24h — purger automatiquement les tokens expirés
4. Prérequis P1-01 (hachage des mots de passe) pour que cette réinitialisation fonctionne correctement
5. Audit de tous les fichiers PHP qui envoient des emails pour s'assurer qu'aucun autre champ sensible n'est transmis en clair

**Fichiers concernés :** `php/plop.php`, créer `php/reset-password.php`, `divers/base_sheril.sql` (nouvelle table `aa_reset_password`)

---

### P1-13 — [DEVOPS] Corriger les permissions `0777` sur `php/live/`

**Problème :** `scripts/init.sh` ligne 3 exécute `chmod -R 0777 php/live/`. Cela rend le répertoire lisible, modifiable et exécutable par n'importe quel utilisateur sur le système — y compris l'utilisateur web du conteneur Apache. Un attaquant ayant accès à un upload ou à une injection de code peut écrire un fichier PHP dans ce répertoire et l'exécuter directement, obtenant ainsi un webshell.

**Action :**
1. Dans `scripts/init.sh` : remplacer `chmod -R 0777 php/live/` par `chmod -R 0755 php/live/ && chown -R www-data:www-data php/live/` — le propriétaire `www-data` peut écrire, les autres ne peuvent que lire et traverser
2. Identifier pourquoi `0777` a été choisi : si Apache a besoin d'écrire dans ce répertoire, `0755` + `chown www-data` suffit ; si le moteur Java doit aussi écrire (mode `IS_LOCAL`), utiliser un groupe partagé `0775` plutôt que `0777`
3. Vérifier que les fichiers déjà créés dans `php/live/` lors de déploiements passés n'ont pas hérité de `0777` : `find php/live/ -perm 0777 -exec chmod 0644 {} \;`
4. Ajouter un test dans le script : `if [ -d php/live/ ] && [ "$(stat -c %a php/live/)" = "777" ]; then echo "ERREUR: permissions trop larges"; exit 1; fi`

**Fichiers concernés :** `scripts/init.sh`

---

### P1-16 — [SÉCU] Désérialisation PHP non validée dans `principal.txt` → RCE potentielle

**Problème :** `php/ordres/principal.txt` ligne 18 et 30 passe directement un paramètre GET dans `unserialize()` sans aucune validation :
```php
$tableau = unserialize(urldecode($_GET['previous']));
```
Un attaquant connecté avec n'importe quel compte peut soumettre un objet PHP sérialisé malveillant. Selon les classes disponibles dans le contexte PHP (gadget chains), cela peut mener à de l'exécution de code arbitraire (RCE), de l'écriture de fichiers, ou du SSRF. C'est une vulnérabilité de classe OWASP A08 (Software and Data Integrity Failures).

**Action :**
1. Supprimer complètement l'usage de `unserialize()` sur des données utilisateur — remplacer par `json_decode()` qui ne présente pas ce risque : `$tableau = json_decode(urldecode($_GET['previous']), true)`
2. Si le format sérialisé doit être conservé pour compatibilité ascendante, valider l'origine avec un HMAC signé côté serveur avant de désérialiser : envoyer `previous=<payload>&sig=<hmac_sha256(payload, SECRET)>`, vérifier la signature avant `unserialize()`
3. Chercher dans tout `php/` d'autres appels à `unserialize()` sur des données non validées : `grep -rn "unserialize" php/`
4. Côté génération : le code qui produit la valeur `previous` (probablement dans `affiche.txt`) doit aussi basculer vers JSON

**Fichiers concernés :** `php/ordres/principal.txt`, rechercher tous les `unserialize()` dans `php/`

---

### P1-18 — [SÉCU] Injection SQL dans `elimine.txt` — divulgation de schéma et suppression arbitraire

**Problème :** `php/ordres/elimine.txt` utilise `$table` (issu de `$_GET['table']` via `principal.txt`) dans trois requêtes SQL sans validation :
1. `SHOW COLUMNS FROM $table` (ligne 10) — expose le schéma de n'importe quelle table de la base
2. `DELETE FROM $table WHERE $identifierKey='$identifier' AND NUMERO='$commandant'` (ligne 20) — suppression ciblée
3. `SELECT * FROM $table` + `DELETE FROM $table WHERE $var_result` (lignes 23-36) — suppression par index de ligne

La protection de `$identifierKey` (validé contre `SHOW COLUMNS`) est contournée dès que `$table` est contrôlable : l'attaquant choisit une table, récupère ses colonnes, et construit une suppression valide.

**Action :** Appliquer la liste blanche sur `$table` en tête de `elimine.txt`, identique à la correction de P1-17. La variable `$allowed_tables` définie dans `principal.txt` doit être disponible dans le contexte inclus avant tout traitement.

**Fichiers concernés :** `php/ordres/elimine.txt`

---

### P1-19 — [SÉCU] Injection SQL dans `insert.txt` et `affiche.txt` — propagation du vecteur `$table`

**Problème :** Le même vecteur `$table` (issu de `$_GET['table']`) se propage à deux autres fichiers inclus par `principal.txt` :

- **`insert.txt` lignes 57, 76, 85, 102** : `SELECT * FROM $table` et `INSERT INTO $table(...)` — un attaquant peut insérer des ordres dans n'importe quelle table d'ordres en choisissant `$table` librement. Les valeurs (`$v0`-`$v7`) sont correctement échappées. La première valeur est toujours `$commandant` (ligne 88 : `$var_champ = "'$commandant'"`) — **impact limité au joueur courant, pas d'usurpation d'identité possible**.
- **`affiche.txt` ligne 2** : `SELECT * FROM $table WHERE NUMERO='$commandant'` — lecture seule filtrée sur le joueur courant, constitue le vecteur d'exfiltration du PoC de `principal.txt`.

**Action :** La correction de P1-17 (liste blanche sur `$table` dans `principal.txt`) corrige automatiquement ces deux fichiers car `$table` leur est transmis depuis `principal.txt`. Vérifier après correction que `insert.txt` et `affiche.txt` n'accèdent pas à `$_GET['table']` directement.

**Fichiers concernés :** `php/ordres/insert.txt`, `php/ordres/affiche.txt`

---

### P1-17 — [SÉCU] Local File Inclusion via le paramètre `$table` dans `principal.txt`

**Problème :** `php/ordres/principal.txt` utilise `$table` (issu de `$_GET['table']`) directement dans des `include` et une requête SQL :
```php
include "./data/$table.txt";          // ligne 14
include "./$langue/choix/$table.txt"; // ligne 28
include "./$langue/aide/$table.txt";  // ligne 51
mysql($base, "SELECT COUNT(*) as total FROM $table WHERE NUMERO=$commandant"); // ligne 41
```
Bien que certaines valeurs soient vérifiées (`list_ordres`, `diviser_flotte`), la branche `else if ($table != "")` accepte n'importe quelle valeur. Un attaquant peut injecter `../../secure/connect` pour inclure `php/secure/connect.txt` (credentials BDD) ou d'autres fichiers `.txt` du serveur.

**Action :**
1. Implémenter une liste blanche stricte des valeurs autorisées pour `$table` en tête de `principal.txt` :
   ```php
   $allowed_tables = array_merge($code_ordres, ['list_ordres', 'diviser_flotte', 'marche_galactique', 'technology_plan']);
   if (!in_array($table, $allowed_tables, true)) {
       http_response_code(400);
       die("Ordre inconnu");
   }
   ```
2. Pour la requête SQL ligne 41 : `$table` étant whitelisté, l'utilisation dans la requête est alors sécurisée — mais ajouter des backticks par précaution : `` "SELECT COUNT(*) as total FROM `$table` WHERE NUMERO=?" ``
3. Ne jamais construire un chemin de fichier `include` avec une donnée utilisateur non whitelistée

**Fichiers concernés :** `php/ordres/principal.txt`

---

### P1-07 — [JAVA] Injection SQL côté Java dans `SessionSQL`

**Problème :** `sources/zIgzAg/sql/SessionSQL.java` expose plusieurs méthodes (`champsTraduction1()` à `champsTraduction4()`) qui construisent des fragments SQL par concaténation directe de valeurs sans échappement. Ces méthodes sont appelées depuis `ReceptionOrdres` pour construire des `INSERT`/`UPDATE` à partir des paramètres d'ordres joueurs.

```java
// SessionSQL.java - champsTraduction3()
retour.append(v[i]);  // v[i] non échappé, concaténé directement dans le SQL
```

**Action :**
1. Dans `sources/zIgzAg/sql/SessionSQL.java` : supprimer les méthodes `champsTraduction1/2/3/4()` qui construisent du SQL par concaténation
2. Les remplacer par des méthodes retournant des `PreparedStatement` avec paramètres liés via `setString()`, `setInt()`, etc.
3. Dans `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` : adapter tous les appels pour passer à des prepared statements
4. Dans `sources/zIgzAg/sql/SessionSQL.java` : la méthode `update(Statement s, String commande)` doit elle aussi basculer vers `PreparedStatement`

**Fichiers concernés :** `sources/zIgzAg/sql/SessionSQL.java`, `sources/zIgzAg/sql/SessionMysql.java`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`

---

### P1-08 — [JAVA] Injection de commande via `ProcessBuilder` dans `Start.java`

**Problème :** `sources/Start.java::upload()` construit une commande `scp` en passant directement des valeurs non validées issues de `config.properties` : `Const.SSH_PORT`, `Const.SSH_BASE_PATH + remotePath`. Si ces valeurs contiennent des caractères shell spéciaux, cela peut permettre l'exécution de commandes arbitraires sur le serveur.

```java
// Start.java:198-214
ProcessBuilder pb = new ProcessBuilder("scp", "-r", "-P",
    Const.SSH_PORT,                       // non validé
    localPath,                            // non validé
    Const.SSH_BASE_PATH + remotePath);    // concaténation non validée
```

**Action :**
1. Dans `sources/Start.java::upload()` : valider `Const.SSH_PORT` est bien un entier (`Integer.parseInt()` dans un bloc try/catch avant usage)
2. Valider que `localPath` et `remotePath` ne contiennent que des caractères alphanumériques, slashes et points (regex `[a-zA-Z0-9_./-]+`)
3. Dans `sources/zIgzAg/jeu/oceane/Const.java` : lors du chargement de `config.properties`, valider le format de `SSH_PORT` et `SSH_BASE_PATH` et lever une exception explicite si invalide
4. Documenter clairement que `IS_LOCAL=true` (mode local) ne passe jamais par ce code path

**Fichiers concernés :** `sources/Start.java`, `sources/zIgzAg/jeu/oceane/Const.java`

---

### P1-06 — [FRONT] Supprimer le pseudo MJ hardcodé dans le code source

**Problème :** Dans `php/register.php`, la vérification d'inscription passe par `if(strtolower($_POST['mj'])!='myst')`. Le pseudo du MJ ("myst") est exposé en clair dans le code source versionné — n'importe qui lisant le dépôt peut le voir et contourner la vérification.

**Action :**
1. Déplacer le secret MJ dans `config.properties` et `php/secure/connect.txt` (déjà ignorés par git) : `MJ_SECRET=myst`
2. Dans `php/register.php`, lire la valeur depuis une variable d'environnement ou un fichier de config (`getenv('MJ_SECRET')` ou une constante chargée par `connect.txt`)
3. Utiliser `hash_equals()` pour la comparaison afin d'éviter les timing attacks

**Fichiers concernés :** `php/register.php`, `config.properties.sample`, `php/secure/connect.txt.sample`

---

## P2 — Stabilité, persistance et accessibilité critique

### P2-01 — Corriger le double appel à `addNewGalaxy 0` dans `init.sh`

**Problème :** `Start.initUnivers()` (ligne 59 de `sources/Start.java`) appelle déjà `addNewGalaxy("0")` en interne. Le script `scripts/init.sh` appelle ensuite `java -cp sheril.jar Start addNewGalaxy 0` une seconde fois, ce qui réinitialise la galaxie 0 en écrasant la première.

**Action :** Supprimer la dernière ligne `java -cp sheril.jar Start addNewGalaxy 0` de `scripts/init.sh`.

**Fichiers concernés :** `scripts/init.sh`

---

### P2-02 — Sauvegardes atomiques pour les fichiers sérialisés Java

**Problème :** Si le processus Java crashe en milieu de sauvegarde (ex. pendant `univers.sauvegarder()`), le fichier `comm.txt` peut être partiellement écrit et l'univers devient illisible sans récupération manuelle.

**Action :**
1. Localiser dans `sources/zIgzAg/jeu/oceane/Univers.java` la méthode `sauvegarder()` et identifier tous les appels à `ObjectOutputStream`
2. Remplacer le pattern `new FileOutputStream(chemin)` par une écriture dans un fichier temporaire (`chemin + ".tmp"`) suivi d'un `Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)`
3. Appliquer le même pattern à toutes les méthodes de sauvegarde dans `Chemin.java` / `Univers.java`

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Chemin.java`

---

### P2-03 — Ajouter un mécanisme de backup par tour

**Problème :** Aucun backup automatique avant le passage de tour. En cas de bug dans `DeroulementDuTour`, les données du tour précédent sont écrasées sans possibilité de rollback.

**Action :**
1. Dans `sources/Start.java::newRound()`, avant d'appeler `DeroulementDuTour.main()`, copier récursivement le répertoire `data/tourN/donnees/` vers `data/tourN/donnees_backup/` avec `java.nio.file.Files.copy()`
2. En cas d'exception dans `DeroulementDuTour`, logger clairement le chemin du backup pour permettre une récupération manuelle

**Fichiers concernés :** `sources/Start.java`

---

### P2-23 — [PERF] Éliminer les fuites mémoire structurelles sur plusieurs tours

**Problème :** Trois collections Java croissent sans mécanisme de nettoyage à travers les tours, causant une fuite mémoire qui s'aggrave au fil du temps :
1. **`DEBRIS`** (`Univers.java:50`) : chaque combat crée des débris spatiaux ajoutés à la collection mais jamais purgés → après 100 tours avec des combats réguliers, la collection peut contenir des dizaines de milliers d'objets
2. **`RAPPORTS_COMBAT`** (`Univers.java:82`) : les rapports de combat de chaque tour s'accumulent indéfiniment en mémoire
3. **`pointDeVictoireHistory`** (`Commandant.java:110`) : chaque joueur accumule un historique de PV sur tous les tours passés — pour N joueurs × T tours = potentiellement des centaines de milliers d'entrées

Estimation : sur 100 tours avec 50 joueurs, la mémoire supplémentaire due à ces fuites peut atteindre plusieurs centaines de MB.

**Action :**
1. **DEBRIS** : dans `Univers.java::sauvegarder()`, après sérialisation, purger les débris de plus de 3 tours d'ancienneté (vérifier si un champ `tourCreation` existe sur `Debris`, sinon l'ajouter) : `DEBRIS.entrySet().removeIf(e -> e.getValue().getTourCreation() < Univers.getTour() - 3)`
2. **RAPPORTS_COMBAT** : ces rapports ne sont nécessaires que pour la génération du rapport du tour courant — vider la collection après `Rapport.ecrireMessagesSystemes()` dans `DeroulementDuTour.java` : `Univers.viderRapportsCombat()`
3. **pointDeVictoireHistory** : archiver les entrées de plus de 10 tours dans un fichier CSV plutôt qu'en mémoire, conserver uniquement les 10 derniers tours en mémoire
4. Ajouter dans `scripts/` un script de surveillance mémoire : `docker stats sheril-engine-1 --no-stream` — logger l'usage mémoire avant et après chaque tour pour détecter les dérives

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Commandant.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`

---

### P2-18 — [DBA] Migrer toutes les tables de MyISAM vers InnoDB

**Problème :** Plus de 100 tables dans `divers/base_sheril.sql` utilisent le moteur `MyISAM` (ex. lignes 7, 17, 51, 61, 82, 91 du schéma). MyISAM ne supporte ni les transactions, ni les clés étrangères, ni les verrous au niveau des lignes. Pour un jeu multijoueur où plusieurs ordres modifient la base simultanément et où le passage de tour doit être atomique, c'est un risque de corruption de données critique. Seule la table `_post` du forum utilise InnoDB.

**Action :**
1. Générer le script de migration : `SELECT CONCAT('ALTER TABLE ', table_name, ' ENGINE=InnoDB;') FROM information_schema.tables WHERE table_schema='sheril' AND engine='MyISAM';`
2. Exécuter ce script sur la base en production après un backup complet (P2-21)
3. Dans `divers/base_sheril.sql` : remplacer tous les `ENGINE=MyISAM` par `ENGINE=InnoDB` pour que les futures installations partent directement sur InnoDB
4. Vérifier après migration que les tables qui utilisaient des fonctionnalités MyISAM-only (FULLTEXT search) fonctionnent toujours — MySQL 5.6+ et MySQL 8 supportent FULLTEXT sur InnoDB
5. Tester le passage de tour complet après migration

**Fichiers concernés :** `divers/base_sheril.sql`

---

### P2-19 — [DBA] Entourer `deroulementOrdres()` dans une transaction MySQL

**Problème :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java::deroulementOrdres()` traite les 62 types d'ordres en boucle sur la base MySQL sans transaction. Si le processus crashe à mi-parcours (ordre 30 sur 62), les 29 premiers ordres sont appliqués de façon permanente alors que les 33 suivants ne le sont pas — l'univers est dans un état incohérent impossible à distinguer d'un état valide.

**Action :**
1. Dans `ReceptionOrdres.java::deroulementOrdres()` : ajouter `connection.setAutoCommit(false)` en début de méthode
2. Entourer la boucle de traitement dans un `try { ... connection.commit(); } catch (Exception e) { connection.rollback(); throw new SherilTourException("Rollback effectué", e); } finally { connection.setAutoCommit(true); }`
3. Prérequis : P2-18 (InnoDB) — `rollback()` n'a aucun effet sur des tables MyISAM
4. Dans `Start.java::newRound()` : attraper `SherilTourException`, logger l'erreur avec le numéro de tour et ne pas incrémenter le compteur de tour si la transaction a échoué
5. Tester le rollback en simulant une exception artificielle au milieu de la boucle

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, `sources/Start.java`, dépend de P2-18

---

### P2-20 — [DBA] Corriger l'incohérence `_statistiques` vs `statistiques` dans les requêtes

**Problème :** Le schéma MySQL (`divers/base_sheril.sql` ligne ~1119) définit une table nommée `statistiques` (sans underscore). Or, `php/stats_general.php` référence `_statistiques` (avec underscore) dans ses requêtes SQL. Cette incohérence cause des erreurs `Table '_statistiques' doesn't exist` silencieuses ou une page de stats vide, selon la gestion d'erreur en place.

**Action :**
1. Vérifier le nom réel en base : `SHOW TABLES LIKE '%statistiques%'`
2. Choisir un nom canonique (`statistiques` sans underscore pour cohérence avec les autres tables non-forum) et l'appliquer partout
3. Dans `php/stats_general.php` : corriger toutes les occurrences de `_statistiques` par le nom correct
4. Dans `divers/base_sheril.sql` : s'assurer que la définition de la table correspond au nom utilisé dans toutes les requêtes PHP et Java
5. Grep dans tout `php/` et `sources/` pour trouver d'autres références au nom incorrect : `grep -r "statistiques" php/ sources/ --include="*.php" --include="*.java"`

**Fichiers concernés :** `php/stats_general.php`, `php/stats_detail.php`, `divers/base_sheril.sql`

---

### P2-21 — [DEVOPS] Mettre en place des backups automatisés MySQL et données Java

**Problème :** Il n'existe aucun mécanisme de backup automatique — ni pour la base MySQL (ordres, joueurs, forum), ni pour les fichiers sérialisés Java dans `data/` (état de l'univers, flottes, commandants). En cas de crash disque, corruption ou erreur lors du passage de tour, toutes les données de la partie en cours sont perdues définitivement. Le RTO et RPO actuels sont infinis.

**Action :**
1. Créer `scripts/backup.sh` :
   ```bash
   #!/bin/bash
   set -euo pipefail
   BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
   mkdir -p "$BACKUP_DIR"
   # Backup MySQL
   docker compose exec -T db mysqldump -u user -ppassword sheril | gzip > "$BACKUP_DIR/mysql.sql.gz"
   # Backup données Java
   tar -czf "$BACKUP_DIR/gamedata.tar.gz" ./data/
   # Rotation: garder 30 jours
   find ./backups -maxdepth 1 -type d -mtime +30 -exec rm -rf {} +
   echo "Backup done: $BACKUP_DIR"
   ```
2. Planifier ce script via `cron` sur l'hôte (ex. `0 3 * * * /chemin/scripts/backup.sh >> /var/log/sheril-backup.log 2>&1`)
3. Créer `scripts/restore.sh` avec la procédure inverse documentée et testée
4. Tester la restauration au moins une fois avant de considérer le backup comme fiable
5. Option hors-site : ajouter `rsync -avz ./backups/ backup@remote:/backups/sheril/` ou upload S3 à la fin du script

**Fichiers concernés :** Créer `scripts/backup.sh`, `scripts/restore.sh`

---

### P2-22 — [DEVOPS] Corriger l'utilisateur root dans le conteneur Docker

**Problème :** Le `Dockerfile` ne définit aucune directive `USER` — Apache et PHP tournent donc en tant que `root` dans le conteneur. Si un attaquant exploite une vulnérabilité applicative (RCE via upload, injection, etc.), il obtient un accès root au conteneur et potentiellement à l'hôte via les volumes montés (le répertoire du projet est monté dans `engine`).

**Action :**
1. Dans `Dockerfile` : après toutes les installations, ajouter :
   ```dockerfile
   RUN chown -R www-data:www-data /var/www/html
   USER www-data
   ```
2. Dans `docker-compose.yml`, pour le service `engine` (Java) : ajouter `user: "1001:1001"` et s'assurer que les répertoires `data/`, `logs/` sont accessibles à cet utilisateur
3. Tester que l'application fonctionne toujours en utilisateur non-root (permissions sur `php/live/`, `data/`, logs Apache)
4. Pour le conteneur `db` (MySQL) : l'image officielle MySQL gère déjà l'utilisateur `mysql`, aucun changement nécessaire

**Fichiers concernés :** `Dockerfile`, `docker-compose.yml`

---

### P2-08 — [JAVA] Corriger les fuites de ressources — généraliser `try-with-resources`

**Problème :** De nombreux endroits du code Java ouvrent des flux (`ObjectInputStream`, `FileInputStream`, `ObjectOutputStream`, `Scanner`, `BufferedWriter`) sans garantir leur fermeture en cas d'exception. Cela cause des fuites de descripteurs de fichiers qui peuvent bloquer le processus sur des systèmes avec peu de ressources.

Exemples critiques :
- `sources/zIgzAg/jeu/oceane/Univers.java` méthode `chargerMap()` : `ObjectInputStream` + `FileInputStream` ouverts, le `finally { ois.close() }` ne ferme pas le `FileInputStream` sous-jacent
- `sources/Start.java::getRemoteFileContent()` : `Scanner` ouvert sur `conn.getInputStream()` sans fermeture garantie
- `sources/zIgzAg/jeu/oceane/Combat.java` : `BufferedWriter writer` statique jamais fermé (voir P2-09)
- `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` : `Connection` JDBC ouverte dans le constructeur, jamais fermée (voir P2-10)

**Action :**
1. Grep toutes les occurrences de `new FileInputStream`, `new ObjectInputStream`, `new ObjectOutputStream`, `new Scanner`, `new BufferedWriter`, `new FileWriter` dans `sources/`
2. Entourer chaque instanciation avec `try (Resource r = new Resource(...)) { ... }` — le `try-with-resources` ferme automatiquement la ressource même en cas d'exception
3. Pour `Univers.java::chargerMap()` spécifiquement : imbriquer les deux streams dans le même `try` : `try (var ois = new ObjectInputStream(new FileInputStream(fiche))) { ... }`
4. Pour `Univers.java::sauvegarderMap()` : même pattern avec `ObjectOutputStream`
5. Pour `Start.java::getRemoteFileContent()` : `try (var scanner = new Scanner(conn.getInputStream())) { ... }`

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/Start.java`, `sources/zIgzAg/jeu/oceane/Combat.java`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, `sources/zIgzAg/sql/SessionSQL.java`

---

### P2-09 — [JAVA] Fermer le `BufferedWriter` statique de `Combat.java`

**Problème :** `sources/zIgzAg/jeu/oceane/Combat.java` maintient un `private static BufferedWriter writer` initialisé à la première entrée dans `log()` mais jamais fermé. Le fichier de log reste ouvert pendant toute la durée du processus Java. Sur une longue partie (beaucoup de tours), cela consomme un descripteur de fichier en permanence et les dernières lignes non flushées peuvent être perdues si le JVM crashe.

```java
// Combat.java
private static BufferedWriter writer;  // jamais fermé
private static void log(...) {
    if (writer == null) {
        writer = new BufferedWriter(new FileWriter(file, true));
    }
    writer.write(...);
    // pas de flush() systématique non plus
}
```

**Action :**
1. Ajouter une méthode statique `public static void fermerLog()` dans `Combat.java` qui appelle `writer.flush()` puis `writer.close()` et remet `writer = null`
2. Appeler `Combat.fermerLog()` dans `DeroulementDuTour.java` en fin de méthode `main()`, dans un bloc `finally` pour garantir l'exécution même en cas d'exception
3. Alternativement, remplacer le `BufferedWriter` statique par un appel SLF4J (prérequis : P3-05) avec un `FileAppender` configuré par tour dans `logback.xml`
4. Ajouter `writer.flush()` après chaque appel `writer.write()` pour s'assurer que les logs sont écrits immédiatement

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Combat.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`

---

### P2-10 — [JAVA] Fermer la connexion MySQL dans `ReceptionOrdres`

**Problème :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` ouvre une connexion MySQL dans son constructeur (`connection = mySQL.getConnection(...)`) mais ne la ferme jamais — ni dans le flux normal, ni en cas d'exception. Si `ReceptionOrdres` est instancié plusieurs fois (ou si le processus Java reste longtemps actif), les connexions s'accumulent côté MySQL jusqu'au `Too many connections`.

**Action :**
1. Faire implémenter `AutoCloseable` à `ReceptionOrdres` : `public class ReceptionOrdres implements AutoCloseable`
2. Implémenter `public void close() throws Exception { if (connection != null && !connection.isClosed()) connection.close(); }`
3. Dans `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java` : utiliser `try (ReceptionOrdres ro = new ReceptionOrdres()) { ro.deroulementOrdres(); }` pour fermeture automatique
4. Faire de même pour la `Connection` dans `sources/zIgzAg/sql/SessionMysql.java` : retourner la connexion dans un objet wrapper `AutoCloseable` plutôt qu'un `Connection` brut

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`, `sources/zIgzAg/sql/SessionMysql.java`

---

### P2-11 — [JAVA] Corriger les NPE critiques non protégées

**Problème :** Plusieurs NPE certaines ou probables identifiées dans des chemins d'exécution courants :

1. **`Univers.java::escape(null)`** : la méthode `escape(String text)` appelle `text.replace(...)` sans null check. Elle est appelée pour formater les messages Discord — si un nom de commandant est null, le processus du tour crashe.
2. **`Univers.java::notify()`** : `response.split("\"id\":\"")[1]` lève `ArrayIndexOutOfBoundsException` si la réponse Discord n'a pas le format attendu (réseau lent, rate limit, API Discord changée).
3. **`Planete.java`** : les champs `ArrayList populations` et `ArrayList batiments` ne sont pas initialisés dans le constructeur — tout appel à `populations.size()` ou `batiments.add()` avant initialisation explicite lève une NPE.
4. **`Position.java`** : la classe est utilisée comme clé dans des `TreeMap` mais son `compareTo()` doit être cohérent avec `equals()` — si `equals()` n'est pas surchargé, deux `Position` identiques dans des `TreeMap` différentes peuvent ne pas être trouvées.

**Action :**
1. Dans `Univers.java::escape()` : ajouter `if (text == null) return ""` en première ligne
2. Dans `Univers.java::notify()` : entourer le parsing de la réponse Discord dans un try/catch `Exception` avec un fallback (`messageId = null`) au lieu de laisser crasher
3. Dans `Planete.java` constructeur : initialiser `populations = new ArrayList()` et `batiments = new ArrayList()`
4. Dans `Vaisseau.java` constructeur : initialiser `dommages = new HashMap<>()` et `cargaison = new HashMap<>()`
5. Dans `Position.java` : vérifier que `equals()` et `hashCode()` sont correctement surchargés (comparaison sur `galaxie` + `pos[0]` + `pos[1]`) et que `compareTo()` est cohérent avec `equals()`

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Planete.java`, `sources/zIgzAg/jeu/oceane/Vaisseau.java`, `sources/zIgzAg/jeu/oceane/Position.java`

---

### P2-15 — [UX] Protéger les actions destructives contre les suppressions accidentelles

**Problème :** Dans `php/ordres/division.php`, la suppression d'une division de flotte se fait via un lien GET :
```php
// division.php:49-53
if (isset($_GET['elimine']) && $_GET['elimine'] == 0) {
    mysql($base, "DELETE FROM diviser_flotte WHERE id = {$_GET['identifier']} AND NUMERO=$commandant");
}
```
Un clic accidentel, un préchargement par le navigateur, ou un lien partagé peut supprimer une division irrémédiablement sans aucune confirmation. Le joueur perd ses ordres stratégiques sans avertissement ni possibilité de récupération.

**Action :**
1. Remplacer le lien GET par un formulaire POST avec bouton de confirmation :
   ```html
   <form method="POST" action="division.php"
         onsubmit="return confirm('Supprimer cette division ? Cette action est irréversible.')">
     <input type="hidden" name="action" value="supprimer_division">
     <input type="hidden" name="identifier" value="<?= intval($id) ?>">
     <button type="submit" class="btn-danger">Supprimer</button>
   </form>
   ```
2. Côté PHP : ne traiter la suppression que sur `$_SERVER['REQUEST_METHOD'] === 'POST'` et avec vérification CSRF (P1-03)
3. Après suppression réussie, afficher un message de confirmation : `<div class="alert-success">Division supprimée.</div>` avec un lien de retour
4. Appliquer le même principe à toute autre action destructive détectée dans `php/ordres/` (grep pour `DELETE` et `elimine` dans les paramètres GET)

**Fichiers concernés :** `php/ordres/division.php`, autres fichiers `php/ordres/` avec liens de suppression

---

### P2-16 — [UX] Ajouter du feedback après la soumission des ordres

**Problème :** Après avoir soumis un formulaire d'ordre (construire, déplacer une flotte, passer une technologie, etc.), aucun message de succès ou d'erreur n'est affiché. L'utilisateur ne sait pas si son ordre a été enregistré. Il doit naviguer vers "Liste des ordres" pour vérifier — ce qui est non-intuitif et anxiogène, surtout pour un jeu au tour par tour où chaque ordre compte.

**Action :**
1. Dans le mécanisme de traitement des ordres (le script qui reçoit le POST de chaque formulaire d'ordre) : stocker le résultat dans la session PHP avant redirection :
   ```php
   // Après INSERT réussi
   $_SESSION['flash_success'] = "Ordre enregistré : construction de 3 bases minières en système Alpha-7.";
   header("Location: ordres.php?table=" . $_GET['table']);
   exit;
   ```
2. Dans le template de la console d'ordres (`ordres.php` après P1-12) : afficher et purger le message flash en début de page :
   ```php
   if (!empty($_SESSION['flash_success'])) {
       echo '<div class="alert alert-success">' . htmlspecialchars($_SESSION['flash_success']) . '</div>';
       unset($_SESSION['flash_success']);
   }
   ```
3. En cas d'erreur de validation, stocker `$_SESSION['flash_error']` avec un message explicite et afficher en rouge
4. Ajouter un lien "Voir tous mes ordres de ce tour" dans le message de succès pour faciliter la vérification

**Fichiers concernés :** Tous les scripts de traitement d'ordres dans `php/ordres/`, `php/ordres/ordres.php` (après P1-12)

---

### P2-17 — [UX] Gérer les états vides dans les formulaires d'ordres

**Problème :** Les formulaires d'ordres (`php/ordres/fr/choix/*.txt`) utilisent des `<select>` peuplés dynamiquement avec les systèmes, flottes ou technologies du joueur. Si un joueur n'a aucun système habitable, aucune flotte, ou n'a pas encore les prérequis technologiques, le `<select>` apparaît vide ou avec une option générique sans explication. L'utilisateur ne comprend pas pourquoi il ne peut pas passer l'ordre.

**Action :**
1. Dans chaque script qui peuple un `<select>` d'ordre : vérifier si la liste est vide avant de rendre le formulaire et afficher à la place un message contextuel :
   ```php
   if (empty($t0)) {
       echo '<div class="alert alert-info">
         Vous ne possédez aucun système pour cet ordre.
         <a href="/presentation.php#systemes">En savoir plus</a>
       </div>';
       return; // ne pas afficher le formulaire
   }
   ```
2. Messages à personnaliser par type d'ordre :
   - Construire : "Vous ne possédez aucun système. Conquérez des systèmes d'abord."
   - Déplacer flotte : "Vous n'avez aucune flotte. Construisez des vaisseaux d'abord."
   - Technologie : "Aucune technologie disponible — vérifiez vos prérequis."
3. Pour les `<select>` avec des options : toujours ajouter une option vide en tête `<option value="">-- Choisir --</option>` et l'attribut `required` pour forcer la sélection
4. Appliquer à tous les fichiers dans `php/ordres/fr/choix/`

**Fichiers concernés :** Tous les fichiers `php/ordres/fr/choix/*.txt` (à convertir en `.php`)

---

### P2-12 — [SÉCU] Ajouter les headers HTTP de sécurité

**Problème :** Aucun header de sécurité n'est envoyé par le serveur Apache/PHP. Un attaquant peut :
- Intégrer le site dans une iframe pour du clickjacking (`X-Frame-Options` absent)
- Injecter du contenu via MIME sniffing (`X-Content-Type-Options` absent)
- Forcer HTTP au lieu de HTTPS (`Strict-Transport-Security` absent)
- Exécuter des scripts inline ou depuis des CDN non autorisés (`Content-Security-Policy` absent)

**Action :**
1. Créer ou modifier `php/.htaccess` pour ajouter les headers Apache :
   ```apache
   Header always set X-Frame-Options "DENY"
   Header always set X-Content-Type-Options "nosniff"
   Header always set Referrer-Policy "no-referrer-when-downgrade"
   Header always set Strict-Transport-Security "max-age=31536000; includeSubDomains"
   Header always set Content-Security-Policy "default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' https://fonts.googleapis.com https://cdn.jsdelivr.net; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:"
   ```
2. La CSP doit autoriser `cdn.jsdelivr.net` (Quill), `fonts.googleapis.com` (Roboto) — lister tous les CDN utilisés
3. Activer `mod_headers` dans le `Dockerfile` : `RUN a2enmod headers`
4. En PHP comme fallback, ajouter les headers en début de `php/includes/top.php` avant tout output HTML :
   ```php
   header("X-Frame-Options: DENY");
   header("X-Content-Type-Options: nosniff");
   ```
5. Tester avec https://securityheaders.com une fois déployé

**Fichiers concernés :** `php/.htaccess`, `Dockerfile`, `php/includes/top.php`

---

### P2-13 — [SÉCU] Sécuriser les cookies de session PHP

**Problème :** Les cookies de session PHP sont émis sans les flags de sécurité modernes, ce qui les rend vulnérables au vol et à l'usage cross-site :
- Pas de flag `HttpOnly` → le JavaScript peut lire le cookie (combiné avec le XSS stored du forum, cela permet le vol de session)
- Pas de flag `Secure` → le cookie est transmis en HTTP en clair
- Pas de `SameSite=Strict` → le cookie est envoyé lors de requêtes cross-site (facilite le CSRF)

**Action :**
1. En tête de `php/includes/top.php`, avant `session_start()`, configurer les paramètres de session :
   ```php
   ini_set('session.cookie_httponly', 1);
   ini_set('session.cookie_secure', 1);     // à activer une fois HTTPS en place
   ini_set('session.cookie_samesite', 'Strict');
   ini_set('session.use_strict_mode', 1);   // rejette les ID de session non initialisés par le serveur
   ini_set('session.gc_maxlifetime', 3600); // expiration 1h
   ```
2. Ces paramètres peuvent aussi être définis dans `php.ini` via le `Dockerfile` pour s'appliquer globalement
3. Vérifier avec les outils de développement du navigateur que le cookie `PHPSESSID` porte bien les flags `HttpOnly`, `Secure`, `SameSite=Strict`

**Fichiers concernés :** `php/includes/top.php`, `Dockerfile` (ajout `php.ini`)

---

### P2-14 — [SÉCU] Désactiver `display_errors` en production et protéger les logs

**Problème :** `php/includes/top.php` et plusieurs fichiers dans `php/ordres/` (ex. `technology_plan.php`) appellent explicitement `ini_set('display_errors', 1)` et `error_reporting(E_ALL)`. En production, toute erreur PHP (connexion BDD échouée, fichier manquant) affiche une stack trace complète incluant les chemins du serveur, les variables internes et parfois des credentials.

**Action :**
1. Supprimer ou conditionner les appels `ini_set('display_errors', 1)` dans `php/includes/top.php` et dans tous les fichiers d'ordres — grep pour `display_errors`
2. Remplacer par :
   ```php
   ini_set('display_errors', 0);
   ini_set('log_errors', 1);
   ini_set('error_log', '/var/log/php/sheril-errors.log');
   ```
3. Créer une constante `APP_DEBUG` chargée depuis `config.properties` ou une variable d'environnement Docker (`APP_ENV=production`) — n'afficher les erreurs que si `APP_DEBUG === true`
4. Dans le `Dockerfile` : créer le répertoire `/var/log/php/` et s'assurer que l'utilisateur Apache peut y écrire
5. Ajouter un handler PHP global avec `set_error_handler()` et `set_exception_handler()` qui logge l'erreur et affiche un message générique à l'utilisateur

**Fichiers concernés :** `php/includes/top.php`, `php/ordres/technology_plan.php`, tous les fichiers PHP avec `display_errors`, `Dockerfile`

---

### P2-05 — [FRONT] Retirer `user-scalable=no` de la balise viewport

**Problème :** Tous les templates HTML (dont `php/includes/top.php` et les pages de races autonomes `php/races/*.php`) contiennent `<meta name="viewport" content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">`. Cela empêche les utilisateurs malvoyants de zoomer. C'est une violation WCAG 2.1 critère 1.4.4 (Resize Text, Level AA).

**Action :**
1. Dans `php/includes/top.php` : remplacer la balise viewport par `<meta name="viewport" content="width=device-width, initial-scale=1.0">`
2. Faire de même dans `php/races/fremen.php`, `php/races/atalante.php`, `php/races/yoksor.php`, `php/races/fergok.php`, `php/races/zwaia.php` — chaque page autonome duplique ce meta tag
3. Vérifier que la suppression du `maximum-scale` ne casse pas de mise en page (c'est attendu : les layouts CSS doivent gérer le zoom correctement)

**Fichiers concernés :** `php/includes/top.php`, `php/races/*.php`

---

### P2-06 — [FRONT] Corriger la suppression du focus outline

**Problème :** `php/assets/css/styles.sass` contient `*:focus { outline: none }` sans substitut systématique pour tous les éléments. Les utilisateurs naviguant au clavier ne voient pas quel élément est focalisé. Violation WCAG 2.1 critère 2.4.7 (Focus Visible, Level AA).

**Action :**
1. Dans `php/assets/css/styles.sass` : remplacer `*:focus { outline: none }` par :
   ```css
   *:focus { outline: none }  /* supprimer uniquement pour souris */
   *:focus-visible {
     outline: 2px solid var(--accent);
     outline-offset: 2px;
   }
   ```
   La pseudo-classe `:focus-visible` n'affiche l'outline que lors de la navigation clavier, pas lors des clics souris.
2. Vérifier que les champs de formulaire (`input`, `select`, `textarea`) conservent leur `box-shadow` de focus déjà défini pour ne pas doubler l'effet
3. Recompiler le SASS vers `styles.css`

**Fichiers concernés :** `php/assets/css/styles.sass`, `php/assets/css/styles.css` (recompiler)

---

### P2-07 — [FRONT] Corriger les rapports de contraste insuffisants

**Problème :** Plusieurs combinaisons couleur/fond ne respectent pas le ratio minimum WCAG 1.4.3 (4.5:1 pour le texte normal) : la couleur `#604A7F` (violet) sur fond `#333` atteint ~2.5:1 dans `style-sheril.css`, et les liens de déconnexion/connexion en `#ccc` sur fond sombre dans le header sont proches de la limite.

**Action :**
1. Auditer toutes les couleurs de texte dans `styles.sass` et `style-sheril.css` avec un outil de contraste (ex. https://webaim.org/resources/contrastchecker/)
2. Dans `php/includes/top.php`, les liens du header `style="color: #ccc;"` : remplacer par une classe CSS et ajuster la couleur pour atteindre au minimum 4.5:1
3. Dans `styles.sass` : remplacer les couleurs de texte insuffisantes — en particulier `.post-date { color: #aaa }` sur fond sombre qui atteint ~3.5:1
4. Les couleurs des races (`.race0` à `.race5`) utilisées sur fond sombre dans les tableaux : vérifier et ajuster si nécessaire
5. Recompiler le SASS

**Fichiers concernés :** `php/assets/css/styles.sass`, `php/includes/top.php`, `php/assets/css/style-sheril.css`

---

### P2-04 — Corriger la gestion du `serialVersionUID` dans les classes sérialisées

**Problème :** Plusieurs classes ont un `serialVersionUID` fixe mais leurs champs peuvent évoluer sans mise à jour du UID, causant des `InvalidClassException` silencieuses au chargement.

**Action :**
1. Lister toutes les classes implémentant `Serializable` dans `sources/zIgzAg/jeu/oceane/` (grep pour `implements Serializable`)
2. Pour chaque classe, vérifier que le `serialVersionUID` est déclaré explicitement
3. Documenter dans un commentaire les champs `transient` (non sérialisés) pour que les futurs mainteneurs comprennent pourquoi ils sont exclus
4. Ajouter un test de désérialisation dans `scripts/` qui vérifie qu'un fichier `comm.txt` existant peut être chargé sans erreur après un changement de code

**Fichiers concernés :** Toutes les classes dans `sources/zIgzAg/jeu/oceane/` implémentant `Serializable`

---

## P3 — Maintenabilité et qualité du code Java

### P3-01 — Typer les collections raw en collections génériques

**Problème :** Des centaines de `TreeMap`, `ArrayList`, `HashMap` sans paramètre générique produisent des warnings "unchecked" et cachent des `ClassCastException` potentielles.

**Action :**
1. Dans `sources/zIgzAg/jeu/oceane/Univers.java` : typer tous les champs statiques (`private static TreeMap<Integer, Commandant> COMMANDANTS`, `private static TreeMap<Position, Systeme> SYSTEMES`, etc.)
2. Propager les types aux signatures de méthodes publiques (ex. `getListeCommandants()` retourne `Collection<Commandant>`)
3. Faire de même pour `Commandant.java` (champs `domaine`, `flottes`, `recherches`, etc.)
4. Compiler et corriger tous les casts explicites devenus inutiles

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Commandant.java`, et toutes les classes avec collections

---

### P3-02 — Remplacer le dispatch par réflexion des ordres par un Map de handlers

**Problème :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` construit dynamiquement le nom de méthode (`"ordre_" + typeOrdre`) et l'invoque par réflexion. Les 62 ordres sont invisibles au compilateur — un rename de méthode casse silencieusement un ordre en production.

**Action :**
1. Créer une interface `OrdreHandler` avec une méthode `void executer(Commandant commandant, String[] parametres)`
2. Créer une classe par ordre (ou les regrouper par thème dans des classes internes) implémentant `OrdreHandler`
3. Dans `ReceptionOrdres`, construire une `Map<String, OrdreHandler>` initialisée dans le constructeur avec les 62 entrées
4. Remplacer l'invocation par réflexion par `handlers.get(typeOrdre).executer(commandant, parametres)`
5. Gérer le cas `null` (ordre inconnu) avec un log explicite

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, créer des classes dans un sous-package `ordres/`

---

### P3-03 — Extraire la God Class `Univers` en contexte injecté

**Problème :** `Univers` est une classe avec 15+ champs statiques globaux. Tout le moteur en dépend directement. Impossible à tester ou à isoler.

**Action :**
1. Créer une classe `ContexteJeu` (non-statique) contenant toutes les collections actuellement statiques de `Univers`
2. Faire passer `ContexteJeu` en paramètre aux méthodes principales de `DeroulementDuTour`, `ReceptionOrdres`, `Combat`, `Rapport`
3. Conserver des méthodes statiques dans `Univers` comme façade vers une instance singleton `ContexteJeu` — cela permet une migration progressive sans tout casser d'un coup
4. Marquer les méthodes statiques directes comme `@Deprecated` au fur et à mesure qu'elles sont remplacées

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, `sources/zIgzAg/jeu/oceane/Combat.java`

---

### P3-04 — Adopter un système de build Maven ou Gradle

**Problème :** `scripts/create-jar.sh` est un script bash manuel. Aucune gestion de dépendances, aucun test automatisé, pas de build reproductible.

**Action :**
1. Créer un `pom.xml` (Maven) à la racine avec `groupId=zigzag`, `artifactId=sheril`, `version=1.0`
2. Déclarer les dépendances : driver MySQL (`com.mysql:mysql-connector-j:8.0.33`), JavaMail si utilisé
3. Configurer `maven-jar-plugin` avec `mainClass=Start`
4. Remplacer `scripts/create-jar.sh` par `mvn clean package`
5. Adapter `scripts/init.sh` pour utiliser `target/sheril.jar` au lieu de `sheril.jar`
6. Vérifier que le JAR tiers actuel (dans le repo) peut être supprimé au profit de la dépendance Maven

**Fichiers concernés :** Créer `pom.xml`, modifier `scripts/create-jar.sh`, `scripts/init.sh`, `docker-compose.yml` (commande de build engine)

---

### P3-05 — Remplacer `System.out.println` par un logger structuré

**Problème :** Tout le logging du moteur Java est fait via `System.out.println`. Pas de niveaux, pas de timestamps, pas de rotation de logs.

**Action :**
1. Ajouter la dépendance SLF4J + Logback dans `pom.xml`
2. Créer `src/main/resources/logback.xml` avec une configuration console + fichier rotatif dans `data/logs/`
3. Dans chaque classe du moteur, remplacer `System.out.println(...)` par `private static final Logger log = LoggerFactory.getLogger(NomClasse.class)` et les appels appropriés (`log.info(...)`, `log.error(...)`)
4. `Combat.logln()` peut rester en fichier séparé mais migrer vers SLF4J

**Fichiers concernés :** Tous les `.java` dans `sources/` — grep pour `System.out.println`

---

### P3-06 — Découpler la génération HTML du moteur Java

**Problème :** `sources/zIgzAg/jeu/oceane/Rapport.java` (~600 lignes) et `sources/zIgzAg/jeu/oceane/ProductionOrdres.java` (~500 lignes) construisent du HTML à la main via une classe `BaliseHTML`. Ce code est fragile et mélange logique métier et présentation.

**Action :**
1. Ajouter la dépendance Mustache.java (`com.github.spullara.mustache.java:compiler:0.9.10`) dans `pom.xml`
2. Créer un répertoire `src/main/resources/templates/` avec des fichiers `.mustache` pour chaque type de rapport (rapport commandant, stats, registre, etc.)
3. Réécrire `Rapport.java` pour alimenter un `Map<String, Object>` de données et le passer au moteur Mustache pour générer le HTML
4. Supprimer `sources/zIgzAg/html/BaliseHTML.java` une fois la migration terminée

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Rapport.java`, `sources/zIgzAg/jeu/oceane/ProductionOrdres.java`, `sources/zIgzAg/html/BaliseHTML.java`

---

### P3-27 — [UX] Créer un tableau de bord différencié connecté/non-connecté

**Problème :** `php/index.php` affiche le même contenu (une histoire de lore narrative) qu'on soit connecté ou non. Un joueur connecté voit la même page introductive qu'un visiteur anonyme, sans aucune information sur son état de jeu actuel (tour en cours, ordres passés, dernière connexion). Il doit cliquer sur "Console d'ordre" pour savoir ce qui se passe — sans indication de ce qu'il faut faire.

**Action :**
1. Dans `php/index.php` : détecter `$_SESSION['commandant_num']` et brancher vers deux vues distinctes
2. **Vue non-connecté** (actuelle) : garder le lore + ajouter des CTA clairs : "Se connecter" et "S'inscrire" en boutons primaires bien visibles, avec une accroche en 3 points sur le jeu
3. **Vue connecté** — nouveau "tableau de bord" :
   - Section "État du tour" : numéro du tour actuel, date du prochain tour estimée
   - Section "Mes ordres" : nombre d'ordres passés ce tour / maximum possible, lien direct vers la console
   - Section "Mes rapports" : lien de téléchargement du dernier rapport reçu
   - Section "Actualités" : 3 derniers posts du forum
4. Les données du tableau de bord sont lues depuis MySQL (ordres de ce tour pour ce commandant) et depuis `tour.txt` pour le numéro de tour
5. Sur mobile, les sections s'empilent verticalement en ordre de priorité

**Fichiers concernés :** `php/index.php`, `php/includes/top.php` (pour la détection de session), `php/assets/css/styles.sass`

---

### P3-28 — [UX] Ajouter un indicateur de page active et des breadcrumbs

**Problème :** La navigation principale (`php/includes/top.php`) ne donne aucune indication visuelle de la page actuellement visitée. Tous les liens ont le même style, qu'on soit sur la page en question ou non. Dans la console d'ordres (après migration depuis FRAMESET), aucun fil d'Ariane n'indique dans quelle section de la console on se trouve. Le joueur se perd facilement entre les 60+ types d'ordres disponibles.

**Action :**
1. Dans `php/includes/top.php` : passer `$currentPage` depuis chaque page (`$currentPage = 'ordres'`) et l'utiliser pour ajouter une classe `active` sur le lien correspondant :
   ```php
   $pages = ['/' => 'Accueil', '/ordres/ordres.php' => 'Console d\'ordre', ...];
   foreach ($pages as $url => $label) {
       $isActive = ($currentPage === $url) ? ' class="active"' : '';
       echo "<li><a href=\"$url\"$isActive>$label</a></li>";
   }
   ```
2. Dans le CSS : `.active { border-bottom: 2px solid var(--accent); font-weight: bold; }`
3. Dans la console d'ordres : afficher un breadcrumb contextuel selon l'ordre sélectionné :
   `Console d'ordres > Militaire > Déplacer une flotte`
4. Dans les pages de races (`php/races/`) : la nav entre races (`nav.php`) doit marquer la race courante comme active

**Fichiers concernés :** `php/includes/top.php`, `php/races/nav.php`, `php/assets/css/styles.sass`, toutes les pages PHP (ajout de `$currentPage`)

---

### P3-29 — [UX] Améliorer l'onboarding des nouveaux joueurs

**Problème :** Le parcours d'un nouveau joueur est : inscription → mail du MJ → connexion → page d'accueil narrative → débrouille totale. Aucune information sur les premières actions à effectuer, les règles de base, ou les ressources disponibles. La page `php/presentation.php` contient les règles mais n'est pas reliée au flux d'inscription. Un nouveau joueur qui ne trouve pas Discord risque d'abandonner au premier tour.

**Action :**
1. Dans `php/register.php` : avant le formulaire d'inscription, ajouter une section "Avant de commencer" avec les 5 races en miniature (nom + couleur + force principale) et 3 règles fondamentales en bullet points
2. Après inscription réussie : remplacer le simple `echo "Inscription réussie"` par une page de confirmation structurée :
   - Message de succès stylistiquement distinct
   - "Prochaines étapes" : (1) Attendre le mail du MJ, (2) Lire la présentation, (3) Rejoindre Discord
   - Liens directs vers `presentation.php` et Discord
3. Dans `php/index.php` (tableau de bord, P3-27) : pour un joueur au tour 0 ou 1, afficher un banner "Bienvenue ! Voici vos premières actions recommandées" avec 3 ordres prioritaires cliquables
4. Dans la console d'ordres : grouper les ordres du menu par catégorie (Économie, Militaire, Diplomatie, Recherche) plutôt qu'une liste plate alphabétique — cela aide à découvrir les ordres disponibles

**Fichiers concernés :** `php/register.php`, `php/index.php`, `php/ordres/menu.php` (après migration P1-12)

---

### P3-30 — [UX] Ajouter l'aide contextuelle inline dans les formulaires d'ordres

**Problème :** Chaque type d'ordre possède déjà un fichier d'aide (`php/ordres/fr/aide/[ordre].txt`) contenant une explication détaillée des paramètres et des effets de l'ordre. Cependant, cette aide n'est jamais affichée dans le formulaire lui-même — l'utilisateur doit aller dans "Liste des ordres", trouver l'ordre, et lire l'aide dans une vue séparée. Pendant la saisie du formulaire, il n'a aucune assistance.

**Action :**
1. Dans chaque formulaire d'ordre (`php/ordres/fr/choix/*.txt` → à migrer en `.php`) : inclure le fichier d'aide correspondant dans un bloc `<details>` collapsible :
   ```html
   <details class="order-help">
     <summary>? Aide sur cet ordre</summary>
     <div class="help-content">
       <?php include __DIR__ . "/../aide/" . $table . ".txt"; ?>
     </div>
   </details>
   ```
2. Par défaut, le bloc `<details>` est fermé pour ne pas surcharger l'interface — un joueur expérimenté peut ignorer l'aide
3. Pour les paramètres complexes (ex. types de constructions, directives de flottes) : ajouter des tooltips `title="..."` sur les options des `<select>` pour expliquer chaque valeur
4. Dans le CSS : styliser `.order-help` avec un fond légèrement contrasté et une icône d'aide (`?`) reconnaissable

**Fichiers concernés :** Tous les fichiers `php/ordres/fr/choix/`, `php/assets/css/styles.sass`

---

### P3-31 — [UX] Uniformiser la terminologie et le système de composants UI

**Problème :** Le projet utilise plusieurs termes pour le même concept selon les pages : "commandant" / "joueur" / "utilisateur", "ordre" / "action" / "directive", "système" / "empire". Les boutons d'action n'ont pas de style cohérent : certains sont des `<input type="submit">`, d'autres des `<button>`, d'autres des `<a>` stylisés. Les couleurs des races sont définies à trois endroits différents avec des valeurs différentes.

**Action :**
1. **Glossaire** : créer `php/docs/glossaire.md` listant les termes canoniques : "commandant" (jamais "joueur"), "ordre" (jamais "action" sauf dans le code interne), "système stellaire" (jamais "planète" seul pour désigner un système)
2. **Composants boutons** dans `styles.sass` :
   ```sass
   .btn                          // base commune
   .btn-primary                  // action principale (envoyer ordre)
   .btn-secondary                // action secondaire (annuler, retour)
   .btn-danger                   // action destructive (supprimer)
   .btn-sm .btn-lg               // variantes de taille
   ```
3. Remplacer tous les `<input type="submit">` par `<button type="submit" class="btn btn-primary">` dans les formulaires d'ordres
4. **Couleurs des races** : définir une seule source de vérité dans `styles.sass` via des variables CSS custom properties :
   ```sass
   :root
     --race-fremen: #CC00FF
     --race-atalante: #0066CC
     --race-zwaia: #FFCC00
     --race-yoksor: #CC0033
     --race-fergok: #009933
     --race-cyborg: #777777
   ```
   Supprimer les définitions dupliquées dans `php/register.php` et les autres fichiers PHP qui hardcodent ces couleurs
5. Recompiler le SASS

**Fichiers concernés :** `php/assets/css/styles.sass`, `php/register.php`, `php/stats_general.php`, tous les fichiers avec couleurs de races, créer `php/docs/glossaire.md`

---

### P3-22 — [SÉCU] Ajouter un rate limiting sur le login et les ordres

**Problème :** L'endpoint `php/connexion.php` n'a aucune limite de tentatives. Un attaquant peut tester des milliers de mots de passe en boucle sans être bloqué. De même, `php/ordres/` n't impose aucune limite sur le nombre d'ordres soumis par tour — un joueur malveillant peut inonder la table avec des milliers d'ordres, causant un déni de service lors du traitement Java en fin de tour.

**Action — Rate limiting login :**
1. Sans Redis disponible, implémenter un rate limiting en base MySQL : créer une table `aa_login_attempts (ip VARCHAR(45), login VARCHAR(100), attempt_time DATETIME, INDEX(ip, attempt_time))`
2. Dans `php/includes/auth.php::auth_login()` : avant de tenter l'authentification, compter les tentatives de l'IP dans les 10 dernières minutes et rejeter avec HTTP 429 si > 10
3. En cas de succès, purger les tentatives de cette IP
4. Ajouter un délai artificiel de 500ms sur les échecs pour ralentir les attaques (`usleep(500000)`)

**Action — Rate limiting ordres :**
1. Dans chaque script `php/ordres/*.php` : avant tout INSERT, vérifier le nombre d'ordres déjà enregistrés pour ce commandant ce tour via `SELECT COUNT(*) FROM <table_ordre> WHERE NUMERO = ?`
2. Définir une constante `MAX_ORDERS_PER_TYPE` (ex. 10) dans `php/secure/connect.txt` et rejeter si dépassé
3. Côté Java, dans `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, vérifier également le nombre d'ordres avant traitement et logger un warning si anormalement élevé

**Fichiers concernés :** `php/includes/auth.php`, `php/connexion.php`, `php/ordres/*.php`, `divers/base_sheril.sql` (nouvelle table), `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`

---

### P3-23 — [SÉCU] Corriger la race condition sur les ordres (isolation transactionnelle)

**Problème :** Dans `php/ordres/division.php`, la création d'une division de flotte utilise un pattern non atomique :
```php
// Étape 1 - lecture
$res_max = mysql($base, "SELECT MAX(NB_DIVISION) as max_div FROM diviser_flotte WHERE NUMERO='$commandant'");
// Étape 2 - écriture (window de race condition entre les deux)
mysql($base, "INSERT INTO diviser_flotte (NB_DIVISION, ...) VALUES ($next_div, ...)");
```
Si deux requêtes parallèles (double-clic, replay d'une requête HTTP) s'exécutent simultanément, `NB_DIVISION` peut être dupliqué, créant des données corrompues que le moteur Java traite de manière imprévisible.

**Action :**
1. Dans `php/ordres/division.php` : entourer le SELECT MAX + INSERT dans une transaction MySQL avec niveau d'isolation `SERIALIZABLE` :
   ```php
   $pdo->beginTransaction();
   try {
       // SELECT ... FOR UPDATE pour poser un verrou
       $stmt = $pdo->prepare("SELECT MAX(NB_DIVISION) FROM diviser_flotte WHERE NUMERO = ? FOR UPDATE");
       $stmt->execute([$commandant]);
       $next_div = ($stmt->fetchColumn() ?? 0) + 1;
       // INSERT
       $pdo->commit();
   } catch (Exception $e) { $pdo->rollBack(); throw $e; }
   ```
2. Identifier tous les autres patterns SELECT puis INSERT dans `php/ordres/` qui pourraient souffrir du même problème (grep pour `SELECT MAX` et `SELECT COUNT` suivis d'un INSERT sans transaction)
3. Ajouter une contrainte d'unicité en base sur `(NUMERO, NB_DIVISION)` dans `divers/base_sheril.sql` pour que la BDD rejette les doublons même si le code PHP échoue à les prévenir

**Fichiers concernés :** `php/ordres/division.php`, autres fichiers `php/ordres/`, `divers/base_sheril.sql`

---

### P3-24 — [SÉCU] Valider l'URL du webhook Discord pour prévenir le SSRF

**Problème :** Dans `sources/zIgzAg/jeu/oceane/Univers.java`, la méthode `doRequest()` construit une requête HTTP vers `Const.DISCORD_WEBHOOK_URL` sans valider le schéma ni le domaine cible. Si un attaquant peut modifier `config.properties` (via une autre vulnérabilité ou accès physique), il peut rediriger toutes les notifications vers un service interne du réseau Docker (`http://db:3306/`, `http://172.17.0.1/admin`, etc.), réalisant un Server-Side Request Forgery.

**Action :**
1. Dans `sources/zIgzAg/jeu/oceane/Univers.java::doRequest()` ou dans `sources/zIgzAg/jeu/oceane/Const.java` au chargement de la config : valider l'URL Discord avant usage :
   ```java
   private static final String DISCORD_WEBHOOK_PREFIX_1 = "https://discord.com/api/webhooks/";
   private static final String DISCORD_WEBHOOK_PREFIX_2 = "https://discordapp.com/api/webhooks/";
   
   if (!DISCORD_WEBHOOK_URL.startsWith(DISCORD_WEBHOOK_PREFIX_1) &&
       !DISCORD_WEBHOOK_URL.startsWith(DISCORD_WEBHOOK_PREFIX_2)) {
       throw new IllegalStateException("DISCORD_WEBHOOK_URL invalide : " + DISCORD_WEBHOOK_URL);
   }
   ```
2. Configurer le `HttpClient` Java avec un timeout explicite (5 secondes connect, 10 secondes read) pour éviter qu'une cible SSRF lente ne bloque le passage de tour indéfiniment
3. Documenter dans `config.properties.sample` le format attendu de l'URL

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Const.java`

---

### P3-25 — [SÉCU] Mettre en place un logging des événements de sécurité

**Problème :** Aucun événement de sécurité n'est actuellement loggué : les tentatives de connexion échouées, les ordres rejetés, les accès à des ressources non autorisées, les erreurs d'authentification. Sans ces logs, il est impossible de détecter une attaque en cours, de faire une analyse forensique après incident, ou de déclencher des alertes automatiques.

**Action :**
1. Créer `php/includes/security_log.php` avec une fonction `security_log(string $event, array $context = [])` qui écrit en append dans `/var/log/php/sheril-security.log` avec timestamp, IP, user-agent, et les données de contexte
2. Appeler `security_log('login_failure', ['login' => $login, 'ip' => $_SERVER['REMOTE_ADDR']])` dans `auth.php` en cas d'échec
3. Appeler `security_log('login_success', ['commandant' => $num])` en cas de succès
4. Appeler `security_log('csrf_violation', ...)` si un token CSRF invalide est reçu (P1-03)
5. Appeler `security_log('order_limit_exceeded', ...)` si le rate limiting des ordres est déclenché (P3-22)
6. Côté Java, dans `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` : logger via SLF4J (prérequis P3-05) tout ordre rejeté (paramètres invalides, commandant inexistant, ordre hors-tour)
7. Configurer la rotation des logs (logrotate ou via Logback pour Java)

**Fichiers concernés :** Créer `php/includes/security_log.php`, `php/includes/auth.php`, `php/ordres/*.php`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`

---

### P3-26 — [SÉCU] Garantir l'intégrité des ordres par signature HMAC

**Problème :** Les ordres sont stockés en clair dans MySQL sans aucune signature d'intégrité. N'importe qui ayant accès en écriture à la base (administrateur, exploit SQL injection, accès physique) peut modifier, supprimer ou insérer des ordres avant que le moteur Java ne les traite. Un MJ pourrait aussi modifier les ordres des joueurs sans laisser de trace.

**Action :**
1. Ajouter une colonne `SIGNATURE VARCHAR(64)` à chaque table d'ordres dans `divers/base_sheril.sql`
2. Dans `php/ordres/*.php` : au moment de l'INSERT d'un ordre, calculer une signature HMAC-SHA256 sur les données de l'ordre :
   ```php
   $order_data = json_encode(['type' => $type_ordre, 'params' => $params, 'commandant' => $commandant, 'tour' => $tour]);
   $signature = hash_hmac('sha256', $order_data, $_ENV['ORDER_SIGNING_KEY']);
   // Stocker $signature dans la colonne SIGNATURE
   ```
3. Dans `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java` : à la lecture des ordres, recalculer la signature avec la même clé et rejeter (logger comme fraude) tout ordre dont la signature ne correspond pas
4. Stocker `ORDER_SIGNING_KEY` dans `config.properties` (non versionné) et dans `php/secure/connect.txt`
5. Cette mesure ne protège pas contre un accès root à la BDD mais crée une preuve d'intégrité vérifiable et détecte les modifications accidentelles ou malveillantes

**Fichiers concernés :** `divers/base_sheril.sql`, `php/ordres/*.php`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, `config.properties.sample`, `php/secure/connect.txt.sample`

---

### P3-43 — [CVE] Épingler les versions CDN et auto-héberger les assets critiques

**Problème :** Deux vulnérabilités d'approvisionnement (supply chain) liées aux CDN :
1. **Chart.js chargé sans version** dans `php/stats_detail.php` : `<script src="https://cdn.jsdelivr.net/npm/chart.js">` — sans version épinglée, le CDN livre toujours la dernière version disponible. Une compromission du package npm `chart.js` serait automatiquement propagée à tous les utilisateurs du jeu (CVE-2021-21385 : XSS dans Chart.js < 3.7.0)
2. **Google Fonts** chargé depuis `fonts.googleapis.com` dans `styles.sass`, `body.txt` et `menu.php3` — chaque chargement de page envoie l'IP et l'URL de la page à Google (tracking tiers), ce qui peut constituer un traitement de données personnelles sous RGPD sans consentement

**Action :**
1. Dans `php/stats_detail.php` : épingler `chart.js@4.4.2` → `<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js" integrity="sha384-..." crossorigin="anonymous">`
2. Ajouter le hash SRI (`integrity="sha384-..."`) sur tous les assets CDN (Quill, Chart.js, Slim Select) — générer avec `openssl dgst -sha384 -binary fichier.js | openssl base64 -A`
3. **Auto-héberger Roboto** : télécharger `Roboto-Regular.woff2` et `Roboto-Bold.woff2`, les placer dans `php/assets/fonts/`, remplacer l'import Google Fonts par `@font-face { src: url('/assets/fonts/Roboto-Regular.woff2') }` dans `styles.sass`
4. Pour Quill (déjà versionné à 2.0.3) : ajouter le hash SRI pour sécuriser contre une compromission CDN

**Fichiers concernés :** `php/stats_detail.php`, `php/assets/css/styles.sass`, `php/ordres/body.txt`, `php/ordres/menu.php3`, créer `php/assets/fonts/`

---

### P3-44 — [PERF] Éliminer les recalculs répétés en boucle dans le moteur

**Problème :** Plusieurs patterns d'inefficacité dans les boucles critiques exécutées à chaque tour :

1. **`Pattern.compile()` recompilé à chaque appel** dans `Univers.java::supprimerAccent()` : `Pattern.compile("\\p{InCombiningDiacriticalMarks}+")` — chaque appel recrée un objet `Pattern`, alors que cette regex est constante
2. **`TreeMap` réalloué dans la boucle de combat** (`Combat.java:926`) : `TreeMap ht1 = determinationTempo(m1, h1)` est appelé à chaque itération de la boucle `while (!finDeTour)` au lieu d'être réutilisé et vidé
3. **`getNombrePlanetesPossedees()` et `getPopulationMaximale()` recalculés** dans une boucle sur les systèmes du commandant (`DeroulementDuTour.java:239-250`) sans mise en cache
4. **`toArray()` inutiles** dans `Combat.java` lignes 83, 91, 93 : les collections sont converties en tableau pour être itérées, alors qu'un `for (Map.Entry entry : map.entrySet())` éviterait l'allocation

**Action :**
1. Dans `Univers.java` : `private static final Pattern ACCENT_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");` puis utiliser ce champ statique dans `supprimerAccent()`
2. Dans `Combat.java::combatFlotteFlotte()` : pré-allouer `ht1` et `ht2` avant la boucle `while`, les vider avec `.clear()` à chaque itération
3. Dans `DeroulementDuTour.java` : calculer les valeurs de `getNombrePlanetesPossedees()` une fois avant la boucle et les passer en paramètre
4. Dans `Combat.java` : remplacer les `m.keySet().toArray(...)` par des boucles `for (Map.Entry<?,?> e : m.entrySet())`

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Combat.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`

---

### P3-45 — [PERF] Paralléliser la génération des rapports avec `ExecutorService`

**Problème :** Dans `DeroulementDuTour.java:157-177`, la génération des rapports pour chaque commandant est entièrement séquentielle : `Rapport`, `RapportXML` et `ProductionOrdres` sont créés et écrits sur disque l'un après l'autre. Pour 50 joueurs, cela représente ~20 secondes d'I/O bloquant. Or les rapports de chaque joueur sont **indépendants** — ils peuvent être générés en parallèle.

**Action :**
1. Dans `DeroulementDuTour.java` : remplacer la boucle séquentielle par un `ExecutorService` fixé à 4 threads :
   ```java
   ExecutorService pool = Executors.newFixedThreadPool(4);
   List<Future<?>> futures = new ArrayList<>();
   for (Commandant cmd : listeCommandants) {
       futures.add(pool.submit(() -> {
           new Rapport(cmd).ecriture();
           new RapportXML(cmd).ecrireRapportXML();
           new ProductionOrdres(cmd).creation();
       }));
   }
   for (Future<?> f : futures) f.get(); // attendre tous
   pool.shutdown();
   ```
2. Vérifier que `Rapport`, `RapportXML` et `ProductionOrdres` n'accèdent pas à des ressources partagées mutables — si c'est le cas, les rendre thread-safe ou utiliser des copies locales
3. Le `BufferedWriter` statique dans `Combat.java` (P2-09) doit être fermé **avant** de lancer les threads de rapport pour éviter les conflits de fichier
4. Gain estimé : 4x sur la phase de génération, soit ~15 secondes gagnées pour 50 joueurs

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`, `sources/zIgzAg/jeu/oceane/Rapport.java`, `sources/zIgzAg/jeu/oceane/RapportXML.java`

---

### P3-46 — [PERF] Configurer les options JVM dans `docker-compose.yml`

**Problème :** Le conteneur `engine` (Java) tourne avec les options JVM par défaut, ce qui signifie : heap initial et maximum calculés automatiquement par la JVM (souvent trop faibles pour un univers de jeu en mémoire), GC non optimisé pour ce profil d'utilisation (charge intensive puis idle), et aucun logging GC pour diagnostiquer les problèmes de mémoire.

**Action :**
1. Dans `docker-compose.yml`, ajouter des variables d'environnement pour le service `engine` :
   ```yaml
   engine:
     environment:
       JAVA_OPTS: >-
         -Xms512m -Xmx2g
         -XX:+UseG1GC
         -XX:MaxGCPauseMillis=200
         -XX:G1HeapRegionSize=16m
         -Xlog:gc*:file=/app/data/logs/gc.log:time,uptime:filecount=3,filesize=10m
   ```
2. Adapter la commande de lancement dans les scripts pour utiliser `$JAVA_OPTS` : `java $JAVA_OPTS -cp sheril.jar Start newRound`
3. Créer `data/logs/` s'il n'existe pas
4. Ajuster `-Xmx` selon le nombre de joueurs : ~500MB pour 20 joueurs, ~2GB pour 100 joueurs (voir P5-10 pour l'optimisation du moteur)
5. Ajouter `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/data/logs/` pour faciliter le debug en cas d'OOM

**Fichiers concernés :** `docker-compose.yml`, `scripts/init.sh` (commande java)

---

### P3-47 — [PERF] Batcher les requêtes MySQL dans `ReceptionOrdres`

**Problème :** `ReceptionOrdres.java::deroulementOrdres()` effectue une boucle sur les 62 types d'ordres (`Const.BORNE_ORDRES_VISIBLES`) et pour chacun interroge MySQL séparément pour chaque joueur. Estimation : 20 types × 50 joueurs = **1 000 requêtes SELECT** par passage de tour, soit ~50 secondes à 50ms par requête.

**Action :**
1. Remplacer la lecture ordre-par-ordre par une lecture en batch : pour chaque type d'ordre, une seule requête `SELECT * FROM <table_ordre> ORDER BY NUMERO` récupère tous les ordres de tous les joueurs en une fois, puis les distribuer en mémoire par joueur
2. Utiliser un `PreparedStatement` réutilisable pour chaque type (prérequis P1-07)
3. Pour les ordres qui nécessitent un traitement par joueur : regrouper en `Map<Integer, List<String[]>>` (numéro commandant → liste d'ordres) avant le traitement
4. Gain estimé : de 1 000 requêtes à 62 requêtes par tour (16x moins de round-trips MySQL)

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`

---

### P3-32 — [DBA] Ajouter les clés étrangères manquantes

**Problème :** Le schéma entier (`divers/base_sheril.sql`) ne définit aucune clé étrangère malgré des relations évidentes : `diviser_flotte.NUMERO` → `aa_registre.NUMERO`, `_post.id_forum` → `_forum.id_forum`, `_post.id_author` → `aa_registre.NUMERO`, et toutes les tables `*_ordres.NUMERO` → `aa_registre.NUMERO`. Sans FK, des données orphelines s'accumulent silencieusement (ordres de commandants supprimés, posts d'auteurs inexistants).

**Action :**
1. Prérequis : P2-18 (InnoDB) — les FK ne fonctionnent pas sur MyISAM
2. Ajouter dans `divers/base_sheril.sql` les contraintes prioritaires :
   ```sql
   ALTER TABLE _post ADD CONSTRAINT fk_post_forum
     FOREIGN KEY (id_forum) REFERENCES _forum(id_forum) ON DELETE CASCADE;
   ALTER TABLE _post ADD CONSTRAINT fk_post_author
     FOREIGN KEY (id_author) REFERENCES aa_registre(NUMERO) ON DELETE SET NULL;
   ```
3. Pour les tables d'ordres : ajouter `FOREIGN KEY (NUMERO) REFERENCES aa_registre(NUMERO) ON DELETE CASCADE` — si un commandant est supprimé, ses ordres en attente sont nettoyés automatiquement
4. Avant d'ajouter chaque FK : vérifier l'absence de données orphelines existantes avec `SELECT COUNT(*) FROM table_fille LEFT JOIN table_mere ... WHERE table_mere.pk IS NULL`
5. Documenter les choix `ON DELETE` (CASCADE vs SET NULL vs RESTRICT) dans un commentaire SQL

**Fichiers concernés :** `divers/base_sheril.sql`

---

### P3-33 — [DBA] Ajouter les index manquants sur les colonnes de filtrage

**Problème :** Plusieurs colonnes utilisées dans des `WHERE` et `JOIN` fréquents n'ont aucun index, forçant des full table scans à chaque requête :
- `diviser_flotte(NUMERO, FLOTTE)` : requête dans `division.php` `WHERE NUMERO=? AND FLOTTE=?` sans index
- `_post(id_parent)` : requête forum `WHERE id_parent=?` sans index (l'index composite existant `(id_forum, id_parent)` ne couvre pas les recherches sur `id_parent` seul)
- `statistiques(numero, tour)` : jointure dans `stats_general.php` sans index dans la direction `(numero, tour)`

**Action :**
1. Ajouter dans `divers/base_sheril.sql` :
   ```sql
   CREATE INDEX idx_diviser_flotte_num_flotte ON diviser_flotte(NUMERO, FLOTTE);
   CREATE INDEX idx_post_id_parent ON _post(id_parent);
   CREATE INDEX idx_statistiques_numero_tour ON statistiques(numero, tour);
   ```
2. Supprimer l'index redondant `idx_forum` sur `_post(id_forum)` seul (l'index composite `(id_forum, id_parent)` le couvre déjà)
3. Utiliser `EXPLAIN` sur les requêtes de `stats_general.php`, `division.php` et `forum/view_topic.php` pour vérifier que les index sont utilisés
4. Pour la base de production existante : exécuter les `CREATE INDEX` directement (opération non-bloquante sur InnoDB avec MySQL 5.6+)

**Fichiers concernés :** `divers/base_sheril.sql`

---

### P3-34 — [DBA] Migrer le charset `utf8` vers `utf8mb4` globalement

**Problème :** La quasi-totalité des tables du schéma n'a pas de charset explicite et hérite du charset serveur MySQL qui est probablement `utf8` — le `utf8` MySQL est limité à 3 octets et ne supporte pas les caractères Unicode au-delà du BMP (emojis, caractères rares). Seule la table `_post` utilise `utf8mb4`. Un nom de commandant avec un emoji ou un caractère spécial peut provoquer des erreurs silencieuses ou des troncatures.

**Action :**
1. En tête de `divers/base_sheril.sql` : ajouter `ALTER DATABASE sheril CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
2. Ajouter `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` sur chaque `CREATE TABLE` dans le schéma
3. Pour la base existante : `SELECT CONCAT('ALTER TABLE ', table_name, ' CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;') FROM information_schema.tables WHERE table_schema='sheril';` puis exécuter le résultat
4. Dans `sources/zIgzAg/sql/SessionMysql.java` : vérifier que l'URL JDBC inclut `&characterEncoding=UTF-8&useUnicode=true` (déjà présent ligne ~30 selon l'analyse) et ajouter `&connectionCollation=utf8mb4_unicode_ci`
5. Dans `php/secure/connect.txt` : s'assurer que la connexion PDO passe `charset=utf8mb4` dans le DSN

**Fichiers concernés :** `divers/base_sheril.sql`, `sources/zIgzAg/sql/SessionMysql.java`, `php/secure/connect.txt.sample`

---

### P3-35 — [DBA] Ajouter contraintes NOT NULL et CHECK sur les colonnes critiques

**Problème :** Des colonnes qui ne devraient jamais être NULL acceptent NULL dans le schéma : `aa_registre.LOGIN`, `aa_registre.MOT_DE_PASSE`, `aa_registre.NOM`, `_player_ready.num`, `_player_ready.tour`. De même, il n'y a aucune contrainte CHECK pour borner les valeurs métier : `aa_registre.RACE` devrait être dans `[0,5]`, les colonnes de statistiques comme `puissance` devraient être `>= 0`.

**Action :**
1. Dans `divers/base_sheril.sql` :
   ```sql
   ALTER TABLE aa_registre
     MODIFY LOGIN VARCHAR(50) NOT NULL,
     MODIFY MOT_DE_PASSE VARCHAR(255) NOT NULL,
     MODIFY NOM VARCHAR(100) NOT NULL,
     ADD CONSTRAINT chk_race CHECK (RACE BETWEEN 0 AND 5);
   ALTER TABLE _player_ready
     MODIFY num INT NOT NULL,
     MODIFY tour INT NOT NULL;
   ALTER TABLE statistiques
     ADD CONSTRAINT chk_puissance CHECK (puissance >= 0),
     ADD CONSTRAINT chk_centaure CHECK (centaure >= 0);
   ```
2. Avant d'ajouter `NOT NULL` : vérifier l'absence de valeurs NULL existantes avec `SELECT COUNT(*) FROM aa_registre WHERE LOGIN IS NULL`
3. Note : les contraintes CHECK ne sont appliquées qu'à partir de MySQL 8.0.16 — sur MySQL 5.7, les définir pour la migration future et documenter la limitation

**Fichiers concernés :** `divers/base_sheril.sql`

---

### P3-36 — [DBA] Éliminer les requêtes N+1 dans `chargerDescriptionTables()`

**Problème :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java::chargerDescriptionTables()` itère sur les 62 noms de tables d'ordres (`Const.NOMS_TABLES_ORDRES`) et exécute une requête `SHOW COLUMNS FROM <table>` pour chacune — soit 62 allers-retours MySQL au démarrage de chaque passage de tour. Cette information est disponible en une seule requête via `INFORMATION_SCHEMA`.

**Action :**
1. Dans `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, remplacer la boucle actuelle par :
   ```java
   PreparedStatement stmt = connection.prepareStatement(
       "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
       "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" +
       String.join(",", Collections.nCopies(Const.NOMS_TABLES_ORDRES.length, "?")) +
       ") ORDER BY TABLE_NAME, ORDINAL_POSITION"
   );
   for (int i = 0; i < Const.NOMS_TABLES_ORDRES.length; i++)
       stmt.setString(i + 1, Const.NOMS_TABLES_ORDRES[i]);
   ```
2. Construire la `HashMap` `descriptionTables` en itérant le `ResultSet` unique
3. Mesurer le temps d'initialisation avant/après avec `System.currentTimeMillis()` pour quantifier le gain

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`

---

### P3-37 — [DEVOPS] Rendre les scripts shell robustes et idempotents

**Problème :** Les scripts dans `scripts/` n'ont pas de protection contre les erreurs silencieuses ni contre les ré-exécutions accidentelles :
- `scripts/init.sh` : pas de `set -e` — si `mkdir -p data/commun` échoue, le script continue et peut corrompre l'état
- Relancer `init.sh` écrase `config.properties` et `connect.txt` même s'ils ont été personnalisés par l'administrateur
- `scripts/create-jar.sh` : même problème

**Action :**
1. Ajouter en tête de chaque script : `#!/bin/bash` suivi de `set -euo pipefail` et `trap 'echo "Erreur ligne $LINENO" >&2; exit 1' ERR`
2. Dans `scripts/init.sh` : conditionner chaque copie de fichier de config :
   ```bash
   [ -f config.properties ] || cp config.properties.sample config.properties
   [ -f php/secure/connect.txt ] || cp php/secure/connect.txt.sample php/secure/connect.txt
   [ -f php/live/a.php ] || cp php/live/a.php.sample php/live/a.php
   ```
3. Ajouter un header de documentation en début de chaque script (description, paramètres, exemple d'usage)
4. Tester chaque script en le relançant deux fois de suite — le second lancement doit produire le même résultat sans erreur ni écrasement

**Fichiers concernés :** `scripts/init.sh`, `scripts/create-jar.sh`, tous les scripts dans `scripts/`

---

### P3-38 — [DEVOPS] Ajouter health checks, limites de ressources et isolation réseau Docker

**Problème :** `docker-compose.yml` ne définit ni health checks (aucune détection automatique de service dégradé), ni limites de ressources (un bug peut consumer tout le CPU/RAM de l'hôte), ni réseau Docker dédié (tous les conteneurs sont sur le réseau bridge par défaut, sans isolation).

**Action :**
1. Ajouter des health checks dans `docker-compose.yml` :
   ```yaml
   db:
     healthcheck:
       test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "user", "-ppassword"]
       interval: 10s
       timeout: 5s
       retries: 3
   console:
     healthcheck:
       test: ["CMD", "curl", "-f", "http://localhost/index.php"]
       interval: 30s
       timeout: 5s
       retries: 3
   ```
2. Ajouter des limites de ressources :
   ```yaml
   db:
     deploy:
       resources:
         limits: { cpus: '1', memory: 512M }
   console:
     deploy:
       resources:
         limits: { cpus: '0.5', memory: 256M }
   engine:
     deploy:
       resources:
         limits: { cpus: '2', memory: 1G }
   ```
3. Créer un réseau Docker dédié et supprimer l'exposition inutile du port MySQL sur l'hôte :
   ```yaml
   networks:
     sheril-net:
       driver: bridge
   # Retirer `ports: - '3311:3306'` du service db (garder seulement `expose: 3306`)
   ```
4. Ajouter une configuration de logging avec rotation dans chaque service : `logging: { driver: json-file, options: { max-size: "10m", max-file: "3" } }`

**Fichiers concernés :** `docker-compose.yml`

---

### P3-39 — [TESTS] Mettre en place l'infrastructure de tests automatisés

**Problème :** Il n'existe aucune infrastructure de tests dans le projet — ni JUnit, ni Mockito, ni PHPUnit, ni Testcontainers. Le seul artefact existant est `sources/Test.java` (82 lignes sans assertions), qui n'est pas un test au sens formel. Le taux de couverture est de 0%.

**Action :**
1. Prérequis : P3-04 (Maven) — le `pom.xml` doit exister pour gérer les dépendances de test
2. Ajouter dans `pom.xml` les dépendances de test :
   ```xml
   <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.10.0</version><scope>test</scope></dependency>
   <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><version>5.5.0</version><scope>test</scope></dependency>
   <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId><version>1.19.0</version><scope>test</scope></dependency>
   <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><version>3.24.2</version><scope>test</scope></dependency>
   ```
3. Créer la structure `src/test/java/` et `src/main/java/` (réorganiser les sources actuelles de `sources/` vers `src/main/java/`)
4. Créer des classes de factory réutilisables dans `src/test/java/fixtures/` : `CommandantFactory.createBasicPlayer(int numero)`, `FlotteFactory.createWithVaisseaux(int count, String type)`, `UniversFactory.createMinimal()`
5. Configurer JaCoCo dans `pom.xml` pour générer un rapport de couverture dans `target/site/jacoco/` à chaque `mvn test`
6. Ajouter le job `test` dans `.github/workflows/ci.yml` (prérequis P5-04)

**Fichiers concernés :** `pom.xml`, créer `src/test/java/fixtures/`, `.github/workflows/ci.yml`

---

### P3-40 — [TESTS] Écrire les tests unitaires des composants critiques

**Problème :** Aucun test ne protège les trois mécaniques les plus complexes et les plus risquées du moteur : le combat (1500+ lignes de logique), le traitement des ordres (62 types via réflexion), et le calcul du budget (25 catégories). Un bug dans l'une de ces zones peut invalider silencieusement tout un tour de jeu.

**Action :**
1. **Combat** (étendre P3-07) : créer `src/test/java/CombatFlotteFlotteTest.java` avec au minimum :
   - `testFlottePlusForte_Remporte()` : 10 destroyers vs 5 chasseurs → destroyers gagnent
   - `testStrategiFuyard_NeFuitPasToujoursRageVersus()` : vérifier que le moral impacte la fuite
   - `testPorteeArme_LimiteDistance()` : flotte hors portée n'est pas touchée
   - `testDebris_GenereApresCombat()` : vérifier la création de débris en fin de combat
2. **Budget** : créer `BudgetCommandantTest.java` :
   - `testCircuitComplet_RevenusMinusDepenses()` : 100 + 200 revenus − 50 construction − 100 lieutenant = 150
   - `testBudgetNeverNegative_OrdersExceedFunds()` : les ordres coûteux sont refusés plutôt que de passer le budget en négatif
3. **Ordres simples** : créer `ReceptionOrdresTest.java` avec mock de la connexion MySQL (Mockito) pour les ordres les plus fréquents : `construire`, `deplacer_flotte`, `diviser_flotte`
4. Prérequis : P3-39 (infrastructure), P3-03 (injection contexte pour permettre le mocking d'`Univers`)

**Fichiers concernés :** Créer `src/test/java/CombatFlotteFlotteTest.java`, `src/test/java/BudgetCommandantTest.java`, `src/test/java/ReceptionOrdresTest.java`

---

### P3-41 — [DOC] Créer la documentation technique manquante

**Problème :** Un développeur ne peut pas démarrer seul sur le projet en moins d'une heure. Il manque : un guide d'installation étape par étape, un diagramme d'architecture montrant le flux PHP→MySQL→Java, une procédure documentée pour passer un tour, et une procédure de debug/recovery. La connaissance est entièrement dans les cerveaux des mainteneurs historiques.

**Action — 4 fichiers à créer :**

1. **`docs/INSTALLATION.md`** : prérequis (Java 21, Docker, Docker Compose), étapes ordonnées (`git clone` → `docker compose up -d` → `./scripts/init.sh` → accès web), vérification post-install (tables créées, page accessible, log propre), pièges courants (CRLF dans scripts, `connect.txt` manquant, volume MySQL corrompu)

2. **`docs/ARCHITECTURE.md`** : diagramme ASCII du flux complet (navigateur → PHP → MySQL → Java engine → fichiers tour → rapport → joueur), description des 3 conteneurs Docker et leurs rôles, explication du cycle de vie d'un tour (ordres stockés en BDD → Java les consomme → sérialisation → rapport généré)

3. **`docs/TURN_MANAGEMENT.md`** : commande pour lancer un tour, étapes internes de `DeroulementDuTour`, signification de `FAKE_TURN=true`, procédure de rollback si crash (supprimer `data/tourN+1/`, remettre `tour.txt` à N), où se trouvent les logs de combat

4. **`docs/DEBUGGING.md`** : localisation des logs (stdout Docker, `data/tourN/combats/*.log`), comment rejouer un ordre manuellement en SQL, comment lire les fichiers sérialisés Java (snippet de désérialisation), questions fréquentes (tour bloqué, rapport manquant, erreur MySQL)

**Fichiers concernés :** Créer `docs/INSTALLATION.md`, `docs/ARCHITECTURE.md`, `docs/TURN_MANAGEMENT.md`, `docs/DEBUGGING.md`

---

### P3-42 — [DOC] Documenter les constantes magiques dans `Const.java`

**Problème :** `sources/zIgzAg/jeu/oceane/Const.java` (855 lignes) contient 300+ constantes sans aucun javadoc. Des valeurs comme `NB_SECTEURS_X = 4`, `BORNE_SECTEUR_X = 10`, `NB_SYSTEMES_PAR_SECTEUR = 17`, `STRATEGIE_AGRESSIVITE_RAGE = 5` sont impossibles à comprendre sans lire le code des classes qui les utilisent. Modifier une constante sans comprendre son impact peut casser silencieusement l'équilibre du jeu.

**Action :**
1. Pour chaque constante dans `Const.java`, ajouter un commentaire Javadoc `/** */` expliquant : à quoi elle correspond en termes de règles du jeu, sa plage de valeurs valide, et ce qui se passe si on la modifie
2. Prioriser les constantes de gameplay (combat, budget, races) sur les constantes techniques
3. Exemple :
   ```java
   /** Nombre de secteurs par ligne et colonne dans une galaxie. 4×4 = 16 secteurs par galaxie. */
   public static final int NB_SECTEURS_X = 4;
   /** Borne max des coordonnées X/Y au sein d'un secteur. Taille effective d'un secteur = 10×10. */
   public static final int BORNE_SECTEUR_X = 10;
   ```
4. Créer un fichier `docs/GAME_CONSTANTS.md` listant les constantes d'équilibre clés avec leur valeur actuelle et leur impact gameplay — plus accessible qu'un fichier Java pour les game designers

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Const.java`, créer `docs/GAME_CONSTANTS.md`

---

### P3-16 — [JAVA] Traiter correctement les exceptions — arrêter le swallowing et les `System.exit`

**Problème :** Le code Java utilise deux anti-patterns de gestion d'erreurs qui rendent les pannes impossibles à diagnostiquer :

1. **Exception swallowing** : `catch (Exception e) { e.printStackTrace(); }` sans rethrow — l'exécution continue dans un état potentiellement incohérent. Présent dans `SessionSQL.java::query()` (retourne `null` après catch), `SessionSQL.java::update()`, `Start.java::newRound()`.
2. **`System.exit()` abusif** : `Univers.java::chargerMap()` appelle `System.exit(0)` en cas d'`IOException` lors du chargement d'un fichier sérialisé — ce qui est impossible à tester et empêche tout mécanisme de recovery. Présent aussi dans `SessionMysql.java::getConnection()`.

**Action :**
1. Définir une hiérarchie d'exceptions métier : `SherilException` (runtime), `SherilInitException`, `SherilTourException` dans un package `sources/zIgzAg/jeu/oceane/exception/`
2. Dans `Univers.java::chargerMap()` : remplacer `System.exit(0)` par `throw new SherilInitException("Impossible de charger " + fichier, e)` — laisser remonter jusqu'à `Start.java::main()` qui décide si le processus doit s'arrêter
3. Dans `SessionSQL.java::query()` : retourner `Optional<ResultSet>` ou relancer l'exception — ne jamais retourner `null` silencieusement après une SQLException
4. Dans `SessionSQL.java::update()` : relancer la `SQLException` wrappée dans `SherilException`
5. Dans `Start.java::newRound()` : le `catch (Exception e) { e.printStackTrace(); }` doit au moins appeler `Univers.notify("ERREUR CRITIQUE : " + e.getMessage())` et quitter proprement avec un code d'erreur non-zéro
6. Supprimer tous les appels `System.exit()` hors de `Start.java::main()`

**Fichiers concernés :** `sources/zIgzAg/sql/SessionSQL.java`, `sources/zIgzAg/sql/SessionMysql.java`, `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/Start.java`, créer `sources/zIgzAg/jeu/oceane/exception/`

---

### P3-17 — [JAVA] Supprimer le code mort et les blocs commentés

**Problème :** Le code source contient de nombreux blocs commentés qui alourdissent la lecture et induisent en erreur sur l'état réel du système. Exemples identifiés :
- `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java` : `//Univers.corrigerPlanDeVaisseau()` (ligne 28), blocs entiers de `System.out.println` commentés (lignes 73-90)
- `sources/zIgzAg/jeu/oceane/Univers.java` : ancienne implémentation de `notify()` commentée (lignes 1750-1772), javadoc de plan commenté (lignes 1156-1159)
- `sources/zIgzAg/jeu/oceane/Univers.java` : méthode `fechier()` (ligne 1678) qui semble être du code expérimental jamais supprimé
- `sources/zIgzAg/jeu/oceane/Univers.java` : `getDebris()` contient un accès redondant (`DEBRIS.get(pos)` appelé deux fois)

**Action :**
1. Grep dans `sources/` pour les patterns `//.*[a-zA-Z]{5,}` et `/* ... */` sur plusieurs lignes
2. Pour chaque bloc commenté : décider de le supprimer (s'il est remplacé par du code actif) ou de le documenter avec un commentaire expliquant pourquoi il est conservé
3. Supprimer la méthode `Univers::fechier()` si elle n'est appelée nulle part (grep pour `fechier(`)
4. Dans `Univers::getDebris()` : simplifier en `return (Debris) DEBRIS.get(pos)` (suppression du double accès)
5. Le git log préserve l'historique — le code commenté n'a aucune valeur de backup dans le source

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`, `sources/zIgzAg/jeu/oceane/Univers.java`

---

### P3-18 — [JAVA] Dédupliquer le code des plans de vaisseaux

**Problème :** Le code de construction des plans de vaisseaux (`PLAN_DEPART` et `PLAN_RACIAUX`) est copié-collé en quasi-totalité trois fois dans `sources/zIgzAg/jeu/oceane/Univers.java` : dans `initialisation()` (lignes ~1300-1374), dans `rechargerPlan()` (lignes ~1206-1290), et dans `fechier()` (lignes ~1678+). Les trois blocs itèrent sur les mêmes tableaux avec la même logique de création de `PlanDeVaisseau`.

**Action :**
1. Extraire une méthode privée statique `private static void ajouterPlans(Object[][] plans, Commandant neutre, int typeAcces)` qui contient la logique commune de création de plans
2. Dans `initialisation()`, `rechargerPlan()` et `fechier()` : remplacer les blocs dupliqués par des appels à `ajouterPlans(PLAN_DEPART, neutre, 0)` et `ajouterPlans(PLAN_RACIAUX, neutre, 3)`
3. Supprimer `fechier()` si elle n'est jamais appelée (vérifier avec grep)
4. Ajouter un test (prérequis P3-07) qui vérifie que le nombre de plans créés par `initialisation()` correspond à celui créé par `rechargerPlan()`

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`

---

### P3-19 — [JAVA] Corriger les inefficacités de performance dans les boucles

**Problème :** Plusieurs patterns inefficaces dans des méthodes appelées à chaque tour :

1. **Concaténation de String en boucle** (`Univers.java::trierAlphabetiquementTechnologies()`) : `clef += tab[i].getNom(...)` crée un nouvel objet String à chaque itération — sur 100+ technologies, c'est des centaines d'allocations inutiles.
2. **Division répétée en boucle** (`Univers.java::choisirPositionsDepartEquitables()`) : `Const.BORNE_MAX / 2.0` est calculé à chaque itération de boucle au lieu d'une constante pré-calculée.
3. **`nombreLignesResultSet()`** (`sources/zIgzAg/sql/SessionSQL.java`) : itère tout un `ResultSet` pour le compter — toutes les requêtes qui l'utilisent devraient être remplacées par `SELECT COUNT(*) FROM ...`
4. **`trouverProchainIdOffreMarche()`** (`Univers.java`) : itère manuellement la liste des offres avec un index au lieu d'un stream.
5. **`listePositionsSystemes()`** appelé plusieurs fois consécutivement dans `DeroulementDuTour` en recréant le tableau complet à chaque appel.

**Action :**
1. Dans `trierAlphabetiquementTechnologies()` : remplacer `clef += ...` par `String clef = tab[i].getNom(loc) + tab[i].getNiveau()`
2. Dans `choisirPositionsDepartEquitables()` : extraire `final double demi = Const.BORNE_MAX / 2.0` avant la boucle
3. Dans `SessionSQL.java` : supprimer `nombreLignesResultSet()` et remplacer ses appels par des requêtes `SELECT COUNT(*)`
4. Dans `Univers.java::trouverProchainIdOffreMarche()` : remplacer par `MARCHE_GALACTIQUE.stream().mapToInt(OffreMarche::getId).max().orElse(0) + 1`
5. Dans `DeroulementDuTour.java` : appeler `listePositionsSystemes()` une seule fois en début de méthode et passer la liste en paramètre aux méthodes qui en ont besoin

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/sql/SessionSQL.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`

---

### P3-20 — [JAVA] Corriger le risque de deadlock d'initialisation dans `SherilLogger`

**Problème :** `sources/zIgzAg/utile/SherilLogger.java` contient un bloc `static {}` qui appelle `Univers.getTour()` pendant l'initialisation statique de la classe. Si `SherilLogger` est chargé pendant que la classe `Univers` est elle-même en cours d'initialisation statique (ce qui arrive si du code appelé depuis le `static {}` d'`Univers` référence `SherilLogger`), Java détecte un cycle d'initialisation et l'un des deux blocs statiques reçoit une classe partiellement initialisée — comportement indéfini pouvant causer un deadlock ou une valeur de tour incorrecte (0 au lieu de la valeur réelle).

**Action :**
1. Dans `SherilLogger.java` : supprimer l'appel à `Univers.getTour()` du bloc `static {}` et remplacer par un nom de fichier générique (`sheril.log`) ou un timestamp
2. Exposer une méthode `SherilLogger.setTour(int tour)` appelée explicitement depuis `Start.java::main()` après que `Univers` est complètement initialisé
3. Alternativement, migrer vers SLF4J/Logback (prérequis P3-05) qui gère ce type de configuration sans dépendance circulaire

**Fichiers concernés :** `sources/zIgzAg/utile/SherilLogger.java`, `sources/Start.java`

---

### P3-21 — [JAVA] Corriger l'usage de `hashCode()` comme clé de `Map`

**Problème :** Dans `sources/zIgzAg/jeu/oceane/Univers.java` (ligne ~1019), des clés de `Map` sont construites à partir de `hashCode()` :
```java
Integer cle = new Integer(donneur.hashCode());
Integer cle2 = new Integer(beneficiaire.hashCode());
```
`hashCode()` n'est pas garanti unique — deux objets différents peuvent avoir le même hashCode (collision), ce qui écraserait silencieusement une entrée dans la Map avec des données d'un autre joueur.

**Action :**
1. Identifier toutes les utilisations de `hashCode()` comme clé de Map dans `sources/` (grep pour `.hashCode()` suivi d'un put dans une Map)
2. Les remplacer par l'identifiant métier de l'objet : pour un `Commandant`, utiliser `commandant.getNumero()` (qui est unique par conception du jeu) ; pour une `Alliance`, utiliser `alliance.getNumeroAlliance()`
3. Vérifier que les classes `Commandant`, `Alliance`, `Systeme` implémentent `equals()` et `hashCode()` basés sur leur identifiant métier (et non sur la référence objet par défaut)

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/Commandant.java`, `sources/zIgzAg/jeu/oceane/Alliance.java`

---

### P3-08 — [FRONT] Supprimer `style-sheril.css` et consolider le CSS

**Problème :** Deux feuilles de style coexistent : `php/assets/css/style-sheril.css` (305 lignes, CSS legacy années 2000, non linkée dans le HTML de production mais présente dans le repo) et `php/assets/css/styles.css` (526 lignes, compilé depuis SASS, en production). Elles définissent des règles contradictoires sur `body`, `font-family`, et la mise en page générale.

**Action :**
1. Vérifier qu'aucune page PHP ne fait `<link>` vers `style-sheril.css` (grep dans `php/`)
2. Supprimer `php/assets/css/style-sheril.css`
3. Vérifier que `styles.css` couvre tous les cas qui étaient dans l'ancien fichier (notamment les styles des tableaux et de la navigation)
4. Le cache-busting du lien CSS dans `top.php` est actuellement `?d=qsd` (valeur statique inutile) : le remplacer par un hash du fichier ou la date de modification via PHP (`?v=<?= filemtime(...)?>`)

**Fichiers concernés :** `php/assets/css/style-sheril.css` (suppression), `php/includes/top.php`

---

### P3-09 — [FRONT] Extraire tous les styles inline en classes CSS

**Problème :** Des dizaines de balises HTML portent des attributs `style=""` inline répétés, notamment dans `php/connexion.php`, `php/register.php`, `php/liste.php`, `php/includes/top.php`, `php/stats_general.php`. Cela rend toute refonte visuelle impossible sans parcourir chaque fichier PHP.

**Action :**
1. Grep dans `php/` pour `style="` et lister toutes les occurrences
2. Regrouper les patterns répétés en classes CSS dans `styles.sass` :
   - Inputs de formulaire → `.form-input`
   - Conteneurs de formulaire → `.form-card`
   - Messages d'erreur → `.alert-error`
   - Liens de navigation utilisateur → `.user-nav-link`
   - Cellules de tableau centrées → `.cell-center`
3. Remplacer les attributs `style=""` par les classes correspondantes dans chaque fichier PHP
4. Recompiler le SASS

**Fichiers concernés :** `php/assets/css/styles.sass`, `php/connexion.php`, `php/register.php`, `php/liste.php`, `php/includes/top.php`, `php/stats_general.php`

---

### P3-10 — [FRONT] Unifier le boilerplate HTML des pages de races autonomes

**Problème :** Les pages `php/races/fremen.php`, `atalante.php`, `yoksor.php`, `fergok.php`, `zwaia.php` sont des pages HTML complètes (`<!DOCTYPE html>`, `<html>`, `<head>`, etc.) qui dupliquent entièrement le boilerplate de `top.php`/`bot.php`. Toute modification du header (ajout d'un meta, changement de favicon, nouvelle police) doit être répercutée manuellement dans 5+ fichiers.

**Action :**
1. Remplacer le boilerplate HTML manuel dans chaque fichier de race par `<?php require_once '../includes/top.php'; ?>` en début de fichier et `<?php require_once '../includes/bot.php'; ?>` en fin
2. Vérifier que les pages de races fonctionnent correctement via `top.php` (chemin relatif des assets déjà en absolu `/assets/css/`)
3. Les pages de races ont leur propre `<nav>` interne de navigation entre races (`nav.php`) : le conserver tel quel à l'intérieur du `<main>`

**Fichiers concernés :** `php/races/fremen.php`, `php/races/atalante.php`, `php/races/yoksor.php`, `php/races/fergok.php`, `php/races/zwaia.php`

---

### P3-11 — [FRONT] Ajouter les breakpoints responsive manquants et le support tactile

**Problème :** Le CSS ne définit qu'un seul breakpoint à `768px`. Aucun style pour grands écrans (1200px+), aucun style pour petits mobiles (< 480px), et aucune prise en compte des interfaces tactiles (boutons trop petits pour les doigts).

**Action :**
1. Dans `styles.sass`, ajouter :
   - `@media (max-width: 480px)` : taille de police réduite, padding réduit, tableaux en défilement horizontal
   - `@media (min-width: 1200px)` : limiter `max-width` du contenu principal à `1200px` centré (`margin: 0 auto`)
   - `@media (hover: none) and (pointer: coarse)` : taille minimale des cibles tactiles à 44×44px pour les boutons et liens de navigation (`min-height: 44px; min-width: 44px`)
2. Pour le tableau de stats (`stats_general.php`, 13 colonnes) : ajouter `overflow-x: auto` sur le conteneur et un indicateur visuel de défilement horizontal sur mobile (pseudo-élément `::after` avec flèche ou `scroll-snap`)
3. Ajouter `@media (prefers-reduced-motion: reduce) { * { transition: none !important; animation: none !important; } }` pour les utilisateurs sensibles aux animations
4. Recompiler le SASS

**Fichiers concernés :** `php/assets/css/styles.sass`, `php/assets/css/styles.css`

---

### P3-12 — [FRONT] Améliorer l'accessibilité sémantique des tableaux et de la navigation

**Problème :** Plusieurs lacunes d'accessibilité sémantique sont présentes : la `<nav>` principale n'utilise pas `<ul><li>`, les tableaux n'ont pas de `<caption>`, les colonnes triables n'ont pas d'`aria-sort`, le fil d'Ariane du forum n'est pas une `<nav>` avec `<ol>`, et certains `<input>` n'ont pas de message d'aide associé.

**Action :**
1. Dans `php/includes/top.php` : transformer la `<nav>` en `<nav aria-label="Navigation principale"><ul><li><a href="/">Accueil</a></li>...</ul></nav>`
2. Dans `php/liste.php` et `php/stats_general.php` : ajouter `<caption>` sur chaque `<table>` et attribut `scope="col"` sur les `<th>`
3. Dans `php/stats_general.php` : la fonction PHP `trie()` (ligne ~20) doit ajouter `aria-sort="ascending"` ou `"descending"` sur les `<th>` selon le tri actif
4. Dans `php/forum/view_topic.php` : remplacer le fil d'Ariane textuel par `<nav aria-label="Fil d'Ariane"><ol><li>...</li></ol></nav>` avec `aria-current="page"` sur le dernier élément
5. Dans `php/register.php` : ajouter `aria-describedby` sur les `<input>` pointant vers des `<small id="...">` contenant les règles de validation

**Fichiers concernés :** `php/includes/top.php`, `php/liste.php`, `php/stats_general.php`, `php/forum/view_topic.php`, `php/register.php`

---

### P3-13 — [FRONT] Optimiser les images (lazy loading, srcset, format WebP)

**Problème :** Toutes les images du site (`php/races/img/`, images des rapports) utilisent des balises `<img>` simples sans `loading="lazy"`, sans `srcset`, et au format JPEG/PNG sans alternative WebP. Les pages de races chargent des images immédiatement même si elles sont hors écran.

**Action :**
1. Ajouter `loading="lazy"` sur toutes les `<img>` qui ne sont pas dans le viewport initial (images des races, illustrations de l'index)
2. Convertir les images JPEG/PNG importantes en WebP (outil : `cwebp` ou Squoosh) et les stocker dans `php/assets/img/` et `php/races/img/`
3. Remplacer les `<img src="fremen.jpeg">` par des `<picture>` avec fallback :
   ```html
   <picture>
     <source srcset="fremen.webp" type="image/webp">
     <img src="fremen.jpeg" alt="Soldat Fremen" loading="lazy">
   </picture>
   ```
4. Pour les images de races avec classe `.float` : ajouter `srcset` pour proposer deux tailles (400px mobile, 800px desktop)
5. Les chemins d'images sont incohérents (`./img/` vs `/assets/img/`) : normaliser en chemins absolus `/assets/img/`

**Fichiers concernés :** `php/races/*.php`, `php/index.php`, `php/presentation.php`, répertoires `php/races/img/` et `php/assets/img/`

---

### P3-14 — [FRONT] Robustifier et améliorer les scripts JavaScript

**Problème :** `php/assets/js/script.js` (27 lignes) et le JS inline dans `php/stats.php` présentent plusieurs fragilités : pas de timeout sur les `fetch()`, pas de fallback si `/tour.txt` est inaccessible, boucle potentiellement infinie si le numéro de tour est anormal, et le contenu injecté dans le DOM n'est pas annoncé aux lecteurs d'écran.

**Action :**
1. Dans `script.js` : ajouter un timeout sur le fetch via `AbortController` (5 secondes) et un fallback si le fetch échoue (ne rien afficher plutôt que casser silencieusement)
2. Dans `script.js` : valider que `lastModified` produit une date valide avant de l'afficher (`if (isNaN(date.getTime())) return`)
3. Dans `php/stats.php` (JS inline) : ajouter une vérification que le numéro de tour est dans une plage raisonnable (`if (maxTour > 500 || maxTour < 0) return`) et que le conteneur `#statsLink` existe avant d'itérer
4. Pour l'injection de contenu dans le header : ajouter un élément `<span aria-live="polite">` dans le template HTML pour que les lecteurs d'écran annoncent le numéro de tour quand il est chargé
5. Remplacer `<small>` par `<p class="tour-info">` pour le contenu du numéro de tour — `<small>` a une sémantique de "fine print" inappropriée ici

**Fichiers concernés :** `php/assets/js/script.js`, `php/stats.php`, `php/includes/top.php`

---

### P3-15 — [FRONT] Ajouter les meta tags SEO et partage social manquants

**Problème :** `php/includes/top.php` ne contient aucun meta tag de description, pas d'Open Graph, pas de favicon. Chaque page a le même titre générique "Sheril, le jeu de conquête galactique".

**Action :**
1. Dans `php/includes/top.php` : ajouter les balises meta manquantes avec des valeurs par défaut surchargeables :
   ```php
   $pageTitle = $pageTitle ?? "Sheril, le jeu de conquête galactique";
   $pageDescription = $pageDescription ?? "Jeu de stratégie spatiale au tour par tour";
   ```
   ```html
   <title><?= htmlspecialchars($pageTitle) ?></title>
   <meta name="description" content="<?= htmlspecialchars($pageDescription) ?>">
   <meta property="og:title" content="<?= htmlspecialchars($pageTitle) ?>">
   <meta property="og:description" content="<?= htmlspecialchars($pageDescription) ?>">
   <meta property="og:image" content="/assets/img/og-image.png">
   <link rel="icon" href="/assets/img/favicon.ico">
   ```
2. Dans chaque page clé (`index.php`, `presentation.php`, `connexion.php`, pages de races), définir `$pageTitle` et `$pageDescription` avant d'inclure `top.php`
3. Créer un favicon `php/assets/img/favicon.ico` (même une version minimaliste)

**Fichiers concernés :** `php/includes/top.php`, pages principales PHP

---

### P3-07 — Ajouter des tests unitaires sur le système de combat

**Problème :** `Combat.resolutionCombats()` est la mécanique la plus critique et la plus complexe — aucun test ne protège les modifications.

**Action :**
1. Créer `src/test/java/CombatTest.java` avec JUnit 5
2. Instancier deux flottes minimales (1 vaisseau chacune) avec des caractéristiques fixes
3. Appeler `Combat.resolutionCombatsSurUneCase()` et vérifier que le résultat (vainqueur, pertes) correspond aux attentes calculées manuellement
4. Ajouter des cas limites : flotte vide, flotte neutre, combat à 3 partis
5. Prérequis : P3-03 (injecter le contexte) pour pouvoir isoler le test de l'état global statique

**Fichiers concernés :** Créer `src/test/java/CombatTest.java`, dépend de P3-03

---

## P4 — Modernisation de la stack

### P4-10 — [RGPD] Créer la politique de confidentialité, les mentions légales et la bannière cookie

**Problème :** Le site collecte des données personnelles (email, pseudo, Discord ID, adresse IP via logs Apache) sans aucun document légal — ni politique de confidentialité, ni mentions légales, ni consentement aux cookies. C'est une violation des Art. 13-14 RGPD (information des personnes), de l'Art. 82 RGPD (consentement cookies), et de la Loi pour la Confiance dans l'Économie Numérique (LCEN) pour les mentions légales. La CNIL peut prononcer une amende allant jusqu'à 20M€ ou 4% du CA.

**Action :**
1. **`php/politique-confidentialite.php`** : rédiger en français la politique RGPD complète incluant : identité du responsable de traitement, liste des données collectées, base juridique de chaque collecte, durée de conservation, droits des utilisateurs (contact email pour exercer les droits), transferts vers tiers (Discord), hébergeur
2. **`php/mentions-legales.php`** : identité du créateur/éditeur, hébergeur du site, adresse de contact
3. **Bannière cookie** dans `php/includes/top.php` : avant `session_start()`, vérifier si le cookie de consentement existe (`$_COOKIE['cookie_consent']`), sinon afficher une bannière avec bouton "Accepter" qui pose le cookie et recharge ; le cookie `PHPSESSID` ne doit être créé qu'après consentement (ou justifié comme "strictement nécessaire" sans consentement)
4. Ajouter des liens vers la politique de confidentialité et les mentions légales dans le footer (`php/includes/bot.php`)
5. Dans `php/register.php` : ajouter une case à cocher obligatoire "J'ai lu et j'accepte la politique de confidentialité" avec lien — archiver la date et version du consentement dans `aa_registre`

**Fichiers concernés :** Créer `php/politique-confidentialite.php`, `php/mentions-legales.php`, modifier `php/includes/top.php`, `php/includes/bot.php`, `php/register.php`

---

### P4-11 — [RGPD] Implémenter les droits RGPD des utilisateurs

**Problème :** Aucun des 5 droits RGPD n'est implémentable par les utilisateurs : droit d'accès (Art. 15), droit à l'effacement (Art. 17), droit de rectification (Art. 16), droit à la portabilité (Art. 20), droit d'opposition (Art. 21). La CNIL peut sanctionner chaque manquement séparément.

**Action :**
1. **Droit d'accès et portabilité** : créer `php/mes-donnees.php` (accessible après connexion) qui génère un export JSON téléchargeable contenant toutes les données du commandant : pseudo, email, race, statistiques, ordres passés, posts forum, historique PV
2. **Droit à l'effacement** : créer `php/supprimer-compte.php` avec confirmation par email — la suppression anonymise les données (remplace NOM par "Commandant supprimé", efface EMAIL et LOGIN, conserve les statistiques anonymisées pour l'intégrité de l'historique)
3. **Droit de rectification** : créer `php/modifier-profil.php` permettant de changer l'email et le pseudo
4. Dans `aa_registre` : ajouter les colonnes `rgpd_consent_date DATETIME`, `rgpd_consent_version VARCHAR(10)`, `deleted_at DATETIME NULL`
5. Documenter la procédure dans `php/politique-confidentialite.php` avec l'email de contact pour exercer les droits

**Fichiers concernés :** Créer `php/mes-donnees.php`, `php/supprimer-compte.php`, `php/modifier-profil.php`, `divers/base_sheril.sql` (nouvelles colonnes)

---

### P4-12 — [RGPD] Définir et appliquer une politique de rétention des données

**Problème :** Les données des joueurs (emails, pseudos, statistiques, posts forum) sont conservées indéfiniment sans limite. L'Art. 5(1)(e) RGPD impose une durée de conservation "limitée au nécessaire". Les comptes inactifs depuis plusieurs années représentent un risque inutile en cas de fuite.

**Action :**
1. Dans `divers/base_sheril.sql` : ajouter `last_activity_at DATETIME` dans `aa_registre`, mise à jour à chaque connexion
2. Définir dans `php/politique-confidentialite.php` les durées : comptes actifs conservés indéfiniment, comptes inactifs depuis 3 ans → notification par email → 30 jours pour réactiver → suppression automatique
3. Créer `scripts/rgpd-cleanup.php` (à lancer via cron mensuel) : identifier les comptes inactifs depuis 3 ans, envoyer notification email, supprimer les comptes non réactivés après délai
4. Pour les posts forum : conserver mais anonymiser si le compte auteur est supprimé (remplacer `id_author` par NULL, afficher "Ancien commandant")
5. Pour les statistiques historiques : conserver de façon anonymisée (supprimer le lien avec le compte joueur après suppression)

**Fichiers concernés :** `divers/base_sheril.sql`, créer `scripts/rgpd-cleanup.php`, `php/politique-confidentialite.php`

---

### P4-06 — [DEVOPS] Configurer MySQL pour les performances

**Problème :** Le conteneur MySQL tourne avec la configuration par défaut — `innodb_buffer_pool_size` à 128MB (insuffisant pour un univers de jeu en mémoire), `max_connections` à 151 (peut saturer si plusieurs processus Java tournent), et aucune configuration de log InnoDB pour la durabilité des transactions.

**Action :**
1. Créer `config/mysql/my.cnf` :
   ```ini
   [mysqld]
   innodb_buffer_pool_size = 256M
   innodb_log_file_size = 64M
   max_connections = 50
   slow_query_log = 1
   slow_query_log_file = /var/log/mysql/slow.log
   long_query_time = 1
   ```
2. Monter ce fichier dans `docker-compose.yml` :
   ```yaml
   db:
     volumes:
       - ./config/mysql/my.cnf:/etc/mysql/conf.d/sheril.cnf:ro
   ```
3. Activer le slow query log pour identifier les requêtes lentes (> 1s) pendant les passages de tour
4. Après P4-02 (MySQL 8) : activer `performance_schema = ON` pour profiler les requêtes

**Fichiers concernés :** Créer `config/mysql/my.cnf`, `docker-compose.yml`

---

### P4-07 — [TESTS] Tests d'intégration Testcontainers et tests PHP (Pest)

**Problème :** Les tests unitaires (P3-40) mockeront les dépendances mais ne verront pas les bugs d'intégration entre Java et MySQL. Les tests PHP sont inexistants. Il faut une couche de tests d'intégration qui démarre une vraie base de données MySQL dans Docker et vérifie les flux complets.

**Action :**
1. **Tests intégration Java** : créer `src/test/java/integration/DeroulementDuTourIntegrationTest.java` annoté `@Testcontainers` — démarre un conteneur MySQL 8, joue le schéma `base_sheril.sql`, insère des données de test, exécute `DeroulementDuTour.main()`, vérifie l'état final de la base
2. **Scénario minimal** : 2 commandants, 2 flottes, 1 ordre de déplacement + 1 ordre de construction → vérifier que les tables sont mises à jour correctement après le tour
3. **Tests PHP** : installer Pest (`composer require pestphp/pest --dev`) dans `php/`, créer `php/tests/Feature/AuthTest.php` (login valide → session créée, login invalide → rejet), `php/tests/Feature/OrderDivisionTest.php` (division valide → INSERT en base, division invalide → rejet)
4. Intégrer les deux suites de tests dans `.github/workflows/ci.yml`

**Fichiers concernés :** Créer `src/test/java/integration/`, `php/composer.json`, `php/tests/`, `.github/workflows/ci.yml`

---

### P4-08 — [DOC] Documenter le flux des ordres et le format des données sérialisées

**Problème :** La circulation des données entre PHP et Java (comment un ordre passe du navigateur au moteur Java et comment le résultat revient au joueur) n'est documentée nulle part. Le format des fichiers `.txt` sérialisés Java dans `data/` est totalement opaque — impossible de les lire, déboguer ou migrer sans écrire du code Java spécifique.

**Action :**
1. **`docs/ORDERS_FLOW.md`** : décrire les 4 étapes pour chaque type d'ordre : (1) formulaire PHP → INSERT dans table `aa_<ordre>`, (2) `ReceptionOrdres.deroulementOrdres()` lit la table via JDBC, (3) validation métier + application sur l'univers en mémoire, (4) résultat écrit dans `aa_<ordre>_rendu` et univers sérialisé. Inclure un exemple complet avec `construire_flotte`
2. **`docs/DATA_FORMAT.md`** : lister les fichiers sérialisés (`comm.txt` → `TreeMap<Integer, Commandant>`, `sys.txt` → `TreeMap<Position, Systeme>`, etc.), donner un snippet Java pour désérialiser un fichier, documenter les champs `transient` (non sérialisés) et pourquoi, avertir sur les risques de `InvalidClassException` après changement de code

**Fichiers concernés :** Créer `docs/ORDERS_FLOW.md`, `docs/DATA_FORMAT.md`

---

### P4-09 — [DOC] Documentation joueur : référence des ordres et guide de démarrage

**Problème :** Les 17 fichiers de règles dans `rules/` sont excellents mais rédigés en Markdown brut non publié. Il n'existe pas de référence des ordres accessibles aux joueurs (les fichiers `php/ordres/fr/aide/*.txt` existent mais ne sont jamais affichés), ni de guide de démarrage pour un nouveau joueur.

**Action :**
1. **`rules/GLOSSAIRE.md`** : définir les 25 termes métier canoniques (centaure, flotte, gouverneur, héros, ordre, tour, système, secteur, galaxie, technologie, etc.) — établit aussi la terminologie officielle pour tout le projet (P3-31)
2. **`rules/ORDRES_REFERENCE.md`** : table complète des 62 ordres avec pour chacun : nom, prérequis (technologie, ressource), coût en centaures, délai (tours), effet, limite par tour/joueur. S'appuyer sur les fichiers `php/ordres/fr/aide/*.txt` existants comme source
3. **`docs/QUICKSTART_PLAYER.md`** : scénario d'un premier tour (3 ordres prioritaires avec leur effet), lien vers Discord, lien vers la présentation du jeu
4. Script de compilation : `scripts/build-docs.sh` qui utilise `pandoc` pour convertir `rules/*.md` en `rules/index.html` — les règles deviennent consultables en ligne depuis le serveur web

**Fichiers concernés :** Créer `rules/GLOSSAIRE.md`, `rules/ORDRES_REFERENCE.md`, `docs/QUICKSTART_PLAYER.md`, `scripts/build-docs.sh`

---

### P4-05 — [JAVA] Moderniser le code Java vers Java 21

**Problème :** Le projet utilise Java 21 (image Docker `eclipse-temurin:21-jdk`) mais le code source est écrit en style Java 1.4/1.5 : pas de génériques, pas de `var`, pas de switch expressions, pas d'`Optional`, `StringBuffer` au lieu de `StringBuilder`, itérateurs manuels au lieu de for-each. Plusieurs constructions modernes rendraient le code plus lisible et plus sûr.

**Action (migrer par fichier, par ordre de fréquence d'appel) :**

1. **`StringBuffer` → `StringBuilder`** : grep pour `new StringBuffer` dans `sources/` et remplacer par `new StringBuilder` — `StringBuffer` est synchronized inutilement, `StringBuilder` est plus rapide

2. **Switch expression** dans `sources/Start.java::main()` (lignes 33-51) :
   ```java
   // Avant
   if (args[0].equals("init")) { ... } else if (args[0].equals("addNewGalaxy")) { ... }
   // Après
   switch (args[0]) {
       case "init" -> initUnivers();
       case "addNewGalaxy" -> { /* ... */ }
       default -> displayHelp();
   }
   ```

3. **`Optional` à la place des retours `null`** : `Univers::getOffreMarche()` retourne déjà `null` après un `stream().findFirst()` — remplacer par `Optional<OffreMarche>`. Appliquer le même pattern à `getTechnologie()`, `getAlliance()`, `getCommandant()`

4. **`var` keyword** dans les corps de méthodes locales pour réduire la verbosité des déclarations longues (`TreeMap<String, Technologie> t = new TreeMap<>()` → `var t = new TreeMap<String, Technologie>()`)

5. **For-each et streams** : remplacer les `for (int i = 0; i < liste.length; i++)` par `for (var element : liste)` ou `Arrays.stream(liste).forEach(...)` dans les boucles sans accès à l'index

6. **`new Integer(x)` → `Integer.valueOf(x)`** ou auto-boxing direct : `new Integer(...)` est déprécié depuis Java 9 — grep pour `new Integer(`, `new Long(`, `new Double(`

**Fichiers concernés :** `sources/Start.java`, `sources/zIgzAg/jeu/oceane/Univers.java`, `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`, et tous les fichiers Java (pour StringBuffer/new Integer)

---

### P4-04 — [FRONT] Adopter Twig comme moteur de templates PHP

**Problème :** Les fichiers PHP mélangent logique métier (requêtes SQL, traitement des données) et présentation HTML dans le même fichier. `php/register.php` en est l'exemple le plus extrême avec des blocs PHP et HTML imbriqués sur 400 lignes. Cela rend le code difficile à lire, tester et maintenir.

**Action :**
1. Ajouter Twig (`twig/twig:^3.0`) via Composer dans `php/` : créer `php/composer.json` et lancer `composer require twig/twig`
2. Créer un répertoire `php/templates/` avec un fichier `.twig` par page (ex. `templates/connexion.html.twig`, `templates/liste.html.twig`)
3. Créer `php/includes/twig.php` : instancie le moteur Twig pointant vers `templates/` avec cache dans `php/cache/twig/`
4. Migrer les pages progressivement en deux étapes par fichier : (a) extraire la logique PHP en tête de fichier, (b) remplacer la partie HTML par un `$twig->render('page.html.twig', $data)`
5. Prioriser les pages avec le plus de HTML inline : `register.php`, `liste.php`, `stats_general.php`, `forum/view_topic.php`
6. Twig gère l'échappement HTML automatiquement (`{{ variable }}` est échappé par défaut) — supprimer les `htmlspecialchars()` redondants dans les templates

**Fichiers concernés :** Créer `php/composer.json`, `php/templates/`, `php/includes/twig.php`, puis migrer `php/register.php`, `php/liste.php`, `php/stats_general.php`, `php/forum/`

---

### P4-01 — Migrer de PHP 5.6 vers PHP 8.2

**Problème :** PHP 5.6 est EOL depuis décembre 2018. Aucune mise à jour de sécurité.

**Action :**
1. Remplacer dans `Dockerfile` : `FROM php:5.6-apache` → `FROM php:8.2-apache`
2. Supprimer `php/mysql_compat.php` (inutile avec PDO, prérequis : P1-02 terminé)
3. Corriger les incompatibilités PHP 8 : `ereg_*` supprimé, `each()` supprimé, `create_function()` supprimé — scanner avec `php -l` ou un outil comme `rector`
4. Désactiver les `short_open_tag` dans `Dockerfile` (bonne pratique) et remplacer les `<?` par `<?php` dans les fichiers concernés
5. Tester toutes les pages de `php/ordres/`, `php/forum/`, `php/rapports/`

**Fichiers concernés :** `Dockerfile`, `php/mysql_compat.php` (suppression), tous les `.php` avec incompatibilités

---

### P4-02 — Migrer de MySQL 5.7 vers MySQL 8

**Problème :** MySQL 5.7 EOL depuis octobre 2023.

**Action :**
1. Remplacer dans `docker-compose.yml` : `image: mysql:5` → `image: mysql:8`
2. Vérifier le charset : MySQL 8 utilise `utf8mb4` par défaut (probablement déjà voulu par le projet)
3. Tester le script `divers/base_sheril.sql` sur MySQL 8 et corriger les syntaxes dépréciées (ex. `TYPE=InnoDB` → `ENGINE=InnoDB`)
4. Côté Java, remplacer le driver `org.gjt.mm.mysql` par `com.mysql:mysql-connector-j:8.0.33` dans `pom.xml` (prérequis : P3-04)

**Fichiers concernés :** `docker-compose.yml`, `divers/base_sheril.sql`, `pom.xml`

---

### P4-03 — Remplacer le driver JDBC obsolète

**Problème :** `sources/zIgzAg/sql/SessionMysql.java` charge le driver `org.gjt.mm.mysql.Driver` via `Class.forName()`, un driver abandonné vers 2002.

**Action :**
1. Supprimer l'appel à `Class.forName("org.gjt.mm.mysql.Driver")` (le chargement automatique JDBC 4.0+ le rend inutile)
2. Utiliser `DriverManager.getConnection("jdbc:mysql://...")` directement avec le driver moderne `com.mysql:mysql-connector-j` (prérequis : P3-04 pour Maven)
3. Envisager de remplacer l'accès manuel par HikariCP pour un connection pool

**Fichiers concernés :** `sources/zIgzAg/sql/SessionMysql.java`, `sources/zIgzAg/sql/SessionSQL.java`

---

## P5 — Évolutions fonctionnelles et qualité long terme

### P5-08 — [RGPD] DPA Discord, protection des mineurs et auto-hébergement des polices

**Problème :** Trois obligations légales résiduelles après P4-10 à P4-12 :
1. Aucun Data Processing Agreement (DPA) avec Discord — l'ID Discord est stocké dans `aa_registre` et des données de jeu sont envoyées via webhook sans contrat documenté (Art. 28 RGPD)
2. Aucune protection des mineurs — le jeu est accessible sans vérification d'âge alors que l'Art. 8 RGPD impose un consentement parental pour les moins de 16 ans
3. Google Fonts envoie l'IP de chaque visiteur à Google sans consentement (tracking tiers)

**Action :**
1. **DPA Discord** : documenter dans `php/politique-confidentialite.php` le transfert de données vers Discord et sa base légale ; si l'application Discord dépasse 25k utilisateurs, signer formellement le DPA disponible via Discord Developer Portal
2. **Protection mineurs** : ajouter dans `php/register.php` une case "Je certifie avoir plus de 16 ans" — obligatoire à l'inscription ; documenter dans la politique de confidentialité
3. **Google Fonts** : appliquer P3-43 (auto-hébergement de Roboto) pour éliminer le tracking tiers ; si conservé sur CDN Google, catégoriser comme cookie tiers et conditionner son chargement au consentement de la bannière (P4-10)

**Fichiers concernés :** `php/register.php`, `php/politique-confidentialite.php`, `php/assets/css/styles.sass`

---

### P5-09 — [CVE] Intégrer le scan automatique de CVE dans la CI

**Problème :** Les vulnérabilités des dépendances (JARs, images Docker, packages npm) ne sont actuellement détectées que manuellement. Sans scan automatique, une nouvelle CVE critique peut rester non détectée pendant des mois.

**Action :**
1. Ajouter dans `.github/workflows/ci.yml` un job `security-scan` (prérequis P5-04) :
   - **Trivy** pour les images Docker : `trivy image php:8.2-apache` et `trivy image mysql:8` — bloque la CI si CVE CRITIQUE trouvée
   - **OWASP Dependency Check** pour les JARs : `dependency-check --project sheril --scan libs/ --format HTML`
2. Une fois Maven adopté (P3-04) : ajouter le plugin `org.owasp:dependency-check-maven` dans `pom.xml` — `mvn dependency-check:check` détecte les CVE dans les dépendances Maven et échoue le build si CVSS > 7
3. Configurer Dependabot sur GitHub (`.github/dependabot.yml`) pour être notifié automatiquement des nouvelles CVE sur les dépendances

**Fichiers concernés :** `.github/workflows/ci.yml`, `pom.xml` (après P3-04), créer `.github/dependabot.yml`

---

### P5-10 — [PERF] Optimiser le combat O(N²) pour la scalabilité à 100+ joueurs

**Problème :** L'algorithme de détermination de cible dans `Combat.java` (lignes 1260-1309) a une complexité O(m1 × m2 × 5 × 50 tours de combat) par affrontement, soit ~2.5M opérations pour deux flottes de 100 vaisseaux. Avec 50+ combats simultanés sur un tour chargé, le temps de traitement dépasse 2 minutes pour 100 joueurs.

**Action :**
1. **Profiler d'abord** avec Java Flight Recorder pour confirmer le goulot : `java -XX:StartFlightRecording=duration=120s,filename=sheril.jfr -cp sheril.jar Start newRound` puis analyser avec JDK Mission Control
2. **Optimisation de `determinationCible()`** : précalculer les listes de vaisseaux par type une seule fois par combat (pas à chaque appel dans la boucle `while`) ; éviter les réallocations de collections temporaires à chaque tour de combat
3. **Paralléliser les combats indépendants** : les combats sur des positions différentes n'ont aucune dépendance entre eux — les exécuter en parallèle avec `CompletableFuture.allOf(...)` et un `ExecutorService` dédié aux combats (distinct de P3-45 qui concerne les rapports)
4. Cible de scalabilité : 100 joueurs en < 2 minutes, 200 joueurs en < 5 minutes

**Fichiers concernés :** `sources/zIgzAg/jeu/oceane/Combat.java`, `sources/zIgzAg/jeu/oceane/DeroulementDuTour.java`

---

### P5-06 — [TESTS] Tests E2E et tests de charge du passage de tour

**Problème :** Même avec des tests unitaires et d'intégration (P3-40, P4-07), les parcours utilisateurs complets (connexion → passage d'ordres → consultation stats) et le comportement sous charge (10 joueurs simultanés passant des ordres, puis passage de tour) ne sont pas couverts.

**Action :**
1. **Tests E2E Playwright** : créer `tests/e2e/` avec Playwright (`npm init playwright@latest`) — scénarios : connexion joueur → console d'ordres → soumettre un ordre de construction → vérifier confirmation, et inscription → déconnexion → reconnexion
2. **Tests de charge passage de tour** : créer `tests/load/tour_k6.js` avec k6 — simuler 50 joueurs soumettant des ordres en parallèle sur 60 secondes, puis mesurer la durée de `Start newRound` — objectif : tour < 30 secondes pour 50 joueurs
3. Intégrer les tests E2E dans CI (GitHub Actions) en mode headless
4. Les tests de charge sont à exécuter manuellement (non bloquants en CI)

**Fichiers concernés :** Créer `tests/e2e/`, `tests/load/tour_k6.js`

---

### P5-07 — [DOC] ADR, glossaire et compilation des règles en HTML

**Problème :** Aucune décision architecturale n'est documentée — pourquoi Java pour le moteur, pourquoi PHP pour l'interface, pourquoi la sérialisation Java, pourquoi le stockage par tour. Ces décisions sont irréversibles à court terme mais incompréhensibles pour un nouveau mainteneur. Les règles du jeu en Markdown ne sont pas publiées sous forme consultable.

**Action :**
1. **`docs/ADR/`** : créer un fichier par décision architecturale majeure au format Nygard (Contexte → Décision → Conséquences) :
   - `ADR-001-java-engine.md` : pourquoi Java pour la logique de jeu
   - `ADR-002-php-frontend.md` : pourquoi PHP pour l'interface web
   - `ADR-003-java-serialization.md` : pourquoi la sérialisation Java vs SQL vs JSON
   - `ADR-004-per-turn-storage.md` : pourquoi stocker les données par tour
2. **`scripts/build-docs.sh`** (étendre P4-09) : ajouter la génération d'un site de règles complet avec un CSS minimaliste et une table des matières automatique depuis les fichiers `rules/*.md`
3. **`docs/GOTCHAS.md`** : liste des 10 pièges connus pour un nouveau développeur (tour bloqué si `tourN+1/` existe, sérialisation cassée après rename de classe, `FAKE_TURN=true` oublié, CRLF dans les scripts, `connect.txt` non créé, etc.)

**Fichiers concernés :** Créer `docs/ADR/`, `docs/GOTCHAS.md`, `scripts/build-docs.sh`

---

### P5-05 — [UX] Tutoriel interactif premier tour

**Problème :** Même après les améliorations d'onboarding (P3-29), un nouveau joueur reçoit son premier rapport et se retrouve devant 60+ ordres disponibles sans savoir lesquels sont prioritaires ni dans quel ordre les passer. Un tutoriel interactif guidé permettrait de réduire le taux d'abandon des nouveaux joueurs.

**Action :**
1. Créer une table `aa_tutoriel_progress (commandant_id INT, etape INT, completed_at DATETIME)` pour tracker la progression du tutoriel
2. Pour les commandants à leur tour 1, afficher un overlay de tutoriel dans la console d'ordres indiquant l'ordre recommandé : "Commencez par construire un bâtiment de production → puis planifiez vos recherches → puis donnez vos directives de flottes"
3. Chaque étape du tutoriel met en surbrillance (`outline: 3px solid var(--accent); animation: pulse`) la section de menu correspondante
4. Afficher un indicateur de progression "Étape 2/5" et permettre de quitter le tutoriel à tout moment (ne plus le voir = marquer toutes les étapes comme complètes)
5. Le tutoriel est optionnel et ne bloque pas l'accès aux autres fonctionnalités

**Fichiers concernés :** `php/ordres/ordres.php` (après P1-12), `divers/base_sheril.sql` (nouvelle table), `php/assets/css/styles.sass`, `php/assets/js/script.js`

---

### P5-01 — Migrer la persistance de la sérialisation Java vers JSON

**Problème :** La sérialisation Java native est illisible, non-versionnée et fragile aux changements de code.

**Action :**
1. Ajouter Jackson (`com.fasterxml.jackson.core:jackson-databind`) dans `pom.xml`
2. Pour chaque classe sérialisée (`Commandant`, `Systeme`, `Flotte`, etc.), annoter les champs avec `@JsonProperty` ou créer des `@JsonSerialize` custom
3. Créer un `UniversRepository` avec méthodes `sauvegarder(ContexteJeu)` et `charger(): ContexteJeu` utilisant Jackson
4. Les fichiers `.txt` binaires deviennent des `.json` lisibles et diffables
5. Écrire un script de migration one-shot qui charge l'ancienne sérialisation et écrit le nouveau JSON (à exécuter une fois sur les données existantes)

**Fichiers concernés :** Toutes les classes `Serializable`, créer `sources/zIgzAg/jeu/oceane/UniversRepository.java`, dépend de P3-03 et P3-04

---

### P5-02 — Séparer les variables de configuration sensibles dans `.env`

**Problème :** Les credentials sont dupliqués entre `docker-compose.yml` (hardcodés), `config.properties.sample` et `php/secure/connect.txt.sample`. Le `docker-compose.yml` a les mots de passe en clair dans le dépôt.

**Action :**
1. Créer un fichier `.env.sample` à la racine avec toutes les variables (`MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `DISCORD_WEBHOOK_URL`, etc.)
2. Ajouter `.env` dans `.gitignore`
3. Mettre à jour `docker-compose.yml` pour utiliser `${MYSQL_PASSWORD}` via le fichier `.env`
4. `config.properties` et `php/secure/connect.txt` peuvent lire les mêmes variables d'environnement au lieu de dupliquer les valeurs

**Fichiers concernés :** Créer `.env.sample`, `.gitignore`, `docker-compose.yml`, `config.properties.sample`

---

### P5-03 — Ajouter un système de migration de base de données

**Problème :** Le schéma MySQL est dans un seul fichier `divers/base_sheril.sql`. Toute modification de schéma nécessite de recréer la base ou d'appliquer manuellement des `ALTER TABLE`.

**Action :**
1. Adopter Flyway (`org.flywaydb:flyway-core`) dans le projet Java (via Maven)
2. Créer `src/main/resources/db/migration/V1__schema_initial.sql` reprenant le contenu de `base_sheril.sql`
3. Les futures évolutions de schéma deviennent `V2__ajout_hash_password.sql`, `V3__...`, etc.
4. Appeler `Flyway.configure().dataSource(...).load().migrate()` au démarrage du moteur Java

**Fichiers concernés :** `pom.xml`, créer `src/main/resources/db/migration/`, dépend de P3-04

---

### P5-04 — Mettre en place un pipeline CI simple (GitHub Actions)

**Problème :** Aucune validation automatique du code. Les CRLF dans les scripts, les erreurs de compilation, les regressions passent inaperçus.

**Action :**
1. Créer `.github/workflows/ci.yml`
2. Job `build` : checkout → `mvn clean package` (compile + jar) sur ubuntu-latest avec JDK 21
3. Job `lint-scripts` : `find scripts/ -name "*.sh" -exec file {} \;` pour détecter les CRLF
4. Optionnel : job `test` exécutant `mvn test` une fois P3-07 terminé

**Fichiers concernés :** Créer `.github/workflows/ci.yml`, dépend de P3-04

---

## Récapitulatif des priorités

| ID | Titre | Domaine | Priorité | Effort estimé | Dépendances |
|----|-------|---------|----------|---------------|-------------|
| P1-01 | Hacher les mots de passe | Sécu | 🔴 CRITIQUE | 2h | — |
| P1-02 | Requêtes PDO préparées (PHP) | Sécu | 🔴 CRITIQUE | 4h | — |
| P1-03 | Protection CSRF | Sécu | 🔴 CRITIQUE | 2h | — |
| P1-04 | Régénération session login | Sécu | 🔴 CRITIQUE | 30min | — |
| P1-05 | XSS éditeur Quill forum | Sécu | 🔴 CRITIQUE | 3h | — |
| P1-06 | Pseudo MJ hardcodé en clair | Sécu | 🔴 CRITIQUE | 1h | — |
| P1-07 | Injection SQL Java (SessionSQL) | Sécu | 🔴 CRITIQUE | 4h | — |
| P1-08 | Injection commande ProcessBuilder SSH | Sécu | 🔴 CRITIQUE | 2h | — |
| P1-09 | IDOR — contrôle d'accès brisé sur ordres | Sécu | 🔴 CRITIQUE | 4h | P1-02 |
| P1-10 | Secret OAuth Discord hardcodé | Sécu | 🔴 CRITIQUE | 1h | — |
| P1-11 | IDOR + path traversal dans download.php | Sécu | 🔴 CRITIQUE | 2h | — |
| P1-12 | Supprimer FRAMESET → layout CSS console ordres | UX | 🔴 CRITIQUE | 6h | — |
| P1-13 | chmod 0777 php/live/ → permissions correctes | DevOps | 🔴 CRITIQUE | 30min | — |
| P1-14 | Supprimer JAR obsolètes (mail.jar, pircbot.jar) | CVE | 🔴 CRITIQUE | 2h | P3-04 |
| P1-16 | unserialize() sur GET non validé → RCE (principal.txt) | Sécu | 🔴 CRITIQUE | 1h | — |
| P1-17 | LFI + injection SQL via $table dans principal.txt | Sécu | 🔴 CRITIQUE | 1h | — |
| P1-18 | Injection SQL dans elimine.txt (SHOW COLUMNS + DELETE) | Sécu | 🔴 CRITIQUE | 30min | P1-17 |
| P1-19 | Injection SQL dans insert.txt + affiche.txt via $table | Sécu | 🔴 CRITIQUE | 30min | P1-17 |
| P1-15 | Mots de passe envoyés en clair par email | RGPD | 🔴 CRITIQUE | 3h | P1-01 |
| P2-01 | Corriger double addNewGalaxy | Java | 🟠 URGENT | 15min | — |
| P2-02 | Sauvegardes atomiques | Java | 🟠 URGENT | 3h | — |
| P2-03 | Backup avant passage de tour | Java | 🟠 URGENT | 2h | — |
| P2-04 | serialVersionUID cohérents | Java | 🟡 IMPORTANT | 2h | — |
| P2-05 | Retirer user-scalable=no | Front | 🔴 CRITIQUE | 30min | — |
| P2-06 | Corriger outline:none → focus-visible | Front | 🔴 CRITIQUE | 1h | — |
| P2-07 | Corriger les contrastes insuffisants | Front | 🟠 URGENT | 2h | — |
| P2-08 | Fuites de ressources — try-with-resources | Java | 🔴 CRITIQUE | 4h | — |
| P2-09 | Fermer le BufferedWriter statique Combat | Java | 🟠 URGENT | 1h | — |
| P2-10 | Fermer la connexion MySQL ReceptionOrdres | Java | 🟠 URGENT | 2h | — |
| P2-11 | NPE critiques non protégées | Java | 🟠 URGENT | 3h | — |
| P2-12 | Headers HTTP de sécurité manquants | Sécu | 🔴 CRITIQUE | 2h | — |
| P2-13 | Cookies de session non sécurisés | Sécu | 🔴 CRITIQUE | 1h | — |
| P2-14 | display_errors=1 en production | Sécu | 🟠 URGENT | 1h | — |
| P2-15 | Actions destructives via GET sans confirmation | UX | 🟠 URGENT | 2h | P1-03 |
| P2-18 | Migrer MyISAM → InnoDB (100+ tables) | DBA | 🔴 CRITIQUE | 3h | — |
| P2-23 | Fuites mémoire structurelles (DEBRIS, PV history) | Perf | 🟠 URGENT | 3h | — |
| P2-19 | Transaction MySQL dans deroulementOrdres() | DBA | 🔴 CRITIQUE | 2h | P2-18 |
| P2-20 | Corriger incohérence _statistiques vs statistiques | DBA | 🔴 CRITIQUE | 1h | — |
| P2-21 | Backups automatisés MySQL et données Java | DevOps | 🔴 CRITIQUE | 4h | — |
| P2-22 | Utilisateur root dans le conteneur Docker | DevOps | 🟠 URGENT | 2h | — |
| P2-16 | Feedback manquant après soumission d'ordres | UX | 🟠 URGENT | 3h | P1-12 |
| P2-17 | États vides non gérés dans les ordres | UX | 🟠 URGENT | 3h | — |
| P3-01 | Typer les collections Java (génériques) | Java | 🟡 IMPORTANT | 4h | — |
| P3-02 | Remplacer dispatch réflexion ordres | Java | 🟡 IMPORTANT | 8h | — |
| P3-03 | Extraire God Class Univers | Java | 🟡 IMPORTANT | 16h | P3-01 |
| P3-04 | Adopter Maven | Java | 🟡 IMPORTANT | 3h | — |
| P3-05 | Logger structuré (SLF4J) | Java | 🟡 IMPORTANT | 4h | P3-04 |
| P3-06 | Templates rapports (Mustache) | Java | 🟢 SOUHAITABLE | 16h | P3-04 |
| P3-07 | Tests unitaires Combat | Java | 🟢 SOUHAITABLE | 8h | P3-03 |
| P3-08 | Supprimer style-sheril.css | Front | 🟠 URGENT | 1h | — |
| P3-09 | Extraire styles inline en classes CSS | Front | 🟡 IMPORTANT | 4h | P3-08 |
| P3-10 | Unifier boilerplate HTML pages races | Front | 🟡 IMPORTANT | 2h | — |
| P3-11 | Breakpoints responsive + support tactile | Front | 🟡 IMPORTANT | 4h | P3-08 |
| P3-12 | Accessibilité sémantique tableaux/nav | Front | 🟡 IMPORTANT | 3h | — |
| P3-13 | Optimiser images (lazy, srcset, WebP) | Front | 🟡 IMPORTANT | 4h | — |
| P3-14 | Robustifier les scripts JavaScript | Front | 🟡 IMPORTANT | 2h | — |
| P3-15 | Ajouter meta tags SEO manquants | Front | 🟢 SOUHAITABLE | 2h | — |
| P3-16 | Traiter correctement les exceptions Java | Java | 🟠 URGENT | 4h | — |
| P3-17 | Supprimer le code mort et commenté | Java | 🟡 IMPORTANT | 2h | — |
| P3-18 | Dédupliquer le code des plans vaisseaux | Java | 🟡 IMPORTANT | 3h | — |
| P3-19 | Corriger les inefficacités de performance | Java | 🟡 IMPORTANT | 3h | — |
| P3-20 | Deadlock d'initialisation SherilLogger | Java | 🟡 IMPORTANT | 1h | — |
| P3-21 | hashCode() comme clé de Map | Java | 🟡 IMPORTANT | 2h | — |
| P3-22 | Rate limiting login et ordres | Sécu | 🟠 URGENT | 4h | P1-02 |
| P3-23 | Race condition ordres (transactions) | Sécu | 🟠 URGENT | 3h | P1-02 |
| P3-24 | SSRF via URL Discord webhook | Sécu | 🟡 IMPORTANT | 1h | — |
| P3-25 | Logging des événements de sécurité | Sécu | 🟡 IMPORTANT | 4h | P3-05 |
| P3-26 | Intégrité des ordres par signature HMAC | Sécu | 🟢 SOUHAITABLE | 6h | P1-02 |
| P3-32 | Clés étrangères manquantes dans le schéma | DBA | 🟡 IMPORTANT | 3h | P2-18 |
| P3-33 | Index manquants (diviser_flotte, _post, stats) | DBA | 🟡 IMPORTANT | 2h | — |
| P3-34 | Migrer charset utf8 → utf8mb4 globalement | DBA | 🟡 IMPORTANT | 2h | P2-18 |
| P3-35 | Contraintes NOT NULL et CHECK manquantes | DBA | 🟡 IMPORTANT | 2h | P2-18 |
| P3-36 | Éliminer N+1 dans chargerDescriptionTables() | DBA | 🟡 IMPORTANT | 2h | — |
| P3-37 | Scripts shell robustes et idempotents | DevOps | 🟡 IMPORTANT | 3h | — |
| P3-38 | Health checks, limites ressources, réseau Docker | DevOps | 🟡 IMPORTANT | 3h | — |
| P3-39 | Infrastructure de tests (JUnit 5, Mockito, TC) | Tests | 🟡 IMPORTANT | 4h | P3-04 |
| P3-40 | Tests unitaires combat, ordres, budget | Tests | 🟡 IMPORTANT | 8h | P3-39, P3-03 |
| P3-41 | Documentation technique (INSTALL, ARCHI, DEBUG) | Doc | 🟡 IMPORTANT | 6h | — |
| P3-42 | Documenter constantes magiques de Const.java | Doc | 🟡 IMPORTANT | 4h | — |
| P3-43 | Épingler versions CDN + auto-héberger Google Fonts | CVE | 🟡 IMPORTANT | 3h | — |
| P3-44 | Pattern.compile() statique + recalculs en boucle | Perf | 🟡 IMPORTANT | 2h | — |
| P3-45 | Paralléliser génération rapports (ExecutorService) | Perf | 🟡 IMPORTANT | 4h | — |
| P3-46 | Configurer options JVM dans docker-compose.yml | Perf | 🟡 IMPORTANT | 1h | — |
| P3-47 | Batcher requêtes MySQL de ReceptionOrdres | Perf | 🟡 IMPORTANT | 4h | P1-07 |
| P3-27 | Tableau de bord différencié connecté/non-connecté | UX | 🟡 IMPORTANT | 6h | — |
| P3-28 | Indicateur de page active et breadcrumbs | UX | 🟡 IMPORTANT | 3h | P1-12 |
| P3-29 | Onboarding nouveau joueur | UX | 🟡 IMPORTANT | 8h | P3-27 |
| P3-30 | Aide contextuelle inline dans les ordres | UX | 🟡 IMPORTANT | 4h | P1-12 |
| P3-31 | Cohérence terminologique et composants UI | UX | 🟡 IMPORTANT | 4h | P3-09 |
| P4-01 | PHP 5.6 → PHP 8.2 | Infra | 🟠 URGENT | 8h | P1-02 |
| P4-02 | MySQL 5.7 → MySQL 8 | Infra | 🟠 URGENT | 4h | — |
| P4-03 | Remplacer driver JDBC obsolète | Java | 🟡 IMPORTANT | 1h | P3-04 |
| P4-04 | Adopter Twig comme moteur de templates | Front | 🟢 SOUHAITABLE | 16h | P1-02 |
| P4-05 | Moderniser le code vers Java 21 | Java | 🟡 IMPORTANT | 8h | P3-01 |
| P4-06 | Configurer MySQL pour les performances | DevOps | 🟢 SOUHAITABLE | 2h | — |
| P4-10 | Politique de confidentialité + mentions légales + cookie | RGPD | 🟠 URGENT | 8h | — |
| P4-11 | Droits RGPD utilisateurs (accès, effacement, portabilité) | RGPD | 🟠 URGENT | 12h | P4-10 |
| P4-12 | Politique de rétention des données | RGPD | 🟡 IMPORTANT | 6h | P4-11 |
| P4-07 | Tests intégration Testcontainers + PHP Pest | Tests | 🟢 SOUHAITABLE | 12h | P3-39, P2-18 |
| P4-08 | Doc flux ordres + format données sérialisées | Doc | 🟢 SOUHAITABLE | 6h | — |
| P4-09 | Doc joueur (référence ordres, quickstart) | Doc | 🟢 SOUHAITABLE | 8h | — |
| P5-01 | Persistance JSON (Jackson) | Java | 🟢 SOUHAITABLE | 24h | P3-03, P3-04 |
| P5-02 | Variables d'env dans `.env` | Infra | 🟢 SOUHAITABLE | 2h | — |
| P5-03 | Migrations Flyway | Java | 🟢 SOUHAITABLE | 4h | P3-04 |
| P5-04 | CI GitHub Actions | Infra | 🟢 SOUHAITABLE | 2h | P3-04 |
| P5-05 | Tutoriel interactif premier tour | UX | 🟢 SOUHAITABLE | 12h | P3-27, P1-12 |
| P5-06 | Tests E2E Playwright + tests de charge k6 | Tests | 🟢 SOUHAITABLE | 16h | P4-07 |
| P5-07 | ADR + glossaire + compiler règles en HTML | Doc | 🟢 SOUHAITABLE | 8h | P4-09 |
| P5-08 | DPA Discord + protection mineurs + polices auto-hébergées | RGPD | 🟢 SOUHAITABLE | 4h | P4-10 |
| P5-09 | Scan CVE automatique dans CI (Trivy, Dependabot) | CVE | 🟢 SOUHAITABLE | 3h | P5-04 |
| P5-10 | Optimiser combat O(N²) + parallélisation | Perf | 🟢 SOUHAITABLE | 16h | P3-45 |
