package stsarena.arena;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import org.junit.Test;
import org.junit.runner.RunWith;
import stsarena.GdxTestRunner;

import static org.junit.Assert.*;

/**
 * Tests for LoadoutConfig - pure configuration and utility functions.
 * These tests don't require game initialization.
 */
@RunWith(GdxTestRunner.class)
public class LoadoutConfigTest {

    @Test
    public void testPlayerClassesArray() {
        assertEquals("Should have 4 player classes", 4, LoadoutConfig.getPlayerClasses().length);

        boolean hasIronclad = false, hasSilent = false, hasDefect = false, hasWatcher = false;
        for (AbstractPlayer.PlayerClass pc : LoadoutConfig.getPlayerClasses()) {
            if (pc == AbstractPlayer.PlayerClass.IRONCLAD) hasIronclad = true;
            if (pc == AbstractPlayer.PlayerClass.THE_SILENT) hasSilent = true;
            if (pc == AbstractPlayer.PlayerClass.DEFECT) hasDefect = true;
            if (pc == AbstractPlayer.PlayerClass.WATCHER) hasWatcher = true;
        }
        assertTrue("Should include Ironclad", hasIronclad);
        assertTrue("Should include Silent", hasSilent);
        assertTrue("Should include Defect", hasDefect);
        assertTrue("Should include Watcher", hasWatcher);
    }

    @Test
    public void testGetCardColorIronclad() {
        assertEquals(AbstractCard.CardColor.RED,
            LoadoutConfig.getCardColor(AbstractPlayer.PlayerClass.IRONCLAD));
    }

    @Test
    public void testGetCardColorSilent() {
        assertEquals(AbstractCard.CardColor.GREEN,
            LoadoutConfig.getCardColor(AbstractPlayer.PlayerClass.THE_SILENT));
    }

    @Test
    public void testGetCardColorDefect() {
        assertEquals(AbstractCard.CardColor.BLUE,
            LoadoutConfig.getCardColor(AbstractPlayer.PlayerClass.DEFECT));
    }

    @Test
    public void testGetCardColorWatcher() {
        assertEquals(AbstractCard.CardColor.PURPLE,
            LoadoutConfig.getCardColor(AbstractPlayer.PlayerClass.WATCHER));
    }

    @Test
    public void testGetBaseMaxHpIronclad() {
        assertEquals("Ironclad should have 80 HP", 80,
            LoadoutConfig.getBaseMaxHp(AbstractPlayer.PlayerClass.IRONCLAD));
    }

    @Test
    public void testGetBaseMaxHpSilent() {
        assertEquals("Silent should have 70 HP", 70,
            LoadoutConfig.getBaseMaxHp(AbstractPlayer.PlayerClass.THE_SILENT));
    }

    @Test
    public void testGetBaseMaxHpDefect() {
        assertEquals("Defect should have 75 HP", 75,
            LoadoutConfig.getBaseMaxHp(AbstractPlayer.PlayerClass.DEFECT));
    }

    @Test
    public void testGetBaseMaxHpWatcher() {
        assertEquals("Watcher should have 72 HP", 72,
            LoadoutConfig.getBaseMaxHp(AbstractPlayer.PlayerClass.WATCHER));
    }

    @Test
    public void testGetStarterRelicIdIronclad() {
        assertEquals("Burning Blood",
            LoadoutConfig.getStarterRelicId(AbstractPlayer.PlayerClass.IRONCLAD));
    }

    @Test
    public void testGetStarterRelicIdSilent() {
        assertEquals("Ring of the Snake",
            LoadoutConfig.getStarterRelicId(AbstractPlayer.PlayerClass.THE_SILENT));
    }

    @Test
    public void testGetStarterRelicIdDefect() {
        assertEquals("Cracked Core",
            LoadoutConfig.getStarterRelicId(AbstractPlayer.PlayerClass.DEFECT));
    }

    @Test
    public void testGetStarterRelicIdWatcher() {
        assertEquals("PureWater",
            LoadoutConfig.getStarterRelicId(AbstractPlayer.PlayerClass.WATCHER));
    }

    @Test
    public void testIsExcludedRelicBossUpgrades() {
        assertTrue("Black Blood should be excluded",
            LoadoutConfig.isExcludedRelic("Black Blood"));
        assertTrue("Ring of the Serpent should be excluded",
            LoadoutConfig.isExcludedRelic("Ring of the Serpent"));
        assertTrue("FrozenCore should be excluded",
            LoadoutConfig.isExcludedRelic("FrozenCore"));
        assertTrue("HolyWater should be excluded",
            LoadoutConfig.isExcludedRelic("HolyWater"));
    }

    @Test
    public void testIsExcludedRelicCirclets() {
        assertTrue("Circlet should be excluded",
            LoadoutConfig.isExcludedRelic("Circlet"));
        assertTrue("Red Circlet should be excluded",
            LoadoutConfig.isExcludedRelic("Red Circlet"));
    }

    @Test
    public void testIsExcludedRelicNeowsBlessing() {
        assertTrue("NeowsBlessing should be excluded",
            LoadoutConfig.isExcludedRelic("NeowsBlessing"));
    }

    @Test
    public void testIsExcludedRelicNormalRelics() {
        assertFalse("Vajra should not be excluded",
            LoadoutConfig.isExcludedRelic("Vajra"));
        assertFalse("Bag of Preparation should not be excluded",
            LoadoutConfig.isExcludedRelic("Bag of Preparation"));
        assertFalse("Snecko Eye should not be excluded",
            LoadoutConfig.isExcludedRelic("Snecko Eye"));
    }

    @Test
    public void testIsExcludedCardRitualDagger() {
        assertTrue("Ritual Dagger should be excluded",
            LoadoutConfig.isExcludedCard("Ritual Dagger"));
    }

    @Test
    public void testIsExcludedCardLessonLearned() {
        // Lesson Learned is no longer excluded - it can still be useful in a single fight
        assertFalse("Lesson Learned should not be excluded",
            LoadoutConfig.isExcludedCard("Lesson Learned"));
    }

    @Test
    public void testIsExcludedCardNormalCards() {
        assertFalse("Strike should not be excluded",
            LoadoutConfig.isExcludedCard("Strike"));
        assertFalse("Defend should not be excluded",
            LoadoutConfig.isExcludedCard("Defend"));
        assertFalse("Inflame should not be excluded",
            LoadoutConfig.isExcludedCard("Inflame"));
    }

    @Test
    public void testCalculateAttackCount() {
        // With randomValue = 0, we should get minimum ratio (40%)
        int attacks = LoadoutConfig.calculateAttackCount(20, 0.0);
        assertEquals("20 cards * 40% = 8 attacks", 8, attacks);

        // With randomValue = 1, we should get maximum ratio (50%)
        attacks = LoadoutConfig.calculateAttackCount(20, 1.0);
        assertEquals("20 cards * 50% = 10 attacks", 10, attacks);

        // Middle value
        attacks = LoadoutConfig.calculateAttackCount(20, 0.5);
        assertEquals("20 cards * 45% = 9 attacks", 9, attacks);
    }

    @Test
    public void testCalculatePowerCount() {
        // Should be between 1 and 4 powers
        int powers = LoadoutConfig.calculatePowerCount(10, 0);
        assertTrue("Powers should be at least 1", powers >= 1);
        assertTrue("Powers should be at most 4", powers <= 4);

        // With limited available powers
        powers = LoadoutConfig.calculatePowerCount(2, 0);
        assertTrue("Powers should be at least 1", powers >= 1);
        assertTrue("Powers should be at most 2 when only 2 available", powers <= 2);
    }

    @Test
    public void testEncountersArray() {
        assertNotNull("Encounters should not be null", LoadoutConfig.ENCOUNTERS);
        assertTrue("Should have Act 1 encounters", LoadoutConfig.ENCOUNTERS.length >= 10);

        // Check for some specific Act 1 encounters
        boolean hasCultist = false, hasJawWorm = false, hasGremlinNob = false;
        for (String enc : LoadoutConfig.ENCOUNTERS) {
            if (enc.equals("Cultist")) hasCultist = true;
            if (enc.equals("Jaw Worm")) hasJawWorm = true;
            if (enc.equals("Gremlin Nob")) hasGremlinNob = true;
        }
        assertTrue("Should include Cultist", hasCultist);
        assertTrue("Should include Jaw Worm", hasJawWorm);
        assertTrue("Should include Gremlin Nob elite", hasGremlinNob);
    }

    @Test
    public void testDeckSizeBounds() {
        assertTrue("Min deck size should be reasonable",
            LoadoutConfig.MIN_DECK_SIZE >= 10);
        assertTrue("Max deck size should be larger than min",
            LoadoutConfig.MAX_DECK_SIZE > LoadoutConfig.MIN_DECK_SIZE);
        assertTrue("Max deck size should be reasonable",
            LoadoutConfig.MAX_DECK_SIZE <= 40);
    }

    @Test
    public void testRelicCountBounds() {
        assertTrue("Min relics should be at least 1",
            LoadoutConfig.MIN_RELICS >= 1);
        assertTrue("Max relics should be larger than min",
            LoadoutConfig.MAX_RELICS > LoadoutConfig.MIN_RELICS);
        assertTrue("Max relics should be reasonable",
            LoadoutConfig.MAX_RELICS <= 20);
    }

    @Test
    public void testUpgradeChance() {
        assertTrue("Upgrade chance should be between 0 and 1",
            LoadoutConfig.UPGRADE_CHANCE >= 0 && LoadoutConfig.UPGRADE_CHANCE <= 1);
    }

    @Test
    public void testPrismaticShardChance() {
        assertTrue("Prismatic shard chance should be between 0 and 1",
            LoadoutConfig.PRISMATIC_SHARD_CHANCE >= 0 && LoadoutConfig.PRISMATIC_SHARD_CHANCE <= 1);
        assertTrue("Prismatic shard should be rare (< 50%)",
            LoadoutConfig.PRISMATIC_SHARD_CHANCE < 0.5);
    }
}
