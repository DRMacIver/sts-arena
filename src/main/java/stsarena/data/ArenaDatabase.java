package stsarena.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.sql.*;

/**
 * Manages SQLite database for arena loadouts and fight history.
 *
 * Database is stored in ~/Library/Preferences/ModTheSpire/stsarena/arena.db (macOS)
 * or equivalent platform-specific location.
 */
public class ArenaDatabase {

    private static final String DB_NAME = "arena.db";
    private static final int SCHEMA_VERSION = 7;
    private static final Logger logger = LogManager.getLogger(ArenaDatabase.class.getName());

    private static ArenaDatabase instance;
    private Connection connection;
    private final String dbPath;

    private ArenaDatabase() {
        this.dbPath = getDefaultDbPath();
        initialize();
    }

    /**
     * Constructor for testing - allows specifying custom database path.
     */
    ArenaDatabase(String customDbPath) {
        this.dbPath = customDbPath;
        initialize();
    }

    public static synchronized ArenaDatabase getInstance() {
        if (instance == null) {
            instance = new ArenaDatabase();
        }
        return instance;
    }

    /**
     * Reset the singleton instance. Used for testing only.
     */
    static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    /**
     * Create a test instance with a custom database path.
     */
    public static ArenaDatabase createTestInstance(String dbPath) {
        return new ArenaDatabase(dbPath);
    }

    private static String getDefaultDbPath() {
        String modDir;
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            modDir = System.getenv("LOCALAPPDATA") + File.separator + "ModTheSpire";
        } else if (os.contains("mac")) {
            modDir = System.getProperty("user.home") + "/Library/Preferences/ModTheSpire";
        } else {
            // Linux and others
            modDir = System.getProperty("user.home") + "/.config/ModTheSpire";
        }

        String arenaDir = modDir + File.separator + "stsarena";
        new File(arenaDir).mkdirs();

        return arenaDir + File.separator + DB_NAME;
    }

    private void initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);

            createSchema();
            logger.info("Arena database initialized at: " + dbPath);
        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Schema version tracking
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                "    version INTEGER PRIMARY KEY" +
                ")"
            );

            // Get current schema version
            int currentVersion = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
                if (rs.next()) {
                    currentVersion = rs.getInt("version");
                }
            }

            logger.info("Current schema version: " + currentVersion + ", target: " + SCHEMA_VERSION);

            // Run migrations incrementally
            if (currentVersion < 1) {
                migrateToV1(stmt);
            }
            if (currentVersion < 2) {
                migrateToV2(stmt);
            }
            if (currentVersion < 3) {
                migrateToV3(stmt);
            }
            if (currentVersion < 4) {
                migrateToV4(stmt);
            }
            if (currentVersion < 5) {
                migrateToV5(stmt);
            }
            if (currentVersion < 6) {
                migrateToV6(stmt);
            }
            if (currentVersion < 7) {
                migrateToV7(stmt);
            }

            // Record schema version
            stmt.execute(
                "INSERT OR REPLACE INTO schema_version (version) VALUES (" + SCHEMA_VERSION + ")"
            );
        }
    }

    /**
     * V1: Initial schema - loadouts and arena_runs tables
     */
    private void migrateToV1(Statement stmt) throws SQLException {
        logger.info("Running migration to V1: creating base tables");

        // Loadouts table
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS loadouts (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    uuid TEXT NOT NULL UNIQUE," +
            "    name TEXT NOT NULL," +
            "    character_class TEXT NOT NULL," +
            "    max_hp INTEGER NOT NULL," +
            "    current_hp INTEGER NOT NULL," +
            "    deck_json TEXT NOT NULL," +
            "    relics_json TEXT NOT NULL," +
            "    created_at INTEGER NOT NULL" +
            ")"
        );

        // Arena runs table
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS arena_runs (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    loadout_id INTEGER NOT NULL," +
            "    encounter_id TEXT NOT NULL," +
            "    started_at INTEGER NOT NULL," +
            "    ended_at INTEGER," +
            "    outcome TEXT," +
            "    starting_hp INTEGER NOT NULL," +
            "    ending_hp INTEGER," +
            "    potions_used_json TEXT," +
            "    damage_dealt INTEGER," +
            "    damage_taken INTEGER," +
            "    turns_taken INTEGER," +
            "    cards_played INTEGER," +
            "    relics_triggered_json TEXT," +
            "    FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE" +
            ")"
        );

        // Indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_runs_loadout ON arena_runs(loadout_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_runs_started_at ON arena_runs(started_at DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_runs_outcome ON arena_runs(outcome)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_loadouts_uuid ON loadouts(uuid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_loadouts_character ON loadouts(character_class)");
    }

    /**
     * V2: Add ascension_level column
     */
    private void migrateToV2(Statement stmt) throws SQLException {
        logger.info("Running migration to V2: adding ascension_level");
        addColumnIfNotExists(stmt, "loadouts", "ascension_level", "INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * V3: Reserved (was a failed migration attempt)
     */
    private void migrateToV3(Statement stmt) throws SQLException {
        // No-op, kept for version continuity
    }

    /**
     * V4: Add potions_json column
     */
    private void migrateToV4(Statement stmt) throws SQLException {
        logger.info("Running migration to V4: adding potions_json");
        addColumnIfNotExists(stmt, "loadouts", "potions_json", "TEXT");
    }

    /**
     * V5: Add potion_slots column
     */
    private void migrateToV5(Statement stmt) throws SQLException {
        logger.info("Running migration to V5: adding potion_slots");
        addColumnIfNotExists(stmt, "loadouts", "potion_slots", "INTEGER NOT NULL DEFAULT 3");
    }

    /**
     * V6: Add content_hash for loadout versioning and snapshot columns to arena_runs
     */
    private void migrateToV6(Statement stmt) throws SQLException {
        logger.info("Running migration to V6: adding content_hash and run snapshots");

        // Add content_hash to loadouts table
        addColumnIfNotExists(stmt, "loadouts", "content_hash", "TEXT");

        // Add snapshot columns to arena_runs for version tracking
        addColumnIfNotExists(stmt, "arena_runs", "deck_json", "TEXT");
        addColumnIfNotExists(stmt, "arena_runs", "relics_json", "TEXT");
        addColumnIfNotExists(stmt, "arena_runs", "potions_json", "TEXT");
        addColumnIfNotExists(stmt, "arena_runs", "content_hash", "TEXT");

        // Create index for content_hash lookups
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_arena_runs_content_hash ON arena_runs(content_hash)");
        } catch (SQLException e) {
            // Index may already exist
            logger.info("Index idx_arena_runs_content_hash may already exist");
        }
    }

    /**
     * V7: Add is_favorite column for pinning loadouts
     */
    private void migrateToV7(Statement stmt) throws SQLException {
        logger.info("Running migration to V7: adding is_favorite column");
        addColumnIfNotExists(stmt, "loadouts", "is_favorite", "INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * Helper to add a column if it doesn't exist.
     */
    private void addColumnIfNotExists(Statement stmt, String table, String column, String definition) throws SQLException {
        // Check if column exists
        boolean exists = false;
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }

        if (!exists) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            logger.info("Added column " + column + " to " + table);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Close the database connection. Should be called when the game exits.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Arena database closed");
            } catch (SQLException e) {
                logger.error("Error closing database", e);
            }
        }
    }
}
