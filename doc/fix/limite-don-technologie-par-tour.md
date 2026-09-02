# [INVALIDÉ] Absence de la limite "une seule technologie cédée par tour"

> **Correction (postérieure à ce rapport, exécution manuelle de la
> procédure du §4) : l'écart décrit ci-dessous est réfuté.** Le
> contournement du formulaire PHP insère bien 2 lignes en base (§1,
> vérifié sur les tours 10/11 réels : `robotI` et `transfoI` vers le
> commandant 2), mais aucun des 2 dons n'a été exécuté par Java — pas
> même le premier, légitime. Cause : `ReceptionOrdres.getOrdres()` (non
> examinée en §2) écarte la **totalité** des lignes
> `transferer_technologie` d'un commandant dès que leur nombre dépasse
> `Const.NOMBRE_LIMITE_DON_TECHNOLOGIE = 1` — détail complet en §6. Le
> correctif proposé en §3 **ne doit pas être appliqué**, la limite
> existe déjà. Rapport conservé pour la trace de l'investigation.

- **Fichiers concernés** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthode `transferer_technologie` — voir §6 : la méthode réellement
  en cause pour la limite est `getOrdres()`/`resoudreMethode()`, pas
  celle-ci), `sources/zIgzAg/jeu/oceane/Commandant.java`
  (méthode `transfertTechnologie`)
- **Nature** : ~~contrôle manquant côté serveur~~ — en réalité un
  contrôle existant, situé à un autre niveau que celui d'abord examiné.
- **Statut** : **invalidé**, voir correction en §6, confirmée par
  exécution manuelle de la procédure du §4 sur des tours réels.
  Correctif proposé en §3 **non appliqué, et ne devant pas l'être**.

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

**Exécutée manuellement**, sur le même principe que pour l'enrôlement
de lieutenant, entièrement côté PHP/navigateur (les joueurs n'ayant pas
accès au Java) :

1. Soumettre un premier don de technologie normalement via le
   formulaire (`index.php3?table=transferer_technologie`).
2. Recharger la page : le formulaire disparaît (`$nb_lignes==0` devenu
   faux).
3. Dans le contexte de la frame affichant `index.php3?table=...`,
   exécuter depuis la console JavaScript du navigateur :
   ```js
   fetch('index.php3?table=transferer_technologie', {
     method: 'POST',
     credentials: 'same-origin',
     headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
     body: new URLSearchParams({
       v0: '<numéro du commandant bénéficiaire>',
       v1: '<code d\'une technologie différente du 1er ordre>',
       v2: '0',
       ajout: 'Envoyer cet ordre'
     })
   }).then(r => r.text()).then(html => console.log(html.length, html));
   ```
4. Recharger `index.php3?table=list_ordres`.

**Résultat observé (tour 10, commandant 1 → commandant 2)** : 2 lignes
ont bien été insérées en base (confirmé sur `data/tour10/dump.sql` :
`(1, 2, 'robotI', 0)` et `(1, 2, 'transfoI', 0)`). Mais le rapport
individuel du commandant 1 pour le tour suivant
(`data/tour11/rapports/1tour11/rapport.xml`) ne contient aucun
événement de don de technologie, et une recherche des deux codes dans
les données sérialisées du tour suivant
(`data/tour11/donnees/comm.txt`) ne les trouve chacun qu'à raison d'une
seule occurrence — celle déjà connue du commandant 1 lui-même (visible
dans son propre rapport), pas de seconde occurrence chez le commandant
2 bénéficiaire. Aucun des deux dons n'a eu lieu. Voir §6 pour
l'explication.

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

## 6. Correction : la limite est en réalité appliquée par `ReceptionOrdres.getOrdres()`

L'exécution du test décrit en §4 sur les tours réels 10 et 11 a mis en
évidence un écart entre le résultat prévu (§4, "2 lignes... au lieu
d'une seule maximum") et le résultat observé (aucun des 2 dons
exécuté). Ceci a conduit à relire `ReceptionOrdres.java` au-delà des
seules méthodes `transferer_technologie`/`transfertTechnologie`
examinées en §2 :

```java
// ReceptionOrdres.java:146-158 (getOrdres(), appelée avant tout traitement)
if (... || ((index == Const.ORDRE_DON_TECHNOLOGIE) &&
                (a.size() > Const.NOMBRE_LIMITE_DON_TECHNOLOGIE)) || ...) {
    a = new ArrayList();
    Univers.ajouterErreur(c[iC].getNomNumeroHtml(), "ER_ORDRE_0002",
            Const.NOMS_TABLES_ORDRES[index]);
}
```

```java
// Const.java:756
NOMBRE_LIMITE_DON_TECHNOLOGIE = 1;
```

`getOrdres()` est appelée par `resoudreMethode()` **avant** que
`transferer_technologie(o)` (et donc `transfertTechnologie`) ne soit
invoquée pour chaque ligne. Si le nombre de lignes en base pour ce
commandant dépasse 1, elle retourne un tableau **vide** : aucun des 2
dons soumis au tour 10 n'a donc jamais atteint
`Commandant.transfertTechnologie`, ce qui explique précisément
l'absence totale d'effet observée en §4 — pas un plafonnement au
premier don, un rejet du lot entier.

**Conséquence pour le joueur** : contourner le formulaire pour ajouter
un 2ᵉ don ne rapporte rien — cela fait perdre le premier don légitime
en plus du second. Le point ouvert du §3 sur `technology_plan.php`
devient sans objet : puisque le formulaire classique fait déjà
respecter la limite côté Java (à la résolution), il n'y a pas de
correctif à appliquer qui risquerait d'entrer en conflit avec cette
page.

Voir `doc/audit-regles-vs-code.md` §8.2 sur la branche
`audit/regles-vs-code-technologies` pour la version complète et à jour
de cette correction, incluse dans le même document que les deux cas
jumeaux (`doc/fix/limite-enchere-lieutenant-par-tour.md` et
`doc/fix/limite-missions-speciales-par-tour.md`).
