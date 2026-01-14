package stsarena.communication;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;
import stsarena.arena.ArenaRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // Dismiss any pending card selection screens (e.g., Gambling Chip)
        // These block combat from proceeding properly
        dismissPendingCardSelections();

        STSArena.logger.info("LOSE command: Forcing player death directly");

        // Clear damage-preventing powers
        clearDamagePreventingPowers();

        int hpBefore = AbstractDungeon.player.currentHealth;
        STSArena.logger.info("LOSE command: HP before: " + hpBefore);

        // Deal enough damage to kill the player through normal game mechanics
        // This ensures the DeathScreen is shown properly (with arena retry buttons if in arena)
        int damageNeeded = AbstractDungeon.player.currentHealth + AbstractDungeon.player.currentBlock + 999;
        STSArena.logger.info("LOSE command: Dealing " + damageNeeded + " damage to player");

        AbstractDungeon.player.damage(new com.megacrit.cardcrawl.cards.DamageInfo(
            null,  // null source for environmental damage
            damageNeeded,
            com.megacrit.cardcrawl.cards.DamageInfo.DamageType.HP_LOSS
        ));

        STSArena.logger.info("LOSE command: Player death triggered, DeathScreen will show");

        // Signal ready for next command and trigger a state response
        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
    }

    /**
     * Clear powers that prevent damage, such as Intangible and Buffer.
     * This ensures the LOSE command can kill the player even with defensive powers active.
     */
    private static void clearDamagePreventingPowers() {
        if (AbstractDungeon.player == null || AbstractDungeon.player.powers == null) {
            return;
        }

        // List of power IDs that prevent or significantly reduce damage
        List<String> damagePreventingPowerIds = Arrays.asList(
            "Intangible",    // Reduces all damage to 1
            "Buffer",        // Blocks one instance of damage
            "Invincible",    // Boss/elite damage cap (shouldn't be on player, but just in case)
            "IntangiblePlayer"  // Player-specific variant used by some mods
        );

        // Remove these powers from the player
        List<AbstractPower> powersToRemove = new ArrayList<>();
        for (AbstractPower power : AbstractDungeon.player.powers) {
            if (damagePreventingPowerIds.contains(power.ID)) {
                STSArena.logger.info("LOSE command: Removing damage-preventing power: " + power.ID);
                powersToRemove.add(power);
            }
        }

        for (AbstractPower power : powersToRemove) {
            AbstractDungeon.player.powers.remove(power);
        }

        // Also clear block to ensure HP_LOSS isn't blocked
        if (AbstractDungeon.player.currentBlock > 0) {
            STSArena.logger.info("LOSE command: Clearing " + AbstractDungeon.player.currentBlock + " block");
            AbstractDungeon.player.loseBlock();
        }
    }

    /**
     * Dismiss any pending card selection screens (Gambling Chip, Watcher cards, etc.)
     * that might block combat from proceeding.
     */
    private static void dismissPendingCardSelections() {
        try {
            // Check for hand card select screen (used by Gambling Chip, etc.)
            if (AbstractDungeon.handCardSelectScreen != null &&
                AbstractDungeon.handCardSelectScreen.wereCardsRetrieved == false) {
                STSArena.logger.info("LOSE command: Dismissing pending hand card selection");
                // Clear the selection without choosing cards
                AbstractDungeon.handCardSelectScreen.selectedCards.clear();
                AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
                AbstractDungeon.overlayMenu.cancelButton.hide();
            }

            // Check for grid card select screen
            if (AbstractDungeon.gridSelectScreen != null &&
                AbstractDungeon.gridSelectScreen.selectedCards != null &&
                !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
                STSArena.logger.info("LOSE command: Clearing pending grid selection");
                AbstractDungeon.gridSelectScreen.selectedCards.clear();
            }

            // Close any currently open screen
            if (AbstractDungeon.screen != null &&
                AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT) {
                STSArena.logger.info("LOSE command: Closing hand select screen");
                AbstractDungeon.closeCurrentScreen();
            }
        } catch (Exception e) {
            STSArena.logger.warn("LOSE command: Error dismissing card selections: " + e.getMessage());
            // Continue anyway - the lose command should still work
        }
    }
}
