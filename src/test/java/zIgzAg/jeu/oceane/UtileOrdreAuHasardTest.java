package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste Utile.ordreAuHasard, la fonction qui tire l'ordre de résolution des
 * combats sur une case (Combat.resolutionCombatsSurUneCase / attaques
 * flotte-flotte) : elle doit toujours produire une permutation valide de
 * [0, t-1], sans quoi des vaisseaux seraient oubliés ou traités deux fois
 * pendant la résolution d'un combat.
 *
 * Univers.HASARD (générateur aléatoire statique utilisé par ordreAuHasard)
 * est posé par réflexion avec un Random seedé, pour éviter d'avoir à
 * initialiser tout Univers (chargement de fichiers de config, etc.) et pour
 * rendre le test déterministe.
 */
class UtileOrdreAuHasardTest {

    @BeforeEach
    void seedHasard() throws Exception {
        Field f = Univers.class.getDeclaredField("HASARD");
        f.setAccessible(true);
        f.set(null, new Random(42));
    }

    @Test
    void tailleZeroDonneUnTableauVide() {
        assertEquals(0, Utile.ordreAuHasard(0).length);
    }

    @Test
    void tailleUnDonneLeSeulIndicePossible() {
        assertArrayEquals(new int[]{0}, Utile.ordreAuHasard(1));
    }

    @Test
    void resultatEstUnePermutationValide() {
        for (int taille = 1; taille <= 20; taille++) {
            int[] ordre = Utile.ordreAuHasard(taille);
            assertEquals(taille, ordre.length);

            boolean[] vus = new boolean[taille];
            for (int valeur : ordre) {
                assertTrue(valeur >= 0 && valeur < taille,
                        "valeur hors bornes: " + valeur + " pour taille " + taille);
                assertTrue(!vus[valeur],
                        "doublon détecté pour la valeur " + valeur + " (taille " + taille + ")");
                vus[valeur] = true;
            }
            for (boolean vu : vus)
                assertTrue(vu, "toutes les valeurs de 0 à taille-1 doivent apparaître");
        }
    }

    @Test
    void appelsRepetesNeSontPasForcementIdentiques() {
        int[] premier = Utile.ordreAuHasard(10);
        int[] second = Utile.ordreAuHasard(10);

        assertTrue(!java.util.Arrays.equals(premier, second),
                "avec un générateur seedé déterministe mais avancé entre deux appels, "
                        + "deux tirages de taille 10 ne devraient pas être strictement identiques");
    }
}
