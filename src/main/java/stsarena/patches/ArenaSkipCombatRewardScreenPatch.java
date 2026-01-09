package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.CombatRewardScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

import java.util.ArrayList;

/**
 * Skip the combat reward screen entirely in arena mode.
 * When an arena fight is won, we should return to main menu, not show rewards.
 */
public class ArenaSkipCombatRewardScreenPatch {

    /**
     * Intercept the combat reward screen opening and skip it in arena mode.
     * For imperfect victories (where player took damage), we don't auto-return
     * so the VictoryScreen can show retry/modify options.
     * In screenshot mode, we never auto-return so victory screens can be captured.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.CombatRewardScreen", method = "open", paramtypez = {})
    public static class SkipCombatRewardScreenOpen {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(CombatRewardScreen __instance) {
            if (ArenaRunner.isArenaRun()) {
                // In screenshot mode, skip rewards but DON'T return to menu
                // This allows victory screens to be captured for documentation
                if (STSArena.isScreenshotMode()) {
                    STSArena.logger.info("ARENA: Screenshot mode - skipping CombatRewardScreen without auto-return");
                    __instance.rewards.clear();
                    return SpireReturn.Return(null);
                }

                // Check if this is an imperfect victory (player took damage)
                boolean imperfect = false;
                if (AbstractDungeon.player != null && ArenaRunner.getCurrentLoadout() != null) {
                    int startHp = ArenaRunner.getCurrentLoadout().currentHp;
                    int endHp = AbstractDungeon.player.currentHealth;
                    imperfect = endHp < startHp;
                }

                if (imperfect) {
                    // For imperfect victories, skip rewards but DON'T return to menu
                    // The VictoryScreen will show our retry/modify buttons
                    STSArena.logger.info("ARENA: Skipping CombatRewardScreen for imperfect victory - VictoryScreen will show buttons");
                    __instance.rewards.clear();
                    return SpireReturn.Return(null);
                }

                // For perfect victories, auto-return to menu
                STSArena.logger.info("ARENA: Skipping CombatRewardScreen.open() - triggering return to menu");

                // Clear the rewards to prevent any processing
                __instance.rewards.clear();

                // Force return to main menu
                triggerReturnToMainMenu();

                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Trigger the return to main menu after arena victory.
     * Note: Victory recording is already done by ArenaVictoryPatch when endBattle() is called,
     * so we don't record again here (that would cause duplicate database entries).
     */
    private static void triggerReturnToMainMenu() {
        try {
            STSArena.logger.info("ARENA: triggerReturnToMainMenu called from CombatRewardScreen patch");

            // Set the flag to return to arena selection when main menu is reached
            STSArena.setReturnToArenaOnMainMenu();

            // Mark room as complete
            if (AbstractDungeon.getCurrRoom() != null) {
                AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
            }

            // Reset game settings
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;

            // Clear the "run in progress" flag so ClearArenaOnMainMenuPatch will clear state.
            // We keep isArenaRun true so patches can still detect arena mode during the transition.
            ArenaRunner.setArenaRunInProgress(false);

            // Trigger return to main menu
            // IMPORTANT: Do NOT clear isArenaRun here! startOver() is async and game screens
            // will continue updating. Arena patches check isArenaRun to skip normal game flow
            // during the transition. ClearArenaOnMainMenuPatch will clear the arena state
            // when MainMenuScreen is created.
            CardCrawlGame.startOver();

            STSArena.logger.info("ARENA: Initiated return to main menu");
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Error in triggerReturnToMainMenu", e);
        }
    }
}
