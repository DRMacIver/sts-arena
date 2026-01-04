package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.DeathScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patch to detect defeat in arena combat.
 */
@SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
public class ArenaDefeatPatch {
    @SpirePostfixPatch
    public static void Postfix(DeathScreen __instance, MonsterGroup m) {
        if (ArenaRunner.isArenaRun()) {
            STSArena.logger.info("ARENA: DeathScreen created - recording defeat");
            ArenaRunner.recordDefeat();
        }
    }
}
