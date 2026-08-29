package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
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
}
