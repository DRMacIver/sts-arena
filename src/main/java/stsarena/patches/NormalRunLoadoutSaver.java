package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
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
import java.util.List;
import java.util.UUID;

/**
 * Saves loadouts from normal (non-arena) runs when they complete.
 * This allows players to replay their builds in arena mode.
 */
public class NormalRunLoadoutSaver {

    // State captured at the start of combat (for defeats)
    private static int combatStartHp = 0;
    private static int combatStartMaxHp = 0;
    private static List<AbstractPotion> combatStartPotions = new ArrayList<>();
    private static String combatEncounterId = null;

    // Most recently saved loadout (for "Try Again in Arena" feature)
    private static long lastSavedLoadoutId = -1;
    private static String lastSavedEncounterId = null;

    /**
     * Capture player state at the start of combat.
     * This is used for defeats so the loadout reflects pre-combat state.
     */
    @SpirePatch(clz = MonsterRoom.class, method = "onPlayerEntry")
    public static class OnCombatStart {
        @SpirePostfixPatch
        public static void Postfix() {
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

                // Capture encounter ID from the monster list
                // The current encounter should be at the front of the monster list
                combatEncounterId = null;
                if (AbstractDungeon.monsterList != null && !AbstractDungeon.monsterList.isEmpty()) {
                    combatEncounterId = AbstractDungeon.monsterList.get(0);
                }

                STSArena.logger.info("Captured combat start state: HP=" + combatStartHp + "/" + combatStartMaxHp +
                    ", potions=" + combatStartPotions.size() + ", encounter=" + combatEncounterId);
            }
        }
    }

    /**
     * Save loadout when player dies in a normal run.
     * Uses the pre-combat state (HP and potions from before the fatal fight).
     */
    @SpirePatch(clz = DeathScreen.class, method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
    public static class OnDefeat {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, MonsterGroup m) {
            if (!ArenaRunner.isArenaRun()) {
                STSArena.logger.info("Normal run ended in defeat - saving loadout with pre-combat state");
                saveLoadoutOnDefeat();
            }
        }
    }

    /**
     * Save loadout when player wins a normal run.
     * Uses the current state (post-victory).
     */
    @SpirePatch(clz = VictoryScreen.class, method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
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

            // Copy the relics
            List<AbstractRelic> relics = new ArrayList<>();
            for (AbstractRelic relic : player.relics) {
                relics.add(relic.makeCopy());
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

            // Copy the relics
            List<AbstractRelic> relics = new ArrayList<>();
            for (AbstractRelic relic : player.relics) {
                relics.add(relic.makeCopy());
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
}
