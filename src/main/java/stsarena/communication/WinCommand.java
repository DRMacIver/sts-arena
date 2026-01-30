package stsarena.communication;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
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

        // Clear the action queue to prevent queued actions (like Gambling Chip's
        // GamblingChipAction) from firing after monsters are killed. Without this,
        // there's a race condition where combat starts, win is sent before Gambling
        // Chip's action executes, monsters die, then the action opens HAND_SELECT
        // on a dead combat.
        clearActionQueue();

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
     * by closing the screen directly. This is synchronous - the screen is closed
     * immediately in the current frame, unlike clicking the confirm button which
     * would only be processed on the next frame's update() call.
     */
    private static void dismissPendingCardSelections() {
        try {
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.HAND_SELECT
                    && AbstractDungeon.handCardSelectScreen != null
                    && !AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
                STSArena.logger.info("WIN command: Force-closing hand card selection (Gambling Chip etc.)");
                AbstractDungeon.closeCurrentScreen();
            }
        } catch (Exception e) {
            STSArena.logger.warn("WIN command: Error dismissing card selections: " + e.getMessage());
        }
    }

    /**
     * Clear the game's action queue to prevent queued actions from executing
     * after combat is forcefully ended.
     */
    private static void clearActionQueue() {
        if (AbstractDungeon.actionManager != null) {
            int cleared = AbstractDungeon.actionManager.actions.size();
            AbstractDungeon.actionManager.actions.clear();
            AbstractDungeon.actionManager.currentAction = null;
            if (cleared > 0) {
                STSArena.logger.info("WIN command: Cleared " + cleared + " queued actions");
            }
        }
    }
}
