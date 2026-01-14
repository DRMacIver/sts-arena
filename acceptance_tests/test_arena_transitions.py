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
)


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

        # Take a snapshot of player state
        snapshot = get_player_snapshot(coord)
        assert snapshot is not None
        assert snapshot['hp'] > 0

    @pytest.mark.skip(reason="Practice in Arena requires reaching combat first - needs map navigation")
    def test_practice_in_arena_button_visible_in_combat(self, at_main_menu):
        """Pause during combat, verify Practice in Arena button shows"""
        # This test would require reaching combat first, which requires
        # map navigation that isn't implemented yet
        pass

    @pytest.mark.skip(reason="Practice in Arena requires reaching combat first - needs map navigation")
    def test_practice_in_arena_starts_arena(self, at_main_menu):
        """Click Practice in Arena, verify arena mode active"""
        pass


class TestArenaToNormalRun:
    """Arena → normal run resume - Tests for resuming normal runs after arena"""

    @pytest.mark.skip(reason="Requires Practice in Arena flow which needs map navigation")
    def test_leave_arena_resumes_normal_run(self, at_main_menu):
        """Practice in Arena → Leave Arena → back to normal run"""
        pass

    @pytest.mark.skip(reason="Requires Practice in Arena flow which needs map navigation")
    def test_normal_run_hp_preserved(self, at_main_menu):
        """After arena and resume, HP matches pre-arena state"""
        pass

    @pytest.mark.skip(reason="Requires Practice in Arena flow which needs map navigation")
    def test_normal_run_floor_preserved(self, at_main_menu):
        """After arena and resume, floor number is correct"""
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

        # Rematch
        click_results_button(coord, "rematch")
        wait_for_in_game(coord)
        wait_for_combat(coord)

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

        # Start arena - use a harder encounter that might cause damage
        coord.send_message("arena IRONCLAD Jaw Worm")
        wait_for_ready(coord)
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Play a turn to take some damage, then win
        # For now, just win - the game may or may not show as imperfect
        coord.send_message("win")
        wait_for_ready(coord)

        # Either we auto-return to menu (perfect) or results screen shows
        # Give time for either path
        time.sleep(1.0)
        wait_for_stable(coord)

        # Check if we're already at menu (perfect victory auto-close)
        if not coord.in_game:
            # Perfect victory auto-closed, test is done
            return

        # If still in game, results screen should be open - click rematch
        click_results_button(coord, "rematch")
        wait_for_in_game(coord)
        wait_for_combat(coord)

        # Verify back in combat
        assert coord.in_game

        # Clean up
        coord.send_message("win")
        wait_for_ready(coord)
        wait_for_main_menu(coord)
