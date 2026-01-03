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
        List<String> failedCards = new ArrayList<>();
        for (LoadoutBuilder.CardEntry entry : built.deck) {
            AbstractCard card = getCard(entry.cardId);
            if (card != null) {
                AbstractCard copy = card.makeCopy();
                if (entry.upgraded && copy.canUpgrade()) {
                    copy.upgrade();
                }
                deck.add(copy);
            } else {
                failedCards.add(entry.cardId);
            }
        }

        // Log any failed card conversions with detailed error reporting
        if (!failedCards.isEmpty()) {
            STSArena.logger.error("=== CARD ID MISMATCH DETECTED ===");
            STSArena.logger.error("Failed to find " + failedCards.size() + " cards for " + playerClass.name() + ":");
            for (String failedId : failedCards) {
                STSArena.logger.error("  - Card ID not found: \"" + failedId + "\"");
                // Try to suggest similar IDs that might be correct
                String suggestion = suggestSimilarCardId(failedId);
                if (suggestion != null) {
                    STSArena.logger.error("    Possible correct ID: \"" + suggestion + "\"");
                }
            }
            STSArena.logger.error("Check LoadoutConfig.java for incorrect card IDs.");
            STSArena.logger.error("Watcher cards use CamelCase (e.g., TalkToTheHand, not 'Talk to the Hand')");
            STSArena.logger.error("=================================");
        }

        // Ensure minimum deck size - add starter cards if needed
        if (deck.size() < LoadoutConfig.MIN_DECK_SIZE) {
            STSArena.logger.warn("Deck too small (" + deck.size() + " cards), adding starter cards");
            List<String> starterDeck = LoadoutConfig.getStarterDeck(playerClass.name());
            for (String cardId : starterDeck) {
                if (deck.size() >= LoadoutConfig.MIN_DECK_SIZE) break;
                AbstractCard card = getCard(cardId);
                if (card != null) {
                    deck.add(card.makeCopy());
                }
            }
        }

        // Final safety check - this should never happen now
        if (deck.size() < LoadoutConfig.MIN_DECK_SIZE) {
            STSArena.logger.error("CRITICAL: Could not build minimum deck size. Built deck has " + deck.size() +
                " cards, needed " + LoadoutConfig.MIN_DECK_SIZE + ". Failed cards: " + failedCards);
        }

        // Ensure deck has at least some attacks - a deck with no attacks is not reasonable
        int attackCount = 0;
        for (AbstractCard card : deck) {
            if (card.type == AbstractCard.CardType.ATTACK) {
                attackCount++;
            }
        }
        if (attackCount == 0) {
            STSArena.logger.warn("Deck has no attacks, adding Strikes");
            // Add 3-5 Strikes
            String strikeId = getStrikeIdForClass(playerClass.name());
            int strikesToAdd = 3 + random.nextInt(3);
            for (int i = 0; i < strikesToAdd; i++) {
                AbstractCard strike = getCard(strikeId);
                if (strike != null) {
                    deck.add(strike.makeCopy());
                }
            }
        }

        // Convert relic IDs to actual relics
        // NOTE: RelicLibrary.getRelic() returns Circlet if ID not found (never null)
        // So we must check with isARelic() first
        List<AbstractRelic> relics = new ArrayList<>();
        List<String> failedRelics = new ArrayList<>();
        boolean hasPrismaticShard = false;
        for (String relicId : built.relics) {
            if (RelicLibrary.isARelic(relicId)) {
                AbstractRelic relic = RelicLibrary.getRelic(relicId);
                relics.add(relic.makeCopy());
                if ("PrismaticShard".equals(relicId)) {
                    hasPrismaticShard = true;
                }
            } else {
                failedRelics.add(relicId);
            }
        }
        if (!failedRelics.isEmpty()) {
            STSArena.logger.error("=== RELIC ID MISMATCH DETECTED ===");
            STSArena.logger.error("Failed to find " + failedRelics.size() + " relics:");
            for (String failedId : failedRelics) {
                STSArena.logger.error("  - Relic ID not found: \"" + failedId + "\"");
            }
            STSArena.logger.error("Check LoadoutConfig.java for incorrect relic IDs.");
            STSArena.logger.error("Note: Invalid IDs would have become Circlets before this fix.");
            STSArena.logger.error("==================================");
        }

        // Convert potion IDs to actual potions
        List<AbstractPotion> potions = new ArrayList<>();
        List<String> failedPotions = new ArrayList<>();
        for (String potionId : built.potions) {
            AbstractPotion potion = PotionHelper.getPotion(potionId);
            if (potion != null) {
                potions.add(potion.makeCopy());
            } else {
                failedPotions.add(potionId);
            }
        }
        if (!failedPotions.isEmpty()) {
            STSArena.logger.error("=== POTION ID MISMATCH DETECTED ===");
            STSArena.logger.error("Failed to find " + failedPotions.size() + " potions:");
            for (String failedId : failedPotions) {
                STSArena.logger.error("  - Potion ID not found: \"" + failedId + "\"");
            }
            STSArena.logger.error("Check LoadoutConfig.java for incorrect potion IDs.");
            STSArena.logger.error("===================================");
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
     * Get the Strike card ID for a given player class.
     */
    private static String getStrikeIdForClass(String playerClass) {
        switch (playerClass) {
            case "IRONCLAD": return "Strike_R";
            case "THE_SILENT": return "Strike_G";
            case "DEFECT": return "Strike_B";
            case "WATCHER": return "Strike_P";
            default: return "Strike_R";
        }
    }

    /**
     * Try to suggest a correct card ID based on a failed ID.
     * Attempts CamelCase conversion and common variations.
     */
    private static String suggestSimilarCardId(String failedId) {
        // Try CamelCase version (remove spaces, capitalize each word)
        if (failedId.contains(" ")) {
            String camelCase = toCamelCase(failedId);
            AbstractCard card = CardLibrary.getCard(camelCase);
            if (card != null) {
                return camelCase;
            }
        }

        // Try with underscores replaced by nothing
        if (failedId.contains("_")) {
            String noUnderscores = failedId.replace("_", "");
            AbstractCard card = CardLibrary.getCard(noUnderscores);
            if (card != null) {
                return noUnderscores;
            }
        }

        // Try adding common suffixes for starter cards
        for (String suffix : new String[]{"_R", "_G", "_B", "_P"}) {
            AbstractCard card = CardLibrary.getCard(failedId + suffix);
            if (card != null) {
                return failedId + suffix;
            }
        }

        return null;
    }

    /**
     * Convert a space-separated string to CamelCase.
     * "Talk to the Hand" -> "TalkToTheHand"
     */
    private static String toCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        for (String word : input.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        return result.toString();
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
        // NOTE: RelicLibrary.getRelic() returns Circlet if ID not found (never null)
        // Also filter out any Circlets that might have been saved in older loadouts
        // Handle both new format (RelicData with counter) and old format (just strings)
        List<AbstractRelic> relics = new ArrayList<>();
        boolean hasPrismaticShard = false;
        try {
            // Try new format first (RelicData with id and counter)
            Type relicDataListType = new TypeToken<List<ArenaRepository.RelicData>>(){}.getType();
            List<ArenaRepository.RelicData> relicDataList = gson.fromJson(record.relicsJson, relicDataListType);

            // Check if it's actually the new format by looking at the first element
            boolean isNewFormat = !relicDataList.isEmpty() && relicDataList.get(0).id != null;

            if (isNewFormat) {
                for (ArenaRepository.RelicData relicData : relicDataList) {
                    String relicId = relicData.id;
                    // Skip Circlets (placeholder relics that shouldn't appear in arena)
                    if ("Circlet".equals(relicId) || "Red Circlet".equals(relicId)) {
                        STSArena.logger.warn("Skipping Circlet from saved loadout (likely a bug in previous save)");
                        continue;
                    }
                    if (RelicLibrary.isARelic(relicId)) {
                        AbstractRelic relic = RelicLibrary.getRelic(relicId);
                        AbstractRelic copy = relic.makeCopy();
                        copy.counter = relicData.counter;  // Restore counter
                        relics.add(copy);
                        if ("PrismaticShard".equals(relicId)) {
                            hasPrismaticShard = true;
                        }
                    } else {
                        STSArena.logger.warn("Relic ID not found in saved loadout: " + relicId);
                    }
                }
            } else {
                // Fall back to old format (just list of strings)
                Type relicListType = new TypeToken<List<String>>(){}.getType();
                List<String> relicIds = gson.fromJson(record.relicsJson, relicListType);
                for (String relicId : relicIds) {
                    if ("Circlet".equals(relicId) || "Red Circlet".equals(relicId)) {
                        continue;
                    }
                    if (RelicLibrary.isARelic(relicId)) {
                        AbstractRelic relic = RelicLibrary.getRelic(relicId);
                        relics.add(relic.makeCopy());
                        if ("PrismaticShard".equals(relicId)) {
                            hasPrismaticShard = true;
                        }
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
