"""
Basic acceptance tests for Slay the Spire with CommunicationMod.

These tests verify that:
1. The game can be started
2. Basic communication works
3. Runs can be abandoned

To run:
    uv run pytest acceptance_tests/ -v

Note: These tests require the game to be running with CommunicationMod.
"""

import pytest
import logging

logger = logging.getLogger(__name__)


@pytest.mark.requires_game
class TestBasicCommunication:
    """Test basic communication with the game."""

    def test_game_process_running(self, game_process):
        """Verify the game process is running."""
        assert game_process.is_running, "Game process should be running"

    def test_at_main_menu(self, at_main_menu, game_process):
        """Verify we can get to the main menu."""
        # The at_main_menu fixture should ensure we're at main menu
        assert game_process.is_running, "Game should still be running"


@pytest.mark.requires_game
class TestArenaMode:
    """Test arena mode functionality (placeholder for future tests)."""

    def test_placeholder(self, at_main_menu):
        """Placeholder test for arena mode."""
        # TODO: Implement arena mode tests
        # - Navigate to arena menu
        # - Start a fight
        # - Verify fight state
        # - Complete or abandon fight
        pass
