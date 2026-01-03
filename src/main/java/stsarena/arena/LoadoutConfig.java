package stsarena.arena;

import java.util.*;

/**
 * Configuration and utility functions for loadout generation.
 *
 * This class contains pure functions that don't depend on game state,
 * making them testable without initializing the game.
 */
public class LoadoutConfig {

    // Deck parameters
    public static final int MIN_DECK_SIZE = 10;
    public static final int MAX_DECK_SIZE = 35;

    // Relic parameters (reduced from 5-10 to 2-5)
    public static final int MIN_RELICS = 2;
    public static final int MAX_RELICS = 5;

    // Upgrade and special relic chances
    public static final double UPGRADE_CHANCE = 0.4;
    public static final double PRISMATIC_SHARD_CHANCE = 0.05; // Reduced

    /**
     * All available player classes (as strings for LoadoutBuilder).
     */
    public static final String[] PLAYER_CLASS_NAMES = {
        "IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"
    };

    // Lazy-loaded to avoid class loading issues when running standalone tests
    private static com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass[] playerClassesCache = null;

    /**
     * All available player classes (for game integration).
     * Lazy-loaded to avoid requiring game classes for standalone testing.
     */
    public static com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass[] getPlayerClasses() {
        if (playerClassesCache == null) {
            playerClassesCache = new com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass[] {
                com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.IRONCLAD,
                com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.THE_SILENT,
                com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.DEFECT,
                com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass.WATCHER
            };
        }
        return playerClassesCache;
    }

    // ========== STARTER DECKS ==========

    public static List<String> getStarterDeck(String playerClass) {
        List<String> deck = new ArrayList<>();
        switch (playerClass) {
            case "IRONCLAD":
                for (int i = 0; i < 5; i++) deck.add("Strike_R");
                for (int i = 0; i < 4; i++) deck.add("Defend_R");
                deck.add("Bash");
                break;
            case "THE_SILENT":
                for (int i = 0; i < 5; i++) deck.add("Strike_G");
                for (int i = 0; i < 5; i++) deck.add("Defend_G");
                deck.add("Survivor");
                deck.add("Neutralize");
                break;
            case "DEFECT":
                for (int i = 0; i < 4; i++) deck.add("Strike_B");
                for (int i = 0; i < 4; i++) deck.add("Defend_B");
                deck.add("Zap");
                deck.add("Dualcast");
                break;
            case "WATCHER":
                for (int i = 0; i < 4; i++) deck.add("Strike_P");
                for (int i = 0; i < 4; i++) deck.add("Defend_P");
                deck.add("Eruption");
                deck.add("Vigilance");
                break;
        }
        return deck;
    }

    // ========== BASE STATS ==========

    public static int getBaseMaxHp(String playerClass) {
        switch (playerClass) {
            case "IRONCLAD": return 80;
            case "THE_SILENT": return 70;
            case "DEFECT": return 75;
            case "WATCHER": return 72;
            default: return 75;
        }
    }

    // For backwards compatibility with AbstractPlayer.PlayerClass
    public static int getBaseMaxHp(com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass playerClass) {
        return getBaseMaxHp(playerClass.name());
    }

    public static String getStarterRelicId(String playerClass) {
        switch (playerClass) {
            case "IRONCLAD": return "Burning Blood";
            case "THE_SILENT": return "Ring of the Snake";
            case "DEFECT": return "Cracked Core";
            case "WATCHER": return "PureWater";
            default: return null;
        }
    }

    public static String getStarterRelicId(com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass playerClass) {
        return getStarterRelicId(playerClass.name());
    }

    // ========== CARD COLOR MAPPING ==========

    public static com.megacrit.cardcrawl.cards.AbstractCard.CardColor getCardColor(
            com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass playerClass) {
        switch (playerClass) {
            case IRONCLAD:
                return com.megacrit.cardcrawl.cards.AbstractCard.CardColor.RED;
            case THE_SILENT:
                return com.megacrit.cardcrawl.cards.AbstractCard.CardColor.GREEN;
            case DEFECT:
                return com.megacrit.cardcrawl.cards.AbstractCard.CardColor.BLUE;
            case WATCHER:
                return com.megacrit.cardcrawl.cards.AbstractCard.CardColor.PURPLE;
            default:
                return com.megacrit.cardcrawl.cards.AbstractCard.CardColor.RED;
        }
    }

    // ========== USELESS RELICS (for arena combat) ==========

    public static final Set<String> USELESS_RELICS = new HashSet<>(Arrays.asList(
        // Shop-only / gold-related (useless in arena)
        "Courier", "Membership Card", "Smiling Mask", "The Courier",
        "Ceramic Fish", "Golden Idol", "Bloody Idol", "Old Coin",
        "Maw Bank", "Ectoplasm",

        // Map/event only
        "JuzuBracelet", "Ssserpent Head", "Nloth's Gift", "Matryoshka",
        "Tiny Chest", "Shovel", "WingedGreaves",

        // Rest site only
        "Regal Pillow", "Dream Catcher", "Eternal Feather", "Peace Pipe",
        "Coffee Dripper",

        // Run progression / between-combat
        "Prayer Wheel", "Question Card", "Pantograph", "Black Star",
        "Calling Bell", "Empty Cage", "Pandora's Box", "Astrolabe",
        "Cauldron", "Orrery", "Prismatic Shard", "Singing Bowl",
        "Tiny House", "Dollys Mirror", "Orrery", "War Paint",
        "Whetstone",

        // Boss relics that replace starter
        "Black Blood", "Ring of the Serpent", "FrozenCore", "HolyWater",

        // Problematic for arena
        "Circlet", "Red Circlet", "NeowsBlessing", "Spirit Poop",
        "Necronomicon", "Dead Branch", "NlothsMask", "Molten Egg 2",
        "Toxic Egg 2", "Frozen Egg 2", "Unceasing Top",

        // Require specific conditions
        "Blue Candle", "Medical Kit", "Strange Spoon", "White Beast Statue",

        // Time-based (arena is one fight)
        "Sundial", "Frozen Eye", "Juzu Bracelet"
    ));

    // ========== CLASS-SPECIFIC RELICS ==========

    private static final Map<String, String> RELIC_CLASS = new HashMap<>();
    static {
        // Ironclad
        RELIC_CLASS.put("Burning Blood", "IRONCLAD");
        RELIC_CLASS.put("Black Blood", "IRONCLAD");
        RELIC_CLASS.put("Red Skull", "IRONCLAD");
        RELIC_CLASS.put("Paper Phrog", "IRONCLAD");
        RELIC_CLASS.put("Self Forming Clay", "IRONCLAD");
        RELIC_CLASS.put("Champion Belt", "IRONCLAD");
        RELIC_CLASS.put("Charon's Ashes", "IRONCLAD");
        RELIC_CLASS.put("Magic Flower", "IRONCLAD");
        RELIC_CLASS.put("Brimstone", "IRONCLAD");
        RELIC_CLASS.put("Runic Cube", "IRONCLAD");

        // Silent
        RELIC_CLASS.put("Ring of the Snake", "THE_SILENT");
        RELIC_CLASS.put("Ring of the Serpent", "THE_SILENT");
        RELIC_CLASS.put("Snake Skull", "THE_SILENT");
        RELIC_CLASS.put("The Specimen", "THE_SILENT");
        RELIC_CLASS.put("Tingsha", "THE_SILENT");
        RELIC_CLASS.put("Tough Bandages", "THE_SILENT");
        RELIC_CLASS.put("Hovering Kite", "THE_SILENT");
        RELIC_CLASS.put("WristBlade", "THE_SILENT");
        RELIC_CLASS.put("Ninja Scroll", "THE_SILENT");

        // Defect
        RELIC_CLASS.put("Cracked Core", "DEFECT");
        RELIC_CLASS.put("FrozenCore", "DEFECT");
        RELIC_CLASS.put("DataDisk", "DEFECT");
        RELIC_CLASS.put("Symbiotic Virus", "DEFECT");
        RELIC_CLASS.put("Nuclear Battery", "DEFECT");
        RELIC_CLASS.put("Runic Capacitor", "DEFECT");
        RELIC_CLASS.put("Emotion Chip", "DEFECT");
        RELIC_CLASS.put("GoldPlatedCables", "DEFECT");
        RELIC_CLASS.put("Inserter", "DEFECT");

        // Watcher
        RELIC_CLASS.put("PureWater", "WATCHER");
        RELIC_CLASS.put("HolyWater", "WATCHER");
        RELIC_CLASS.put("Violet Lotus", "WATCHER");
        RELIC_CLASS.put("Teardrop Locket", "WATCHER");
        RELIC_CLASS.put("Damaru", "WATCHER");
        RELIC_CLASS.put("Duality", "WATCHER");
        RELIC_CLASS.put("Cloak Clasp", "WATCHER");
        RELIC_CLASS.put("Golden Eye", "WATCHER");
    }

    public static String getRelicClass(String relicId) {
        return RELIC_CLASS.get(relicId);
    }

    // ========== GOOD RELICS FOR ARENA ==========

    public static final List<String> GOOD_COMMON_RELICS = Arrays.asList(
        "Anchor", "Art of War", "Bag of Marbles", "Blood Vial",
        "Bronze Scales", "Centennial Puzzle", "Lantern", "Oddly Smooth Stone",
        "Pen Nib", "Vajra", "War Paint", "Whetstone", "Orichalcum",
        "Bag of Preparation", "Red Mask", "Happy Flower", "Boot"
    );

    public static final List<String> GOOD_UNCOMMON_RELICS = Arrays.asList(
        "Blue Candle", "Bottled Flame", "Bottled Lightning", "Bottled Tornado",
        "Darkstone Periapt", "Eternal Feather", "Frozen Egg", "Horn Cleat",
        "Ink Bottle", "Kunai", "Letter Opener", "Meat on the Bone",
        "Mercury Hourglass", "Molten Egg", "Mummified Hand", "Ornamental Fan",
        "Paper Krane", "Pear", "Shuriken", "Singing Bowl",
        "Strike Dummy", "Sundial", "Toxic Egg", "White Beast Statue",
        "Gambling Chip", "Gremlin Horn", "The Courier", "Ninja Scroll",
        "Ice Cream", "Cables", "Symbiotic Virus", "Nunchaku",
        "Thread and Needle", "Torii"
    );

    public static final List<String> GOOD_RARE_RELICS = Arrays.asList(
        "Bird Faced Urn", "Calipers", "Captain's Wheel", "Champion Belt",
        "Charon's Ashes", "Du-Vu Doll", "Emotion Chip", "Fossilized Helix",
        "Gambling Chip", "Ginger", "Girya", "Ice Cream", "Incense Burner",
        "Lizard Tail", "Mango", "Old Coin", "Orange Pellets", "Pocketwatch",
        "Self Forming Clay", "Stone Calendar", "The Specimen", "Thread and Needle",
        "Tingsha", "Torii", "Tough Bandages", "Toy Ornithopter", "Tungsten Rod",
        "Turnip", "Unceasing Top", "WristBlade", "Shovel", "Gremlin Horn"
    );

    public static List<String> getAvailableRelics(String playerClass) {
        List<String> relics = new ArrayList<>();

        // Add common relics
        for (String relic : GOOD_COMMON_RELICS) {
            if (!USELESS_RELICS.contains(relic)) {
                String relicClass = getRelicClass(relic);
                if (relicClass == null || relicClass.equals(playerClass)) {
                    relics.add(relic);
                }
            }
        }

        // Add uncommon relics
        for (String relic : GOOD_UNCOMMON_RELICS) {
            if (!USELESS_RELICS.contains(relic)) {
                String relicClass = getRelicClass(relic);
                if (relicClass == null || relicClass.equals(playerClass)) {
                    relics.add(relic);
                }
            }
        }

        // Add rare relics (less common)
        for (String relic : GOOD_RARE_RELICS) {
            if (!USELESS_RELICS.contains(relic)) {
                String relicClass = getRelicClass(relic);
                if (relicClass == null || relicClass.equals(playerClass)) {
                    relics.add(relic);
                }
            }
        }

        return relics;
    }

    // ========== CARD SYNERGY TRACKING ==========

    public static final Set<String> ORB_GENERATORS = new HashSet<>(Arrays.asList(
        "Zap", "Dualcast", "Ball Lightning", "Compile Driver", "Cold Snap",
        "Coolheaded", "Glacier", "Capacitor", "Consume",
        "Darkness", "Doom and Gloom", "Electrodynamics", "Fusion",
        "Chaos", "Rainbow", "Recursion", "Loop", "Multi-Cast",
        "Tempest", "Thunder Strike", "White Noise"
    ));

    // Frost orb generators specifically (for Blizzard synergy)
    public static final Set<String> FROST_ORB_GENERATORS = new HashSet<>(Arrays.asList(
        "Cold Snap", "Coolheaded", "Glacier", "Chill"
    ));

    public static final Set<String> SHIV_GENERATORS = new HashSet<>(Arrays.asList(
        "Blade Dance", "Cloak and Dagger", "Infinite Blades", "Storm of Steel",
        "Accuracy", "After Image"
    ));

    public static final Set<String> POISON_CARDS = new HashSet<>(Arrays.asList(
        "Deadly Poison", "Poisoned Stab", "Noxious Fumes", "Crippling Cloud",
        "Envenom", "Corpse Explosion", "Catalyst", "Bouncing Flask",
        "Bane", "Flechettes", "Alchemize"
    ));

    public static final Set<String> BLOCK_CARDS = new HashSet<>(Arrays.asList(
        "Defend_R", "Defend_G", "Defend_B", "Defend_P",
        "Shrug It Off", "Flame Barrier", "Impervious", "Entrench", "Barricade",
        "Footwork", "Blur", "Cloak And Dagger", "Dodge and Roll",
        "Leg Sweep", "Backflip", "Escape Plan", "After Image",
        "Glacier", "Reinforced Body", "Equilibrium", "Genetic Algorithm",
        // Watcher cards (CamelCase IDs)
        "Vigilance", "LikeWater", "EmptyBody", "Protect", "WaveOfTheHand",
        "TalkToTheHand", "MentalFortress", "SpiritShield"
    ));

    public static final Set<String> STRENGTH_GENERATORS = new HashSet<>(Arrays.asList(
        "Flex", "Inflame", "Spot Weakness", "Demon Form", "Limit Break",
        "Rupture"
    ));

    public static final Set<String> FOCUS_GENERATORS = new HashSet<>(Arrays.asList(
        "Defragment", "Biased Cognition", "Consume", "Capacitor", "Core Surge"
    ));

    public static final Set<String> MANTRA_GENERATORS = new HashSet<>(Arrays.asList(
        "Prostrate", "Worship", "Devotion", "Blasphemy", "Damaru"
    ));

    public static final Set<String> RETAIN_CARDS = new HashSet<>(Arrays.asList(
        "Establishment", "Meditate", "Protect", "WindmillStrike",
        "SandsOfTime", "Perseverance", "SashWhip"
    ));

    // Cards that need synergies
    private static final Map<String, LoadoutBuilder.CardSynergy> CARD_SYNERGIES = new HashMap<>();
    static {
        // Defect orb synergy cards
        CARD_SYNERGIES.put("Consume", LoadoutBuilder.CardSynergy.REQUIRES_ORBS);
        CARD_SYNERGIES.put("Fission", LoadoutBuilder.CardSynergy.REQUIRES_ORBS);
        CARD_SYNERGIES.put("Recursion", LoadoutBuilder.CardSynergy.REQUIRES_ORBS);
        CARD_SYNERGIES.put("Aggregate", LoadoutBuilder.CardSynergy.REQUIRES_ORBS);
        CARD_SYNERGIES.put("All For One", LoadoutBuilder.CardSynergy.REQUIRES_ORBS);

        // Silent shiv synergy
        CARD_SYNERGIES.put("Accuracy", LoadoutBuilder.CardSynergy.REQUIRES_SHIVS);
        CARD_SYNERGIES.put("Finisher", LoadoutBuilder.CardSynergy.REQUIRES_SHIVS);

        // Poison synergy
        CARD_SYNERGIES.put("Catalyst", LoadoutBuilder.CardSynergy.REQUIRES_POISON);
        CARD_SYNERGIES.put("Bane", LoadoutBuilder.CardSynergy.REQUIRES_POISON);
        CARD_SYNERGIES.put("Flechettes", LoadoutBuilder.CardSynergy.REQUIRES_POISON);

        // Strength synergy
        CARD_SYNERGIES.put("Heavy Blade", LoadoutBuilder.CardSynergy.REQUIRES_STRENGTH);
        CARD_SYNERGIES.put("Sword Boomerang", LoadoutBuilder.CardSynergy.REQUIRES_STRENGTH);

        // Orb synergy
        // Note: Hyperbeam removes all focus - it's anti-synergy with focus, not a requirement
        // Blizzard deals damage based on Frost channeled, so specifically needs frost orb generation
        CARD_SYNERGIES.put("Blizzard", LoadoutBuilder.CardSynergy.REQUIRES_FROST_ORBS);

        // Mantra synergy - cards that specifically need mantra generation
        // Note: Rushdown (Adaptation) triggers on Wrath entry, not mantra - no requirement
        // Note: Blasphemy sets you to Divinity directly - no requirement

        // Retain synergy (Watcher CamelCase IDs)
        CARD_SYNERGIES.put("WindmillStrike", LoadoutBuilder.CardSynergy.REQUIRES_RETAIN);
        CARD_SYNERGIES.put("SandsOfTime", LoadoutBuilder.CardSynergy.REQUIRES_RETAIN);
    }

    public static LoadoutBuilder.CardSynergy getCardSynergy(String cardId) {
        return CARD_SYNERGIES.get(cardId);
    }

    // ========== CARD PRIORITY (lower = better) ==========

    private static final Map<String, Integer> IRONCLAD_PRIORITY = new HashMap<>();
    private static final Map<String, Integer> SILENT_PRIORITY = new HashMap<>();
    private static final Map<String, Integer> DEFECT_PRIORITY = new HashMap<>();
    private static final Map<String, Integer> WATCHER_PRIORITY = new HashMap<>();

    static {
        // Ironclad priorities
        IRONCLAD_PRIORITY.put("Demon Form", 10);
        IRONCLAD_PRIORITY.put("Barricade", 15);
        IRONCLAD_PRIORITY.put("Impervious", 20);
        IRONCLAD_PRIORITY.put("Offering", 25);
        IRONCLAD_PRIORITY.put("Reaper", 30);
        IRONCLAD_PRIORITY.put("Flame Barrier", 35);
        IRONCLAD_PRIORITY.put("Shrug It Off", 40);
        IRONCLAD_PRIORITY.put("Inflame", 45);
        IRONCLAD_PRIORITY.put("Battle Trance", 50);
        IRONCLAD_PRIORITY.put("Whirlwind", 55);
        IRONCLAD_PRIORITY.put("Immolate", 60);
        IRONCLAD_PRIORITY.put("Feed", 65);
        IRONCLAD_PRIORITY.put("Pommel Strike", 70);
        IRONCLAD_PRIORITY.put("Carnage", 75);
        IRONCLAD_PRIORITY.put("Uppercut", 80);
        IRONCLAD_PRIORITY.put("Metallicize", 85);

        // Silent priorities
        SILENT_PRIORITY.put("Footwork", 10);
        SILENT_PRIORITY.put("After Image", 15);
        SILENT_PRIORITY.put("Noxious Fumes", 20);
        SILENT_PRIORITY.put("Adrenaline", 25);
        SILENT_PRIORITY.put("Wraith Form", 30);
        SILENT_PRIORITY.put("Malaise", 35);
        SILENT_PRIORITY.put("Leg Sweep", 40);
        SILENT_PRIORITY.put("Burst", 45);
        SILENT_PRIORITY.put("Well-Laid Plans", 50);
        SILENT_PRIORITY.put("Backflip", 55);
        SILENT_PRIORITY.put("Piercing Wail", 60);
        SILENT_PRIORITY.put("Blade Dance", 65);
        SILENT_PRIORITY.put("Acrobatics", 70);
        SILENT_PRIORITY.put("Dash", 75);
        SILENT_PRIORITY.put("Predator", 80);
        SILENT_PRIORITY.put("Deadly Poison", 85);

        // Defect priorities
        DEFECT_PRIORITY.put("Echo Form", 10);
        DEFECT_PRIORITY.put("Electrodynamics", 15);
        DEFECT_PRIORITY.put("Defragment", 20);
        DEFECT_PRIORITY.put("Biased Cognition", 25);
        DEFECT_PRIORITY.put("Glacier", 30);
        DEFECT_PRIORITY.put("Buffer", 35);
        DEFECT_PRIORITY.put("Machine Learning", 40);
        DEFECT_PRIORITY.put("Core Surge", 45);
        DEFECT_PRIORITY.put("Coolheaded", 50);
        DEFECT_PRIORITY.put("Compile Driver", 55);
        DEFECT_PRIORITY.put("Seek", 60);
        DEFECT_PRIORITY.put("Genetic Algorithm", 65);
        DEFECT_PRIORITY.put("Ball Lightning", 70);
        DEFECT_PRIORITY.put("Cold Snap", 75);
        DEFECT_PRIORITY.put("Doom and Gloom", 80);

        // Watcher priorities (uses CamelCase IDs)
        WATCHER_PRIORITY.put("Vault", 10);
        WATCHER_PRIORITY.put("Scrawl", 15);
        WATCHER_PRIORITY.put("LessonLearned", 20);
        WATCHER_PRIORITY.put("Ragnarok", 25);
        WATCHER_PRIORITY.put("Omniscience", 30);
        WATCHER_PRIORITY.put("TalkToTheHand", 35);
        WATCHER_PRIORITY.put("Tantrum", 40);
        WATCHER_PRIORITY.put("EmptyFist", 45);
        WATCHER_PRIORITY.put("Wallop", 50);
        WATCHER_PRIORITY.put("Crescendo", 55);
        WATCHER_PRIORITY.put("FlurryOfBlows", 60);
        WATCHER_PRIORITY.put("Eruption", 65);
        WATCHER_PRIORITY.put("Vigilance", 70);
        WATCHER_PRIORITY.put("Halt", 75);
        WATCHER_PRIORITY.put("EmptyBody", 80);
    }

    public static int getCardPriority(String cardId, String playerClass) {
        Map<String, Integer> priorities;
        switch (playerClass) {
            case "IRONCLAD": priorities = IRONCLAD_PRIORITY; break;
            case "THE_SILENT": priorities = SILENT_PRIORITY; break;
            case "DEFECT": priorities = DEFECT_PRIORITY; break;
            case "WATCHER": priorities = WATCHER_PRIORITY; break;
            default: return 100;
        }
        return priorities.getOrDefault(cardId, 100);
    }

    // ========== MAX COPIES PER CARD ==========

    private static final Map<String, Integer> MAX_COPIES = new HashMap<>();
    static {
        // Powers should be limited
        MAX_COPIES.put("Demon Form", 1);
        MAX_COPIES.put("Barricade", 1);
        MAX_COPIES.put("Echo Form", 1);
        MAX_COPIES.put("Wraith Form", 1);
        MAX_COPIES.put("Noxious Fumes", 2);
        MAX_COPIES.put("After Image", 2);
        MAX_COPIES.put("Footwork", 2);
        MAX_COPIES.put("Defragment", 2);
        MAX_COPIES.put("Electrodynamics", 1);
        MAX_COPIES.put("Biased Cognition", 1);
        MAX_COPIES.put("Buffer", 2);
        MAX_COPIES.put("Metallicize", 2);
        MAX_COPIES.put("Inflame", 2);
        MAX_COPIES.put("LikeWater", 1);
        MAX_COPIES.put("MentalFortress", 2);

        // Limited attacks
        MAX_COPIES.put("Offering", 1);
        MAX_COPIES.put("Feed", 1);
        MAX_COPIES.put("Reaper", 2);
        MAX_COPIES.put("Immolate", 2);
        MAX_COPIES.put("Whirlwind", 2);
        MAX_COPIES.put("Omniscience", 1);
        MAX_COPIES.put("Ragnarok", 2);
        MAX_COPIES.put("Vault", 1);
        MAX_COPIES.put("Scrawl", 1);
    }

    public static int getMaxCopies(String cardId) {
        return MAX_COPIES.getOrDefault(cardId, 3);
    }

    // ========== CARD TYPE MAPPING ==========

    private static final Map<String, String> CARD_TYPES = new HashMap<>();
    static {
        // Powers
        for (String card : Arrays.asList(
            "Demon Form", "Barricade", "Dark Embrace", "Evolve", "Feel No Pain",
            "Fire Breathing", "Juggernaut", "Metallicize", "Rupture", "Combust",
            "Brutality", "Corruption", "Berserk", "Inflame",
            "Noxious Fumes", "Footwork", "A Thousand Cuts", "After Image",
            "Accuracy", "Infinite Blades", "Envenom", "Tools of the Trade",
            "Wraith Form", "Caltrops", "Well-Laid Plans", "Burst",
            "Echo Form", "Electrodynamics", "Defragment", "Biased Cognition",
            "Buffer", "Creative AI", "Hello World", "Machine Learning",
            "Loop", "Static Discharge", "Storm", "Capacitor", "Heatsinks",
            // Watcher powers (CamelCase IDs)
            "LikeWater", "MentalFortress", "Establishment", "Wireheading",  // Wireheading = Foresight
            "Adaptation", "Devotion", "BattleHymn", "DevaForm", "DeusExMachina",  // Adaptation = Rushdown
            "MasterReality", "Study", "Omega", "Nirvana"
        )) {
            CARD_TYPES.put(card, "POWER");
        }

        // Skills (common ones)
        for (String card : Arrays.asList(
            "Defend_R", "Defend_G", "Defend_B", "Defend_P",
            "Shrug It Off", "Flame Barrier", "Impervious", "Entrench", "Rage",
            "True Grit", "Power Through", "Burning Pact", "Disarm", "Sentinel",
            "Intimidate", "Double Tap", "Dual Wield", "Second Wind", "Seeing Red",
            "Ghostly Armor", "Bloodletting", "Warcry", "Havoc", "Exhume",
            "Backflip", "Blur", "Prepared", "Acrobatics", "Cloak And Dagger",
            "Dodge and Roll", "Leg Sweep", "Escape Plan", "Outmaneuver",
            "Concentrate", "Malaise", "Piercing Wail", "Setup", "Distraction",
            "Nightmare", "Storm of Steel", "Corpse Explosion", "Adrenaline",
            "Bullet Time", "Dagger Spray", "Calculated Gamble", "Terror",
            "Glacier", "Coolheaded", "Reinforced Body", "Equilibrium", "Hologram",
            "Skim", "Turbo", "White Noise", "Seek", "Reboot", "Reprogram",
            "Self Repair", "BootSequence", "Recursion", "Undo", "Amplify",
            "Stack", "Force Field", "Multi-Cast", "Core Surge",
            // Watcher skills (CamelCase IDs)
            "Vigilance", "Protect", "EmptyBody", "ThirdEye", "Perseverance",
            "Halt", "Evaluate", "Pray", "Prostrate", "Crescendo", "ClearTheMind",  // ClearTheMind = Tranquility
            "Worship", "Collect", "DeceiveReality", "InnerPeace", "Meditate",
            "SashWhip", "WaveOfTheHand", "Sanctity", "SpiritShield",
            "Swivel", "Blasphemy", "Scrawl", "Vault", "Wish", "EmptyMind",
            "Fasting2", "FearNoEvil", "Indignation", "ReachHeaven"
        )) {
            CARD_TYPES.put(card, "SKILL");
        }

        // Everything else is an attack (by default)
    }

    public static String getCardType(String cardId) {
        return CARD_TYPES.getOrDefault(cardId, "ATTACK");
    }

    // ========== CLASS CARDS ==========

    public static List<String> getClassCards(String playerClass) {
        switch (playerClass) {
            case "IRONCLAD": return new ArrayList<>(IRONCLAD_CARDS);
            case "THE_SILENT": return new ArrayList<>(SILENT_CARDS);
            case "DEFECT": return new ArrayList<>(DEFECT_CARDS);
            case "WATCHER": return new ArrayList<>(WATCHER_CARDS);
            default: return new ArrayList<>();
        }
    }

    public static List<String> getRareCards(String playerClass) {
        switch (playerClass) {
            case "IRONCLAD": return new ArrayList<>(IRONCLAD_RARES);
            case "THE_SILENT": return new ArrayList<>(SILENT_RARES);
            case "DEFECT": return new ArrayList<>(DEFECT_RARES);
            case "WATCHER": return new ArrayList<>(WATCHER_RARES);
            default: return new ArrayList<>();
        }
    }

    private static final List<String> IRONCLAD_CARDS = Arrays.asList(
        // Commons
        "Anger", "Armaments", "Body Slam", "Clash", "Cleave", "Clothesline",
        "Flex", "Havoc", "Headbutt", "Heavy Blade", "Iron Wave", "Perfected Strike",
        "Pommel Strike", "Shrug It Off", "Sword Boomerang", "Thunderclap", "True Grit", "Twin Strike",
        "Warcry", "Wild Strike",
        // Uncommons
        "Battle Trance", "Blood for Blood", "Bloodletting", "Burning Pact", "Carnage",
        "Combust", "Disarm", "Dropkick", "Dual Wield", "Entrench", "Evolve",
        "Feel No Pain", "Fire Breathing", "Flame Barrier", "Ghostly Armor",
        "Hemokinesis", "Infernal Blade", "Inflame", "Intimidate", "Metallicize",
        "Power Through", "Pummel", "Rage", "Rampage", "Reckless Charge",
        "Rupture", "Searing Blow", "Second Wind", "Seeing Red", "Sentinel",
        "Sever Soul", "Shockwave", "Spot Weakness", "Uppercut", "Whirlwind"
    );

    private static final List<String> IRONCLAD_RARES = Arrays.asList(
        "Barricade", "Berserk", "Bludgeon", "Brutality", "Corruption",
        "Demon Form", "Double Tap", "Exhume", "Feed", "Fiend Fire",
        "Immolate", "Impervious", "Juggernaut", "Limit Break", "Offering", "Reaper"
    );

    private static final List<String> SILENT_CARDS = Arrays.asList(
        // Commons
        "Acrobatics", "Backflip", "Bane", "Blade Dance", "Cloak and Dagger",
        "Dagger Spray", "Dagger Throw", "Deadly Poison", "Deflect", "Dodge and Roll",
        "Flying Knee", "Outmaneuver", "Piercing Wail", "Poisoned Stab", "Prepared",
        "Quick Slash", "Slice", "Sneaky Strike", "Sucker Punch",
        // Uncommons
        "Accuracy", "All-Out Attack", "Backstab", "Blur", "Bouncing Flask",
        "Calculated Gamble", "Caltrops", "Catalyst", "Choke", "Concentrate",
        "Crippling Cloud", "Dash", "Distraction", "Endless Agony", "Escape Plan",
        "Eviscerate", "Expertise", "Finisher", "Flechettes", "Footwork",
        "Heel Hook", "Infinite Blades", "Leg Sweep", "Masterful Stab", "Noxious Fumes",
        "Predator", "Reflex", "Riddle with Holes", "Setup", "Skewer",
        "Tactician", "Terror", "Well-Laid Plans"
    );

    private static final List<String> SILENT_RARES = Arrays.asList(
        "A Thousand Cuts", "Adrenaline", "After Image", "Alchemize", "Bullet Time",
        "Burst", "Corpse Explosion", "Die Die Die", "Doppelganger", "Envenom",
        "Glass Knife", "Grand Finale", "Malaise", "Nightmare", "Phantasmal Killer",
        "Storm of Steel", "Tools of the Trade", "Unload", "Wraith Form"
    );

    private static final List<String> DEFECT_CARDS = Arrays.asList(
        // Commons
        "Ball Lightning", "Barrage", "Beam Cell", "Charge Battery", "Claw",
        "Cold Snap", "Compile Driver", "Coolheaded", "Go for the Eyes", "Hologram",
        "Leap", "Rebound", "Recursion", "Stack", "Steam", "Streamline", "Sweeping Beam", "Turbo",
        // Uncommons
        "Aggregate", "Auto-Shields", "Blizzard", "Boot Sequence", "Bullseye",
        "Capacitor", "Chaos", "Chill", "Consume", "Darkness", "Defragment",
        "Doom and Gloom", "Double Energy", "Equilibrium", "FTL", "Force Field",
        "Fusion", "Genetic Algorithm", "Glacier", "Heatsinks", "Hello World",
        "Loop", "Melter", "Overclock", "Recycle", "Reinforced Body", "Reprogram",
        "Rip and Tear", "Scrape", "Self Repair", "Skim", "Static Discharge",
        "Storm", "Sunder", "Tempest", "White Noise"
    );

    private static final List<String> DEFECT_RARES = Arrays.asList(
        "All For One", "Amplify", "Biased Cognition", "Buffer", "Core Surge",
        "Creative AI", "Echo Form", "Electrodynamics", "Fission", "Hyperbeam",
        "Machine Learning", "Meteor Strike", "Multi-Cast", "Rainbow", "Reboot",
        "Seek", "Thunder Strike"
    );

    private static final List<String> WATCHER_CARDS = Arrays.asList(
        // Commons (Watcher uses CamelCase IDs, some have different internal names)
        "BowlingBash", "Consecrate", "Crescendo", "CrushJoints", "CutThroughFate",
        "EmptyBody", "EmptyFist", "Evaluate", "FlurryOfBlows", "FlyingSleeves",
        "FollowUp", "Halt", "JustLucky", "PathToVictory", "Prostrate",  // PathToVictory = Pressure Points
        "Protect", "SashWhip", "ThirdEye", "ClearTheMind", "Vigilance",  // ClearTheMind = Tranquility
        // Uncommons
        "BattleHymn", "CarveReality", "Collect", "Conclude", "DeceiveReality",
        "EmptyMind", "Fasting2", "FearNoEvil", "Wireheading", "Indignation",  // Wireheading = Foresight
        "InnerPeace", "LikeWater", "Meditate", "MentalFortress", "Nirvana",
        "Perseverance", "Pray", "ReachHeaven", "Adaptation", "Sanctity",  // Adaptation = Rushdown
        "SandsOfTime", "SignatureMove", "Vengeance", "Study", "Swivel",  // Vengeance = Simmering Fury
        "TalkToTheHand", "Tantrum", "Wallop", "WaveOfTheHand", "WheelKick",
        "WindmillStrike", "Worship", "WreathOfFlame"
    );

    private static final List<String> WATCHER_RARES = Arrays.asList(
        "Alpha", "Blasphemy", "Brilliance", "ConjureBlade", "DeusExMachina",
        "DevaForm", "Establishment", "Judgement", "LessonLearned", "MasterReality",
        "Omniscience", "Ragnarok", "Scrawl", "SpiritShield",  // Omega removed (it's a temp card from Alpha)
        "Vault", "Wish"
    );

    // ========== COLORLESS CARDS ==========

    public static List<String> getColorlessCards() {
        return new ArrayList<>(COLORLESS_CARDS);
    }

    private static final List<String> COLORLESS_CARDS = Arrays.asList(
        // Uncommon
        "Bandage Up", "Blind", "Dark Shackles", "Deep Breath", "Discovery",
        "Dramatic Entrance", "Enlightenment", "Finesse", "Flash of Steel",
        "Forethought", "Good Instincts", "Impatience", "Jack Of All Trades",
        "Madness", "Mind Blast", "Panacea", "Panic Button", "Purity",
        "Swift Strike", "Trip",
        // Rare
        "Apotheosis", "Chrysalis", "Hand of Greed", "Magnetism", "Master of Strategy",
        "Mayhem", "Metamorphosis", "Panache", "Sadistic Nature", "Secret Technique",
        "Secret Weapon", "The Bomb", "Thinking Ahead", "Transmutation", "Violence"
    );

    // ========== POTIONS ==========

    public static List<String> getAvailablePotions(String playerClass) {
        List<String> potions = new ArrayList<>(COMMON_POTIONS);

        switch (playerClass) {
            case "IRONCLAD":
                potions.addAll(IRONCLAD_POTIONS);
                break;
            case "THE_SILENT":
                potions.addAll(SILENT_POTIONS);
                break;
            case "DEFECT":
                potions.addAll(DEFECT_POTIONS);
                break;
            case "WATCHER":
                potions.addAll(WATCHER_POTIONS);
                break;
        }

        return potions;
    }

    private static final List<String> COMMON_POTIONS = Arrays.asList(
        "Block Potion", "Dexterity Potion", "Energy Potion", "Explosive Potion",
        "Fire Potion", "Strength Potion", "Swift Potion", "Weak Potion",
        "Fear Potion", "Attack Potion", "Skill Potion", "Power Potion",
        "Colorless Potion", "Flex Potion", "Speed Potion", "Regen Potion",
        "Ancient Potion", "Liquid Bronze", "Essence of Steel", "Duplication Potion",
        "Distilled Chaos", "Liquid Memories", "Heart of Iron", "Gambler's Brew",
        "Entropic Brew", "Snecko Oil", "Fairy in a Bottle", "Smoke Bomb",
        "Fruit Juice", "Cultist Potion", "Potion of Capacity", "Essence of Darkness"
    );

    private static final List<String> IRONCLAD_POTIONS = Arrays.asList(
        "BloodPotion", "Elixir"
    );

    private static final List<String> SILENT_POTIONS = Arrays.asList(
        "Cunning Potion", "Ghost In A Jar", "Poison Potion"
    );

    private static final List<String> DEFECT_POTIONS = Arrays.asList(
        "Focus Potion"
    );

    private static final List<String> WATCHER_POTIONS = Arrays.asList(
        "Ambrosia", "Bottled Miracle", "Stance Potion"
    );

    // ========== EXCLUDED CARDS/RELICS ==========

    public static boolean isExcludedRelic(String relicId) {
        return USELESS_RELICS.contains(relicId);
    }

    public static boolean isExcludedCard(String cardId) {
        // Cards that need kills across fights
        if (cardId.contains("Ritual") && cardId.contains("Dagger")) {
            return true;
        }
        // Feed is less useful in arena (no permanent HP)
        // Lesson Learned needs to kill to upgrade - still ok for one fight
        return false;
    }

    // ========== ENCOUNTERS ==========

    /**
     * Encounter category containing a header and list of encounter IDs.
     */
    public static class EncounterCategory {
        public final String header;
        public final String[] encounters;
        public final boolean isElite;
        public final boolean isBoss;

        public EncounterCategory(String header, String[] encounters, boolean isElite, boolean isBoss) {
            this.header = header;
            this.encounters = encounters;
            this.isElite = isElite;
            this.isBoss = isBoss;
        }

        public EncounterCategory(String header, String[] encounters) {
            this(header, encounters, false, false);
        }
    }

    /**
     * All encounter categories organized by act.
     * This is the single source of truth for encounter organization.
     */
    public static final EncounterCategory[] ENCOUNTER_CATEGORIES = {
        // Act 1
        new EncounterCategory("--- Act 1 ---", new String[]{
            "Cultist", "Jaw Worm", "2 Louse", "Small Slimes", "Blue Slaver",
            "Gremlin Gang", "Looter", "Large Slime", "Lots of Slimes",
            "Exordium Thugs", "Exordium Wildlife", "Red Slaver", "3 Louse", "2 Fungi Beasts"
        }),
        new EncounterCategory("Act 1 Elites", new String[]{
            "Gremlin Nob", "Lagavulin", "3 Sentries"
        }, true, false),
        new EncounterCategory("Act 1 Bosses", new String[]{
            "The Guardian", "Hexaghost", "Slime Boss"
        }, false, true),

        // Act 2
        new EncounterCategory("--- Act 2 ---", new String[]{
            "Chosen", "Shell Parasite", "Spheric Guardian", "3 Byrds", "2 Thieves",
            "Chosen and Byrds", "Sentry and Sphere", "Snake Plant", "Snecko",
            "Centurion and Healer", "Cultist and Chosen", "3 Cultists", "Shelled Parasite and Fungi"
        }),
        new EncounterCategory("Act 2 Elites", new String[]{
            "Gremlin Leader", "Slavers", "Book of Stabbing"
        }, true, false),
        new EncounterCategory("Act 2 Bosses", new String[]{
            "Automaton", "Collector", "Champ"
        }, false, true),

        // Act 3
        new EncounterCategory("--- Act 3 ---", new String[]{
            "3 Darklings", "Orb Walker", "3 Shapes", "Spire Growth", "Transient",
            "4 Shapes", "Maw", "Jaw Worm Horde", "Sphere and 2 Shapes", "Writhing Mass"
        }),
        new EncounterCategory("Act 3 Elites", new String[]{
            "Giant Head", "Nemesis", "Reptomancer"
        }, true, false),
        new EncounterCategory("Act 3 Bosses", new String[]{
            "Awakened One", "Time Eater", "Donu and Deca"
        }, false, true),

        // Act 4
        new EncounterCategory("--- Act 4 ---", new String[]{
            "Shield and Spear", "The Heart"
        }, false, true),

        // Event Encounters
        new EncounterCategory("--- Event Encounters ---", new String[]{
            "The Mushroom Lair",     // 3 Fungi Beasts (event version)
            "Masked Bandits",        // Red Mask gang event
            "Colosseum Slavers",     // Colosseum fight 1
            "Colosseum Nobs",        // Colosseum fight 2
            "2 Orb Walkers"          // Mind Bloom event
        })
    };

    /**
     * Set of elite encounter IDs (derived from ENCOUNTER_CATEGORIES).
     * At Ascension 18+, these apply the "burning elite" effect (1 damage per turn).
     */
    private static Set<String> eliteEncountersCache = null;

    private static Set<String> getEliteEncounters() {
        if (eliteEncountersCache == null) {
            eliteEncountersCache = new HashSet<>();
            for (EncounterCategory cat : ENCOUNTER_CATEGORIES) {
                if (cat.isElite) {
                    eliteEncountersCache.addAll(Arrays.asList(cat.encounters));
                }
            }
        }
        return eliteEncountersCache;
    }

    /**
     * Check if an encounter is an elite encounter.
     */
    public static boolean isEliteEncounter(String encounterId) {
        return getEliteEncounters().contains(encounterId);
    }

    /**
     * Check if an encounter is a boss encounter.
     */
    public static boolean isBossEncounter(String encounterId) {
        for (EncounterCategory cat : ENCOUNTER_CATEGORIES) {
            if (cat.isBoss) {
                for (String enc : cat.encounters) {
                    if (enc.equals(encounterId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * All encounters as a flat array (derived from ENCOUNTER_CATEGORIES).
     */
    public static final String[] ENCOUNTERS = {
        // Act 1 normal encounters
        "Cultist", "Jaw Worm", "2 Louse", "Small Slimes", "Blue Slaver",
        "Gremlin Gang", "Looter", "Large Slime", "Lots of Slimes",
        "Exordium Thugs", "Exordium Wildlife", "Red Slaver", "3 Louse", "2 Fungi Beasts",
        // Act 1 elites
        "Gremlin Nob", "Lagavulin", "3 Sentries",
        // Act 1 bosses
        "The Guardian", "Hexaghost", "Slime Boss",
        // Act 2 normal encounters
        "Chosen", "Shell Parasite", "Spheric Guardian", "3 Byrds", "2 Thieves",
        "Chosen and Byrds", "Sentry and Sphere", "Snake Plant", "Snecko",
        "Centurion and Healer", "Cultist and Chosen", "3 Cultists", "Shelled Parasite and Fungi",
        // Act 2 elites
        "Gremlin Leader", "Slavers", "Book of Stabbing",
        // Act 2 bosses
        "Automaton", "Collector", "Champ",
        // Act 3 normal encounters
        "3 Darklings", "Orb Walker", "3 Shapes", "Spire Growth", "Transient",
        "4 Shapes", "Maw", "Jaw Worm Horde", "Sphere and 2 Shapes", "Writhing Mass",
        // Act 3 elites
        "Giant Head", "Nemesis", "Reptomancer",
        // Act 3 bosses
        "Awakened One", "Time Eater", "Donu and Deca",
        // Act 4
        "Shield and Spear", "The Heart"
    };

    // Legacy compatibility methods for code using old constants
    public static final int POWER_COUNT_MIN = 1;
    public static final int POWER_COUNT_MAX = 4;
    public static final double ATTACK_RATIO_MIN = 0.4;
    public static final double ATTACK_RATIO_MAX = 0.5;

    public static int calculateAttackCount(int deckSize, double randomValue) {
        double ratio = ATTACK_RATIO_MIN + randomValue * (ATTACK_RATIO_MAX - ATTACK_RATIO_MIN);
        return (int) (deckSize * ratio);
    }

    public static int calculatePowerCount(int availablePowers, int randomValue) {
        int maxPowers = Math.min(availablePowers, POWER_COUNT_MAX);
        return POWER_COUNT_MIN + (randomValue % (maxPowers - POWER_COUNT_MIN + 1));
    }
}
