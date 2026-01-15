package stsarena.arena;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;
import stsarena.screens.ArenaLoadoutSelectScreen;

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
    private static boolean tookDamageThisCombat = false;

    // Flag to trigger return to main menu after arena fight ends
    private static boolean pendingReturnToMainMenu = false;

    // Flag and data for pending arena restart (used when restarting from imperfect victory)
    private static boolean pendingArenaRestart = false;
    private static RandomLoadoutGenerator.GeneratedLoadout pendingRestartLoadout = null;
    private static String pendingRestartEncounter = null;
    private static long pendingRestartLoadoutDbId = -1;

    // Track if arena was started from a normal run (Practice in Arena button)
    private static boolean startedFromNormalRun = false;

    // Flag to indicate we're in the middle of resuming a normal run
    // Used to prevent ClearArenaOnMainMenuPatch from clearing state during transition
    private static boolean resumingNormalRun = false;
    private static com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass normalRunPlayerClass = null;

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
        STSArena.logger.info("ARENA: BEFORE - arenaRunInProgress=" + arenaRunInProgress + ", isArenaRun=" + isArenaRun);

        // Close any open arena screens to ensure clean transition
        if (STSArena.encounterSelectScreen != null && STSArena.encounterSelectScreen.isOpen) {
            STSArena.logger.info("ARENA: Closing encounter select screen before starting fight");
            STSArena.encounterSelectScreen.close();
        }
        if (STSArena.loadoutSelectScreen != null && STSArena.loadoutSelectScreen.isOpen) {
            STSArena.logger.info("ARENA: Closing loadout select screen before starting fight");
            STSArena.loadoutSelectScreen.close();
        }

        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;
        STSArena.logger.info("ARENA: AFTER setting flags - arenaRunInProgress=" + arenaRunInProgress + ", isArenaRun=" + isArenaRun);

        // Store for later tracking
        currentLoadout = loadout;
        currentEncounter = encounter;
        combatStartHp = loadout.currentHp;
        potionsUsedThisCombat.clear();
        tookDamageThisCombat = false;

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

                // Update loadout selection state so subsequent fights use the same loadout
                ArenaRepository.LoadoutRecord savedRecord = repo.getLoadoutById(currentLoadoutDbId);
                if (savedRecord != null) {
                    ArenaLoadoutSelectScreen.selectedSavedLoadout = savedRecord;
                    ArenaLoadoutSelectScreen.useNewRandomLoadout = false;
                    STSArena.logger.info("ARENA: Updated loadout selection to use saved loadout: " + savedRecord.name);
                }
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
        STSArena.logger.info("ARENA: END startFight() - arenaRunInProgress=" + arenaRunInProgress + ", isArenaRun=" + isArenaRun);
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
        STSArena.logger.info("ARENA: Monster list size BEFORE add: " + AbstractDungeon.monsterList.size());
        if (AbstractDungeon.monsterList.size() > 0) {
            STSArena.logger.info("ARENA: Monster list first 3 entries: " +
                AbstractDungeon.monsterList.subList(0, Math.min(3, AbstractDungeon.monsterList.size())));
        }

        // Clear any lingering visual effects
        AbstractDungeon.topLevelEffects.clear();
        AbstractDungeon.effectList.clear();

        // Add encounter to monster list
        AbstractDungeon.monsterList.add(0, encounterName);
        STSArena.logger.info("ARENA: Added " + encounterName + " to monster list. List size now: " +
            AbstractDungeon.monsterList.size());
        STSArena.logger.info("ARENA: Monster list first 3 entries AFTER add: " +
            AbstractDungeon.monsterList.subList(0, Math.min(3, AbstractDungeon.monsterList.size())));

        // Get current node (should exist from dungeon creation)
        MapRoomNode cur = AbstractDungeon.currMapNode;
        if (cur == null) {
            STSArena.logger.error("ARENA ERROR: currMapNode is null, creating one");
            cur = new MapRoomNode(0, 0);
            cur.room = new MonsterRoom();
            AbstractDungeon.currMapNode = cur;
        }

        // Determine if we should use elite room (for burning effect at A18+)
        boolean useEliteRoom = LoadoutConfig.isEliteEncounter(encounterName) &&
                               AbstractDungeon.ascensionLevel >= 18;

        // Create next room node with appropriate room type
        MapRoomNode node = new MapRoomNode(cur.x, cur.y);
        if (useEliteRoom) {
            node.room = new MonsterRoomElite();
            STSArena.logger.info("ARENA: Using MonsterRoomElite for burning elite effect");
        } else {
            node.room = new MonsterRoom();
        }

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
     * Set whether an arena run is currently being set up.
     * Used by patches that need to clear this flag without clearing the full arena state.
     */
    public static void setArenaRunInProgress(boolean inProgress) {
        arenaRunInProgress = inProgress;
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
     * Check if we're in the middle of resuming a normal run.
     * Used by ClearArenaOnMainMenuPatch to avoid clearing state during transition.
     */
    public static boolean isResumingNormalRun() {
        return resumingNormalRun;
    }

    /**
     * Clear the resumingNormalRun flag.
     * Called after the normal run dungeon is fully initialized.
     */
    public static void clearResumingNormalRun() {
        if (resumingNormalRun) {
            STSArena.logger.info("ARENA: Normal run resume complete, clearing resumingNormalRun flag");
            resumingNormalRun = false;
        }
    }

    /**
     * Get the player's HP at combat start.
     * Used for imperfect victory detection to compare against end-of-combat HP.
     */
    public static int getCombatStartHp() {
        return combatStartHp;
    }

    /**
     * Record that the player took damage this combat.
     * Called from DamageCommand and can be called from damage-tracking patches.
     */
    public static void recordDamageTaken() {
        if (isArenaRun) {
            tookDamageThisCombat = true;
            STSArena.logger.info("ARENA: Recorded damage taken this combat");
        }
    }

    /**
     * Check if the player took any damage during the current combat.
     * This is used for imperfect victory detection, which is more reliable than
     * comparing HP values at end of combat (since relics may heal the player).
     */
    public static boolean didTakeDamageThisCombat() {
        return tookDamageThisCombat;
    }

    /**
     * Clear the arena run flag. Called when returning to main menu.
     * Also restores the original save file if one was backed up.
     */
    public static void clearArenaRun() {
        STSArena.logger.info("=== ARENA: clearArenaRun() called ===");
        STSArena.logger.info("ARENA: BEFORE clear - arenaRunInProgress=" + arenaRunInProgress + ", isArenaRun=" + isArenaRun);
        // Log stack trace to see who called us
        STSArena.logger.info("ARENA: clearArenaRun called from: " + new Exception().getStackTrace()[1]);

        // Restore the original save file (or delete arena save if there was no original)
        SaveFileManager.restoreOriginalSave();

        // Reset the loadingSave flag - we set this to true when starting arena fights
        // If we don't reset it, the next "new" game will try to load a save file
        CardCrawlGame.loadingSave = false;

        isArenaRun = false;
        arenaRunInProgress = false;
        resumingNormalRun = false;
        startedFromNormalRun = false;
        normalRunPlayerClass = null;
        currentRunDbId = -1;
        currentLoadoutDbId = -1;
        currentLoadout = null;
        currentEncounter = null;
        tookDamageThisCombat = false;

        // Clear pending state too
        pendingLoadout = null;
        pendingEncounter = null;
        pendingReturnToMainMenu = false;
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
     * @param imperfect If true, the player took damage during the fight.
     *                  For imperfect victories, we don't immediately return to menu
     *                  to allow the VictoryScreen to show "Try Again?" option.
     */
    public static void recordVictory(boolean imperfect) {
        STSArena.logger.info("ARENA: recordVictory called - isArenaRun=" + isArenaRun + ", currentRunDbId=" + currentRunDbId + ", imperfect=" + imperfect);
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

        // Note: For both perfect and imperfect victories, we DO NOT set pendingReturnToMainMenu here.
        // The return to menu is handled by:
        // - ArenaSkipCombatRewardScreenPatch for perfect victories (when reward screen opens)
        // - ArenaVictoryScreenPatch buttons for imperfect victories (user choice)
        // Setting pendingReturnToMainMenu here would cause a race condition where the
        // arena state gets cleared before CombatRewardScreen.open() is called, allowing
        // the normal game flow to continue and show the map screen.
    }

    /**
     * Complete the current arena run with victory (backward compatibility).
     * Assumes perfect victory.
     */
    public static void recordVictory() {
        recordVictory(false);
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
     * Get the current loadout's database ID.
     */
    public static long getCurrentLoadoutDbId() {
        return currentLoadoutDbId;
    }

    /**
     * Check if we need to return to main menu after an arena fight.
     * Called from STSArena's update loop.
     *
     * NOTE: This should never actually trigger anymore. The pendingReturnToMainMenu flag
     * is no longer set by recordVictory() to avoid race conditions. Return to menu is now
     * handled by ArenaSkipCombatRewardScreenPatch when CombatRewardScreen.open() is called.
     * This method is kept as a safety net in case something unexpected sets the flag.
     */
    public static void checkPendingReturnToMainMenu() {
        if (!pendingReturnToMainMenu) {
            return;
        }

        // This should not happen - log a warning
        STSArena.logger.info("ARENA: WARNING - pendingReturnToMainMenu was unexpectedly true. " +
            "This indicates a code path that should be reviewed. Proceeding with return to menu.");
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
     * Schedule an arena restart to happen after returning to main menu.
     * This is used when we need to restart from a state where direct mode change doesn't work
     * (e.g., imperfect victory where CombatRewardScreen was intercepted).
     */
    public static void scheduleArenaRestart() {
        STSArena.logger.info("=== ARENA: scheduleArenaRestart() called ===");
        STSArena.logger.info("ARENA: currentLoadout=" + currentLoadout + ", currentEncounter=" + currentEncounter);

        if (currentLoadout == null || currentEncounter == null) {
            STSArena.logger.error("Cannot schedule arena restart - no current loadout/encounter stored");
            return;
        }

        STSArena.logger.info("ARENA: Scheduling arena restart for loadout: " + currentLoadout.name +
            ", encounter: " + currentEncounter);

        // Store the loadout/encounter for restart after returning to main menu
        // IMPORTANT: Store these BEFORE calling clearArenaRun() which clears currentLoadout/currentEncounter
        pendingArenaRestart = true;
        pendingRestartLoadout = currentLoadout;
        pendingRestartEncounter = currentEncounter;
        pendingRestartLoadoutDbId = currentLoadoutDbId;

        STSArena.logger.info("ARENA: pendingArenaRestart=" + pendingArenaRestart +
            ", pendingRestartLoadout=" + (pendingRestartLoadout != null ? pendingRestartLoadout.name : "null") +
            ", pendingRestartEncounter=" + pendingRestartEncounter);

        // Return to main menu - the arena will be restarted when main menu is reached
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;

        // Mark room as complete
        if (AbstractDungeon.getCurrRoom() != null) {
            AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
        }

        // Clear arena state and return to main menu
        clearArenaRun();
        STSArena.setReturnToArenaOnMainMenu();  // This ensures we go to arena selection
        CardCrawlGame.startOver();
    }

    /**
     * Check if there's a pending arena restart and execute it.
     * Called from STSArena.receivePostUpdate() when at main menu.
     */
    public static void checkPendingArenaRestart() {
        STSArena.logger.info("=== ARENA: checkPendingArenaRestart() called ===");
        STSArena.logger.info("ARENA: pendingArenaRestart=" + pendingArenaRestart +
            ", pendingRestartLoadout=" + (pendingRestartLoadout != null ? pendingRestartLoadout.name : "null") +
            ", pendingRestartEncounter=" + pendingRestartEncounter);

        if (!pendingArenaRestart || pendingRestartLoadout == null || pendingRestartEncounter == null) {
            STSArena.logger.info("ARENA: checkPendingArenaRestart - conditions not met, returning early");
            return;
        }

        STSArena.logger.info("ARENA: Executing pending arena restart");

        // Clear the pending flag
        RandomLoadoutGenerator.GeneratedLoadout loadout = pendingRestartLoadout;
        String encounter = pendingRestartEncounter;
        long loadoutDbId = pendingRestartLoadoutDbId;
        pendingArenaRestart = false;
        pendingRestartLoadout = null;
        pendingRestartEncounter = null;
        pendingRestartLoadoutDbId = -1;

        // Start the arena fight (using startFight which will create a new DB entry)
        startFight(loadout, encounter);
    }

    /**
     * Check if there's a pending arena restart.
     */
    public static boolean hasPendingArenaRestart() {
        return pendingArenaRestart;
    }

    /**
     * Restart the current arena fight with the same loadout and encounter.
     * Called from the "Try Again" button on the arena death screen.
     */
    public static void restartCurrentFight() {
        if (currentLoadout == null || currentEncounter == null) {
            STSArena.logger.error("Cannot restart fight - no current loadout/encounter stored");
            return;
        }

        STSArena.logger.info("=== ARENA: restartCurrentFight() called ===");
        STSArena.logger.info("ARENA: Restarting with loadout: " + currentLoadout.name + ", encounter: " + currentEncounter);

        // Store references before clearing state
        RandomLoadoutGenerator.GeneratedLoadout loadout = currentLoadout;
        String encounter = currentEncounter;
        long loadoutDbId = currentLoadoutDbId;

        // Clear current arena state
        clearArenaRun();

        // Restart with the same loadout and encounter
        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;

        currentLoadout = loadout;
        currentEncounter = encounter;
        combatStartHp = loadout.currentHp;
        potionsUsedThisCombat.clear();
        tookDamageThisCombat = false;
        currentLoadoutDbId = loadoutDbId;

        // Start tracking a new run
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            currentRunDbId = repo.startArenaRun(currentLoadoutDbId, encounter, loadout.currentHp);
            STSArena.logger.info("ARENA: New arena run started with DB ID: " + currentRunDbId);
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

        // Set main menu to already faded out to prevent visible flash
        if (CardCrawlGame.mainMenuScreen != null) {
            CardCrawlGame.mainMenuScreen.isFadingOut = true;
            CardCrawlGame.mainMenuScreen.fadedOut = true;
            CardCrawlGame.mainMenuScreen.fadeOutMusic();
        }
        CardCrawlGame.music.fadeOutTempBGM();

        // If we're still in a dungeon (e.g., after imperfect victory where we intercepted
        // CombatRewardScreen), we need to properly clean up before transitioning.
        // Mark the room as complete to help with state cleanup.
        if (AbstractDungeon.getCurrRoom() != null) {
            AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
        }

        // Close any open screens that might interfere with the transition
        try {
            if (AbstractDungeon.screen != null &&
                AbstractDungeon.screen != AbstractDungeon.CurrentScreen.NONE) {
                AbstractDungeon.closeCurrentScreen();
            }
        } catch (Exception e) {
            STSArena.logger.warn("ARENA: Error closing screen during restart: " + e.getMessage());
        }

        // Close the death screen and trigger game restart
        CardCrawlGame.mode = CardCrawlGame.GameMode.CHAR_SELECT;

        STSArena.logger.info("Arena restart initiated");
    }

    /**
     * Get the current loadout for retry purposes.
     */
    public static RandomLoadoutGenerator.GeneratedLoadout getCurrentLoadout() {
        return currentLoadout;
    }

    /**
     * Get the current encounter for retry purposes.
     */
    public static String getCurrentEncounter() {
        return currentEncounter;
    }

    /**
     * Mark that the next arena run is starting from a normal run (Practice in Arena).
     * Call this before startFightWithSavedLoadout when coming from the pause menu.
     */
    public static void setStartedFromNormalRun(com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass playerClass) {
        startedFromNormalRun = true;
        normalRunPlayerClass = playerClass;
        STSArena.logger.info("ARENA: Marked as starting from normal run (class: " + playerClass + ")");
    }

    /**
     * Check if the current arena run was started from a normal run.
     */
    public static boolean wasStartedFromNormalRun() {
        return startedFromNormalRun;
    }

    /**
     * Get the player class of the normal run we came from.
     */
    public static com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass getNormalRunPlayerClass() {
        return normalRunPlayerClass;
    }

    /**
     * Resume the normal run that was in progress before arena started.
     * Call this instead of startOver() when leaving arena via pause menu.
     */
    public static void resumeNormalRun() {
        if (!startedFromNormalRun || normalRunPlayerClass == null) {
            STSArena.logger.warn("ARENA: Cannot resume normal run - not started from one");
            return;
        }

        STSArena.logger.info("ARENA: Resuming normal run for " + normalRunPlayerClass);

        // Set flag to prevent ClearArenaOnMainMenuPatch from interfering during transition
        resumingNormalRun = true;

        // Restore the original save file
        SaveFileManager.restoreOriginalSave();

        // Set up to load the save
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.chosenCharacter = normalRunPlayerClass;

        // Clear arena state
        isArenaRun = false;
        arenaRunInProgress = false;
        startedFromNormalRun = false;
        normalRunPlayerClass = null;
        currentRunDbId = -1;
        currentLoadoutDbId = -1;
        currentLoadout = null;
        currentEncounter = null;
        pendingLoadout = null;
        pendingEncounter = null;
        pendingReturnToMainMenu = false;

        // Set main menu to faded out to skip animation
        if (CardCrawlGame.mainMenuScreen != null) {
            CardCrawlGame.mainMenuScreen.isFadingOut = true;
            CardCrawlGame.mainMenuScreen.fadedOut = true;
        }

        // Transition to load the save
        CardCrawlGame.mode = CardCrawlGame.GameMode.CHAR_SELECT;

        STSArena.logger.info("ARENA: Normal run resume initiated");
    }

    /**
     * Start an arena fight with a loadout ID and encounter from a normal run defeat.
     * Used by the "Try Again in Arena Mode" button.
     */
    public static void startFightFromDefeat(long loadoutDbId, String encounter) {
        STSArena.logger.info("=== ARENA: startFightFromDefeat() called ===");
        STSArena.logger.info("ARENA: Loadout DB ID: " + loadoutDbId + ", Encounter: " + encounter);

        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            ArenaRepository.LoadoutRecord savedLoadout = repo.getLoadoutById(loadoutDbId);

            if (savedLoadout == null) {
                STSArena.logger.error("Failed to load loadout with ID: " + loadoutDbId);
                return;
            }

            startFightWithSavedLoadout(savedLoadout, encounter);
        } catch (Exception e) {
            STSArena.logger.error("Error starting fight from defeat", e);
        }
    }

    // Flag to indicate we should restart fight after editing loadout
    private static boolean pendingRetryAfterEdit = false;

    /**
     * Open the loadout editor for deck tweaks, then restart the fight.
     * Called from "Modify Deck" button on death/victory screens.
     */
    public static void modifyDeckAndRetry() {
        if (currentLoadout == null || currentLoadoutDbId <= 0) {
            STSArena.logger.error("Cannot modify deck - no current loadout stored");
            return;
        }

        STSArena.logger.info("ARENA: Opening deck editor for retry with loadout ID: " + currentLoadoutDbId);

        // Set flag to restart fight after editing
        pendingRetryAfterEdit = true;

        // Get the loadout record from database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            ArenaRepository.LoadoutRecord loadoutRecord = repo.getLoadoutById(currentLoadoutDbId);

            if (loadoutRecord != null) {
                // Clear arena state but keep the encounter for retry
                String encounter = currentEncounter;

                // Store encounter for later
                pendingEncounter = encounter;

                // Trigger return to main menu and open editor
                Settings.isTrial = false;
                Settings.isDailyRun = false;
                Settings.isEndless = false;

                // Clear arena run but remember we're editing for retry
                isArenaRun = false;
                arenaRunInProgress = false;

                STSArena.setOpenLoadoutEditorOnMainMenu(loadoutRecord);
                CardCrawlGame.startOver();
            } else {
                STSArena.logger.error("Failed to load loadout record for editing");
                pendingRetryAfterEdit = false;
            }
        } catch (Exception e) {
            STSArena.logger.error("Error opening deck editor for retry", e);
            pendingRetryAfterEdit = false;
        }
    }

    /**
     * Check if we're waiting to retry after editing a loadout.
     */
    public static boolean isPendingRetryAfterEdit() {
        return pendingRetryAfterEdit;
    }

    /**
     * Called after loadout editing is complete. Restarts the fight if we were editing for retry.
     */
    public static void completeRetryEdit(ArenaRepository.LoadoutRecord editedLoadout) {
        if (!pendingRetryAfterEdit || pendingEncounter == null) {
            STSArena.logger.info("ARENA: completeRetryEdit called but not in retry edit mode");
            pendingRetryAfterEdit = false;
            return;
        }

        STSArena.logger.info("ARENA: Completing retry edit, starting fight with modified loadout");
        pendingRetryAfterEdit = false;
        String encounter = pendingEncounter;
        pendingEncounter = null;

        // Start fight with the edited loadout
        startFightWithSavedLoadout(editedLoadout, encounter);
    }

    /**
     * Clear the pending retry after edit state.
     */
    public static void clearPendingRetryEdit() {
        pendingRetryAfterEdit = false;
    }

    /**
     * Start an arena fight with a saved loadout from the database.
     */
    public static void startFightWithSavedLoadout(ArenaRepository.LoadoutRecord savedLoadout, String encounter) {
        STSArena.logger.info("=== ARENA: startFightWithSavedLoadout() called ===");
        STSArena.logger.info("ARENA: Saved Loadout: " + savedLoadout.name + ", Encounter: " + encounter);

        // Convert saved loadout to GeneratedLoadout
        RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.fromSavedLoadout(savedLoadout);

        // When starting from a normal run, DON'T force a new save.
        // The game already autosaved with ENTER_ROOM type when the player entered the combat room.
        // That autosave contains the correct pre-combat state.
        // If we force a save now with POST_COMBAT type, we'd overwrite the good save with a bad one
        // that puts the player at the loot screen instead of in combat.
        // We'll just back up the existing autosave as-is.
        STSArena.logger.info("ARENA: Using existing autosave for backup (already saved at ENTER_ROOM)");

        // Use the existing dbId for the loadout instead of saving a new one
        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;
        isArenaRun = true;

        currentLoadout = loadout;
        currentEncounter = encounter;
        combatStartHp = loadout.currentHp;
        potionsUsedThisCombat.clear();
        tookDamageThisCombat = false;
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
