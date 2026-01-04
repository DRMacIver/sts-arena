package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import stsarena.STSArena;

/**
 * Patch to ensure the Arena Mode button is always present on the main menu.
 *
 * When a run is abandoned, the game's update() method removes the last 2 buttons
 * and adds a PLAY button. Since our ARENA_MODE button is at the end, it gets
 * removed incorrectly. This patch checks every frame and re-adds our button if missing.
 */
@SpirePatch(
    cls = "com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen",
    method = "update"
)
public class MainMenuUpdatePatch {

    public static void Postfix(MainMenuScreen __instance) {
        // Check button state
        boolean hasArenaButton = false;
        boolean hasPlayButton = false;
        boolean hasAbandonButton = false;
        boolean needsRebuild = false;

        for (MenuButton button : __instance.buttons) {
            if (button.result == ArenaMenuButton.ARENA_MODE) {
                hasArenaButton = true;
            } else if (button.result == MenuButton.ClickResult.PLAY) {
                hasPlayButton = true;
            } else if (button.result == MenuButton.ClickResult.ABANDON_RUN) {
                hasAbandonButton = true;
            }
        }

        // Detect broken state: PLAY and ABANDON_RUN together, or missing Arena button
        if ((hasPlayButton && hasAbandonButton) || !hasArenaButton) {
            needsRebuild = true;
        }

        if (needsRebuild) {
            // Rebuild the button list with correct buttons and indices
            java.util.ArrayList<MenuButton.ClickResult> keepResults = new java.util.ArrayList<>();

            for (MenuButton button : __instance.buttons) {
                MenuButton.ClickResult result = button.result;
                // Skip ABANDON_RUN if we have PLAY (broken state)
                if (result == MenuButton.ClickResult.ABANDON_RUN && hasPlayButton) {
                    continue;
                }
                // Skip our button, we'll insert it in the right place
                if (result == ArenaMenuButton.ARENA_MODE) {
                    continue;
                }
                keepResults.add(result);
            }

            // Find where to insert Arena Mode (just before PLAY or RESUME_GAME)
            int insertIndex = keepResults.size(); // default to end
            for (int i = 0; i < keepResults.size(); i++) {
                MenuButton.ClickResult result = keepResults.get(i);
                if (result == MenuButton.ClickResult.PLAY ||
                    result == MenuButton.ClickResult.RESUME_GAME) {
                    insertIndex = i;
                    break;
                }
            }
            keepResults.add(insertIndex, ArenaMenuButton.ARENA_MODE);

            // Rebuild button list with correct indices
            __instance.buttons.clear();
            for (int i = 0; i < keepResults.size(); i++) {
                __instance.buttons.add(new MenuButton(keepResults.get(i), i));
            }

            STSArena.logger.info("Rebuilt menu buttons, count: " + __instance.buttons.size());
        }
    }
}
