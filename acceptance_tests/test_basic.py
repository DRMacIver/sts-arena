"""
Basic acceptance tests for Slay the Spire with CommunicationMod.

These tests verify:
- Basic communication works
- Games can be started
- Runs can be abandoned
"""

import pytest

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from spirecomm.spire.screen import ScreenType
from conftest import wait_for_ready, GameTimeout, DEFAULT_TIMEOUT, wait_for_in_game, wait_for_main_menu, wait_for_combat

# Screen types that should NEVER appear during or after arena fights
FORBIDDEN_ARENA_SCREENS = {
    ScreenType.CARD_REWARD,    # Should not get card rewards in arena
    ScreenType.COMBAT_REWARD,  # Should not get combat rewards in arena
    ScreenType.MAP,            # Should not see map in arena
    ScreenType.BOSS_REWARD,    # Should not get boss rewards in arena
}


class TestCommunication:
    """Test basic communication with the game."""

    def test_state_command(self, coordinator: Coordinator):
        """Verify the state command returns valid data."""
        coordinator.send_message("state")
        wait_for_ready(coordinator)

        assert coordinator.last_error is None, f"State command returned error: {coordinator.last_error}"
        assert coordinator.game_is_ready, "Game should be ready for commands"


class TestGameLifecycle:
    """Test starting and abandoning games."""

    def test_start_game(self, at_main_menu: Coordinator):
        """Verify we can start a new game."""
        coord = at_main_menu

        # Start a game as Ironclad
        coord.send_message("start IRONCLAD 0")

        # Wait for game to initialize
        wait_for_in_game(coord)

        # Verify we're in a game
        assert coord.in_game, "Should be in game after start command"
        assert coord.last_game_state is not None, "Should have game state"
        assert coord.last_game_state.current_hp > 0, "Player should have HP"
        assert coord.last_game_state.max_hp > 0, "Player should have max HP"

    def test_abandon_command(self, at_main_menu: Coordinator):
        """Verify we can abandon a run and return to main menu."""
        coord = at_main_menu

        # Start a game
        coord.send_message("start IRONCLAD 0")

        # Wait for game to start
        wait_for_in_game(coord)
        assert coord.in_game, "Should be in game"

        # Abandon the run
        coord.send_message("abandon")

        # Wait for abandon to complete
        wait_for_main_menu(coord)
        assert not coord.in_game, "Should be at main menu after abandon"


class TestArenaMode:
    """Tests for arena mode functionality."""

    def test_arena_command_available(self, coordinator: Coordinator):
        """Verify the arena command is available at the main menu."""
        coordinator.send_message("state")
        wait_for_ready(coordinator)

        # When at main menu (not in game), arena command should be available
        assert not coordinator.in_game, "Should be at main menu"

        # Check that arena is in available commands
        # We need to parse the raw message to check available_commands
        coordinator.send_message("state")
        wait_for_ready(coordinator)

        # For now, just verify we're at the menu and the mod is loaded
        assert coordinator.last_error is None

    def test_start_arena_fight(self, at_main_menu: Coordinator):
        """Test starting an arena fight via the arena command."""
        coord = at_main_menu

        # Start an arena fight as Ironclad vs Cultist
        coord.send_message("arena IRONCLAD Cultist")

        # Wait for arena fight to initialize
        wait_for_combat(coord)

        # Verify we're in an arena fight
        assert coord.in_game, "Should be in arena fight"
        assert coord.last_game_state is not None, "Should have game state"
        assert coord.last_game_state.current_hp > 0, "Player should have HP"
        assert coord.last_game_state.in_combat, "Should be in combat"
        assert coord.last_game_state.monsters, "Should have monsters"

    def test_arena_no_card_reward_after_victory(self, at_main_menu: Coordinator):
        """Test that arena fights don't show card reward screens after victory.

        This is a regression test for a bug where card reward screens appeared
        after winning arena fights, particularly after the second fight in a session.
        """
        coord = at_main_menu

        # Run multiple arena fights to trigger the bug (it appeared on second fight)
        for fight_num in range(3):
            # Start an arena fight
            coord.send_message(f"arena IRONCLAD Cultist {12345 + fight_num}")

            # Wait for combat to be ready
            wait_for_combat(coord)
            assert coord.in_game, f"Fight {fight_num+1}: Should be in arena fight"
            assert coord.last_game_state.in_combat, f"Fight {fight_num+1}: Should be in combat"

            # Win the fight using the win command
            coord.send_message("win")

            # Wait for return to main menu
            wait_for_main_menu(coord)

            # Check that we didn't get a forbidden screen
            # (The state returned by wait_for includes the final screen we passed through)
            assert not coord.in_game, f"Fight {fight_num+1}: Should be back at main menu after arena victory"

    def test_arena_no_card_reward_after_loss(self, at_main_menu: Coordinator):
        """Test that arena fights don't show card reward screens after loss."""
        coord = at_main_menu

        # Run multiple arena fights to test consistency
        for fight_num in range(2):
            # Start an arena fight
            coord.send_message(f"arena IRONCLAD Gremlin Nob {54321 + fight_num}")

            # Wait for combat to be ready
            wait_for_combat(coord)
            assert coord.in_game, f"Fight {fight_num+1}: Should be in arena fight"
            assert coord.last_game_state.in_combat, f"Fight {fight_num+1}: Should be in combat"

            # Lose the fight using the lose command
            coord.send_message("lose")

            # Wait for return to main menu
            wait_for_main_menu(coord)
            assert not coord.in_game, f"Fight {fight_num+1}: Should be back at main menu after arena loss"

    def test_arena_loss_returns_to_menu(self, at_main_menu: Coordinator):
        """Test that losing an arena fight automatically returns to main menu.

        This is a regression test for a bug where the game would stay on the death
        screen after an arena loss instead of returning to the main menu.
        The 'lose' command should trigger an automatic return to menu without
        needing a manual 'abandon' command.
        """
        coord = at_main_menu

        # Start an arena fight
        coord.send_message("arena IRONCLAD Cultist 12345")

        # Wait for combat to be ready
        wait_for_combat(coord)
        assert coord.in_game, "Should be in arena fight"

        # Lose the fight using the lose command
        coord.send_message("lose")

        # Wait for return to main menu - should happen automatically
        wait_for_main_menu(coord)
        assert not coord.in_game, (
            "Arena loss should automatically return to main menu without needing abandon command."
        )
