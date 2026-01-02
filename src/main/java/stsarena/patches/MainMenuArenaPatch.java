package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;

/**
 * Patch to add the Arena Mode button to the main menu.
 *
 * Adds the button at the end of the menu list.
 */
@SpirePatch(
    clz = MainMenuScreen.class,
    method = "setMainMenuButtons"
)
public class MainMenuArenaPatch {

    public static void Postfix(MainMenuScreen __instance) {
        // Find the position of Abandon Run button (if it exists) or Play button
        // Menu order (higher index = higher on screen):
        //   RESUME_GAME (top)
        //   ABANDON_RUN
        //   ARENA_MODE  <- we want to insert here (below Abandon)
        //   INFO, STAT, SETTINGS, etc.
        //
        // If no save exists, order is:
        //   PLAY (top)
        //   ARENA_MODE  <- insert here
        //   INFO, STAT, etc.

        int insertIndex = -1;

        // First, look for ABANDON_RUN - we want to insert just below it
        for (int i = 0; i < __instance.buttons.size(); i++) {
            MenuButton button = __instance.buttons.get(i);
            if (button.result == MenuButton.ClickResult.ABANDON_RUN) {
                insertIndex = i;  // Insert at Abandon's position (pushes Abandon up)
                break;
            }
        }

        // If no Abandon button, look for PLAY button
        if (insertIndex < 0) {
            for (int i = 0; i < __instance.buttons.size(); i++) {
                MenuButton button = __instance.buttons.get(i);
                if (button.result == MenuButton.ClickResult.PLAY) {
                    insertIndex = i;  // Insert at Play's position (pushes Play up)
                    break;
                }
            }
        }

        if (insertIndex >= 0) {
            // Collect all button results, insert Arena Mode, then rebuild with correct indices
            java.util.ArrayList<MenuButton.ClickResult> results = new java.util.ArrayList<>();
            for (MenuButton button : __instance.buttons) {
                results.add(button.result);
            }
            results.add(insertIndex, ArenaMenuButton.ARENA_MODE);

            // Rebuild button list with correct indices
            __instance.buttons.clear();
            for (int i = 0; i < results.size(); i++) {
                __instance.buttons.add(new MenuButton(results.get(i), i));
            }
        } else {
            // Fallback: add at the end (top of menu)
            int nextIndex = __instance.buttons.size();
            __instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_MODE, nextIndex));
        }
    }
}
