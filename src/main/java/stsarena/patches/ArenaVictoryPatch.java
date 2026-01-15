package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patch to detect victory in arena combat.
 */
@SpirePatch(cls = "com.megacrit.cardcrawl.rooms.AbstractRoom", method = "endBattle")
public class ArenaVictoryPatch {
    @SpirePostfixPatch
    public static void Postfix(AbstractRoom __instance) {
        STSArena.logger.info("ARENA: endBattle called - isArenaRun=" + ArenaRunner.isArenaRun() + ", runDbId=" + ArenaRunner.getCurrentRunDbId());
        if (ArenaRunner.isArenaRun()) {
            // Check if this was an imperfect victory (player took damage)
            // Use didTakeDamageThisCombat() flag instead of comparing HP, because relics like
            // Burning Blood can heal the player at end of combat before this check runs.
            boolean imperfect = ArenaRunner.didTakeDamageThisCombat();
            STSArena.logger.info("ARENA: endBattle imperfect check - tookDamage=" + imperfect);

            STSArena.logger.info("ARENA: endBattle - recording victory (imperfect=" + imperfect + ")");
            // Record victory but don't trigger return to menu for imperfect victories
            // The VictoryScreen patch will handle showing Try Again option
            ArenaRunner.recordVictory(imperfect);
        }
    }
}
