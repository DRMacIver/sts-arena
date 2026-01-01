package stsarena.patches;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.ui.panels.PotionPopUp;
import javassist.CtBehavior;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

import java.lang.reflect.Field;

/**
 * Patches to track arena combat outcomes.
 */
public class ArenaCombatPatch {

    /**
     * Patch to detect victory - called when all monsters are dead.
     */
    @SpirePatch(clz = AbstractRoom.class, method = "endBattle")
    public static class VictoryPatch {
        @SpirePostfixPatch
        public static void Postfix(AbstractRoom __instance) {
            STSArena.logger.info("ARENA: endBattle called - isArenaRun=" + ArenaRunner.isArenaRun() + ", runDbId=" + ArenaRunner.getCurrentRunDbId());
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: endBattle - recording victory");
                ArenaRunner.recordVictory();
            }
        }
    }

    /**
     * Patch to detect defeat - called when player dies.
     */
    @SpirePatch(clz = DeathScreen.class, method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
    public static class DefeatPatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, MonsterGroup m) {
            if (ArenaRunner.isArenaRun()) {
                STSArena.logger.info("ARENA: DeathScreen created - recording defeat");
                ArenaRunner.recordDefeat();
            }
        }
    }

    /**
     * Patch to track potion usage for targeted potions.
     * The use() call is in updateTargetMode(), which handles targeted potions.
     */
    @SpirePatch(clz = PotionPopUp.class, method = "updateTargetMode")
    public static class TargetedPotionUsePatch {
        private static Field potionField = null;

        @SpireInsertPatch(locator = UseLocator.class)
        public static void Insert(PotionPopUp __instance) {
            if (!ArenaRunner.isArenaRun()) {
                return;
            }

            try {
                if (potionField == null) {
                    potionField = PotionPopUp.class.getDeclaredField("potion");
                    potionField.setAccessible(true);
                }
                AbstractPotion potion = (AbstractPotion) potionField.get(__instance);
                if (potion != null) {
                    STSArena.logger.info("ARENA: Targeted potion used - " + potion.ID);
                    ArenaRunner.recordPotionUsed(potion.ID);
                }
            } catch (Exception e) {
                STSArena.logger.error("Failed to track targeted potion usage", e);
            }
        }

        public static class UseLocator extends SpireInsertLocator {
            @Override
            public int[] Locate(CtBehavior ctMethodToPatch) throws Exception {
                Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractPotion.class, "use");
                return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
            }
        }
    }

    /**
     * Patch to track potion usage for non-targeted potions.
     * The use() call is in updateInput(), which handles non-targeted potions.
     */
    @SpirePatch(clz = PotionPopUp.class, method = "updateInput")
    public static class NonTargetedPotionUsePatch {
        private static Field potionField = null;

        @SpireInsertPatch(locator = UseLocator.class)
        public static void Insert(PotionPopUp __instance) {
            if (!ArenaRunner.isArenaRun()) {
                return;
            }

            try {
                if (potionField == null) {
                    potionField = PotionPopUp.class.getDeclaredField("potion");
                    potionField.setAccessible(true);
                }
                AbstractPotion potion = (AbstractPotion) potionField.get(__instance);
                if (potion != null) {
                    STSArena.logger.info("ARENA: Non-targeted potion used - " + potion.ID);
                    ArenaRunner.recordPotionUsed(potion.ID);
                }
            } catch (Exception e) {
                STSArena.logger.error("Failed to track non-targeted potion usage", e);
            }
        }

        public static class UseLocator extends SpireInsertLocator {
            @Override
            public int[] Locate(CtBehavior ctMethodToPatch) throws Exception {
                Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractPotion.class, "use");
                return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
            }
        }
    }
}
