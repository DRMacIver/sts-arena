package stsarena.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.screens.options.AbandonRunButton;
import com.megacrit.cardcrawl.screens.options.OptionsPanel;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds a "Practice in Arena" button to the pause menu during normal runs.
 * This allows players to save their current deck and practice with it in arena mode.
 * Styled to match the "Abandon Run" button and placed right below it.
 */
public class ArenaPauseButtonPatch {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    // Button image dimensions (same as AbandonRunButton)
    private static final int W = 440;
    private static final int H = 100;

    // Hitbox size (same as AbandonRunButton)
    private static final float HB_WIDTH = 340.0f * Settings.scale;
    private static final float HB_HEIGHT = 70.0f * Settings.scale;

    // Calculate panel center Y (same formula as OptionsPanel.SCREEN_CENTER_Y)
    // SCREEN_CENTER_Y = Settings.HEIGHT / 2.0f - 64.0f * Settings.scale
    private static final float PANEL_CENTER_Y = Settings.HEIGHT / 2.0f - 64.0f * Settings.scale;

    // The options panel has a visual top boundary approximately 270 pixels above its center
    private static final float PANEL_TOP_Y = PANEL_CENTER_Y + 270.0f * Settings.scale;

    // Button height after scaling
    private static final float BUTTON_HEIGHT = H * Settings.scale;

    // Visual button height (hitbox height, not image height - the image has padding)
    private static final float VISUAL_BUTTON_HEIGHT = 70.0f * Settings.scale;

    // Spacing between button centers - slightly less than visual height for overlap
    // Set to 62 for optimal visual alignment
    private static final float BUTTON_SPACING = 62.0f * Settings.scale;

    // Position of Practice in Arena button (BOTTOM edge near panel top, adjusted down slightly)
    private static final float BUTTON_Y = PANEL_TOP_Y + BUTTON_HEIGHT / 2.0f - 5.0f * Settings.scale;

    // Position of Abandon Run button (slightly overlapping - Practice in Arena draws on top)
    private static final float BUTTON_X = 1430.0f * Settings.xScale;
    private static final float ABANDON_BUTTON_Y = BUTTON_Y + BUTTON_SPACING;

    private static Hitbox arenaButtonHb = new Hitbox(HB_WIDTH, HB_HEIGHT);

    // Track if run is being abandoned (to prevent loadout creation)
    public static boolean isAbandoning = false;

    /**
     * Update the arena practice button during pause menu update.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.options.OptionsPanel", method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(OptionsPanel __instance) {
            // Only show during normal runs (not arena runs)
            if (!CardCrawlGame.isInARun() || ArenaRunner.isArenaRun()) {
                return;
            }

            // Position hitbox at button center (same as AbandonRunButton)
            arenaButtonHb.move(BUTTON_X, BUTTON_Y);
            arenaButtonHb.update();

            if (arenaButtonHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }

            if (arenaButtonHb.hovered && InputHelper.justClickedLeft) {
                CardCrawlGame.sound.play("UI_CLICK_1");
                arenaButtonHb.clickStarted = true;
            }

            if (arenaButtonHb.clicked) {
                arenaButtonHb.clicked = false;
                arenaButtonHb.clickStarted = false;
                handleArenaPractice();
            }
        }
    }

    /**
     * Render the arena practice button during pause menu render.
     * Styled to match the AbandonRunButton.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.options.OptionsPanel", method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(OptionsPanel __instance, SpriteBatch sb) {
            // Only show during normal runs (not arena runs)
            if (!CardCrawlGame.isInARun() || ArenaRunner.isArenaRun()) {
                return;
            }

            // Draw button image (same style as AbandonRunButton)
            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.OPTION_ABANDON,
                BUTTON_X - (float) W / 2.0f,
                BUTTON_Y - (float) H / 2.0f,
                (float) W / 2.0f,
                (float) H / 2.0f,
                W, H,
                Settings.scale, Settings.scale,
                0.0f,
                0, 0, W, H,
                false, false);

            // Draw button label (same font and position as AbandonRunButton)
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont,
                "Practice in Arena",
                BUTTON_X + 15.0f * Settings.scale,
                BUTTON_Y + 5.0f * Settings.scale,
                Settings.GOLD_COLOR);

            // Draw hover highlight (same as AbandonRunButton)
            if (arenaButtonHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0f, 1.0f, 1.0f, 0.2f));
                sb.draw(ImageMaster.OPTION_ABANDON,
                    BUTTON_X - (float) W / 2.0f,
                    BUTTON_Y - (float) H / 2.0f,
                    (float) W / 2.0f,
                    (float) H / 2.0f,
                    W, H,
                    Settings.scale, Settings.scale,
                    0.0f,
                    0, 0, W, H,
                    false, false);
                sb.setBlendFunction(770, 771);
            }

            // Render hitbox for click detection
            arenaButtonHb.render(sb);
        }
    }

    /**
     * Handle clicking the arena practice button.
     */
    private static void handleArenaPractice() {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) {
            STSArena.logger.warn("Cannot open arena - no player");
            return;
        }

        STSArena.logger.info("Practice in Arena button clicked - saving current deck");

        try {
            // Create loadout from current state
            long loadoutId = saveCurrentLoadout(player);

            if (loadoutId > 0) {
                STSArena.logger.info("Saved loadout with ID: " + loadoutId);

                // Close the settings screen
                AbstractDungeon.closeCurrentScreen();

                // Get the current encounter (if in combat)
                String currentEncounter = NormalRunLoadoutSaver.getCurrentCombatEncounterId();
                STSArena.logger.info("Current combat encounter: " + currentEncounter);

                // Open the encounter selection screen with this loadout and current encounter
                STSArena.openEncounterSelectScreenWithLoadout(loadoutId, currentEncounter);
            }
        } catch (Exception e) {
            STSArena.logger.error("Error handling arena practice button", e);
        }
    }

    /**
     * Save the current player state as a loadout.
     */
    private static long saveCurrentLoadout(AbstractPlayer player) {
        // Generate unique ID and name
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String name = generateLoadoutName(player);

        // Copy the deck (preserving upgrades)
        List<AbstractCard> deck = new ArrayList<>();
        for (AbstractCard card : player.masterDeck.group) {
            AbstractCard copy = card.makeCopy();
            // makeCopy() doesn't preserve upgrades, so apply them manually
            for (int i = 0; i < card.timesUpgraded; i++) {
                if (copy.canUpgrade()) {
                    copy.upgrade();
                }
            }
            deck.add(copy);
        }

        // Copy the relics, using pre-combat counters if available
        // This ensures relics like Neow's Lament have their charges from before the fight
        Map<String, Integer> preCombatCounters = NormalRunLoadoutSaver.getCombatStartRelicCounters();
        List<AbstractRelic> relics = new ArrayList<>();
        for (AbstractRelic relic : player.relics) {
            AbstractRelic copy = relic.makeCopy();
            // Use pre-combat counter if available, otherwise use current
            Integer preCombatCounter = preCombatCounters.get(relic.relicId);
            copy.counter = preCombatCounter != null ? preCombatCounter : relic.counter;
            relics.add(copy);
        }

        // Copy current potions
        List<AbstractPotion> potions = new ArrayList<>();
        for (AbstractPotion potion : player.potions) {
            if (!(potion instanceof PotionSlot)) {
                potions.add(potion.makeCopy());
            }
        }

        // Check for Prismatic Shard
        boolean hasPrismaticShard = player.hasRelic("PrismaticShard");

        // Get ascension level
        int ascensionLevel = AbstractDungeon.ascensionLevel;

        // Get potion slots from player
        int potionSlots = player.potionSlots;

        // Create the loadout
        RandomLoadoutGenerator.GeneratedLoadout loadout = new RandomLoadoutGenerator.GeneratedLoadout(
            id,
            name,
            createdAt,
            player.chosenClass,
            deck,
            relics,
            potions,
            potionSlots,
            hasPrismaticShard,
            player.maxHealth,
            player.currentHealth,
            ascensionLevel
        );

        // Save to database
        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        return repo.saveLoadout(loadout);
    }

    /**
     * Generate a descriptive name for the loadout.
     */
    private static String generateLoadoutName(AbstractPlayer player) {
        String className = player.chosenClass.name();
        // Convert to title case (e.g., "IRONCLAD" -> "Ironclad")
        className = className.substring(0, 1) + className.substring(1).toLowerCase();

        int floor = AbstractDungeon.floorNum;
        int ascension = AbstractDungeon.ascensionLevel;
        String timestamp = DATE_FORMAT.format(new Date());

        if (ascension > 0) {
            return className + " A" + ascension + " F" + floor + " Practice (" + timestamp + ")";
        } else {
            return className + " F" + floor + " Practice (" + timestamp + ")";
        }
    }

    /**
     * Move the Abandon Run button up to make room and avoid overlapping settings.
     * Also track when it's clicked to prevent loadout creation.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.options.AbandonRunButton", method = "update")
    public static class AbandonRunButtonUpdatePatch {
        private static Field yField = null;
        private static Field hbField = null;
        private static boolean fieldsInitialized = false;

        @SpirePostfixPatch
        public static void Postfix(AbandonRunButton __instance) {
            // Initialize reflection fields once
            if (!fieldsInitialized) {
                try {
                    yField = AbandonRunButton.class.getDeclaredField("y");
                    yField.setAccessible(true);
                    hbField = AbandonRunButton.class.getDeclaredField("hb");
                    hbField.setAccessible(true);
                    fieldsInitialized = true;
                } catch (Exception e) {
                    STSArena.logger.error("Failed to access AbandonRunButton fields", e);
                }
            }

            // Move the button up
            if (yField != null && hbField != null) {
                try {
                    float currentY = yField.getFloat(__instance);
                    if (Math.abs(currentY - ABANDON_BUTTON_Y) > 1.0f) {
                        yField.setFloat(__instance, ABANDON_BUTTON_Y);
                        // Also move the hitbox
                        Hitbox hb = (Hitbox) hbField.get(__instance);
                        if (hb != null) {
                            hb.move(hb.cX, ABANDON_BUTTON_Y);
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Check if Abandon was clicked - set flag to prevent loadout save
            // Check for clickStarted (mouse down) or clicked (mouse up) on the hitbox
            try {
                if (hbField != null) {
                    Hitbox hb = (Hitbox) hbField.get(__instance);
                    if (hb != null && (hb.clicked || hb.clickStarted)) {
                        if (!isAbandoning) {
                            isAbandoning = true;
                            STSArena.logger.info("Abandon Run clicked - will not save loadout");
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Move the Abandon Run button rendering up.
     */
    @SpirePatch(cls = "com.megacrit.cardcrawl.screens.options.AbandonRunButton", method = "render")
    public static class AbandonRunButtonRenderPatch {
        private static Field yField = null;
        private static boolean fieldInitialized = false;

        @SpirePrefixPatch
        public static void Prefix(AbandonRunButton __instance, SpriteBatch sb) {
            // Initialize reflection field once
            if (!fieldInitialized) {
                try {
                    yField = AbandonRunButton.class.getDeclaredField("y");
                    yField.setAccessible(true);
                    fieldInitialized = true;
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Ensure Y position is set for rendering
            if (yField != null) {
                try {
                    yField.setFloat(__instance, ABANDON_BUTTON_Y);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
