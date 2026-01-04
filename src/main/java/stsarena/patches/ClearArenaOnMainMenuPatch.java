package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Clears the arena run flag when returning to main menu.
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
        public static void Postfix(MainMenuScreen __instance, boolean playBgm) {
            // Only clear if we're not in the middle of setting up an arena run
            // ArenaRunner.isArenaRunInProgress() is true during the setup phase
            if (ArenaRunner.isArenaRunInProgress()) {
                STSArena.logger.info("ARENA: MainMenuScreen created during arena setup - NOT clearing state");
                return;
            }

            STSArena.logger.info("ARENA: MainMenuScreen created - clearing arena run state");
            ArenaRunner.clearArenaRun();
        }
    }
}
