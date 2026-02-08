package stsarena.arena;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests for LoadoutBuilder - loadout generation logic.
 */
public class LoadoutBuilderTest {

    @Test
    public void testAscendersBaneAddedAtAscension10() {
        // Use a seeded random for reproducibility
        Random random = new Random(12345);

        // Create a builder and force ascension to 10
        LoadoutBuilder builder = new LoadoutBuilder("IRONCLAD", random);

        // Use reflection to set ascension to 10 and call finalizeLoadout
        // Since we can't directly set ascension, we'll generate loadouts and check

        // Generate many loadouts and verify Ascender's Bane is present at A10+
        for (int i = 0; i < 50; i++) {
            Random testRandom = new Random(i);
            LoadoutBuilder.BuiltLoadout loadout = LoadoutBuilder.generateForClass("IRONCLAD", testRandom);

            boolean hasAscendersBane = false;
            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                if ("AscendersBane".equals(card.cardId)) {
                    hasAscendersBane = true;
                    break;
                }
            }

            if (loadout.ascension >= 10) {
                assertTrue("Loadout at ascension " + loadout.ascension + " should have Ascender's Bane",
                    hasAscendersBane);
            } else {
                assertFalse("Loadout at ascension " + loadout.ascension + " should NOT have Ascender's Bane",
                    hasAscendersBane);
            }
        }
    }

    @Test
    public void testAscendersBaneNotAddedBeforeAscension10() {
        // Generate loadouts with seeds that give low ascension values
        // and verify no Ascender's Bane
        int lowAscensionCount = 0;
        int highAscensionCount = 0;

        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            LoadoutBuilder.BuiltLoadout loadout = LoadoutBuilder.generateForClass("THE_SILENT", random);

            if (loadout.ascension < 10) {
                lowAscensionCount++;
            } else {
                highAscensionCount++;
            }

            boolean hasAscendersBane = loadout.deck.stream()
                .anyMatch(card -> "AscendersBane".equals(card.cardId));

            // Verify the invariant
            assertEquals("Ascender's Bane presence should match ascension >= 10",
                loadout.ascension >= 10, hasAscendersBane);
        }

        // Ensure we tested both cases (statistically should happen with 100 trials)
        assertTrue("Should have some low ascension loadouts", lowAscensionCount > 0);
        assertTrue("Should have some high ascension loadouts", highAscensionCount > 0);
    }

    @Test
    public void testAscendersBaneIsNotUpgraded() {
        // Verify that when Ascender's Bane is added, it's never upgraded
        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            LoadoutBuilder.BuiltLoadout loadout = LoadoutBuilder.generateForClass("DEFECT", random);

            for (LoadoutBuilder.CardEntry card : loadout.deck) {
                if ("AscendersBane".equals(card.cardId)) {
                    assertFalse("Ascender's Bane should never be upgraded", card.upgraded);
                }
            }
        }
    }
}
