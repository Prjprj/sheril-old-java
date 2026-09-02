# Construction planétaire silencieusement tronquée par manque d'espace

- **Fichier modifié** : `src/main/java/zIgzAg/jeu/oceane/Possession.java`
- **Méthode en cause** : `Possession.resolutionConstructions`
- **Nature** : bug logique (condition incomplète), pas un problème de
  données — atteignable en jeu normal, sans configuration particulière.

## 1. Comportement observé

Signalé par un utilisateur : sur une planète, une commande de 10
**Boucliers planétaires VII** programmée un tour donné ne voit qu'**une
seule unité sortir**, sans qu'aucun message d'erreur ou d'avertissement
n'explique le manque. L'utilisateur a précisé que l'incident a eu lieu
sur le **système 4-20**.

Retrouvé dans les données réelles fournies (`analyse/tour15/dump.sql`,
table `construire`) : `(10, '0_4_20', 'boucplaVII', 10, 100, 512)`. Cette
table journalise les ordres `construire` du tour (voir
`ReceptionOrdres.construire` → `Commandant.mettreEnChantier`) — la ligne
correspond donc à l'ordre du joueur 10 : « mettre en chantier 10
Boucliers planétaires VII sur le système 0_4_20, planète non précisée »
(`100` = `PLANETE_NON_PRECISE`, pas une planète ni un pourcentage — voir
§4 pour la levée de cette ambiguïté initiale).

Cet ordre, rejoué avec le vrai code de production sur l'état réel du
système 4-20 juste avant le tour (`comm.txt`/`sys.txt`), produit
exactement **1 unité construite sur les 10 commandées** (voir §4) — la
reproduction exacte du signalement.

## 2. Cause racine

`Possession.resolutionConstructions(Commandant com, Systeme s)` calcule,
pour chaque ligne de construction en cours, quatre limites indépendantes
sur le nombre d'unités qui peuvent réellement sortir ce tour : l'argent
(`pasAssezDeCentaure`), le minerai (`pasAssezDeMinerai`), les
marchandises (`pasAssezDeMarchandises`) et **l'espace libre sur le
système** (`pasAssezDePlace`, dérivée de `l_espace`, elle-même dérivée de
`s.getEspaceLibre(...)` et du nombre de points de structure du bâtiment).

`nbbis` (nombre réellement construit) est bien réduit par les quatre
limites :

```java
if (l_espace < nb) {
    nbbis = Math.min(l_espace, nbbis);
    pasAssezDePlace = true;
}
```

Mais au moment de décider **quel message envoyer au joueur**, seules
trois des quatre limites sont testées :

```java
// On envoi le log de la construction
if (pasAssezDeCentaure || pasAssezDeMinerai || pasAssezDeMarchandises) {
    String nombreConstruit = nbbis + "/" + nb;
    List<String> manqueL = new ArrayList<>();
    if (pasAssezDeCentaure) manqueL.add("centaure");
    if (pasAssezDeMinerai) manqueL.add(Messages.MINERAI);
    if (pasAssezDeMarchandises) manqueL.add("marchandise (" + ... + ")");
    if (pasAssezDePlace) manqueL.add("d'espace libre"); // <- jamais atteint seul

    com.ajouterEvenement("EV_COMMANDANT_CONSTRUCTION_0002", ...);

} else { // Sinon la construction est OKAY
    com.ajouterEvenement("EV_COMMANDANT_CONSTRUCTION_0001",
            s.getPosition(), descriptionTechno, nbbis);
}
```

`pasAssezDePlace` est bien ajouté à la liste `manqueL` — mais cette
branche n'est atteinte que si l'une des trois *autres* limites est vraie.
Quand l'espace est la **seule** contrainte active (argent, minerai et
marchandises tous suffisants pour la totalité de la commande), la
condition est fausse, le code tombe dans le `else`, et émet
`EV_COMMANDANT_CONSTRUCTION_0001` — l'événement de **succès** — avec
`nbbis` (1, dans le cas rapporté) comme si c'était le nombre demandé. Le
joueur voit "1 Bouclier planétaire VII construit", sans jamais être
informé que 9 autres unités, pourtant payées, restent bloquées faute de
place (le même bug existe symétriquement dans la branche `nbbis == 0`,
avec `EV_COMMANDANT_CONSTRUCTION_0003`).

Les 9 unités restantes ne sont pas perdues (`c[i].diminuerNombre(nbbis)`
ne retire que celles réellement sorties) : elles restent programmées et
pourront sortir un tour ultérieur si de l'espace se libère — mais rien
dans les événements du tour ne le signale.

## 3. Correctif proposé

Inclure `pasAssezDePlace` dans les deux conditions qui décident d'émettre
un message d'avertissement plutôt qu'un message de succès muet :

```diff
-				if (pasAssezDeCentaure || pasAssezDeMinerai || pasAssezDeMarchandises) {
+				if (pasAssezDeCentaure || pasAssezDeMinerai || pasAssezDeMarchandises || pasAssezDePlace) {
 					String nombreConstruit = nbbis + "/" + nb;
 					...
 					com.ajouterEvenement("EV_COMMANDANT_CONSTRUCTION_0002", ...);
 				} else { // Sinon la construction est OKAY
 					com.ajouterEvenement("EV_COMMANDANT_CONSTRUCTION_0001", ...);
 				}
 			} else { // Si jamais il n'y a aucune construction ( nbbis == 0 )
-				if (pasAssezDeCentaure || pasAssezDeMinerai || pasAssezDeMarchandises) {
+				if (pasAssezDeCentaure || pasAssezDeMinerai || pasAssezDeMarchandises || pasAssezDePlace) {
 					String nombreConstruit = ""+nb;
 					...
 					com.ajouterEvenement("EV_COMMANDANT_CONSTRUCTION_0003", ...);
 				}
```

Aucun correctif compagnon nécessaire : `manqueL.add("d'espace libre")`
existait déjà dans les deux blocs, seule la condition de garde était
incomplète.

## 4. Reproduction empirique sur les données réelles (`analyse/tour15`)

Rejoué avec le vrai code de production (classes compilées du projet,
`Univers` réinitialisé a minima par réflexion — technologies, aléatoire,
listes de messages — pour éviter de dépendre d'une base MySQL) sur
`comm.txt`/`sys.txt` désérialisés tels quels, sans aucune valeur forcée :

**Données réelles chargées (joueur 10, système 4-20) :**

| Donnée | Valeur | Origine |
|---|---|---|
| `c.getCentaures()` | 177 482,14 | `Commandant` désérialisé |
| `getCapaciteEncombrement(10)` | 26 300 | somme des 20 planètes du joueur |
| `getEncombrement(10)` | 3 643 | idem |
| `getEspaceLibre(10)` | **22 657** | capacité − encombrement |
| `getPointsDeConstructionModifie(...)` | 2 177 | potentiel de construction du système ce tour |
| `getStockMinerai(10)` avant production du tour | 0 | snapshot pris avant `Systeme.evolutionMinerai` |

**Caractéristiques réelles de `boucplaVII`** (`Univers.getTechnologie`) :
`pointsDeConstruction = 40`, `pointsDeStructure = 17150`, `prix = 400,0`,
`mineraiNecessaire = 40`.

**Financement des points** : la ligne `boucplaVII` (nombre=10) partage le
potentiel du système avec une ligne `mineI` déjà en cours. Besoin total
= 10 × 40 = 400 points ; potentiel disponible = 2 177 > 400 → les 10
unités sont **intégralement financées** ce tour (`nb = 10`).

**Étape du pipeline de tour omise par un premier essai naïf** :
`Commandant.java` appelle `Systeme.evolutionMinerai(numero)` (~ligne
1717) **avant** `Possession.resolutionConstructions` (~ligne 1726). Le
snapshot `sys.txt` est pris avant cette étape : le stock minerai y est à
0, mais passe à **455** une fois la production du tour rejouée — c'est
cette valeur de 455, et non 0, que voit réellement
`resolutionConstructions`.

**Les 4 limites, avec les vraies valeurs (potentiel déjà financé, minerai
après production) :**

```
l_argent  = (int)(177482,14 / 400,0) = 443   → pasAssezDeCentaure = false
l_minerai = (int)(455 / 40)          = 11    → pasAssezDeMinerai  = false
l_espace  = 22657 / 17150            = 1     → pasAssezDePlace    = true   (1 < 10)

nbbis = min(nb, l_argent, l_minerai, l_espace) = min(10, 443, 11, 1) = 1
```

L'espace libre (22 657) rapporté à la structure d'un seul bouclier
(17 150) ne permet qu'**une unité** — argent et minerai sont largement
suffisants pour les 10. C'est exactement, et uniquement, le scénario que
le correctif du §3 adresse.

**Résultat de l'exécution réelle de `Possession.resolutionConstructions`**,
avec le code original (bug) :

```
File de construction après résolution : boucplaVII nombre=9, pointsEffectues=360
EVENEMENT EV_COMMANDANT_CONSTRUCTION_0001 params=[0_4_20, <Batiment boucplaVII>, 1]
```

→ un événement de **succès** portant sur `1`, sans aucune mention des 9
unités restantes ni de la contrainte d'espace : la reproduction exacte,
avec des données réelles, du symptôme rapporté.

Avec le correctif appliqué, la même exécution produit :

```
EVENEMENT EV_COMMANDANT_CONSTRUCTION_0002 params=[0_4_20, "1/10", <Batiment boucplaVII>, "d'espace libre"]
```

## 5. Vérification par test dédié

Test écrit et exécuté (`src/test/java/zIgzAg/jeu/oceane/
ConstructionEspaceInsuffisantSansMessageTest.java`), qui appelle
directement `Possession.resolutionConstructions` avec :

- une commande de 10 `boucplaVII` entièrement financée ce tour
  (`pointsEffectues = 10 × pointsParUnite`, `potentiel` mocké en
  conséquence sur `Systeme.getPointsDeConstructionModifie`),
- de l'argent et du minerai largement suffisants pour les 10 unités,
- un espace libre (`Systeme.getEspaceLibre`) ne permettant qu'**une
  seule** unité (structure de 50, espace de 50).

Résultat avant correctif (confirmé en exécutant le vrai code de
production, `Univers` mocké statiquement via Mockito) :

```
Tests run: 1, Failures: 1  // le test attend le message 0002, mais seul 0001 (succès, "1") est émis
```

Contre-vérification (sanity check) : en modifiant temporairement la
condition pour inclure `pasAssezDePlace` (le correctif visé), le même
test bascule de l'échec à la réussite — confirmant que le test discrimine
bien le comportement buggé du comportement corrigé, et n'est pas
tautologique.

Résultat après correctif (`mvn test`, exécution complète de la suite) :

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ConstructionEspaceInsuffisantSansMessageTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- SessionSQLTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`EV_COMMANDANT_CONSTRUCTION_0002` est bien émis avec `nombreConstruit =
"1/10"` et la mention "d'espace libre" dans le libellé, et
`EV_COMMANDANT_CONSTRUCTION_0001` n'est plus émis pour ce cas. La file de
construction contient toujours, comme avant le correctif, une entrée
résiduelle de 9 unités (le correctif ne change pas ce qui est
effectivement construit, seulement l'information donnée au joueur).

## 6. Portée et limites du correctif

- S'applique à toute construction planétaire (bâtiment avec points de
  structure > 0 : boucliers, batteries, mines, chantiers...) dont l'espace
  libre du système est l'unique facteur limitant le nombre d'unités
  sorties ce tour.
- Ne change ni le nombre d'unités réellement construites (`nbbis`), ni la
  progression de la file de construction restante — seule l'information
  transmise au joueur (message de succès vs message d'avertissement) est
  corrigée.
- Ne résout pas un éventuel manque d'information sur *pourquoi* l'espace
  est insuffisant (nombre de points de structure déjà occupés, capacité
  totale du système) : le message se limite à mentionner "d'espace
  libre", comme pour les autres ressources manquantes — cohérent avec le
  niveau de détail déjà en place pour minerai/centaures/marchandises.

## 7. Effet du fractionnement de l'ordre sur plusieurs commandes ou plusieurs tours

Question soulevée pendant l'investigation : le joueur aurait-il eu un
meilleur résultat en passant 9 ordres séparés de 1 unité plutôt qu'un
seul ordre de 10 ? La réponse dépend entièrement de si ce fractionnement
a lieu **dans le même tour** ou **sur plusieurs tours**.

### Même tour : aucun effet

`Commandant.mettreEnChantier` ajoute chaque ordre via
`Possession.ajouterConstruction`, qui **fusionne** les ordres portant sur
le même code de bâtiment et la même planète (`Integer.MIN_VALUE` quand
elle n'est pas précisée) :

```java
public void ajouterConstruction(Construction c) {
    Construction[] l = listeConstructions();
    for (int i = 0; i < l.length; i++)
        if (l[i].getCode().equals(c.getCode()) && l[i].getPlanete() == c.getPlanete()) {
            l[i].augmenterNombre(c.getNombre());
            return;
        }
    constructions.add(c);
}
```

9 ordres de `construire boucplaVII x1` passés le même tour, sans planète
précisée, fusionnent donc **avant résolution** en une seule ligne
`nombre = 9` — l'état vu par `resolutionConstructions` est identique à
celui d'un ordre unique de 9. Or `l_espace` (= espace libre du système /
structure du bâtiment) ne dépend pas de `nb` : c'est un plafond fixe du
système pour ce tour, pas une fraction du nombre demandé. Résultat
identique quel que soit le découpage : `nbbis = min(nb, ..., l_espace) =
min(9, ..., 1) = 1`, toujours **1 seule unité construite**, peu importe
que la demande soit passée comme "9" ou comme "1 × 9".

### Sur plusieurs tours (1 par tour) : ça fonctionne sans anomalie

Si le joueur avait commandé 1 seul Bouclier VII par tour (en attendant
qu'il sorte avant de recommander le suivant), chaque tour aurait vu
`nb = 1` et `l_espace = 1` → `nbbis = min(1, ..., 1) = 1` : le nombre
construit correspond exactement au nombre demandé, la branche `else`
(succès) du code est correcte dans ce cas — pas de troncature, donc pas
de bug, avec ou sans correctif.

De plus, l'encombrement croît très marginalement à chaque unité
construite (+40 points de construction par bouclier, sur un pool cumulé
de 22 657 pour ce joueur sur ce système — voir §4) : `l_espace` resterait
à 1 pendant un très grand nombre de tours. Les 9 unités auraient donc
fini par sortir, une par tour, sur 9 tours, sans jamais déclencher la
situation observée.

**Conclusion** : le bug ne dépend pas du nombre total demandé mais du
fait de le demander **en un seul lot dépassant le plafond d'un tour**. Le
moteur gère correctement le cas "1 demandé, 1 possible" ; c'est
uniquement le cas "N demandés, M < N possibles" qui déclenchait le
message de succès trompeur (corrigé au §3).

## 8. Piste de refonte proposée — non implémentée

Le correctif du §3 résout le symptôme (message trompeur) mais ne change
rien à la façon dont l'encombrement disponible est réparti entre les
lignes de construction en cours. Deux limites structurelles restent en
place, indépendantes du bug corrigé ici :

1. **Répartition dépendante de l'ordre d'insertion, pas d'un objectif
   explicite.** `resolutionConstructions` itère `listeConstructions()`
   dans l'ordre du tableau (= ordre de programmation des ordres ce tour)
   et relit `s.getEspaceLibre(numero)` à chaque ligne — ce qui reflète
   correctement la consommation des lignes précédentes (puisque
   `ajouterRichesses` a déjà mis à jour l'encombrement en fin de ligne
   précédente), mais ne cherche jamais à *maximiser* le nombre total
   d'unités construites ce tour. Une grosse ligne (ex. un bâtiment à forte
   structure) placée en premier peut épuiser tout l'espace restant pour 1
   seule unité, alors qu'une ligne plus légère placée juste après aurait
   pu produire plusieurs unités dans le même espace.
2. **Budget "espace" figé par ligne, pas par le tour dans son ensemble.**
   Le calcul actuel (`l_espace = espaceLibreSurSysteme / nbPointsDeStructure`,
   une division entière unique par ligne) est correct *au sein d'une même
   ligne*, mais rien ne re-belance l'espace non consommé par une ligne
   totalement/partiellement bloquée vers une autre ligne différente qui,
   elle, aurait pu en profiter — dans l'implémentation actuelle ce
   rééquilibrage se produit implicitement (ligne suivante = espace déjà
   décrémenté), mais seulement dans l'ordre où les lignes apparaissent
   dans `constructions`, jamais en réexaminant les lignes précédentes une
   fois toutes traitées.

**Proposition (à valider avant toute implémentation)** : remplacer le
parcours séquentiel actuel par une allocation gloutonne explicite du pool
d'encombrement du joueur sur le système, calculée une seule fois en
début de méthode :

```
potentiel_espace = s.getEspaceLibre(com.getNumero())   // une seule lecture pour tout le tour

trier les lignes de construction (bâtiments uniquement, nbPointsDeStructure > 0)
    par pointsDeStructure croissant   // maximise le nombre total d'unités constructibles
    // alternative : conserver l'ordre d'arrivée pour rester prévisible/équitable —
    // compromis débit vs équité à trancher avec le porteur produit

pour chaque ligne (dans cet ordre) :
    nb_ligne = points déjà financés / pc (comme aujourd'hui)
    l_espace_ligne = potentiel_espace / nbPointsDeStructure
    nbbis = min(nb_ligne, l_argent, l_minerai, l_marchan, l_espace_ligne)
    construire nbbis unités
    potentiel_espace -= nbbis * nbPointsDeStructure   // décrément explicite, plus de relecture système
```

Points à trancher avant implémentation :

- **Critère de tri** : structure croissante (maximise le *nombre* de
  bâtiments sortis, mais peut faire attendre indéfiniment un gros
  bâtiment derrière un flux de petits) vs ordre d'arrivée actuel (prévisible,
  mais peut gaspiller de l'espace comme observé ici) vs une pondération
  configurable. Un changement de comportement joueur-visible, à valider
  avec le porteur produit avant tout correctif.
- **Traçabilité** : si le tri change l'ordre de résolution, les messages
  d'événement (§3) doivent rester associés à la bonne ligne/bâtiment pour
  ne pas dérouter le joueur qui compare sa liste d'ordres à la liste
  d'événements.
- **Risque de régression** : `s.getEspaceLibre()` est aujourd'hui relu
  après chaque ligne parce que `ajouterRichesses` modifie l'état de la
  planète en direct (couplage implicite) ; le remplacer par un compteur
  en mémoire explicite est plus robuste (n'a plus besoin de cette
  relecture) mais doit être vérifié avec un test qui couvre au moins
  deux lignes de types de bâtiments différents en concurrence pour le
  même espace, dans les deux ordres d'arrivée possibles.
- **Compatibilité** avec le mécanisme d'auto-programmation
  (`programmationConstructions`, §2) qui ajoute déjà de nouvelles lignes
  avant la résolution du tour — le tri/l'allocation doit s'appliquer
  après cette étape, comme c'est le cas aujourd'hui.

Cette piste est documentée ici pour référence future ; **elle n'a pas été
implémentée** dans le cadre de ce correctif, qui reste volontairement
minimal (§7 du principe directeur : un correctif par anomalie identifiée,
pas de refactoring combiné).
