# Combat : comportements non documentés

Constats faits en écrivant des tests de caractérisation pour `Combat.java`
(`src/test/java/zIgzAg/jeu/oceane/CombatFlotteFlotteTest.java`,
`CombatFlottePlaneteTest.java`, `CombatDegatsNegatifsTest.java`) : des règles
réelles du moteur qui ne sont
écrites nulle part ailleurs (ni commentaire, ni documentation, ni nom de
méthode explicite). Elles ne sont pas nécessairement des bugs — ce sont des
comportements du code **tel qu'il existe aujourd'hui**, à connaître avant de
le modifier ou de s'appuyer dessus pour équilibrer le jeu.

Chaque constat précise s'il est **vérifié empiriquement** (couvert par une
assertion de test) ou **déduit de la lecture du code** (raisonnement sur le
code source, pas spécifiquement mis en évidence par un test dédié).

Voir aussi `doc/combat-algorithme.md` pour la description d'ensemble de
l'algorithme dans lequel s'insèrent ces comportements.

## 1. À tempo égal, le défenseur tire toujours en premier

**Vérifié empiriquement** — `CombatFlotteFlotteTest.combatFlotteFlotte_coupsGarantis_tempoIdentique_leDefenseurTireEnPremierEtSeul`.

Dans `Combat.combat()`, l'ordre de tir entre les deux flottes est déterminé
par comparaison stricte des clés de tempo :

```java
if (mt1[compteur1].getKey().compareTo(mt2[compteur2].getKey()) > 0)
    premier = true;
```

Le tempo de chaque vaisseau (`Combat.determinationTempo` /
`Vaisseau.getTempo()`) dépend de sa capacité de mouvement, de son
expérience et de la vitesse de ses armes, avec une part aléatoire —
mais **deux vaisseaux strictement identiques obtiennent le même tempo**. Or
la comparaison utilise `>` et non `>=` : en cas d'égalité, c'est
systématiquement la **flotte 2** (le défenseur, dans `combatFlotteFlotte`)
qui tire en premier. Avec des vaisseaux "à un coup" (détruits dès le premier
impact), cela suffit à faire perdre systématiquement l'attaquant dans un
duel parfaitement symétrique — un avantage structurel au défenseur qui n'a
rien à voir avec la stratégie de combat choisie.

## 2. Les défenses planétaires (batteries et milice) ne peuvent jamais menacer qu'un seul vaisseau assaillant par round

**Vérifié empiriquement** — `CombatFlottePlaneteTest`, voir le commentaire de
classe et les deux scénarios de prise de planète.

`Combat.tirDefensesPlanetaires` (utilisée aussi bien pour les vraies
batteries que pour la milice, voir point 3) sélectionne sa cible ainsi, à
**chacune** des `Const.NOMBRE_SALVE_BATTERIE` (50) salves, pour **chaque**
bâtiment défensif :

```java
Vaisseau cible = (Vaisseau) inter.get(Univers.getInt(inter.size()));
```

Le tirage est indépendant à chaque salve (avec remise, pas une répartition
équilibrée entre les vaisseaux présents). Avec un générateur aléatoire réel,
la distribution se répartit statistiquement sur l'ensemble de la liste "sol"
au fil des salves — mais rien dans le code ne garantit qu'un vaisseau donné
sera visé, ni qu'aucun vaisseau ne sera visé plusieurs fois de suite.
Autrement dit, les défenses planétaires n'ont **aucune notion de
répartition des tirs sur la flotte** : elles tirent au hasard, salve après
salve, sans mémoire des vaisseaux déjà visés.

## 3. La milice est un bâtiment de défense fabriqué "en dur", indépendant des bâtiments réels de la planète

**Déduit de la lecture du code.**

`Combat.tirMilicesPlanetaires` construit elle-même son "bâtiment" :

```java
ConstructionPlanetaire[] c = new ConstructionPlanetaire[1];
c[0] = new ConstructionPlanetaire("battlaI");
```

Le code `"battlaI"` est écrit en dur dans `Combat.java`, indépendamment de
`Planete.getBatiments()` : la milice existe et peut tirer même sur une
planète qui n'a **aucun** bâtiment de défense construit, tant que la
technologie `"battlaI"` est résolvable via `Univers.getTechnologie`. Elle
n'est pas non plus affectée par `Planete.eliminerPertesBatiments()`
puisqu'elle n'est jamais ajoutée à la liste réelle des bâtiments de la
planète — elle est recréée à chaque appel de `tirMilicesPlanetaires`.

Le nombre de salves de milice dépend uniquement de la population
défensive restante au moment de l'appel :

```java
if (nbPopDefensives > 50)
    nbTirs = 1 + (nbPopDefensives / (2 * Const.NOMBRE_SALVE_BATTERIE));
```

En dessous de 50 habitants défensifs (`nbPopDefensives`), la milice ne tire
plus du tout — un seuil qui n'est mentionné nulle part côté jeu.

## 4. Une arme purement spatiale peut infliger des dégâts à la population au sol

**Déduit de la lecture du code**, cohérent avec les scénarios
`CombatFlottePlaneteTest` (les vaisseaux assaillants n'ont que des armes de
type combat spatial, et endommagent pourtant la population).

`Combat.tirAirSol` est appelée deux fois par round de combat
flotte-planète : une première fois avec `construCible = true` (les
vaisseaux visent les boucliers/bâtiments), une seconde avec
`construCible = false` (les vaisseaux visent la population). Ce paramètre
est transmis à `Vaisseau.tirSurMilices(h, g, bombe, popRestante)` sous le nom
`bombe`, où :

```java
boolean possible = false;
if ((bombe) && (arme.estCombatPlanetaire()))
    possible = true;
if (!bombe)
    possible = true;
```

Quand `bombe` vaut `false` (le cas de la seconde phase, celle qui vise la
population), **n'importe quelle arme est éligible**, y compris une arme
strictement `Const.CV_ARME_CS` (combat spatial) qui ne peut normalement pas
viser une planète ailleurs dans le jeu. La restriction "arme adaptée au
combat planétaire" ne s'applique donc qu'à la première phase (tir sur les
bâtiments), pas à la seconde (tir sur la population).

## 5. Un vaisseau de la plus petite classe de taille ne peut tirer qu'une seule arme par round

**Déduit de la lecture du code**, non spécifiquement mis en évidence par un
test (les vaisseaux de test n'ont qu'une seule arme chacun).

`Vaisseau.tir()` borne le nombre de tirs par round ainsi :

```java
while ((compteur < listeArmesValides.size())
        && compteur <= Const.NB_CIBLES[getTaille()]
        && (!cible.estDetruit())) { ... }
```

`Const.NB_CIBLES = {0, 3, 7, 15, 31, 63, 127, 255, 511, 2000}`, indexé par
`getTaille()` (classe de taille du vaisseau, 0 pour les plus petits — 1 à 3
cases). Pour un vaisseau de taille 0, `NB_CIBLES[0] = 0` : la condition
`compteur <= 0` n'autorise qu'une seule itération (`compteur == 0`), **quel
que soit le nombre d'armes effectivement montées sur le vaisseau**. Un
chasseur multi-armé de la plus petite classe ne tirera donc qu'une seule de
ses armes par round de combat.

## 6. La perte de population appliquée à une planète est plafonnée à 10 % de sa population de départ — même en cas de prise complète

**Vérifié empiriquement** — `CombatFlottePlaneteTest.combatFlottePlanete_uneFlotteEnNombreSuffisantEpuiseLaPopulationEtPrendLaPlanete`
(population de départ 40, perte appliquée constatée : 4).

À la fin de `Combat.combatFlottePlanete`, quel que soit le résultat du
combat (population défensive épuisée ou non, planète prise ou non par une
directive `ATTAQUE_PLANETE`/`ATTAQUE_SYSTEME`) :

```java
int memoireTot = p.populationTotale() / 10;   // calculé AVANT le combat
...
s.getPlanete(numPla).diminuerPopulation(
        Math.min(memoirePop - Math.max(0, nbPopDefensive), memoireTot));
```

`memoireTot` (10 % de la population de départ) plafonne la perte réellement
appliquée à la planète, indépendamment de l'écart réel entre la population
défensive de départ et son niveau final (potentiellement très négatif après
plusieurs rounds de bombardement réussi). **Prendre une planète par
occupation (`DIRECTIVE_FLOTTE_ATTAQUE_PLANETE`/`ATTAQUE_SYSTEME`) ne vide
donc jamais plus de 10 % de sa population lors du combat qui la fait
tomber** — seules les directives de pillage
(`s.getPlanete(numPla).diminuerPopulation(populationTotale())`) et
d'éradication (`p.initialiserPopulation()`) contournent ce plafond, via des
branches de code entièrement séparées.

## 7. `Utile.ordreAuHasard` peut boucler indéfiniment si `Univers.getTest` ne renvoie jamais vrai

**Déduit de la lecture du code**, observé en écrivant les tests (un premier
jet de `CombatFlotteFlotteTest` avec `Univers.getTest` toujours faux ne
terminait jamais).

Sans rapport direct avec `Utile.ordreAuHasard` (qui utilise
`Univers.getInt`, sans risque de blocage — voir les tests dédiés dans
`UtileOrdreAuHasardTest`), c'est `Combat.determinationCible` qui présente ce
risque :

```java
while (choix2 == null)
    for (int j = 0; j < Const.TAILLE_MAXIMAL_VAISSEAU; j++) {
        if (Univers.getTest(50))
            choix2 = cibleNonDejaChoisie(...);
        if ((choix2 != null) && (choix2.length > 0))
            break;
    }
```

`choix2` n'est affecté **que** si `Univers.getTest(50)` renvoie `true`. En
jeu réel, `getTest` utilise un générateur aléatoire véritable : la boucle finit
presque sûrement par se terminer (elle retente indéfiniment jusqu'à un tirage
favorable), mais c'est une boucle de tentative sans limite ni délai, pas un
mécanisme borné. Le seul filet de sécurité du code est la boucle englobante
juste après, bornée à 5 tentatives — mais elle ne s'exécute qu'*après* être
sorti de celle-ci, donc ne protège pas contre ce cas précis.

## 8. `StrategieDeCombatSpatial.fusionner` fait une copie superficielle, contrairement au constructeur de copie

**Déduit de la lecture du code** (les deux mécanismes sont testés dans
`StrategieDeCombatSpatialTest`, mais pas leur différence de profondeur de
copie).

Le constructeur de copie clone chaque tableau `int[]` :

```java
this.comportement.put(entry.getKey(), entry.getValue().clone());
```

`fusionner`, lui, réutilise directement les références des tableaux de la
stratégie source :

```java
this.comportement.putAll(autreStrategie.comportement);
this.positionnement.putAll(autreStrategie.positionnement);
```

Après un `fusionner`, modifier un tableau `int[]` récupéré via
`getPositionnement`/`getCibles` sur la stratégie fusionnée modifie donc
**aussi** le tableau correspondant de la stratégie source passée en
argument — un piège de partage d'état auquel le constructeur de copie
échappe totalement.

## 9. Une arme de défense planétaire mal configurée peut produire des dégâts négatifs — dans le journal, pas dans les statistiques du commandant

**Vérifié empiriquement** — `src/test/java/zIgzAg/jeu/oceane/
CombatDegatsNegatifsTest.java` (investigation menée sans exemple concret,
suite à un signalement d'utilisateur de "dégâts négatifs" en combat).

`ConstructionPlanetaire.tirArme` (le tir d'une batterie ou de la milice sur
un vaisseau) ajoute les dégâts de l'arme à son propre compteur de
statistiques sans aucune vérification de signe :

```java
this.dommagesEffectues += dommageCoque;   // ou dommageBouc, selon bouclier ou non
```

À comparer avec `Vaisseau.effectuerDommages` (le tir d'un vaisseau sur un
autre), qui protège la même opération par `if (degats > 0)`. Si l'arme
d'un bâtiment de défense a une caractéristique de dégâts négative (données
de jeu mal configurées — rien dans le code ne le détecte ni ne l'empêche),
chaque tir touché de cette arme fait directement **baisser** ce compteur.
`Combat.tirDefensesPlanetaires` calcule alors, pour ce tir, un delta
"après − avant" négatif.

Ce delta négatif n'atteint cependant **pas** la statistique persistée du
commandant : `Combat.tirDefensesPlanetaires` ne fait
`defenseur.ajouterDegats(degatsDuTir)` que si `degatsDuTir > 0` — un test
qui échoue aussi bien pour un delta négatif que pour un délta nul, donc
`Commandant.degatsInfligesCeTour` (utilisée en score de victoire) reste
protégée. En revanche, la ligne `SherilLogger.log(...)` juste avant ce
test-là affiche `degatsDuTir` **sans aucune garde** — c'est dans le journal
de combat (`data/logs/tourX.log`) que le nombre négatif devient visible,
sous la forme `"Dégâts: -20"`.

À noter, dans la même investigation : un débordement d'entier (overflow) du
même compteur `ConstructionPlanetaire.dommagesEffectues` — jamais
réinitialisé sur toute la durée de vie d'un bâtiment, contrairement à
`Vaisseau.dommagesEffectues` remis à zéro à chaque round — rend bien le
compteur lui-même négatif une fois débordé, mais ceci a été **vérifié comme
n'étant pas une source de dégâts négatifs visibles** : l'arithmétique
entière Java (complément à deux) fait que le delta "après − avant" reste
mathématiquement correct malgré un débordement, tant qu'il ne survient
qu'une seule fois entre les deux mesures.
