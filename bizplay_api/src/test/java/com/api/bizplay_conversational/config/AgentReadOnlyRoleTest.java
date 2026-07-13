package com.api.bizplay_conversational.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Proves the Phase-1 database-level guardrail: the {@code bizplay_agent_ro} role used by
 * the database-lookup sub-agent can SELECT the allow-listed tables but is physically unable
 * to INSERT/UPDATE/DELETE or run DDL, because Postgres runs its sessions read-only.
 *
 * <p>Pure JDBC via {@link DriverManager} (no Spring context). It connects to the local DB
 * as the admin user to provision the role, then opens a fresh session as the read-only role
 * to verify behaviour. If the database is not reachable, the test is skipped rather than
 * failed. Credentials come from the same env vars the app uses, with local defaults.
 */
class AgentReadOnlyRoleTest {

    private static final String URL = env("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/bizplay");
    private static final String ADMIN_USER = env("SPRING_DATASOURCE_USERNAME", "postgres");
    private static final String ADMIN_PW = env("SPRING_DATASOURCE_PASSWORD", "123");

    private static final String RO_USER = "bizplay_agent_ro";
    // Matches the local default in application.properties so this test also provisions the
    // role the app's agent datasource will use locally.
    private static final String RO_PW =
            env("APP_CONVERSATIONAL_AGENT_DATASOURCE_PASSWORD", "agent_ro_change_me");

    /** Allow-listed lookup tables (mirrors DatabaseLookupAgentServiceImple / schemaV2.sql). */
    private static final List<String> ALLOWED_TABLES = List.of(
            "conversational_department", "conversational_staff", "conversational_traveler",
            "conversational_trip_plan", "conversational_trip_report", "conversational_cost_expense",
            "conversational_transportation_expense", "conversational_attachment",
            "conversational_agent_session", "corp", "corp_group");

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    @Test
    void readOnlyRoleCanSelectButCannotWrite() throws Exception {
        Connection admin;
        try {
            admin = DriverManager.getConnection(URL, ADMIN_USER, ADMIN_PW);
        } catch (SQLException e) {
            assumeTrue(false, "Postgres not reachable at " + URL + " (" + e.getMessage() + ") -> skipping.");
            return;
        }

        String selectTable;
        try (admin) {
            List<String> existing = existingAllowedTables(admin);
            assumeTrue(!existing.isEmpty(),
                    "None of the allow-listed tables exist yet (schema not applied) -> skipping.");
            // Prefer 'corp' for the write test (it is seeded); else the first existing table.
            selectTable = existing.contains("corp") ? "corp" : existing.get(0);
            provisionReadOnlyRole(admin, existing);
        }

        // Fresh session so the role-level SET default_transaction_read_only takes effect.
        try (Connection ro = DriverManager.getConnection(URL, RO_USER, RO_PW)) {

            // 1) SELECT must succeed.
            try (Statement st = ro.createStatement();
                 ResultSet rs = st.executeQuery("SELECT count(*) FROM " + selectTable)) {
                assertTrue(rs.next(), "SELECT should return a row");
            }

            // 2) Every write path must be rejected by Postgres (read-only transaction).
            assertThrows(SQLException.class, () -> exec(ro,
                    "INSERT INTO " + selectTable + " DEFAULT VALUES"), "INSERT must be rejected");
            assertThrows(SQLException.class, () -> exec(ro,
                    "UPDATE " + selectTable + " SET corp_name = corp_name"), "UPDATE must be rejected");
            assertThrows(SQLException.class, () -> exec(ro,
                    "DELETE FROM " + selectTable), "DELETE must be rejected");
            assertThrows(SQLException.class, () -> exec(ro,
                    "CREATE TABLE agent_ro_should_not_exist (id int)"), "DDL must be rejected");
            assertThrows(SQLException.class, () -> exec(ro,
                    "DROP TABLE " + selectTable), "DROP must be rejected");
        }
    }

    private List<String> existingAllowedTables(Connection con) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ANY (?)";
        try (var ps = con.prepareStatement(sql)) {
            ps.setArray(1, con.createArrayOf("text", ALLOWED_TABLES.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    private void provisionReadOnlyRole(Connection admin, List<String> tables) throws SQLException {
        try (Statement st = admin.createStatement()) {
            st.execute("DO $$ BEGIN "
                    + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + RO_USER + "') THEN "
                    + "CREATE ROLE " + RO_USER + " LOGIN PASSWORD '" + RO_PW + "'; END IF; END $$;");
            st.execute("ALTER ROLE " + RO_USER + " WITH PASSWORD '" + RO_PW + "'");
            st.execute("ALTER ROLE " + RO_USER + " SET default_transaction_read_only = on");
            st.execute("ALTER ROLE " + RO_USER + " SET statement_timeout = '3s'");
            st.execute("GRANT USAGE ON SCHEMA public TO " + RO_USER);
            for (String t : tables) {
                st.execute("GRANT SELECT ON " + t + " TO " + RO_USER);
            }
        }
    }

    private void exec(Connection con, String sql) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }
}
