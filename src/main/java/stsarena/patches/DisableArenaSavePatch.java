package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Prevents the game from creating save files during arena runs.
 * This ensures arena fights are truly isolated practice sessions.
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
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("Skipping save - arena run");
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
