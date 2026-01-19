package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Clears the arena run flag when returning to main menu.
 *
 * IMPORTANT: This uses a Prefix patch to ensure arena state is cleaned up
 * BEFORE the MainMenuScreen constructor runs. This is critical because the
 * constructor checks if save files exist to decide which buttons to show
 * (Continue vs Play). If we used a Postfix, the save file would still exist
 * when the buttons are created, causing a spurious Continue button.
 *
 * Only clears if we're not in the middle of starting an arena fight.
 * During arena startup, the MainMenuScreen constructor may be called
 * as part of the transition, and we don't want to reset state then.
 */
public class ClearArenaOnMainMenuPatch {

    @SpirePatch(
        cls = "com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen",
        method = SpirePatch.CONSTRUCTOR,
        paramtypez = {boolean.class}
    )
    public static class ClearOnMainMenu {
        // Use Prefix (not Postfix) so cleanup happens BEFORE constructor runs.
        // The constructor calls setMainMenuButtons() which checks anySaveFileExists().
        public static void Prefix(boolean playBgm) {
            STSArena.logger.info("ARENA: MainMenuScreen being created - isArenaRunInProgress=" +
                ArenaRunner.isArenaRunInProgress() + ", isArenaRun=" + ArenaRunner.isArenaRun() +
                ", isResumingNormalRun=" + ArenaRunner.isResumingNormalRun());

            // Only clear if we're not in the middle of setting up an arena run
            // ArenaRunner.isArenaRunInProgress() is true during the setup phase
            if (ArenaRunner.isArenaRunInProgress()) {
                STSArena.logger.info("ARENA: MainMenuScreen created during arena setup - NOT clearing state");
                return;
            }

            // Also skip if we're resuming a normal run (leave_arena from Practice in Arena)
            // The MainMenuScreen may be created as part of the transition
            if (ArenaRunner.isResumingNormalRun()) {
                STSArena.logger.info("ARENA: MainMenuScreen created during normal run resume - NOT clearing state");
                return;
            }

            STSArena.logger.info("ARENA: MainMenuScreen being created - clearing arena run state");
            ArenaRunner.clearArenaRun();

            // Also close the results screen if it's open
            STSArena.closeResultsScreen();
        }
    }
}
