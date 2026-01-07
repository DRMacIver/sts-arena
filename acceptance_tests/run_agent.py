#!/usr/bin/env python3
"""
STS Arena Acceptance Test Agent.

HOW TO RUN ACCEPTANCE TESTS:
    ./scripts/run-acceptance-tests.sh

The wrapper script handles:
- Building the mod and CommunicationMod
- Setting up a mock Steam installation
- Starting Xvfb for headless testing
- Launching the game with ModTheSpire
- Waiting for test completion

This script (run_agent.py) is spawned BY CommunicationMod, not run directly.
It:
1. Sends "ready" immediately to stdout (CommunicationMod protocol)
2. Creates named pipes for game communication
3. Runs pytest in a subprocess with pipe paths in environment
4. Bridges between CommunicationMod's stdin/stdout and the named pipes

You can pass pytest arguments through the wrapper:
    ./scripts/run-acceptance-tests.sh -k "test_arena"
    ./scripts/run-acceptance-tests.sh --tb=short
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
    # Create named pipes in a temp directory FIRST
    pipe_dir = tempfile.mkdtemp(prefix="sts-arena-")
    game_to_test = os.path.join(
        pipe_dir, "game_to_test"
    )  # game state from CommunicationMod
    test_to_game = os.path.join(
        pipe_dir, "test_to_game"
    )  # commands to CommunicationMod
    os.mkfifo(game_to_test)
    os.mkfifo(test_to_game)

    try:
        # Run pytest in a subprocess
        # pytest can use stdout/stderr freely - we communicate via pipes
        test_dir = Path(__file__).parent
        project_dir = test_dir.parent
        pytest_output_file = project_dir / "lib" / "pytest_output.txt"

        env = os.environ.copy()
        env["STS_GAME_INPUT_PIPE"] = game_to_test
        env["STS_GAME_OUTPUT_PIPE"] = test_to_game

        # Write pytest output to a file AND stderr for unified logging
        # This allows viewing pytest output alongside game logs in real-time
        pytest_debug_file = project_dir / "lib" / "pytest_debug.txt"

        with (
            open(pytest_output_file, "w") as outfile,
            open(pytest_debug_file, "w") as debugfile,
        ):
            # Start bridge threads BEFORE sending ready
            # Bridge: stdin (from game) -> game_to_test pipe
            input_thread = threading.Thread(
                target=bridge_input, args=(sys.stdin, game_to_test), daemon=True
            )
            input_thread.start()

            # Bridge: test_to_game pipe -> stdout (to game)
            output_thread = threading.Thread(
                target=bridge_output,
                args=(test_to_game, sys.stdout),
                daemon=True,
            )
            output_thread.start()

            # NOW send ready - CommunicationMod will respond to stdin,
            # which bridge_input is now ready to receive
            print("ready", flush=True)

            # Allow passing additional pytest arguments via command line
            pytest_args = [
                sys.executable,
                "-m",
                "pytest",
                "--ff",
                "--maxfail=1",
                "-v",
                "-s",  # Disable output capturing
                "--tb=short",
            ]
            # Add test directory or specific test files from command line
            if len(sys.argv) > 1:
                pytest_args.extend(sys.argv[1:])
            else:
                pytest_args.append(str(test_dir))

            # Run pytest with PIPE to capture output, then tee to file and stderr
            proc = subprocess.Popen(
                pytest_args,
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )

            def tee_stream(src, dest_file, dest_stderr):
                """Read from source and write to both file and stderr."""
                for line in iter(src.readline, b""):
                    text = line.decode("utf-8", errors="replace")
                    dest_file.write(text)
                    dest_file.flush()
                    dest_stderr.write(text)
                    dest_stderr.flush()

            # Start tee threads for both stdout and stderr
            stdout_tee = threading.Thread(
                target=tee_stream,
                args=(proc.stdout, outfile, sys.stderr),
                daemon=True,
            )
            stderr_tee = threading.Thread(
                target=tee_stream,
                args=(proc.stderr, debugfile, sys.stderr),
                daemon=True,
            )
            stdout_tee.start()
            stderr_tee.start()

            # Wait for subprocess and threads
            proc.wait()
            stdout_tee.join(timeout=5)
            stderr_tee.join(timeout=5)

            result = proc

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
