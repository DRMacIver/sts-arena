package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Patches to hook into dungeon initialization for arena mode.
 */
public class ArenaDungeonPatch {

    /**
     * Hook into when the player enters their first room (after Neow).
     * This is when we apply our loadout and force the fight.
     */
    @SpirePatch(
        clz = AbstractRoom.class,
        method = "onPlayerEntry"
    )
    public static class OnPlayerEntry {
        @SpirePostfixPatch
        public static void Postfix(AbstractRoom __instance) {
            if (ArenaRunner.hasPendingLoadout()) {
                STSArena.logger.info("Player entered room, triggering arena setup");
                ArenaRunner.onDungeonInitialized();
            }
        }
    }
}
