"""
Arena-specific communication utilities for STS Arena acceptance tests.

This module extends spirecomm to support arena-specific functionality
by using click commands to navigate the arena UI.

Note: Arena mode uses custom UI screens that aren't directly supported by
CommunicationMod's standard commands. We use click commands to interact
with arena-specific UI elements.
"""

import sys
import logging
from typing import Optional, List, Tuple
from dataclasses import dataclass

from spirecomm.communication.coordinator import Coordinator
from spirecomm.communication.action import Action

logger = logging.getLogger(__name__)


@dataclass
class ScreenCoordinates:
    """Screen coordinates for UI elements (before scaling)."""
    x: float
    y: float


# Arena UI element positions (approximate, will need calibration)
# These are base coordinates that get scaled by Settings.scale
ARENA_BUTTON_MAIN_MENU = ScreenCoordinates(960, 500)  # Position in main menu


class ClickAction(Action):
    """Action to click at specific screen coordinates."""

    def __init__(self, x: float, y: float, button: str = "left", timeout: int = 100):
        super().__init__(f"click {button} {x} {y} {timeout}")
        self.x = x
        self.y = y
        self.button = button
        self.timeout = timeout

    def execute(self, coordinator):
        coordinator.send_message(self.command)


class WaitAction(Action):
    """Action to wait for a specified number of milliseconds."""

    def __init__(self, timeout: int):
        super().__init__(f"wait {timeout}")
        self.timeout = timeout


class ArenaCommunicator:
    """
    Communicator for arena-specific functionality.

    This class wraps a Coordinator and provides methods to navigate
    arena mode using click commands.
    """

    def __init__(self, coordinator: Coordinator):
        self.coordinator = coordinator

    def click(self, x: float, y: float, button: str = "left", timeout: int = 100):
        """Click at the specified coordinates."""
        action = ClickAction(x, y, button, timeout)
        action.execute(self.coordinator)
        self._wait_for_ready()

    def wait(self, timeout: int):
        """Wait for the specified number of milliseconds."""
        action = WaitAction(timeout)
        action.execute(self.coordinator)
        self._wait_for_ready()

    def _wait_for_ready(self):
        """Wait for the game to be ready for the next command."""
        while not self.coordinator.game_is_ready:
            self.coordinator.receive_game_state_update(block=True, perform_callbacks=False)

    def click_arena_button(self):
        """
        Click the Arena Mode button on the main menu.

        Note: This requires the Arena Mode button to be visible on the main menu.
        The exact position may need to be calibrated based on screen resolution.
        """
        if self.coordinator.in_game:
            logger.warning("Cannot click arena button - not in main menu")
            return False

        logger.info("Clicking arena button...")
        self.click(ARENA_BUTTON_MAIN_MENU.x, ARENA_BUTTON_MAIN_MENU.y)
        return True

    def navigate_to_arena(self) -> bool:
        """
        Navigate from main menu to arena mode.

        Returns True if successful, False otherwise.
        """
        if self.coordinator.in_game:
            logger.warning("Cannot navigate to arena - already in game")
            return False

        # Click the arena button
        if not self.click_arena_button():
            return False

        # Wait for arena screen to load
        self.wait(500)

        # TODO: Verify we're on the arena screen
        # This would require parsing the game state to detect custom screens

        return True

    def start_random_arena_fight(self) -> bool:
        """
        Start a random arena fight.

        This assumes we're already in the arena selection screen.
        """
        # TODO: Implement arena fight selection
        # This would involve clicking the "Random Fight" button
        # and then navigating through loadout selection

        logger.warning("start_random_arena_fight not yet implemented")
        return False


# Helper functions for arena testing

def setup_arena_test(coordinator: Coordinator) -> Optional[ArenaCommunicator]:
    """
    Set up for arena testing by navigating to the arena screen.

    Returns an ArenaCommunicator if successful, None otherwise.
    """
    arena = ArenaCommunicator(coordinator)

    if not coordinator.in_game:
        if arena.navigate_to_arena():
            return arena

    return None
