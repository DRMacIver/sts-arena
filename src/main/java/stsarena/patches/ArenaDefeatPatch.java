package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.DeathScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patch to detect defeat in arena combat.
 * In arena mode, we record the defeat and trigger return to main menu.
 */
@SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
public class ArenaDefeatPatch {
    @SpirePostfixPatch
    public static void Postfix(DeathScreen __instance, MonsterGroup m) {
        if (ArenaRunner.isArenaRun()) {
            STSArena.logger.info("ARENA: DeathScreen created - recording defeat");
            ArenaRunner.recordDefeat();

            // In screenshot mode, don't auto-return - let the death screen be captured
            if (STSArena.isScreenshotMode()) {
                STSArena.logger.info("ARENA: Screenshot mode - skipping auto-return from DeathScreen");
                return;
            }

            // Trigger automatic return to main menu after a brief delay
            // This is set by recordDefeat() via STSArena.setReturnToArenaOnMainMenu()
            // Force immediate return for test consistency
            triggerReturnToMainMenu();
        }
    }

    /**
     * Trigger the return to main menu after arena defeat.
     */
    private static void triggerReturnToMainMenu() {
        try {
            STSArena.logger.info("ARENA: triggerReturnToMainMenu from DefeatPatch");

            // Mark room as complete
            if (AbstractDungeon.getCurrRoom() != null) {
                AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
            }

            // Reset game settings
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;

            // Set return flag BEFORE clearing arena state
            // This ensures the arena selection screen opens after returning to menu
            STSArena.setReturnToArenaOnMainMenu();

            // Clear the "run in progress" flag so ClearArenaOnMainMenuPatch will clear state.
            // We keep isArenaRun true so patches can still detect arena mode during the transition.
            ArenaRunner.setArenaRunInProgress(false);

            // Trigger return to main menu
            // IMPORTANT: Do NOT clear isArenaRun here! startOver() is async and the death screen
            // will continue updating. ArenaDeathScreenButtonsPatch.SkipUpdateOnStartOver checks
            // (isArenaRun && startOver) to skip updates during the transition.
            // ClearArenaOnMainMenuPatch will clear the arena state when MainMenuScreen is created.
            CardCrawlGame.startOver();

            STSArena.logger.info("ARENA: Initiated return to main menu after defeat");
        } catch (Exception e) {
            STSArena.logger.error("ARENA: Error in triggerReturnToMainMenu", e);
        }
    }
}
