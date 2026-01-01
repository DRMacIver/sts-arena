package stsarena.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Tests for ArenaDatabase - verifies schema creation and basic operations.
 */
public class ArenaDatabaseTest {

    private ArenaDatabase db;
    private File tempDbFile;

    @Before
    public void setUp() throws Exception {
        // Create a temporary database file for testing
        tempDbFile = File.createTempFile("arena_test_", ".db");
        tempDbFile.deleteOnExit();
        db = ArenaDatabase.createTestInstance(tempDbFile.getAbsolutePath());
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }

    @Test
    public void testDatabaseCreation() {
        assertNotNull("Database should be created", db);
        assertNotNull("Connection should be available", db.getConnection());
    }

    @Test
    public void testSchemaCreation() throws Exception {
        Connection conn = db.getConnection();

        // Verify loadouts table exists with correct columns
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='loadouts'")) {
            assertTrue("loadouts table should exist", rs.next());
        }

        // Verify fight_history table exists
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='fight_history'")) {
            assertTrue("fight_history table should exist", rs.next());
        }

        // Verify schema_version table exists
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
            assertTrue("schema_version table should exist", rs.next());
        }
    }

    @Test
    public void testSchemaVersion() throws Exception {
        Connection conn = db.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
            assertTrue("Schema version should be recorded", rs.next());
            assertEquals("Schema version should be 1", 1, rs.getInt("version"));
        }
    }

    @Test
    public void testLoadoutsTableColumns() throws Exception {
        Connection conn = db.getConnection();

        // Get table info
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(loadouts)")) {

            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }

            assertTrue("Should have id column", columns.contains("id"));
            assertTrue("Should have name column", columns.contains("name"));
            assertTrue("Should have character_class column", columns.contains("character_class"));
            assertTrue("Should have deck_json column", columns.contains("deck_json"));
            assertTrue("Should have relics_json column", columns.contains("relics_json"));
            assertTrue("Should have potions_json column", columns.contains("potions_json"));
            assertTrue("Should have max_hp column", columns.contains("max_hp"));
            assertTrue("Should have current_hp column", columns.contains("current_hp"));
        }
    }

    @Test
    public void testFightHistoryTableColumns() throws Exception {
        Connection conn = db.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(fight_history)")) {

            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }

            assertTrue("Should have id column", columns.contains("id"));
            assertTrue("Should have encounter_id column", columns.contains("encounter_id"));
            assertTrue("Should have encounter_name column", columns.contains("encounter_name"));
            assertTrue("Should have result column", columns.contains("result"));
            assertTrue("Should have turns_taken column", columns.contains("turns_taken"));
            assertTrue("Should have damage_dealt column", columns.contains("damage_dealt"));
            assertTrue("Should have damage_taken column", columns.contains("damage_taken"));
        }
    }

    @Test
    public void testIndexesCreated() throws Exception {
        Connection conn = db.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'")) {

            java.util.Set<String> indexes = new java.util.HashSet<>();
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }

            assertTrue("Should have encounter index",
                indexes.contains("idx_fight_history_encounter"));
            assertTrue("Should have character index",
                indexes.contains("idx_fight_history_character"));
            assertTrue("Should have fought_at index",
                indexes.contains("idx_fight_history_fought_at"));
            assertTrue("Should have loadouts character index",
                indexes.contains("idx_loadouts_character"));
        }
    }

    @Test
    public void testCanInsertLoadout() throws Exception {
        Connection conn = db.getConnection();

        long now = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            int inserted = stmt.executeUpdate(
                "INSERT INTO loadouts (name, character_class, max_hp, current_hp, " +
                "deck_json, relics_json, potions_json, created_at, updated_at) " +
                "VALUES ('Test Loadout', 'IRONCLAD', 80, 75, " +
                "'[\"Strike\",\"Defend\"]', '[\"Burning Blood\"]', '[]', " +
                now + ", " + now + ")"
            );
            assertEquals("Should insert one row", 1, inserted);
        }

        // Verify the insert
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM loadouts WHERE name='Test Loadout'")) {
            assertTrue("Should find inserted loadout", rs.next());
            assertEquals("IRONCLAD", rs.getString("character_class"));
            assertEquals(80, rs.getInt("max_hp"));
            assertEquals(75, rs.getInt("current_hp"));
        }
    }

    @Test
    public void testCanInsertFightRecord() throws Exception {
        Connection conn = db.getConnection();

        long now = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            int inserted = stmt.executeUpdate(
                "INSERT INTO fight_history (encounter_id, encounter_name, character_class, " +
                "deck_json, relics_json, potions_json, result, turns_taken, " +
                "damage_dealt, damage_taken, fought_at) " +
                "VALUES ('3 Louse', 'Three Lice', 'THE_SILENT', " +
                "'[]', '[]', '[]', 'WIN', 5, 42, 8, " + now + ")"
            );
            assertEquals("Should insert one row", 1, inserted);
        }

        // Verify the insert
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT * FROM fight_history WHERE encounter_id='3 Louse'")) {
            assertTrue("Should find inserted fight record", rs.next());
            assertEquals("WIN", rs.getString("result"));
            assertEquals(5, rs.getInt("turns_taken"));
            assertEquals(42, rs.getInt("damage_dealt"));
        }
    }

    @Test
    public void testMultipleDatabaseInstances() throws Exception {
        // Create a second test database
        File tempDbFile2 = File.createTempFile("arena_test2_", ".db");
        tempDbFile2.deleteOnExit();
        ArenaDatabase db2 = ArenaDatabase.createTestInstance(tempDbFile2.getAbsolutePath());

        try {
            // Insert into first db
            try (Statement stmt = db.getConnection().createStatement()) {
                stmt.executeUpdate(
                    "INSERT INTO loadouts (name, character_class, max_hp, current_hp, " +
                    "deck_json, relics_json, potions_json, created_at, updated_at) " +
                    "VALUES ('DB1 Loadout', 'IRONCLAD', 80, 80, '[]', '[]', '[]', 0, 0)"
                );
            }

            // Insert into second db
            try (Statement stmt = db2.getConnection().createStatement()) {
                stmt.executeUpdate(
                    "INSERT INTO loadouts (name, character_class, max_hp, current_hp, " +
                    "deck_json, relics_json, potions_json, created_at, updated_at) " +
                    "VALUES ('DB2 Loadout', 'DEFECT', 75, 75, '[]', '[]', '[]', 0, 0)"
                );
            }

            // Verify isolation - db1 should only have DB1 Loadout
            try (Statement stmt = db.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM loadouts")) {
                rs.next();
                assertEquals("DB1 should have exactly 1 loadout", 1, rs.getInt("cnt"));
            }

            // db2 should only have DB2 Loadout
            try (Statement stmt = db2.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM loadouts")) {
                assertTrue(rs.next());
                assertEquals("DB2 Loadout", rs.getString("name"));
            }
        } finally {
            db2.close();
            tempDbFile2.delete();
        }
    }
}
