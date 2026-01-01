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
        // Add our buttons at the end of the list
        int nextIndex = __instance.buttons.size();
        __instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_MODE, nextIndex));
        __instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_HISTORY, nextIndex + 1));
    }
}
