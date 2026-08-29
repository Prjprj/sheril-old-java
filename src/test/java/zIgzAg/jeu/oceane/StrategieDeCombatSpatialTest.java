package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste StrategieDeCombatSpatial, qui pilote le positionnement et le
 * comportement des vaisseaux pendant Combat.combatFlotteFlotte (voir
 * positionnement()/determinationCible() dans Combat.java, qui lisent
 * getPositionnement/getCibles). Une mauvaise copie ou fusion de stratégie
 * changerait silencieusement le déroulement des combats d'un commandant.
 */
class StrategieDeCombatSpatialTest {

    private StrategieDeCombatSpatial creerStrategie(String nom, int agressivite) {
        return new StrategieDeCombatSpatial(
                nom,
                agressivite,
                1,
                new String[]{"chasA", "croiA"},
                new int[][]{{1, 2}, {3, 4}},
                new int[][]{{5, 6}, {7, 8}});
    }

    @Test
    void constructeurExposeLesValeursDeBase() {
        StrategieDeCombatSpatial s = creerStrategie("Ma Stratégie", 3);

        assertEquals("Ma Stratégie", s.getNom());
        assertEquals(3, s.getAgressivite());
        assertEquals(1, s.getTypeCible());
    }

    @Test
    void constructeurAssociePositionnementEtCiblesParTypeDeVaisseau() {
        StrategieDeCombatSpatial s = creerStrategie("S", 0);

        assertArrayEqualsPrefix(new int[]{1, 2}, s.getPositionnement("chasA"));
        assertArrayEqualsPrefix(new int[]{3, 4}, s.getPositionnement("croiA"));
        assertEquals(null, s.getPositionnement("inconnu"));

        assertArrayEqualsPrefix(new int[]{5, 6}, s.getCibles("chasA"));
        assertArrayEqualsPrefix(new int[]{7, 8}, s.getCibles("croiA"));
    }

    private void assertArrayEqualsPrefix(int[] attendu, int[] reel) {
        for (int i = 0; i < attendu.length; i++)
            assertEquals(attendu[i], reel[i]);
    }

    @Test
    void copieProfondeNePartagePasLesTableaux() {
        StrategieDeCombatSpatial original = creerStrategie("Originale", 2);
        StrategieDeCombatSpatial copie = new StrategieDeCombatSpatial(original);

        assertEquals(original.getNom(), copie.getNom());
        assertEquals(original.getAgressivite(), copie.getAgressivite());
        assertEquals(original.getTypeCible(), copie.getTypeCible());

        assertNotSame(original.getPositionnement("chasA"), copie.getPositionnement("chasA"));
        assertNotSame(original.getCibles("chasA"), copie.getCibles("chasA"));

        // Muter la copie ne doit pas affecter l'original : sinon deux
        // commandants réutilisant la même stratégie de base modifieraient
        // le comportement de combat l'un de l'autre.
        copie.getPositionnement("chasA")[0] = 99;
        assertEquals(1, original.getPositionnement("chasA")[0]);
    }

    @Test
    void copieDeStrategieNulleEstRefusee() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategieDeCombatSpatial(null));
    }

    @Test
    void fusionnerRemplaceLesValeursDeBaseEtAjouteLesEntrees() {
        StrategieDeCombatSpatial cible = creerStrategie("Cible", 1);
        StrategieDeCombatSpatial source = new StrategieDeCombatSpatial(
                "Source",
                4,
                2,
                new String[]{"chasA", "torpA"},
                new int[][]{{10, 11}, {20, 21}},
                new int[][]{{30, 31}, {40, 41}});

        cible.fusionner(source);

        assertEquals("Source", cible.getNom());
        assertEquals(4, cible.getAgressivite());
        assertEquals(2, cible.getTypeCible());

        // "chasA" existait déjà côté cible : la fusion écrase sa valeur.
        assertArrayEqualsPrefix(new int[]{10, 11}, cible.getPositionnement("chasA"));
        // "croiA" n'était que côté cible : il doit être conservé.
        assertArrayEqualsPrefix(new int[]{3, 4}, cible.getPositionnement("croiA"));
        // "torpA" n'était que côté source : il doit être ajouté.
        assertArrayEqualsPrefix(new int[]{20, 21}, cible.getPositionnement("torpA"));
    }

    @Test
    void estStrategieParDefautComparAuCodeDeLaStrategieParDefaut() {
        assertTrue(StrategieDeCombatSpatial.estStrategieParDefaut(Const.STRATEGIE_DEFAUT.getNom()));
        assertTrue(!StrategieDeCombatSpatial.estStrategieParDefaut("une-autre-strategie"));
    }
}
