package zIgzAg.sql;

import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test basique vérifiant le fonctionnement correct de la construction de
 * fragments SQL de SessionSQL (utilisée par ReceptionOrdres, ProductionOrdres
 * et InputSQLWriter pour construire les requêtes SELECT/INSERT du moteur).
 */
class SessionSQLTest {

    private final SessionSQL session = new SessionSQL() {
        @Override
        public Connection getConnection(String host, String base, String login, String motDePasse) {
            return null;
        }

        @Override
        public int nombreDeLignesTable(Connection c, String nomTable) {
            return 0;
        }

        @Override
        public String[] getNomsColonnes(Connection c, String nomTable) {
            return new String[0];
        }

        @Override
        public String[] getTypesColonnes(Connection c, String nomTable) {
            return new String[0];
        }

        @Override
        public String[] listeTables(Connection c) {
            return new String[0];
        }
    };

    @Test
    void champsTraduction1_separeLesChampsParDesVirgules() {
        assertEquals("NOM,PRENOM", session.champsTraduction1(new String[]{"NOM", "PRENOM"}));
    }

    @Test
    void champsTraduction2_separeEtEntoureLesValeursDeQuotes() {
        assertEquals("'a','b'", session.champsTraduction2(new String[]{"a", "b"}));
    }

    @Test
    void champsTraduction3_construitDesConditionsEgaliteJointesParAnd() {
        assertEquals("NUMERO='1' AND RACE='humain'",
                session.champsTraduction3(new String[]{"NUMERO", "RACE"}, new String[]{"1", "humain"}));
    }

    @Test
    void champsTraduction4_construitDesDeclarationsDeColonnesTypees() {
        assertEquals("num INT,nom VARCHAR(255)",
                session.champsTraduction4(new String[]{"num", "nom"}, new String[]{"INT", "VARCHAR(255)"}));
    }
}
