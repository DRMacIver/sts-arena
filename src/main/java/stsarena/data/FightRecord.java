package stsarena.data;

import stsarena.STSArena;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a record of a completed arena fight.
 */
public class FightRecord {

    public enum Result {
        WIN, LOSS
    }

    private long id;
    private Long loadoutId;  // nullable - might be a custom one-off fight
    private String encounterId;
    private String encounterName;
    private String characterClass;
    private int ascensionLevel;
    private String deckJson;
    private String relicsJson;
    private String potionsJson;
    private Result result;
    private int turnsTaken;
    private int damageDealt;
    private int damageTaken;
    private int floorNum;
    private long foughtAt;

    public FightRecord() {
        this.foughtAt = System.currentTimeMillis();
    }

    // Getters and setters

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getLoadoutId() { return loadoutId; }
    public void setLoadoutId(Long loadoutId) { this.loadoutId = loadoutId; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public String getEncounterName() { return encounterName; }
    public void setEncounterName(String encounterName) { this.encounterName = encounterName; }

    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }

    public int getAscensionLevel() { return ascensionLevel; }
    public void setAscensionLevel(int ascensionLevel) { this.ascensionLevel = ascensionLevel; }

    public String getDeckJson() { return deckJson; }
    public void setDeckJson(String deckJson) { this.deckJson = deckJson; }

    public String getRelicsJson() { return relicsJson; }
    public void setRelicsJson(String relicsJson) { this.relicsJson = relicsJson; }

    public String getPotionsJson() { return potionsJson; }
    public void setPotionsJson(String potionsJson) { this.potionsJson = potionsJson; }

    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }

    public int getTurnsTaken() { return turnsTaken; }
    public void setTurnsTaken(int turnsTaken) { this.turnsTaken = turnsTaken; }

    public int getDamageDealt() { return damageDealt; }
    public void setDamageDealt(int damageDealt) { this.damageDealt = damageDealt; }

    public int getDamageTaken() { return damageTaken; }
    public void setDamageTaken(int damageTaken) { this.damageTaken = damageTaken; }

    public int getFloorNum() { return floorNum; }
    public void setFloorNum(int floorNum) { this.floorNum = floorNum; }

    public long getFoughtAt() { return foughtAt; }

    // Database operations

    public void save() {
        Connection conn = ArenaDatabase.getInstance().getConnection();
        String sql = "INSERT INTO fight_history (loadout_id, encounter_id, encounter_name, " +
                     "character_class, ascension_level, deck_json, relics_json, potions_json, " +
                     "result, turns_taken, damage_dealt, damage_taken, floor_num, fought_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (loadoutId != null) {
                stmt.setLong(1, loadoutId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setString(2, encounterId);
            stmt.setString(3, encounterName);
            stmt.setString(4, characterClass);
            stmt.setInt(5, ascensionLevel);
            stmt.setString(6, deckJson);
            stmt.setString(7, relicsJson);
            stmt.setString(8, potionsJson);
            stmt.setString(9, result.name());
            stmt.setInt(10, turnsTaken);
            stmt.setInt(11, damageDealt);
            stmt.setInt(12, damageTaken);
            stmt.setInt(13, floorNum);
            stmt.setLong(14, foughtAt);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    this.id = keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to save fight record", e);
        }
    }

    public static List<FightRecord> findByEncounter(String encounterId) {
        List<FightRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM fight_history WHERE encounter_id = ? ORDER BY fought_at DESC";

        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setString(1, encounterId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to find fight records", e);
        }
        return records;
    }

    public static List<FightRecord> findRecent(int limit) {
        List<FightRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM fight_history ORDER BY fought_at DESC LIMIT ?";

        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to find recent fights", e);
        }
        return records;
    }

    public static FightStats getStatsForEncounter(String encounterId) {
        String sql = "SELECT " +
                     "COUNT(*) as total, " +
                     "SUM(CASE WHEN result = 'WIN' THEN 1 ELSE 0 END) as wins, " +
                     "AVG(turns_taken) as avg_turns, " +
                     "AVG(damage_taken) as avg_damage_taken " +
                     "FROM fight_history WHERE encounter_id = ?";

        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setString(1, encounterId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FightStats stats = new FightStats();
                    stats.totalFights = rs.getInt("total");
                    stats.wins = rs.getInt("wins");
                    stats.avgTurns = rs.getDouble("avg_turns");
                    stats.avgDamageTaken = rs.getDouble("avg_damage_taken");
                    return stats;
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to get fight stats", e);
        }
        return new FightStats();
    }

    private static FightRecord fromResultSet(ResultSet rs) throws SQLException {
        FightRecord record = new FightRecord();
        record.id = rs.getLong("id");
        long loadoutId = rs.getLong("loadout_id");
        record.loadoutId = rs.wasNull() ? null : loadoutId;
        record.encounterId = rs.getString("encounter_id");
        record.encounterName = rs.getString("encounter_name");
        record.characterClass = rs.getString("character_class");
        record.ascensionLevel = rs.getInt("ascension_level");
        record.deckJson = rs.getString("deck_json");
        record.relicsJson = rs.getString("relics_json");
        record.potionsJson = rs.getString("potions_json");
        record.result = Result.valueOf(rs.getString("result"));
        record.turnsTaken = rs.getInt("turns_taken");
        record.damageDealt = rs.getInt("damage_dealt");
        record.damageTaken = rs.getInt("damage_taken");
        record.floorNum = rs.getInt("floor_num");
        record.foughtAt = rs.getLong("fought_at");
        return record;
    }

    /**
     * Aggregated statistics for an encounter.
     */
    public static class FightStats {
        public int totalFights;
        public int wins;
        public double avgTurns;
        public double avgDamageTaken;

        public double getWinRate() {
            return totalFights > 0 ? (double) wins / totalFights : 0;
        }
    }
}
