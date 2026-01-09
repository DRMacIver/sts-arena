package stsarena.communication;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * CommunicationMod command extension to exit arena screens.
 *
 * Usage: arena_back
 *
 * This command closes any open arena screens (encounter select, loadout select)
 * and cleans up the arena state, including restoring any backed-up save files.
 *
 * Use this command to simulate pressing the "Back" button from arena screens,
 * which is needed to properly clean up arena state when returning to the main menu.
 */
public class ArenaBackCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the arena_back command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaBackCommand());
            STSArena.logger.info("Registered arena_back command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena_back command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena_back";
    }

    @Override
    public boolean isAvailable() {
        // Command is available:
        // 1. When not in a dungeon (at main menu area), or
        // 2. When in an arena run (to exit VictoryScreen/DeathScreen back to menu)
        boolean inDungeon = CommandExecutor.isInDungeon();
        boolean isArena = ArenaRunner.isArenaRun();
        boolean result = !inDungeon || isArena;
        STSArena.logger.info("arena_back isAvailable check: inDungeon=" + inDungeon + ", isArenaRun=" + isArena + " => " + result);
        return result;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        STSArena.logger.info("arena_back command: closing arena screens and cleaning up state");

        // If we're in a dungeon (VictoryScreen, DeathScreen, etc.), trigger return to menu
        if (CommandExecutor.isInDungeon()) {
            STSArena.logger.info("arena_back: in dungeon, triggering startOver to return to menu");

            // Clean up arena state
            ArenaRunner.clearArenaRun();

            // Clear trial/daily/endless flags
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;

            // Clear the return-to-arena flag so screens don't reopen
            STSArena.clearReturnToArenaOnMainMenu();

            // Trigger return to main menu
            // Note: startOver() is async - the game will transition on next update.
            // We signal ready immediately so the caller knows the command was processed,
            // but they should still wait for in_game=false before proceeding.
            CardCrawlGame.startOver();

            // Signal that the command was processed (caller should wait for main menu separately)
            GameStateListener.signalReadyForCommand();

            STSArena.logger.info("arena_back command triggered startOver (async transition)");
            return;
        }

        // At main menu - close any open arena screens
        if (STSArena.encounterSelectScreen != null && STSArena.encounterSelectScreen.isOpen) {
            STSArena.logger.info("arena_back: closing encounter select screen");
            STSArena.encounterSelectScreen.close();
        }

        if (STSArena.loadoutSelectScreen != null && STSArena.loadoutSelectScreen.isOpen) {
            STSArena.logger.info("arena_back: closing loadout select screen");
            STSArena.loadoutSelectScreen.close();
        }

        // Clean up arena state and restore save files
        STSArena.logger.info("arena_back: calling clearArenaRun()");
        ArenaRunner.clearArenaRun();

        // Clear the return-to-arena flag so screens don't reopen
        STSArena.clearReturnToArenaOnMainMenu();

        // Force a state update to be sent back to the caller
        GameStateListener.signalReadyForCommand();
        CommunicationMod.mustSendGameState = true;

        STSArena.logger.info("arena_back command complete");
    }
}
