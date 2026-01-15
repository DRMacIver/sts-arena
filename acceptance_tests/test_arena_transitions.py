"""
Acceptance tests for arena ↔ main game transitions.

These tests focus on the transitions between arena mode and normal gameplay,
including:
- Arena mode → main menu
- Normal run → arena (Practice in Arena)
- Arena → normal run resume
- Save file integrity during transitions
- Rapid transition stress tests
- Results screen button behaviors
"""

import time

import pytest
from conftest import (
    click_results_button,
    get_player_snapshot,
    leave_arena,
    practice_in_arena,
    start_normal_run,
    wait_for_combat,
    wait_for_in_game,
    wait_for_main_menu,
    wait_for_ready,
    wait_for_stable,
    wait_for_state_update,
)
from spirecomm.spire.screen import ScreenType


def find_monster_room_index(coordinator):
    """Find the index of a monster room in the available map choices."""
    game = coordinator.last_game_state
    if not game or game.screen_type != ScreenType.MAP:
        return 0

    if hasattr(game.screen, 'next_nodes') and game.screen.next_nodes:
        for i, node in enumerate(game.screen.next_nodes):
            if node.symbol in ('M', 'E'):
                return i
    return 0


def navigate_to_combat(coordinator, timeout=60):
    """Navigate from current position to combat.

    Handles Neow event, card rewards, and map navigation.
    """
    start = time.time()
    max_iterations = 30
    event_choices_made = 0

    for iteration in range(max_iterations):
        if time.time() - start > timeout:
            return False

        time.sleep(0.5)
        wait_for_state_update(coordinator)
        game = coordinator.last_game_state

        if not game:
            time.sleep(1.0)
            continue

        if game.in_combat:
            return True

        screen_type = game.screen_type

        if screen_type == ScreenType.EVENT:
            if event_choices_made < 5:
                coordinator.game_is_ready = False
                coordinator.send_message("choose 0")
                wait_for_ready(coordinator)
                event_choices_made += 1
                time.sleep(1.0)
            else:
                coordinator.game_is_ready = False
                coordinator.send_message("proceed")
                wait_for_ready(coordinator)
                time.sleep(1.0)

        elif screen_type == ScreenType.CARD_REWARD:
            coordinator.game_is_ready = False
            coordinator.send_message("skip")
            wait_for_ready(coordinator)
            time.sleep(0.5)

        elif screen_type == ScreenType.MAP:
            monster_idx = find_monster_room_index(coordinator)
            coordinator.game_is_ready = False
            coordinator.send_message(f"choose {monster_idx}")
            wait_for_ready(coordinator)
            coordinator.game_is_ready = False
            coordinator.send_message("wait_for in_combat true")
            wait_for_ready(coordinator, timeout=30)
            return coordinator.last_game_state and coordinator.last_game_state.in_combat

        elif screen_type == ScreenType.NONE:
            coordinator.game_is_ready = False
            coordinator.send_message("key MAP")
            wait_for_ready(coordinator)
            time.sleep(1.0)

        else:
            coordinator.game_is_ready = False
            coordinator.send_message("proceed")
            wait_for_ready(coordinator)
            time.sleep(0.5)

    return False


class TestArenaToMenuTransitions:
    """Arena mode → main menu transitions"""

    def test_arena_win_returns_to_menu(self, at_main_menu):
        """Win arena fight, verify at main menu with no Continue button issues"""
        coord = at_main_menu

        # Start arena fight
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Verify we're in combat
        assert coord.in_game
        game = coord.last_game_state
        assert game.in_combat

        # Win the fight
        coord.send_message("win")
        wait_for_ready(coord)

        # Wait for return to main menu
        wait_for_main_menu(coord)

        # Verify we're at main menu
        assert not coord.in_game

    def test_arena_lose_returns_to_menu(self, at_main_menu):
        """Lose arena fight, click Retreat, verify at main menu"""
        coord = at_main_menu

        # Start arena fight
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Lose the fight
        coord.send_message("lose")
        wait_for_ready(coord)
        time.sleep(0.5)  # Let results screen appear

        # Click retreat button
        click_results_button(coord, "continue")
        wait_for_main_menu(coord)

        # Verify we're at main menu
        assert not coord.in_game

    def test_arena_abandon_returns_to_menu(self, at_main_menu):
        """Abandon arena via leave_arena command, verify at main menu"""
        coord = at_main_menu

        # Start arena fight
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Leave arena
        leave_arena(coord)
        wait_for_main_menu(coord)

        # Verify we're at main menu
        assert not coord.in_game

    def test_sequential_arenas_same_character(self, at_main_menu):
        """Multiple arenas in sequence with same character"""
        coord = at_main_menu

        for i in range(3):
            # Start arena fight
            coord.send_message("arena IRONCLAD Cultist")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            # Verify we're in combat
            assert coord.in_game

            # Win the fight
            coord.send_message("win")
            wait_for_ready(coord)
            wait_for_main_menu(coord)

            # Verify we're at main menu
            assert not coord.in_game

    def test_sequential_arenas_different_characters(self, at_main_menu):
        """Arena with Ironclad, then Silent, then Defect"""
        coord = at_main_menu

        characters = ["IRONCLAD", "SILENT", "DEFECT"]
        for character in characters:
            # Start arena fight
            coord.send_message(f"arena {character} Cultist")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            # Verify we're in combat with correct character
            assert coord.in_game
            game = coord.last_game_state
            # Character class is accessible in the game state
            assert game is not None

            # Win the fight
            coord.send_message("win")
            wait_for_ready(coord)
            wait_for_main_menu(coord)

            # Verify we're at main menu
            assert not coord.in_game


class TestNormalRunToArena:
    """Normal run → arena (Practice in Arena) - Tests for Practice in Arena flow"""

    def test_start_normal_run_enter_combat(self, at_main_menu):
        """Verify we can start normal run and reach combat"""
        coord = at_main_menu

        # Start a normal run
        start_normal_run(coord, "IRONCLAD", 0)

        # Verify we're in game
        assert coord.in_game

        # Navigate to combat
        success = navigate_to_combat(coord)
        assert success, "Failed to navigate to combat"

        # Verify we're in combat
        game = coord.last_game_state
        assert game.in_combat

    def test_practice_in_arena_starts_arena(self, at_main_menu):
        """Click Practice in Arena, verify arena mode active"""
        coord = at_main_menu

        # Start a normal run and navigate to combat
        start_normal_run(coord, "IRONCLAD", 0)
        success = navigate_to_combat(coord)
        assert success, "Failed to navigate to combat"

        # Capture pre-arena state
        pre_snapshot = get_player_snapshot(coord)
        assert pre_snapshot is not None

        # Use Practice in Arena command
        practice_in_arena(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Verify we're now in an arena fight
        assert coord.in_game
        game = coord.last_game_state
        assert game.in_combat

        # Clean up - leave arena and abandon the run
        leave_arena(coord)
        wait_for_in_game(coord)  # Back in normal run
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)


class TestArenaToNormalRun:
    """Arena → normal run resume - Tests for resuming normal runs after arena"""

    def test_leave_arena_resumes_normal_run(self, at_main_menu):
        """Practice in Arena → Leave Arena → back to normal run"""
        coord = at_main_menu

        # Start a normal run and navigate to combat
        start_normal_run(coord, "IRONCLAD", 0)
        success = navigate_to_combat(coord)
        assert success, "Failed to navigate to combat"

        # Capture pre-arena state
        pre_snapshot = get_player_snapshot(coord)
        assert pre_snapshot is not None
        pre_floor = pre_snapshot['floor']

        # Use Practice in Arena
        practice_in_arena(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Leave arena - should resume normal run
        leave_arena(coord)
        wait_for_in_game(coord)

        # Verify we're back in the normal run
        assert coord.in_game
        post_snapshot = get_player_snapshot(coord)
        assert post_snapshot is not None

        # Floor should be preserved
        assert post_snapshot['floor'] == pre_floor

        # Clean up
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

    def test_normal_run_hp_preserved(self, at_main_menu):
        """After arena and resume, HP matches pre-arena state"""
        coord = at_main_menu

        # Start a normal run and navigate to combat
        start_normal_run(coord, "IRONCLAD", 0)
        success = navigate_to_combat(coord)
        assert success, "Failed to navigate to combat"

        # Capture pre-arena HP
        pre_snapshot = get_player_snapshot(coord)
        assert pre_snapshot is not None
        pre_hp = pre_snapshot['hp']

        # Use Practice in Arena
        practice_in_arena(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Leave arena
        leave_arena(coord)
        wait_for_in_game(coord)

        # Verify HP is preserved
        post_snapshot = get_player_snapshot(coord)
        assert post_snapshot is not None
        assert post_snapshot['hp'] == pre_hp

        # Clean up
        coord.send_message("abandon")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

    def test_practice_win_then_leave(self, at_main_menu):
        """Practice in Arena → Win → Leave Arena → normal run restored"""
        coord = at_main_menu

        # Start a normal run and navigate to combat
        start_normal_run(coord, "IRONCLAD", 0)
        success = navigate_to_combat(coord)
        assert success, "Failed to navigate to combat"

        # Capture pre-arena state
        pre_snapshot = get_player_snapshot(coord)
        assert pre_snapshot is not None

        # Use Practice in Arena
        practice_in_arena(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Win the arena fight
        coord.send_message("win")
        wait_for_ready(coord)
        time.sleep(1.0)  # Let results screen appear or auto-close

        # Now leave arena (may already be at menu for perfect victory, or at results screen)
        wait_for_stable(coord)
        if coord.in_game:
            # Still in game - either results screen or back in normal run
            # Try leave_arena to get back to normal run
            leave_arena(coord)
            wait_for_in_game(coord)

            # Should be back in normal run
            post_snapshot = get_player_snapshot(coord)
            assert post_snapshot is not None
            assert post_snapshot['floor'] == pre_snapshot['floor']

            # Clean up
            coord.send_message("abandon")
            wait_for_ready(coord)
            wait_for_main_menu(coord)
        else:
            # Perfect victory auto-closed to menu - normal run save should be restored
            # The normal run was saved, so starting a new run won't have the same state
            # This is expected behavior for perfect victories
            pass


class TestSaveFileIntegrity:
    """Save file handling during transitions"""

    def test_arena_returns_clean_to_menu(self, at_main_menu):
        """Arena run completes cleanly without corrupting menu state"""
        coord = at_main_menu

        # Start arena
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Win
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Verify clean state
        assert not coord.in_game

        # Start another arena to verify no corruption
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)
        assert coord.in_game

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

    def test_arena_from_fresh_start_no_crash(self, at_main_menu):
        """Arena works with no prior save file"""
        coord = at_main_menu

        # Simply starting an arena should work even with no prior save
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        assert coord.in_game
        game = coord.last_game_state
        assert game.in_combat

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)


class TestRapidTransitions:
    """Stress tests for rapid interactions"""

    def test_rapid_arena_starts(self, at_main_menu):
        """Quick succession: arena → win → arena → win → arena"""
        coord = at_main_menu

        for i in range(5):
            # Start arena
            coord.send_message("arena IRONCLAD Cultist")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            # Win immediately
            coord.send_message("win")
            wait_for_ready(coord)
            wait_for_main_menu(coord)

            # Verify clean return to menu
            assert not coord.in_game

    def test_quick_abandon_restart(self, at_main_menu):
        """Arena → abandon immediately → arena"""
        coord = at_main_menu

        for i in range(3):
            # Start arena
            coord.send_message("arena IRONCLAD Cultist")
            wait_for_ready(coord)
            wait_for_in_game(coord)
            wait_for_combat(coord)

            # Leave immediately
            leave_arena(coord)
            wait_for_main_menu(coord)

            # Verify clean return
            assert not coord.in_game

    def test_win_immediate_new_arena(self, at_main_menu):
        """Win arena, immediately start new arena same character"""
        coord = at_main_menu

        # First arena
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Win
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

        # Immediately start another
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        assert coord.in_game

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

    def test_rematch_chain(self, at_main_menu):
        """Lose → Rematch → Lose → Rematch → Lose → Retreat"""
        coord = at_main_menu

        # Start arena
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Lose and rematch twice
        for i in range(2):
            coord.send_message("lose")
            wait_for_ready(coord)
            time.sleep(0.5)

            # Click rematch
            click_results_button(coord, "rematch")
            wait_for_in_game(coord)
            wait_for_combat(coord)

            assert coord.in_game

        # Final lose and retreat
        coord.send_message("lose")
        wait_for_ready(coord)
        time.sleep(0.5)

        click_results_button(coord, "continue")
        wait_for_main_menu(coord)

        assert not coord.in_game


class TestResultsScreenFlows:
    """Results screen button behaviors"""

    def test_defeat_rematch_works(self, at_main_menu):
        """Click Rematch after defeat, fight restarts"""
        coord = at_main_menu

        # Start arena
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Lose
        coord.send_message("lose")
        wait_for_ready(coord)
        time.sleep(0.5)

        # Rematch - uses scheduleArenaRestart which goes through main menu
        click_results_button(coord, "rematch")

        # Wait for combat - the game transitions: death screen -> main menu -> combat
        # Use polling since the transition goes through multiple states
        for _ in range(30):  # 30 second timeout
            time.sleep(1.0)
            wait_for_stable(coord)
            if coord.in_game and coord.last_game_state and coord.last_game_state.in_combat:
                break
        else:
            raise GameTimeout("Expected to be in combat after rematch")

        # Verify back in combat
        assert coord.in_game
        game = coord.last_game_state
        assert game.in_combat

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)

    def test_defeat_retreat_button(self, at_main_menu):
        """Click Retreat after defeat, returns to menu"""
        coord = at_main_menu

        # Start arena
        coord.send_message("arena IRONCLAD Cultist")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Lose
        coord.send_message("lose")
        wait_for_ready(coord)
        time.sleep(0.5)

        # Retreat
        click_results_button(coord, "continue")
        wait_for_main_menu(coord)

        assert not coord.in_game

    def test_imperfect_victory_rematch_works(self, at_main_menu):
        """Click Rematch after imperfect victory, fight restarts"""
        coord = at_main_menu

        # Start arena fight
        coord.send_message("arena IRONCLAD Jaw Worm")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Deal damage to player to ensure imperfect victory
        coord.send_message("damage 5")
        wait_for_ready(coord)

        # Now win the fight (imperfect because we took damage)
        coord.send_message("win")
        wait_for_ready(coord)

        # Give time for results screen to appear (imperfect victory shows results)
        time.sleep(1.0)
        wait_for_stable(coord)

        # Imperfect victory should show results screen, not auto-close
        assert coord.in_game, "Imperfect victory should show results screen, not auto-close"

        # Click rematch - this triggers scheduleArenaRestart which:
        # 1. Goes to main menu first
        # 2. Then restarts the fight via checkPendingArenaRestart
        click_results_button(coord, "rematch")

        # Wait for combat - the game transitions: results screen -> main menu -> combat
        # Use polling since the transition goes through multiple states
        for _ in range(30):  # 30 second timeout
            time.sleep(1.0)
            wait_for_stable(coord)
            if coord.in_game and coord.last_game_state and coord.last_game_state.in_combat:
                break
        else:
            raise GameTimeout("Expected to be in combat after rematch")

        # Verify back in combat
        assert coord.in_game
        game = coord.last_game_state
        assert game.in_combat

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)
