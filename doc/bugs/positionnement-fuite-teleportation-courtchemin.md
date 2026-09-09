# [CONFIRMÉ, NON CORRIGÉ] Un vaisseau qui fuit en combat est téléporté hors de portée au lieu de s'éloigner progressivement

- **Fichier en cause** : `src/main/java/zIgzAg/jeu/oceane/Utile.java`
  (méthode `courtChemin`), appelée depuis `src/main/java/zIgzAg/jeu/oceane/Position3D.java`
  (méthode `positionAtteinte`), elle-même appelée depuis
  `src/main/java/zIgzAg/jeu/oceane/Combat.java` (méthode privée
  `mouvementVaisseau`).
- **Nature** : bug logique — une fonction écrite pour un usage (déplacement
  de flotte sur la carte galactique, bornée et torique) est réutilisée pour
  un usage différent (positionnement tactique en combat, non borné de la
  même façon) dont elle ne respecte pas les hypothèses. Atteignable en jeu
  normal, sans donnée mal configurée.
- **Statut : cause confirmée empiriquement, y compris en rejouant un combat
  réel tiré des données de partie et en lisant son journal détaillé.
  Correctif non appliqué** (analyse et tests d'abord, correctif seulement
  si demandé — voir §4 pour la proposition).

Second document lié au même signalement utilisateur que
`doc/bugs/degats-bouclier-non-comptabilises-attaquant.md` (combat en
position 37-27, flotte "Gzav" vs flotte "Bear One" du commandant Snagha,
0 dégâts rapportés). Ce document couvre une seconde cause, indépendante
de l'absorption par bouclier : des vaisseaux quittent instantanément la
zone de combat dès le premier round, rendant tout échange de tir
impossible pour le reste du combat.

## 1. Signalement

En activant le journal détaillé de combat (`Combat.log`/`logln`) lors du
rejeu du combat réel (mêmes flottes que le document ci-dessus,
désérialisées depuis `analyse/donneesT16/donnees/comm.txt`), dès le
**round 1** :

```
TOUR DE COMBAT 1
[F24_10 VS F11_19]  C10 , tir vaisseau 1 N°0/7 (scylla-vortex, race: 4) , attP: (x:83|y:17|z:11)  - chance < 0 , exit
...
, cible: scylla-vortex, deffP: (x:83|y:17|z:11), distance: 67
, cible: scylla-vortex, deffP: (x:83|y:17|z:11), distance: 80
...
[F24_10 VS F11_19]  C19 , tir vaisseau 16 N°0/3 (Grand Bombardier standard, race: 2) , attP: (x:31|y:1020|z:88)  - pas darmes valides
```

Le second vaisseau `scylla-vortex` de la flotte "Gzav" (portée d'arme :
16) se retrouve à une distance de 60 à 80+ de ses cibles **dès le premier
round** — largement hors de portée de toute arme du jeu — et y reste pour
le reste du combat. Un "Grand Bombardier standard" de "Bear One" atteint
même `y:1020`, une valeur qui n'a rien à voir avec les dimensions de
l'arène de combat.

## 2. Cause racine

`Const.COMBAT_X_MAX = 30`, `Const.COMBAT_Y_MAX = 10`,
`Const.COMBAT_Y_ESPACE = 10` définissent une arène de combat compacte.
Mais un vaisseau qui doit fuir (voir §2.1 pour les deux cas où cela
arrive) est envoyé vers `Position3D.positionDeFuite(att)` :

```java
// Position3D.java
public static Position3D positionDeFuite(boolean att) {
    Position3D retour = new Position3D();
    int[] inter = new int[3];
    if (att) inter[1] = Const.COMBAT_Y_FIN1;   // -1000
    else     inter[1] = Const.COMBAT_Y_FIN2;   // 1020
    inter[0] = Univers.getInd() * Univers.getInt(100);   // ±0-99
    inter[2] = Univers.getInd() * Univers.getInt(100);   // ±0-99
    retour.setPos(inter);
    return retour;
}
```

`Const.COMBAT_Y_FIN1 = -1000` et `Const.COMBAT_Y_FIN2 = -COMBAT_Y_FIN1 +
COMBAT_Y_MAX + COMBAT_Y_ESPACE = 1020` : des sentinelles délibérément
extrêmes, pensées comme "hors du champ de bataille". Le déplacement réel
vers cette cible passe par :

```java
// Position3D.java
public static Position3D positionAtteinte(Position3D depart, Position3D arrivee, int x) {
    ...
    inter[0] = Utile.courtChemin(depart.getPos()[0], arrivee.getPos()[0], x);
    inter[1] = Utile.courtChemin(depart.getPos()[1], arrivee.getPos()[1], x);
    inter[2] = Utile.courtChemin(depart.getPos()[2], arrivee.getPos()[2], x);
    ...
}
```

`Utile.courtChemin(depart, arrivee, deplacement)` est la fonction utilisée
partout ailleurs dans le jeu pour déplacer une **flotte sur la carte
galactique** — un tore qui boucle tous les `Const.BORNE_MAX = 40`
secteurs (seul autre appelant : `Position.java`, pour ce même usage
galactique légitime). Son calcul de "plus court chemin en tenant compte
du bouclage" suppose implicitement que `depart` et `arrivee` sont tous
deux dans l'intervalle `[0, BORNE_MAX)` — une hypothèse que les
coordonnées de fuite en combat (jusqu'à ±1020, très loin au-delà de
`BORNE_MAX = 40`) violent largement. Deux défauts distincts en résultent
selon le signe de l'écart :

**a) Vers la sentinelle positive (`COMBAT_Y_FIN2` = 1020) ou une
coordonnée X/Z aléatoire lointaine (jusqu'à 99)** — le nombre de pas
restants calculé (`compteur`) devient négatif :

```java
if (depart < arrivee)
    compteur = Math.min(Math.abs(arrivee - depart), depart - (arrivee - Const.BORNE_MAX));
...
if (compteur <= deplacement) return arrivee;   // trivialement vrai si compteur < 0
```

Exemple réel : `courtChemin(17, 1020, 7)` → `compteur = min(1003, 17-(1020-40)) = min(1003, -963) = -963`,
et `-963 <= 7` → retourne **1020 directement**, quelle que soit la
vitesse réelle du vaisseau (`deplacement = 7`).

**b) Vers la sentinelle négative (`COMBAT_Y_FIN1` = -1000)** — un défaut
différent : la comparaison des deux "distances avec bouclage" sélectionne
la branche qui déplace le vaisseau de sa vitesse propre, mais **dans le
sens opposé** à sa cible de fuite. Exemple réel :
`courtChemin(17, -1000, 7)` retourne **24** (le vaisseau s'éloigne de
7 vers le +, alors qu'il devrait se rapprocher de -1000).

### 2.1 Deux déclencheurs, pas seulement une fuite tactique délibérée

`Combat.mouvementVaisseau` envoie un vaisseau vers `positionDeFuite` dans
deux cas :

```java
if ((fuite) || (!v.estCombatif()) || (v.getNombreArmesValides() == 0))
    pos1.put(cle, Position3D.positionAtteinte(inter, Position3D.positionDeFuite(att),
            Math.max(0, v.getCapaciteMouvement(false) - 2)));
```

- `fuite` : décision tactique de la flotte entière pour ce round
  (`Combat.fuiteTactique`, dépend de la stratégie d'agressivité et du
  rapport de puissance) — c'est le cas du second `scylla-vortex` de Gzav,
  dont la flotte (puissance 116) est très largement dominée par "Bear One"
  (puissance 736).
- `v.getNombreArmesValides() == 0` : **tout vaisseau sans arme utilisable
  dans ce type de combat**, indépendamment de toute décision tactique —
  notamment tout vaisseau purement bombardier (arme "bombe",
  `estCombatPlanetaire() == true` mais pas `estCombatSpatial()`, cf.
  `Vaisseau.determinerListeArmesValides`) engagé en combat
  vaisseau-contre-vaisseau. C'est le cas des "Grand Bombardier standard"
  et "Bombardier Zwaia" de "Bear One", qui composent la majorité de son
  effectif (44 vaisseaux, dont l'essentiel n'a aucune arme spatiale).

Le second cas est complètement indépendant de tout choix stratégique : il
suffit qu'une flotte compte des vaisseaux dédiés au bombardement
planétaire pour qu'ils soient, de fait, "en fuite" dès le premier round de
tout combat de flotte à flotte — et donc exposés à ce bug de
téléportation, peu importe leur vitesse réelle.

## 3. Vérification effectuée

Fichier : `src/test/java/zIgzAg/jeu/oceane/PositionnementCombatFuiteTeleportationTest.java`
(7 tests, tous verts — suite complète du projet : 55/55 avec ce fichier
ajouté, aucun correctif de production appliqué).

- **Niveau mathématique pur** (`Utile.courtChemin`, sans aucune dépendance
  à `Univers` ni au moteur de combat) : reproduit précisément les deux
  défauts du §2 avec les constantes réelles du jeu (`courtChemin(17,
  Const.COMBAT_Y_FIN2, 7) == 1020` ; `courtChemin(16, 83, 7) == 83` ;
  `courtChemin(17, Const.COMBAT_Y_FIN1, 7) == 24`, pas 10), plus deux
  contre-exemples de garde-fou : une cible de fuite proche se comporte
  normalement (`courtChemin(16, 3, 7) == 9`), et l'usage galactique prévu
  reste correct (`courtChemin(5, 35, 3) == 2`, chemin par le bord
  légitimement plus court).
- **Niveau `Position3D`** (chaîne publique réelle `positionDeFuite` +
  `positionAtteinte`, sans réflexion) : confirme la téléportation de bout
  en bout avec les vraies méthodes appelées par `Combat.mouvementVaisseau`.
- **Niveau `Combat.mouvementVaisseau`** lui-même (méthode privée, appelée
  par réflexion) : un vaisseau muni d'une **vraie arme spatiale valide**
  mais d'une **capacité de mouvement nulle** (aucun moteur sur son plan,
  vérifié par assertion : `v.getCapaciteMouvement(false) == 0`) est
  malgré tout téléporté sur `Const.COMBAT_Y_FIN2` en un seul appel — la
  preuve la plus directe que le bug ignore totalement la vitesse réelle du
  vaisseau, et qu'il ne dépend pas du cas "0 arme valide" documenté par
  ailleurs.

## 4. Correctif proposé (non appliqué)

`Utile.courtChemin` est correct pour son usage prévu (déplacement de
flotte sur la carte galactique torique, `Position.java`) et ne doit **pas**
être modifié — le modifier casserait ce mouvement légitime, ou nécessiterait
de le rendre conditionnel à un contexte, ce qui complexifierait une
fonction par ailleurs correcte.

Le correctif doit donc porter sur `Position3D.positionAtteinte`, qui n'a
aucune raison de boucler sur un tore : l'arène de combat n'est pas
circulaire. Remplacer les trois appels à `Utile.courtChemin` par un
déplacement borné simple, sans notion de bouclage :

```diff
+    private static int deplacementLineaire(int depart, int arrivee, int deplacement) {
+        if (depart == arrivee) return arrivee;
+        int diff = arrivee - depart;
+        int pas = Math.min(Math.abs(diff), Math.max(0, deplacement));
+        return depart + Integer.signum(diff) * pas;
+    }
+
     public static Position3D positionAtteinte(Position3D depart,
             Position3D arrivee, int x) {
         if (arrivee == null)
             return depart;
         Position3D retour = (Position3D) depart.clone();
         int[] inter = retour.getPos();
-        inter[0] = Utile.courtChemin(depart.getPos()[0], arrivee.getPos()[0], x);
-        inter[1] = Utile.courtChemin(depart.getPos()[1], arrivee.getPos()[1], x);
-        inter[2] = Utile.courtChemin(depart.getPos()[2], arrivee.getPos()[2], x);
+        inter[0] = deplacementLineaire(depart.getPos()[0], arrivee.getPos()[0], x);
+        inter[1] = deplacementLineaire(depart.getPos()[1], arrivee.getPos()[1], x);
+        inter[2] = deplacementLineaire(depart.getPos()[2], arrivee.getPos()[2], x);
         return retour;
     }
```

Avec ce correctif, un vaisseau en fuite se rapproche de sa cible de fuite
d'au plus `deplacement` unités par round, dans la bonne direction, sans
jamais "sauter" au-delà de sa vitesse réelle — qu'il fuie vers
`COMBAT_Y_FIN1` (-1000), `COMBAT_Y_FIN2` (1020), ou toute autre
coordonnée. Un vaisseau sans moteur (`deplacement == 0`) resterait
immobile, comme attendu.

**Effet de bord à évaluer avant d'appliquer ce correctif** : aujourd'hui,
un vaisseau qui doit fuir (notamment tout bombardier sans arme spatiale,
cf. §2.1) est de facto mis hors de portée dès le premier round, ce qui
l'immunise complètement pour le reste du combat — un effet secondaire du
bug, mais qui a pu, sans que ce soit voulu, masquer jusqu'ici le fait que
ces vaisseaux (nombreux dans les flottes mixtes attaque-planète/attaque-
flotte) participent normalement à un combat de flotte à flotte sans
pouvoir y tirer un seul coup. Corriger le positionnement les rendra à
nouveau atteignables — sans changer `determinerListeArmesValides`, ils
resteront incapables de riposter (0 arme valide), mais pourront être
visés et détruits normalement par l'adversaire, ce qui n'était plus le
cas depuis que ce bug les téléporte hors de portée.

## 5. Portée et limites du correctif proposé

- S'applique à tout combat vaisseau-contre-vaisseau où au moins un
  vaisseau prend la branche "fuite" de `Combat.mouvementVaisseau` — fuite
  tactique délibérée, ou (le cas le plus fréquent en pratique) tout
  vaisseau sans arme utilisable dans ce type de combat.
- Ne modifie ni `Utile.courtChemin` (laissé intact pour son usage
  galactique légitime, `Position.java`), ni les conditions qui déclenchent
  la fuite (`Combat.fuiteTactique`, `Vaisseau.estCombatif`,
  `getNombreArmesValides`) — seul le calcul de la nouvelle position d'un
  vaisseau déjà décidé à fuir est concerné.
- Rend de nouveau atteignables des vaisseaux qui, sous le bug actuel,
  étaient de fait invulnérables après le round 1 (voir "effet de bord" au
  §4) — un changement de comportement de jeu à anticiper, pas seulement
  un correctif silencieux.
- N'a aucune incidence sur le bug documenté séparément dans
  `doc/bugs/degats-bouclier-non-comptabilises-attaquant.md` (absorption
  par bouclier non comptabilisée) : les deux peuvent se combiner dans un
  même combat sans interaction directe entre eux.
