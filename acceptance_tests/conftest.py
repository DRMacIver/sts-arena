"""
Pytest fixtures for STS Arena acceptance tests.

These tests run within the subprocess that CommunicationMod spawns.
Uses the spirecomm library for communication.
"""

import pytest
import logging
import sys
import time

from spirecomm.communication.coordinator import Coordinator

# Set up logging
logger = logging.getLogger(__name__)

# Get the original stdout that was saved by run_agent.py at the very start
import run_agent
_original_stdout = run_agent.original_stdout

# Initialize the coordinator IMMEDIATELY when conftest.py is imported
# This ensures "ready" is sent before pytest starts collecting tests
logger.info("Creating coordinator (during conftest import)...")
_coordinator = Coordinator(output_file=_original_stdout)
_coordinator.signal_ready()

logger.info("Waiting for initial game state...")
while not _coordinator.game_is_ready:
    _coordinator.receive_game_state_update(block=True, perform_callbacks=False)
logger.info("Game is ready!")


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
        logger.info("Already at main menu")
        return True

    # We're in a game - need to abandon
    logger.info("Abandoning current run...")
    coordinator.send_message("abandon")

    # Wait for state update
    time.sleep(1.0)

    coordinator.send_message("state")
    while not coordinator.game_is_ready:
        coordinator.receive_game_state_update(block=True, perform_callbacks=False)

    if not coordinator.in_game:
        logger.info("Successfully returned to main menu")
        return True
    else:
        logger.warning("Still in game after abandon")
        return False
