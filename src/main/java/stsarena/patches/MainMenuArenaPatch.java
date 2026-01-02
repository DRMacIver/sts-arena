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
        // Find the position of Play or Resume Game button (whichever is the topmost game button)
        // We want to insert Arena Mode just below it
        int insertIndex = -1;
        for (int i = 0; i < __instance.buttons.size(); i++) {
            MenuButton button = __instance.buttons.get(i);
            if (button.result == MenuButton.ClickResult.PLAY ||
                button.result == MenuButton.ClickResult.RESUME_GAME) {
                insertIndex = i;  // Insert at this position (pushing Play/Resume up)
                break;
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
            // Fallback: add at the end
            int nextIndex = __instance.buttons.size();
            __instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_MODE, nextIndex));
        }
    }
}
