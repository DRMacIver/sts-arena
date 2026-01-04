#!/usr/bin/env python3
"""
STS Arena Acceptance Test Agent.

This script is the entry point that CommunicationMod spawns. It:
1. Sends "ready" immediately to stdout (CommunicationMod protocol)
2. Creates named pipes for game communication
3. Runs pytest in a subprocess with pipe paths in environment
4. Bridges between CommunicationMod's stdin/stdout and the named pipes

To configure CommunicationMod:
    uv run --directory /path/to/acceptance_tests python run_agent.py
"""

import os
import subprocess
import sys
import tempfile
import threading
from pathlib import Path


def bridge_input(source, dest_path):
    """Read from source and write to named pipe."""
    with open(dest_path, "w") as dest:
        while True:
            line = source.readline()
            if not line:
                break
            dest.write(line)
            dest.flush()


def bridge_output(source_path, dest):
    """Read from named pipe and write to dest."""
    with open(source_path, "r") as source:
        while True:
            line = source.readline()
            if not line:
                break
            dest.write(line)
            dest.flush()


def main():
    """Main entry point."""
    # Send ready signal immediately - this is what CommunicationMod waits for
    print("ready", flush=True)

    # Create named pipes in a temp directory
    pipe_dir = tempfile.mkdtemp(prefix="sts-arena-")
    game_to_test = os.path.join(pipe_dir, "game_to_test")  # game state from CommunicationMod
    test_to_game = os.path.join(pipe_dir, "test_to_game")  # commands to CommunicationMod
    os.mkfifo(game_to_test)
    os.mkfifo(test_to_game)

    try:
        # Start bridge threads
        # Bridge: stdin (from game) -> game_to_test pipe
        input_thread = threading.Thread(
            target=bridge_input,
            args=(sys.stdin, game_to_test),
            daemon=True
        )
        input_thread.start()

        # Bridge: test_to_game pipe -> stdout (to game)
        output_thread = threading.Thread(
            target=bridge_output,
            args=(test_to_game, sys.stdout),
            daemon=True
        )
        output_thread.start()

        # Run pytest in a subprocess
        # pytest can use stdout/stderr freely - we communicate via pipes
        test_dir = Path(__file__).parent
        env = os.environ.copy()
        env["STS_GAME_INPUT_PIPE"] = game_to_test
        env["STS_GAME_OUTPUT_PIPE"] = test_to_game

        result = subprocess.run(
            [
                sys.executable, "-m", "pytest",
                str(test_dir),
                "-v",
                "--tb=short",
                "-p", "no:cacheprovider",
            ],
            env=env,
        )

        sys.exit(result.returncode)
    finally:
        # Clean up pipes
        try:
            os.unlink(game_to_test)
            os.unlink(test_to_game)
            os.rmdir(pipe_dir)
        except OSError:
            pass


if __name__ == "__main__":
    main()
