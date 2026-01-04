package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomBoss;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saves loadouts from normal (non-arena) runs when they complete.
 * This allows players to replay their builds in arena mode.
 */
public class NormalRunLoadoutSaver {

    // State captured at the start of combat (for defeats and Practice in Arena)
    private static int combatStartHp = 0;
    private static int combatStartMaxHp = 0;
    private static List<AbstractPotion> combatStartPotions = new ArrayList<>();
    private static Map<String, Integer> combatStartRelicCounters = new HashMap<>();
    private static String combatEncounterId = null;

    // Most recently saved loadout (for "Try Again in Arena" feature)
    private static long lastSavedLoadoutId = -1;
    private static String lastSavedEncounterId = null;

    /**
     * Capture player state at the start of combat.
     * This is used for defeats so the loadout reflects pre-combat state.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.rooms.MonsterRoom", method = "onPlayerEntry")
    public static class OnCombatStart {
        @SpirePostfixPatch
        public static void Postfix() {
            captureCombatStartState();
        }
    }

    /**
     * Also patch elite rooms in case they override onPlayerEntry.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.rooms.MonsterRoomElite", method = "onPlayerEntry")
    public static class OnEliteCombatStart {
        @SpirePostfixPatch
        public static void Postfix() {
            captureCombatStartState();
        }
    }

    /**
     * Also patch boss rooms in case they override onPlayerEntry.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.rooms.MonsterRoomBoss", method = "onPlayerEntry")
    public static class OnBossCombatStart {
        @SpirePostfixPatch
        public static void Postfix() {
            captureCombatStartState();
        }
    }

    /**
     * Shared logic to capture combat start state.
     */
    private static void captureCombatStartState() {
        // Skip if this is an arena run
        if (ArenaRunner.isArenaRun()) {
            return;
        }

        // Capture state when entering a monster room
        if (AbstractDungeon.player != null) {
            combatStartHp = AbstractDungeon.player.currentHealth;
            combatStartMaxHp = AbstractDungeon.player.maxHealth;

            // Copy potions
            combatStartPotions.clear();
            for (AbstractPotion potion : AbstractDungeon.player.potions) {
                if (!(potion instanceof PotionSlot)) {
                    combatStartPotions.add(potion.makeCopy());
                }
            }

            // Capture relic counters
            combatStartRelicCounters.clear();
            for (AbstractRelic relic : AbstractDungeon.player.relics) {
                combatStartRelicCounters.put(relic.relicId, relic.counter);
            }

            // Capture encounter ID based on room type
            combatEncounterId = getEncounterIdForCurrentRoom();

            STSArena.logger.info("Captured combat start state: HP=" + combatStartHp + "/" + combatStartMaxHp +
                ", potions=" + combatStartPotions.size() + ", relics=" + combatStartRelicCounters.size() +
                ", encounter=" + combatEncounterId);
        }
    }

    /**
     * Get the encounter ID for the current room based on room type.
     * Works for normal fights, elites, bosses, and event fights.
     */
    private static String getEncounterIdForCurrentRoom() {
        if (AbstractDungeon.getCurrRoom() == null) {
            return null;
        }

        MonsterGroup monsters = AbstractDungeon.getCurrRoom().monsters;

        // For boss rooms, use bossKey which is set by the game
        if (AbstractDungeon.getCurrRoom() instanceof MonsterRoomBoss) {
            String bossKey = AbstractDungeon.bossKey;
            STSArena.logger.info("Boss room detected, bossKey: " + bossKey);
            return bossKey;
        }

        // For elite rooms, get the encounter from the monsters directly
        // (eliteMonsterList contains NEXT elites, not current)
        if (AbstractDungeon.getCurrRoom() instanceof MonsterRoomElite) {
            String encounterId = getEncounterFromMonsters(monsters);
            STSArena.logger.info("Elite room detected, encounterId: " + encounterId);
            return encounterId;
        }

        // For normal monster rooms, use the monster list (front entry is current)
        if (AbstractDungeon.monsterList != null && !AbstractDungeon.monsterList.isEmpty()) {
            String encounterId = AbstractDungeon.monsterList.get(0);
            STSArena.logger.info("Monster room detected, encounterId: " + encounterId);
            return encounterId;
        }

        // Fallback: get from monsters in room
        String encounterId = getEncounterFromMonsters(monsters);
        STSArena.logger.info("Fallback encounter from monsters: " + encounterId);
        return encounterId;
    }

    /**
     * Get the encounter ID from the monsters currently in the room.
     * For single monsters, uses the monster's name.
     * For multi-monster encounters, uses the count + name pattern.
     */
    private static String getEncounterFromMonsters(MonsterGroup monsters) {
        if (monsters == null || monsters.monsters == null || monsters.monsters.isEmpty()) {
            return null;
        }

        int count = monsters.monsters.size();

        // Single monster - use its name directly
        if (count == 1) {
            return monsters.monsters.get(0).name;
        }

        // Multi-monster - check if all same type
        String firstName = monsters.monsters.get(0).name;
        boolean allSame = true;
        for (int i = 1; i < count; i++) {
            if (!monsters.monsters.get(i).name.equals(firstName)) {
                allSame = false;
                break;
            }
        }

        if (allSame) {
            // "3 Sentries", "2 Louse", etc.
            return count + " " + firstName + (firstName.endsWith("s") ? "" : "s");
        }

        // Mixed encounter - just use first monster's name as best guess
        return firstName;
    }

    /**
     * Save loadout when player dies in a normal run.
     * Uses the pre-combat state (HP and potions from before the fatal fight).
     * Does NOT save if the run was abandoned.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
    public static class OnDefeat {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, MonsterGroup m) {
            if (!ArenaRunner.isArenaRun()) {
                // Check if the run was abandoned - don't save loadout in that case
                // CardCrawlGame.startOver is true when abandoning
                if (ArenaPauseButtonPatch.isAbandoning || CardCrawlGame.startOver) {
                    STSArena.logger.info("Run was abandoned (isAbandoning=" + ArenaPauseButtonPatch.isAbandoning +
                        ", startOver=" + CardCrawlGame.startOver + ") - not saving loadout");
                    ArenaPauseButtonPatch.isAbandoning = false;  // Reset flag
                    return;
                }
                STSArena.logger.info("Normal run ended in defeat - saving loadout with pre-combat state");
                saveLoadoutOnDefeat();
            }
        }
    }

    /**
     * Save loadout when player wins a normal run.
     * Uses the current state (post-victory).
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.VictoryScreen", method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
    public static class OnVictory {
        @SpirePostfixPatch
        public static void Postfix(VictoryScreen __instance, MonsterGroup m) {
            if (!ArenaRunner.isArenaRun()) {
                STSArena.logger.info("Normal run ended in victory - saving loadout");
                saveLoadoutOnVictory();
            }
        }
    }

    /**
     * Save loadout on defeat using pre-combat state (HP and potions from before the fatal fight).
     */
    private static void saveLoadoutOnDefeat() {
        try {
            AbstractPlayer player = AbstractDungeon.player;
            if (player == null) {
                STSArena.logger.error("Cannot save loadout - player is null");
                return;
            }

            // Generate unique ID and name
            String id = UUID.randomUUID().toString();
            long createdAt = System.currentTimeMillis();
            String name = generateLoadoutName(player, "Defeat");

            // Copy the deck
            List<AbstractCard> deck = new ArrayList<>();
            for (AbstractCard card : player.masterDeck.group) {
                deck.add(card.makeCopy());
            }

            // Copy the relics (preserving counters)
            List<AbstractRelic> relics = new ArrayList<>();
            for (AbstractRelic relic : player.relics) {
                AbstractRelic copy = relic.makeCopy();
                copy.counter = relic.counter;
                relics.add(copy);
            }

            // Check for Prismatic Shard
            boolean hasPrismaticShard = player.hasRelic("PrismaticShard");

            // Get ascension level
            int ascensionLevel = AbstractDungeon.ascensionLevel;

            // Use pre-combat HP (captured at start of the fatal fight)
            int hp = combatStartHp > 0 ? combatStartHp : player.currentHealth;
            int maxHp = combatStartMaxHp > 0 ? combatStartMaxHp : player.maxHealth;

            // Use pre-combat potions
            List<AbstractPotion> potions = new ArrayList<>(combatStartPotions);

            // Get potion slots from player
            int potionSlots = player.potionSlots;

            STSArena.logger.info("Saving defeat loadout with pre-combat HP: " + hp + "/" + maxHp + ", potions: " + potions.size() + ", slots: " + potionSlots);

            // Create the loadout
            RandomLoadoutGenerator.GeneratedLoadout loadout = new RandomLoadoutGenerator.GeneratedLoadout(
                id,
                name,
                createdAt,
                player.chosenClass,
                deck,
                relics,
                potions,
                potionSlots,
                hasPrismaticShard,
                maxHp,
                hp,
                ascensionLevel
            );

            // Save to database
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            long dbId = repo.saveLoadout(loadout);

            if (dbId > 0) {
                STSArena.logger.info("Saved loadout '" + name + "' from normal run (dbId=" + dbId + ")");

                // Store for "Try Again in Arena" button
                lastSavedLoadoutId = dbId;
                lastSavedEncounterId = combatEncounterId;
                STSArena.logger.info("Stored for arena retry: loadoutId=" + lastSavedLoadoutId +
                    ", encounterId=" + lastSavedEncounterId);
            } else {
                STSArena.logger.error("Failed to save loadout from normal run");
            }

        } catch (Exception e) {
            STSArena.logger.error("Error saving loadout from normal run", e);
        }
    }

    /**
     * Save loadout on victory using current state.
     */
    private static void saveLoadoutOnVictory() {
        try {
            AbstractPlayer player = AbstractDungeon.player;
            if (player == null) {
                STSArena.logger.error("Cannot save loadout - player is null");
                return;
            }

            // Generate unique ID and name
            String id = UUID.randomUUID().toString();
            long createdAt = System.currentTimeMillis();
            String name = generateLoadoutName(player, "Victory");

            // Copy the deck
            List<AbstractCard> deck = new ArrayList<>();
            for (AbstractCard card : player.masterDeck.group) {
                deck.add(card.makeCopy());
            }

            // Copy the relics (preserving counters)
            List<AbstractRelic> relics = new ArrayList<>();
            for (AbstractRelic relic : player.relics) {
                AbstractRelic copy = relic.makeCopy();
                copy.counter = relic.counter;
                relics.add(copy);
            }

            // Check for Prismatic Shard
            boolean hasPrismaticShard = player.hasRelic("PrismaticShard");

            // Get ascension level
            int ascensionLevel = AbstractDungeon.ascensionLevel;

            // Copy current potions
            List<AbstractPotion> potions = new ArrayList<>();
            for (AbstractPotion potion : player.potions) {
                if (!(potion instanceof PotionSlot)) {
                    potions.add(potion.makeCopy());
                }
            }

            // Get potion slots from player
            int potionSlots = player.potionSlots;

            // Create the loadout with current state
            RandomLoadoutGenerator.GeneratedLoadout loadout = new RandomLoadoutGenerator.GeneratedLoadout(
                id,
                name,
                createdAt,
                player.chosenClass,
                deck,
                relics,
                potions,
                potionSlots,
                hasPrismaticShard,
                player.maxHealth,
                player.currentHealth,
                ascensionLevel
            );

            // Save to database
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            long dbId = repo.saveLoadout(loadout);

            if (dbId > 0) {
                STSArena.logger.info("Saved loadout '" + name + "' from normal run (dbId=" + dbId + ", potions=" + potions.size() + ")");
            } else {
                STSArena.logger.error("Failed to save loadout from normal run");
            }

        } catch (Exception e) {
            STSArena.logger.error("Error saving loadout from normal run", e);
        }
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    /**
     * Generate a descriptive name for the loadout.
     */
    private static String generateLoadoutName(AbstractPlayer player, String outcome) {
        String className = player.chosenClass.name();
        // Convert to title case (e.g., "IRONCLAD" -> "Ironclad")
        className = className.substring(0, 1) + className.substring(1).toLowerCase();

        int floor = AbstractDungeon.floorNum;
        int ascension = AbstractDungeon.ascensionLevel;
        String timestamp = DATE_FORMAT.format(new Date());

        if (ascension > 0) {
            return className + " A" + ascension + " F" + floor + " " + outcome + " (" + timestamp + ")";
        } else {
            return className + " F" + floor + " " + outcome + " (" + timestamp + ")";
        }
    }

    /**
     * Get the most recently saved loadout ID (from a defeat).
     * Used by the "Try Again in Arena Mode" button.
     */
    public static long getLastSavedLoadoutId() {
        return lastSavedLoadoutId;
    }

    /**
     * Get the encounter ID from the most recent defeat.
     * Used by the "Try Again in Arena Mode" button.
     */
    public static String getLastSavedEncounterId() {
        return lastSavedEncounterId;
    }

    /**
     * Check if there's a valid loadout/encounter available for arena retry.
     */
    public static boolean hasArenaRetryData() {
        return lastSavedLoadoutId > 0 && lastSavedEncounterId != null && !lastSavedEncounterId.isEmpty();
    }

    /**
     * Clear the saved retry data.
     */
    public static void clearRetryData() {
        lastSavedLoadoutId = -1;
        lastSavedEncounterId = null;
    }

    /**
     * Get the current combat encounter ID (captured when entering the fight).
     * Returns null if not currently in combat or encounter wasn't captured.
     */
    public static String getCurrentCombatEncounterId() {
        // Only return encounter ID if we're actually in combat
        if (AbstractDungeon.getCurrRoom() == null) {
            return null;
        }

        // Check if there's active combat by looking at room phase
        // COMBAT phase means we're fighting, COMPLETE means fight is over
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room.phase != AbstractRoom.RoomPhase.COMBAT) {
            // Not currently in combat - could be in shop, rest site, or post-combat
            return null;
        }

        return combatEncounterId;
    }

    /**
     * Get the pre-combat relic counter for a specific relic.
     * Returns the counter value captured at combat start, or null if not captured.
     */
    public static Integer getCombatStartRelicCounter(String relicId) {
        return combatStartRelicCounters.get(relicId);
    }

    /**
     * Get all pre-combat relic counters.
     * Used by Practice in Arena to restore relic state from before the fight.
     */
    public static Map<String, Integer> getCombatStartRelicCounters() {
        return new HashMap<>(combatStartRelicCounters);
    }

    /**
     * Get pre-combat HP.
     */
    public static int getCombatStartHp() {
        return combatStartHp;
    }

    /**
     * Get pre-combat max HP.
     */
    public static int getCombatStartMaxHp() {
        return combatStartMaxHp;
    }

    /**
     * Get pre-combat potions.
     */
    public static List<AbstractPotion> getCombatStartPotions() {
        return new ArrayList<>(combatStartPotions);
    }
}
