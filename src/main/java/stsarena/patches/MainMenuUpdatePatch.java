package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import stsarena.STSArena;

/**
 * Patch to re-add the Arena Mode button after the game processes abandon run.
 *
 * When a run is abandoned, the game's update() method removes the last 2 buttons
 * and adds a PLAY button. Since our ARENA_MODE button is at the end, it gets
 * removed incorrectly. This patch detects when that happens and re-adds our button.
 */
@SpirePatch(
    clz = MainMenuScreen.class,
    method = "update"
)
public class MainMenuUpdatePatch {

    // Track whether we've already added our button after abandon
    private static boolean needsArenaButton = false;

    public static void Prefix(MainMenuScreen __instance) {
        // Check if the game is about to process abandonedRun
        // If so, we'll need to re-add our button after
        if (__instance.abandonedRun) {
            needsArenaButton = true;
        }
    }

    public static void Postfix(MainMenuScreen __instance) {
        if (needsArenaButton) {
            needsArenaButton = false;

            // Check if our button is missing from the menu
            boolean hasArenaButton = false;
            for (MenuButton button : __instance.buttons) {
                if (button.result == ArenaMenuButton.ARENA_MODE) {
                    hasArenaButton = true;
                    break;
                }
            }

            if (!hasArenaButton) {
                int nextIndex = __instance.buttons.size();
                __instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_MODE, nextIndex));
                STSArena.logger.info("Re-added Arena Mode button after abandon run");
            }
        }
    }
}
