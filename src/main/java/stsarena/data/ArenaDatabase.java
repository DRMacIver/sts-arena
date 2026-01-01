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
    private static final int SCHEMA_VERSION = 3;
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

            // Check if we need to recreate tables by checking if uuid column exists
            boolean needsRecreate = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(loadouts)")) {
                boolean hasUuid = false;
                while (rs.next()) {
                    if ("uuid".equals(rs.getString("name"))) {
                        hasUuid = true;
                        break;
                    }
                }
                // If table exists but doesn't have uuid, we need to recreate
                needsRecreate = !hasUuid;
            } catch (SQLException e) {
                // Table might not exist yet, which is fine
                logger.info("loadouts table doesn't exist yet, will create it");
            }

            if (needsRecreate) {
                logger.info("Schema needs upgrade - dropping old tables");
                stmt.execute("DROP TABLE IF EXISTS arena_runs");
                stmt.execute("DROP TABLE IF EXISTS loadouts");
                stmt.execute("DROP INDEX IF EXISTS idx_arena_runs_loadout");
                stmt.execute("DROP INDEX IF EXISTS idx_arena_runs_started_at");
                stmt.execute("DROP INDEX IF EXISTS idx_arena_runs_outcome");
                stmt.execute("DROP INDEX IF EXISTS idx_loadouts_uuid");
                stmt.execute("DROP INDEX IF EXISTS idx_loadouts_character");
            }

            // Loadouts table - saved deck/relic/potion configurations
            // uuid is the unique string identifier, id is for internal references
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
                "    created_at INTEGER NOT NULL," +
                "    ascension_level INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            // Add ascension_level column if it doesn't exist (migration)
            try {
                stmt.execute("ALTER TABLE loadouts ADD COLUMN ascension_level INTEGER NOT NULL DEFAULT 0");
                logger.info("Added ascension_level column to loadouts table");
            } catch (SQLException e) {
                // Column already exists, ignore
            }

            // Arena runs table - records of arena fights with outcomes
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS arena_runs (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    loadout_id INTEGER NOT NULL," +
                "    encounter_id TEXT NOT NULL," +
                "    started_at INTEGER NOT NULL," +
                "    ended_at INTEGER," +
                "    outcome TEXT," +  // 'VICTORY', 'DEFEAT', 'ABANDONED', or NULL if in progress
                "    starting_hp INTEGER NOT NULL," +
                "    ending_hp INTEGER," +
                "    potions_used_json TEXT," +  // JSON array of potion IDs used
                "    damage_dealt INTEGER," +
                "    damage_taken INTEGER," +
                "    turns_taken INTEGER," +
                "    cards_played INTEGER," +
                "    relics_triggered_json TEXT," +  // JSON array of relic IDs that had effects
                "    FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE" +
                ")"
            );

            // Indexes for common queries
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_arena_runs_loadout " +
                "ON arena_runs(loadout_id)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_arena_runs_started_at " +
                "ON arena_runs(started_at DESC)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_arena_runs_outcome " +
                "ON arena_runs(outcome)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_loadouts_uuid " +
                "ON loadouts(uuid)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_loadouts_character " +
                "ON loadouts(character_class)"
            );

            // Record schema version
            stmt.execute(
                "INSERT OR REPLACE INTO schema_version (version) VALUES (" + SCHEMA_VERSION + ")"
            );
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
