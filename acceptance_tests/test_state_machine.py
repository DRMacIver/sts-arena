"""
Concrete acceptance tests for state machine debugging.

These tests verify specific state transitions and invariants in isolation,
making it easier to debug issues than the randomized Hypothesis tests.

Each test targets a specific aspect of the game state machine:
- State transitions (menu -> arena -> combat -> menu)
- Error handling for invalid state transitions
- Invariants that should always hold
- Edge cases and timing issues
"""

import pytest
import time

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from spirecomm.spire.screen import ScreenType

from conftest import (
    wait_for_ready,
    wait_for_stable,
    wait_for_in_game,
    wait_for_main_menu,
    wait_for_combat,
    GameTimeout,
    DEFAULT_TIMEOUT,
    drain_pending_messages,
)


# =============================================================================
# State Transition Tests
# =============================================================================

class TestStateTransitions:
    """Tests for specific state transitions that have been problematic."""

    def test_menu_to_arena_to_menu_via_win(self, at_main_menu: Coordinator):
        """Test the complete arena lifecycle: menu -> arena -> win -> menu."""
        coord = at_main_menu

        # Verify starting state
        assert not coord.in_game, "Should start at main menu"

        # Transition: menu -> arena combat
        coord.send_message("arena IRONCLAD Cultist 10001")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Verify arena state
        assert coord.in_game, "Should be in game after arena start"
        assert coord.last_game_state is not None, "Should have game state"
        assert coord.last_game_state.in_combat, "Should be in combat"
        assert coord.last_game_state.character == PlayerClass.IRONCLAD

        # Transition: arena combat -> win -> menu
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Verify back at menu
        assert not coord.in_game, "Should be at menu after win"

    def test_menu_to_arena_to_menu_via_lose(self, at_main_menu: Coordinator):
        """Test arena lifecycle with loss: menu -> arena -> lose -> menu."""
        coord = at_main_menu

        # Transition: menu -> arena combat
        coord.send_message("arena IRONCLAD Cultist 10002")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Transition: arena combat -> lose -> menu
        coord.send_message("lose")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Verify back at menu
        assert not coord.in_game, "Should be at menu after lose"

    def test_menu_to_arena_to_menu_via_abandon(self, at_main_menu: Coordinator):
        """Test arena lifecycle with abandon: menu -> arena -> abandon -> menu."""
        coord = at_main_menu

        # Transition: menu -> arena combat
        coord.send_message("arena IRONCLAD Cultist 10003")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Transition: arena combat -> abandon -> menu
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Verify back at menu
        assert not coord.in_game, "Should be at menu after abandon"

    def test_rapid_arena_starts(self, at_main_menu: Coordinator):
        """Test starting multiple arena fights in quick succession.

        This tests for race conditions and cleanup issues between fights.
        """
        coord = at_main_menu

        for i in range(3):
            # Start arena fight
            coord.send_message(f"arena IRONCLAD Cultist {10010 + i}")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            assert coord.in_game, f"Fight {i+1}: Should be in game"
            assert coord.last_game_state.in_combat, f"Fight {i+1}: Should be in combat"

            # End fight
            coord.send_message("win")
            wait_for_ready(coord)
            wait_for_main_menu(coord)

            assert not coord.in_game, f"Fight {i+1}: Should be at menu"

            # Cleanup
            coord.send_message("arena_back")
            wait_for_ready(coord)

    def test_different_characters_sequential(self, at_main_menu: Coordinator):
        """Test arena fights with different characters in sequence."""
        coord = at_main_menu
        characters = ["IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"]

        for i, char in enumerate(characters):
            # Start arena fight
            coord.send_message(f"arena {char} Cultist {10020 + i}")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            # Verify correct character
            expected = PlayerClass[char]
            assert coord.last_game_state.character == expected, (
                f"Expected {char}, got {coord.last_game_state.character}"
            )

            # End fight
            coord.send_message("win")
            wait_for_ready(coord)
            wait_for_main_menu(coord)
            coord.send_message("arena_back")
            wait_for_ready(coord)


# =============================================================================
# Invariant Tests
# =============================================================================

class TestInvariants:
    """Tests that verify state machine invariants in isolation."""

    def test_in_game_has_game_state(self, at_main_menu: Coordinator):
        """Invariant: If in_game is True, game state should not be None."""
        coord = at_main_menu

        # Start arena fight
        coord.send_message("arena IRONCLAD Cultist 20001")
        wait_for_ready(coord)
        wait_for_in_game(coord)

        # Check invariant
        if coord.in_game:
            assert coord.last_game_state is not None, (
                "Invariant violation: in_game=True but game state is None"
            )

    def test_in_combat_has_monsters(self, at_main_menu: Coordinator):
        """Invariant: If in combat, there should be monsters."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 20002")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        game = coord.last_game_state
        if game.in_combat:
            assert game.monsters is not None, (
                "Invariant violation: in_combat=True but monsters is None"
            )
            assert len(game.monsters) > 0, (
                "Invariant violation: in_combat=True but no monsters"
            )

    def test_player_hp_non_negative(self, at_main_menu: Coordinator):
        """Invariant: Player HP should never be negative."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 20003")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        game = coord.last_game_state
        assert game.current_hp >= 0, (
            f"Invariant violation: HP is negative ({game.current_hp})"
        )

    def test_hand_size_maximum(self, at_main_menu: Coordinator):
        """Invariant: Hand should have at most 10 cards."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 20004")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        game = coord.last_game_state
        if game.hand:
            assert len(game.hand) <= 10, (
                f"Invariant violation: hand has {len(game.hand)} cards (max 10)"
            )

    def test_arena_no_forbidden_screens(self, at_main_menu: Coordinator):
        """Invariant: Arena fights should never show reward/map screens."""
        coord = at_main_menu

        FORBIDDEN_SCREENS = {
            ScreenType.CARD_REWARD,
            ScreenType.COMBAT_REWARD,
            ScreenType.MAP,
            ScreenType.BOSS_REWARD,
            ScreenType.SHOP_ROOM,
            ScreenType.SHOP_SCREEN,
            ScreenType.REST,
            ScreenType.CHEST,
            ScreenType.EVENT,
        }

        # Run an arena fight to completion
        coord.send_message("arena IRONCLAD Cultist 20005")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        coord.send_message("win")
        wait_for_ready(coord)

        # Check we didn't hit a forbidden screen while transitioning
        if coord.in_game and coord.last_game_state:
            screen_type = coord.last_game_state.screen_type
            assert screen_type not in FORBIDDEN_SCREENS, (
                f"Invariant violation: arena showed forbidden screen {screen_type.name}"
            )


# =============================================================================
# Error Handling Tests
# =============================================================================

class TestErrorHandling:
    """Tests for graceful error handling in invalid states."""

    def test_win_at_main_menu(self, at_main_menu: Coordinator):
        """Test that 'win' command at main menu is handled gracefully."""
        coord = at_main_menu

        coord.send_message("win")
        wait_for_ready(coord)

        # Should either error gracefully or do nothing
        assert not coord.in_game, "Should still be at menu"
        # The command should not crash the game

    def test_lose_at_main_menu(self, at_main_menu: Coordinator):
        """Test that 'lose' command at main menu is handled gracefully."""
        coord = at_main_menu

        coord.send_message("lose")
        wait_for_ready(coord)

        # Should either error gracefully or do nothing
        assert not coord.in_game, "Should still be at menu"

    def test_abandon_at_main_menu(self, at_main_menu: Coordinator):
        """Test that 'abandon' command at main menu is handled gracefully."""
        coord = at_main_menu

        coord.send_message("abandon")
        wait_for_ready(coord)

        # Should do nothing since we're not in a game
        assert not coord.in_game, "Should still be at menu"

    def test_double_win(self, at_main_menu: Coordinator):
        """Test that sending 'win' twice doesn't cause issues."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 30001")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # First win - should work
        coord.send_message("win")
        wait_for_ready(coord)

        # Second win - should be gracefully ignored
        coord.send_message("win")
        wait_for_ready(coord)

        # Should end up at menu regardless
        wait_for_main_menu(coord)
        assert not coord.in_game, "Should be at menu"


# =============================================================================
# State Consistency Tests
# =============================================================================

class TestStateConsistency:
    """Tests that verify state remains consistent across operations."""

    def test_state_after_multiple_state_commands(self, at_main_menu: Coordinator):
        """Test that multiple 'state' commands return consistent data."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 40001")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Get state multiple times
        states = []
        for _ in range(3):
            coord.send_message("state")
            wait_for_ready(coord)
            states.append({
                "in_game": coord.in_game,
                "in_combat": coord.last_game_state.in_combat if coord.last_game_state else None,
                "hp": coord.last_game_state.current_hp if coord.last_game_state else None,
            })

        # All states should be consistent (no changes between queries)
        for i, state in enumerate(states[1:], 1):
            assert state["in_game"] == states[0]["in_game"], (
                f"State {i}: in_game changed unexpectedly"
            )
            assert state["in_combat"] == states[0]["in_combat"], (
                f"State {i}: in_combat changed unexpectedly"
            )
            assert state["hp"] == states[0]["hp"], (
                f"State {i}: HP changed unexpectedly"
            )

    def test_character_persists_throughout_fight(self, at_main_menu: Coordinator):
        """Test that character class doesn't change during a fight."""
        coord = at_main_menu

        coord.send_message("arena THE_SILENT Cultist 40002")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        initial_char = coord.last_game_state.character
        assert initial_char == PlayerClass.THE_SILENT

        # Query state a few times
        for _ in range(3):
            coord.send_message("state")
            wait_for_ready(coord)
            assert coord.last_game_state.character == initial_char, (
                "Character changed unexpectedly during fight"
            )

    def test_deck_count_consistent(self, at_main_menu: Coordinator):
        """Test that deck card count remains consistent when no cards played."""
        coord = at_main_menu

        coord.send_message("arena IRONCLAD Cultist 40003")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        game = coord.last_game_state
        total_cards = len(game.deck)

        # Verify total cards in all zones equals deck size
        in_zones = (
            len(game.hand or []) +
            len(game.draw_pile or []) +
            len(game.discard_pile or []) +
            len(game.exhaust_pile or [])
        )

        # These should be equal (accounting for cards in limbo during animations)
        assert abs(in_zones - total_cards) <= 1, (
            f"Card count mismatch: deck={total_cards}, in zones={in_zones}"
        )
