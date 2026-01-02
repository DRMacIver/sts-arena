package stsarena.arena;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles starting arena fights with custom loadouts.
 */
public class ArenaRunner {

    private static RandomLoadoutGenerator.GeneratedLoadout pendingLoadout;
    private static String pendingEncounter;
    private static boolean arenaRunInProgress = false;

    // Flag to indicate current run is an arena run (used to disable saving)
    private static boolean isArenaRun = false;

    // Current run tracking
    private static long currentLoadoutDbId = -1;
    private static long currentRunDbId = -1;
    private static RandomLoadoutGenerator.GeneratedLoadout currentLoadout;
    private static String currentEncounter;

    // Combat tracking
    private static int combatStartHp = 0;
    private static List<String> potionsUsedThisCombat = new ArrayList<>();

    // Flag to trigger return to main menu after arena fight ends
    private static boolean pendingReturnToMainMenu = false;

    /**
     * Start a random arena fight from the main menu.
     */
    public static void startRandomFight() {
        STSArena.logger.info("=== ARENA: startRandomFight() called ===");

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
        STSArena.logger.info("=== ARENA: startFight() called ===");
        STSArena.logger.info("ARENA: Loadout: " + loadout.name + ", Encounter: " + encounter);

        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;

        // Store for later tracking
        currentLoadout = loadout;
        currentEncounter = encounter;
        combatStartHp = loadout.currentHp;
        potionsUsedThisCombat.clear();

        STSArena.logger.info("Starting arena: " + loadout.playerClass + " vs " + encounter);

        // Save loadout to database
        STSArena.logger.info("ARENA: About to save loadout to database...");
        try {
            STSArena.logger.info("ARENA: Getting database instance...");
            ArenaDatabase db = ArenaDatabase.getInstance();
            STSArena.logger.info("ARENA: Database instance: " + db);
            ArenaRepository repo = new ArenaRepository(db);
            STSArena.logger.info("ARENA: Calling saveLoadout...");
            currentLoadoutDbId = repo.saveLoadout(loadout);
            STSArena.logger.info("ARENA: saveLoadout returned: " + currentLoadoutDbId);
            if (currentLoadoutDbId > 0) {
                // Start tracking the run
                STSArena.logger.info("ARENA: Calling startArenaRun...");
                currentRunDbId = repo.startArenaRun(currentLoadoutDbId, encounter, loadout.currentHp);
                STSArena.logger.info("ARENA: Arena run started with DB ID: " + currentRunDbId);
            } else {
                STSArena.logger.error("ARENA: saveLoadout failed, returned: " + currentLoadoutDbId);
            }
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Failed to save arena run to database: " + e.getMessage(), e);
            e.printStackTrace();
        }
        STSArena.logger.info("ARENA: Database save section complete. currentRunDbId=" + currentRunDbId);

        // Backup any existing save file before creating arena save
        SaveFileManager.backupOriginalSave(loadout.playerClass);

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
     * Also restores the original save file if one was backed up.
     */
    public static void clearArenaRun() {
        // Restore the original save file (or delete arena save if there was no original)
        SaveFileManager.restoreOriginalSave();

        isArenaRun = false;
        currentRunDbId = -1;
        currentLoadoutDbId = -1;
        currentLoadout = null;
        currentEncounter = null;
    }

    /**
     * Record a potion being used during combat.
     */
    public static void recordPotionUsed(String potionId) {
        if (isArenaRun && potionId != null) {
            potionsUsedThisCombat.add(potionId);
            STSArena.logger.info("ARENA: Recorded potion used: " + potionId);
        }
    }

    /**
     * Complete the current arena run with victory.
     */
    public static void recordVictory() {
        STSArena.logger.info("ARENA: recordVictory called - isArenaRun=" + isArenaRun + ", currentRunDbId=" + currentRunDbId);
        if (!isArenaRun || currentRunDbId < 0) {
            STSArena.logger.info("ARENA: recordVictory skipped - conditions not met");
            return;
        }

        STSArena.logger.info("ARENA: Recording victory!");

        ArenaRepository.ArenaRunOutcome outcome = new ArenaRepository.ArenaRunOutcome();
        outcome.result = ArenaRepository.ArenaRunOutcome.RunResult.VICTORY;
        outcome.endingHp = AbstractDungeon.player != null ? AbstractDungeon.player.currentHealth : 0;
        outcome.potionsUsed = new ArrayList<>(potionsUsedThisCombat);
        outcome.damageTaken = combatStartHp - outcome.endingHp;

        // Try to get combat stats from the game
        if (AbstractDungeon.actionManager != null) {
            outcome.cardsPlayed = AbstractDungeon.actionManager.cardsPlayedThisCombat.size();
            outcome.turnsTaken = AbstractDungeon.actionManager.turn;
        }

        // Calculate damage dealt (sum of monster max HP since they're all dead)
        outcome.damageDealt = 0;
        if (AbstractDungeon.getMonsters() != null) {
            for (com.megacrit.cardcrawl.monsters.AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                outcome.damageDealt += m.maxHealth;
            }
        }

        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            repo.completeArenaRun(currentRunDbId, outcome);
            STSArena.logger.info("ARENA: Victory recorded - HP: " + outcome.endingHp + ", Potions used: " + outcome.potionsUsed.size());
        } catch (Exception e) {
            STSArena.logger.error("Failed to record victory", e);
        }

        // Trigger return to main menu
        pendingReturnToMainMenu = true;
        STSArena.setReturnToArenaOnMainMenu();
    }

    /**
     * Complete the current arena run with defeat.
     */
    public static void recordDefeat() {
        if (!isArenaRun || currentRunDbId < 0) {
            return;
        }

        STSArena.logger.info("ARENA: Recording defeat!");

        ArenaRepository.ArenaRunOutcome outcome = new ArenaRepository.ArenaRunOutcome();
        outcome.result = ArenaRepository.ArenaRunOutcome.RunResult.DEFEAT;
        outcome.endingHp = 0;
        outcome.potionsUsed = new ArrayList<>(potionsUsedThisCombat);
        outcome.damageTaken = combatStartHp;

        // Try to get combat stats
        if (AbstractDungeon.actionManager != null) {
            outcome.cardsPlayed = AbstractDungeon.actionManager.cardsPlayedThisCombat.size();
            outcome.turnsTaken = AbstractDungeon.actionManager.turn;
        }

        // Estimate damage dealt (remaining monster HP shows what wasn't dealt)
        outcome.damageDealt = 0;
        if (AbstractDungeon.getMonsters() != null) {
            for (com.megacrit.cardcrawl.monsters.AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                outcome.damageDealt += (m.maxHealth - m.currentHealth);
            }
        }

        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            repo.completeArenaRun(currentRunDbId, outcome);
            STSArena.logger.info("ARENA: Defeat recorded - Damage dealt: " + outcome.damageDealt);
        } catch (Exception e) {
            STSArena.logger.error("Failed to record defeat", e);
        }

        // Set flag to return to arena selection when main menu is reached
        STSArena.setReturnToArenaOnMainMenu();
    }

    /**
     * Get the current run's database ID.
     */
    public static long getCurrentRunDbId() {
        return currentRunDbId;
    }

    /**
     * Get the current loadout name for display.
     */
    public static String getCurrentLoadoutName() {
        return currentLoadout != null ? currentLoadout.name : null;
    }

    /**
     * Check if we need to return to main menu after an arena fight.
     * Called from STSArena's update loop.
     */
    public static void checkPendingReturnToMainMenu() {
        if (!pendingReturnToMainMenu) {
            return;
        }

        // Force return to main menu
        STSArena.logger.info("ARENA: Forcing return to main menu");
        pendingReturnToMainMenu = false;

        try {
            // Clear the arena run state
            clearArenaRun();

            // Use the game's death screen mechanism to return to main menu
            // This properly handles all cleanup
            AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;

            // Trigger return to main menu like abandoning a run
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;

            CardCrawlGame.startOver();
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Error returning to main menu", e);
        }
    }

    /**
     * Start an arena fight with a saved loadout from the database.
     */
    public static void startFightWithSavedLoadout(ArenaRepository.LoadoutRecord savedLoadout, String encounter) {
        STSArena.logger.info("=== ARENA: startFightWithSavedLoadout() called ===");
        STSArena.logger.info("ARENA: Saved Loadout: " + savedLoadout.name + ", Encounter: " + encounter);

        // Convert saved loadout to GeneratedLoadout
        RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.fromSavedLoadout(savedLoadout);

        // Use the existing dbId for the loadout instead of saving a new one
        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;

        currentLoadout = loadout;
        currentEncounter = encounter;
        combatStartHp = loadout.currentHp;
        potionsUsedThisCombat.clear();
        currentLoadoutDbId = savedLoadout.dbId;

        STSArena.logger.info("Starting arena with saved loadout: " + loadout.playerClass + " vs " + encounter);

        // Start tracking the run (use existing loadout ID)
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            currentRunDbId = repo.startArenaRun(currentLoadoutDbId, encounter, loadout.currentHp);
            STSArena.logger.info("ARENA: Arena run started with DB ID: " + currentRunDbId);
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Failed to start arena run", e);
        }

        // Backup any existing save file before creating arena save
        SaveFileManager.backupOriginalSave(loadout.playerClass);

        // Create arena save file
        String savePath = ArenaSaveManager.createArenaSave(loadout, encounter);
        if (savePath == null) {
            STSArena.logger.error("Failed to create arena save file");
            clearPendingState();
            return;
        }

        // Set up the game to load the save
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.chosenCharacter = loadout.playerClass;

        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;

        if (CardCrawlGame.mainMenuScreen != null) {
            CardCrawlGame.mainMenuScreen.isFadingOut = true;
            CardCrawlGame.mainMenuScreen.fadedOut = true;
            CardCrawlGame.mainMenuScreen.fadeOutMusic();
        }
        CardCrawlGame.music.fadeOutTempBGM();

        CardCrawlGame.mode = CardCrawlGame.GameMode.CHAR_SELECT;

        STSArena.logger.info("Arena save created, transitioning to load it");
    }
}
