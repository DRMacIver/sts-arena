package stsarena.arena;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import stsarena.STSArena;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates random but "vaguely sensible" loadouts for arena fights.
 */
public class RandomLoadoutGenerator {

    private static final Random random = new Random();

    // Loadout parameters
    private static final int MIN_DECK_SIZE = 15;
    private static final int MAX_DECK_SIZE = 25;
    private static final int MIN_RELICS = 5;
    private static final int MAX_RELICS = 10;
    private static final double UPGRADE_CHANCE = 0.4;
    private static final double PRISMATIC_SHARD_CHANCE = 0.15;

    /**
     * Result of generating a random loadout.
     */
    public static class GeneratedLoadout {
        public final AbstractPlayer.PlayerClass playerClass;
        public final List<AbstractCard> deck;
        public final List<AbstractRelic> relics;
        public final boolean hasPrismaticShard;
        public final int maxHp;
        public final int currentHp;

        public GeneratedLoadout(AbstractPlayer.PlayerClass playerClass, List<AbstractCard> deck,
                                List<AbstractRelic> relics, boolean hasPrismaticShard,
                                int maxHp, int currentHp) {
            this.playerClass = playerClass;
            this.deck = deck;
            this.relics = relics;
            this.hasPrismaticShard = hasPrismaticShard;
            this.maxHp = maxHp;
            this.currentHp = currentHp;
        }
    }

    /**
     * Generate a completely random loadout.
     */
    public static GeneratedLoadout generate() {
        // Pick a random character
        AbstractPlayer.PlayerClass[] classes = {
            AbstractPlayer.PlayerClass.IRONCLAD,
            AbstractPlayer.PlayerClass.THE_SILENT,
            AbstractPlayer.PlayerClass.DEFECT,
            AbstractPlayer.PlayerClass.WATCHER
        };
        AbstractPlayer.PlayerClass playerClass = classes[random.nextInt(classes.length)];

        return generateForClass(playerClass);
    }

    /**
     * Generate a random loadout for a specific character class.
     */
    public static GeneratedLoadout generateForClass(AbstractPlayer.PlayerClass playerClass) {
        STSArena.logger.info("Generating random loadout for: " + playerClass);

        // Decide if we're using Prismatic Shard (allows any card color)
        boolean hasPrismaticShard = random.nextDouble() < PRISMATIC_SHARD_CHANCE;

        // Generate relics first (Prismatic Shard affects card selection)
        List<AbstractRelic> relics = generateRelics(playerClass, hasPrismaticShard);

        // Generate deck
        List<AbstractCard> deck = generateDeck(playerClass, hasPrismaticShard);

        // Calculate HP based on character
        int baseMaxHp = getBaseMaxHp(playerClass);
        // Add some variance and potential bonus from relics
        int maxHp = baseMaxHp + random.nextInt(20);
        int currentHp = (int) (maxHp * (0.7 + random.nextDouble() * 0.3)); // 70-100% HP

        STSArena.logger.info("Generated loadout: " + deck.size() + " cards, " +
                            relics.size() + " relics, " + currentHp + "/" + maxHp + " HP");

        return new GeneratedLoadout(playerClass, deck, relics, hasPrismaticShard, maxHp, currentHp);
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

        // Add common, uncommon, rare relics
        for (String relicId : RelicLibrary.commonList) {
            AbstractRelic relic = RelicLibrary.getRelic(relicId);
            if (relic != null && canUseRelic(relic, playerClass)) {
                availableRelics.add(relic);
            }
        }
        for (String relicId : RelicLibrary.uncommonList) {
            AbstractRelic relic = RelicLibrary.getRelic(relicId);
            if (relic != null && canUseRelic(relic, playerClass)) {
                availableRelics.add(relic);
            }
        }
        for (String relicId : RelicLibrary.rareList) {
            AbstractRelic relic = RelicLibrary.getRelic(relicId);
            if (relic != null && canUseRelic(relic, playerClass)) {
                availableRelics.add(relic);
            }
        }
        for (String relicId : RelicLibrary.shopList) {
            AbstractRelic relic = RelicLibrary.getRelic(relicId);
            if (relic != null && canUseRelic(relic, playerClass)) {
                availableRelics.add(relic);
            }
        }

        // Shuffle and pick
        Collections.shuffle(availableRelics, random);
        int numRelics = MIN_RELICS + random.nextInt(MAX_RELICS - MIN_RELICS + 1);

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

    private static boolean canUseRelic(AbstractRelic relic, AbstractPlayer.PlayerClass playerClass) {
        // Filter out relics that don't make sense or are problematic
        String id = relic.relicId;

        // Skip boss relics that replace starter (we handle starter separately)
        if (id.equals("Black Blood") || id.equals("Ring of the Serpent") ||
            id.equals("FrozenCore") || id.equals("HolyWater")) {
            return false;
        }

        // Skip relics that require specific game state
        if (id.equals("Circlet") || id.equals("Red Circlet")) {
            return false;
        }

        // Skip Neow's Lament (boss kill effect doesn't apply in arena)
        if (id.equals("NeowsBlessing")) {
            return false;
        }

        return true;
    }

    private static List<AbstractCard> generateDeck(AbstractPlayer.PlayerClass playerClass,
                                                    boolean prismaticShard) {
        List<AbstractCard> result = new ArrayList<>();

        // Get available cards
        List<AbstractCard> availableCards = new ArrayList<>();
        AbstractCard.CardColor primaryColor = getCardColor(playerClass);

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
        int deckSize = MIN_DECK_SIZE + random.nextInt(MAX_DECK_SIZE - MIN_DECK_SIZE + 1);

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
        int numAttacks = (int) (deckSize * (0.4 + random.nextDouble() * 0.1));
        int numPowers = Math.min(powers.size(), 1 + random.nextInt(4)); // 1-4 powers
        int numSkills = deckSize - numAttacks - numPowers;

        // Add cards
        addCardsFromList(result, attacks, numAttacks);
        addCardsFromList(result, skills, numSkills);
        addCardsFromList(result, powers, numPowers);

        // Upgrade some cards
        for (AbstractCard card : result) {
            if (card.canUpgrade() && random.nextDouble() < UPGRADE_CHANCE) {
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
        // Skip cards that don't work well in isolated fights
        String id = card.cardID;
        if (id.contains("Ritual") && id.contains("Dagger")) {
            return true; // Ritual Dagger needs kills across fights
        }
        if (id.equals("Lesson Learned")) {
            return true; // Needs to kill to upgrade
        }
        return false;
    }

    private static AbstractCard.CardColor getCardColor(AbstractPlayer.PlayerClass playerClass) {
        switch (playerClass) {
            case IRONCLAD:
                return AbstractCard.CardColor.RED;
            case THE_SILENT:
                return AbstractCard.CardColor.GREEN;
            case DEFECT:
                return AbstractCard.CardColor.BLUE;
            case WATCHER:
                return AbstractCard.CardColor.PURPLE;
            default:
                return AbstractCard.CardColor.RED;
        }
    }

    private static AbstractRelic getStarterRelic(AbstractPlayer.PlayerClass playerClass) {
        String relicId;
        switch (playerClass) {
            case IRONCLAD:
                relicId = "Burning Blood";
                break;
            case THE_SILENT:
                relicId = "Ring of the Snake";
                break;
            case DEFECT:
                relicId = "Cracked Core";
                break;
            case WATCHER:
                relicId = "PureWater";
                break;
            default:
                return null;
        }
        AbstractRelic relic = RelicLibrary.getRelic(relicId);
        return relic != null ? relic.makeCopy() : null;
    }

    private static int getBaseMaxHp(AbstractPlayer.PlayerClass playerClass) {
        switch (playerClass) {
            case IRONCLAD:
                return 80;
            case THE_SILENT:
                return 70;
            case DEFECT:
                return 75;
            case WATCHER:
                return 72;
            default:
                return 75;
        }
    }

    /**
     * Get a random encounter ID for any act.
     */
    public static String getRandomEncounter() {
        // Mix of encounters from all acts
        String[] encounters = {
            // Act 1 normal
            "Cultist", "Jaw Worm", "2 Louse", "Small Slimes",
            "Blue Slaver", "Gremlin Gang", "Looter", "Large Slime",
            "Lots of Slimes", "Exordium Thugs", "Exordium Wildlife",
            "Red Slaver", "3 Louse", "2 Fungi Beasts",
            // Act 1 elites
            "Gremlin Nob", "Lagavulin", "3 Sentries",
            // Act 2 normal
            "Chosen", "Shell Parasite", "3 Byrds", "2 Thieves",
            "Chosen and Byrds", "Cultist and Chosen", "Snecko",
            "Snake Plant", "Centurion and Healer", "Shelled Parasite and Fungi",
            // Act 2 elites
            "Gremlin Leader", "Slavers", "Book of Stabbing",
            // Act 3 normal
            "3 Darklings", "Orb Walker", "3 Shapes", "Spire Growth",
            "Transient", "4 Shapes", "Maw", "Jaw Worm Horde",
            "Sphere and 2 Shapes", "Giant Head",
            // Act 3 elites
            "Nemesis", "Reptomancer", "Awakened One",
            // Bosses
            "The Guardian", "Hexaghost", "Slime Boss",
            "Automaton", "Collector", "Champ",
            "Time Eater", "Donu and Deca", "Awakened One"
        };
        return encounters[random.nextInt(encounters.length)];
    }
}
