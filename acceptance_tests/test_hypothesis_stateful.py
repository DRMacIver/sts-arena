"""
Hypothesis-based stateful tests for STS Arena.

These tests use Hypothesis's stateful testing framework to generate random
sequences of actions and verify the arena system maintains consistent state.

Note: These tests require the game to be running via scripts/run-acceptance-tests.sh.
The tests use the shared coordinator fixture from conftest.py.
"""

import time
import random
from hypothesis import given, strategies as st, settings, Verbosity, note, assume
import pytest

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from conftest import wait_for_ready, GameTimeout, DEFAULT_TIMEOUT


# Characters that can be used for arena fights
CHARACTERS = ["IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"]

# Encounters to test (subset for faster tests)
ENCOUNTERS = [
    "Cultist",
    "Jaw Worm",
    "2 Louse",
    "Lagavulin",
    "Hexaghost",
    "Chosen",
    "Awakened One",
    "Gremlin Nob",
    "3 Sentries",
]


def wait_for_in_game(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're in a game."""
    start = time.time()
    while not coord.in_game:
        if time.time() - start > timeout:
            raise GameTimeout(f"Timed out after {timeout}s waiting to be in game")
        coord.send_message("state")
        try:
            wait_for_ready(coord, timeout=10)
        except GameTimeout:
            pass


def wait_for_main_menu(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're at the main menu (not in game)."""
    start = time.time()
    while coord.in_game:
        if time.time() - start > timeout:
            raise GameTimeout(f"Timed out after {timeout}s waiting to reach main menu")
        coord.send_message("state")
        try:
            wait_for_ready(coord, timeout=10)
        except GameTimeout:
            pass


def wait_for_combat(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're in combat with monsters and can take actions."""
    start = time.time()
    while True:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for combat")
        coord.send_message("state")
        try:
            wait_for_ready(coord, timeout=min(10, remaining))
        except GameTimeout:
            continue

        if coord.in_game and coord.last_game_state:
            game = coord.last_game_state
            if game.in_combat and game.monsters:
                if game.play_available or game.end_available:
                    return


def ensure_main_menu(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Ensure we're at the main menu. Abandons any active run."""
    start = time.time()

    def time_remaining():
        return timeout - (time.time() - start)

    coord.send_message("state")
    try:
        wait_for_ready(coord, timeout=min(10, time_remaining()))
    except GameTimeout:
        if time_remaining() <= 0:
            raise
        coord.send_message("state")
        wait_for_ready(coord, timeout=min(10, time_remaining()))

    if not coord.in_game:
        return

    coord.send_message("abandon")

    while coord.in_game:
        if time_remaining() <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for abandon")
        coord.send_message("state")
        try:
            wait_for_ready(coord, timeout=min(5, time_remaining()))
        except GameTimeout:
            continue


# =============================================================================
# Property-based tests using Hypothesis
# =============================================================================

class TestHypothesisArena:
    """Property-based tests for the arena system using Hypothesis."""

    @pytest.fixture(autouse=True)
    def setup(self, at_main_menu):
        """Ensure we start at main menu."""
        self.coord = at_main_menu
        yield
        # Cleanup
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass

    @given(character=st.sampled_from(CHARACTERS))
    @settings(max_examples=10, deadline=None)
    def test_arena_always_creates_combat(self, character):
        """Arena fights should always result in combat with monsters."""
        encounter = random.choice(ENCOUNTERS)

        # Reset to main menu first
        ensure_main_menu(self.coord)

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)

            game = self.coord.last_game_state
            assert game is not None
            assert game.in_combat
            assert len(game.monsters) > 0
            assert game.character == PlayerClass[character]

        finally:
            # Return to main menu for next iteration
            try:
                ensure_main_menu(self.coord, timeout=30)
            except GameTimeout:
                pass

    @given(encounter=st.sampled_from(ENCOUNTERS))
    @settings(max_examples=10, deadline=None)
    def test_arena_loads_correct_encounter(self, encounter):
        """Arena should load the requested encounter (not Apology Slime)."""
        character = random.choice(CHARACTERS)

        ensure_main_menu(self.coord)

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)

            game = self.coord.last_game_state
            monster_names = [m.name for m in game.monsters]

            # Should not have Apology Slime (fallback monster)
            assert "Apology Slime" not in str(monster_names), \
                f"Got fallback Apology Slime instead of {encounter}. Monsters: {monster_names}"

        finally:
            try:
                ensure_main_menu(self.coord, timeout=30)
            except GameTimeout:
                pass

    @given(
        character=st.sampled_from(CHARACTERS),
        encounter=st.sampled_from(ENCOUNTERS),
    )
    @settings(max_examples=10, deadline=None)
    def test_arena_abandon_returns_to_menu(self, character, encounter):
        """Abandoning an arena fight should return to main menu."""
        ensure_main_menu(self.coord)

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)

            self.coord.send_message("abandon")
            wait_for_main_menu(self.coord, timeout=60)

            assert not self.coord.in_game

        finally:
            try:
                ensure_main_menu(self.coord, timeout=30)
            except GameTimeout:
                pass


# =============================================================================
# Sequential action tests
# =============================================================================

class TestArenaSequences:
    """Test sequences of arena actions."""

    @pytest.fixture(autouse=True)
    def setup(self, at_main_menu):
        """Ensure we start at main menu."""
        self.coord = at_main_menu
        yield
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass

    @given(num_fights=st.integers(min_value=2, max_value=5))
    @settings(max_examples=5, deadline=None)
    def test_multiple_arena_fights_sequence(self, num_fights):
        """Multiple consecutive arena fights should all work correctly."""
        ensure_main_menu(self.coord)

        for i in range(num_fights):
            character = random.choice(CHARACTERS)
            encounter = random.choice(ENCOUNTERS)

            note(f"Fight {i+1}/{num_fights}: {character} vs {encounter}")

            self.coord.send_message(f"arena {character} {encounter}")

            try:
                wait_for_in_game(self.coord, timeout=60)
                wait_for_combat(self.coord, timeout=60)

                game = self.coord.last_game_state
                assert game.in_combat, f"Fight {i+1} not in combat"
                assert len(game.monsters) > 0, f"Fight {i+1} has no monsters"

                # Check no Apology Slime
                monster_names = [m.name for m in game.monsters]
                assert "Apology Slime" not in str(monster_names), \
                    f"Fight {i+1} got Apology Slime: {monster_names}"

            finally:
                # Abandon and return to menu for next fight
                try:
                    ensure_main_menu(self.coord, timeout=30)
                except GameTimeout:
                    pass

    @given(
        arena_first=st.booleans(),
        character=st.sampled_from(CHARACTERS),
    )
    @settings(max_examples=8, deadline=None)
    def test_arena_and_normal_run_interleave(self, arena_first, character):
        """Arena and normal runs should not interfere with each other."""
        ensure_main_menu(self.coord)

        if arena_first:
            # Start arena, then normal run
            encounter = random.choice(ENCOUNTERS)

            self.coord.send_message(f"arena {character} {encounter}")
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)

            game1 = self.coord.last_game_state
            assert game1.in_combat

            ensure_main_menu(self.coord)

            self.coord.send_message(f"start {character} 0")
            wait_for_in_game(self.coord, timeout=60)

            game2 = self.coord.last_game_state
            assert game2.floor == 0  # Normal run starts at Neow room

        else:
            # Start normal run, then arena
            self.coord.send_message(f"start {character} 0")
            wait_for_in_game(self.coord, timeout=60)

            game1 = self.coord.last_game_state
            assert game1.floor == 0

            ensure_main_menu(self.coord)

            encounter = random.choice(ENCOUNTERS)
            self.coord.send_message(f"arena {character} {encounter}")
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)

            game2 = self.coord.last_game_state
            assert game2.in_combat
            assert len(game2.monsters) > 0


# =============================================================================
# Stress tests with many examples
# =============================================================================

class TestArenaStress:
    """Stress tests for the arena system."""

    @pytest.fixture(autouse=True)
    def setup(self, at_main_menu):
        """Ensure we start at main menu."""
        self.coord = at_main_menu
        yield
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass

    @given(
        character=st.sampled_from(CHARACTERS),
        encounter=st.sampled_from(ENCOUNTERS),
    )
    @settings(max_examples=50, deadline=None)  # More examples for stress testing
    def test_rapid_arena_creation(self, character, encounter):
        """Rapidly creating arena fights should work reliably."""
        ensure_main_menu(self.coord)

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)

            game = self.coord.last_game_state
            assert game.in_combat
            assert len(game.monsters) > 0

            # Verify correct character
            assert game.character == PlayerClass[character]

            # No fallback monster
            monster_names = [m.name for m in game.monsters]
            assert "Apology Slime" not in str(monster_names)

        finally:
            try:
                ensure_main_menu(self.coord, timeout=30)
            except GameTimeout:
                pass
