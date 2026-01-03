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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        String deckJson = serializeDeck(loadout.deck);
        String relicsJson = serializeRelics(loadout.relics);
        String potionsJson = serializePotions(loadout.potions);
        String contentHash = computeContentHash(deckJson, relicsJson, potionsJson);

        String sql = "INSERT INTO loadouts (uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, potion_slots, created_at, ascension_level, content_hash) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            stmt.setString(6, deckJson);
            stmt.setString(7, relicsJson);
            stmt.setString(8, potionsJson);
            stmt.setInt(9, loadout.potionSlots);
            stmt.setLong(10, loadout.createdAt);
            stmt.setInt(11, loadout.ascensionLevel);
            stmt.setString(12, contentHash);

            logger.info("saveLoadout: Executing insert...");
            int rows = stmt.executeUpdate();
            logger.info("saveLoadout: Insert returned " + rows + " rows");

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.info("Saved loadout '" + loadout.name + "' with database ID: " + id + ", contentHash: " + contentHash);
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
     * Compute a content hash for a loadout's deck, relics, and potions.
     * Used for version tracking - if the hash changes, the loadout was modified.
     */
    public static String computeContentHash(String deckJson, String relicsJson, String potionsJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String content = deckJson + "|" + relicsJson + "|" + potionsJson;
            byte[] hash = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 16); // Use first 16 chars for brevity
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 not available", e);
            return null;
        }
    }

    /**
     * Start a new arena run. Returns the run ID.
     * Snapshots the loadout configuration at the time of the run.
     */
    public long startArenaRun(long loadoutId, String encounterId, int startingHp) {
        // First, get the loadout to snapshot its current state
        LoadoutRecord loadout = getLoadoutById(loadoutId);
        if (loadout == null) {
            logger.error("Cannot start arena run: loadout not found with ID " + loadoutId);
            return -1;
        }

        String sql = "INSERT INTO arena_runs (loadout_id, encounter_id, started_at, starting_hp, deck_json, relics_json, potions_json, content_hash) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, loadoutId);
            stmt.setString(2, encounterId);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setInt(4, startingHp);
            stmt.setString(5, loadout.deckJson);
            stmt.setString(6, loadout.relicsJson);
            stmt.setString(7, loadout.potionsJson);
            stmt.setString(8, loadout.contentHash);

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.info("Started arena run " + id + " with encounter: " + encounterId + ", contentHash: " + loadout.contentHash);
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
        List<RelicData> relicData = new ArrayList<>();
        for (AbstractRelic relic : relics) {
            relicData.add(new RelicData(relic.relicId, relic.counter));
        }
        return gson.toJson(relicData);
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
     * Simple relic data for JSON serialization (includes counter).
     */
    public static class RelicData {
        public String id;
        public int counter;

        public RelicData() {} // For Gson

        public RelicData(String id, int counter) {
            this.id = id;
            this.counter = counter;
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
        // Loadout snapshot at time of run
        public String deckJson;
        public String relicsJson;
        public String potionsJson;
        public String contentHash;
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
        String sql = "SELECT id, uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, potion_slots, created_at, ascension_level, content_hash, is_favorite " +
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
                    record.potionSlots = rs.getInt("potion_slots");
                    record.createdAt = rs.getLong("created_at");
                    record.ascensionLevel = rs.getInt("ascension_level");
                    record.contentHash = rs.getString("content_hash");
                    record.isFavorite = rs.getInt("is_favorite") == 1;
                    return record;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get loadout by ID: " + loadoutId, e);
        }

        return null;
    }

    /**
     * Toggle the favorite status of a loadout.
     * Returns the new favorite status.
     */
    public boolean toggleFavorite(long loadoutId) {
        // First get current status
        String selectSql = "SELECT is_favorite FROM loadouts WHERE id = ?";
        boolean currentStatus = false;

        try (PreparedStatement stmt = database.getConnection().prepareStatement(selectSql)) {
            stmt.setLong(1, loadoutId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    currentStatus = rs.getInt("is_favorite") == 1;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get favorite status", e);
            return false;
        }

        // Toggle the status
        boolean newStatus = !currentStatus;
        String updateSql = "UPDATE loadouts SET is_favorite = ? WHERE id = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(updateSql)) {
            stmt.setInt(1, newStatus ? 1 : 0);
            stmt.setLong(2, loadoutId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Toggled favorite for loadout " + loadoutId + " to: " + newStatus);
                return newStatus;
            }
        } catch (SQLException e) {
            logger.error("Failed to toggle favorite", e);
        }
        return currentStatus;
    }

    /**
     * Get saved loadouts for selection.
     * Results are sorted by favorite status (favorites first), then by created_at descending.
     */
    public List<LoadoutRecord> getLoadouts(int limit) {
        String sql = "SELECT id, uuid, name, character_class, max_hp, current_hp, deck_json, relics_json, potions_json, potion_slots, created_at, ascension_level, content_hash, is_favorite " +
                     "FROM loadouts ORDER BY is_favorite DESC, created_at DESC LIMIT ?";

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
                    record.potionSlots = rs.getInt("potion_slots");
                    record.createdAt = rs.getLong("created_at");
                    record.ascensionLevel = rs.getInt("ascension_level");
                    record.contentHash = rs.getString("content_hash");
                    record.isFavorite = rs.getInt("is_favorite") == 1;
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
        public int potionSlots;
        public long createdAt;
        public int ascensionLevel;
        public String contentHash;
        public boolean isFavorite;
    }

    /**
     * Delete a loadout and its associated arena runs.
     * Returns true if successful.
     */
    public boolean deleteLoadout(long loadoutId) {
        try {
            // First delete associated arena runs
            String deleteRunsSql = "DELETE FROM arena_runs WHERE loadout_id = ?";
            try (PreparedStatement stmt = database.getConnection().prepareStatement(deleteRunsSql)) {
                stmt.setLong(1, loadoutId);
                int deletedRuns = stmt.executeUpdate();
                logger.info("Deleted " + deletedRuns + " arena runs for loadout " + loadoutId);
            }

            // Then delete the loadout
            String deleteLoadoutSql = "DELETE FROM loadouts WHERE id = ?";
            try (PreparedStatement stmt = database.getConnection().prepareStatement(deleteLoadoutSql)) {
                stmt.setLong(1, loadoutId);
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    logger.info("Deleted loadout " + loadoutId);
                    return true;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to delete loadout", e);
        }
        return false;
    }

    /**
     * Rename a loadout.
     * Returns true if successful.
     */
    public boolean renameLoadout(long loadoutId, String newName) {
        String sql = "UPDATE loadouts SET name = ? WHERE id = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setLong(2, loadoutId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Renamed loadout " + loadoutId + " to: " + newName);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Failed to rename loadout", e);
        }
        return false;
    }

    /**
     * Update a loadout's configuration (deck, relics, potions, HP).
     * This recalculates the content hash for version tracking.
     * Returns true if successful.
     */
    public boolean updateLoadout(RandomLoadoutGenerator.GeneratedLoadout loadout, long loadoutId) {
        String deckJson = serializeDeck(loadout.deck);
        String relicsJson = serializeRelics(loadout.relics);
        String potionsJson = serializePotions(loadout.potions);
        String contentHash = computeContentHash(deckJson, relicsJson, potionsJson);

        String sql = "UPDATE loadouts SET name = ?, max_hp = ?, current_hp = ?, deck_json = ?, relics_json = ?, " +
                     "potions_json = ?, potion_slots = ?, ascension_level = ?, content_hash = ? WHERE id = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, loadout.name);
            stmt.setInt(2, loadout.maxHp);
            stmt.setInt(3, loadout.currentHp);
            stmt.setString(4, deckJson);
            stmt.setString(5, relicsJson);
            stmt.setString(6, potionsJson);
            stmt.setInt(7, loadout.potionSlots);
            stmt.setInt(8, loadout.ascensionLevel);
            stmt.setString(9, contentHash);
            stmt.setLong(10, loadoutId);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Updated loadout " + loadoutId + " (" + loadout.name + "), new contentHash: " + contentHash);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Failed to update loadout", e);
        }
        return false;
    }

    /**
     * Get arena runs for a specific loadout.
     */
    public List<ArenaRunRecord> getRunsForLoadout(long loadoutId, int limit) {
        String sql = "SELECT r.id, r.loadout_id, r.started_at, r.ended_at, r.outcome, r.starting_hp, r.ending_hp, " +
                     "r.encounter_id, r.potions_used_json, r.damage_dealt, r.damage_taken, r.turns_taken, " +
                     "l.name as loadout_name, l.character_class " +
                     "FROM arena_runs r " +
                     "JOIN loadouts l ON r.loadout_id = l.id " +
                     "WHERE r.loadout_id = ? AND r.outcome IS NOT NULL " +
                     "ORDER BY r.started_at DESC " +
                     "LIMIT ?";

        List<ArenaRunRecord> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, loadoutId);
            stmt.setInt(2, limit);

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
            logger.error("Failed to get runs for loadout", e);
        }

        return results;
    }

    /**
     * Get all loadout+encounter combinations with aggregated statistics.
     * Used for the stats screen to show Pareto-best victories.
     */
    public List<LoadoutEncounterStats> getLoadoutEncounterStats() {
        String sql = "SELECT l.id as loadout_id, l.name as loadout_name, l.character_class, " +
                     "r.encounter_id, " +
                     "COUNT(*) as total_runs, " +
                     "SUM(CASE WHEN r.outcome = 'VICTORY' THEN 1 ELSE 0 END) as wins, " +
                     "SUM(CASE WHEN r.outcome = 'DEFEAT' THEN 1 ELSE 0 END) as losses " +
                     "FROM arena_runs r " +
                     "JOIN loadouts l ON r.loadout_id = l.id " +
                     "WHERE r.outcome IS NOT NULL " +
                     "GROUP BY l.id, r.encounter_id " +
                     "ORDER BY l.name, r.encounter_id";

        List<LoadoutEncounterStats> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                LoadoutEncounterStats stats = new LoadoutEncounterStats();
                stats.loadoutId = rs.getLong("loadout_id");
                stats.loadoutName = rs.getString("loadout_name");
                stats.characterClass = rs.getString("character_class");
                stats.encounterId = rs.getString("encounter_id");
                stats.totalRuns = rs.getInt("total_runs");
                stats.wins = rs.getInt("wins");
                stats.losses = rs.getInt("losses");
                results.add(stats);
            }
        } catch (SQLException e) {
            logger.error("Failed to get loadout encounter stats", e);
        }

        return results;
    }

    /**
     * Get all victories for a specific loadout+encounter combination.
     * Used to calculate Pareto-best victories.
     */
    public List<VictoryRecord> getVictoriesForLoadoutEncounter(long loadoutId, String encounterId) {
        String sql = "SELECT r.id, r.turns_taken, r.damage_taken, r.damage_dealt, r.ending_hp, r.starting_hp, " +
                     "r.potions_used_json, r.started_at " +
                     "FROM arena_runs r " +
                     "WHERE r.loadout_id = ? AND r.encounter_id = ? AND r.outcome = 'VICTORY' " +
                     "ORDER BY r.started_at DESC";

        List<VictoryRecord> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, loadoutId);
            stmt.setString(2, encounterId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    VictoryRecord record = new VictoryRecord();
                    record.runId = rs.getLong("id");
                    record.turnsTaken = rs.getInt("turns_taken");
                    record.damageTaken = rs.getInt("damage_taken");
                    record.damageDealt = rs.getInt("damage_dealt");
                    record.endingHp = rs.getInt("ending_hp");
                    record.startingHp = rs.getInt("starting_hp");
                    record.startedAt = rs.getLong("started_at");

                    String potionsJson = rs.getString("potions_used_json");
                    if (potionsJson != null && !potionsJson.isEmpty()) {
                        Type listType = new TypeToken<List<String>>(){}.getType();
                        List<String> potions = gson.fromJson(potionsJson, listType);
                        record.potionsUsed = potions != null ? potions.size() : 0;
                    } else {
                        record.potionsUsed = 0;
                    }

                    results.add(record);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get victories for loadout encounter", e);
        }

        return results;
    }

    /**
     * Stats for a loadout+encounter combination.
     */
    public static class LoadoutEncounterStats {
        public long loadoutId;
        public String loadoutName;
        public String characterClass;
        public String encounterId;
        public int totalRuns;
        public int wins;
        public int losses;

        public double getWinRate() {
            return totalRuns > 0 ? (double) wins / totalRuns : 0;
        }
    }

    /**
     * Record of a single victory with metrics for Pareto comparison.
     */
    public static class VictoryRecord {
        public long runId;
        public int turnsTaken;
        public int damageTaken;
        public int damageDealt;
        public int endingHp;
        public int startingHp;
        public int potionsUsed;
        public long startedAt;

        /**
         * Check if this victory is dominated by another (other is strictly better in all metrics).
         * A victory is dominated if another victory has:
         * - Same or fewer turns
         * - Same or less damage taken
         * - Same or fewer potions used
         * - AND is strictly better in at least one of these
         */
        public boolean isDominatedBy(VictoryRecord other) {
            boolean sameOrBetterTurns = other.turnsTaken <= this.turnsTaken;
            boolean sameOrBetterDamage = other.damageTaken <= this.damageTaken;
            boolean sameOrBetterPotions = other.potionsUsed <= this.potionsUsed;

            boolean strictlyBetterSomewhere =
                other.turnsTaken < this.turnsTaken ||
                other.damageTaken < this.damageTaken ||
                other.potionsUsed < this.potionsUsed;

            return sameOrBetterTurns && sameOrBetterDamage && sameOrBetterPotions && strictlyBetterSomewhere;
        }
    }

    /**
     * Get distinct content hashes used in runs for a specific loadout.
     * Used to determine how many "versions" of a loadout have been used.
     */
    public List<String> getDistinctContentHashesForLoadout(long loadoutId) {
        String sql = "SELECT DISTINCT content_hash FROM arena_runs " +
                     "WHERE loadout_id = ? AND content_hash IS NOT NULL " +
                     "ORDER BY started_at DESC";

        List<String> results = new ArrayList<>();

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setLong(1, loadoutId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("content_hash"));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get distinct content hashes for loadout", e);
        }

        return results;
    }

    /**
     * Check if a loadout has been modified since a particular content hash.
     * Returns true if the current loadout's content hash differs from the given hash.
     */
    public boolean hasLoadoutChanged(long loadoutId, String previousContentHash) {
        LoadoutRecord loadout = getLoadoutById(loadoutId);
        if (loadout == null || loadout.contentHash == null || previousContentHash == null) {
            return false;
        }
        return !loadout.contentHash.equals(previousContentHash);
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
