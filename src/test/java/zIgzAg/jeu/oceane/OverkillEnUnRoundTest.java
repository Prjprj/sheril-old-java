package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Reproduit le mécanisme exact de Combat.tirAirSol : dans un round, la liste
 * "cibles" (bâtiments encore vivants au DÉBUT du round) est construite une
 * seule fois, puis chaque vaisseau tire sur un index choisi au hasard dans
 * cette liste (Univers.getInt(cibles.length)), sans jamais vérifier
 * cibles[index].estDetruit() avant de tirer — p.eliminerPertesBatiments()
 * n'est appelé qu'une seule fois, après que tous les vaisseaux du round
 * aient tiré. Ce mécanisme de ciblage n'est pas modifié par le correctif de
 * doc/fix/plafonnement-dommages-constructions-planetaires.md : seul le
 * compteur ConstructionPlanetaire.dommages est désormais plafonné.
 *
 * Voir doc/bugs/dommages-persistants-mines-plafond-manquant.md et
 * CombatDegatsNegatifsTest, finding 5, pour le contexte complet.
 */
class OverkillEnUnRoundTest {

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

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
        for (int i = 0; i < chance.length; i++) chance[i] = 90;
        setField(arme, Arme.class, "caracteristiquesArmes", carac);
        setField(arme, Arme.class, "chanceToucher", chance);
        setField(arme, Arme.class, "typeArme", Const.CV_ARME_CP);
        setField(arme, ComposantDeVaisseau.class, "type", Const.CV_ARME);
        setField(arme, ComposantDeVaisseau.class, "nombreDeCasesPrises", 1);
        return arme;
    }

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

    @Test
    void unSeulRound_beaucoupDeVaisseauxSurPeuDeMines_resteDansLaLimiteDeLaStructureTotale() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        int pointsDeStructureMine = 20;
        int dommagesParTir = 8;
        int nbMinesSurvivantes = 3;   // comme dans le rapport réel (6tour15 : 3 mines vivantes)
        int nbVaisseauxAttaquants = 26; // ordre de grandeur d'une flotte de bombardiers réelle

        Arme arme = armeBombardier(30, dommagesParTir);
        PlanDeVaisseau plan = planMonoArme("Bombardier", arme);
        plans.put("bombA", plan);

        Batiment batimentMine = new Batiment("mineI", 0, null, 0, null, 0, 0f, null, pointsDeStructureMine, 0, null);

        ConstructionPlanetaire[] cibles = new ConstructionPlanetaire[nbMinesSurvivantes];
        for (int i = 0; i < nbMinesSurvivantes; i++) cibles[i] = new ConstructionPlanetaire("mineI");

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie("mineI")).thenReturn(batimentMine);
            // Univers.getInt(n) : cible au hasard dans les n bâtiments -- aucun filtre estDetruit()
            // ici, exactement comme Combat.tirAirSol qui construit "cibles" une seule fois par round.
            int[] compteur = {0};
            univers.when(() -> Univers.getInt(anyInt())).thenAnswer(inv -> {
                int n = inv.getArgument(0);
                return compteur[0]++ % n;
            });

            // Simule exactement le round : tous les vaisseaux tirent, dans l'ordre, sur la liste
            // "cibles" figée, sans jamais retirer une mine détruite en cours de round (comme le fait
            // réellement Combat.tirAirSol avant l'appel unique à eliminerPertesBatiments()).
            for (int i = 0; i < nbVaisseauxAttaquants; i++) {
                Vaisseau v = new Vaisseau("Bombardier-" + i, "bombA", 0);
                v.preparerAuCombat(false);
                v.tirSurConstruction(cibles, Heros.HEROS_NON_PRESENT, Gouverneur.GOUVERNEUR_NON_PRESENT, true);
            }

            int total = 0;
            for (ConstructionPlanetaire c : cibles) total += c.getDommages();
            int plafondTheorique = nbMinesSurvivantes * pointsDeStructureMine;

            assertTrue(total <= plafondTheorique,
                    "depuis le correctif de plafonnement (doc/fix/plafonnement-dommages-constructions-"
                            + "planetaires.md), le total cumulé des compteurs dommages ne peut plus dépasser "
                            + "le plafond théorique (structure x nombre de mines) même quand le mécanisme de "
                            + "ciblage laisse un bâtiment déjà détruit continuer à encaisser des tirs jusqu'à "
                            + "la fin du round : attendu <= " + plafondTheorique + ", obtenu " + total);
        }
    }
}
