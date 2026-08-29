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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste Combat.combatFlottePlanete, le calcul d'une attaque de flotte contre
 * une planète : défenses planétaires (batteries), milice (population), puis
 * riposte de la flotte contre ces mêmes défenses, jusqu'à réduction de la
 * population défensive à 0 (prise de planète) ou épuisement du nombre de
 * tours de combat de la flotte.
 *
 * Même approche que CombatFlotteFlotteTest : Commandant/Systeme/Planete/
 * Possession sont mockés (classes trop couplées à la base de données pour
 * être instanciées telles quelles), Univers est mocké statiquement avec un
 * générateur aléatoire déterministe, et Flotte/Vaisseau/PlanDeVaisseau/Arme/
 * ConstructionPlanetaire/Batiment restent de VRAIES instances : ce sont elles
 * qui portent le calcul de dégâts qu'on veut exercer, côté flotte comme côté
 * défenses planétaires.
 *
 * Particularité propre aux défenses planétaires : Combat.tirMilicesPlanetaires
 * fabrique en dur un ConstructionPlanetaire("battlaI") pour représenter la
 * milice (résistance de la population elle-même), indépendamment des
 * bâtiments réels de la planète (Planete.getBatiments()) — il faut donc
 * enregistrer une résolution Univers.getTechnologie("battlaI") même pour un
 * scénario "sans bâtiments".
 *
 * Avec Univers.getInt toujours nul (déterminisme des scénarios), les tirs de
 * défense planétaire (Combat.tirDefensesPlanetaires, utilisé aussi bien par
 * les batteries que par la milice) ciblent systématiquement le PREMIER
 * vaisseau de la liste "sol" et lui seul (Vaisseau cible = inter.get(getInt(
 * inter.size()))) : les défenses au sol ne peuvent jamais menacer qu'un seul
 * vaisseau de la flotte assaillante par combat, quel que soit le nombre de
 * salves tirées. C'est ce constat qui motive le choix, ci-dessous, d'une
 * flotte à un seul vaisseau pour le scénario "la milice repousse l'assaut"
 * (elle est la cible exclusive et sa perte élimine toute la flotte) et d'une
 * flotte nombreuse pour le scénario de prise de planète (le premier vaisseau
 * encaisse seul les tirs défensifs, les suivants ripostent sans jamais être
 * inquiétés).
 */
class CombatFlottePlaneteTest {

    private final Map<String, PlanDeVaisseau> plans = new HashMap<>();
    private final Map<String, Object> technologies = new HashMap<>();

    // ---------------------------------------------------------------
    // Fixtures vaisseaux (identiques à CombatFlotteFlotteTest : un unique
    // composant, détruit dès 1 point de dégâts, pour des scénarios prévisibles)
    // ---------------------------------------------------------------

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    private static Arme armeMonoCoup(int portee, int dommagesSol) throws Exception {
        return armeMonoCoup(portee, 10, 10, dommagesSol);
    }

    private static Arme armeMonoCoup(int portee, int dommagesBouclier, int dommagesCoque, int dommagesSol) throws Exception {
        Constructor<Arme> ctor = Arme.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Arme arme = ctor.newInstance();

        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = dommagesBouclier;
        carac[Const.ARME_DOMMAGES_COQUE] = dommagesCoque;
        carac[Const.ARME_DOMMAGES_SOL] = dommagesSol;
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

    private static PlanDeVaisseau planMonoArme(String nom, Arme arme) throws Exception {
        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom(nom);

        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[]{"armeMonoCoupI"});
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[]{arme});

        return plan;
    }

    // ---------------------------------------------------------------
    // Fixtures défenses planétaires : un Batiment réel (défense planétaire
    // ou milice) portant une Arme réelle, résolus via Univers.getTechnologie
    // ("code de bâtiment" -> Batiment, "code d'arme"+niveau -> Arme).
    // ---------------------------------------------------------------

    private Batiment batimentDefense(String code, String codeArme, int structure) {
        Batiment b = new Batiment(code, 0, null, 0, null, 0, 0f, null, structure, 0, codeArme);
        technologies.put(code, b);
        return b;
    }

    /** Enregistre l'arme d'un bâtiment de défense (battlaI/battXX...) sous "codeArme" + "I" (niveau 0). */
    private void armeDeBatiment(String codeArme, int portee, int dommagesBouclier, int dommagesCoque) throws Exception {
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

        technologies.put(codeArme + "I", arme);
    }

    private static void neutraliserJournalCombat() throws Exception {
        Field writer = Combat.class.getDeclaredField("writer");
        writer.setAccessible(true);
        writer.set(null, new BufferedWriter(new StringWriter()));
    }

    private Commandant commandantMinimal(int numero) {
        Commandant c = mock(Commandant.class);
        when(c.getStrategie(anyString())).thenReturn(Const.STRATEGIE_DEFAUT);
        when(c.numeroFlotte(any())).thenReturn(0);
        when(c.estJoueurNeutre()).thenReturn(false);
        when(c.estJoueurHumain()).thenReturn(false);
        when(c.getLocale()).thenReturn(Locale.FRENCH);
        when(c.getNumero()).thenReturn(numero);
        when(c.getPossession(any())).thenReturn(mock(Possession.class));
        return c;
    }

    /** Planete mock dont getBatiments()/eliminerPertesBatiments() partagent un état mutable réel. */
    private Planete planeteMinimal(int population, int stabilite, ConstructionPlanetaire... batimentsDepart) {
        Planete p = mock(Planete.class);
        List<ConstructionPlanetaire> batiments = new ArrayList<>(java.util.Arrays.asList(batimentsDepart));
        when(p.populationTotale()).thenReturn(population);
        when(p.getStabilite()).thenReturn(stabilite);
        when(p.getBatiments()).thenAnswer(inv -> batiments.toArray(new ConstructionPlanetaire[0]));
        when(p.listeEquipementsNombresDommages()).thenReturn(new HashMap<>());
        org.mockito.Mockito.doAnswer(inv -> {
            batiments.removeIf(ConstructionPlanetaire::estDetruit);
            return null;
        }).when(p).eliminerPertesBatiments();
        return p;
    }

    private Systeme systemeMinimal(Planete planete, int numPla) {
        Systeme s = mock(Systeme.class);
        when(s.getPosition()).thenReturn(new Position(1, 1, 1));
        when(s.getPlanete(numPla)).thenReturn(planete);
        when(s.getNombrePlanetes()).thenReturn(numPla + 1);
        when(s.getNomNumeroPlanete(anyInt())).thenReturn("Planete-test");
        when(s.estProprio(anyInt())).thenReturn(true);
        return s;
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    void combatFlottePlanete_puissanceInsuffisante_neDeclenchePasDeCombat() throws Exception {
        // Une seule arme quasi inoffensive : puissance de flotte trop faible
        // (Const.PUISSANCE_ATTAQUE_PLANETAIRE_MINIMALE = 50) pour même engager
        // le combat planétaire — la méthode doit s'arrêter avant de toucher
        // Systeme/Planete.
        Arme armeFaible = armeMonoCoup(30, 1);
        PlanDeVaisseau planFaible = planMonoArme("Vaisseau chetif", armeFaible);
        plans.put("chetifA", planFaible);

        Commandant c1 = commandantMinimal(1);
        Commandant c2 = commandantMinimal(2);
        Systeme s = mock(Systeme.class);
        Planete p = mock(Planete.class);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));

            Flotte f = new Flotte("Attaque faible", new Position(1, 1, 1));
            f.ajouterVaisseau(new Vaisseau("Chetif-1", "chetifA", 0));
            f.setDirective(Const.DIRECTIVE_FLOTTE_ATTAQUE_PLANETE);
            assertTrue(f.getPuissance() < Const.PUISSANCE_ATTAQUE_PLANETAIRE_MINIMALE,
                    "prérequis du scénario : la flotte doit être sous le seuil minimal");

            when(c1.getFlotte(0)).thenReturn(f);

            neutraliserJournalCombat();

            int resultat = Combat.combatFlottePlanete(c1, 0, c2, s, 0, 0, null);

            assertEquals(-1, resultat, "sous le seuil de puissance minimal, le combat planétaire ne se déroule pas");
            org.mockito.Mockito.verifyNoInteractions(s, p);
        }
    }

    @Test
    void combatFlottePlanete_miliceEliminentSeuleUnAssaillantIsole() throws Exception {
        // Population importante défendue uniquement par la milice (aucun
        // bâtiment de défense). Le vaisseau assaillant est bâti pour avoir,
        // à lui seul, assez de puissance pour franchir le seuil d'engagement
        // (Const.PUISSANCE_ATTAQUE_PLANETAIRE_MINIMALE = 50) tout en restant
        // "mono-tir" (un seul composant, détruit dès 1 point de dégâts) :
        // avec des coups garantis, la milice le détruit dans la phase de tir
        // défensif du round 1, avant la phase de riposte (Combat.tirAirSol
        // exclut les vaisseaux déjà détruits) — la population n'est donc
        // jamais entamée.
        armeDeBatiment("milArme", 30, 10, 10);
        batimentDefense("battlaI", "milArme", 999);
        Arme armeVaisseau = armeMonoCoup(30, 25, 25, 10);
        PlanDeVaisseau planVaisseau = planMonoArme("Vaisseau mono-tir renforce", armeVaisseau);
        plans.put("monoA", planVaisseau);

        Commandant c1 = commandantMinimal(1);
        Commandant c2 = commandantMinimal(2);

        Planete planete = planeteMinimal(500, 100);
        Systeme s = systemeMinimal(planete, 0);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie(anyString()))
                    .thenAnswer(inv -> technologies.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f = new Flotte("Attaque isolee", new Position(1, 1, 1));
            f.ajouterVaisseau(new Vaisseau("V1", "monoA", 0));
            f.setDirective(Const.DIRECTIVE_FLOTTE_ATTAQUE_PLANETE);
            assertTrue(f.getPuissance() >= Const.PUISSANCE_ATTAQUE_PLANETAIRE_MINIMALE,
                    "prérequis du scénario : le vaisseau seul doit franchir le seuil d'engagement");

            when(c1.getFlotte(0)).thenReturn(f);

            neutraliserJournalCombat();

            Combat.combatFlottePlanete(c1, 0, c2, s, 0, 0, null);

            assertEquals(0, f.getNombreDeVaisseaux(),
                    "la milice élimine l'unique vaisseau assaillant avant qu'il ne puisse riposter");
            verify(planete, never()).setProprio(anyInt());
            verify(planete).diminuerPopulation(0);
        }
    }

    @Test
    void combatFlottePlanete_uneFlotteEnNombreSuffisantEpuiseLaPopulationEtPrendLaPlanete() throws Exception {
        // Population faible face à une flotte nombreuse à coups garantis :
        // même en perdant des vaisseaux face à la milice, il en reste assez
        // pour ramener la population défensive à 0 et déclencher la prise de
        // la planète (Commandant.transfertPlanete -> Planete.setProprio).
        //
        // Constaté (caractérisation) : même en cas de prise complète, la
        // perte de population réellement appliquée à la planète
        // (Planete.diminuerPopulation) est plafonnée à populationTotale()/10
        // — ici 40/10=4 — quel que soit l'écart entre la population de
        // départ et la population défensive finale (nulle ou négative). La
        // prise de la planète ne "vide" donc pas sa population d'un coup ;
        // seule une directive de pillage/éradication le fait (branches
        // séparées, hors de ce scénario).
        armeDeBatiment("milArme", 30, 5, 5);
        batimentDefense("battlaI", "milArme", 999);

        Arme armeVaisseau = armeMonoCoup(30, 50);
        PlanDeVaisseau planVaisseau = planMonoArme("Vaisseau mono-tir", armeVaisseau);
        for (int i = 0; i < 8; i++)
            plans.put("mono" + i, planVaisseau);

        Commandant c1 = commandantMinimal(1);
        Commandant c2 = commandantMinimal(2);

        Planete planete = planeteMinimal(40, 100);
        Systeme s = systemeMinimal(planete, 0);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getTechnologie(anyString()))
                    .thenAnswer(inv -> technologies.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f = new Flotte("Grande Attaque", new Position(1, 1, 1));
            for (int i = 0; i < 8; i++)
                f.ajouterVaisseau(new Vaisseau("V" + i, "mono" + i, 0));
            f.setDirective(Const.DIRECTIVE_FLOTTE_ATTAQUE_PLANETE);

            when(c1.getFlotte(0)).thenReturn(f);

            neutraliserJournalCombat();

            Combat.combatFlottePlanete(c1, 0, c2, s, 0, 0, null);

            verify(planete).setProprio(1);
            verify(planete).diminuerPopulation(4);
        }
    }
}
