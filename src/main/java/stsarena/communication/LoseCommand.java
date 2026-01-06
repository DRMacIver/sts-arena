package stsarena.communication;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
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

        STSArena.logger.info("LOSE command: Forcing player death via action queue");

        // Queue the death as a top-priority action to ensure it's processed
        // during the normal game loop, avoiding timing issues with direct damage.
        // This action keeps trying until the player actually dies, which handles
        // death-prevention relics like Fairy in a Bottle and Lizard Tail.
        AbstractDungeon.actionManager.addToTop(new AbstractGameAction() {
            private int attempts = 0;
            private static final int MAX_ATTEMPTS = 5;

            @Override
            public void update() {
                if (AbstractDungeon.player == null) {
                    STSArena.logger.info("LOSE command: No player, action done");
                    this.isDone = true;
                    return;
                }

                if (AbstractDungeon.player.isDead) {
                    STSArena.logger.info("LOSE command: Player is dead after " + attempts + " attempts");
                    this.isDone = true;
                    return;
                }

                if (attempts >= MAX_ATTEMPTS) {
                    STSArena.logger.warn("LOSE command: Max attempts reached, player still alive with HP="
                        + AbstractDungeon.player.currentHealth);
                    this.isDone = true;
                    return;
                }

                attempts++;
                int overkillDamage = AbstractDungeon.player.currentHealth + 999;
                STSArena.logger.info("LOSE command: Attempt " + attempts + ", dealing " + overkillDamage + " damage");

                AbstractDungeon.player.damage(
                    new com.megacrit.cardcrawl.cards.DamageInfo(
                        null,  // source (null = no source)
                        overkillDamage,
                        com.megacrit.cardcrawl.cards.DamageInfo.DamageType.HP_LOSS  // HP_LOSS bypasses block
                    )
                );

                // If player is still alive (death prevented), the action will run again
                if (!AbstractDungeon.player.isDead) {
                    STSArena.logger.info("LOSE command: Player survived with HP="
                        + AbstractDungeon.player.currentHealth + ", will try again");
                    // Don't set isDone = true, so the action runs again
                } else {
                    STSArena.logger.info("LOSE command: Player died on attempt " + attempts);
                    this.isDone = true;
                }
            }
        });

        STSArena.logger.info("LOSE command: Death action queued");
    }
}
