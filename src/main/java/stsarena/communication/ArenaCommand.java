package stsarena.communication;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import communicationmod.CommandExecutor;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.LoadoutConfig;
import stsarena.arena.RandomLoadoutGenerator;

/**
 * CommunicationMod command extension for arena mode.
 *
 * Usage: arena <CHARACTER> <ENCOUNTER> [SEED]
 * Example: arena IRONCLAD Cultist
 * Example: arena IRONCLAD Cultist 12345
 *
 * This command starts an arena fight with a randomly generated loadout
 * for the specified character (with random ascension level).
 *
 * The optional SEED parameter allows reproducible loadout generation
 * for testing purposes.
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

        // Check if last token is a numeric seed
        Long seed = null;
        int encounterEndIndex = tokens.length;
        String lastToken = tokens[tokens.length - 1];
        try {
            seed = Long.parseLong(lastToken);
            encounterEndIndex = tokens.length - 1;  // Exclude seed from encounter name
        } catch (NumberFormatException e) {
            // Last token is not a seed, include it in encounter name
        }

        // Combine remaining tokens for encounter name (may have spaces)
        StringBuilder encounterBuilder = new StringBuilder();
        for (int i = 2; i < encounterEndIndex; i++) {
            if (i > 2) encounterBuilder.append(" ");
            encounterBuilder.append(tokens[i]);
        }
        String rawEncounter = encounterBuilder.toString();

        // Normalize encounter name to match LoadoutConfig.ENCOUNTERS (case-insensitive lookup)
        // CommunicationMod may lowercase the command, so we need to find the correct case
        String encounter = normalizeEncounterName(rawEncounter);

        STSArena.logger.info("Arena command: " + characterName + " vs " + encounter +
            " (raw: " + rawEncounter + ")" + (seed != null ? " seed=" + seed : ""));

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
            RandomLoadoutGenerator.generateForClass(playerClass, seed);

        // Start the arena fight
        ArenaRunner.startFight(loadout, encounter);
    }

    /**
     * Normalize an encounter name to match the correct case from LoadoutConfig.ENCOUNTERS.
     * CommunicationMod may lowercase commands, so we need case-insensitive lookup.
     *
     * @param rawEncounter The raw encounter name (possibly lowercase)
     * @return The correctly-cased encounter name, or the original if not found
     */
    private static String normalizeEncounterName(String rawEncounter) {
        if (rawEncounter == null || rawEncounter.isEmpty()) {
            return rawEncounter;
        }

        String lowerRaw = rawEncounter.toLowerCase();

        // Search through all encounter categories
        for (LoadoutConfig.EncounterCategory category : LoadoutConfig.ENCOUNTER_CATEGORIES) {
            for (String encounter : category.encounters) {
                if (encounter.toLowerCase().equals(lowerRaw)) {
                    STSArena.logger.info("ARENA: Normalized encounter '" + rawEncounter + "' -> '" + encounter + "'");
                    return encounter;
                }
            }
        }

        // Also check the flat ENCOUNTERS array (for any stragglers)
        for (String encounter : LoadoutConfig.ENCOUNTERS) {
            if (encounter.toLowerCase().equals(lowerRaw)) {
                STSArena.logger.info("ARENA: Normalized encounter '" + rawEncounter + "' -> '" + encounter + "'");
                return encounter;
            }
        }

        // Not found - return as-is (will likely fail, but with a clear error)
        STSArena.logger.warn("ARENA: Unknown encounter name: " + rawEncounter);
        return rawEncounter;
    }
}
