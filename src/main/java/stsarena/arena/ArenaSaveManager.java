package stsarena.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import stsarena.STSArena;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages creating and loading save files for arena mode.
 */
public class ArenaSaveManager {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Get the save file path for a character class.
     */
    public static String getSavePath(AbstractPlayer.PlayerClass playerClass) {
        // Get the actual saves directory used by the game
        // The game runs from the Resources folder, saves are relative to that
        String userHome = System.getProperty("user.home");
        String savesDir = userHome + "/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/saves/";

        // Fallback for non-Mac or different install locations
        File savesDirFile = new File(savesDir);
        if (!savesDirFile.exists()) {
            savesDir = "saves" + File.separator;
        }

        // Use .autosave (not .autosaveBETA) - this is what SaveAndContinue.loadSaveFile reads
        String path = savesDir + playerClass.name() + ".autosave";
        STSArena.logger.info("ARENA: Save path: " + path);
        return path;
    }

    /**
     * Create an arena save file with the given loadout.
     * Returns the path to the save file.
     */
    public static String createArenaSave(RandomLoadoutGenerator.GeneratedLoadout loadout, String encounter) {
        STSArena.logger.info("Creating arena save file for " + loadout.playerClass);

        Map<String, Object> save = new HashMap<>();

        // Basic game state
        save.put("name", "Arena");
        save.put("loadout", loadout.playerClass.name());  // Required field
        save.put("floor_num", 1);
        save.put("level_name", "Exordium");
        save.put("act_num", 1);
        save.put("is_ascension_mode", false);
        save.put("ascension_level", 0);
        save.put("is_daily", false);
        save.put("is_trial", false);
        save.put("is_endless_mode", false);
        save.put("is_final_act_on", false);
        save.put("chose_neow_reward", true);
        save.put("neow_bonus", "NONE");
        save.put("neow_cost", "NONE");

        // Player state
        save.put("current_health", loadout.currentHp);
        save.put("max_health", loadout.maxHp);
        save.put("gold", 100);
        save.put("hand_size", 5);
        save.put("potion_slots", 3);
        // Energy is stored in red field (all characters use this)
        save.put("red", 3);
        save.put("green", 0);
        save.put("blue", 0);

        // Room state - set to empty room, we'll transition to fight
        save.put("current_room", "com.megacrit.cardcrawl.rooms.EmptyRoom");
        save.put("room_x", 0);
        save.put("room_y", 0);
        save.put("post_combat", false);
        save.put("smoked", false);

        // Cards
        List<Map<String, Object>> cards = new ArrayList<>();
        for (AbstractCard card : loadout.deck) {
            Map<String, Object> cardData = new HashMap<>();
            cardData.put("id", card.cardID);
            cardData.put("upgrades", card.timesUpgraded);
            cardData.put("misc", card.misc);
            cards.add(cardData);
        }
        save.put("cards", cards);

        // Relics
        List<String> relics = new ArrayList<>();
        List<Integer> relicCounters = new ArrayList<>();
        for (AbstractRelic relic : loadout.relics) {
            relics.add(relic.relicId);
            relicCounters.add(0);
        }
        save.put("relics", relics);
        save.put("relic_counters", relicCounters);

        // Potions (empty)
        List<String> potions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            potions.add("Potion Slot");
        }
        save.put("potions", potions);

        // Monster list with our encounter first
        List<String> monsterList = new ArrayList<>();
        monsterList.add(encounter);
        // Add some more encounters in case needed
        for (String enc : LoadoutConfig.ENCOUNTERS) {
            if (!enc.equals(encounter)) {
                monsterList.add(enc);
            }
        }
        save.put("monster_list", monsterList);

        // Elite and boss lists (minimal)
        save.put("elite_monster_list", new ArrayList<String>());
        save.put("boss_list", new ArrayList<String>());
        save.put("boss", "The Guardian");
        save.put("boss_relics", new ArrayList<String>());

        // Event lists
        save.put("event_list", new ArrayList<String>());
        save.put("one_time_event_list", new ArrayList<String>());
        // event_chances needs exactly 4 values: [ELITE, MONSTER, SHOP, TREASURE]
        List<Float> eventChances = new ArrayList<>();
        eventChances.add(0.0f);   // ELITE_CHANCE
        eventChances.add(0.1f);   // MONSTER_CHANCE
        eventChances.add(0.03f);  // SHOP_CHANCE
        eventChances.add(0.02f);  // TREASURE_CHANCE
        save.put("event_chances", eventChances);

        // Relic pools
        save.put("common_relics", new ArrayList<String>());
        save.put("uncommon_relics", new ArrayList<String>());
        save.put("rare_relics", new ArrayList<String>());
        save.put("shop_relics", new ArrayList<String>());

        // Seeds and counters - these must match what the game expects
        long seed = new java.util.Random().nextLong();
        save.put("seed", seed);
        save.put("seed_set", false);  // Required boolean field
        save.put("special_seed", 0L);  // Must be long
        save.put("shuffle_seed_count", 0);
        save.put("card_seed_count", 0);
        save.put("card_random_seed_count", 0);
        save.put("card_random_seed_randomizer", 0);
        save.put("relic_seed_count", 0);
        save.put("potion_seed_count", 0);
        save.put("monster_seed_count", 0);
        save.put("event_seed_count", 0);
        save.put("merchant_seed_count", 0);
        save.put("treasure_seed_count", 0);
        save.put("ai_seed_count", 0);
        save.put("monster_hp_seed_count", 0);  // Missing field
        save.put("potion_chance", 40);
        save.put("purgeCost", 75);

        // Metrics
        save.put("metric_floor_reached", 1);
        save.put("metric_playtime", 0);
        save.put("metric_build_version", "2022-12-18");
        save.put("metric_seed_played", String.valueOf(seed));
        save.put("metric_purchased_purges", 0);
        save.put("metric_campfire_rested", 0);
        save.put("metric_campfire_upgraded", 0);
        save.put("metric_campfire_rituals", 0);
        save.put("metric_campfire_meditates", 0);
        save.put("monsters_killed", 0);
        save.put("elites1_killed", 0);
        save.put("elites2_killed", 0);
        save.put("elites3_killed", 0);
        save.put("gold_gained", 0);
        save.put("champions", 0);
        save.put("perfect", 0);
        save.put("combo", false);
        save.put("overkill", false);
        save.put("mystery_machine", 0);
        save.put("spirit_count", 0);
        save.put("max_orbs", 3);
        save.put("play_time", 0);
        save.put("daily_date", 0);

        // Path info
        save.put("path_x", new ArrayList<Integer>());
        save.put("path_y", new ArrayList<Integer>());
        save.put("metric_path_per_floor", new ArrayList<String>());
        save.put("metric_path_taken", new ArrayList<String>());
        save.put("metric_current_hp_per_floor", new ArrayList<Integer>());
        save.put("metric_max_hp_per_floor", new ArrayList<Integer>());
        save.put("metric_gold_per_floor", new ArrayList<Integer>());
        save.put("metric_campfire_choices", new ArrayList<Object>());
        save.put("metric_card_choices", new ArrayList<Object>());
        save.put("metric_event_choices", new ArrayList<Object>());
        save.put("metric_damage_taken", new ArrayList<Object>());
        save.put("metric_boss_relics", new ArrayList<Object>());
        save.put("metric_relics_obtained", new ArrayList<Object>());
        save.put("metric_potions_obtained", new ArrayList<Object>());
        save.put("metric_potions_floor_spawned", new ArrayList<Object>());
        save.put("metric_potions_floor_usage", new ArrayList<Object>());
        save.put("metric_items_purchased", new ArrayList<Object>());
        save.put("metric_item_purchase_floors", new ArrayList<Object>());
        save.put("metric_items_purged", new ArrayList<Object>());
        save.put("metric_items_purged_floors", new ArrayList<Object>());
        save.put("endless_increments", new ArrayList<Integer>());

        // Keys
        save.put("has_emerald_key", false);
        save.put("has_ruby_key", false);
        save.put("has_sapphire_key", false);

        // Blights (for endless mode)
        save.put("blights", new ArrayList<String>());
        save.put("blight_counters", new ArrayList<Integer>());

        // Custom mods and daily mods
        save.put("custom_mods", new ArrayList<String>());
        save.put("daily_mods", new ArrayList<String>());
        save.put("mugged", false);
        save.put("save_date", System.currentTimeMillis());
        save.put("obtained_cards", new HashMap<String, Integer>());
        save.put("combat_rewards", new ArrayList<Object>());  // Required for post_combat handling

        // Write save file
        String savePath = getSavePath(loadout.playerClass);
        try {
            File saveFile = new File(savePath);
            STSArena.logger.info("ARENA: Creating save file at: " + saveFile.getAbsolutePath());
            saveFile.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(saveFile)) {
                gson.toJson(save, writer);
            }

            // Verify the file was written
            if (saveFile.exists()) {
                STSArena.logger.info("ARENA: Save file written successfully, size: " + saveFile.length() + " bytes");
            } else {
                STSArena.logger.error("ARENA: Save file does not exist after writing!");
            }

            return savePath;
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Failed to write arena save", e);
            e.printStackTrace();
            return null;
        }
    }
}
