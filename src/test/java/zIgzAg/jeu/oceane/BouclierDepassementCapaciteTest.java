package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Vérifie l'hypothèse formulée en réponse à "est-ce que le bug identifié
 * (ConstructionPlanetaire.dommages jamais plafonné, cf. doc/fix/
 * plafonnement-dommages-constructions-planetaires.md) peut se reproduire à
 * d'autres endroits" : Vaisseau.ajouterDommagesBouclier présente le même
 * défaut de fond (`boucliers[numBouc] = boucliers[numBouc] + dommages`, sans
 * plafond), mais avec une portée très différente. Contrairement au cas des
 * mines (liste de cibles figée en tête de round, permettant une accumulation
 * illimitée sur toute une salve), le bouclier ciblé est réévalué à l'état
 * réel à chaque tir via Vaisseau.getNumeroBouclierValide() — il ne s'agit
 * donc PAS d'une accumulation illimitée, mais d'un dépassement borné à, au
 * plus, les dégâts bruts d'UN SEUL coup.
 *
 * Ce test n'utilise aucune réflexion pour forcer un état de dégâts : il
 * construit un vaisseau cible avec un plan réel (un bouclier de faible
 * niveau) et appelle directement les deux méthodes de production réellement
 * utilisées en combat (Vaisseau.getNumeroBouclierValide /
 * Vaisseau.ajouterDommagesBouclier, voir Vaisseau.effectuerDommages et
 * ConstructionPlanetaire.tirArme pour leurs appelants réels).
 */
class BouclierDepassementCapaciteTest {

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

    @Test
    void unSeulCoup_bouclierDeFaibleNiveau_peutDepasserSonPlafondTheorique() throws Exception {
        int niveauBouclier = 5;
        int dommagesBouclierDuTir = 30; // un seul coup, bien au-dessus du niveau du bouclier

        int[][] caracS = new int[][]{{Const.COMPOSANT_CAPACITE_BOUCLIER_MAGNETIQUE, niveauBouclier}};
        ComposantDeVaisseau bouclier = new ComposantDeVaisseau(
                "boucI", 0, null, 0, caracS, 0, 0f, null, "bouclier", 1);

        Constructor<PlanDeVaisseau> ctorPlan = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlan.setAccessible(true);
        PlanDeVaisseau plan = ctorPlan.newInstance();
        plan.setNom("CibleBlindee");
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"boucI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{bouclier});

        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        plans.put("cibleA", plan);

        int[] boucliersApres;
        int b;
        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));

            Vaisseau cible = new Vaisseau("Cible-1", "cibleA", 0);
            cible.preparerAuCombat(false); // initialise boucliers[] (un seul slot, à 0)

            b = cible.getNumeroBouclierValide();
            assertTrue(b != -1, "un bouclier neuf (0 dégât encaissé) doit être proposé comme cible valide");

            // Appel direct de la méthode de production réellement utilisée par
            // Vaisseau.effectuerDommages (ship-vs-ship) et ConstructionPlanetaire.
            // tirArme (défenses planétaires vs flotte) — aucun état forcé par
            // réflexion, uniquement le mécanisme réel.
            cible.ajouterDommagesBouclier(b, dommagesBouclierDuTir);

            boucliersApres = getField(cible, Vaisseau.class, "boucliers");
        }

        assertTrue(boucliersApres[b] > niveauBouclier,
                "un seul coup dont les dégâts de bouclier bruts (" + dommagesBouclierDuTir + ") dépassent le "
                        + "niveau restant du bouclier (" + niveauBouclier + ") le fait dépasser sans plafond : "
                        + "attendu > " + niveauBouclier + ", obtenu " + boucliersApres[b]);
        assertEquals(dommagesBouclierDuTir, boucliersApres[b],
                "aucun plafonnement n'est appliqué : la valeur stockée est exactement le dégât brut de l'arme, "
                        + "pas bornée au niveau du bouclier (" + niveauBouclier + ")");
    }

    @Test
    void coupSuivant_bouclierDejaSature_estRouteVersUneAutreCibleReevalueeEnDirect() throws Exception {
        // Contre-vérification de la borne : contrairement au cas des mines
        // (liste de cibles figée en tête de round), un second coup après
        // saturation du bouclier ne continue PAS d'accumuler sur le même
        // bouclier : getNumeroBouclierValide() réévalue l'état réel et ne
        // propose plus ce bouclier une fois son niveau dépassé.
        int niveauBouclier = 5;
        int dommagesBouclierDuTir = 30;

        int[][] caracS = new int[][]{{Const.COMPOSANT_CAPACITE_BOUCLIER_MAGNETIQUE, niveauBouclier}};
        ComposantDeVaisseau bouclier = new ComposantDeVaisseau(
                "boucI", 0, null, 0, caracS, 0, 0f, null, "bouclier", 1);

        Constructor<PlanDeVaisseau> ctorPlan = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlan.setAccessible(true);
        PlanDeVaisseau plan = ctorPlan.newInstance();
        plan.setNom("CibleBlindeeMonoBouclier");
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"boucI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{bouclier});

        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        plans.put("cibleA", plan);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));

            Vaisseau cible = new Vaisseau("Cible-2", "cibleA", 0);
            cible.preparerAuCombat(false);

            int premier = cible.getNumeroBouclierValide();
            cible.ajouterDommagesBouclier(premier, dommagesBouclierDuTir);

            int second = cible.getNumeroBouclierValide();
            assertEquals(-1, second,
                    "un seul bouclier existe sur ce plan et il est désormais saturé (30 > 5) : aucun bouclier "
                            + "valide ne doit plus être proposé pour un second coup (il serait routé vers la coque, "
                            + "pas vers un nouvel empilement sur ce même bouclier) — pas d'accumulation illimitée "
                            + "possible contrairement au cas des mines corrigé dans doc/fix/plafonnement-dommages-"
                            + "constructions-planetaires.md");
        }
    }

    private static Arme armeBouclierEtCoque(int dommagesBouclier, int dommagesCoque) throws Exception {
        Constructor<Arme> ctorArme = Arme.class.getDeclaredConstructor();
        ctorArme.setAccessible(true);
        Arme arme = ctorArme.newInstance();
        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = dommagesBouclier;
        carac[Const.ARME_DOMMAGES_COQUE] = dommagesCoque;
        carac[Const.ARME_DOMMAGES_SOL] = 0;
        carac[Const.ARME_PORTEE] = 30;
        int[] chance = new int[10];
        for (int i = 0; i < chance.length; i++) chance[i] = 90;
        setField(arme, Arme.class, "caracteristiquesArmes", carac);
        setField(arme, Arme.class, "chanceToucher", chance);
        setField(arme, Arme.class, "typeArme", Const.CV_ARME_CS);
        setField(arme, ComposantDeVaisseau.class, "type", Const.CV_ARME);
        setField(arme, ComposantDeVaisseau.class, "nombreDeCasesPrises", 1);
        return arme;
    }

    @Test
    void coupQuiSatureLeBouclier_nAppliqueAucunDegatDeCoqueEnSpillover() throws Exception {
        // Vérifie l'implication soulignée par l'utilisateur : le coup qui sature
        // (ou "détruit") le bouclier est intégralement absorbé par celui-ci — le
        // if/else de Vaisseau.effectuerDommages est strictement exclusif, il
        // n'existe aucun chemin qui répartit un même coup entre bouclier et
        // coque. La coque doit rester totalement intacte après ce coup, quel que
        // soit l'écart entre le dégât brut de l'arme (30) et la capacité
        // résiduelle du bouclier (5).
        int niveauBouclier = 5;
        int dommagesBouclierDuTir = 30;
        int dommagesCoqueDeLaMemeArme = 15; // ne doit JAMAIS s'appliquer sur ce coup

        int[][] caracS = new int[][]{{Const.COMPOSANT_CAPACITE_BOUCLIER_MAGNETIQUE, niveauBouclier}};
        ComposantDeVaisseau bouclier = new ComposantDeVaisseau(
                "boucI", 0, null, 0, caracS, 0, 0f, null, "bouclier", 1);

        Constructor<PlanDeVaisseau> ctorPlanCible = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlanCible.setAccessible(true);
        PlanDeVaisseau planCible = ctorPlanCible.newInstance();
        planCible.setNom("CibleBlindeeSpillover");
        setField(planCible, PlanDeVaisseau.class, "nbCases", 1);
        setField(planCible, PlanDeVaisseau.class, "composants", new String[]{"boucI"});
        setField(planCible, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{bouclier});

        Arme arme = armeBouclierEtCoque(dommagesBouclierDuTir, dommagesCoqueDeLaMemeArme);
        Constructor<PlanDeVaisseau> ctorPlanAtt = PlanDeVaisseau.class.getDeclaredConstructor();
        ctorPlanAtt.setAccessible(true);
        PlanDeVaisseau planAttaquantReel = ctorPlanAtt.newInstance();
        planAttaquantReel.setNom("AttaquantSpillover");
        setField(planAttaquantReel, PlanDeVaisseau.class, "nbCases", 1);
        setField(planAttaquantReel, PlanDeVaisseau.class, "composants", new String[]{"armeI"});
        setField(planAttaquantReel, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});

        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        plans.put("cibleA", planCible);
        plans.put("attA", planAttaquantReel);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);

            Vaisseau cible = new Vaisseau("Cible-3", "cibleA", 0);
            cible.preparerAuCombat(false);

            Vaisseau attaquant = new Vaisseau("Attaquant-1", "attA", 0);
            attaquant.preparerAuCombat(true);

            Method effectuerDommages = Vaisseau.class.getDeclaredMethod(
                    "effectuerDommages", Vaisseau.class, Arme.class, Heros.class, Heros.class);
            effectuerDommages.setAccessible(true);
            effectuerDommages.invoke(attaquant, cible, arme, Heros.HEROS_NON_PRESENT, Heros.HEROS_NON_PRESENT);

            int[] boucliersApres = getField(cible, Vaisseau.class, "boucliers");
            assertEquals(dommagesBouclierDuTir, boucliersApres[0],
                    "le bouclier a bien encaissé la totalité du coup, dépassant son niveau (" + niveauBouclier + ")");

            assertEquals(0, cible.nombreTotalPointsDeDommage(),
                    "aucun dégât de coque ne doit avoir été appliqué : le coup qui sature le bouclier est "
                            + "intégralement absorbé par celui-ci (branchement if/else exclusif dans "
                            + "Vaisseau.effectuerDommages), il n'y a pas de répartition partielle même quand "
                            + "les dégâts bruts (" + dommagesBouclierDuTir + ") dépassent largement la capacité "
                            + "résiduelle du bouclier (" + niveauBouclier + ")");
        }
    }
}
