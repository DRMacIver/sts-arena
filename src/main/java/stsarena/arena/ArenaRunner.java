package stsarena.arena;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import stsarena.STSArena;

/**
 * Handles starting arena fights with custom loadouts.
 */
public class ArenaRunner {

    private static RandomLoadoutGenerator.GeneratedLoadout pendingLoadout;
    private static String pendingEncounter;
    private static boolean arenaRunInProgress = false;

    // Flag to indicate current run is an arena run (used to disable saving)
    private static boolean isArenaRun = false;

    /**
     * Start a random arena fight from the main menu.
     */
    public static void startRandomFight() {
        STSArena.logger.info("Starting random arena fight");

        // Generate random loadout and encounter
        RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
        String encounter = RandomLoadoutGenerator.getRandomEncounter();

        startFight(loadout, encounter);
    }

    /**
     * Start an arena fight with a specific loadout and encounter.
     * Uses save file approach to properly initialize game state.
     */
    public static void startFight(RandomLoadoutGenerator.GeneratedLoadout loadout, String encounter) {
        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;

        STSArena.logger.info("Starting arena: " + loadout.playerClass + " vs " + encounter);

        // Create arena save file
        String savePath = ArenaSaveManager.createArenaSave(loadout, encounter);
        if (savePath == null) {
            STSArena.logger.error("Failed to create arena save file");
            clearPendingState();
            return;
        }

        // Set up the game to load the save - mimic what resumeGame() does
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.chosenCharacter = loadout.playerClass;

        // Configure run settings
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;

        // Trigger the fade out and set fadedOut to skip animation
        if (CardCrawlGame.mainMenuScreen != null) {
            CardCrawlGame.mainMenuScreen.isFadingOut = true;
            CardCrawlGame.mainMenuScreen.fadedOut = true;  // Skip the fade animation
            CardCrawlGame.mainMenuScreen.fadeOutMusic();
        }
        CardCrawlGame.music.fadeOutTempBGM();

        // Set mode to CHAR_SELECT - the game will see fadedOut=true and
        // transition to GAMEPLAY, loading the save
        CardCrawlGame.mode = CardCrawlGame.GameMode.CHAR_SELECT;

        STSArena.logger.info("Arena save created, transitioning to load it");
    }

    /**
     * Start the pending fight.
     * Called from PostUpdateSubscriber after the dungeon is fully initialized.
     */
    public static void startPendingFight() {
        STSArena.logger.info("=== ARENA: startPendingFight called ===");
        STSArena.logger.info("ARENA: arenaRunInProgress=" + arenaRunInProgress + ", pendingEncounter=" + pendingEncounter);

        if (!arenaRunInProgress) {
            STSArena.logger.info("ARENA: Skipping - arenaRunInProgress is false");
            return;
        }
        if (pendingEncounter == null) {
            STSArena.logger.info("ARENA: Skipping - pendingEncounter is null");
            return;
        }

        STSArena.logger.info("ARENA: Will transition to fight: " + pendingEncounter);
        STSArena.logger.info("ARENA: CardCrawlGame.mode = " + CardCrawlGame.mode);

        try {
            transitionToFight(pendingEncounter);
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Exception in transitionToFight", e);
        } finally {
            STSArena.logger.info("ARENA: Clearing pending state");
            clearPendingState();
        }
    }

    /**
     * Clear the pending loadout and encounter state.
     */
    private static void clearPendingState() {
        pendingLoadout = null;
        pendingEncounter = null;
        arenaRunInProgress = false;
    }

    /**
     * Force transition to a monster fight.
     * Uses nextRoomTransitionStart() like BaseMod's dev console Fight command.
     */
    private static void transitionToFight(String encounterName) {
        STSArena.logger.info("=== ARENA: transitionToFight START for: " + encounterName + " ===");

        // Validate preconditions
        if (encounterName == null || encounterName.isEmpty()) {
            STSArena.logger.error("ARENA ERROR: encounterName is null or empty!");
            return;
        }
        if (AbstractDungeon.player == null) {
            STSArena.logger.error("ARENA ERROR: AbstractDungeon.player is null!");
            return;
        }
        if (AbstractDungeon.monsterList == null) {
            STSArena.logger.error("ARENA ERROR: AbstractDungeon.monsterList is null!");
            return;
        }

        STSArena.logger.info("ARENA: Preconditions OK. Player: " + AbstractDungeon.player.chosenClass);
        STSArena.logger.info("ARENA: Current currMapNode: " + AbstractDungeon.currMapNode);

        // Clear any lingering visual effects
        AbstractDungeon.topLevelEffects.clear();
        AbstractDungeon.effectList.clear();

        // Add encounter to monster list
        AbstractDungeon.monsterList.add(0, encounterName);
        STSArena.logger.info("ARENA: Added " + encounterName + " to monster list");

        // Get current node (should exist from dungeon creation)
        MapRoomNode cur = AbstractDungeon.currMapNode;
        if (cur == null) {
            STSArena.logger.error("ARENA ERROR: currMapNode is null, creating one");
            cur = new MapRoomNode(0, 0);
            cur.room = new MonsterRoom();
            AbstractDungeon.currMapNode = cur;
        }

        // Create next room node with MonsterRoom (like BaseMod Fight command)
        MapRoomNode node = new MapRoomNode(cur.x, cur.y);
        node.room = new MonsterRoom();

        // Copy edges from current node
        for (com.megacrit.cardcrawl.map.MapEdge edge : cur.getEdges()) {
            node.addEdge(edge);
        }

        STSArena.logger.info("ARENA: Created next room node, calling nextRoomTransitionStart()");

        // Use the game's room transition system
        AbstractDungeon.nextRoom = node;
        AbstractDungeon.nextRoomTransitionStart();

        STSArena.logger.info("=== ARENA: transitionToFight END ===");
    }

    /**
     * Check if an arena run is currently being set up.
     */
    public static boolean isArenaRunInProgress() {
        return arenaRunInProgress;
    }

    /**
     * Check if we have a pending loadout to apply.
     */
    public static boolean hasPendingLoadout() {
        return pendingLoadout != null;
    }

    /**
     * Check if the current run is an arena run.
     * Used to disable saving for arena runs.
     */
    public static boolean isArenaRun() {
        return isArenaRun;
    }

    /**
     * Clear the arena run flag. Called when returning to main menu.
     */
    public static void clearArenaRun() {
        isArenaRun = false;
    }
}
