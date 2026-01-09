package stsarena.communication;

import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.patches.NormalRunLoadoutSaver;

/**
 * CommunicationMod command to set arena retry data for testing.
 * This enables the "Try Again in Arena Mode" button on the death screen.
 *
 * Usage:
 *   set_retry_data <loadout_id> <encounter_id>
 */
public class SetRetryDataCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the set_retry_data command with CommunicationMod.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new SetRetryDataCommand());
            STSArena.logger.info("Registered set_retry_data command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, set_retry_data command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "set_retry_data";
    }

    @Override
    public boolean isAvailable() {
        return true;  // Always available for testing
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException("Usage: set_retry_data <loadout_id> <encounter_id>");
        }

        long loadoutId;
        try {
            loadoutId = Long.parseLong(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid loadout ID: " + tokens[1]);
        }

        String encounterId = tokens[2];

        NormalRunLoadoutSaver.setRetryData(loadoutId, encounterId);

        // Signal ready for next command
        GameStateListener.signalReadyForCommand();
        CommunicationMod.mustSendGameState = true;
    }
}
