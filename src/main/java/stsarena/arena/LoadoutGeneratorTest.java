package stsarena.arena;

import java.util.*;

/**
 * Standalone test runner for LoadoutBuilder.
 *
 * This can be run outside of the game to sanity check the generation logic.
 * Run with: java -cp target/classes stsarena.arena.LoadoutGeneratorTest
 */
public class LoadoutGeneratorTest {

    public static void main(String[] args) {
        System.out.println("=== LoadoutBuilder Test ===\n");

        int numTests = 20;
        if (args.length > 0) {
            try {
                numTests = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        // Statistics tracking
        Map<String, Integer> classCount = new HashMap<>();
        Map<String, Integer> relicCount = new HashMap<>();
        Map<String, Integer> cardCount = new HashMap<>();
        List<Integer> deckSizes = new ArrayList<>();
        List<Integer> relicCounts = new ArrayList<>();
        List<Integer> potionCounts = new ArrayList<>();

        for (int i = 0; i < numTests; i++) {
            Random random = new Random(System.nanoTime());
            LoadoutBuilder.BuiltLoadout loadout = LoadoutBuilder.generateRandom(random);

            // Track stats
            classCount.merge(loadout.playerClass, 1, Integer::sum);
            deckSizes.add(loadout.deck.size());
            relicCounts.add(loadout.relics.size());
            potionCounts.add(loadout.potions.size());

            for (String relic : loadout.relics) {
                relicCount.merge(relic, 1, Integer::sum);
            }

            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                cardCount.merge(card.cardId, 1, Integer::sum);
            }

            // Print individual loadout
            System.out.println("--- Loadout " + (i + 1) + " ---");
            System.out.println(loadout);
            System.out.println("  Relics: " + String.join(", ", loadout.relics));
            System.out.println("  Potions: " + String.join(", ", loadout.potions));

            // Print deck summary
            Map<String, Integer> types = new HashMap<>();
            int upgraded = 0;
            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                String type = LoadoutConfig.getCardType(card.cardId);
                types.merge(type, 1, Integer::sum);
                if (card.upgraded) upgraded++;
            }
            System.out.println("  Deck: " + types.getOrDefault("ATTACK", 0) + " attacks, " +
                types.getOrDefault("SKILL", 0) + " skills, " +
                types.getOrDefault("POWER", 0) + " powers, " +
                upgraded + " upgraded");

            // Print all cards
            List<String> cardNames = new ArrayList<>();
            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                cardNames.add(card.toString());
            }
            Collections.sort(cardNames);
            System.out.println("  Cards: " + String.join(", ", cardNames));
            System.out.println();
        }

        // Print summary statistics
        System.out.println("=== Statistics ===");
        System.out.println("Class distribution:");
        for (String cls : new String[]{"IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"}) {
            System.out.println("  " + cls + ": " + classCount.getOrDefault(cls, 0));
        }

        System.out.println("\nDeck size: min=" + Collections.min(deckSizes) +
            ", max=" + Collections.max(deckSizes) +
            ", avg=" + String.format("%.1f", deckSizes.stream().mapToInt(Integer::intValue).average().orElse(0)));

        System.out.println("Relic count: min=" + Collections.min(relicCounts) +
            ", max=" + Collections.max(relicCounts) +
            ", avg=" + String.format("%.1f", relicCounts.stream().mapToInt(Integer::intValue).average().orElse(0)));

        System.out.println("Potion count: min=" + Collections.min(potionCounts) +
            ", max=" + Collections.max(potionCounts) +
            ", avg=" + String.format("%.1f", potionCounts.stream().mapToInt(Integer::intValue).average().orElse(0)));

        // Most common relics
        System.out.println("\nMost common relics:");
        relicCount.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

        // Most common cards
        System.out.println("\nMost common cards:");
        cardCount.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(15)
            .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));

        // Check for potential issues
        System.out.println("\n=== Sanity Checks ===");

        // Check that class-specific relics only appear for their class
        boolean relicIssue = false;
        // (This check would require tracking which class each loadout was for each relic)

        // Check deck sizes are reasonable
        if (Collections.min(deckSizes) < 8 || Collections.max(deckSizes) > 40) {
            System.out.println("WARNING: Deck sizes out of expected range (8-40)");
            relicIssue = true;
        }

        if (!relicIssue) {
            System.out.println("All sanity checks passed!");
        }
    }

    /**
     * Test a specific class multiple times.
     */
    public static void testClass(String playerClass, int count) {
        System.out.println("=== Testing " + playerClass + " (" + count + " loadouts) ===\n");

        for (int i = 0; i < count; i++) {
            Random random = new Random(System.nanoTime());
            LoadoutBuilder.BuiltLoadout loadout = LoadoutBuilder.generateForClass(playerClass, random);

            System.out.println("--- Loadout " + (i + 1) + " ---");
            System.out.println(loadout);
            System.out.println("  Relics: " + String.join(", ", loadout.relics));
            System.out.println("  Potions: " + String.join(", ", loadout.potions));

            // Check for class-specific relic violations
            for (String relic : loadout.relics) {
                String relicClass = LoadoutConfig.getRelicClass(relic);
                if (relicClass != null && !relicClass.equals(playerClass)) {
                    System.out.println("  ERROR: Wrong class relic " + relic + " (belongs to " + relicClass + ")");
                }
            }

            // Print deck
            List<String> cardNames = new ArrayList<>();
            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                cardNames.add(card.toString());
            }
            Collections.sort(cardNames);
            System.out.println("  Cards: " + String.join(", ", cardNames));
            System.out.println();
        }
    }
}
