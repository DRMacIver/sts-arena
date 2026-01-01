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
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for selecting which loadout to use in arena mode.
 * This is the first screen shown when entering arena mode.
 */
public class ArenaLoadoutSelectScreen {

    private static final float TITLE_Y = Settings.HEIGHT - 100.0f * Settings.scale;
    private static final float LIST_START_Y = TITLE_Y - 100.0f * Settings.scale;
    private static final float ROW_HEIGHT = 45.0f * Settings.scale;
    private static final float BUTTON_WIDTH = 500.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 40.0f * Settings.scale;
    private static final float CENTER_X = Settings.WIDTH / 2.0f;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // List items
    private List<ListItem> items;
    private Hitbox[] hitboxes;

    // Selection state - either a new random loadout or a saved one
    public static boolean useNewRandomLoadout = false;
    public static ArenaRepository.LoadoutRecord selectedSavedLoadout = null;

    private static class ListItem {
        String text;
        boolean isNewRandom;
        boolean isHeader;
        ArenaRepository.LoadoutRecord savedLoadout;

        ListItem(String text, boolean isNewRandom, boolean isHeader, ArenaRepository.LoadoutRecord savedLoadout) {
            this.text = text;
            this.isNewRandom = isNewRandom;
            this.isHeader = isHeader;
            this.savedLoadout = savedLoadout;
        }
    }

    public ArenaLoadoutSelectScreen() {
        this.cancelButton = new MenuCancelButton();
        this.items = new ArrayList<>();
        this.hitboxes = new Hitbox[0];
    }

    public void open() {
        STSArena.logger.info("Opening Arena Loadout Select Screen");
        this.isOpen = true;
        this.cancelButton.show("Return");
        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;

        // Reset selection state
        useNewRandomLoadout = false;
        selectedSavedLoadout = null;

        // Build the item list
        buildItemList();

        // Create hitboxes
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
        }
    }

    private void buildItemList() {
        items.clear();

        // New random loadout option
        items.add(new ListItem("New Random Loadout", true, false, null));

        // Load saved loadouts from database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            List<ArenaRepository.LoadoutRecord> loadouts = repo.getLoadouts(50);

            if (!loadouts.isEmpty()) {
                // Add header
                items.add(new ListItem("--- Saved Loadouts ---", false, true, null));

                for (ArenaRepository.LoadoutRecord loadout : loadouts) {
                    String label = loadout.name + " (" + loadout.characterClass + ", A" + loadout.ascensionLevel + ")";
                    items.add(new ListItem(label, false, false, loadout));
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to load saved loadouts", e);
        }
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    public void update() {
        if (!isOpen) return;

        // Cancel button - return to main menu
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
        float maxScroll = Math.max(0, items.size() * ROW_HEIGHT - 500.0f * Settings.scale);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > maxScroll) targetScrollY = maxScroll;

        scrollY = MathHelper.scrollSnapLerpSpeed(scrollY, targetScrollY);

        // Update hitboxes and check for clicks
        float y = LIST_START_Y + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Skip header items
            if (item.isHeader) continue;

            // Only update hitboxes that are visible
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT) {
                hitboxes[i].move(CENTER_X, buttonY - BUTTON_HEIGHT / 2.0f);
                hitboxes[i].update();

                if (hitboxes[i].hovered && InputHelper.justClickedLeft) {
                    selectLoadout(item);
                    return;
                }
            }
        }
    }

    private void selectLoadout(ListItem item) {
        if (item.isNewRandom) {
            STSArena.logger.info("New random loadout selected");
            useNewRandomLoadout = true;
            selectedSavedLoadout = null;
        } else if (item.savedLoadout != null) {
            STSArena.logger.info("Saved loadout selected: " + item.savedLoadout.name);
            useNewRandomLoadout = false;
            selectedSavedLoadout = item.savedLoadout;
        }

        // Close this screen and open encounter selection
        this.close();
        STSArena.openEncounterSelectScreen();
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.8f));
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Select Loadout",
            CENTER_X, TITLE_Y, Settings.GOLD_COLOR);

        // Render items
        float y = LIST_START_Y + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Only render visible items
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT - 50.0f * Settings.scale) {
                if (item.isHeader) {
                    renderHeader(sb, item.text, buttonY);
                } else {
                    renderOption(sb, item.text, buttonY, hitboxes[i], item.isNewRandom);
                }
            }
        }

        // Cancel button
        this.cancelButton.render(sb);
    }

    private void renderHeader(SpriteBatch sb, String text, float y) {
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            text,
            CENTER_X, y - BUTTON_HEIGHT / 2.0f, Settings.GOLD_COLOR);
    }

    private void renderOption(SpriteBatch sb, String text, float y, Hitbox hb, boolean isNewRandom) {
        // Background for button
        Color bgColor;
        if (hb.hovered) {
            bgColor = new Color(0.3f, 0.3f, 0.4f, 0.8f);
        } else {
            bgColor = new Color(0.1f, 0.1f, 0.15f, 0.6f);
        }

        sb.setColor(bgColor);
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG,
            CENTER_X - BUTTON_WIDTH / 2.0f,
            y - BUTTON_HEIGHT,
            BUTTON_WIDTH,
            BUTTON_HEIGHT);

        // Text
        Color textColor;
        if (isNewRandom) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (hb.hovered) {
            textColor = Settings.GOLD_COLOR;
        } else {
            textColor = Settings.CREAM_COLOR;
        }

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            text,
            CENTER_X, y - BUTTON_HEIGHT / 2.0f, textColor);
    }
}
