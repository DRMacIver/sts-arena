package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Screen showing arena fight history and statistics.
 */
public class ArenaHistoryScreen {

    private static final float TITLE_Y = Settings.HEIGHT - 100.0f * Settings.scale;
    private static final float STATS_Y = TITLE_Y - 80.0f * Settings.scale;
    private static final float HISTORY_START_Y = STATS_Y - 100.0f * Settings.scale;
    private static final float ROW_HEIGHT = 50.0f * Settings.scale;
    private static final float TABLE_WIDTH = 1150.0f * Settings.scale;
    private static final float LEFT_X = (Settings.WIDTH - TABLE_WIDTH) / 2.0f;
    private static final float REPLAY_BUTTON_WIDTH = 85.0f * Settings.scale;
    private static final float REPLAY_BUTTON_HEIGHT = 32.0f * Settings.scale;
    private static final float BORDER_WIDTH = 2.0f * Settings.scale;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    private List<ArenaRepository.ArenaRunRecord> recentRuns;
    private ArenaRepository.ArenaStats stats;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm");

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // Replay button hitboxes (one per row)
    private Hitbox[] replayHitboxes;

    public ArenaHistoryScreen() {
        this.cancelButton = new MenuCancelButton();
    }

    public void open() {
        STSArena.logger.info("Opening Arena History Screen");
        this.isOpen = true;
        this.cancelButton.show("Return");

        // Load data from database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            this.recentRuns = repo.getRecentRuns(50);
            this.stats = repo.getStats();
            STSArena.logger.info("Loaded " + recentRuns.size() + " arena runs");

            // Create hitboxes for replay buttons
            replayHitboxes = new Hitbox[recentRuns.size()];
            for (int i = 0; i < recentRuns.size(); i++) {
                replayHitboxes[i] = new Hitbox(REPLAY_BUTTON_WIDTH, REPLAY_BUTTON_HEIGHT);
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to load arena history", e);
            this.recentRuns = null;
            this.stats = null;
            this.replayHitboxes = new Hitbox[0];
        }

        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    public void update() {
        if (!isOpen) return;

        // Cancel button - update first so it can receive input
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            this.close();
            return;
        }

        // Scrolling
        if (InputHelper.scrolledDown) {
            targetScrollY += Settings.SCROLL_SPEED;
        } else if (InputHelper.scrolledUp) {
            targetScrollY -= Settings.SCROLL_SPEED;
        }

        // Clamp scroll
        float maxScroll = Math.max(0, (recentRuns != null ? recentRuns.size() : 0) * ROW_HEIGHT - 400.0f * Settings.scale);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > maxScroll) targetScrollY = maxScroll;

        scrollY = MathHelper.scrollSnapLerpSpeed(scrollY, targetScrollY);

        // Update replay hitboxes and check for clicks
        if (recentRuns != null && replayHitboxes != null) {
            float replayX = LEFT_X + 1000.0f * Settings.scale + REPLAY_BUTTON_WIDTH / 2.0f;
            float y = HISTORY_START_Y - scrollY;

            for (int i = 0; i < recentRuns.size(); i++) {
                float rowY = y - i * ROW_HEIGHT;

                // Only update visible hitboxes
                if (rowY > 0 && rowY < Settings.HEIGHT - 100.0f * Settings.scale) {
                    // Center button vertically in the row
                    replayHitboxes[i].move(replayX, rowY - 15.0f * Settings.scale);
                    replayHitboxes[i].update();

                    if (replayHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        replayRun(recentRuns.get(i));
                        return;
                    }
                }
            }
        }
    }

    private void replayRun(ArenaRepository.ArenaRunRecord run) {
        STSArena.logger.info("Replaying run: " + run.loadoutName + " vs " + run.encounterId);

        // Load the loadout from database
        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        ArenaRepository.LoadoutRecord loadout = repo.getLoadoutById(run.loadoutId);

        if (loadout == null) {
            STSArena.logger.error("Failed to load loadout for replay: " + run.loadoutId);
            return;
        }

        this.close();
        ArenaRunner.startFightWithSavedLoadout(loadout, run.encounterId);
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.8f));
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Arena History",
            Settings.WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Stats summary
        if (stats != null) {
            String statsText = String.format("Total Runs: %d  |  Wins: %d  |  Losses: %d  |  Win Rate: %.1f%%",
                stats.totalRuns, stats.wins, stats.losses, stats.getWinRate() * 100);
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                statsText,
                Settings.WIDTH / 2.0f, STATS_Y, Settings.CREAM_COLOR);
        }

        // Column headers - adjusted spacing to fit replay button
        float headerY = HISTORY_START_Y + 30.0f * Settings.scale;
        float col1 = LEFT_X;                           // Loadout name
        float col2 = LEFT_X + 320.0f * Settings.scale; // Encounter
        float col3 = LEFT_X + 620.0f * Settings.scale; // Outcome
        float col4 = LEFT_X + 780.0f * Settings.scale; // Date
        float col5 = LEFT_X + 1000.0f * Settings.scale; // Replay

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Loadout", col1, headerY, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Encounter", col2, headerY, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Outcome", col3, headerY, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Date", col4, headerY, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Action", col5, headerY, Settings.GOLD_COLOR);

        // History rows
        if (recentRuns != null && !recentRuns.isEmpty()) {
            float y = HISTORY_START_Y - scrollY;
            for (int i = 0; i < recentRuns.size(); i++) {
                ArenaRepository.ArenaRunRecord run = recentRuns.get(i);
                // Only render visible rows
                if (y > 0 && y < Settings.HEIGHT - 100.0f * Settings.scale) {
                    renderRunRow(sb, run, y, replayHitboxes[i]);
                }
                y -= ROW_HEIGHT;
            }
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                "No arena fights recorded yet. Start an arena fight to see history!",
                Settings.WIDTH / 2.0f, HISTORY_START_Y - 50.0f * Settings.scale, Settings.CREAM_COLOR);
        }

        // Cancel button
        this.cancelButton.render(sb);
    }

    private void renderRunRow(SpriteBatch sb, ArenaRepository.ArenaRunRecord run, float y, Hitbox replayHb) {
        // Column positions - must match header positions
        float col1 = LEFT_X;
        float col2 = LEFT_X + 320.0f * Settings.scale;
        float col3 = LEFT_X + 620.0f * Settings.scale;
        float col4 = LEFT_X + 780.0f * Settings.scale;
        float col5 = LEFT_X + 1000.0f * Settings.scale;

        Color textColor = Settings.CREAM_COLOR;

        // Outcome color
        Color outcomeColor = Settings.CREAM_COLOR;
        String outcomeText = run.outcome != null ? run.outcome : "IN PROGRESS";
        if ("VICTORY".equals(run.outcome)) {
            outcomeColor = Settings.GREEN_TEXT_COLOR;
        } else if ("DEFEAT".equals(run.outcome)) {
            outcomeColor = Settings.RED_TEXT_COLOR;
        }

        // Show loadout name with wrapping for long names
        String loadoutName = run.loadoutName != null ? run.loadoutName : "Unknown";
        float loadoutColWidth = 300.0f * Settings.scale;
        FontHelper.renderSmartText(sb, FontHelper.cardDescFont_N,
            loadoutName, col1, y, loadoutColWidth, 20.0f * Settings.scale, textColor);

        // Encounter
        String encounter = run.encounterId != null ? run.encounterId : "Unknown";
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            encounter, col2, y, textColor);

        // Outcome
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            outcomeText, col3, y, outcomeColor);

        // Date
        String dateText = dateFormat.format(new Date(run.startedAt));
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            dateText, col4, y, textColor);

        // Replay button - centered vertically in row
        float buttonX = col5;
        float buttonY = y - REPLAY_BUTTON_HEIGHT - 1.0f * Settings.scale;
        float buttonCenterY = buttonY + REPLAY_BUTTON_HEIGHT / 2.0f;

        // Button border (draw slightly larger rectangle first)
        Color borderColor = replayHb.hovered ? Settings.GREEN_TEXT_COLOR : new Color(0.5f, 0.6f, 0.5f, 0.8f);
        sb.setColor(borderColor);
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG,
            buttonX - BORDER_WIDTH, buttonY - BORDER_WIDTH,
            REPLAY_BUTTON_WIDTH + BORDER_WIDTH * 2, REPLAY_BUTTON_HEIGHT + BORDER_WIDTH * 2);

        // Button background
        Color buttonBg = replayHb.hovered ? new Color(0.2f, 0.35f, 0.2f, 1.0f) : new Color(0.1f, 0.15f, 0.1f, 0.9f);
        sb.setColor(buttonBg);
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG,
            buttonX, buttonY,
            REPLAY_BUTTON_WIDTH, REPLAY_BUTTON_HEIGHT);

        // Button text
        Color buttonTextColor = replayHb.hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR;
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Replay",
            buttonX + REPLAY_BUTTON_WIDTH / 2.0f, buttonCenterY, buttonTextColor);
    }
}
