package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caractérise un signalement utilisateur : combat en position 37-27 entre la
 * flotte "Gzav" (commandant Gzav, 2 vaisseaux "scylla-vortex", arme "canon à
 * plasma" portée 16 / dégâts coque 2 / dégâts bouclier 2) et la flotte "Bear
 * One" du commandant Snagha (44 vaisseaux, dont des "Fregate standard" à 5
 * composants "bouclier" chacune) — "0 dégâts" rapporté alors qu'un vrai combat
 * a eu lieu.
 *
 * Rejeu du combat réel (désérialisation directe de analyse/donneesT16/donnees/
 * comm.txt, la même flotte/le même commandant que ceux qui ont produit le
 * rapport signalé, avec le générateur aléatoire réel du jeu, sur plusieurs
 * essais) : à chaque tir de "Gzav" sur une "Fregate standard" de "Bear One",
 * le journal de combat affiche systématiquement "Dégâts: 0" — jamais une
 * seule fois un delta positif, alors que les vaisseaux de Snagha, eux,
 * infligent bien des dégâts non nuls à "Gzav" (qui n'a pas de bouclier). La
 * cause identifiée dans Vaisseau.effectuerDommages (ci-dessous) explique
 * exactement cette asymétrie.
 *
 * La cause : Vaisseau.effectuerDommages(Vaisseau cible, Arme a, ...) —
 * lorsque la cible a un bouclier valide (Vaisseau.getNumeroBouclierValide()
 * != -1), le dégât est bien appliqué (cible.ajouterDommagesBouclier(...)),
 * mais UNIQUEMENT dans la branche "else" (pas de bouclier valide) le compteur
 * dommagesEffectues de l'ATTAQUANT est incrémenté. Tant que la cible garde un
 * bouclier non saturé — ce qui est le cas ici : la flotte de Gzav ne compte
 * que 2 vaisseaux, répartis sur ~15 Fregates différentes de Bear One pendant
 * la durée du combat (bornée par Flotte.calculeCombativiteMoyenne, quelques
 * rounds), et chaque coup ne retire que 2 points d'un bouclier à 5
 * composants — aucun bouclier individuel ne se sature jamais sur toute la
 * durée du combat.
 *
 * Conséquence observable côté joueur : Combat.tirVaisseau n'appelle
 * Commandant.ajouterDegats(...) que si le delta de dommagesEffectues est
 * strictement positif (`if (degatsDuTir > 0)`) — cette statistique
 * (Commandant.degatsInfligesCeTour, utilisée comme score de victoire) reste
 * donc à 0 pour Gzav du début à la fin du combat, alors que ses tirs
 * touchent réellement leur cible (le bouclier encaisse bien des dégâts
 * cumulés, vérifiable sur Vaisseau.boucliers). Le rapport de combat vu par
 * le joueur (Flotte.listeVaisseauxParTypePourCombat, colonne "dommages
 * effectués") repose sur le même compteur : il affiche donc, lui aussi, "0
 * dégâts infligés" pour Gzav — un combat réel, mais silencieux, tant que
 * le bouclier de la cible n'est pas épuisé.
 *
 * Ce n'est PAS un défaut du plafonnement du bouclier lui-même (voir
 * BouclierDepassementCapaciteTest, qui couvre déjà l'absence de plafond sur
 * Vaisseau.ajouterDommagesBouclier) : c'est le fait que l'absorption par
 * bouclier ne remonte JAMAIS, même partiellement, dans le compteur de
 * dégâts infligés de l'attaquant — contrairement aux dégâts de coque, qui y
 * sont comptés au coup par coup.
 */
class CombatDegatsAbsorbesParBouclierTest {

    private final Map<String, PlanDeVaisseau> plans = new HashMap<>();

    // ---------------------------------------------------------------
    // Fixtures (mêmes conventions que CombatFlotteFlotteTest /
    // BouclierDepassementCapaciteTest : construction par réflexion pour
    // fixer nous-mêmes les valeurs numériques du scénario, sans dépendre
    // du chargement dynamique habituel des technologies).
    // ---------------------------------------------------------------

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object cible, Class<?> declarant, String nom) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        return (T) f.get(cible);
    }

    /** Arme "canon à plasma" simplifiée : dégâts coque ET bouclier, à longue portée (jamais hors de portée dans ce scénario). */
    private static Arme armeBouclierEtCoque(int dommagesBouclier, int dommagesCoque, int portee) throws Exception {
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

    /** Plan dont l'unique composant est l'arme fournie (comme "scylla-vortex" : que des armes). */
    private static PlanDeVaisseau planMonoArme(String nom, Arme arme) throws Exception {
        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom(nom);

        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"armeI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});

        return plan;
    }

    /** Plan dont l'unique composant est un bouclier de niveau donné, SANS arme (comme une "Fregate standard" désarmée : isole l'effet du bouclier, sans riposte à comptabiliser côté défenseur). */
    private static PlanDeVaisseau planBouclierSeul(String nom, int niveauBouclier) throws Exception {
        int[][] caracS = new int[][]{{Const.COMPOSANT_CAPACITE_BOUCLIER_MAGNETIQUE, niveauBouclier}};
        ComposantDeVaisseau bouclier = new ComposantDeVaisseau(
                "boucI", 0, null, 0, caracS, 0, 0f, null, "bouclier", 1);

        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom(nom);

        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"boucI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{bouclier});

        return plan;
    }

    private static void neutraliserJournalCombat() throws Exception {
        Field writer = Combat.class.getDeclaredField("writer");
        writer.setAccessible(true);
        writer.set(null, new BufferedWriter(new StringWriter()));
    }

    private Commandant commandantMinimal() {
        Commandant c = mock(Commandant.class);
        when(c.getStrategie(anyString())).thenReturn(Const.STRATEGIE_DEFAUT);
        when(c.numeroFlotte(any())).thenReturn(0);
        when(c.estJoueurNeutre()).thenReturn(false);
        when(c.estJoueurHumain()).thenReturn(false);
        when(c.getLocale()).thenReturn(Locale.FRENCH);
        return c;
    }

    // ---------------------------------------------------------------
    // Test 1 : au niveau du mécanisme élémentaire (Vaisseau.effectuerDommages)
    // ---------------------------------------------------------------

    @Test
    void effectuerDommages_cibleAvecBouclierNonSature_nIncrementeJamaisLesDommagesEffectuesDeLAttaquant() throws Exception {
        int niveauBouclier = 10_000; // jamais saturé sur les quelques coups du test
        int dommagesBouclierParTir = 2; // comme "canon à plasma" de scylla-vortex
        int dommagesCoqueParTir = 2;

        PlanDeVaisseau planCible = planBouclierSeul("CibleBouclieeSansArme", niveauBouclier);

        Arme arme = armeBouclierEtCoque(dommagesBouclierParTir, dommagesCoqueParTir, 30);
        Constructor<PlanDeVaisseau> ctorPlanAtt = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlanAtt.setAccessible(true);
        PlanDeVaisseau planAttaquant = ctorPlanAtt.newInstance();
        planAttaquant.setNom("AttaquantCanonPlasma");
        setField(planAttaquant, PlanDeVaisseau.class, "nbCases", 1);
        setField(planAttaquant, PlanDeVaisseau.class, "composants", new String[]{"armeI"});
        setField(planAttaquant, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});

        Map<String, PlanDeVaisseau> plansLocaux = new HashMap<>();
        plansLocaux.put("cibleA", planCible);
        plansLocaux.put("attA", planAttaquant);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plansLocaux.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);

            Vaisseau cible = new Vaisseau("Cible-Bouclier", "cibleA", 0);
            cible.preparerAuCombat(false);

            Vaisseau attaquant = new Vaisseau("Attaquant-Plasma", "attA", 0);
            attaquant.preparerAuCombat(true);

            Method effectuerDommages = Vaisseau.class.getDeclaredMethod(
                    "effectuerDommages", Vaisseau.class, Arme.class, Heros.class, Heros.class);
            effectuerDommages.setAccessible(true);

            int nbTirsSimules = 5;
            for (int i = 0; i < nbTirsSimules; i++)
                effectuerDommages.invoke(attaquant, cible, arme, Heros.HEROS_NON_PRESENT, Heros.HEROS_NON_PRESENT);

            int[] boucliersApres = getField(cible, Vaisseau.class, "boucliers");

            assertEquals(nbTirsSimules * dommagesBouclierParTir, boucliersApres[0],
                    "le bouclier a bien encaissé la totalité des " + nbTirsSimules + " tirs (aucun n'est passé à travers)");
            assertEquals(0, cible.nombreTotalPointsDeDommage(),
                    "aucun dégât de coque : tant que le bouclier reste valide, tout est absorbé");
            assertEquals(0, attaquant.getDommagesEffectues(),
                    nbTirsSimules + " tirs ont pourtant réellement touché et endommagé le bouclier (voir ci-dessus) : "
                            + "le compteur de dégâts infligés de L'ATTAQUANT — celui qui alimente le rapport de combat "
                            + "et le score de victoire (Commandant.degatsInfligesCeTour) — reste pourtant à 0, "
                            + "car Vaisseau.effectuerDommages ne l'incrémente que dans la branche \"pas de bouclier "
                            + "valide\", jamais dans la branche \"bouclier absorbe le coup\"");
        }
    }

    // ---------------------------------------------------------------
    // Test 2 : au niveau du combat complet (Combat.combatFlotteFlotte),
    // reproduisant la forme du cas réel rapporté (Gzav vs Bear One / Snagha,
    // position 37-27).
    // ---------------------------------------------------------------

    @Test
    void combatFlotteFlotte_cibleIntegralementBouclieeToutLeCombat_leCommandantAttaquantNAccumuleJamaisDeDegatsInfligesTraces() throws Exception {
        // Bouclier volontairement surdimensionné par rapport au nombre de rounds
        // possibles (Flotte.calculeCombativiteMoyenne borne le combat à quelques
        // rounds) : il ne doit jamais se saturer sur toute la durée du combat,
        // exactement comme dans le cas réel (2 vaisseaux de Gzav répartis sur
        // ~15 Fregates différentes de Bear One, aucune ne prend plus de 1-2
        // coups de 2 points de dégâts de bouclier chacun).
        Arme armeAttaquant = armeBouclierEtCoque(2, 2, 30);
        PlanDeVaisseau planAttaquant = planMonoArme("AttaquantBlinde", armeAttaquant);
        PlanDeVaisseau planDefenseur = planBouclierSeul("DefenseurBlinde", 10_000);

        plans.put("attA", planAttaquant);
        plans.put("defA", planDefenseur);

        Commandant c1 = commandantMinimal();
        Commandant c2 = commandantMinimal();

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true); // coups garantis : isole l'effet du bouclier, pas de la précision
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f1 = new Flotte("Gzav", new Position(1, 1, 1));
            f1.ajouterVaisseau(new Vaisseau("scylla-vortex", "attA", 0));

            Flotte f2 = new Flotte("BearOne", new Position(1, 1, 1));
            Vaisseau cible = new Vaisseau("Fregate-bouclee", "defA", 0);
            f2.ajouterVaisseau(cible);

            univers.when(() -> Univers.getCommandantFromFlotte(f1)).thenReturn(c1);
            univers.when(() -> Univers.getCommandantFromFlotte(f2)).thenReturn(c2);

            when(c1.getFlotte(0)).thenReturn(f1);
            when(c2.getFlotte(0)).thenReturn(f2);

            neutraliserJournalCombat();

            Combat.combatFlotteFlotte(c1, c2, 0, 0);

            int[] boucliersApres = getField(cible, Vaisseau.class, "boucliers");
            int totalAbsorbeParBouclier = Arrays.stream(boucliersApres).sum();

            assertTrue(totalAbsorbeParBouclier > 0,
                    "le bouclier de la cible a bien encaissé des tirs réels au fil du combat (coups garantis, portée "
                            + "suffisante) — sinon ce test ne démontrerait rien");
            assertEquals(1, f2.getNombreDeVaisseaux(),
                    "la cible n'a subi aucun dégât de coque de tout le combat : elle survit intacte, bouclier non saturé");
            verify(c1, never()).ajouterDegats(anyFloat());
            // C'est exactement le signalement utilisateur : un combat a bien eu lieu (des tirs réels ont touché et
            // endommagé le bouclier de la cible), mais le compteur de dégâts infligés de l'attaquant — celui que le
            // rapport de combat affiche au joueur — reste à 0 du premier au dernier round.
        }
    }
}
