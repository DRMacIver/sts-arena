#!/usr/bin/env python3
"""
Simple acceptance test that doesn't use pytest's collection mechanism.
This sends "ready" immediately and runs basic tests.
"""

import sys
import time

# Save original stdout BEFORE any imports
_original_stdout = sys.stdout
sys.stdout = sys.stderr

from spirecomm.communication.coordinator import Coordinator

# ANSI color codes
GREEN = "\033[32m"
RED = "\033[31m"
YELLOW = "\033[33m"
RESET = "\033[0m"


def log(msg):
    """Print a log message to stderr."""
    print(msg, file=sys.stderr, flush=True)


def test_state_command(coord):
    """Test that state command works."""
    coord.send_message("state")

    timeout = 30
    start = time.time()
    while not coord.game_is_ready and time.time() - start < timeout:
        coord.receive_game_state_update(block=True, perform_callbacks=False)

    assert coord.game_is_ready, "Game should be ready"
    assert coord.last_error is None, f"Should have no error: {coord.last_error}"


def test_start_game(coord):
    """Test that we can start a game."""
    # If we're in a game, abandon first
    if coord.in_game:
        coord.send_message("abandon")
        time.sleep(2.0)
        coord.send_message("state")
        while not coord.game_is_ready:
            coord.receive_game_state_update(block=True, perform_callbacks=False)

    # Start a new game
    coord.send_message("start IRONCLAD 0")
    time.sleep(3.0)

    coord.send_message("state")
    while not coord.game_is_ready:
        coord.receive_game_state_update(block=True, perform_callbacks=False)

    assert coord.in_game, "Should be in game"
    assert coord.last_game_state is not None, "Should have game state"
    assert coord.last_game_state.current_hp > 0, "Should have HP"


def test_abandon_game(coord):
    """Test that we can abandon a game."""
    # Make sure we're in a game first
    if not coord.in_game:
        coord.send_message("start IRONCLAD 0")
        time.sleep(3.0)
        coord.send_message("state")
        while not coord.game_is_ready:
            coord.receive_game_state_update(block=True, perform_callbacks=False)

    assert coord.in_game, "Should be in game before abandon"

    # Abandon the game
    coord.send_message("abandon")
    time.sleep(2.0)

    coord.send_message("state")
    while not coord.game_is_ready:
        coord.receive_game_state_update(block=True, perform_callbacks=False)

    assert not coord.in_game, "Should not be in game after abandon"


def main():
    """Run simple tests."""
    log("=" * 60)
    log("simple_test.py - STS Arena Acceptance Tests")
    log("=" * 60)

    # Create coordinator and signal ready IMMEDIATELY
    log("Setting up game connection...")
    coord = Coordinator(output_file=_original_stdout)
    coord.signal_ready()

    while not coord.game_is_ready:
        coord.receive_game_state_update(block=True, perform_callbacks=False)
    log("Game is ready!\n")

    # Run tests
    passed = 0
    failed = 0
    errors = []

    tests = [
        test_state_command,
        test_start_game,
        test_abandon_game,
    ]

    for test in tests:
        test_name = f"simple_test.py::{test.__name__}"
        try:
            test(coord)
            log(f"{test_name} {GREEN}PASSED{RESET}")
            passed += 1
        except AssertionError as e:
            log(f"{test_name} {RED}FAILED{RESET}")
            errors.append((test.__name__, "FAILED", str(e)))
            failed += 1
        except Exception as e:
            log(f"{test_name} {RED}ERROR{RESET}")
            errors.append((test.__name__, "ERROR", f"{type(e).__name__}: {e}"))
            failed += 1

    # Print summary
    log("")
    log("=" * 60)

    # Print errors if any
    if errors:
        log(f"{RED}FAILURES:{RESET}")
        for name, status, msg in errors:
            log(f"  {name}: {msg}")
        log("")

    # Final summary line (pytest style)
    total = passed + failed
    if failed == 0:
        log(f"{GREEN}{passed} passed{RESET} in simple_test.py")
    else:
        log(f"{RED}{failed} failed{RESET}, {GREEN}{passed} passed{RESET} in simple_test.py")
    log("=" * 60)

    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
