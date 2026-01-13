package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.SaveFileManager;

/**
 * Prevents the game from creating save files during arena runs.
 * This ensures arena fights are truly isolated practice sessions.
 *
 * Uses both in-memory flag (isArenaRun) and file-based marker for robustness.
 * Throws an error if a save is attempted during arena mode to help identify
 * unexpected save file creation.
 */
public class DisableArenaSavePatch {

    /**
     * Exception thrown when a save is attempted during arena mode.
     * This helps identify unexpected save file creation during arena runs.
     */
    public static class ArenaSaveAttemptException extends RuntimeException {
        public ArenaSaveAttemptException(String message) {
            super(message);
        }
    }

    /**
     * Patch SaveAndContinue.save() to block saving during arena runs.
     * Throws an exception to help identify the source of unexpected save attempts.
     */
    @SpirePatch(
        cls = "com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue",
        method = "save"
    )
    public static class DisableSave {
        public static SpireReturn<Void> Prefix(SaveFile saveFile) {
            boolean inArenaRun = ArenaRunner.isArenaRun();
            boolean hasMarker = SaveFileManager.hasActiveArenaSession();

            if (inArenaRun || hasMarker) {
                String floorInfo = saveFile != null ? "floor=" + saveFile.floor_num : "null";
                String message = String.format(
                    "ARENA ERROR: Save attempted during arena mode! " +
                    "%s, isArenaRun=%b, hasActiveMarker=%b",
                    floorInfo, inArenaRun, hasMarker
                );

                // Log the error with full stack trace
                ArenaSaveAttemptException exception = new ArenaSaveAttemptException(message);
                STSArena.logger.error(message, exception);

                // Throw the exception to fail fast and identify the source
                throw exception;
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Patch AsyncSaver.save() as a lower-level catch-all.
     * This catches any save attempts that might bypass SaveAndContinue.save().
     * Only blocks saves to .autosave files (game saves), not preferences.
     */
    @SpirePatch(
        cls = "com.megacrit.cardcrawl.helpers.AsyncSaver",
        method = "save"
    )
    public static class DisableAsyncSave {
        public static SpireReturn<Void> Prefix(String filepath, String data) {
            // Only block autosave files, not preferences
            if (filepath == null || !filepath.contains(".autosave")) {
                return SpireReturn.Continue();
            }

            boolean inArenaRun = ArenaRunner.isArenaRun();
            boolean hasMarker = SaveFileManager.hasActiveArenaSession();

            if (inArenaRun || hasMarker) {
                String message = String.format(
                    "ARENA ERROR: AsyncSaver.save() attempted during arena mode! " +
                    "filepath=%s, isArenaRun=%b, hasActiveMarker=%b",
                    filepath, inArenaRun, hasMarker
                );

                ArenaSaveAttemptException exception = new ArenaSaveAttemptException(message);
                STSArena.logger.error(message, exception);
                throw exception;
            }
            return SpireReturn.Continue();
        }
    }
}
