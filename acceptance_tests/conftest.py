"""
Pytest fixtures for STS Arena acceptance tests.

These tests run in a subprocess spawned by run_agent.py.
run_agent.py handles the "ready" signal and creates named pipes for communication.
"""

import os
import pytest
import sys
import time

from spirecomm.communication.coordinator import Coordinator

# Import screenshot utilities (optional - may not be available if mss not installed)
try:
    from screenshot import get_capture, screenshot_on_failure, generate_screenshot_index
    SCREENSHOTS_ENABLED = True
except ImportError:
    SCREENSHOTS_ENABLED = False


DEFAULT_TIMEOUT = 60  # seconds - game takes time to initialize


class GameTimeout(Exception):
    """Raised when waiting for game state times out."""
    pass


def _process_message(coordinator, msg):
    """Process a raw message from the game and update coordinator state."""
    import json
    communication_state = json.loads(msg)
    coordinator.last_error = communication_state.get("error", None)
    coordinator.game_is_ready = communication_state.get("ready_for_command")
    if coordinator.last_error is None:
        coordinator.in_game = communication_state.get("in_game")
        if coordinator.in_game:
            from spirecomm.spire.game import Game
            coordinator.last_game_state = Game.from_json(
                communication_state.get("game_state"),
                communication_state.get("available_commands")
            )


def wait_for_ready(coordinator, timeout=DEFAULT_TIMEOUT):
    """Wait for the game to be ready for commands, with timeout."""
    start = time.time()
    while not coordinator.game_is_ready:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise GameTimeout(f"Timed out after {timeout}s waiting for game to be ready")
        # Block for up to 1 second at a time, checking timeout between
        msg = coordinator.get_next_raw_message(block=True, timeout=min(1.0, remaining))
        if msg is not None:
            _process_message(coordinator, msg)


def wait_for_stable(coordinator, timeout=DEFAULT_TIMEOUT):
    """
    Wait for the game to stabilize after an action.

    This is the primary synchronization primitive for tests. Use this instead
    of time.sleep() to wait for the game state to settle.

    The function:
    1. Resets the ready flag so we wait for a fresh response
    2. Requests the current state from the game
    3. Blocks until the game is ready for the next command

    Args:
        coordinator: The game coordinator
        timeout: Maximum time to wait in seconds

    Returns:
        The coordinator (for chaining)

    Raises:
        GameTimeout: If the game doesn't stabilize within timeout
    """
    coordinator.game_is_ready = False
    coordinator.send_message("state")
    wait_for_ready(coordinator, timeout=timeout)
    return coordinator


# Get pipe paths from environment (set by run_agent.py)
_input_pipe_path = os.environ.get("STS_GAME_INPUT_PIPE")
_output_pipe_path = os.environ.get("STS_GAME_OUTPUT_PIPE")

if not _input_pipe_path or not _output_pipe_path:
    raise RuntimeError("STS_GAME_INPUT_PIPE and STS_GAME_OUTPUT_PIPE must be set")

# Open the named pipes
# Note: opening a FIFO blocks until the other end is also opened,
# so run_agent.py must have its bridge threads running
_game_input = open(_input_pipe_path, "r")
_game_output = open(_output_pipe_path, "w")

# Create coordinator using the pipes
# Don't send "ready" - run_agent.py already did that
_coordinator = Coordinator(input_file=_game_input, output_file=_game_output)

# Wait for initial game state
wait_for_ready(_coordinator)


@pytest.fixture(scope="session")
def coordinator():
    """
    Session-scoped fixture providing the game coordinator.
    The coordinator was already initialized when conftest.py was imported.
    """
    return _coordinator


@pytest.fixture
def at_main_menu(coordinator):
    """
    Fixture that ensures we're at the main menu before and after each test.
    Abandons any active run.
    """
    # Before test: ensure at main menu
    _ensure_main_menu(coordinator)

    yield coordinator

    # After test: ensure at main menu for next test
    _ensure_main_menu(coordinator)


def wait_for_state_update(coordinator, timeout=DEFAULT_TIMEOUT):
    """Request state and wait for response."""
    coordinator.game_is_ready = False
    coordinator.send_message("state")
    wait_for_ready(coordinator, timeout=timeout)


def wait_for_in_game(coordinator, timeout=DEFAULT_TIMEOUT):
    """Block until we're in game using the wait_for command."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for in_game true")
    wait_for_ready(coordinator, timeout=timeout)
    if not coordinator.in_game:
        raise GameTimeout("Expected to be in game but we're at main menu")


def wait_for_main_menu(coordinator, timeout=DEFAULT_TIMEOUT):
    """Block until we're at main menu using the wait_for command."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for main_menu")
    wait_for_ready(coordinator, timeout=timeout)
    if coordinator.in_game:
        raise GameTimeout("Expected to be at main menu but we're in game")


def wait_for_combat(coordinator, timeout=DEFAULT_TIMEOUT):
    """Block until we're in combat using the wait_for command."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for in_combat true")
    wait_for_ready(coordinator, timeout=timeout)
    if not (coordinator.in_game and coordinator.last_game_state and coordinator.last_game_state.in_combat):
        raise GameTimeout("Expected to be in combat")


def _ensure_main_menu(coordinator, timeout=DEFAULT_TIMEOUT):
    """Ensure we're at the main menu. Abandons any active run."""
    # Get current state
    wait_for_state_update(coordinator, timeout=10)

    if not coordinator.in_game:
        return

    # We're in a game - need to abandon
    coordinator.send_message("abandon")

    # Wait for abandon command response first
    wait_for_ready(coordinator)

    # Wait for return to main menu using wait_for command
    wait_for_main_menu(coordinator, timeout=timeout)

    assert not coordinator.in_game, "Should be at main menu after abandon"


# =============================================================================
# Screenshot capture hooks
# =============================================================================

@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """
    Hook to capture screenshots on test completion (both success and failure).

    This runs after each test phase (setup, call, teardown) and captures
    a screenshot when the test call completes.
    """
    outcome = yield
    report = outcome.get_result()

    # Capture on test call completion (not setup/teardown)
    if report.when == "call" and SCREENSHOTS_ENABLED:
        test_name = item.nodeid
        try:
            if report.failed:
                exc_info = call.excinfo
                exception = exc_info.value if exc_info else None
                screenshot_on_failure(test_name, exception)
            else:
                # Also capture on success
                from screenshot import take_screenshot
                take_screenshot(name="success", test_name=test_name)
        except Exception as e:
            # Don't fail the test because of screenshot issues
            print(f"Warning: Failed to capture screenshot: {e}")


def pytest_sessionfinish(session, exitstatus):
    """
    Hook to generate screenshot index at the end of the test session.
    """
    if SCREENSHOTS_ENABLED:
        try:
            index_path = generate_screenshot_index()
            if index_path:
                print(f"\nScreenshot index generated: {index_path}")
        except Exception as e:
            print(f"Warning: Failed to generate screenshot index: {e}")

        # Cleanup
        try:
            get_capture().cleanup()
        except Exception:
            pass


@pytest.fixture
def screenshot(request):
    """
    Fixture to take screenshots during a test.

    Usage:
        def test_something(screenshot):
            # Do some setup
            screenshot("after_setup")

            # Do something
            screenshot("after_action")
    """
    if not SCREENSHOTS_ENABLED:
        # Return a no-op function if screenshots aren't available
        def noop(name=None):
            pass
        yield noop
        return

    test_name = request.node.nodeid

    def take(name=None):
        from screenshot import take_screenshot
        return take_screenshot(name=name, test_name=test_name)

    yield take
