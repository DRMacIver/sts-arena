package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.ByRef;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import javassist.CannotCompileException;
import javassist.CtBehavior;

/**
 * Patch to add the Arena Mode button to the main menu.
 *
 * This inserts our button before the Patch Notes button in the menu.
 */
@SpirePatch(
    clz = MainMenuScreen.class,
    method = "setMainMenuButtons"
)
public class MainMenuArenaPatch {

    @SpireInsertPatch(
        locator = Locator.class,
        localvars = {"index"}
    )
    public static void Insert(Object __obj_instance, @ByRef int[] index) {
        MainMenuScreen instance = (MainMenuScreen) __obj_instance;
        instance.buttons.add(new MenuButton(ArenaMenuButton.ARENA_MODE, index[0]++));
    }

    /**
     * Locator to find where to insert our button.
     * We insert just before the PATCH_NOTES button is added.
     */
    private static class Locator extends SpireInsertLocator {
        @Override
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher finalMatcher = new Matcher.FieldAccessMatcher(
                MenuButton.ClickResult.class,
                "PATCH_NOTES"
            );
            return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
        }
    }
}
