package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing loadout+encounter statistics with Pareto-best victories.
 */
public class ArenaStatsScreen {

    private static final float TITLE_Y = Settings.HEIGHT - 80.0f * Settings.scale;
    private static final float HEADER_Y = TITLE_Y - 70.0f * Settings.scale;
    private static final float LIST_START_Y = HEADER_Y - 40.0f * Settings.scale;
    private static final float ROW_HEIGHT = 36.0f * Settings.scale;
    private static final float PARETO_ROW_HEIGHT = 28.0f * Settings.scale;
    private static final float TABLE_WIDTH = 1100.0f * Settings.scale;
    private static final float LEFT_X = (Settings.WIDTH - TABLE_WIDTH) / 2.0f;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Data
    private List<ArenaRepository.LoadoutEncounterStats> allStats;
    private ArenaRepository repo;

    // Expanded rows (to show Pareto-best victories)
    private List<Integer> expandedRows = new ArrayList<>();
    private List<List<ArenaRepository.VictoryRecord>> paretoVictories = new ArrayList<>();

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // Hitboxes for expand buttons
    private Hitbox[] expandHitboxes;

    public ArenaStatsScreen() {
        this.cancelButton = new MenuCancelButton();
    }

    public void open() {
        STSArena.logger.info("Opening Arena Stats Screen");
        this.isOpen = true;
        this.cancelButton.show("Return");
        this.expandedRows.clear();
        this.paretoVictories.clear();

        // Load data
        try {
            repo = new ArenaRepository(ArenaDatabase.getInstance());
            allStats = repo.getLoadoutEncounterStats();
            STSArena.logger.info("Loaded " + allStats.size() + " loadout+encounter combinations");

            // Initialize Pareto victories list (empty until expanded)
            for (int i = 0; i < allStats.size(); i++) {
                paretoVictories.add(null);
            }

            // Create hitboxes
            expandHitboxes = new Hitbox[allStats.size()];
            for (int i = 0; i < allStats.size(); i++) {
                expandHitboxes[i] = new Hitbox(30.0f * Settings.scale, 30.0f * Settings.scale);
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to load stats", e);
            allStats = new ArrayList<>();
            expandHitboxes = new Hitbox[0];
        }

        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    /**
     * Calculate Pareto-best victories for a loadout+encounter combination.
     */
    private List<ArenaRepository.VictoryRecord> calculateParetoVictories(long loadoutId, String encounterId) {
        List<ArenaRepository.VictoryRecord> allVictories = repo.getVictoriesForLoadoutEncounter(loadoutId, encounterId);
        List<ArenaRepository.VictoryRecord> paretoOptimal = new ArrayList<>();

        for (ArenaRepository.VictoryRecord candidate : allVictories) {
            boolean isDominated = false;
            for (ArenaRepository.VictoryRecord other : allVictories) {
                if (candidate != other && candidate.isDominatedBy(other)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                paretoOptimal.add(candidate);
            }
        }

        return paretoOptimal;
    }

    public void update() {
        if (!isOpen) return;

        // Cancel button
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

        // Calculate total content height
        float totalHeight = calculateTotalHeight();
        float maxScroll = Math.max(0, totalHeight - 500.0f * Settings.scale);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > maxScroll) targetScrollY = maxScroll;

        scrollY = MathHelper.scrollSnapLerpSpeed(scrollY, targetScrollY);

        // Update expand hitboxes
        if (allStats != null && expandHitboxes != null) {
            float y = LIST_START_Y + scrollY;
            int rowIndex = 0;

            for (int i = 0; i < allStats.size(); i++) {
                float rowY = y - rowIndex * ROW_HEIGHT;
                rowIndex++;

                // Account for expanded Pareto rows
                if (expandedRows.contains(i) && paretoVictories.get(i) != null) {
                    rowIndex += paretoVictories.get(i).size();
                }

                // Update hitbox position
                if (rowY > 0 && rowY < Settings.HEIGHT - 100.0f * Settings.scale) {
                    float expandX = LEFT_X + 15.0f * Settings.scale;
                    expandHitboxes[i].move(expandX, rowY - ROW_HEIGHT / 2.0f);
                    expandHitboxes[i].update();

                    if (expandHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        toggleExpand(i);
                        InputHelper.justClickedLeft = false;
                    }
                }
            }
        }
    }

    private void toggleExpand(int index) {
        if (expandedRows.contains(index)) {
            expandedRows.remove(Integer.valueOf(index));
        } else {
            // Load Pareto victories if not already loaded
            if (paretoVictories.get(index) == null) {
                ArenaRepository.LoadoutEncounterStats stats = allStats.get(index);
                paretoVictories.set(index, calculateParetoVictories(stats.loadoutId, stats.encounterId));
            }
            expandedRows.add(index);
        }
    }

    private float calculateTotalHeight() {
        if (allStats == null) return 0;

        float height = allStats.size() * ROW_HEIGHT;

        for (int i : expandedRows) {
            if (i < paretoVictories.size() && paretoVictories.get(i) != null) {
                height += paretoVictories.get(i).size() * PARETO_ROW_HEIGHT;
            }
        }

        return height;
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.85f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);
        sb.setColor(Color.WHITE);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Loadout + Encounter Statistics",
            Settings.WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Subtitle
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Click [+] to see your best victories (least damage taken or fewest potions used)",
            Settings.WIDTH / 2.0f, TITLE_Y - 30.0f * Settings.scale, Settings.CREAM_COLOR);

        // Column headers
        float col0 = LEFT_X;                           // Expand button
        float col1 = LEFT_X + 40.0f * Settings.scale;  // Loadout
        float col2 = LEFT_X + 340.0f * Settings.scale; // Encounter
        float col3 = LEFT_X + 620.0f * Settings.scale; // Runs
        float col4 = LEFT_X + 700.0f * Settings.scale; // Wins
        float col5 = LEFT_X + 780.0f * Settings.scale; // Losses
        float col6 = LEFT_X + 860.0f * Settings.scale; // Win Rate

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "", col0, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Loadout", col1, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Encounter", col2, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Runs", col3, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Wins", col4, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Losses", col5, HEADER_Y, Settings.GOLD_COLOR);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, "Win Rate", col6, HEADER_Y, Settings.GOLD_COLOR);

        // Stats rows
        if (allStats != null && !allStats.isEmpty()) {
            float y = LIST_START_Y + scrollY;

            for (int i = 0; i < allStats.size(); i++) {
                ArenaRepository.LoadoutEncounterStats stats = allStats.get(i);
                float rowY = y;

                // Only render visible rows
                if (rowY > 0 && rowY < Settings.HEIGHT - 50.0f * Settings.scale) {
                    renderStatsRow(sb, stats, rowY, i);
                }

                y -= ROW_HEIGHT;

                // Render expanded Pareto victories
                if (expandedRows.contains(i) && paretoVictories.get(i) != null) {
                    for (ArenaRepository.VictoryRecord victory : paretoVictories.get(i)) {
                        if (y > 0 && y < Settings.HEIGHT - 50.0f * Settings.scale) {
                            renderParetoRow(sb, victory, y);
                        }
                        y -= PARETO_ROW_HEIGHT;
                    }
                }
            }
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                "No arena statistics yet. Fight some battles to see stats!",
                Settings.WIDTH / 2.0f, LIST_START_Y - 50.0f * Settings.scale, Settings.CREAM_COLOR);
        }

        // Cancel button
        this.cancelButton.render(sb);
    }

    private void renderStatsRow(SpriteBatch sb, ArenaRepository.LoadoutEncounterStats stats, float y, int index) {
        float col0 = LEFT_X;
        float col1 = LEFT_X + 40.0f * Settings.scale;
        float col2 = LEFT_X + 340.0f * Settings.scale;
        float col3 = LEFT_X + 620.0f * Settings.scale;
        float col4 = LEFT_X + 700.0f * Settings.scale;
        float col5 = LEFT_X + 780.0f * Settings.scale;
        float col6 = LEFT_X + 860.0f * Settings.scale;

        // Row background
        sb.setColor(new Color(0.1f, 0.1f, 0.15f, 0.5f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, LEFT_X, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT - 2);

        // Expand button (only if there are wins)
        if (stats.wins > 0) {
            boolean isExpanded = expandedRows.contains(index);
            boolean isHovered = expandHitboxes[index] != null && expandHitboxes[index].hovered;

            Color expandColor = isHovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR;
            String expandText = isExpanded ? "[-]" : "[+]";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, expandText, col0, y - 8.0f * Settings.scale, expandColor);
        }

        // Loadout name
        String loadoutName = stats.loadoutName;
        if (loadoutName.length() > 28) {
            loadoutName = loadoutName.substring(0, 25) + "...";
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, loadoutName, col1, y - 8.0f * Settings.scale, Settings.CREAM_COLOR);

        // Encounter
        String encounter = stats.encounterId;
        if (encounter.length() > 25) {
            encounter = encounter.substring(0, 22) + "...";
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, encounter, col2, y - 8.0f * Settings.scale, Settings.CREAM_COLOR);

        // Runs
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            String.valueOf(stats.totalRuns), col3, y - 8.0f * Settings.scale, Settings.CREAM_COLOR);

        // Wins
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            String.valueOf(stats.wins), col4, y - 8.0f * Settings.scale, Settings.GREEN_TEXT_COLOR);

        // Losses
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            String.valueOf(stats.losses), col5, y - 8.0f * Settings.scale, Settings.RED_TEXT_COLOR);

        // Win rate
        Color winRateColor = stats.getWinRate() >= 0.5 ? Settings.GREEN_TEXT_COLOR : Settings.RED_TEXT_COLOR;
        if (stats.totalRuns == 0) winRateColor = Settings.CREAM_COLOR;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            String.format("%.0f%%", stats.getWinRate() * 100), col6, y - 8.0f * Settings.scale, winRateColor);
    }

    private void renderParetoRow(SpriteBatch sb, ArenaRepository.VictoryRecord victory, float y) {
        // Indented row for Pareto victory details
        float indent = LEFT_X + 60.0f * Settings.scale;

        // Subtle background
        sb.setColor(new Color(0.08f, 0.15f, 0.08f, 0.4f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, LEFT_X + 30.0f * Settings.scale, y - PARETO_ROW_HEIGHT,
            TABLE_WIDTH - 30.0f * Settings.scale, PARETO_ROW_HEIGHT - 2);

        // Build description of this victory
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("%d dmg taken", victory.damageTaken));
        desc.append(String.format("  |  %d potions", victory.potionsUsed));
        desc.append(String.format("  |  %d/%d HP remaining", victory.endingHp, victory.startingHp));

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            desc.toString(), indent, y - 6.0f * Settings.scale, new Color(0.7f, 0.9f, 0.7f, 1.0f));
    }
}
