# [INVALIDÉ] Absence de la limite "une seule enchère de lieutenant par tour"

> **Correction (postérieure à ce rapport) : l'écart décrit ci-dessous
> est réfuté.** L'analyse initiale (§1-§2) ne portait que sur
> `ReceptionOrdres.enroler_lieutenant`/`reglerEncheres`, sans examiner
> la boucle de dispatch des ordres qui les appelle,
> `ReceptionOrdres.getOrdres()`/`resoudreMethode()`. Cette boucle
> applique bien la règle : `Const.NOMBRE_LIMITE_ENROLER_LIEUTENANT = 1`,
> et si plus d'une ligne `enroler_lieutenant` existe en base pour un
> commandant, **la totalité** de ses lignes est écartée avant
> traitement (pas seulement l'excédent) — voir le détail complet en
> §6 ci-dessous, avec preuve sur données de partie réelles (tours 10 et
> 11). Le contournement du formulaire PHP décrit en §1/§4 reste réel
> (la ligne excédentaire est bien insérée en base), mais **sans effet
> en jeu** : il fait perdre au joueur son enchère légitime en plus de
> la seconde, au lieu de lui permettre de gagner deux lieutenants.
> Rapport conservé pour la trace de l'investigation (une hypothèse
> réfutée reste documentée, pas supprimée), le correctif proposé en §3
> **ne doit pas être appliqué**.

- **Fichier concerné** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthodes `enroler_lieutenant` et `reglerEncheres` — voir la
  correction en §6 : la méthode réellement en cause pour la limite est
  `getOrdres()`/`resoudreMethode()`, pas celles-ci)
- **Nature** : ~~contrôle manquant côté serveur~~ — en réalité un
  contrôle existant, situé à un autre niveau que celui d'abord examiné.
- **Statut** : **invalidé**, voir correction en §6. Comportement décrit
  en §1 confirmé par exécution manuelle du test décrit en §4 (l'insertion
  en base a bien lieu), mais sa conséquence en jeu (double enchère
  honorée) est réfutée par la lecture complémentaire de §6 et par les
  données de partie réelles qui y sont citées. Correctif proposé en §3
  **non appliqué, et ne devant pas l'être** (la limite existe déjà).

## 1. Comportement observé

Les règles du jeu (`rules/Mise à jour/8. Lieutenants.md` §8.1) sont
explicites : *"Un commandant ne peut faire qu'une seule enchère par
tour."*

Rien dans `ReceptionOrdres.enroler_lieutenant` ne fait respecter cette
règle : chaque ordre est traité indépendamment, sans jamais vérifier
si le commandant courant a déjà une autre enchère en cours ce tour-ci.

Vérifié empiriquement côté PHP (sans toucher au Java, les joueurs n'y
ayant pas accès), par exécution manuelle du test décrit en détail au
§4 : le formulaire standard (`php/ordres/fr/choix/enroler_lieutenant.txt`)
masque son propre champ de saisie dès qu'une ligne existe déjà pour le
commandant dans la table `enroler_lieutenant` ce tour-ci
(`if ($nb_lignes < 1)`), donnant l'illusion d'une limite appliquée.
Mais l'endpoint qui insère réellement l'ordre
(`index.php3?table=enroler_lieutenant`, traité par le script générique
`php/ordres/insert.txt`) ne revérifie jamais ce compte avant d'insérer.
Une deuxième requête `POST` vers ce même endpoint, avec un `v1` (index
de lieutenant) différent du premier ordre, envoyée directement depuis
la console JavaScript du navigateur (même session, sans repasser par le
formulaire masqué), a été acceptée sans erreur JavaScript ni erreur
serveur, et a créé une deuxième ligne dans `enroler_lieutenant` pour le
même commandant. Confirmé en relisant ensuite
`index.php3?table=list_ordres` : les deux ordres ("Offre de X centaures
pour \<lieutenant>") y sont apparus, l'un pour chaque lieutenant visé.

## 2. Cause racine

```java
// ReceptionOrdres.java:495-527
public void enroler_lieutenant(String[] o) {
    Leader[] leadersList = Univers.listeLeadersEnVente();
    int index = tInt(o[1]);
    ...
    if ((float) tInt(o[0]) <= c[iC].getCentaures()) {
        if (!offresLieutenants.containsKey(o[1])) {
            offresLieutenants.put(o[1], o[0] + "*" + c[iC].getNumero());
        } else {
            // ... comparaison avec l'enchérisseur précédent sur CE lieutenant ...
        }
    }
    ...
}
```

`offresLieutenants` est une `HashMap` indexée par **lieutenant visé**
(`o[1]`, la clé), pas par commandant. La seule vérification de doublon
porte sur "quelqu'un a-t-il déjà misé sur *ce* lieutenant" — jamais sur
"*ce commandant* a-t-il déjà misé sur un lieutenant *quelconque* ce
tour-ci". Rien n'empêche donc `c[iC]` d'apparaître comme enchérisseur
dans plusieurs entrées de la map simultanément, une par lieutenant visé.

Cette absence de contrôle se propage jusqu'à la résolution des
enchères en fin de tour :

```java
// ReceptionOrdres.java:307-334
public void reglerEncheres() {
    Leader[] l = Univers.listeLeadersEnVente();
    Map.Entry[] m = (Map.Entry[]) offresLieutenants.entrySet().toArray(new Map.Entry[0]);
    for (int i = 0; i < m.length; i++) {
        ...
        Commandant c = Univers.getCommandant(numC);
        if ((float) offre >= l[numeroLieutenant].getValeur()) {
            if (l[numeroLieutenant] instanceof Heros)
                c.ajouterHeros((Heros) l[numeroLieutenant]);
            else
                c.ajouterGouverneur((Gouverneur) l[numeroLieutenant]);
            ...
        }
    }
}
```

`reglerEncheres()` parcourt chaque entrée de `offresLieutenants`
indépendamment et attribue le lieutenant correspondant au commandant
gagnant (`Commandant.ajouterHeros`/`ajouterGouverneur`, qui ne fait
qu'ajouter à une liste, sans aucune vérification de quota). Si le même
`numC` gagne plusieurs entrées, il reçoit tout simplement plusieurs
lieutenants au même tour.

## 3. Correctif proposé

Faire respecter la règle au moment de l'ordre plutôt qu'à la
résolution : refuser une nouvelle enchère du commandant courant si
`offresLieutenants` contient déjà une entrée dont il est l'auteur
(quel que soit le lieutenant visé), à l'exception du cas où l'ordre
concerne le *même* lieutenant que son enchère existante (ce qui reste
une mise à jour de son unique enchère en cours, pas une seconde
enchère).

```diff
 public void enroler_lieutenant(String[] o) {
 	Leader[] leadersList = Univers.listeLeadersEnVente();
 	int index = tInt(o[1]);
 	Leader l;
 	if (index >= 0 && index < leadersList.length) {
 		l = leadersList[index];
 	} else {
 		System.out.println("ERREUR: enchère en dehors des clous : " + index);
 		return;
 	}
 	String leaderName = l.getNom();
+
+	// Un commandant ne peut avoir qu'une seule enchère en cours par tour
+	// (règles §8.1) : chercher si une entrée existe déjà pour lui, sur un
+	// AUTRE lieutenant que celui visé par cet ordre.
+	for (Object cle : offresLieutenants.keySet()) {
+		if (cle.equals(o[1]))
+			continue;
+		String entree = (String) offresLieutenants.get(cle);
+		int numeroExistant = tInt(entree.substring(entree.indexOf('*') + 1));
+		if (numeroExistant == c[iC].getNumero()) {
+			c[iC].ajouterErreur("ER_COMMANDANT_ACHETER_LIEUTENANT_0002", leaderName);
+			return;
+		}
+	}
+
 	if ((float) tInt(o[0]) <= c[iC].getCentaures()) {
 		if (!offresLieutenants.containsKey(o[1])) {
 			offresLieutenants.put(o[1], o[0] + "*" + c[iC].getNumero());
```

Un nouveau message d'erreur (`ER_COMMANDANT_ACHETER_LIEUTENANT_0002`,
à ajouter dans `MessagesInfo`/`MessagesSystemes` selon la convention du
reste du fichier) serait nécessaire pour informer le joueur que son
ordre a été rejeté faute de pouvoir enchérir deux fois. Sans ce
message, l'ordre échouerait silencieusement, ce qui serait une
régression d'expérience par rapport au reste de l'application (tous les
autres rejets de `enroler_lieutenant` génèrent un événement explicite).

### Point ouvert à trancher avant d'appliquer ce correctif

Le formulaire PHP masque le champ de saisie dès qu'**une** ligne existe
dans `enroler_lieutenant` pour le commandant, y compris si cette ligne
correspond à une modification de sa propre enchère sur le même
lieutenant (auquel cas `enroler_lieutenant` prend la branche `else` et
compare simplement les montants). Le correctif ci-dessus autorise
explicitement ce cas (`if (cle.equals(o[1])) continue;`) pour ne pas
casser la possibilité de réviser son enchère sur le lieutenant déjà
visé — mais il faudrait vérifier si le formulaire PHP permet réellement
à un joueur de renvoyer un ordre modifié sur le même lieutenant une
fois son formulaire masqué (probablement non, avec le même défaut que
celui documenté en §13.2 de `doc/audit-regles-vs-code.md` : c'est
l'UI, pas le serveur, qui gère aujourd'hui ce cas). Un correctif complet
devrait donc aussi revoir ce comportement côté PHP, hors du périmètre
Java de ce rapport.

## 4. Vérification

Le correctif proposé en §3 n'a pas été appliqué ni testé (à la demande
explicite formulée pour cette branche). Le **comportement observé**
(§1), en revanche, a été vérifié empiriquement — manuellement, par
l'utilisateur, en exécutant la procédure ci-dessous depuis le
navigateur, sans aucun accès Java ni serveur :

1. Se connecter en tant que joueur, soumettre une première offre via le
   formulaire normal `index.php3?table=enroler_lieutenant` (lieutenant
   d'indice 0).
2. Recharger la page : le formulaire a bien disparu (`$nb_lignes<1`
   devenu faux côté PHP), confirmant que la protection n'existe que
   côté UI.
3. Dans le contexte de la frame affichant `index.php3?table=...`,
   exécuter depuis la console JavaScript du navigateur :
   ```js
   fetch('index.php3?table=enroler_lieutenant', {
     method: 'POST',
     credentials: 'same-origin',
     headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
     body: new URLSearchParams({ v0: '500', v1: '1', ajout: 'Envoyer cet ordre' })
   }).then(r => r.text()).then(html => console.log(html.length, html));
   ```
   (offre sur le lieutenant d'indice 1, différent du premier ordre,
   même session).
4. Recharger `index.php3?table=list_ordres`.

**Résultat observé** : la requête du point 3 n'a produit aucune erreur
JavaScript (la promesse s'est résolue normalement). À l'étape 4, les
**deux** lignes d'enrôlement sont apparues ("Offre de 500 centaures
pour \<lieutenant 0>" et "Offre de 500 centaures pour \<lieutenant
1>"), confirmant que le second ordre a bien été accepté et enregistré
malgré le formulaire masqué — exactement le comportement que laissait
prévoir la lecture de `insert.txt` (absence de recomptage côté
serveur).

Une vérification complémentaire par test unitaire Java (mock de
`Univers`, `ReceptionOrdres` instancié sans passer par son constructeur
qui ouvre une connexion JDBC réelle — via
`Mockito.mock(ReceptionOrdres.class, Mockito.CALLS_REAL_METHODS)` puis
initialisation des champs privés nécessaires par réflexion) reste à
faire pour confirmer que `reglerEncheres()` attribue effectivement les
deux lieutenants au même commandant en fin de tour — non réalisée à ce
stade.

## 5. Portée et limites du correctif proposé

- Corrige uniquement l'enrôlement de lieutenants (`enroler_lieutenant`).
  D'autres plafonds "par tour" annoncés par les règles et absents côté
  Java ont été identifiés séparément lors de l'audit règles-vs-code
  (`doc/audit-regles-vs-code.md`, branche `audit/regles-vs-code-technologies`)
  — notamment la limite de 999 unités par transfert inter-système
  (§2.3), la limite de 3 missions spéciales par tour (§5.4) et la
  limite d'une technologie cédée par tour (§8.2). Ce rapport ne les
  couvre pas ; chacun nécessiterait un correctif du même type, localisé
  à sa propre méthode d'ordre.
- Ne modifie ni le montant des enchères, ni le mécanisme de doublement
  d'offre pour un commandant sans lieutenant (§8.1, déjà conforme), ni
  la résolution des enchères en fin de tour (`reglerEncheres`) —
  seule l'acceptation d'un ordre en amont est concernée.
- Le point ouvert du §3 (cohérence avec le comportement du formulaire
  PHP pour la modification d'une enchère existante) doit être tranché
  avant application, pour éviter de casser un cas d'usage légitime que
  le formulaire semble aujourd'hui permettre.

## 6. Correction : la limite est en réalité appliquée par `ReceptionOrdres.getOrdres()`

Suite à l'exécution du test décrit en §4 sur des tours réels (10 et
11) et à la question de savoir si l'effet observé en jeu correspondait
bien à la prédiction de ce rapport, une lecture complémentaire de
`ReceptionOrdres.java` — au-delà des seules méthodes `enroler_lieutenant`
et `reglerEncheres` examinées en §1-§2 — a révélé le mécanisme réel de
traitement des ordres :

```java
// ReceptionOrdres.java:146-158 (getOrdres(), appelée avant tout traitement)
if (((index == Const.ORDRE_ENROLER_LIEUTENANT) && (a.size() > Const.NOMBRE_LIMITE_ENROLER_LIEUTENANT))
        || ((index == Const.ORDRE_SERVICES_SPECIAUX) && (a.size() > Const.NOMBRE_LIMITE_SERVICES_SPECIAUX))
        || ((index == Const.ORDRE_DON_TECHNOLOGIE) && (a.size() > Const.NOMBRE_LIMITE_DON_TECHNOLOGIE))
        || ((index == Const.ORDRE_CREER_PLAN) && (a.size() > Const.NOMBRE_LIMITE_CREATION_PLAN))
        || ((index == Const.ORDRE_CREER_STRATEGIE) && (a.size() > Const.NOMBRE_LIMITE_CREATION_STRATEGIE))) {
    a = new ArrayList();
    Univers.ajouterErreur(c[iC].getNomNumeroHtml(), "ER_ORDRE_0002",
            Const.NOMS_TABLES_ORDRES[index]);
}
```

```java
// Const.java:754-758
NOMBRE_LIMITE_ENROLER_LIEUTENANT = 1;
NOMBRE_LIMITE_SERVICES_SPECIAUX = 3;
NOMBRE_LIMITE_DON_TECHNOLOGIE = 1;
```

`getOrdres()` est appelée par `resoudreMethode()` **avant** que
`enroler_lieutenant(o)` ne soit invoquée pour chaque ligne — si le
nombre de lignes en base pour ce commandant dépasse la limite, elle
retourne un tableau **vide**, et aucune des méthodes examinées en §1-§2
n'est jamais appelée. `resoudreMethode()` porte en plus un garde-fou
redondant par index (`j > 0` pour `ORDRE_ENROLER_LIEUTENANT`, ligne
203-206), qui n'a normalement pas l'occasion de s'exécuter puisque
`getOrdres()` a déjà tout écarté en amont dans le cas où la limite est
dépassée.

**Preuve sur données de partie réelles.** Après le test manuel décrit
en §4 (deux offres pour le commandant 1, sur les lieutenants 0 et 1),
`data/tour10/dump.sql` confirme bien l'insertion des deux lignes en
base — mais le rapport individuel du commandant 1 pour le tour suivant
(`data/tour11/rapports/1tour11/rapport.xml`, section `<messages>`) ne
contient **aucun** événement lié à l'enrôlement d'un lieutenant, ni
succès ni échec. (Le même test répété sur les missions spéciales et le
don de technologie, avec les mêmes conclusions, est détaillé dans les
rapports jumeaux `doc/fix/limite-missions-speciales-par-tour.md` et
`doc/fix/limite-don-technologie-par-tour.md`.)

**Conséquence pour le joueur qui tenterait ce contournement en jeu
réel** : loin de gagner un second lieutenant, il perd la totalité de
ses enchères de lieutenant pour ce tour — y compris la première,
légitime. Le "bug" décrit en §1 est donc réel *au niveau de
l'insertion en base* (le formulaire masqué n'empêche pas une requête
directe d'ajouter une ligne), mais n'a **aucune conséquence en jeu** :
la règle "une seule enchère par tour" est effectivement respectée à la
résolution.

Voir `doc/audit-regles-vs-code.md` §4.6 sur la branche
`audit/regles-vs-code-technologies` pour la version complète et à jour
de cette correction, incluse dans le même document que les deux cas
jumeaux.
