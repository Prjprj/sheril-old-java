# Absence de la limite "3 missions spéciales par tour"

- **Fichiers concernés** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthode `services_speciaux`), `sources/zIgzAg/jeu/oceane/Commandant.java`
  (méthode `effectuerMissionSpeciale`)
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

**Non exécutée.** Procédure prévue, entièrement côté PHP/navigateur
(les joueurs n'ayant pas accès au Java), sur le même principe que celle
déjà exécutée et confirmée pour l'enrôlement de lieutenant :

1. Soumettre 3 ordres `services_speciaux` normalement via le formulaire
   (`index.php3?table=services_speciaux`), sur 3 systèmes/types
   différents.
2. Recharger la page : le formulaire doit avoir disparu
   (`$nb_lignes<3` devenu faux).
3. Dans le contexte de la frame affichant `index.php3?table=...`,
   exécuter depuis la console JavaScript du navigateur :
   ```js
   fetch('index.php3?table=services_speciaux', {
     method: 'POST',
     credentials: 'same-origin',
     headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
     body: new URLSearchParams({
       v0: '<code système, différent des 3 précédents>',
       v1: '0',    // type de mission (espionnage = 0 dans z_missions, à vérifier via le menu déroulant réel)
       v2: '100',  // planète non précisée (convention PLANETE_NON_PRECISE)
       ajout: 'Envoyer cet ordre'
     })
   }).then(r => r.text()).then(html => console.log(html.length, html));
   ```
4. Recharger `index.php3?table=list_ordres`.

**Résultat attendu si l'écart est confirmé** : 4 lignes de mission
spéciale apparaissent pour ce commandant ce tour-ci, au lieu des 3
maximum annoncées par les règles.

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
