package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Vérifie le correctif du bug "instance ConstructionPlanetaire partagée
 * entre planètes" (combat Wiryxi/Juqav — voir
 * doc/fix/combat-construction-partagee-entre-planetes.md) : quand
 * plusieurs exemplaires d'un même bâtiment sont construits en une fois
 * sans planète précisée, Systeme.ajouterRichesses (branche
 * TRANSPORT_BATIMENT) répartit les exemplaires sur plusieurs planètes
 * différentes (équilibrage de charge). Avant correctif, une seule
 * instance ConstructionPlanetaire était réutilisée pour toutes les
 * planètes ; après correctif, chaque planète reçoit sa propre instance
 * distincte.
 */
class ConstructionPartageeEntrePlanetesTest {

    private static Batiment fabriquerBatiment(String codeDeBase, int niveau, int structure, int pointsConstruction) throws Exception {
        int[][] caracS = {{Const.BATIMENT_CAPACITE_NON_PRESENCE_HUMAINE, 1}};
        Constructor<Batiment> ctor = Batiment.class.getDeclaredConstructor(
                String.class, int.class, String[].class, int.class, int[][].class,
                int.class, float.class, int[][].class, int.class, int.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(codeDeBase, niveau, null, 0, caracS, 0, 100f, null, structure, pointsConstruction, null);
    }

    @Test
    void plusieursExemplairesSansPlaneteDedieeRecoiventChacunLeurPropreInstance() throws Exception {
        String code = "usineV"; // = codeDeBase("usine") + ROMAINS[4]("V")
        Batiment batiment = fabriquerBatiment("usine", 4, /*structure*/ 50, /*pointsConstruction*/ 10);
        assertEquals(code, batiment.getCode(), "vérifie que le code reconstitué correspond bien à celui utilisé pour le mock Univers.getTechnologie");

        Planete p1 = new Planete();
        p1.initialiserBatiments();
        p1.setProprio(10);

        Planete p2 = new Planete();
        p2.initialiserBatiments();
        p2.setProprio(10);

        Planete p3 = new Planete();
        p3.initialiserBatiments();
        p3.setProprio(10);

        Systeme systeme = new Systeme();
        systeme.setPlanetes(new Planete[]{p1, p2, p3});

        ObjetComplexeTransporte objet = new ObjetComplexeTransporte(code);
        objet.ajouterObjet(new ConstructionPlanetaire(code));
        objet.ajouterObjet(new ConstructionPlanetaire(code));
        objet.ajouterObjet(new ConstructionPlanetaire(code));
        assertEquals(3, objet.getNombreObjets(), "3 exemplaires distincts ont bien été construits (resolutionConstructions)");

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.existenceTechnologieBatiment(anyString())).thenReturn(true);
            univers.when(() -> Univers.getTechnologie(code)).thenReturn(batiment);

            // équivalent de : mettreEnChantier(..., 3, "usineV", planète non précisée)
            // -> Possession.resolutionConstructions -> Systeme.ajouterRichesses(numero, objet, Integer.MIN_VALUE)
            systeme.ajouterRichesses(10, objet, Integer.MIN_VALUE);
        }

        assertEquals(1, p1.getBatiments().length, "un exemplaire réparti sur p1");
        assertEquals(1, p2.getBatiments().length, "un exemplaire réparti sur p2");
        assertEquals(1, p3.getBatiments().length, "un exemplaire réparti sur p3");

        ConstructionPlanetaire cp1 = p1.getBatiments()[0];
        ConstructionPlanetaire cp2 = p2.getBatiments()[0];
        ConstructionPlanetaire cp3 = p3.getBatiments()[0];

        // Correctif vérifié : ce sont bien trois instances DISTINCTES, plus de partage par référence.
        assertNotSame(cp1, cp2, "p1 et p2 doivent désormais référencer des objets distincts");
        assertNotSame(cp2, cp3, "p2 et p3 doivent désormais référencer des objets distincts");
        assertNotSame(cp1, cp3, "p1 et p3 doivent désormais référencer des objets distincts");

        // Conséquence attendue : détruire le bâtiment sur p1 n'affecte plus p2 ni p3,
        // qui n'ont subi aucun combat.
        cp1.ajouterDommages(50);
        assertTrue(cp1.estDetruit());
        assertFalse(p2.getBatiments()[0].estDetruit(),
                "p2 n'a subi aucun combat : son exemplaire ne doit pas être affecté par ce qui arrive à p1");
        assertFalse(p3.getBatiments()[0].estDetruit(),
                "p3 n'a subi aucun combat : son exemplaire ne doit pas être affecté par ce qui arrive à p1");

        p1.eliminerPertesBatiments();
        assertEquals(0, p1.getBatiments().length, "balayé sur p1 (où le combat a eu lieu)");
        assertEquals(1, p2.getBatiments().length, "p2 conserve son propre exemplaire, intact");
        assertEquals(1, p3.getBatiments().length, "p3 conserve son propre exemplaire, intact");
    }
}
