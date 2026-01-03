package stsarena.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Adds "Retreat" and "Try Again" buttons to the death screen when in arena mode.
 */
public class ArenaDeathScreenButtonsPatch {

    private static final float BUTTON_WIDTH = 240.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 160.0f * Settings.scale;
    private static final float BUTTON_Y = Settings.HEIGHT * 0.15f;
    private static final float BUTTON_SPACING = 300.0f * Settings.scale;

    // Button hitboxes
    private static Hitbox retreatHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private static Hitbox tryAgainHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Button state
    private static boolean buttonsVisible = false;
    private static boolean retreatClickStarted = false;
    private static boolean tryAgainClickStarted = false;

    /**
     * Intercept update to handle our custom buttons in arena mode.
     */
    @SpirePatch(clz = DeathScreen.class, method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance) {
            if (!ArenaRunner.isArenaRun()) {
                buttonsVisible = false;
                return;
            }

            buttonsVisible = true;

            // Position buttons
            float centerX = Settings.WIDTH / 2.0f;
            retreatHb.move(centerX - BUTTON_SPACING / 2.0f, BUTTON_Y);
            tryAgainHb.move(centerX + BUTTON_SPACING / 2.0f, BUTTON_Y);

            retreatHb.update();
            tryAgainHb.update();

            // Handle hover sounds
            if (retreatHb.justHovered || tryAgainHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            // Handle click start - must set the hitbox's clickStarted field
            if (InputHelper.justClickedLeft) {
                if (retreatHb.hovered) {
                    retreatHb.clickStarted = true;
                    retreatClickStarted = true;
                    CardCrawlGame.sound.play("UI_CLICK_1");
                } else if (tryAgainHb.hovered) {
                    tryAgainHb.clickStarted = true;
                    tryAgainClickStarted = true;
                    CardCrawlGame.sound.play("UI_CLICK_1");
                }
            }

            // Handle click release
            if (retreatHb.clicked) {
                retreatHb.clicked = false;
                retreatHb.clickStarted = false;
                retreatClickStarted = false;
                STSArena.logger.info("ARENA: Retreat button clicked");
                // Return to main menu
                handleRetreat();
            }

            if (tryAgainHb.clicked) {
                tryAgainHb.clicked = false;
                tryAgainHb.clickStarted = false;
                tryAgainClickStarted = false;
                STSArena.logger.info("ARENA: Try Again button clicked");
                // Restart the fight
                handleTryAgain();
            }
        }

        private static void handleRetreat() {
            // Clear arena state and return to main menu
            ArenaRunner.clearArenaRun();
            Settings.isTrial = false;
            Settings.isDailyRun = false;
            Settings.isEndless = false;
            CardCrawlGame.startOver();
        }

        private static void handleTryAgain() {
            // Restart the current fight
            ArenaRunner.restartCurrentFight();
        }
    }

    /**
     * Render our custom buttons instead of (or in addition to) the normal return button.
     */
    @SpirePatch(clz = DeathScreen.class, method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, SpriteBatch sb) {
            if (!ArenaRunner.isArenaRun() || !buttonsVisible) {
                return;
            }

            // Render Retreat button
            renderButton(sb, retreatHb, "Retreat", retreatClickStarted);

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
     * Hide the default return button in arena mode by intercepting its render.
     */
    @SpirePatch(clz = com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton.class, method = "render")
    public static class HideDefaultButtonPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton __instance, SpriteBatch sb) {
            // Only hide when we're in arena mode AND on the death screen
            if (ArenaRunner.isArenaRun() && buttonsVisible) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Skip rendering the stats screen in arena mode - we don't want to show run scores.
     */
    @SpirePatch(clz = GameOverScreen.class, method = "renderStatsScreen")
    public static class HideStatsScreenPatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(GameOverScreen __instance, SpriteBatch sb) {
            if (ArenaRunner.isArenaRun()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Skip updating the stats screen animations in arena mode.
     */
    @SpirePatch(clz = DeathScreen.class, method = "updateStatsScreen")
    public static class SkipStatsUpdatePatch {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(DeathScreen __instance) {
            if (ArenaRunner.isArenaRun()) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }
}
