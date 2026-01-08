package stsarena;

import basemod.BaseMod;
import basemod.interfaces.PostDungeonInitializeSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsarena.arena.ArenaRunner;
import stsarena.arena.SaveFileManager;
import stsarena.communication.ArenaBackCommand;
import stsarena.communication.ArenaCommand;
import stsarena.communication.ArenaLoadoutCommand;
import stsarena.communication.ArenaScreenCommand;
import stsarena.communication.CursorHideCommand;
import stsarena.communication.LoseCommand;
import stsarena.communication.WinCommand;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;
import stsarena.screens.ArenaEncounterSelectScreen;
import stsarena.screens.ArenaHistoryScreen;
import stsarena.screens.ArenaLoadoutSelectScreen;
import stsarena.screens.ArenaStatsScreen;
import stsarena.screens.LoadoutCreatorScreen;

/**
 * STS Arena - A Slay the Spire mod for playing isolated single fights as practice runs.
 *
 * This mod allows players to:
 * - Select any enemy encounter from the game
 * - Build a custom deck with any cards
 * - Choose relics and potions
 * - Practice specific fights without playing through the full game
 */
@SpireInitializer
public class STSArena implements PostInitializeSubscriber, PostDungeonInitializeSubscriber, PreUpdateSubscriber, PostUpdateSubscriber, PostRenderSubscriber {

    public static final Logger logger = LogManager.getLogger(STSArena.class.getName());
    public static final String MOD_ID = "stsarena";

    // Arena screens
    public static ArenaHistoryScreen historyScreen;
    public static ArenaEncounterSelectScreen encounterSelectScreen;
    public static ArenaLoadoutSelectScreen loadoutSelectScreen;
    public static LoadoutCreatorScreen loadoutCreatorScreen;
    public static ArenaStatsScreen statsScreen;

    // Flag to trigger fight start on next update (gives game time to initialize)
    private static boolean pendingFightStart = false;

    // Flag to return to arena selection after returning to main menu
    private static boolean returnToArenaOnMainMenu = false;

    // Pending loadout to open for editing (for retry deck tweaks)
    private static ArenaRepository.LoadoutRecord pendingLoadoutEditorRecord = null;

    public STSArena() {
        logger.info("Initializing STS Arena");
        BaseMod.subscribe(this);

        // Register commands with CommunicationMod early (during mod initialization)
        // This must happen before PostInitialize because CommunicationMod starts
        // accepting commands as soon as it receives "ready" during mod init
        //
        // IMPORTANT: We must check for CommunicationMod BEFORE loading our command classes,
        // because they implement CommandExtension and can't be loaded without it.
        registerCommunicationModCommands();
    }

    /**
     * Register commands with CommunicationMod if it's available.
     * Uses reflection to avoid loading command classes when CommunicationMod isn't present.
     */
    private void registerCommunicationModCommands() {
        try {
            // Check if CommunicationMod is loaded by trying to load its class
            Class.forName("communicationmod.CommandExecutor");

            // CommunicationMod is available - now safe to load and register our commands
            ArenaCommand.register();
            ArenaBackCommand.register();
            ArenaLoadoutCommand.register();
            ArenaScreenCommand.register();
            CursorHideCommand.register();
            LoseCommand.register();
            WinCommand.register();
            logger.info("CommunicationMod detected - commands registered");
        } catch (ClassNotFoundException e) {
            logger.info("CommunicationMod not loaded - arena/lose commands not available");
        }
    }

    /**
     * Required by @SpireInitializer - called by ModTheSpire to initialize the mod.
     */
    public static void initialize() {
        logger.info("STS Arena initialize()");
        new STSArena();
    }

    /**
     * Called after the game has fully initialized.
     * This is where we set up our UI elements and custom game mode.
     */
    @Override
    public void receivePostInitialize() {
        logger.info("STS Arena post-initialize");

        // Clean up any orphaned arena saves from previous sessions (crash recovery)
        SaveFileManager.cleanupOrphanedArenaSaves();

        // Initialize the database
        ArenaDatabase.getInstance();

        // Initialize the screens
        historyScreen = new ArenaHistoryScreen();
        encounterSelectScreen = new ArenaEncounterSelectScreen();
        loadoutSelectScreen = new ArenaLoadoutSelectScreen();
        loadoutCreatorScreen = new LoadoutCreatorScreen();
        statsScreen = new ArenaStatsScreen();

        // Arena Mode button is added via patches/MainMenuArenaPatch
        // Note: CommunicationMod commands are registered in constructor
    }

    /**
     * Called after a dungeon is initialized.
     * We now apply loadout directly in ArenaRunner.startFight(), so just set the flag.
     */
    @Override
    public void receivePostDungeonInitialize() {
        logger.info("=== ARENA: receivePostDungeonInitialize called ===");
        logger.info("ARENA: ArenaRunner.isArenaRunInProgress() = " + ArenaRunner.isArenaRunInProgress());
        if (ArenaRunner.isArenaRunInProgress()) {
            logger.info("ARENA: Setting pendingFightStart = true");
            pendingFightStart = true;
        }
    }

    /**
     * Called before the game updates. Used to handle our screens before main menu processes input.
     */
    @Override
    public void receivePreUpdate() {
        // Update our screens BEFORE main menu so we get input first
        if (historyScreen != null && historyScreen.isOpen) {
            historyScreen.update();
            // Consume remaining input to block main menu
            InputHelper.justClickedLeft = false;
            InputHelper.justClickedRight = false;
        }
        if (encounterSelectScreen != null && encounterSelectScreen.isOpen) {
            encounterSelectScreen.update();
            // Consume remaining input to block main menu
            InputHelper.justClickedLeft = false;
            InputHelper.justClickedRight = false;
        }
        if (loadoutSelectScreen != null && loadoutSelectScreen.isOpen) {
            loadoutSelectScreen.update();
            // Consume remaining input to block main menu
            InputHelper.justClickedLeft = false;
            InputHelper.justClickedRight = false;
        }
        if (loadoutCreatorScreen != null && loadoutCreatorScreen.isOpen) {
            loadoutCreatorScreen.update();
            // Consume remaining input to block main menu
            InputHelper.justClickedLeft = false;
            InputHelper.justClickedRight = false;
        }
        if (statsScreen != null && statsScreen.isOpen) {
            statsScreen.update();
            // Consume remaining input to block main menu
            InputHelper.justClickedLeft = false;
            InputHelper.justClickedRight = false;
        }
    }

    /**
     * Called every frame. Used to trigger fight start after dungeon is fully ready.
     */
    @Override
    public void receivePostUpdate() {
        if (pendingFightStart) {
            logger.info("=== ARENA: receivePostUpdate - pendingFightStart is true ===");
            logger.info("ARENA: ArenaRunner.isArenaRunInProgress() = " + ArenaRunner.isArenaRunInProgress());
            if (ArenaRunner.isArenaRunInProgress()) {
                pendingFightStart = false;
                logger.info("ARENA: Calling ArenaRunner.startPendingFight()");
                ArenaRunner.startPendingFight();
            } else {
                logger.info("ARENA: ArenaRunner not in progress, resetting pendingFightStart");
                pendingFightStart = false;
            }
        }

        // Check if arena fight ended and we need to return to main menu
        ArenaRunner.checkPendingReturnToMainMenu();

        // Check if we should open the loadout editor after returning to main menu (retry deck tweaks)
        if (pendingLoadoutEditorRecord != null &&
            CardCrawlGame.mainMenuScreen != null &&
            AbstractDungeon.player == null &&
            !CardCrawlGame.loadingSave) {
            ArenaRepository.LoadoutRecord loadout = pendingLoadoutEditorRecord;
            pendingLoadoutEditorRecord = null;
            logger.info("ARENA: Returned to main menu, opening loadout editor for retry");
            if (loadoutCreatorScreen != null && !loadoutCreatorScreen.isOpen) {
                loadoutCreatorScreen.openForRetryEdit(loadout);
            }
            return;  // Don't also open encounter select
        }

        // Check if we should return to arena selection after returning to main menu
        // We detect main menu by: mainMenuScreen exists, no dungeon active, not loading
        if (returnToArenaOnMainMenu &&
            CardCrawlGame.mainMenuScreen != null &&
            AbstractDungeon.player == null &&
            !CardCrawlGame.loadingSave) {
            returnToArenaOnMainMenu = false;
            logger.info("ARENA: Returned to main menu, opening encounter selection");
            if (encounterSelectScreen != null) {
                if (!encounterSelectScreen.isOpen) {
                    openEncounterSelectScreen();
                } else {
                    // Screen is already open - just refresh the outcomes to show new victories
                    logger.info("ARENA: Encounter screen already open, refreshing outcomes");
                    encounterSelectScreen.refreshEncounterOutcomes();
                }
            }
        }
    }

    /**
     * Called after rendering. Used to render our custom screens.
     */
    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (historyScreen != null && historyScreen.isOpen) {
            historyScreen.render(sb);
        }
        if (encounterSelectScreen != null && encounterSelectScreen.isOpen) {
            encounterSelectScreen.render(sb);
        }
        if (loadoutSelectScreen != null && loadoutSelectScreen.isOpen) {
            loadoutSelectScreen.render(sb);
        }
        if (loadoutCreatorScreen != null && loadoutCreatorScreen.isOpen) {
            loadoutCreatorScreen.render(sb);
        }
        if (statsScreen != null && statsScreen.isOpen) {
            statsScreen.render(sb);
        }
    }

    /**
     * Open the arena history screen.
     */
    public static void openHistoryScreen() {
        if (historyScreen != null) {
            historyScreen.open();
        }
    }

    /**
     * Open the arena encounter select screen.
     */
    public static void openEncounterSelectScreen() {
        if (encounterSelectScreen != null) {
            encounterSelectScreen.open();
        }
    }

    /**
     * Open the arena encounter select screen with a pre-selected loadout.
     * Used when entering arena from a normal run (F10 keybind).
     */
    public static void openEncounterSelectScreenWithLoadout(long loadoutId) {
        if (encounterSelectScreen != null) {
            encounterSelectScreen.openWithLoadout(loadoutId);
        }
    }

    /**
     * Open the arena encounter select screen with a pre-selected loadout and current encounter.
     * Used when entering arena from a fight (Practice in Arena button during combat).
     */
    public static void openEncounterSelectScreenWithLoadout(long loadoutId, String currentEncounter) {
        if (encounterSelectScreen != null) {
            encounterSelectScreen.openWithLoadout(loadoutId, currentEncounter);
        }
    }

    /**
     * Open the arena loadout select screen.
     * This is the entry point for arena mode.
     */
    public static void openLoadoutSelectScreen() {
        if (loadoutSelectScreen != null) {
            loadoutSelectScreen.open();
        }
    }

    /**
     * Open the loadout creator screen.
     */
    public static void openLoadoutCreatorScreen() {
        if (loadoutCreatorScreen != null) {
            loadoutCreatorScreen.open();
        }
    }

    /**
     * Open the loadout creator screen pre-populated with an existing loadout (for copying).
     */
    public static void openLoadoutCreatorWithLoadout(ArenaRepository.LoadoutRecord loadout) {
        if (loadoutCreatorScreen != null) {
            loadoutCreatorScreen.openWithLoadout(loadout);
        }
    }

    /**
     * Open the loadout creator screen for editing an existing loadout in-place.
     */
    public static void openLoadoutCreatorForEdit(ArenaRepository.LoadoutRecord loadout) {
        if (loadoutCreatorScreen != null) {
            loadoutCreatorScreen.openForEdit(loadout);
        }
    }

    /**
     * Open the history screen filtered to a specific loadout.
     */
    public static void openHistoryScreenForLoadout(long loadoutId, String loadoutName) {
        if (historyScreen != null) {
            historyScreen.openForLoadout(loadoutId, loadoutName);
        }
    }

    /**
     * Open the arena stats screen showing loadout+encounter statistics and Pareto-best victories.
     */
    public static void openStatsScreen() {
        if (statsScreen != null) {
            statsScreen.open();
        }
    }

    /**
     * Set flag to return to arena selection when main menu is reached.
     * Called when an arena fight ends.
     */
    public static void setReturnToArenaOnMainMenu() {
        returnToArenaOnMainMenu = true;
        logger.info("ARENA: Will return to arena selection on main menu");
    }

    /**
     * Clear the flag to return to arena selection.
     * Called when exiting arena screens via Back button or arena_back command.
     */
    public static void clearReturnToArenaOnMainMenu() {
        returnToArenaOnMainMenu = false;
        logger.info("ARENA: Cleared return-to-arena flag");
    }

    /**
     * Set a loadout to open in the editor when main menu is reached.
     * Used for deck tweaks between retry attempts.
     */
    public static void setOpenLoadoutEditorOnMainMenu(ArenaRepository.LoadoutRecord loadout) {
        pendingLoadoutEditorRecord = loadout;
        logger.info("ARENA: Will open loadout editor on main menu for: " + loadout.name);
    }

    /**
     * Check if any arena screen is currently open.
     * Used to block main menu button hover effects.
     */
    public static boolean isAnyScreenOpen() {
        return (historyScreen != null && historyScreen.isOpen) ||
               (encounterSelectScreen != null && encounterSelectScreen.isOpen) ||
               (loadoutSelectScreen != null && loadoutSelectScreen.isOpen) ||
               (loadoutCreatorScreen != null && loadoutCreatorScreen.isOpen) ||
               (statsScreen != null && statsScreen.isOpen);
    }
}
