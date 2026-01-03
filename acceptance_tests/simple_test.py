#!/usr/bin/env python3
"""
Simple acceptance test that doesn't use pytest's collection mechanism.
This sends "ready" immediately and runs basic tests.
"""

import sys
import time
import logging

# Save original stdout BEFORE any imports
_original_stdout = sys.stdout
sys.stdout = sys.stderr

from spirecomm.communication.coordinator import Coordinator

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='[TEST] %(levelname)s: %(message)s',
    stream=sys.stderr
)
logger = logging.getLogger(__name__)


def test_state_command(coord):
    """Test that state command works."""
    logger.info("Testing state command...")
    coord.send_message("state")

    timeout = 30
    start = time.time()
    while not coord.game_is_ready and time.time() - start < timeout:
        coord.receive_game_state_update(block=True, perform_callbacks=False)

    assert coord.game_is_ready, "Game should be ready"
    assert coord.last_error is None, f"Should have no error: {coord.last_error}"
    logger.info("PASSED: state command works")


def test_start_game(coord):
    """Test that we can start a game."""
    logger.info("Testing start game...")

    # If we're in a game, abandon first
    if coord.in_game:
        logger.info("Abandoning current run...")
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
    logger.info("PASSED: can start game")

    return True


def test_abandon_game(coord):
    """Test that we can abandon a game."""
    logger.info("Testing abandon game...")

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
    logger.info("PASSED: can abandon game")

    return True


def main():
    """Run simple tests."""
    logger.info("Starting simple acceptance tests...")

    # Create coordinator and signal ready IMMEDIATELY
    logger.info("Creating coordinator...")
    coord = Coordinator(output_file=_original_stdout)
    coord.signal_ready()

    logger.info("Waiting for game to be ready...")
    while not coord.game_is_ready:
        coord.receive_game_state_update(block=True, perform_callbacks=False)
    logger.info("Game is ready!")

    # Run tests
    passed = 0
    failed = 0

    tests = [
        test_state_command,
        test_start_game,
        test_abandon_game,
    ]

    for test in tests:
        try:
            test(coord)
            passed += 1
        except AssertionError as e:
            logger.error(f"FAILED: {test.__name__}: {e}")
            failed += 1
        except Exception as e:
            logger.error(f"ERROR: {test.__name__}: {e}")
            failed += 1

    logger.info(f"Results: {passed} passed, {failed} failed")

    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
