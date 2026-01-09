package stsarena.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import stsarena.arena.ArenaRunner;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
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
    // List starts below the search box with proper spacing
    private static final float LIST_START_Y = TITLE_Y - 120.0f * Settings.scale;
    private static final float ROW_HEIGHT = 40.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 35.0f * Settings.scale;

    // Layout constants - right panel (preview)
    private static final float RIGHT_PANEL_X = LEFT_PANEL_X + LEFT_PANEL_WIDTH + 40.0f * Settings.scale;
    private static final float RIGHT_PANEL_WIDTH = Settings.WIDTH - RIGHT_PANEL_X - 80.0f * Settings.scale;
    private static final float PREVIEW_TITLE_Y = TITLE_Y;

    // History and Stats buttons
    private static final float HISTORY_BUTTON_WIDTH = 120.0f * Settings.scale;
    private static final float HISTORY_BUTTON_HEIGHT = 40.0f * Settings.scale;
    private static final float HISTORY_BUTTON_X = Settings.WIDTH - HISTORY_BUTTON_WIDTH - 40.0f * Settings.scale;
    private static final float HISTORY_BUTTON_Y = 90.0f * Settings.scale;  // Moved up by 40px
    private static final float STATS_BUTTON_X = HISTORY_BUTTON_X - HISTORY_BUTTON_WIDTH - 20.0f * Settings.scale;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // List items
    private List<ListItem> items;
    private Hitbox[] hitboxes;

    // History and Stats buttons
    private Hitbox historyButtonHitbox;
    private Hitbox statsButtonHitbox;

    // Currently hovered item for preview
    private ListItem hoveredItem = null;

    // Persistently selected item (for action buttons)
    private ListItem selectedItem = null;

    // Action buttons (shown when a saved loadout is selected) - two rows at bottom
    private static final float ACTION_BUTTON_WIDTH = 120.0f * Settings.scale;
    private static final float ACTION_BUTTON_HEIGHT = 35.0f * Settings.scale;
    private static final float ACTION_BUTTON_GAP = 10.0f * Settings.scale;
    private static final float ACTION_BUTTONS_Y_ROW1 = 180.0f * Settings.scale;  // Top row (moved up by ~40px)
    private static final float ACTION_BUTTONS_Y_ROW2 = 135.0f * Settings.scale;   // Bottom row (moved up by ~40px)
    private Hitbox fightButtonHb, favoriteButtonHb, editButtonHb, copyButtonHb, renameButtonHb, deleteButtonHb, loadoutHistoryButtonHb;

    // Rename state
    private boolean isRenaming = false;
    private String renameText = "";

    // Delete confirmation state
    private boolean isConfirmingDelete = false;
    private Hitbox confirmDeleteHb, cancelDeleteHb;

    // Multi-select mode for bulk operations
    private boolean isMultiSelectMode = false;
    private List<Long> selectedLoadoutIds = new ArrayList<>();
    private Hitbox multiSelectToggleHb;
    private Hitbox bulkDeleteHb;
    private Hitbox bulkUnfavoriteHb;
    private Hitbox cancelMultiSelectHb;
    private static final float BULK_BUTTON_WIDTH = 140.0f * Settings.scale;
    private static final float BULK_BUTTON_HEIGHT = 35.0f * Settings.scale;

    // Anchor index for shift-click range selection (-1 = no anchor)
    private int selectionAnchorIndex = -1;

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

    // Character class filter (null = all classes)
    private String filterClass = null;  // "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER", or null for all
    private Hitbox[] classFilterHitboxes;
    private static final float FILTER_TAB_WIDTH = 70.0f * Settings.scale;
    private static final float FILTER_TAB_HEIGHT = 25.0f * Settings.scale;
    private static final float FILTER_TAB_Y = TITLE_Y - 40.0f * Settings.scale;

    // Search and filter state
    private String searchText = "";
    private boolean isTypingSearch = false;
    private Hitbox searchBoxHitbox;
    private static final float SEARCH_BOX_WIDTH = LEFT_PANEL_WIDTH - 20.0f * Settings.scale;  // Nearly full width
    private static final float SEARCH_BOX_HEIGHT = 30.0f * Settings.scale;
    // Position search box below the filter tabs with proper spacing (between tabs and list)
    private static final float SEARCH_BOX_Y = TITLE_Y - 85.0f * Settings.scale;  // Between filter tabs and list
    private static final float SEARCH_BOX_X = LEFT_PANEL_X + 10.0f * Settings.scale;

    // Unfiltered loadouts (for filtering)
    private List<ArenaRepository.LoadoutRecord> allLoadouts = new ArrayList<>();

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
        this.statsButtonHitbox = new Hitbox(HISTORY_BUTTON_WIDTH, HISTORY_BUTTON_HEIGHT);

        // Action buttons
        fightButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        favoriteButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        editButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        copyButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        renameButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        deleteButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);
        loadoutHistoryButtonHb = new Hitbox(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT);

        // Delete confirmation buttons
        confirmDeleteHb = new Hitbox(80.0f * Settings.scale, 35.0f * Settings.scale);
        cancelDeleteHb = new Hitbox(80.0f * Settings.scale, 35.0f * Settings.scale);

        // Multi-select mode buttons
        multiSelectToggleHb = new Hitbox(BULK_BUTTON_WIDTH, BULK_BUTTON_HEIGHT);
        bulkDeleteHb = new Hitbox(BULK_BUTTON_WIDTH, BULK_BUTTON_HEIGHT);
        bulkUnfavoriteHb = new Hitbox(BULK_BUTTON_WIDTH, BULK_BUTTON_HEIGHT);
        cancelMultiSelectHb = new Hitbox(BULK_BUTTON_WIDTH, BULK_BUTTON_HEIGHT);

        // Search box
        searchBoxHitbox = new Hitbox(SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT);

        // Class filter tabs (All, IC, SI, DE, WA)
        classFilterHitboxes = new Hitbox[5];
        for (int i = 0; i < 5; i++) {
            classFilterHitboxes[i] = new Hitbox(FILTER_TAB_WIDTH, FILTER_TAB_HEIGHT);
        }
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

        // Reset search/filter state
        this.searchText = "";
        this.isTypingSearch = false;
        this.filterClass = null;

        // Reset multi-select state
        this.isMultiSelectMode = false;
        this.selectedLoadoutIds.clear();
        this.selectionAnchorIndex = -1;

        // Load all loadouts from database
        loadAllLoadouts();

        // Build the item list (with current filters)
        buildItemList();

        // Create hitboxes
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(LEFT_PANEL_WIDTH - 20.0f * Settings.scale, BUTTON_HEIGHT);
        }
    }

    /**
     * Load all loadouts from database (unfiltered).
     */
    private void loadAllLoadouts() {
        allLoadouts.clear();
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            allLoadouts = repo.getLoadouts(100);
        } catch (Exception e) {
            STSArena.logger.error("Failed to load saved loadouts", e);
        }
    }

    private void buildItemList() {
        items.clear();

        // Create custom loadout option (only if no search active)
        if (searchText.isEmpty()) {
            items.add(new ListItem("+ Create Custom Loadout", false, false, true, null));

            // New random loadout option
            items.add(new ListItem("New Random Loadout", true, false, false, null));
        }

        // Apply filters to loadouts
        List<ArenaRepository.LoadoutRecord> filteredLoadouts = new ArrayList<>();
        for (ArenaRepository.LoadoutRecord loadout : allLoadouts) {
            // Filter by class
            if (filterClass != null && !filterClass.equals(loadout.characterClass)) {
                continue;
            }
            // Filter by search text
            if (!searchText.isEmpty() && !loadout.name.toLowerCase().contains(searchText.toLowerCase())) {
                continue;
            }
            filteredLoadouts.add(loadout);
        }

        if (!filteredLoadouts.isEmpty()) {
            // Add header
            String headerText = searchText.isEmpty() && filterClass == null ?
                "--- Saved Loadouts ---" :
                "--- Filtered (" + filteredLoadouts.size() + ") ---";
            items.add(new ListItem(headerText, false, true, false, null));

            for (ArenaRepository.LoadoutRecord loadout : filteredLoadouts) {
                String label = loadout.name;
                items.add(new ListItem(label, false, false, false, loadout));
            }
        } else if (!searchText.isEmpty() || filterClass != null) {
            items.add(new ListItem("--- No matches ---", false, true, false, null));
        }

        // Recreate hitboxes for new item list
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(LEFT_PANEL_WIDTH - 20.0f * Settings.scale, BUTTON_HEIGHT);
        }
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    /**
     * Select a loadout for preview display.
     * Used by CommunicationMod commands to show a loadout's contents in the preview panel.
     */
    public void selectLoadoutForPreview(ArenaRepository.LoadoutRecord loadout) {
        if (loadout == null) return;

        // Find the item with this loadout
        for (ListItem item : items) {
            if (item.savedLoadout != null && item.savedLoadout.dbId == loadout.dbId) {
                this.selectedItem = item;
                this.hoveredItem = item;  // Also set hovered so preview shows
                selectedSavedLoadout = loadout;
                STSArena.logger.info("Selected loadout for preview: " + loadout.name);
                return;
            }
        }

        // If not found in items (maybe filtered out), create a temporary item
        ListItem tempItem = new ListItem(loadout.name, false, false, false, loadout);
        this.selectedItem = tempItem;
        this.hoveredItem = tempItem;
        selectedSavedLoadout = loadout;
        STSArena.logger.info("Selected loadout for preview (not in list): " + loadout.name);
    }

    public void update() {
        if (!isOpen) return;

        // Handle rename text input
        if (isRenaming) {
            handleRenameInput();
        }

        // Handle search text input
        if (isTypingSearch) {
            handleSearchInput();
        }

        // Update search box (centered on its X position)
        searchBoxHitbox.move(SEARCH_BOX_X + SEARCH_BOX_WIDTH / 2.0f, SEARCH_BOX_Y);
        searchBoxHitbox.update();
        if (searchBoxHitbox.hovered && InputHelper.justClickedLeft && !isRenaming && !isConfirmingDelete) {
            isTypingSearch = true;
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !searchBoxHitbox.hovered && isTypingSearch) {
            isTypingSearch = false;
        }

        // Update class filter tabs
        String[] filterValues = {null, "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        float filterStartX = LEFT_PANEL_X + 10.0f * Settings.scale;
        for (int i = 0; i < 5; i++) {
            float tabX = filterStartX + i * (FILTER_TAB_WIDTH + 5.0f * Settings.scale);
            classFilterHitboxes[i].move(tabX + FILTER_TAB_WIDTH / 2.0f, FILTER_TAB_Y);
            classFilterHitboxes[i].update();

            if (classFilterHitboxes[i].hovered && InputHelper.justClickedLeft && !isRenaming && !isConfirmingDelete && !isTypingSearch) {
                String newFilter = filterValues[i];
                if ((filterClass == null && newFilter == null) || (filterClass != null && filterClass.equals(newFilter))) {
                    // Already selected, do nothing
                } else {
                    filterClass = newFilter;
                    selectedItem = null;  // Clear selection when filter changes
                    buildItemList();
                    scrollY = 0;
                    targetScrollY = 0;
                }
                InputHelper.justClickedLeft = false;
            }
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
            // When closing loadout select to return to main menu, clean up arena state
            // This restores any backed-up save files since the main menu constructor
            // won't be called (the main menu already exists, arena screens render over it)
            ArenaRunner.clearArenaRun();
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

        // Stats button (global)
        statsButtonHitbox.move(STATS_BUTTON_X + HISTORY_BUTTON_WIDTH / 2.0f, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT / 2.0f);
        statsButtonHitbox.update();
        if (statsButtonHitbox.hovered && InputHelper.justClickedLeft && !isRenaming && !isConfirmingDelete) {
            InputHelper.justClickedLeft = false;
            this.close();
            STSArena.openStatsScreen();
            return;
        }

        // Handle delete confirmation
        if (isConfirmingDelete && selectedItem != null && selectedItem.savedLoadout != null) {
            updateDeleteConfirmation();
            return;  // Block other interaction while confirming
        }

        // Update bulk operation buttons when items are selected via shift/ctrl-click
        if (!selectedLoadoutIds.isEmpty()) {
            updateBulkOperations();
        }

        // Update action buttons when a saved loadout is selected (and no multi-selection active)
        if (selectedLoadoutIds.isEmpty() && selectedItem != null && selectedItem.savedLoadout != null && !isRenaming) {
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

    private void handleSearchInput() {
        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            selectedItem = null;  // Clear selection when search changes
            buildItemList();
            scrollY = 0;
            targetScrollY = 0;
        }

        // Handle escape or enter to stop typing
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            isTypingSearch = false;
        }

        // Handle typed characters - letters
        for (int keycode = com.badlogic.gdx.Input.Keys.A; keycode <= com.badlogic.gdx.Input.Keys.Z; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('a' + (keycode - com.badlogic.gdx.Input.Keys.A));
                searchText += c;
                selectedItem = null;  // Clear selection when search changes
                buildItemList();
                scrollY = 0;
                targetScrollY = 0;
            }
        }

        // Handle numbers
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                searchText += c;
                selectedItem = null;
                buildItemList();
                scrollY = 0;
                targetScrollY = 0;
            }
        }

        // Handle space
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            searchText += ' ';
            selectedItem = null;
            buildItemList();
            scrollY = 0;
            targetScrollY = 0;
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

                // Find the index of the deleted item to select the next one
                int deletedIndex = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i) == selectedItem) {
                        deletedIndex = i;
                        break;
                    }
                }

                if (repo.deleteLoadout(selectedItem.savedLoadout.dbId)) {
                    STSArena.logger.info("Deleted loadout: " + selectedItem.savedLoadout.name);
                    // Refresh the list
                    isConfirmingDelete = false;
                    loadAllLoadouts();  // Reload from database
                    buildItemList();    // Rebuild filtered list

                    // Select the next saved loadout (skip headers and special items)
                    selectedItem = null;
                    if (deletedIndex >= 0 && items.size() > 0) {
                        // Bound deletedIndex to new list size (list shrunk after deletion)
                        int startIndex = Math.min(deletedIndex, items.size() - 1);

                        // Try to select the item at the same position, or the next one
                        for (int i = startIndex; i < items.size(); i++) {
                            if (items.get(i).savedLoadout != null) {
                                selectedItem = items.get(i);
                                break;
                            }
                        }
                        // If nothing found after, try before
                        if (selectedItem == null) {
                            for (int i = startIndex - 1; i >= 0; i--) {
                                if (items.get(i).savedLoadout != null) {
                                    selectedItem = items.get(i);
                                    break;
                                }
                            }
                        }
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
        // Two rows of buttons at the bottom of the preview panel
        float panelCenterX = RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f;
        float totalRow1Width = ACTION_BUTTON_WIDTH * 4 + ACTION_BUTTON_GAP * 3;
        float totalRow2Width = ACTION_BUTTON_WIDTH * 3 + ACTION_BUTTON_GAP * 2;
        float row1StartX = panelCenterX - totalRow1Width / 2.0f + ACTION_BUTTON_WIDTH / 2.0f;
        float row2StartX = panelCenterX - totalRow2Width / 2.0f + ACTION_BUTTON_WIDTH / 2.0f;

        // Row 1: Fight, Favorite, Edit, Copy
        fightButtonHb.move(row1StartX, ACTION_BUTTONS_Y_ROW1);
        favoriteButtonHb.move(row1StartX + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP, ACTION_BUTTONS_Y_ROW1);
        editButtonHb.move(row1StartX + (ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP) * 2, ACTION_BUTTONS_Y_ROW1);
        copyButtonHb.move(row1StartX + (ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP) * 3, ACTION_BUTTONS_Y_ROW1);

        // Row 2: Rename, History, Delete
        renameButtonHb.move(row2StartX, ACTION_BUTTONS_Y_ROW2);
        loadoutHistoryButtonHb.move(row2StartX + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP, ACTION_BUTTONS_Y_ROW2);
        deleteButtonHb.move(row2StartX + (ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP) * 2, ACTION_BUTTONS_Y_ROW2);

        fightButtonHb.update();
        favoriteButtonHb.update();
        editButtonHb.update();
        copyButtonHb.update();
        renameButtonHb.update();
        loadoutHistoryButtonHb.update();
        deleteButtonHb.update();

        if (InputHelper.justClickedLeft) {
            if (fightButtonHb.hovered) {
                // Fight with this loadout
                startFightWithSelectedLoadout();
                InputHelper.justClickedLeft = false;
            } else if (favoriteButtonHb.hovered) {
                // Toggle favorite status
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                boolean newStatus = repo.toggleFavorite(selectedItem.savedLoadout.dbId);
                selectedItem.savedLoadout.isFavorite = newStatus;
                // Reload and rebuild to re-sort
                loadAllLoadouts();
                buildItemList();
                // Re-select the item
                for (ListItem item : items) {
                    if (item.savedLoadout != null && item.savedLoadout.dbId == selectedItem.savedLoadout.dbId) {
                        selectedItem = item;
                        break;
                    }
                }
                InputHelper.justClickedLeft = false;
            } else if (editButtonHb.hovered) {
                // Edit this loadout in-place
                this.close();
                STSArena.openLoadoutCreatorForEdit(selectedItem.savedLoadout);
                InputHelper.justClickedLeft = false;
            } else if (copyButtonHb.hovered) {
                // Copy to loadout creator (creates new loadout)
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
        // Check if shift or ctrl is held for multi-select
        boolean isShiftHeld = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        boolean isCtrlHeld = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean isMultiSelectClick = isShiftHeld || isCtrlHeld;

        // For Create Custom and New Random, navigate immediately (unless multi-selecting)
        if (!isMultiSelectClick) {
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
        }

        // Find the index of the clicked item
        int clickedIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == item) {
                clickedIndex = i;
                break;
            }
        }

        // For saved loadouts
        if (item.savedLoadout != null) {
            if (isShiftHeld && selectionAnchorIndex >= 0) {
                // Shift-click: select range from anchor to clicked item
                int start = Math.min(selectionAnchorIndex, clickedIndex);
                int end = Math.max(selectionAnchorIndex, clickedIndex);

                // Select all saved loadouts in the range
                for (int i = start; i <= end; i++) {
                    if (i >= 0 && i < items.size()) {
                        ListItem rangeItem = items.get(i);
                        if (rangeItem.savedLoadout != null) {
                            Long id = rangeItem.savedLoadout.dbId;
                            if (!selectedLoadoutIds.contains(id)) {
                                selectedLoadoutIds.add(id);
                            }
                        }
                    }
                }
                // Don't update anchor on shift-click (allow extending the range)
            } else if (isCtrlHeld) {
                // Ctrl-click: toggle selection of clicked item
                Long id = item.savedLoadout.dbId;
                if (selectedLoadoutIds.contains(id)) {
                    selectedLoadoutIds.remove(id);
                } else {
                    selectedLoadoutIds.add(id);
                }
                // Set anchor to clicked item for future shift-clicks
                selectionAnchorIndex = clickedIndex;
            } else {
                // Normal click: single select, clear any multi-selection
                selectedLoadoutIds.clear();
                selectedItem = item;
                isRenaming = false;
                isConfirmingDelete = false;
                // Set anchor to clicked item
                selectionAnchorIndex = clickedIndex;
            }
        }
    }

    private void updateBulkOperations() {
        // Only show bulk operations when items are selected
        if (selectedLoadoutIds.isEmpty()) return;

        float buttonsY = 100.0f * Settings.scale;  // Moved up by 40px
        float startX = LEFT_PANEL_X + 10.0f * Settings.scale;

        bulkDeleteHb.move(startX + BULK_BUTTON_WIDTH / 2.0f, buttonsY);
        bulkUnfavoriteHb.move(startX + BULK_BUTTON_WIDTH * 1.5f + 10.0f * Settings.scale, buttonsY);
        cancelMultiSelectHb.move(startX + BULK_BUTTON_WIDTH * 2.5f + 20.0f * Settings.scale, buttonsY);

        bulkDeleteHb.update();
        bulkUnfavoriteHb.update();
        cancelMultiSelectHb.update();

        if (InputHelper.justClickedLeft) {
            if (bulkDeleteHb.hovered) {
                // Delete all selected loadouts
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                for (Long id : selectedLoadoutIds) {
                    repo.deleteLoadout(id);
                    STSArena.logger.info("Bulk deleted loadout: " + id);
                }
                selectedLoadoutIds.clear();
                loadAllLoadouts();
                buildItemList();
                InputHelper.justClickedLeft = false;
            } else if (bulkUnfavoriteHb.hovered) {
                // Unfavorite all selected loadouts
                for (Long id : selectedLoadoutIds) {
                    // Set favorite to false directly
                    String sql = "UPDATE loadouts SET is_favorite = 0 WHERE id = ?";
                    try (PreparedStatement stmt = ArenaDatabase.getInstance().getConnection().prepareStatement(sql)) {
                        stmt.setLong(1, id);
                        stmt.executeUpdate();
                    } catch (Exception e) {
                        STSArena.logger.error("Failed to unfavorite loadout: " + id, e);
                    }
                }
                selectedLoadoutIds.clear();
                loadAllLoadouts();
                buildItemList();
                InputHelper.justClickedLeft = false;
            } else if (cancelMultiSelectHb.hovered) {
                // Clear selection
                selectedLoadoutIds.clear();
                InputHelper.justClickedLeft = false;
            }
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

        // Search box
        renderSearchBox(sb);

        // Class filter tabs
        renderFilterTabs(sb);

        // Render loadout list (left panel)
        renderLoadoutList(sb);

        // Render preview panel (right side)
        renderPreviewPanel(sb);

        // Render history and stats buttons
        renderHistoryButton(sb);
        renderStatsButton(sb);

        // Render bulk operations when items are selected
        renderBulkOperations(sb);

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
        boolean isMultiSelected = item.savedLoadout != null &&
                                  selectedLoadoutIds.contains(item.savedLoadout.dbId);

        // Background
        Color bgColor;
        if (isMultiSelected) {
            bgColor = new Color(0.3f, 0.45f, 0.35f, 0.9f);  // Stronger green for multi-selected
        } else if (isSelected) {
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

        // When items are selected, render checkbox indicator for saved loadouts
        float textOffset = 0;
        if (!selectedLoadoutIds.isEmpty() && item.savedLoadout != null) {
            float checkboxSize = 18.0f * Settings.scale;
            float checkboxX = LEFT_PANEL_X + 8.0f * Settings.scale;
            float checkboxY = y - BUTTON_HEIGHT / 2.0f - checkboxSize / 2.0f;

            // Checkbox background
            sb.setColor(new Color(0.15f, 0.15f, 0.2f, 1.0f));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, checkboxX, checkboxY, checkboxSize, checkboxSize);

            // Checkbox border
            sb.setColor(isMultiSelected ? Settings.GREEN_TEXT_COLOR : new Color(0.4f, 0.4f, 0.5f, 1.0f));
            float bw = 2.0f * Settings.scale;
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, checkboxX, checkboxY, checkboxSize, bw);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, checkboxX, checkboxY + checkboxSize - bw, checkboxSize, bw);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, checkboxX, checkboxY, bw, checkboxSize);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, checkboxX + checkboxSize - bw, checkboxY, bw, checkboxSize);

            // Checkmark if selected
            if (isMultiSelected) {
                sb.setColor(Settings.GREEN_TEXT_COLOR);
                float innerSize = checkboxSize - 6.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    checkboxX + 3.0f * Settings.scale,
                    checkboxY + 3.0f * Settings.scale,
                    innerSize, innerSize);
            }

            textOffset = checkboxSize + 8.0f * Settings.scale;
        }

        // Text
        Color textColor;
        if (item.isCustomCreate) {
            textColor = new Color(0.4f, 0.8f, 1.0f, 1.0f);  // Cyan
        } else if (item.isNewRandom) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (isMultiSelected) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (isSelected) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (hb.hovered) {
            textColor = Settings.GOLD_COLOR;
        } else {
            textColor = Settings.CREAM_COLOR;
        }

        // Star prefix for favorites
        String prefix = "";
        if (item.savedLoadout != null && item.savedLoadout.isFavorite) {
            prefix = "* ";  // Star indicator for favorites
        }

        // Truncate long names to fit
        String displayText = prefix + item.text;
        float maxWidth = rowWidth - 20.0f * Settings.scale - textOffset;
        if (FontHelper.getSmartWidth(FontHelper.cardDescFont_N, displayText, maxWidth, 0) > maxWidth) {
            // Truncate until it fits
            while (displayText.length() > 3 + prefix.length() &&
                   FontHelper.getSmartWidth(FontHelper.cardDescFont_N, displayText + "...", maxWidth, 0) > maxWidth) {
                displayText = displayText.substring(0, displayText.length() - 1);
            }
            displayText = displayText.trim() + "...";
        }

        float textX = LEFT_PANEL_X + 10.0f * Settings.scale + textOffset;

        // Use gold color for the star if favorite
        if (item.savedLoadout != null && item.savedLoadout.isFavorite) {
            // Render star in gold
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                "*",
                textX, y - 8.0f * Settings.scale, Settings.GOLD_COLOR);
            // Render rest of text
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                displayText.substring(2),  // Skip the "* " prefix
                textX + FontHelper.getSmartWidth(FontHelper.cardDescFont_N, "* ", 1000, 0),
                y - 8.0f * Settings.scale, textColor);
        } else {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                displayText,
                textX, y - 8.0f * Settings.scale, textColor);
        }
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
        // Row 1: Fight, Favorite, Edit, Copy
        renderActionButton(sb, fightButtonHb, "Fight",
            new Color(0.2f, 0.5f, 0.2f, 0.9f), Settings.GREEN_TEXT_COLOR);

        boolean isFav = selectedItem != null && selectedItem.savedLoadout != null && selectedItem.savedLoadout.isFavorite;
        String favLabel = isFav ? "Unfav" : "Fav";
        Color favBgColor = isFav ? new Color(0.5f, 0.45f, 0.2f, 0.9f) : new Color(0.25f, 0.25f, 0.3f, 0.9f);
        Color favTextColor = isFav ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
        renderActionButton(sb, favoriteButtonHb, favLabel, favBgColor, favTextColor);

        renderActionButton(sb, editButtonHb, "Edit",
            new Color(0.5f, 0.4f, 0.2f, 0.9f), new Color(1.0f, 0.8f, 0.4f, 1.0f));

        renderActionButton(sb, copyButtonHb, "Copy",
            new Color(0.2f, 0.3f, 0.5f, 0.9f), new Color(0.5f, 0.7f, 1.0f, 1.0f));

        // Row 2: Rename, History, Delete
        renderActionButton(sb, renameButtonHb, "Rename",
            new Color(0.25f, 0.25f, 0.3f, 0.9f), Settings.CREAM_COLOR);

        renderActionButton(sb, loadoutHistoryButtonHb, "History",
            new Color(0.25f, 0.25f, 0.3f, 0.9f), Settings.CREAM_COLOR);

        renderActionButton(sb, deleteButtonHb, "Delete",
            new Color(0.5f, 0.2f, 0.2f, 0.9f), new Color(1.0f, 0.5f, 0.5f, 1.0f));
    }

    private void renderActionButton(SpriteBatch sb, Hitbox hb, String text, Color bgColor, Color textColor) {
        float x = hb.cX;
        float y = hb.cY;

        // Darken or lighten on hover
        Color finalBgColor = hb.hovered ?
            new Color(bgColor.r * 1.3f, bgColor.g * 1.3f, bgColor.b * 1.3f, bgColor.a) : bgColor;
        Color finalTextColor = hb.hovered ? Settings.GOLD_COLOR : textColor;

        sb.setColor(finalBgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            x - ACTION_BUTTON_WIDTH / 2.0f,
            y - ACTION_BUTTON_HEIGHT / 2.0f,
            ACTION_BUTTON_WIDTH,
            ACTION_BUTTON_HEIGHT);

        // Border
        sb.setColor(hb.hovered ? Settings.GOLD_COLOR : new Color(0.4f, 0.4f, 0.5f, 1.0f));
        float bw = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - ACTION_BUTTON_WIDTH / 2.0f, y - ACTION_BUTTON_HEIGHT / 2.0f, ACTION_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - ACTION_BUTTON_WIDTH / 2.0f, y + ACTION_BUTTON_HEIGHT / 2.0f - bw, ACTION_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - ACTION_BUTTON_WIDTH / 2.0f, y - ACTION_BUTTON_HEIGHT / 2.0f, bw, ACTION_BUTTON_HEIGHT);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x + ACTION_BUTTON_WIDTH / 2.0f - bw, y - ACTION_BUTTON_HEIGHT / 2.0f, bw, ACTION_BUTTON_HEIGHT);

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, text, x, y, finalTextColor);
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

    private void renderStatsButton(SpriteBatch sb) {
        // Background
        Color bgColor = statsButtonHitbox.hovered ?
            new Color(0.3f, 0.4f, 0.3f, 0.9f) : new Color(0.15f, 0.2f, 0.15f, 0.8f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            STATS_BUTTON_X, HISTORY_BUTTON_Y,
            HISTORY_BUTTON_WIDTH, HISTORY_BUTTON_HEIGHT);

        // Border
        sb.setColor(statsButtonHitbox.hovered ? Settings.GREEN_TEXT_COLOR : new Color(0.4f, 0.5f, 0.4f, 1.0f));
        float borderWidth = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, STATS_BUTTON_X, HISTORY_BUTTON_Y, HISTORY_BUTTON_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, STATS_BUTTON_X, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT - borderWidth, HISTORY_BUTTON_WIDTH, borderWidth);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, STATS_BUTTON_X, HISTORY_BUTTON_Y, borderWidth, HISTORY_BUTTON_HEIGHT);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, STATS_BUTTON_X + HISTORY_BUTTON_WIDTH - borderWidth, HISTORY_BUTTON_Y, borderWidth, HISTORY_BUTTON_HEIGHT);

        // Text
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Stats",
            STATS_BUTTON_X + HISTORY_BUTTON_WIDTH / 2.0f, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT / 2.0f,
            statsButtonHitbox.hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
    }

    private void renderSearchBox(SpriteBatch sb) {
        // Background
        Color bgColor = isTypingSearch ? new Color(0.2f, 0.2f, 0.3f, 0.9f) :
                        searchBoxHitbox.hovered ? new Color(0.2f, 0.2f, 0.25f, 0.8f) :
                        new Color(0.1f, 0.1f, 0.15f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            SEARCH_BOX_X, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f,
            SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT);

        // Border when active
        if (isTypingSearch) {
            sb.setColor(Settings.GOLD_COLOR);
            float bw = 2.0f * Settings.scale;
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, SEARCH_BOX_X, SEARCH_BOX_Y + SEARCH_BOX_HEIGHT / 2.0f - bw, SEARCH_BOX_WIDTH, bw);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, SEARCH_BOX_X, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, SEARCH_BOX_WIDTH, bw);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, SEARCH_BOX_X, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, bw, SEARCH_BOX_HEIGHT);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, SEARCH_BOX_X + SEARCH_BOX_WIDTH - bw, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, bw, SEARCH_BOX_HEIGHT);
        }

        // Text
        String displayText = searchText.isEmpty() ? "Search..." : searchText;
        Color textColor = searchText.isEmpty() ? new Color(0.5f, 0.5f, 0.5f, 1.0f) : Settings.CREAM_COLOR;
        if (isTypingSearch) {
            displayText = searchText + "|";
            textColor = Settings.CREAM_COLOR;
        }
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            displayText,
            SEARCH_BOX_X + SEARCH_BOX_WIDTH / 2.0f, SEARCH_BOX_Y, textColor);
    }

    private void renderFilterTabs(SpriteBatch sb) {
        String[] filterLabels = {"All", "IC", "SI", "DE", "WA"};
        String[] filterValues = {null, "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"};
        float filterStartX = LEFT_PANEL_X + 10.0f * Settings.scale;

        for (int i = 0; i < 5; i++) {
            float tabX = filterStartX + i * (FILTER_TAB_WIDTH + 5.0f * Settings.scale);
            boolean isSelected = (filterClass == null && filterValues[i] == null) ||
                                 (filterClass != null && filterClass.equals(filterValues[i]));
            boolean isHovered = classFilterHitboxes[i].hovered;

            // Background
            Color bgColor;
            if (isSelected) {
                bgColor = new Color(0.25f, 0.35f, 0.45f, 0.95f);
            } else if (isHovered) {
                bgColor = new Color(0.2f, 0.25f, 0.35f, 0.8f);
            } else {
                bgColor = new Color(0.1f, 0.12f, 0.18f, 0.6f);
            }
            sb.setColor(bgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                tabX, FILTER_TAB_Y - FILTER_TAB_HEIGHT / 2.0f,
                FILTER_TAB_WIDTH, FILTER_TAB_HEIGHT);

            // Text
            Color textColor = isSelected ? Settings.GOLD_COLOR : (isHovered ? Settings.CREAM_COLOR : new Color(0.7f, 0.7f, 0.7f, 1.0f));
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                filterLabels[i],
                tabX + FILTER_TAB_WIDTH / 2.0f, FILTER_TAB_Y, textColor);
        }
    }

    private void renderBulkOperations(SpriteBatch sb) {
        if (selectedLoadoutIds.isEmpty()) return;

        float buttonsY = 100.0f * Settings.scale;  // Moved up by 40px
        float startX = LEFT_PANEL_X + 10.0f * Settings.scale;

        // Selection count
        String countLabel = selectedLoadoutIds.size() + " selected";
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N, countLabel,
            startX, buttonsY + BULK_BUTTON_HEIGHT / 2.0f + 20.0f * Settings.scale, Settings.GOLD_COLOR);

        // Delete button
        renderBulkButton(sb, bulkDeleteHb, "Delete All",
            startX + BULK_BUTTON_WIDTH / 2.0f, buttonsY,
            new Color(0.5f, 0.2f, 0.2f, 0.9f), new Color(1.0f, 0.5f, 0.5f, 1.0f));

        // Unfavorite button
        renderBulkButton(sb, bulkUnfavoriteHb, "Unfavorite All",
            startX + BULK_BUTTON_WIDTH * 1.5f + 10.0f * Settings.scale, buttonsY,
            new Color(0.4f, 0.35f, 0.2f, 0.9f), new Color(0.9f, 0.8f, 0.4f, 1.0f));

        // Cancel button
        renderBulkButton(sb, cancelMultiSelectHb, "Cancel",
            startX + BULK_BUTTON_WIDTH * 2.5f + 20.0f * Settings.scale, buttonsY,
            new Color(0.25f, 0.25f, 0.3f, 0.9f), Settings.CREAM_COLOR);
    }

    private void renderBulkButton(SpriteBatch sb, Hitbox hb, String text, float x, float y, Color bgColor, Color textColor) {
        Color finalBgColor = hb.hovered ?
            new Color(bgColor.r * 1.3f, bgColor.g * 1.3f, bgColor.b * 1.3f, bgColor.a) : bgColor;
        Color finalTextColor = hb.hovered ? Settings.GOLD_COLOR : textColor;

        // Background
        sb.setColor(finalBgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            x - BULK_BUTTON_WIDTH / 2.0f, y - BULK_BUTTON_HEIGHT / 2.0f,
            BULK_BUTTON_WIDTH, BULK_BUTTON_HEIGHT);

        // Border
        sb.setColor(hb.hovered ? Settings.GOLD_COLOR : new Color(0.4f, 0.4f, 0.5f, 1.0f));
        float bw = 2.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - BULK_BUTTON_WIDTH / 2.0f, y - BULK_BUTTON_HEIGHT / 2.0f, BULK_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - BULK_BUTTON_WIDTH / 2.0f, y + BULK_BUTTON_HEIGHT / 2.0f - bw, BULK_BUTTON_WIDTH, bw);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x - BULK_BUTTON_WIDTH / 2.0f, y - BULK_BUTTON_HEIGHT / 2.0f, bw, BULK_BUTTON_HEIGHT);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, x + BULK_BUTTON_WIDTH / 2.0f - bw, y - BULK_BUTTON_HEIGHT / 2.0f, bw, BULK_BUTTON_HEIGHT);

        // Text
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N, text, x, y, finalTextColor);
    }
}
