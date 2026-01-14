package stsarena.communication;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * CommunicationMod command to leave arena mode.
 *
 * If the arena was started from a normal run (via "Practice in Arena"),
 * this resumes the normal run. Otherwise, it returns to the main menu.
 *
 * Usage:
 *   leave_arena    - Leave arena (resume normal run or go to menu)
 *
 * Available when in an arena run.
 */
public class LeaveArenaCommand implements CommandExecutor.CommandExtension {

    public static void register() {
        try {
            CommandExecutor.registerCommand(new LeaveArenaCommand());
            STSArena.logger.info("Registered leave_arena command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, leave_arena command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "leave_arena";
    }

    @Override
    public boolean isAvailable() {
        return ArenaRunner.isArenaRun();
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        STSArena.logger.info("leave_arena: Leaving arena mode (startedFromNormalRun=" +
            ArenaRunner.wasStartedFromNormalRun() + ")");

        if (ArenaRunner.wasStartedFromNormalRun()) {
            // Resume the normal run
            ArenaRunner.resumeNormalRun();
        } else {
            // Return to main menu
            ArenaRunner.clearArenaRun();
            CardCrawlGame.startOver();
        }

        GameStateListener.registerStateChange();
    }
}
