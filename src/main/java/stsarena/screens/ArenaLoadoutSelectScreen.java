package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen for selecting which loadout to use in arena mode.
 * Shows a list on the left with a preview panel on the right.
 */
public class ArenaLoadoutSelectScreen {

    // Layout constants - left panel (loadout list)
    private static final float LEFT_PANEL_WIDTH = 480.0f * Settings.scale;
    private static final float LEFT_PANEL_X = 80.0f * Settings.scale;
    private static final float TITLE_Y = Settings.HEIGHT - 100.0f * Settings.scale;
    private static final float LIST_START_Y = TITLE_Y - 80.0f * Settings.scale;
    private static final float ROW_HEIGHT = 40.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 35.0f * Settings.scale;

    // Layout constants - right panel (preview)
    private static final float RIGHT_PANEL_X = LEFT_PANEL_X + LEFT_PANEL_WIDTH + 40.0f * Settings.scale;
    private static final float RIGHT_PANEL_WIDTH = Settings.WIDTH - RIGHT_PANEL_X - 80.0f * Settings.scale;
    private static final float PREVIEW_TITLE_Y = TITLE_Y;

    // History button
    private static final float HISTORY_BUTTON_WIDTH = 120.0f * Settings.scale;
    private static final float HISTORY_BUTTON_HEIGHT = 40.0f * Settings.scale;
    private static final float HISTORY_BUTTON_X = Settings.WIDTH - HISTORY_BUTTON_WIDTH - 40.0f * Settings.scale;
    private static final float HISTORY_BUTTON_Y = 50.0f * Settings.scale;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // List items
    private List<ListItem> items;
    private Hitbox[] hitboxes;

    // History button
    private Hitbox historyButtonHitbox;

    // Currently hovered item for preview
    private ListItem hoveredItem = null;

    // Persistently selected item (for action buttons)
    private ListItem selectedItem = null;

    // Action buttons (shown when a saved loadout is selected)
    private static final float ACTION_BUTTON_WIDTH = 180.0f * Settings.scale;
    private static final float ACTION_BUTTON_HEIGHT = 40.0f * Settings.scale;
    private static final float ACTION_BUTTON_SPACING = 50.0f * Settings.scale;
    private Hitbox fightButtonHb, copyButtonHb, renameButtonHb, deleteButtonHb, loadoutHistoryButtonHb;

    // Rename state
    private boolean isRenaming = false;
    private String renameText = "";

    // Delete confirmation state
    private boolean isConfirmingDelete = false;
    private Hitbox confirmDeleteHb, cancelDeleteHb;

    // Backspace repeat timing
    private float backspaceHeldTime = 0f;
    private static final float BACKSPACE_INITIAL_DELAY = 0.4f;
    private static final float BACKSPACE_REPEAT_DELAY = 0.05f;
    private boolean backspaceRepeating = false;

    // Selection state for starting fights
    public static boolean useNewRandomLoadout = false;
    public static ArenaRepository.LoadoutRecord selectedSavedLoadout = null;

    // For parsing JSON
    private static final Gson gson = new Gson();

    private static class ListItem {
        String text;
        boolean isNewRandom;
        boolean isHeader;
        boolean isCustomCreate;
        ArenaRepository.LoadoutRecord savedLoadout;

        ListItem(String text, boolean isNewRandom, boolean isHeader, boolean isCustomCreate, ArenaRepository.LoadoutRecord savedLoadout) {
            this.text = text;
            this.isNewRandom = isNewRandom;
            this.isHeader = isHeader;
            this.isCustomCreate = isCustomCreate;
            this.savedLoadout = savedLoadout;
        }
    }

    public ArenaLoadoutSelectScreen() {
        this.cancelButton = new MenuCancelButton();
        this.items = new ArrayList<>();
        this.hitboxes = new Hitbox[0];
        this.historyButtonHitbox = new Hitbox(HISTORY_BUTTON_WIDTH, HISTORY_BUTTON_HEIGHT);

        // Action buttons
        fightButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        copyButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        renameButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        deleteButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        loadoutHistoryButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);

        // Delete confirmation buttons
        confirmDeleteHb = new Hitbox(80.0f * Settings.scale, 35.0f * Settings.scale);
        cancelDeleteHb = new Hitbox(80.0f * Settings.scale, 35.0f * Settings.scale);
    }

    public void open() {
        STSArena.logger.info("Opening Arena Loadout Select Screen");
        this.isOpen = true;
        this.cancelButton.show("Return");
        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;
        this.hoveredItem = null;
        this.selectedItem = null;

        // Reset selection state
        useNewRandomLoadout = false;
        selectedSavedLoadout = null;

        // Reset rename/delete state
        this.isRenaming = false;
        this.renameText = "";
        this.isConfirmingDelete = false;

        // Build the item list
        buildItemList();

        // Create hitboxes
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(LEFT_PANEL_WIDTH - 20.0f * Settings.scale, BUTTON_HEIGHT);
        }
    }

    private void buildItemList() {
        items.clear();

        // Create custom loadout option
        items.add(new ListItem("+ Create Custom Loadout", false, false, true, null));

        // New random loadout option
        items.add(new ListItem("New Random Loadout", true, false, false, null));

        // Load saved loadouts from database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            List<ArenaRepository.LoadoutRecord> loadouts = repo.getLoadouts(50);

            if (!loadouts.isEmpty()) {
                // Add header
                items.add(new ListItem("--- Saved Loadouts ---", false, true, false, null));

                for (ArenaRepository.LoadoutRecord loadout : loadouts) {
                    String label = loadout.name;
                    items.add(new ListItem(label, false, false, false, loadout));
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

        // Handle rename text input
        if (isRenaming) {
            handleRenameInput();
        }

        // Cancel button
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            // If renaming or confirming delete, cancel that instead of closing screen
            if (isRenaming) {
                isRenaming = false;
                renameText = "";
                return;
            }
            if (isConfirmingDelete) {
                isConfirmingDelete = false;
                return;
            }
            this.close();
            return;
        }

        // History button (global)
        historyButtonHitbox.move(HISTORY_BUTTON_X + HISTORY_BUTTON_WIDTH / 2.0f, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT / 2.0f);
        historyButtonHitbox.update();
        if (historyButtonHitbox.hovered && InputHelper.justClickedLeft && !isRenaming && !isConfirmingDelete) {
            InputHelper.justClickedLeft = false;
            this.close();
            STSArena.openHistoryScreen();
            return;
        }

        // Handle delete confirmation
        if (isConfirmingDelete && selectedItem != null && selectedItem.savedLoadout != null) {
            updateDeleteConfirmation();
            return;  // Block other interaction while confirming
        }

        // Update action buttons when a saved loadout is selected
        if (selectedItem != null && selectedItem.savedLoadout != null && !isRenaming) {
            updateActionButtons();
        }

        // Scrolling (only when not in modal states)
        if (!isRenaming && !isConfirmingDelete) {
            if (InputHelper.scrolledDown) {
                targetScrollY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                targetScrollY -= Settings.SCROLL_SPEED;
            }
        }

        // Clamp scroll
        float maxScroll = Math.max(0, items.size() * ROW_HEIGHT - 500.0f * Settings.scale);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > maxScroll) targetScrollY = maxScroll;

        scrollY = MathHelper.scrollSnapLerpSpeed(scrollY, targetScrollY);

        // Update hitboxes and check for clicks
        hoveredItem = null;
        float y = LIST_START_Y + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Skip header items
            if (item.isHeader) continue;

            // Only update hitboxes that are visible
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT) {
                hitboxes[i].move(LEFT_PANEL_X + LEFT_PANEL_WIDTH / 2.0f, buttonY - BUTTON_HEIGHT / 2.0f);
                hitboxes[i].update();

                if (hitboxes[i].hovered) {
                    hoveredItem = item;
                    if (InputHelper.justClickedLeft && !isRenaming && !isConfirmingDelete) {
                        handleItemClick(item);
                        return;
                    }
                }
            }
        }
    }

    private void handleRenameInput() {
        float delta = com.badlogic.gdx.Gdx.graphics.getDeltaTime();
        boolean shift = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) ||
                        com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);

        // Handle backspace with repeat
        if (com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.BACKSPACE)) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE)) {
                // First press - delete immediately
                if (!renameText.isEmpty()) {
                    renameText = renameText.substring(0, renameText.length() - 1);
                }
                backspaceHeldTime = 0f;
                backspaceRepeating = false;
            } else {
                // Held - use repeat delay
                backspaceHeldTime += delta;
                if (!backspaceRepeating && backspaceHeldTime >= BACKSPACE_INITIAL_DELAY) {
                    backspaceRepeating = true;
                    backspaceHeldTime = 0f;
                }
                if (backspaceRepeating && backspaceHeldTime >= BACKSPACE_REPEAT_DELAY) {
                    if (!renameText.isEmpty()) {
                        renameText = renameText.substring(0, renameText.length() - 1);
                    }
                    backspaceHeldTime = 0f;
                }
            }
        } else {
            backspaceHeldTime = 0f;
            backspaceRepeating = false;
        }

        // Handle enter to save
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) {
            if (!renameText.trim().isEmpty() && selectedItem != null && selectedItem.savedLoadout != null) {
                // Save the new name
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                if (repo.renameLoadout(selectedItem.savedLoadout.dbId, renameText.trim())) {
                    selectedItem.savedLoadout.name = renameText.trim();
                    selectedItem.text = renameText.trim();
                    STSArena.logger.info("Renamed loadout to: " + renameText.trim());
                }
            }
            isRenaming = false;
            renameText = "";
            return;
        }

        // Handle escape to cancel
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            isRenaming = false;
            renameText = "";
            return;
        }

        // Handle typed characters - letters
        for (int keycode = com.badlogic.gdx.Input.Keys.A; keycode <= com.badlogic.gdx.Input.Keys.Z; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('a' + (keycode - com.badlogic.gdx.Input.Keys.A));
                if (shift) c = Character.toUpperCase(c);
                renameText += c;
            }
        }

        // Handle numbers with shift for symbols
        String shiftedNumbers = ")!@#$%^&*(";  // Shift + 0-9
        for (int i = 0; i <= 9; i++) {
            int keycode = com.badlogic.gdx.Input.Keys.NUM_0 + i;
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                if (shift) {
                    renameText += shiftedNumbers.charAt(i);
                } else {
                    renameText += (char) ('0' + i);
                }
            }
        }

        // Handle other keys with shift variants
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            renameText += ' ';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.MINUS)) {
            renameText += shift ? '_' : '-';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.PERIOD)) {
            renameText += shift ? '>' : '.';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.COMMA)) {
            renameText += shift ? '<' : ',';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SLASH)) {
            renameText += shift ? '?' : '/';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SEMICOLON)) {
            renameText += shift ? ':' : ';';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.APOSTROPHE)) {
            renameText += shift ? '"' : '\'';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT_BRACKET)) {
            renameText += shift ? '{' : '[';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT_BRACKET)) {
            renameText += shift ? '}' : ']';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSLASH)) {
            renameText += shift ? '|' : '\\';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.EQUALS)) {
            renameText += shift ? '+' : '=';
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.GRAVE)) {
            renameText += shift ? '~' : '`';
        }
    }

    private void updateDeleteConfirmation() {
        float centerX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float centerY = Settings.HEIGHT / 2.0f;

        confirmDeleteHb.move(centerX - 50.0f * Settings.scale, centerY - 50.0f * Settings.scale);
        cancelDeleteHb.move(centerX + 50.0f * Settings.scale, centerY - 50.0f * Settings.scale);
        confirmDeleteHb.update();
        cancelDeleteHb.update();

        if (InputHelper.justClickedLeft) {
            if (confirmDeleteHb.hovered) {
                // Delete the loadout
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                if (repo.deleteLoadout(selectedItem.savedLoadout.dbId)) {
                    STSArena.logger.info("Deleted loadout: " + selectedItem.savedLoadout.name);
                    // Refresh the list
                    selectedItem = null;
                    isConfirmingDelete = false;
                    buildItemList();
                    hitboxes = new Hitbox[items.size()];
                    for (int i = 0; i < items.size(); i++) {
                        hitboxes[i] = new Hitbox(LEFT_PANEL_WIDTH - 20.0f * Settings.scale, BUTTON_HEIGHT);
                    }
                }
                InputHelper.justClickedLeft = false;
            } else if (cancelDeleteHb.hovered) {
                isConfirmingDelete = false;
                InputHelper.justClickedLeft = false;
            }
        }
    }

    private void updateActionButtons() {
        float buttonX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float buttonStartY = 350.0f * Settings.scale;

        fightButtonHb.move(buttonX, buttonStartY);
        copyButtonHb.move(buttonX, buttonStartY - ACTION_BUTTON_SPACING);
        renameButtonHb.move(buttonX, buttonStartY - ACTION_BUTTON_SPACING * 2);
        loadoutHistoryButtonHb.move(buttonX, buttonStartY - ACTION_BUTTON_SPACING * 3);
        deleteButtonHb.move(buttonX, buttonStartY - ACTION_BUTTON_SPACING * 4);

        fightButtonHb.update();
        copyButtonHb.update();
        renameButtonHb.update();
        loadoutHistoryButtonHb.update();
        deleteButtonHb.update();

        if (InputHelper.justClickedLeft) {
            if (fightButtonHb.hovered) {
                // Fight with this loadout
                startFightWithSelectedLoadout();
                InputHelper.justClickedLeft = false;
            } else if (copyButtonHb.hovered) {
                // Copy to loadout creator
                this.close();
                STSArena.openLoadoutCreatorWithLoadout(selectedItem.savedLoadout);
                InputHelper.justClickedLeft = false;
            } else if (renameButtonHb.hovered) {
                // Start rename mode
                isRenaming = true;
                renameText = selectedItem.savedLoadout.name;
                InputHelper.justClickedLeft = false;
            } else if (loadoutHistoryButtonHb.hovered) {
                // Open history for this loadout
                this.close();
                STSArena.openHistoryScreenForLoadout(selectedItem.savedLoadout.dbId, selectedItem.savedLoadout.name);
                InputHelper.justClickedLeft = false;
            } else if (deleteButtonHb.hovered) {
                // Start delete confirmation
                isConfirmingDelete = true;
                InputHelper.justClickedLeft = false;
            }
        }
    }

    private void handleItemClick(ListItem item) {
        // For Create Custom and New Random, navigate immediately
        if (item.isCustomCreate) {
            STSArena.logger.info("Opening custom loadout creator");
            this.close();
            STSArena.openLoadoutCreatorScreen();
            return;
        }

        if (item.isNewRandom) {
            STSArena.logger.info("New random loadout - proceeding to encounter selection");
            useNewRandomLoadout = true;
            selectedSavedLoadout = null;
            this.close();
            STSArena.openEncounterSelectScreen();
            return;
        }

        // For saved loadouts, just select it
        if (item.savedLoadout != null) {
            selectedItem = item;
            isRenaming = false;
            isConfirmingDelete = false;
        }
    }

    private void startFightWithSelectedLoadout() {
        if (selectedItem != null && selectedItem.savedLoadout != null) {
            STSArena.logger.info("Saved loadout selected: " + selectedItem.savedLoadout.name);
            useNewRandomLoadout = false;
            selectedSavedLoadout = selectedItem.savedLoadout;
            this.close();
            STSArena.openEncounterSelectScreen();
        }
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.85f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Select Loadout",
            LEFT_PANEL_X + LEFT_PANEL_WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Render loadout list (left panel)
        renderLoadoutList(sb);

        // Render preview panel (right side)
        renderPreviewPanel(sb);

        // Render history button
        renderHistoryButton(sb);

        // Cancel button
        this.cancelButton.render(sb);

        // Render cursor
        com.megacrit.cardcrawl.core.CardCrawlGame.cursor.render(sb);
    }

    private void renderLoadoutList(SpriteBatch sb) {
        float y = LIST_START_Y + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Only render visible items
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT - 50.0f * Settings.scale) {
                if (item.isHeader) {
                    renderHeader(sb, item.text, buttonY);
                } else {
                    renderOption(sb, item, buttonY, hitboxes[i]);
                }
            }
        }
    }

    private void renderHeader(SpriteBatch sb, String text, float y) {
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            text,
            LEFT_PANEL_X + LEFT_PANEL_WIDTH / 2.0f, y - BUTTON_HEIGHT / 2.0f, Settings.GOLD_COLOR);
    }

    private void renderOption(SpriteBatch sb, ListItem item, float y, Hitbox hb) {
        boolean isSelected = (selectedItem == item);

        // Background
        Color bgColor;
        if (isSelected) {
            bgColor = new Color(0.2f, 0.4f, 0.3f, 0.9f);  // Green tint for selected
        } else if (hb.hovered) {
            bgColor = new Color(0.3f, 0.3f, 0.4f, 0.8f);
        } else {
            bgColor = new Color(0.1f, 0.1f, 0.15f, 0.6f);
        }

        sb.setColor(bgColor);
        float rowWidth = LEFT_PANEL_WIDTH - 20.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            LEFT_PANEL_X,
            y - BUTTON_HEIGHT,
            rowWidth,
            BUTTON_HEIGHT);

        // Text
        Color textColor;
        if (item.isCustomCreate) {
            textColor = new Color(0.4f, 0.8f, 1.0f, 1.0f);  // Cyan
        } else if (item.isNewRandom) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (isSelected) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (hb.hovered) {
            textColor = Settings.GOLD_COLOR;
        } else {
            textColor = Settings.CREAM_COLOR;
        }

        // Truncate long names to fit
        String displayText = item.text;
        float maxWidth = rowWidth - 20.0f * Settings.scale;
        if (FontHelper.getSmartWidth(FontHelper.cardDescFont_N, displayText, maxWidth, 0) > maxWidth) {
            // Truncate until it fits
            while (displayText.length() > 3 &&
                   FontHelper.getSmartWidth(FontHelper.cardDescFont_N, displayText + "...", maxWidth, 0) > maxWidth) {
                displayText = displayText.substring(0, displayText.length() - 1);
            }
            displayText = displayText.trim() + "...";
        }

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            displayText,
            LEFT_PANEL_X + 10.0f * Settings.scale, y - 8.0f * Settings.scale, textColor);
    }

    private void renderPreviewPanel(SpriteBatch sb) {
        // Panel background
        sb.setColor(new Color(0.05f, 0.05f, 0.1f, 0.9f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            RIGHT_PANEL_X, 100.0f * Settings.scale,
            RIGHT_PANEL_WIDTH, Settings.HEIGHT - 200.0f * Settings.scale);

        // Border
        sb.setColor(new Color(0.3f, 0.3f, 0.4f, 1.0f));
        float borderWidth = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, RIGHT_PANEL_X, 100.0f * Settings.scale, RIGHT_PANEL_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, RIGHT_PANEL_X, Settings.HEIGHT - 100.0f * Settings.scale - borderWidth, RIGHT_PANEL_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, RIGHT_PANEL_X, 100.0f * Settings.scale, borderWidth, Settings.HEIGHT - 200.0f * Settings.scale);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, RIGHT_PANEL_X + RIGHT_PANEL_WIDTH - borderWidth, 100.0f * Settings.scale, borderWidth, Settings.HEIGHT - 200.0f * Settings.scale);

        // Show delete confirmation if active
        if (isConfirmingDelete && selectedItem != null && selectedItem.savedLoadout != null) {
            renderDeleteConfirmation(sb);
            return;
        }

        // Use selectedItem if available, otherwise hoveredItem
        ListItem previewItem = selectedItem != null ? selectedItem : hoveredItem;

        if (previewItem == null) {
            // No selection - show instruction
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                "Click a loadout to select it",
                RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f, Settings.HEIGHT / 2.0f, Settings.CREAM_COLOR);
        } else if (previewItem.isNewRandom || previewItem.isCustomCreate) {
            // Random/Custom loadout - show question mark
            renderRandomPreview(sb, previewItem);
        } else if (previewItem.savedLoadout != null) {
            // Saved loadout - show details and action buttons
            renderLoadoutPreview(sb, previewItem.savedLoadout);

            // Render action buttons (only if this is the selected item)
            if (selectedItem == previewItem && !isRenaming) {
                renderActionButtons(sb);
            }

            // Render rename input if active
            if (isRenaming && selectedItem == previewItem) {
                renderRenameInput(sb);
            }
        }
    }

    private void renderActionButtons(SpriteBatch sb) {
        float buttonX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float buttonStartY = 350.0f * Settings.scale;

        // Fight button (green)
        renderActionButton(sb, fightButtonHb, "Fight", buttonStartY,
            new Color(0.2f, 0.5f, 0.2f, 0.9f), Settings.GREEN_TEXT_COLOR);

        // Copy button (blue)
        renderActionButton(sb, copyButtonHb, "Copy", buttonStartY - ACTION_BUTTON_SPACING,
            new Color(0.2f, 0.3f, 0.5f, 0.9f), new Color(0.5f, 0.7f, 1.0f, 1.0f));

        // Rename button (gray)
        renderActionButton(sb, renameButtonHb, "Rename", buttonStartY - ACTION_BUTTON_SPACING * 2,
            new Color(0.25f, 0.25f, 0.3f, 0.9f), Settings.CREAM_COLOR);

        // History button (gray)
        renderActionButton(sb, loadoutHistoryButtonHb, "History", buttonStartY - ACTION_BUTTON_SPACING * 3,
            new Color(0.25f, 0.25f, 0.3f, 0.9f), Settings.CREAM_COLOR);

        // Delete button (red)
        renderActionButton(sb, deleteButtonHb, "Delete", buttonStartY - ACTION_BUTTON_SPACING * 4,
            new Color(0.5f, 0.2f, 0.2f, 0.9f), new Color(1.0f, 0.5f, 0.5f, 1.0f));
    }

    private void renderActionButton(SpriteBatch sb, Hitbox hb, String text, float y, Color bgColor, Color textColor) {
        float buttonX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;

        // Darken or lighten on hover
        Color finalBgColor = hb.hovered ?
            new Color(bgColor.r * 1.3f, bgColor.g * 1.3f, bgColor.b * 1.3f, bgColor.a) : bgColor;
        Color finalTextColor = hb.hovered ? Settings.GOLD_COLOR : textColor;

        sb.setColor(finalBgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            buttonX - ACTION_BUTTON_WIDTH / 2.0f,
            y - ACTION_BUTTON_HEIGHT / 2.0f,
            ACTION_BUTTON_WIDTH,
            ACTION_BUTTON_HEIGHT);

        // Border
        sb.setColor(hb.hovered ? Settings.GOLD_COLOR : new Color(0.4f, 0.4f, 0.5f, 1.0f));
        float bw = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - ACTION_BUTTON_WIDTH / 2.0f, y - ACTION_BUTTON_HEIGHT / 2.0f, ACTION_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - ACTION_BUTTON_WIDTH / 2.0f, y + ACTION_BUTTON_HEIGHT / 2.0f - bw, ACTION_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - ACTION_BUTTON_WIDTH / 2.0f, y - ACTION_BUTTON_HEIGHT / 2.0f, bw, ACTION_BUTTON_HEIGHT);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX + ACTION_BUTTON_WIDTH / 2.0f - bw, y - ACTION_BUTTON_HEIGHT / 2.0f, bw, ACTION_BUTTON_HEIGHT);

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, text, buttonX, y, finalTextColor);
    }

    private void renderRenameInput(SpriteBatch sb) {
        float centerX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float y = 350.0f * Settings.scale;

        // Input box background
        float inputWidth = 300.0f * Settings.scale;
        float inputHeight = 40.0f * Settings.scale;

        sb.setColor(new Color(0.1f, 0.1f, 0.15f, 1.0f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            centerX - inputWidth / 2.0f, y - inputHeight / 2.0f,
            inputWidth, inputHeight);

        // Border
        sb.setColor(Settings.GOLD_COLOR);
        float bw = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, centerX - inputWidth / 2.0f, y - inputHeight / 2.0f, inputWidth, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, centerX - inputWidth / 2.0f, y + inputHeight / 2.0f - bw, inputWidth, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, centerX - inputWidth / 2.0f, y - inputHeight / 2.0f, bw, inputHeight);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, centerX + inputWidth / 2.0f - bw, y - inputHeight / 2.0f, bw, inputHeight);

        // Text with cursor
        String displayText = renameText + "|";
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, displayText, centerX, y, Settings.CREAM_COLOR);

        // Instructions
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Press Enter to save, Escape to cancel",
            centerX, y - 50.0f * Settings.scale, new Color(0.6f, 0.6f, 0.6f, 1.0f));
    }

    private void renderDeleteConfirmation(SpriteBatch sb) {
        float centerX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float centerY = Settings.HEIGHT / 2.0f;

        // Warning message
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Delete \"" + selectedItem.savedLoadout.name + "\"?",
            centerX, centerY + 50.0f * Settings.scale, Settings.RED_TEXT_COLOR);

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "This will also delete all fight history for this loadout.",
            centerX, centerY + 20.0f * Settings.scale, Settings.CREAM_COLOR);

        // Confirm button
        Color confirmBg = confirmDeleteHb.hovered ? new Color(0.6f, 0.2f, 0.2f, 1.0f) : new Color(0.4f, 0.15f, 0.15f, 0.9f);
        sb.setColor(confirmBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            confirmDeleteHb.x, confirmDeleteHb.y, confirmDeleteHb.width, confirmDeleteHb.height);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, "Delete",
            confirmDeleteHb.cX, confirmDeleteHb.cY,
            confirmDeleteHb.hovered ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);

        // Cancel button
        Color cancelBg = cancelDeleteHb.hovered ? new Color(0.3f, 0.3f, 0.4f, 1.0f) : new Color(0.2f, 0.2f, 0.25f, 0.9f);
        sb.setColor(cancelBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            cancelDeleteHb.x, cancelDeleteHb.y, cancelDeleteHb.width, cancelDeleteHb.height);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, "Cancel",
            cancelDeleteHb.cX, cancelDeleteHb.cY,
            cancelDeleteHb.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR);
    }

    private void renderRandomPreview(SpriteBatch sb, ListItem item) {
        float centerX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float centerY = Settings.HEIGHT / 2.0f;

        // Big question mark box
        float boxSize = 200.0f * Settings.scale;
        sb.setColor(new Color(0.1f, 0.1f, 0.15f, 1.0f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            centerX - boxSize / 2.0f, centerY - boxSize / 2.0f,
            boxSize, boxSize);

        // Question mark
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "?",
            centerX, centerY, Settings.GOLD_COLOR);

        // Label
        String label = item.isCustomCreate ? "Create New Custom Loadout" : "Random Loadout";
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            label,
            centerX, centerY - boxSize / 2.0f - 40.0f * Settings.scale, Settings.CREAM_COLOR);
    }

    private void renderLoadoutPreview(SpriteBatch sb, ArenaRepository.LoadoutRecord loadout) {
        float x = RIGHT_PANEL_X + 20.0f * Settings.scale;
        float y = PREVIEW_TITLE_Y - 30.0f * Settings.scale;
        float contentWidth = RIGHT_PANEL_WIDTH - 40.0f * Settings.scale;

        // Loadout name
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.SCP_cardTitleFont_small,
            loadout.name,
            x, y, Settings.GOLD_COLOR);
        y -= 50.0f * Settings.scale;

        // Character class
        String className = loadout.characterClass;
        className = className.substring(0, 1) + className.substring(1).toLowerCase().replace("_", " ");
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Character: " + className,
            x, y, Settings.CREAM_COLOR);
        y -= 30.0f * Settings.scale;

        // Ascension
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Ascension: " + loadout.ascensionLevel,
            x, y, Settings.CREAM_COLOR);
        y -= 30.0f * Settings.scale;

        // HP
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "HP: " + loadout.currentHp + " / " + loadout.maxHp,
            x, y, Settings.CREAM_COLOR);
        y -= 40.0f * Settings.scale;

        // Relics
        y = renderRelicsPreview(sb, loadout, x, y, contentWidth);

        // Cards
        y -= 20.0f * Settings.scale;
        renderCardsPreview(sb, loadout, x, y, contentWidth);
    }

    private float renderRelicsPreview(SpriteBatch sb, ArenaRepository.LoadoutRecord loadout, float x, float y, float width) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Relics:",
            x, y, Settings.GOLD_COLOR);
        y -= 25.0f * Settings.scale;

        // Parse relics
        try {
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> relicIds = gson.fromJson(loadout.relicsJson, listType);

            if (relicIds != null && !relicIds.isEmpty()) {
                float relicSize = 48.0f * Settings.scale;
                float relicSpacing = 52.0f * Settings.scale;
                float relicX = x;
                int relicsPerRow = (int) (width / relicSpacing);
                int count = 0;

                for (String relicId : relicIds) {
                    AbstractRelic relic = RelicLibrary.getRelic(relicId);
                    if (relic != null) {
                        // Render relic icon
                        Texture img = relic.img;
                        if (img != null) {
                            sb.setColor(Color.WHITE);
                            sb.draw(img, relicX, y - relicSize, relicSize, relicSize);
                        }
                        relicX += relicSpacing;
                        count++;

                        if (count >= relicsPerRow) {
                            relicX = x;
                            y -= relicSpacing;
                            count = 0;
                        }
                    }
                }
                if (count > 0) {
                    y -= relicSpacing;
                }
            } else {
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    "  (none)",
                    x, y, new Color(0.5f, 0.5f, 0.5f, 1.0f));
                y -= 25.0f * Settings.scale;
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to parse relics", e);
        }

        return y;
    }

    private void renderCardsPreview(SpriteBatch sb, ArenaRepository.LoadoutRecord loadout, float x, float y, float width) {
        // Parse cards
        try {
            Type cardListType = new TypeToken<List<ArenaRepository.CardData>>(){}.getType();
            List<ArenaRepository.CardData> cardDataList = gson.fromJson(loadout.deckJson, cardListType);

            if (cardDataList != null && !cardDataList.isEmpty()) {
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    "Deck (" + cardDataList.size() + " cards):",
                    x, y, Settings.GOLD_COLOR);
                y -= 25.0f * Settings.scale;

                // Render cards in columns with dynamic widths
                float lineHeight = 28.0f * Settings.scale;
                int cardsPerColumn = 16;
                float columnPadding = 15.0f * Settings.scale;
                float startY = y;

                // First pass: build card names and calculate column widths
                List<String> cardNames = new ArrayList<>();
                List<Color> cardColors = new ArrayList<>();
                List<Float> columnWidths = new ArrayList<>();
                float currentColumnMaxWidth = 0;
                int cardIndex = 0;

                for (ArenaRepository.CardData cardData : cardDataList) {
                    AbstractCard card = CardLibrary.getCard(cardData.id);
                    if (card != null) {
                        String cardName = card.name;
                        if (cardData.upgrades > 0) {
                            cardName += "+";
                        }
                        cardNames.add(cardName);

                        Color cardColor = getCardTypeColor(card.type);
                        if (cardData.upgrades > 0) {
                            cardColor = Settings.GREEN_TEXT_COLOR;
                        }
                        cardColors.add(cardColor);

                        float textWidth = FontHelper.getSmartWidth(FontHelper.cardDescFont_N, cardName, 9999, 0);
                        if (textWidth > currentColumnMaxWidth) {
                            currentColumnMaxWidth = textWidth;
                        }

                        cardIndex++;
                        if (cardIndex % cardsPerColumn == 0) {
                            columnWidths.add(currentColumnMaxWidth + columnPadding);
                            currentColumnMaxWidth = 0;
                        }
                    }
                }
                // Add last column width if there are remaining cards
                if (cardIndex % cardsPerColumn != 0) {
                    columnWidths.add(currentColumnMaxWidth + columnPadding);
                }

                // Second pass: render cards using calculated column positions
                float cardX = x;
                int column = 0;
                int row = 0;

                for (int i = 0; i < cardNames.size(); i++) {
                    float cardY = startY - row * lineHeight;

                    FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                        cardNames.get(i),
                        cardX, cardY, cardColors.get(i));

                    row++;
                    if (row >= cardsPerColumn) {
                        row = 0;
                        if (column < columnWidths.size()) {
                            cardX += columnWidths.get(column);
                        }
                        column++;
                    }
                }
            } else {
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    "Deck: (empty)",
                    x, y, new Color(0.5f, 0.5f, 0.5f, 1.0f));
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to parse cards", e);
        }
    }

    private Color getCardTypeColor(AbstractCard.CardType type) {
        switch (type) {
            case ATTACK:
                return new Color(0.9f, 0.5f, 0.5f, 1.0f);
            case SKILL:
                return new Color(0.5f, 0.7f, 0.9f, 1.0f);
            case POWER:
                return new Color(0.9f, 0.8f, 0.4f, 1.0f);
            default:
                return Settings.CREAM_COLOR;
        }
    }

    private void renderHistoryButton(SpriteBatch sb) {
        // Background
        Color bgColor = historyButtonHitbox.hovered ?
            new Color(0.3f, 0.3f, 0.5f, 0.9f) : new Color(0.15f, 0.15f, 0.25f, 0.8f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            HISTORY_BUTTON_X, HISTORY_BUTTON_Y,
            HISTORY_BUTTON_WIDTH, HISTORY_BUTTON_HEIGHT);

        // Border
        sb.setColor(historyButtonHitbox.hovered ? Settings.GOLD_COLOR : new Color(0.4f, 0.4f, 0.5f, 1.0f));
        float borderWidth = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, HISTORY_BUTTON_X, HISTORY_BUTTON_Y, HISTORY_BUTTON_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, HISTORY_BUTTON_X, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT - borderWidth, HISTORY_BUTTON_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, HISTORY_BUTTON_X, HISTORY_BUTTON_Y, borderWidth, HISTORY_BUTTON_HEIGHT);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, HISTORY_BUTTON_X + HISTORY_BUTTON_WIDTH - borderWidth, HISTORY_BUTTON_Y, borderWidth, HISTORY_BUTTON_HEIGHT);

        // Text
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "History",
            HISTORY_BUTTON_X + HISTORY_BUTTON_WIDTH / 2.0f, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT / 2.0f,
            historyButtonHitbox.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR);
    }
}
