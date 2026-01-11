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


def get_loadout_list(coordinator):
    """Get the list of all loadouts with their details.

    Returns a list of loadout dicts, or empty list if failed.
    """
    import json

    # Clear the previous message to ensure we get fresh data
    coordinator.last_message = None
    coordinator.game_is_ready = False
    coordinator.send_message("arena-loadout list")
    wait_for_ready(coordinator)

    # Wait a moment for the message to be fully received
    time.sleep(0.1)

    # Parse the loadout list from the message
    if coordinator.last_message:
        try:
            return json.loads(coordinator.last_message)
        except json.JSONDecodeError:
            print(f"  Warning: Could not parse loadout list: {coordinator.last_message}")

    return []


def get_loadout_id(coordinator, index=0):
    """Get the ID of a loadout by its index in the list.

    Returns the loadout ID, or None if not found.
    """
    loadouts = get_loadout_list(coordinator)
    if loadouts and len(loadouts) > index:
        return loadouts[index].get("id")
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
    time.sleep(0.2)  # Brief delay for visual update


def take_screenshot(coordinator, name: str) -> Path:
    """Hide cursor and take a screenshot.

    This ensures the cursor is hidden before each screenshot to avoid
    the cursor appearing in documentation images.
    """
    hide_cursor(coordinator, hide=True)
    time.sleep(0.1)  # Let cursor hiding take effect
    return capture_screenshot(name)


def rename_loadout(coordinator, loadout_id, new_name):
    """Rename a loadout to the given name."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena-loadout rename {loadout_id} {new_name}")
    wait_for_ready(coordinator)
    time.sleep(0.1)


def add_card_to_creator(coordinator, card_id):
    """Add a card to the open loadout creator."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena_creator add_card {card_id}")
    wait_for_ready(coordinator)
    time.sleep(0.1)


def add_relic_to_creator(coordinator, relic_id):
    """Add a relic to the open loadout creator."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena_creator add_relic {relic_id}")
    wait_for_ready(coordinator)
    time.sleep(0.1)


def set_creator_hp(coordinator, current, max_hp):
    """Set HP values in the open loadout creator."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena_creator set_hp {current} {max_hp}")
    wait_for_ready(coordinator)
    time.sleep(0.1)


def set_creator_ascension(coordinator, level):
    """Set ascension level in the open loadout creator."""
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena_creator set_asc {level}")
    wait_for_ready(coordinator)
    time.sleep(0.1)


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

        # Wait for the game to process victory and return to menu
        # (arena victory screen auto-returns after a brief delay)
        wait_for_main_menu(coordinator)

        # Clean up any arena screens/state
        coordinator.game_is_ready = False
        coordinator.send_message("arena_back")
        wait_for_ready(coordinator)
        time.sleep(0.3)

    # Get the ID of the newly created loadout
    return get_loadout_id(coordinator, index=0)


def add_loss_to_history(coordinator, character, encounter):
    """Add a loss to the fight history by starting and losing a fight.

    Args:
        coordinator: The game coordinator
        character: Character class
        encounter: Encounter name to fight
    """
    print(f"    Losing against {encounter}...")
    coordinator.game_is_ready = False
    coordinator.send_message(f"arena {character} {encounter}")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(0.3)

    # Lose the fight
    coordinator.game_is_ready = False
    coordinator.send_message("lose")
    wait_for_ready(coordinator)

    # Wait for the game to process defeat and return to menu
    wait_for_main_menu(coordinator)

    # Clean up any arena screens/state
    coordinator.game_is_ready = False
    coordinator.send_message("arena_back")
    wait_for_ready(coordinator)
    time.sleep(0.3)


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
    print("(Cursor will be hidden before each screenshot)")

    # ====================
    # Clear all existing loadouts for a clean slate
    # ====================
    print("\n[Setup] Clearing all existing loadouts...")
    coordinator.game_is_ready = False
    coordinator.send_message("arena-loadout delete-all")
    wait_for_ready(coordinator)
    print("  Loadouts cleared")

    # ====================
    # Create fight history with interleaved wins and losses
    # ====================
    print("\n[Setup] Creating loadouts with fight history...")

    # Character-specific loadout names
    character_names = {
        "IRONCLAD": ["Angry Ironclad", "Strength Stacker", "Exhaust Engine"],
        "THE_SILENT": ["Sneaky Shiv Build", "Poison Master", "Discard Demon"],
        "DEFECT": ["Orb Goes Brrr", "Focus Fanatic", "Claw Machine"],
        "WATCHER": ["Calm Before Storm", "Stance Dancer", "Divinity Seeker"],
    }

    # Fight sequence: interleave wins and losses for more realistic history
    # Format: (character, encounter, win=True/lose=False)
    fight_sequence = [
        ("IRONCLAD", "Cultist", True),
        ("THE_SILENT", "JawWorm", True),
        ("IRONCLAD", "JawWorm", False),  # Loss
        ("DEFECT", "Cultist", True),
        ("WATCHER", "Cultist", True),
        ("THE_SILENT", "Cultist", False),  # Loss
        ("IRONCLAD", "2Louse", True),
        ("DEFECT", "2Louse", True),
        ("DEFECT", "JawWorm", False),  # Loss
        ("WATCHER", "JawWorm", True),
        ("IRONCLAD", "Cultist", True),
        ("THE_SILENT", "2Louse", True),
        ("IRONCLAD", "JawWorm", True),
        ("DEFECT", "Cultist", False),  # Loss
        ("WATCHER", "2Louse", True),
    ]

    wins = 0
    losses = 0
    for i, (character, encounter, is_win) in enumerate(fight_sequence):
        action = "Fighting" if is_win else "Losing to"
        print(f"    [{i+1}/{len(fight_sequence)}] {action} {encounter} as {character}...")

        coordinator.game_is_ready = False
        coordinator.send_message(f"arena {character} {encounter}")
        wait_for_ready(coordinator)
        wait_for_combat(coordinator)
        # Wait for combat animations to fully initialize before sending win/lose
        # This ensures the game is in a stable state for command processing
        wait_for_visual_stable(coordinator)

        coordinator.game_is_ready = False
        coordinator.send_message("win" if is_win else "lose")
        wait_for_ready(coordinator)
        # ArenaResultsScreen auto-closes after ~2s for wins, ~2.5s for losses
        # Give it time to complete the victory/defeat sequence and return to menu
        # The death/victory animations can vary in duration, so we wait longer
        time.sleep(5.0)
        # Try to wait for main menu with longer timeout
        try:
            wait_for_main_menu(coordinator, timeout=30)
        except Exception as e:
            print(f"    Warning: wait_for_main_menu failed: {e}, retrying with arena_back...")
            # Try sending arena_back to force return to menu
            coordinator.game_is_ready = False
            coordinator.send_message("arena_back")
            wait_for_ready(coordinator)
            time.sleep(1.0)
            wait_for_main_menu(coordinator, timeout=30)

        coordinator.game_is_ready = False
        coordinator.send_message("arena_back")
        wait_for_ready(coordinator)
        time.sleep(0.3)

        if is_win:
            wins += 1
        else:
            losses += 1

    print(f"  Created fight history: {wins} wins, {losses} losses")

    # Rename loadouts to entertaining names
    # First, open and close an arena screen to "reset" the game state after the fight sequence
    # This helps ensure the coordinator's message handling is working correctly
    print("  Resetting game state...")
    open_arena_screen(coordinator, "loadout")
    open_arena_screen(coordinator, "close")
    time.sleep(0.5)

    # Rename loadouts based on their character class
    print("  Renaming loadouts...")
    loadouts = get_loadout_list(coordinator)
    renamed_count = 0
    name_counters = {char: 0 for char in character_names}  # Track which name to use per character

    for loadout in loadouts[:10]:  # Limit to first 10
        loadout_id = loadout.get("id")
        char_class = loadout.get("characterClass")
        if loadout_id and char_class and char_class in character_names:
            names = character_names[char_class]
            name_index = name_counters[char_class] % len(names)
            name = names[name_index]
            name_counters[char_class] += 1

            rename_loadout(coordinator, loadout_id, name)
            print(f"    Renamed {char_class} loadout {loadout_id} to '{name}'")
            renamed_count += 1

    if renamed_count == 0:
        print("  Warning: Could not rename any loadouts")
    else:
        print(f"  Renamed {renamed_count} loadouts")

    # ====================
    # Main Menu Screenshot
    # ====================
    # Note: The Save Slot screen is prevented by setting DEFAULT_SLOT in STSSaveSlots preferences
    print("\n[1/11] Main menu with Arena Mode button...")
    # Close all arena screens to get back to main menu
    open_arena_screen(coordinator, "close")
    time.sleep(0.5)
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "main_menu")

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
    take_screenshot(coordinator, "loadout_select")

    # ====================
    # Loadout Creator Screen (Cards tab)
    # ====================
    print("\n[3/11] Loadout creator - cards tab...")
    # Open the creator with an existing loadout to show a customized deck
    loadout_for_creator = get_loadout_id(coordinator, index=0)
    if loadout_for_creator:
        open_arena_screen(coordinator, f"creator {loadout_for_creator}")
    else:
        open_arena_screen(coordinator, "creator")

    # Add some cards and relics to show a modified loadout
    # These are Ironclad cards that work well together for the screenshot
    print("  Adding cards and relics to show a modified loadout...")
    add_card_to_creator(coordinator, "Demon Form")
    add_card_to_creator(coordinator, "Limit Break")
    add_card_to_creator(coordinator, "Reaper")
    add_card_to_creator(coordinator, "Offering")
    add_card_to_creator(coordinator, "Battle Trance")
    add_relic_to_creator(coordinator, "Vajra")
    add_relic_to_creator(coordinator, "Bag of Marbles")
    set_creator_hp(coordinator, 65, 80)
    set_creator_ascension(coordinator, 10)

    time.sleep(0.3)  # Let UI update
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "loadout_creator_cards")

    # ====================
    # Encounter Select Screen
    # ====================
    print("\n[4/11] Encounter selection screen...")
    open_arena_screen(coordinator, "encounter")
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "encounter_select")

    # ====================
    # History Screen
    # ====================
    print("\n[5/11] Fight history screen...")
    open_arena_screen(coordinator, "history")
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "history_screen")

    # ====================
    # Stats Screen
    # ====================
    print("\n[6/11] Statistics screen...")
    open_arena_screen(coordinator, "stats")
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "stats_screen")

    # ====================
    # Arena Combat
    # ====================
    print("\n[7/11] Arena combat...")
    open_arena_screen(coordinator, "close")
    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(1.5)  # Let combat UI fully appear
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "arena_combat")

    # ====================
    # Arena Victory (imperfect - need to take damage first)
    # ====================
    print("\n[8/11] Arena victory screen (imperfect victory)...")

    # Enable screenshot mode to prevent auto-return to menu
    coordinator.game_is_ready = False
    coordinator.send_message("screenshot_mode on")
    wait_for_ready(coordinator)

    # End turn to let the monster attack us (so we take damage for imperfect victory)
    coordinator.game_is_ready = False
    coordinator.send_message("end")
    wait_for_ready(coordinator)
    time.sleep(2.5)  # Wait for monster attack animation

    # Now win the fight
    coordinator.game_is_ready = False
    coordinator.send_message("win")
    wait_for_ready(coordinator)

    # Wait for the game to exit combat and show victory screen
    # IMPORTANT: Wait for in_combat to be false before taking screenshot
    print("  Waiting for victory screen to appear...")
    for i in range(20):
        wait_for_state_update(coordinator)
        if not coordinator.last_game_state or not coordinator.last_game_state.in_combat:
            break
        time.sleep(0.2)
    time.sleep(2.0)  # Let victory screen animations complete
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "arena_victory")

    # Disable screenshot mode
    coordinator.game_is_ready = False
    coordinator.send_message("screenshot_mode off")
    wait_for_ready(coordinator)

    # ====================
    # Arena Defeat Screen
    # ====================
    print("\n[9/11] Arena defeat screen...")
    # Return to menu first
    coordinator.game_is_ready = False
    coordinator.send_message("arena_back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # Enable screenshot mode to prevent auto-return to menu
    coordinator.game_is_ready = False
    coordinator.send_message("screenshot_mode on")
    wait_for_ready(coordinator)

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

    # Wait for death screen to appear
    # IMPORTANT: Wait for in_combat to be false before taking screenshot
    print("  Waiting for defeat screen to appear...")
    for i in range(20):
        wait_for_state_update(coordinator)
        if not coordinator.last_game_state or not coordinator.last_game_state.in_combat:
            break
        time.sleep(0.2)
    time.sleep(2.0)  # Let death screen animations complete
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "arena_defeat")

    # Disable screenshot mode
    coordinator.game_is_ready = False
    coordinator.send_message("screenshot_mode off")
    wait_for_ready(coordinator)
    print("  screenshot_mode off completed")

    # Check for any errors
    if coordinator.last_error:
        print(f"  WARNING: Error after screenshot_mode off: {coordinator.last_error}")
        coordinator.last_error = None

    # ====================
    # Practice in Arena from Normal Run (Pause menu)
    # ====================
    print("\n[10/11] Pause menu with 'Practice in Arena' button...")
    # Return to menu - use longer timeout since arena_back may take time
    print("  Sending arena_back command...")
    coordinator.game_is_ready = False
    coordinator.last_error = None
    coordinator.send_message("arena_back")
    try:
        wait_for_ready(coordinator, timeout=30)
    except Exception as e:
        print(f"  ERROR: arena_back command failed: {e}")
        if coordinator.last_error:
            print(f"  last_error: {coordinator.last_error}")
        # Try to get state to understand current situation
        print("  Attempting to get current state...")
        coordinator.game_is_ready = False
        coordinator.send_message("state")
        try:
            wait_for_ready(coordinator, timeout=10)
            print(f"  in_game: {coordinator.in_game}")
            if coordinator.last_game_state:
                print(f"  screen_type: {coordinator.last_game_state.screen_type}")
        except:
            print("  Could not get state")
        raise

    if coordinator.last_error:
        print(f"  WARNING: Error from arena_back: {coordinator.last_error}")

    print("  Waiting for main menu...")
    wait_for_main_menu(coordinator)

    # Get a loadout ID BEFORE starting the normal run (commands don't work well during runs)
    saved_loadout_id = get_loadout_id(coordinator, index=0)
    if saved_loadout_id:
        print(f"  Saved loadout ID {saved_loadout_id} for later use")
    else:
        print("  Warning: Could not get loadout ID before starting normal run")

    # Start a normal run
    coordinator.game_is_ready = False
    coordinator.send_message("start IRONCLAD 0")
    wait_for_ready(coordinator)
    wait_for_in_game(coordinator)
    time.sleep(1.0)

    # Navigate from Neow to combat (handles intermediate screens automatically)
    if not navigate_to_combat(coordinator):
        raise RuntimeError("Failed to navigate to combat for pause menu screenshot")

    # Wait for combat to fully stabilize and the "1st turn" banner to fade
    # The turnPhaseEffectActive check in visual_stable should catch this, but
    # add a small delay after to ensure the banner animation fully completes
    wait_for_visual_stable(coordinator)
    time.sleep(0.3)  # Extra buffer for turn banner fade animation

    # Now press escape to open pause menu
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    print("  Waiting for pause menu to appear...")
    # Wait for pause menu fade-in, then check visual stability again
    # to ensure no turn banners are showing
    time.sleep(1.0)
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "pause_menu_practice")

    # ====================
    # Death screen in normal run (with Practice in Arena button)
    # ====================
    print("\n[11/11] Death screen with Practice in Arena button...")
    # Close pause menu by pressing ESCAPE again
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    time.sleep(1.0)

    # Lose the fight to show death screen first
    # IMPORTANT: We must lose BEFORE setting retry data because the death screen
    # construction triggers saveLoadoutOnDefeat() which would overwrite our values
    coordinator.game_is_ready = False
    coordinator.send_message("lose")
    wait_for_ready(coordinator)

    # Wait for death screen to appear
    print("  Waiting for death screen to appear...")
    time.sleep(1.0)  # Give death screen time to initialize

    # NOW set up arena retry data so the "Try Again in Arena Mode" button appears
    # This must be done AFTER the death screen is created to avoid being overwritten
    # Use the saved_loadout_id we got before starting the normal run
    if saved_loadout_id:
        print(f"  Setting retry data with loadout {saved_loadout_id}")
        coordinator.game_is_ready = False
        coordinator.send_message(f"set_retry_data {saved_loadout_id} Cultist")
        wait_for_ready(coordinator)
    else:
        print("  Warning: No loadout ID available for retry data")

    # Wait for the button to appear (needs a frame update)
    time.sleep(1.0)
    wait_for_visual_stable(coordinator)
    take_screenshot(coordinator, "normal_run_death")

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
