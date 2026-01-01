package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpireEnum;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

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
     * Custom ClickResult enum value for our Arena History button.
     */
    @SpireEnum
    public static MenuButton.ClickResult ARENA_HISTORY;

    /**
     * Patch to set the label text for our custom buttons.
     */
    @SpirePatch(
        clz = MenuButton.class,
        method = "setLabel"
    )
    public static class SetLabel {
        public static void Postfix(MenuButton __instance) {
            try {
                Field labelField = MenuButton.class.getDeclaredField("label");
                labelField.setAccessible(true);

                if (__instance.result == ARENA_MODE) {
                    labelField.set(__instance, "Arena Mode");
                } else if (__instance.result == ARENA_HISTORY) {
                    labelField.set(__instance, "Arena History");
                }
            } catch (Exception e) {
                STSArena.logger.error("Failed to set Arena button label", e);
            }
        }
    }

    /**
     * Patch to handle clicks on our custom buttons.
     */
    @SpirePatch(
        clz = MenuButton.class,
        method = "buttonEffect"
    )
    public static class ButtonEffect {
        public static void Postfix(MenuButton __instance) {
            if (__instance.result == ARENA_MODE) {
                STSArena.logger.info("Arena Mode button clicked!");
                ArenaRunner.startRandomFight();
            } else if (__instance.result == ARENA_HISTORY) {
                STSArena.logger.info("Arena History button clicked!");
                STSArena.openHistoryScreen();
            }
        }
    }
}
