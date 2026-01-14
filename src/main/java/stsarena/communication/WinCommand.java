package stsarena.communication;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.screens.select.HandCardSelectScreen;
import communicationmod.CommunicationMod;
import communicationmod.CommandExecutor;
import communicationmod.GameStateListener;
import communicationmod.InvalidCommandException;
import stsarena.STSArena;

/**
 * CommunicationMod command extension for forcing a win.
 *
 * Usage: win
 *
 * This command immediately kills all monsters, ending the current combat with a win.
 * Useful for testing victory scenarios without playing through the entire fight.
 */
public class WinCommand implements CommandExecutor.CommandExtension {

    /**
     * Register the win command with CommunicationMod.
     * Call this during mod initialization.
     */
    public static void register() {
        try {
            CommandExecutor.registerCommand(new WinCommand());
            STSArena.logger.info("Registered win command with CommunicationMod");
        } catch (NoClassDefFoundError e) {
            STSArena.logger.info("CommunicationMod not loaded, win command not registered");
        }
    }

    @Override
    public String getCommandName() {
        return "win";
    }

    @Override
    public boolean isAvailable() {
        // Win command is available only during combat with living monsters
        return CommandExecutor.isInDungeon() && AbstractDungeon.getCurrRoom() != null
            && AbstractDungeon.getCurrRoom().phase == com.megacrit.cardcrawl.rooms.AbstractRoom.RoomPhase.COMBAT
            && AbstractDungeon.getMonsters() != null
            && !AbstractDungeon.getMonsters().areMonstersBasicallyDead();
    }

    @Override
    public void execute(String[] tokens) throws InvalidCommandException {
        if (AbstractDungeon.getMonsters() == null) {
            throw new InvalidCommandException("No monsters found");
        }

        // Dismiss any pending card selection screens (e.g., Gambling Chip)
        // These block combat from proceeding properly
        dismissPendingCardSelections();

        STSArena.logger.info("WIN command: Killing all monsters");

        // Kill all monsters
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDead && !m.isDying) {
                // Deal enough damage to kill the monster
                m.damage(new com.megacrit.cardcrawl.cards.DamageInfo(
                    AbstractDungeon.player,
                    m.currentHealth + 999,
                    com.megacrit.cardcrawl.cards.DamageInfo.DamageType.HP_LOSS
                ));
            }
        }

        STSArena.logger.info("WIN command: All monsters killed");

        // Signal ready for next command and trigger a state response
        GameStateListener.signalReadyForCommand();
        CommunicationMod.publishOnGameStateChange();
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
                STSArena.logger.info("WIN command: Dismissing pending hand card selection");
                // Clear the selection without choosing cards
                AbstractDungeon.handCardSelectScreen.selectedCards.clear();
                AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
                AbstractDungeon.overlayMenu.cancelButton.hide();
            }

            // Check for grid card select screen
            if (AbstractDungeon.gridSelectScreen != null &&
                AbstractDungeon.gridSelectScreen.selectedCards != null &&
                !AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
                STSArena.logger.info("WIN command: Clearing pending grid selection");
                AbstractDungeon.gridSelectScreen.selectedCards.clear();
            }

            // Close any currently open screen
            if (AbstractDungeon.screen != null &&
                AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT) {
                STSArena.logger.info("WIN command: Closing hand select screen");
                AbstractDungeon.closeCurrentScreen();
            }
        } catch (Exception e) {
            STSArena.logger.warn("WIN command: Error dismissing card selections: " + e.getMessage());
            // Continue anyway - the win command should still work
        }
    }
}
