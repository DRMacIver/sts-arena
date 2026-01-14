package stsarena.communication;

import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.screens.ArenaResultsScreen;

/**
 * CommunicationMod command to click buttons on the arena results screen.
 *
 * Usage:
 *   results_button continue   - Click Continue/Return to Menu button
 *   results_button rematch    - Click Rematch/Retry button
 *   results_button modify     - Click Modify Deck button
 *
 * Available when the arena results screen is open.
 */
public class ResultsButtonCommand implements CommandExecutor.CommandExtension {

    public static void register() {
        try {
            CommandExecutor.registerCommand(new ResultsButtonCommand());
            STSArena.logger.info("Registered results_button command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, results_button command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "results_button";
    }

    @Override
    public boolean isAvailable() {
        ArenaResultsScreen screen = STSArena.getResultsScreen();
        return screen != null && screen.isOpen;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        ArenaResultsScreen screen = STSArena.getResultsScreen();
        if (screen == null || !screen.isOpen) {
            throw new InvalidCommandException("Results screen is not open");
        }

        String button = tokens.length > 1 ? tokens[1].toLowerCase() : "continue";

        STSArena.logger.info("results_button: Clicking " + button + " button");

        switch (button) {
            case "continue":
            case "return":
            case "retreat":
                screen.clickContinue();
                break;
            case "rematch":
            case "retry":
                screen.clickRetry();
                break;
            case "modify":
            case "edit":
                screen.clickModifyDeck();
                break;
            default:
                throw new InvalidCommandException("Unknown button: " + button +
                    ". Valid options: continue, rematch, modify");
        }

        GameStateListener.registerStateChange();
    }
}
