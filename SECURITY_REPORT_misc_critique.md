# Rapport de vulnérabilité — Vulnérabilités de configuration critique

**Date :** 2026-08-28  
**Sévérité :** CRITIQUE  
**Statut :** Non corrigé

---

## Résumé

Trois vulnérabilités de configuration indépendantes, toutes de sévérité critique, regroupées dans ce rapport.

---

## Vuln 1 — Mots de passe envoyés en clair par email (`plop.php`)

### Localisation

| Fichier | Ligne | Problème |
|---------|-------|---------|
| `php/plop.php` | ~25, 34 | `$message .= "Mot de passe : $password\n\n"` |

### Description

`php/plop.php` lit le mot de passe en clair depuis la base de données et l'inclut dans le corps d'un email :

```php
$password = $row['MOT_DE_PASSE'];
$message .= "Mot de passe : $password\n\n";
```

Les mots de passe circulent en clair dans les emails et sont stockés dans les boîtes mail des destinataires indéfiniment. C'est une violation directe de l'Art. 32 RGPD.

### Correctif

Supprimer tout envoi de mot de passe par email. Implémenter un lien de réinitialisation à usage unique expirant après 24h :

```php
$token = bin2hex(random_bytes(32));
// Stocker hash($token) en base avec expiration
// Envoyer uniquement : "Cliquez ici pour réinitialiser : https://sheril.../reset?token=$token"
```

---

## Vuln 2 — Permissions `0777` sur `php/live/` (`init.sh`)

### Localisation

| Fichier | Ligne | Problème |
|---------|-------|---------|
| `scripts/init.sh` | 3 | `chmod -R 0777 php/live/` |

### Description

Le script d'initialisation applique des permissions `0777` (lecture/écriture/exécution pour tous) sur le répertoire `php/live/`. Depuis le conteneur Apache, n'importe quel processus peut écrire dans ce répertoire. Combiné avec une vulnérabilité d'upload ou d'injection, un attaquant peut déposer un fichier PHP et l'exécuter directement (webshell).

### Correctif

```bash
# AVANT
chmod -R 0777 php/live/

# APRÈS
chown -R www-data:www-data php/live/
chmod -R 0755 php/live/
```

---

## Vuln 3 — Injection SQL côté Java dans `SessionSQL.java`

### Localisation

| Fichier | Ligne | Problème |
|---------|-------|---------|
| `sources/zIgzAg/sql/SessionSQL.java` | `champsTraduction1()` à `champsTraduction4()` | Valeurs concaténées directement dans le SQL |

### Description

Les méthodes `champsTraduction*()` construisent des fragments SQL par concaténation directe sans échappement :

```java
// SessionSQL.java — champsTraduction3()
retour.append(v[i]);  // v[i] non échappé, concaténé directement dans le SQL
```

Ces méthodes sont appelées depuis `ReceptionOrdres` pour construire des `INSERT`/`UPDATE` à partir des paramètres d'ordres joueurs.

### Correctif

Remplacer les méthodes de construction de chaînes SQL par des `PreparedStatement` avec paramètres liés :

```java
PreparedStatement stmt = connection.prepareStatement(
    "INSERT INTO " + tableName + " (col1, col2) VALUES (?, ?)"
);
stmt.setString(1, values[0]);
stmt.setString(2, values[1]);
stmt.executeUpdate();
```

---

## Timeline

| Date | Événement |
|------|-----------|
| 2026-08-28 | Découvertes lors des audits PHP et Java |
| 2026-08-28 | Rapport rédigé |
| En attente | Corrections |
