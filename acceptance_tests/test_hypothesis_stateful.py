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
from hypothesis import note, settings, Verbosity
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


def wait_for_state_update(coord: Coordinator, timeout: float = 30):
    """Wait for game state to update."""
    coord.game_is_ready = False
    coord.send_message("state")
    wait_for_ready(coord, timeout=timeout)


def wait_for_in_game(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're in a game."""
    start = time.time()
    while not coord.in_game:
        if time.time() - start > timeout:
            raise GameTimeout(f"Timed out after {timeout}s waiting to be in game")
        wait_for_state_update(coord, timeout=10)


def wait_for_main_menu(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're at the main menu (not in game)."""
    start = time.time()
    while coord.in_game:
        if time.time() - start > timeout:
            raise GameTimeout(f"Timed out after {timeout}s waiting to reach main menu")
        wait_for_state_update(coord, timeout=10)


def wait_for_combat(coord: Coordinator, timeout: float = DEFAULT_TIMEOUT):
    """Wait until we're in combat with monsters and can take actions."""
    start = time.time()
    while True:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for combat")

        wait_for_state_update(coord, timeout=min(10, remaining))

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

    wait_for_state_update(coord, timeout=min(10, time_remaining()))

    if not coord.in_game:
        return

    coord.send_message("abandon")

    while coord.in_game:
        if time_remaining() <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for abandon")
        wait_for_state_update(coord, timeout=min(5, time_remaining()))


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
        ensure_main_menu(self.coord, timeout=60)

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
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS))
    def start_arena_fight(self, character, encounter):
        """Start an arena fight from the main menu."""
        note(f"Starting arena: {character} vs {encounter}")
        self.action_history.append(f"arena {character} {encounter}")

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=60)
        except GameTimeout as e:
            note(f"Timeout starting arena: {e}")
            # Update model to match reality
            wait_for_state_update(self.coord, timeout=10)
            self.model_in_game = self.coord.in_game
            if not self.model_in_game:
                return

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

        try:
            wait_for_in_game(self.coord, timeout=60)
        except GameTimeout as e:
            note(f"Timeout starting run: {e}")
            wait_for_state_update(self.coord, timeout=10)
            self.model_in_game = self.coord.in_game
            return

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

        try:
            wait_for_main_menu(self.coord, timeout=60)
        except GameTimeout as e:
            note(f"Timeout abandoning: {e}")
            wait_for_state_update(self.coord, timeout=10)
            self.model_in_game = self.coord.in_game
            return

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
    def end_turn(self):
        """End the current turn in combat."""
        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        if not game.end_available:
            note("End turn not available, skipping")
            return

        note("Ending turn")
        self.action_history.append("end")

        self.coord.send_message("end")

        # Wait for state update
        try:
            wait_for_state_update(self.coord, timeout=30)
        except GameTimeout:
            pass

        # Check if combat ended (won or lost)
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if not game.in_combat:
                self.model_in_combat = False
                self.model_combat_ended = True
                note("Combat ended after turn")
        elif not self.coord.in_game:
            # Game ended (probably death)
            self.model_in_game = False
            self.model_in_combat = False
            note("Game ended (death?)")

    @precondition(lambda self: self.model_in_game and self.model_in_combat)
    @rule()
    def play_random_card(self):
        """Play a random playable card from hand."""
        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        if not game.play_available or not game.hand:
            note("No cards to play")
            return

        # Find playable cards
        playable = [c for c in game.hand if c.is_playable]
        if not playable:
            note("No playable cards")
            return

        # Prefer untargeted cards to avoid targeting issues
        untargeted = [c for c in playable if not c.has_target]

        if untargeted:
            card = random.choice(untargeted)
            note(f"Playing untargeted card: {card.name}")
            self.action_history.append(f"play {card.name}")
            self.coord.send_message(f"play {game.hand.index(card)}")
        elif game.monsters:
            # Play targeted card on random monster
            card = random.choice(playable)
            target = random.randint(0, len(game.monsters) - 1)
            note(f"Playing {card.name} on monster {target}")
            self.action_history.append(f"play {card.name} -> {target}")
            self.coord.send_message(f"play {game.hand.index(card)} {target}")
        else:
            return

        # Wait for state update
        try:
            wait_for_state_update(self.coord, timeout=30)
        except GameTimeout:
            pass

        # Check if combat/game ended
        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if not game.in_combat:
                self.model_in_combat = False
                self.model_combat_ended = True
                note("Combat ended after card")
        elif not self.coord.in_game:
            self.model_in_game = False
            self.model_in_combat = False
            note("Game ended after card")

    @precondition(lambda self: self.model_in_game and self.model_in_combat)
    @rule()
    def use_random_potion(self):
        """Use a random potion if available."""
        if not self.coord.in_game or not self.coord.last_game_state:
            wait_for_state_update(self.coord, timeout=10)

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.model_in_combat = False
            return

        potions = getattr(game, 'potions', None)
        if not potions:
            return

        # Find usable potions (not empty slots)
        usable = [(i, p) for i, p in enumerate(potions) if p and p.can_use]
        if not usable:
            return

        idx, potion = random.choice(usable)

        if potion.requires_target and game.monsters:
            target = random.randint(0, len(game.monsters) - 1)
            note(f"Using potion {potion.name} on monster {target}")
            self.action_history.append(f"potion {idx} {target}")
            self.coord.send_message(f"potion use {idx} {target}")
        else:
            note(f"Using potion {potion.name}")
            self.action_history.append(f"potion {idx}")
            self.coord.send_message(f"potion use {idx}")

        try:
            wait_for_state_update(self.coord, timeout=30)
        except GameTimeout:
            pass

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

            try:
                wait_for_state_update(self.coord, timeout=30)
            except GameTimeout:
                pass

            self.model_combat_ended = False

    def teardown(self):
        """Clean up after the test - return to main menu."""
        note(f"Teardown. Action history: {self.action_history}")
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass


# Create the test class from the state machine
TestArenaStateful = ArenaStateMachine.TestCase

# Configure the test settings
TestArenaStateful.settings = settings(
    max_examples=50,
    stateful_step_count=20,
    deadline=None,
    suppress_health_check=True,
)


# =============================================================================
# Additional focused state machines
# =============================================================================

class ArenaCombatMachine(RuleBasedStateMachine):
    """
    State machine focused on combat actions within an arena fight.

    Starts already in combat and tests various combat action sequences.
    """

    def __init__(self):
        super().__init__()
        self.coord = None
        self.in_combat = False
        self.turn_count = 0
        self.cards_played_this_turn = 0

    @initialize()
    def setup(self):
        """Start an arena fight."""
        import conftest
        self.coord = conftest._coordinator

        ensure_main_menu(self.coord, timeout=60)

        character = random.choice(CHARACTERS)
        encounter = random.choice(ENCOUNTERS)

        note(f"Starting combat test: {character} vs {encounter}")

        self.coord.send_message(f"arena {character} {encounter}")
        wait_for_in_game(self.coord, timeout=60)
        wait_for_combat(self.coord, timeout=60)

        self.in_combat = True
        self.turn_count = 1
        self.cards_played_this_turn = 0

    @invariant()
    def valid_combat_state(self):
        """Combat state should be consistent."""
        if not self.in_combat:
            return

        if self.coord.in_game and self.coord.last_game_state:
            game = self.coord.last_game_state
            if game.in_combat:
                # Should have hand
                assert game.hand is not None, "No hand in combat"
                # Should have monsters
                assert game.monsters, "No monsters in combat"

    @invariant()
    def turn_count_reasonable(self):
        """Turn count shouldn't exceed reasonable limits."""
        assert self.turn_count <= 100, f"Too many turns: {self.turn_count}"

    @invariant()
    def cards_per_turn_reasonable(self):
        """Cards played per turn shouldn't exceed reasonable limits."""
        assert self.cards_played_this_turn <= 20, \
            f"Too many cards this turn: {self.cards_played_this_turn}"

    @precondition(lambda self: self.in_combat)
    @rule()
    def play_card(self):
        """Play a card from hand."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        game = self.coord.last_game_state
        if not game or not game.in_combat:
            self.in_combat = False
            return

        playable = [c for c in (game.hand or []) if c.is_playable]
        if not playable:
            return

        card = random.choice(playable)
        idx = game.hand.index(card)

        if card.has_target and game.monsters:
            target = random.randint(0, len(game.monsters) - 1)
            self.coord.send_message(f"play {idx} {target}")
        else:
            self.coord.send_message(f"play {idx}")

        self.cards_played_this_turn += 1

        try:
            wait_for_state_update(self.coord, timeout=30)
        except GameTimeout:
            pass

        if not self.coord.in_game or (self.coord.last_game_state and
                                       not self.coord.last_game_state.in_combat):
            self.in_combat = False

    @precondition(lambda self: self.in_combat)
    @rule()
    def end_turn(self):
        """End the current turn."""
        if not self.coord.in_game:
            self.in_combat = False
            return

        game = self.coord.last_game_state
        if not game or not game.in_combat or not game.end_available:
            return

        self.coord.send_message("end")
        self.turn_count += 1
        self.cards_played_this_turn = 0

        try:
            wait_for_state_update(self.coord, timeout=30)
        except GameTimeout:
            pass

        if not self.coord.in_game or (self.coord.last_game_state and
                                       not self.coord.last_game_state.in_combat):
            self.in_combat = False

    def teardown(self):
        """Clean up."""
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass


TestArenaCombat = ArenaCombatMachine.TestCase
TestArenaCombat.settings = settings(
    max_examples=20,
    stateful_step_count=30,
    deadline=None,
    suppress_health_check=True,
)


class ArenaTransitionMachine(RuleBasedStateMachine):
    """
    State machine focused on testing transitions between arena and main menu.

    Rapidly starts and abandons fights to test state management.
    """

    def __init__(self):
        super().__init__()
        self.coord = None
        self.at_menu = True
        self.fights_started = 0
        self.fights_abandoned = 0

    @initialize()
    def setup(self):
        """Initialize at main menu."""
        import conftest
        self.coord = conftest._coordinator
        ensure_main_menu(self.coord, timeout=60)
        self.at_menu = True
        self.fights_started = 0
        self.fights_abandoned = 0

    @invariant()
    def state_consistent(self):
        """Model state should match reality."""
        assert self.at_menu == (not self.coord.in_game), \
            f"Model: at_menu={self.at_menu}, Reality: in_game={self.coord.in_game}"

    @invariant()
    def fight_counts_valid(self):
        """Fight counts should be consistent."""
        assert self.fights_abandoned <= self.fights_started, \
            f"Abandoned {self.fights_abandoned} > started {self.fights_started}"

    @precondition(lambda self: self.at_menu)
    @rule(character=st.sampled_from(CHARACTERS), encounter=st.sampled_from(ENCOUNTERS))
    def start_fight(self, character, encounter):
        """Start an arena fight."""
        note(f"Starting fight #{self.fights_started + 1}: {character} vs {encounter}")

        self.coord.send_message(f"arena {character} {encounter}")

        try:
            wait_for_in_game(self.coord, timeout=60)
            wait_for_combat(self.coord, timeout=30)
        except GameTimeout:
            wait_for_state_update(self.coord, timeout=10)
            self.at_menu = not self.coord.in_game
            return

        self.at_menu = False
        self.fights_started += 1

    @precondition(lambda self: not self.at_menu)
    @rule()
    def abandon_fight(self):
        """Abandon current fight."""
        note(f"Abandoning fight #{self.fights_started}")

        self.coord.send_message("abandon")

        try:
            wait_for_main_menu(self.coord, timeout=60)
        except GameTimeout:
            wait_for_state_update(self.coord, timeout=10)
            self.at_menu = not self.coord.in_game
            return

        self.at_menu = True
        self.fights_abandoned += 1

    def teardown(self):
        """Clean up."""
        note(f"Started {self.fights_started}, abandoned {self.fights_abandoned}")
        try:
            ensure_main_menu(self.coord, timeout=30)
        except GameTimeout:
            pass


TestArenaTransitions = ArenaTransitionMachine.TestCase
TestArenaTransitions.settings = settings(
    max_examples=30,
    stateful_step_count=15,
    deadline=None,
    suppress_health_check=True,
)
