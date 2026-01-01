package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patch to detect victory in arena combat.
 */
@SpirePatch(clz = AbstractRoom.class, method = "endBattle")
public class ArenaVictoryPatch {
    @SpirePostfixPatch
    public static void Postfix(AbstractRoom __instance) {
        STSArena.logger.info("ARENA: endBattle called - isArenaRun=" + ArenaRunner.isArenaRun() + ", runDbId=" + ArenaRunner.getCurrentRunDbId());
        if (ArenaRunner.isArenaRun()) {
            STSArena.logger.info("ARENA: endBattle - recording victory");
            ArenaRunner.recordVictory();
        }
    }
}
