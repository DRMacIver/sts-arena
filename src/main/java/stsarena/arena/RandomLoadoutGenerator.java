package stsarena.arena;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import stsarena.STSArena;
import stsarena.data.ArenaRepository;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Generates random but "vaguely sensible" loadouts for arena fights.
 *
 * Uses LoadoutBuilder for the core logic, then converts IDs to game objects.
 */
public class RandomLoadoutGenerator {

    private static final Random random = new Random();

    /**
     * Result of generating a random loadout.
     */
    public static class GeneratedLoadout {
        public final String id;           // Unique identifier (UUID)
        public final String name;         // Human-readable name
        public final long createdAt;      // Timestamp when created
        public final AbstractPlayer.PlayerClass playerClass;
        public final List<AbstractCard> deck;
        public final List<AbstractRelic> relics;
        public final List<AbstractPotion> potions;
        public final int potionSlots;     // Number of potion slots (affected by ascension and Potion Belt)
        public final boolean hasPrismaticShard;
        public final int maxHp;
        public final int currentHp;
        public final int ascensionLevel;  // 0-20

        public GeneratedLoadout(String id, String name, long createdAt,
                                AbstractPlayer.PlayerClass playerClass, List<AbstractCard> deck,
                                List<AbstractRelic> relics, List<AbstractPotion> potions,
                                int potionSlots,
                                boolean hasPrismaticShard,
                                int maxHp, int currentHp, int ascensionLevel) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.playerClass = playerClass;
            this.deck = deck;
            this.relics = relics;
            this.potions = potions != null ? potions : new ArrayList<>();
            this.potionSlots = potionSlots;
            this.hasPrismaticShard = hasPrismaticShard;
            this.maxHp = maxHp;
            this.currentHp = currentHp;
            this.ascensionLevel = ascensionLevel;
        }
    }

    /**
     * Generate a completely random loadout.
     */
    public static GeneratedLoadout generate() {
        // Pick a random character
        String[] classes = LoadoutConfig.PLAYER_CLASS_NAMES;
        String playerClassName = classes[random.nextInt(classes.length)];

        return generateForClass(AbstractPlayer.PlayerClass.valueOf(playerClassName));
    }

    /**
     * Generate a random loadout for a specific character class.
     */
    public static GeneratedLoadout generateForClass(AbstractPlayer.PlayerClass playerClass) {
        STSArena.logger.info("Generating random loadout for: " + playerClass);

        // Use the LoadoutBuilder to create the loadout
        LoadoutBuilder.BuiltLoadout built = LoadoutBuilder.generateForClass(
            playerClass.name(), random);

        // Generate unique ID and name
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String name = generateLoadoutName(createdAt);

        // Convert card IDs to actual cards
        List<AbstractCard> deck = new ArrayList<>();
        for (LoadoutBuilder.CardEntry entry : built.deck) {
            AbstractCard card = getCard(entry.cardId);
            if (card != null) {
                AbstractCard copy = card.makeCopy();
                if (entry.upgraded && copy.canUpgrade()) {
                    copy.upgrade();
                }
                deck.add(copy);
            } else {
                STSArena.logger.warn("Card not found: " + entry.cardId);
            }
        }

        // Convert relic IDs to actual relics
        List<AbstractRelic> relics = new ArrayList<>();
        boolean hasPrismaticShard = false;
        for (String relicId : built.relics) {
            AbstractRelic relic = RelicLibrary.getRelic(relicId);
            if (relic != null) {
                relics.add(relic.makeCopy());
                if ("PrismaticShard".equals(relicId)) {
                    hasPrismaticShard = true;
                }
            } else {
                STSArena.logger.warn("Relic not found: " + relicId);
            }
        }

        // Convert potion IDs to actual potions
        List<AbstractPotion> potions = new ArrayList<>();
        for (String potionId : built.potions) {
            AbstractPotion potion = PotionHelper.getPotion(potionId);
            if (potion != null) {
                potions.add(potion.makeCopy());
            } else {
                STSArena.logger.warn("Potion not found: " + potionId);
            }
        }

        STSArena.logger.info("Generated loadout '" + name + "': " + deck.size() + " cards, " +
                            relics.size() + " relics, " + potions.size() + " potions, " +
                            built.currentHp + "/" + built.maxHp + " HP, A" + built.ascension);

        return new GeneratedLoadout(id, name, createdAt, playerClass, deck, relics, potions,
            built.potionSlots, hasPrismaticShard, built.maxHp, built.currentHp, built.ascension);
    }

    /**
     * Get a card by ID, handling the Strike/Defend color variants.
     */
    private static AbstractCard getCard(String cardId) {
        // First try direct lookup
        AbstractCard card = CardLibrary.getCard(cardId);
        if (card != null) return card;

        // Handle legacy Strike/Defend IDs without color suffix
        if (cardId.equals("Strike") || cardId.equals("Defend")) {
            // Try each color variant
            for (String suffix : new String[]{"_R", "_G", "_B", "_P"}) {
                card = CardLibrary.getCard(cardId + suffix);
                if (card != null) return card;
            }
        }

        return null;
    }

    /**
     * Generate a human-readable name for a loadout.
     * Format: "Random #N" where N is a sequence number from the database.
     */
    private static String generateLoadoutName(long timestamp) {
        int nextNum = getNextLoadoutNumber();
        return "Random #" + nextNum;
    }

    /**
     * Get the next loadout number from the database.
     */
    private static int getNextLoadoutNumber() {
        try {
            java.sql.Connection conn = stsarena.data.ArenaDatabase.getInstance().getConnection();
            if (conn != null) {
                try (java.sql.Statement stmt = conn.createStatement();
                     java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) + 1 as next_num FROM loadouts")) {
                    if (rs.next()) {
                        return rs.getInt("next_num");
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to get next loadout number", e);
        }
        // Fallback to random number if database query fails
        return random.nextInt(9999) + 1;
    }

    /**
     * Get a random encounter ID for any act.
     */
    public static String getRandomEncounter() {
        return LoadoutConfig.ENCOUNTERS[random.nextInt(LoadoutConfig.ENCOUNTERS.length)];
    }

    private static final Gson gson = new Gson();

    /**
     * Reconstruct a GeneratedLoadout from a saved LoadoutRecord.
     */
    public static GeneratedLoadout fromSavedLoadout(ArenaRepository.LoadoutRecord record) {
        STSArena.logger.info("Reconstructing loadout from saved record: " + record.name);

        AbstractPlayer.PlayerClass playerClass = AbstractPlayer.PlayerClass.valueOf(record.characterClass);

        // Deserialize deck
        List<AbstractCard> deck = new ArrayList<>();
        try {
            Type cardListType = new TypeToken<List<ArenaRepository.CardData>>(){}.getType();
            List<ArenaRepository.CardData> cardDataList = gson.fromJson(record.deckJson, cardListType);
            for (ArenaRepository.CardData cardData : cardDataList) {
                AbstractCard card = CardLibrary.getCard(cardData.id);
                if (card != null) {
                    AbstractCard copy = card.makeCopy();
                    for (int i = 0; i < cardData.upgrades; i++) {
                        if (copy.canUpgrade()) {
                            copy.upgrade();
                        }
                    }
                    deck.add(copy);
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to deserialize deck", e);
        }

        // Deserialize relics
        List<AbstractRelic> relics = new ArrayList<>();
        boolean hasPrismaticShard = false;
        try {
            Type relicListType = new TypeToken<List<String>>(){}.getType();
            List<String> relicIds = gson.fromJson(record.relicsJson, relicListType);
            for (String relicId : relicIds) {
                AbstractRelic relic = RelicLibrary.getRelic(relicId);
                if (relic != null) {
                    relics.add(relic.makeCopy());
                    if ("PrismaticShard".equals(relicId)) {
                        hasPrismaticShard = true;
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to deserialize relics", e);
        }

        // Deserialize potions
        List<AbstractPotion> potions = new ArrayList<>();
        try {
            if (record.potionsJson != null && !record.potionsJson.isEmpty()) {
                Type potionListType = new TypeToken<List<String>>(){}.getType();
                List<String> potionIds = gson.fromJson(record.potionsJson, potionListType);
                for (String potionId : potionIds) {
                    AbstractPotion potion = PotionHelper.getPotion(potionId);
                    if (potion != null) {
                        potions.add(potion.makeCopy());
                    }
                }
            }
        } catch (Exception e) {
            STSArena.logger.error("Failed to deserialize potions", e);
        }

        // Use stored potionSlots, fall back to calculating from ascension/relics if not stored
        int potionSlots = record.potionSlots;
        if (potionSlots == 0) {
            // Legacy records without potion_slots - calculate from ascension and relics
            potionSlots = record.ascensionLevel >= 11 ? 2 : 3;
            for (AbstractRelic relic : relics) {
                if ("Potion Belt".equals(relic.relicId)) {
                    potionSlots += 2;
                    break;
                }
            }
        }

        STSArena.logger.info("Reconstructed loadout: " + deck.size() + " cards, " + relics.size() + " relics, " +
            potions.size() + " potions, " + potionSlots + " slots, A" + record.ascensionLevel);

        return new GeneratedLoadout(
            record.uuid,
            record.name,
            record.createdAt,
            playerClass,
            deck,
            relics,
            potions,
            potionSlots,
            hasPrismaticShard,
            record.maxHp,
            record.currentHp,
            record.ascensionLevel
        );
    }
}
