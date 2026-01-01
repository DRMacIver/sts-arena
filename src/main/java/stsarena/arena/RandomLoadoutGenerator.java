package stsarena.arena;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import stsarena.STSArena;
import stsarena.data.ArenaRepository;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates random but "vaguely sensible" loadouts for arena fights.
 *
 * Pure configuration logic is in LoadoutConfig for testability.
 * This class handles the StS-specific card/relic library interactions.
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
        public final boolean hasPrismaticShard;
        public final int maxHp;
        public final int currentHp;
        public final int ascensionLevel;  // 0-20

        public GeneratedLoadout(String id, String name, long createdAt,
                                AbstractPlayer.PlayerClass playerClass, List<AbstractCard> deck,
                                List<AbstractRelic> relics, boolean hasPrismaticShard,
                                int maxHp, int currentHp, int ascensionLevel) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.playerClass = playerClass;
            this.deck = deck;
            this.relics = relics;
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
        AbstractPlayer.PlayerClass[] classes = LoadoutConfig.PLAYER_CLASSES;
        AbstractPlayer.PlayerClass playerClass = classes[random.nextInt(classes.length)];

        return generateForClass(playerClass);
    }

    /**
     * Generate a random loadout for a specific character class.
     */
    public static GeneratedLoadout generateForClass(AbstractPlayer.PlayerClass playerClass) {
        STSArena.logger.info("Generating random loadout for: " + playerClass);

        // Generate unique ID and name
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String name = generateLoadoutName(createdAt);

        // Decide if we're using Prismatic Shard (allows any card color)
        boolean hasPrismaticShard = random.nextDouble() < LoadoutConfig.PRISMATIC_SHARD_CHANCE;

        // Generate relics first (Prismatic Shard affects card selection)
        List<AbstractRelic> relics = generateRelics(playerClass, hasPrismaticShard);

        // Generate deck
        List<AbstractCard> deck = generateDeck(playerClass, hasPrismaticShard);

        // Calculate HP based on character
        int baseMaxHp = LoadoutConfig.getBaseMaxHp(playerClass);
        // Add some variance and potential bonus from relics
        int maxHp = baseMaxHp + random.nextInt(20);
        int currentHp = (int) (maxHp * (0.7 + random.nextDouble() * 0.3)); // 70-100% HP

        // Random ascension level (0-20)
        int ascensionLevel = random.nextInt(21);

        STSArena.logger.info("Generated loadout '" + name + "': " + deck.size() + " cards, " +
                            relics.size() + " relics, " + currentHp + "/" + maxHp + " HP, A" + ascensionLevel);

        return new GeneratedLoadout(id, name, createdAt, playerClass, deck, relics, hasPrismaticShard, maxHp, currentHp, ascensionLevel);
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

    private static List<AbstractRelic> generateRelics(AbstractPlayer.PlayerClass playerClass,
                                                       boolean includePrismaticShard) {
        List<AbstractRelic> result = new ArrayList<>();

        // Get character's starting relic
        AbstractRelic starterRelic = getStarterRelic(playerClass);
        if (starterRelic != null) {
            result.add(starterRelic);
        }

        // Add Prismatic Shard if selected
        if (includePrismaticShard) {
            AbstractRelic prismatic = RelicLibrary.getRelic("PrismaticShard");
            if (prismatic != null) {
                result.add(prismatic.makeCopy());
            }
        }

        // Gather available relics
        List<AbstractRelic> availableRelics = new ArrayList<>();

        // Add common, uncommon, rare relics from the library
        for (AbstractRelic relic : RelicLibrary.commonList) {
            if (!LoadoutConfig.isExcludedRelic(relic.relicId)) {
                availableRelics.add(relic);
            }
        }
        for (AbstractRelic relic : RelicLibrary.uncommonList) {
            if (!LoadoutConfig.isExcludedRelic(relic.relicId)) {
                availableRelics.add(relic);
            }
        }
        for (AbstractRelic relic : RelicLibrary.rareList) {
            if (!LoadoutConfig.isExcludedRelic(relic.relicId)) {
                availableRelics.add(relic);
            }
        }
        for (AbstractRelic relic : RelicLibrary.shopList) {
            if (!LoadoutConfig.isExcludedRelic(relic.relicId)) {
                availableRelics.add(relic);
            }
        }

        // Shuffle and pick
        Collections.shuffle(availableRelics, random);
        int numRelics = LoadoutConfig.MIN_RELICS +
            random.nextInt(LoadoutConfig.MAX_RELICS - LoadoutConfig.MIN_RELICS + 1);

        Set<String> addedRelicIds = result.stream()
            .map(r -> r.relicId)
            .collect(Collectors.toSet());

        for (AbstractRelic relic : availableRelics) {
            if (result.size() >= numRelics) break;
            if (!addedRelicIds.contains(relic.relicId)) {
                result.add(relic.makeCopy());
                addedRelicIds.add(relic.relicId);
            }
        }

        return result;
    }

    private static List<AbstractCard> generateDeck(AbstractPlayer.PlayerClass playerClass,
                                                    boolean prismaticShard) {
        List<AbstractCard> result = new ArrayList<>();

        // Get available cards
        List<AbstractCard> availableCards = new ArrayList<>();
        AbstractCard.CardColor primaryColor = LoadoutConfig.getCardColor(playerClass);

        // Always include colorless cards
        addCardsOfColor(availableCards, AbstractCard.CardColor.COLORLESS);

        if (prismaticShard) {
            // Include all colors
            addCardsOfColor(availableCards, AbstractCard.CardColor.RED);
            addCardsOfColor(availableCards, AbstractCard.CardColor.GREEN);
            addCardsOfColor(availableCards, AbstractCard.CardColor.BLUE);
            addCardsOfColor(availableCards, AbstractCard.CardColor.PURPLE);
        } else {
            // Only the character's color
            addCardsOfColor(availableCards, primaryColor);
        }

        // Filter out cards that are problematic for arena
        availableCards = availableCards.stream()
            .filter(c -> !isProblematicCard(c))
            .collect(Collectors.toList());

        // Build a deck with some balance
        int deckSize = LoadoutConfig.MIN_DECK_SIZE +
            random.nextInt(LoadoutConfig.MAX_DECK_SIZE - LoadoutConfig.MIN_DECK_SIZE + 1);

        // Separate by type for balance
        List<AbstractCard> attacks = availableCards.stream()
            .filter(c -> c.type == AbstractCard.CardType.ATTACK)
            .collect(Collectors.toList());
        List<AbstractCard> skills = availableCards.stream()
            .filter(c -> c.type == AbstractCard.CardType.SKILL)
            .collect(Collectors.toList());
        List<AbstractCard> powers = availableCards.stream()
            .filter(c -> c.type == AbstractCard.CardType.POWER)
            .collect(Collectors.toList());

        Collections.shuffle(attacks, random);
        Collections.shuffle(skills, random);
        Collections.shuffle(powers, random);

        // Aim for roughly: 40-50% attacks, 35-45% skills, 10-20% powers
        int numAttacks = LoadoutConfig.calculateAttackCount(deckSize, random.nextDouble());
        int numPowers = LoadoutConfig.calculatePowerCount(powers.size(), random.nextInt(100));
        int numSkills = deckSize - numAttacks - numPowers;

        // Add cards
        addCardsFromList(result, attacks, numAttacks);
        addCardsFromList(result, skills, numSkills);
        addCardsFromList(result, powers, numPowers);

        // Upgrade some cards
        for (AbstractCard card : result) {
            if (card.canUpgrade() && random.nextDouble() < LoadoutConfig.UPGRADE_CHANCE) {
                card.upgrade();
            }
        }

        return result;
    }

    private static void addCardsOfColor(List<AbstractCard> list, AbstractCard.CardColor color) {
        // Use CardLibrary to get cards of this color
        HashMap<String, AbstractCard> library = CardLibrary.cards;
        for (AbstractCard card : library.values()) {
            if (card.color == color && card.rarity != AbstractCard.CardRarity.BASIC &&
                card.rarity != AbstractCard.CardRarity.SPECIAL) {
                list.add(card);
            }
        }
    }

    private static void addCardsFromList(List<AbstractCard> deck, List<AbstractCard> source, int count) {
        for (int i = 0; i < count && i < source.size(); i++) {
            deck.add(source.get(i).makeCopy());
        }
    }

    private static boolean isProblematicCard(AbstractCard card) {
        // Skip cards that require specific setup or are status/curse
        if (card.type == AbstractCard.CardType.STATUS || card.type == AbstractCard.CardType.CURSE) {
            return true;
        }
        // Use config for card-specific exclusions
        return LoadoutConfig.isExcludedCard(card.cardID);
    }

    private static AbstractRelic getStarterRelic(AbstractPlayer.PlayerClass playerClass) {
        String relicId = LoadoutConfig.getStarterRelicId(playerClass);
        if (relicId == null) {
            return null;
        }
        AbstractRelic relic = RelicLibrary.getRelic(relicId);
        return relic != null ? relic.makeCopy() : null;
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

        STSArena.logger.info("Reconstructed loadout: " + deck.size() + " cards, " + relics.size() + " relics, A" + record.ascensionLevel);

        return new GeneratedLoadout(
            record.uuid,
            record.name,
            record.createdAt,
            playerClass,
            deck,
            relics,
            hasPrismaticShard,
            record.maxHp,
            record.currentHp,
            record.ascensionLevel
        );
    }
}
