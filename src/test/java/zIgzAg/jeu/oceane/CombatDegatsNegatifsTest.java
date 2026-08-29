package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reproduction d'un signalement utilisateur ("dégâts négatifs" en combat)
 * sans exemple concret fourni. Faute de repro, ce fichier explore par lecture
 * du code toutes les voies plausibles menant à une valeur de dégâts négative
 * dans les statistiques de combat, et vérifie empiriquement chacune d'elles —
 * qu'elle se confirme ou qu'elle se réfute. Les deux issues sont utiles :
 * une hypothèse réfutée évite de perdre du temps à ré-explorer la même piste
 * plus tard.
 *
 * Bilan de l'investigation :
 *
 *  1. HYPOTHÈSE RÉFUTÉE — débordement d'entier (overflow) de
 *     ConstructionPlanetaire.dommagesEffectues, jamais réinitialisé sur toute
 *     la durée de vie d'un bâtiment de défense (contrairement à
 *     Vaisseau.dommagesEffectues, remis à zéro chaque round). Le compteur lui
 *     -même devient bien négatif une fois débordé (vérifié), mais le calcul
 *     "après - avant" que fait Combat.tirDefensesPlanetaires reste
 *     mathématiquement correct malgré ce débordement (arithmétique en
 *     complément à deux : voir le commentaire du premier test). Cette voie ne
 *     produit donc PAS de dégâts négatifs visibles aujourd'hui.
 *
 *  2. HYPOTHÈSE CONFIRMÉE (avec une nuance importante) — ConstructionPlanetaire.
 *     tirArme (le tir d'un bâtiment de défense ou de la milice sur un
 *     vaisseau) ajoute les dégâts de coque/bouclier de l'arme à son propre
 *     compteur de statistiques SANS AUCUNE vérification de signe (`this.
 *     dommagesEffectues += dommageCoque`, à comparer avec Vaisseau.
 *     effectuerDommages qui protège la même opération par `if (degats > 0)`).
 *     Si l'arme d'une batterie ou de la milice a une caractéristique de
 *     dégâts négative — configuration de données incorrecte, mais rien dans
 *     le code ne l'empêche ni ne la détecte — chaque tir touché de cette
 *     arme fait directement baisser le compteur, sans même avoir besoin d'un
 *     débordement : Combat.tirDefensesPlanetaires calcule alors un delta
 *     négatif dès le premier tir.
 *
 *     Nuance : ce delta négatif N'ATTEINT PAS Commandant.ajouterDegats — le
 *     même `if (degatsDuTir > 0 && defenseur != null)` qui protège le
 *     compteur d'overflow (point 1) échoue AUSSI pour un delta négatif,
 *     donc le rejette tout simplement (aucun appel à ajouterDegats). La
 *     statistique persistée du commandant (Commandant.degatsInfligesCeTour,
 *     utilisée en score de victoire) ne devient donc PAS négative par cette
 *     voie. En revanche, la ligne SherilLogger juste avant ce test-là
 *     (`"[DEB-2.4] BATTERIES -> FLOTTE | ... Dégâts: %d ..."`) affiche
 *     `degatsDuTir` sans aucune garde : c'est LÀ, dans le journal de combat
 *     (data/logs/tourX.log en jeu réel), que le nombre négatif devient
 *     effectivement visible.
 *
 * Les deux niveaux de reproduction pour l'hypothèse confirmée (2) : un test
 * unitaire isolé sur ConstructionPlanetaire.tir (le delta négatif lui-même),
 * puis un test de bout en bout via Combat.combatFlottePlanete montrant à la
 * fois l'apparition du nombre négatif dans le journal SherilLogger et son
 * absence des statistiques du commandant.
 *
 *  3. HYPOTHÈSE RÉFUTÉE — combiner des attributs de Commandant, de Heros ou
 *     un composant "absorbeur" ne permet pas de produire des dégâts
 *     négatifs :
 *     - Les attributs de Heros/Gouverneur (attaque, défense, moral, vitesse,
 *       compétences) n'entrent QUE dans le calcul de la CHANCE de toucher
 *       (Vaisseau.reussiteTir / ConstructionPlanetaire.reussiteTir), jamais
 *       dans le montant des dégâts d'un coup au but (fixé par l'arme :
 *       getDommagesCoque/Bouclier/Sol, indépendant du tireur ou de la
 *       cible). Cette chance est explicitement plancherisée à 1 via
 *       `Univers.getTest(Math.max(1, test))` : aussi négatifs que soient les
 *       attributs du héros, la chance de toucher ne descend jamais en
 *       dessous de 1 (elle ne devient jamais négative elle-même), et un coup
 *       qui touche malgré tout inflige exactement les dégâts de base de
 *       l'arme, ni plus ni moins.
 *     - Aucune méthode du code ne modifie d'ailleurs JAMAIS ces attributs
 *       après la création d'un héros/gouverneur (Leader.setAttaque/
 *       setDefense/setMoral/setVitesse existent mais ne sont appelés nulle
 *       part dans le moteur) : dans le jeu tel qu'il tourne aujourd'hui, ces
 *       valeurs restent d'ailleurs toujours positives ou nulles (tirées de
 *       Univers.getInt(3) à la création).
 *     - Le composant "absorbeur" (Vaisseau.getCapaciteAbsorbtion, utilisé
 *       par dommagesApresAbsorbe) est encore mieux protégé : sa capacité
 *       est calculée par un maximum qui part de 0
 *       (PlanDeVaisseau.capaciteMaximaleCaracteristiqueSpeciale, `int retour
 *       = 0; ... retour = Math.max(valeur, retour);`), donc même un
 *       composant dont la donnée serait mal configurée avec une capacité
 *       d'absorption négative ne peut jamais faire redescendre
 *       Vaisseau.absorbeur sous 0 — contrairement aux dégâts d'arme (point
 *       2), qui eux n'ont aucun filet de sécurité de ce type.
 *
 *  4. HYPOTHÈSE CONFIRMÉE, PUIS CORRIGÉE — reproduisait un cas réel rapporté
 *     par l'utilisateur : rapport synthétique d'une attaque de 26
 *     Bombardiers Zwaia + 10 Grands Bombardiers Standard contre une planète
 *     (2169 milices, 6 mines) — "6 mines détruites ayant encaissé 120
 *     dégâts" côté planète, mais seulement "78 dégâts infligés" côté flotte
 *     assaillante. Ce n'était pas un cas de dégâts négatifs, mais un
 *     SOUS-COMPTAGE des dégâts infligés dès qu'un tir détruisait ou
 *     "overkill" sa cible.
 *
 *     Vaisseau.tirSurConstruction (bombardement d'un bâtiment/mine par un
 *     vaisseau) faisait, dans cet ordre :
 *     ```
 *     cibles[index].ajouterDommages(arme.getDommagesSol());              // 1. applique le dégât
 *     int dommagesActuel = Math.min(arme.getDommagesSol(),
 *             cibles[index].getPointsDeStructureRestants());             // 2. mesure le "restant"... APRÈS coup
 *     dommagesEffectues += dommagesActuel;
 *     ```
 *     `getPointsDeStructureRestants()` était lu APRÈS que le dégât avait
 *     déjà été appliqué à la cible (`ajouterDommages` en ligne 1), au lieu
 *     d'AVANT le coup — ce qui aurait donné la structure réellement
 *     disponible pour absorber ce tir précis. Un tir qui détruisait sa
 *     cible (ou l'"overkill", dégâts appliqués > structure restante avant
 *     coup) mesurait donc un "restant après coup" à 0 (ou presque), et
 *     `dommagesActuel` — donc la statistique `dommagesEffectues` de
 *     l'attaquant — sous-comptait ce coup, potentiellement jusqu'à 0, même
 *     si la cible avait bien reçu et enregistré (dans son propre champ
 *     `dommages`, utilisé par le rapport côté planète) la totalité du coup.
 *
 *     CORRIGÉ : `Vaisseau.tirSurConstruction` mesure désormais la structure
 *     restante AVANT d'appliquer le dégât (voir
 *     `doc/combat-comportements-non-documentes.md`, finding 11, pour le
 *     détail du correctif — y compris le correctif compagnon nécessaire sur
 *     `ConstructionPlanetaire.getPointsDeStructureRestants`, qui ne
 *     résolvait pas son `Batiment` sous-jacent avant de le déréférencer).
 *     Les deux tests ci-dessous vérifient maintenant le comportement
 *     attendu après correctif : `dommagesActuel` est plafonné à ce qu'il
 *     restait réellement à détruire, jamais tronqué à 0 sur un coup fatal.
 */
class CombatDegatsNegatifsTest {

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    private static Arme armeDeBatterie(int portee, int dommagesBouclier, int dommagesCoque) throws Exception {
        Constructor<Arme> ctor = Arme.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Arme arme = ctor.newInstance();

        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = dommagesBouclier;
        carac[Const.ARME_DOMMAGES_COQUE] = dommagesCoque;
        carac[Const.ARME_DOMMAGES_SOL] = 0;
        carac[Const.ARME_PORTEE] = portee;

        int[] chance = new int[10];
        for (int i = 0; i < chance.length; i++)
            chance[i] = 90;

        setField(arme, Arme.class, "caracteristiquesArmes", carac);
        setField(arme, Arme.class, "chanceToucher", chance);
        setField(arme, Arme.class, "typeArme", Const.CV_ARME_CS);
        setField(arme, ComposantDeVaisseau.class, "type", Const.CV_ARME);
        setField(arme, ComposantDeVaisseau.class, "nombreDeCasesPrises", 1);

        return arme;
    }

    private static Vaisseau vaisseauSansBouclier(String nom, PlanDeVaisseau plan, Map<String, PlanDeVaisseau> plans, String type) {
        plans.put(type, plan);
        return new Vaisseau(nom, type, 0);
    }

    private static PlanDeVaisseau planMonoArme(String nom, Arme arme) throws Exception {
        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom(nom);
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"coqueI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});
        return plan;
    }

    /** Batiment.getArme() résout codeArme + getRepresentationNiveau() du BATIMENT (pas de l'arme) ; niveau 0 => "I". */
    private static Batiment batimentDeBatterie(String code, String prefixeCodeArme) {
        return new Batiment(code, 0, null, 0, null, 0, 0f, null, 999, 0, prefixeCodeArme);
    }

    private static void neutraliserJournalCombat() throws Exception {
        Field writer = Combat.class.getDeclaredField("writer");
        writer.setAccessible(true);
        writer.set(null, new BufferedWriter(new StringWriter()));
    }

    // -----------------------------------------------------------------
    // 1. Débordement d'entier : réfuté comme source de dégâts négatifs
    // -----------------------------------------------------------------

    @Test
    void tirDeBatterie_compteurProcheDeIntegerMaxValue_leCompteurDevientNegatifMaisLeDeltaResteCorrect() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        Arme armeBatterie = armeDeBatterie(30, 10, 10);
        Arme armeCible = armeDeBatterie(30, 0, 0);
        PlanDeVaisseau planCible = planMonoArme("Cible", armeCible);

        ConstructionPlanetaire batterie = new ConstructionPlanetaire("battXX");

        // Simule une batterie ayant déjà accumulé, au fil de très nombreux
        // combats passés sur toute la durée de vie de la planète, la quasi
        // totalité de la plage représentable par un int : il ne lui manque
        // que 3 points de dégâts pour atteindre Integer.MAX_VALUE.
        setField(batterie, ConstructionPlanetaire.class, "dommagesEffectues", Integer.MAX_VALUE - 3);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            Batiment batimentBatterie = batimentDeBatterie("battXX", "canon");
            univers.when(() -> Univers.getTechnologie("battXX")).thenReturn(batimentBatterie);
            univers.when(() -> Univers.getTechnologie("canonI")).thenReturn(armeBatterie);

            Vaisseau cible = vaisseauSansBouclier("Cible-1", planCible, plans, "cibleA");
            // Initialise le tableau de boucliers du vaisseau : dans un
            // combat réel, c'est Flotte.preparerAuCombat qui s'en charge à
            // chaque round avant tout tir.
            cible.preparerAuCombat(true);

            int dommagesAvant = batterie.getDommagesEffectues();

            // Même calcul que Combat.tirDefensesPlanetaires : un coup au but
            // (tir à bout portant, cible sans bouclier -> dégâts de coque).
            batterie.tir(cible, Gouverneur.GOUVERNEUR_NON_PRESENT, Heros.HEROS_NON_PRESENT, true);

            int dommagesApres = batterie.getDommagesEffectues();
            int degatsDuTirCalculesParCombatJava = dommagesApres - dommagesAvant;

            assertTrue(dommagesApres < 0,
                    "le compteur \"dommagesEffectues\" de la batterie, jamais réinitialisé sur toute la durée "
                            + "de vie du bâtiment, déborde bien sur une valeur négative une fois le tir encaissé "
                            + "(dommagesAvant=" + dommagesAvant + ", dommagesApres=" + dommagesApres + ") — "
                            + "getDommagesEffectues() lirait un \"total de dégâts infligés\" négatif s'il était "
                            + "un jour affiché directement (il ne l'est actuellement nulle part).");

            // En arithmétique entière Java (complément à deux),
            // (avant + degats) - avant == degats (mod 2^32) même quand la somme
            // intermédiaire déborde : les deux débordements (l'addition, puis
            // la soustraction) s'annulent. Le calcul de
            // Combat.tirDefensesPlanetaires (delta "après - avant" pris à
            // chaque tir) est donc protégé contre UN SEUL franchissement de la
            // limite d'un int — ce n'est PAS la voie par laquelle des dégâts
            // négatifs peuvent apparaître dans les statistiques de combat
            // (voir les tests suivants pour la voie qui fonctionne réellement).
            assertTrue(degatsDuTirCalculesParCombatJava >= 0,
                    "le delta avant/après reste correct malgré le débordement du compteur "
                            + "(obtenu " + degatsDuTirCalculesParCombatJava + ", attendu "
                            + armeBatterie.getDommagesCoque() + ")");
        }
    }

    @Test
    void tirsRepetesDUneBatterie_traversantLeDebordement_chaqueDeltaIndividuelResteCorrect() throws Exception {
        // Réponse à la question : un combat plus long ou avec plus de
        // vaisseaux (donc plus de tirs) aggrave-t-il le débordement ?
        //
        // Non : Combat.tirDefensesPlanetaires capture `dommagesAvant`
        // immédiatement avant CHAQUE tir individuel, et ne compare jamais
        // qu'un "avant" à son "après" immédiat (un seul += entre les deux
        // mesures). Le nombre total de tirs déjà effectués — sur toute la
        // durée de vie du bâtiment, pas seulement dans le combat en cours —
        // n'entre pas en jeu : la propriété du complément à deux
        // ((avant + degats) - avant == degats mod 2^32) tient pour CHAQUE
        // mesure prise isolément, qu'elle ait lieu juste avant, pendant, ou
        // bien après le franchissement de Integer.MAX_VALUE. Ce test tire
        // deux douzaines de coups d'affilée sur la même batterie, à cheval
        // sur le débordement, et vérifie que chaque delta individuel reste
        // exact du premier au dernier coup.
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        int dommagesParTir = 7;
        Arme armeBatterie = armeDeBatterie(30, dommagesParTir, dommagesParTir);
        Arme armeCible = armeDeBatterie(30, 0, 0);
        // Cible capable d'encaisser toute la série de tirs sans être détruite
        // (sinon ConstructionPlanetaire.tir n'a plus aucun effet sur elle) :
        // on donne à son unique composant une résistance largement
        // supérieure au cumul des dégâts de la série.
        setField(armeCible, ComposantDeVaisseau.class, "nombreDeCasesPrises", 100_000);
        PlanDeVaisseau planCible = planMonoArme("Cible resistante", armeCible);

        ConstructionPlanetaire batterie = new ConstructionPlanetaire("battXX");
        // Départ volontairement proche du débordement, pour que la série de
        // tirs le traverse en cours de route.
        setField(batterie, ConstructionPlanetaire.class, "dommagesEffectues", Integer.MAX_VALUE - 50);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            Batiment batimentBatterie = batimentDeBatterie("battXX", "canon");
            univers.when(() -> Univers.getTechnologie("battXX")).thenReturn(batimentBatterie);
            univers.when(() -> Univers.getTechnologie("canonI")).thenReturn(armeBatterie);

            Vaisseau cible = vaisseauSansBouclier("Cible-1", planCible, plans, "cibleA");
            cible.preparerAuCombat(true);

            int nombreDeTirs = 24; // traverse largement le débordement (50/7 ≈ 8 tirs suffiraient)
            for (int i = 1; i <= nombreDeTirs; i++) {
                int dommagesAvant = batterie.getDommagesEffectues();
                batterie.tir(cible, Gouverneur.GOUVERNEUR_NON_PRESENT, Heros.HEROS_NON_PRESENT, true);
                int delta = batterie.getDommagesEffectues() - dommagesAvant;

                assertTrue(delta == dommagesParTir,
                        "tir n°" + i + "/" + nombreDeTirs + " : delta attendu " + dommagesParTir
                                + ", obtenu " + delta + " (dommagesAvant=" + dommagesAvant
                                + ", dommagesApres=" + batterie.getDommagesEffectues() + ") — "
                                + "le nombre de tirs déjà encaissés ne doit pas affecter la mesure individuelle");
            }
        }
    }

    // -----------------------------------------------------------------
    // 2. Arme de défense mal configurée (dégâts négatifs en données) :
    //    confirmé comme source directe de dégâts négatifs, sans overflow
    // -----------------------------------------------------------------

    @Test
    void tirDeBatterie_armeAvecDegatsDeCoqueNegatifs_produitUnDeltaDeDegatsNegatifImmediat() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        // Arme de batterie mal configurée : dégâts de coque négatifs. Rien
        // dans Arme, ConstructionPlanetaire ou Combat ne valide le signe
        // d'une caractéristique d'arme lue depuis les données du jeu.
        Arme armeBatterieMalConfiguree = armeDeBatterie(30, 10, -20);
        Arme armeCible = armeDeBatterie(30, 0, 0);
        PlanDeVaisseau planCible = planMonoArme("Cible", armeCible);

        ConstructionPlanetaire batterie = new ConstructionPlanetaire("battXX");

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            Batiment batimentBatterie = batimentDeBatterie("battXX", "canon");
            univers.when(() -> Univers.getTechnologie("battXX")).thenReturn(batimentBatterie);
            univers.when(() -> Univers.getTechnologie("canonI")).thenReturn(armeBatterieMalConfiguree);

            Vaisseau cible = vaisseauSansBouclier("Cible-1", planCible, plans, "cibleA");
            // Initialise le tableau de boucliers du vaisseau : dans un
            // combat réel, c'est Flotte.preparerAuCombat qui s'en charge à
            // chaque round avant tout tir.
            cible.preparerAuCombat(true);

            int dommagesAvant = batterie.getDommagesEffectues();

            batterie.tir(cible, Gouverneur.GOUVERNEUR_NON_PRESENT, Heros.HEROS_NON_PRESENT, true);

            int dommagesApres = batterie.getDommagesEffectues();
            int degatsDuTir = dommagesApres - dommagesAvant;

            assertTrue(degatsDuTir < 0,
                    "ConstructionPlanetaire.tirArme fait `this.dommagesEffectues += dommageCoque` sans jamais "
                            + "vérifier que dommageCoque est positif (contrairement à Vaisseau.effectuerDommages, "
                            + "qui protège la même opération par `if (degats > 0)`) : un coup au but avec une arme "
                            + "mal configurée (dégâts de coque négatifs) fait directement baisser le compteur de la "
                            + "batterie, dès le premier tir, sans avoir besoin d'un débordement (delta obtenu : "
                            + degatsDuTir + ")");
            // Effet de bord constaté : le vaisseau visé ne subit en revanche
            // aucun dégât réel (Vaisseau.ajouterDommagesAuHasard(nb) avec
            // nb négatif ne fait tourner aucune itération de sa boucle
            // `for (i=0; i<nb; i++)` et renvoie true sans rien endommager) —
            // la cible est intacte, seul le compteur de la batterie est faux.
            assertTrue(!cible.estDetruit(), "la cible n'est pas réellement endommagée par ce tir");
        }
    }

    @Test
    void combatFlottePlanete_batterieAvecArmeMalConfiguree_infligeDesDegatsNegatifsAuCommandantDefenseur() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        Map<String, Object> technologies = new HashMap<>();

        Arme armeBatterieMalConfiguree = armeDeBatterie(30, 10, -20);
        Batiment batimentBatterie = batimentDeBatterie("battXX", "canon");
        technologies.put("battXX", batimentBatterie);
        technologies.put("canonI", armeBatterieMalConfiguree);

        // La milice (Combat.tirMilicesPlanetaires) tire aussi chaque round,
        // indépendamment des bâtiments de la planète : il faut résoudre son
        // "battlaI" même si le scénario ne porte que sur la batterie. Arme
        // neutre (aucun dégât) pour ne pas polluer les valeurs capturées.
        Arme armeMiliceNeutre = armeDeBatterie(30, 0, 0);
        Batiment batimentMilice = batimentDeBatterie("battlaI", "milice");
        technologies.put("battlaI", batimentMilice);
        technologies.put("miliceI", armeMiliceNeutre);

        Arme armeVaisseau = armeDeBatterie(30, 10, 10);
        PlanDeVaisseau planVaisseau = planMonoArme("Vaisseau attaquant", armeVaisseau);
        plans.put("attA", planVaisseau);

        Commandant c1 = mock(Commandant.class);
        when(c1.getStrategie(anyString())).thenReturn(Const.STRATEGIE_DEFAUT);
        when(c1.numeroFlotte(any())).thenReturn(0);
        when(c1.estJoueurNeutre()).thenReturn(false);
        when(c1.estJoueurHumain()).thenReturn(false);
        when(c1.getLocale()).thenReturn(Locale.FRENCH);
        when(c1.getNumero()).thenReturn(1);
        when(c1.getPossession(any())).thenReturn(mock(Possession.class));

        Commandant c2 = mock(Commandant.class);
        when(c2.getStrategie(anyString())).thenReturn(Const.STRATEGIE_DEFAUT);
        when(c2.estJoueurNeutre()).thenReturn(false);
        when(c2.estJoueurHumain()).thenReturn(false);
        when(c2.getLocale()).thenReturn(Locale.FRENCH);
        when(c2.getNumero()).thenReturn(2);
        when(c2.getPossession(any())).thenReturn(mock(Possession.class));

        ConstructionPlanetaire batterie = new ConstructionPlanetaire("battXX");

        Planete planete = mock(Planete.class);
        List<ConstructionPlanetaire> batiments = new ArrayList<>(java.util.List.of(batterie));
        when(planete.populationTotale()).thenReturn(500);
        when(planete.getStabilite()).thenReturn(100);
        when(planete.getBatiments()).thenAnswer(inv -> batiments.toArray(new ConstructionPlanetaire[0]));
        when(planete.listeEquipementsNombresDommages()).thenReturn(new HashMap<>());
        org.mockito.Mockito.doAnswer(inv -> {
            batiments.removeIf(ConstructionPlanetaire::estDetruit);
            return null;
        }).when(planete).eliminerPertesBatiments();

        Systeme s = mock(Systeme.class);
        when(s.getPosition()).thenReturn(new Position(1, 1, 1));
        when(s.getPlanete(0)).thenReturn(planete);
        when(s.getNombrePlanetes()).thenReturn(1);
        when(s.getNomNumeroPlanete(anyInt())).thenReturn("Planete-test");
        when(s.estProprio(anyInt())).thenReturn(true);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie(anyString()))
                    .thenAnswer(inv -> technologies.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f = new Flotte("Attaque", new Position(1, 1, 1));
            f.ajouterVaisseau(new Vaisseau("V1", "attA", 0));
            f.ajouterVaisseau(new Vaisseau("V2", "attA", 0));
            f.ajouterVaisseau(new Vaisseau("V3", "attA", 0));
            f.setDirective(Const.DIRECTIVE_FLOTTE_ATTAQUE_PLANETE);

            when(c1.getFlotte(0)).thenReturn(f);

            neutraliserJournalCombat();

            List<String> messagesJournalises = new ArrayList<>();
            java.util.logging.Handler capteur = new java.util.logging.Handler() {
                public void publish(java.util.logging.LogRecord record) {
                    messagesJournalises.add(record.getMessage());
                }
                public void flush() {}
                public void close() {}
            };
            java.util.logging.Logger.getLogger("Sheril").addHandler(capteur);
            try {
                Combat.combatFlottePlanete(c1, 0, c2, s, 0, 0, null);
            } finally {
                java.util.logging.Logger.getLogger("Sheril").removeHandler(capteur);
            }

            // Combat.tirDefensesPlanetaires ne fait `defenseur.ajouterDegats(degatsDuTir)`
            // que si `degatsDuTir > 0` : un delta négatif échoue AUSSI ce test
            // (`> 0`), donc il n'est jamais transmis à Commandant — la
            // statistique persistée du commandant, elle, ne devient pas
            // négative par cette voie.
            verify(c2, org.mockito.Mockito.never()).ajouterDegats(org.mockito.ArgumentMatchers.floatThat(v -> v < 0));

            // En revanche, RIEN ne protège la ligne de journalisation
            // SherilLogger juste avant ce test : elle affiche `degatsDuTir`
            // tel quel, inconditionnellement. C'est là que le nombre négatif
            // devient visible — dans le journal de combat (data/logs/tourX.log
            // en jeu réel), pas dans les statistiques du commandant.
            boolean journalMontreDesDegatsNegatifs = messagesJournalises.stream()
                    .anyMatch(m -> m.contains("Dégâts: -"));
            assertTrue(journalMontreDesDegatsNegatifs,
                    "une arme de batterie mal configurée (dégâts de coque négatifs) doit apparaître comme "
                            + "\"Dégâts: -20\" dans le journal SherilLogger de Combat.tirDefensesPlanetaires, "
                            + "qui n'applique aucune garde de signe contrairement à Commandant.ajouterDegats : "
                            + "messages journalisés = " + messagesJournalises);
        }
    }

    // -----------------------------------------------------------------
    // 3. Attributs de Commandant/Heros et composant absorbeur : réfuté
    //    comme source de dégâts négatifs
    // -----------------------------------------------------------------

    @Test
    void heroAvecAttaqueEtDefenseTresNegatives_neRendJamaisLesDegatsNegatifsEtPlancheLaChanceA1() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        int dommagesCoque = 10;
        Arme armeAttaquant = armeDeBatterie(30, 0, dommagesCoque);
        Arme armeCible = armeDeBatterie(30, 0, 0);
        setField(armeCible, ComposantDeVaisseau.class, "nombreDeCasesPrises", 100_000);
        PlanDeVaisseau planAttaquant = planMonoArme("Attaquant", armeAttaquant);
        PlanDeVaisseau planCible = planMonoArme("Cible resistante", armeCible);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            // getTest toujours vrai : on force le coup à toucher, pour
            // observer si l'attribut négatif du héros a une influence sur le
            // MONTANT des dégâts (il ne doit pas en avoir).
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));

            Vaisseau attaquant = vaisseauSansBouclier("Attaquant-1", planAttaquant, plans, "attA");
            Vaisseau cible = vaisseauSansBouclier("Cible-1", planCible, plans, "cibleA");
            attaquant.preparerAuCombat(true);
            cible.preparerAuCombat(true);

            // Héros du tireur avec des attributs extrêmement défavorables —
            // aucun chemin de jeu normal ne permet de les rendre négatifs
            // (Leader.setAttaque/setDefense ne sont appelés nulle part dans
            // le moteur), mais rien dans le code ne l'empêche non plus :
            // testons donc directement le cas limite.
            Heros heroTresFaible = new Heros("Faible", new int[0][0], 0, -1000, -1000, 0, 0, 0, 0);

            int dommagesAvant = attaquant.getDommagesEffectues();
            attaquant.tir(cible, 0, heroTresFaible, Heros.HEROS_NON_PRESENT);
            int degatsDuTir = attaquant.getDommagesEffectues() - dommagesAvant;

            ArgumentCaptor<Integer> chanceCaptee = ArgumentCaptor.forClass(Integer.class);
            univers.verify(() -> Univers.getTest(chanceCaptee.capture()), org.mockito.Mockito.atLeastOnce());

            assertTrue(degatsDuTir == dommagesCoque,
                    "l'attaque/défense du héros ne doit influencer QUE la chance de toucher, jamais le montant "
                            + "des dégâts d'un coup au but : attendu " + dommagesCoque + ", obtenu " + degatsDuTir);
            assertTrue(chanceCaptee.getAllValues().stream().allMatch(c -> c >= 1),
                    "Vaisseau.reussiteTir plancherise la chance à Math.max(1, test) : même avec un héros aux "
                            + "attributs extrêmement négatifs, la valeur passée à Univers.getTest ne descend "
                            + "jamais sous 1 (valeurs observées : " + chanceCaptee.getAllValues() + ")");
        }
    }

    @Test
    void composantAbsorbeurAvecCapaciteMalConfigureeEnNegatif_neDescendJamaisSous0() throws Exception {
        // Même si un composant "absorbeur" avait, par erreur de données, une
        // capacité d'absorption négative, PlanDeVaisseau.
        // capaciteMaximaleCaracteristiqueSpeciale calcule cette capacité par
        // un maximum qui part de 0 : contrairement aux dégâts d'arme (test
        // de la section 2), ce mécanisme a donc déjà un filet de sécurité
        // intégré, sans qu'aucune donnée de jeu réelle n'ait de valeur
        // négative pour cette caractéristique (voir ListeCaracSpeciales.java
        // : absorbI..absorbX vont tous de 1 à 25).
        ComposantDeVaisseau absorbeurMalConfigure = new ComposantDeVaisseau(
                "absorbXX", 0, null, 0,
                new int[][]{{Const.COMPOSANT_CAPACITE_ABSORBTION, -50}},
                0, 0f, null, "coque", 1);

        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom("Vaisseau avec absorbeur corrompu");
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"absorbXXI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{absorbeurMalConfigure});

        assertTrue(plan.getCapaciteAbsorbtion() == 0,
                "une capacité d'absorption négative dans les données ne doit jamais se traduire par une capacité "
                        + "effective négative : obtenu " + plan.getCapaciteAbsorbtion());
    }

    // -----------------------------------------------------------------
    // 4. Sous-comptage des dégâts infligés sur un coup fatal/surpuissant :
    //    CORRIGÉ (voir doc/combat-comportements-non-documentes.md, finding 11)
    //    — tests de non-régression sur le comportement attendu après fix.
    // -----------------------------------------------------------------

    private static Arme armeBombardier(int portee, int dommagesSol) throws Exception {
        Constructor<Arme> ctor = Arme.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Arme arme = ctor.newInstance();

        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = 0;
        carac[Const.ARME_DOMMAGES_COQUE] = 0;
        carac[Const.ARME_DOMMAGES_SOL] = dommagesSol;
        carac[Const.ARME_PORTEE] = portee;

        int[] chance = new int[10];
        for (int i = 0; i < chance.length; i++)
            chance[i] = 90;

        setField(arme, Arme.class, "caracteristiquesArmes", carac);
        setField(arme, Arme.class, "chanceToucher", chance);
        setField(arme, Arme.class, "typeArme", Const.CV_ARME_CP);
        setField(arme, ComposantDeVaisseau.class, "type", Const.CV_ARME);
        setField(arme, ComposantDeVaisseau.class, "nombreDeCasesPrises", 1);

        return arme;
    }

    @Test
    void tirSurConstruction_coupQuiDetruitLaCible_comptabiliseLaStructureReellementConsommee() throws Exception {
        // Même scénario que le cas rapporté : une mine (points de structure
        // = 20, comme 120 dégâts / 6 mines détruites dans le rapport de
        // l'utilisateur) déjà endommagée à 15/20, visée par un bombardier
        // dont l'arme inflige 20 points de dégâts au sol — largement de quoi
        // la détruire d'un coup ("overkill" de 15 points : 15+20=35 > 20 de
        // structure).
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        int pointsDeStructureMine = 20;
        int dommagesDejaSubis = 15;
        int dommagesDuTir = 20;
        int structureRestanteAvantLeCoup = pointsDeStructureMine - dommagesDejaSubis; // 5

        Arme armeBombardier = armeBombardier(30, dommagesDuTir);
        PlanDeVaisseau planBombardier = planMonoArme("Bombardier", armeBombardier);
        plans.put("bombA", planBombardier);

        Batiment batimentMine = new Batiment("mineI", 0, null, 0, null, 0, 0f, null, pointsDeStructureMine, 0, null);
        ConstructionPlanetaire mine = new ConstructionPlanetaire("mineI");
        setField(mine, ConstructionPlanetaire.class, "dommages", dommagesDejaSubis);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie("mineI")).thenReturn(batimentMine);

            Vaisseau bombardier = new Vaisseau("Bombardier-1", "bombA", 0);
            bombardier.preparerAuCombat(false);

            int degatsInfliges = bombardier.tirSurConstruction(
                    new ConstructionPlanetaire[]{mine}, Heros.HEROS_NON_PRESENT, Gouverneur.GOUVERNEUR_NON_PRESENT, true);

            assertTrue(mine.getDommages() == dommagesDejaSubis + dommagesDuTir,
                    "la mine reçoit toujours la totalité du coup dans son propre compteur de dégâts, comme avant "
                            + "le correctif : attendu " + (dommagesDejaSubis + dommagesDuTir) + ", obtenu "
                            + mine.getDommages());
            assertTrue(mine.estDetruit(), "la mine doit être détruite (35 > 20 points de structure)");

            assertTrue(degatsInfliges == structureRestanteAvantLeCoup,
                    "après le correctif, Vaisseau.tirSurConstruction mesure la structure restante AVANT "
                            + "d'appliquer le dégât : dommagesActuel est plafonné à ce qu'il restait réellement à "
                            + "détruire (5), pas tronqué à 0 comme avant le correctif ni égal aux 20 points bruts "
                            + "de l'arme (le surplus de 15 points d'overkill n'est légitimement pas compté) — "
                            + "attendu " + structureRestanteAvantLeCoup + ", obtenu " + degatsInfliges);
        }
    }

    @Test
    void tirSurConstruction_flotteDeBombardiers_totalInfligeEgaleStructureTotaleDetruite() throws Exception {
        // Variante à l'échelle d'une petite flotte : le total des dégâts
        // infligés déclarés par plusieurs bombardiers successifs doit
        // désormais correspondre exactement à la structure totale détruite,
        // y compris sur le tir fatal.
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        int pointsDeStructureMine = 20;
        int dommagesParTir = 7;

        Arme armeBombardier = armeBombardier(30, dommagesParTir);
        PlanDeVaisseau planBombardier = planMonoArme("Bombardier", armeBombardier);
        plans.put("bombA", planBombardier);

        Batiment batimentMine = new Batiment("mineI", 0, null, 0, null, 0, 0f, null, pointsDeStructureMine, 0, null);
        ConstructionPlanetaire mine = new ConstructionPlanetaire("mineI");

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie("mineI")).thenReturn(batimentMine);

            int totalInflige = 0;
            // 3 tirs de 7 : 7, 14, 21 points cumulés sur une mine à 20 de
            // structure -> le 3e tir la détruit avec un surplus de 1 point
            // (7 bruts, mais seuls 6 points restaient à détruire).
            for (int i = 1; i <= 3 && !mine.estDetruit(); i++) {
                Vaisseau bombardier = new Vaisseau("Bombardier-" + i, "bombA", 0);
                bombardier.preparerAuCombat(false);
                totalInflige += bombardier.tirSurConstruction(
                        new ConstructionPlanetaire[]{mine}, Heros.HEROS_NON_PRESENT, Gouverneur.GOUVERNEUR_NON_PRESENT, true);
            }

            assertTrue(mine.estDetruit(), "3 tirs de 7 doivent détruire une mine à 20 points de structure (21 > 20)");
            assertTrue(mine.getDommages() == 3 * dommagesParTir,
                    "la mine encaisse toujours la totalité des 3 tirs bruts dans son propre compteur : attendu "
                            + (3 * dommagesParTir) + ", obtenu " + mine.getDommages());
            assertTrue(totalInflige == pointsDeStructureMine,
                    "après le correctif, le cumul des dégâts infligés déclarés (7 + 7 + 6, le dernier tir plafonné "
                            + "aux 6 points qu'il restait à détruire plutôt que ses 7 points bruts) correspond "
                            + "exactement à la structure totale de la mine détruite : attendu " + pointsDeStructureMine
                            + ", obtenu " + totalInflige);
        }
    }
}
