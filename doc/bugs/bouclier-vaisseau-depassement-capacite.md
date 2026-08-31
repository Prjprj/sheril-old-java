# [CONFIRMÉ, NON CORRIGÉ] Le compteur de dégâts d'un bouclier de vaisseau n'est pas plafonné à son niveau

**Précision importante (confirmée par test, §1 bis) : sur le coup qui sature
le bouclier, l'intégralité du dégât est absorbée par celui-ci — aucun
dégât de coque ne s'applique en "débordement", quel que soit l'écart entre
le dégât brut de l'arme et la capacité réellement restante du bouclier. Ce
n'est pas qu'un compteur affiche un chiffre incohérent : le vaisseau est
concrètement protégé à 100 % sur ce coup précis.**

**Statut : cause confirmée empiriquement par test (voir §3). Correctif non
appliqué, sur demande explicite de l'utilisateur** (même principe que pour
les cas précédents : analyse et vérification d'abord, correctif seulement si
demandé).

Ce document répond à la question « le bug identifié dans
`doc/fix/plafonnement-dommages-constructions-planetaires.md`
(`ConstructionPlanetaire.ajouterDommages` sans plafond) se reproduit-il
ailleurs ? ». Réponse : le même défaut de fond existe à un autre endroit
(`Vaisseau.ajouterDommagesBouclier`), mais avec une portée nettement plus
limitée — voir §4 pour la comparaison précise des deux mécanismes.

## 1. Constat

```java
// src/main/java/zIgzAg/jeu/oceane/Vaisseau.java
public void ajouterDommagesBouclier(int numBouc, int dommages) {
    boucliers[numBouc] = boucliers[numBouc] + dommages;
}
```

Comme `ConstructionPlanetaire.ajouterDommages` avant son correctif, cette
méthode cumule les dégâts sans jamais les borner au niveau du bouclier
concerné (`ComposantDeVaisseau.getNiveauBouclier()`).

## 2. Différence essentielle avec le cas corrigé : pas de liste de cibles figée

Le bug corrigé dans `ConstructionPlanetaire.ajouterDommages` était
dangereux en pratique parce qu'il se combinait avec un second défaut :
`Combat.tirAirSol` construit sa liste de bâtiments cibles **une seule fois
en tête de round**, sans jamais revérifier `estDetruit()` avant chaque tir —
un même bâtiment déjà détruit pouvait donc absorber la totalité des tirs
restants du round, sans limite (voir doc/fix/plafonnement-dommages-
constructions-planetaires.md §2.2 pour le détail).

**Ce second ingrédient est absent ici.** Le bouclier ciblé par un tir est
toujours choisi via `Vaisseau.getNumeroBouclierValide()`, qui réévalue
l'état réel du vaisseau **à chaque tir** :

```java
public int getNumeroBouclierValide() {
    if (boucliers.length == 0)
        return -1;
    int compteur = 0;
    for (int i = 0; i < plan.getNombreDeComposants(); i++)
        if ((plan.getComposant(i).estBouclier())
                && (!composantInutilisable(i)))
            if (boucliers[compteur] < plan.getComposant(i).getNiveauBouclier())
                return compteur;      // encore de la marge : ce bouclier est proposé
            else
                compteur++;            // saturé : passe au bouclier suivant
    return -1;                         // aucun bouclier valide restant
}
```

Ses deux appelants (`Vaisseau.effectuerDommages` pour le combat
vaisseau-contre-vaisseau, `ConstructionPlanetaire.tirArme` pour les
défenses planétaires visant la flotte) appellent systématiquement cette
méthode juste avant chaque tir individuel, pas une seule fois par round.

## 2 bis. Le coup qui sature le bouclier est intégralement absorbé, sans débordement sur la coque

`Vaisseau.effectuerDommages` (le point d'entrée réel qui applique un coup à
une cible) est un `if`/`else` strictement exclusif :

```java
private void effectuerDommages(Vaisseau cible, Arme a, Heros h1, Heros h2) {
    int degats;
    int b = cible.getNumeroBouclierValide();
    if (b != -1) {
        cible.ajouterDommagesBouclier(b, a.getDommagesBouclier());   // TOUT le dégât bouclier ici
        ...
    } else {
        degats = cible.dommagesApresAbsorbe(a.getDommagesCoque());   // sinon TOUT sur la coque
        ...
    }
}
```

Il n'existe **aucun chemin de code qui répartit un même coup entre bouclier
et coque**. Tant qu'un bouclier valide est proposé par
`getNumeroBouclierValide()` (c'est-à-dire tant qu'il n'était pas *déjà*
saturé avant ce coup), la totalité du dégât de bouclier de l'arme est
appliquée à ce bouclier — y compris sur le coup qui le fait dépasser (ou
exploser très largement) son niveau. La coque n'est jamais touchée par ce
coup-là, quel que soit l'écart entre le dégât réel de l'arme et la capacité
résiduelle du bouclier.

**Implication concrète : sur le tour où le bouclier est saturé/détruit, ce
coup précis est intégralement absorbé — le vaisseau ne prend aucun dégât de
coque de ce coup, même si le bouclier n'avait qu'une capacité résiduelle
infime face à un coup bien plus puissant.** C'est un effet de bord
favorable au défenseur (protection totale sur ce coup précis), pas
seulement un compteur interne incohérent.

**Conséquence : le dépassement est borné à, au plus, les dégâts bruts d'UN
SEUL coup** — pas une accumulation illimitée sur toute une salve comme pour
les mines. Une fois un bouclier saturé (même dépassé), le tir suivant est
automatiquement routé vers un autre bouclier valide s'il en existe un, ou
vers la coque (`dommagesApresAbsorbe`/`ajouterDommagesAuHasard`, qui sont
eux auto-limités — voir §5) si plus aucun bouclier n'est disponible.

## 3. Vérification effectuée (test créé, résultats réels)

Fichier : `src/test/java/zIgzAg/jeu/oceane/BouclierDepassementCapaciteTest.java`.

**Test 1** (`unSeulCoup_bouclierDeFaibleNiveau_peutDepasserSonPlafondTheorique`) :
un vaisseau cible avec un seul bouclier de niveau 5 encaisse un tir de 30
points de dégâts de bouclier. Résultat mesuré (production réelle, aucune
donnée forcée par réflexion sur le champ `dommages`/`boucliers` —
uniquement construction d'un plan de vaisseau réel avec un composant
bouclier) :

```
boucliers[0] après le coup = 30   (niveau du bouclier = 5, dépassement = 25)
```

Confirmé : le compteur n'est jamais plafonné, il vaut exactement le dégât
brut de l'arme.

**Test 2** (`coupSuivant_bouclierDejaSature_estRouteVersUneAutreCibleReevalueeEnDirect`) :
sur un vaisseau à un seul bouclier déjà saturé (30 > 5), un second appel à
`getNumeroBouclierValide()` retourne **-1** — confirmant qu'aucun
empilement supplémentaire sur ce même bouclier n'est possible : un tir
suivant serait routé ailleurs (autre bouclier, ou coque), pas vers une
nouvelle accumulation sur le compteur déjà dépassé.

**Test 3** (`coupQuiSatureLeBouclier_nAppliqueAucunDegatDeCoqueEnSpillover`) :
appel direct de `Vaisseau.effectuerDommages` (via réflexion, méthode privée
— seul le point d'entrée est atteint par réflexion, aucun état de dégâts
n'est forcé) avec une arme infligeant 30 points de dégâts de bouclier ET 15
points de dégâts de coque, sur une cible dont le bouclier n'a que 5 points
de niveau. Résultat mesuré :

```
boucliers[0] après le coup = 30   (le bouclier absorbe tout, dépasse son niveau de 5)
nombreTotalPointsDeDommage() de la cible = 0   (AUCUN dégât de coque appliqué)
```

Confirme précisément l'implication soulevée en tête de document : le coup
qui sature le bouclier ne laisse rien "déborder" vers la coque, quelle que
soit l'ampleur du dépassement.

Suite complète du projet : 41/41 tests passent avec ce nouveau fichier de
test ajouté (aucun correctif de production appliqué).

## 4. Comparaison avec le cas corrigé

| | Mines (`ConstructionPlanetaire`, corrigé) | Bouclier (`Vaisseau`, non corrigé) |
|---|---|---|
| Compteur non plafonné | `dommages` | `boucliers[numBouc]` |
| Liste de cibles réévaluée en direct avant chaque tir ? | **Non** — figée en tête de round (`Combat.tirAirSol`) | **Oui** — `getNumeroBouclierValide()` à chaque tir |
| Ampleur du dépassement possible | Illimitée sur toute une salve (jusqu'à 4,7× le plafond observé en réel) | Bornée à un seul coup (au plus les dégâts bruts d'une arme) |
| Gravité | Confirmée en jeu réel, corrigée | Défaut réel mais mineur, non observé en rapport réel à ce jour |

## 5. Pourquoi les autres compteurs de dégâts de `Vaisseau` ne sont pas concernés

`Vaisseau.ajouterDommagesAuHasard`/`ajouterDommage` (dégâts de coque,
répartis composant par composant) sont structurellement auto-limités : un
composant déjà entièrement endommagé (`composantInutilisable`) est retiré
de `listeComposantsValides`, la liste dans laquelle `ajouterDommageAuHasard`
choisit sa cible — aucun composant ne peut donc recevoir plus de points que
son `nombreDeCasesPrises`. Ce mécanisme est différent de celui des
boucliers/mines et n'est pas concerné par ce défaut.

## 6. Portée — non corrigé, sur demande explicite

Aucun correctif n'a été appliqué à `Vaisseau.ajouterDommagesBouclier`. Si
un correctif était souhaité, il suivrait le même principe que celui déjà
appliqué à `ConstructionPlanetaire.ajouterDommages` :

```diff
 	public void ajouterDommagesBouclier(int numBouc, int dommages) {
-		boucliers[numBouc] = boucliers[numBouc] + dommages;
+		boucliers[numBouc] = Math.min(
+				boucliers[numBouc] + dommages,
+				/* niveau du bouclier correspondant */);
 	}
```

Non implémenté ici : `ajouterDommagesBouclier` ne connaît actuellement que
l'indice du bouclier, pas son niveau — il faudrait soit passer le niveau en
paramètre depuis les deux appelants, soit le résoudre en interne à partir de
`plan`, ce qui mérite sa propre décision de conception avant correctif.
