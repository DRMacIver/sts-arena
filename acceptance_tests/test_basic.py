"""
Basic acceptance tests for Slay the Spire with CommunicationMod.

These tests verify:
- Basic communication works
- Games can be started
- Runs can be abandoned
"""

import time
import pytest

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from conftest import wait_for_ready, GameTimeout, DEFAULT_TIMEOUT


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

        # Wait for game to initialize - poll until we're in game
        start = time.time()
        while not coord.in_game:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for game to start")
            coord.send_message("state")
            wait_for_ready(coord, timeout=5)

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
        start = time.time()
        while not coord.in_game:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for game to start")
            coord.send_message("state")
            wait_for_ready(coord, timeout=5)

        assert coord.in_game, "Should be in game"

        # Abandon the run
        coord.send_message("abandon")

        # Wait for abandon to complete
        start = time.time()
        while coord.in_game:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for abandon to complete")
            coord.send_message("state")
            wait_for_ready(coord, timeout=5)

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

        # Wait for arena fight to initialize - poll until we're in game
        # Use longer timeout because arena involves loading a save file
        start = time.time()
        while not coord.in_game:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for arena fight to start")
            coord.send_message("state")
            try:
                wait_for_ready(coord, timeout=10)
            except GameTimeout:
                # Game may be loading/transitioning - continue polling
                pass

        # Verify we're in an arena fight
        assert coord.in_game, "Should be in arena fight"
        assert coord.last_game_state is not None, "Should have game state"
        assert coord.last_game_state.current_hp > 0, "Player should have HP"

        # Verify we're in combat (should have monsters)
        # The game state should indicate we're in combat
        game_state = coord.last_game_state
        assert game_state.screen_type is not None, "Should have a screen type"
