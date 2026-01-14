"""
Pytest fixtures for STS Arena acceptance tests.

HOW TO RUN:
    ./scripts/run-acceptance-tests.sh

These tests run in a subprocess spawned by run_agent.py, which is itself
spawned by CommunicationMod when the game starts. The wrapper script
(run-acceptance-tests.sh) handles all the setup.

See run_agent.py docstring for more details.
"""

import os
import sys
import time

import pytest
from spirecomm.communication.coordinator import Coordinator

# Import screenshot utilities (optional - may not be available if mss not installed)
try:
    from screenshot import (
        finalize_stateful_trackers,
        generate_screenshot_index,
        get_capture,
        get_stateful_tracker,
        screenshot_on_failure,
    )

    SCREENSHOTS_ENABLED = True
except ImportError:
    SCREENSHOTS_ENABLED = False


DEFAULT_TIMEOUT = 60  # seconds - game takes time to initialize


class GameTimeout(Exception):
    """Raised when waiting for game state times out.

    Inherits from BaseException (not Exception) so that Hypothesis won't
    catch it and try to minimize - timeouts are test infrastructure failures,
    not test logic failures.
    """

    pass


def _process_message(coordinator, msg):
    """Process a raw message from the game and update coordinator state."""
    import json

    communication_state = json.loads(msg)
    coordinator.last_error = communication_state.get("error", None)
    # Only update last_message if a message is present (don't overwrite with None)
    msg_value = communication_state.get("message")
    if msg_value is not None:
        coordinator.last_message = msg_value
    coordinator.game_is_ready = communication_state.get("ready_for_command")
    if coordinator.last_error is None:
        coordinator.in_game = communication_state.get("in_game")
        # Debug: save the raw in_game value we received
        coordinator._last_in_game_raw = communication_state.get("in_game")
        if coordinator.in_game:
            from spirecomm.spire.game import Game

            coordinator.last_game_state = Game.from_json(
                communication_state.get("game_state"),
                communication_state.get("available_commands"),
            )


def drain_pending_messages(coordinator, max_drain=10):
    """Drain any pending messages from the queue without blocking.

    This is useful for clearing stale messages between tests.
    Returns the number of messages drained.
    """
    count = 0
    while count < max_drain:
        msg = coordinator.get_next_raw_message(block=False)
        if msg is None:
            break
        _process_message(coordinator, msg)
        count += 1
    return count


def wait_for_ready(coordinator, timeout=DEFAULT_TIMEOUT):
    """Wait for the game to be ready for commands, with timeout."""
    start = time.time()
    while not coordinator.game_is_ready:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise GameTimeout(
                f"Timed out after {timeout}s waiting for game to be ready"
            )
        # Block for up to 1 second at a time, checking timeout between
        msg = coordinator.get_next_raw_message(
            block=True, timeout=min(1.0, remaining)
        )
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
    raise RuntimeError(
        "STS_GAME_INPUT_PIPE and STS_GAME_OUTPUT_PIPE must be set.\n"
        "\n"
        "These tests cannot be run directly with pytest. They must be run through\n"
        "the acceptance test harness, which starts the game and sets up communication.\n"
        "\n"
        "To run the tests, use:\n"
        "\n"
        "    ./scripts/run-acceptance-tests.sh [pytest args...]\n"
        "\n"
        "Examples:\n"
        "    ./scripts/run-acceptance-tests.sh                    # Run all tests\n"
        "    ./scripts/run-acceptance-tests.sh -k test_basic      # Run specific tests\n"
        "    ./scripts/run-acceptance-tests.sh test_generate_screenshots.py  # Run one file\n"
        "\n"
        "The script will:\n"
        "  1. Build the mod\n"
        "  2. Start the game with ModTheSpire in Xvfb\n"
        "  3. Configure CommunicationMod to run the tests\n"
        "  4. Wait for tests to complete\n"
    )

# Open the named pipes
# Note: opening a FIFO blocks until the other end is also opened,
# so run_agent.py must have its bridge threads running
_game_input = open(_input_pipe_path, "r")
_game_output = open(_output_pipe_path, "w")

# Create coordinator using the pipes
# Don't send "ready" - run_agent.py already did that
_coordinator = Coordinator(input_file=_game_input, output_file=_game_output)
_coordinator.last_message = None  # Initialize message field

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
    # Wrap in try-except so teardown issues don't mark passing tests as ERROR
    # The next test's setup will try to recover anyway
    try:
        _ensure_main_menu(coordinator)
    except GameTimeout as e:
        print(f"Warning: Teardown failed to return to main menu: {e}")


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
    # First wait for the main_menu condition to be met
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for main_menu")
    wait_for_ready(coordinator, timeout=timeout)

    # Then wait for visual stability to ensure the transition is complete
    # This handles the case where we receive an intermediate state before
    # the game has fully transitioned to main menu
    wait_for_visual_stable(coordinator, timeout=timeout)

    print(f"[DEBUG wait_for_main_menu] in_game={coordinator.in_game}, raw_in_game={getattr(coordinator, '_last_in_game_raw', 'N/A')}", file=sys.stderr, flush=True)
    if coordinator.in_game:
        raise GameTimeout("Expected to be at main menu but we're in game")


def wait_for_combat(coordinator, timeout=DEFAULT_TIMEOUT):
    """Block until we're in combat using the wait_for command."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for in_combat true")
    wait_for_ready(coordinator, timeout=timeout)
    if not (
        coordinator.in_game
        and coordinator.last_game_state
        and coordinator.last_game_state.in_combat
    ):
        raise GameTimeout("Expected to be in combat")


def select_save_slot(coordinator, slot=0, timeout=DEFAULT_TIMEOUT):
    """Click on a save slot button if the Save Slot screen is showing.

    The game shows a Save Slot selection screen when no slot has been chosen.
    This function clicks on one of the three slot buttons to dismiss it.

    Args:
        coordinator: The game coordinator
        slot: Which slot to select (0, 1, or 2)
        timeout: Timeout for the click command

    The screen is 1280x720 and the slot buttons are centered vertically:
    - Slot 0: center y ~= 261 (from top)
    - Slot 1: center y ~= 401
    - Slot 2: center y ~= 541
    """
    import time

    # Slot button Y coordinates (from top of screen)
    slot_y = {0: 261, 1: 401, 2: 541}
    y = slot_y.get(slot, 261)
    x = 640  # Center of 1280 width screen

    print(f"[select_save_slot] Clicking on save slot {slot} at ({x}, {y})")
    coordinator.game_is_ready = False
    coordinator.send_message(f"click LEFT {x} {y}")
    wait_for_ready(coordinator, timeout=timeout)
    time.sleep(0.5)  # Let the UI update


class VisualStabilityTimeout(Exception):
    """Raised when visual stability wait times out in the game.

    Inherits from BaseException (not Exception) so that Hypothesis won't
    catch it and try to minimize - timeouts are test infrastructure failures,
    not test logic failures.
    """

    pass


def wait_for_visual_stable(coordinator, timeout=DEFAULT_TIMEOUT):
    """Block until visual effects have completed.

    This waits for:
    - No fading in/out
    - No screen swap in progress
    - Room wait timer is zero
    - CardCrawlGame screen timer is zero

    Use this before taking screenshots to ensure the screen shows
    the stable state rather than mid-transition.

    Raises:
        VisualStabilityTimeout: If the game reports a timeout waiting for visual stability
        GameTimeout: If we time out waiting for the game to respond
    """
    import sys

    print(
        f"[wait_for_visual_stable] Sending wait_for visual_stable command...",
        file=sys.stderr,
        flush=True,
    )
    coordinator.game_is_ready = False
    coordinator.last_error = None
    coordinator.send_message("wait_for visual_stable")
    print(
        f"[wait_for_visual_stable] Waiting for response (timeout={timeout}s)...",
        file=sys.stderr,
        flush=True,
    )
    wait_for_ready(coordinator, timeout=timeout)
    print(
        f"[wait_for_visual_stable] Got response! error={coordinator.last_error}",
        file=sys.stderr,
        flush=True,
    )

    # Check if the game reported a timeout error
    if (
        coordinator.last_error
        and "visual stability" in coordinator.last_error.lower()
    ):
        raise VisualStabilityTimeout(coordinator.last_error)


def _ensure_main_menu(coordinator, timeout=DEFAULT_TIMEOUT, max_retries=2):
    """Ensure we're at the main menu. Abandons any active run.

    This function is critical for test isolation. It must:
    1. Clear any stale messages from previous tests/examples
    2. Get a fresh view of the actual game state
    3. Return to menu if we're in a game

    Args:
        coordinator: The game coordinator
        timeout: Timeout for each operation
        max_retries: Number of times to retry if communication fails
    """
    import time

    last_error = None
    for attempt in range(max_retries + 1):
        try:
            # Reset coordinator state to force a fresh read from the game
            coordinator.game_is_ready = False
            coordinator.last_error = None

            # Drain any pending messages that might be queued
            # Use larger max_drain on retry attempts
            max_drain = 10 if attempt == 0 else 50
            drained = drain_pending_messages(coordinator, max_drain=max_drain)

            # Delay to allow messages to arrive - longer on retries
            delay = 0.1 if attempt == 0 else 1.0
            time.sleep(delay)

            # Drain again to catch any messages that arrived during the delay
            drain_pending_messages(coordinator, max_drain=max_drain)

            # Get current state - use shorter timeout on first attempt
            state_timeout = 30 if attempt == 0 else 15
            wait_for_state_update(coordinator, timeout=state_timeout)

            if not coordinator.in_game:
                return

            # We're in a game - need to abandon
            coordinator.send_message("abandon")

            # Wait for abandon command response first
            wait_for_ready(coordinator)

            # Wait for return to main menu using wait_for command
            wait_for_main_menu(coordinator, timeout=timeout)

            assert not coordinator.in_game, "Should be at main menu after abandon"
            return

        except GameTimeout as e:
            last_error = e
            if attempt < max_retries:
                print(
                    f"Warning: _ensure_main_menu attempt {attempt + 1} timed out, "
                    f"retrying..."
                )
                # Aggressive drain before retry
                drain_pending_messages(coordinator, max_drain=100)
                time.sleep(2.0)  # Give game time to recover
            else:
                raise


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

        # Wait for visual stability before taking screenshot
        # This ensures we capture the actual screen state, not a transition
        # Note: GameTimeout and VisualStabilityTimeout are NOT caught here -
        # they indicate real problems that should fail the test
        wait_for_visual_stable(_coordinator, timeout=10)

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
            # Only catch screenshot-specific errors (file I/O, mss errors, etc.)
            # NOT game communication timeouts
            print(f"Warning: Failed to capture screenshot: {e}")


def pytest_sessionfinish(session, exitstatus):
    """
    Hook to generate screenshot index at the end of the test session.
    """
    if SCREENSHOTS_ENABLED:
        try:
            # Finalize stateful test trackers first (generates per-run indices)
            finalize_stateful_trackers()

            # Then generate main index (includes links to stateful tests)
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


# =============================================================================
# Arena transition test helpers
# =============================================================================


def start_normal_run(coordinator, character="IRONCLAD", ascension=0, timeout=DEFAULT_TIMEOUT):
    """
    Start a normal (non-arena) run and wait until in game.

    Args:
        coordinator: The game coordinator
        character: Character class (IRONCLAD, SILENT, DEFECT, WATCHER)
        ascension: Ascension level (0-20)
        timeout: Timeout for the operation

    Returns:
        The coordinator for chaining
    """
    coordinator.send_message(f"start {character} {ascension}")
    wait_for_ready(coordinator, timeout=timeout)
    wait_for_in_game(coordinator, timeout=timeout)
    return coordinator


def reach_first_combat(coordinator, timeout=120):
    """
    Progress a normal run until we reach the first combat room.

    This uses keyboard navigation to select a combat node on the map
    and proceed to it.

    Args:
        coordinator: The game coordinator
        timeout: Maximum time to wait for combat

    Returns:
        The coordinator for chaining
    """
    import time

    start_time = time.time()

    # Wait for map screen to be available
    wait_for_stable(coordinator, timeout=30)

    # Try to find and enter a combat room
    # The map starts with floor 0, and there are always combat nodes available
    # We'll use the "proceed" command to auto-select and enter the next room
    while time.time() - start_time < timeout:
        wait_for_stable(coordinator)
        game = coordinator.last_game_state

        if game and game.in_combat:
            return coordinator

        # If we're on the map, try to proceed
        if game and hasattr(game, 'screen_type'):
            coordinator.send_message("proceed")
            wait_for_ready(coordinator)
            time.sleep(0.5)

    raise GameTimeout(f"Timed out after {timeout}s waiting to reach combat")


def open_pause_menu(coordinator, timeout=DEFAULT_TIMEOUT):
    """
    Open the pause menu (settings screen).

    Args:
        coordinator: The game coordinator
        timeout: Timeout for the operation
    """
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator, timeout=timeout)
    # Give the menu time to open
    import time
    time.sleep(0.3)


def get_arena_state(coordinator, timeout=DEFAULT_TIMEOUT):
    """
    Query the current arena state flags.

    Returns a dict with:
        - is_arena_run: bool
        - arena_run_in_progress: bool
        - started_from_normal_run: bool
        - has_marker_file: bool
        - results_screen_open: bool
        - current_encounter: str or None
        - current_loadout_id: int

    Note: The arena_state command logs to the game log but doesn't return
    the state in the message field. We infer state from other indicators.
    """
    coordinator.send_message("arena_state")
    wait_for_ready(coordinator, timeout=timeout)
    # The state is logged but not directly returned
    # Tests should check coordinator.in_game and last_game_state instead
    return coordinator


def get_player_snapshot(coordinator):
    """
    Capture the current player state for comparison.

    Returns a dict with:
        - hp: current HP
        - max_hp: maximum HP
        - deck: list of card IDs
        - relics: list of relic IDs
        - potions: list of potion IDs
        - floor: current floor number
        - gold: current gold

    Returns None if not in game or no player state available.
    """
    if not coordinator.in_game or not coordinator.last_game_state:
        return None

    game = coordinator.last_game_state
    player = getattr(game, 'player', None)
    if not player:
        return None

    return {
        'hp': getattr(game, 'current_hp', 0),
        'max_hp': getattr(game, 'max_hp', 0),
        'deck': [card.card_id for card in getattr(game, 'deck', [])],
        'relics': [relic.relic_id for relic in getattr(game, 'relics', [])],
        'potions': [p.potion_id for p in getattr(game, 'potions', []) if hasattr(p, 'potion_id')],
        'floor': getattr(game, 'floor', 0),
        'gold': getattr(game, 'gold', 0),
    }


def practice_in_arena(coordinator, encounter=None, timeout=DEFAULT_TIMEOUT):
    """
    Execute the Practice in Arena command.

    This saves the current player state and starts an arena fight.
    Must be called while in combat during a normal run.

    Args:
        coordinator: The game coordinator
        encounter: Optional encounter to use (defaults to current combat encounter)
        timeout: Timeout for the operation
    """
    if encounter:
        coordinator.send_message(f"practice_in_arena {encounter}")
    else:
        coordinator.send_message("practice_in_arena")
    wait_for_ready(coordinator, timeout=timeout)


def leave_arena(coordinator, timeout=DEFAULT_TIMEOUT):
    """
    Leave arena mode.

    If the arena was started from a normal run (via Practice in Arena),
    this resumes the normal run. Otherwise, it returns to the main menu.

    Args:
        coordinator: The game coordinator
        timeout: Timeout for the operation
    """
    coordinator.send_message("leave_arena")
    wait_for_ready(coordinator, timeout=timeout)


def click_results_button(coordinator, button="continue", timeout=DEFAULT_TIMEOUT):
    """
    Click a button on the arena results screen.

    Args:
        coordinator: The game coordinator
        button: Which button to click ("continue", "rematch", or "modify")
        timeout: Timeout for the operation
    """
    coordinator.send_message(f"results_button {button}")
    wait_for_ready(coordinator, timeout=timeout)


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
