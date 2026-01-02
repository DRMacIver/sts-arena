package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.SaveFileManager;

/**
 * Prevents the game from creating save files during arena runs.
 * This ensures arena fights are truly isolated practice sessions.
 *
 * Uses both in-memory flag (isArenaRun) and file-based marker for robustness.
 */
public class DisableArenaSavePatch {

    /**
     * Patch SaveAndContinue.save() to skip saving during arena runs.
     */
    @SpirePatch(
        clz = SaveAndContinue.class,
        method = "save"
    )
    public static class DisableSave {
        public static SpireReturn<Void> Prefix(SaveFile saveFile) {
            // Check both in-memory flag AND file-based marker for robustness
            // This prevents saves even if the in-memory flag gets out of sync
            if (ArenaRunner.isArenaRun() || SaveFileManager.hasActiveArenaSession()) {
                STSArena.logger.info("Skipping save - arena mode active (flag=" +
                    ArenaRunner.isArenaRun() + ", marker=" + SaveFileManager.hasActiveArenaSession() + ")");
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
