package stsarena.communication;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;
import stsarena.patches.NormalRunLoadoutSaver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CommunicationMod command to simulate "Practice in Arena" from pause menu.
 *
 * This command saves the current player state as a loadout, marks that we're
 * starting from a normal run, and starts an arena fight with the current combat encounter.
 *
 * Usage:
 *   practice_in_arena              - Start arena with current encounter
 *   practice_in_arena <ENCOUNTER>  - Start arena with specified encounter
 *
 * Available when in combat during a normal (non-arena) run.
 */
public class PracticeInArenaCommand implements CommandExecutor.CommandExtension {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    public static void register() {
        try {
            CommandExecutor.registerCommand(new PracticeInArenaCommand());
            STSArena.logger.info("Registered practice_in_arena command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, practice_in_arena command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "practice_in_arena";
    }

    @Override
    public boolean isAvailable() {
        // Available when in combat during a normal (non-arena) run
        if (!CommandExecutor.isInDungeon()) {
            return false;
        }
        if (ArenaRunner.isArenaRun()) {
            return false;
        }
        if (AbstractDungeon.getCurrRoom() == null) {
            return false;
        }
        return AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) {
            throw new InvalidCommandException("No player available");
        }

        // Determine encounter
        String encounter;
        if (tokens.length > 1) {
            // Use provided encounter name
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < tokens.length; i++) {
                if (i > 1) sb.append(" ");
                sb.append(tokens[i]);
            }
            encounter = sb.toString();
        } else {
            // Use current combat encounter
            encounter = NormalRunLoadoutSaver.getCurrentCombatEncounterId();
            if (encounter == null || encounter.isEmpty()) {
                throw new InvalidCommandException("Not in combat - no current encounter");
            }
        }

        STSArena.logger.info("practice_in_arena: Saving current state and starting arena vs " + encounter);

        // Save current player state as a loadout
        long loadoutId = saveCurrentLoadout(player);
        if (loadoutId <= 0) {
            throw new InvalidCommandException("Failed to save loadout");
        }

        // Get the saved loadout record
        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        ArenaRepository.LoadoutRecord loadoutRecord = repo.getLoadoutById(loadoutId);
        if (loadoutRecord == null) {
            throw new InvalidCommandException("Failed to retrieve saved loadout");
        }

        // Mark that we're starting from a normal run (so we can return to it later)
        ArenaRunner.setStartedFromNormalRun(player.chosenClass);

        // Start the arena fight
        ArenaRunner.startFightWithSavedLoadout(loadoutRecord, encounter);

        GameStateListener.registerStateChange();
    }

    /**
     * Save the current player state as a loadout.
     * Mirrors the logic in ArenaPauseButtonPatch.saveCurrentLoadout().
     */
    private long saveCurrentLoadout(AbstractPlayer player) {
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        String name = generateLoadoutName(player);

        // Copy the deck
        List<AbstractCard> deck = new ArrayList<>();
        for (AbstractCard card : player.masterDeck.group) {
            deck.add(card.makeCopy());
        }

        // Copy relics with pre-combat counters
        Map<String, Integer> preCombatCounters = NormalRunLoadoutSaver.getCombatStartRelicCounters();
        List<AbstractRelic> relics = new ArrayList<>();
        for (AbstractRelic relic : player.relics) {
            AbstractRelic copy = relic.makeCopy();
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

        boolean hasPrismaticShard = player.hasRelic("PrismaticShard");
        int ascensionLevel = AbstractDungeon.ascensionLevel;
        int potionSlots = player.potionSlots;

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

        ArenaRepository repo = new ArenaRepository(ArenaDatabase.getInstance());
        return repo.saveLoadout(loadout);
    }

    private String generateLoadoutName(AbstractPlayer player) {
        String className = player.chosenClass.name();
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
