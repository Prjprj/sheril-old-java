# [EN COURS] Affichage "+-230" et chiffres anormaux sur "Dommages encaissés" d'un bâtiment planétaire

**Statut : investigation ouverte, en attente d'informations complémentaires
de l'utilisateur.** Ce document sera complété/finalisé dès que ces
informations seront disponibles — voir §5 pour la liste précise de ce qui
manque.

## 1. Signalement

Combat entre une planète (1876 milices, 1 station d'armements et
explosifs, 1 mine) et une flotte (1 cleanMate + 27 Scylla-vortex, plans
inconnus).

Rapport observé (capture d'écran, source exacte non identifiée — voir §5) :
- station d'armements et explosifs : **"+-230" dégâts encaissés** (affichage
  anormal, signe en double)
- mine : **+20** dégâts encaissés
- milices : baissent de **95** (affichage normal, pas de glitch de signe)
- dégâts infligés (rapport côté flotte) : 10 par le cleanMate, 95 par les
  Scylla-vortex

Contrairement au signalement précédent (voir `doc/combat-comportements-
non-documentes.md`, finding 11, et `doc/fix/sous-comptage-degats-infliges-
batiments.md`), l'utilisateur confirme qu'il s'agit d'un **rapport
différent** de celui du cas des mines — sans savoir de quelle page il
provient précisément.

## 2. Ce qui est confirmé

### 2.1 Le bug d'affichage "+-" est réel et localisé

`Combat.ecrireDetailCombatPlanete` construit la cellule "Dommages
encaissés" ainsi :

```java
int dom = nT[1] + (-nT[0] + dT[0]) * nbCases;
int domA = mT[1] + (-mT[0] + dT[0]) * nbCases;
if (dom == domA)
    a[ligne][2] = ...ajout(Rapport.getText(Integer.toString(dom)));
else
    a[ligne][2] = ...ajout(Rapport.getText(Integer.toString(dom)))
            .ajout(...ajout(Rapport.getText("(+"
                    + Integer.toString(dom - domA)
                    + ")")));
```

Le préfixe `"(+"` est codé en dur, sans condition de signe. Si
`dom - domA` est négatif, la concaténation produit littéralement
`"(+" + "-230" + ")"` = **"(+-230)"** — correspond exactement au
symptôme rapporté.

C'est le **seul** endroit du code source qui produit le libellé de colonne
"Dommages encaissés" (`MessagesRapport.COMBAT_PLANETE[3]`, utilisé une
seule fois, dans `Combat.java` ligne 638) : quelle que soit la page HTML
précise sur laquelle l'utilisateur a vu ce chiffre, elle est très
probablement générée par cette même méthode `ecrireDetailCombatPlanete`.

### 2.2 Les milices ne sont pas concernées par ce bug précis

La cellule "Milices" du même tableau utilise un formatage de signe correct
(`Integer.toString(Math.max(0, popn) - popm)`, sans préfixe codé en dur) —
cohérent avec le fait que l'utilisateur rapporte un affichage normal pour
les milices ("baissent de 95", pas de "+-95").

## 3. Ce qui a été testé et ÉCARTÉ comme mécanisme déclencheur

Hypothèse testée : un bâtiment (la station) détruit en un round, visé par
un grand nombre de vaisseaux (jusqu'à 28, comme la flotte rapportée),
produirait un `dom - domA` négatif à cause d'un empilement de dégâts
("overkill") avant son retrait de la liste des bâtiments.

**Résultat : cette hypothèse ne tient pas.** Analyse algébrique et
simulation empirique (deux scénarios : 1 bâtiment seul, puis 2 bâtiments
en concurrence pour le ciblage, sur plusieurs rounds, avec des dizaines de
vaisseaux tirant sur un nombre réduit de cibles) montrent que, pour un
type de bâtiment présent en un seul exemplaire qui passe de "vivant" à
"détruit et retiré" en un round, la formule se simplifie
systématiquement à :

```
dom - domA = pointsDeStructure - dommagesAuDebutDuRound
```

— une valeur **toujours comprise entre 0 et pointsDeStructure**, jamais
négative, quel que soit le niveau de surpuissance ("overkill") du ou des
coups qui l'ont détruit dans l'intervalle. Le terme `dT[1]` (dégâts de
référence au tout début du combat) n'intervient même pas dans le résultat
— il s'élimine algébriquement.

Autres pistes envisagées et écartées faute de code correspondant :
- **Réparation en cours de combat** : aucun appel à
  `ConstructionPlanetaire.reparation`/`Planete.reparation` n'existe dans
  `Combat.java` — la réparation est un mécanisme de tour, pas de combat.
- **Rapport généré par un code différent** : `"Dommages encaissés"`
  (`MessagesRapport.COMBAT_PLANETE[3]`) n'est utilisé qu'à un seul endroit
  du code source (`Combat.java:638`) — pas de méthode alternative
  identifiée qui produirait ce même libellé avec une logique différente.

## 4. Hypothèses restant à explorer

Non testées faute d'informations suffisantes (voir §5) :

- **Plusieurs combats successifs sur la même planète au même tour** — si
  la flotte assaillante est en réalité scindée en plusieurs *flottes*
  distinctes (le cleanMate et les 27 Scylla-vortex pourraient ne pas
  appartenir à la même flotte), `Combat.resolutionAttaqueSysteme` appelle
  `combatFlottePlanete` séparément pour chaque flotte avec directive
  correspondante, chacune recalculant sa propre référence `dT`/`materielPlanete`
  à partir de l'état courant de la planète. Analyse préliminaire : ceci ne
  change pas la borne démontrée en §3 pour une transition simple en un
  round au sein d'un même appel — mais l'enchaînement de plusieurs appels
  n'a pas encore été simulé de bout en bout.
- **Le round exact où le chiffre a été observé** — si le rapport montre le
  *dernier* round d'un combat qui en a compté plusieurs, et que la station
  a été endommagée sur plusieurs rounds avant sa destruction finale, la
  séquence complète des transitions round par round n'a pas été simulée
  avec des valeurs proches du cas réel (structure de la station, dégâts
  par tir des Scylla-vortex/cleanMate inconnus — voir §5).
- **Bâtiments multiples du même type** — si la planète avait en réalité
  plus d'un exemplaire de "station d'armements et explosifs" à un moment
  du combat (contredit l'énoncé "1 station", mais à vérifier), la formule
  peut se comporter différemment (les termes de comptage ne s'annulent
  plus proprement).
- **Une page de rapport distincte, non encore localisée dans le code Java**
  — l'utilisateur confirme que ce rapport diffère de celui du cas des
  mines, sans certitude sur son origine exacte (voir §5, question 3).

## 5. Informations manquantes pour trancher

Demandées à l'utilisateur, réponses en attente :

1. La station a-t-elle été **détruite** dans ce combat, ou existe-t-elle
   encore après (endommagée mais debout) ?
2. Combien de **rounds** ce combat a-t-il duré ?
3. **Confirmé par l'utilisateur** : ce rapport est différent de celui du
   cas des mines. Origine exacte (nom de page/fichier HTML, ou contexte
   d'affichage) encore inconnue — l'utilisateur n'a qu'une capture
   d'écran.
4. **Confirmé par l'utilisateur** : les milices s'affichent correctement
   (pas de glitch de signe) — cohérent avec §2.2, n'invalide aucune piste.
5. Les plans de vaisseaux ("cleanMate", "Scylla-vortex") et leurs dégâts
   au sol par tir, ainsi que les points de structure de la station et de
   la mine, permettraient de reproduire fidèlement le scénario complet
   (nombre de rounds, séquence de dégâts) plutôt que des valeurs de
   substitution.
6. Le cleanMate et les 27 Scylla-vortex appartiennent-ils à la **même
   flotte**, ou à des flottes séparées du même commandant (pertinent pour
   la piste "combats successifs" du §4) ?

## 6. Prochaines étapes

Dès réception des informations du §5 :
1. Reproduire le scénario exact (nombre de rounds, structure/dégâts
   réels) dans un test de caractérisation, à l'image de
   `CombatDegatsNegatifsTest.java`.
2. Si la piste "flottes séparées / combats successifs" se confirme,
   simuler l'enchaînement complet de `resolutionAttaqueSysteme` sur
   plusieurs flottes plutôt qu'un seul appel à `combatFlottePlanete`.
3. Une fois la cause confirmée, documenter le correctif selon le même
   format que `doc/fix/sous-comptage-degats-infliges-batiments.md`.
