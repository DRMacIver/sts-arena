#!/usr/bin/env python3
"""
Screenshot generator for STS Arena documentation.

This script captures screenshots of all key screens in the STS Arena mod
for use in documentation. It uses the same infrastructure as the acceptance
tests to connect to and control the game.

Usage:
    Run via the acceptance test framework:
    ./scripts/run-acceptance-tests.sh test_generate_screenshots.py

This file is a pytest test module that leverages the existing test fixtures.
"""

import time
from pathlib import Path

import pytest

from conftest import (
    wait_for_combat,
    wait_for_main_menu,
    wait_for_ready,
    wait_for_visual_stable,
    wait_for_in_game,
    wait_for_state_update,
)
from spirecomm.spire.screen import ScreenType

# Try to import mss for screenshots
try:
    import mss
    import mss.tools

    HAS_MSS = True
except ImportError:
    HAS_MSS = False
    print("Warning: mss not installed, screenshots will be skipped")

try:
    from PIL import Image

    HAS_PIL = True
except ImportError:
    HAS_PIL = False

# Screenshot output directory - save to docs/screenshots
SCREENSHOT_DIR = Path(__file__).parent.parent / "docs" / "screenshots"


def capture_screenshot(name: str) -> Path:
    """Capture a screenshot and save it to the docs/screenshots directory.

    The game runs at 1280x720 centered in the virtual screen (1920x1080).
    We crop to just the game area to avoid black borders.
    """
    if not HAS_MSS:
        print(f"  [SKIP] {name} (mss not available)")
        return None

    # Ensure directory exists
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
    filepath = SCREENSHOT_DIR / f"{name}.png"

    # Game window dimensions and position (centered in 1920x1080)
    GAME_WIDTH = 1280
    GAME_HEIGHT = 720
    SCREEN_WIDTH = 1920
    SCREEN_HEIGHT = 1080
    GAME_X = (SCREEN_WIDTH - GAME_WIDTH) // 2  # 320
    GAME_Y = (SCREEN_HEIGHT - GAME_HEIGHT) // 2  # 180

    try:
        with mss.mss() as sct:
            # Define the game window region
            game_region = {
                "left": GAME_X,
                "top": GAME_Y,
                "width": GAME_WIDTH,
                "height": GAME_HEIGHT,
            }

            # Capture just the game window area
            sct_img = sct.grab(game_region)

            # Save to file
            mss.tools.to_png(sct_img.rgb, sct_img.size, output=str(filepath))

            # Optionally optimize if PIL is available
            if HAS_PIL:
                img = Image.open(filepath)
                img.save(filepath, optimize=True)

            print(f"  [OK] {name}")
            return filepath
    except Exception as e:
        print(f"  [ERROR] {name}: {e}")
        return None


def open_arena_screen(coordinator, screen_name):
    """Open an arena screen and wait for it to be ready."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena_screen {screen_name}")
    wait_for_ready(coordinator)
    time.sleep(0.5)  # Let screen render
    wait_for_visual_stable(coordinator)


def find_monster_room_index(coordinator):
    """Find the index of a monster room in the available map choices.

    Returns the index of a room with symbol 'M' (monster) or 'E' (elite),
    or 0 if none found (fallback).
    """
    game = coordinator.last_game_state
    if not game or game.screen_type != ScreenType.MAP:
        print("  Warning: Not on map screen, falling back to index 0")
        return 0

    # The map screen has next_nodes which are the available choices
    if hasattr(game.screen, 'next_nodes') and game.screen.next_nodes:
        for i, node in enumerate(game.screen.next_nodes):
            if node.symbol in ('M', 'E'):  # Monster or Elite
                print(f"  Found fight room at index {i} (symbol: {node.symbol})")
                return i

    # Fallback - just use index 0 and hope for the best
    print("  Warning: No monster room found, using index 0")
    return 0


def navigate_to_combat(coordinator, timeout=60):
    """Navigate from current position to combat.

    This handles the flow from Neow event to first combat:
    1. If on EVENT screen (Neow), choose option 0
    2. If on CARD_REWARD screen, skip it
    3. If on MAP screen, choose a monster room
    4. Wait for combat to start

    Args:
        coordinator: The game coordinator
        timeout: Maximum time to spend navigating

    Returns:
        True if we successfully entered combat
    """
    import time
    start = time.time()
    max_iterations = 30
    event_choices_made = 0

    for iteration in range(max_iterations):
        if time.time() - start > timeout:
            print("  Error: Timed out navigating to combat")
            return False

        # Always wait a bit between checks to let the game process
        time.sleep(0.5)

        wait_for_state_update(coordinator)
        game = coordinator.last_game_state

        if not game:
            print("  No game state, waiting...")
            time.sleep(1.0)
            continue

        # Check if we're already in combat
        if game.in_combat:
            print("  Successfully entered combat")
            return True

        screen_type = game.screen_type
        print(f"  [{iteration}] Screen: {screen_type}, in_combat: {game.in_combat}")

        if screen_type == ScreenType.EVENT:
            # Neow event has multiple dialogue phases before showing blessing choices
            # We need to keep clicking through until we get past the event
            # Allow up to 5 event choices (2-3 for dialogue, 1 for blessing choice)
            if event_choices_made < 5:
                print(f"  Choosing event option 0 (choice {event_choices_made + 1})")
                coordinator.game_is_ready = False
                coordinator.send_message("choose 0")
                wait_for_ready(coordinator)
                event_choices_made += 1
                time.sleep(1.0)  # Give event time to process
            else:
                # Made too many choices, try proceed to dismiss
                print("  Too many event choices, trying proceed...")
                coordinator.game_is_ready = False
                coordinator.send_message("proceed")
                wait_for_ready(coordinator)
                time.sleep(1.0)

        elif screen_type == ScreenType.CARD_REWARD:
            # Card reward screen - skip it
            print("  Skipping card reward")
            coordinator.game_is_ready = False
            coordinator.send_message("skip")
            wait_for_ready(coordinator)
            time.sleep(0.5)

        elif screen_type == ScreenType.MAP:
            # Map screen - find and choose a monster room
            monster_idx = find_monster_room_index(coordinator)
            print(f"  Choosing map node {monster_idx}")
            coordinator.game_is_ready = False
            coordinator.send_message(f"choose {monster_idx}")
            wait_for_ready(coordinator)
            # Use wait_for to wait for combat instead of fixed sleep
            print("  Waiting for combat...")
            coordinator.game_is_ready = False
            coordinator.send_message("wait_for in_combat true")
            wait_for_ready(coordinator, timeout=30)
            return coordinator.last_game_state and coordinator.last_game_state.in_combat

        elif screen_type == ScreenType.NONE:
            # Between screens - try pressing proceed or opening map
            print("  Screen type NONE, trying to open map")
            coordinator.game_is_ready = False
            coordinator.send_message("key MAP")
            wait_for_ready(coordinator)
            time.sleep(1.0)

        else:
            # Unknown screen, try proceed
            print(f"  Unknown screen {screen_type}, trying proceed")
            coordinator.game_is_ready = False
            coordinator.send_message("proceed")
            wait_for_ready(coordinator)
            time.sleep(0.5)

    print("  Error: Max iterations reached without entering combat")
    return False


def get_loadout_id(coordinator, index=0):
    """Get the ID of a loadout by its index in the list.

    Returns the loadout ID, or None if not found.
    """
    import json

    coordinator.game_is_ready = False
    coordinator.send_message("arena-loadout list")
    wait_for_ready(coordinator)

    # Parse the loadout list from the message
    if coordinator.last_message:
        try:
            loadouts = json.loads(coordinator.last_message)
            if loadouts and len(loadouts) > index:
                return loadouts[index].get("id")
        except json.JSONDecodeError:
            print(f"  Warning: Could not parse loadout list: {coordinator.last_message}")

    return None


def hide_cursor(coordinator, hide=True):
    """Hide or show the game cursor.

    Args:
        coordinator: The game coordinator
        hide: True to hide the cursor, False to show it
    """
    coordinator.game_is_ready = False
    coordinator.send_message(f"cursor_hide {'true' if hide else 'false'}")
    wait_for_ready(coordinator)
    time.sleep(0.1)  # Brief delay for visual update


def create_loadout_with_fights(coordinator, character, encounters, loadout_name=None):
    """Create a loadout by winning fights against specified encounters.

    Args:
        coordinator: The game coordinator
        character: Character class (IRONCLAD, THE_SILENT, DEFECT, WATCHER)
        encounters: List of encounter names to fight
        loadout_name: Optional name for the loadout

    Returns:
        The loadout ID if successful, None otherwise
    """
    for i, encounter in enumerate(encounters):
        print(f"    Fighting {encounter} ({i + 1}/{len(encounters)})...")
        coordinator.game_is_ready = False
        coordinator.send_message(f"arena {character} {encounter}")
        wait_for_ready(coordinator)
        wait_for_combat(coordinator)
        time.sleep(0.3)

        # Win the fight
        coordinator.game_is_ready = False
        coordinator.send_message("win")
        wait_for_ready(coordinator)
        time.sleep(0.5)

        # Return to menu
        coordinator.game_is_ready = False
        coordinator.send_message("arena-back")
        wait_for_ready(coordinator)
        wait_for_main_menu(coordinator)
        time.sleep(0.3)

    # Get the ID of the newly created loadout
    return get_loadout_id(coordinator, index=0)


def test_generate_documentation_screenshots(at_main_menu):
    """
    Generate all documentation screenshots.

    This test navigates through all arena screens and captures screenshots
    for use in the documentation.

    NOTE: The run-acceptance-tests.sh script creates placeholder save files
    to prevent the Save Slot screen from blocking CommunicationMod commands.
    """
    coordinator = at_main_menu

    print("\n" + "=" * 50)
    print("STS Arena Documentation Screenshot Generator")
    print("=" * 50)
    print(f"Output directory: {SCREENSHOT_DIR}")

    # ====================
    # Hide cursor for clean screenshots
    # ====================
    print("\n[Setup] Hiding cursor...")
    hide_cursor(coordinator, hide=True)

    # ====================
    # Clear all existing loadouts for a clean slate
    # ====================
    print("\n[Setup] Clearing all existing loadouts...")
    coordinator.game_is_ready = False
    coordinator.send_message("arena-loadout delete-all")
    wait_for_ready(coordinator)
    print("  Loadouts cleared")

    # ====================
    # Create 5 loadouts with 2-3 fights each for history/stats screenshots
    # ====================
    print("\n[Setup] Creating loadouts with fight history...")

    # Loadout 1: Ironclad vs normal enemies
    print("  Creating Ironclad loadout (3 fights)...")
    create_loadout_with_fights(coordinator, "IRONCLAD",
        ["Cultist", "JawWorm", "2Louse"])

    # Loadout 2: Silent vs normal enemies
    print("  Creating Silent loadout (2 fights)...")
    create_loadout_with_fights(coordinator, "THE_SILENT",
        ["Cultist", "JawWorm"])

    # Loadout 3: Defect vs normal enemies
    print("  Creating Defect loadout (3 fights)...")
    create_loadout_with_fights(coordinator, "DEFECT",
        ["Cultist", "2Louse", "JawWorm"])

    # Loadout 4: Watcher vs normal enemies
    print("  Creating Watcher loadout (2 fights)...")
    create_loadout_with_fights(coordinator, "WATCHER",
        ["Cultist", "JawWorm"])

    # Loadout 5: Another Ironclad loadout (2 fights)
    print("  Creating second Ironclad loadout (2 fights)...")
    create_loadout_with_fights(coordinator, "IRONCLAD",
        ["2Louse", "Cultist"])

    print("  Created 5 loadouts with 12 total fights")

    # ====================
    # Main Menu Screenshot
    # ====================
    # Note: The Save Slot screen is prevented by setting DEFAULT_SLOT in STSSaveSlots preferences
    print("\n[1/11] Main menu with Arena Mode button...")
    wait_for_visual_stable(coordinator)
    capture_screenshot("main_menu")

    # ====================
    # Loadout Select Screen with a loadout selected to show contents
    # ====================
    print("\n[2/11] Loadout selection screen with loadout preview...")
    # Get the ID of the first loadout (we have 5 now)
    loadout_id = get_loadout_id(coordinator, index=0)
    if loadout_id:
        # Open loadout screen with the loadout selected (shows preview panel)
        open_arena_screen(coordinator, f"loadout {loadout_id}")
    else:
        # Fallback: just open the loadout screen
        print("  Warning: Could not get loadout ID, opening without selection")
        open_arena_screen(coordinator, "loadout")
    time.sleep(1.0)  # Let UI fully render
    wait_for_visual_stable(coordinator)
    capture_screenshot("loadout_select")

    # ====================
    # Loadout Creator Screen (Cards tab)
    # ====================
    print("\n[3/11] Loadout creator - cards tab...")
    open_arena_screen(coordinator, "creator")
    capture_screenshot("loadout_creator_cards")

    # ====================
    # Encounter Select Screen
    # ====================
    print("\n[4/11] Encounter selection screen...")
    open_arena_screen(coordinator, "encounter")
    capture_screenshot("encounter_select")

    # ====================
    # History Screen
    # ====================
    print("\n[5/11] Fight history screen...")
    open_arena_screen(coordinator, "history")
    capture_screenshot("history_screen")

    # ====================
    # Stats Screen
    # ====================
    print("\n[6/11] Statistics screen...")
    open_arena_screen(coordinator, "stats")
    capture_screenshot("stats_screen")

    # ====================
    # Arena Combat
    # ====================
    print("\n[7/11] Arena combat...")
    open_arena_screen(coordinator, "close")
    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(1.0)
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_combat")

    # ====================
    # Arena Victory (imperfect - need to take damage first)
    # ====================
    print("\n[8/11] Arena victory screen (imperfect victory)...")
    # End turn to let the monster attack us (so we take damage for imperfect victory)
    coordinator.game_is_ready = False
    coordinator.send_message("end")
    wait_for_ready(coordinator)
    time.sleep(2.0)  # Wait for monster attack animation

    # Now win the fight
    coordinator.game_is_ready = False
    coordinator.send_message("win")
    wait_for_ready(coordinator)

    # Wait for the game to exit combat and show victory screen
    # The victory screen appears when in_game is true but in_combat is false
    print("  Waiting for victory screen to appear...")
    time.sleep(3.0)  # Victory screen has transition animations
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_victory")

    # ====================
    # Arena Defeat Screen
    # ====================
    print("\n[9/11] Arena defeat screen...")
    # Return to menu first
    coordinator.game_is_ready = False
    coordinator.send_message("arena-back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # Start a new arena fight
    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(0.5)

    # Lose the fight - this should now show death screen with arena buttons
    coordinator.game_is_ready = False
    coordinator.send_message("lose")
    wait_for_ready(coordinator)

    # Wait for death screen to appear (has transition animations)
    print("  Waiting for defeat screen to appear...")
    time.sleep(3.0)  # Death screen has transition animations
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_defeat")

    # ====================
    # Practice in Arena from Normal Run (Pause menu)
    # ====================
    print("\n[10/11] Pause menu with 'Practice in Arena' button...")
    # Return to menu
    coordinator.game_is_ready = False
    coordinator.send_message("arena-back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # Start a normal run
    coordinator.game_is_ready = False
    coordinator.send_message("start IRONCLAD 0")
    wait_for_ready(coordinator)
    wait_for_in_game(coordinator)
    time.sleep(1.0)

    # Navigate from Neow to combat (handles intermediate screens automatically)
    if not navigate_to_combat(coordinator):
        raise RuntimeError("Failed to navigate to combat for pause menu screenshot")

    # Wait for combat to fully stabilize
    time.sleep(1.0)

    # Now press escape to open pause menu
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    print("  Waiting for pause menu to appear...")
    time.sleep(1.5)  # Pause menu has fade-in animation
    wait_for_visual_stable(coordinator)
    capture_screenshot("pause_menu_practice")

    # ====================
    # Death screen in normal run (with Practice in Arena button)
    # ====================
    print("\n[11/11] Death screen with Practice in Arena button...")
    # Close pause menu
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    time.sleep(0.5)

    # Lose the fight to show death screen
    coordinator.game_is_ready = False
    coordinator.send_message("lose")
    wait_for_ready(coordinator)
    print("  Waiting for death screen to appear...")
    time.sleep(3.0)  # Death screen has transition animations
    wait_for_visual_stable(coordinator)
    capture_screenshot("normal_run_death")

    # ====================
    # Cleanup
    # ====================
    print("\nCleaning up...")

    # Show cursor again
    hide_cursor(coordinator, hide=False)

    # Abandon the run to return to menu
    coordinator.game_is_ready = False
    coordinator.send_message("abandon")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    print("\n" + "=" * 50)
    print("Screenshot generation complete!")
    print(f"Screenshots saved to: {SCREENSHOT_DIR}")

    # List generated screenshots
    screenshots = sorted(SCREENSHOT_DIR.glob("*.png"))
    if screenshots:
        print(f"\nGenerated {len(screenshots)} screenshots:")
        for ss in screenshots:
            size = ss.stat().st_size / 1024
            print(f"  - {ss.name} ({size:.1f} KB)")

    # Assert we captured at least some screenshots
    assert len(screenshots) > 0, "No screenshots were generated"
