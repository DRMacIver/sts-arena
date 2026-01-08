package stsarena.communication;

import com.megacrit.cardcrawl.core.GameCursor;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;

/**
 * CommunicationMod command extension to hide/show the cursor.
 *
 * Usage: cursor_hide [true|false]
 *
 * This command hides or shows the game cursor. Useful for taking clean screenshots.
 * If no argument is provided, defaults to hiding the cursor (true).
 */
public class CursorHideCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the cursor_hide command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new CursorHideCommand());
            STSArena.logger.info("Registered cursor_hide command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, cursor_hide command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "cursor_hide";
    }

    @Override
    public boolean isAvailable() {
        // Always available
        return true;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        boolean hide = true;  // Default to hiding

        if (tokens.length > 1) {
            String arg = tokens[1].toLowerCase();
            if (arg.equals("false") || arg.equals("0") || arg.equals("no")) {
                hide = false;
            }
        }

        GameCursor.hidden = hide;
        STSArena.logger.info("cursor_hide command: cursor hidden = " + hide);

        // Signal ready for next command
        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
    }
}
