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
from hypothesis import note, settings, Verbosity, HealthCheck, Phase, assume
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
from spirecomm.spire.screen import ScreenType
from conftest import (
    wait_for_ready,
    wait_for_stable,
    wait_for_in_game,
    wait_for_main_menu,
    wait_for_combat,
    wait_for_state_update,
    GameTimeout,
    DEFAULT_TIMEOUT,
)

# Screen types that should NEVER appear during or after arena fights
FORBIDDEN_ARENA_SCREENS = {
    ScreenType.CARD_REWARD,    # Should not get card rewards in arena
    ScreenType.COMBAT_REWARD,  # Should not get combat rewards in arena
    ScreenType.MAP,            # Should not see map in arena
    ScreenType.BOSS_REWARD,    # Should not get boss rewards in arena
    ScreenType.SHOP_ROOM,      # Should not see shop in arena
    ScreenType.SHOP_SCREEN,    # Should not see shop screen in arena
    ScreenType.REST,           # Should not see rest sites in arena
    ScreenType.CHEST,          # Should not see chests in arena
    ScreenType.EVENT,          # Should not see events in arena
}

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


def wait_for_arena_end(coord: Coordinator, timeout: float = 60):
    """Block until arena fight ends (return to main menu)."""
    wait_for_main_menu(coord, timeout=timeout)


def ensure_main_menu(coord: Coordinator, timeout: float = 60):
    """Ensure we're at the main menu. Abandons any active run."""
    # Check current state first
    wait_for_state_update(coord, timeout=5)
    if not coord.in_game:
        return

    # We're in a game - need to abandon
    coord.send_message("abandon")
    wait_for_main_menu(coord, timeout=timeout)
    assert not coord.in_game, "Could not return to main menu after abandon"


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
        ensure_main_menu(self.coord)

        # Model state is deterministic - always start at menu
        # DO NOT sync with game state - that causes flakiness
        self.model_in_game = False
        self.model_is_arena = False
        self.model_character = None
        self.model_encounter = None
        self.model_in_combat = False
        self.model_combat_ended = False

        # Verify game actually is at menu
        assert not self.coord.in_game, "ensure_main_menu() didn't work - still in game"

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

    @invariant()
    def arena_no_forbidden_screens(self):
        """Arena fights should never show reward screens, map, or other non-combat screens."""
        if self.model_is_arena and self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            screen_type = game.screen_type
            if screen_type in FORBIDDEN_ARENA_SCREENS:
                # This is the bug! Arena fights should return to menu, not show reward screens
                assert False, \
                    f"Arena fight showed forbidden screen {screen_type.name}! " \
                    f"Character={self.model_character}, Encounter={self.model_encounter}, " \
                    f"Actions={self.action_history}"

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
        wait_for_in_game(self.coord)
        wait_for_combat(self.coord) 

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
        wait_for_in_game(self.coord)

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
        wait_for_main_menu(self.coord)

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
        wait_for_state_update(self.coord)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        note("Forcing win")
        self.action_history.append("win")

        self.coord.send_message("win")

        # After win, handle based on arena vs normal run
        if self.model_is_arena:
            # Arena should return to menu
            wait_for_arena_end(self.coord)
            self.model_in_game = False
            self.model_in_combat = False
            self.model_combat_ended = False
            note("Arena fight won, at menu")
        else:
            # Normal run - might be at reward screen
            wait_for_state_update(self.coord)
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
        wait_for_state_update(self.coord)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        note("Forcing loss")
        self.action_history.append("lose")

        self.coord.send_message("lose")

        # After loss, handle based on arena vs normal run
        if self.model_is_arena:
            # Arena should return to menu
            wait_for_arena_end(self.coord)
            self.model_in_game = False
            self.model_in_combat = False
            self.model_combat_ended = False
            note("Arena fight lost, at menu")
        else:
            # Normal run - game ends on death
            wait_for_state_update(self.coord)
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
            wait_for_state_update(self.coord)
            if not self.coord.in_game:
                self.model_in_game = False
                self.model_combat_ended = False
            return

        wait_for_state_update(self.coord)
        if not self.coord.in_game:
            self.model_in_game = False
            return

        game = self.coord.last_game_state
        if hasattr(game, 'proceed_available') and game.proceed_available:
            note("Proceeding after combat")
            self.action_history.append("proceed")
            self.coord.send_message("proceed")
            wait_for_state_update(self.coord)
            self.model_combat_ended = False

    def teardown(self):
        """Clean up after the test - return to main menu."""
        note(f"Teardown. Action history: {self.action_history}")
        ensure_main_menu(self.coord)


# Re-enabled after fixing model state determinism
TestArenaStateful = ArenaStateMachine.TestCase
TestArenaStateful.settings = settings(
    max_examples=20,
    stateful_step_count=8,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.normal,
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

    @initialize()
    def setup(self):
        """Initialize at main menu."""
        import conftest
        self.coord = conftest._coordinator
        ensure_main_menu(self.coord)

        # Model state is deterministic - always start at menu, not in combat
        # DO NOT sync with game state - that causes flakiness
        self.in_combat = False
        self.wins = 0
        self.losses = 0

        # Verify game actually is at menu
        assert not self.coord.in_game, "ensure_main_menu() didn't work - still in game"

    @precondition(lambda self: not self.in_combat)
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS),
          seed=st.integers(min_value=0, max_value=2**63-1))
    def start_fight(self, character, encounter, seed):
        """Start an arena fight."""
        note(f"Starting combat: {character} vs {encounter} (seed={seed})")

        self.coord.send_message(f"arena {character} {encounter} {seed}")
        wait_for_in_game(self.coord) 
        wait_for_combat(self.coord)  

        self.in_combat = True

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

    @invariant()
    def no_forbidden_screens(self):
        """Arena fights should never show reward screens or map."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            screen_type = game.screen_type
            if screen_type in FORBIDDEN_ARENA_SCREENS:
                assert False, \
                    f"Arena fight showed forbidden screen {screen_type.name}! " \
                    f"Wins={self.wins}, Losses={self.losses}"

    @precondition(lambda self: self.in_combat)
    @rule()
    def win(self):
        """Force a win."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        note("Forcing win")
        self.coord.send_message("win")

        # Use polling wait for arena fights to handle victory screen transitions
        wait_for_arena_end(self.coord)

        self.wins += 1
        self.in_combat = False
        note("Returned to menu after win")

    @precondition(lambda self: self.in_combat)
    @rule()
    def lose(self):
        """Force a loss."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        note("Forcing loss")
        self.coord.send_message("lose")

        # Use polling wait for arena fights to handle death screen transitions
        wait_for_arena_end(self.coord)

        self.losses += 1
        self.in_combat = False
        note("Returned to menu after loss")

    def teardown(self):
        """Clean up."""
        note(f"Wins: {self.wins}, Losses: {self.losses}")
        ensure_main_menu(self.coord)


# Re-enabled after fixing model state determinism
TestArenaCombat = ArenaCombatMachine.TestCase
TestArenaCombat.settings = settings(
    max_examples=20,
    stateful_step_count=8,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.normal,
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
        self._action_log = []

    def _log(self, msg):
        """Log an action with timestamp for debugging."""
        import time
        ts = time.strftime("%H:%M:%S")
        entry = f"[{ts}] {msg}"
        self._action_log.append(entry)
        note(entry)

    @initialize()
    def setup(self):
        """Initialize at main menu."""
        import conftest
        self.coord = conftest._coordinator
        self._action_log = []

        self._log(f"Setup: initial in_game={self.coord.in_game}")
        ensure_main_menu(self.coord)
        self._log(f"Setup: after ensure_main_menu, in_game={self.coord.in_game}")

        # Model state is deterministic - always start at menu
        # DO NOT sync with game state - that causes flakiness
        self.at_menu = True
        self.fights_started = 0
        self.fights_won = 0
        self.fights_lost = 0
        self.fights_abandoned = 0

        # Verify game actually is at menu (fail if not)
        assert not self.coord.in_game, f"ensure_main_menu() didn't work - still in game"

        self._log(f"Setup complete: at_menu={self.at_menu}")

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

    @invariant()
    def no_forbidden_screens(self):
        """Arena fights should never show reward screens or map."""
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            screen_type = game.screen_type
            if screen_type in FORBIDDEN_ARENA_SCREENS:
                assert False, \
                    f"Arena fight showed forbidden screen {screen_type.name}! " \
                    f"Fight #{self.fights_started}, won={self.fights_won}, lost={self.fights_lost}"

    @precondition(lambda self: self.at_menu)
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS),
          seed=st.integers(min_value=0, max_value=2**63-1))
    def start_fight(self, character, encounter, seed):
        """Start an arena fight."""
        self._log(f"start_fight: {character} vs {encounter} (seed={seed}), at_menu={self.at_menu}")

        self.coord.send_message(f"arena {character} {encounter} {seed}")
        wait_for_in_game(self.coord) 
        wait_for_combat(self.coord)  

        self.at_menu = False
        self.fights_started += 1
        self._log(f"start_fight complete: at_menu={self.at_menu}, in_game={self.coord.in_game}")

    @precondition(lambda self: not self.at_menu)
    @rule()
    def win_fight(self):
        """Win the current fight."""
        self._log(f"win_fight: fight #{self.fights_started}, at_menu={self.at_menu}")

        self.coord.send_message("win")

        # Use polling wait with abandon fallback for arena fights
        wait_for_arena_end(self.coord)

        self.at_menu = True
        self.fights_won += 1
        self._log(f"win_fight complete: at_menu={self.at_menu}, in_game={self.coord.in_game}")

    @precondition(lambda self: not self.at_menu)
    @rule()
    def lose_fight(self):
        """Lose the current fight."""
        self._log(f"lose_fight: fight #{self.fights_started}, at_menu={self.at_menu}")

        self.coord.send_message("lose")

        # Use polling wait with abandon fallback for arena fights
        wait_for_arena_end(self.coord)

        self.at_menu = True
        self.fights_lost += 1
        self._log(f"lose_fight complete: at_menu={self.at_menu}, in_game={self.coord.in_game}")

    @precondition(lambda self: not self.at_menu)
    @rule()
    def abandon_fight(self):
        """Abandon current fight."""
        self._log(f"abandon_fight: fight #{self.fights_started}, at_menu={self.at_menu}")

        self.coord.send_message("abandon")
        wait_for_main_menu(self.coord)

        self.at_menu = True
        self.fights_abandoned += 1
        self._log(f"abandon_fight complete: at_menu={self.at_menu}, in_game={self.coord.in_game}")

    def teardown(self):
        """Clean up."""
        self._log(f"teardown: started={self.fights_started}, won={self.fights_won}, lost={self.fights_lost}, abandoned={self.fights_abandoned}")
        self._log(f"teardown: full log = {self._action_log}")
        ensure_main_menu(self.coord)


# Now working! Run more thorough tests
TestArenaTransitions = ArenaTransitionMachine.TestCase
TestArenaTransitions.settings = settings(
    max_examples=20,
    stateful_step_count=8,
    deadline=None,
    suppress_health_check=list(HealthCheck),
    verbosity=Verbosity.normal,
)
