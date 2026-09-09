# [CONFIRMÉ, NON CORRIGÉ] Les dégâts absorbés par un bouclier ne sont jamais comptabilisés dans les dégâts infligés de l'attaquant

- **Fichier en cause** : `src/main/java/zIgzAg/jeu/oceane/Vaisseau.java`
- **Méthode en cause** : `Vaisseau.effectuerDommages`
- **Nature** : bug logique (branche `if`/`else` incomplète), atteignable en
  jeu normal dès qu'une flotte attaque une cible équipée de boucliers non
  saturés — pas besoin de données mal configurées.
- **Statut : cause confirmée empiriquement, y compris en rejouant un combat
  réel tiré des données de partie. Correctif non appliqué** (analyse et
  tests d'abord, correctif seulement si demandé — voir §4 pour la
  proposition).

Ce document répond à un signalement utilisateur : un combat en position
37-27 entre la flotte "Gzav" (commandant Gzav, n°10) et la flotte "Bear
One" (commandant Snagha, n°19) rapporte **0 dégâts**, alors qu'il s'agit
d'un vrai combat entre deux flottes armées. Deux causes distinctes et
indépendantes ont été identifiées ; ce document couvre la première
(absorption par bouclier). La seconde (téléportation de vaisseaux par un
bug de positionnement) est documentée séparément dans
`doc/bugs/positionnement-fuite-teleportation-courtchemin.md`.

## 1. Signalement

Désérialisation directe de `analyse/donneesT16/donnees/comm.txt` (le
même fichier qui a produit le rapport signalé) :

- Flotte "Gzav" (commandant Gzav, n°10), position `0_37_27` : 2 vaisseaux
  `scylla-vortex`, chacun équipé de 13 "canon à plasma" (portée 16, dégâts
  coque **et** bouclier = 2 chacun).
- Flotte "Bear One" (commandant Snagha, n°19), même position : 44
  vaisseaux, dont une quinzaine de "Fregate standard" équipées chacune de
  **5 composants bouclier**.

En rejouant le combat réel via `Combat.combatFlotteFlotte(...)` avec les
vraies flottes désérialisées et le générateur aléatoire réel du jeu (3
essais indépendants, journal de combat activé) : **tous** les tirs de
Gzav sur les Fregates de Snagha affichent `Dégâts: 0` dans le journal,
alors que les Fregates infligent bien des dégâts non nuls à Gzav (qui n'a
pas de bouclier) en retour. Extrait représentatif du journal réel :

```
[COMBAT-SPATIAL] Tir: scylla-vortex (ID:0) sur Fregate standard | Dégâts: 0 | Cumul Commandant Gzav (10) : 163379.00
[COMBAT-SPATIAL] Tir: Fregate standard (ID:65) sur scylla-vortex  | Dégâts: 1 | Cumul Commandant Snagha (19) : 19691.00
```

## 2. Cause racine

```java
// src/main/java/zIgzAg/jeu/oceane/Vaisseau.java
private void effectuerDommages(Vaisseau cible, Arme a, Heros h1, Heros h2) {
    int degats;
    int b = cible.getNumeroBouclierValide();
    if (b != -1) {
        cible.ajouterDommagesBouclier(b, a.getDommagesBouclier());   // dégât RÉEL appliqué au bouclier
        augmenterMoral();
        cible.diminuerMoral();
        // dommagesEffectues n'est PAS incrémenté ici
    } else {
        degats = cible.dommagesApresAbsorbe(a.getDommagesCoque());
        if (degats > 0) {
            ...
            dommagesEffectues = dommagesEffectues + degats;   // seule cette branche compte le dégât
        } else
            diminuerMoral();
        ...
    }
}
```

Lorsque la cible a un bouclier valide (`getNumeroBouclierValide() != -1`,
c'est-à-dire tant qu'il n'est pas encore saturé), le tir touche
réellement — le bouclier encaisse le dégât (`ajouterDommagesBouclier`),
le moral des deux camps évolue en conséquence — **mais le compteur
`dommagesEffectues` de l'ATTAQUANT n'est jamais incrémenté dans cette
branche.** Seule la branche "pas de bouclier valide" incrémente ce
compteur.

Or c'est ce compteur qui alimente :
- `Commandant.degatsInfligesCeTour` (via `Combat.tirVaisseau`, qui n'appelle
  `com.ajouterDegats(...)` que si le delta de `dommagesEffectues` est
  strictement positif) — utilisée comme score de victoire
  (`Commandant.getScoreCategorie`/`PointDeVictoireCategorie.COMBATS`) ;
- la colonne "Dommages" du rapport de combat vu par le joueur
  (`Flotte.listeVaisseauxParTypePourCombat`, valeur d'indice 2 :
  "dommages effectues").

Tant qu'une flotte attaquante ne compte que peu de vaisseaux répartis sur
de nombreuses cibles chacune dotée d'un bouclier à forte capacité totale
(5 composants dans le cas de "Fregate standard"), et que le combat est de
courte durée (borné par `Flotte.calculeCombativiteMoyenne`, quelques
rounds), il est parfaitement possible qu'aucun bouclier individuel ne
sature jamais — tous les tirs de l'attaquant sont alors comptabilisés à 0,
du premier au dernier round, alors même que ses tirs touchent réellement
et endommagent les boucliers adverses.

Ce n'est **pas** un défaut du plafonnement du bouclier lui-même (voir
`doc/bugs/bouclier-vaisseau-depassement-capacite.md`, qui couvre déjà
l'absence de plafond sur `Vaisseau.ajouterDommagesBouclier`) : c'est le
fait que l'absorption par bouclier ne remonte JAMAIS, même
partiellement, dans le compteur de dégâts infligés de l'attaquant —
contrairement aux dégâts de coque, comptés au coup par coup.

## 3. Vérification effectuée

Fichier : `src/test/java/zIgzAg/jeu/oceane/CombatDegatsAbsorbesParBouclierTest.java`
(2 tests, tous deux verts — suite complète du projet : 55/55 avec ce
fichier ajouté, aucun correctif de production appliqué).

**Test 1** (`effectuerDommages_cibleAvecBouclierNonSature_...`) — niveau
unitaire, appel direct de `Vaisseau.effectuerDommages` (via réflexion,
méthode privée) : 5 tirs garantis d'une arme infligeant 2 points de
dégâts bouclier et 2 de coque, sur une cible dont le bouclier a une
capacité de 10 000 (jamais saturé). Résultat mesuré :

```
boucliers[0] après 5 tirs = 10   (= 5 × 2, le bouclier a bien tout encaissé)
cible.nombreTotalPointsDeDommage() = 0   (aucun dégât de coque)
attaquant.getDommagesEffectues() = 0     (alors que 5 tirs ont réellement touché)
```

**Test 2** (`combatFlotteFlotte_cibleIntegralementBouclieeToutLeCombat_...`)
— combat complet via `Combat.combatFlotteFlotte`, coups garantis,
reproduisant la forme du cas réel (1 assaillant armé vs 1 cible
uniquement équipée d'un bouclier surdimensionné, sans arme, pour isoler
l'effet mesuré). Résultat mesuré : le bouclier de la cible a bien
absorbé des dégâts réels (`Arrays.stream(boucliers).sum() > 0`), la
cible survit intacte (aucune perte de vaisseau), et
`verify(c1, never()).ajouterDegats(anyFloat())` confirme que le
compteur de dégâts infligés de l'attaquant n'a **jamais** été incrémenté
de tout le combat.

## 4. Correctif proposé (non appliqué)

Le plus simple, dans l'esprit du code existant (qui compte déjà les
dégâts de coque coup par coup), est de compter aussi les dégâts de
bouclier réellement appliqués :

```diff
 private void effectuerDommages(Vaisseau cible, Arme a, Heros h1, Heros h2) {
     int degats;
     int b = cible.getNumeroBouclierValide();
     if (b != -1) {
         cible.ajouterDommagesBouclier(b, a.getDommagesBouclier());
         augmenterMoral();
         cible.diminuerMoral();
+        dommagesEffectues = dommagesEffectues + a.getDommagesBouclier();
     } else {
         degats = cible.dommagesApresAbsorbe(a.getDommagesCoque());
         ...
```

**Point à trancher avant d'implémenter** : ce correctif minimal compte le
dégât **brut** de l'arme (`a.getDommagesBouclier()`), pas le dégât
réellement "consommé" par le bouclier — cohérent avec le fait que
`ajouterDommagesBouclier` elle-même n'est pas plafonnée (voir
`doc/bugs/bouclier-vaisseau-depassement-capacite.md`) : le bouclier
encaisse déjà l'intégralité du coup, plafonné ou non. Compter la même
valeur brute au crédit de l'attaquant reste donc cohérent avec l'état
réel de la cible. Si le plafonnement du bouclier (§ document séparé)
était corrigé un jour, ce correctif-ci devrait être revu en parallèle
pour ne compter que le dégât effectivement absorbé (`Math.min(dégât
brut, capacité résiduelle du bouclier)`), sans quoi les deux compteurs
("dégât infligé" côté attaquant, "dégât reçu" côté bouclier) divergeraient
à nouveau, cette fois dans l'autre sens.

Une alternative plus lourde, non retenue ici faute de décision de
conception : introduire un compteur séparé (« dégâts de bouclier
infligés ») pour distinguer dans le rapport les dégâts de coque des
dégâts de bouclier, plutôt que de les fusionner dans un seul
`dommagesEffectues`. Cela toucherait aussi
`Flotte.listeVaisseauxParTypePourCombat` et les méthodes d'écriture du
rapport HTML (`Combat.ecrireDetailCombatVaisseaux*`), une portée plus
large que le correctif minimal ci-dessus.

## 5. Portée et limites du correctif proposé

- S'applique à tout combat vaisseau-contre-vaisseau (`combatFlotteFlotte`)
  où l'attaquant touche une cible dotée d'un bouclier valide — c'est-à-dire
  tout combat spatial impliquant des vaisseaux blindés côté défenseur.
- Ne change aucune mécanique de combat (dégâts réellement appliqués aux
  boucliers/à la coque, destruction de vaisseaux, déroulement des rounds) :
  seul le compteur de statistiques `Vaisseau.dommagesEffectues` (et donc
  `Commandant.degatsInfligesCeTour` et le rapport affiché) serait modifié.
- Ne corrige pas l'absence de plafond de `Vaisseau.ajouterDommagesBouclier`
  (bug séparé, non corrigé sur demande explicite — voir
  `doc/bugs/bouclier-vaisseau-depassement-capacite.md`) : les deux
  correctifs sont liés (voir remarque du §4) mais indépendants l'un de
  l'autre.
- N'a aucune incidence sur le second bug identifié dans le même cas réel
  (téléportation de vaisseaux par positionnement, voir
  `doc/bugs/positionnement-fuite-teleportation-courtchemin.md`) : les deux
  peuvent se combiner dans un même combat sans interaction directe entre
  eux.
