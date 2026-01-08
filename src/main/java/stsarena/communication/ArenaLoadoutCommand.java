package stsarena.communication;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.data.ArenaDatabase;
import stsarena.data.ArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * CommunicationMod command extension for managing arena loadouts.
 *
 * Usage:
 *   arena-loadout list                    - List all saved loadouts
 *   arena-loadout info <id>               - Get detailed info about a loadout
 *   arena-loadout rename <id> <new-name>  - Rename a loadout
 *   arena-loadout delete <id>             - Delete a loadout
 *   arena-loadout delete-all              - Delete all loadouts (for testing)
 *
 * This command provides external control over saved loadouts for testing
 * and automation purposes.
 */
public class ArenaLoadoutCommand implements CommandExecutor.CommandExtension {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Register the arena-loadout command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaLoadoutCommand());
            STSArena.logger.info("Registered arena-loadout command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena-loadout command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena-loadout";
    }

    @Override
    public boolean isAvailable() {
        // Loadout management is always available (doesn't require being in/out of dungeon)
        return true;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(
                "Usage: arena-loadout <list|info|rename|delete|delete-all> [args]\n" +
                "  list              - List all saved loadouts\n" +
                "  info <id>         - Get detailed info about a loadout\n" +
                "  rename <id> <name> - Rename a loadout\n" +
                "  delete <id>       - Delete a loadout\n" +
                "  delete-all        - Delete all loadouts (for testing)");
        }

        String subCommand = tokens[1].toLowerCase();
        ArenaRepository repo = getRepository();

        if (repo == null) {
            throw new InvalidCommandException("Database not available");
        }

        switch (subCommand) {
            case "list":
                executeList(repo);
                break;
            case "info":
                executeInfo(tokens, repo);
                break;
            case "rename":
                executeRename(tokens, repo);
                break;
            case "delete":
                executeDelete(tokens, repo);
                break;
            case "delete-all":
                executeDeleteAll(repo);
                break;
            default:
                throw new InvalidCommandException("Unknown subcommand: " + subCommand +
                    ". Valid subcommands: list, info, rename, delete, delete-all");
        }

        // Trigger a state response so the client knows the command completed
        // Call publishOnGameStateChange directly to ensure response is sent immediately
        CommunicationMod.publishOnGameStateChange();
    }

    private ArenaRepository getRepository() {
        ArenaDatabase db = ArenaDatabase.getInstance();
        if (db == null) {
            return null;
        }
        return new ArenaRepository(db);
    }

    /**
     * List all saved loadouts.
     * Output is JSON for easy parsing by tests/automation.
     * The list is returned in the "message" field of the response.
     */
    private void executeList(ArenaRepository repo) {
        List<ArenaRepository.LoadoutRecord> loadouts = repo.getLoadouts(100);

        List<LoadoutSummary> summaries = new ArrayList<>();
        for (ArenaRepository.LoadoutRecord loadout : loadouts) {
            LoadoutSummary summary = new LoadoutSummary();
            summary.id = loadout.dbId;
            summary.uuid = loadout.uuid;
            summary.name = loadout.name;
            summary.characterClass = loadout.characterClass;
            summary.ascensionLevel = loadout.ascensionLevel;
            summary.maxHp = loadout.maxHp;
            summary.currentHp = loadout.currentHp;
            summary.isFavorite = loadout.isFavorite;
            summary.createdAt = loadout.createdAt;
            summaries.add(summary);
        }

        String jsonList = gson.toJson(summaries);
        STSArena.logger.info("ARENA-LOADOUT LIST: Found " + summaries.size() + " loadouts");

        // Set the message so it's returned in the CommunicationMod response
        GameStateListener.setMessage(jsonList);
    }

    /**
     * Get detailed info about a specific loadout.
     */
    private void executeInfo(String[] tokens, ArenaRepository repo) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException("Usage: arena-loadout info <id>");
        }

        long loadoutId;
        try {
            loadoutId = Long.parseLong(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid loadout ID: " + tokens[2]);
        }

        ArenaRepository.LoadoutRecord loadout = repo.getLoadoutById(loadoutId);
        if (loadout == null) {
            throw new InvalidCommandException("Loadout not found with ID: " + loadoutId);
        }

        LoadoutInfo info = new LoadoutInfo();
        info.id = loadout.dbId;
        info.uuid = loadout.uuid;
        info.name = loadout.name;
        info.characterClass = loadout.characterClass;
        info.ascensionLevel = loadout.ascensionLevel;
        info.maxHp = loadout.maxHp;
        info.currentHp = loadout.currentHp;
        info.potionSlots = loadout.potionSlots;
        info.isFavorite = loadout.isFavorite;
        info.createdAt = loadout.createdAt;
        info.contentHash = loadout.contentHash;
        info.deckJson = loadout.deckJson;
        info.relicsJson = loadout.relicsJson;
        info.potionsJson = loadout.potionsJson;

        STSArena.logger.info("ARENA-LOADOUT INFO: " + gson.toJson(info));
    }

    /**
     * Rename a loadout.
     */
    private void executeRename(String[] tokens, ArenaRepository repo) throws InvalidCommandException {
        if (tokens.length < 4) {
            throw new InvalidCommandException("Usage: arena-loadout rename <id> <new-name>");
        }

        long loadoutId;
        try {
            loadoutId = Long.parseLong(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid loadout ID: " + tokens[2]);
        }

        // Combine remaining tokens for name (may have spaces)
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 3; i < tokens.length; i++) {
            if (i > 3) nameBuilder.append(" ");
            nameBuilder.append(tokens[i]);
        }
        String newName = nameBuilder.toString();

        if (newName.isEmpty()) {
            throw new InvalidCommandException("New name cannot be empty");
        }

        ArenaRepository.LoadoutRecord existing = repo.getLoadoutById(loadoutId);
        if (existing == null) {
            throw new InvalidCommandException("Loadout not found with ID: " + loadoutId);
        }

        boolean success = repo.renameLoadout(loadoutId, newName);
        if (success) {
            STSArena.logger.info("ARENA-LOADOUT RENAME: Renamed loadout " + loadoutId + " to '" + newName + "'");
        } else {
            throw new InvalidCommandException("Failed to rename loadout");
        }
    }

    /**
     * Delete a loadout.
     */
    private void executeDelete(String[] tokens, ArenaRepository repo) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException("Usage: arena-loadout delete <id>");
        }

        long loadoutId;
        try {
            loadoutId = Long.parseLong(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid loadout ID: " + tokens[2]);
        }

        ArenaRepository.LoadoutRecord existing = repo.getLoadoutById(loadoutId);
        if (existing == null) {
            throw new InvalidCommandException("Loadout not found with ID: " + loadoutId);
        }

        boolean success = repo.deleteLoadout(loadoutId);
        if (success) {
            STSArena.logger.info("ARENA-LOADOUT DELETE: Deleted loadout " + loadoutId + " ('" + existing.name + "')");
        } else {
            throw new InvalidCommandException("Failed to delete loadout");
        }
    }

    /**
     * Delete all loadouts.
     * This is primarily for testing/screenshot generation to start with a clean slate.
     */
    private void executeDeleteAll(ArenaRepository repo) throws InvalidCommandException {
        List<ArenaRepository.LoadoutRecord> loadouts = repo.getLoadouts(1000);

        int deleted = 0;
        for (ArenaRepository.LoadoutRecord loadout : loadouts) {
            if (repo.deleteLoadout(loadout.dbId)) {
                deleted++;
            }
        }

        STSArena.logger.info("ARENA-LOADOUT DELETE-ALL: Deleted " + deleted + " loadouts");
        GameStateListener.setMessage("Deleted " + deleted + " loadouts");
        GameStateListener.signalReadyForCommand();
    }

    /**
     * Summary of a loadout for list output.
     */
    private static class LoadoutSummary {
        public long id;
        public String uuid;
        public String name;
        public String characterClass;
        public int ascensionLevel;
        public int maxHp;
        public int currentHp;
        public boolean isFavorite;
        public long createdAt;
    }

    /**
     * Detailed info about a loadout.
     */
    private static class LoadoutInfo {
        public long id;
        public String uuid;
        public String name;
        public String characterClass;
        public int ascensionLevel;
        public int maxHp;
        public int currentHp;
        public int potionSlots;
        public boolean isFavorite;
        public long createdAt;
        public String contentHash;
        public String deckJson;
        public String relicsJson;
        public String potionsJson;
    }
}
