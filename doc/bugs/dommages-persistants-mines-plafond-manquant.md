# [CORRIGÉ] "Dommages persistants" des mines dépassant leur structure max — cause de l'affichage "(+-N)"

**Statut : cause confirmée par preuve empirique (3 rapports réels, 6 occurrences),
puis corrigée.** Voir `doc/fix/plafonnement-dommages-constructions-
planetaires.md` pour le rapport de détection/correctif complet — le
plafonnement de `ConstructionPlanetaire.ajouterDommages` élimine la cause
racine (compteur non borné). Vérifié via `OverkillEnUnRoundTest` : le même
scénario (26 bombardiers, 3 mines) passait de 208 points cumulés (plafond
théorique 60) avant correctif à 20 après.

Le bug d'affichage séparé (le préfixe `"(+"` codé en dur dans
`ecrireDetailCombatPlanete`, sans gestion du signe) n'est PAS corrigé par ce
correctif — voir §5 de `doc/fix/plafonnement-dommages-constructions-
planetaires.md`.

Ce document concerne un **rapport de combat distinct** de celui étudié dans
`doc/bugs/station-dommages-encaisses-anormaux.md` (page à 5 colonnes avec
"Dommages persistants avant/après" — absente de notre dépôt, voir §2). Les deux
documents partagent la même famille de bug d'affichage (§1) mais leurs causes
racines diffèrent : celui-ci a une cause confirmée, l'autre reste en attente
d'informations.

## 1. Signalement et preuve

Rapports réels fournis par l'utilisateur (serveur de test du dépôt amont,
URLs caviardées — trois rapports de combat distincts, désignés ci-après
`6tour15`, `10tour15`, `12tour15`) :
- `[URL]/6tour15/combat.htm`
- `[URL]/10tour15/combat.htm`
- `[URL]/12tour15/combat.htm`

Sur ces 3 pages, la colonne "Dommages encaissés" affiche à 6 reprises un delta
de la forme `(+-N)` au lieu de `(-N)`, **systématiquement sur des lignes
"Mine"**, jamais sur les autres bâtiments (stations, chantier naval) :

| Rapport | Bâtiment | Nombre (delta) | Dommages encaissés (delta) | Persistants avant | Persistants après |
|---|---|---|---|---|---|
| 6tour15 | Mine | 2 `(-1)` | 20 `(+-263)` | **283** | 0 |
| 10tour15 | Mine | 2 `(-1)` | 20 `(+-35)` | **55** | 0 |
| 10tour15 | Mine | 0 `(-6)` | 120 `(+-117)` | **237** | 0 |
| 10tour15 | Mine | 0 `(-4)` | 80 `(+-222)` | **302** | 0 |
| 10tour15 | Mine | 0 `(-6)` | 120 `(+-42)` | **162** | 0 |
| 12tour15 | Mine | (idem 6tour15, même planète) | | | |

À titre de comparaison, les bâtiments qui **ne** produisent jamais ce glitch
dans les mêmes rapports (station de logiciels, station de produits
alimentaires, station d'unité énergétique, chantier naval) affichent tous un
delta positif normal, par exemple `100<span>(+100)</span>` — cohérent avec un
bâtiment unique, détruit dans le round même où il commence à encaisser des
dégâts (cas couvert et validé par `doc/bugs/station-dommages-encaisses-
anormaux.md` §3).

## 2. Code responsable (confirmé, différent de notre dépôt)

Cette page ne provient d'aucune branche de notre dépôt (`develop`,
`build/maven-migration`, `feature/combat-tests`) : la recherche
`git grep "persistants avant"` sur ces branches et sur `upstream/develop` ne
retourne aucun résultat. Le format à 5 colonnes correspond exactement (comparaison
octet à octet de `ecrireDetailCombatPlanete`) à une branche de travail non
fusionnée sur le dépôt amont, `upstream/fix-ecrireDetailcombat-batiments`
(auteur : ydomenjoud/yann, propriétaire du serveur de test).

```java
// Combat.java, ecrireDetailCombatPlanete (upstream/fix-ecrireDetailcombat-batiments, ~commit 41c7c7e)
int dom = nT[1] + (-nT[0] + dT[0]) * nbCases;
int domA = mT[1] + (-mT[0] + dT[0]) * nbCases;
if (dom == domA)
    a[ligne][2] = ...ajout(Rapport.getText(Integer.toString(dom)));
else
    a[ligne][2] = ...ajout(Rapport.getText(Integer.toString(dom)))
            .ajout(...ajout(Rapport.getText("(+"
                    + Integer.toString(dom - domA)
                    + ")")));
a[ligne][3] = ...ajout(Rapport.getText(Integer.toString(mT[1])));  // "Dommages persistants avant"
a[ligne][4] = ...ajout(Rapport.getText(Integer.toString(nT[1])));  // "Dommages persistants après"
```

Le préfixe `"(+"` codé en dur est la **même classe de défaut** que celle déjà
documentée dans `doc/bugs/station-dommages-encaisses-anormaux.md` §2.1 et
corrigée pour un autre cas dans `doc/fix/sous-comptage-degats-infliges-
batiments.md` : rien à ajouter sur ce point précis, il ne s'agit que du
symptôme d'affichage.

## 3. Cause racine confirmée : `dommages` (persistant) peut dépasser la structure du bâtiment

`mT[1]` — la colonne "Dommages persistants avant" — est la somme brute du
champ `dommages` de `ConstructionPlanetaire`, **sans plafond appliqué à
l'affichage**, pour les mines encore en vie au début de ce round. C'est un
champ persistant (conservé entre rounds *et* entre combats successifs sur
plusieurs tours de jeu), pas une valeur recalculée pour ce seul rapport.

En résolvant la formule ci-dessus avec les valeurs réelles de chaque ligne
(`dT[0] = mT[0]` dans tous les cas, ce qui s'obtient directement du fait que
`domA == mT[1]` à chaque fois — voir calcul détaillé en annexe), on obtient
`nbCases` (points de structure d'une mine) `= 20`, cohérent sur les 3
rapports. Le nombre de mines encore en vie (`mT[0]`) se lit directement à
partir du delta de la colonne "Nombre".

| Rapport | mT[0] (mines vivantes) | Plafond théorique = mT[0] × 20 | Dommages persistants avant (mT[1]) réel | Dépassement |
|---|---|---|---|---|
| 6/12tour15 | 3 | 60 | 283 | **+223** |
| 10tour15 (1er) | 3 | 60 | 55 | *aucun (dans la borne)* |
| 10tour15 (2e) | 6 | 120 | 237 | **+117** (= delta affiché) |
| 10tour15 (3e) | 4 | 80 | 302 | **+222** (= delta affiché) |
| 10tour15 (4e) | 6 | 120 | 162 | **+42** (= delta affiché) |

Sur 4 des 5 lignes indépendantes, le total persisté dépasse largement le
plafond théorique (structure × nombre de mines vivantes) — jusqu'à 5 fois la
valeur maximale possible si chaque mine individuelle était plafonnée à ses
propres points de structure. Le montant du dépassement correspond exactement,
à chaque fois, au delta négatif affiché — ce n'est pas une coïncidence, c'est
la même quantité vue sous deux angles algébriques différents.

**Conclusion : le champ `dommages` d'au moins une partie des mines de ce
serveur contient une valeur historique qui dépasse la structure du
bâtiment.** C'est exactement la même famille de défaut que le bug déjà
identifié et corrigé dans ce dépôt (`Vaisseau.tirSurConstruction` /
`ConstructionPlanetaire.ajouterDommages`, voir `doc/fix/sous-comptage-degats-
infliges-batiments.md`) : sans plafonnement au moment où les dégâts sont
appliqués, un bâtiment déjà détruit ou presque, visé par plusieurs tirs dans
le même round, peut accumuler un total de dégâts bien supérieur à ce que sa
structure permettrait physiquement — et comme ce champ est persistant, la
valeur excessive reste stockée telle quelle et ressurgit dans n'importe quel
rapport ultérieur qui l'affiche, y compris longtemps après.

Fait notable : le dépôt amont a **indépendamment identifié le même défaut**
sur sa branche de travail `upstream/fix-ecrireDetailcombat-batiments`, avec un
commit dédié `c26f43e "Plafonner les dégâts des constructions planétaires"`
qui introduit `dommages = Math.min(dommages + nb, batiment.getPointsDeStructure())`.
Ce plafonnement empêche toute **nouvelle** accumulation excessive à partir du
moment où il est déployé, mais **ne corrige pas rétroactivement** les valeurs
déjà excessives stockées avant son déploiement — ce qui explique pourquoi les
mines de ce serveur de test, vraisemblablement endommagées à plusieurs
reprises avant l'introduction du plafond, continuent d'afficher des totaux
incohérents malgré le correctif déjà en place sur le code de génération du
rapport.

## 4. Pourquoi seules les mines sont concernées ici

Les bâtiments à exemplaire unique (stations, chantier naval) sont détruits en
un seul round : leur `dommages` ne peut jamais dépasser leur structure de
plus que les dégâts du (ou des) coup(s) de ce round-là, et ils sont retirés
de la liste aussitôt détruits (§3 de `doc/bugs/station-dommages-encaisses-
anormaux.md` prouve que cette transition reste toujours bornée entre 0 et la
structure). Les mines, présentes en plusieurs exemplaires, peuvent en
revanche être endommagées sur de nombreux rounds/combats distincts sans être
détruites (structure plus élevée relative aux dégâts par tir, ou nombre
d'exemplaires qui absorbe les tirs) — ce qui laisse le temps à un
dépassement non plafonné de s'accumuler avant qu'un correctif de plafonnement
n'entre en vigueur, ou si un chemin de dégâts n'est pas couvert par le
plafonnement.

## 5. Portée pour ce dépôt — vérifié empiriquement sur le code actuel

Le correctif déjà appliqué dans ce dépôt (`fix/tir-sur-construction-sous-
comptage-degats`, propagé sur `feature/combat-tests` et
`build/maven-migration`) corrige le sous-comptage des **dégâts infligés**
déclarés par l'attaquant, mais **ne plafonne pas** le champ `dommages` de
`ConstructionPlanetaire` lui-même : `ajouterDommages` dans notre code reste
aujourd'hui, à l'identique des tout premiers commits de la branche amont
(`cb2714e`/`2857c2e`, avant leur propre correctif `c26f43e`) :

```java
public void ajouterDommages(int nb) {
    determinerBatiment();
    dommages = dommages + nb;                              // aucun plafond
    if (dommages > batiment.getPointsDeStructure())
        detruit = true;                                     // marque "détruit"...
}                                                            // ...mais dommages continue de grossir après
```

**Le dépassement est atteignable dans une seule manche de combat normale,
sans réflexion ni état forcé**, à cause d'un second défaut dans
`Combat.tirAirSol` : la liste `cibles` (bâtiments encore vivants) est
construite **une seule fois en début de round**, puis chaque vaisseau tire
sur un index choisi au hasard dans cette liste (`Univers.getInt(cibles.length)`)
**sans jamais vérifier `cibles[index].estDetruit()` avant de tirer** —
`p.eliminerPertesBatiments()` n'est appelé qu'**après** que tous les vaisseaux
du round aient tiré. Un bâtiment déjà détruit tôt dans le round continue donc
d'absorber des tirs (et d'incrémenter son `dommages` sans plafond) jusqu'à la
fin de la manche.

Test de vérification exécuté (production réelle, aucune donnée forcée par
réflexion — juste `Vaisseau.tirSurConstruction` appelé en boucle exactement
comme le fait `Combat.tirAirSol`) : une flotte de 26 bombardiers (8 dégâts au
sol chacun) tirant sur 3 mines survivantes (20 points de structure chacune,
plafond théorique 60) produit, en une seule manche :

```
dommages() par mine :
  208 (détruite=true)
  0 (détruite=false)
  0 (détruite=false)
Total dommages = 208 ; plafond théorique = 60 ; dépassement = 148
```

**Confirmé : avec le code actuel, le compteur `dommages` peut largement
dépasser le plafond de structure du bâtiment — pas seulement en théorie, mais
de façon directement reproductible avec une flotte de taille réaliste,
en un seul round.** C'est le même ordre de grandeur de dépassement (3 à 5
fois le plafond) que celui observé dans les vrais rapports du serveur de
test (§3) — la piste "accumulation sur plusieurs combats/tours" évoquée
plus haut n'est donc pas nécessaire pour expliquer le phénomène : un seul
round suffit, dès qu'un nombre de vaisseaux supérieur au nombre de bâtiments
cibles continue de tirer sans vérifier qui est déjà mort.

Notre dépôt n'a pas non plus les colonnes "Dommages persistants avant/après"
  (fonctionnalité de rapport ajoutée uniquement sur la branche de travail
  amont) — le symptôme visible "(+-N)" existe donc potentiellement aussi chez
  nous dès qu'un bâtiment multi-exemplaires (mine) encaisse un total
  supérieur à sa structure sur plusieurs rounds/combats, même si nos rapports
  actuels n'exposent pas la colonne "persistants avant" qui le rendrait
  visible aussi clairement.
- Un plafonnement de `ConstructionPlanetaire.ajouterDommages` (à l'image de
  `c26f43e` côté amont) empêcherait toute nouvelle occurrence, mais ne
  corrigerait pas les valeurs déjà stockées en base pour des parties en
  cours — un correctif de données (migration/purge) serait nécessaire en
  complément si on veut nettoyer l'historique existant.

## 6. Prochaines étapes possibles (non entreprises, en attente de décision)

1. Si souhaité : appliquer un plafonnement de `dommages` dans
   `ConstructionPlanetaire.ajouterDommages` sur ce dépôt, à l'image du
   correctif amont `c26f43e`, avec un rapport de détection/correctif au même
   format que `doc/fix/sous-comptage-degats-infliges-batiments.md`.
2. Corriger le préfixe `"(+"` codé en dur dans `ecrireDetailCombatPlanete`
   pour qu'il gère correctement les deltas négatifs (`"(" + (delta >= 0 ?
   "+" : "") + delta + ")"`), indépendamment du plafonnement — un correctif
   d'affichage pur qui n'élimine pas la cause mais supprime le symptôme
   visuel choquant.
3. `doc/bugs/station-dommages-encaisses-anormaux.md` reste un cas distinct et
   toujours ouvert (rapport à 3 colonnes, pas de colonnes "persistants") — ne
   pas le clore sur la base de cette investigation.

## Annexe — détail du calcul pour la ligne 6tour15 (Mine, delta `(+-263)`)

Contexte du rapport : "Tour de combat numéro 9 entre votre flotte BBBB1(7) et
la planète Nuk 6 (6)". Ligne : `Nombre = 2 (-1)`, `Dommages encaissés = 20
(+-263)`, `Persistants avant = 283`, `Persistants après = 0`.

- `nT[0] = 2`, delta `nT[0]-mT[0] = -1` ⟹ `mT[0] = 3`.
- `nT[1] = 0` (persistants après, colonne 4).
- `dom = 20` (affiché), `dom - domA = -263` ⟹ `domA = 283`.
- `domA` (colonne 3, "persistants avant") = `283` = `mT[1]` **exactement** ⟹
  par la formule `domA = mT[1] + (dT[0]-mT[0])×nbCases`, ceci force
  `(dT[0]-mT[0])×nbCases = 0`, donc `dT[0] = mT[0] = 3` (aucune mine perdue
  avant ce round, dans cet appel de combat).
- Avec `dT[0]=3` et `dom = nT[1] + (dT[0]-nT[0])×nbCases = 0 + (3-2)×nbCases
  = nbCases = 20` ⟹ **nbCases = 20 points de structure par mine**, cohérent
  avec les 4 autres lignes indépendantes du rapport 10tour15.
- Plafond théorique pour 3 mines : `3 × 20 = 60`. Valeur réelle : `283`, soit
  **4,7 fois le plafond** — confirmation numérique de la conclusion du §3.
