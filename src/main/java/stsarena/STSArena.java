package stsarena;

import basemod.BaseMod;
import basemod.interfaces.PostDungeonInitializeSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsarena.arena.ArenaRunner;
import stsarena.data.ArenaDatabase;
import stsarena.screens.ArenaHistoryScreen;

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

    // Arena history screen
    public static ArenaHistoryScreen historyScreen;

    // Flag to trigger fight start on next update (gives game time to initialize)
    private static boolean pendingFightStart = false;

    public STSArena() {
        logger.info("Initializing STS Arena");
        BaseMod.subscribe(this);
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

        // Initialize the database
        ArenaDatabase.getInstance();

        // Initialize the history screen
        historyScreen = new ArenaHistoryScreen();

        // Arena Mode button is added via patches/MainMenuArenaPatch
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
        // Update history screen BEFORE main menu so we get input first
        if (historyScreen != null && historyScreen.isOpen) {
            historyScreen.update();
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
    }

    /**
     * Called after rendering. Used to render our custom screens.
     */
    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (historyScreen != null && historyScreen.isOpen) {
            historyScreen.render(sb);
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
}
