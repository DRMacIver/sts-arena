"""
Game runner for launching Slay the Spire for acceptance tests.

CommunicationMod architecture:
- The game is the parent process
- CommunicationMod spawns an external process (our test agent)
- Communication happens via stdin/stdout of the child process

This module handles:
1. Configuring CommunicationMod to spawn our test agent
2. Starting the game with Xvfb for headless display
3. Waiting for tests to complete
"""

import subprocess
import os
import json
import logging
import time
import configparser
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# Default paths (can be overridden)
DEFAULT_PROJECT_DIR = Path(__file__).parent.parent
DEFAULT_LIB_DIR = DEFAULT_PROJECT_DIR / "lib"
DEFAULT_CONFIG_DIR = Path.home() / ".config" / "ModTheSpire"
DEFAULT_PREFS_DIR = Path.home() / "Library" / "Preferences" / "ModTheSpire"


def find_mts_config_dir() -> Path:
    """Find the ModTheSpire config directory."""
    # Linux
    if DEFAULT_CONFIG_DIR.exists():
        return DEFAULT_CONFIG_DIR

    # macOS
    if DEFAULT_PREFS_DIR.exists():
        return DEFAULT_PREFS_DIR

    # Create Linux config dir if nothing exists
    DEFAULT_CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    return DEFAULT_CONFIG_DIR


def configure_communication_mod(agent_command: str, run_at_start: bool = True):
    """
    Configure CommunicationMod to run the specified agent command.

    Args:
        agent_command: The command CommunicationMod should run
        run_at_start: Whether to run the command automatically at game start
    """
    config_dir = find_mts_config_dir() / "CommunicationMod"
    config_dir.mkdir(parents=True, exist_ok=True)

    config_file = config_dir / "config.properties"

    # CommunicationMod uses Java Properties format
    config = configparser.ConfigParser()
    config.optionxform = str  # Preserve case

    # Create the config with no section header (Java Properties style)
    # We'll write it manually to avoid section headers
    config_content = f"""command={agent_command}
runAtGameStart={str(run_at_start).lower()}
verbose=false
maxInitializationTimeout=30
"""

    with open(config_file, 'w') as f:
        f.write(config_content)

    logger.info(f"Configured CommunicationMod: {config_file}")
    logger.info(f"  Command: {agent_command}")


class GameProcess:
    """
    Manages a running Slay the Spire game instance.
    """

    def __init__(
        self,
        project_dir: Path = DEFAULT_PROJECT_DIR,
        use_xvfb: bool = True
    ):
        self.project_dir = Path(project_dir)
        self.lib_dir = self.project_dir / "lib"
        self.use_xvfb = use_xvfb

        self.game_process: Optional[subprocess.Popen] = None
        self.xvfb_process: Optional[subprocess.Popen] = None

    def _start_xvfb(self) -> bool:
        """Start Xvfb for headless display. Returns True if successful."""
        try:
            # Check if Xvfb is already running on :99
            result = subprocess.run(
                ["xdpyinfo", "-display", ":99"],
                capture_output=True,
                timeout=2
            )
            if result.returncode == 0:
                logger.info("Xvfb already running on :99")
                return True
        except Exception:
            pass

        logger.info("Starting Xvfb on :99...")
        try:
            self.xvfb_process = subprocess.Popen(
                ["Xvfb", ":99", "-screen", "0", "1920x1080x24"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            time.sleep(1)  # Give Xvfb time to start
            return True
        except FileNotFoundError:
            logger.error("Xvfb not found - install with: apt-get install xvfb")
            return False

    def _build_classpath(self) -> str:
        """Build the classpath for running the game."""
        jars = [
            self.lib_dir / "desktop-1.0.jar",
            self.lib_dir / "ModTheSpire.jar",
        ]

        # Verify all required jars exist
        for jar in jars:
            if not jar.exists():
                raise FileNotFoundError(f"Required JAR not found: {jar}")

        return ":".join(str(j) for j in jars)

    def start(self, timeout: int = 120) -> bool:
        """
        Start the game process.

        Args:
            timeout: Maximum time to wait for game to start (seconds)

        Returns:
            True if the game started successfully
        """
        logger.info("Starting Slay the Spire...")

        # Start Xvfb if needed
        if self.use_xvfb:
            if not self._start_xvfb():
                return False

        # Build command
        # ModTheSpire is run as a Java JAR that loads the game
        mts_jar = self.lib_dir / "ModTheSpire.jar"

        cmd = [
            "java",
            "-jar", str(mts_jar),
            "--mods", "basemod,communicationmod,stsarena",
        ]

        # Set up environment
        env = os.environ.copy()
        if self.use_xvfb:
            env["DISPLAY"] = ":99"

        # Set working directory to lib so MTS can find desktop-1.0.jar
        logger.info(f"Starting game: {' '.join(cmd)}")
        logger.info(f"Working directory: {self.lib_dir}")

        try:
            self.game_process = subprocess.Popen(
                cmd,
                cwd=str(self.lib_dir),
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )
        except Exception as e:
            logger.error(f"Failed to start game: {e}")
            return False

        logger.info(f"Game process started (PID: {self.game_process.pid})")
        return True

    def wait(self, timeout: Optional[int] = None) -> int:
        """
        Wait for the game process to exit.

        Args:
            timeout: Maximum time to wait (None for infinite)

        Returns:
            Exit code of the game process
        """
        if not self.game_process:
            return -1

        try:
            return self.game_process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            logger.warning("Game process timed out")
            return -1

    def stop(self):
        """Stop the game process."""
        logger.info("Stopping game...")

        if self.game_process:
            try:
                self.game_process.terminate()
                self.game_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.game_process.kill()
            self.game_process = None

        if self.xvfb_process:
            try:
                self.xvfb_process.terminate()
                self.xvfb_process.wait(timeout=2)
            except Exception:
                pass
            self.xvfb_process = None

        logger.info("Game stopped")

    def read_output(self, timeout: float = 0.1) -> Optional[str]:
        """Read a line from the game's stdout/stderr."""
        if not self.game_process or not self.game_process.stdout:
            return None

        try:
            import select
            if select.select([self.game_process.stdout], [], [], timeout)[0]:
                return self.game_process.stdout.readline()
        except Exception:
            pass
        return None

    @property
    def is_running(self) -> bool:
        """Check if the game process is still running."""
        return self.game_process is not None and self.game_process.poll() is None


def run_acceptance_tests(
    test_agent_path: Path,
    project_dir: Path = DEFAULT_PROJECT_DIR,
    timeout: int = 300,
    use_xvfb: bool = True
) -> int:
    """
    Run acceptance tests by starting the game with CommunicationMod.

    Args:
        test_agent_path: Path to the test agent Python script
        project_dir: Project directory
        timeout: Maximum time to wait for tests (seconds)
        use_xvfb: Whether to use Xvfb for headless display

    Returns:
        0 if tests passed, non-zero otherwise
    """
    # Configure CommunicationMod to run our test agent
    # Use uv run to execute the agent in the correct environment
    agent_command = f"uv run --directory {project_dir / 'acceptance_tests'} python {test_agent_path}"
    configure_communication_mod(agent_command, run_at_start=True)

    # Start the game
    game = GameProcess(project_dir=project_dir, use_xvfb=use_xvfb)

    try:
        if not game.start():
            logger.error("Failed to start game")
            return 1

        # Wait for the game to exit (our test agent will communicate and then exit)
        exit_code = game.wait(timeout=timeout)

        # Log any remaining output
        while True:
            line = game.read_output(timeout=0.1)
            if not line:
                break
            logger.info(f"[GAME] {line.strip()}")

        return exit_code

    except KeyboardInterrupt:
        logger.info("Interrupted")
        return 130  # Standard interrupt exit code

    finally:
        game.stop()


if __name__ == "__main__":
    # For testing the game runner directly
    logging.basicConfig(level=logging.INFO)

    test_agent = DEFAULT_PROJECT_DIR / "acceptance_tests" / "run_agent.py"
    exit_code = run_acceptance_tests(test_agent)
    exit(exit_code)
