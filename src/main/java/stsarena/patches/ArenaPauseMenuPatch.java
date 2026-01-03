package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.screens.options.AbandonRunButton;
import com.megacrit.cardcrawl.screens.options.ConfirmPopup;
import com.megacrit.cardcrawl.screens.options.ExitGameButton;
import com.megacrit.cardcrawl.screens.options.SettingsScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patches to modify pause menu behavior during arena mode:
 * - Change "Save and Quit" to "Leave Arena"
 * - Hide "Abandon Run" button
 * - Clean up properly when leaving (restore original save)
 */
public class ArenaPauseMenuPatch {

    /**
     * Change the exit button label to "Leave Arena" during arena mode.
     * We intercept updateLabel and replace the incoming label.
     */
    @SpirePatch(
        clz = ExitGameButton.class,
        method = "updateLabel"
    )
    public static class ChangeExitButtonLabel {
        public static void Prefix(ExitGameButton __instance, @com.evacipated.cardcrawl.modthespire.lib.ByRef String[] newLabel) {
            if (ArenaRunner.isArenaRun()) {
                newLabel[0] = "Leave Arena";
            }
        }
    }

    /**
     * Change the popup description for arena mode.
     */
    @SpirePatch(
        clz = SettingsScreen.class,
        method = "popup"
    )
    public static class ChangePopupDescription {
        public static void Postfix(SettingsScreen __instance, ConfirmPopup.ConfirmType type) {
            if (type == ConfirmPopup.ConfirmType.EXIT && ArenaRunner.isArenaRun()) {
                // Change description based on whether we came from a normal run
                if (ArenaRunner.wasStartedFromNormalRun()) {
                    __instance.exitPopup.desc = "Return to your game?";
                } else {
                    __instance.exitPopup.desc = "Return to the main menu?";
                }
            }
        }
    }

    /**
     * Hide the Abandon Run button during arena mode - don't update it.
     */
    @SpirePatch(
        clz = AbandonRunButton.class,
        method = "update"
    )
    public static class HideAbandonButtonUpdate {
        public static SpireReturn<Void> Prefix(AbandonRunButton __instance) {
            if (ArenaRunner.isArenaRun()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Hide the Abandon Run button during arena mode - don't render it.
     */
    @SpirePatch(
        clz = AbandonRunButton.class,
        method = "render"
    )
    public static class HideAbandonButtonRender {
        public static SpireReturn<Void> Prefix(AbandonRunButton __instance) {
            if (ArenaRunner.isArenaRun()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * When exiting during arena mode, clean up properly instead of saving.
     * If we came from a normal run (Practice in Arena), resume that run.
     */
    @SpirePatch(
        clz = ConfirmPopup.class,
        method = "yesButtonEffect"
    )
    public static class HandleArenaExit {
        public static SpireReturn<Void> Prefix(ConfirmPopup __instance) {
            if (__instance.type == ConfirmPopup.ConfirmType.EXIT && ArenaRunner.isArenaRun()) {
                STSArena.logger.info("Leaving arena mode via pause menu");

                // Check if we should return to a normal run
                boolean resumeNormalRun = ArenaRunner.wasStartedFromNormalRun();

                // Stop sounds
                CardCrawlGame.music.fadeAll();
                __instance.hide();
                AbstractDungeon.getCurrRoom().clearEvent();
                AbstractDungeon.closeCurrentScreen();

                if (RestRoom.lastFireSoundId != 0L) {
                    CardCrawlGame.sound.fadeOut("REST_FIRE_WET", RestRoom.lastFireSoundId);
                }
                if (AbstractDungeon.player.stance != null &&
                    !AbstractDungeon.player.stance.ID.equals("Neutral")) {
                    AbstractDungeon.player.stance.stopIdleSfx();
                }

                if (resumeNormalRun) {
                    // Resume the normal run we came from
                    STSArena.logger.info("Resuming normal run after arena practice");
                    ArenaRunner.resumeNormalRun();
                } else {
                    // Clean up arena run and go to main menu
                    ArenaRunner.clearArenaRun();
                    CardCrawlGame.startOver();
                }

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
