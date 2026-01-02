package stsarena.arena;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import stsarena.STSArena;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages save file backup and restoration for arena mode.
 *
 * When arena mode starts, any existing save file is backed up.
 * When arena mode ends, the original save is restored (or the arena save is deleted
 * if there was no original).
 *
 * This ensures arena fights don't corrupt or overwrite normal game saves.
 */
public class SaveFileManager {

    private static final String BACKUP_SUFFIX = ".arena_backup";

    // Track whether we have an active backup for each character class
    private static AbstractPlayer.PlayerClass activeBackupClass = null;
    private static boolean hadOriginalSave = false;

    /**
     * Back up the original save file before starting an arena run.
     * Call this before creating the arena save file.
     *
     * @param playerClass The character class whose save to back up
     */
    public static void backupOriginalSave(AbstractPlayer.PlayerClass playerClass) {
        if (activeBackupClass != null) {
            STSArena.logger.warn("SaveFileManager: Already have active backup for " + activeBackupClass +
                ", will overwrite tracking for " + playerClass);
        }

        String savePath = ArenaSaveManager.getSavePath(playerClass);
        File saveFile = new File(savePath);
        File backupFile = new File(savePath + BACKUP_SUFFIX);

        activeBackupClass = playerClass;

        if (saveFile.exists()) {
            try {
                Files.copy(saveFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                hadOriginalSave = true;
                STSArena.logger.info("SaveFileManager: Backed up original save for " + playerClass +
                    " (" + saveFile.length() + " bytes)");
            } catch (IOException e) {
                STSArena.logger.error("SaveFileManager: Failed to backup save file", e);
                hadOriginalSave = false;
            }
        } else {
            hadOriginalSave = false;
            STSArena.logger.info("SaveFileManager: No original save for " + playerClass + " to backup");
        }
    }

    /**
     * Restore the original save file after an arena run ends.
     * If there was an original save, it is restored.
     * If there was no original save, any save file created during arena mode is deleted.
     *
     * Call this when the arena run ends (victory, defeat, or abandon).
     */
    public static void restoreOriginalSave() {
        if (activeBackupClass == null) {
            STSArena.logger.info("SaveFileManager: No active backup to restore");
            return;
        }

        String savePath = ArenaSaveManager.getSavePath(activeBackupClass);
        File saveFile = new File(savePath);
        File backupFile = new File(savePath + BACKUP_SUFFIX);

        if (hadOriginalSave) {
            // Restore the backed-up save
            if (backupFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    backupFile.delete();
                    STSArena.logger.info("SaveFileManager: Restored original save for " + activeBackupClass);
                } catch (IOException e) {
                    STSArena.logger.error("SaveFileManager: Failed to restore save file", e);
                }
            } else {
                STSArena.logger.warn("SaveFileManager: Backup file missing, cannot restore");
            }
        } else {
            // No original save - delete any save file created during arena mode
            if (saveFile.exists()) {
                if (saveFile.delete()) {
                    STSArena.logger.info("SaveFileManager: Deleted arena save for " + activeBackupClass +
                        " (no original existed)");
                } else {
                    STSArena.logger.error("SaveFileManager: Failed to delete arena save");
                }
            }
            // Clean up backup file if it somehow exists
            if (backupFile.exists()) {
                backupFile.delete();
            }
        }

        // Clear state
        activeBackupClass = null;
        hadOriginalSave = false;
    }

    /**
     * Check if we have an active save backup.
     */
    public static boolean hasActiveBackup() {
        return activeBackupClass != null;
    }

    /**
     * Get the character class with an active backup, or null if none.
     */
    public static AbstractPlayer.PlayerClass getActiveBackupClass() {
        return activeBackupClass;
    }

    /**
     * Check if there was an original save file when the backup was made.
     */
    public static boolean hadOriginalSave() {
        return hadOriginalSave;
    }

    /**
     * Force clear the backup state without restoring.
     * Only use this for error recovery.
     */
    public static void clearBackupState() {
        if (activeBackupClass != null) {
            // Try to clean up backup file
            String savePath = ArenaSaveManager.getSavePath(activeBackupClass);
            File backupFile = new File(savePath + BACKUP_SUFFIX);
            if (backupFile.exists()) {
                backupFile.delete();
            }
        }
        activeBackupClass = null;
        hadOriginalSave = false;
    }
}
