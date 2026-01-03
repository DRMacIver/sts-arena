package stsarena.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Adds "Continue" and "Try Again" buttons to the victory screen for imperfect arena victories.
 * An imperfect victory is when the player wins but took damage during the fight.
 */
public class ArenaVictoryScreenPatch {

    private static final float BUTTON_WIDTH = 240.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 160.0f * Settings.scale;
    private static final float BUTTON_Y = Settings.HEIGHT * 0.15f;
    private static final float BUTTON_SPACING = 300.0f * Settings.scale;
    private static final float MESSAGE_Y = Settings.HEIGHT * 0.35f;

    // Button hitboxes
    private static Hitbox continueHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private static Hitbox tryAgainHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Button state
    private static boolean buttonsVisible = false;
    private static boolean continueClickStarted = false;
    private static boolean tryAgainClickStarted = false;

    // Track if this was an imperfect victory
    private static boolean isImperfectVictory = false;
    private static int damageTaken = 0;

    /**
     * Check if this arena victory was imperfect (player took damage).
     */
    public static void checkImperfectVictory() {
        if (!ArenaRunner.isArenaRun()) {
            isImperfectVictory = false;
            damageTaken = 0;
            return;
        }

        if (AbstractDungeon.player != null) {
            int startHp = ArenaRunner.getCurrentLoadout() != null ?
                ArenaRunner.getCurrentLoadout().currentHp : AbstractDungeon.player.maxHealth;
            int endHp = AbstractDungeon.player.currentHealth;
            damageTaken = startHp - endHp;
            isImperfectVictory = damageTaken > 0;
            STSArena.logger.info("ARENA: Victory check - startHp=" + startHp + ", endHp=" + endHp +
                ", damageTaken=" + damageTaken + ", imperfect=" + isImperfectVictory);
        }
    }

    /**
     * Check for imperfect victory when VictoryScreen is constructed.
     */
    @SpirePatch(clz = VictoryScreen.class, method = SpirePatch.CONSTRUCTOR, paramtypez = {MonsterGroup.class})
    public static class ConstructorPatch {
        @SpirePostfixPatch
        public static void Postfix(VictoryScreen __instance, MonsterGroup m) {
            checkImperfectVictory();
        }
    }

    /**
     * Intercept update to handle our custom buttons in arena mode for imperfect victories.
     */
    @SpirePatch(clz = VictoryScreen.class, method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(VictoryScreen __instance) {
            if (!ArenaRunner.isArenaRun()) {
                buttonsVisible = false;
                return;
            }

            // Only show buttons for imperfect victories
            if (!isImperfectVictory) {
                buttonsVisible = false;
                return;
            }

            buttonsVisible = true;

            // Position buttons
            float centerX = Settings.WIDTH / 2.0f;
            continueHb.move(centerX - BUTTON_SPACING / 2.0f, BUTTON_Y);
            tryAgainHb.move(centerX + BUTTON_SPACING / 2.0f, BUTTON_Y);

            continueHb.update();
            tryAgainHb.update();

            // Handle hover sounds
            if (continueHb.justHovered || tryAgainHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            // Handle click start
            if (InputHelper.justClickedLeft) {
                if (continueHb.hovered) {
                    continueHb.clickStarted = true;
                    continueClickStarted = true;
                    CardCrawlGame.sound.play("UI_CLICK_1");
                } else if (tryAgainHb.hovered) {
                    tryAgainHb.clickStarted = true;
                    tryAgainClickStarted = true;
                    CardCrawlGame.sound.play("UI_CLICK_1");
                }
            }

            // Handle click release
            if (continueHb.clicked) {
                continueHb.clicked = false;
                continueHb.clickStarted = false;
                continueClickStarted = false;
                STSArena.logger.info("ARENA: Continue button clicked on imperfect victory");
                handleContinue();
            }

            if (tryAgainHb.clicked) {
                tryAgainHb.clicked = false;
                tryAgainHb.clickStarted = false;
                tryAgainClickStarted = false;
                STSArena.logger.info("ARENA: Try Again button clicked on imperfect victory");
                handleTryAgain();
            }
        }

        private static void handleContinue() {
            // Clear imperfect victory state and proceed
            isImperfectVictory = false;
            damageTaken = 0;
            buttonsVisible = false;

            // Return to encounter selection via main menu
            ArenaRunner.clearArenaRun();
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;
            STSArena.setReturnToArenaOnMainMenu();
            CardCrawlGame.startOver();
        }

        private static void handleTryAgain() {
            // Clear imperfect victory state
            isImperfectVictory = false;
            damageTaken = 0;
            buttonsVisible = false;

            // Restart the current fight
            ArenaRunner.restartCurrentFight();
        }
    }

    /**
     * Render our custom buttons for imperfect victories.
     */
    @SpirePatch(clz = VictoryScreen.class, method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(VictoryScreen __instance, SpriteBatch sb) {
            if (!ArenaRunner.isArenaRun() || !buttonsVisible) {
                return;
            }

            // Render message about imperfect victory
            String message = "You took " + damageTaken + " damage. Try again for a perfect victory?";
            FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, message,
                Settings.WIDTH / 2.0f, MESSAGE_Y, Settings.GOLD_COLOR);

            // Render Continue button
            renderButton(sb, continueHb, "Continue", continueClickStarted);

            // Render Try Again button
            renderButton(sb, tryAgainHb, "Try Again", tryAgainClickStarted);
        }

        private static void renderButton(SpriteBatch sb, Hitbox hb, String label, boolean clickStarted) {
            float x = hb.cX;
            float y = hb.cY;

            // Determine color based on state
            Color buttonColor;
            if (clickStarted) {
                buttonColor = Color.LIGHT_GRAY;
            } else if (hb.hovered) {
                buttonColor = Color.WHITE;
            } else {
                buttonColor = new Color(0.7f, 0.7f, 0.7f, 1.0f);
            }

            // Draw button background
            sb.setColor(buttonColor);
            sb.draw(ImageMaster.DYNAMIC_BTN_IMG2,
                x - 256.0f, y - 256.0f,
                256.0f, 256.0f,
                512.0f, 512.0f,
                Settings.scale, Settings.scale,
                0.0f,
                0, 0, 512, 512,
                false, false);

            // Draw button label
            FontHelper.renderFontCentered(sb, FontHelper.panelEndTurnFont, label, x, y, buttonColor);

            // Draw hitbox for debugging (optional)
            hb.render(sb);
        }
    }

    /**
     * Hide the default "Continue" button in arena mode for imperfect victories.
     */
    @SpirePatch(clz = VictoryScreen.class, method = "renderArenaResults")
    public static class HideDefaultButtonPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(VictoryScreen __instance, SpriteBatch sb) {
            // Only hide when we're in arena mode with imperfect victory
            if (ArenaRunner.isArenaRun() && buttonsVisible) {
                // Skip the default arena results rendering
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
