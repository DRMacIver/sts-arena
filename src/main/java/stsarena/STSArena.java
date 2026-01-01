package stsarena;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stsarena.data.ArenaDatabase;

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
public class STSArena implements PostInitializeSubscriber {

    public static final Logger logger = LogManager.getLogger(STSArena.class.getName());
    public static final String MOD_ID = "stsarena";

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

        // Arena Mode button is added via patches/MainMenuArenaPatch
    }
}
