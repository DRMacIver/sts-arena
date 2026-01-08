package stsarena.communication;

import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

/**
 * CommunicationMod command extension for opening arena screens.
 *
 * Usage:
 *   arena_screen loadout        - Open loadout selection screen
 *   arena_screen loadout <id>   - Open loadout screen with a loadout selected (shows preview)
 *   arena_screen encounter      - Open encounter selection screen
 *   arena_screen creator        - Open loadout creator screen
 *   arena_screen history        - Open fight history screen
 *   arena_screen stats          - Open statistics screen
 *   arena_screen close          - Close any open arena screen
 *
 * This command is primarily used for documentation screenshot generation
 * and testing purposes.
 */
public class ArenaScreenCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the arena_screen command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaScreenCommand());
            STSArena.logger.info("Registered arena_screen command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena_screen command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena_screen";
    }

    @Override
    public boolean isAvailable() {
        // Screen navigation is available when not in a dungeon (at main menu)
        return !CommandExecutor.isInDungeon();
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(
                "Usage: arena_screen <loadout|encounter|creator|history|stats|close>\n" +
                "  loadout   - Open loadout selection screen\n" +
                "  encounter - Open encounter selection screen\n" +
                "  creator   - Open loadout creator screen\n" +
                "  history   - Open fight history screen\n" +
                "  stats     - Open statistics screen\n" +
                "  close     - Close any open arena screen");
        }

        String screenName = tokens[1].toLowerCase();

        switch (screenName) {
            case "loadout":
                closeAllArenaScreens();
                STSArena.openLoadoutSelectScreen();
                // If a loadout ID is provided, select it to show the preview
                if (tokens.length >= 3) {
                    try {
                        long loadoutId = Long.parseLong(tokens[2]);
                        selectLoadoutById(loadoutId);
                    } catch (NumberFormatException e) {
                        throw new InvalidCommandException("Invalid loadout ID: " + tokens[2]);
                    }
                }
                STSArena.logger.info("ARENA_SCREEN: Opened loadout selection screen");
                break;

            case "encounter":
                closeAllArenaScreens();
                STSArena.openEncounterSelectScreen();
                STSArena.logger.info("ARENA_SCREEN: Opened encounter selection screen");
                break;

            case "creator":
                closeAllArenaScreens();
                STSArena.openLoadoutCreatorScreen();
                STSArena.logger.info("ARENA_SCREEN: Opened loadout creator screen");
                break;

            case "history":
                closeAllArenaScreens();
                if (STSArena.historyScreen != null) {
                    STSArena.historyScreen.open();
                    STSArena.logger.info("ARENA_SCREEN: Opened history screen");
                }
                break;

            case "stats":
                closeAllArenaScreens();
                STSArena.openStatsScreen();
                STSArena.logger.info("ARENA_SCREEN: Opened stats screen");
                break;

            case "close":
                closeAllArenaScreens();
                STSArena.logger.info("ARENA_SCREEN: Closed all arena screens");
                break;

            default:
                throw new InvalidCommandException("Unknown screen: " + screenName +
                    ". Valid screens: loadout, encounter, creator, history, stats, close");
        }

        // Signal ready for next command and trigger a state response
        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
    }

    /**
     * Close all open arena screens.
     */
    private void closeAllArenaScreens() {
        if (STSArena.loadoutSelectScreen != null && STSArena.loadoutSelectScreen.isOpen) {
            STSArena.loadoutSelectScreen.close();
        }
        if (STSArena.encounterSelectScreen != null && STSArena.encounterSelectScreen.isOpen) {
            STSArena.encounterSelectScreen.close();
        }
        if (STSArena.loadoutCreatorScreen != null && STSArena.loadoutCreatorScreen.isOpen) {
            STSArena.loadoutCreatorScreen.close();
        }
        if (STSArena.historyScreen != null && STSArena.historyScreen.isOpen) {
            STSArena.historyScreen.close();
        }
        if (STSArena.statsScreen != null && STSArena.statsScreen.isOpen) {
            STSArena.statsScreen.close();
        }
    }

    /**
     * Select a loadout by ID in the loadout selection screen.
     * This makes the preview panel show the loadout's contents.
     */
    private void selectLoadoutById(long loadoutId) throws InvalidCommandException {
        ArenaDatabase db = ArenaDatabase.getInstance();
        if (db == null) {
            throw new InvalidCommandException("Database not available");
        }

        ArenaRepository repo = new ArenaRepository(db);
        ArenaRepository.LoadoutRecord loadout = repo.getLoadoutById(loadoutId);
        if (loadout == null) {
            throw new InvalidCommandException("Loadout not found with ID: " + loadoutId);
        }

        // Set the static selectedSavedLoadout so the screen shows the preview
        if (STSArena.loadoutSelectScreen != null) {
            STSArena.loadoutSelectScreen.selectLoadoutForPreview(loadout);
            STSArena.logger.info("ARENA_SCREEN: Selected loadout " + loadoutId + " for preview");
        }
    }
}
