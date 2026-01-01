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
    private static final int SCHEMA_VERSION = 1;
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

            // Loadouts table - saved deck/relic/potion configurations
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS loadouts (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    name TEXT NOT NULL," +
                "    character_class TEXT NOT NULL," +
                "    ascension_level INTEGER DEFAULT 0," +
                "    max_hp INTEGER NOT NULL," +
                "    current_hp INTEGER NOT NULL," +
                "    gold INTEGER DEFAULT 0," +
                "    deck_json TEXT NOT NULL," +
                "    relics_json TEXT NOT NULL," +
                "    potions_json TEXT NOT NULL," +
                "    created_at INTEGER NOT NULL," +
                "    updated_at INTEGER NOT NULL" +
                ")"
            );

            // Fight history table - records of completed arena fights
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS fight_history (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    loadout_id INTEGER," +
                "    encounter_id TEXT NOT NULL," +
                "    encounter_name TEXT NOT NULL," +
                "    character_class TEXT NOT NULL," +
                "    ascension_level INTEGER DEFAULT 0," +
                "    deck_json TEXT NOT NULL," +
                "    relics_json TEXT NOT NULL," +
                "    potions_json TEXT NOT NULL," +
                "    result TEXT NOT NULL," +
                "    turns_taken INTEGER," +
                "    damage_dealt INTEGER," +
                "    damage_taken INTEGER," +
                "    floor_num INTEGER," +
                "    fought_at INTEGER NOT NULL," +
                "    FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE SET NULL" +
                ")"
            );

            // Indexes for common queries
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_fight_history_encounter " +
                "ON fight_history(encounter_id)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_fight_history_character " +
                "ON fight_history(character_class)"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_fight_history_fought_at " +
                "ON fight_history(fought_at DESC)"
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
