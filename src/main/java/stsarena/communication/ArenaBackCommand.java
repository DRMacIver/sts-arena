package stsarena.communication;

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
        // Command is available when not in a dungeon (at main menu area)
        // and either arena screens are open or there's arena state to clean up
        return !CommandExecutor.isInDungeon();
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        STSArena.logger.info("arena_back command: closing arena screens and cleaning up state");

        // Close encounter select screen if open
        if (STSArena.encounterSelectScreen != null && STSArena.encounterSelectScreen.isOpen) {
            STSArena.logger.info("arena_back: closing encounter select screen");
            STSArena.encounterSelectScreen.close();
        }

        // Close loadout select screen if open
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
        // Without this, no response would be sent since we're already at main menu
        // and closing arena screens doesn't trigger a detectable state change
        GameStateListener.signalReadyForCommand();
        CommunicationMod.mustSendGameState = true;

        STSArena.logger.info("arena_back command complete");
    }
}
