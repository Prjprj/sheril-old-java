# Absence de la limite "une seule technologie cédée par tour"

- **Fichiers concernés** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthode `transferer_technologie`), `sources/zIgzAg/jeu/oceane/Commandant.java`
  (méthode `transfertTechnologie`)
- **Nature** : contrôle manquant côté serveur — pas un problème de
  données ni de configuration. Atteignable en jeu normal via une simple
  requête HTTP, sans outillage particulier.
- **Statut** : cas identifié par analogie avec l'écart déjà confirmé sur
  l'enrôlement de lieutenant (`doc/fix/limite-enchere-lieutenant-par-tour.md`,
  branche `fix/enchere-lieutenant-limite-par-tour`) — même mécanisme
  exact (formulaire masqué par comptage de lignes, script d'insertion
  générique non protégé). Comportement établi par lecture de code ;
  **procédure de vérification empirique fournie ci-dessous, non encore
  exécutée**. Correctif proposé en §3, **non appliqué** sur cette
  branche.

## 1. Comportement observé

Les règles du jeu (`rules/Mise à jour/7. Relations entre les
commandants.md` §7.4, cohérentes avec l'ancien
`rules/produits_commerciaux_et_dons.md` §6.2) sont explicites : *"Vous
ne pouvez pas céder plus d'une technologie par tour."*

`Commandant.transfertTechnologie` (`Commandant.java:2491-2530`) ne
comporte aucun compteur ni verrou empêchant d'appeler cette méthode
plusieurs fois dans le même tour, vers la même cible ou des cibles
différentes.

Le formulaire standard (`php/ordres/fr/choix/transferer_technologie.txt:1-4`)
masque son propre champ de saisie dès qu'**une** ligne existe déjà pour
le commandant dans la table `transferer_technologie` ce tour-ci :

```php
$result = mysql($base,"SELECT * FROM $table WHERE NUMERO='$commandant'");
$nb_lignes = mysql_num_rows($result);
if($nb_lignes==0){
    // ... <FORM> de saisie ...
```

Mais l'endpoint qui insère réellement l'ordre
(`index.php3?table=transferer_technologie`, traité par le script
générique `php/ordres/insert.txt`, branche générique lignes 120-145) ne
revérifie jamais ce compte avant d'insérer une nouvelle ligne — le même
mécanisme, à l'identique, que celui déjà confirmé par test manuel sur
l'enrôlement de lieutenant. La table SQL elle-même ne porte aucune
contrainte d'unicité qui pourrait compenser :

```php
// php/divers/creer_tables.php3:227
CREATE TABLE transferer_technologie(NUMERO int, BENEFICIAIRE int, TECHNOLOGIE varchar(20), MODE int)
```

Une page plus récente, `technology_plan.php` (interface matricielle
donateur×bénéficiaire pour un groupe de commandants en partage de
technologies), limite accidentellement chaque donateur à un don par
soumission via un `break` après la première case remplie rencontrée
dans le POST (déjà documenté dans `doc/audit-regles-vs-code.md` §13.2)
— mais cette limitation ne s'applique qu'à cette page précise, pas au
formulaire classique décrit ci-dessus.

## 2. Cause racine

```java
// Commandant.java:2491-2519
public boolean transfertTechnologie(int destinataire, String codeTechno,
        int modeTransfert) {
    if (!Univers.existenceCommandant(destinataire))
        return Univers.ajouterErreur(...);
    if (!estTechnologieConnue(codeTechno))
        return Univers.ajouterErreur(...);
    // ... aucune vérification du nombre de technologies déjà cédées ce tour ...
    cible.ajouterTechnologieConnue(codeTechno);
    cible.suppressionDomaineDeRecherche(codeTechno);
    ...
}
```

```java
// ReceptionOrdres.java:679-681
public void transferer_technologie(String[] o) {
    c[iC].transfertTechnologie(tInt(o[0]), o[1], tInt(o[2]));
}
```

Aucune trace, dans `transfertTechnologie` ni dans son appelant, d'un
compteur de dons déjà effectués ce tour par le commandant courant.

## 3. Correctif proposé

Même patron que pour les missions spéciales (`doc/fix/limite-missions-speciales-par-tour.md`)
et l'enrôlement de lieutenant : compter en amont, refuser au-delà d'un
seuil de 1.

```diff
 public boolean transfertTechnologie(int destinataire, String codeTechno,
 		int modeTransfert) {
+	if (technologieDejaCedeeCeTour)
+		return Univers.ajouterErreur(getNomNumeroHtml(),
+				"ER_COMMANDANT_DON_TECHNOLOGIE_0003", codeTechno, destinataire);
+
 	if (!Univers.existenceCommandant(destinataire))
 		return Univers.ajouterErreur(getNomNumeroHtml(),
 				"ER_COMMANDANT_DON_TECHNOLOGIE_0000", codeTechno,
 				destinataire);
 	...
 	Univers.ajouterTransfert(this, cible, "technologie : " + codeTechno);
+	technologieDejaCedeeCeTour = true;
 	...
 }
```

Un nouveau champ `private boolean technologieDejaCedeeCeTour` sur
`Commandant`, remis à `false` en même temps que les autres indicateurs
"par tour" équivalents (à identifier — non recherchée pour ce rapport,
même remarque que pour le compteur de missions spéciales), et un
nouveau message d'erreur
(`ER_COMMANDANT_DON_TECHNOLOGIE_0003`) seraient nécessaires.

### Point ouvert à trancher avant d'appliquer ce correctif

`technology_plan.php` (la page matricielle) supprime et réinsère tous
les ordres `transferer_technologie` du groupe de partage à chaque
soumission (`DELETE FROM transferer_technologie WHERE NUMERO IN
(...)`, suivi d'au plus une ligne par donateur). Un correctif purement
Java doit rester compatible avec ce flux : il ne doit pas empêcher
cette page de fonctionner (un donateur y soumet toujours au plus un don
par son propre traitement `break`), mais il faut vérifier que la
remise à zéro du compteur `technologieDejaCedeeCeTour` en amont d'une
resoumission par cette page ne casse pas la mise à jour légitime d'un
don déjà choisi.

## 4. Vérification

**Non exécutée.** Procédure prévue, entièrement côté PHP/navigateur
(les joueurs n'ayant pas accès au Java), sur le même principe que celle
déjà exécutée et confirmée pour l'enrôlement de lieutenant :

1. Soumettre un premier don de technologie normalement via le
   formulaire (`index.php3?table=transferer_technologie`), vers un
   commandant A.
2. Recharger la page : le formulaire doit avoir disparu
   (`$nb_lignes==0` devenu faux).
3. Dans le contexte de la frame affichant `index.php3?table=...`,
   exécuter depuis la console JavaScript du navigateur :
   ```js
   fetch('index.php3?table=transferer_technologie', {
     method: 'POST',
     credentials: 'same-origin',
     headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
     body: new URLSearchParams({
       v0: '<numéro d\'un commandant B, différent de A, ou A lui-même avec une autre technologie>',
       v1: '<code d\'une technologie différente de celle du 1er ordre>',
       v2: '0',   // mode de transfert public (Const.DON_MODE_NORMAL = 0)
       ajout: 'Envoyer cet ordre'
     })
   }).then(r => r.text()).then(html => console.log(html.length, html));
   ```
4. Recharger `index.php3?table=list_ordres`.

**Résultat attendu si l'écart est confirmé** : 2 lignes de don de
technologie apparaissent pour ce commandant ce tour-ci, au lieu d'une
seule maximum annoncée par les règles.

## 5. Portée et limites du correctif proposé

- Corrige uniquement le don de technologie via le formulaire classique
  (`transfertTechnologie`). Ne couvre pas les autres écarts apparentés
  du même audit (§4.6 enrôlement de lieutenant, traité sur sa propre
  branche ; §5.4 missions spéciales, traité sur sa propre branche ;
  §2.3 limite de 999 unités par transfert inter-système, non traitée).
- Ne modifie ni le coût des modes de transfert discret/anonyme
  (`Const.SURCOUT_DON_TECHNO_CACHE`/`ANONYME`), ni la mécanique de
  révélation publique aléatoire (`Const.CHANCE_DON_TECHNO_PUBLIC`) —
  uniquement le nombre de dons acceptés par tour.
- Le point ouvert du §3 (cohérence avec `technology_plan.php`) doit
  être tranché avant application, pour ne pas casser le fonctionnement
  de cette page.
