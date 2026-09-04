package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Vérifie le correctif du signalement "division de flotte signalée en
 * succès même sans aucun vaisseau transféré" (combat Wiryxi/Juqav, joueur
 * 1 "mabeur", flotte "Phalange Cyan" — voir
 * doc/fix/division-flotte-echec-transfert-signale-comme-succes.md).
 *
 * Cause racine : Commandant.diviserFlotte émettait
 * EV_COMMANDANT_DIVISER_FLOTTE_0000 (succès) inconditionnellement, même
 * quand Flotte.diviserFlotte n'avait transféré aucun vaisseau (type de
 * vaisseau demandé absent de la flotte source) et qu'aucune nouvelle
 * flotte n'avait donc été créée.
 */
class DivisionFlotteEchecTransfertTest {

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    private static PlanDeVaisseau fabriquerPlanVide(String nom) throws Exception {
        Constructor<PlanDeVaisseau> ctor = PlanDeVaisseau.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        PlanDeVaisseau plan = ctor.newInstance();
        plan.setNom(nom);
        setField(plan, PlanDeVaisseau.class, "nbCases", 1);
        setField(plan, PlanDeVaisseau.class, "composants", new String[0]);
        setField(plan, PlanDeVaisseau.class, "composantsDeVaisseau", new ComposantDeVaisseau[0]);
        return plan;
    }

    private static Commandant creerCommandantAvecUneFlotte(String typeVaisseauPresent, int nombre) throws Exception {
        // Constructeur sans argument : on n'a besoin ici que de la gestion des
        // flottes, événements et erreurs, pas de tout l'appareil de score /
        // points de victoire qu'initialise le constructeur complet.
        Commandant c = new Commandant();
        c.initialiserFlottes();
        c.initialiserListesMessages();
        c.initialiserCorrespondanceFlotteDivisee();

        Flotte ancienne = new Flotte("Phalange Cyan", new Position(0, 15, 10));
        for (int i = 0; i < nombre; i++)
            ancienne.ajouterVaisseau(new Vaisseau("Vaisseau" + i, typeVaisseauPresent, 0));
        c.ajouterFlotte(ancienne);
        return c;
    }

    @Test
    void typeDeVaisseauAbsentDeLaFlotte_neCreeAucuneFlotteEtSignaleUneErreur() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        plans.put("Snip", fabriquerPlanVide("Snip"));

        Commandant c;
        boolean resultat;
        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.existencePlanDeVaisseau(anyString())).thenReturn(true);

            c = creerCommandantAvecUneFlotte("Snip", 2);

            // demande de diviser en prélevant un type de vaisseau absent de la flotte
            resultat = c.diviserFlotte(0, new String[]{"Archios II"}, new int[]{1}, "A", 1);
        }

        assertFalse(resultat, "aucun vaisseau du type demandé n'existe : la division doit échouer");
        // aucune nouvelle flotte créée
        assertEquals(1, c.listeFlottesEtNumeros().length, "aucune flotte supplémentaire ne doit apparaître");
        // la flotte source n'a pas perdu de vaisseau
        assertEquals(2, c.getFlotte(0).getNombreDeVaisseaux(), "la flotte source doit rester intacte");
        // une erreur a bien été journalisée, pas un événement de succès
        assertEquals(1, c.getErreurs().nbMessages());
        assertEquals(0, c.getEvenements().nbMessages());
    }

    @Test
    void typeDeVaisseauPresent_creeLaNouvelleFlotteEtSignaleLeSucces() throws Exception {
        Map<String, PlanDeVaisseau> plans = new HashMap<>();
        plans.put("Snip", fabriquerPlanVide("Snip"));

        Commandant c;
        boolean resultat;
        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.existencePlanDeVaisseau(anyString())).thenReturn(true);

            c = creerCommandantAvecUneFlotte("Snip", 2);

            resultat = c.diviserFlotte(0, new String[]{"Snip"}, new int[]{1}, "A", 1);
        }

        assertTrue(resultat, "le type demandé existe dans la flotte : la division doit réussir");
        assertEquals(2, c.listeFlottesEtNumeros().length, "la nouvelle flotte doit apparaître");
        assertEquals(1, c.getFlotte(0).getNombreDeVaisseaux(), "1 vaisseau prélevé sur la flotte source");
        assertEquals(0, c.getErreurs().nbMessages());
        assertEquals(1, c.getEvenements().nbMessages());
    }
}
