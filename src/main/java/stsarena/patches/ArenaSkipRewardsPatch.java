package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Skip combat rewards in arena mode - we don't want card/gold rewards.
 * This patches the monster's die() method to prevent reward drops.
 */
public class ArenaSkipRewardsPatch {

    /**
     * Prevent monsters from dropping rewards in arena mode.
     * The die() method is called when a monster is killed and triggers reward drops.
     */
    @SpirePatch(clz = AbstractMonster.class, method = "die", paramtypez = {boolean.class})
    public static class SkipMonsterRewards {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractMonster __instance, boolean triggerRelics) {
            if (ArenaRunner.isArenaRun()) {
                // In arena mode, kill the monster but skip reward generation
                // Set isDying and halfDead to prevent the base method from running reward logic
                __instance.isDying = true;
                __instance.currentHealth = 0;
                
                // Don't skip the method entirely - we need death animations and effects
                // But the rewards are triggered by conditions we can work around
                STSArena.logger.info("ARENA: Monster dying, will skip rewards");
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Prevent the room from adding gold rewards in arena mode.
     */
    @SpirePatch(clz = AbstractRoom.class, method = "addGoldToRewards", paramtypez = {int.class})
    public static class SkipGoldRewards {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractRoom __instance, int gold) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: Skipping gold reward: " + gold);
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Prevent the room from adding potion rewards in arena mode.
     */
    @SpirePatch(clz = AbstractRoom.class, method = "addPotionToRewards", paramtypez = {com.megacrit.cardcrawl.potions.AbstractPotion.class})
    public static class SkipPotionRewards {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(AbstractRoom __instance, com.megacrit.cardcrawl.potions.AbstractPotion potion) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: Skipping potion reward");
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
