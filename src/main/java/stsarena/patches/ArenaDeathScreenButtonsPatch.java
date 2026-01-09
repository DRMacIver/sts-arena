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
    private static final float BUTTON_SPACING = 260.0f * Settings.scale;

    // Button hitboxes
    private static Hitbox retreatHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private static Hitbox modifyDeckHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private static Hitbox tryAgainHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Button state
    private static boolean buttonsVisible = false;
    private static boolean retreatClickStarted = false;
    private static boolean modifyDeckClickStarted = false;
    private static boolean tryAgainClickStarted = false;

    /**
     * Skip death screen update entirely when startOver is triggered.
     * This allows the game to process the transition to main menu.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "update")
    public static class SkipUpdateOnStartOver {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(DeathScreen __instance) {
            // If startOver was triggered (e.g., by lose command), skip the death screen
            // and let the game process the transition to main menu
            if (ArenaRunner.isArenaRun() && CardCrawlGame.startOver) {
                buttonsVisible = false;
                STSArena.logger.info("ARENA: Skipping death screen update - startOver is true");
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Intercept update to handle our custom buttons in arena mode.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance) {
            if (!ArenaRunner.isArenaRun()) {
                buttonsVisible = false;
                return;
            }

            buttonsVisible = true;

            // Position buttons (3 buttons now)
            float centerX = Settings.WIDTH / 2.0f;
            retreatHb.move(centerX - BUTTON_SPACING, BUTTON_Y);
            modifyDeckHb.move(centerX, BUTTON_Y);
            tryAgainHb.move(centerX + BUTTON_SPACING, BUTTON_Y);

            retreatHb.update();
            modifyDeckHb.update();
            tryAgainHb.update();

            // Handle hover sounds
            if (retreatHb.justHovered || modifyDeckHb.justHovered || tryAgainHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            // Handle click start - must set the hitbox's clickStarted field
            if (InputHelper.justClickedLeft) {
                if (retreatHb.hovered) {
                    retreatHb.clickStarted = true;
                    retreatClickStarted = true;
                    CardCrawlGame.sound.play("UI_CLICK_1");
                } else if (modifyDeckHb.hovered) {
                    modifyDeckHb.clickStarted = true;
                    modifyDeckClickStarted = true;
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

            if (modifyDeckHb.clicked) {
                modifyDeckHb.clicked = false;
                modifyDeckHb.clickStarted = false;
                modifyDeckClickStarted = false;
                STSArena.logger.info("ARENA: Modify Deck button clicked");
                // Open deck editor for retry
                handleModifyDeck();
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

        private static void handleModifyDeck() {
            // Open the deck editor for retry
            ArenaRunner.modifyDeckAndRetry();
        }
    }

    /**
     * Skip death screen render when startOver is triggered.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "render")
    public static class SkipRenderOnStartOver {
        @SpirePrefixPatch
        public static SpireReturn<Void> Prefix(DeathScreen __instance, SpriteBatch sb) {
            // If startOver was triggered, skip rendering the death screen entirely
            if (ArenaRunner.isArenaRun() && CardCrawlGame.startOver) {
                return SpireReturn.Return(null);
            }
            return SpireReturn.Continue();
        }
    }

    /**
     * Render our custom buttons instead of (or in addition to) the normal return button.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(DeathScreen __instance, SpriteBatch sb) {
            if (!ArenaRunner.isArenaRun() || !buttonsVisible) {
                return;
            }

            // Render Retreat button
            renderButton(sb, retreatHb, "Retreat", retreatClickStarted);

            // Render Modify Deck button
            renderButton(sb, modifyDeckHb, "Modify Deck", modifyDeckClickStarted);

            // Render Rematch button
            renderButton(sb, tryAgainHb, "Rematch", tryAgainClickStarted);
        }

        private static void renderButton(SpriteBatch sb, Hitbox hb, String label, boolean clickStarted) {
            float x = hb.cX;
            float y = hb.cY;

            // Determine color based on state
            Color buttonColor;
            Color textColor;
            if (clickStarted) {
                buttonColor = new Color(0.5f, 0.5f, 0.5f, 1.0f);
                textColor = Settings.CREAM_COLOR;
            } else if (hb.hovered) {
                buttonColor = new Color(1.0f, 1.0f, 0.9f, 1.0f);
                textColor = Settings.GOLD_COLOR;
            } else {
                buttonColor = new Color(0.8f, 0.8f, 0.8f, 1.0f);
                textColor = Settings.CREAM_COLOR;
            }

            // Draw button background
            // Position offset is NOT scaled - scaling happens around the origin (center)
            sb.setColor(buttonColor);
            sb.draw(ImageMaster.DYNAMIC_BTN_IMG2,
                x - 256.0f, y - 256.0f,
                256.0f, 256.0f,
                512.0f, 512.0f,
                Settings.scale, Settings.scale,
                0.0f,
                0, 0, 512, 512,
                false, false);

            // Draw text shadow for better readability
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, label,
                x + 2.0f * Settings.scale, y - 2.0f * Settings.scale, Color.BLACK);

            // Draw button label
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, label, x, y, textColor);

            // Draw hitbox for debugging (optional)
            hb.render(sb);
        }
    }

    /**
     * Hide the default return button in arena mode by intercepting its render.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton", method = "render")
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
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.GameOverScreen", method = "renderStatsScreen")
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
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.DeathScreen", method = "updateStatsScreen")
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
