package stsarena.communication;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import communicationmod.CommandExecutor;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;
import stsarena.arena.LoadoutConfig;
import stsarena.arena.RandomLoadoutGenerator;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

/**
 * CommunicationMod command extension for arena mode.
 *
 * Usage:
 *   arena <CHARACTER> <ENCOUNTER> [SEED]    - Start with random loadout
 *   arena --loadout <ID> <ENCOUNTER>        - Start with saved loadout
 *
 * Examples:
 *   arena IRONCLAD Cultist                  - Random IRONCLAD loadout vs Cultist
 *   arena IRONCLAD Cultist 12345            - Random with seed for reproducibility
 *   arena --loadout 5 Cultist               - Use saved loadout #5 vs Cultist
 *
 * The optional SEED parameter (for random mode) allows reproducible loadout
 * generation for testing purposes.
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

        // Check for --loadout option
        if (tokens[1].equalsIgnoreCase("--loadout")) {
            executeWithSavedLoadout(tokens);
            return;
        }

        // Standard random loadout mode: arena <CHARACTER> <ENCOUNTER> [SEED]
        String characterName = normalizeCharacterName(tokens[1].toUpperCase());

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
                ". Valid options: IRONCLAD, SILENT (or THE_SILENT), DEFECT, WATCHER");
        }

        // Generate a loadout for the specified character (random ascension)
        RandomLoadoutGenerator.GeneratedLoadout loadout =
            RandomLoadoutGenerator.generateForClass(playerClass, seed);

        // Start the arena fight
        ArenaRunner.startFight(loadout, encounter);
    }

    /**
     * Execute arena with a saved loadout: arena --loadout <ID> <ENCOUNTER>
     */
    private void executeWithSavedLoadout(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 4) {
            throw new InvalidCommandException("Usage: arena --loadout <id> <encounter>");
        }

        // Parse loadout ID
        long loadoutId;
        try {
            loadoutId = Long.parseLong(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid loadout ID: " + tokens[2]);
        }

        // Combine remaining tokens for encounter name
        StringBuilder encounterBuilder = new StringBuilder();
        for (int i = 3; i < tokens.length; i++) {
            if (i > 3) encounterBuilder.append(" ");
            encounterBuilder.append(tokens[i]);
        }
        String rawEncounter = encounterBuilder.toString();
        String encounter = normalizeEncounterName(rawEncounter);

        // Get database and repository
        ArenaDatabase db = ArenaDatabase.getInstance();
        if (db == null) {
            throw new InvalidCommandException("Database not available");
        }
        ArenaRepository repo = new ArenaRepository(db);

        // Get the saved loadout
        ArenaRepository.LoadoutRecord record = repo.getLoadoutById(loadoutId);
        if (record == null) {
            throw new InvalidCommandException("Loadout not found with ID: " + loadoutId);
        }

        STSArena.logger.info("Arena command: Using saved loadout '" + record.name +
            "' (ID=" + loadoutId + ") vs " + encounter);

        // Convert LoadoutRecord to GeneratedLoadout
        RandomLoadoutGenerator.GeneratedLoadout loadout =
            RandomLoadoutGenerator.fromSavedLoadout(record);

        // Start the arena fight
        ArenaRunner.startFight(loadout, encounter);
    }

    /**
     * Normalize an encounter name to match the correct case from LoadoutConfig.ENCOUNTERS.
     * CommunicationMod may lowercase commands, so we need case-insensitive lookup.
     * Also handles spaceless versions like "JawWorm" -> "Jaw Worm".
     *
     * @param rawEncounter The raw encounter name (possibly lowercase or without spaces)
     * @return The correctly-cased encounter name, or the original if not found
     */
    private static String normalizeEncounterName(String rawEncounter) {
        if (rawEncounter == null || rawEncounter.isEmpty()) {
            return rawEncounter;
        }

        String lowerRaw = rawEncounter.toLowerCase();
        String lowerRawNoSpaces = lowerRaw.replace(" ", "");

        // Search through all encounter categories
        for (LoadoutConfig.EncounterCategory category : LoadoutConfig.ENCOUNTER_CATEGORIES) {
            for (String encounter : category.encounters) {
                String lowerEnc = encounter.toLowerCase();
                String lowerEncNoSpaces = lowerEnc.replace(" ", "");
                if (lowerEnc.equals(lowerRaw) || lowerEncNoSpaces.equals(lowerRawNoSpaces)) {
                    STSArena.logger.info("ARENA: Normalized encounter '" + rawEncounter + "' -> '" + encounter + "'");
                    return encounter;
                }
            }
        }

        // Also check the flat ENCOUNTERS array (for any stragglers)
        for (String encounter : LoadoutConfig.ENCOUNTERS) {
            String lowerEnc = encounter.toLowerCase();
            String lowerEncNoSpaces = lowerEnc.replace(" ", "");
            if (lowerEnc.equals(lowerRaw) || lowerEncNoSpaces.equals(lowerRawNoSpaces)) {
                STSArena.logger.info("ARENA: Normalized encounter '" + rawEncounter + "' -> '" + encounter + "'");
                return encounter;
            }
        }

        // Not found - return as-is (will likely fail, but with a clear error)
        STSArena.logger.warn("ARENA: Unknown encounter name: " + rawEncounter);
        return rawEncounter;
    }

    /**
     * Normalize character name to match PlayerClass enum values.
     * Handles common aliases like "SILENT" -> "THE_SILENT".
     *
     * @param rawName The raw character name from user input
     * @return The normalized name that matches PlayerClass enum
     */
    private static String normalizeCharacterName(String rawName) {
        if (rawName == null) {
            return rawName;
        }
        // Handle common aliases
        switch (rawName.toUpperCase()) {
            case "SILENT":
                return "THE_SILENT";
            case "THESILENT":
                return "THE_SILENT";
            default:
                return rawName;
        }
    }
}
