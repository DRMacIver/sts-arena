package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for selecting which encounter to fight in arena mode.
 */
public class ArenaEncounterSelectScreen {

    private static final float TITLE_Y = Settings.HEIGHT - 100.0f * Settings.scale;
    private static final float LIST_START_Y = TITLE_Y - 100.0f * Settings.scale;
    private static final float ROW_HEIGHT = 45.0f * Settings.scale;
    private static final float BUTTON_WIDTH = 400.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 40.0f * Settings.scale;
    private static final float CENTER_X = Settings.WIDTH / 2.0f;

    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Scrolling
    private float scrollY = 0.0f;
    private float targetScrollY = 0.0f;

    // List items (headers and encounters)
    private List<ListItem> items;
    private Hitbox[] hitboxes;

    // Encounter outcomes for current loadout (encounter ID -> "VICTORY" or "DEFEAT")
    private Map<String, String> encounterOutcomes = new HashMap<>();

    // Track if we came from the pause menu (Practice in Arena button)
    private boolean openedFromPauseMenu = false;

    // Current encounter when opened from a fight (shown at top of list)
    private String currentEncounter = null;
    private Hitbox currentEncounterHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);

    // Search state
    private String searchText = "";
    private boolean isTypingSearch = false;
    private Hitbox searchBoxHitbox = new Hitbox(200.0f * Settings.scale, 30.0f * Settings.scale);
    private static final float SEARCH_BOX_WIDTH = 200.0f * Settings.scale;
    private static final float SEARCH_BOX_HEIGHT = 30.0f * Settings.scale;
    private static final float SEARCH_BOX_Y = TITLE_Y + 5.0f * Settings.scale;

    // All encounters (unfiltered) and filtered list
    private List<ListItem> allItems;
    private List<ListItem> filteredItems;

    private static class ListItem {
        String text;
        String encounterId; // null for headers
        boolean isHeader;
        boolean isRandom;

        ListItem(String text, String encounterId, boolean isHeader, boolean isRandom) {
            this.text = text;
            this.encounterId = encounterId;
            this.isHeader = isHeader;
            this.isRandom = isRandom;
        }
    }

    public ArenaEncounterSelectScreen() {
        this.cancelButton = new MenuCancelButton();
        buildItemList();
        applyFilter();
    }

    private void buildItemList() {
        allItems = new ArrayList<>();

        // Random option
        allItems.add(new ListItem("Random", null, false, true));

        // Build list from centralized encounter categories
        for (stsarena.arena.LoadoutConfig.EncounterCategory category : stsarena.arena.LoadoutConfig.ENCOUNTER_CATEGORIES) {
            // Add header
            allItems.add(new ListItem(category.header, null, true, false));
            // Add encounters
            addEncounters(allItems, category.encounters);
        }
    }

    private void addEncounters(List<ListItem> list, String[] encounters) {
        for (String enc : encounters) {
            list.add(new ListItem(enc, enc, false, false));
        }
    }

    /**
     * Apply search filter to encounters.
     */
    private void applyFilter() {
        if (searchText.isEmpty()) {
            // No filter - use all items
            items = new ArrayList<>(allItems);
        } else {
            items = new ArrayList<>();
            String lowerSearch = searchText.toLowerCase();
            String currentHeader = null;
            boolean headerAdded = false;

            for (ListItem item : allItems) {
                if (item.isHeader) {
                    currentHeader = item.text;
                    headerAdded = false;
                } else if (item.isRandom) {
                    // Always include Random option
                    items.add(item);
                } else if (item.text.toLowerCase().contains(lowerSearch)) {
                    // Add header if not yet added
                    if (!headerAdded && currentHeader != null) {
                        items.add(new ListItem(currentHeader, null, true, false));
                        headerAdded = true;
                    }
                    items.add(item);
                }
            }
        }

        // Create hitboxes for the filtered items
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
        }
    }

    public void open() {
        STSArena.logger.info("Opening Arena Encounter Select Screen");
        this.isOpen = true;
        this.openedFromPauseMenu = false;  // Reset when opening normally
        this.currentEncounter = null;       // Reset current encounter
        this.cancelButton.show("Back");
        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;

        // Reset search state
        this.searchText = "";
        this.isTypingSearch = false;
        applyFilter();

        // Load encounter outcomes for the selected loadout
        refreshEncounterOutcomes();
    }

    /**
     * Refresh the encounter outcomes from the database.
     * Called on open() and can be called to force refresh after fights.
     */
    public void refreshEncounterOutcomes() {
        encounterOutcomes.clear();
        if (ArenaLoadoutSelectScreen.selectedSavedLoadout != null) {
            try {
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                encounterOutcomes = repo.getEncounterOutcomesForLoadout(
                    ArenaLoadoutSelectScreen.selectedSavedLoadout.dbId);
                STSArena.logger.info("Loaded " + encounterOutcomes.size() + " encounter outcomes for loadout " +
                    ArenaLoadoutSelectScreen.selectedSavedLoadout.dbId);
            } catch (Exception e) {
                STSArena.logger.error("Failed to load encounter outcomes", e);
            }
        } else {
            STSArena.logger.info("No selectedSavedLoadout, cannot load encounter outcomes");
        }
    }

    /**
     * Open the encounter selection screen with a pre-selected loadout.
     * Used when entering arena from the pause menu (Practice in Arena button).
     */
    public void openWithLoadout(long loadoutId) {
        openWithLoadout(loadoutId, null);
    }

    /**
     * Open the encounter selection screen with a pre-selected loadout and optional current encounter.
     * Used when entering arena from the pause menu (Practice in Arena button) during a fight.
     */
    public void openWithLoadout(long loadoutId, String currentEncounter) {
        STSArena.logger.info("Opening Arena Encounter Select Screen with loadout ID: " + loadoutId +
            ", currentEncounter: " + currentEncounter);

        // Load the loadout from the database
        try {
            ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
            ArenaRepository.LoadoutRecord loadout = repo.getLoadoutById(loadoutId);
            if (loadout != null) {
                // Set it as the selected loadout
                ArenaLoadoutSelectScreen.selectedSavedLoadout = loadout;
                ArenaLoadoutSelectScreen.useNewRandomLoadout = false;
                STSArena.logger.info("Loaded loadout: " + loadout.name);
            } else {
                STSArena.logger.warn("Could not find loadout with ID: " + loadoutId);
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to load loadout by ID", e);
        }

        // Open the screen
        open();
        // Mark that we came from the pause menu (after open() resets it)
        this.openedFromPauseMenu = true;
        this.currentEncounter = currentEncounter;
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    public void update() {
        if (!isOpen) return;

        // Handle search text input
        if (isTypingSearch) {
            handleSearchInput();
        }

        // Update search box
        float searchX = CENTER_X + BUTTON_WIDTH / 2.0f - SEARCH_BOX_WIDTH - 10.0f * Settings.scale;
        searchBoxHitbox.move(searchX + SEARCH_BOX_WIDTH / 2.0f, SEARCH_BOX_Y);
        searchBoxHitbox.update();
        if (searchBoxHitbox.hovered && InputHelper.justClickedLeft) {
            isTypingSearch = true;
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !searchBoxHitbox.hovered && isTypingSearch) {
            isTypingSearch = false;
        }

        // Cancel button - go back to previous screen
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            this.close();

            if (openedFromPauseMenu) {
                // Go back to the settings/pause menu
                STSArena.logger.info("Returning to settings screen from arena encounter select");
                AbstractDungeon.settingsScreen.open();
            } else if (ArenaRunner.wasStartedFromNormalRun()) {
                // Return to the normal run we came from
                STSArena.logger.info("Returning to normal run from arena encounter select");
                ArenaRunner.resumeNormalRun();
            } else {
                // Go back to loadout selection
                STSArena.openLoadoutSelectScreen();
            }
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

        // Handle current encounter hitbox (special entry at top)
        if (currentEncounter != null) {
            float currentEncounterY = LIST_START_Y - ROW_HEIGHT;
            currentEncounterHb.move(CENTER_X, currentEncounterY - BUTTON_HEIGHT / 2.0f);
            currentEncounterHb.update();

            if (currentEncounterHb.hovered && InputHelper.justClickedLeft) {
                selectCurrentEncounter();
                return;
            }
        }

        // Calculate offset for regular items when current encounter is shown
        float currentEncounterOffset = currentEncounter != null ? ROW_HEIGHT * 2.5f : 0;

        // Update hitboxes and check for clicks
        float y = LIST_START_Y - currentEncounterOffset + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Skip headers for interaction
            if (item.isHeader) continue;

            // Only update hitboxes that are visible
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT) {
                hitboxes[i].move(CENTER_X, buttonY - BUTTON_HEIGHT / 2.0f);
                hitboxes[i].update();

                if (hitboxes[i].hovered && InputHelper.justClickedLeft) {
                    selectEncounter(item);
                    return;
                }
            }
        }
    }

    /**
     * Select the current encounter (from a fight).
     */
    private void selectCurrentEncounter() {
        STSArena.logger.info("Current encounter selected: " + currentEncounter);

        // If we came from the pause menu, mark that we're starting from a normal run
        if (openedFromPauseMenu && AbstractDungeon.player != null) {
            ArenaRunner.setStartedFromNormalRun(AbstractDungeon.player.chosenClass);
        }

        this.close();

        // Use the loadout selection from ArenaLoadoutSelectScreen
        if (ArenaLoadoutSelectScreen.useNewRandomLoadout) {
            // Generate new random loadout (shouldn't happen from Practice in Arena)
            RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
            ArenaRunner.startFight(loadout, currentEncounter);
        } else if (ArenaLoadoutSelectScreen.selectedSavedLoadout != null) {
            // Use saved loadout
            ArenaRunner.startFightWithSavedLoadout(ArenaLoadoutSelectScreen.selectedSavedLoadout, currentEncounter);
        } else {
            // Fallback - should not happen
            STSArena.logger.warn("No loadout selected, generating random");
            RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
            ArenaRunner.startFight(loadout, currentEncounter);
        }
    }

    private void selectEncounter(ListItem item) {
        String encounter;
        if (item.isRandom) {
            encounter = RandomLoadoutGenerator.getRandomEncounter();
            STSArena.logger.info("Random encounter selected: " + encounter);
        } else {
            encounter = item.encounterId;
            STSArena.logger.info("Specific encounter selected: " + encounter);
        }

        // If we came from the pause menu, mark that we're starting from a normal run
        // This enables returning to the original game when leaving arena
        if (openedFromPauseMenu && AbstractDungeon.player != null) {
            ArenaRunner.setStartedFromNormalRun(AbstractDungeon.player.chosenClass);
        }

        this.close();

        // Use the loadout selection from ArenaLoadoutSelectScreen
        if (ArenaLoadoutSelectScreen.useNewRandomLoadout) {
            // Generate new random loadout
            RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
            ArenaRunner.startFight(loadout, encounter);
        } else if (ArenaLoadoutSelectScreen.selectedSavedLoadout != null) {
            // Use saved loadout
            ArenaRunner.startFightWithSavedLoadout(ArenaLoadoutSelectScreen.selectedSavedLoadout, encounter);
        } else {
            // Fallback - should not happen
            STSArena.logger.warn("No loadout selected, generating random");
            RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
            ArenaRunner.startFight(loadout, encounter);
        }
    }

    private void handleSearchInput() {
        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            applyFilter();
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
                applyFilter();
                scrollY = 0;
                targetScrollY = 0;
            }
        }

        // Handle numbers
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                searchText += c;
                applyFilter();
                scrollY = 0;
                targetScrollY = 0;
            }
        }

        // Handle space
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            searchText += ' ';
            applyFilter();
            scrollY = 0;
            targetScrollY = 0;
        }
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.8f));
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Select Encounter",
            CENTER_X, TITLE_Y, Settings.GOLD_COLOR);

        // Search box
        renderSearchBox(sb);

        // Calculate offset for current encounter section
        float currentEncounterOffset = 0;
        if (currentEncounter != null) {
            // Render current encounter section at the top
            float currentY = LIST_START_Y;

            // Header for current encounter
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                "--- Current Fight ---",
                CENTER_X, currentY - BUTTON_HEIGHT / 2.0f, new Color(1.0f, 0.8f, 0.2f, 1.0f));

            currentY -= ROW_HEIGHT;

            // Render the current encounter button with special styling
            renderCurrentEncounter(sb, currentEncounter, currentY);

            // Add spacing after current encounter section
            currentEncounterOffset = ROW_HEIGHT * 2.5f;
        }

        // Render regular items
        float y = LIST_START_Y - currentEncounterOffset + scrollY;
        for (int i = 0; i < items.size(); i++) {
            ListItem item = items.get(i);
            float buttonY = y - i * ROW_HEIGHT;

            // Only render visible items
            if (buttonY > -BUTTON_HEIGHT && buttonY < Settings.HEIGHT - 50.0f * Settings.scale) {
                if (item.isHeader) {
                    renderHeader(sb, item.text, buttonY);
                } else {
                    renderOption(sb, item.text, buttonY, hitboxes[i], item.isRandom, item.encounterId);
                }
            }
        }

        // Cancel button
        this.cancelButton.render(sb);
    }

    private void renderCurrentEncounter(SpriteBatch sb, String text, float y) {
        // Special background for current encounter (highlighted)
        Color bgColor;
        if (currentEncounterHb.hovered) {
            bgColor = new Color(0.4f, 0.35f, 0.2f, 0.9f);
        } else {
            bgColor = new Color(0.25f, 0.2f, 0.1f, 0.8f);
        }

        sb.setColor(bgColor);
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG,
            CENTER_X - BUTTON_WIDTH / 2.0f,
            y - BUTTON_HEIGHT,
            BUTTON_WIDTH,
            BUTTON_HEIGHT);

        // Gold text for current encounter
        Color textColor = currentEncounterHb.hovered ? Settings.GOLD_COLOR : new Color(1.0f, 0.9f, 0.6f, 1.0f);

        FontHelper.renderFontCentered(sb, FontHelper.tipHeaderFont,
            text,
            CENTER_X, y - BUTTON_HEIGHT / 2.0f, textColor);

        currentEncounterHb.render(sb);
    }

    private void renderHeader(SpriteBatch sb, String text, float y) {
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            text,
            CENTER_X, y - BUTTON_HEIGHT / 2.0f, Settings.GOLD_COLOR);
    }

    private void renderOption(SpriteBatch sb, String text, float y, Hitbox hb, boolean isRandom, String encounterId) {
        // Check outcome for this encounter
        String outcome = encounterId != null ? encounterOutcomes.get(encounterId) : null;
        boolean hasOutcome = outcome != null;

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

        // Text color based on outcome
        Color textColor;
        if (isRandom) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if ("VICTORY".equals(outcome)) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if ("DEFEAT".equals(outcome)) {
            textColor = Settings.RED_TEXT_COLOR;
        } else if (hb.hovered) {
            textColor = Settings.GOLD_COLOR;
        } else {
            textColor = Settings.CREAM_COLOR;
        }

        // Use bold font (tipHeaderFont) for encounters with outcomes
        com.badlogic.gdx.graphics.g2d.BitmapFont font = hasOutcome ?
            FontHelper.tipHeaderFont : FontHelper.cardDescFont_N;

        FontHelper.renderFontCentered(sb, font,
            text,
            CENTER_X, y - BUTTON_HEIGHT / 2.0f, textColor);
    }

    private void renderSearchBox(SpriteBatch sb) {
        float searchX = CENTER_X + BUTTON_WIDTH / 2.0f - SEARCH_BOX_WIDTH - 10.0f * Settings.scale;

        // Background
        Color bgColor = isTypingSearch ? new Color(0.2f, 0.2f, 0.3f, 0.9f) :
                        searchBoxHitbox.hovered ? new Color(0.2f, 0.2f, 0.25f, 0.8f) :
                        new Color(0.1f, 0.1f, 0.15f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG,
            searchX, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f,
            SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT);

        // Border when active
        if (isTypingSearch) {
            sb.setColor(Settings.GOLD_COLOR);
            float bw = 2.0f * Settings.scale;
            sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, searchX, SEARCH_BOX_Y + SEARCH_BOX_HEIGHT / 2.0f - bw, SEARCH_BOX_WIDTH, bw);
            sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, searchX, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, SEARCH_BOX_WIDTH, bw);
            sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, searchX, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, bw, SEARCH_BOX_HEIGHT);
            sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, searchX + SEARCH_BOX_WIDTH - bw, SEARCH_BOX_Y - SEARCH_BOX_HEIGHT / 2.0f, bw, SEARCH_BOX_HEIGHT);
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
            searchX + SEARCH_BOX_WIDTH / 2.0f, SEARCH_BOX_Y, textColor);
    }
}
