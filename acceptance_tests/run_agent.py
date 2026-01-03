#!/usr/bin/env python3
"""
STS Arena Acceptance Test Agent.

This script is the entry point that CommunicationMod spawns. It:
1. Signals ready to CommunicationMod
2. Runs acceptance tests against the game
3. Reports results to stderr (stdout is for game communication)

To configure:
    Set CommunicationMod's command to:
    uv run --directory /path/to/acceptance_tests python run_agent.py
"""

import sys
import json
import logging
import time
import select
from typing import Optional
from dataclasses import dataclass

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
    available_commands: list = None
    raw_state: Optional[dict] = None

    def __post_init__(self):
        if self.available_commands is None:
            self.available_commands = []


class GameCommunicator:
    """
    Handles communication with Slay the Spire via stdin/stdout.
    This runs within the subprocess that CommunicationMod spawns.
    """

    def __init__(self):
        self.game_is_ready = False
        self.in_game = False
        self.last_state: Optional[GameState] = None

    def signal_ready(self):
        """Signal to CommunicationMod that we're ready."""
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
        """Ensure we're at the main menu."""
        state = self.execute("state", timeout=5.0)
        if state.error:
            logger.error(f"Error getting state: {state.error}")
            return False

        if not state.in_game:
            logger.info("Already at main menu")
            return True

        return self.abandon_run()


class TestRunner:
    """Runs acceptance tests against the game."""

    def __init__(self, comm: GameCommunicator):
        self.comm = comm
        self.results = []
        self.current_test = None

    def run_all_tests(self):
        """Run all acceptance tests."""
        logger.info("=== Starting Acceptance Tests ===")

        # Ensure we're at main menu first
        self.start_test("ensure_main_menu")
        if self.comm.ensure_main_menu():
            self.pass_test("At main menu")
        else:
            self.fail_test("Could not get to main menu")
            return  # Can't proceed if not at main menu

        # Test: State command works
        self.start_test("state_command")
        state = self.comm.execute("state")
        if not state.error:
            self.pass_test(f"Got state: in_game={state.in_game}")
        else:
            self.fail_test(f"State error: {state.error}")

        # Test: Available commands at main menu
        self.start_test("main_menu_commands")
        if "start" in state.available_commands:
            self.pass_test(f"Commands: {state.available_commands}")
        else:
            self.fail_test(f"Expected 'start' in commands: {state.available_commands}")

        # Test: Start a game
        self.start_test("start_game")
        state = self.comm.execute("start ironclad 0")
        # Wait for game to start
        time.sleep(2.0)
        state = self.comm.execute("state")
        if state.in_game:
            self.pass_test(f"Game started: HP={state.current_hp}/{state.max_hp}")
        else:
            self.fail_test("Failed to start game")
            return

        # Test: Abandon command works
        self.start_test("abandon_command")
        if self.comm.abandon_run():
            self.pass_test("Successfully abandoned run")
        else:
            self.fail_test("Failed to abandon run")

        self.report_results()

    def start_test(self, name: str):
        """Start a new test."""
        self.current_test = name
        logger.info(f"TEST: {name}")

    def pass_test(self, message: str = ""):
        """Mark current test as passed."""
        self.results.append({"name": self.current_test, "passed": True, "message": message})
        logger.info(f"  PASS: {message}")
        self.current_test = None

    def fail_test(self, message: str):
        """Mark current test as failed."""
        self.results.append({"name": self.current_test, "passed": False, "message": message})
        logger.error(f"  FAIL: {message}")
        self.current_test = None

    def report_results(self):
        """Report test results."""
        logger.info("=== Test Results ===")
        passed = sum(1 for r in self.results if r["passed"])
        total = len(self.results)

        for result in self.results:
            status = "PASS" if result["passed"] else "FAIL"
            logger.info(f"  [{status}] {result['name']}: {result['message']}")

        logger.info(f"=== {passed}/{total} tests passed ===")

        if passed < total:
            sys.exit(1)


def main():
    """Main entry point."""
    logger.info("=== STS Arena Acceptance Test Agent ===")

    comm = GameCommunicator()

    # Signal ready to CommunicationMod
    comm.signal_ready()

    # Wait for first state update
    logger.info("Waiting for game to be ready...")
    if not comm.wait_for_ready(timeout=60.0):
        logger.error("Timeout waiting for game to be ready")
        sys.exit(1)

    logger.info("Game is ready!")

    # Run tests
    runner = TestRunner(comm)
    runner.run_all_tests()


if __name__ == "__main__":
    main()
