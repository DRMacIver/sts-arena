package stsarena.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.localization.LocalizedStrings;

/**
 * Screen displayed after an arena fight ends, showing victory/defeat status
 * and providing options to continue, retry, or modify deck.
 */
public class ArenaResultsScreen {

    // Layout constants
    private static final float TITLE_Y = Settings.HEIGHT * 0.65f;
    private static final float STATS_Y = Settings.HEIGHT * 0.50f;
    private static final float STATS_LINE_HEIGHT = 35.0f * Settings.scale;
    private static final float BUTTON_Y = Settings.HEIGHT * 0.22f;
    private static final float BUTTON_WIDTH = 240.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 160.0f * Settings.scale;
    private static final float BUTTON_SPACING = 260.0f * Settings.scale;

    // Button hitboxes
    private Hitbox continueHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private Hitbox modifyDeckHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private Hitbox retryHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Button click state
    private boolean continueClickStarted = false;
    private boolean modifyDeckClickStarted = false;
    private boolean retryClickStarted = false;

    // Screen state
    public boolean isOpen = false;
    private boolean isVictory = false;
    private boolean isImperfect = false;
    private int damageTaken = 0;
    private int damageDealt = 0;
    private int turnsTaken = 0;
    private int endingHp = 0;
    private int startingHp = 0;

    // Fade animation
    private float fadeAlpha = 0.0f;
    private static final float FADE_SPEED = 2.0f;

    // Auto-close timer for non-screenshot mode (perfect victories)
    private float autoCloseTimer = 0.0f;
    private static final float AUTO_CLOSE_DELAY = 2.0f; // seconds
    private boolean autoCloseEnabled = false;

    public ArenaResultsScreen() {
        // Position buttons
        float centerX = Settings.WIDTH / 2.0f;
        continueHb.move(centerX - BUTTON_SPACING, BUTTON_Y);
        modifyDeckHb.move(centerX, BUTTON_Y);
        retryHb.move(centerX + BUTTON_SPACING, BUTTON_Y);
    }

    /**
     * Open the results screen for a victory.
     */
    public void openVictory(boolean imperfect) {
        this.isVictory = true;
        this.isImperfect = imperfect;
        openInternal();
    }

    /**
     * Open the results screen for a defeat.
     */
    public void openDefeat() {
        this.isVictory = false;
        this.isImperfect = false;
        openInternal();
    }

    private void openInternal() {
        STSArena.logger.info("Opening ArenaResultsScreen - victory=" + isVictory + ", imperfect=" + isImperfect);
        this.isOpen = true;
        this.fadeAlpha = 0.0f;
        this.autoCloseTimer = 0.0f;

        // Auto-close is enabled for:
        // - Perfect victories (not imperfect) when NOT in screenshot mode
        // - All defeats when NOT in screenshot mode
        // Imperfect victories require user interaction (retry/modify options)
        // In screenshot mode, the screen stays open so it can be captured
        boolean shouldAutoClose = !STSArena.isScreenshotMode() && (!isVictory || !isImperfect);
        this.autoCloseEnabled = shouldAutoClose;

        // Capture stats
        if (ArenaRunner.getCurrentLoadout() != null) {
            this.startingHp = ArenaRunner.getCurrentLoadout().currentHp;
        } else if (AbstractDungeon.player != null) {
            this.startingHp = AbstractDungeon.player.maxHealth;
        }

        if (AbstractDungeon.player != null) {
            this.endingHp = isVictory ? AbstractDungeon.player.currentHealth : 0;
        }

        this.damageTaken = startingHp - endingHp;

        // Get combat stats
        if (AbstractDungeon.actionManager != null) {
            this.turnsTaken = AbstractDungeon.actionManager.turn;
        }

        // Calculate damage dealt
        this.damageDealt = 0;
        if (AbstractDungeon.getMonsters() != null) {
            for (com.megacrit.cardcrawl.monsters.AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
                if (isVictory) {
                    // All monsters dead, damage dealt = their max HP
                    this.damageDealt += m.maxHealth;
                } else {
                    // Partial damage dealt
                    this.damageDealt += (m.maxHealth - m.currentHealth);
                }
            }
        }

        // Reset button state
        continueClickStarted = false;
        modifyDeckClickStarted = false;
        retryClickStarted = false;
    }

    public void close() {
        this.isOpen = false;
    }

    public void update() {
        if (!isOpen) return;

        // Fade in animation
        if (fadeAlpha < 1.0f) {
            fadeAlpha += Gdx.graphics.getDeltaTime() * FADE_SPEED;
            if (fadeAlpha > 1.0f) fadeAlpha = 1.0f;
        }

        // Auto-close logic - check screenshot mode dynamically
        // Auto-close is enabled for perfect victories and defeats when NOT in screenshot mode
        // Imperfect victories always require user interaction
        boolean shouldAutoClose = !STSArena.isScreenshotMode() && (!isVictory || !isImperfect);
        if (shouldAutoClose && fadeAlpha >= 1.0f) {
            autoCloseTimer += Gdx.graphics.getDeltaTime();
            if (autoCloseTimer >= AUTO_CLOSE_DELAY) {
                STSArena.logger.info("ARENA: Auto-closing results screen");
                handleContinue();
                return;
            }
        }

        // Update hitboxes
        continueHb.update();
        modifyDeckHb.update();
        retryHb.update();

        // Handle hover sounds
        if (continueHb.justHovered || modifyDeckHb.justHovered || retryHb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        // Handle click start
        if (InputHelper.justClickedLeft) {
            if (continueHb.hovered) {
                continueHb.clickStarted = true;
                continueClickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (modifyDeckHb.hovered) {
                modifyDeckHb.clickStarted = true;
                modifyDeckClickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (retryHb.hovered) {
                retryHb.clickStarted = true;
                retryClickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            }
        }

        // Handle click release
        if (continueHb.clicked) {
            continueHb.clicked = false;
            continueHb.clickStarted = false;
            continueClickStarted = false;
            STSArena.logger.info("ARENA: Continue button clicked on results screen");
            handleContinue();
        }

        if (modifyDeckHb.clicked) {
            modifyDeckHb.clicked = false;
            modifyDeckHb.clickStarted = false;
            modifyDeckClickStarted = false;
            STSArena.logger.info("ARENA: Modify Deck button clicked on results screen");
            handleModifyDeck();
        }

        if (retryHb.clicked) {
            retryHb.clicked = false;
            retryHb.clickStarted = false;
            retryClickStarted = false;
            STSArena.logger.info("ARENA: Retry button clicked on results screen");
            handleRetry();
        }
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Draw semi-transparent background
        sb.setColor(new Color(0.0f, 0.0f, 0.0f, 0.8f * fadeAlpha));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Get localized strings
        String[] resultStrings = LocalizedStrings.getAllText(LocalizedStrings.RESULTS);
        String victoryText = resultStrings.length > 0 ? resultStrings[0] : "Victory!";
        String defeatText = resultStrings.length > 1 ? resultStrings[1] : "Defeat";
        String continueText = resultStrings.length > 2 ? resultStrings[2] : "Return to Menu";
        String retryText = resultStrings.length > 3 ? resultStrings[3] : "Retry";
        String modifyText = resultStrings.length > 4 ? resultStrings[4] : "Edit Loadout";

        // Get localized history strings for stats labels
        String[] historyStrings = LocalizedStrings.getAllText(LocalizedStrings.HISTORY);
        String damageDealtLabel = historyStrings.length > 7 ? historyStrings[7] : "Damage Dealt";
        String damageTakenLabel = historyStrings.length > 8 ? historyStrings[8] : "Damage Taken";
        String turnsLabel = historyStrings.length > 9 ? historyStrings[9] : "Turns";

        // Draw title
        String title = isVictory ? victoryText : defeatText;
        Color titleColor = isVictory ? Settings.GOLD_COLOR : Settings.RED_TEXT_COLOR;
        sb.setColor(new Color(titleColor.r, titleColor.g, titleColor.b, fadeAlpha));
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small, title,
            Settings.WIDTH / 2.0f, TITLE_Y, new Color(titleColor.r, titleColor.g, titleColor.b, fadeAlpha));

        // Draw imperfect victory message if applicable
        if (isVictory && isImperfect) {
            String imperfectMsg = "You took " + damageTaken + " damage. Try again for a perfect victory?";
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont, imperfectMsg,
                Settings.WIDTH / 2.0f, TITLE_Y - 50.0f * Settings.scale,
                new Color(Settings.CREAM_COLOR.r, Settings.CREAM_COLOR.g, Settings.CREAM_COLOR.b, fadeAlpha));
        }

        // Draw stats
        float statsX = Settings.WIDTH / 2.0f;
        float statsY = STATS_Y;
        Color statsColor = new Color(Settings.CREAM_COLOR.r, Settings.CREAM_COLOR.g, Settings.CREAM_COLOR.b, fadeAlpha);

        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            damageDealtLabel + ": " + damageDealt,
            statsX, statsY, statsColor);

        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            damageTakenLabel + ": " + damageTaken,
            statsX, statsY - STATS_LINE_HEIGHT, statsColor);

        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            turnsLabel + ": " + turnsTaken,
            statsX, statsY - STATS_LINE_HEIGHT * 2, statsColor);

        if (isVictory) {
            FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
                "HP: " + endingHp + "/" + startingHp,
                statsX, statsY - STATS_LINE_HEIGHT * 3, statsColor);
        }

        // Draw buttons with fade
        renderButton(sb, continueHb, continueText, continueClickStarted, fadeAlpha);
        renderButton(sb, modifyDeckHb, modifyText, modifyDeckClickStarted, fadeAlpha);
        renderButton(sb, retryHb, retryText, retryClickStarted, fadeAlpha);
    }

    private void renderButton(SpriteBatch sb, Hitbox hb, String label, boolean clickStarted, float alpha) {
        float x = hb.cX;
        float y = hb.cY;

        // Determine color based on state
        Color buttonColor;
        Color textColor;
        if (clickStarted) {
            buttonColor = new Color(0.5f, 0.5f, 0.5f, alpha);
            textColor = new Color(Settings.CREAM_COLOR.r, Settings.CREAM_COLOR.g, Settings.CREAM_COLOR.b, alpha);
        } else if (hb.hovered) {
            buttonColor = new Color(1.0f, 1.0f, 0.9f, alpha);
            textColor = new Color(Settings.GOLD_COLOR.r, Settings.GOLD_COLOR.g, Settings.GOLD_COLOR.b, alpha);
        } else {
            buttonColor = new Color(0.8f, 0.8f, 0.8f, alpha);
            textColor = new Color(Settings.CREAM_COLOR.r, Settings.CREAM_COLOR.g, Settings.CREAM_COLOR.b, alpha);
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

        // Draw text shadow for better readability
        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, label,
            x + 2.0f * Settings.scale, y - 2.0f * Settings.scale,
            new Color(0.0f, 0.0f, 0.0f, alpha));

        // Draw button label
        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, label, x, y, textColor);

        // Draw hitbox for debugging
        hb.render(sb);
    }

    private void handleContinue() {
        close();

        // Return to encounter selection via main menu
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;
        STSArena.setReturnToArenaOnMainMenu();
        ArenaRunner.setArenaRunInProgress(false);
        CardCrawlGame.startOver();
    }

    private void handleModifyDeck() {
        close();

        // Open the deck editor for retry
        ArenaRunner.modifyDeckAndRetry();
    }

    private void handleRetry() {
        close();

        // Restart the current fight
        ArenaRunner.restartCurrentFight();
    }
}
