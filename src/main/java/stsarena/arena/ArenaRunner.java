package stsarena.arena;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.screens.CharSelectInfo;
import stsarena.STSArena;

import java.util.ArrayList;

/**
 * Handles starting arena fights with custom loadouts.
 */
public class ArenaRunner {

    private static RandomLoadoutGenerator.GeneratedLoadout pendingLoadout;
    private static String pendingEncounter;
    private static boolean arenaRunInProgress = false;

    /**
     * Start a random arena fight from the main menu.
     */
    public static void startRandomFight() {
        STSArena.logger.info("Starting random arena fight");

        // Generate random loadout and encounter
        RandomLoadoutGenerator.GeneratedLoadout loadout = RandomLoadoutGenerator.generate();
        String encounter = RandomLoadoutGenerator.getRandomEncounter();

        startFight(loadout, encounter);
    }

    /**
     * Start an arena fight with a specific loadout and encounter.
     */
    public static void startFight(RandomLoadoutGenerator.GeneratedLoadout loadout, String encounter) {
        pendingLoadout = loadout;
        pendingEncounter = encounter;
        arenaRunInProgress = true;

        STSArena.logger.info("Starting arena: " + loadout.playerClass + " vs " + encounter);

        // Set up the game to start a new run with the chosen character
        CardCrawlGame.chosenCharacter = loadout.playerClass;

        // Generate a random seed for the run
        Settings.seed = SeedHelper.generateUnofficalSeed();

        // Use the standard game start mechanism
        // This will create the dungeon, player, etc.
        CardCrawlGame.music.fadeOutTempBGM();
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();

        // Set ascension level to 0 for arena
        AbstractDungeon.isAscensionMode = false;
        AbstractDungeon.ascensionLevel = 0;

        // Trigger the game to start - the dungeon will be created
        // We hook into room entry to apply our loadout
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;

        // This triggers CardCrawlGame to create the dungeon on next update
        CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;

        // The actual loadout application and fight start happens via our patch
        // after the dungeon initializes and player enters the first room
    }

    /**
     * Called after the dungeon is initialized to apply the loadout and start the fight.
     * This should be called from a patch on AbstractDungeon initialization.
     */
    public static void onDungeonInitialized() {
        if (!arenaRunInProgress || pendingLoadout == null) {
            return;
        }

        STSArena.logger.info("Dungeon initialized, applying arena loadout");

        try {
            applyLoadout(pendingLoadout);
            transitionToFight(pendingEncounter);
        } catch (Exception e) {
            STSArena.logger.error("Failed to start arena fight", e);
        } finally {
            // Clear pending state
            pendingLoadout = null;
            pendingEncounter = null;
            arenaRunInProgress = false;
        }
    }

    /**
     * Apply the generated loadout to the current player.
     */
    private static void applyLoadout(RandomLoadoutGenerator.GeneratedLoadout loadout) {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) {
            STSArena.logger.error("No player to apply loadout to!");
            return;
        }

        STSArena.logger.info("Applying loadout to " + player.chosenClass);

        // Clear existing deck and add our cards
        player.masterDeck.clear();
        for (AbstractCard card : loadout.deck) {
            player.masterDeck.addToTop(card.makeCopy());
        }

        // Clear existing relics and add ours
        player.relics.clear();
        int relicIndex = 0;
        for (AbstractRelic relic : loadout.relics) {
            AbstractRelic copy = relic.makeCopy();
            copy.instantObtain(player, relicIndex++, false);
        }

        // Set HP
        player.maxHealth = loadout.maxHp;
        player.currentHealth = loadout.currentHp;

        // Give some gold
        player.gold = 100 + (int)(Math.random() * 200);

        // Reset energy
        player.energy.energyMaster = 3;

        STSArena.logger.info("Loadout applied: " + player.masterDeck.size() + " cards, " +
                            player.relics.size() + " relics");
    }

    /**
     * Force transition to a monster fight.
     */
    private static void transitionToFight(String encounterName) {
        STSArena.logger.info("Transitioning to fight: " + encounterName);

        MapRoomNode currNode = AbstractDungeon.currMapNode;
        if (currNode == null) {
            STSArena.logger.error("No current map node!");
            return;
        }

        // Add the encounter to the monster list
        if (AbstractDungeon.getCurrRoom() instanceof MonsterRoom) {
            AbstractDungeon.monsterList.add(1, encounterName);
        } else {
            AbstractDungeon.monsterList.add(0, encounterName);
        }

        // Create a new node with a monster room
        MapRoomNode node = new MapRoomNode(currNode.x, currNode.y);
        node.room = new MonsterRoom();

        // Copy edges from current node
        ArrayList<MapEdge> curEdges = currNode.getEdges();
        for (MapEdge edge : curEdges) {
            node.addEdge(edge);
        }

        // Start the transition
        AbstractDungeon.nextRoom = node;
        AbstractDungeon.nextRoomTransitionStart();
    }

    /**
     * Check if an arena run is currently being set up.
     */
    public static boolean isArenaRunInProgress() {
        return arenaRunInProgress;
    }

    /**
     * Check if we have a pending loadout to apply.
     */
    public static boolean hasPendingLoadout() {
        return pendingLoadout != null;
    }
}
