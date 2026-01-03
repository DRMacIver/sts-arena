package stsarena.patches;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
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
 * Adds a keybind (F10) to open arena mode with the current deck during a normal run.
 * Press F10 during any point in a normal run to:
 * 1. Save current deck/relics/potions/HP as a loadout
 * 2. Open the encounter selection screen to practice with that loadout
 */
public class ArenaKeybindPatch {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    // Track if key was just pressed to avoid repeat
    private static boolean keyWasDown = false;

    // The saved loadout ID for quick access
    private static long pendingLoadoutId = -1;

    /**
     * Check for arena keybind during dungeon update.
     */
    @SpirePatch(clz = AbstractDungeon.class, method = "update")
    public static class DungeonUpdatePatch {
        @SpirePostfixPatch
        public static void Postfix() {
            // Only process in normal runs, not arena
            if (ArenaRunner.isArenaRun()) {
                return;
            }

            // Check if F10 is pressed
            boolean keyIsDown = Gdx.input.isKeyPressed(Input.Keys.F10);

            if (keyIsDown && !keyWasDown) {
                // Key just pressed
                handleArenaKeybind();
            }

            keyWasDown = keyIsDown;
        }
    }

    /**
     * Handle the arena keybind press.
     */
    private static void handleArenaKeybind() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) {
            STSArena.logger.warn("Cannot open arena - no player");
            return;
        }

        // Don't trigger during certain screens
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.DEATH ||
            AbstractDungeon.screen == AbstractDungeon.CurrentScreen.VICTORY) {
            return;
        }

        STSArena.logger.info("F10 pressed - saving current deck and opening arena");

        try {
            // Create loadout from current state
            long loadoutId = saveCurrentLoadout(player);

            if (loadoutId > 0) {
                pendingLoadoutId = loadoutId;
                STSArena.logger.info("Saved loadout with ID: " + loadoutId);

                // Open the encounter selection screen
                // The encounter screen will pick up the pending loadout
                STSArena.openEncounterSelectScreenWithLoadout(loadoutId);
            }
        } catch (Exception e) {
            STSArena.logger.error("Error handling arena keybind", e);
        }
    }

    /**
     * Save the current player state as a loadout.
     */
    private static long saveCurrentLoadout(AbstractPlayer player) {
        // Generate unique ID and name
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String name = generateLoadoutName(player);

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

        // Copy current potions
        List<AbstractPotion> potions = new ArrayList<>();
        for (AbstractPotion potion : player.potions) {
            if (!(potion instanceof PotionSlot)) {
                potions.add(potion.makeCopy());
            }
        }

        // Check for Prismatic Shard
        boolean hasPrismaticShard = player.hasRelic("PrismaticShard");

        // Get ascension level
        int ascensionLevel = AbstractDungeon.ascensionLevel;

        // Get potion slots from player
        int potionSlots = player.potionSlots;

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
            player.maxHealth,
            player.currentHealth,
            ascensionLevel
        );

        // Save to database
        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        return repo.saveLoadout(loadout);
    }

    /**
     * Generate a descriptive name for the loadout.
     */
    private static String generateLoadoutName(AbstractPlayer player) {
        String className = player.chosenClass.name();
        // Convert to title case (e.g., "IRONCLAD" -> "Ironclad")
        className = className.substring(0, 1) + className.substring(1).toLowerCase();

        int floor = AbstractDungeon.floorNum;
        int ascension = AbstractDungeon.ascensionLevel;
        String timestamp = DATE_FORMAT.format(new Date());

        if (ascension > 0) {
            return className + " A" + ascension + " F" + floor + " Practice (" + timestamp + ")";
        } else {
            return className + " F" + floor + " Practice (" + timestamp + ")";
        }
    }

    /**
     * Get the pending loadout ID (set when keybind was pressed).
     */
    public static long getPendingLoadoutId() {
        return pendingLoadoutId;
    }

    /**
     * Clear the pending loadout.
     */
    public static void clearPendingLoadout() {
        pendingLoadoutId = -1;
    }
}
