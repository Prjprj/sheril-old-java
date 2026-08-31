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

### 5.4 [Point non vérifiable en l'état, signalé] Aucune limite de "3 ordres de mission spéciale par tour" trouvée côté code Java

Les règles (§7.3) : *"À chaque tour, vous pouvez donner 3 ordres de
mission."*

`Commandant.effectuerMissionSpeciale` (`Commandant.java:3016`) traite
chaque ordre `services_speciaux` reçu sans compteur ni limite, et
`ReceptionOrdres.services_speciaux` (`ReceptionOrdres.java:547-550`)
appelle cette méthode une fois par ligne d'ordre sans plafond. Recherche
exhaustive d'un mécanisme générique de limitation du nombre d'ordres
d'un même type par tour dans tout `src/main/java` : aucun résultat. Ceci
fait écho à un constat similaire déjà relevé sur la limite de 999 unités
par transfert inter-système (§2.3) : les plafonds "par tour"/"par ordre"
annoncés dans les règles ne semblent pas appliqués côté serveur Java sur
ce dépôt. Il n'a pas été possible de confirmer si un tel plafond existe
ailleurs (validation du formulaire d'ordres côté PHP, ou couche non
explorée) — signalé comme point ouvert plutôt que comme écart certain,
conformément au principe de ne pas conclure sans vérification directe.

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

*Prochaines sections à auditer : flottes/combats — en attente de feu
vert.*
