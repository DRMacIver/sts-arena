package stsarena.communication;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import communicationmod.CommandExecutor;
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
    }
}
