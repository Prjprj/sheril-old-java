# Audit règles vs code

Comparaison systématique entre les règles du jeu telles que documentées
dans `rules/` et le comportement réel implémenté dans le code Java
(`src/main/java/zIgzAg/jeu/oceane`). Chaque écart est vérifié par lecture
du code (avec recherche des appelants pour confirmer qu'un mécanisme
décrit dans les règles/l'aide en jeu n'est bien jamais déclenché), pas
par supposition.

Portée : les fichiers `rules/*.md` et `rules/Mise à jour/*.md` numérotés
(1 à 8), qui décrivent les règles actuelles du jeu. Le fichier
`rules/Mise à jour/0.0 Propositions d'amélioration du jeu.md` est exclu :
il liste des propositions d'évolution, pas des règles en vigueur.

Document vivant, complété au fil des sections auditées (une section =
un domaine de règles). Chaque écart est numéroté et marqué de son statut :

- **Écart confirmé** : le code contredit ou n'implémente pas ce que
  décrivent les règles — vérifié par lecture du code source et recherche
  des appelants.
- **Comportement non documenté** : le code fait quelque chose de
  cohérent mais que les règles ne mentionnent pas.

## 1. Technologies

Règles auditées : `rules/technologie.md` et
`rules/Mise à jour/6. Recherches technologiques.md`.

Code audité : `Technologie.java`, `Commandant.java`
(`src/main/java/zIgzAg/jeu/oceane`).

### 1.1 [Écart confirmé] Seuil de publication d'une technologie : 60% dans le code, 75% dans les règles

Les règles (§3.2 de `technologie.md`, §6.2 de la mise à jour) sont
formelles : *"Pour qu'une technologie devienne publique, il faut qu'à un
moment donné 75% des commandants la connaisse."*

Le code (`Technologie.java`, méthode `testDevenirTechnologiesPubliques`)
compare au seuil de **60%** :

```java
// Technologie.java:292
if (nb > (c.length * 60) / 100) {
    Univers.ajouterEvenement("PUBLIC_TECHNOLOGIE_0000", t[i]);
    Univers.ajouterTechnologieAuDomainePublic(t[i].getCode());
    ...
}
```

Une technologie connue par exemple par 65% des commandants humains
devient donc publique dans le jeu réel, alors que les règles annoncées
aux joueurs promettent 75%.

### 1.2 [Écart confirmé] Entretien des technologies non-publiques : décrit dans les règles et l'aide en jeu, jamais implémenté

Les règles (§3.2) : *"Chaque technologie non-publique connue nécessite
un entretien... Chaque technologie qui ne représente pas un composant de
vaisseau coûte 1% des revenus totaux des systèmes du commandant."*
Exemple donné : 20 technologies non-publiques/non-composants → 20% des
revenus des systèmes.

Le texte d'aide en jeu de l'ordre "Abandonner une technologie"
(`php/ordres/fr/aide/abandonner_technologie.txt`) confirme cette
attente côté joueur : *"Cela vous permettra de ne plus avoir à payer son
entretien."*

Le code ne facture pourtant jamais cet entretien :

```java
// Commandant.java:1081
public float getEntretienTechnologies() {
    return 0;
}
```

Recherche de tous les appelants de `getEntretienTechnologies()` dans
`src/main/java` : **aucun** — la méthode n'est appelée nulle part, y
compris dans `finaliserBudget()` (qui calcule pourtant l'entretien de la
flotte et des systèmes juste à côté, lignes ~1784-1793).

Une constante de catégorie budgétaire dédiée existe même
(`Const.BUDGET_COMMANDANT_ENTRETIEN_TECHNOLOGIES = 42`) mais n'est
utilisée nulle part non plus (recherche de toutes ses occurrences dans
`src/main/java` : une seule, sa déclaration).

Conclusion : posséder des technologies non-publiques est aujourd'hui
gratuit dans le jeu réel, quel que soit leur nombre — contrairement à ce
que règles et aide en jeu annoncent aux joueurs. L'exception "les
technologies de composants de vaisseaux ne paient pas d'entretien"
(§3.2) est de fait sans objet puisqu'aucune technologie ne paie
d'entretien.

### 1.3 [Comportement non documenté] Remise de 80% sur le budget technologique non affecté

Les règles décrivent comment attribuer tout ou partie du budget
technologique à des technologies (§3.1 / §6.1), mais ne disent rien sur
ce qu'il advient de la part non attribuée.

Le code (`Commandant.java`, méthode appelée en fin de tour, ~ligne 1742)
reverse 80% du budget technologique non affecté vers les revenus divers
du commandant :

```java
// Commandant.java:1742-1750
if (totalAffectationPourcentage() < 100) {
    float argent = (float) ((100 - totalAffectationPourcentage())
            * getBudgetDepense(Const.DOMAINES_BUDGET_TECHNOLOGIQUE)
            * 0.8 / 100);
    if (argent > 0) {
        modifierBudget(Const.BUDGET_COMMANDANT_REVENUS_DIVERS, argent);
        ajouterEvenement("EV_TECHNO_RABAIS_0000", argent);
    }
}
```

Un événement dédié (`EV_TECHNO_RABAIS_0000`) existe donc côté jeu pour
notifier ce remboursement — le mécanisme est bien volontaire, seulement
absent des règles publiées.

### 1.4 Points conformes aux règles (vérifiés, pour mémoire)

- **1 Centaure de budget technologique alloué → 1 point de recherche** :
  confirmé, `ajouterPointsDeRecherche(s[i], (int) (r * pourcentage) / 100)`
  où `r` est le budget technologique total (`Commandant.java:1762`),
  cohérent avec §3.1 de `technologie.md`.
- **Persistance du pourcentage alloué d'un tour sur l'autre si le seuil
  n'est pas atteint** : confirmé, le pourcentage n'est réinitialisé que
  lors de l'obtention de la technologie
  (`suppressionDomaineDeRecherche` appelée uniquement dans ce cas,
  `Commandant.java:1764-1767`), cohérent avec §6.1 de la mise à jour.
- **Technologies spécifiques de race exclues de la publication** : les
  codes `maitr0_` à `maitr5_` (`Const.TECHNOLOGIES_RESERVED`) sont
  explicitement écartés du calcul de passage au domaine public
  (`Technologie.java:289`), cohérent avec la mention de `technologie.md`
  §Introduction sur les technologies spécifiques à une race.

---

## 2. Constructions

Règles auditées : `rules/constructions.md` et
`rules/Mise à jour/3. Constructions.md`.

Code audité : `Construction.java`, `Possession.java`, `Planete.java`,
`Systeme.java`, `Commandant.java`
(`src/main/java/zIgzAg/jeu/oceane`).

### 2.1 [Écart confirmé] Mise au rebut : 100% du minerai récupéré dans le code, 50% dans les règles

Les règles (§4.2.2 de `constructions.md`) sont explicites : *"Si vous
possédez une usine de retraitement de minerai sur la planète où vous
voulez détruire du matériel, vous récupérez automatiquement **la
moitié** du minerai qui avait été consommé lors de la construction du
bâtiment."*

Le code (`Planete.java`, méthode `recyclerMateriel`, appelée depuis
`Commandant.detruireBatiments`) recrédite l'intégralité du minerai
consommé, pas la moitié :

```java
// Planete.java:947-953
public int recyclerMateriel(Batiment b, int nombre) {
    int nbElimine = eliminerBatiment(b, nombre).getNombreObjets();
    if (nbElimine > 0)
        if (contientUniteDeRecyclage())
            ajouterMinerai(b.getMineraiNecessaire() * nbElimine);
    return nbElimine;
}
```

`b.getMineraiNecessaire() * nbElimine` est le coût minerai total des
bâtiments détruits, sans aucune division par 2. `contientUniteDeRecyclage()`
vérifie bien la présence du bâtiment "retraiteI" (capacité spéciale
`BATIMENT_CAPACITE_RECYCLAGE_MINERAI`, `ListeCaracSpeciales.java:173`),
donc la condition d'activation est correcte — seul le taux de
récupération est faux (100% au lieu de 50%).

### 2.2 [Comportement non documenté] Détruire un bâtiment exige la technologie "gestplaI", absente des règles

`Commandant.detruireBatiments` refuse la démolition si le commandant ne
connaît pas la technologie `gestplaI` :

```java
// Commandant.java:2931-2933
if (!estTechnologieConnue("gestplaI"))
    return Univers.ajouterErreur(getNomNumeroHtml(),
            "ER_COMMANDANT_DETRUIRE_BATIMENT_0002");
```

Ni `constructions.md` §4.2.2 ("Mise au rebut de matériel") ni la version
à jour ne mentionnent de prérequis technologique pour démolir un
bâtiment — les règles présentent la mise au rebut comme une option
toujours disponible pour alléger ses coûts d'entretien.

### 2.3 [Écart confirmé] Aucune limite de 999 unités appliquée aux transferts inter-systèmes

Les règles (`constructions.md` §4.2.3 et `Mise à jour/3. Constructions.md`
§3.3) précisent : *"Il est possible de transférer un maximum de 999
unités par transfert inter-système."*

Recherche de cette limite dans le code (`Commandant.transfererEntreSysteme`,
`ObjetTransporte`/`ObjetSimpleTransporte`/`ObjetComplexeTransporte`) :
**aucune limite numérique de ce type n'existe**. La seule borne
appliquée à un transfert de marchandise est le stock disponible sur la
planète d'origine :

```java
// Commandant.java:3241-3248
int present = p1.getQuantiteMarchandise(Utile.numeroMarchandise(code));
...
int nb2 = Math.min(present, nb);
```

Rien de comparable n'encadre `nb` pour un transfert de bâtiments. Un
commandant peut donc aujourd'hui transférer plus de 999 unités en un
seul ordre, contrairement à ce qu'annoncent les règles. Le nombre
*maximal de transferts par tour* (dépendant du nombre de systèmes
possédés, `getNombreMaximalDeTransfertEntreSysteme()`) est bien lui
implémenté — c'est uniquement le plafond de 999 unités *par transfert*
qui est absent.

### 2.4 [Incohérence entre les deux versions des règles, pas un défaut du code] Coût de conception d'un plan de vaisseau : 5x dans l'ancien fichier, 10x dans la version à jour — le code applique 10x

`rules/constructions.md` §4.1.3.2 (fichier le plus ancien, non retouché
depuis) : *"Ce coût est égal à **cinq** fois le coût de construction du
vaisseau."*
`rules/Mise à jour/3. Constructions.md` §3.5 (version à jour) : *"Il est
égal à **10 fois** le coût du vaisseau."*

Le code applique un facteur 10 :

```java
// Const.java:213
public static final int MODIFICATEUR_MULTIPLICATEUR_CREATION = 10;
// Commandant.java:3821-3822, 3828-3829
if (centaures < p.getPrix() * Const.MODIFICATEUR_MULTIPLICATEUR_CREATION)
    ...
modifierBudget(Const.BUDGET_COMMANDANT_CREATION_PLAN,
        -p.getPrix() * Const.MODIFICATEUR_MULTIPLICATEUR_CREATION);
```

Le code est donc cohérent avec la version à jour des règles — ce n'est
pas un écart de comportement, mais un signal que `rules/constructions.md`
est un document obsolète qu'il vaudrait la peine de retirer ou de
marquer comme périmé pour éviter toute confusion future (le même
document contient aussi une phrase sur "le coût d'entretien... équivalent
au coût de construction divisé par 10" qui n'apparaît dans aucune des
deux versions consultées côté flottes — à revérifier lors de l'audit du
chapitre flottes/combats).

### 2.5 Points conformes aux règles (vérifiés, pour mémoire)

- **Attribution round-robin d'un point de construction par ligne de
  construction en attente** : confirmé, `Possession.resolutionConstructions`
  distribue le potentiel de points un par un à chaque construction en
  cours tant qu'il en reste (`Possession.java:442-453`), reproduisant
  exactement l'exemple chiffré de `constructions.md` §4.3 (mines, poste
  commercial, chasseur).
- **Formule de production de minerai selon le nombre de mines** :
  confirmée avec l'exemple donné (`Planete.calculeRevenuMinerai`,
  `Planete.java:517-529`) — testé par calcul sur les valeurs de l'exemple
  de `Mise à jour/3. Constructions.md` §3.1 (valeur de base 3 → 3, 5, 6
  minerais pour 1, 2, 3 mines), cohérent avec le fait que chaque mine de
  base (`mineI`) apporte une capacité d'extraction de 1
  (`ListeCaracSpeciales.java:167`).
- **Bonus de stock de marchandises (§3.2 de la mise à jour)**, vérifiés
  pour les valeurs numériques suivantes : Robots +5 points de
  construction (`Systeme.java:861-862`), Articles de luxe +10% sur les
  revenus (`Systeme.java:646-647`), Métaux précieux +5% sur les revenus
  (`Systeme.java:648-649`), Pièces industrielles -10% d'entretien des
  bâtiments (`Systeme.java:668-669`) — tous conformes au tableau des
  règles.
- **Nécessité d'un chantier naval pour achever la construction d'un
  vaisseau** : confirmée, mais appliquée seulement à la *résolution* de
  la construction (`Possession.java:535`, capacité spéciale
  `BATIMENT_CAPACITE_PRODUCTION_VAISSEAU`) et non à la mise en chantier
  elle-même (`Commandant.mettreEnChantier` ne vérifie pas la présence
  d'un chantier naval avant d'accepter l'ordre). Les règles ne précisent
  pas à quel moment la condition doit être vérifiée ; ce n'est donc pas
  classé comme écart, mais à garder en tête si un futur signalement
  porte sur des points de construction "gaspillés" sur un système sans
  chantier naval.

---

## 3. Population

Règles auditées : `rules/Mise à jour/2. Population.md`.

Code audité : `Planete.java`, `Systeme.java`, `Possession.java`,
`Commandant.java`, `Flotte.java` (`src/main/java/zIgzAg/jeu/oceane`).

### 3.1 [Écart confirmé] Colonisation d'une planète déjà peuplée par la MÊME race : la pénalité d'extermination s'applique quand même

Les règles (§2.1.2) sont explicites : *"Si la planète est déjà colonisée
pour cette race, il ne se passe rien mais le colonisateur est
quand-même détruit."* L'extermination (-10 stabilité, -300 réputation,
population effacée) n'est censée s'appliquer que si une race
**différente** occupe déjà la planète.

Le code (`Commandant.coloniserPlanetes`) ne compare jamais la race déjà
présente à celle du colonisateur — il déclenche l'extermination dès
qu'il y a une race présente, quelle qu'elle soit :

```java
// Commandant.java:3613-3624
if (pla.getNombreDeTypeDePopulationsPresentes() > 0) {
    int[] racesPresentes = pla.racesPresentes();
    if (racesPresentes.length > 0 ){
        eradication = true;
        pla.setStabilite(pla.getStabilite()-10);
        ajouterReputation(-300);
        pla.initialiserPopulation();
    }
}
boolean reussite = pla.explorerPlanete(numRace);
```

Conséquence concrète pour une planète déjà colonisée par la **même**
race que le colonisateur : la population existante est effacée
(`initialiserPopulation()`), -10 de stabilité et -300 de réputation sont
appliqués comme s'il s'agissait d'une extermination réelle, *puis*
`explorerPlanete(numRace)` réussit (population désormais nulle) et
recrée la planète à 100 habitants — l'événement affiché est même
`EV_COMMANDANT_COLONISER_PLANETE_0002` (le message "extermination"), pas
un message "rien ne se passe". Le comportement attendu par les règles
(no-op silencieux, colonisateur détruit sans autre effet) n'existe pas.

### 3.2 [Écart confirmé] Choix du colonisateur en cas de plusieurs colonisateurs dans la flotte : premier trouvé, pas aléatoire

Les règles (§2.1.2) : *"En cas de présence de plusieurs colonisateurs
dans une même flotte, le colonisateur est choisi au hasard."*

Le code (`Flotte.trouverNumeroColonisateur`) retourne le premier
colonisateur rencontré dans l'ordre de la liste des vaisseaux de la
flotte — aucun tirage aléatoire :

```java
// Flotte.java:1049-1056
public int trouverNumeroColonisateur() {
    Vaisseau[] v = listeVaisseaux();
    Integer[] c = listeNumerosVaisseaux2();
    for (int i = 0; i < v.length; i++)
        if (v[i].estColonisateur())
            return c[i].intValue();
    return -1;
}
```

Le choix est donc déterministe (dépendant de l'ordre d'ajout des
vaisseaux à la flotte), ce qui devient significatif si les colonisateurs
présents sont d'équipages de races différentes : le joueur ne peut ni
prévoir ni influencer lequel sera utilisé autrement qu'en connaissant cet
ordre interne, contrairement à l'aléatoire annoncé.

### 3.3 [Écart confirmé] Pénalité d'absence de capitale : -16% de stabilité par tour dans le code, -10% annoncé dans les règles

Les règles (§2.2) : *"Si vous ne possédez pas de capitale, une pénalité
de -10 par tour en stabilité est appliquée."*

Le code réutilise la dernière entrée de la table de distance (utilisée
normalement pour "11 systèmes ou plus" = -16%) au lieu d'une valeur
dédiée :

```java
// Possession.java:343-344
if (c.getCapitale() == null)
    mod_pos = Const.MODIFICATEUR_STABILITE_CAPITALE[Const.MODIFICATEUR_STABILITE_CAPITALE.length - 1][1];
// Const.java:651-653 — dernière entrée de la table : {Integer.MAX_VALUE, -16}
```

Un commandant sans capitale subit donc -16% de stabilité par tour et par
système sur l'ensemble de son domaine, et non -10% comme annoncé — un
écart substantiel puisque c'est justement dans cette situation (perte de
la capitale) que la stabilité est la plus fragile.

*Le reste de la table de distance a été vérifié par calcul systématique
de l'algorithme de recherche (`Possession.java:346-354`) pour chaque
tranche de distance (0, 1-2, 3-4, 5, 6, 7, 8-9, 10, 11+) : toutes les
autres valeurs correspondent exactement au tableau des règles.*

### 3.4 [Écart confirmé] Malus de stabilité "Alcools et drogues" documenté mais désactivé dans le code

Le tableau des bonus de marchandises (`Mise à jour/3. Constructions.md`
§3.2, cohérent avec `Mise à jour/2. Population.md` sur les effets de
stabilité) annonce -1% de stabilité pour un stock important d'Alcools
et drogues.

Le code contient bien ce malus, mais en commentaire — donc jamais
appliqué :

```java
// Possession.java:326-330
// Stock de marchandises
/**
if (possedeStockImportantPoste(Const.PRODUIT_ALCOOLS))
    mod_post = mod_post - 1; // Pas de malus pour systeme de guidage
    **/
```

Les autres malus/bonus de la même méthode (Armement -1%, Holofilms +1%)
sont eux bien actifs et vérifiés conformes.

### 3.5 [Écart confirmé] Politique "Loisir" : absente du tableau à jour des règles, et taux de -20% sur les revenus au lieu des -5% de l'ancienne documentation

`rules/Mise à jour/2. Population.md` §2.3 liste 14 politiques numérotées
et ne mentionne aucune politique "Loisir". Elle n'apparaît que dans
l'ancien fichier `rules/univers_systemes_planetes.md` : *"0. Loisir :
les revenus des impôts de chaque planète du système sont diminués de
5%. La stabilité du système augmente de 2% par tour..."*

Le code implémente bien `POLITIQUE_LOISIR` (stabilité +2%/tour, conforme
à l'ancienne description), mais avec un impact sur les revenus de -20%
et non -5% :

```java
// Systeme.java:641-644 (calcul du revenu)
if (p.getPolitique() == Const.POLITIQUE_IMPOT)
    retour = retour + retour / 10;
if (p.getPolitique() == Const.POLITIQUE_LOISIR)
    retour = retour - retour / 5;   // -20%, pas -5%
```

Deux problèmes distincts : la politique "Loisir" a disparu du tableau
des règles à jour (alors qu'elle existe et reste sélectionnable en jeu),
et sa pénalité de revenu réelle (-20%) ne correspond même pas à la valeur
de l'ancienne documentation (-5%).

### 3.6 [Écart mineur, confirmé] Seuil d'éradication d'une population (politiques anti-race) : `≤ 30` dans le code, `< 30` dans les règles

Règles (§2.3, politique Anti-fremens) : *"Si la population fremen sur
une planète est inférieure à 30, elle est éradiquée."* — sinon, la
population est divisée par deux.

```java
// Planete.java:701-712
public int politiqueExtermination(int race) {
    Population p = getPopulation(race);
    if (p == null) return 0;
    if (p.getPopActuelle() > 30) {
        int retour = p.getPopActuelle() / 2;
        p.setPopActuelle(retour);
        return retour;
    }
    eradiquerPopulation(race);
    return p.getPopActuelle();
}
```

Une population exactement égale à 30 est éradiquée par le code
(`> 30` faux → branche `else`) alors que les règles ("inférieure à 30")
prévoient qu'elle soit seulement divisée par deux à cette valeur
exacte. Écart d'une unité sur la frontière, gain en centaures de la
politique bien reversé au budget par ailleurs (`Systeme.java:344`,
conforme aux règles).

### 3.7 Points conformes aux règles (vérifiés, pour mémoire)

- **Table de modificateur de stabilité selon le taux de taxation** :
  `Const.MODIFICATEUR_STABILITE_TAXATION = {6, 3, 1, -3, -7, -12}`,
  exactement les valeurs du tableau des règles pour les niveaux 0 à 5.
- **Effets des politiques sur la stabilité** : Totalitaire +2%
  (`Systeme.java:315-316`), Intégrisme -2% (`:317-318`), Esclavagiste
  -2% (`:319-320`), politiques anti-race -5% de stabilité et -300 de
  réputation par tour (`aPolitiqueAnti()`, `:323-324`) — tous conformes.
- **Bonus/malus de marchandises actifs** : Armement -1% de stabilité,
  Holofilms +1% de stabilité (`Possession.java:331-336`).

---

## 4. Lieutenants

Règles auditées : `rules/Mise à jour/8. Lieutenants.md`.

Code audité : `Leader.java`, `Heros.java`, `Gouverneur.java`,
`ReceptionOrdres.java`, `Const.java`
(`src/main/java/zIgzAg/jeu/oceane`).

### 4.1 [Écart confirmé, risque latent] Les tables de probabilité de compétences ne couvrent que 6 races sur les 7 décrites par les règles

Le tableau des chances d'obtenir une nouvelle compétence (§8.4) donne
des valeurs pour **sept** races : Fremen, Atalante, Zwaias, Yoksor,
Fergok, Cyborg **et Koros**.

Le code ne définit que 6 lignes (races d'indice 0 à 5, cf.
`Messages.RACES = {"Fremens","Atalantes","Zwaias","Yoksor","Fergok","Cyborg"}`,
sans Koros) :

```java
// Const.java:512-519
public static final int[][] CHANCE_TROUVER_COMPETENCE_HEROS = {
        {9, 9, 9, 9, 0, 9, 9, 9, 9, 9, 0, 0, 10, 0, 0},   // Fremens
        {15, 5, 5, 7, 0, 8, 7, 10, 10, 15, 0, 0, 10, 0, 0}, // Atalantes
        {15, 15, 15, 5, 0, 12, 5, 5, 5, 5, 0, 0, 13, 0, 0}, // Zwaias
        {8, 5, 5, 15, 0, 12, 8, 5, 15, 5, 0, 0, 7, 0, 0},   // Yoksor
        {8, 5, 5, 15, 0, 12, 8, 5, 15, 5, 0, 0, 7, 0, 0},   // Fergok
        {8, 5, 5, 15, 0, 12, 8, 5, 15, 5, 0, 0, 7, 0, 0},   // Cyborg
};
// (idem pour CHANCE_TROUVER_COMPETENCE_GOUVERNEUR)
```

`competenceNouvelleAuHasard(race)` indexe ce tableau directement avec le
code race du lieutenant. Un lieutenant de race Koros (indice 6)
provoquerait une `ArrayIndexOutOfBoundsException` à sa prochaine montée
de niveau.

Portée : les nouveaux lieutenants mis aux enchères chaque tour ne
peuvent pas être Koros — `Leader.creer` tire la race avec
`Univers.getInt(Const.NB_RACES - 1)` (`Const.NB_RACES` = 6, donc un
indice 0-4 selon la convention de `getInt`), l'indice 6 n'est jamais
généré par ce chemin. Le risque n'est donc pas atteignable par le
tirage aléatoire standard. Il reste néanmoins réel : la documentation du
projet elle-même (`rules/Mise à jour/0.0 Propositions d'amélioration du
jeu.md`, non auditée ici sur demande, mais consultée en passant) atteste
que *"certains lieutenants sont de la race Koros"* en jeu — probablement
issus de données historiques/scénario plutôt que du tirage aléatoire
standard. Le champ exact d'atteignabilité n'a pas pu être confirmé
davantage sans accès aux données de partie ; ce point est à vérifier
avant de le classer en bug bloquant.

### 4.2 [Comportement non documenté] Une compétence "voyage intragalactique" existe dans le code, absente des règles, et n'est jamais tirée

`Const.java` définit `COMPETENCE_LEADER_VOYAGE_INTRAGALACTIQUE = 10`, en
plus de `COMPETENCE_LEADER_VOYAGE_INTERGALACTIQUE = 11` qui, seule, est
documentée (§8.3/8.4, "Voyage intergalactique").

Dans les deux tables de probabilité (héros et gouverneurs, toutes
races), l'indice 10 (voyage intragalactique) vaut systématiquement 0 —
cette compétence ne peut donc jamais être attribuée par le tirage
aléatoire. Code mort mais non documenté, sans impact actuellement
observable en jeu.

### 4.3 [Écart confirmé, mineur] Chance de survie "Immortalité" : `1 + niveau × 20`% dans le code au lieu de `niveau × 20`%

Règles (§8.3) : *"Il y a niveau x 20% de chance que le héros ne meure
pas."*

```java
// Leader.java:453-454
public void mourir(Commandant c) {
    int chance = 1 + getNiveauCompetence(Const.COMPETENCE_LEADER_IMMORTALITE) * 20;
```

Un point de pourcentage systématiquement ajouté en trop par rapport à la
règle (niveau 1 → 21% au lieu de 20%). Sans effet au niveau maximum (5),
où le seuil de 100%+ garantit de toute façon la survie dans les deux cas.

### 4.4 [Écart confirmé] Aucune exception pour le héros/gouverneur de départ à la mort — ils sont clonés comme n'importe quel autre lieutenant

Règles (§9.4/8, "Mort") : *"Toutefois pas de clonage pour le héros et le
gouverneur de départ."*

`Leader.mourir()` ne comporte aucune vérification de ce type — aucun
champ de la classe `Leader` (`competencesDepart`, `vitesseDepart`, etc.
ne concernent que les caractéristiques *initiales* du lieutenant, pas un
indicateur "attribué au départ du commandant") ne permet de distinguer
un lieutenant de départ d'un lieutenant recruté aux enchères :

```java
// Leader.java:460-468
Univers.ajouterEvenement("HEROS_MORT_0002", ...);
mettreEnReserve();
initialiserLeader();
Univers.ajouterLeaderEnVente(this);   // reclonage et remise aux enchères, sans exception
c.supprimerLieutenant(nom);
```

Le héros et le gouverneur de départ d'un commandant sont donc, comme
tout autre lieutenant, réinitialisés et remis aux enchères à leur mort
au lieu de disparaître définitivement.

### 4.5 [Écart confirmé, mineur] Case "Maîtrise du marchandage" du héros Fremen : 0% dans le code, 9% dans les règles

Toutes les autres cases "Marchandage" du tableau des règles (héros des
autres races, gouverneurs de toutes races) sont déjà à 0%, cohérent avec
§8.2 *"Marchandage : pas d'intérêt dans cette version du jeu"*. Seule la
case héros Fremen fait exception dans les règles, à 9% — mais le code
la met aussi à 0% (`Const.CHANCE_TROUVER_COMPETENCE_HEROS[0][4] = 0`),
sans exception pour cette race. Cohérent avec la désactivation générale
du marchandage, mais en écart avec le chiffre publié dans les règles.

### 4.6 Points conformes aux règles (vérifiés, pour mémoire)

- **10 héros et 10 gouverneurs mis aux enchères chaque tour** :
  confirmé, `Leader.produireEncheres()` (`Leader.java:380-383`).
- **Offre doublée pour un commandant sans lieutenant** : confirmé,
  `ReceptionOrdres.enroler_lieutenant` compare
  `ancienneOffre * (1 ou 2)` à `nouvelleOffre * (1 ou 2)` selon que
  chaque commandant possède ou non déjà au moins un lieutenant
  (`ReceptionOrdres.java:514-517`).
- **Entretien d'un lieutenant = 1/10 de sa valeur, réduit de 20% par
  niveau de la compétence "Entretien du lieutenant"** : confirmé,
  `Leader.getEntretien()` (`Leader.java:252-258`).
- **Passage de niveau tous les 1000 points d'expérience** : confirmé,
  `Leader.getNiveau()` (paliers 1000/2000/3000...).
- **Répartition aléatoire 25/25/25/25/0% entre vitesse, attaque,
  défense, moral et marchandage à la montée de caractéristique** :
  confirmé pour toutes les races,
  `Const.CHANCE_AUGMENTER_CARACTERISTIQUE_LEADER` (`Const.java:504-508`).

---

## 5. Relations entre les commandants

Règles auditées : `rules/Mise à jour/7. Relations entre les
commandants.md` (`rules/alliances_et_pactes.md` consulté en complément
pour les formules d'alliance, cohérent avec la version à jour sur ce
point).

Code audité : `Alliance.java`, `Commandant.java`, `Combat.java`,
`ReceptionOrdres.java` (`src/main/java/zIgzAg/jeu/oceane`).

### 5.1 [Écart confirmé] Succession du dirigeant d'alliance : basée sur la "puissance" globale du commandant, pas sur le nombre de planètes

Les règles sont précises sur le critère de remplacement automatique du
dirigeant sortant : *"Si le dirigeant quitte l'alliance, le membre qui
possède **le moins de planètes** prend sa place au tour suivant"*
(anarchique), et *"le membre qui possède **le plus de planètes**"*
(autocratique).

Le code trie les membres par `Stats.trierParPuissance`, un score
composite sans rapport avec le nombre de planètes :

```java
// Alliance.java:217-225
SortedMap<Integer, Commandant> sm = Stats.trierParPuissance(c);
if (a[i].estAutocratique())
    a[i].setDirigeant(Stats.getPremier(sm).getNumero());
if (a[i].estAnarchique())
    a[i].setDirigeant(Stats.getDernier(sm).getNumero());

// Commandant.java:550-554
public int getPuissance() {
    return 10 * getPuissanceFlottes() + getPuissanceSystemes()
            + nombreTechnologiesNonPubliquesConnues() * 100
            + getValeurTotaleLeaders() + (int) centaures;
}
```

`getPuissance()` combine la puissance des flottes (x10), la puissance
des systèmes, le nombre de technologies non-publiques (x100), la valeur
des lieutenants et les centaures en compte — aucun de ces termes n'est
le nombre de planètes possédées. Un commandant avec peu de planètes
mais une grosse flotte ou beaucoup de technologies peut ainsi devenir
dirigeant d'une alliance anarchique à la place d'un commandant qui a
réellement le moins de planètes, et inversement pour l'autocratique.

### 5.2 [Écart confirmé] Réputation "attaquer une planète" : -50 dans le code, -100 dans les règles

Le tableau des règles (§7.5) attribue -100 de réputation pour
*"Attaquer une planète d'un autre commandant"*, sur laquelle se greffent
ensuite les modes pillage (-100 et -nombre d'individus éliminés) et
éradication (-100 et -nombre de la population maximale).

```java
// Const.java:301
public static final int REPUTATION_ATTAQUER_PLANETE = -50;
```

Cette constante unique sert de base aux trois cas dans `Combat.java` :

```java
// Combat.java:534 (attaque simple)
c1.ajouterReputation(Const.REPUTATION_ATTAQUER_PLANETE);
// Combat.java:554-555 (pillage)
c1.ajouterReputation(Const.REPUTATION_ATTAQUER_PLANETE
        - memoirePop - Math.max(0, nbPopDefensive));
// Combat.java:572-573 (éradication)
c1.ajouterReputation(Const.REPUTATION_ATTAQUER_PLANETE
        - p.populationMaximaleTotale());
```

La structure de calcul (base + population perdue/max) est cohérente
avec les règles pour les modes pillage et éradication, mais la valeur de
base utilisée dans les trois cas est deux fois moins pénalisante que ce
qu'annoncent les règles (-50 au lieu de -100).

### 5.3 [Écart confirmé] Réputation "coloniser une planète" : +20 dans le code, +50 dans les règles

```java
// Const.java:300
public static final int REPUTATION_COLONISER_PLANETE = 20;
// Commandant.java:3634
ajouterReputation(Const.REPUTATION_COLONISER_PLANETE);
```

Les règles (§7.5) annoncent +50 pour *"Coloniser une planète"*. Le malus
de -300 pour *"Coloniser une planète qui est déjà colonisée par une
autre race"* est en revanche correctement implémenté
(`ajouterReputation(-300)`, cf. §3.1 de ce document) et cohérent avec la
règle.

### 5.4 [Écart confirmé] Aucune limite de "3 ordres de mission spéciale par tour"

Les règles (§7.3) : *"À chaque tour, vous pouvez donner 3 ordres de
mission."*

`Commandant.effectuerMissionSpeciale` (`Commandant.java:3016`) traite
chaque ordre `services_speciaux` reçu sans compteur ni limite, et
`ReceptionOrdres.services_speciaux` (`ReceptionOrdres.java:547-550`)
appelle cette méthode une fois par ligne d'ordre sans plafond. Recherche
exhaustive d'un mécanisme générique de limitation du nombre d'ordres
d'un même type par tour dans tout `src/main/java` : aucun résultat.

Vérification complémentaire côté PHP (réception/stockage des ordres) :
`php/divers/creer_tables.php3:276` crée la table `services_speciaux`
(`NUMERO, SYSTEME, TYPE, PLANETE`) sans contrainte d'unicité ni de
comptage, `php/ordres/fr/affiche/services_speciaux.txt` ne fait que
formater le texte de confirmation d'un ordre déjà accepté, et aucun des
fichiers référençant `services_speciaux`
(`php/ordres/fr/ordres.txt`, `php/ordres/liste/menu.php3`) ne porte de
logique de plafonnement. Aucune limite n'a été trouvée à aucun des deux
niveaux explorés (traitement Java des ordres, définition/stockage PHP) :
un commandant peut donner plus de 3 ordres de mission spéciale par tour.
Ceci fait écho au constat similaire déjà relevé sur la limite de 999
unités par transfert inter-système (§2.3) : les plafonds "par tour"
annoncés dans les règles ne sont pas appliqués sur ce dépôt.

### 5.5 Points conformes aux règles (vérifiés, pour mémoire)

- **Formules de revenu des trois types d'alliance** : anarchique 100
  centaures/tour/membre (`Const.REVENU_ALLIANCE_ANARCHIQUE = 100F`),
  démocratique `10 × (nombreDeMembres-1)` par membre, autocratique
  `5 × (nombreDeMembres-1)²` au seul dirigeant — les trois formules et
  leurs bénéficiaires respectifs correspondent exactement aux règles
  (`Alliance.java:230-247`).
- **Seules les deux premières alliances rejointes rapportent** :
  confirmé, condition `getPlaceAlliance(...) < 2` appliquée aux trois
  types d'alliance.
- **Restrictions de vote de dirigeant/exclusion selon le type
  d'alliance et le caractère secret** : confirmées côté soumission
  d'ordre (`Commandant.voterElectionDirigeant`,
  `Commandant.voterExclusionCommandant`) — autocratique n'accepte aucun
  vote de dirigeant, anarchique secrète non plus, anarchique n'accepte
  aucun vote d'exclusion, autocratique n'accepte l'exclusion que du
  dirigeant lui-même, démocratique exige la moitié des voix pour les
  deux types de vote.
- **Signature d'un pacte de non-agression conditionnée à un ordre des
  deux commandants le même tour** : confirmé,
  `ReceptionOrdres.signer_pacte` vérifie l'existence d'un ordre
  symétrique de l'autre commandant avant d'enregistrer le pacte
  (`ReceptionOrdres.java:468-477`) ; rupture unilatérale possible à tout
  moment (`Commandant.dechirerPacteDeNonAgression`).
- **Seuils de statut de réputation** (Sanguinaire < -10000, Pirate
  < -5000, Hors-la-loi < -1000, Neutre < 1000, Honnête < 5000, Respecté
  au-delà) : confirmés, `Commandant.getStatutReputationIndex()`.
- **Pas de perte de réputation en attaquant un commandant Sanguinaire ou
  Pirate** : confirmé, la pénalité de réputation dans `Combat.java`
  n'est appliquée que si `c2.getStatutReputationIndex() >= 2`, c'est-à-dire
  si le défenseur n'est ni Sanguinaire (indice 0) ni Pirate (indice 1).
- **Signature de pacte +25 / rupture de pacte -100 de réputation** :
  confirmés (`Const.REPUTATION_SIGNATURE_DE_PACTE = 25`,
  `Const.REPUTATION_RUPTURE_DE_PACTE = -100`).

---

## 6. Flottes et combats

Règles auditées : `rules/Mise à jour/4. Les flottes.md` et
`rules/Mise à jour/5. Combats.md`.

Code audité : `Flotte.java`, `Vaisseau.java`, `PlanDeVaisseau.java`,
`Commandant.java`, `Combat.java`, `Systeme.java`, `Const.java`
(`src/main/java/zIgzAg/jeu/oceane`).

*Ce domaine avait déjà fait l'objet d'investigations ciblées lors de
sessions précédentes (bugs de dommages sur constructions/boucliers,
documentées sur d'autres branches dans `doc/combat-algorithme.md` et
`doc/combat-comportements-non-documentes.md`, absentes de
`build/maven-migration`). L'audit ci-dessous est une nouvelle passe,
indépendante de ces investigations, focalisée sur la conformité aux
règles publiées plutôt que sur la recherche de bugs runtime.*

### 6.1 [Écart confirmé, sévère] Entretien d'une flotte : divisé par 20 (voire 60 en garnison) au lieu de 10 (20 en garnison), plus un forfait fixe non documenté

Les règles (`Mise à jour/4. Les flottes.md`, cohérent avec l'ancien
`rules/constructions.md` §4.1.3.2) : *"Le coût d'entretien d'une flotte
est égal au coût de construction divisé par 10. L'entretien de vos
flottes qui stationnent au-dessus de vos systèmes ne coûte que la
moitié"* — soit valeur/20 pour une flotte en garnison.

Le code applique un diviseur différent à chaque étape :

```java
// Flotte.java:562-572
public float getEntretien(Heros h, boolean carburant) {
    float retour = getValeur() / 20F;      // déjà /20, pas /10
    if (estEnGarnison())
        retour = retour / 3F;              // garnison : /3, pas /2
    if (carburant)
        retour = retour / 2F;              // facteur non documenté
    if (h != null)
        retour = retour - (20 * retour * h.getNiveauCompetence(
                Const.COMPETENCE_LEADER_ENTRETIEN_FLOTTE)) / 100F;
    return retour + Const.BASE_ENTRETIEN_FLOTTE;   // +20 non documenté
}
```

Conséquences chiffrées : une flotte normale (hors garnison) coûte déjà
moitié moins d'entretien que prévu par les règles (valeur/20 au lieu de
valeur/10). Une flotte en garnison coûte valeur/60 au lieu de valeur/20
annoncé — trois fois moins cher que prévu, pas juste "la moitié" du cas
normal. S'y ajoutent deux éléments totalement absents des règles : un
facteur `carburant` qui divise encore par 2 dans certains cas, et un
forfait fixe (`Const.BASE_ENTRETIEN_FLOTTE = 20`) ajouté systématiquement
à chaque flotte, quelle que soit sa valeur.

### 6.2 [Écart confirmé] Fusion de flottes : la directive résultante est choisie par le joueur, pas automatiquement héritée de la flotte de plus petit numéro

Les règles (§4.1) : *"la directive de la flotte sera celle qui a le
plus petit numéro"* — la fusion ne devrait donc pas nécessiter de
préciser de directive, celle-ci étant héritée automatiquement.

L'ordre de fusion (`fusionner_flotte`) prend pourtant une directive
explicite en paramètre, appliquée sans tenir compte de celle des deux
flottes d'origine :

```java
// ReceptionOrdres.java:650-653
public void fusionner_flotte(String[] o) {
    int[] d = Flotte.nombreDonneDirective(tInt(o[2]));
    c[iC].fusionnerFlotte(tInt(o[0]), tInt(o[1]), d[0], d[1]);
}

// Commandant.java:3507-3508
Flotte f3 = f1.fusion(f2);
f3.setDirectiveComplete(directive, directivePrecision);
```

Le choix du numéro de flotte survivant (le plus petit) est en revanche
correctement implémenté (`Commandant.java:3493-3497`, permutation pour
garantir que `numFlotte1` est toujours le plus petit). Seul l'héritage
automatique de la directive n'existe pas : le joueur doit — et peut —
spécifier explicitement la directive voulue à chaque fusion, ce qui
dépasse ce que les règles décrivent (mais reste cohérent avec elles tant
que le joueur choisit de fait la directive de la flotte de plus petit
numéro).

### 6.3 Points conformes aux règles (vérifiés, pour mémoire)

- **Table des niveaux de puissance d'une flotte** (0 "insignifiante" à
  10 "inimaginable") : confirmée exhaustivement par calcul des onze
  seuils. `Vaisseau.retournerNiveauPuissance` utilise les seuils
  `{0,1,2,4,8,20,40,80,200,400,720} × Const.BASE_NIVEAU_PUISSANCE (25)`,
  soit 0, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 18000 — les
  onze tranches obtenues correspondent exactement au tableau des règles.
- **Formule de puissance d'un vaisseau** : `ForceSpatiale +
  ForcePlanetaire / 2` (`PlanDeVaisseau.java:720-722`), conforme à la
  formule donnée en §5.2 des règles de combat (*"Puissance spatiale +
  Puissance planétaire/2"*).
- **Seuil minimal de puissance 50 pour engager un combat planétaire** :
  confirmé, `Const.PUISSANCE_ATTAQUE_PLANETAIRE_MINIMALE = 50`, appliqué
  comme condition bloquante dans `Combat.java:400-407`.
- **Bouclier de type I pare 2 tirs** : confirmé,
  `ListeCaracSpeciales.bouclierI` = capacité 2.
- **Réparation par chantier naval** : capacité de base de "chantierI"
  = 20 points de structure (`ListeCaracSpeciales.chantierI`, conforme à
  *"un chantier naval permet de réparer 20 points de structure"*), coût
  de 0,5 centaure par point réparé (`Const.COUT_REPARATION_VAISSEAU =
  0.5F`), et réparation automatique des bâtiments planétaires de 5
  points par tour (`Const.POINTS_REPARATION_BATIMENT = 5`) — tous
  conformes.
- **Combativité d'un vaisseau** : `5 + niveau de moral de l'équipage +
  moral modifié du héros` (`Vaisseau.calculeCombativite`,
  `Vaisseau.java:231-233`, avec un objet nul `Heros.HEROS_NON_PRESENT`
  en l'absence de héros pour éviter une erreur), décrémentée de 1 à
  chaque fin de tour de combat (`Vaisseau.diminuerCombativite`) —
  conforme à §5.1.

### 6.4 [Écart confirmé] Milice planétaire : mécanique de tir sans rapport avec "10 miliciens = 1 laser de type I"

Les règles (§5.2.4) : *"Chaque groupe de 10 miliciens est armé d'un
laser de type I."* — sous-entendu : le nombre d'armes de milice qui
tirent est `population défensive / 10`, chacune tirant une fois par
tour de combat comme n'importe quelle arme (§5.2.5, *"les armes ne
tirent qu'une seule fois par tour de combat"*).

Le code ne modélise pas une population de lasers individuels. Il
réutilise le mécanisme des batteries de défense (`tirDefensesPlanetaires`,
qui fait tirer chaque construction passée en argument
`Const.NOMBRE_SALVE_BATTERIE` = 50 fois) avec une construction fictive
représentant la milice, un nombre de fois dérivé d'une formule
différente :

```java
// Combat.java:784-797
private static void tirMilicesPlanetaires(int nbPopDefensives,
                                          ArrayList sol, Gouverneur g, Heros h, Commandant defenseur) {
    ConstructionPlanetaire[] c = new ConstructionPlanetaire[1];
    c[0] = new ConstructionPlanetaire("battlaI");
    int nbTirs = 0;
    if (nbPopDefensives > 50)
        nbTirs = 1 + (nbPopDefensives / (2 * Const.NOMBRE_SALVE_BATTERIE));
    if (!sol.isEmpty())
        for (int i = 0; i < nbTirs; i++)
            tirDefensesPlanetaires(c, sol, sol, g, h, false, defenseur);
}
```

Le type d'arme utilisé pour ces tirs de milice est bien de la famille
"laser" (`battlaI` est défini avec le dernier paramètre `"laser"` dans
`ListeTechnologique.java:427`), cohérent avec "laser de type I". En
revanche, la *fréquence* de tir ne correspond à aucune lecture directe
de "1 shot par tranche de 10 population, une fois par tour" : la
construction fictive est invoquée `1 + population/100` fois, et chaque
invocation déclenche elle-même une boucle de 50 tirs
(`Const.NOMBRE_SALVE_BATTERIE`, le même mécanisme que pour une vraie
batterie de défense planétaire). Le volume de tirs résultant (de l'ordre
de grandeur de `population/2` pour une grande population défensive) n'a
pas de lien évident avec le nombre de lasers attendu (`population/10`)
annoncé par les règles.

*Portée du constat* : la lecture du code confirme sans ambiguïté que la
formule et le mécanisme diffèrent de ce que décrivent les règles au
mot près. Quantifier précisément l'écart de dégâts en résultant (la
milice inflige-t-elle plus ou moins de dégâts qu'attendu, et de quel
facteur) demanderait un test simulant un combat planétaire complet
avec des valeurs réalistes — non réalisé ici, conformément au principe
de ne présenter que des résultats de test réellement exécutés. Seul
l'écart de structure/formule est donc affirmé avec certitude.

### 6.5 Points de la passe précédente désormais vérifiés

En complément de la passe initiale (§6.3), les points suivants ont été
vérifiés et sont **conformes** aux règles :

- **Ordre de tir en combat planétaire** (§5.2.3) : confirmé exactement,
  `Combat.java:460-468` enchaîne dans cet ordre batteries de défense
  (`tirDefensesPlanetaires`, ciblant en priorité les forces
  stratosphériques), forces stratosphériques (`tirAirSol` sur `strato`),
  milice (`tirMilicesPlanetaires`), puis vaisseaux attaquant au sol
  (`tirAirSol` sur `sol`).
- **Seuils de fuite selon l'agressivité** (§5.3) : confirmés
  exhaustivement pour les six niveaux, `Combat.fuiteTactique`
  (`Combat.java:1349-1374`) — Fuyard fuit toujours, Prudent si
  puissance adverse > 2×, Standard si > 4×, Combatif si > 8×, Pillage
  s'il n'y a plus de vaisseaux de la taille visée, Rageur ne fuit
  jamais pour cause de puissance (aucune condition ne le fait fuir).
- **Répartition stratosphère/surface selon l'agressivité** (§5.2 combat
  planétaire) : confirmée pour Fuyard/Prudent (tous en stratosphère) et
  Standard/Pillage (vaisseaux avec bombe en stratosphère, sans bombe au
  sol) ; pour Combatif, le code oppose `estChasseur()` (arme de combat
  spatial) à son complément plutôt que de tester littéralement "ne
  possède que des bombes", ce qui ne diverge en pratique que pour un
  vaisseau sans aucune arme (cas marginal, non approfondi).
- **1% de chance d'explosion si le moteur est détruit** (§5.2.5) :
  confirmé, `Const.CHANCE_EXPLOSION_MOTEUR = 1`, déclenché uniquement
  quand un composant "moteur" devient inutilisable
  (`Vaisseau.ajouterDommage`, `Vaisseau.java:322-338`).
- **Batterie de défense = 50 armes du type et du niveau du bâtiment**
  (§5.2.4) : confirmé, `Const.NOMBRE_SALVE_BATTERIE = 50` fait tirer
  chaque construction de défense 50 fois par salve
  (`Combat.tirDefensesPlanetaires`, `Combat.java:760-761`).
- **Pénalités/bonus sur la population défensive (miliciens)** :
  confirmés par recoupement avec les sections 2 et 3 de ce document —
  `nbPopDefensive = population × stabilité / 100` (`Combat.java:420`,
  cohérent avec §2.2), politique Défense +50% plafonné à la population
  totale (`Combat.java:427-429`, cohérent avec §2.3), stock d'Armement
  +50% de miliciens (`Combat.java:430-433`, cohérent avec le tableau de
  §3.2 de `Mise à jour/3. Constructions.md`).

---

## 7. Synthèse : écarts de logique vs écarts de paramétrage

Pour prioriser les corrections, les écarts confirmés ci-dessus sont
reclassés selon leur emplacement : dans un fichier de données/constantes
(`Const.java` et ses tables) où une simple valeur est fautive, ou dans
la logique elle-même (méthode d'un fichier comme `Combat.java`,
`Commandant.java`, `Flotte.java`...) où c'est un comportement entier
qu'il faut recoder.

### 7.1 Écarts de logique/algorithme

Corriger ces écarts demande de modifier du code exécutable (une
condition, une formule, un appel manquant), pas seulement une valeur
dans une table — y compris pour ceux qui, au premier abord, ressemblent
à un simple nombre : ce nombre est écrit en dur dans une méthode plutôt
que centralisé dans une table de données.

| § | Écart | Pourquoi c'est du code |
|---|---|---|
| 1.1 | Seuil de publication 60% au lieu de 75% | Littéral `60` écrit en dur dans `Technologie.java`, pas dans `Const.java` |
| 1.2 | Entretien des technologies jamais appelé | Appel manquant, pas une valeur |
| 1.3 | Remise de 80% non documentée | Bloc logique entier, pas un paramètre |
| 2.1 | Mise au rebut récupère 100% au lieu de 50% | Division `/2` manquante dans `Planete.recyclerMateriel` |
| 2.2 | Prérequis `gestplaI` non documenté | Condition ajoutée dans le code |
| 2.3 | Aucun plafond de 999 unités/transfert | Contrôle absent, pas une valeur à ajuster |
| 3.1 | Colonisation même race → extermination | Comparaison de race manquante dans la condition |
| 3.2 | Colonisateur choisi = premier de la liste, pas aléatoire | Boucle déterministe au lieu d'un tirage |
| 3.3 | Pénalité sans capitale -16% au lieu de -10% | Réutilisation erronée du dernier indice du tableau de distance |
| 3.4 | Malus Alcools désactivé | Code commenté |
| 3.5 | Politique Loisir -20% au lieu de -5% | Littéral `retour - retour/5` écrit en dur dans `Systeme.java` |
| 3.6 | Seuil d'éradication `≤30` au lieu de `<30` | Opérateur de comparaison |
| 4.3 | Immortalité `1 + niveau×20` au lieu de `niveau×20` | `+1` câblé dans la formule de `Leader.mourir()` |
| 4.4 | Pas d'exception clonage héros/gouverneur de départ | Vérification absente |
| 5.1 | Succession de dirigeant par "puissance" au lieu du nb. de planètes | Mauvaise méthode de tri utilisée (`getPuissance` vs nb. planètes) |
| 6.1 (diviseurs) | Entretien flotte `/20`, garnison `/3`, `carburant /2` | Diviseurs câblés dans `Flotte.getEntretien` |
| 6.2 | Directive de fusion non héritée automatiquement | Comportement de fusion, paramètre fourni par le joueur |
| 5.4 | Aucune limite de 3 missions spéciales par tour | Contrôle absent, vérifié côté Java et côté PHP (réception/stockage des ordres) |
| 6.4 | Milice planétaire : formule de tir sans rapport avec "10 miliciens = 1 laser" | Formule et mécanisme entiers dans `Combat.tirMilicesPlanetaires`, pas une valeur de table |

### 7.2 Écarts de paramétrage

Corriger ces écarts se limite en principe à changer une valeur ou
compléter une table dans `Const.java` — sans toucher à la logique qui
les consomme.

| § | Écart | Fichier/table concerné |
|---|---|---|
| 4.1 | Tables de compétences ne couvrent que 6 races sur 7 (Koros absent) | `Const.CHANCE_TROUVER_COMPETENCE_HEROS`/`GOUVERNEUR` |
| 4.2 | Compétence "voyage intragalactique" toujours à poids 0 | Mêmes tables, colonne jamais alimentée |
| 4.5 | Marchandage héros Fremen à 0% au lieu de 9% | Une cellule de `Const.CHANCE_TROUVER_COMPETENCE_HEROS` |
| 5.2 | Réputation "attaquer une planète" -50 au lieu de -100 | `Const.REPUTATION_ATTAQUER_PLANETE` |
| 5.3 | Réputation "coloniser" +20 au lieu de +50 | `Const.REPUTATION_COLONISER_PLANETE` |
| 6.1 (forfait) | +20 centaures ajoutés à toute flotte, non documenté | `Const.BASE_ENTRETIEN_FLOTTE` (mais son usage inconditionnel reste une décision de code) |

*2.4 (coût de conception de plan 5x/10x) n'entre dans aucune des deux
catégories : `Const.MODIFICATEUR_MULTIPLICATEUR_CREATION` est bien un
paramètre, mais ce n'est pas un écart de code — le code est cohérent
avec la version à jour des règles ; seul l'ancien fichier
`rules/constructions.md`, non retouché depuis, est obsolète sur ce
point.*

### 7.3 Bilan

13 des 19 écarts confirmés relèvent de la logique du code et demandent
une correction algorithmique. Les 6 écarts purement paramétriques sont
concentrés sur deux zones de données : les tables de compétences des
lieutenants (§4) et les constantes de réputation (§5).

---

*Toutes les sections initialement prévues (technologies, constructions,
population, lieutenants, relations entre commandants, flottes/combats)
ont été auditées au moins une fois. Les points laissés ouverts lors de
la première passe (limite de missions spéciales du §5.4, et les points
de combat planétaire listés en §6.4 de la version précédente de ce
document) ont été traités et tranchés : cinq d'entre eux se sont révélés
conformes aux règles (§6.5), et un — la mécanique de tir de la milice
planétaire — est un écart confirmé (§6.4).*
