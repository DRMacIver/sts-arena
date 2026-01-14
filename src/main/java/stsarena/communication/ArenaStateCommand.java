package stsarena.communication;

import com.google.gson.JsonObject;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.SaveFileManager;
import stsarena.screens.ArenaResultsScreen;

/**
 * CommunicationMod command to query arena state.
 *
 * Returns a JSON object with arena state flags for test assertions.
 *
 * Usage:
 *   arena_state    - Returns JSON with arena state
 *
 * Response (in message field):
 *   {
 *     "is_arena_run": true/false,
 *     "arena_run_in_progress": true/false,
 *     "started_from_normal_run": true/false,
 *     "has_marker_file": true/false,
 *     "results_screen_open": true/false,
 *     "current_encounter": "encounter name or null",
 *     "current_loadout_id": number or -1
 *   }
 *
 * Always available.
 */
public class ArenaStateCommand implements CommandExecutor.CommandExtension {

    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaStateCommand());
            STSArena.logger.info("Registered arena_state command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena_state command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena_state";
    }

    @Override
    public boolean isAvailable() {
        // Always available for debugging/testing
        return true;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        JsonObject state = new JsonObject();

        // Core arena flags
        state.addProperty("is_arena_run", ArenaRunner.isArenaRun());
        state.addProperty("arena_run_in_progress", ArenaRunner.isArenaRunInProgress());
        state.addProperty("started_from_normal_run", ArenaRunner.wasStartedFromNormalRun());

        // File-based state
        state.addProperty("has_marker_file", SaveFileManager.hasActiveArenaSession());

        // Results screen state
        ArenaResultsScreen resultsScreen = STSArena.getResultsScreen();
        state.addProperty("results_screen_open", resultsScreen != null && resultsScreen.isOpen);

        // Current run info
        String encounter = ArenaRunner.getCurrentEncounter();
        if (encounter != null) {
            state.addProperty("current_encounter", encounter);
        } else {
            state.add("current_encounter", null);
        }

        state.addProperty("current_loadout_id", ArenaRunner.getCurrentLoadoutDbId());

        // Store in a place the test can access
        // The message will be available in the game state response
        STSArena.logger.info("arena_state: " + state.toString());

        // Signal that we have a message to send
        GameStateListener.registerStateChange();
    }
}
