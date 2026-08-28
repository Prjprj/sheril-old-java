# Rapport de vulnérabilité — IDOR et divulgation de chemin dans `download.php`

**Date :** 2026-08-28  
**Sévérité :** HAUTE  
**Statut :** Non corrigé

---

## Résumé

`download.php` présente deux problèmes combinés : un bug logique permettant de contourner la validation du numéro de tour, et un message d'erreur révélant le chemin absolu du serveur. Ces failles permettent à un joueur de tenter de télécharger des rapports de tours invalides et d'obtenir des informations sur la structure du serveur.

---

## Localisation

| Fichier | Ligne | Problème |
|---------|-------|---------|
| `php/auth/download.php` | 12 | Opérateur `&` (bitwise) au lieu de `&&` (logique) |
| `php/auth/download.php` | ~15 | Message d'erreur exposant le chemin complet |

---

## Cause racine

### Bug 1 — Opérateur bitwise au lieu de logique

```php
// download.php:12 — VULNÉRABLE
$tour = ($givenTurn > 0 & $givenTurn <= $currentTurn) ? $givenTurn : $currentTour;
//                     ^-- & (bitwise ET) au lieu de && (ET logique)
```

`&` est l'opérateur ET bit-à-bit, pas l'opérateur logique `&&`. Le comportement de la validation du tour est imprévisible pour certaines valeurs.

### Bug 2 — Divulgation du chemin serveur

```php
// download.php — VULNÉRABLE
if (!file_exists($file)) {
    exit("Fichier introuvable " . $file);  // expose /var/www/html/../rapports/...
}
```

Le chemin absolu complet est exposé dans le message d'erreur.

---

## Conditions d'exploitation

| Condition | Valeur |
|-----------|--------|
| Authentification | Oui |
| Complexité | Faible |
| Outils nécessaires | Navigateur web |

---

## Impact

- **Divulgation d'information** : chemin absolu du serveur révélé (`/var/www/html/rapports/...`)
- **Comportement imprévisible** : la validation du numéro de tour peut être contournée pour certaines valeurs en raison du bug bitwise
- **Note** : le numéro du joueur est lu depuis `$_SESSION` — l'accès aux rapports d'autres joueurs n'est pas possible sans compromission de session

---

## Correctif

```php
// AVANT — vulnérable
$tour = ($givenTurn > 0 & $givenTurn <= $currentTurn) ? $givenTurn : $currentTour;
if (!file_exists($file)) {
    exit("Fichier introuvable " . $file);
}

// APRÈS — corrigé
$tour = ($givenTurn > 0 && $givenTurn <= $currentTurn) ? $givenTurn : $currentTour;
if (!file_exists($file)) {
    http_response_code(404);
    exit("Fichier non trouvé");  // pas de chemin dans le message
}
```

---

## Timeline

| Date | Événement |
|------|-----------|
| 2026-08-28 | Découverte lors de l'audit de `download.php` |
| 2026-08-28 | Rapport rédigé |
| En attente | Correction |
