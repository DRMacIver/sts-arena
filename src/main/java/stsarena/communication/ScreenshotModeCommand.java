package stsarena.communication;

import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;

/**
 * CommunicationMod command to enable/disable screenshot mode.
 * When screenshot mode is enabled, automatic screen transitions are prevented,
 * allowing screenshots of victory/defeat screens to be captured.
 *
 * Usage:
 *   screenshot_mode on   - Enable screenshot mode
 *   screenshot_mode off  - Disable screenshot mode
 */
public class ScreenshotModeCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the screenshot_mode command with CommunicationMod.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ScreenshotModeCommand());
            STSArena.logger.info("Registered screenshot_mode command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, screenshot_mode command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "screenshot_mode";
    }

    @Override
    public boolean isAvailable() {
        return true;  // Always available
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException("Usage: screenshot_mode <on|off>");
        }

        String mode = tokens[1].toLowerCase();
        switch (mode) {
            case "on":
            case "true":
            case "1":
                STSArena.setScreenshotMode(true);
                break;
            case "off":
            case "false":
            case "0":
                STSArena.setScreenshotMode(false);
                break;
            default:
                throw new InvalidCommandException("Invalid mode: " + mode + ". Use 'on' or 'off'");
        }

        // Signal ready for next command
        GameStateListener.signalReadyForCommand();
        CommunicationMod.mustSendGameState = true;
    }
}
