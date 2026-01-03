"""
Game communicator module.

Provides a singleton GameCommunicator instance that handles communication
with Slay the Spire via stdin/stdout (as spawned by CommunicationMod).
"""

import sys
import json
import logging
import time
import select
from typing import Optional
from dataclasses import dataclass, field

# Set up logging to stderr (stdout is for game communication)
logging.basicConfig(
    level=logging.INFO,
    format='[TEST] %(levelname)s: %(message)s',
    stream=sys.stderr
)
logger = logging.getLogger(__name__)


@dataclass
class GameState:
    """Parsed game state from CommunicationMod."""
    ready_for_command: bool = False
    in_game: bool = False
    error: Optional[str] = None
    screen_type: Optional[str] = None
    floor: int = 0
    current_hp: int = 0
    max_hp: int = 0
    available_commands: list = field(default_factory=list)
    raw_state: Optional[dict] = None


class GameCommunicator:
    """
    Handles communication with Slay the Spire via stdin/stdout.

    This runs within the subprocess that CommunicationMod spawns.
    Communication protocol:
    - Send commands as single lines to stdout
    - Receive JSON state updates from stdin
    """

    def __init__(self):
        self.game_is_ready = False
        self.in_game = False
        self.last_state: Optional[GameState] = None
        self._initialized = False

    def signal_ready(self):
        """Signal to CommunicationMod that we're ready to communicate."""
        logger.info("Signaling ready to CommunicationMod...")
        print("ready", flush=True)
        self.game_is_ready = False

    def send_command(self, command: str):
        """Send a command to the game."""
        logger.debug(f"Sending: {command}")
        print(command, flush=True)
        self.game_is_ready = False

    def receive_state(self, timeout: float = 30.0) -> Optional[GameState]:
        """Receive and parse game state. Returns None on timeout."""
        start = time.time()
        while time.time() - start < timeout:
            if select.select([sys.stdin], [], [], 0.1)[0]:
                line = sys.stdin.readline().strip()
                if line:
                    return self._parse_state(line)
        return None

    def _parse_state(self, message: str) -> GameState:
        """Parse a JSON message from CommunicationMod."""
        try:
            data = json.loads(message)
            state = GameState(
                ready_for_command=data.get("ready_for_command", False),
                in_game=data.get("in_game", False),
                error=data.get("error"),
                available_commands=data.get("available_commands", []),
                raw_state=data
            )

            if state.in_game and "game_state" in data:
                gs = data["game_state"]
                state.screen_type = gs.get("screen_type")
                state.floor = gs.get("floor", 0)
                state.current_hp = gs.get("current_hp", 0)
                state.max_hp = gs.get("max_hp", 0)

            self.game_is_ready = state.ready_for_command
            self.in_game = state.in_game
            self.last_state = state

            return state

        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse: {message[:100]}... - {e}")
            return GameState(error=f"Parse error: {e}")

    def wait_for_ready(self, timeout: float = 60.0) -> bool:
        """Wait until the game is ready for commands."""
        start = time.time()
        while time.time() - start < timeout:
            state = self.receive_state(timeout=1.0)
            if state and state.ready_for_command:
                return True
        return False

    def execute(self, command: str, timeout: float = 30.0) -> GameState:
        """Send a command and wait for the response."""
        self.send_command(command)
        state = self.receive_state(timeout=timeout)
        if not state:
            return GameState(error="Timeout waiting for response")
        return state

    def abandon_run(self) -> bool:
        """Abandon the current run and return to main menu."""
        if not self.in_game:
            logger.info("Not in game, nothing to abandon")
            return True

        logger.info("Abandoning current run...")
        state = self.execute("abandon")

        if state.error:
            logger.error(f"Error abandoning run: {state.error}")
            return False

        # Wait for transition
        time.sleep(1.0)

        # Verify at main menu
        state = self.execute("state")
        if not state.in_game:
            logger.info("Successfully returned to main menu")
            return True
        else:
            logger.warning("Still in game after abandon")
            return False

    def ensure_main_menu(self) -> bool:
        """Ensure we're at the main menu. Abandons any active run."""
        state = self.execute("state", timeout=5.0)
        if state.error:
            logger.error(f"Error getting state: {state.error}")
            return False

        if not state.in_game:
            logger.info("Already at main menu")
            return True

        return self.abandon_run()


# Singleton instance
communicator: Optional[GameCommunicator] = None


def initialize_communicator() -> GameCommunicator:
    """Initialize the global communicator and wait for game to be ready."""
    global communicator

    if communicator is not None and communicator._initialized:
        return communicator

    communicator = GameCommunicator()

    # Signal ready to CommunicationMod
    communicator.signal_ready()

    # Wait for first state update
    logger.info("Waiting for game to be ready...")
    if not communicator.wait_for_ready(timeout=60.0):
        raise RuntimeError("Timeout waiting for game to be ready")

    logger.info("Game is ready!")
    communicator._initialized = True

    return communicator


def get_communicator() -> GameCommunicator:
    """Get the global communicator instance. Must be initialized first."""
    if communicator is None:
        raise RuntimeError("Communicator not initialized. Call initialize_communicator() first.")
    return communicator
