package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Complète l'investigation de CombatDegatsAbsorbesParBouclierTest (0 dégâts
 * comptabilisés malgré des tirs réels, à cause de l'absorption par bouclier)
 * en répondant à la question posée en revue : "est-ce que le positionnement
 * des flottes peut aussi expliquer le comportement ?" — Oui, et il s'agit
 * d'un second défaut, distinct et indépendant, dans le mécanisme de
 * repositionnement "fuite" d'un vaisseau en combat.
 *
 * Rejeu du cas réel (flotte "Gzav", position 0_37_27, cf. rapport signalé) :
 * dès le premier round, le journal de combat (Combat.log) montre le second
 * vaisseau "scylla-vortex" de Gzav passer d'une position raisonnable à
 * "attP: (x:83|y:17|z:11)", à une distance de 60 à 80+ cases de ses cibles —
 * bien au-delà de la portée de toute arme du jeu — pour le reste du combat.
 * Un vaisseau de "Bear One" ("Grand Bombardier standard") atteint même
 * "(x:31|y:1020|z:88)" dès le round 1.
 *
 * La cause : Combat.mouvementVaisseau fait fuir un vaisseau (loin de
 * l'ennemi, vers Position3D.positionDeFuite) dans deux cas — pas seulement
 * une fuite tactique délibérée (stratégie "Fuyard" ou flotte trop faible) :
 * AUSSI dès que le vaisseau n'a plus aucune arme utilisable dans ce type de
 * combat (v.getNombreArmesValides() == 0 — le cas, entre autres, de tout
 * vaisseau purement bombardier engagé en combat spatial, cf.
 * Vaisseau.determinerListeArmesValides). Position3D.positionDeFuite vise une
 * coordonnée Y volontairement extrême — Const.COMBAT_Y_FIN1 (-1000) ou
 * Const.COMBAT_Y_FIN2 (+1020) — et un X/Z aléatoire jusqu'à ±99, bien au-delà
 * de la petite arène de combat (Const.COMBAT_X_MAX=30, COMBAT_Y_MAX=10).
 *
 * Le déplacement réel vers cette cible passe par Position3D.positionAtteinte,
 * qui délègue à Utile.courtChemin(depart, arrivee, deplacement) — une
 * fonction écrite pour le déplacement de flottes sur la carte GALACTIQUE, qui
 * boucle sur elle-même tous les Const.BORNE_MAX (40) secteurs (un tore). Son
 * calcul de "plus court chemin en tenant compte du bouclage" devient invalide
 * dès que la coordonnée cible dépasse très largement BORNE_MAX. Selon le
 * signe de l'écart, deux défauts distincts apparaissent (constatés
 * empiriquement ci-dessous, pas symétriques) : vers la sentinelle positive du
 * défenseur (+1020) ou une coordonnée X/Z aléatoire lointaine, le nombre de
 * pas restants calculé (compteur) devient négatif et
 * `if (compteur <= deplacement) return arrivee;` est trivialement vrai quelle
 * que soit la vitesse réelle du vaisseau — téléportation instantanée ; vers
 * la sentinelle négative de l'attaquant (-1000), la comparaison des deux
 * "distances avec bouclage" sélectionne la branche qui déplace le vaisseau
 * de sa vitesse propre mais dans le sens OPPOSÉ à sa cible de fuite.
 *
 * Conséquence : une fois téléporté à une distance de plusieurs dizaines à
 * plus de 1000 cases, un vaisseau ne peut plus ni toucher ni être touché
 * (Arme.getChanceDeToucher renvoie 0 dès que distance >= portée — aucune arme
 * du jeu n'approche une portée pareille) pour le reste du combat, quel que
 * soit par ailleurs son armement réel. Ce mécanisme s'ajoute (et peut se
 * combiner) à celui de CombatDegatsAbsorbesParBouclierTest pour expliquer un
 * rapport de combat à "0 dégâts" malgré un combat qui a réellement eu lieu.
 */
class PositionnementCombatFuiteTeleportationTest {

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    // ---------------------------------------------------------------
    // Niveau 1 : le mécanisme mathématique brut (Utile.courtChemin), sans
    // aucune dépendance à Univers ou au moteur de combat.
    // ---------------------------------------------------------------

    @Test
    void courtChemin_versLaSentinelleDeFuiteDuDefenseur_teleporteInstantanementMalgreUneVitesseFaible() {
        // Position réaliste dans l'arène de combat (Const.COMBAT_Y_MAX=10, COMBAT_Y_ESPACE=10)
        // vers Const.COMBAT_Y_FIN2 (1020), avec une vitesse de déplacement modeste (7, cohérente
        // avec Combat.mouvementVaisseau : v.getCapaciteMouvement(false) - 2).
        assertEquals(Const.COMBAT_Y_FIN2, Utile.courtChemin(17, Const.COMBAT_Y_FIN2, 7),
                "avec une vitesse de 7, le vaisseau ne devrait parcourir que 7 cases vers sa cible de fuite, "
                        + "pas atteindre instantanément la sentinelle \"hors du champ de bataille\" (1020)");
    }

    @Test
    void courtChemin_versLaSentinelleDeFuiteDeLAttaquant_seDeplaceDansLeMauvaisSensAuLieuDeSenRapprocher() {
        // Asymétrie constatée : contrairement à la sentinelle positive du défenseur
        // (Const.COMBAT_Y_FIN2, cf. test ci-dessus), la sentinelle négative de l'attaquant
        // (Const.COMBAT_Y_FIN1 = -1000) ne déclenche pas une téléportation immédiate mais un
        // AUTRE défaut du même calcul de bouclage : la comparaison des deux "distances avec
        // bouclage" (1017 vs 977) sélectionne la branche qui fait AVANCER le vaisseau de sa
        // vitesse (17+7=24) au lieu de le faire reculer vers sa cible de fuite (-1000) — il
        // s'éloigne donc dans le sens opposé à celui prévu, au lieu de fuir.
        int resultat = Utile.courtChemin(17, Const.COMBAT_Y_FIN1, 7);
        assertEquals(24, resultat,
                "avec les constantes réelles du jeu, courtChemin(17, -1000, 7) déplace le vaisseau vers 24 : "
                        + "il s'éloigne de sa position de départ dans le sens OPPOSÉ à sa cible de fuite (-1000), "
                        + "au lieu de s'en rapprocher de 7 (soit 10)");
        assertNotEquals(10, resultat,
                "un déplacement correct de 7 cases vers -1000 depuis 17 aurait dû donner 10, pas " + resultat);
    }

    @Test
    void courtChemin_versUneCoordonneeXZAleatoireLointaineMaisPasExtreme_peutAussiTeleporter() {
        // Reproduit le cas réel observé du second vaisseau "scylla-vortex" de Gzav : la
        // coordonnée X/Z de fuite est un tirage aléatoire ±99 (Position3D.positionDeFuite),
        // pas une constante extrême comme Y — mais 83 dépasse déjà Const.BORNE_MAX (40),
        // ce qui suffit à déclencher le même piège de calcul.
        assertEquals(83, Utile.courtChemin(16, 83, 7),
                "un vaisseau à vitesse 7 ne devrait pas franchir 67 cases (83-16) en un seul round");
    }

    @Test
    void courtChemin_versUneCibleDeFuiteProche_respecteLaVitesseDeDeplacementNormale() {
        // Contre-exemple : quand la cible de fuite (tirage aléatoire) reste dans un
        // intervalle raisonnable, le même calcul se comporte normalement (pas de
        // téléportation) — le défaut dépend de la valeur tirée, pas de la fuite elle-même.
        assertEquals(9, Utile.courtChemin(16, 3, 7),
                "sans le piège de bouclage, le déplacement doit être borné par la vitesse (16-7=9)");
    }

    @Test
    void courtChemin_usageNormalCarteGalactique_boucleCorrectementParLeBordDuTore() {
        // Garde-fou : confirme que le bug ci-dessus n'est pas une régression de l'usage
        // normal et prévu de courtChemin (déplacement de flotte sur la carte galactique,
        // bornée par Const.BORNE_MAX=40 secteurs, où boucler par le bord est légitime).
        assertEquals(2, Utile.courtChemin(5, 35, 3),
                "de 5 vers 35 sur un tore de 40 secteurs, le chemin par le bord (5->0/40->35, 8 pas) est plus "
                        + "court que le chemin direct (30 pas) : après 3 pas de vitesse, le vaisseau doit être en 2, "
                        + "pas téléporté en 35");
    }

    // ---------------------------------------------------------------
    // Niveau 2 : la chaîne d'appel publique réellement utilisée par
    // Combat.mouvementVaisseau (Position3D.positionDeFuite + positionAtteinte),
    // sans réflexion.
    // ---------------------------------------------------------------

    @Test
    void positionAtteinte_versPositionDeFuiteDuDefenseur_teleporteEnUnSeulRound() throws Exception {
        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(Univers::getInd).thenReturn(1);
            univers.when(() -> Univers.getInt(100)).thenReturn(63); // tirage X/Z simulé, sans incidence sur Y

            Position3D depart = new Position3D(new int[]{16, 17, 5});
            Position3D destinationFuite = Position3D.positionDeFuite(false); // défenseur : Y = Const.COMBAT_Y_FIN2

            int vitesseDeplacementDuRound = 7; // réaliste : capaciteMouvement - 2 (cf. Combat.mouvementVaisseau)
            Position3D arrivee = Position3D.positionAtteinte(depart, destinationFuite, vitesseDeplacementDuRound);

            assertEquals(Const.COMBAT_Y_FIN2, arrivee.getPos()[1],
                    "le vaisseau devrait ne parcourir que 7 unités vers sa cible de fuite, pas l'atteindre "
                            + "instantanément (1020) en un seul round");
        }
    }

    // ---------------------------------------------------------------
    // Niveau 3 : Combat.mouvementVaisseau lui-même (méthode privée, appelée
    // par réflexion comme Vaisseau.effectuerDommages dans
    // CombatDegatsAbsorbesParBouclierTest), avec un VRAI vaisseau muni d'une
    // arme spatiale valide — pour bien montrer que cette téléportation ne
    // dépend PAS d'un vaisseau sans arme (cas déjà couvert séparément par
    // "0 arme valide"), mais peut toucher n'importe quel vaisseau en fuite.
    // ---------------------------------------------------------------

    @Test
    void mouvementVaisseau_fuiteActive_teleporteUnVaisseauArmeInstantanementHorsDePortee() throws Exception {
        Constructor<Arme> ctorArme = Arme.class.getDeclaredConstructor();
        ctorArme.setAccessible(true);
        Arme arme = ctorArme.newInstance();
        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = 2;
        carac[Const.ARME_DOMMAGES_COQUE] = 2;
        carac[Const.ARME_PORTEE] = 16; // comme "canon à plasma" de scylla-vortex
        setField(arme, Arme.class, "caracteristiquesArmes", carac);
        setField(arme, Arme.class, "chanceToucher", new int[10]);
        setField(arme, Arme.class, "typeArme", Const.CV_ARME_CS);
        setField(arme, ComposantDeVaisseau.class, "type", Const.CV_ARME);
        setField(arme, ComposantDeVaisseau.class, "nombreDeCasesPrises", 1);

        Constructor<PlanDeVaisseau> ctorPlan = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlan.setAccessible(true);
        PlanDeVaisseau plan = ctorPlan.newInstance();
        plan.setNom("AssaillantArme");
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"armeI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});

        Map<String, PlanDeVaisseau> plansLocaux = new HashMap<>();
        plansLocaux.put("attA", plan);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plansLocaux.get((String) inv.getArgument(0)));
            univers.when(Univers::getInd).thenReturn(1);
            univers.when(() -> Univers.getInt(100)).thenReturn(63);

            Flotte f = new Flotte("Gzav", new Position(1, 1, 1));
            Vaisseau v = new Vaisseau("scylla-vortex", "attA", 0);
            f.ajouterVaisseau(v); // devient la clé 0 (Flotte.numeroVaisseauDisponible)
            v.preparerAuCombat(true);

            assertNotEquals(0, v.getNombreArmesValides(),
                    "ce vaisseau a bien une arme spatiale valide : la téléportation testée ici n'a donc rien à "
                            + "voir avec le cas \"0 arme valide\" (couvert séparément)");
            assertEquals(0, v.getCapaciteMouvement(false),
                    "ce plan n'a aucun moteur (que l'arme) : la capacité de mouvement réelle est nulle — la "
                            + "téléportation ci-dessous se produit donc même pour un vaisseau qui ne peut, en "
                            + "théorie, pas bouger du tout ce round (Math.max(0, 0 - 2) = 0)");

            HashMap pos1 = new HashMap();
            Integer cle = 0;
            pos1.put(cle, new Position3D(new int[]{16, 17, 5})); // position initiale raisonnable, en portée

            Method mouvementVaisseau = Combat.class.getDeclaredMethod("mouvementVaisseau",
                    Object.class, Flotte.class, HashMap.class, HashMap.class, HashMap.class,
                    boolean.class, boolean.class);
            mouvementVaisseau.setAccessible(true);
            // fuite=true (stratégie "Fuyard" ou flotte trop faible), att=false (défenseur, peu importe ici)
            mouvementVaisseau.invoke(null, cle, f, pos1, new HashMap(), new HashMap(), true, false);

            Position3D apres = (Position3D) pos1.get(cle);
            assertEquals(Const.COMBAT_Y_FIN2, apres.getPos()[1],
                    "malgré une arme valide et une capacité de mouvement NULLE, le vaisseau en fuite est "
                            + "téléporté instantanément sur la sentinelle \"hors du champ de bataille\" (1020) : "
                            + "aucune arme du jeu n'a une portée suffisante pour retrouver la cible ensuite");
        }
    }
}
