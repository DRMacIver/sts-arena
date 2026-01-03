package stsarena.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.PrismaticShard;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import stsarena.STSArena;
import stsarena.arena.LoadoutConfig;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Screen for creating a custom loadout.
 * Allows selecting character, cards, relics, potions, HP, and ascension.
 */
public class LoadoutCreatorScreen {

    // Layout constants
    private static final float TITLE_Y = Settings.HEIGHT - 60.0f * Settings.scale;
    private static final float CHAR_TAB_Y = TITLE_Y - 50.0f * Settings.scale;
    private static final float STATS_Y = CHAR_TAB_Y - 45.0f * Settings.scale;
    private static final float CONTENT_TAB_Y = STATS_Y - 45.0f * Settings.scale;
    private static final float SEARCH_Y = CONTENT_TAB_Y - 40.0f * Settings.scale;
    private static final float LIST_START_Y = SEARCH_Y - 50.0f * Settings.scale;

    private static final float ROW_HEIGHT = 32.0f * Settings.scale;
    private static final float COLUMN_WIDTH = 380.0f * Settings.scale;
    private static final float LEFT_COLUMN_X = Settings.WIDTH * 0.25f;
    private static final float RIGHT_COLUMN_X = Settings.WIDTH * 0.70f;

    private static final float CHAR_TAB_WIDTH = 100.0f * Settings.scale;
    private static final float CHAR_TAB_HEIGHT = 30.0f * Settings.scale;
    private static final float CHAR_TAB_START_X = Settings.WIDTH / 2.0f - 2.0f * CHAR_TAB_WIDTH;

    private static final float CONTENT_TAB_WIDTH = 100.0f * Settings.scale;
    private static final float CONTENT_TAB_HEIGHT = 28.0f * Settings.scale;

    private static final float BUTTON_HEIGHT = 28.0f * Settings.scale;
    private static final float SMALL_BUTTON_WIDTH = 28.0f * Settings.scale;

    // List heights
    private static final float LIST_HEIGHT = 380.0f * Settings.scale;

    // Content tab enum
    private enum ContentTab { CARDS, RELICS, POTIONS }

    // UI components
    private MenuCancelButton cancelButton;
    public boolean isOpen = false;

    // Character selection
    private AbstractPlayer.PlayerClass selectedClass = AbstractPlayer.PlayerClass.IRONCLAD;
    private Hitbox[] characterTabHitboxes;

    // Content tab selection
    private ContentTab activeTab = ContentTab.CARDS;
    private Hitbox[] contentTabHitboxes;

    // Stats
    private int currentHp;
    private int maxHp;
    private int ascensionLevel = 0;
    private Hitbox hpMinusHitbox, hpPlusHitbox, hpValueHitbox;
    private Hitbox maxHpMinusHitbox, maxHpPlusHitbox, maxHpValueHitbox;
    private Hitbox ascMinusHitbox, ascPlusHitbox;

    // HP editing state
    private boolean isEditingHp = false;
    private boolean isEditingMaxHp = false;
    private String hpEditText = "";
    private String maxHpEditText = "";

    // Loadout name
    private String loadoutName = "";
    private boolean isTypingName = false;
    private Hitbox nameBoxHitbox;

    // Search
    private String searchText = "";
    private boolean isTypingSearch = false;
    private Hitbox searchBoxHitbox;

    // Available items (left panel) - depends on active tab
    private List<AbstractCard> availableCards;
    private List<AbstractRelic> availableRelics;
    private List<AbstractPotion> availablePotions;
    private Hitbox[] availableItemHitboxes;
    private float availableScrollY = 0.0f;
    private float availableTargetScrollY = 0.0f;

    // Selected items (right panel)
    private List<DeckCard> deckCards;
    private List<AbstractRelic> selectedRelics;
    private List<AbstractPotion> selectedPotions;
    private Hitbox[] deckCardUpgradeHitboxes;
    private Hitbox[] deckCardRemoveHitboxes;
    private Hitbox[] relicRemoveHitboxes;
    private Hitbox[] potionRemoveHitboxes;
    private float selectedScrollY = 0.0f;
    private float selectedTargetScrollY = 0.0f;

    // Save button
    private Hitbox saveButtonHitbox;

    // For parsing JSON
    private static final Gson gson = new Gson();

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
        this.availableRelics = new ArrayList<>();
        this.availablePotions = new ArrayList<>();
        this.deckCards = new ArrayList<>();
        this.selectedRelics = new ArrayList<>();
        this.selectedPotions = new ArrayList<>();

        // Create character tab hitboxes
        characterTabHitboxes = new Hitbox[4];
        for (int i = 0; i < 4; i++) {
            characterTabHitboxes[i] = new Hitbox(CHAR_TAB_WIDTH, CHAR_TAB_HEIGHT);
        }

        // Create content tab hitboxes
        contentTabHitboxes = new Hitbox[3];
        for (int i = 0; i < 3; i++) {
            contentTabHitboxes[i] = new Hitbox(CONTENT_TAB_WIDTH, CONTENT_TAB_HEIGHT);
        }

        // Name box hitbox
        nameBoxHitbox = new Hitbox(280.0f * Settings.scale, 28.0f * Settings.scale);

        // Search box hitbox
        searchBoxHitbox = new Hitbox(280.0f * Settings.scale, 28.0f * Settings.scale);

        // Save button hitbox
        saveButtonHitbox = new Hitbox(120.0f * Settings.scale, 35.0f * Settings.scale);

        // Stats hitboxes
        float smallBtn = 30.0f * Settings.scale;
        float valueWidth = 50.0f * Settings.scale;
        hpMinusHitbox = new Hitbox(smallBtn, smallBtn);
        hpPlusHitbox = new Hitbox(smallBtn, smallBtn);
        hpValueHitbox = new Hitbox(valueWidth, smallBtn);
        maxHpMinusHitbox = new Hitbox(smallBtn, smallBtn);
        maxHpPlusHitbox = new Hitbox(smallBtn, smallBtn);
        maxHpValueHitbox = new Hitbox(valueWidth, smallBtn);
        ascMinusHitbox = new Hitbox(smallBtn, smallBtn);
        ascPlusHitbox = new Hitbox(smallBtn, smallBtn);
    }

    public void open() {
        STSArena.logger.info("Opening Loadout Creator Screen");
        this.isOpen = true;
        this.cancelButton.show("Cancel");

        // Reset state
        this.selectedClass = AbstractPlayer.PlayerClass.IRONCLAD;
        this.activeTab = ContentTab.CARDS;
        this.loadoutName = "";
        this.isTypingName = false;
        this.searchText = "";
        this.isTypingSearch = false;
        this.deckCards.clear();
        this.selectedRelics.clear();
        this.selectedPotions.clear();
        this.availableScrollY = 0.0f;
        this.availableTargetScrollY = 0.0f;
        this.selectedScrollY = 0.0f;
        this.selectedTargetScrollY = 0.0f;

        // Set default HP for selected class
        this.maxHp = LoadoutConfig.getBaseMaxHp(selectedClass);
        this.currentHp = this.maxHp;
        this.ascensionLevel = 0;

        // Add starter relic
        String starterRelicId = LoadoutConfig.getStarterRelicId(selectedClass);
        if (starterRelicId != null) {
            AbstractRelic starterRelic = RelicLibrary.getRelic(starterRelicId);
            if (starterRelic != null) {
                selectedRelics.add(starterRelic.makeCopy());
            }
        }

        // Add starter deck
        addStarterDeck();

        // Initialize potions for this class
        PotionHelper.initialize(selectedClass);

        // Build available items list
        refreshAvailableItems();
        refreshSelectedHitboxes();
    }

    public void close() {
        this.isOpen = false;
        this.cancelButton.hide();
        this.isTypingSearch = false;
    }

    /**
     * Open the loadout creator pre-populated with data from an existing loadout.
     * Used for "Copy" functionality.
     */
    public void openWithLoadout(ArenaRepository.LoadoutRecord loadout) {
        STSArena.logger.info("Opening Loadout Creator with existing loadout: " + loadout.name);

        // Call open() to initialize the screen with defaults
        this.isOpen = true;
        this.cancelButton.show("Cancel");
        this.loadoutName = "Copy of " + loadout.name;
        this.isTypingName = false;
        this.searchText = "";
        this.isTypingSearch = false;
        this.deckCards.clear();
        this.selectedRelics.clear();
        this.selectedPotions.clear();
        this.availableScrollY = 0.0f;
        this.availableTargetScrollY = 0.0f;
        this.selectedScrollY = 0.0f;
        this.selectedTargetScrollY = 0.0f;

        // Set character class from loadout
        try {
            this.selectedClass = AbstractPlayer.PlayerClass.valueOf(loadout.characterClass);
        } catch (IllegalArgumentException e) {
            this.selectedClass = AbstractPlayer.PlayerClass.IRONCLAD;
        }

        // Set HP and ascension from loadout
        this.maxHp = loadout.maxHp;
        this.currentHp = loadout.currentHp;
        this.ascensionLevel = loadout.ascensionLevel;

        // Parse and populate deck
        try {
            Type cardListType = new TypeToken<List<ArenaRepository.CardData>>(){}.getType();
            List<ArenaRepository.CardData> cardDataList = gson.fromJson(loadout.deckJson, cardListType);
            if (cardDataList != null) {
                for (ArenaRepository.CardData cardData : cardDataList) {
                    AbstractCard card = CardLibrary.getCard(cardData.id);
                    if (card != null) {
                        DeckCard dc = new DeckCard(card.makeCopy());
                        dc.upgraded = cardData.upgrades > 0;
                        deckCards.add(dc);
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to parse deck from loadout", e);
        }

        // Parse and populate relics
        try {
            Type relicListType = new TypeToken<List<String>>(){}.getType();
            List<String> relicIds = gson.fromJson(loadout.relicsJson, relicListType);
            if (relicIds != null) {
                for (String relicId : relicIds) {
                    if (RelicLibrary.isARelic(relicId)) {
                        AbstractRelic relic = RelicLibrary.getRelic(relicId);
                        if (relic != null) {
                            selectedRelics.add(relic.makeCopy());
                        }
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to parse relics from loadout", e);
        }

        // Parse and populate potions
        try {
            Type potionListType = new TypeToken<List<String>>(){}.getType();
            List<String> potionIds = gson.fromJson(loadout.potionsJson, potionListType);
            if (potionIds != null) {
                for (String potionId : potionIds) {
                    AbstractPotion potion = PotionHelper.getPotion(potionId);
                    if (potion != null) {
                        selectedPotions.add(potion.makeCopy());
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to parse potions from loadout", e);
        }

        // Initialize potions for this class
        PotionHelper.initialize(selectedClass);

        // Build available items list
        refreshAvailableItems();
        refreshSelectedHitboxes();
    }

    private boolean hasPrismaticShard() {
        for (AbstractRelic r : selectedRelics) {
            if (r instanceof PrismaticShard || r.relicId.equals("PrismaticShard")) {
                return true;
            }
        }
        return false;
    }

    private void refreshAvailableItems() {
        switch (activeTab) {
            case CARDS:
                refreshAvailableCards();
                break;
            case RELICS:
                refreshAvailableRelics();
                break;
            case POTIONS:
                refreshAvailablePotions();
                break;
        }
    }

    private void refreshAvailableCards() {
        availableCards.clear();

        AbstractCard.CardColor targetColor = LoadoutConfig.getCardColor(selectedClass);
        boolean prismatic = hasPrismaticShard();

        for (AbstractCard card : CardLibrary.cards.values()) {
            // Include character cards + colorless (+ all colors if prismatic)
            if (!prismatic) {
                if (card.color != targetColor && card.color != AbstractCard.CardColor.COLORLESS) {
                    continue;
                }
            } else {
                // With Prismatic Shard, include all non-special colors
                if (card.color == AbstractCard.CardColor.CURSE) {
                    continue;
                }
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

        // Create hitboxes (width must match rendered row width)
        float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
        availableItemHitboxes = new Hitbox[availableCards.size()];
        for (int i = 0; i < availableCards.size(); i++) {
            availableItemHitboxes[i] = new Hitbox(rowWidth, BUTTON_HEIGHT);
        }

        availableScrollY = 0.0f;
        availableTargetScrollY = 0.0f;
    }

    private void refreshAvailableRelics() {
        availableRelics.clear();

        for (AbstractRelic relic : RelicLibrary.starterList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.commonList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.uncommonList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.rareList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.bossList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.shopList) {
            addRelicIfMatches(relic);
        }
        for (AbstractRelic relic : RelicLibrary.specialList) {
            addRelicIfMatches(relic);
        }

        // Sort alphabetically
        availableRelics.sort(Comparator.comparing(r -> r.name));

        // Create hitboxes (width must match rendered row width)
        float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
        availableItemHitboxes = new Hitbox[availableRelics.size()];
        for (int i = 0; i < availableRelics.size(); i++) {
            availableItemHitboxes[i] = new Hitbox(rowWidth, BUTTON_HEIGHT);
        }

        availableScrollY = 0.0f;
        availableTargetScrollY = 0.0f;
    }

    private void addRelicIfMatches(AbstractRelic relic) {
        if (relic == null) return;

        // Filter by search
        if (!searchText.isEmpty() &&
            !relic.name.toLowerCase().contains(searchText.toLowerCase())) {
            return;
        }

        // Check if already selected
        for (AbstractRelic r : selectedRelics) {
            if (r.relicId.equals(relic.relicId)) {
                return;
            }
        }

        availableRelics.add(relic);
    }

    private void refreshAvailablePotions() {
        availablePotions.clear();

        for (String potionId : PotionHelper.potions) {
            AbstractPotion potion = PotionHelper.getPotion(potionId);
            if (potion == null || potion instanceof PotionSlot) continue;

            // Filter by search
            if (!searchText.isEmpty() &&
                !potion.name.toLowerCase().contains(searchText.toLowerCase())) {
                continue;
            }

            availablePotions.add(potion);
        }

        // Sort alphabetically
        availablePotions.sort(Comparator.comparing(p -> p.name));

        // Create hitboxes (width must match rendered row width)
        float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
        availableItemHitboxes = new Hitbox[availablePotions.size()];
        for (int i = 0; i < availablePotions.size(); i++) {
            availableItemHitboxes[i] = new Hitbox(rowWidth, BUTTON_HEIGHT);
        }

        availableScrollY = 0.0f;
        availableTargetScrollY = 0.0f;
    }

    private void refreshSelectedHitboxes() {
        // Deck card hitboxes
        deckCardUpgradeHitboxes = new Hitbox[deckCards.size()];
        deckCardRemoveHitboxes = new Hitbox[deckCards.size()];
        for (int i = 0; i < deckCards.size(); i++) {
            deckCardUpgradeHitboxes[i] = new Hitbox(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
            deckCardRemoveHitboxes[i] = new Hitbox(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        // Relic hitboxes
        relicRemoveHitboxes = new Hitbox[selectedRelics.size()];
        for (int i = 0; i < selectedRelics.size(); i++) {
            relicRemoveHitboxes[i] = new Hitbox(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        }

        // Potion hitboxes
        potionRemoveHitboxes = new Hitbox[selectedPotions.size()];
        for (int i = 0; i < selectedPotions.size(); i++) {
            potionRemoveHitboxes[i] = new Hitbox(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
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

        // Handle text input for name
        if (isTypingName) {
            handleNameInput();
        }

        // Handle text input for search
        if (isTypingSearch) {
            handleSearchInput();
        }

        // Update name box hitbox (top right, near save button)
        float nameBoxX = Settings.WIDTH - 320.0f * Settings.scale;
        nameBoxHitbox.move(nameBoxX, TITLE_Y);
        nameBoxHitbox.update();
        if (nameBoxHitbox.hovered && InputHelper.justClickedLeft) {
            isTypingName = true;
            isTypingSearch = false;
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !nameBoxHitbox.hovered && isTypingName) {
            isTypingName = false;
        }

        // Update search box hitbox
        searchBoxHitbox.move(LEFT_COLUMN_X, SEARCH_Y);
        searchBoxHitbox.update();
        if (searchBoxHitbox.hovered && InputHelper.justClickedLeft) {
            isTypingSearch = true;
            isTypingName = false;
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !searchBoxHitbox.hovered && isTypingSearch) {
            isTypingSearch = false;
        }

        // Update character tabs
        for (int i = 0; i < 4; i++) {
            float tabX = CHAR_TAB_START_X + i * CHAR_TAB_WIDTH + CHAR_TAB_WIDTH / 2.0f;
            characterTabHitboxes[i].move(tabX, CHAR_TAB_Y);
            characterTabHitboxes[i].update();

            if (characterTabHitboxes[i].hovered && InputHelper.justClickedLeft) {
                AbstractPlayer.PlayerClass newClass = LoadoutConfig.getPlayerClasses()[i];
                if (newClass != selectedClass) {
                    selectedClass = newClass;
                    // Update HP for new class
                    maxHp = LoadoutConfig.getBaseMaxHp(selectedClass);
                    currentHp = Math.min(currentHp, maxHp);
                    // Update starter relic and deck
                    updateStarterRelic();
                    addStarterDeck();
                    // Reinitialize potions for new class
                    PotionHelper.initialize(selectedClass);
                    refreshAvailableItems();
                }
                InputHelper.justClickedLeft = false;
            }
        }

        // Update content tabs
        float contentTabStartX = LEFT_COLUMN_X - CONTENT_TAB_WIDTH;
        for (int i = 0; i < 3; i++) {
            float tabX = contentTabStartX + i * CONTENT_TAB_WIDTH;
            // move() sets CENTER, so add half width to match rendered position
            contentTabHitboxes[i].move(tabX + CONTENT_TAB_WIDTH / 2.0f, CONTENT_TAB_Y);
            contentTabHitboxes[i].update();

            if (contentTabHitboxes[i].hovered && InputHelper.justClickedLeft) {
                ContentTab newTab = ContentTab.values()[i];
                if (newTab != activeTab) {
                    activeTab = newTab;
                    searchText = "";
                    refreshAvailableItems();
                }
                InputHelper.justClickedLeft = false;
            }
        }

        // Update stats controls
        updateStatsControls();

        // Update save button
        saveButtonHitbox.move(Settings.WIDTH - 100.0f * Settings.scale, TITLE_Y);
        saveButtonHitbox.update();
        if (saveButtonHitbox.hovered && InputHelper.justClickedLeft && !deckCards.isEmpty()) {
            saveLoadout();
            InputHelper.justClickedLeft = false;
            return;
        }

        // Scrolling for available items (left panel)
        if (InputHelper.mX < Settings.WIDTH / 2.0f) {
            if (InputHelper.scrolledDown) {
                availableTargetScrollY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                availableTargetScrollY -= Settings.SCROLL_SPEED;
            }
        }

        // Scrolling for selected items (right panel)
        if (InputHelper.mX >= Settings.WIDTH / 2.0f) {
            if (InputHelper.scrolledDown) {
                selectedTargetScrollY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                selectedTargetScrollY -= Settings.SCROLL_SPEED;
            }
        }

        // Clamp scrolling
        int availableCount = getAvailableCount();
        float availableMaxScroll = Math.max(0, availableCount * ROW_HEIGHT - LIST_HEIGHT);
        availableTargetScrollY = Math.max(0, Math.min(availableMaxScroll, availableTargetScrollY));
        availableScrollY = MathHelper.scrollSnapLerpSpeed(availableScrollY, availableTargetScrollY);

        int selectedCount = deckCards.size() + selectedRelics.size() + selectedPotions.size() + 3; // +3 for headers
        float selectedMaxScroll = Math.max(0, selectedCount * ROW_HEIGHT - LIST_HEIGHT);
        selectedTargetScrollY = Math.max(0, Math.min(selectedMaxScroll, selectedTargetScrollY));
        selectedScrollY = MathHelper.scrollSnapLerpSpeed(selectedScrollY, selectedTargetScrollY);

        // Update available item hitboxes and check for clicks
        updateAvailableItems();

        // Update selected item hitboxes and check for clicks
        updateSelectedItems();
    }

    private void updateStatsControls() {
        float statsX = Settings.WIDTH / 2.0f + 80.0f * Settings.scale;
        float btnSize = 30.0f * Settings.scale;

        // Handle HP editing input
        if (isEditingHp) {
            handleHpInput(true);
        }
        if (isEditingMaxHp) {
            handleHpInput(false);
        }

        // HP controls
        float hpX = statsX;
        hpMinusHitbox.move(hpX, STATS_Y);
        hpValueHitbox.move(hpX + 40.0f * Settings.scale, STATS_Y);
        hpPlusHitbox.move(hpX + 80.0f * Settings.scale, STATS_Y);
        hpMinusHitbox.update();
        hpValueHitbox.update();
        hpPlusHitbox.update();

        if (hpMinusHitbox.hovered && InputHelper.justClickedLeft && !isEditingHp) {
            currentHp = Math.max(1, currentHp - 1);
            InputHelper.justClickedLeft = false;
        }
        if (hpPlusHitbox.hovered && InputHelper.justClickedLeft && !isEditingHp) {
            currentHp = Math.min(maxHp, currentHp + 1);
            InputHelper.justClickedLeft = false;
        }
        if (hpValueHitbox.hovered && InputHelper.justClickedLeft) {
            isEditingHp = true;
            isEditingMaxHp = false;
            isTypingName = false;
            isTypingSearch = false;
            hpEditText = String.valueOf(currentHp);
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !hpValueHitbox.hovered && isEditingHp) {
            finishHpEdit(true);
        }

        // Max HP controls
        float maxHpX = statsX + 200.0f * Settings.scale;
        maxHpMinusHitbox.move(maxHpX, STATS_Y);
        maxHpValueHitbox.move(maxHpX + 40.0f * Settings.scale, STATS_Y);
        maxHpPlusHitbox.move(maxHpX + 80.0f * Settings.scale, STATS_Y);
        maxHpMinusHitbox.update();
        maxHpValueHitbox.update();
        maxHpPlusHitbox.update();

        if (maxHpMinusHitbox.hovered && InputHelper.justClickedLeft && !isEditingMaxHp) {
            maxHp = Math.max(1, maxHp - 1);
            currentHp = Math.min(currentHp, maxHp);
            InputHelper.justClickedLeft = false;
        }
        if (maxHpPlusHitbox.hovered && InputHelper.justClickedLeft && !isEditingMaxHp) {
            maxHp = Math.min(999, maxHp + 1);
            InputHelper.justClickedLeft = false;
        }
        if (maxHpValueHitbox.hovered && InputHelper.justClickedLeft) {
            isEditingMaxHp = true;
            isEditingHp = false;
            isTypingName = false;
            isTypingSearch = false;
            maxHpEditText = String.valueOf(maxHp);
            InputHelper.justClickedLeft = false;
        } else if (InputHelper.justClickedLeft && !maxHpValueHitbox.hovered && isEditingMaxHp) {
            finishHpEdit(false);
        }

        // Ascension controls
        float ascX = statsX + 380.0f * Settings.scale;
        ascMinusHitbox.move(ascX, STATS_Y);
        ascPlusHitbox.move(ascX + 60.0f * Settings.scale, STATS_Y);
        ascMinusHitbox.update();
        ascPlusHitbox.update();

        if (ascMinusHitbox.hovered && InputHelper.justClickedLeft) {
            ascensionLevel = Math.max(0, ascensionLevel - 1);
            InputHelper.justClickedLeft = false;
        }
        if (ascPlusHitbox.hovered && InputHelper.justClickedLeft) {
            ascensionLevel = Math.min(20, ascensionLevel + 1);
            InputHelper.justClickedLeft = false;
        }
    }

    private void handleHpInput(boolean isCurrentHp) {
        String editText = isCurrentHp ? hpEditText : maxHpEditText;

        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !editText.isEmpty()) {
            editText = editText.substring(0, editText.length() - 1);
            if (isCurrentHp) {
                hpEditText = editText;
            } else {
                maxHpEditText = editText;
            }
        }

        // Handle escape to cancel
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            if (isCurrentHp) {
                isEditingHp = false;
            } else {
                isEditingMaxHp = false;
            }
        }

        // Handle enter to confirm
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) {
            finishHpEdit(isCurrentHp);
        }

        // Handle number input
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                // Limit to 3 digits
                if (editText.length() < 3) {
                    editText += c;
                    if (isCurrentHp) {
                        hpEditText = editText;
                    } else {
                        maxHpEditText = editText;
                    }
                }
            }
        }
    }

    private void finishHpEdit(boolean isCurrentHp) {
        if (isCurrentHp) {
            isEditingHp = false;
            try {
                int value = Integer.parseInt(hpEditText);
                currentHp = Math.max(1, Math.min(maxHp, value));
            } catch (NumberFormatException e) {
                // Keep existing value
            }
        } else {
            isEditingMaxHp = false;
            try {
                int value = Integer.parseInt(maxHpEditText);
                maxHp = Math.max(1, Math.min(999, value));
                currentHp = Math.min(currentHp, maxHp);
            } catch (NumberFormatException e) {
                // Keep existing value
            }
        }
    }

    private int getAvailableCount() {
        switch (activeTab) {
            case CARDS: return availableCards.size();
            case RELICS: return availableRelics.size();
            case POTIONS: return availablePotions.size();
            default: return 0;
        }
    }

    private void updateAvailableItems() {
        float y = LIST_START_Y + availableScrollY;
        int count = getAvailableCount();

        for (int i = 0; i < count; i++) {
            float itemY = y - i * ROW_HEIGHT;

            if (itemY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && itemY < LIST_START_Y + ROW_HEIGHT * 2) {
                if (i < availableItemHitboxes.length) {
                    availableItemHitboxes[i].move(LEFT_COLUMN_X, itemY - BUTTON_HEIGHT / 2.0f);
                    availableItemHitboxes[i].update();

                    if (availableItemHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        addItemFromAvailable(i);
                        InputHelper.justClickedLeft = false;
                    }
                }
            }
        }
    }

    private void addItemFromAvailable(int index) {
        switch (activeTab) {
            case CARDS:
                if (index < availableCards.size()) {
                    addCardToDeck(availableCards.get(index));
                }
                break;
            case RELICS:
                if (index < availableRelics.size()) {
                    addRelic(availableRelics.get(index));
                }
                break;
            case POTIONS:
                if (index < availablePotions.size()) {
                    addPotion(availablePotions.get(index));
                }
                break;
        }
    }

    private void updateSelectedItems() {
        float y = LIST_START_Y + selectedScrollY;
        int row = 0;
        // Row width must match render code
        float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;

        // Cards section
        row++; // Header
        for (int i = 0; i < deckCards.size(); i++) {
            float cardY = y - row * ROW_HEIGHT;
            row++;

            if (cardY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT * 2) {
                float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 70.0f * Settings.scale;

                // Upgrade button
                if (i < deckCardUpgradeHitboxes.length) {
                    deckCardUpgradeHitboxes[i].move(buttonX, cardY - BUTTON_HEIGHT / 2.0f);
                    deckCardUpgradeHitboxes[i].update();
                    if (deckCardUpgradeHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        toggleUpgrade(i);
                        InputHelper.justClickedLeft = false;
                    }
                }

                // Remove button
                if (i < deckCardRemoveHitboxes.length) {
                    deckCardRemoveHitboxes[i].move(buttonX + 35.0f * Settings.scale, cardY - BUTTON_HEIGHT / 2.0f);
                    deckCardRemoveHitboxes[i].update();
                    if (deckCardRemoveHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        removeCardFromDeck(i);
                        InputHelper.justClickedLeft = false;
                    }
                }
            }
        }

        // Relics section
        row++; // Header
        for (int i = 0; i < selectedRelics.size(); i++) {
            float relicY = y - row * ROW_HEIGHT;
            row++;

            if (relicY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && relicY < LIST_START_Y + ROW_HEIGHT * 2) {
                if (i < relicRemoveHitboxes.length) {
                    float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 35.0f * Settings.scale;
                    relicRemoveHitboxes[i].move(buttonX, relicY - BUTTON_HEIGHT / 2.0f);
                    relicRemoveHitboxes[i].update();
                    if (relicRemoveHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        removeRelic(i);
                        InputHelper.justClickedLeft = false;
                    }
                }
            }
        }

        // Potions section
        row++; // Header
        for (int i = 0; i < selectedPotions.size(); i++) {
            float potionY = y - row * ROW_HEIGHT;
            row++;

            if (potionY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && potionY < LIST_START_Y + ROW_HEIGHT * 2) {
                if (i < potionRemoveHitboxes.length) {
                    float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 35.0f * Settings.scale;
                    potionRemoveHitboxes[i].move(buttonX, potionY - BUTTON_HEIGHT / 2.0f);
                    potionRemoveHitboxes[i].update();
                    if (potionRemoveHitboxes[i].hovered && InputHelper.justClickedLeft) {
                        removePotion(i);
                        InputHelper.justClickedLeft = false;
                    }
                }
            }
        }
    }

    private void handleNameInput() {
        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !loadoutName.isEmpty()) {
            loadoutName = loadoutName.substring(0, loadoutName.length() - 1);
        }

        // Handle escape or enter to stop typing
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            isTypingName = false;
        }

        // Handle shift key for uppercase letters
        boolean shiftHeld = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) ||
                           com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);

        // Handle typed characters
        for (int keycode = com.badlogic.gdx.Input.Keys.A; keycode <= com.badlogic.gdx.Input.Keys.Z; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ((shiftHeld ? 'A' : 'a') + (keycode - com.badlogic.gdx.Input.Keys.A));
                loadoutName += c;
            }
        }
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                loadoutName += c;
            }
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            loadoutName += ' ';
        }
    }

    private void handleSearchInput() {
        // Handle backspace
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACKSPACE) && !searchText.isEmpty()) {
            searchText = searchText.substring(0, searchText.length() - 1);
            refreshAvailableItems();
        }

        // Handle escape or enter to stop typing
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER) ||
            com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            isTypingSearch = false;
        }

        // Handle typed characters
        for (int keycode = com.badlogic.gdx.Input.Keys.A; keycode <= com.badlogic.gdx.Input.Keys.Z; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('a' + (keycode - com.badlogic.gdx.Input.Keys.A));
                searchText += c;
                refreshAvailableItems();
            }
        }
        for (int keycode = com.badlogic.gdx.Input.Keys.NUM_0; keycode <= com.badlogic.gdx.Input.Keys.NUM_9; keycode++) {
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(keycode)) {
                char c = (char) ('0' + (keycode - com.badlogic.gdx.Input.Keys.NUM_0));
                searchText += c;
                refreshAvailableItems();
            }
        }
        if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            searchText += ' ';
            refreshAvailableItems();
        }
    }

    private void updateStarterRelic() {
        // Remove old starter relic and add new one
        String newStarterRelicId = LoadoutConfig.getStarterRelicId(selectedClass);
        if (newStarterRelicId != null) {
            // Check if we already have a starter relic from the new class
            boolean hasNewStarter = false;
            for (AbstractRelic r : selectedRelics) {
                if (r.relicId.equals(newStarterRelicId)) {
                    hasNewStarter = true;
                    break;
                }
            }

            if (!hasNewStarter) {
                // Remove old class starter relics
                selectedRelics.removeIf(r ->
                    r.relicId.equals("Burning Blood") ||
                    r.relicId.equals("Ring of the Snake") ||
                    r.relicId.equals("Cracked Core") ||
                    r.relicId.equals("PureWater")
                );

                // Add new starter
                AbstractRelic starterRelic = RelicLibrary.getRelic(newStarterRelicId);
                if (starterRelic != null) {
                    selectedRelics.add(0, starterRelic.makeCopy());
                }
            }
        }
        refreshSelectedHitboxes();
    }

    private void addStarterDeck() {
        deckCards.clear();

        // Get the character instance to access its starting deck
        AbstractPlayer character = com.megacrit.cardcrawl.core.CardCrawlGame.characterManager.recreateCharacter(selectedClass);
        if (character != null) {
            java.util.ArrayList<String> starterCardIds = character.getStartingDeck();
            for (String cardId : starterCardIds) {
                AbstractCard card = CardLibrary.getCard(cardId);
                if (card != null) {
                    deckCards.add(new DeckCard(card.makeCopy()));
                }
            }
        }

        refreshSelectedHitboxes();
    }

    private void addCardToDeck(AbstractCard card) {
        DeckCard dc = new DeckCard(card.makeCopy());
        deckCards.add(dc);
        refreshSelectedHitboxes();
    }

    private void removeCardFromDeck(int index) {
        if (index >= 0 && index < deckCards.size()) {
            deckCards.remove(index);
            refreshSelectedHitboxes();
        }
    }

    private void toggleUpgrade(int index) {
        if (index >= 0 && index < deckCards.size()) {
            DeckCard dc = deckCards.get(index);
            if (dc.card.canUpgrade() || dc.upgraded) {
                dc.upgraded = !dc.upgraded;
            }
        }
    }

    private void addRelic(AbstractRelic relic) {
        selectedRelics.add(relic.makeCopy());
        refreshSelectedHitboxes();
        // Refresh available relics to remove the added one
        if (activeTab == ContentTab.RELICS) {
            refreshAvailableRelics();
        }
        // If Prismatic Shard was added, refresh cards
        if (relic.relicId.equals("PrismaticShard") && activeTab == ContentTab.CARDS) {
            refreshAvailableCards();
        }
    }

    private void removeRelic(int index) {
        if (index >= 0 && index < selectedRelics.size()) {
            AbstractRelic removed = selectedRelics.remove(index);
            refreshSelectedHitboxes();
            // Refresh available relics
            if (activeTab == ContentTab.RELICS) {
                refreshAvailableRelics();
            }
            // If Prismatic Shard was removed, refresh cards
            if (removed.relicId.equals("PrismaticShard") && activeTab == ContentTab.CARDS) {
                refreshAvailableCards();
            }
        }
    }

    private void addPotion(AbstractPotion potion) {
        // Limit potions (base is 3, but can be modified by ascension/relics)
        int maxPotions = getPotionSlots();
        if (selectedPotions.size() < maxPotions) {
            selectedPotions.add(potion.makeCopy());
            refreshSelectedHitboxes();
        }
    }

    private void removePotion(int index) {
        if (index >= 0 && index < selectedPotions.size()) {
            selectedPotions.remove(index);
            refreshSelectedHitboxes();
        }
    }

    private int getPotionSlots() {
        int slots = 3;
        // Ascension 11+ reduces to 2
        if (ascensionLevel >= 11) {
            slots = 2;
        }
        // Potion Belt adds 2
        for (AbstractRelic r : selectedRelics) {
            if (r.relicId.equals("Potion Belt")) {
                slots += 2;
            }
        }
        return slots;
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

        // Copy relics
        List<AbstractRelic> relics = new ArrayList<>();
        for (AbstractRelic r : selectedRelics) {
            relics.add(r.makeCopy());
        }

        // Copy potions
        List<AbstractPotion> potions = new ArrayList<>();
        for (AbstractPotion p : selectedPotions) {
            potions.add(p.makeCopy());
        }

        int potionSlots = getPotionSlots();

        // Use custom name or generate default
        String name;
        if (loadoutName.isEmpty()) {
            String className = selectedClass.name();
            className = className.substring(0, 1) + className.substring(1).toLowerCase().replace("_", " ");
            name = "Custom " + className;
        } else {
            name = loadoutName.trim();
        }

        RandomLoadoutGenerator.GeneratedLoadout loadout = new RandomLoadoutGenerator.GeneratedLoadout(
            UUID.randomUUID().toString(),
            name,
            System.currentTimeMillis(),
            selectedClass,
            deck,
            relics,
            potions,
            potionSlots,
            hasPrismaticShard(),
            maxHp,
            currentHp,
            ascensionLevel
        );

        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        long dbId = repo.saveLoadout(loadout);

        if (dbId > 0) {
            STSArena.logger.info("Saved custom loadout with " + deck.size() + " cards, " +
                relics.size() + " relics, " + potions.size() + " potions");
        }

        close();
        STSArena.openLoadoutSelectScreen();
    }

    public void render(SpriteBatch sb) {
        if (!isOpen) return;

        // Darken background
        sb.setColor(new Color(0, 0, 0, 0.92f));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0, 0, Settings.WIDTH, Settings.HEIGHT);

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.SCP_cardTitleFont_small,
            "Create Custom Loadout",
            Settings.WIDTH / 2.0f, TITLE_Y, Settings.GOLD_COLOR);

        // Name input box
        renderNameBox(sb);

        // Save button
        renderSaveButton(sb);

        // Character tabs
        renderCharacterTabs(sb);

        // Stats row (HP, Max HP, Ascension)
        renderStats(sb);

        // Content tabs
        renderContentTabs(sb);

        // Search box
        renderSearchBox(sb);

        // Column headers
        String leftHeader = getLeftColumnHeader();
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            leftHeader,
            LEFT_COLUMN_X, LIST_START_Y + 25.0f * Settings.scale, Settings.GOLD_COLOR);

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "Your Loadout",
            RIGHT_COLUMN_X, LIST_START_Y + 25.0f * Settings.scale, Settings.GOLD_COLOR);

        // Draw panel backgrounds for scroll areas
        float panelWidth = COLUMN_WIDTH - 10.0f * Settings.scale;
        sb.setColor(new Color(0.05f, 0.05f, 0.08f, 0.6f));
        // Left panel background
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            LEFT_COLUMN_X - panelWidth / 2.0f, LIST_START_Y - LIST_HEIGHT,
            panelWidth, LIST_HEIGHT);
        // Right panel background
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            RIGHT_COLUMN_X - panelWidth / 2.0f, LIST_START_Y - LIST_HEIGHT,
            panelWidth, LIST_HEIGHT);

        // Render available items (left panel)
        renderAvailableItems(sb);

        // Render selected items (right panel)
        renderSelectedItems(sb);

        // Cancel button
        this.cancelButton.render(sb);

        // Render cursor (must be last)
        com.megacrit.cardcrawl.core.CardCrawlGame.cursor.render(sb);
    }

    private String getLeftColumnHeader() {
        switch (activeTab) {
            case CARDS: return "Available Cards (" + availableCards.size() + ")";
            case RELICS: return "Available Relics (" + availableRelics.size() + ")";
            case POTIONS: return "Available Potions (" + availablePotions.size() + ")";
            default: return "";
        }
    }

    private void renderNameBox(SpriteBatch sb) {
        // Label
        FontHelper.renderFontRightTopAligned(sb, FontHelper.cardDescFont_N,
            "Name:",
            nameBoxHitbox.x - 10.0f * Settings.scale,
            nameBoxHitbox.y + nameBoxHitbox.height - 6.0f * Settings.scale,
            Settings.CREAM_COLOR);

        // Box background
        Color bgColor = isTypingName ? new Color(0.2f, 0.2f, 0.3f, 0.9f) : new Color(0.1f, 0.1f, 0.15f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            nameBoxHitbox.x, nameBoxHitbox.y,
            nameBoxHitbox.width, nameBoxHitbox.height);

        // Border when active
        if (isTypingName) {
            sb.setColor(Settings.GOLD_COLOR);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, nameBoxHitbox.x, nameBoxHitbox.y + nameBoxHitbox.height - 2, nameBoxHitbox.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, nameBoxHitbox.x, nameBoxHitbox.y, nameBoxHitbox.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, nameBoxHitbox.x, nameBoxHitbox.y, 2, nameBoxHitbox.height);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, nameBoxHitbox.x + nameBoxHitbox.width - 2, nameBoxHitbox.y, 2, nameBoxHitbox.height);
        }

        // Text - truncate if too long
        String displayText = loadoutName.isEmpty() ? "Untitled" : loadoutName;
        Color textColor = loadoutName.isEmpty() ? new Color(0.5f, 0.5f, 0.5f, 1.0f) : Settings.CREAM_COLOR;
        if (isTypingName) {
            displayText = loadoutName + "|";
            textColor = Settings.CREAM_COLOR;
        }

        // Truncate text to fit within the box (with some padding)
        float maxWidth = nameBoxHitbox.width - 16.0f * Settings.scale;
        while (displayText.length() > 1 && FontHelper.getWidth(FontHelper.cardDescFont_N, displayText, 1.0f) > maxWidth) {
            // When editing, keep the cursor visible at the end
            if (isTypingName && displayText.endsWith("|")) {
                displayText = "..." + displayText.substring(displayText.length() - Math.min(displayText.length(), 20));
            } else {
                displayText = displayText.substring(0, displayText.length() - 1);
            }
        }

        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            displayText,
            nameBoxHitbox.cX, nameBoxHitbox.cY, textColor);
    }

    private void renderSaveButton(SpriteBatch sb) {
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
    }

    private void renderCharacterTabs(SpriteBatch sb) {
        String[] tabNames = {"Ironclad", "Silent", "Defect", "Watcher"};

        for (int i = 0; i < 4; i++) {
            float tabX = CHAR_TAB_START_X + i * CHAR_TAB_WIDTH;
            boolean isSelected = LoadoutConfig.getPlayerClasses()[i] == selectedClass;
            boolean isHovered = characterTabHitboxes[i].hovered;

            Color bgColor;
            if (isSelected) {
                bgColor = new Color(0.3f, 0.4f, 0.5f, 0.9f);
            } else if (isHovered) {
                bgColor = new Color(0.2f, 0.3f, 0.4f, 0.7f);
            } else {
                bgColor = new Color(0.1f, 0.15f, 0.2f, 0.5f);
            }
            sb.setColor(bgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, tabX, CHAR_TAB_Y - CHAR_TAB_HEIGHT / 2.0f, CHAR_TAB_WIDTH, CHAR_TAB_HEIGHT);

            Color textColor = isSelected ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                tabNames[i],
                tabX + CHAR_TAB_WIDTH / 2.0f, CHAR_TAB_Y, textColor);
        }
    }

    private void renderStats(SpriteBatch sb) {
        float statsX = Settings.WIDTH / 2.0f + 80.0f * Settings.scale;
        float btnSize = 30.0f * Settings.scale;

        // HP
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "HP:",
            statsX - 90.0f * Settings.scale, STATS_Y + 10.0f * Settings.scale, Settings.CREAM_COLOR);
        renderStatButton(sb, hpMinusHitbox, "-");
        renderHpValue(sb, hpValueHitbox, isEditingHp, isEditingHp ? hpEditText : String.valueOf(currentHp));
        renderStatButton(sb, hpPlusHitbox, "+");

        // Max HP
        float maxHpX = statsX + 200.0f * Settings.scale;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "Max:",
            maxHpX - 55.0f * Settings.scale, STATS_Y + 10.0f * Settings.scale, Settings.CREAM_COLOR);
        renderStatButton(sb, maxHpMinusHitbox, "-");
        renderHpValue(sb, maxHpValueHitbox, isEditingMaxHp, isEditingMaxHp ? maxHpEditText : String.valueOf(maxHp));
        renderStatButton(sb, maxHpPlusHitbox, "+");

        // Ascension
        float ascX = statsX + 380.0f * Settings.scale;
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            "A:",
            ascX - 30.0f * Settings.scale, STATS_Y + 10.0f * Settings.scale, Settings.CREAM_COLOR);
        renderStatButton(sb, ascMinusHitbox, "-");
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            String.valueOf(ascensionLevel),
            ascX + 30.0f * Settings.scale, STATS_Y, Settings.CREAM_COLOR);
        renderStatButton(sb, ascPlusHitbox, "+");
    }

    private void renderHpValue(SpriteBatch sb, Hitbox hb, boolean isEditing, String text) {
        // Background
        Color bgColor;
        if (isEditing) {
            bgColor = new Color(0.2f, 0.2f, 0.3f, 0.9f);
        } else if (hb.hovered) {
            bgColor = new Color(0.2f, 0.25f, 0.3f, 0.7f);
        } else {
            bgColor = new Color(0.1f, 0.1f, 0.15f, 0.5f);
        }
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x, hb.y, hb.width, hb.height);

        // Border when editing
        if (isEditing) {
            sb.setColor(Settings.GOLD_COLOR);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x, hb.y + hb.height - 2, hb.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x, hb.y, hb.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x, hb.y, 2, hb.height);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x + hb.width - 2, hb.y, 2, hb.height);
        }

        // Text (with cursor when editing)
        String displayText = isEditing ? text + "|" : text;
        Color textColor = isEditing ? Settings.GOLD_COLOR : (hb.hovered ? Settings.CREAM_COLOR : Settings.CREAM_COLOR);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            displayText, hb.cX, hb.cY, textColor);
    }

    private void renderStatButton(SpriteBatch sb, Hitbox hb, String text) {
        Color bgColor = hb.hovered ? new Color(0.3f, 0.3f, 0.4f, 0.9f) : new Color(0.15f, 0.15f, 0.2f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, hb.x, hb.y, hb.width, hb.height);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            text, hb.cX, hb.cY,
            hb.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR);
    }

    private void renderContentTabs(SpriteBatch sb) {
        String[] tabNames = {"Cards", "Relics", "Potions"};
        float contentTabStartX = LEFT_COLUMN_X - CONTENT_TAB_WIDTH;

        for (int i = 0; i < 3; i++) {
            float tabX = contentTabStartX + i * CONTENT_TAB_WIDTH;
            boolean isSelected = ContentTab.values()[i] == activeTab;
            boolean isHovered = contentTabHitboxes[i].hovered;

            Color bgColor;
            if (isSelected) {
                bgColor = new Color(0.25f, 0.35f, 0.45f, 0.9f);
            } else if (isHovered) {
                bgColor = new Color(0.2f, 0.25f, 0.35f, 0.7f);
            } else {
                bgColor = new Color(0.1f, 0.12f, 0.18f, 0.5f);
            }
            sb.setColor(bgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, tabX, CONTENT_TAB_Y - CONTENT_TAB_HEIGHT / 2.0f,
                CONTENT_TAB_WIDTH, CONTENT_TAB_HEIGHT);

            Color textColor = isSelected ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
            FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
                tabNames[i],
                tabX + CONTENT_TAB_WIDTH / 2.0f, CONTENT_TAB_Y, textColor);
        }
    }

    private void renderSearchBox(SpriteBatch sb) {
        Color bgColor = isTypingSearch ? new Color(0.2f, 0.2f, 0.3f, 0.9f) : new Color(0.1f, 0.1f, 0.15f, 0.7f);
        sb.setColor(bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            searchBoxHitbox.x, searchBoxHitbox.y,
            searchBoxHitbox.width, searchBoxHitbox.height);

        if (isTypingSearch) {
            sb.setColor(Settings.GOLD_COLOR);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y + searchBoxHitbox.height - 2, searchBoxHitbox.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y, searchBoxHitbox.width, 2);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x, searchBoxHitbox.y, 2, searchBoxHitbox.height);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, searchBoxHitbox.x + searchBoxHitbox.width - 2, searchBoxHitbox.y, 2, searchBoxHitbox.height);
        }

        String displayText = searchText.isEmpty() ? "Search..." : searchText;
        Color textColor = searchText.isEmpty() ? new Color(0.5f, 0.5f, 0.5f, 1.0f) : Settings.CREAM_COLOR;
        if (isTypingSearch) {
            displayText = searchText + "|";
        }
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            displayText,
            searchBoxHitbox.cX, searchBoxHitbox.cY, textColor);
    }

    private void renderAvailableItems(SpriteBatch sb) {
        float y = LIST_START_Y + availableScrollY;

        switch (activeTab) {
            case CARDS:
                renderAvailableCards(sb, y);
                break;
            case RELICS:
                renderAvailableRelics(sb, y);
                break;
            case POTIONS:
                renderAvailablePotions(sb, y);
                break;
        }
    }

    private void renderAvailableCards(SpriteBatch sb, float startY) {
        for (int i = 0; i < availableCards.size(); i++) {
            float cardY = startY - i * ROW_HEIGHT;

            if (cardY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && cardY < LIST_START_Y + ROW_HEIGHT * 2) {
                AbstractCard card = availableCards.get(i);
                boolean hovered = i < availableItemHitboxes.length && availableItemHitboxes[i].hovered;

                Color bgColor = hovered ? new Color(0.2f, 0.3f, 0.2f, 0.8f) : new Color(0.1f, 0.1f, 0.15f, 0.5f);
                sb.setColor(bgColor);
                float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    LEFT_COLUMN_X - rowWidth / 2.0f, cardY - BUTTON_HEIGHT,
                    rowWidth, BUTTON_HEIGHT);

                String costStr = card.cost >= 0 ? "[" + card.cost + "] " : "";
                Color textColor = hovered ? Settings.GREEN_TEXT_COLOR : getCardTypeColor(card.type);
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    costStr + card.name,
                    LEFT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
                    cardY - 4.0f * Settings.scale, textColor);

                FontHelper.renderFontRightTopAligned(sb, FontHelper.cardDescFont_N,
                    "+",
                    LEFT_COLUMN_X + rowWidth / 2.0f - 8.0f * Settings.scale,
                    cardY - 4.0f * Settings.scale,
                    hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            }
        }
    }

    private void renderAvailableRelics(SpriteBatch sb, float startY) {
        for (int i = 0; i < availableRelics.size(); i++) {
            float relicY = startY - i * ROW_HEIGHT;

            if (relicY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && relicY < LIST_START_Y + ROW_HEIGHT * 2) {
                AbstractRelic relic = availableRelics.get(i);
                boolean hovered = i < availableItemHitboxes.length && availableItemHitboxes[i].hovered;

                Color bgColor = hovered ? new Color(0.2f, 0.3f, 0.2f, 0.8f) : new Color(0.1f, 0.1f, 0.15f, 0.5f);
                sb.setColor(bgColor);
                float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    LEFT_COLUMN_X - rowWidth / 2.0f, relicY - BUTTON_HEIGHT,
                    rowWidth, BUTTON_HEIGHT);

                Color textColor = hovered ? Settings.GREEN_TEXT_COLOR : getRelicTierColor(relic.tier);
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    relic.name,
                    LEFT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
                    relicY - 4.0f * Settings.scale, textColor);

                FontHelper.renderFontRightTopAligned(sb, FontHelper.cardDescFont_N,
                    "+",
                    LEFT_COLUMN_X + rowWidth / 2.0f - 8.0f * Settings.scale,
                    relicY - 4.0f * Settings.scale,
                    hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
            }
        }
    }

    private void renderAvailablePotions(SpriteBatch sb, float startY) {
        int maxPotions = getPotionSlots();
        boolean canAddMore = selectedPotions.size() < maxPotions;

        for (int i = 0; i < availablePotions.size(); i++) {
            float potionY = startY - i * ROW_HEIGHT;

            if (potionY > LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT && potionY < LIST_START_Y + ROW_HEIGHT * 2) {
                AbstractPotion potion = availablePotions.get(i);
                boolean hovered = i < availableItemHitboxes.length && availableItemHitboxes[i].hovered && canAddMore;

                Color bgColor = hovered ? new Color(0.2f, 0.3f, 0.2f, 0.8f) : new Color(0.1f, 0.1f, 0.15f, 0.5f);
                if (!canAddMore) {
                    bgColor = new Color(0.1f, 0.1f, 0.1f, 0.3f);
                }
                sb.setColor(bgColor);
                float rowWidth = COLUMN_WIDTH - 30.0f * Settings.scale;
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                    LEFT_COLUMN_X - rowWidth / 2.0f, potionY - BUTTON_HEIGHT,
                    rowWidth, BUTTON_HEIGHT);

                Color textColor = canAddMore ? (hovered ? Settings.GREEN_TEXT_COLOR : getPotionRarityColor(potion.rarity)) : new Color(0.4f, 0.4f, 0.4f, 1.0f);
                FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                    potion.name,
                    LEFT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
                    potionY - 4.0f * Settings.scale, textColor);

                if (canAddMore) {
                    FontHelper.renderFontRightTopAligned(sb, FontHelper.cardDescFont_N,
                        "+",
                        LEFT_COLUMN_X + rowWidth / 2.0f - 8.0f * Settings.scale,
                        potionY - 4.0f * Settings.scale,
                        hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);
                }
            }
        }
    }

    private void renderSelectedItems(SpriteBatch sb) {
        float y = LIST_START_Y + selectedScrollY;
        int row = 0;

        // Use a common visibility check for all items - must match updateSelectedItems
        float visibleTop = LIST_START_Y + ROW_HEIGHT * 2;
        float visibleBottom = LIST_START_Y - LIST_HEIGHT - ROW_HEIGHT;

        // Cards section header
        float headerY = y - row * ROW_HEIGHT;
        if (headerY > visibleBottom && headerY < visibleTop) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                "Deck (" + deckCards.size() + ")",
                RIGHT_COLUMN_X - COLUMN_WIDTH / 2.0f + 8.0f * Settings.scale,
                headerY - 4.0f * Settings.scale, Settings.GOLD_COLOR);
        }
        row++;

        // Cards
        for (int i = 0; i < deckCards.size(); i++) {
            float cardY = y - row * ROW_HEIGHT;
            row++;

            if (cardY > visibleBottom && cardY < visibleTop) {
                renderDeckCard(sb, i, cardY);
            }
        }

        // Relics section header
        float relicHeaderY = y - row * ROW_HEIGHT;
        if (relicHeaderY > visibleBottom && relicHeaderY < visibleTop) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                "Relics (" + selectedRelics.size() + ")",
                RIGHT_COLUMN_X - COLUMN_WIDTH / 2.0f + 8.0f * Settings.scale,
                relicHeaderY - 4.0f * Settings.scale, Settings.GOLD_COLOR);
        }
        row++;

        // Relics
        for (int i = 0; i < selectedRelics.size(); i++) {
            float relicY = y - row * ROW_HEIGHT;
            row++;

            if (relicY > visibleBottom && relicY < visibleTop) {
                renderSelectedRelic(sb, i, relicY);
            }
        }

        // Potions section header
        int maxPotions = getPotionSlots();
        float potionHeaderY = y - row * ROW_HEIGHT;
        if (potionHeaderY > visibleBottom && potionHeaderY < visibleTop) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
                "Potions (" + selectedPotions.size() + "/" + maxPotions + ")",
                RIGHT_COLUMN_X - COLUMN_WIDTH / 2.0f + 8.0f * Settings.scale,
                potionHeaderY - 4.0f * Settings.scale, Settings.GOLD_COLOR);
        }
        row++;

        // Potions
        for (int i = 0; i < selectedPotions.size(); i++) {
            float potionY = y - row * ROW_HEIGHT;
            row++;

            if (potionY > visibleBottom && potionY < visibleTop) {
                renderSelectedPotion(sb, i, potionY);
            }
        }
    }

    private void renderDeckCard(SpriteBatch sb, int index, float cardY) {
        DeckCard dc = deckCards.get(index);
        AbstractCard card = dc.card;

        Color bgColor = new Color(0.1f, 0.1f, 0.15f, 0.5f);
        sb.setColor(bgColor);
        float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            RIGHT_COLUMN_X - rowWidth / 2.0f, cardY - BUTTON_HEIGHT,
            rowWidth, BUTTON_HEIGHT);

        String name = card.name + (dc.upgraded ? "+" : "");
        Color textColor = dc.upgraded ? Settings.GREEN_TEXT_COLOR : getCardTypeColor(card.type);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            name,
            RIGHT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
            cardY - 4.0f * Settings.scale, textColor);

        // Upgrade button
        boolean upgradeHovered = index < deckCardUpgradeHitboxes.length && deckCardUpgradeHitboxes[index].hovered;
        boolean canUpgrade = card.canUpgrade();
        Color upgradeBg = upgradeHovered && canUpgrade ? new Color(0.3f, 0.4f, 0.3f, 0.9f) : new Color(0.15f, 0.2f, 0.15f, 0.6f);
        if (!canUpgrade && !dc.upgraded) {
            upgradeBg = new Color(0.2f, 0.2f, 0.2f, 0.3f);
        }
        float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 70.0f * Settings.scale;
        sb.setColor(upgradeBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - SMALL_BUTTON_WIDTH / 2.0f, cardY - BUTTON_HEIGHT, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            dc.upgraded ? "U" : "u",
            buttonX, cardY - BUTTON_HEIGHT / 2.0f,
            dc.upgraded ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR);

        // Remove button
        boolean removeHovered = index < deckCardRemoveHitboxes.length && deckCardRemoveHitboxes[index].hovered;
        Color removeBg = removeHovered ? new Color(0.4f, 0.2f, 0.2f, 0.9f) : new Color(0.2f, 0.1f, 0.1f, 0.6f);
        float removeX = buttonX + 35.0f * Settings.scale;
        sb.setColor(removeBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, removeX - SMALL_BUTTON_WIDTH / 2.0f, cardY - BUTTON_HEIGHT, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "-",
            removeX, cardY - BUTTON_HEIGHT / 2.0f,
            removeHovered ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);
    }

    private void renderSelectedRelic(SpriteBatch sb, int index, float relicY) {
        AbstractRelic relic = selectedRelics.get(index);

        Color bgColor = new Color(0.1f, 0.1f, 0.15f, 0.5f);
        sb.setColor(bgColor);
        float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            RIGHT_COLUMN_X - rowWidth / 2.0f, relicY - BUTTON_HEIGHT,
            rowWidth, BUTTON_HEIGHT);

        Color textColor = getRelicTierColor(relic.tier);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            relic.name,
            RIGHT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
            relicY - 4.0f * Settings.scale, textColor);

        // Remove button
        boolean removeHovered = index < relicRemoveHitboxes.length && relicRemoveHitboxes[index].hovered;
        Color removeBg = removeHovered ? new Color(0.4f, 0.2f, 0.2f, 0.9f) : new Color(0.2f, 0.1f, 0.1f, 0.6f);
        float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 35.0f * Settings.scale;
        sb.setColor(removeBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - SMALL_BUTTON_WIDTH / 2.0f, relicY - BUTTON_HEIGHT, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "-",
            buttonX, relicY - BUTTON_HEIGHT / 2.0f,
            removeHovered ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);
    }

    private void renderSelectedPotion(SpriteBatch sb, int index, float potionY) {
        AbstractPotion potion = selectedPotions.get(index);

        Color bgColor = new Color(0.1f, 0.1f, 0.15f, 0.5f);
        sb.setColor(bgColor);
        float rowWidth = COLUMN_WIDTH - 20.0f * Settings.scale;
        sb.draw(ImageMaster.WHITE_SQUARE_IMG,
            RIGHT_COLUMN_X - rowWidth / 2.0f, potionY - BUTTON_HEIGHT,
            rowWidth, BUTTON_HEIGHT);

        Color textColor = getPotionRarityColor(potion.rarity);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.cardDescFont_N,
            potion.name,
            RIGHT_COLUMN_X - rowWidth / 2.0f + 8.0f * Settings.scale,
            potionY - 4.0f * Settings.scale, textColor);

        // Remove button
        boolean removeHovered = index < potionRemoveHitboxes.length && potionRemoveHitboxes[index].hovered;
        Color removeBg = removeHovered ? new Color(0.4f, 0.2f, 0.2f, 0.9f) : new Color(0.2f, 0.1f, 0.1f, 0.6f);
        float buttonX = RIGHT_COLUMN_X + rowWidth / 2.0f - 35.0f * Settings.scale;
        sb.setColor(removeBg);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, buttonX - SMALL_BUTTON_WIDTH / 2.0f, potionY - BUTTON_HEIGHT, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT);
        FontHelper.renderFontCentered(sb, FontHelper.cardDescFont_N,
            "-",
            buttonX, potionY - BUTTON_HEIGHT / 2.0f,
            removeHovered ? Settings.RED_TEXT_COLOR : Settings.CREAM_COLOR);
    }

    private Color getCardTypeColor(AbstractCard.CardType type) {
        switch (type) {
            case ATTACK: return new Color(0.9f, 0.4f, 0.4f, 1.0f);
            case SKILL: return new Color(0.4f, 0.7f, 0.9f, 1.0f);
            case POWER: return new Color(0.9f, 0.8f, 0.3f, 1.0f);
            default: return Settings.CREAM_COLOR;
        }
    }

    private Color getRelicTierColor(AbstractRelic.RelicTier tier) {
        switch (tier) {
            case STARTER: return new Color(0.6f, 0.6f, 0.6f, 1.0f);
            case COMMON: return new Color(0.7f, 0.7f, 0.7f, 1.0f);
            case UNCOMMON: return new Color(0.4f, 0.7f, 0.9f, 1.0f);
            case RARE: return new Color(0.9f, 0.8f, 0.3f, 1.0f);
            case BOSS: return new Color(0.9f, 0.4f, 0.4f, 1.0f);
            case SHOP: return new Color(0.5f, 0.9f, 0.5f, 1.0f);
            case SPECIAL: return new Color(0.8f, 0.5f, 0.9f, 1.0f);
            default: return Settings.CREAM_COLOR;
        }
    }

    private Color getPotionRarityColor(AbstractPotion.PotionRarity rarity) {
        switch (rarity) {
            case COMMON: return new Color(0.7f, 0.7f, 0.7f, 1.0f);
            case UNCOMMON: return new Color(0.4f, 0.7f, 0.9f, 1.0f);
            case RARE: return new Color(0.9f, 0.8f, 0.3f, 1.0f);
            default: return Settings.CREAM_COLOR;
        }
    }
}
