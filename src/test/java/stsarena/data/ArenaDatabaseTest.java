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

        // Verify arena_runs table exists
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='arena_runs'")) {
            assertTrue("arena_runs table should exist", rs.next());
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
            assertEquals("Schema version should be 5", 5, rs.getInt("version"));
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
            assertTrue("Should have uuid column", columns.contains("uuid"));
            assertTrue("Should have name column", columns.contains("name"));
            assertTrue("Should have character_class column", columns.contains("character_class"));
            assertTrue("Should have deck_json column", columns.contains("deck_json"));
            assertTrue("Should have relics_json column", columns.contains("relics_json"));
            assertTrue("Should have max_hp column", columns.contains("max_hp"));
            assertTrue("Should have current_hp column", columns.contains("current_hp"));
            assertTrue("Should have created_at column", columns.contains("created_at"));
        }
    }

    @Test
    public void testArenaRunsTableColumns() throws Exception {
        Connection conn = db.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(arena_runs)")) {

            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }

            assertTrue("Should have id column", columns.contains("id"));
            assertTrue("Should have loadout_id column", columns.contains("loadout_id"));
            assertTrue("Should have encounter_id column", columns.contains("encounter_id"));
            assertTrue("Should have outcome column", columns.contains("outcome"));
            assertTrue("Should have starting_hp column", columns.contains("starting_hp"));
            assertTrue("Should have ending_hp column", columns.contains("ending_hp"));
            assertTrue("Should have turns_taken column", columns.contains("turns_taken"));
            assertTrue("Should have damage_dealt column", columns.contains("damage_dealt"));
            assertTrue("Should have damage_taken column", columns.contains("damage_taken"));
            assertTrue("Should have potions_used_json column", columns.contains("potions_used_json"));
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

            assertTrue("Should have arena_runs loadout index",
                indexes.contains("idx_arena_runs_loadout"));
            assertTrue("Should have arena_runs started_at index",
                indexes.contains("idx_arena_runs_started_at"));
            assertTrue("Should have loadouts uuid index",
                indexes.contains("idx_loadouts_uuid"));
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
                "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, " +
                "deck_json, relics_json, created_at) " +
                "VALUES ('test-uuid-123', 'Test Loadout', 'IRONCLAD', 80, 75, " +
                "'[\"Strike\",\"Defend\"]', '[\"Burning Blood\"]', " + now + ")"
            );
            assertEquals("Should insert one row", 1, inserted);
        }

        // Verify the insert
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM loadouts WHERE name='Test Loadout'")) {
            assertTrue("Should find inserted loadout", rs.next());
            assertEquals("IRONCLAD", rs.getString("character_class"));
            assertEquals("test-uuid-123", rs.getString("uuid"));
            assertEquals(80, rs.getInt("max_hp"));
            assertEquals(75, rs.getInt("current_hp"));
        }
    }

    @Test
    public void testCanInsertArenaRun() throws Exception {
        Connection conn = db.getConnection();

        // First insert a loadout to reference
        long now = System.currentTimeMillis();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, " +
                "deck_json, relics_json, created_at) " +
                "VALUES ('run-test-uuid', 'Run Test Loadout', 'THE_SILENT', 70, 70, '[]', '[]', " + now + ")"
            );
        }

        // Get the loadout id
        long loadoutId;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM loadouts WHERE uuid='run-test-uuid'")) {
            assertTrue(rs.next());
            loadoutId = rs.getLong("id");
        }

        // Insert arena run
        try (Statement stmt = conn.createStatement()) {
            int inserted = stmt.executeUpdate(
                "INSERT INTO arena_runs (loadout_id, encounter_id, started_at, " +
                "outcome, starting_hp, ending_hp, damage_dealt, damage_taken, turns_taken) " +
                "VALUES (" + loadoutId + ", '3 Louse', " + now + ", " +
                "'VICTORY', 70, 62, 42, 8, 5)"
            );
            assertEquals("Should insert one row", 1, inserted);
        }

        // Verify the insert
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT * FROM arena_runs WHERE encounter_id='3 Louse'")) {
            assertTrue("Should find inserted arena run", rs.next());
            assertEquals("VICTORY", rs.getString("outcome"));
            assertEquals(5, rs.getInt("turns_taken"));
            assertEquals(42, rs.getInt("damage_dealt"));
            assertEquals(62, rs.getInt("ending_hp"));
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
                    "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, " +
                    "deck_json, relics_json, created_at) " +
                    "VALUES ('db1-uuid', 'DB1 Loadout', 'IRONCLAD', 80, 80, '[]', '[]', 0)"
                );
            }

            // Insert into second db
            try (Statement stmt = db2.getConnection().createStatement()) {
                stmt.executeUpdate(
                    "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, " +
                    "deck_json, relics_json, created_at) " +
                    "VALUES ('db2-uuid', 'DB2 Loadout', 'DEFECT', 75, 75, '[]', '[]', 0)"
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
