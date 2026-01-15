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
                // Check if this is an imperfect victory (player took damage or got cursed)
                // Use didTakeDamageThisCombat() flag instead of comparing HP, because relics like
                // Burning Blood can heal the player at end of combat before this check runs.
                boolean imperfect = ArenaRunner.didTakeDamageThisCombat();
                STSArena.logger.info("ARENA: CombatRewardScreen imperfect check - tookDamage=" + imperfect);
                // TODO: Also track curses gained during combat for perfect victory check

                // Clear the rewards to prevent any processing
                __instance.rewards.clear();

                // Prevent POST_COMBAT save in AbstractRoom.update() after this method returns.
                AbstractDungeon.loading_post_combat = true;

                if (imperfect) {
                    // Imperfect victory: show results screen with retry/modify options
                    STSArena.logger.info("ARENA: Imperfect victory - opening ArenaResultsScreen");
                    STSArena.openResultsScreenVictory(true);
                } else if (ArenaRunner.wasStartedFromNormalRun()) {
                    // Perfect victory from normal run: show results screen so user can return to normal run
                    STSArena.logger.info("ARENA: Perfect victory from normal run - opening ArenaResultsScreen");
                    STSArena.openResultsScreenVictory(false);
                } else {
                    // Perfect victory from main menu: go directly to encounter selection
                    STSArena.logger.info("ARENA: Perfect victory - returning to encounter selection");
                    triggerReturnToMainMenu();
                }

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
