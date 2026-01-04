package stsarena.communication;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import communicationmod.CommandExecutor;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.RandomLoadoutGenerator;

/**
 * CommunicationMod command extension for arena mode.
 *
 * Usage: arena <CHARACTER> <ENCOUNTER>
 * Example: arena IRONCLAD Cultist
 *
 * This command starts an arena fight with a randomly generated loadout
 * for the specified character (with random ascension level).
 */
public class ArenaCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the arena command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaCommand());
            STSArena.logger.info("Registered arena command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena";
    }

    @Override
    public boolean isAvailable() {
        // Arena command is available when not in a dungeon (at main menu)
        return !CommandExecutor.isInDungeon();
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }

        String characterName = tokens[1].toUpperCase();

        // Combine remaining tokens for encounter name (may have spaces)
        StringBuilder encounterBuilder = new StringBuilder();
        for (int i = 2; i < tokens.length; i++) {
            if (i > 2) encounterBuilder.append(" ");
            encounterBuilder.append(tokens[i]);
        }
        String encounter = encounterBuilder.toString();

        STSArena.logger.info("Arena command: " + characterName + " vs " + encounter);

        // Get PlayerClass from character name
        AbstractPlayer.PlayerClass playerClass;
        try {
            playerClass = AbstractPlayer.PlayerClass.valueOf(characterName);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException("Invalid character: " + characterName +
                ". Valid options: IRONCLAD, SILENT, DEFECT, WATCHER");
        }

        // Generate a loadout for the specified character (random ascension)
        RandomLoadoutGenerator.GeneratedLoadout loadout =
            RandomLoadoutGenerator.generateForClass(playerClass);

        // Start the arena fight
        ArenaRunner.startFight(loadout, encounter);
    }
}
