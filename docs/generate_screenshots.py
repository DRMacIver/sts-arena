#!/usr/bin/env python3
"""
Screenshot generator for STS Arena documentation.

This script captures screenshots of all key screens in the STS Arena mod
for use in documentation. It uses the same infrastructure as the acceptance
tests to connect to and control the game.

Usage:
    Run via the acceptance test framework:
    ./scripts/run-acceptance-tests.sh docs/generate_screenshots.py

Or interactively if pipes are already set up:
    python docs/generate_screenshots.py
"""

import json
import os
import sys
import time
from pathlib import Path

# Add acceptance_tests to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent / "acceptance_tests"))

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

from spirecomm.communication.coordinator import Coordinator


# Screenshot output directory
SCREENSHOT_DIR = Path(__file__).parent / "screenshots"


def _process_message(coordinator, msg):
    """Process a raw message from the game and update coordinator state."""
    communication_state = json.loads(msg)
    coordinator.last_error = communication_state.get("error", None)
    msg_value = communication_state.get("message")
    if msg_value is not None:
        coordinator.last_message = msg_value
    coordinator.game_is_ready = communication_state.get("ready_for_command")
    if coordinator.last_error is None:
        coordinator.in_game = communication_state.get("in_game")


def wait_for_ready(coordinator, timeout=60):
    """Wait for the game to be ready for commands, with timeout."""
    start = time.time()
    while not coordinator.game_is_ready:
        remaining = timeout - (time.time() - start)
        if remaining <= 0:
            raise TimeoutError(f"Timed out after {timeout}s waiting for game to be ready")
        msg = coordinator.get_next_raw_message(block=True, timeout=min(1.0, remaining))
        if msg is not None:
            _process_message(coordinator, msg)


def wait_for_visual_stable(coordinator, timeout=10):
    """Wait for visual effects to complete."""
    coordinator.game_is_ready = False
    coordinator.last_error = None
    coordinator.send_message("wait_for visual_stable")
    wait_for_ready(coordinator, timeout=timeout)


def wait_for_main_menu(coordinator, timeout=60):
    """Wait until we're at main menu."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for main_menu")
    wait_for_ready(coordinator, timeout=timeout)


def wait_for_combat(coordinator, timeout=60):
    """Wait until we're in combat."""
    coordinator.game_is_ready = False
    coordinator.send_message("wait_for in_combat true")
    wait_for_ready(coordinator, timeout=timeout)


def capture_screenshot(name: str) -> Path:
    """Capture a screenshot and save it to the docs/screenshots directory."""
    if not HAS_MSS:
        print(f"  [SKIP] {name} (mss not available)")
        return None

    filepath = SCREENSHOT_DIR / f"{name}.png"

    try:
        with mss.mss() as sct:
            # Capture the first monitor (primary screen)
            monitor = sct.monitors[0]
            sct_img = sct.grab(monitor)

            # Save to file
            mss.tools.to_png(sct_img.rgb, sct_img.size, output=str(filepath))

            # Optionally resize if PIL is available (for smaller file sizes)
            if HAS_PIL:
                img = Image.open(filepath)
                # Keep original size for documentation quality
                img.save(filepath, optimize=True)

            print(f"  [OK] {name}")
            return filepath
    except Exception as e:
        print(f"  [ERROR] {name}: {e}")
        return None


def ensure_main_menu(coord):
    """Ensure we're at the main menu before starting."""
    coord.game_is_ready = False
    coord.send_message("state")
    wait_for_ready(coord)

    if coord.in_game:
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)


def close_arena_screens(coord):
    """Close any open arena screens."""
    coord.send_message("arena_screen close")
    wait_for_ready(coord)
    time.sleep(0.3)  # Brief pause for screen transition


def generate_screenshots():
    """Main function to generate all documentation screenshots."""
    print("STS Arena Documentation Screenshot Generator")
    print("=" * 50)

    # Ensure screenshot directory exists
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Output directory: {SCREENSHOT_DIR}")

    # Get pipe paths from environment
    input_pipe_path = os.environ.get("STS_GAME_INPUT_PIPE")
    output_pipe_path = os.environ.get("STS_GAME_OUTPUT_PIPE")

    if not input_pipe_path or not output_pipe_path:
        print("\nError: STS_GAME_INPUT_PIPE and STS_GAME_OUTPUT_PIPE must be set")
        print("Run this script via: ./scripts/run-acceptance-tests.sh docs/generate_screenshots.py")
        sys.exit(1)

    # Connect to the game
    print("\nConnecting to game...")
    game_input = open(input_pipe_path, "r")
    game_output = open(output_pipe_path, "w")
    coord = Coordinator(input_file=game_input, output_file=game_output)
    coord.last_message = None
    wait_for_ready(coord)
    print("Connected!")

    # Ensure we start at main menu
    print("\nEnsuring we're at main menu...")
    ensure_main_menu(coord)
    close_arena_screens(coord)

    try:
        # ====================
        # Main Menu Screenshot
        # ====================
        print("\n[1/12] Main menu with Arena Mode button...")
        wait_for_visual_stable(coord)
        capture_screenshot("main_menu")

        # ====================
        # Loadout Select Screen
        # ====================
        print("\n[2/12] Loadout selection screen...")
        coord.send_message("arena_screen loadout")
        wait_for_ready(coord)
        time.sleep(0.5)  # Let screen render
        wait_for_visual_stable(coord)
        capture_screenshot("loadout_select")

        # ====================
        # Loadout Creator Screen (Cards tab)
        # ====================
        print("\n[3/12] Loadout creator - cards tab...")
        close_arena_screens(coord)
        coord.send_message("arena_screen creator")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("loadout_creator_cards")

        # ====================
        # Encounter Select Screen
        # ====================
        print("\n[4/12] Encounter selection screen...")
        close_arena_screens(coord)
        coord.send_message("arena_screen encounter")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("encounter_select")

        # ====================
        # History Screen
        # ====================
        print("\n[5/12] Fight history screen...")
        close_arena_screens(coord)
        coord.send_message("arena_screen history")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("history_screen")

        # ====================
        # Stats Screen
        # ====================
        print("\n[6/12] Statistics screen...")
        close_arena_screens(coord)
        coord.send_message("arena_screen stats")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("stats_screen")

        # ====================
        # Arena Combat
        # ====================
        print("\n[7/12] Arena combat...")
        close_arena_screens(coord)
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_combat(coord)
        time.sleep(1.0)  # Let combat initialize fully
        wait_for_visual_stable(coord)
        capture_screenshot("arena_combat")

        # ====================
        # Arena Victory
        # ====================
        print("\n[8/12] Arena victory screen...")
        coord.send_message("win")
        wait_for_ready(coord)
        time.sleep(1.5)  # Let victory animation play
        wait_for_visual_stable(coord)
        capture_screenshot("arena_victory")

        # ====================
        # Return to menu and start another fight for defeat
        # ====================
        print("\n[9/12] Arena defeat screen...")
        coord.send_message("arena-back")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_combat(coord)
        time.sleep(0.5)

        coord.send_message("lose")
        wait_for_ready(coord)
        time.sleep(1.5)  # Let defeat animation play
        wait_for_visual_stable(coord)
        capture_screenshot("arena_defeat")

        # ====================
        # Practice in Arena from Normal Run
        # ====================
        print("\n[10/12] Pause menu with 'Practice in Arena' button...")
        coord.send_message("arena-back")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Start a normal run to show pause menu
        coord.send_message("start IRONCLAD 0")
        wait_for_ready(coord)
        time.sleep(2.0)  # Let run initialize

        # Press escape to open pause menu
        coord.send_message("key ESCAPE")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("pause_menu_practice")

        # Close pause menu and abandon run
        coord.send_message("key ESCAPE")
        wait_for_ready(coord)
        time.sleep(0.3)
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # ====================
        # Additional screenshots with content
        # ====================
        print("\n[11/12] Loadout select with preview...")
        # Create a loadout first by running a quick arena fight
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_combat(coord)
        coord.send_message("win")
        wait_for_ready(coord)
        time.sleep(1.0)
        coord.send_message("arena-back")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Open loadout select - should now have at least one loadout
        coord.send_message("arena_screen loadout")
        wait_for_ready(coord)
        time.sleep(0.5)
        wait_for_visual_stable(coord)
        capture_screenshot("loadout_select_preview")

        # ====================
        # Loadout Creator with relics
        # ====================
        print("\n[12/12] Loadout creator - relics view...")
        close_arena_screens(coord)
        coord.send_message("arena_screen creator")
        wait_for_ready(coord)
        time.sleep(0.5)
        # Note: Would need to click on relics tab - for now capture as-is
        wait_for_visual_stable(coord)
        capture_screenshot("loadout_creator_relics")

        # ====================
        # Cleanup
        # ====================
        print("\nCleaning up...")
        close_arena_screens(coord)

    except Exception as e:
        print(f"\nError during screenshot generation: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        game_input.close()
        game_output.close()

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


if __name__ == "__main__":
    generate_screenshots()
