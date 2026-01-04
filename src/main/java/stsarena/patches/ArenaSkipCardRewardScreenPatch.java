package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

import java.util.ArrayList;

/**
 * Skip the card reward screen entirely in arena mode.
 * This prevents card selection screens from appearing after arena victories.
 */
public class ArenaSkipCardRewardScreenPatch {

    /**
     * Intercept the card reward screen opening and skip it in arena mode.
     * CardRewardScreen.open(ArrayList, RewardItem, String)
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.CardRewardScreen", method = "open")
    public static class SkipCardRewardScreenOpen {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(CardRewardScreen __instance, ArrayList cards,
                                               com.megacrit.cardcrawl.rewards.RewardItem relic,
                                               String header) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: Skipping CardRewardScreen.open() - should not appear in arena");

                // Clear the cards to prevent any processing
                __instance.rewardGroup.clear();
                if (cards != null) {
                    cards.clear();
                }

                // Force return to main menu if not already returning
                triggerReturnToMainMenu();

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Intercept opening from draft rewards.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.CardRewardScreen", method = "draftOpen")
    public static class SkipCardRewardDraftOpen {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(CardRewardScreen __instance) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: Skipping CardRewardScreen.draftOpen() - should not appear in arena");

                // Clear the cards
                __instance.rewardGroup.clear();

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Intercept boss relic choice screen as well.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.CardRewardScreen", method = "chooseOneOpen")
    public static class SkipChooseOneOpen {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(CardRewardScreen __instance, ArrayList cards) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: Skipping CardRewardScreen.chooseOneOpen() - should not appear in arena");

                // Clear the cards
                __instance.rewardGroup.clear();
                if (cards != null) {
                    cards.clear();
                }

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Trigger the return to main menu after arena victory.
     */
    private static void triggerReturnToMainMenu() {
        try {
            STSArena.logger.info("ARENA: triggerReturnToMainMenu called from CardRewardScreen patch");

            // Set the flag to return to arena selection when main menu is reached
            STSArena.setReturnToArenaOnMainMenu();

            // Clear the arena run state
            ArenaRunner.clearArenaRun();

            // Mark room as complete
            if (AbstractDungeon.getCurrRoom() != null) {
                AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
            }

            // Reset game settings
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;

            // Trigger return to main menu
            CardCrawlGame.startOver();

            STSArena.logger.info("ARENA: Initiated return to main menu");
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Error in triggerReturnToMainMenu", e);
        }
    }
}
