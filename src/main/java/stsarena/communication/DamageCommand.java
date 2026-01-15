package stsarena.communication;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

/**
 * CommunicationMod command extension for dealing damage to the player.
 *
 * Usage: damage [amount]
 *
 * This command deals damage to the player, useful for testing imperfect victory
 * scenarios where the player needs to take damage before winning.
 *
 * If amount is not specified, defaults to 1 damage.
 */
public class DamageCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the damage command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new DamageCommand());
            STSArena.logger.info("Registered damage command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, damage command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "damage";
    }

    @Override
    public boolean isAvailable() {
        // Damage command is available only during combat
        return CommandExecutor.isInDungeon() && AbstractDungeon.getCurrRoom() != null
            && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT
            && AbstractDungeon.player != null
            && !AbstractDungeon.player.isDead;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (AbstractDungeon.player == null) {
            throw new InvalidCommandException("No player found");
        }

        // Default to 1 damage if not specified
        int amount = 1;
        if (tokens.length >= 2) {
            try {
                amount = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Invalid damage amount: " + tokens[1]);
            }
        }

        if (amount <= 0) {
            throw new InvalidCommandException("Damage amount must be positive");
        }

        STSArena.logger.info("DAMAGE command: Dealing " + amount + " damage to player");

        // Deal damage to player using HP_LOSS type (bypasses block)
        AbstractDungeon.player.damage(new com.megacrit.cardcrawl.cards.DamageInfo(
            null, // No source
            amount,
            com.megacrit.cardcrawl.cards.DamageInfo.DamageType.HP_LOSS
        ));

        // Record that damage was taken for imperfect victory detection
        ArenaRunner.recordDamageTaken();

        STSArena.logger.info("DAMAGE command: Player HP is now " + AbstractDungeon.player.currentHealth);

        // Signal ready for next command and trigger a state response
        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
    }
}
