package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import stsarena.STSArena;
import stsarena.arena.LoadoutConfig;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Screen for creating a custom loadout.
 * Allows selecting character, searching/adding cards, and toggling upgrades.
 */
public class LoadoutCreatorScreen {

    // Layout constants
    private static final float TITLE_Y = Settings.HEIGHT - 80.0f * Settings.scale;
    private static final float TAB_Y = TITLE_Y - 60.0f * Settings.scale;
    private static final float SEARCH_Y = TAB_Y - 50.0f * Settings.scale;
    private static final float LIST_START_Y = SEARCH_Y - 60.0f * Settings.scale;

    private static final float ROW_HEIGHT = 35.0f * Settings.scale;
    private static final float COLUMN_WIDTH = 400.0f * Settings.scale;
    private static final float LEFT_COLUMN_X = Settings.WIDTH * 0.28f;
    private static final float RIGHT_COLUMN_X = Settings.WIDTH * 0.72f;

    private static final float TAB_WIDTH = 120.0f * Settings.scale;
    private static final float TAB_HEIGHT = 35.0f * Settings.scale;
    private static final float TAB_START_X = Settings.WIDTH / 2.0f - 2.0f * TAB_WIDTH;

    private static final float BUTTON_HEIGHT = 30.0f * Settings.scale;
    private static final float ADD_BUTTON_WIDTH = 30.0f * Settings.scale;

    // UI components
    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Character selection
    private AbstractPlayer.PlayerClass selectedClass = AbstractPlayer.PlayerClass.IRONCLAD;
    private Hitbox[] characterTabHitboxes;

    // Search
    private String searchText = "";
    private boolean isTypingSearch = false;
    private Hitbox searchBoxHitbox;

    // Available cards (left panel)
    private List<AbstractCard> availableCards;
    private Hitbox[] availableCardHitboxes;
    private float availableScrollY = 0.0f;
    private float availableTargetScrollY = 0.0f;

    // Deck cards (right panel)
    private List<DeckCard> deckCards;
    private Hitbox[] deckCardUpgradeHitboxes;
    private Hitbox[] deckCardRemoveHitboxes;
    private float deckScrollY = 0.0f;
    private float deckTargetScrollY = 0.0f;

    // Save button
    private Hitbox saveButtonHitbox;

    // Visible list height
    private static final float LIST_HEIGHT = 450.0f * Settings.scale;

    /**
     * A card in the deck with its upgrade status.
     */
    private static class DeckCard {
        AbstractCard card;
        boolean upgraded;

        DeckCard(AbstractCard card) {
            this.card = card;
            this.upgraded = false;
        }
    }

    public LoadoutCreatorScreen() {
        this.cancelButton = new MenuCancelButton();
        this.availableCards = new ArrayList<>();
        this.deckCards = new ArrayList<>();

        // Create character tab hitboxes
        characterTabHitboxes = new Hitbox[4];
        for (int i = 0; i < 4; i++) {
            characterTabHitboxes[i] = new Hitbox(TAB_WIDTH, TAB_HEIGHT);
        }

        // Search box hitbox
        searchBoxHitbox = new Hitbox(300.0f * Settings.scale, 30.0f * Settings.scale);

        // Save button hitbox
        saveButtonHitbox = new Hitbox(150.0f * Settings.scale, 40.0f * Settings.scale);
    }

    public void open() {
        STSArena.logger.info("Opening Loadout Creator Screen");
        this.isOpen = true;
        this.cancelButton.show("Cancel");

        // Reset state
        this.selectedClass = AbstractPlayer.PlayerClass.IRONCLAD;
        this.searchText = "";
        this.isTypingSearch = false;
        this.deckCards.clear();
        this.availableScrollY = 0.0f;
        this.availableTargetScrollY = 0.0f;
        this.deckScrollY = 0.0f;
        this.deckTargetScrollY = 0.0f;

        // Build available cards list
        refreshAvailableCards();
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
        this.isTypingSearch = false;
    }

    private void refreshAvailableCards() {
        availableCards.clear();

        AbstractCard.CardColor targetColor = LoadoutConfig.getCardColor(selectedClass);

        for (AbstractCard card : CardLibrary.cards.values()) {
            // Include character cards + colorless
            if (card.color != targetColor && card.color != AbstractCard.CardColor.COLORLESS) {
                continue;
            }

            // Skip basic/special/status/curse
            if (card.rarity == AbstractCard.CardRarity.BASIC ||
                card.rarity == AbstractCard.CardRarity.SPECIAL) {
                continue;
            }
            if (card.type == AbstractCard.CardType.STATUS ||
                card.type == AbstractCard.CardType.CURSE) {
                continue;
            }

            // Filter by search
            if (!searchText.isEmpty() &&
                !card.name.toLowerCase().contains(searchText.toLowerCase())) {
                continue;
            }

            availableCards.add(card);
        }

        // Sort alphabetically
        availableCards.sort(Comparator.comparing(c -> c.name));

        // Create hitboxes for available cards
        availableCardHitboxes = new Hitbox[availableCards.size()];
        for (int i = 0; i < availableCards.size(); i++) {
            availableCardHitboxes[i] = new Hitbox(COLUMN_WIDTH - 50.0f * Settings.scale, BUTTON_HEIGHT);
        }

        // Reset scroll
        availableScrollY = 0.0f;
        availableTargetScrollY = 0.0f;
    }

    private void refreshDeckHitboxes() {
        deckCardUpgradeHitboxes = new Hitbox[deckCards.size()];
        deckCardRemoveHitboxes = new Hitbox[deckCards.size()];
        for (int i = 0; i < deckCards.size(); i++) {
            deckCardUpgradeHitboxes[i] = new Hitbox(ADD_BUTTON_WIDTH, BUTTON_HEIGHT);
            deckCardRemoveHitboxes[i] = new Hitbox(ADD_BUTTON_WIDTH, BUTTON_HEIGHT);
        }
    }

    public void update() {
        if (!isOpen) return;

        // Cancel button
        this.cancelButton.update();
        if (this.cancelButton.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            this.cancelButton.hb.clicked = false;
            this.close();
            STSArena.openLoadoutSelectScreen();
            return;
        }

        // Handle text input for search
        if (isTypingSearch) {
            handleSearchInput();
        }

        // Update search box hitbox
        searchBoxHitbox.move(Settings.WIDTH / 2.0f, SEARCH_Y);
        searchBoxHitbox.update();
        if (searchBoxHitbox.hovered && InputHelper.justClickedLeft) {
            isTypingSearch = true;
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !searchBoxHitbox.hovered) {
            isTypingSearch = false;
        }

        // Update character tabs
        for (int i = 0; i < 4; i++) {
            float tabX = TAB_START_X + i * TAB_WIDTH + TAB_WIDTH / 2.0f;
            characterTabHitboxes[i].move(tabX, TAB_Y);
            characterTabHitboxes[i].update();

            if (characterTabHitboxes[i].hovered && InputHelper.justClickedLeft) {
                selectedClass = LoadoutConfig.PLAYER_CLASSES[i];
                refreshAvailableCards();
                InputHelper.justClickedLeft = false;
            }
        }

        // Update save button
        saveButtonHitbox.move(Settings.WIDTH - 120.0f * Settings.scale, TITLE_Y);
        saveButtonHitbox.update();
        if (saveButtonHitbox.hovered && InputHelper.justClickedLeft && !deckCards.isEmpty()) {
            saveLoadout();
            InputHelper.justClickedLeft = false;
            return;
        }

        // Scrolling for available cards (left panel) - when mouse is on left side
        if (InputHelper.mX < Settings.WIDTH / 2.0f) {
            if (InputHelper.scrolledDown) {
                availableTargetScrollY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                availableTargetScrollY -= Settings.SCROLL_SPEED;
            }
        }

        // Scrolling for deck (right panel) - when mouse is on right side
        if (InputHelper.mX >= Settings.WIDTH / 2.0f) {
            if (InputHelper.scrolledDown) {
                deckTargetScrollY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                deckTargetScrollY -= Settings.SCROLL_SPEED;
            }
        }

        // Clamp scrolling
        float availableMaxScroll = Math.max(0, availableCards.size() * ROW_HEIGHT - LIST_HEIGHT);
        availableTargetScrollY = Math.max(0, Math.min(availableMaxScroll, availableTargetScrollY));
        availableScrollY = MathHelper.scrollSnapLerpSpeed(availableScrollY, availableTargetScrollY);

        float deckMaxScroll = Math.max(0, deckCards.size() * ROW_HEIGHT - LIST_HEIGHT);
        deckTargetScrollY = Math.max(0, Math.min(deckMaxScroll, deckTargetScrollY));
        deckScrollY = MathHelper.scrollSnapLerpSpeed(deckScrollY, deckTargetScrollY);

        // Update available cards hitboxes and check for clicks
        float y = LIST_START_Y + availableScrollY;
        for (int i = 0; i < availableCards.size(); i++) {
            float cardY = y - i * ROW_HEIGHT;

            if (cardY > LIST_START_Y - LIST_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT) {
                availableCardHitboxes[i].move(LEFT_COLUMN_X, cardY - BUTTON_HEIGHT / 2.0f);
                availableCardHitboxes[i].update();

                if (availableCardHitboxes[i].hovered && InputHelper.justClickedLeft) {
                    addCardToDeck(availableCards.get(i));
                    InputHelper.justClickedLeft = false;
                }
            }
        }

        // Update deck hitboxes and check for clicks
        y = LIST_START_Y + deckScrollY;
        for (int i = 0; i < deckCards.size(); i++) {
            float cardY = y - i * ROW_HEIGHT;

            if (cardY > LIST_START_Y - LIST_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT) {
                float buttonX = RIGHT_COLUMN_X + COLUMN_WIDTH / 2.0f - 70.0f * Settings.scale;

                // Upgrade button
                deckCardUpgradeHitboxes[i].move(buttonX, cardY - BUTTON_HEIGHT / 2.0f);
                deckCardUpgradeHitboxes[i].update();
                if (deckCardUpgradeHitboxes[i].hovered && InputHelper.justClickedLeft) {
                    toggleUpgrade(i);
                    InputHelper.justClickedLeft = false;
                }

                // Remove button
                deckCardRemoveHitboxes[i].move(buttonX + 40.0f * Settings.scale, cardY - BUTTON_HEIGHT / 2.0f);
                deckCardRemoveHitboxes[i].update();
                if (deckCardRemoveHitboxes[i].hovered && InputHelper.justClickedLeft) {
                    removeCardFromDeck(i);
                    InputHelper.justClickedLeft = false;
                }
            }
        }
    }

    private void handleSearchInput() {
        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            refreshAvailableCards();
        }

        // Handle escape or enter to stop typing
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            isTypingSearch = false;
        }

        // Handle typed characters (A-Z, 0-9, space)
        for (int keycode = com.badlogic.gdx.Input.Keys.A; keycode <= com.badlogic.gdx.Input.Keys.Z; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('a' + (keycode - com.badlogic.gdx.Input.Keys.A));
                searchText += c;
                refreshAvailableCards();
            }
        }
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                searchText += c;
                refreshAvailableCards();
            }
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            searchText += ' ';
            refreshAvailableCards();
        }
    }

    private void addCardToDeck(AbstractCard card) {
        DeckCard dc = new DeckCard(card.makeCopy());
        deckCards.add(dc);
        refreshDeckHitboxes();
        STSArena.logger.info("Added card to deck: " + card.name);
    }

    private void removeCardFromDeck(int index) {
        if (index >= 0 && index < deckCards.size()) {
            STSArena.logger.info("Removed card from deck: " + deckCards.get(index).card.name);
            deckCards.remove(index);
            refreshDeckHitboxes();
        }
    }

    private void toggleUpgrade(int index) {
        if (index >= 0 && index < deckCards.size()) {
            DeckCard dc = deckCards.get(index);
            if (dc.card.canUpgrade() || dc.upgraded) {
                dc.upgraded = !dc.upgraded;
                STSArena.logger.info("Toggled upgrade for: " + dc.card.name + " -> " + dc.upgraded);
            }
        }
    }

    private void saveLoadout() {
        if (deckCards.isEmpty()) return;

        // Build deck with upgrades
        List<AbstractCard> deck = new ArrayList<>();
        for (DeckCard dc : deckCards) {
            AbstractCard copy = dc.card.makeCopy();
            if (dc.upgraded && copy.canUpgrade()) {
                copy.upgrade();
            }
            deck.add(copy);
        }

        // Default values for MVP
        int maxHp = LoadoutConfig.getBaseMaxHp(selectedClass);
        int potionSlots = 3;
        int ascension = 0;

        // Just starter relic
        List<AbstractRelic> relics = new ArrayList<>();
        String starterRelicId = LoadoutConfig.getStarterRelicId(selectedClass);
        if (starterRelicId != null) {
            AbstractRelic starterRelic = RelicLibrary.getRelic(starterRelicId);
            if (starterRelic != null) {
                relics.add(starterRelic.makeCopy());
            }
        }

        List<AbstractPotion> potions = new ArrayList<>();

        // Generate name
        String className = selectedClass.name();
        className = className.substring(0, 1) + className.substring(1).toLowerCase().replace("_", " ");

        RandomLoadoutGenerator.GeneratedLoadout loadout = new RandomLoadoutGenerator.GeneratedLoadout(
            UUID.randomUUID().toString(),
            "Custom " + className,
            System.currentTimeMillis(),
            selectedClass,
            deck,
            relics,
            potions,
            potionSlots,
            false,  // hasPrismaticShard
            maxHp,
            maxHp,
            ascension
        );

        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        long dbId = repo.saveLoadout(loadout);

        if (dbId > 0) {
            STSArena.logger.info("Saved custom loadout with " + deck.size() + " cards");
        }

        close();
        STSArena.openLoadoutSelectScreen();
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.9f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Create Custom Loadout",
            Settings.WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Save button
        Color saveBgColor = saveButtonHitbox.hovered && !deckCards.isEmpty() ?
            new Color(0.2f, 0.5f, 0.2f, 0.9f) : new Color(0.1f, 0.3f, 0.1f, 0.6f);
        if (deckCards.isEmpty()) {
            saveBgColor = new Color(0.2f, 0.2f, 0.2f, 0.4f);
        }
        sb.setColor(saveBgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            saveButtonHitbox.x, saveButtonHitbox.y,
            saveButtonHitbox.width, saveButtonHitbox.height);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Save",
            saveButtonHitbox.cX, saveButtonHitbox.cY,
            deckCards.isEmpty() ? Settings.CREAM_COLOR : Settings.GREEN_TEXT_COLOR);

        // Character tabs
        renderCharacterTabs(sb);

        // Search box
        renderSearchBox(sb);

        // Column headers
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Available Cards (" + availableCards.size() + ")",
            LEFT_COLUMN_X, LIST_START_Y + 30.0f * Settings.scale, Settings.GOLD_COLOR);

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Your Deck (" + deckCards.size() + " cards)",
            RIGHT_COLUMN_X, LIST_START_Y + 30.0f * Settings.scale, Settings.GOLD_COLOR);

        // Render available cards (left panel)
        renderAvailableCards(sb);

        // Render deck cards (right panel)
        renderDeckCards(sb);

        // Cancel button
        this.cancelButton.render(sb);
    }

    private void renderCharacterTabs(SpriteBatch sb) {
        String[] tabNames = {"Ironclad", "Silent", "Defect", "Watcher"};

        for (int i = 0; i < 4; i++) {
            float tabX = TAB_START_X + i * TAB_WIDTH;
            boolean isSelected = LoadoutConfig.PLAYER_CLASSES[i] == selectedClass;
            boolean isHovered = characterTabHitboxes[i].hovered;

            // Background
            Color bgColor;
            if (isSelected) {
                bgColor = new Color(0.3f, 0.4f, 0.5f, 0.9f);
            } else if (isHovered) {
                bgColor = new Color(0.2f, 0.3f, 0.4f, 0.7f);
            } else {
                bgColor = new Color(0.1f, 0.15f, 0.2f, 0.5f);
            }
            sb.setColor(bgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, tabX, TAB_Y - TAB_HEIGHT / 2.0f, TAB_WIDTH, TAB_HEIGHT);

            // Text
            Color textColor = isSelected ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                tabNames[i],
                tabX + TAB_WIDTH / 2.0f, TAB_Y, textColor);
        }
    }

    private void renderSearchBox(SpriteBatch sb) {
        // Background
        Color bgColor = isTypingSearch ? new Color(0.2f, 0.2f, 0.3f, 0.9f) : new Color(0.1f, 0.1f, 0.15f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            searchBoxHitbox.x, searchBoxHitbox.y,
            searchBoxHitbox.width, searchBoxHitbox.height);

        // Border if typing
        if (isTypingSearch) {
            sb.setColor(Settings.GOLD_COLOR);
            // Top border
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y + searchBoxHitbox.height - 2, searchBoxHitbox.width, 2);
            // Bottom border
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y, searchBoxHitbox.width, 2);
            // Left border
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y, 2, searchBoxHitbox.height);
            // Right border
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x + searchBoxHitbox.width - 2, searchBoxHitbox.y, 2, searchBoxHitbox.height);
        }

        // Text
        String displayText = searchText.isEmpty() ? "Search cards..." : searchText;
        Color textColor = searchText.isEmpty() ? new Color(0.5f, 0.5f, 0.5f, 1.0f) : Settings.CREAM_COLOR;
        if (isTypingSearch) {
            displayText = searchText + "|";  // Cursor
        }
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            displayText,
            searchBoxHitbox.cX, searchBoxHitbox.cY, textColor);
    }

    private void renderAvailableCards(SpriteBatch sb) {
        float y = LIST_START_Y + availableScrollY;

        for (int i = 0; i < availableCards.size(); i++) {
            float cardY = y - i * ROW_HEIGHT;

            // Only render visible cards
            if (cardY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT) {
                AbstractCard card = availableCards.get(i);
                boolean hovered = i < availableCardHitboxes.length && availableCardHitboxes[i].hovered;

                // Background
                Color bgColor = hovered ? new Color(0.2f, 0.3f, 0.2f, 0.8f) : new Color(0.1f, 0.1f, 0.15f, 0.5f);
                sb.setColor(bgColor);
                float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    LEFT_COLUMN_X - rowWidth / 2.0f, cardY - BUTTON_HEIGHT,
                    rowWidth, BUTTON_HEIGHT);

                // Card name with cost
                String costStr = card.cost >= 0 ? "[" + card.cost + "] " : "";
                Color textColor = hovered ? Settings.GREEN_TEXT_COLOR : getCardTypeColor(card.type);
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    costStr + card.name,
                    LEFT_COLUMN_X - rowWidth / 2.0f + 10.0f * Settings.scale,
                    cardY - 5.0f * Settings.scale, textColor);

                // [+] indicator on right
                FontHelper.renderFontRightTopAligned(sb, FontHelper.cardDescFont_N,
                    "+",
                    LEFT_COLUMN_X + rowWidth / 2.0f - 10.0f * Settings.scale,
                    cardY - 5.0f * Settings.scale,
                    hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            }
        }
    }

    private void renderDeckCards(SpriteBatch sb) {
        float y = LIST_START_Y + deckScrollY;

        for (int i = 0; i < deckCards.size(); i++) {
            float cardY = y - i * ROW_HEIGHT;

            // Only render visible cards
            if (cardY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT) {
                DeckCard dc = deckCards.get(i);
                AbstractCard card = dc.card;

                // Background
                Color bgColor = new Color(0.1f, 0.1f, 0.15f, 0.5f);
                sb.setColor(bgColor);
                float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    RIGHT_COLUMN_X - rowWidth / 2.0f, cardY - BUTTON_HEIGHT,
                    rowWidth, BUTTON_HEIGHT);

                // Card name (with + if upgraded)
                String name = card.name + (dc.upgraded ? "+" : "");
                Color textColor = dc.upgraded ? Settings.GREEN_TEXT_COLOR : getCardTypeColor(card.type);
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    name,
                    RIGHT_COLUMN_X - rowWidth / 2.0f + 10.0f * Settings.scale,
                    cardY - 5.0f * Settings.scale, textColor);

                // Upgrade button [U]
                boolean upgradeHovered = i < deckCardUpgradeHitboxes.length && deckCardUpgradeHitboxes[i].hovered;
                boolean canUpgrade = card.canUpgrade();
                Color upgradeBg = upgradeHovered && canUpgrade ? new Color(0.3f, 0.4f, 0.3f, 0.9f) : new Color(0.15f, 0.2f, 0.15f, 0.6f);
                if (!canUpgrade && !dc.upgraded) {
                    upgradeBg = new Color(0.2f, 0.2f, 0.2f, 0.3f);
                }
                float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 80.0f * Settings.scale;
                sb.setColor(upgradeBg);
                sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - ADD_BUTTON_WIDTH / 2.0f, cardY - BUTTON_HEIGHT, ADD_BUTTON_WIDTH, BUTTON_HEIGHT);
                FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                    dc.upgraded ? "U" : "u",
                    buttonX, cardY - BUTTON_HEIGHT / 2.0f,
                    dc.upgraded ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);

                // Remove button [-]
                boolean removeHovered = i < deckCardRemoveHitboxes.length && deckCardRemoveHitboxes[i].hovered;
                Color removeBg = removeHovered ? new Color(0.4f, 0.2f, 0.2f, 0.9f) : new Color(0.2f, 0.1f, 0.1f, 0.6f);
                float removeX = buttonX + 40.0f * Settings.scale;
                sb.setColor(removeBg);
                sb.draw(ImageMaster.WHITE_SQUARE_IMG, removeX - ADD_BUTTON_WIDTH / 2.0f, cardY - BUTTON_HEIGHT, ADD_BUTTON_WIDTH, BUTTON_HEIGHT);
                FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                    "-",
                    removeX, cardY - BUTTON_HEIGHT / 2.0f,
                    removeHovered ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);
            }
        }
    }

    private Color getCardTypeColor(AbstractCard.CardType type) {
        switch (type) {
            case ATTACK:
                return new Color(0.9f, 0.4f, 0.4f, 1.0f);  // Reddish
            case SKILL:
                return new Color(0.4f, 0.7f, 0.9f, 1.0f);  // Bluish
            case POWER:
                return new Color(0.9f, 0.8f, 0.3f, 1.0f);  // Yellowish
            default:
                return Settings.CREAM_COLOR;
        }
    }
}
