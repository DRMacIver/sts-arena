package stsarena.arena;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;

/**
 * Configuration and utility functions for loadout generation.
 *
 * This class contains pure functions that don't depend on game state,
 * making them testable without initializing the game.
 */
public class LoadoutConfig {

    // Deck parameters
    public static final int MIN_DECK_SIZE = 15;
    public static final int MAX_DECK_SIZE = 25;

    // Relic parameters
    public static final int MIN_RELICS = 5;
    public static final int MAX_RELICS = 10;

    // Upgrade and special relic chances
    public static final double UPGRADE_CHANCE = 0.4;
    public static final double PRISMATIC_SHARD_CHANCE = 0.15;

    // Deck composition ratios
    public static final double ATTACK_RATIO_MIN = 0.4;
    public static final double ATTACK_RATIO_MAX = 0.5;
    public static final int POWER_COUNT_MIN = 1;
    public static final int POWER_COUNT_MAX = 4;

    /**
     * All available player classes.
     */
    public static final AbstractPlayer.PlayerClass[] PLAYER_CLASSES = {
        AbstractPlayer.PlayerClass.IRONCLAD,
        AbstractPlayer.PlayerClass.THE_SILENT,
        AbstractPlayer.PlayerClass.DEFECT,
        AbstractPlayer.PlayerClass.WATCHER
    };

    /**
     * Get the card color for a player class.
     * Pure function - no game state needed.
     */
    public static AbstractCard.CardColor getCardColor(AbstractPlayer.PlayerClass playerClass) {
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

    /**
     * Get the base max HP for a player class.
     * Pure function - no game state needed.
     */
    public static int getBaseMaxHp(AbstractPlayer.PlayerClass playerClass) {
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
     * Get the starter relic ID for a player class.
     * Pure function - returns ID string, not the actual relic.
     */
    public static String getStarterRelicId(AbstractPlayer.PlayerClass playerClass) {
        switch (playerClass) {
            case IRONCLAD:
                return "Burning Blood";
            case THE_SILENT:
                return "Ring of the Snake";
            case DEFECT:
                return "Cracked Core";
            case WATCHER:
                return "PureWater";
            default:
                return null;
        }
    }

    /**
     * Relics that should be excluded from random selection.
     * Returns true if the relic should be filtered out.
     */
    public static boolean isExcludedRelic(String relicId) {
        // Boss relics that replace starter
        if (relicId.equals("Black Blood") || relicId.equals("Ring of the Serpent") ||
            relicId.equals("FrozenCore") || relicId.equals("HolyWater")) {
            return true;
        }

        // Relics that require specific game state
        if (relicId.equals("Circlet") || relicId.equals("Red Circlet")) {
            return true;
        }

        // Neow's Lament (boss kill effect doesn't apply in arena)
        if (relicId.equals("NeowsBlessing")) {
            return true;
        }

        return false;
    }

    /**
     * Cards that should be excluded from random selection.
     * Returns true if the card should be filtered out.
     */
    public static boolean isExcludedCard(String cardId) {
        // Cards that need kills across fights
        if (cardId.contains("Ritual") && cardId.contains("Dagger")) {
            return true;
        }
        // Lesson Learned needs to kill to upgrade
        if (cardId.equals("Lesson Learned")) {
            return true;
        }
        return false;
    }

    /**
     * Calculate the number of attacks for a deck of a given size.
     * Pure function for testing deck composition logic.
     */
    public static int calculateAttackCount(int deckSize, double randomValue) {
        double ratio = ATTACK_RATIO_MIN + randomValue * (ATTACK_RATIO_MAX - ATTACK_RATIO_MIN);
        return (int) (deckSize * ratio);
    }

    /**
     * Calculate the number of powers for a deck.
     * Pure function for testing deck composition logic.
     */
    public static int calculatePowerCount(int availablePowers, int randomValue) {
        int maxPowers = Math.min(availablePowers, POWER_COUNT_MAX);
        return POWER_COUNT_MIN + (randomValue % (maxPowers - POWER_COUNT_MIN + 1));
    }

    /**
     * All encounters from the game, organized by act.
     * These are the internal encounter IDs used by the game.
     */
    public static final String[] ENCOUNTERS = {
        // Act 1 normal encounters
        "Cultist",
        "Jaw Worm",
        "2 Louse",
        "Small Slimes",
        "Blue Slaver",
        "Gremlin Gang",
        "Looter",
        "Large Slime",
        "Lots of Slimes",
        "Exordium Thugs",
        "Exordium Wildlife",
        "Red Slaver",
        "3 Louse",
        "2 Fungi Beasts",
        // Act 1 elites
        "Gremlin Nob",
        "Lagavulin",
        "3 Sentries",
        // Act 1 bosses
        "The Guardian",
        "Hexaghost",
        "Slime Boss",
        // Act 2 normal encounters
        "Chosen",
        "Shell Parasite",
        "Spheric Guardian",
        "3 Byrds",
        "2 Thieves",
        "Chosen and Byrds",
        "Sentry and Sphere",
        "Snake Plant",
        "Snecko",
        "Centurion and Healer",
        "Cultist and Chosen",
        "3 Cultists",
        "Shelled Parasite and Fungi",
        // Act 2 elites
        "Gremlin Leader",
        "Slavers",
        "Book of Stabbing",
        // Act 2 bosses
        "Automaton",
        "Collector",
        "Champ",
        // Act 3 normal encounters
        "3 Darklings",
        "Orb Walker",
        "3 Shapes",
        "Spire Growth",
        "Transient",
        "4 Shapes",
        "Maw",
        "Jaw Worm Horde",
        "Sphere and 2 Shapes",
        "Writhing Mass",
        // Act 3 elites
        "Giant Head",
        "Nemesis",
        "Reptomancer",
        // Act 3 bosses
        "Awakened One",
        "Time Eater",
        "Donu and Deca",
        // Act 4
        "Shield and Spear",
        "The Heart"
    };
}
