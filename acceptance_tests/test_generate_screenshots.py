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
)

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
    """Capture a screenshot and save it to the docs/screenshots directory."""
    if not HAS_MSS:
        print(f"  [SKIP] {name} (mss not available)")
        return None

    # Ensure directory exists
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
    filepath = SCREENSHOT_DIR / f"{name}.png"

    try:
        with mss.mss() as sct:
            # Capture the first monitor (primary screen)
            monitor = sct.monitors[0]
            sct_img = sct.grab(monitor)

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


def test_generate_documentation_screenshots(at_main_menu):
    """
    Generate all documentation screenshots.

    This test navigates through all arena screens and captures screenshots
    for use in the documentation.
    """
    coordinator = at_main_menu

    print("\n" + "=" * 50)
    print("STS Arena Documentation Screenshot Generator")
    print("=" * 50)
    print(f"Output directory: {SCREENSHOT_DIR}")

    # ====================
    # Main Menu Screenshot
    # ====================
    print("\n[1/12] Main menu with Arena Mode button...")
    # Close any open screens first
    open_arena_screen(coordinator, "close")
    wait_for_visual_stable(coordinator)
    capture_screenshot("main_menu")

    # ====================
    # Loadout Select Screen
    # ====================
    print("\n[2/12] Loadout selection screen...")
    open_arena_screen(coordinator, "loadout")
    capture_screenshot("loadout_select")

    # ====================
    # Loadout Creator Screen (Cards tab)
    # ====================
    print("\n[3/12] Loadout creator - cards tab...")
    open_arena_screen(coordinator, "creator")
    capture_screenshot("loadout_creator_cards")

    # ====================
    # Encounter Select Screen
    # ====================
    print("\n[4/12] Encounter selection screen...")
    open_arena_screen(coordinator, "encounter")
    capture_screenshot("encounter_select")

    # ====================
    # History Screen
    # ====================
    print("\n[5/12] Fight history screen...")
    open_arena_screen(coordinator, "history")
    capture_screenshot("history_screen")

    # ====================
    # Stats Screen
    # ====================
    print("\n[6/12] Statistics screen...")
    open_arena_screen(coordinator, "stats")
    capture_screenshot("stats_screen")

    # ====================
    # Arena Combat
    # ====================
    print("\n[7/12] Arena combat...")
    open_arena_screen(coordinator, "close")  # Return to main menu first
    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(1.0)  # Let combat initialize fully
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_combat")

    # ====================
    # Arena Victory
    # ====================
    print("\n[8/12] Arena victory screen...")
    coordinator.game_is_ready = False
    coordinator.send_message("win")
    wait_for_ready(coordinator)
    time.sleep(1.5)  # Let victory animation play
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_victory")

    # ====================
    # Return to menu and start another fight for defeat
    # ====================
    print("\n[9/12] Arena defeat screen...")
    coordinator.game_is_ready = False
    coordinator.send_message("arena-back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    time.sleep(0.5)

    coordinator.game_is_ready = False
    coordinator.send_message("lose")
    wait_for_ready(coordinator)
    time.sleep(1.5)  # Let defeat animation play
    wait_for_visual_stable(coordinator)
    capture_screenshot("arena_defeat")

    # ====================
    # Practice in Arena from Normal Run
    # ====================
    print("\n[10/12] Pause menu with 'Practice in Arena' button...")
    coordinator.game_is_ready = False
    coordinator.send_message("arena-back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # Start a normal run to show pause menu
    coordinator.game_is_ready = False
    coordinator.send_message("start IRONCLAD 0")
    wait_for_ready(coordinator)
    time.sleep(2.0)  # Let run initialize

    # Press escape to open pause menu
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    time.sleep(0.5)
    wait_for_visual_stable(coordinator)
    capture_screenshot("pause_menu_practice")

    # Close pause menu and abandon run
    coordinator.game_is_ready = False
    coordinator.send_message("key ESCAPE")
    wait_for_ready(coordinator)
    time.sleep(0.3)
    coordinator.game_is_ready = False
    coordinator.send_message("abandon")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # ====================
    # Additional screenshots with content
    # ====================
    print("\n[11/12] Loadout select with preview...")
    # Create a loadout first by running a quick arena fight
    coordinator.game_is_ready = False
    coordinator.send_message("arena IRONCLAD Cultist")
    wait_for_ready(coordinator)
    wait_for_combat(coordinator)
    coordinator.game_is_ready = False
    coordinator.send_message("win")
    wait_for_ready(coordinator)
    time.sleep(1.0)
    coordinator.game_is_ready = False
    coordinator.send_message("arena-back")
    wait_for_ready(coordinator)
    wait_for_main_menu(coordinator)

    # Open loadout select - should now have at least one loadout
    open_arena_screen(coordinator, "loadout")
    capture_screenshot("loadout_select_preview")

    # ====================
    # Loadout Creator with relics
    # ====================
    print("\n[12/12] Loadout creator - relics view...")
    open_arena_screen(coordinator, "creator")
    # Note: Would need to click on relics tab - for now capture as-is
    capture_screenshot("loadout_creator_relics")

    # ====================
    # Cleanup
    # ====================
    print("\nCleaning up...")
    open_arena_screen(coordinator, "close")

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
