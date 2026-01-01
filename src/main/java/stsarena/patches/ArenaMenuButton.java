package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpireEnum;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import stsarena.STSArena;

import java.lang.reflect.Field;

/**
 * Defines the Arena Mode menu button and its behavior.
 */
public class ArenaMenuButton {

    /**
     * Custom ClickResult enum value for our Arena Mode button.
     */
    @SpireEnum
    public static MenuButton.ClickResult ARENA_MODE;

    /**
     * Patch to set the label text for our custom button.
     */
    @SpirePatch(
        clz = MenuButton.class,
        method = "setLabel"
    )
    public static class SetLabel {
        public static void Postfix(MenuButton __instance) {
            try {
                if (__instance.result == ARENA_MODE) {
                    Field labelField = MenuButton.class.getDeclaredField("label");
                    labelField.setAccessible(true);
                    labelField.set(__instance, "Arena Mode");
                }
            } catch (Exception e) {
                STSArena.logger.error("Failed to set Arena Mode button label", e);
            }
        }
    }

    /**
     * Patch to handle clicks on our custom button.
     */
    @SpirePatch(
        clz = MenuButton.class,
        method = "buttonEffect"
    )
    public static class ButtonEffect {
        public static void Postfix(MenuButton __instance) {
            if (__instance.result == ARENA_MODE) {
                STSArena.logger.info("Arena Mode button clicked!");
                // TODO: Open arena mode screen
            }
        }
    }
}
