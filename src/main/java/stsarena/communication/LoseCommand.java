package stsarena.communication;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import communicationmod.CommandExecutor;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;

/**
 * CommunicationMod command extension for forcing a loss.
 *
 * Usage: lose
 *
 * This command immediately kills the player, ending the current combat with a loss.
 * Useful for testing defeat scenarios without waiting for monsters to kill the player.
 */
public class LoseCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the lose command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new LoseCommand());
            STSArena.logger.info("Registered lose command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, lose command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "lose";
    }

    @Override
    public boolean isAvailable() {
        // Lose command is available only during combat
        return CommandExecutor.isInDungeon() && AbstractDungeon.getCurrRoom() != null
            && AbstractDungeon.getCurrRoom().phase == com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMBAT
            && AbstractDungeon.player != null
            && !AbstractDungeon.player.isDead;
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (AbstractDungeon.player == null) {
            throw new InvalidCommandException("No player found");
        }

        if (AbstractDungeon.player.isDead) {
            throw new InvalidCommandException("Player is already dead");
        }

        STSArena.logger.info("LOSE command: Forcing player death");

        // Deal enough damage to kill the player
        // Using loseBlock = false so block doesn't prevent death
        // Using selfDamage = false as this is not from the player
        int overkillDamage = AbstractDungeon.player.currentHealth + 999;
        AbstractDungeon.player.damage(
            new com.megacrit.cardcrawl.cards.DamageInfo(
                null,  // source (null = no source)
                overkillDamage,
                com.megacrit.cardcrawl.cards.DamageInfo.DamageType.HP_LOSS  // HP_LOSS bypasses block
            )
        );

        STSArena.logger.info("LOSE command: Player death triggered");
    }
}
