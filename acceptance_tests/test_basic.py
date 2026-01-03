"""
Basic acceptance tests for Slay the Spire with CommunicationMod.

These tests verify:
- Basic communication works
- Games can be started
- Runs can be abandoned
"""

import time
import pytest

from communicator import GameCommunicator


class TestCommunication:
    """Test basic communication with the game."""

    def test_state_command(self, game: GameCommunicator):
        """Verify the state command returns valid data."""
        state = game.execute("state")

        assert state.error is None, f"State command returned error: {state.error}"
        assert state.ready_for_command, "Game should be ready for commands"

    def test_available_commands_at_main_menu(self, at_main_menu: GameCommunicator):
        """Verify expected commands are available at main menu."""
        state = at_main_menu.execute("state")

        assert "start" in state.available_commands, \
            f"Expected 'start' command at main menu, got: {state.available_commands}"
        assert "state" in state.available_commands


class TestGameLifecycle:
    """Test starting and abandoning games."""

    def test_start_game(self, at_main_menu: GameCommunicator):
        """Verify we can start a new game."""
        game = at_main_menu

        # Start a game as Ironclad
        state = game.execute("start ironclad 0")
        assert state.error is None, f"Start command failed: {state.error}"

        # Wait for game to initialize
        time.sleep(2.0)

        # Verify we're in a game
        state = game.execute("state")
        assert state.in_game, "Should be in game after start command"
        assert state.current_hp > 0, "Player should have HP"
        assert state.max_hp > 0, "Player should have max HP"

    def test_abandon_command(self, at_main_menu: GameCommunicator):
        """Verify we can abandon a run and return to main menu."""
        game = at_main_menu

        # Start a game
        game.execute("start ironclad 0")
        time.sleep(2.0)

        state = game.execute("state")
        assert state.in_game, "Should be in game"

        # Abandon the run
        assert game.abandon_run(), "Abandon should succeed"

        # Verify we're back at main menu
        state = game.execute("state")
        assert not state.in_game, "Should be at main menu after abandon"


class TestArenaMode:
    """Placeholder tests for arena mode functionality."""

    @pytest.mark.skip(reason="Arena mode tests not yet implemented")
    def test_navigate_to_arena(self, at_main_menu: GameCommunicator):
        """Test navigating to arena mode from main menu."""
        pass

    @pytest.mark.skip(reason="Arena mode tests not yet implemented")
    def test_start_arena_fight(self, at_main_menu: GameCommunicator):
        """Test starting an arena fight."""
        pass
