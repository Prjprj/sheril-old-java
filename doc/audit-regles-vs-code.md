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

## 7. Services spéciaux

Règles auditées : `rules/services_speciaux.md` (ancien, détaille les 4
missions et le principe des chances de succès) et §7.3 de
`Mise à jour/7. Relations entre les commandants.md` (déjà lu lors de
l'audit du §5 de ce document, revu ici plus en détail).

Code audité : `Commandant.effectuerMissionSpeciale`
(`Commandant.java:3016-3151`), `Commandant.trouverTechnoAVoler`
(`Commandant.java:2259-2268`).

### 7.1 [Écart confirmé] Aucune vérification que le système ciblé est possédé ou détecté

Les règles (§7.3, cohérentes dans les deux versions) : *"Les services
spéciaux peuvent agir sur une planète qui vous appartient (pour la
propagande) ou sur une planète que vous détectez."* / *"Vos services
spéciaux ne peuvent agir que sur vos propres systèmes, ou bien sur les
systèmes que vous détectez et dont la liste figure sur votre rapport."*

`Commandant.effectuerMissionSpeciale` ne vérifie que l'existence du
système ciblé (`Univers.existenceSysteme(pos)`) et de la planète — à
aucun moment le code ne vérifie que ce système appartient au commandant
ou figure dans sa liste de systèmes détectés
(`getSystemesDetectesArrayList()`, utilisée ailleurs, par exemple par
`Alliance.determinerSystemesDetectees`). Un commandant peut donc, en
l'état du code, cibler n'importe quel système existant de la galaxie
avec une mission spéciale, y compris un système jamais détecté.

### 7.2 [Écart confirmé] Vol de technologie : vole une technologie déjà connue de la cible, pas des points de sa recherche en cours

Les règles sont précises sur la mécanique : *"Vol de technologie : Des
espions détournent des points de recherche d'une technologie que votre
adversaire cherche ce tour-ci"* (Mise à jour) / *"vos espions... tenteront
de vous ramener des points de technologie pris à l'ennemi dans son
domaine de recherche actuel"* (ancien fichier). Dans les deux formulations,
la source du vol est le **domaine de recherche en cours** de la cible
(une technologie qu'elle est en train de chercher), et ce qui est volé
est un **nombre de points de recherche**, pas la technologie entière.

Le code fait tout autre chose :

```java
// Commandant.java:2259-2268
private String trouverTechnoAVoler(Commandant cible, int fric) {
    String[] liste = cible.listeTechnologiesNonPubliquesConnues();
    for (int i = 0; i < liste.length; i++)
        if (!estTechnologieConnue(liste[i])
                && peutChercherTechnologie(liste[i])
                && fric >= (Univers.getTechnologie(liste[i])
                        .getPointsDeRecherche() / 10))
            return liste[i];
    return null;
}
// Commandant.java:3070-3081
String technoVolee = trouverTechnoAVoler(c, reussite);
...
ajouterTechnologieConnue(technoVolee);   // technologie octroyée intégralement
```

`listeTechnologiesNonPubliquesConnues()` retourne les technologies déjà
**maîtrisées** par la cible (pas son domaine de recherche en cours), et
`ajouterTechnologieConnue` l'accorde **intégralement et immédiatement**
au voleur — aucun point de recherche n'est transféré vers une recherche
en cours de l'attaquant. Deux écarts cumulés par rapport à la règle :
la source (technologies connues au lieu de recherche active) et l'effet
(technologie complète octroyée au lieu de points de recherche transférés).

### 7.3 Points conformes aux règles (vérifiés, pour mémoire)

- **Sabotage détruit tous les bâtiments de la planète ciblée** :
  confirmé, `sys.detruireToutBatimentDePlanete(nPlanete)`
  (`Commandant.java:3092`), cohérent avec *"Un sabotage réussi détruit
  tous les batiments de la planète ciblée."*
- **Chances de succès basées sur le budget total de services spéciaux
  de l'attaquant, le budget total de contre-espionnage de la cible et
  le budget de contre-espionnage local du système visé** : les trois
  facteurs annoncés par les règles sont bien présents — `attaque` =
  budget total de l'attaquant, et `defense = sys.getBudgetContreEspionnage(numero)`
  agrège en réalité *à la fois* le budget de contre-espionnage total du
  commandant défenseur *et* sa part de budget allouée localement à ce
  système (`Systeme.java:977-989`), les deux facteurs sont donc bien
  pris en compte même s'ils sont fusionnés dans une seule variable.
- **Propagande sur ses propres planètes fait remonter la stabilité au
  lieu de la baisser** : confirmé,
  `if (c == this) { setStabilite(stab + Math.min(20, reussite)); }`
  (`Commandant.java:3106-3108`).

---

## 8. Produits commerciaux et dons entre commandants

Règles auditées : `rules/produits_commerciaux_et_dons.md`.

Code audité : `Possession.java`, `Commandant.java` (achat/vente de
marchandises, vente de flotte, transfert de technologie).

### 8.1 [Écart confirmé, sévère — bug de troncature entière] Le prix des marchandises ne converge jamais vers le prix moyen, sauf à taxation 0%

Les règles (§6.1.4) donnent une formule précise de convergence du prix
en fin de tour :

> nouveau prix = ancien prix − ( [100 − taux de taxation] / 100 ) ×
> [ (ancien prix − prix moyen) / (2 + quantité / 10) ]

Le code reproduit cette formule au signe et aux termes près — mais
entièrement en arithmétique entière :

```java
// Possession.java:294-303 — évolution du prix en fin de tour
for (int i = 0; i < Const.NB_MARCHANDISES; i++)
    if (contientMarchandise(i)) {
        int ancienPrix = getPrixMarchandise(i);
        setPrixMarchandise(
                i,
                ancienPrix
                        - ((100 - taux) / 100)
                        * ((ancienPrix - Univers.getPrixMoyenMarchandise(i))
                                / (2 + getQuantiteMarchandise(i) / 10)));
    }
```

`taux` est un `int` (`Possession.evolutionPosteCo(int numero, int taux,
Systeme s)`), et `100` est un littéral entier : `(100 - taux) / 100` est
donc une division entière qui vaut **0 pour tout taux compris entre 1 et
99**, et ne vaut 1 que pour `taux == 0`. Le terme correctif de la
formule — pourtant le cœur du mécanisme — est donc multiplié par zéro
et disparaît intégralement pour tout commandant dont le taux de
taxation des postes n'est pas exactement nul. Comme le taux par défaut
des nouveaux commandants est justement de 50% (`Commandant.java:1488`,
conforme à §6.1.2), la convergence des prix vers la moyenne de l'univers
ne fonctionne en pratique jamais pour l'immense majorité des postes
commerciaux du jeu.

### 8.2 [Écart confirmé] Aucune limite d'une technologie cédée par tour

Les règles (§6.2) : *"Vous ne pouvez pas céder plus d'une technologie
par tour."*

`Commandant.transfertTechnologie` (`Commandant.java:2491-2530`) ne
comporte aucun compteur ni verrou empêchant d'appeler cette méthode
plusieurs fois dans le même tour (vers la même cible ou des cibles
différentes) — troisième occurrence du même constat que les plafonds
"par tour" déjà relevés et absents ailleurs dans le code (999 unités
par transfert inter-système en §2.3, 3 missions spéciales par tour en
§5.4).

### 8.3 Points conformes aux règles (vérifiés, pour mémoire)

- **Prix initial de 5 centaures, plancher 0,1, plafond 10** : confirmé
  — `Const.PRIX_MARCHANDISE_PAR_DEFAUT = 50` (représentation ×10) et
  `setPrixMarchandise` borne la valeur stockée entre 1 et 100
  (`Possession.java:189-191`).
- **Variation de ±0,1 centaure par unité entrant/sortant du poste** :
  confirmée, `ajouterMarchandise`/`supprimerMarchandise` ajustent le
  prix stocké (×10) de exactement `quantite` à chaque mouvement de
  stock (`Possession.java:210-227`).
- **Taux de taxation par défaut de 50% pour un nouveau commandant, et
  plafond de 100%** : confirmés, `tauxTaxationPoste = 50` à
  l'initialisation (`Commandant.java:1488`), et
  `Commandant.fixerTauxPostes` refuse toute valeur supérieure à 100
  (`Commandant.java:4057-4060`).
- **Majoration/minoration du prix effectif selon le taux de taxation à
  l'achat/la vente** : confirmée, le prix est majoré du taux (achat,
  `Commandant.java:2126-2127`) ou minoré du taux (vente,
  `Commandant.java:2200-2201`) — ici en arithmétique flottante, sans le
  bug de troncature relevé en §8.1.
- **Vente de flotte** : moitié du prix payée par l'acheteur
  (`cout = f.getValeur() / 2`), transaction annulée si fonds
  insuffisants, événement rendu public
  (`Commandant.venteFlotte`, `Commandant.java:2594-2631`) — conforme à
  §6.2. Le renouvellement des équipages en novices n'a pas été vérifié
  en détail (méthode `Flotte.initialiserEquipages`, non explorée).

---

## 9. Combat spatial (résolution détaillée)

Règles auditées : `rules/Mise à jour/5. Combats.md` §5.2 (combat
spatial : cibles, tempo, mouvement, tir).

Code audité : `Combat.java`, `Vaisseau.java`, `Const.java`.

### 9.1 [Écart confirmé, très sévère] La boucle de tirs multiples est désactivée — aucun vaisseau ne tire jamais sur plus d'une cible par tour

Les règles (§5.2.5-5.2.6) sont explicites : *"Une ou plusieurs cibles
sont désignées. Le nombre de cibles maximum d'un vaisseau est égal à sa
taille."* / *"Une frégate standard (taille 4) peut tirer sur quatre
vaisseaux différents par tour."*

Le code contient bien une boucle prévue pour gérer les tirs
supplémentaires au-delà du premier — mais sa borne a été remplacée par
un `0` en dur, la valeur réelle étant laissée en commentaire :

```java
// Combat.java:955-962
combat(f1, hp1, hc1, ht1, h1, f2, hp2, hc2, ht2, h2, 0);

//Const.NB_CIBLES[tailleMax]
for (int i = 1; i <= 0 /* Const.NB_CIBLES[tailleMax] */; i++) {
    determinationCible(m1, s1, hc1, hp1, hp2, f2, i);
    determinationCible(m2, s2, hc2, hp2, hp1, f1, i);
    combat(f1, hp1, hc1, ht1, h1, f2, hp2, hc2, ht2, h2, i);
}
```

`for (int i = 1; i <= 0; i++)` ne s'exécute jamais (`1 <= 0` est faux
dès la première évaluation) : seul le premier tir (`combat(...,
0)`, ligne 955, hors boucle) a lieu. Quelle que soit sa taille, un
vaisseau ne peut donc actuellement tirer que sur **une seule cible par
tour de combat**, jamais plusieurs — la mécanique multi-cibles décrite
par les règles et illustrée par l'exemple de la frégate est entièrement
non fonctionnelle.

### 9.2 [Écart confirmé, actuellement sans effet observable à cause de §9.1] La table du nombre de cibles maximum ne correspond pas à "cibles = taille"

Le code destiné à alimenter la boucle désactivée ci-dessus (`Const.NB_CIBLES[tailleMax]`)
contient une table sans rapport avec la règle *"le nombre de cibles
maximum d'un vaisseau est égal à sa taille"* :

```java
// Const.java:629
public static final int[] NB_CIBLES = {0, 3, 7, 15, 31, 63, 127, 255, 511, 2000};
// Vaisseau.java:959-981 — indexée directement par la taille du vaisseau
if (plan.getTaille() == 1) return Const.NB_CIBLES[1];   // = 3, la règle attend 1
if (plan.getTaille() == 4) return Const.NB_CIBLES[4];   // = 31, la règle attend 4
```

Les valeurs suivent une progression `2^n − 1` sans rapport avec une
correspondance directe taille→nombre de cibles. Ceci ne produit
aujourd'hui aucun effet en jeu puisque `getNbCibleMax()` n'est appelée
nulle part dans la boucle de tir réellement exécutée (§9.1) — mais si le
bug de la boucle venait à être corrigé sans revoir cette table, le
comportement obtenu resterait très éloigné de la règle (un intercepteur
de taille 1 pourrait par exemple viser jusqu'à 3 cibles au lieu d'une
seule).

### 9.3 Points conformes aux règles (vérifiés, pour mémoire)

- **Ordre des mouvements du tempo le plus petit au plus grand** :
  confirmé, `Combat.mouvement` fusionne les deux flottes triées par
  tempo croissant (les `TreeMap` de `determinationTempo` sont
  naturellement ordonnées, `Combat.java:1384-1411`).
- **La cible définitive est la plus proche dans la liste des cibles
  possibles** : confirmé, `Position3D.positionLaPlusProche(...)` est
  utilisée pour trancher parmi les cibles possibles
  (`Combat.determinationCible`, `Combat.java:1304-1305`).
- **Sans taille de cible prioritaire définie, priorité aux vaisseaux
  les plus grands** : confirmé, `choixPossibleCible` parcourt les
  tailles de `Const.TAILLE_MAXIMAL_VAISSEAU - 1` (la plus grande) vers
  0 quand aucune stratégie ne précise de taille cible
  (`Combat.java:1311-1319`).

---

## 11. Introduction, situation de départ et avantages de race

Règles auditées : `rules/Mise à jour/0.1 Introduction et situation de
départ.md` et `rules/Mise à jour/0.2 Avantage de race du commandant.md`.

Code audité : `Joueur.creerCommandant` (`Joueur.java:328-473`),
`Flotte.choixFlotteDeDepart` (`Flotte.java:257-306`),
`Const.RACE_TECHNOLOGIES`.

### 11.1 [Écart confirmé] Budget de départ : 20000 centaures au lieu de 21000

Les règles (§0.1) : *"21000 centaures (monnaie du jeu)"*.

```java
// Joueur.java:341 (valeur initiale, écrasée plus bas)
c.setCentaures(20000F);
...
// Joueur.java:455 (valeur finale effectivement conservée)
c.setCentaures(20000 + Univers.getTour() * 1000);
```

Pour un commandant créé au tour 0 (début de partie), le montant final
est de 20000 centaures, pas 21000. La progression `+1000 par tour`
pour les commandants arrivant plus tard dans la partie n'est pas
documentée par les règles mais est cohérente avec la nécessité de
rattraper le retard de revenus accumulés par les commandants déjà en
jeu — seule la valeur de référence au tour 0 diverge de la règle.

### 11.2 [Écart confirmé] Technologies de départ par race : sans rapport avec le tableau des règles

Les règles (§0.2) donnent une technologie de départ précise par race :
Fremens → Station de produits alimentaires I, Atalantes → Station de
métaux précieux I, Zwaias → Station de composants électroniques,
Yoksors → Radar III, Fergoks → Station armement et explosifs I +
Maîtrise militaire II.

```java
// Const.java:576-583
public static final String[][] RACE_TECHNOLOGIES = {
        {"scanI", "metauxII"},   // Fremen  — la règle attend une station alimentaire
        {"plasmaI", "raffineII"},// Atalante — la règle attend une station de métaux précieux
        {"bombeI","armeII"},     // Zwaia   — la règle attend une station de composants électroniques
        {"missI", "infoII"},     // Yoksor  — la règle attend un radar de type III
        {"laserI", "technoII"},  // Fergok  — la règle attend une station d'armement + maîtrise militaire II
        {}
};
```

Aucune des cinq races ne reçoit la ou les technologies annoncées par le
tableau des règles — le code attribue systématiquement une arme
(scanner, plasma, bombe, missile, laser) accompagnée d'une technologie
de deuxième niveau totalement différente de celle documentée. Vu
l'ampleur et la cohérence du décalage (les cinq lignes divergent), il
s'agit probablement d'un rééquilibrage du jeu postérieur à la rédaction
de ce tableau plutôt que d'un bug isolé — mais le tableau des règles à
jour n'a pas été mis à jour en conséquence.

### 11.3 [Écart confirmé] Aucun colonisateur dans la flotte de départ

Les règles (§0.1) : *"La flotte contient des vaisseaux armés, des
colonisateurs et des éclaireurs."* Et (§2.1.2, déjà citée en §3 de ce
document) : *"Au début du jeu, vous disposez de 10 colonisateurs de
votre race."*

```java
// Flotte.java:260-264
quotas.put("Intercepteur standard", 10 + modifier * 2);
quotas.put("Chasseur standard", 20 + modifier * 2);
quotas.put("Fregate standard", 20 + modifier * 2);
quotas.put("Eclaireur standard", 3 + modifier);
quotas.put("Grand Bombardier standard", 20 + modifier * 3);
```

La flotte de départ contient bien des vaisseaux armés (Intercepteur,
Chasseur, Frégate, Grand Bombardier) et un éclaireur — mais aucune
entrée "colonisateur". Recherche du terme "colonisateur" dans
`Joueur.java` et `Flotte.java` : aucune occurrence en dehors des
méthodes de détection d'un colonisateur déjà présent dans une flotte
(`contientColonisateur`, `trouverColonisateur`). Un nouveau commandant
démarre donc avec 0 colonisateur au lieu des 10 annoncés.

### 11.4 [Écart confirmé] Le coût de terraformation augmente bien avec le niveau, contrairement à ce que la note des règles observe

Les règles (§2.1.1) contiennent une note empirique : *"Terraformer une
planète coûte 52 à 54 centaures... **Mais ça ne semble pas être le cas
[qu'il augmente avec le niveau] dans cette version du jeu.**"*

```java
// Planete.java:271-273
public float coutTerraformation() {
    return Const.COUT_BASE_TERRAFORMATION + (terraformation + 1)
            * Const.COUT_PALIER_TERRAFORMATION;
}
// Const.java:184-185
COUT_BASE_TERRAFORMATION = 50F; COUT_PALIER_TERRAFORMATION = 2F;
```

Le coût est `50 + (niveau_actuel + 1) × 2`, donc 52 au niveau 0, 54 au
niveau 1, 56 au niveau 2, etc. — il augmente bien de 2 centaures par
niveau de terraformation déjà atteint, indéfiniment. La fourchette
"52 à 54" de la note des règles correspond seulement aux deux premiers
paliers ; l'observation selon laquelle le prix n'augmenterait pas est
donc incorrecte au-delà du niveau 1, probablement une conclusion tirée
d'un nombre limité d'essais en jeu.

### 11.5 Points conformes aux règles (vérifiés, pour mémoire)

- **2 systèmes, 30 planètes au total, 2 lieutenants (1 héros + 1
  gouverneur)** : confirmés,
  `Systeme.creerAuHasard(pos2, 30 - s.getNombrePlanetes())`
  (`Joueur.java:421`) et création d'un héros et d'un gouverneur de
  départ (`Joueur.java:459-465`).
- **Effet de la terraformation sur les seuils de tolérance climatique**
  (±2 par niveau sur radiation/température, pas sur la gravité) :
  confirmé structurellement,
  `radiation < (-2*terraformation + HABITAT_RADIATION[race][0])`
  (`Planete.java:842-843`) — voir cependant §12.2 sur les valeurs de
  base de ces tables de tolérance.

---

## 12. La galaxie, les systèmes et les planètes

Règles auditées : `rules/Mise à jour/1. La galaxie, les systèmes et les
planètes.md` (première moitié du fichier ; sa seconde moitié duplique
le chapitre Population déjà audité en §3).

Code audité : `Const.java` (constantes de génération de galaxie),
`Systeme.creerAuHasard` (`Systeme.java:155-190`), `Messages.ETOILES`,
`Planete.calculeMaxPopDeBase` (`Planete.java:840-...`).

### 12.1 [Écart confirmé] Structure de la galaxie : 16 secteurs de 17 systèmes, pas 4 secteurs de 40 systèmes

Les règles (§1.1) : *"La galaxie est découpée en 4 secteurs. Il y a 40
systèmes par secteur."* (soit 160 systèmes au total). La grille 40×40
à bords contigus est en revanche bien confirmée séparément (voir
ci-dessous).

```java
// Const.java:94-100
public static final int NB_SECTEURS_X = 4;           // secteurs par ligne ET par colonne
public static final int BORNE_SECTEUR_X = 10;
public static final int NB_SYSTEMES_PAR_SECTEUR = 17; // et non 40
public static final int BORNE_MAX = NB_SECTEURS_X * BORNE_SECTEUR_X;      // = 40, conforme
public static final int NB_SECTEURS = NB_SECTEURS_X * NB_SECTEURS_X;      // = 16, pas 4
public static final int NB_SYSTEME = NB_SECTEURS * NB_SYSTEMES_PAR_SECTEUR; // = 272
```

`NB_SECTEURS_X = 4` désigne en réalité le nombre de secteurs par ligne
*et* par colonne (grille 4×4 de secteurs), soit 16 secteurs au total —
pas 4. Combiné aux 17 systèmes par secteur (et non 40), la galaxie
compte au total 272 systèmes, très différent des 160 (4×40) qu'impliquent
les règles. Seule la borne de coordonnées (40×40) correspond au texte.

### 12.2 [Écart confirmé] Tables de tolérance climatique par race : valeurs sans rapport avec le tableau des règles

Les règles (§2.1, table déjà citée en §3 de ce document) donnent des
bornes précises de radiation et de température par race. Les tables de
tolérance du code divergent sur la quasi-totalité des races :

```java
// Const.java:542-546 — ordre Fremens, Atalantes, Zwaias, Yoksor, Fergok, Cyborg
HABITAT_RADIATION   = {{0, 200}, {10, 200}, {20, 150}, {20, 180}, {0, 180}, {0, 180}};
HABITAT_TEMPERATURE = {{-110, 140}, {-80, 200}, {-150, 150}, {-150, 180}, {-150, 180}, {-100, 100}};
```

Exemple pour les Fremens : radiation min/max règle = 40/200, code =
0/200 ; température min/max règle = 0/200, code = -110/140. Les deux
bornes divergent pour la quasi-totalité des races et des deux
caractéristiques. La table de gravité (`HABITAT_GRAVITE`, valeurs
0-100 dans le code contre 0,0-10,0 "g" dans les règles) n'a pas pu être
comparée avec certitude faute d'avoir confirmé la convention d'unité
utilisée en interne (échelle ×10 supposée mais non vérifiée par un
calcul de population réel) — non tranché, à vérifier par un test si
cette piste est utile.

### 12.3 [Écart confirmé, mineur] 10 types d'étoiles dans le code contre 6 documentés

Les règles (§1.2.1) : *"On distingue 6 étoiles différentes : Etoile
bleue, Nova, Etoile blanche, Naine orange, Naine bleue, Naine rouge."*

```java
// Messages.java:47-49
public static final String[] ETOILES = { "Heron", "Kyo", "flamboyant",
        "arcturus", "étoile bleue", "nova", "étoile blanche",
        "naine orange", "naine bleue", "naine rouge" };
```

Les 6 types documentés sont bien présents, mais précédés de 4 types
supplémentaires non documentés ("Heron", "Kyo", "flamboyant",
"arcturus"), portant le total à 10.

### 12.4 Points conformes aux règles (vérifiés, pour mémoire)

- **Grille 40×40 à bords contigus, coordonnées de 1 à 40** : confirmé,
  `Const.BORNE_MAX = 40` et usage modulo dans les calculs de position
  (par exemple `Joueur.java:405-406`, `(choix.getY()+dis[i][0]) %
  (Const.BORNE_MAX + 1)`).
- **10 à 20 planètes par système** : confirmé,
  `Systeme.creerAuHasard` génère `10 + hasard(10) + bonus_étoile`, puis
  plafonne explicitement à 20 (`Systeme.java:163-176`) — le champ
  `Const.NB_PLANETES_PAR_SYSTEMES = 29` n'est qu'une borne de
  dimensionnement de tableau interne, jamais atteinte en pratique.
- **Les meilleures étoiles génèrent en moyenne plus de planètes** :
  confirmé structurellement, le bonus de planètes appliqué dépend de
  `(NB_ETOILES - 1 - typeEtoile)`, favorisant les types d'étoile
  d'indice le plus bas (`Systeme.java:169`).
- **Bonus liés à la population majoritaire du système (tableau
  §1.2.2)** : les règles précisent elles-mêmes que ce mécanisme
  *"n'est pas le cas à ce jour"* — recherche dans le code d'un
  quelconque modificateur basé sur la race majoritaire d'un système
  (hors équipage des vaisseaux construits, bien confirmé conforme) :
  aucun résultat, cohérent avec l'aveu des règles elles-mêmes. Pas
  compté comme écart puisque déjà annoncé comme non implémenté par la
  documentation.

---

## 13. Couche PHP (interface web)

Jusqu'ici, l'audit a porté presque exclusivement sur
`src/main/java/zIgzAg/jeu/oceane` — la couche PHP (`php/`, une
cinquantaine de fichiers : formulaires d'ordres, rapports, forum,
pages de présentation des races) n'avait été consultée que
ponctuellement (aide en jeu, recherche de plafonds absents). Cette
section couvre une passe dédiée à cette couche : le PHP de ce dépôt est
essentiellement une interface web (soumission d'ordres, affichage de
rapports) au-dessus du moteur de jeu Java, pas une réimplémentation des
règles — les constats ci-dessous portent donc sur des fonctionnalités
web spécifiques plutôt que sur des recalculs de formules déjà audités
côté Java.

### 13.1 [Comportement non documenté] "Marché Galactique" : une bourse d'offres de vente publique, mécanisme distinct de celui décrit par les règles

Les règles du commerce (§6.1.1 de `produits_commerciaux_et_dons.md`,
déjà auditées en §8 de ce document) décrivent un commerce **fondé sur
la flotte** : pour acheter/vendre dans un poste commercial étranger, il
faut qu'une flotte stationne au-dessus du système ciblé en fin de tour.

`php/ordres/marche_galactique.php` implémente un mécanisme différent :
un commandant poste une offre de vente (système, marchandise, quantité,
prix) dans une table globale (`vendre_galactique`), visible/achetable
en principe par n'importe quel autre commandant sans qu'aucune flotte
ne soit nécessaire — une bourse d'annonces plutôt qu'un commerce de
proximité. Ce mécanisme n'est mentionné nulle part dans les règles
consultées. Le volet achat de cette même page est entièrement
commenté (lignes 83-103, `<!--...-->`) : seul le dépôt d'offres de
vente est aujourd'hui fonctionnel, l'achat effectif via cette bourse
ne l'est pas.

### 13.2 [Nuance sur l'écart §8.2] Le don de plus d'une technologie par tour n'est bloqué que sur un des deux chemins d'ordre possibles

L'écart §8.2 constate qu'aucune limite d'une technologie cédée par tour
n'existe côté Java. La couche PHP révèle deux chemins distincts pour
soumettre un don de technologie, avec un comportement différent :

- **Formulaire classique** (`php/ordres/data/transferer_technologie.txt`,
  même mécanisme générique que tous les autres types d'ordres, par
  exemple `services_speciaux` déjà vu en §7) : rien n'empêche de le
  soumettre plusieurs fois dans le même tour. La table SQL qui stocke
  ces ordres ne comporte aucune contrainte d'unicité :
  `CREATE TABLE transferer_technologie(NUMERO int, BENEFICIAIRE int,
  TECHNOLOGIE varchar(20), MODE int)` (`php/divers/creer_tables.php3:227`)
  — un même `NUMERO` (commandant) peut y avoir autant de lignes que
  voulu.
- **Page matricielle** `php/ordres/technology_plan.php` (interface plus
  récente permettant de choisir, pour un groupe de commandants en
  partage de technologies, qui donne quoi à qui via une grille) :
  son traitement (lignes 65-91) supprime tous les ordres existants du
  groupe puis, pour chaque donateur, ne conserve que la **première**
  case remplie rencontrée dans le POST (`break` après la première
  correspondance, ligne 84) — un donateur qui aurait rempli plusieurs
  cases de sa ligne ne voit que la première effectivement enregistrée.
  Ceci limite de fait à un don par donateur *à chaque soumission de
  cette page précise*, mais rien n'indique que cette limitation soit
  une décision délibérée d'implémenter la règle plutôt qu'un effet de
  bord du `break` — et un joueur utilisant le formulaire classique en
  plus de cette page n'est pas concerné par la limite.

L'écart §8.2 reste donc valable dans l'absolu (le formulaire classique,
qui utilise le même mécanisme générique que tous les autres ordres,
n'impose aucune limite), mais son exploitabilité réelle dépend du
chemin d'ordre utilisé par le joueur.

### 13.3 [Corroboration renforcée de l'écart §11.2] Les pages de présentation des races confirment les technologies codées en Java, pas celles du tableau des règles

Les pages `php/races/{fremen,atalante,zwaia,yoksor,fergok}.php`
décrivent, pour chaque race, une "Station de [ressource] de type II"
constructible sur les systèmes de départ. Ces descriptions correspondent
à la **deuxième** technologie de chaque entrée de
`Const.RACE_TECHNOLOGIES` (§11.2), pas au tableau des règles §0.2 :

| Race | Règles §0.2 | Java (2ᵉ techno) | PHP (`php/races/*.php`) |
|---|---|---|---|
| Fremens | Station produits alimentaires I | `metauxII` | "Stations d'enrichissement des métaux de type II" |
| Atalantes | Station métaux précieux I | `raffineII` | "Stations énergétiques de type II" |
| Zwaias | Station composants électroniques | `armeII` | "Stations d'armements et explosifs de type II" |
| Yoksors | Radar de type III | `infoII` | "Stations de logiciels de type II" |
| Fergoks | Station armement + maîtrise militaire II | `technoII` | "Stations de composants électroniques de type II" |

Java et PHP se corroborent mutuellement (aux noms de code près) sur les
cinq races, contre le tableau des règles §0.2 qui décrit un jeu
différent. Ceci confirme que §11.2 est bien un décalage de la
documentation par rapport à un rééquilibrage réel du jeu, plutôt qu'un
bug de code isolé — le code (Java et PHP) est cohérent avec lui-même,
seule la documentation des règles n'a pas suivi.

En revanche, les noms des plans de vaisseau de départ (colonne "Plan(s)
de vaisseau(x)" du tableau §0.2) sont bien confirmés exacts : la page
`php/races/fremen.php` mentionne le vaisseau "Sidjin" et
`php/races/atalante.php` le vaisseau "Gardien", conformes au tableau
des règles.

### 13.4 [Point ouvert, non tranché] Système de "prêt pour le tour suivant" non documenté

Les règles (§0.1) : *"une fois par semaine, le jeu passe au tour
suivant"* — un rythme fixe, sans mention de résolution anticipée.

`php/ordres/fr/readyness.php` implémente un mécanisme où chaque
commandant peut se déclarer "prêt" pour le tour en cours
(table `_player_ready`), avec un compteur affichant le nombre de
commandants prêts sur le nombre total. Ce mécanisme n'est mentionné
nulle part dans les règles consultées. Ce fichier n'affiche et ne
modifie que l'état "prêt" : il ne contient aucune logique de
déclenchement de la résolution du tour, qui se trouverait ailleurs
(planificateur externe, cron, ou logique Java non recherchée dans cette
passe). Il n'a donc pas été possible de déterminer si cet indicateur
purement informatif ("combien de joueurs ont fini de jouer") ou s'il
peut réellement avancer le tour plus tôt que le rythme hebdomadaire
annoncé — signalé comme point ouvert, pas comme écart confirmé.

---

## 14. Synthèse : écarts de logique vs écarts de paramétrage

Pour prioriser les corrections, les écarts confirmés ci-dessus sont
reclassés selon leur emplacement : dans un fichier de données/constantes
(`Const.java` et ses tables) où une simple valeur est fautive, ou dans
la logique elle-même (méthode d'un fichier comme `Combat.java`,
`Commandant.java`, `Flotte.java`...) où c'est un comportement entier
qu'il faut recoder.

### 14.1 Écarts de logique/algorithme

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
| 5.4 | Aucune limite de 3 missions spéciales par tour | Contrôle absent, vérifié côté Java et côté PHP (réception/stockage des ordres) |
| 6.1 (diviseurs) | Entretien flotte `/20`, garnison `/3`, `carburant /2` | Diviseurs câblés dans `Flotte.getEntretien` |
| 6.2 | Directive de fusion non héritée automatiquement | Comportement de fusion, paramètre fourni par le joueur |
| 6.4 | Milice planétaire : formule de tir sans rapport avec "10 miliciens = 1 laser" | Formule et mécanisme entiers dans `Combat.tirMilicesPlanetaires`, pas une valeur de table |
| 7.1 | Aucune vérification que le système ciblé par une mission spéciale est possédé/détecté | Contrôle absent dans `Commandant.effectuerMissionSpeciale` |
| 7.2 | Vol de technologie vole une techno déjà connue et l'octroie intégralement | Méthode entière (`trouverTechnoAVoler`) sur la mauvaise source de données |
| 8.1 | Convergence du prix des marchandises inopérante sauf taxation 0% | Division entière `(100-taux)/100` dans `Possession.evolutionPosteCo` |
| 8.2 | Aucune limite d'une technologie cédée par tour | Contrôle absent dans `Commandant.transfertTechnologie` |
| 9.1 | Boucle de tirs multiples désactivée (`i <= 0`) | Borne de boucle câblée en dur dans `Combat.java`, valeur réelle commentée |
| 11.1 | Budget de départ 20000 au lieu de 21000 centaures | Littéraux `20000F`/`20000 + tour*1000` dans `Joueur.creerCommandant` |
| 11.3 | Aucun colonisateur dans la flotte de départ | Quotas de flotte de départ, colonisateur absent de la liste dans `Flotte.choixFlotteDeDepart` |
| 11.4 | Le coût de terraformation augmente bien avec le niveau | Formule `50 + (niveau+1)*2` dans `Planete.coutTerraformation` |

### 14.2 Écarts de paramétrage

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
| 9.2 | Table du nombre de cibles maximum sans rapport avec "cibles = taille" | `Const.NB_CIBLES` (actuellement sans effet observable, masquée par le bug §9.1) |
| 11.2 | Technologies de départ par race sans rapport avec le tableau des règles | `Const.RACE_TECHNOLOGIES` |
| 12.1 | 16 secteurs de 17 systèmes au lieu de 4 secteurs de 40 | `Const.NB_SECTEURS_X`, `Const.NB_SYSTEMES_PAR_SECTEUR` |
| 12.2 | Tables de tolérance climatique par race sans rapport avec le tableau des règles | `Const.HABITAT_RADIATION`, `Const.HABITAT_TEMPERATURE` |
| 12.3 | 10 types d'étoiles au lieu de 6 documentés | `Messages.ETOILES` |

*2.4 (coût de conception de plan 5x/10x) n'entre dans aucune des deux
catégories : `Const.MODIFICATEUR_MULTIPLICATEUR_CREATION` est bien un
paramètre, mais ce n'est pas un écart de code — le code est cohérent
avec la version à jour des règles ; seul l'ancien fichier
`rules/constructions.md`, non retouché depuis, est obsolète sur ce
point.*

### 14.3 Bilan

33 écarts confirmés au total. 27 relèvent au moins en partie de la
logique du code et demandent une correction algorithmique ; 11
comportent une composante purement paramétrique (une valeur ou une
table dans `Const.java`/`Messages.java` suffirait à les corriger). Le
seul écart à cheval sur les deux catégories est 6.1 (entretien de
flotte) : ses diviseurs sont câblés dans la logique, mais son forfait
fixe non documenté vient d'une constante. Les écarts purement
paramétriques sont concentrés sur cinq zones de données : les tables de
compétences des lieutenants (§4), les constantes de réputation (§5), la
table du nombre
de cibles maximum au combat spatial (§9.2, actuellement inerte), les
technologies de départ par race (§11.2), et les tables de génération de
galaxie/tolérance climatique (§12.1-12.3).

Quatre écarts se distinguent par leur sévérité et méritent une priorité
de correction : la boucle de tirs multiples désactivée en combat
spatial (§9.1, un seul tir par vaisseau et par tour au lieu de plusieurs
selon la taille), la convergence des prix commerciaux inopérante pour
toute taxation non nulle (§8.1), l'entretien de flotte deux à trois
fois moins cher que prévu (§6.1), et le vol de technologie qui octroie
une technologie entière au lieu de transférer des points de recherche
(§7.2) — les quatre sont des bugs de code purs (division/boucle/formule/
mauvaise source de données), pas des erreurs de configuration.

---

*Tous les domaines de règles du dépôt (`rules/` et `rules/Mise à
jour/`) ont désormais été audités au moins une fois : technologies,
constructions, population, lieutenants, relations entre commandants,
flottes/combats, services spéciaux, produits commerciaux et dons,
résolution détaillée du combat spatial, introduction/situation de
départ/avantages de race, et galaxie/systèmes/planètes. Les fichiers
`rules/qui_etes_vous.md` et `rules/situation_debut_jeu.md` n'ont pas
été relus spécifiquement : leur contenu recoupe largement ce qui a déjà
été vérifié via les fichiers `Mise à jour` correspondants.*

*Une passe dédiée à la couche PHP (`php/`, §13) a également été menée,
en complément de l'audit Java qui reste le corps principal de ce
document : le PHP de ce dépôt est essentiellement une interface web
(formulaires d'ordres, rapports, forum, pages de présentation des
races) au-dessus du moteur Java, pas une réimplémentation séparée des
règles. Elle a révélé une fonctionnalité de commerce non documentée
(§13.1), nuancé l'écart §8.2 sur la limite de dons de technologie
(§13.2), renforcé la confiance dans l'écart §11.2 grâce à une
corroboration indépendante Java/PHP (§13.3), et signalé un mécanisme de
"prêt pour le tour suivant" non documenté dont l'effet réel sur le
calendrier des tours n'a pas pu être tranché (§13.4). Cette passe a
couvert les fichiers PHP contenant une logique substantielle
(formulaires d'ordres, création de tables) ; les pages purement
statiques (forum, authentification, présentation générale) n'ont pas
été relues en détail faute d'intérêt pour un audit règles-vs-code.*
