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
    private static final float LEFT_PANEL_WIDTH = 380.0f * Settings.scale;
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

    // Selection state
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
    }

    public void open() {
        STSArena.logger.info("Opening Arena Loadout Select Screen");
        this.isOpen = true;
        this.cancelButton.show("Return");
        this.scrollY = 0.0f;
        this.targetScrollY = 0.0f;
        this.hoveredItem = null;

        // Reset selection state
        useNewRandomLoadout = false;
        selectedSavedLoadout = null;

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

        // Cancel button
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            this.close();
            return;
        }

        // History button
        historyButtonHitbox.move(HISTORY_BUTTON_X + HISTORY_BUTTON_WIDTH / 2.0f, HISTORY_BUTTON_Y + HISTORY_BUTTON_HEIGHT / 2.0f);
        historyButtonHitbox.update();
        if (historyButtonHitbox.hovered && InputHelper.justClickedLeft) {
            InputHelper.justClickedLeft = false;
            this.close();
            STSArena.openHistoryScreen();
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
                    if (InputHelper.justClickedLeft) {
                        selectLoadout(item);
                        return;
                    }
                }
            }
        }
    }

    private void selectLoadout(ListItem item) {
        if (item.isCustomCreate) {
            STSArena.logger.info("Opening custom loadout creator");
            this.close();
            STSArena.openLoadoutCreatorScreen();
            return;
        }

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
        // Background
        Color bgColor;
        if (hb.hovered) {
            bgColor = new Color(0.3f, 0.3f, 0.4f, 0.8f);
        } else {
            bgColor = new Color(0.1f, 0.1f, 0.15f, 0.6f);
        }

        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            LEFT_PANEL_X,
            y - BUTTON_HEIGHT,
            LEFT_PANEL_WIDTH - 20.0f * Settings.scale,
            BUTTON_HEIGHT);

        // Text
        Color textColor;
        if (item.isCustomCreate) {
            textColor = new Color(0.4f, 0.8f, 1.0f, 1.0f);  // Cyan
        } else if (item.isNewRandom) {
            textColor = Settings.GREEN_TEXT_COLOR;
        } else if (hb.hovered) {
            textColor = Settings.GOLD_COLOR;
        } else {
            textColor = Settings.CREAM_COLOR;
        }

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            item.text,
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

        if (hoveredItem == null) {
            // No selection - show instruction
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                "Hover over a loadout to preview",
                RIGHT_PANEL_X + RIGHT_PANEL_WIDTH / 2.0f, Settings.HEIGHT / 2.0f, Settings.CREAM_COLOR);
        } else if (hoveredItem.isNewRandom || hoveredItem.isCustomCreate) {
            // Random loadout - show question mark
            renderRandomPreview(sb);
        } else if (hoveredItem.savedLoadout != null) {
            // Saved loadout - show details
            renderLoadoutPreview(sb, hoveredItem.savedLoadout);
        }
    }

    private void renderRandomPreview(SpriteBatch sb) {
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
        String label = hoveredItem.isCustomCreate ? "Create New Custom Loadout" : "Random Loadout";
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

                // Render cards in columns
                float lineHeight = 22.0f * Settings.scale;
                float columnWidth = 180.0f * Settings.scale;
                int cardsPerColumn = 20;
                int column = 0;
                int row = 0;
                float startY = y;

                for (ArenaRepository.CardData cardData : cardDataList) {
                    AbstractCard card = CardLibrary.getCard(cardData.id);
                    if (card != null) {
                        String cardName = card.name;
                        if (cardData.upgrades > 0) {
                            cardName += "+";
                        }

                        // Truncate long names
                        if (cardName.length() > 16) {
                            cardName = cardName.substring(0, 14) + "..";
                        }

                        // Color by card type
                        Color cardColor = getCardTypeColor(card.type);
                        if (cardData.upgrades > 0) {
                            cardColor = Settings.GREEN_TEXT_COLOR;
                        }

                        float cardX = x + column * columnWidth;
                        float cardY = startY - row * lineHeight;

                        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                            cardName,
                            cardX, cardY, cardColor);

                        row++;
                        if (row >= cardsPerColumn) {
                            row = 0;
                            column++;
                        }
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
