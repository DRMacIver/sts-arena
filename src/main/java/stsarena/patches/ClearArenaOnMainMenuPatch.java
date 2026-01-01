package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import stsarena.arena.ArenaRunner;

/**
 * Clears the arena run flag when returning to main menu.
 */
public class ClearArenaOnMainMenuPatch {

    @SpirePatch(
        clz = MainMenuScreen.class,
        method = SpirePatch.CONSTRUCTOR,
        paramtypez = {boolean.class}
    )
    public static class ClearOnMainMenu {
        public static void Postfix(MainMenuScreen __instance, boolean playBgm) {
            ArenaRunner.clearArenaRun();
        }
    }
}
