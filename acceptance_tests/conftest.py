"""
Pytest fixtures for STS Arena acceptance tests.

These tests run in a subprocess spawned by run_agent.py.
run_agent.py handles the "ready" signal and creates named pipes for communication.
"""

import os
import pytest
import time

from spirecomm.communication.coordinator import Coordinator

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
while not _coordinator.game_is_ready:
    _coordinator.receive_game_state_update(block=True, perform_callbacks=False)


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


def _ensure_main_menu(coordinator) -> bool:
    """Ensure we're at the main menu. Abandons any active run."""
    # Get current state
    coordinator.send_message("state")
    while not coordinator.game_is_ready:
        coordinator.receive_game_state_update(block=True, perform_callbacks=False)

    if not coordinator.in_game:
        return True

    # We're in a game - need to abandon
    coordinator.send_message("abandon")

    # Wait for state update
    time.sleep(1.0)

    coordinator.send_message("state")
    while not coordinator.game_is_ready:
        coordinator.receive_game_state_update(block=True, perform_callbacks=False)

    return not coordinator.in_game
