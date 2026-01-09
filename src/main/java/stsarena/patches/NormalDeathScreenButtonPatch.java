package stsarena.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.DeathScreen;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * Adds "Try Again in Arena Mode" button to the death screen for normal (non-arena) runs.
 */
public class NormalDeathScreenButtonPatch {

    private static final float BUTTON_WIDTH = 240.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 160.0f * Settings.scale;
    private static final float BUTTON_Y = Settings.HEIGHT * 0.25f;  // Above the normal button

    // Button hitbox
    private static Hitbox arenaRetryHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Button state
    private static boolean buttonVisible = false;
    private static boolean clickStarted = false;

    /**
     * Update the arena retry button.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance) {
            // Only show in normal runs (not arena) and when we have retry data
            // Don't show if the run was abandoned
            if (ArenaRunner.isArenaRun()) {
                buttonVisible = false;
                return;
            }

            // Don't show if the run was explicitly abandoned (not just died)
            if (ArenaPauseButtonPatch.isAbandoning) {
                buttonVisible = false;
                return;
            }

            if (!NormalRunLoadoutSaver.hasArenaRetryData()) {
                buttonVisible = false;
                return;
            }

            buttonVisible = true;

            // Position button above the normal return button
            float centerX = Settings.WIDTH / 2.0f;
            arenaRetryHb.move(centerX, BUTTON_Y);
            arenaRetryHb.update();

            // Handle hover sounds
            if (arenaRetryHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            // Handle click start - must set the hitbox's clickStarted field
            if (InputHelper.justClickedLeft && arenaRetryHb.hovered) {
                arenaRetryHb.clickStarted = true;
                clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            }

            // Handle click release - hitbox.clicked is set by hitbox.update() when click released
            if (arenaRetryHb.clicked) {
                arenaRetryHb.clicked = false;
                arenaRetryHb.clickStarted = false;
                clickStarted = false;
                STSArena.logger.info("NORMAL RUN: Try Again in Arena Mode button clicked");
                handleArenaRetry();
            }
        }

        private static void handleArenaRetry() {
            long loadoutId = NormalRunLoadoutSaver.getLastSavedLoadoutId();
            String encounterId = NormalRunLoadoutSaver.getLastSavedEncounterId();

            STSArena.logger.info("Starting arena fight from defeat: loadoutId=" + loadoutId + ", encounter=" + encounterId);

            // Clear the retry data since we're using it
            NormalRunLoadoutSaver.clearRetryData();

            // Start the arena fight
            ArenaRunner.startFightFromDefeat(loadoutId, encounterId);
        }
    }

    /**
     * Render the arena retry button.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, SpriteBatch sb) {
            if (!buttonVisible) {
                return;
            }

            float x = arenaRetryHb.cX;
            float y = arenaRetryHb.cY;

            // Determine color based on state
            Color buttonColor;
            if (clickStarted) {
                buttonColor = Color.LIGHT_GRAY;
            } else if (arenaRetryHb.hovered) {
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

            // Draw button label (two lines)
            FontHelper.renderFontCentered(sb, FontHelper.panelEndTurnFont, "Try Again", x, y + 15.0f * Settings.scale, buttonColor);
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, "in Arena Mode", x, y - 20.0f * Settings.scale, buttonColor);

            // Draw hitbox for debugging (optional)
            arenaRetryHb.render(sb);
        }
    }
}
