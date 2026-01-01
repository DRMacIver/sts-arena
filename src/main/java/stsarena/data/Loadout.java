package stsarena.data;

import stsarena.STSArena;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a saved arena loadout (deck, relics, potions configuration).
 */
public class Loadout {

    private long id;
    private String name;
    private String characterClass;
    private int ascensionLevel;
    private int maxHp;
    private int currentHp;
    private int gold;
    private String deckJson;
    private String relicsJson;
    private String potionsJson;
    private long createdAt;
    private long updatedAt;

    public Loadout() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    // Getters and setters

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }

    public int getAscensionLevel() { return ascensionLevel; }
    public void setAscensionLevel(int ascensionLevel) { this.ascensionLevel = ascensionLevel; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    public String getDeckJson() { return deckJson; }
    public void setDeckJson(String deckJson) { this.deckJson = deckJson; }

    public String getRelicsJson() { return relicsJson; }
    public void setRelicsJson(String relicsJson) { this.relicsJson = relicsJson; }

    public String getPotionsJson() { return potionsJson; }
    public void setPotionsJson(String potionsJson) { this.potionsJson = potionsJson; }

    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    // Database operations

    public void save() {
        this.updatedAt = System.currentTimeMillis();
        Connection conn = ArenaDatabase.getInstance().getConnection();

        if (id == 0) {
            insert(conn);
        } else {
            update(conn);
        }
    }

    private void insert(Connection conn) {
        String sql = "INSERT INTO loadouts (name, character_class, ascension_level, max_hp, " +
                     "current_hp, gold, deck_json, relics_json, potions_json, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, characterClass);
            stmt.setInt(3, ascensionLevel);
            stmt.setInt(4, maxHp);
            stmt.setInt(5, currentHp);
            stmt.setInt(6, gold);
            stmt.setString(7, deckJson);
            stmt.setString(8, relicsJson);
            stmt.setString(9, potionsJson);
            stmt.setLong(10, createdAt);
            stmt.setLong(11, updatedAt);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    this.id = keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to insert loadout", e);
        }
    }

    private void update(Connection conn) {
        String sql = "UPDATE loadouts SET name = ?, character_class = ?, ascension_level = ?, " +
                     "max_hp = ?, current_hp = ?, gold = ?, deck_json = ?, relics_json = ?, " +
                     "potions_json = ?, updated_at = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, characterClass);
            stmt.setInt(3, ascensionLevel);
            stmt.setInt(4, maxHp);
            stmt.setInt(5, currentHp);
            stmt.setInt(6, gold);
            stmt.setString(7, deckJson);
            stmt.setString(8, relicsJson);
            stmt.setString(9, potionsJson);
            stmt.setLong(10, updatedAt);
            stmt.setLong(11, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            STSArena.logger.error("Failed to update loadout", e);
        }
    }

    public void delete() {
        if (id == 0) return;

        String sql = "DELETE FROM loadouts WHERE id = ?";
        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
            this.id = 0;
        } catch (SQLException e) {
            STSArena.logger.error("Failed to delete loadout", e);
        }
    }

    public static Loadout findById(long id) {
        String sql = "SELECT * FROM loadouts WHERE id = ?";
        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return fromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to find loadout", e);
        }
        return null;
    }

    public static List<Loadout> findAll() {
        return findByCharacterClass(null);
    }

    public static List<Loadout> findByCharacterClass(String characterClass) {
        List<Loadout> loadouts = new ArrayList<>();
        String sql = characterClass == null
            ? "SELECT * FROM loadouts ORDER BY updated_at DESC"
            : "SELECT * FROM loadouts WHERE character_class = ? ORDER BY updated_at DESC";

        try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
            if (characterClass != null) {
                stmt.setString(1, characterClass);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loadouts.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            STSArena.logger.error("Failed to find loadouts", e);
        }
        return loadouts;
    }

    private static Loadout fromResultSet(ResultSet rs) throws SQLException {
        Loadout loadout = new Loadout();
        loadout.id = rs.getLong("id");
        loadout.name = rs.getString("name");
        loadout.characterClass = rs.getString("character_class");
        loadout.ascensionLevel = rs.getInt("ascension_level");
        loadout.maxHp = rs.getInt("max_hp");
        loadout.currentHp = rs.getInt("current_hp");
        loadout.gold = rs.getInt("gold");
        loadout.deckJson = rs.getString("deck_json");
        loadout.relicsJson = rs.getString("relics_json");
        loadout.potionsJson = rs.getString("potions_json");
        loadout.createdAt = rs.getLong("created_at");
        loadout.updatedAt = rs.getLong("updated_at");
        return loadout;
    }
}
