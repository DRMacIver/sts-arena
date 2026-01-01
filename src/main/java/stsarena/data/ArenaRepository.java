package stsarena.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsarena.arena.RandomLoadoutGenerator;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for saving and loading arena data from the database.
 */
public class ArenaRepository {

    private static final Logger logger = LogManager.getLogger(ArenaRepository.class.getName());
    private static final Gson gson = new Gson();

    private final ArenaDatabase database;

    public ArenaRepository(ArenaDatabase database) {
        this.database = database;
    }

    /**
     * Save a loadout to the database.
     * Returns the database ID of the saved loadout.
     */
    public long saveLoadout(RandomLoadoutGenerator.GeneratedLoadout loadout) {
        logger.info("saveLoadout called for: " + loadout.name + " (id=" + loadout.id + ")");

        String sql = "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, created_at, ascension_level) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = database.getConnection();
        if (conn == null) {
            logger.error("saveLoadout: Database connection is null!");
            return -1;
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, loadout.id);
            stmt.setString(2, loadout.name);
            stmt.setString(3, loadout.playerClass.name());
            stmt.setInt(4, loadout.maxHp);
            stmt.setInt(5, loadout.currentHp);
            stmt.setString(6, serializeDeck(loadout.deck));
            stmt.setString(7, serializeRelics(loadout.relics));
            stmt.setString(8, serializePotions(loadout.potions));
            stmt.setLong(9, loadout.createdAt);
            stmt.setInt(10, loadout.ascensionLevel);

            logger.info("saveLoadout: Executing insert...");
            int rows = stmt.executeUpdate();
            logger.info("saveLoadout: Insert returned " + rows + " rows");

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.info("Saved loadout '" + loadout.name + "' with database ID: " + id);
                    return id;
                } else {
                    logger.error("saveLoadout: No generated key returned!");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save loadout: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Start a new arena run. Returns the run ID.
     */
    public long startArenaRun(long loadoutId, String encounterId, int startingHp) {
        String sql = "INSERT INTO arena_runs (loadout_id, encounter_id, started_at, starting_hp) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, loadoutId);
            stmt.setString(2, encounterId);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setInt(4, startingHp);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.info("Started arena run " + id + " with encounter: " + encounterId);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to start arena run", e);
        }
        return -1;
    }

    /**
     * Complete an arena run with the outcome.
     */
    public void completeArenaRun(long runId, ArenaRunOutcome outcome) {
        String sql = "UPDATE arena_runs SET " +
                     "ended_at = ?, outcome = ?, ending_hp = ?, potions_used_json = ?, " +
                     "damage_dealt = ?, damage_taken = ?, turns_taken = ?, cards_played = ?, " +
                     "relics_triggered_json = ? " +
                     "WHERE id = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, outcome.result.name());
            stmt.setInt(3, outcome.endingHp);
            stmt.setString(4, gson.toJson(outcome.potionsUsed));
            stmt.setInt(5, outcome.damageDealt);
            stmt.setInt(6, outcome.damageTaken);
            stmt.setInt(7, outcome.turnsTaken);
            stmt.setInt(8, outcome.cardsPlayed);
            stmt.setString(9, gson.toJson(outcome.relicsTriggered));
            stmt.setLong(10, runId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Completed arena run " + runId + " with outcome: " + outcome.result);
            }
        } catch (SQLException e) {
            logger.error("Failed to complete arena run", e);
        }
    }

    /**
     * Get recent arena runs for display in statistics.
     * Deletes incomplete runs (from crashes) and only returns completed runs.
     */
    public List<ArenaRunRecord> getRecentRuns(int limit) {
        // First, delete any incomplete runs (outcome is NULL means crashed/abandoned)
        try (Statement stmt = database.getConnection().createStatement()) {
            int deleted = stmt.executeUpdate("DELETE FROM arena_runs WHERE outcome IS NULL");
            if (deleted > 0) {
                logger.info("Cleaned up " + deleted + " incomplete arena run(s)");
            }
        } catch (SQLException e) {
            logger.error("Failed to clean up incomplete runs", e);
        }

        String sql = "SELECT r.id, r.loadout_id, r.started_at, r.ended_at, r.outcome, r.starting_hp, r.ending_hp, " +
                     "r.encounter_id, r.potions_used_json, r.damage_dealt, r.damage_taken, r.turns_taken, " +
                     "l.name as loadout_name, l.character_class " +
                     "FROM arena_runs r " +
                     "JOIN loadouts l ON r.loadout_id = l.id " +
                     "WHERE r.outcome IS NOT NULL " +
                     "ORDER BY r.started_at DESC " +
                     "LIMIT ?";

        List<ArenaRunRecord> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ArenaRunRecord record = new ArenaRunRecord();
                    record.id = rs.getLong("id");
                    record.loadoutId = rs.getLong("loadout_id");
                    record.startedAt = rs.getLong("started_at");
                    record.endedAt = rs.getLong("ended_at");
                    record.outcome = rs.getString("outcome");
                    record.startingHp = rs.getInt("starting_hp");
                    record.endingHp = rs.getInt("ending_hp");
                    record.encounterId = rs.getString("encounter_id");
                    record.loadoutName = rs.getString("loadout_name");
                    record.characterClass = rs.getString("character_class");
                    record.damageDealt = rs.getInt("damage_dealt");
                    record.damageTaken = rs.getInt("damage_taken");
                    record.turnsTaken = rs.getInt("turns_taken");

                    String potionsJson = rs.getString("potions_used_json");
                    if (potionsJson != null) {
                        Type listType = new TypeToken<List<String>>(){}.getType();
                        record.potionsUsed = gson.fromJson(potionsJson, listType);
                    }

                    results.add(record);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get recent runs", e);
        }

        return results;
    }

    /**
     * Get statistics summary.
     */
    public ArenaStats getStats() {
        ArenaStats stats = new ArenaStats();

        String totalSql = "SELECT COUNT(*) as total, " +
                          "SUM(CASE WHEN outcome = 'VICTORY' THEN 1 ELSE 0 END) as wins, " +
                          "SUM(CASE WHEN outcome = 'DEFEAT' THEN 1 ELSE 0 END) as losses " +
                          "FROM arena_runs WHERE outcome IS NOT NULL";

        try (Statement stmt = database.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(totalSql)) {
            if (rs.next()) {
                stats.totalRuns = rs.getInt("total");
                stats.wins = rs.getInt("wins");
                stats.losses = rs.getInt("losses");
            }
        } catch (SQLException e) {
            logger.error("Failed to get stats", e);
        }

        return stats;
    }

    private String serializeDeck(List<AbstractCard> deck) {
        List<CardData> cards = new ArrayList<>();
        for (AbstractCard card : deck) {
            cards.add(new CardData(card.cardID, card.timesUpgraded));
        }
        return gson.toJson(cards);
    }

    private String serializeRelics(List<AbstractRelic> relics) {
        List<String> relicIds = new ArrayList<>();
        for (AbstractRelic relic : relics) {
            relicIds.add(relic.relicId);
        }
        return gson.toJson(relicIds);
    }

    private String serializePotions(List<AbstractPotion> potions) {
        List<String> potionIds = new ArrayList<>();
        if (potions != null) {
            for (AbstractPotion potion : potions) {
                potionIds.add(potion.ID);
            }
        }
        return gson.toJson(potionIds);
    }

    /**
     * Simple card data for JSON serialization.
     */
    public static class CardData {
        public String id;
        public int upgrades;

        public CardData(String id, int upgrades) {
            this.id = id;
            this.upgrades = upgrades;
        }
    }

    /**
     * Outcome of an arena run.
     */
    public static class ArenaRunOutcome {
        public RunResult result;
        public int endingHp;
        public List<String> potionsUsed = new ArrayList<>();
        public int damageDealt;
        public int damageTaken;
        public int turnsTaken;
        public int cardsPlayed;
        public List<String> relicsTriggered = new ArrayList<>();

        public enum RunResult {
            VICTORY, DEFEAT, ABANDONED
        }
    }

    /**
     * Record of an arena run for display.
     */
    public static class ArenaRunRecord {
        public long id;
        public long loadoutId;
        public long startedAt;
        public long endedAt;
        public String outcome;
        public int startingHp;
        public int endingHp;
        public String encounterId;
        public String loadoutName;
        public String characterClass;
        public List<String> potionsUsed;
        public int damageDealt;
        public int damageTaken;
        public int turnsTaken;
    }

    /**
     * Summary statistics.
     */
    public static class ArenaStats {
        public int totalRuns;
        public int wins;
        public int losses;

        public double getWinRate() {
            return totalRuns > 0 ? (double) wins / totalRuns : 0;
        }
    }

    /**
     * Get a specific loadout by database ID.
     */
    public LoadoutRecord getLoadoutById(long loadoutId) {
        String sql = "SELECT id, uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, created_at, ascension_level " +
                     "FROM loadouts WHERE id = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, loadoutId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LoadoutRecord record = new LoadoutRecord();
                    record.dbId = rs.getLong("id");
                    record.uuid = rs.getString("uuid");
                    record.name = rs.getString("name");
                    record.characterClass = rs.getString("character_class");
                    record.maxHp = rs.getInt("max_hp");
                    record.currentHp = rs.getInt("current_hp");
                    record.deckJson = rs.getString("deck_json");
                    record.relicsJson = rs.getString("relics_json");
                    record.potionsJson = rs.getString("potions_json");
                    record.createdAt = rs.getLong("created_at");
                    record.ascensionLevel = rs.getInt("ascension_level");
                    return record;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get loadout by ID: " + loadoutId, e);
        }

        return null;
    }

    /**
     * Get saved loadouts for selection.
     */
    public List<LoadoutRecord> getLoadouts(int limit) {
        String sql = "SELECT id, uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, created_at, ascension_level " +
                     "FROM loadouts ORDER BY created_at DESC LIMIT ?";

        List<LoadoutRecord> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LoadoutRecord record = new LoadoutRecord();
                    record.dbId = rs.getLong("id");
                    record.uuid = rs.getString("uuid");
                    record.name = rs.getString("name");
                    record.characterClass = rs.getString("character_class");
                    record.maxHp = rs.getInt("max_hp");
                    record.currentHp = rs.getInt("current_hp");
                    record.deckJson = rs.getString("deck_json");
                    record.relicsJson = rs.getString("relics_json");
                    record.potionsJson = rs.getString("potions_json");
                    record.createdAt = rs.getLong("created_at");
                    record.ascensionLevel = rs.getInt("ascension_level");
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get loadouts", e);
        }

        return results;
    }

    /**
     * Record of a saved loadout.
     */
    public static class LoadoutRecord {
        public long dbId;
        public String uuid;
        public String name;
        public String characterClass;
        public int maxHp;
        public int currentHp;
        public String deckJson;
        public String relicsJson;
        public String potionsJson;
        public long createdAt;
        public int ascensionLevel;
    }

    /**
     * Get encounter outcomes for a specific loadout.
     * Returns a map of encounter ID to outcome (VICTORY or DEFEAT).
     * If an encounter was faced multiple times, returns the most recent outcome.
     */
    public Map<String, String> getEncounterOutcomesForLoadout(long loadoutId) {
        String sql = "SELECT encounter_id, outcome FROM arena_runs " +
                     "WHERE loadout_id = ? AND outcome IS NOT NULL " +
                     "ORDER BY started_at DESC";

        Map<String, String> results = new HashMap<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, loadoutId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String encounterId = rs.getString("encounter_id");
                    String outcome = rs.getString("outcome");
                    // Only keep the first (most recent) outcome for each encounter
                    if (!results.containsKey(encounterId)) {
                        results.put(encounterId, outcome);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get encounter outcomes", e);
        }

        return results;
    }
}
