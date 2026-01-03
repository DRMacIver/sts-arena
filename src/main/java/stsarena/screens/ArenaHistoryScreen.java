package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    // Loadout name hitboxes for tooltips (one per row)
    private Hitbox[] loadoutNameHitboxes;
    private static final float LOADOUT_COL_WIDTH = 300.0f * Settings.scale;
    private static final int TRUNCATE_LENGTH = 25;

    // Tooltip state
    private String hoveredLoadoutName = null;

    // Filter by loadout (null = show all)
    private Long filterLoadoutId = null;
    private String filterLoadoutName = null;

    // Sorting state
    private enum SortColumn { LOADOUT, ENCOUNTER, OUTCOME, DATE }
    private SortColumn sortColumn = SortColumn.DATE;
    private boolean sortAscending = false; // Most recent first by default

    // Column header hitboxes for sorting
    private static final float HEADER_HB_HEIGHT = 30.0f * Settings.scale;
    private Hitbox loadoutHeaderHb = new Hitbox(300.0f * Settings.scale, HEADER_HB_HEIGHT);
    private Hitbox encounterHeaderHb = new Hitbox(280.0f * Settings.scale, HEADER_HB_HEIGHT);
    private Hitbox outcomeHeaderHb = new Hitbox(140.0f * Settings.scale, HEADER_HB_HEIGHT);
    private Hitbox dateHeaderHb = new Hitbox(200.0f * Settings.scale, HEADER_HB_HEIGHT);

    public ArenaHistoryScreen() {
        this.cancelButton = new MenuCancelButton();
    }

    public void open() {
        // Clear any filter when opening normally
        this.filterLoadoutId = null;
        this.filterLoadoutName = null;
        openInternal();
    }

    /**
     * Open the history screen filtered to a specific loadout.
     */
    public void openForLoadout(long loadoutId, String loadoutName) {
        this.filterLoadoutId = loadoutId;
        this.filterLoadoutName = loadoutName;
        openInternal();
    }

    private void openInternal() {
        STSArena.logger.info("Opening Arena History Screen" +
            (filterLoadoutId != null ? " for loadout: " + filterLoadoutName : ""));
        this.isOpen = true;
        this.cancelButton.show("Return");

        // Load data from database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());

            if (filterLoadoutId != null) {
                // Filtered mode - show runs for specific loadout
                this.recentRuns = repo.getRunsForLoadout(filterLoadoutId, 50);
                this.stats = null;  // Don't show overall stats in filtered mode
            } else {
                // Normal mode - show all runs
                this.recentRuns = repo.getRecentRuns(50);
                this.stats = repo.getStats();
            }

            STSArena.logger.info("Loaded " + recentRuns.size() + " arena runs");

            // Sort the runs
            sortRuns();

            // Create hitboxes for replay buttons and loadout names
            replayHitboxes = new Hitbox[recentRuns.size()];
            loadoutNameHitboxes = new Hitbox[recentRuns.size()];
            for (int i = 0; i < recentRuns.size(); i++) {
                replayHitboxes[i] = new Hitbox(REPLAY_BUTTON_WIDTH, REPLAY_BUTTON_HEIGHT);
                loadoutNameHitboxes[i] = new Hitbox(LOADOUT_COL_WIDTH, ROW_HEIGHT * 0.6f);
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to load arena history", e);
            this.recentRuns = null;
            this.stats = null;
            this.replayHitboxes = new Hitbox[0];
            this.loadoutNameHitboxes = new Hitbox[0];
        }

        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    /**
     * Sort the runs list based on current sort column and direction.
     */
    private void sortRuns() {
        if (recentRuns == null || recentRuns.isEmpty()) return;

        Comparator<ArenaRepository.ArenaRunRecord> comparator;
        switch (sortColumn) {
            case LOADOUT:
                comparator = (a, b) -> {
                    String nameA = a.loadoutName != null ? a.loadoutName : "";
                    String nameB = b.loadoutName != null ? b.loadoutName : "";
                    return nameA.compareToIgnoreCase(nameB);
                };
                break;
            case ENCOUNTER:
                comparator = (a, b) -> {
                    String encA = a.encounterId != null ? a.encounterId : "";
                    String encB = b.encounterId != null ? b.encounterId : "";
                    return encA.compareToIgnoreCase(encB);
                };
                break;
            case OUTCOME:
                comparator = (a, b) -> {
                    String outA = a.outcome != null ? a.outcome : "";
                    String outB = b.outcome != null ? b.outcome : "";
                    return outA.compareTo(outB);
                };
                break;
            case DATE:
            default:
                comparator = (a, b) -> Long.compare(a.startedAt, b.startedAt);
                break;
        }

        if (!sortAscending) {
            comparator = comparator.reversed();
        }

        Collections.sort(recentRuns, comparator);
    }

    /**
     * Handle clicking on a column header to change sort.
     */
    private void handleHeaderClick(SortColumn column) {
        if (sortColumn == column) {
            // Toggle direction
            sortAscending = !sortAscending;
        } else {
            // New column, reset to descending (or ascending for text columns)
            sortColumn = column;
            sortAscending = (column == SortColumn.LOADOUT || column == SortColumn.ENCOUNTER);
        }
        sortRuns();
        // Reset scroll to top when sorting changes
        scrollY = 0;
        targetScrollY = 0;
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

        // Update column header hitboxes for sorting
        // Positions must match render positions - hitbox X is left edge, not center
        float headerY = HISTORY_START_Y + 30.0f * Settings.scale - HEADER_HB_HEIGHT / 2.0f;
        float col1 = LEFT_X;                           // Loadout
        float col2 = LEFT_X + 320.0f * Settings.scale; // Encounter
        float col3 = LEFT_X + 620.0f * Settings.scale; // Outcome
        float col4 = LEFT_X + 780.0f * Settings.scale; // Date

        // Move hitboxes - use left edge + half width for center
        loadoutHeaderHb.move(col1 + loadoutHeaderHb.width / 2.0f, headerY);
        encounterHeaderHb.move(col2 + encounterHeaderHb.width / 2.0f, headerY);
        outcomeHeaderHb.move(col3 + outcomeHeaderHb.width / 2.0f, headerY);
        dateHeaderHb.move(col4 + dateHeaderHb.width / 2.0f, headerY);

        loadoutHeaderHb.update();
        encounterHeaderHb.update();
        outcomeHeaderHb.update();
        dateHeaderHb.update();

        // Check for header clicks
        if (InputHelper.justClickedLeft) {
            if (loadoutHeaderHb.hovered) {
                handleHeaderClick(SortColumn.LOADOUT);
                return;
            } else if (encounterHeaderHb.hovered) {
                handleHeaderClick(SortColumn.ENCOUNTER);
                return;
            } else if (outcomeHeaderHb.hovered) {
                handleHeaderClick(SortColumn.OUTCOME);
                return;
            } else if (dateHeaderHb.hovered) {
                handleHeaderClick(SortColumn.DATE);
                return;
            }
        }

        // Update replay hitboxes and loadout name hitboxes
        hoveredLoadoutName = null; // Reset tooltip state
        if (recentRuns != null && replayHitboxes != null && loadoutNameHitboxes != null) {
            float replayX = LEFT_X + 1000.0f * Settings.scale + REPLAY_BUTTON_WIDTH / 2.0f;
            float loadoutX = LEFT_X + LOADOUT_COL_WIDTH / 2.0f;
            float y = HISTORY_START_Y - scrollY;

            for (int i = 0; i < recentRuns.size(); i++) {
                float rowY = y - i * ROW_HEIGHT;

                // Only update visible hitboxes
                if (rowY > 0 && rowY < Settings.HEIGHT - 100.0f * Settings.scale) {
                    // Replay button
                    replayHitboxes[i].move(replayX, rowY - 15.0f * Settings.scale);
                    replayHitboxes[i].update();

                    if (replayHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        replayRun(recentRuns.get(i));
                        return;
                    }

                    // Loadout name hitbox for tooltip
                    loadoutNameHitboxes[i].move(loadoutX, rowY - 15.0f * Settings.scale);
                    loadoutNameHitboxes[i].update();

                    // Check if hovering over a truncated name
                    if (loadoutNameHitboxes[i].hovered) {
                        String name = recentRuns.get(i).loadoutName;
                        if (name != null && name.length() > TRUNCATE_LENGTH) {
                            hoveredLoadoutName = name;
                        }
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
        sb.setColor(Color.WHITE); // Reset color for subsequent rendering

        // Title
        String title = filterLoadoutName != null ?
            "History: " + filterLoadoutName : "Arena History";
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            title,
            Settings.WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Stats summary (only in unfiltered mode)
        if (stats != null && filterLoadoutId == null) {
            String statsText = String.format("Total Runs: %d  |  Wins: %d  |  Losses: %d  |  Win Rate: %.1f%%",
                stats.totalRuns, stats.wins, stats.losses, stats.getWinRate() * 100);
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                statsText,
                Settings.WIDTH / 2.0f, STATS_Y, Settings.CREAM_COLOR);
        } else if (filterLoadoutId != null && recentRuns != null) {
            // Show simple count in filtered mode
            int wins = 0, losses = 0;
            for (ArenaRepository.ArenaRunRecord run : recentRuns) {
                if ("VICTORY".equals(run.outcome)) wins++;
                else if ("DEFEAT".equals(run.outcome)) losses++;
            }
            String statsText = String.format("Runs: %d  |  Wins: %d  |  Losses: %d",
                recentRuns.size(), wins, losses);
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

        // Render sortable column headers with indicators
        renderSortableHeader(sb, "Loadout", col1, headerY, SortColumn.LOADOUT, loadoutHeaderHb);
        renderSortableHeader(sb, "Encounter", col2, headerY, SortColumn.ENCOUNTER, encounterHeaderHb);
        renderSortableHeader(sb, "Outcome", col3, headerY, SortColumn.OUTCOME, outcomeHeaderHb);
        renderSortableHeader(sb, "Date", col4, headerY, SortColumn.DATE, dateHeaderHb);
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

        // Render tooltip for truncated loadout names (must be last to appear on top)
        if (hoveredLoadoutName != null) {
            TipHelper.renderGenericTip(InputHelper.mX + 20.0f * Settings.scale,
                InputHelper.mY - 20.0f * Settings.scale,
                "Loadout Name", hoveredLoadoutName);
        }
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

        // Show loadout name - truncate if too long instead of wrapping
        String loadoutName = run.loadoutName != null ? run.loadoutName : "Unknown";
        // Simple truncation - use constant to match tooltip logic
        String displayName = loadoutName;
        if (loadoutName.length() > TRUNCATE_LENGTH) {
            displayName = loadoutName.substring(0, TRUNCATE_LENGTH - 3) + "...";
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            displayName, col1, y, textColor);

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

    /**
     * Render a sortable column header with sort indicator.
     */
    private void renderSortableHeader(SpriteBatch sb, String label, float x, float y,
                                       SortColumn column, Hitbox hb) {
        // Determine color based on state
        Color headerColor;
        if (sortColumn == column) {
            headerColor = Settings.GREEN_TEXT_COLOR; // Highlight active sort column
        } else if (hb.hovered) {
            headerColor = Color.WHITE;
        } else {
            headerColor = Settings.GOLD_COLOR;
        }

        // Build label with sort indicator
        String displayLabel = label;
        if (sortColumn == column) {
            displayLabel = label + (sortAscending ? " ^" : " v");
        }

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            displayLabel, x, y, headerColor);

        // Render hitbox for debugging (comment out in production)
        // hb.render(sb);
    }
}
