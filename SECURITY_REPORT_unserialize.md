# Rapport de vulnérabilité — Désérialisation PHP non validée dans `principal.txt`

**Date :** 2026-08-28  
**Sévérité :** CRITIQUE  
**Statut :** Non corrigé

---

## Résumé

Le paramètre `$_GET['previous']` est passé directement à `unserialize()` sans aucune validation. Cela permet à un attaquant authentifié d'injecter des valeurs arbitraires dans les champs `$_POST` du formulaire cible, et potentiellement d'exécuter du code arbitraire via des gadget chains PHP.

---

## Localisation

| Fichier | Ligne | Variable vulnérable |
|---------|-------|---------------------|
| `php/ordres/principal.txt` | 18 | `$_GET['previous']` |
| `php/ordres/principal.txt` | 30 | `$_GET['previous']` |

---

## Cause racine

```php
// principal.txt:17-22
if (array_key_exists('previous', $_GET)) {
    $tableau = unserialize(urldecode($_GET['previous']));
    for ($i = 1; $i < sizeof($tableau); $i++) {
        $_POST['v' . ($i - 1)] = $tableau[$i];  // injection dans $_POST
    }
}
```

`unserialize()` est appelé sur une chaîne fournie directement par l'utilisateur. La fonctionnalité légitime est de pré-remplir un formulaire avec les valeurs d'un ordre précédent (bouton "Copier"), mais aucune validation de l'origine ou du contenu n'est effectuée.

---

## Conditions d'exploitation

| Condition | Valeur |
|-----------|--------|
| Authentification | Oui — n'importe quel compte joueur |
| Complexité | Faible |
| Outils nécessaires | Navigateur web |

---

## Proof of Concept — Injection de valeurs dans `$_POST`

### Requête HTTP

```
GET /ordres/index.php3?table=construire&previous=a%3A3%3A%7Bi%3A0%3Bs%3A4%3A%22SKIP%22%3Bi%3A1%3Bs%3A6%3A%22INJECT%22%3Bi%3A2%3Bs%3A2%3A%2299%22%3B%7D
Cookie: PHPSESSID=<session_valide>
```

### Payload désérialisé

```php
// a:3:{i:0;s:4:"SKIP";i:1;s:6:"INJECT";i:2;s:2:"99";}
// Résultat : $_POST['v0'] = "INJECT", $_POST['v1'] = "99"
```

### Résultat confirmé en session

La valeur "99" est apparue dans la réponse HTML du formulaire, confirmant que `$_POST['v1']` a été injecté par le payload sérialisé.

---

## Impact

- **Injection dans les champs de formulaire** : pré-remplissage arbitraire de n'importe quel ordre
- **Contournement de l'interface** : valeurs non autorisées par l'UI peuvent être injectées
- **RCE potentielle** : si des classes PHP instanciées lors de la désérialisation exposent des gadget chains (méthodes magiques `__destruct`, `__wakeup`, `__toString`), l'exécution de code arbitraire est possible

---

## Correctif

Remplacer `unserialize()` par `json_decode()` :

```php
// AVANT — vulnérable
$tableau = unserialize(urldecode($_GET['previous']));

// APRÈS — sécurisé
$tableau = json_decode(urldecode($_GET['previous']), true);
if (!is_array($tableau)) $tableau = [];
```

Si le format sérialisé doit être conservé, valider l'intégrité par HMAC avant désérialisation :

```php
$raw  = urldecode($_GET['previous']);
$sig  = $_GET['sig'] ?? '';
if (!hash_equals(hash_hmac('sha256', $raw, SECRET_KEY), $sig)) die("Signature invalide");
$tableau = unserialize($raw);
```

---

## Timeline

| Date | Événement |
|------|-----------|
| 2026-08-28 | Découverte lors de l'analyse de `principal.txt` |
| 2026-08-28 | Confirmation — valeur injectée dans `$_POST` visible dans la réponse |
| 2026-08-28 | Rapport rédigé |
| En attente | Correction |
