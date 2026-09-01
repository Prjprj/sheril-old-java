# [EN COURS] Absence de la limite "une seule enchère de lieutenant par tour"

- **Fichier concerné** : `sources/zIgzAg/jeu/oceane/ReceptionOrdres.java`
  (méthodes `enroler_lieutenant` et `reglerEncheres`)
- **Nature** : contrôle manquant côté serveur — pas un problème de
  données ni de configuration. Atteignable en jeu normal via une simple
  requête HTTP, sans outillage particulier.
- **Statut** : correctif proposé ci-dessous, **non appliqué** sur cette
  branche (rapport de détection uniquement, à la demande explicite).

## 1. Comportement observé

Les règles du jeu (`rules/Mise à jour/8. Lieutenants.md` §8.1) sont
explicites : *"Un commandant ne peut faire qu'une seule enchère par
tour."*

Rien dans `ReceptionOrdres.enroler_lieutenant` ne fait respecter cette
règle : chaque ordre est traité indépendamment, sans jamais vérifier
si le commandant courant a déjà une autre enchère en cours ce tour-ci.

Vérifié empiriquement côté PHP (sans toucher au Java, les joueurs n'y
ayant pas accès) : le formulaire standard
(`php/ordres/fr/choix/enroler_lieutenant.txt`) masque son propre champ
de saisie dès qu'une ligne existe déjà pour le commandant dans la table
`enroler_lieutenant` ce tour-ci (`if ($nb_lignes < 1)`), donnant
l'illusion d'une limite appliquée. Mais l'endpoint qui insère réellement
l'ordre (`index.php3?table=enroler_lieutenant`, traité par le script
générique `php/ordres/insert.txt`) ne revérifie jamais ce compte avant
d'insérer. Une deuxième requête `POST` vers ce même endpoint, avec un
`v1` (index de lieutenant) différent du premier ordre, envoyée
directement depuis la console JavaScript du navigateur (même session,
sans repasser par le formulaire masqué), est acceptée sans erreur et
crée une deuxième ligne dans `enroler_lieutenant` pour le même
commandant. Confirmé en relisant ensuite `index.php3?table=list_ordres`
: les deux ordres ("Offre de X centaures pour \<lieutenant>") y
apparaissent, l'un pour chaque lieutenant visé.

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

Non réalisée : ce rapport documente le comportement observé et propose
un correctif, sans l'appliquer ni le tester, à la demande explicite
formulée pour cette branche. Une vérification par test unitaire
(mock de `Univers`, `ReceptionOrdres` instancié sans passer par son
constructeur qui ouvre une connexion JDBC réelle — via
`Mockito.mock(ReceptionOrdres.class, Mockito.CALLS_REAL_METHODS)` puis
initialisation des champs privés nécessaires par réflexion) est prévue
pour une itération ultérieure, de même qu'une vérification manuelle
côté PHP par navigateur (déjà documentée dans la conversation ayant mené
à ce rapport, non reproduite ici).

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
