package stsarena.arena;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import stsarena.STSArena;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages save file backup and restoration for arena mode.
 *
 * Design goals:
 * 1. NEVER leave orphaned arena saves that could corrupt normal gameplay
 * 2. Survive crashes gracefully - cleanup on next startup
 * 3. Be idempotent - multiple cleanup calls are safe
 * 4. Use file-based state, not just in-memory state
 *
 * How it works:
 * - When arena starts: back up original save (if exists), create marker file
 * - When arena ends: restore original or delete arena save, delete marker file
 * - On mod startup: check for marker files and clean up any orphaned state
 */
public class SaveFileManager {

    private static final String BACKUP_SUFFIX = ".arena_backup";
    private static final String MARKER_SUFFIX = ".arena_active";

    // All character class names for cleanup
    private static final String[] ALL_CLASSES = {"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};

    /**
     * Clean up any orphaned arena saves from previous sessions.
     * Call this on mod initialization.
     */
    public static void cleanupOrphanedArenaSaves() {
        STSArena.logger.info("SaveFileManager: Checking for orphaned arena saves...");

        int cleanedUp = 0;
        for (String className : ALL_CLASSES) {
            if (cleanupForClass(className)) {
                cleanedUp++;
            }
        }

        if (cleanedUp > 0) {
            STSArena.logger.info("SaveFileManager: Cleaned up orphaned arena state for " + cleanedUp + " character(s)");

            // Also reset loadingSave flag in case it was left true from a crash
            // This prevents the game from trying to load a save when starting a new run
            CardCrawlGame.loadingSave = false;
            STSArena.logger.info("SaveFileManager: Reset loadingSave flag");
        } else {
            STSArena.logger.info("SaveFileManager: No orphaned arena saves found");
        }
    }

    /**
     * Clean up arena state for a specific character class.
     * Returns true if cleanup was needed.
     */
    private static boolean cleanupForClass(String className) {
        String savePath = getSavePathForClassName(className);
        File saveFile = new File(savePath);
        File backupFile = new File(savePath + BACKUP_SUFFIX);
        File markerFile = new File(savePath + MARKER_SUFFIX);

        // If marker file exists, arena mode was active when game closed
        if (markerFile.exists()) {
            STSArena.logger.warn("SaveFileManager: Found orphaned arena marker for " + className);

            if (backupFile.exists()) {
                // Restore the backup
                try {
                    Files.copy(backupFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    backupFile.delete();
                    STSArena.logger.info("SaveFileManager: Restored backup for " + className);
                } catch (IOException e) {
                    STSArena.logger.error("SaveFileManager: Failed to restore backup for " + className, e);
                }
            } else {
                // No backup means there was no original save - delete the arena save
                if (saveFile.exists()) {
                    if (saveFile.delete()) {
                        STSArena.logger.info("SaveFileManager: Deleted orphaned arena save for " + className);
                    }
                }
            }

            // Always delete the marker
            markerFile.delete();
            return true;
        }

        // Also clean up any stray backup files (shouldn't happen, but defensive)
        if (backupFile.exists() && !markerFile.exists()) {
            STSArena.logger.warn("SaveFileManager: Found stray backup file for " + className + " without marker, cleaning up");
            backupFile.delete();
            return true;
        }

        return false;
    }

    /**
     * Get save path for a class name string.
     * Uses the cross-platform saves directory from ArenaSaveManager.
     */
    private static String getSavePathForClassName(String className) {
        // Reuse the cross-platform logic from ArenaSaveManager
        // by constructing a temporary PlayerClass to get the path
        try {
            AbstractPlayer.PlayerClass playerClass = AbstractPlayer.PlayerClass.valueOf(className);
            return ArenaSaveManager.getSavePath(playerClass);
        } catch (IllegalArgumentException e) {
            // Fallback for unknown class names (modded characters)
            String os = System.getProperty("os.name").toLowerCase();
            String userHome = System.getProperty("user.home");
            String savesDir;

            if (os.contains("win")) {
                savesDir = "saves\\";
            } else if (os.contains("mac")) {
                savesDir = userHome + "/Library/Application Support/Steam/steamapps/common/SlayTheSpire/SlayTheSpire.app/Contents/Resources/saves/";
                if (!new File(savesDir).exists()) {
                    savesDir = "saves/";
                }
            } else {
                savesDir = "saves/";
            }

            return savesDir + className + ".autosave";
        }
    }

    /**
     * Back up the original save file before starting an arena run.
     * Creates a marker file to track that arena mode is active.
     *
     * @param playerClass The character class whose save to back up
     */
    public static void backupOriginalSave(AbstractPlayer.PlayerClass playerClass) {
        String savePath = ArenaSaveManager.getSavePath(playerClass);
        File saveFile = new File(savePath);
        File backupFile = new File(savePath + BACKUP_SUFFIX);
        File markerFile = new File(savePath + MARKER_SUFFIX);

        // First, clean up any existing state for this class (idempotent)
        if (markerFile.exists()) {
            STSArena.logger.warn("SaveFileManager: Marker already exists for " + playerClass + ", cleaning up first");
            restoreOriginalSaveForClass(playerClass);
        }

        // Create marker file FIRST - this is our crash recovery mechanism
        try {
            markerFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(markerFile)) {
                writer.write("arena_active:" + System.currentTimeMillis());
            }
            STSArena.logger.info("SaveFileManager: Created marker file for " + playerClass);
        } catch (IOException e) {
            STSArena.logger.error("SaveFileManager: Failed to create marker file", e);
            // Continue anyway - the backup is still useful even without marker
        }

        // Back up original save if it exists
        if (saveFile.exists()) {
            try {
                Files.copy(saveFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                STSArena.logger.info("SaveFileManager: Backed up original save for " + playerClass +
                    " (" + saveFile.length() + " bytes)");
            } catch (IOException e) {
                STSArena.logger.error("SaveFileManager: Failed to backup save file", e);
            }
        } else {
            // No original save - make sure no stray backup exists
            if (backupFile.exists()) {
                backupFile.delete();
            }
            STSArena.logger.info("SaveFileManager: No original save for " + playerClass + " to backup");
        }
    }

    /**
     * Restore the original save file after an arena run ends.
     * This is idempotent - safe to call multiple times.
     */
    public static void restoreOriginalSave() {
        // Clean up all classes to be safe
        for (String className : ALL_CLASSES) {
            try {
                AbstractPlayer.PlayerClass playerClass = AbstractPlayer.PlayerClass.valueOf(className);
                restoreOriginalSaveForClass(playerClass);
            } catch (IllegalArgumentException e) {
                // Class name not valid, skip
            }
        }
    }

    /**
     * Restore original save for a specific character class.
     */
    public static void restoreOriginalSaveForClass(AbstractPlayer.PlayerClass playerClass) {
        String savePath = ArenaSaveManager.getSavePath(playerClass);
        File saveFile = new File(savePath);
        File backupFile = new File(savePath + BACKUP_SUFFIX);
        File markerFile = new File(savePath + MARKER_SUFFIX);

        // If no marker, nothing to do
        if (!markerFile.exists()) {
            return;
        }

        STSArena.logger.info("SaveFileManager: Restoring original save for " + playerClass);

        if (backupFile.exists()) {
            // Restore the backed-up save
            try {
                Files.copy(backupFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                backupFile.delete();
                STSArena.logger.info("SaveFileManager: Restored original save for " + playerClass);
            } catch (IOException e) {
                STSArena.logger.error("SaveFileManager: Failed to restore save file", e);
            }
        } else {
            // No backup means there was no original save - delete any arena save
            if (saveFile.exists()) {
                if (saveFile.delete()) {
                    STSArena.logger.info("SaveFileManager: Deleted arena save for " + playerClass +
                        " (no original existed)");
                } else {
                    STSArena.logger.error("SaveFileManager: Failed to delete arena save");
                }
            }
        }

        // Always delete the marker file last
        if (markerFile.delete()) {
            STSArena.logger.info("SaveFileManager: Removed marker file for " + playerClass);
        }
    }

    /**
     * Check if arena mode is active for any character class.
     * Uses file-based detection, not in-memory state.
     */
    public static boolean hasActiveArenaSession() {
        for (String className : ALL_CLASSES) {
            String savePath = getSavePathForClassName(className);
            File markerFile = new File(savePath + MARKER_SUFFIX);
            if (markerFile.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if arena mode is active for a specific class.
     */
    public static boolean hasActiveArenaSession(AbstractPlayer.PlayerClass playerClass) {
        String savePath = ArenaSaveManager.getSavePath(playerClass);
        File markerFile = new File(savePath + MARKER_SUFFIX);
        return markerFile.exists();
    }
}
