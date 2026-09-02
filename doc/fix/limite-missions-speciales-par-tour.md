# [INVALIDÉ] Absence de la limite "3 missions spéciales par tour"

> **Correction (postérieure à ce rapport, exécution manuelle de la
> procédure du §4) : l'écart décrit ci-dessous est réfuté.** Le
> contournement du formulaire PHP insère bien 4 lignes en base (§1,
> vérifié sur les tours 10/11 réels), mais aucune des 4 missions n'a
> été exécutée par Java — pas même les 3 premières, légitimes. Cause :
> `ReceptionOrdres.getOrdres()` (non examinée en §2) écarte la
> **totalité** des lignes `services_speciaux` d'un commandant dès que
> leur nombre dépasse `Const.NOMBRE_LIMITE_SERVICES_SPECIAUX = 3` —
> détail complet en §6. Le correctif proposé en §3 **ne doit pas être
> appliqué**, la limite existe déjà. Rapport conservé pour la trace de
> l'investigation.

- **Fichiers concernés** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthode `services_speciaux` — voir §6 : la méthode réellement en
  cause pour la limite est `getOrdres()`/`resoudreMethode()`, pas
  celle-ci), `sources/zIgzAg/jeu/oceane/Commandant.java`
  (méthode `effectuerMissionSpeciale`)
- **Nature** : ~~contrôle manquant côté serveur~~ — en réalité un
  contrôle existant, situé à un autre niveau que celui d'abord examiné.
- **Statut** : **invalidé**, voir correction en §6, confirmée par
  exécution manuelle de la procédure du §4 sur des tours réels.
  Correctif proposé en §3 **non appliqué, et ne devant pas l'être**.

## 1. Comportement observé

Les règles du jeu (`rules/Mise à jour/7. Relations entre les
commandants.md` §7.3, cohérentes avec l'ancien `rules/services_speciaux.md`
§7.3) sont explicites : *"Vos services spéciaux ne peuvent effectuer
plus de trois actions par tour."* / *"À chaque tour, vous pouvez
donner 3 ordres de mission."*

`Commandant.effectuerMissionSpeciale` (`Commandant.java:3016`) traite
chaque ordre `services_speciaux` reçu sans compteur ni limite, et
`ReceptionOrdres.services_speciaux` (`ReceptionOrdres.java:547-550`)
appelle cette méthode une fois par ligne d'ordre sans plafond.

Le formulaire standard (`php/ordres/fr/choix/services_speciaux.txt:4-8`)
masque son propre champ de saisie dès que 3 ordres existent déjà pour
le commandant dans la table `services_speciaux` ce tour-ci :

```php
$max = 3;
$result = mysql($base,"SELECT * FROM $table WHERE NUMERO='$commandant'");
$nb_lignes = mysql_num_rows($result);
if($nb_lignes<$max){
    // ... <FORM> de saisie ...
```

Mais l'endpoint qui insère réellement l'ordre
(`index.php3?table=services_speciaux`, traité par le script générique
`php/ordres/insert.txt`, branche générique lignes 120-145) ne revérifie
jamais ce compte avant d'insérer une nouvelle ligne — le même
mécanisme, à l'identique, que celui déjà confirmé par test manuel sur
l'enrôlement de lieutenant.

## 2. Cause racine

```java
// Commandant.java:3016-3020
public boolean effectuerMissionSpeciale(Position pos, int typeMission, int numPlanete) {
    float attaque = getBudgetServiceSpeciaux();
    if (typeMission > Const.NB_MISSIONS)
        return Univers.ajouterErreur(getNomNumeroHtml(),
                "ER_COMMANDANT_MISSION_SPECIALE_0000");
    // ... aucune vérification du nombre de missions déjà effectuées ce tour ...
```

```java
// ReceptionOrdres.java:547-550
public void services_speciaux(String[] o) {
    c[iC].effectuerMissionSpeciale(Position.traduction(o[0]), tInt(o[1]),
            pla(o[2]));
}
```

Aucun des deux niveaux (traitement de l'ordre côté `ReceptionOrdres`,
exécution de la mission côté `Commandant`) ne compte les missions déjà
effectuées par ce commandant ce tour-ci. Recherche exhaustive d'un
mécanisme générique de limitation du nombre d'ordres d'un même type par
tour dans tout `sources/` : aucun résultat (cf. audit
`doc/audit-regles-vs-code.md` §5.4 sur la branche
`audit/regles-vs-code-technologies`).

## 3. Correctif proposé

Compter, en amont de `effectuerMissionSpeciale`, le nombre de missions
déjà effectuées par le commandant ce tour-ci, et refuser au-delà de
`Const.NB_MISSIONS_MAX_PAR_TOUR` (constante à créer, valeur 3). Le
compteur le plus simple à maintenir est un champ sur `Commandant`,
incrémenté à chaque mission réussie et remis à zéro en début de tour —
à l'image de `nombreDeTransfertEffectues`-style de compteurs déjà
présents ailleurs dans la classe pour d'autres quotas par tour (à
vérifier/aligner sur le patron existant plutôt que réinventer un
mécanisme).

```diff
 public boolean effectuerMissionSpeciale(Position pos, int typeMission, int numPlanete) {
+	if (nombreMissionsSpecialesCeTour >= Const.NB_MISSIONS_MAX_PAR_TOUR)
+		return Univers.ajouterErreur(getNomNumeroHtml(),
+				"ER_COMMANDANT_MISSION_SPECIALE_0016");
+
 	float attaque = getBudgetServiceSpeciaux();
 	if (typeMission > Const.NB_MISSIONS)
 		return Univers.ajouterErreur(getNomNumeroHtml(),
 				"ER_COMMANDANT_MISSION_SPECIALE_0000");
 	...
+	nombreMissionsSpecialesCeTour++;
 	return true;
 }
```

(Le point d'incrémentation exact — uniquement sur succès de mission, ou
dès la tentative — reste à trancher : les règles parlent de "3 ordres
de mission", ce qui suggère de compter chaque *ordre* passé plutôt que
chaque mission *réussie*. Le diff ci-dessus compte l'ordre, cohérent
avec cette lecture.)

Un nouveau champ `private int nombreMissionsSpecialesCeTour` sur
`Commandant`, remis à zéro dans la méthode de fin/début de tour qui
réinitialise déjà les autres compteurs similaires (à identifier — non
recherchée pour ce rapport), et une nouvelle constante
`Const.NB_MISSIONS_MAX_PAR_TOUR = 3` seraient nécessaires.

## 4. Vérification

**Exécutée manuellement**, sur le même principe que pour l'enrôlement
de lieutenant, entièrement côté PHP/navigateur (les joueurs n'ayant pas
accès au Java) :

1. Soumettre des ordres `services_speciaux` normalement via le
   formulaire (`index.php3?table=services_speciaux`).
2. Recharger la page : le formulaire disparaît une fois 3 ordres
   enregistrés (`$nb_lignes<3` devenu faux).
3. Dans le contexte de la frame affichant `index.php3?table=...`,
   exécuter depuis la console JavaScript du navigateur :
   ```js
   fetch('index.php3?table=services_speciaux', {
     method: 'POST',
     credentials: 'same-origin',
     headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
     body: new URLSearchParams({
       v0: '<code système, différent des précédents>',
       v1: '0',
       v2: '100',
       ajout: 'Envoyer cet ordre'
     })
   }).then(r => r.text()).then(html => console.log(html.length, html));
   ```
4. Recharger `index.php3?table=list_ordres`.

**Résultat observé (tour 10, commandant 1)** : 4 lignes ont bien été
insérées en base (confirmé sur `data/tour10/dump.sql` : systèmes
`0_15_11`, `0_15_10`, `0_15_13`, `0_13_28`, toutes type 0). Mais le
rapport individuel du tour suivant
(`data/tour11/rapports/1tour11/rapport.xml`, section `<messages>`) ne
contient **aucun** événement de mission spéciale — ni succès, ni échec,
pour aucune des 4. Voir §6 pour l'explication : ce n'est pas un plafond
à 3 qui a été appliqué (auquel cas les 3 premières auraient produit un
événement), mais un rejet en bloc des 4.

## 5. Portée et limites du correctif proposé

- Corrige uniquement les missions spéciales (`effectuerMissionSpeciale`).
  Ne couvre pas les autres écarts apparentés du même audit (§4.6
  enrôlement de lieutenant, déjà traité sur sa propre branche ; §8.2
  don de technologie, traité sur sa propre branche ; §2.3 limite de 999
  unités par transfert inter-système, non traitée).
- Ne modifie ni le calcul des chances de succès d'une mission, ni ses
  effets (espionnage, sabotage, vol de technologie, propagande) —
  uniquement le nombre d'ordres acceptés par tour.
- Comme pour l'enrôlement de lieutenant, un correctif complet devrait
  aussi revoir la cohérence du comportement du formulaire PHP (message
  d'erreur explicite au lieu d'un champ simplement masqué), hors du
  périmètre Java de ce rapport.

## 6. Correction : la limite est en réalité appliquée par `ReceptionOrdres.getOrdres()`

L'exécution du test décrit en §4 sur les tours réels 10 et 11 a mis en
évidence un écart entre le résultat prévu (§4, "4 lignes... au lieu
des 3 maximum") et le résultat observé (aucune des 4 missions
exécutée). Ceci a conduit à relire `ReceptionOrdres.java` au-delà des
seules méthodes `services_speciaux`/`effectuerMissionSpeciale`
examinées en §2 :

```java
// ReceptionOrdres.java:146-158 (getOrdres(), appelée avant tout traitement)
if (... || ((index == Const.ORDRE_SERVICES_SPECIAUX) &&
                (a.size() > Const.NOMBRE_LIMITE_SERVICES_SPECIAUX)) || ...) {
    a = new ArrayList();
    Univers.ajouterErreur(c[iC].getNomNumeroHtml(), "ER_ORDRE_0002",
            Const.NOMS_TABLES_ORDRES[index]);
}
```

```java
// Const.java:755
NOMBRE_LIMITE_SERVICES_SPECIAUX = 3;
```

`getOrdres()` est appelée par `resoudreMethode()` **avant** que
`services_speciaux(o)` (et donc `effectuerMissionSpeciale`) ne soit
invoquée pour chaque ligne. Si le nombre de lignes en base pour ce
commandant dépasse 3, elle retourne un tableau **vide** : aucune des 4
missions soumises au tour 10 n'a donc jamais atteint
`Commandant.effectuerMissionSpeciale`, ce qui explique précisément
l'absence totale d'événement observée en §4 — pas un plafonnement aux
3 premières, un rejet du lot entier.

**Conséquence pour le joueur** : contourner le formulaire pour ajouter
une 4ᵉ mission ne rapporte rien — cela fait perdre les 3 missions
légitimes en plus de la quatrième.

Voir `doc/audit-regles-vs-code.md` §5.4 sur la branche
`audit/regles-vs-code-technologies` pour la version complète et à jour
de cette correction, incluse dans le même document que les deux cas
jumeaux (`doc/fix/limite-enchere-lieutenant-par-tour.md` et
`doc/fix/limite-don-technologie-par-tour.md`).
