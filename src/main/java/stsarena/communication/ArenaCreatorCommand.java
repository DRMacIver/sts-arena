package stsarena.communication;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.screens.LoadoutCreatorScreen;

/**
 * CommunicationMod command for manipulating the loadout creator screen.
 *
 * Usage:
 *   arena_creator add_card <card_id>   - Add a card to the deck
 *   arena_creator add_relic <relic_id> - Add a relic
 *   arena_creator set_hp <current> <max> - Set HP values
 *   arena_creator set_asc <level>      - Set ascension level
 *
 * This command is primarily used for documentation screenshot generation.
 */
public class ArenaCreatorCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the arena_creator command with CommunicationMod.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new ArenaCreatorCommand());
            STSArena.logger.info("Registered arena_creator command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, arena_creator command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "arena_creator";
    }

    @Override
    public boolean isAvailable() {
        // Available when the loadout creator screen is open
        return STSArena.loadoutCreatorScreen != null && STSArena.loadoutCreatorScreen.isOpen;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(
                "Usage: arena_creator <add_card|add_relic|set_hp|set_asc> [args]\n" +
                "  add_card <card_id>     - Add a card to the deck\n" +
                "  add_relic <relic_id>   - Add a relic\n" +
                "  set_hp <current> <max> - Set HP values\n" +
                "  set_asc <level>        - Set ascension level");
        }

        LoadoutCreatorScreen creator = STSArena.loadoutCreatorScreen;
        if (creator == null || !creator.isOpen) {
            throw new InvalidCommandException("Loadout creator screen is not open");
        }

        String subCommand = tokens[1].toLowerCase();

        switch (subCommand) {
            case "add_card":
                if (tokens.length < 3) {
                    throw new InvalidCommandException("Usage: arena_creator add_card <card_id>");
                }
                addCard(creator, tokens[2]);
                break;

            case "add_relic":
                if (tokens.length < 3) {
                    throw new InvalidCommandException("Usage: arena_creator add_relic <relic_id>");
                }
                addRelic(creator, tokens[2]);
                break;

            case "set_hp":
                if (tokens.length < 4) {
                    throw new InvalidCommandException("Usage: arena_creator set_hp <current> <max>");
                }
                setHp(creator, tokens[2], tokens[3]);
                break;

            case "set_asc":
                if (tokens.length < 3) {
                    throw new InvalidCommandException("Usage: arena_creator set_asc <level>");
                }
                setAscension(creator, tokens[2]);
                break;

            default:
                throw new InvalidCommandException("Unknown subcommand: " + subCommand);
        }

        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
    }

    private void addCard(LoadoutCreatorScreen creator, String cardId) throws InvalidCommandException {
        // Card IDs are case-sensitive in the game - try to find the card
        AbstractCard card = CardLibrary.getCard(cardId);

        // If not found, try common capitalizations
        if (card == null) {
            // Try Title Case (e.g., "strike" -> "Strike")
            String titleCase = cardId.substring(0, 1).toUpperCase() + cardId.substring(1).toLowerCase();
            card = CardLibrary.getCard(titleCase);
        }

        if (card == null) {
            // Try with underscore handling (e.g., "body_slam" -> "Body Slam")
            String withSpaces = cardId.replace("_", " ");
            String[] words = withSpaces.split(" ");
            StringBuilder titleCaseBuilder = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (i > 0) titleCaseBuilder.append(" ");
                if (words[i].length() > 0) {
                    titleCaseBuilder.append(words[i].substring(0, 1).toUpperCase());
                    if (words[i].length() > 1) {
                        titleCaseBuilder.append(words[i].substring(1).toLowerCase());
                    }
                }
            }
            card = CardLibrary.getCard(titleCaseBuilder.toString());
        }

        if (card == null) {
            throw new InvalidCommandException("Card not found: " + cardId);
        }

        creator.addCardFromCommand(card.makeCopy());
        STSArena.logger.info("ARENA_CREATOR: Added card " + card.cardID);
    }

    private void addRelic(LoadoutCreatorScreen creator, String relicId) throws InvalidCommandException {
        AbstractRelic relic = RelicLibrary.getRelic(relicId);

        // Try common capitalizations if not found
        if (relic == null) {
            String titleCase = relicId.substring(0, 1).toUpperCase() + relicId.substring(1).toLowerCase();
            relic = RelicLibrary.getRelic(titleCase);
        }

        if (relic == null) {
            throw new InvalidCommandException("Relic not found: " + relicId);
        }

        creator.addRelicFromCommand(relic.makeCopy());
        STSArena.logger.info("ARENA_CREATOR: Added relic " + relic.relicId);
    }

    private void setHp(LoadoutCreatorScreen creator, String currentStr, String maxStr) throws InvalidCommandException {
        try {
            int current = Integer.parseInt(currentStr);
            int max = Integer.parseInt(maxStr);
            if (current < 1 || max < 1 || current > max) {
                throw new InvalidCommandException("Invalid HP values: current=" + current + ", max=" + max);
            }
            creator.setHpFromCommand(current, max);
            STSArena.logger.info("ARENA_CREATOR: Set HP to " + current + "/" + max);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid HP values: " + currentStr + "/" + maxStr);
        }
    }

    private void setAscension(LoadoutCreatorScreen creator, String levelStr) throws InvalidCommandException {
        try {
            int level = Integer.parseInt(levelStr);
            if (level < 0 || level > 20) {
                throw new InvalidCommandException("Invalid ascension level: " + level + " (must be 0-20)");
            }
            creator.setAscensionFromCommand(level);
            STSArena.logger.info("ARENA_CREATOR: Set ascension to " + level);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid ascension level: " + levelStr);
        }
    }
}
