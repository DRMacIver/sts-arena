"""
Pytest configuration and fixtures for STS Arena acceptance tests.

Architecture:
- pytest runs as the main process
- Session fixture starts the game with Xvfb
- CommunicationMod (in game) spawns our communication bridge
- Fixtures provide a Communicator for tests to use

Usage:
    pytest acceptance_tests/

Environment:
    STSARENA_NO_GAME=1  - Skip starting the game (for unit tests)
"""

import pytest
import os
import sys
import json
import logging
import time
import subprocess
import threading
import queue
from pathlib import Path
from typing import Optional, Generator
from dataclasses import dataclass

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger(__name__)

# Project paths
PROJECT_DIR = Path(__file__).parent.parent
LIB_DIR = PROJECT_DIR / "lib"


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
    raw_state: Optional[dict] = None


class GameCommunicator:
    """
    Handles communication with Slay the Spire via stdin/stdout.

    This class is used within the subprocess that CommunicationMod spawns.
    """

    def __init__(self):
        self.game_is_ready = False
        self.in_game = False
        self.last_state: Optional[GameState] = None
        self.last_error: Optional[str] = None

    def signal_ready(self):
        """Signal to CommunicationMod that we're ready to communicate."""
        print("ready", flush=True)
        self.game_is_ready = False

    def send_command(self, command: str):
        """Send a command to the game."""
        logger.debug(f"Sending: {command}")
        print(command, flush=True)
        self.game_is_ready = False

    def receive_state(self, timeout: float = 30.0) -> Optional[GameState]:
        """
        Receive and parse game state.

        Returns None on timeout.
        """
        import select

        # Wait for input with timeout
        start = time.time()
        while time.time() - start < timeout:
            # Use select to check if stdin has data
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
            self.last_error = state.error

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
        """
        Abandon the current run and return to main menu.

        Uses the 'abandon' command added to CommunicationMod.
        Returns True if successful.
        """
        if not self.in_game:
            logger.info("Not in game, nothing to abandon")
            return True

        logger.info("Abandoning current run...")

        # Use the abandon command (added to CommunicationMod)
        state = self.execute("abandon")

        if state.error:
            logger.error(f"Error abandoning run: {state.error}")
            return False

        # Wait for transition to complete
        time.sleep(1.0)

        # Verify we're at main menu
        state = self.execute("state")
        if not state.in_game:
            logger.info("Successfully returned to main menu")
            return True
        else:
            logger.warning("Still in game after abandon command")
            return False

    def ensure_main_menu(self, timeout: float = 30.0) -> bool:
        """
        Ensure we're at the main menu.

        If in a game, abandons it. Returns True if at main menu.
        """
        # First, get current state
        state = self.execute("state", timeout=5.0)

        if state.error:
            logger.error(f"Error getting state: {state.error}")
            return False

        if not state.in_game:
            logger.info("Already at main menu")
            return True

        # Try to abandon the run
        return self.abandon_run()


class GameProcess:
    """Manages the Slay the Spire game process."""

    def __init__(self, use_xvfb: bool = True):
        self.use_xvfb = use_xvfb
        self.game_process: Optional[subprocess.Popen] = None
        self.xvfb_process: Optional[subprocess.Popen] = None
        self._output_thread: Optional[threading.Thread] = None
        self._output_queue: queue.Queue = queue.Queue()

    def _start_xvfb(self) -> bool:
        """Start Xvfb for headless display."""
        try:
            result = subprocess.run(
                ["xdpyinfo", "-display", ":99"],
                capture_output=True, timeout=2
            )
            if result.returncode == 0:
                logger.info("Xvfb already running on :99")
                return True
        except Exception:
            pass

        logger.info("Starting Xvfb...")
        try:
            self.xvfb_process = subprocess.Popen(
                ["Xvfb", ":99", "-screen", "0", "1920x1080x24"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            time.sleep(1)
            return True
        except FileNotFoundError:
            logger.error("Xvfb not found")
            return False

    def _read_output(self):
        """Thread to read game output."""
        while self.game_process and self.game_process.poll() is None:
            try:
                line = self.game_process.stdout.readline()
                if line:
                    self._output_queue.put(line.strip())
            except Exception:
                break

    def start(self, agent_command: str) -> bool:
        """Start the game with the specified agent command."""
        logger.info("Starting Slay the Spire...")

        if self.use_xvfb and not self._start_xvfb():
            return False

        # Configure CommunicationMod
        from game_runner import configure_communication_mod
        configure_communication_mod(agent_command, run_at_start=True)

        # Start the game
        mts_jar = LIB_DIR / "ModTheSpire.jar"
        if not mts_jar.exists():
            logger.error(f"ModTheSpire.jar not found: {mts_jar}")
            return False

        cmd = [
            "java", "-jar", str(mts_jar),
            "--mods", "basemod,communicationmod,stsarena"
        ]

        env = os.environ.copy()
        if self.use_xvfb:
            env["DISPLAY"] = ":99"

        try:
            self.game_process = subprocess.Popen(
                cmd,
                cwd=str(LIB_DIR),
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )
        except Exception as e:
            logger.error(f"Failed to start game: {e}")
            return False

        # Start output reader thread
        self._output_thread = threading.Thread(target=self._read_output, daemon=True)
        self._output_thread.start()

        logger.info(f"Game started (PID: {self.game_process.pid})")
        return True

    def stop(self):
        """Stop the game process."""
        if self.game_process:
            logger.info("Stopping game...")
            try:
                self.game_process.terminate()
                self.game_process.wait(timeout=5)
            except Exception:
                self.game_process.kill()
            self.game_process = None

        if self.xvfb_process:
            try:
                self.xvfb_process.terminate()
            except Exception:
                pass
            self.xvfb_process = None

    @property
    def is_running(self) -> bool:
        return self.game_process is not None and self.game_process.poll() is None


# Global game process (session-scoped)
_game_process: Optional[GameProcess] = None


@pytest.fixture(scope="session")
def game_process() -> Generator[GameProcess, None, None]:
    """
    Session-scoped fixture that starts the game.

    The game runs for the entire test session.
    """
    global _game_process

    if os.environ.get("STSARENA_NO_GAME"):
        pytest.skip("Game disabled via STSARENA_NO_GAME")

    # Create and start the game
    _game_process = GameProcess(use_xvfb=True)

    # Configure the agent command
    # The agent will be our bridge process
    agent_script = PROJECT_DIR / "acceptance_tests" / "run_agent.py"
    agent_cmd = f"uv run --directory {PROJECT_DIR / 'acceptance_tests'} python {agent_script}"

    if not _game_process.start(agent_cmd):
        pytest.fail("Failed to start game")

    # Wait for game to initialize
    logger.info("Waiting for game to initialize...")
    time.sleep(10)  # Give the game time to start

    yield _game_process

    # Cleanup
    _game_process.stop()
    _game_process = None


@pytest.fixture
def at_main_menu(game_process: GameProcess) -> Generator[None, None, None]:
    """
    Fixture that ensures we're at the main menu before each test.

    Abandons any active run.
    """
    # This fixture currently just yields - actual implementation
    # requires the communication bridge to be working
    logger.info("Ensuring at main menu...")
    yield
    logger.info("Test complete, ensuring main menu for next test...")


# Mark for tests that need the game running
def pytest_configure(config):
    config.addinivalue_line(
        "markers", "requires_game: test requires the game to be running"
    )
