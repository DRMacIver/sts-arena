package stsarena.arena;

import java.util.*;

/**
 * Builds loadouts using an "edit events" system that simulates a run progression.
 *
 * This class works entirely with IDs (strings) and can be tested without the game running.
 * The RandomLoadoutGenerator wraps this to convert IDs to actual game objects.
 */
public class LoadoutBuilder {

    private final Random random;
    private final String playerClass;

    // Current loadout state
    private final List<CardEntry> deck = new ArrayList<>();
    private final List<String> relics = new ArrayList<>();
    private final List<String> potions = new ArrayList<>();
    private int maxHp;
    private int currentHp;
    private int gold = 99;
    private int potionSlots = 3;
    private int ascension = 0;

    // Tracking for synergies
    private int orbGenerationCount = 0;
    private int shivGenerationCount = 0;
    private int poisonCount = 0;
    private int blockGenerationCount = 0;
    private int strengthGenerationCount = 0;
    private int focusGenerationCount = 0;
    private int mantraCount = 0;
    private int retainCount = 0;

    public static class CardEntry {
        public final String cardId;
        public boolean upgraded;

        public CardEntry(String cardId, boolean upgraded) {
            this.cardId = cardId;
            this.upgraded = upgraded;
        }

        @Override
        public String toString() {
            return upgraded ? cardId + "+" : cardId;
        }
    }

    public static class BuiltLoadout {
        public final String playerClass;
        public final List<CardEntry> deck;
        public final List<String> relics;
        public final List<String> potions;
        public final int maxHp;
        public final int currentHp;
        public final int potionSlots;
        public final int ascension;

        public BuiltLoadout(String playerClass, List<CardEntry> deck, List<String> relics,
                           List<String> potions, int maxHp, int currentHp, int potionSlots, int ascension) {
            this.playerClass = playerClass;
            this.deck = new ArrayList<>(deck);
            this.relics = new ArrayList<>(relics);
            this.potions = new ArrayList<>(potions);
            this.maxHp = maxHp;
            this.currentHp = currentHp;
            this.potionSlots = potionSlots;
            this.ascension = ascension;
        }

        @Override
        public String toString() {
            return String.format("%s: %d cards, %d relics, %d potions, %d/%d HP, A%d",
                playerClass, deck.size(), relics.size(), potions.size(), currentHp, maxHp, ascension);
        }
    }

    public LoadoutBuilder(String playerClass, Random random) {
        this.playerClass = playerClass;
        this.random = random;

        // Initialize with starter relic and deck
        initializeStarter();
    }

    public LoadoutBuilder(String playerClass) {
        this(playerClass, new Random());
    }

    private void initializeStarter() {
        // Set base HP
        maxHp = LoadoutConfig.getBaseMaxHp(playerClass);
        currentHp = maxHp;

        // Add starter relic
        String starterRelic = LoadoutConfig.getStarterRelicId(playerClass);
        if (starterRelic != null) {
            relics.add(starterRelic);
        }

        // Add starter deck
        List<String> starterDeck = LoadoutConfig.getStarterDeck(playerClass);
        for (String cardId : starterDeck) {
            deck.add(new CardEntry(cardId, false));
        }
    }

    /**
     * Apply random edit events to build a loadout.
     */
    public void applyRandomEvents(int numEvents) {
        // First, decide ascension (affects some decisions)
        ascension = random.nextInt(21);
        if (ascension >= 11) {
            potionSlots = 2;
        }

        // Thin the starter deck somewhat
        thinStarterDeck();

        // Apply events
        for (int i = 0; i < numEvents; i++) {
            applyRandomEvent();
        }

        // Final adjustments
        finalizeLoadout();
    }

    private void thinStarterDeck() {
        // Remove a random number of Strikes (0 to all-1)
        int strikeCount = countCards("Strike");
        int strikesToRemove = random.nextInt(strikeCount);
        for (int i = 0; i < strikesToRemove; i++) {
            removeCard("Strike");
        }

        // Remove a random number of Defends (0 to all-1)
        int defendCount = countCards("Defend");
        int defendsToRemove = random.nextInt(defendCount);
        for (int i = 0; i < defendsToRemove; i++) {
            removeCard("Defend");
        }

        // Remove other starter cards with 10% chance each
        List<CardEntry> toRemove = new ArrayList<>();
        for (CardEntry card : deck) {
            if (!card.cardId.contains("Strike") && !card.cardId.contains("Defend")) {
                if (random.nextDouble() < 0.1) {
                    toRemove.add(card);
                }
            }
        }
        deck.removeAll(toRemove);
    }

    private int countCards(String cardIdContains) {
        int count = 0;
        for (CardEntry card : deck) {
            if (card.cardId.contains(cardIdContains)) {
                count++;
            }
        }
        return count;
    }

    private void removeCard(String cardIdContains) {
        for (int i = deck.size() - 1; i >= 0; i--) {
            if (deck.get(i).cardId.contains(cardIdContains)) {
                deck.remove(i);
                return;
            }
        }
    }

    private void applyRandomEvent() {
        // Weight different event types
        double roll = random.nextDouble();

        if (roll < 0.35) {
            // Add a card (most common)
            addRandomCard();
        } else if (roll < 0.50) {
            // Add a relic
            addRandomRelic();
        } else if (roll < 0.60) {
            // Gain a potion
            gainRandomPotion();
        } else if (roll < 0.70) {
            // Remove a card
            removeWorstCard();
        } else if (roll < 0.80) {
            // Upgrade a card
            upgradeRandomCard();
        } else if (roll < 0.88) {
            // Gain max HP
            int hpGain = 5 + random.nextInt(10);
            maxHp += hpGain;
            currentHp += hpGain;
        } else if (roll < 0.95) {
            // Transform a card (remove one, add random)
            if (deck.size() > 5) {
                removeWorstCard();
                addRandomCard();
            }
        } else {
            // Rare event: add a rare card
            addRareCard();
        }
    }

    private void addRandomCard() {
        List<String> available = getAvailableCards();
        if (available.isEmpty()) return;

        // Pick 3 random cards and choose the best one
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < 3 && !available.isEmpty(); i++) {
            int idx = random.nextInt(available.size());
            choices.add(available.remove(idx));
        }

        String best = chooseBestCard(choices);
        if (best != null) {
            addCard(best, random.nextDouble() < 0.3); // 30% chance upgraded
        }
    }

    private void addRareCard() {
        List<String> rares = LoadoutConfig.getRareCards(playerClass);
        if (rares.isEmpty()) return;

        String card = rares.get(random.nextInt(rares.size()));
        if (shouldAddCard(card)) {
            addCard(card, random.nextDouble() < 0.5); // 50% chance upgraded for rares
        }
    }

    private List<String> getAvailableCards() {
        List<String> cards = new ArrayList<>();

        // Add class cards
        cards.addAll(LoadoutConfig.getClassCards(playerClass));

        // Add colorless cards (less common)
        if (random.nextDouble() < 0.2) {
            cards.addAll(LoadoutConfig.getColorlessCards());
        }

        // Filter based on synergies
        cards.removeIf(card -> !shouldAddCard(card));

        return cards;
    }

    private boolean shouldAddCard(String cardId) {
        // Don't add too many copies of the same card
        int copies = 0;
        for (CardEntry entry : deck) {
            if (entry.cardId.equals(cardId)) copies++;
        }
        int maxCopies = LoadoutConfig.getMaxCopies(cardId);
        if (copies >= maxCopies) return false;

        // Synergy checks
        CardSynergy synergy = LoadoutConfig.getCardSynergy(cardId);
        if (synergy != null) {
            switch (synergy) {
                case REQUIRES_ORBS:
                    // Need orb generation before adding orb slot cards
                    if (orbGenerationCount == 0) return false;
                    break;
                case REQUIRES_SHIVS:
                    // Need shiv generation for shiv synergy cards
                    if (shivGenerationCount == 0 && !isShivGenerator(cardId)) return false;
                    break;
                case REQUIRES_POISON:
                    // Need poison for poison synergy
                    if (poisonCount == 0 && !isPoisonCard(cardId)) return false;
                    break;
                case REQUIRES_BLOCK:
                    // Need block generation for block payoffs
                    if (blockGenerationCount < 3) return false;
                    break;
                case REQUIRES_STRENGTH:
                    // Need strength for strength payoffs (e.g., Heavy Blade)
                    if (strengthGenerationCount == 0) return false;
                    break;
                case REQUIRES_FOCUS:
                    // Need focus generation
                    if (focusGenerationCount == 0 && !isFocusGenerator(cardId)) return false;
                    break;
                case REQUIRES_MANTRA:
                    // Need mantra generation for Divinity synergy
                    if (mantraCount == 0 && !isMantraGenerator(cardId)) return false;
                    break;
                case REQUIRES_RETAIN:
                    // Need retain cards
                    if (retainCount == 0 && !isRetainCard(cardId)) return false;
                    break;
            }
        }

        return true;
    }

    private String chooseBestCard(List<String> choices) {
        if (choices.isEmpty()) return null;

        // Score each card based on current deck state
        String best = null;
        int bestScore = Integer.MIN_VALUE;

        for (String cardId : choices) {
            if (!shouldAddCard(cardId)) continue;

            int score = scoreCard(cardId);
            if (score > bestScore) {
                bestScore = score;
                best = cardId;
            }
        }

        return best;
    }

    private int scoreCard(String cardId) {
        int score = LoadoutConfig.getCardPriority(cardId, playerClass);

        // Bonus for synergies
        if (isOrbGenerator(cardId) && orbGenerationCount < 3) score += 20;
        if (isShivGenerator(cardId) && shivGenerationCount < 2) score += 15;
        if (isPoisonCard(cardId) && poisonCount > 0) score += 10;
        if (isBlockCard(cardId) && blockGenerationCount < 5) score += 5;

        // Penalty for too many of a type
        int attackCount = 0, skillCount = 0, powerCount = 0;
        for (CardEntry card : deck) {
            String type = LoadoutConfig.getCardType(card.cardId);
            if ("ATTACK".equals(type)) attackCount++;
            else if ("SKILL".equals(type)) skillCount++;
            else if ("POWER".equals(type)) powerCount++;
        }

        String type = LoadoutConfig.getCardType(cardId);
        if ("ATTACK".equals(type) && attackCount > deck.size() * 0.55) score -= 10;
        if ("SKILL".equals(type) && skillCount > deck.size() * 0.5) score -= 5;
        if ("POWER".equals(type) && powerCount > 5) score -= 15;

        return score;
    }

    private void addCard(String cardId, boolean upgraded) {
        deck.add(new CardEntry(cardId, upgraded));
        updateSynergyTracking(cardId);
    }

    private void updateSynergyTracking(String cardId) {
        if (isOrbGenerator(cardId)) orbGenerationCount++;
        if (isShivGenerator(cardId)) shivGenerationCount++;
        if (isPoisonCard(cardId)) poisonCount++;
        if (isBlockCard(cardId)) blockGenerationCount++;
        if (isStrengthGenerator(cardId)) strengthGenerationCount++;
        if (isFocusGenerator(cardId)) focusGenerationCount++;
        if (isMantraGenerator(cardId)) mantraCount++;
        if (isRetainCard(cardId)) retainCount++;
    }

    private boolean isOrbGenerator(String cardId) {
        return LoadoutConfig.ORB_GENERATORS.contains(cardId);
    }

    private boolean isShivGenerator(String cardId) {
        return LoadoutConfig.SHIV_GENERATORS.contains(cardId);
    }

    private boolean isPoisonCard(String cardId) {
        return LoadoutConfig.POISON_CARDS.contains(cardId);
    }

    private boolean isBlockCard(String cardId) {
        return LoadoutConfig.BLOCK_CARDS.contains(cardId);
    }

    private boolean isStrengthGenerator(String cardId) {
        return LoadoutConfig.STRENGTH_GENERATORS.contains(cardId);
    }

    private boolean isFocusGenerator(String cardId) {
        return LoadoutConfig.FOCUS_GENERATORS.contains(cardId);
    }

    private boolean isMantraGenerator(String cardId) {
        return LoadoutConfig.MANTRA_GENERATORS.contains(cardId);
    }

    private boolean isRetainCard(String cardId) {
        return LoadoutConfig.RETAIN_CARDS.contains(cardId);
    }

    private void removeWorstCard() {
        if (deck.size() <= 5) return;

        // Find worst card (highest priority = worst)
        int worstIdx = -1;
        int worstScore = Integer.MIN_VALUE;

        for (int i = 0; i < deck.size(); i++) {
            CardEntry card = deck.get(i);
            // Prefer removing Strikes/Defends
            int score = 0;
            if (card.cardId.contains("Strike")) score = 100;
            else if (card.cardId.contains("Defend")) score = 80;
            else score = 50 - LoadoutConfig.getCardPriority(card.cardId, playerClass);

            if (score > worstScore) {
                worstScore = score;
                worstIdx = i;
            }
        }

        if (worstIdx >= 0) {
            deck.remove(worstIdx);
        }
    }

    private void upgradeRandomCard() {
        List<Integer> upgradeable = new ArrayList<>();
        for (int i = 0; i < deck.size(); i++) {
            if (!deck.get(i).upgraded) {
                upgradeable.add(i);
            }
        }

        if (!upgradeable.isEmpty()) {
            int idx = upgradeable.get(random.nextInt(upgradeable.size()));
            deck.get(idx).upgraded = true;
        }
    }

    private void addRandomRelic() {
        if (relics.size() >= 5) return; // Cap at 5 relics

        List<String> available = LoadoutConfig.getAvailableRelics(playerClass);
        available.removeAll(relics); // Don't duplicate

        // Filter out relics we already have effects for
        available.removeIf(r -> !shouldAddRelic(r));

        if (!available.isEmpty()) {
            String relic = available.get(random.nextInt(available.size()));
            relics.add(relic);
            applyRelicEffects(relic);
        }
    }

    private boolean shouldAddRelic(String relicId) {
        // Skip useless relics
        if (LoadoutConfig.USELESS_RELICS.contains(relicId)) return false;

        // Skip class-specific relics for other classes
        String relicClass = LoadoutConfig.getRelicClass(relicId);
        if (relicClass != null && !relicClass.equals(playerClass)) return false;

        return true;
    }

    private void applyRelicEffects(String relicId) {
        // Track synergy-relevant relics
        if ("Potion Belt".equals(relicId)) {
            potionSlots += 2;
        }
        if ("Snecko Eye".equals(relicId) || "Runic Pyramid".equals(relicId)) {
            // These are powerful but change how the deck plays
        }
    }

    private void gainRandomPotion() {
        if (potions.size() >= potionSlots) {
            // Replace a random potion
            if (!potions.isEmpty()) {
                potions.remove(random.nextInt(potions.size()));
            }
        }

        if (potions.size() < potionSlots) {
            List<String> available = LoadoutConfig.getAvailablePotions(playerClass);
            if (!available.isEmpty()) {
                potions.add(available.get(random.nextInt(available.size())));
            }
        }
    }

    private void finalizeLoadout() {
        // Ensure minimum deck size
        while (deck.size() < 10) {
            addRandomCard();
        }

        // Cap deck size
        while (deck.size() > 35) {
            removeWorstCard();
        }

        // Apply some damage if ascension > 0
        if (ascension > 0) {
            int damagePercent = 5 + random.nextInt(25);
            currentHp = (int) (maxHp * (1.0 - damagePercent / 100.0));
        }
        currentHp = Math.max(1, Math.min(currentHp, maxHp));

        // Fill empty potion slots with some probability
        while (potions.size() < potionSlots && random.nextDouble() < 0.6) {
            gainRandomPotion();
        }
    }

    public BuiltLoadout build() {
        return new BuiltLoadout(playerClass, deck, relics, potions, maxHp, currentHp, potionSlots, ascension);
    }

    /**
     * Generate a random loadout for a random class.
     */
    public static BuiltLoadout generateRandom() {
        return generateRandom(new Random());
    }

    public static BuiltLoadout generateRandom(Random random) {
        String[] classes = LoadoutConfig.PLAYER_CLASS_NAMES;
        String playerClass = classes[random.nextInt(classes.length)];
        return generateForClass(playerClass, random);
    }

    public static BuiltLoadout generateForClass(String playerClass, Random random) {
        LoadoutBuilder builder = new LoadoutBuilder(playerClass, random);
        int numEvents = 10 + random.nextInt(41); // 10-50 events
        builder.applyRandomEvents(numEvents);
        return builder.build();
    }

    public enum CardSynergy {
        REQUIRES_ORBS,
        REQUIRES_SHIVS,
        REQUIRES_POISON,
        REQUIRES_BLOCK,
        REQUIRES_STRENGTH,
        REQUIRES_FOCUS,
        REQUIRES_MANTRA,
        REQUIRES_RETAIN
    }
}
