package com.restrovision;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FlywayProductionVerificationTest {

    @Test
    @DisplayName("Flyway Scenario 1: Migration behavior on an empty database")
    void testEmptyDatabaseMigration() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway_empty_db;MODE=Oracle;DB_CLOSE_DELAY=-1";
        
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        // 1. Run migrate on empty database
        int applied = flyway.migrate().migrationsExecuted;
        System.out.println("[Flyway Scenario 1] Migrations executed on empty DB: " + applied);

        // Verify V1 was executed
        MigrationInfo current = flyway.info().current();
        System.out.println("[Flyway Scenario 1] Current version: " + current.getVersion().getVersion());
        System.out.println("[Flyway Scenario 1] Current state: " + current.getState());
        
        assertThat(applied).isEqualTo(1);
        assertThat(current.getVersion().getVersion()).isEqualTo("1");
        assertThat(current.getState().name()).isEqualTo("SUCCESS");

        // Verify tables exist
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM restaurants")) {
            assertThat(rs.next()).isTrue();
            System.out.println("[Flyway Scenario 1] Table 'restaurants' successfully verified in empty DB.");
        }
    }

    @Test
    @DisplayName("Flyway Scenario 2A: Investigation - Does baseline-version 0 cause V1 to execute against pre-existing schema?")
    void testBaselineVersionZeroOnPreExistingSchema() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway_existing_db_v0;MODE=Oracle;DB_CLOSE_DELAY=-1";

        // Pre-create tables simulating an existing production database initialized by DBAs before Flyway
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE restaurants (restaurant_id NUMBER PRIMARY KEY, name VARCHAR2(100))");
            stmt.execute("INSERT INTO restaurants (restaurant_id, name) VALUES (1, 'Legacy Pre-Flyway Restaurant')");
            System.out.println("[Flyway Scenario 2A] Pre-existing schema and 1 restaurant record created.");
        }

        // Configure Flyway with baseline-version: 0 on a non-empty DB
        Flyway flywayV0 = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        // Because baselineVersion is 0, Flyway baselines at 0 and then attempts to execute V1 (since 1 > 0).
        // This will collide with the pre-existing 'restaurants' table!
        Exception exception = assertThrows(FlywayException.class, () -> {
            flywayV0.migrate();
        });

        System.out.println("[Flyway Scenario 2A] Confirmed Behavior: baseline-version 0 causes V1 execution attempt and throws: " + exception.getMessage());
        assertThat(exception.getMessage()).containsIgnoringCase("restaurants");
    }

    @Test
    @DisplayName("Flyway Scenario 2B: Recommended Production Baseline (baseline-version: 1) on pre-existing database")
    void testBaselineVersionOneOnPreExistingSchemaPreservesData() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway_existing_db_v1;MODE=Oracle;DB_CLOSE_DELAY=-1";

        // Pre-create tables and populate existing production records
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE restaurants (restaurant_id NUMBER PRIMARY KEY, name VARCHAR2(100), timezone VARCHAR2(64), status VARCHAR2(20), created_at TIMESTAMP)");
            stmt.execute("INSERT INTO restaurants (restaurant_id, name, timezone, status, created_at) VALUES (42, 'Existing Enterprise Branch', 'Asia/Kolkata', 'ACTIVE', CURRENT_TIMESTAMP)");
            System.out.println("[Flyway Scenario 2B] Existing database initialized with restaurant ID 42.");
        }

        // Configure Flyway with baseline-version: 1
        Flyway flywayV1 = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        int applied = flywayV1.migrate().migrationsExecuted;
        System.out.println("[Flyway Scenario 2B] Migrations executed on existing DB with baseline-version 1: " + applied);

        MigrationInfo current = flywayV1.info().current();
        System.out.println("[Flyway Scenario 2B] Current migration state: " + current.getState());
        System.out.println("[Flyway Scenario 2B] Current version: " + current.getVersion().getVersion());
        System.out.println("[Flyway Scenario 2B] Migration type: " + current.getType());

        assertThat(applied).isEqualTo(0); // V1 was NOT re-run
        assertThat(current.getVersion().getVersion()).isEqualTo("1");
        assertThat(current.getType().name()).isEqualTo("BASELINE");

        // Verify existing application records remain completely unchanged
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT restaurant_id, name FROM restaurants WHERE restaurant_id = 42")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Existing Enterprise Branch");
            System.out.println("[Flyway Scenario 2B] Verified: Pre-existing record ID 42 is intact and unmodified!");
        }
    }
}
