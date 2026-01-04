"""
Pytest fixtures for STS Arena acceptance tests.

These tests run in a subprocess spawned by run_agent.py.
run_agent.py handles the "ready" signal and creates named pipes for communication.
"""

import os
import pytest
import sys
import time

from spirecomm.communication.coordinator import Coordinator


DEFAULT_TIMEOUT = 60  # seconds - game takes time to initialize


class GameTimeout(Exception):
    """Raised when waiting for game state times out."""
    pass


def wait_for_ready(coordinator, timeout=DEFAULT_TIMEOUT):
    """Wait for the game to be ready for commands, with timeout."""
    start = time.time()
    while not coordinator.game_is_ready:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for game to be ready")
        # Block for up to 1 second at a time, checking timeout between
        msg = coordinator.get_next_raw_message(block=True, timeout=min(1.0, remaining))
        if msg is not None:
            # Process the message manually since receive_game_state_update expects to call get_next_raw_message
            import json
            communication_state = json.loads(msg)
            coordinator.last_error = communication_state.get("error", None)
            coordinator.game_is_ready = communication_state.get("ready_for_command")
            if coordinator.last_error is None:
                coordinator.in_game = communication_state.get("in_game")
                if coordinator.in_game:
                    from spirecomm.spire.game import Game
                    coordinator.last_game_state = Game.from_json(
                        communication_state.get("game_state"),
                        communication_state.get("available_commands")
                    )


# Get pipe paths from environment (set by run_agent.py)
_input_pipe_path = os.environ.get("STS_GAME_INPUT_PIPE")
_output_pipe_path = os.environ.get("STS_GAME_OUTPUT_PIPE")

if not _input_pipe_path or not _output_pipe_path:
    raise RuntimeError("STS_GAME_INPUT_PIPE and STS_GAME_OUTPUT_PIPE must be set")

# Open the named pipes
# Note: opening a FIFO blocks until the other end is also opened,
# so run_agent.py must have its bridge threads running
_game_input = open(_input_pipe_path, "r")
_game_output = open(_output_pipe_path, "w")

# Create coordinator using the pipes
# Don't send "ready" - run_agent.py already did that
_coordinator = Coordinator(input_file=_game_input, output_file=_game_output)

# Wait for initial game state
wait_for_ready(_coordinator)


@pytest.fixture(scope="session")
def coordinator():
    """
    Session-scoped fixture providing the game coordinator.
    The coordinator was already initialized when conftest.py was imported.
    """
    return _coordinator


@pytest.fixture
def at_main_menu(coordinator):
    """
    Fixture that ensures we're at the main menu before and after each test.
    Abandons any active run.
    """
    # Before test: ensure at main menu
    _ensure_main_menu(coordinator)

    yield coordinator

    # After test: ensure at main menu for next test
    _ensure_main_menu(coordinator)


def _ensure_main_menu(coordinator, timeout=DEFAULT_TIMEOUT):
    """Ensure we're at the main menu. Abandons any active run."""
    start = time.time()

    def time_remaining():
        return timeout - (time.time() - start)

    # Get current state
    coordinator.send_message("state")
    try:
        wait_for_ready(coordinator, timeout=min(10, time_remaining()))
    except GameTimeout:
        # Game might be in transition, try again
        if time_remaining() <= 0:
            raise
        coordinator.send_message("state")
        wait_for_ready(coordinator, timeout=min(10, time_remaining()))

    if not coordinator.in_game:
        return

    # We're in a game - need to abandon
    coordinator.send_message("abandon")

    # Wait for abandon to complete - poll until we're out of game
    while coordinator.in_game:
        if time_remaining() <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for abandon to complete")
        coordinator.send_message("state")
        try:
            wait_for_ready(coordinator, timeout=min(5, time_remaining()))
        except GameTimeout:
            continue

    assert not coordinator.in_game, "Should be at main menu after abandon"
