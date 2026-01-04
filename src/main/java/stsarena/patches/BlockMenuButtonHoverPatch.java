package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.screens.mainMenu.MenuButton;
import stsarena.STSArena;

/**
 * Prevents main menu buttons from updating their hover state when arena screens are open.
 * This stops the buttons from highlighting when the mouse hovers over them behind our screens.
 */
@SpirePatch(
    cls = "com.megacrit.cardcrawl.screens.mainMenu.MenuButton",
    method = "update"
)
public class BlockMenuButtonHoverPatch {

    public static SpireReturn<Void> Prefix(MenuButton __instance) {
        // If any of our screens is open, skip the button update entirely
        if (STSArena.isAnyScreenOpen()) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }
}
