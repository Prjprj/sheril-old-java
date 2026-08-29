package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste Combat.combatFlotteFlotte, le coeur du calcul des combats spatiaux
 * (positionnement, ciblage, tempo, tir, application des dégâts, élimination
 * des vaisseaux détruits, victoire).
 *
 * combatFlotteFlotte s'appuie sur Univers (accès statique façon singleton :
 * générateur aléatoire, résolution de plans de vaisseau, messages de
 * rapport) et sur Commandant (classe de 4000+ lignes couplée à la base de
 * données). Reproduire ces dépendances telles quelles serait hors de portée
 * d'un test unitaire ; on les remplace donc :
 *  - Commandant est un mock Mockito : on ne stub que ce que combatFlotteFlotte
 *    lit réellement (flotte, stratégie, indicatifs joueur/locale).
 *  - Univers est mocké statiquement (Mockito mockStatic). Le générateur
 *    aléatoire (getInt/getTest) est rendu déterministe pour que les
 *    scénarios soient reproductibles : "coup garanti" (getTest toujours
 *    vrai) permet de vérifier la logique de dégâts/élimination sans dépendre
 *    du hasard.
 *  - Flotte, Vaisseau, PlanDeVaisseau, Arme, StrategieDeCombatSpatial sont de
 *    VRAIES instances (ce sont des POJO de calcul, sans dépendance à
 *    Univers une fois construits) : on veut exercer le vrai calcul de
 *    dégâts/ciblage/tir, pas une version simulée.
 *
 * Les plans de vaisseau utilisés ("vaisseau mono-tir") ont un unique
 * composant : une arme qui occupe la totalité des cases du vaisseau. Un seul
 * point de dégâts suffit donc à rendre ce composant inutilisable et à
 * détruire le vaisseau (Vaisseau.ajouterDommagesAuHasard renvoie false dès
 * que plus aucun composant n'est valide) — combiné à getTest toujours vrai,
 * cela rend l'issue du combat prévisible : un coup au but est une élimination.
 */
class CombatFlotteFlotteTest {

    private final Map<String, PlanDeVaisseau> plans = new HashMap<>();

    // ---------------------------------------------------------------
    // Fixtures : construites via les constructeurs protégés/privés (accès
    // package) puis remplies par réflexion, pour éviter que la construction
    // elle-même déclenche les dépendances Univers habituelles (résolution de
    // technologie, calcul des chances de toucher par niveau, etc.) — on veut
    // fixer nous-mêmes les valeurs numériques du scénario.
    // ---------------------------------------------------------------

    private static void setField(Object cible, Class<?> declarant, String nom, Object valeur) throws Exception {
        Field f = declarant.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    /** Arme dont un seul tir suffit à rendre son propre composant (donc le vaisseau) inutilisable. */
    private static Arme armeMonoCoup(int portee) throws Exception {
        Constructor<Arme> ctor = Arme.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Arme arme = ctor.newInstance();

        int[] carac = new int[6];
        carac[Const.ARME_VITESSE_DE_BASE] = 1;
        carac[Const.ARME_DOMMAGES_BOUCLIER] = 10;
        carac[Const.ARME_DOMMAGES_COQUE] = 10;
        carac[Const.ARME_DOMMAGES_SOL] = 10;
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

    /** Plan de vaisseau dont l'unique composant est l'arme fournie (1 case au total). */
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

    /** Redirige les logs texte de Combat (Combat.log/logln) vers la mémoire, pour ne pas écrire dans data/tourX/combats pendant les tests. */
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
    // Tests
    // ---------------------------------------------------------------

    @Test
    void combatFlotteFlotte_coupsGarantis_uneFlotteIsoleeEstAneantieParUnAdversaireEnSurnombre() throws Exception {
        Arme arme = armeMonoCoup(30);
        PlanDeVaisseau plan = planMonoArme("Vaisseau mono-tir", arme);
        plans.put("monoA", plan);

        Commandant c1 = commandantMinimal();
        Commandant c2 = commandantMinimal();

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f1 = new Flotte("Attaque", new Position(1, 1, 1));
            f1.ajouterVaisseau(new Vaisseau("Chasseur-1", "monoA", 0));
            f1.ajouterVaisseau(new Vaisseau("Chasseur-2", "monoA", 0));

            Flotte f2 = new Flotte("Defense", new Position(1, 1, 1));
            f2.ajouterVaisseau(new Vaisseau("Sentinelle", "monoA", 0));

            univers.when(() -> Univers.getCommandantFromFlotte(f1)).thenReturn(c1);
            univers.when(() -> Univers.getCommandantFromFlotte(f2)).thenReturn(c2);

            when(c1.getFlotte(0)).thenReturn(f1);
            when(c2.getFlotte(0)).thenReturn(f2);

            neutraliserJournalCombat();

            Combat.combatFlotteFlotte(c1, c2, 0, 0);

            assertEquals(0, f2.getNombreDeVaisseaux(),
                    "avec des coups garantis et un seul point de dégâts nécessaire pour détruire un vaisseau, "
                            + "la flotte défenseure isolée (1 vaisseau, 2 assaillants) est anéantie dès le premier round");
            assertTrue(f1.getNombreDeVaisseaux() >= 1,
                    "la flotte attaquante en surnombre (2 vaisseaux) ne peut pas perdre plus d'un vaisseau "
                            + "face à un seul défenseur");
            verify(c2).eliminerFlotte(0);
        }
    }

    @Test
    void combatFlotteFlotte_coupsGarantis_tempoIdentique_leDefenseurTireEnPremierEtSeul() throws Exception {
        Arme arme = armeMonoCoup(30);
        PlanDeVaisseau plan = planMonoArme("Vaisseau mono-tir", arme);
        plans.put("monoA", plan);

        Commandant c1 = commandantMinimal();
        Commandant c2 = commandantMinimal();

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f1 = new Flotte("Camp1", new Position(1, 1, 1));
            f1.ajouterVaisseau(new Vaisseau("V1", "monoA", 0));

            Flotte f2 = new Flotte("Camp2", new Position(1, 1, 1));
            f2.ajouterVaisseau(new Vaisseau("V2", "monoA", 0));

            univers.when(() -> Univers.getCommandantFromFlotte(f1)).thenReturn(c1);
            univers.when(() -> Univers.getCommandantFromFlotte(f2)).thenReturn(c2);

            when(c1.getFlotte(0)).thenReturn(f1);
            when(c2.getFlotte(0)).thenReturn(f2);

            neutraliserJournalCombat();

            Combat.combatFlotteFlotte(c1, c2, 0, 0);

            // Constaté empiriquement (caractérisation, pas une attente a priori) : les deux
            // vaisseaux étant strictement identiques, Combat.determinationTempo leur attribue
            // le même tempo, et Combat.combat() ne fait passer la flotte 1 en premier que si sa
            // clé de tempo est STRICTEMENT supérieure (comparaison ">", pas ">="). En cas
            // d'égalité, c'est donc systématiquement la flotte 2 (le défenseur) qui tire en
            // premier — et comme un seul coup suffit à détruire ces vaisseaux, elle tire
            // seule et gagne le duel.
            assertEquals(0, f1.getNombreDeVaisseaux(),
                    "à tempo égal, l'attaquant (flotte 1) perd le duel face au défenseur qui tire en premier");
            assertEquals(1, f2.getNombreDeVaisseaux(),
                    "à tempo égal, le défenseur (flotte 2) tire le premier et seul, donc survit");
        }
    }

    @Test
    void combatFlotteFlotte_coupsToujoursManques_aucunVaisseauNEstDetruit() throws Exception {
        Arme arme = armeMonoCoup(30);
        PlanDeVaisseau plan = planMonoArme("Vaisseau mono-tir", arme);
        plans.put("monoA", plan);

        Commandant c1 = commandantMinimal();
        Commandant c2 = commandantMinimal();

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            // Univers.getTest(50) est aussi utilisé par Combat.determinationCible pour la
            // sélection de cible (boucle "while (choix2 == null)" qui ne se termine que si
            // getTest(50) finit par renvoyer vrai) : le laisser toujours faux bloquerait le
            // combat dans une boucle infinie avant même le tir. On ne rend donc raté que le
            // jet de précision du tir lui-même (reussiteTir, chance != 50 dans ce scénario).
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(false);
            univers.when(() -> Univers.getTest(50)).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f1 = new Flotte("Attaque", new Position(1, 1, 1));
            f1.ajouterVaisseau(new Vaisseau("Chasseur-1", "monoA", 0));

            Flotte f2 = new Flotte("Defense", new Position(1, 1, 1));
            f2.ajouterVaisseau(new Vaisseau("Sentinelle", "monoA", 0));

            univers.when(() -> Univers.getCommandantFromFlotte(f1)).thenReturn(c1);
            univers.when(() -> Univers.getCommandantFromFlotte(f2)).thenReturn(c2);

            when(c1.getFlotte(0)).thenReturn(f1);
            when(c2.getFlotte(0)).thenReturn(f2);

            neutraliserJournalCombat();

            Combat.combatFlotteFlotte(c1, c2, 0, 0);

            assertEquals(1, f1.getNombreDeVaisseaux(), "sans coup au but, aucun vaisseau attaquant n'est perdu");
            assertEquals(1, f2.getNombreDeVaisseaux(), "sans coup au but, aucun vaisseau défenseur n'est perdu");
            verify(c1, org.mockito.Mockito.never()).eliminerFlotte(anyInt());
            verify(c2, org.mockito.Mockito.never()).eliminerFlotte(anyInt());
        }
    }

    @Test
    void combatFlotteFlotte_horsDePortee_aucunTirNePeutToucher() throws Exception {
        // Portée de l'arme volontairement inférieure à la distance de départ
        // (les vaisseaux ne bougent pas : aucun moteur dans leur plan), pour
        // vérifier qu'Arme.getChanceDeToucher retourne bien 0 hors de portée
        // et qu'aucun coup ne peut donc être porté, même avec Univers.getTest
        // toujours vrai.
        Arme armeCourtePortee = armeMonoCoup(1);
        PlanDeVaisseau plan = planMonoArme("Vaisseau courte portee", armeCourtePortee);
        plans.put("monoA", plan);

        Commandant c1 = commandantMinimal();
        Commandant c2 = commandantMinimal();

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.getInt(anyInt())).thenReturn(0);
            univers.when(() -> Univers.getTest(anyInt())).thenReturn(true);
            univers.when(() -> Univers.getPlanDeVaisseau(anyString()))
                    .thenAnswer(inv -> plans.get((String) inv.getArgument(0)));
            univers.when(() -> Univers.getMessageRapport(anyString(), any()))
                    .thenReturn(new String[]{"{0} vs {1}", "{0} de {1}", "Type", "Nombre", "Dommages", "Détruits"});

            Flotte f1 = new Flotte("Attaque", new Position(1, 1, 1));
            f1.ajouterVaisseau(new Vaisseau("Chasseur-1", "monoA", 0));

            Flotte f2 = new Flotte("Defense", new Position(1, 1, 1));
            f2.ajouterVaisseau(new Vaisseau("Sentinelle", "monoA", 0));

            univers.when(() -> Univers.getCommandantFromFlotte(f1)).thenReturn(c1);
            univers.when(() -> Univers.getCommandantFromFlotte(f2)).thenReturn(c2);

            when(c1.getFlotte(0)).thenReturn(f1);
            when(c2.getFlotte(0)).thenReturn(f2);

            neutraliserJournalCombat();

            Combat.combatFlotteFlotte(c1, c2, 0, 0);

            assertEquals(1, f1.getNombreDeVaisseaux(), "hors de portée, aucun vaisseau ne peut être détruit");
            assertEquals(1, f2.getNombreDeVaisseaux(), "hors de portée, aucun vaisseau ne peut être détruit");
        }
    }
}
