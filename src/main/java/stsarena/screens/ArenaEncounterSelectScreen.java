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

        // Create hitboxes for each item
        hitboxes = new Hitbox[items.size()];
        for (int i = 0; i < items.size(); i++) {
            hitboxes[i] = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
        }
    }

    private void buildItemList() {
        items = new ArrayList<>();

        // Random option
        items.add(new ListItem("Random", null, false, true));

        // Act 1
        items.add(new ListItem("--- Act 1 ---", null, true, false));
        addEncounters(items, new String[]{
            "Cultist", "Jaw Worm", "2 Louse", "Small Slimes", "Blue Slaver",
            "Gremlin Gang", "Looter", "Large Slime", "Lots of Slimes",
            "Exordium Thugs", "Exordium Wildlife", "Red Slaver", "3 Louse", "2 Fungi Beasts"
        });
        items.add(new ListItem("Act 1 Elites", null, true, false));
        addEncounters(items, new String[]{"Gremlin Nob", "Lagavulin", "3 Sentries"});
        items.add(new ListItem("Act 1 Bosses", null, true, false));
        addEncounters(items, new String[]{"The Guardian", "Hexaghost", "Slime Boss"});

        // Act 2
        items.add(new ListItem("--- Act 2 ---", null, true, false));
        addEncounters(items, new String[]{
            "Chosen", "Shell Parasite", "Spheric Guardian", "3 Byrds", "2 Thieves",
            "Chosen and Byrds", "Sentry and Sphere", "Snake Plant", "Snecko",
            "Centurion and Healer", "Cultist and Chosen", "3 Cultists", "Shelled Parasite and Fungi"
        });
        items.add(new ListItem("Act 2 Elites", null, true, false));
        addEncounters(items, new String[]{"Gremlin Leader", "Slavers", "Book of Stabbing"});
        items.add(new ListItem("Act 2 Bosses", null, true, false));
        addEncounters(items, new String[]{"Automaton", "Collector", "Champ"});

        // Act 3
        items.add(new ListItem("--- Act 3 ---", null, true, false));
        addEncounters(items, new String[]{
            "3 Darklings", "Orb Walker", "3 Shapes", "Spire Growth", "Transient",
            "4 Shapes", "Maw", "Jaw Worm Horde", "Sphere and 2 Shapes", "Writhing Mass"
        });
        items.add(new ListItem("Act 3 Elites", null, true, false));
        addEncounters(items, new String[]{"Giant Head", "Nemesis", "Reptomancer"});
        items.add(new ListItem("Act 3 Bosses", null, true, false));
        addEncounters(items, new String[]{"Awakened One", "Time Eater", "Donu and Deca"});

        // Act 4
        items.add(new ListItem("--- Act 4 ---", null, true, false));
        addEncounters(items, new String[]{"Shield and Spear", "The Heart"});

        // Event Encounters
        items.add(new ListItem("--- Event Encounters ---", null, true, false));
        addEncounters(items, new String[]{
            "The Mushroom Lair",     // 3 Fungi Beasts (event version)
            "Masked Bandits",        // Red Mask gang event
            "Colosseum Slavers",     // Colosseum fight 1
            "Colosseum Nobs",        // Colosseum fight 2
            "2 Orb Walkers",         // Mind Bloom event
            "Mind Bloom Boss Battle" // Fight 2 act 1 bosses
        });
    }

    private void addEncounters(List<ListItem> list, String[] encounters) {
        for (String enc : encounters) {
            list.add(new ListItem(enc, enc, false, false));
        }
    }

    public void open() {
        STSArena.logger.info("Opening Arena Encounter Select Screen");
        this.isOpen = true;
        this.cancelButton.show("Back");
        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;

        // Load encounter outcomes for the selected loadout
        encounterOutcomes.clear();
        if (ArenaLoadoutSelectScreen.selectedSavedLoadout != null) {
            try {
                ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
                encounterOutcomes = repo.getEncounterOutcomesForLoadout(
                    ArenaLoadoutSelectScreen.selectedSavedLoadout.dbId);
                STSArena.logger.info("Loaded " + encounterOutcomes.size() + " encounter outcomes for loadout");
            } catch (Exception e) {
                STSArena.logger.error("Failed to load encounter outcomes", e);
            }
        }
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
    }

    public void update() {
        if (!isOpen) return;

        // Cancel button - go back to loadout selection
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            this.close();
            STSArena.openLoadoutSelectScreen();
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

    private void selectEncounter(ListItem item) {
        String encounter;
        if (item.isRandom) {
            encounter = RandomLoadoutGenerator.getRandomEncounter();
            STSArena.logger.info("Random encounter selected: " + encounter);
        } else {
            encounter = item.encounterId;
            STSArena.logger.info("Specific encounter selected: " + encounter);
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

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.8f));
        sb.draw(com.megacrit.cardcrawl.helpers.ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Select Encounter",
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
                    renderOption(sb, item.text, buttonY, hitboxes[i], item.isRandom, item.encounterId);
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
}
