"""
Pytest fixtures for STS Arena acceptance tests.

These tests run within the subprocess that CommunicationMod spawns.
The communicator module handles the actual stdin/stdout communication.
"""

import pytest
import logging

from communicator import get_communicator, GameCommunicator

logger = logging.getLogger(__name__)


@pytest.fixture(scope="session")
def game(request) -> GameCommunicator:
    """
    Session-scoped fixture providing the game communicator.

    The communicator is initialized by run_agent.py before pytest starts.
    """
    return get_communicator()


@pytest.fixture
def at_main_menu(game: GameCommunicator):
    """
    Fixture that ensures we're at the main menu before and after each test.

    Abandons any active run.
    """
    # Before test: ensure at main menu
    assert game.ensure_main_menu(), "Failed to get to main menu before test"

    yield game

    # After test: ensure at main menu for next test
    game.ensure_main_menu()
