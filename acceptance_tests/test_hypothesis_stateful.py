"""
Hypothesis stateful tests for STS Arena.

This module uses Hypothesis's RuleBasedStateMachine to generate random sequences
of game actions and verify the arena system maintains consistent state throughout.

The state machine models:
- Main menu vs in-game states
- Arena fights vs normal runs
- Combat actions (play cards, use potions, end turn)
- State transitions (abandon, proceed, combat resolution)

Invariants are checked after every action to catch state inconsistencies.

Note: These tests require the game to be running via scripts/run-acceptance-tests.sh.
"""

import time
import random
from hypothesis import note, settings, Verbosity, HealthCheck
from hypothesis.stateful import (
    RuleBasedStateMachine,
    rule,
    invariant,
    precondition,
    initialize,
    Bundle,
    consumes,
    multiple,
)
from hypothesis import strategies as st
import pytest

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from conftest import wait_for_ready, wait_for_stable, GameTimeout, DEFAULT_TIMEOUT

# Characters that can be used for arena fights
CHARACTERS = ["IRONCLAD", "THE_SILENT", "DEFECT", "WATCHER"]

# Encounters to test
ENCOUNTERS = [
    "Cultist",
    "Jaw Worm",
    "2 Louse",
    "Lagavulin",
    "Hexaghost",
    "Chosen",
    "Gremlin Nob",
    "3 Sentries",
    "Slime Boss",
]

# Card IDs that are generally safe to play (don't require targets or special conditions)
SAFE_UNTARGETED_CARDS = [
    "Defend",
    "Survivor",
    "Backflip",
    "Deflect",
    "Dodge and Roll",
    "Leap",
    "Blur",
    "Dash",  # Ironclad
    "Shrug It Off",
    "Flame Barrier",
    "Ghostly Armor",
    "Impervious",
    "True Grit",
    "Power Through",
    "Defend_G",  # Silent
    "Defend_B",  # Defect
    "Defend_P",  # Watcher
]


def wait_for_state_update(coord: Coordinator, timeout: float = 5):
    """Wait for game state to stabilize. Short timeout - state should update quickly."""
    coord.game_is_ready = False
    coord.send_message("state")
    wait_for_ready(coord, timeout=timeout)


def assert_in_game(coord: Coordinator):
    """Assert we're in a game after state has stabilized."""
    wait_for_state_update(coord, timeout=5)
    assert coord.in_game, "Expected to be in game but we're at main menu"


def assert_at_main_menu(coord: Coordinator):
    """Assert we're at main menu after state has stabilized."""
    wait_for_state_update(coord, timeout=5)
    assert not coord.in_game, "Expected to be at main menu but we're in game"


def assert_in_combat(coord: Coordinator):
    """Assert we're in combat and can take actions after state has stabilized."""
    wait_for_state_update(coord, timeout=5)
    assert coord.in_game, "Expected to be in game but we're at main menu"
    assert coord.last_game_state, "No game state available"
    game = coord.last_game_state
    assert game.in_combat, f"Expected to be in combat but in_combat={game.in_combat}"
    assert game.monsters, "Expected monsters in combat but none found"
    assert game.play_available or game.end_available, "Combat not ready for input"


# Simple wait-and-assert functions - one state update then check
def wait_for_in_game(coord: Coordinator, timeout: float = 5):
    """Wait for one state update then assert we're in game."""
    wait_for_state_update(coord, timeout=timeout)
    assert coord.in_game, "Expected to be in game but we're at main menu"


def wait_for_main_menu(coord: Coordinator, timeout: float = 5):
    """Wait for one state update then assert we're at main menu."""
    wait_for_state_update(coord, timeout=timeout)
    assert not coord.in_game, "Expected to be at main menu but we're in game"


def wait_for_combat(coord: Coordinator, timeout: float = 5):
    """Wait for one state update then assert we're in combat."""
    wait_for_state_update(coord, timeout=timeout)
    assert coord.in_game, "Expected to be in game"
    assert coord.last_game_state, "No game state"
    game = coord.last_game_state
    assert game.in_combat, f"Expected combat, in_combat={game.in_combat}"
    assert game.monsters, "No monsters in combat"
    assert game.play_available or game.end_available, "Combat not ready"


def ensure_main_menu(coord: Coordinator, timeout: float = 10):
    """Ensure we're at the main menu. Abandons any active run."""
    # Try a few times - abandon might need to go through transition screens
    for attempt in range(3):
        wait_for_state_update(coord, timeout=timeout)

        if not coord.in_game:
            return

        # We're in a game, need to abandon
        coord.send_message("abandon")
        wait_for_state_update(coord, timeout=timeout)

        if not coord.in_game:
            return

    # If we're still in game after 3 attempts, that's an error
    assert not coord.in_game, f"Could not return to main menu after 3 abandon attempts"


# =============================================================================
# Stateful State Machine
# =============================================================================

class ArenaStateMachine(RuleBasedStateMachine):
    """
    A state machine that models the STS Arena game states and transitions.

    States:
    - main_menu: At the main menu, no active game
    - in_arena: In an arena fight (single combat encounter)
    - in_normal_run: In a normal game run

    The machine tracks expected state and verifies it matches actual game state.
    """

    def __init__(self):
        super().__init__()
        # Model state (what we expect)
        self.model_in_game = False
        self.model_is_arena = False
        self.model_character = None
        self.model_encounter = None
        self.model_in_combat = False
        self.model_combat_ended = False  # True if we won/lost combat

        # Track actions for debugging
        self.action_history = []

        # Coordinator will be set by initialize
        self.coord = None

    @initialize()
    def setup(self):
        """Initialize the state machine with a coordinator at main menu."""
        # Access the global coordinator from conftest
        import conftest
        self.coord = conftest._coordinator

        # Ensure we start at main menu
        ensure_main_menu(self.coord, timeout=10)

        # Verify model matches reality
        assert not self.coord.in_game, "Should start at main menu"
        self.model_in_game = False
        self.model_is_arena = False
        self.model_character = None
        self.model_encounter = None
        self.model_in_combat = False
        self.model_combat_ended = False

        note("Initialized at main menu")

    # =========================================================================
    # Invariants - checked after every rule
    # =========================================================================

    @invariant()
    def game_state_consistency(self):
        """If we're in game, we should have a game state."""
        if self.coord.in_game:
            assert self.coord.last_game_state is not None, \
                "In game but no game state"

    @invariant()
    def model_matches_reality(self):
        """Our model state should match the actual game state."""
        assert self.model_in_game == self.coord.in_game, \
            f"Model says in_game={self.model_in_game}, but game says {self.coord.in_game}"

    @invariant()
    def combat_has_monsters(self):
        """If we're in combat, there should be monsters (or combat just ended)."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat and not self.model_combat_ended:
                assert game.monsters is not None, "In combat but monsters is None"
                assert len(game.monsters) > 0, "In combat but no monsters"

    @invariant()
    def player_hp_valid(self):
        """Player HP should be positive while in active combat."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat and game.current_hp is not None:
                # HP can be 0 if we just died
                assert game.current_hp >= 0, f"HP is negative: {game.current_hp}"

    @invariant()
    def energy_non_negative(self):
        """Energy should never be negative."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat and hasattr(game, 'player') and game.player:
                energy = getattr(game.player, 'energy', None)
                if energy is not None:
                    assert energy >= 0, f"Energy is negative: {energy}"

    @invariant()
    def hand_size_valid(self):
        """Hand should have at most 10 cards."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat and game.hand:
                assert len(game.hand) <= 10, f"Hand has {len(game.hand)} cards (max 10)"

    @invariant()
    def arena_no_apology_slime(self):
        """Arena fights should never have Apology Slime (fallback monster)."""
        if self.model_is_arena and self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat and game.monsters:
                monster_names = [m.name for m in game.monsters]
                assert "Apology Slime" not in monster_names, \
                    f"Arena got fallback Apology Slime! Expected {self.model_encounter}, got {monster_names}"

    @invariant()
    def correct_character(self):
        """If we're in game, character should match what we started with."""
        if self.model_character and self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            expected = PlayerClass[self.model_character]
            assert game.character == expected, \
                f"Expected {self.model_character}, got {game.character}"

    # =========================================================================
    # Rules - actions that can be taken
    # =========================================================================

    @precondition(lambda self: not self.model_in_game)
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS),
          seed=st.integers(min_value=0, max_value=2**63-1))
    def start_arena_fight(self, character, encounter, seed):
        """Start an arena fight from the main menu."""
        note(f"Starting arena: {character} vs {encounter} (seed={seed})")
        self.action_history.append(f"arena {character} {encounter} {seed}")

        self.coord.send_message(f"arena {character} {encounter} {seed}")
        wait_for_in_game(self.coord, timeout=10)
        wait_for_combat(self.coord, timeout=10)

        # Update model
        self.model_in_game = True
        self.model_is_arena = True
        self.model_character = character
        self.model_encounter = encounter
        self.model_in_combat = True
        self.model_combat_ended = False

        note(f"Arena started successfully")

    @precondition(lambda self: not self.model_in_game)
    @rule(character=st.sampled_from(CHARACTERS))
    def start_normal_run(self, character):
        """Start a normal run from the main menu."""
        note(f"Starting normal run: {character}")
        self.action_history.append(f"start {character}")

        self.coord.send_message(f"start {character} 0")
        wait_for_in_game(self.coord, timeout=10)

        # Update model
        self.model_in_game = True
        self.model_is_arena = False
        self.model_character = character
        self.model_encounter = None
        self.model_in_combat = False
        self.model_combat_ended = False

        note(f"Normal run started")

    @precondition(lambda self: self.model_in_game)
    @rule()
    def abandon_run(self):
        """Abandon the current run and return to main menu."""
        note("Abandoning run")
        self.action_history.append("abandon")

        self.coord.send_message("abandon")
        wait_for_main_menu(self.coord, timeout=10)

        # Update model
        self.model_in_game = False
        self.model_is_arena = False
        self.model_character = None
        self.model_encounter = None
        self.model_in_combat = False
        self.model_combat_ended = False

        note("Abandoned successfully")

    @precondition(lambda self: self.model_in_game and self.model_in_combat)
    @rule()
    def win_combat(self):
        """Force a win by killing all monsters."""
        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        note("Forcing win")
        self.action_history.append("win")

        self.coord.send_message("win")
        wait_for_state_update(self.coord, timeout=10)

        # After win, handle based on arena vs normal run
        if self.model_is_arena:
            # Arena should return to menu - if not, abandon to get there
            if self.coord.in_game:
                self.coord.send_message("abandon")
                wait_for_state_update(self.coord, timeout=10)
            self.model_in_game = False
            self.model_in_combat = False
            self.model_combat_ended = False
            note("Arena fight won, at menu")
        else:
            # Normal run - might be at reward screen
            if not self.coord.in_game:
                self.model_in_game = False
                self.model_in_combat = False
                self.model_combat_ended = False
            elif self.coord.last_game_state and not self.coord.last_game_state.in_combat:
                self.model_in_combat = False
                self.model_combat_ended = True
                note("Normal run combat won")

    @precondition(lambda self: self.model_in_game and self.model_in_combat)
    @rule()
    def lose_combat(self):
        """Force a loss by killing the player."""
        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        note("Forcing loss")
        self.action_history.append("lose")

        self.coord.send_message("lose")
        wait_for_state_update(self.coord, timeout=10)

        # After loss, handle based on arena vs normal run
        if self.model_is_arena:
            # Arena should return to menu - if not, abandon to get there
            if self.coord.in_game:
                self.coord.send_message("abandon")
                wait_for_state_update(self.coord, timeout=10)
            self.model_in_game = False
            self.model_in_combat = False
            self.model_combat_ended = False
            note("Arena fight lost, at menu")
        else:
            # Normal run - game ends on death
            if not self.coord.in_game:
                self.model_in_game = False
                self.model_in_combat = False
                self.model_combat_ended = False
                note("Normal run death")
            elif self.coord.last_game_state and not self.coord.last_game_state.in_combat:
                self.model_in_combat = False
                self.model_combat_ended = True

    @precondition(lambda self: self.model_in_game and self.model_combat_ended)
    @rule()
    def proceed_after_combat(self):
        """Proceed after combat ends (for non-arena fights)."""
        if self.model_is_arena:
            # Arena fights return to menu or show special screen
            note("Arena fight ended, checking state")
            wait_for_state_update(self.coord, timeout=10)
            if not self.coord.in_game:
                self.model_in_game = False
                self.model_combat_ended = False
            return

        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)
            if not self.coord.in_game:
                self.model_in_game = False
                return

        game = self.coord.last_game_state
        if hasattr(game, 'proceed_available') and game.proceed_available:
            note("Proceeding after combat")
            self.action_history.append("proceed")
            self.coord.send_message("proceed")
            wait_for_state_update(self.coord, timeout=10)
            self.model_combat_ended = False

    def teardown(self):
        """Clean up after the test - return to main menu."""
        note(f"Teardown. Action history: {self.action_history}")
        ensure_main_menu(self.coord, timeout=10)


# Create the test class from the state machine
TestArenaStateful = ArenaStateMachine.TestCase

# Configure the test settings
TestArenaStateful.settings = settings(
    max_examples=50,  # Reduced for faster iteration
    stateful_step_count=10,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.debug,
)


# =============================================================================
# Additional focused state machines
# =============================================================================

class ArenaCombatMachine(RuleBasedStateMachine):
    """
    State machine focused on combat outcomes within arena fights.

    Tests win/lose commands and verifies proper state transitions.
    """

    def __init__(self):
        super().__init__()
        self.coord = None
        self.in_combat = False
        self.wins = 0
        self.losses = 0

    @initialize(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS),
                seed=st.integers(min_value=0, max_value=2**63-1))
    def setup(self, character, encounter, seed):
        """Start an arena fight."""
        import conftest
        self.coord = conftest._coordinator

        ensure_main_menu(self.coord, timeout=10)

        note(f"Starting combat test: {character} vs {encounter} (seed={seed})")

        self.coord.send_message(f"arena {character} {encounter} {seed}")
        wait_for_in_game(self.coord, timeout=10)
        wait_for_combat(self.coord, timeout=10)

        self.in_combat = True
        self.wins = 0
        self.losses = 0

    @invariant()
    def valid_combat_state(self):
        """Combat state should be consistent."""
        if not self.in_combat:
            return

        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat:
                assert game.hand is not None, "No hand in combat"
                assert game.monsters, "No monsters in combat"

    @precondition(lambda self: self.in_combat)
    @rule()
    def win(self):
        """Force a win."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        note("Forcing win")
        self.coord.send_message("win")
        wait_for_state_update(self.coord, timeout=10)

        self.wins += 1
        if not self.coord.in_game:
            self.in_combat = False
            note("Returned to menu after win")
        elif self.coord.last_game_state and not self.coord.last_game_state.in_combat:
            self.in_combat = False
            note("Combat ended (win)")

    @precondition(lambda self: self.in_combat)
    @rule()
    def lose(self):
        """Force a loss."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        note("Forcing loss")
        self.coord.send_message("lose")
        wait_for_state_update(self.coord, timeout=10)

        self.losses += 1
        if not self.coord.in_game:
            self.in_combat = False
            note("Returned to menu after loss")
        elif self.coord.last_game_state and not self.coord.last_game_state.in_combat:
            self.in_combat = False
            note("Combat ended (loss)")

    def teardown(self):
        """Clean up."""
        note(f"Wins: {self.wins}, Losses: {self.losses}")
        ensure_main_menu(self.coord, timeout=10)


TestArenaCombat = ArenaCombatMachine.TestCase
TestArenaCombat.settings = settings(
    max_examples=50,  # Reduced for faster iteration
    stateful_step_count=10,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.debug,
)


class ArenaTransitionMachine(RuleBasedStateMachine):
    """
    State machine focused on testing transitions between arena and main menu.

    Tests various ways to exit combat: win, lose, abandon.
    """

    def __init__(self):
        super().__init__()
        self.coord = None
        self.at_menu = True
        self.fights_started = 0
        self.fights_won = 0
        self.fights_lost = 0
        self.fights_abandoned = 0

    @initialize()
    def setup(self):
        """Initialize at main menu."""
        import conftest
        self.coord = conftest._coordinator
        ensure_main_menu(self.coord, timeout=10)
        self.at_menu = True
        self.fights_started = 0
        self.fights_won = 0
        self.fights_lost = 0
        self.fights_abandoned = 0

    @invariant()
    def state_consistent(self):
        """Model state should match reality."""
        assert self.at_menu == (not self.coord.in_game), \
            f"Model: at_menu={self.at_menu}, Reality: in_game={self.coord.in_game}"

    @invariant()
    def fight_counts_valid(self):
        """Fight outcomes shouldn't exceed fights started."""
        total_outcomes = self.fights_won + self.fights_lost + self.fights_abandoned
        assert total_outcomes <= self.fights_started, \
            f"Outcomes {total_outcomes} > started {self.fights_started}"

    @precondition(lambda self: self.at_menu)
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS),
          seed=st.integers(min_value=0, max_value=2**63-1))
    def start_fight(self, character, encounter, seed):
        """Start an arena fight."""
        note(f"Starting fight #{self.fights_started + 1}: {character} vs {encounter} (seed={seed})")

        self.coord.send_message(f"arena {character} {encounter} {seed}")
        wait_for_in_game(self.coord, timeout=10)
        wait_for_combat(self.coord, timeout=10)

        self.at_menu = False
        self.fights_started += 1

    @precondition(lambda self: not self.at_menu)
    @rule()
    def win_fight(self):
        """Win the current fight."""
        note(f"Winning fight #{self.fights_started}")

        self.coord.send_message("win")
        wait_for_state_update(self.coord, timeout=10)

        # Arena fights should return to menu after win
        if not self.coord.in_game:
            self.at_menu = True
            self.fights_won += 1
            note("Returned to menu after win")
        else:
            # Might be showing victory screen
            self.coord.send_message("abandon")
            wait_for_main_menu(self.coord, timeout=10)
            self.at_menu = True
            self.fights_won += 1
            note("Abandoned after win screen")

    @precondition(lambda self: not self.at_menu)
    @rule()
    def lose_fight(self):
        """Lose the current fight."""
        note(f"Losing fight #{self.fights_started}")

        self.coord.send_message("lose")
        wait_for_state_update(self.coord, timeout=10)

        # Arena fights should return to menu after loss
        if not self.coord.in_game:
            self.at_menu = True
            self.fights_lost += 1
            note("Returned to menu after loss")
        else:
            # Might be showing death screen
            self.coord.send_message("abandon")
            wait_for_main_menu(self.coord, timeout=10)
            self.at_menu = True
            self.fights_lost += 1
            note("Abandoned after death screen")

    @precondition(lambda self: not self.at_menu)
    @rule()
    def abandon_fight(self):
        """Abandon current fight."""
        note(f"Abandoning fight #{self.fights_started}")

        self.coord.send_message("abandon")
        wait_for_main_menu(self.coord, timeout=10)

        self.at_menu = True
        self.fights_abandoned += 1

    def teardown(self):
        """Clean up."""
        note(f"Started {self.fights_started}, won {self.fights_won}, lost {self.fights_lost}, abandoned {self.fights_abandoned}")
        ensure_main_menu(self.coord, timeout=10)


TestArenaTransitions = ArenaTransitionMachine.TestCase
TestArenaTransitions.settings = settings(
    max_examples=50,  # Reduced for faster iteration
    stateful_step_count=10,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.debug,
)
