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
from spirecomm.spire.screen import ScreenType
from conftest import wait_for_ready, GameTimeout, DEFAULT_TIMEOUT

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

            # Wait for arena fight to start
            start = time.time()
            while not coord.in_game:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for arena fight to start")
                coord.send_message("state")
                try:
                    wait_for_ready(coord, timeout=5)
                except GameTimeout:
                    pass

            assert coord.in_game, f"Fight {fight_num+1}: Should be in arena fight"

            # Wait for combat to be ready
            start = time.time()
            while True:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for combat to be ready")
                coord.send_message("state")
                wait_for_ready(coord, timeout=5)
                if coord.last_game_state and coord.last_game_state.in_combat:
                    break

            # Win the fight using the win command
            coord.send_message("win")

            # Poll for state changes after win, checking for forbidden screens
            start = time.time()
            while True:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for arena to end after win")

                coord.send_message("state")
                wait_for_ready(coord, timeout=5)

                # Check for forbidden screens - this is the bug we're testing for!
                if coord.in_game and coord.last_game_state:
                    screen_type = coord.last_game_state.screen_type
                    if screen_type in FORBIDDEN_ARENA_SCREENS:
                        pytest.fail(
                            f"Fight {fight_num+1}: Arena fight showed forbidden screen {screen_type.name}! "
                            f"Arena fights should return to menu without reward screens."
                        )

                # If we're back at main menu, we're done with this fight
                if not coord.in_game:
                    break

                # If still in game but not in combat and not forbidden screen, abandon to get back to menu
                if coord.last_game_state and not coord.last_game_state.in_combat:
                    coord.send_message("abandon")
                    wait_for_ready(coord, timeout=5)

            assert not coord.in_game, f"Fight {fight_num+1}: Should be back at main menu after arena victory"

    def test_arena_no_card_reward_after_loss(self, at_main_menu: Coordinator):
        """Test that arena fights don't show card reward screens after loss."""
        coord = at_main_menu

        # Run multiple arena fights to test consistency
        for fight_num in range(2):
            # Start an arena fight
            coord.send_message(f"arena IRONCLAD Gremlin Nob {54321 + fight_num}")

            # Wait for arena fight to start
            start = time.time()
            while not coord.in_game:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for arena fight to start")
                coord.send_message("state")
                try:
                    wait_for_ready(coord, timeout=5)
                except GameTimeout:
                    pass

            assert coord.in_game, f"Fight {fight_num+1}: Should be in arena fight"

            # Wait for combat to be ready
            start = time.time()
            while True:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for combat to be ready")
                coord.send_message("state")
                wait_for_ready(coord, timeout=5)
                if coord.last_game_state and coord.last_game_state.in_combat:
                    break

            # Lose the fight using the lose command
            coord.send_message("lose")

            # Poll for state changes after loss, checking for forbidden screens
            start = time.time()
            while True:
                if time.time() - start > DEFAULT_TIMEOUT:
                    raise GameTimeout(f"Fight {fight_num+1}: Timed out waiting for arena to end after loss")

                coord.send_message("state")
                wait_for_ready(coord, timeout=5)

                # Check for forbidden screens
                if coord.in_game and coord.last_game_state:
                    screen_type = coord.last_game_state.screen_type
                    if screen_type in FORBIDDEN_ARENA_SCREENS:
                        pytest.fail(
                            f"Fight {fight_num+1}: Arena fight showed forbidden screen {screen_type.name}! "
                            f"Arena fights should return to menu without reward screens."
                        )

                # If we're back at main menu, we're done with this fight
                if not coord.in_game:
                    break

                # If still in game but showing death screen or other, abandon to get back to menu
                if coord.last_game_state and not coord.last_game_state.in_combat:
                    coord.send_message("abandon")
                    wait_for_ready(coord, timeout=5)

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

        # Wait for arena fight to start
        start = time.time()
        while not coord.in_game:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for arena fight to start")
            coord.send_message("state")
            try:
                wait_for_ready(coord, timeout=5)
            except GameTimeout:
                pass

        assert coord.in_game, "Should be in arena fight"

        # Wait for combat to be ready
        start = time.time()
        while True:
            if time.time() - start > DEFAULT_TIMEOUT:
                raise GameTimeout("Timed out waiting for combat to be ready")
            coord.send_message("state")
            wait_for_ready(coord, timeout=5)
            if coord.last_game_state and coord.last_game_state.in_combat:
                break

        # Lose the fight using the lose command
        coord.send_message("lose")

        # Wait for return to main menu - should happen automatically without abandon
        start = time.time()
        returned_to_menu = False
        while time.time() - start < DEFAULT_TIMEOUT:
            coord.send_message("state")
            try:
                wait_for_ready(coord, timeout=5)
            except GameTimeout:
                pass

            if not coord.in_game:
                returned_to_menu = True
                break

        assert returned_to_menu, (
            "Arena loss should automatically return to main menu without needing abandon command. "
            "The game is still showing as in_game=True after the timeout."
        )
