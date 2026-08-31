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

*Prochaines sections à auditer : flottes/combats, population,
lieutenants, relations entre commandants — en attente de feu vert.*
