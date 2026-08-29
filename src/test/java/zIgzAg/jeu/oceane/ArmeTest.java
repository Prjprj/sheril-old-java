package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste la mécanique de résolution de tir de Combat : Arme.getChanceDeToucher
 * est la formule qui détermine, pour chaque tir échangé pendant un combat
 * (Combat.combatFlotteFlotte / combatFlottePlanete), si un vaisseau touche sa
 * cible en fonction du type de coque visé et de la distance.
 *
 * Arme est construite via son constructeur protégé (accessible package) puis
 * ses champs privés sont posés par réflexion, pour isoler la formule de tir
 * des dépendances lourdes d'Univers (chargement de ListeCaracArmes /
 * ListeChancesDeToucherArmes) utilisées par le constructeur public.
 */
class ArmeTest {

    private static final int TYPE_COQUE = 3;

    private Arme creerArme(int portee, int chanceDeBase) throws Exception {
        Arme arme = new Arme();

        int[] caracteristiques = new int[6];
        caracteristiques[Const.ARME_VITESSE_DE_BASE] = 1;
        caracteristiques[Const.ARME_DOMMAGES_BOUCLIER] = 10;
        caracteristiques[Const.ARME_DOMMAGES_COQUE] = 20;
        caracteristiques[Const.ARME_DOMMAGES_SOL] = 5;
        caracteristiques[Const.ARME_PORTEE] = portee;

        int[] chanceToucher = new int[10];
        for (int i = 0; i < chanceToucher.length; i++)
            chanceToucher[i] = chanceDeBase;

        setPrivateField(arme, "caracteristiquesArmes", caracteristiques);
        setPrivateField(arme, "chanceToucher", chanceToucher);

        return arme;
    }

    private void setPrivateField(Object cible, String nom, Object valeur) throws Exception {
        Field f = Arme.class.getDeclaredField(nom);
        f.setAccessible(true);
        f.set(cible, valeur);
    }

    @Test
    void chanceDeToucherAPorteeZero_estLaChanceDeBase() throws Exception {
        Arme arme = creerArme(10, 80);

        assertEquals(80, arme.getChanceDeToucher(TYPE_COQUE, 0));
    }

    @Test
    void chanceDeToucherDiminueAvecLaDistance() throws Exception {
        Arme arme = creerArme(10, 100);

        int chancePres = arme.getChanceDeToucher(TYPE_COQUE, 1);
        int chanceLoin = arme.getChanceDeToucher(TYPE_COQUE, 9);

        assertTrue(chanceLoin < chancePres,
                "la chance de toucher doit décroître quand la distance se rapproche de la portée");
    }

    @Test
    void chanceDeToucherAppliqueLaFormuleExacte() throws Exception {
        // (chanceToucher[typeCoque] * (100 - (90 * distance) / portee)) / 100
        Arme arme = creerArme(10, 100);

        int attendu = (100 * (100 - (90 * 4) / 10)) / 100;

        assertEquals(attendu, arme.getChanceDeToucher(TYPE_COQUE, 4));
        assertEquals(64, attendu);
    }

    @Test
    void chanceDeToucherEstNulleAPorteeMaximale() throws Exception {
        Arme arme = creerArme(10, 100);

        assertEquals(0, arme.getChanceDeToucher(TYPE_COQUE, 10));
    }

    @Test
    void chanceDeToucherEstNulleAuDelaDeLaPortee() throws Exception {
        Arme arme = creerArme(10, 100);

        assertEquals(0, arme.getChanceDeToucher(TYPE_COQUE, 25));
    }

    @Test
    void chanceDeToucherUtiliseLeTypeDeCoqueDeLaCible() throws Exception {
        Arme arme = new Arme();
        int[] caracteristiques = new int[6];
        caracteristiques[Const.ARME_PORTEE] = 10;
        int[] chanceToucher = new int[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        setPrivateField(arme, "caracteristiquesArmes", caracteristiques);
        setPrivateField(arme, "chanceToucher", chanceToucher);

        assertEquals(40, arme.getChanceDeToucher(3, 0));
        assertEquals(90, arme.getChanceDeToucher(8, 0));
    }

    @Test
    void forceSpatialeEstLaSommeDommagesCoqueEtBouclier() throws Exception {
        Arme arme = creerArme(10, 100);

        assertEquals(30, arme.getForceSpatiale());
    }

    @Test
    void forcePlanetaireEstLesDommagesSol() throws Exception {
        Arme arme = creerArme(10, 100);

        assertEquals(5, arme.getForcePlanetaire());
    }

    @Test
    void estCombatSpatialEtEstCombatPlanetaireSelonLeTypeArme() throws Exception {
        Arme spatiale = new Arme();
        setPrivateField(spatiale, "typeArme", Const.CV_ARME_CS);
        assertTrue(spatiale.estCombatSpatial());
        assertTrue(!spatiale.estCombatPlanetaire());

        Arme planetaire = new Arme();
        setPrivateField(planetaire, "typeArme", Const.CV_ARME_CP);
        assertTrue(!planetaire.estCombatSpatial());
        assertTrue(planetaire.estCombatPlanetaire());

        Arme mixte = new Arme();
        setPrivateField(mixte, "typeArme", Const.CV_ARME_M);
        assertTrue(mixte.estCombatSpatial());
        assertTrue(mixte.estCombatPlanetaire());
    }
}
