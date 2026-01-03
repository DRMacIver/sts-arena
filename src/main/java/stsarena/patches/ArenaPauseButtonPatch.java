package stsarena.patches;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
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
import com.megacrit.cardcrawl.screens.options.OptionsPanel;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Adds a "Practice in Arena" button to the pause menu during normal runs.
 * This allows players to save their current deck and practice with it in arena mode.
 */
public class ArenaPauseButtonPatch {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    // Button dimensions and position - positioned to the left of the Exit button
    private static final float BUTTON_WIDTH = 280.0f * Settings.scale;
    private static final float BUTTON_HEIGHT = 80.0f * Settings.scale;
    // Position to the left of the exit button (exit is at ~1490)
    private static final float BUTTON_X = 1100.0f * Settings.xScale;
    private static final float BUTTON_Y = Settings.OPTION_Y - 375.0f * Settings.scale;

    private static Hitbox arenaButtonHb = new Hitbox(BUTTON_WIDTH, BUTTON_HEIGHT);
    private static boolean initialized = false;

    /**
     * Update the arena practice button during pause menu update.
     */
    @SpirePatch(clz = OptionsPanel.class, method = "update")
    public static class UpdatePatch {
        @SpirePostfixPatch
        public static void Postfix(OptionsPanel __instance) {
            // Only show during normal runs (not arena runs)
            if (!CardCrawlGame.isInARun() || ArenaRunner.isArenaRun()) {
                return;
            }

            // Initialize hitbox position
            if (!initialized) {
                arenaButtonHb.move(BUTTON_X, BUTTON_Y);
                initialized = true;
            }

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
     */
    @SpirePatch(clz = OptionsPanel.class, method = "render")
    public static class RenderPatch {
        @SpirePostfixPatch
        public static void Postfix(OptionsPanel __instance, SpriteBatch sb) {
            // Only show during normal runs (not arena runs)
            if (!CardCrawlGame.isInARun() || ArenaRunner.isArenaRun()) {
                return;
            }

            float x = arenaButtonHb.cX;
            float y = arenaButtonHb.cY;

            // Button background color
            Color buttonColor;
            if (arenaButtonHb.clickStarted) {
                buttonColor = new Color(0.6f, 0.6f, 0.6f, 1.0f);
            } else if (arenaButtonHb.hovered) {
                buttonColor = Color.WHITE;
            } else {
                buttonColor = new Color(0.8f, 0.8f, 0.8f, 1.0f);
            }

            // Draw a simple button background using the same style as other buttons
            sb.setColor(buttonColor);
            // Use a generic button image
            sb.draw(ImageMaster.OPTION_ABANDON,
                x - 317.5f, y - 244.0f,
                317.5f, 244.0f,
                635.0f, 488.0f,
                Settings.scale * 0.5f, Settings.scale * 0.5f,
                0.0f,
                0, 0, 635, 488,
                false, false);

            // Draw button label
            Color textColor = arenaButtonHb.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont,
                "Practice in Arena",
                x, y,
                textColor);

            // Debug: render hitbox
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

                // Open the encounter selection screen with this loadout
                STSArena.openEncounterSelectScreenWithLoadout(loadoutId);
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

        // Copy the deck
        List<AbstractCard> deck = new ArrayList<>();
        for (AbstractCard card : player.masterDeck.group) {
            deck.add(card.makeCopy());
        }

        // Copy the relics
        List<AbstractRelic> relics = new ArrayList<>();
        for (AbstractRelic relic : player.relics) {
            relics.add(relic.makeCopy());
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
}
