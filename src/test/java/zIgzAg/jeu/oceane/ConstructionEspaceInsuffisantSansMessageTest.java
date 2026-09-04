package zIgzAg.jeu.oceane;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Vérifie le correctif du signalement "1 seul sur 10 Boucliers planétaires
 * VII s'est construit sans message d'erreur" (analyse/tour15, table
 * `construire` : joueur 10, position 0_4_20, code boucplaVII, nombre=10,
 * avancement=100 — soit 10 unités entièrement financées ce tour, mais dont
 * une seule apparaît réellement construite).
 *
 * Cause racine (voir doc/fix/construction-planetaire-espace-insuffisant-
 * silencieux.md) : dans Possession.resolutionConstructions, la variable
 * pasAssezDePlace (limite d'espace libre sur le système, l_espace) était
 * bien calculée et ajoutée au libellé "manqueL", mais la condition qui
 * déclenche l'émission d'un message d'avertissement
 * (EV_COMMANDANT_CONSTRUCTION_0002/0003) ne testait que
 * pasAssezDeCentaure/pasAssezDeMinerai/pasAssezDeMarchandises — jamais
 * pasAssezDePlace. Quand l'espace libre était l'UNIQUE facteur limitant
 * (argent, minerai, marchandises tous suffisants pour les 10 unités), le
 * code émettait un message de succès (EV_COMMANDANT_CONSTRUCTION_0001)
 * portant sur les seules unités effectivement sorties, sans jamais signaler
 * que les autres n'avaient pas pu sortir faute de place.
 *
 * Depuis "améliorations combat / encombrements" (#71), la limite d'espace
 * se calcule sur Batiment.getPointsEncombrement() (= points de construction)
 * plutôt que sur getPointsDeStructure() — d'où la valeur d'espace libre du
 * test, dimensionnée sur pointsParUnite et non plus sur la structure.
 */
class ConstructionEspaceInsuffisantSansMessageTest {

    @Test
    void dixUnitesFinancees_maisUneSeulePlaceLibre_declencheLeMessageDeManqueDePlace() throws Exception {
        String code = "boucplaVII";
        int pointsParUnite = 10;
        int nombreDemande = 10;
        int pointsDeStructureParUnite = 50;
        float prixUnitaire = 1f; // non limitant : budget très généreux ci-dessous
        int mineraiUnitaire = 1; // non limitant : stock très généreux ci-dessous

        Batiment batiment = new Batiment(code, 7, null, 0, null,
                mineraiUnitaire, prixUnitaire, null,
                pointsDeStructureParUnite, pointsParUnite, null);

        Possession possession = new Possession();
        possession.programmerConstruction(code);
        // Les 10 unités sont déjà entièrement financées (points effectués = nombre * pointsParUnite)
        possession.ajouterConstruction(new Construction(code, nombreDemande, Integer.MIN_VALUE));
        Construction[] enCours = possession.listeConstructions();
        enCours[0].setPointsEffectues(nombreDemande * pointsParUnite);

        Commandant commandant = mock(Commandant.class);
        Systeme systeme = mock(Systeme.class);
        Position position = new Position(0, 4, 20);

        when(commandant.getNumero()).thenReturn(10);
        when(commandant.getCentaures()).thenReturn(1_000_000f);
        when(commandant.getGouverneurSurPossession(position)).thenReturn(null);
        when(commandant.existenceGouverneurSurPossession(position)).thenReturn(false);

        when(systeme.getPosition()).thenReturn(position);
        // potentiel largement suffisant pour financer les 100 points nécessaires ce tour
        when(systeme.getPointsDeConstructionModifie(eq(10), isNull(), eq(possession), eq(position)))
                .thenReturn(nombreDemande * pointsParUnite);
        when(systeme.getStockMinerai(10)).thenReturn(1_000_000);
        // espace libre ne permet qu'UNE seule unité : la limite se calcule sur
        // Batiment.getPointsEncombrement() (= pointsParUnite), pas sur la structure.
        when(systeme.getEspaceLibre(10)).thenReturn(pointsParUnite);

        try (MockedStatic<Univers> univers = mockStatic(Univers.class)) {
            univers.when(() -> Univers.existenceTechnologie(anyString())).thenReturn(true);
            univers.when(() -> Univers.getTechnologie(code)).thenReturn(batiment);

            possession.resolutionConstructions(commandant, systeme);
        }

        // Après correctif : le manque de place déclenche bien le message d'avertissement
        // (nombreConstruit = "1/10"), et plus aucun message de succès muet n'est émis.
        verify(commandant, never()).ajouterEvenement(
                eq("EV_COMMANDANT_CONSTRUCTION_0001"), any(), any(), anyInt());
        verify(commandant, times(1)).ajouterEvenement(
                eq("EV_COMMANDANT_CONSTRUCTION_0002"), eq(position), eq("1/10"), any(), contains("espace"));

        // La file de construction ne contient bien plus qu'une demande de 9 unités restantes.
        Construction[] restant = possession.listeConstructions();
        assertEquals(1, restant.length);
        assertEquals(nombreDemande - 1, restant[0].getNombre(),
                "9 unités sur 10 restent à construire ce tour, faute de place");
    }
}
