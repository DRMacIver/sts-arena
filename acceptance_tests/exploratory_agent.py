#!/usr/bin/env python3
"""
Exploratory Testing Agent for STS Arena

This module provides agents that can play the game via CommunicationMod,
exploring different paths and reporting anomalies, crashes, or suspicious behavior.

Usage:
    python exploratory_agent.py --strategy random --duration 300
    python exploratory_agent.py --strategy systematic --focus arena
"""

import argparse
import json
import os
import random
import sys
import time
import traceback
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Set, Tuple

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from spirecomm.communication.coordinator import Coordinator
from spirecomm.spire.character import PlayerClass
from spirecomm.spire.screen import ScreenType

from conftest import (
    DEFAULT_TIMEOUT,
    GameTimeout,
    wait_for_ready,
    wait_for_in_game,
    wait_for_main_menu,
    wait_for_combat,
    wait_for_state_update,
    click_results_button,
)


class ExplorationStrategy(Enum):
    RANDOM = "random"
    SYSTEMATIC = "systematic"
    CHAOS = "chaos"
    FOCUSED = "focused"


@dataclass
class Finding:
    """A finding from exploratory testing."""
    timestamp: str
    severity: str  # "crash", "error", "anomaly", "suspicious", "note"
    category: str
    description: str
    context: Dict[str, Any]
    action_history: List[str]
    screenshot_path: Optional[str] = None


@dataclass
class AgentState:
    """Tracks the agent's current state and history."""
    action_history: List[str] = field(default_factory=list)
    findings: List[Finding] = field(default_factory=list)
    screens_visited: Set[str] = field(default_factory=set)
    encounters_tried: Set[str] = field(default_factory=set)
    characters_tried: Set[str] = field(default_factory=set)
    actions_taken: int = 0
    errors_encountered: int = 0
    start_time: float = field(default_factory=time.time)

    def add_action(self, action: str):
        self.action_history.append(f"{time.time():.2f}: {action}")
        self.actions_taken += 1
        # Keep history manageable
        if len(self.action_history) > 1000:
            self.action_history = self.action_history[-500:]

    def add_finding(self, severity: str, category: str, description: str,
                    context: Dict[str, Any], screenshot_path: str = None):
        finding = Finding(
            timestamp=datetime.now().isoformat(),
            severity=severity,
            category=category,
            description=description,
            context=context,
            action_history=self.action_history[-20:],  # Last 20 actions
            screenshot_path=screenshot_path,
        )
        self.findings.append(finding)
        if severity in ("crash", "error"):
            self.errors_encountered += 1
        return finding


class ExploratoryAgent:
    """
    An agent that explores the game looking for bugs.
    """

    # Known encounters by act
    ENCOUNTERS = {
        "act1": [
            "Cultist", "Jaw Worm", "2 Louse", "Small Slimes", "Blue Slaver",
            "Gremlin Gang", "Looter", "Large Slime", "Lots of Slimes",
            "Exordium Thugs", "Exordium Wildlife", "Red Slaver", "3 Louse",
            "2 Fungi Beasts", "Gremlin Nob", "Lagavulin", "3 Sentries",
            "Slime Boss", "The Guardian", "Hexaghost",
        ],
        "act2": [
            "Chosen", "Byrd", "Cultist and Chosen", "Snecko", "Shelled Parasite",
            "Snake Plant", "Centurion and Healer", "3 Cultists", "Slaver and Parasite",
            "Gremlin Leader", "Book of Stabbing", "Automaton", "Collector",
            "Champ", "Bronze Automaton",
        ],
        "act3": [
            "Spire Growth", "Transient", "4 Shapes", "Maw", "Sphere and 2 Shapes",
            "Jaw Worm Horde", "3 Darklings", "Orbwalker", "Spire Spear and Shield",
            "Giant Head", "Nemesis", "Reptomancer", "Awakened One",
            "Time Eater", "Donu and Deca",
        ],
    }

    CHARACTERS = ["IRONCLAD", "SILENT", "DEFECT", "WATCHER"]

    def __init__(self, coordinator: Coordinator, strategy: ExplorationStrategy,
                 focus: str = None, verbose: bool = True):
        self.coord = coordinator
        self.strategy = strategy
        self.focus = focus
        self.verbose = verbose
        self.state = AgentState()
        self.running = True

    def log(self, msg: str):
        """Log a message if verbose."""
        if self.verbose:
            print(f"[Agent] {msg}", file=sys.stderr, flush=True)

    def take_screenshot(self, name: str) -> Optional[str]:
        """Take a screenshot if available."""
        try:
            from screenshot import take_screenshot
            return take_screenshot(name=name)
        except Exception:
            return None

    def get_game_state(self) -> Dict[str, Any]:
        """Get current game state as a dict for logging."""
        try:
            wait_for_state_update(self.coord, timeout=5)
            gs = self.coord.last_game_state
            if gs:
                return {
                    "in_game": self.coord.in_game,
                    "screen": str(gs.screen_type) if gs else None,
                    "in_combat": gs.in_combat if gs else None,
                    "player_hp": f"{gs.current_hp}/{gs.max_hp}" if gs and hasattr(gs, 'current_hp') else None,
                    "floor": gs.floor if gs and hasattr(gs, 'floor') else None,
                }
            return {"in_game": self.coord.in_game, "screen": None}
        except Exception as e:
            return {"error": str(e)}

    def send_command(self, cmd: str, expect_ready: bool = True, timeout: float = 10) -> bool:
        """Send a command and wait for response."""
        self.state.add_action(f"CMD: {cmd}")
        self.log(f"Sending: {cmd}")

        try:
            self.coord.send_message(cmd)
            if expect_ready:
                wait_for_ready(self.coord, timeout=timeout)
            return True
        except Exception as e:
            self.log(f"Command failed: {e}")
            self.state.add_finding(
                "error", "command_failure",
                f"Command '{cmd}' failed: {e}",
                {"command": cmd, "error": str(e), "game_state": self.get_game_state()}
            )
            return False

    def ensure_main_menu(self, timeout: float = 30) -> bool:
        """Ensure we're at the main menu."""
        try:
            if not self.coord.in_game:
                return True

            # Try to get back to main menu
            self.send_command("abandon", timeout=5)
            wait_for_main_menu(self.coord, timeout=timeout)
            return True
        except Exception as e:
            self.log(f"Failed to return to main menu: {e}")
            return False

    def try_arena_fight(self, character: str, encounter: str, seed: int = None) -> Dict[str, Any]:
        """
        Try an arena fight and report on the result.
        Returns a dict with the outcome and any issues found.
        """
        result = {
            "character": character,
            "encounter": encounter,
            "seed": seed,
            "started": False,
            "completed": False,
            "outcome": None,
            "issues": [],
        }

        self.state.characters_tried.add(character)
        self.state.encounters_tried.add(encounter)

        # Ensure we're at main menu
        if not self.ensure_main_menu():
            result["issues"].append("Could not return to main menu")
            return result

        # Start the arena fight
        cmd = f"arena {character} {encounter}"
        if seed:
            cmd += f" {seed}"

        if not self.send_command(cmd):
            result["issues"].append("Arena command failed")
            return result

        # Wait for game to start
        try:
            wait_for_in_game(self.coord, timeout=15)
            result["started"] = True
        except GameTimeout as e:
            result["issues"].append(f"Failed to start game: {e}")
            screenshot = self.take_screenshot(f"arena_start_fail_{character}_{encounter}")
            self.state.add_finding(
                "error", "arena_start_failure",
                f"Arena failed to start: {character} vs {encounter}",
                {"result": result, "game_state": self.get_game_state()},
                screenshot
            )
            return result

        # Wait for combat
        try:
            wait_for_combat(self.coord, timeout=15)
        except GameTimeout as e:
            result["issues"].append(f"Failed to enter combat: {e}")
            screenshot = self.take_screenshot(f"combat_start_fail_{character}_{encounter}")
            self.state.add_finding(
                "error", "combat_start_failure",
                f"Combat failed to start: {character} vs {encounter}",
                {"result": result, "game_state": self.get_game_state()},
                screenshot
            )
            return result

        # Check combat state
        gs = self.coord.last_game_state
        if gs:
            result["player_hp"] = f"{gs.current_hp}/{gs.max_hp}" if hasattr(gs, 'current_hp') else None
            result["monsters"] = len(gs.monsters) if gs.monsters else 0

            # Verify the character is correct
            if hasattr(gs, 'class_') and gs.class_:
                actual_class = str(gs.class_)
                expected = character.upper().replace("SILENT", "THE_SILENT")
                if expected not in actual_class.upper():
                    result["issues"].append(f"Wrong character: expected {character}, got {actual_class}")
                    self.state.add_finding(
                        "anomaly", "wrong_character",
                        f"Character mismatch: expected {character}, got {actual_class}",
                        {"result": result}
                    )

        # End the fight (randomly win or lose)
        outcome = random.choice(["win", "lose", "abandon"])
        result["outcome"] = outcome

        if outcome == "win":
            if not self.send_command("win"):
                result["issues"].append("Win command failed")
        elif outcome == "lose":
            if not self.send_command("lose"):
                result["issues"].append("Lose command failed")
            # Need to click continue on results screen
            time.sleep(0.5)
            try:
                click_results_button(self.coord, "continue")
            except Exception as e:
                result["issues"].append(f"Failed to click continue: {e}")
        else:  # abandon
            if not self.send_command("arena_back"):
                result["issues"].append("Arena_back command failed")

        # Wait for return to menu
        try:
            wait_for_main_menu(self.coord, timeout=30)
            result["completed"] = True
        except GameTimeout as e:
            result["issues"].append(f"Failed to return to menu: {e}")
            screenshot = self.take_screenshot(f"menu_return_fail_{character}_{encounter}_{outcome}")
            self.state.add_finding(
                "error", "menu_return_failure",
                f"Failed to return to menu after {outcome}: {character} vs {encounter}",
                {"result": result, "game_state": self.get_game_state()},
                screenshot
            )

        if result["issues"]:
            self.log(f"Issues found: {result['issues']}")

        return result

    def try_practice_in_arena(self) -> Dict[str, Any]:
        """
        Try the Practice in Arena flow from a normal run.
        """
        result = {
            "started_run": False,
            "entered_combat": False,
            "started_arena": False,
            "returned_to_run": False,
            "issues": [],
        }

        # Ensure at main menu
        if not self.ensure_main_menu():
            result["issues"].append("Could not return to main menu")
            return result

        # Start a normal run
        character = random.choice(self.CHARACTERS)
        self.send_command(f"start {character} 0")  # Ascension 0

        try:
            wait_for_in_game(self.coord, timeout=30)
            result["started_run"] = True
        except GameTimeout:
            result["issues"].append("Failed to start normal run")
            return result

        # Navigate to combat
        attempts = 0
        while attempts < 20:
            attempts += 1
            wait_for_state_update(self.coord, timeout=5)
            gs = self.coord.last_game_state

            if not gs:
                continue

            if gs.in_combat:
                result["entered_combat"] = True
                break

            screen = gs.screen_type
            self.state.screens_visited.add(str(screen))

            if screen == ScreenType.EVENT:
                # Skip events
                self.send_command("choose 0")
            elif screen == ScreenType.MAP:
                # Find a fight room
                next_nodes = getattr(gs, 'next_nodes', None)
                if hasattr(gs, 'map') and gs.map and next_nodes:
                    fight_idx = None
                    for i, node in enumerate(next_nodes):
                        if hasattr(node, 'symbol') and node.symbol == 'M':
                            fight_idx = i
                            break
                    if fight_idx is not None:
                        self.send_command(f"choose {fight_idx}")
                    else:
                        self.send_command("choose 0")
                else:
                    self.send_command("choose 0")
            elif screen == ScreenType.CARD_REWARD:
                self.send_command("skip")
            elif screen == ScreenType.COMBAT_REWARD:
                self.send_command("proceed")
            elif screen == ScreenType.REST:
                self.send_command("rest")
            elif screen == ScreenType.SHOP:
                self.send_command("leave")
            elif screen == ScreenType.CHEST:
                self.send_command("proceed")
            else:
                # Try proceeding
                if hasattr(gs, 'proceed_available') and gs.proceed_available:
                    self.send_command("proceed")
                else:
                    time.sleep(0.5)

        if not result["entered_combat"]:
            result["issues"].append("Could not reach combat")
            self.ensure_main_menu()
            return result

        # Now try Practice in Arena
        if not self.send_command("practice_in_arena"):
            result["issues"].append("practice_in_arena command failed")
            self.ensure_main_menu()
            return result

        # Wait for arena combat to start
        try:
            wait_for_in_game(self.coord, timeout=15)
            wait_for_combat(self.coord, timeout=15)
            result["started_arena"] = True
        except GameTimeout as e:
            result["issues"].append(f"Arena didn't start: {e}")
            screenshot = self.take_screenshot("practice_arena_fail")
            self.state.add_finding(
                "error", "practice_arena_failure",
                "Practice in Arena failed to start combat",
                {"result": result, "game_state": self.get_game_state()},
                screenshot
            )
            self.ensure_main_menu()
            return result

        # Now leave arena and return to normal run
        if not self.send_command("leave_arena"):
            result["issues"].append("leave_arena command failed")
            self.ensure_main_menu()
            return result

        # Verify we're back in the normal run (in game, in combat)
        try:
            wait_for_in_game(self.coord, timeout=15)
            wait_for_state_update(self.coord, timeout=5)

            gs = self.coord.last_game_state
            if gs and gs.in_combat:
                result["returned_to_run"] = True
            else:
                result["issues"].append("Not in combat after leaving arena")
                screenshot = self.take_screenshot("leave_arena_wrong_state")
                self.state.add_finding(
                    "anomaly", "leave_arena_state",
                    "After leave_arena, not in combat",
                    {"result": result, "game_state": self.get_game_state()},
                    screenshot
                )
        except GameTimeout as e:
            result["issues"].append(f"Failed to verify return: {e}")

        # Clean up - abandon the run
        self.ensure_main_menu()

        return result

    def try_rapid_transitions(self, count: int = 5) -> Dict[str, Any]:
        """
        Try rapid arena start/stop transitions to find race conditions.
        """
        result = {
            "transitions": 0,
            "successful": 0,
            "issues": [],
        }

        for i in range(count):
            result["transitions"] += 1

            if not self.ensure_main_menu():
                result["issues"].append(f"Iteration {i}: couldn't reach menu")
                continue

            # Start arena
            character = random.choice(self.CHARACTERS)
            encounter = random.choice(self.ENCOUNTERS["act1"])

            self.send_command(f"arena {character} {encounter}")

            try:
                wait_for_in_game(self.coord, timeout=10)

                # Immediately try to leave
                action = random.choice(["arena_back", "win", "lose"])
                self.send_command(action)

                if action == "lose":
                    time.sleep(0.5)
                    try:
                        click_results_button(self.coord, "continue")
                    except:
                        pass

                wait_for_main_menu(self.coord, timeout=15)
                result["successful"] += 1

            except Exception as e:
                result["issues"].append(f"Iteration {i}: {e}")
                screenshot = self.take_screenshot(f"rapid_transition_fail_{i}")
                self.state.add_finding(
                    "error", "rapid_transition",
                    f"Rapid transition failed at iteration {i}",
                    {"iteration": i, "error": str(e), "game_state": self.get_game_state()},
                    screenshot
                )

        return result

    def explore_random(self, duration: float = 300):
        """
        Random exploration strategy - try random actions.
        """
        end_time = time.time() + duration

        while self.running and time.time() < end_time:
            # Choose what to try
            action_type = random.choice([
                "arena_fight",
                "arena_fight",
                "arena_fight",
                "practice_in_arena",
                "rapid_transitions",
            ])

            self.log(f"Trying: {action_type}")

            if action_type == "arena_fight":
                character = random.choice(self.CHARACTERS)
                act = random.choice(list(self.ENCOUNTERS.keys()))
                encounter = random.choice(self.ENCOUNTERS[act])
                seed = random.randint(1, 999999) if random.random() > 0.5 else None

                result = self.try_arena_fight(character, encounter, seed)
                self.log(f"Arena result: completed={result['completed']}, issues={len(result['issues'])}")

            elif action_type == "practice_in_arena":
                result = self.try_practice_in_arena()
                self.log(f"Practice result: returned={result['returned_to_run']}, issues={len(result['issues'])}")

            elif action_type == "rapid_transitions":
                result = self.try_rapid_transitions(count=3)
                self.log(f"Rapid result: {result['successful']}/{result['transitions']} successful")

            # Brief pause between explorations
            time.sleep(1)

    def explore_systematic(self, duration: float = 300):
        """
        Systematic exploration - try each character/encounter combination.
        """
        end_time = time.time() + duration

        # Try each character with various encounters
        for character in self.CHARACTERS:
            if not self.running or time.time() >= end_time:
                break

            for act_name, encounters in self.ENCOUNTERS.items():
                if not self.running or time.time() >= end_time:
                    break

                # Sample a few encounters from each act
                sample = random.sample(encounters, min(3, len(encounters)))

                for encounter in sample:
                    if not self.running or time.time() >= end_time:
                        break

                    self.log(f"Testing: {character} vs {encounter}")
                    result = self.try_arena_fight(character, encounter)

                    if result["issues"]:
                        self.log(f"Issues: {result['issues']}")

    def explore_chaos(self, duration: float = 300):
        """
        Chaos exploration - try weird/unexpected sequences.
        """
        end_time = time.time() + duration

        chaos_actions = [
            # Try double commands
            lambda: (self.send_command("arena IRONCLAD Cultist"), self.send_command("arena SILENT Cultist")),
            # Try commands at wrong times
            lambda: self.send_command("win"),
            lambda: self.send_command("lose"),
            lambda: self.send_command("leave_arena"),
            lambda: self.send_command("practice_in_arena"),
            lambda: self.send_command("results_button continue"),
            lambda: self.send_command("arena_back"),
            # Try invalid parameters
            lambda: self.send_command("arena INVALID Cultist"),
            lambda: self.send_command("arena IRONCLAD InvalidEncounter"),
            lambda: self.send_command("arena"),
            # Rapid fire
            lambda: [self.send_command(f"arena IRONCLAD Cultist", expect_ready=False) for _ in range(3)],
        ]

        while self.running and time.time() < end_time:
            # Do some normal operations
            if random.random() > 0.3:
                character = random.choice(self.CHARACTERS)
                encounter = random.choice(self.ENCOUNTERS["act1"])
                self.try_arena_fight(character, encounter)
            else:
                # Do something chaotic
                action = random.choice(chaos_actions)
                self.log(f"Chaos action: {action.__name__ if hasattr(action, '__name__') else 'lambda'}")
                try:
                    action()
                except Exception as e:
                    self.log(f"Chaos caused: {e}")

            # Try to recover
            try:
                self.ensure_main_menu()
            except:
                pass

            time.sleep(0.5)

    def run(self, duration: float = 300):
        """Run the exploration for the specified duration."""
        self.log(f"Starting {self.strategy.value} exploration for {duration}s")

        try:
            if self.strategy == ExplorationStrategy.RANDOM:
                self.explore_random(duration)
            elif self.strategy == ExplorationStrategy.SYSTEMATIC:
                self.explore_systematic(duration)
            elif self.strategy == ExplorationStrategy.CHAOS:
                self.explore_chaos(duration)
            else:
                self.explore_random(duration)
        except Exception as e:
            self.log(f"Exploration crashed: {e}")
            self.state.add_finding(
                "crash", "exploration_crash",
                f"Agent exploration crashed: {e}",
                {"traceback": traceback.format_exc()}
            )

        return self.get_report()

    def get_report(self) -> Dict[str, Any]:
        """Generate a report of findings."""
        elapsed = time.time() - self.state.start_time

        return {
            "strategy": self.strategy.value,
            "duration_seconds": elapsed,
            "actions_taken": self.state.actions_taken,
            "errors_encountered": self.state.errors_encountered,
            "characters_tried": list(self.state.characters_tried),
            "encounters_tried": list(self.state.encounters_tried),
            "screens_visited": list(self.state.screens_visited),
            "findings": [
                {
                    "timestamp": f.timestamp,
                    "severity": f.severity,
                    "category": f.category,
                    "description": f.description,
                    "context": f.context,
                    "recent_actions": f.action_history,
                }
                for f in self.state.findings
            ],
            "summary": {
                "total_findings": len(self.state.findings),
                "crashes": len([f for f in self.state.findings if f.severity == "crash"]),
                "errors": len([f for f in self.state.findings if f.severity == "error"]),
                "anomalies": len([f for f in self.state.findings if f.severity == "anomaly"]),
            }
        }


def run_exploration(strategy: str = "random", duration: int = 300, focus: str = None):
    """
    Run exploratory testing with the specified strategy.

    This function is designed to be called from the test framework.
    """
    from conftest import _coordinator

    coord = _coordinator

    # Parse strategy
    try:
        strat = ExplorationStrategy(strategy.lower())
    except ValueError:
        strat = ExplorationStrategy.RANDOM

    agent = ExploratoryAgent(coord, strat, focus=focus)
    report = agent.run(duration)

    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run exploratory testing")
    parser.add_argument("--strategy", choices=["random", "systematic", "chaos"],
                        default="random", help="Exploration strategy")
    parser.add_argument("--duration", type=int, default=300,
                        help="Duration in seconds")
    parser.add_argument("--focus", type=str, default=None,
                        help="Focus area (e.g., 'arena', 'loadouts')")
    parser.add_argument("--output", type=str, default=None,
                        help="Output file for report (JSON)")

    args = parser.parse_args()

    print(f"Starting exploratory testing: {args.strategy} for {args.duration}s")

    report = run_exploration(args.strategy, args.duration, args.focus)

    # Print summary
    print("\n" + "="*60)
    print("EXPLORATION REPORT")
    print("="*60)
    print(f"Strategy: {report['strategy']}")
    print(f"Duration: {report['duration_seconds']:.1f}s")
    print(f"Actions: {report['actions_taken']}")
    print(f"Errors: {report['errors_encountered']}")
    print(f"Characters tried: {report['characters_tried']}")
    print(f"Encounters tried: {len(report['encounters_tried'])}")
    print()
    print("FINDINGS SUMMARY:")
    print(f"  Crashes: {report['summary']['crashes']}")
    print(f"  Errors: {report['summary']['errors']}")
    print(f"  Anomalies: {report['summary']['anomalies']}")
    print()

    if report['findings']:
        print("DETAILED FINDINGS:")
        for i, finding in enumerate(report['findings']):
            print(f"\n  [{i+1}] {finding['severity'].upper()}: {finding['category']}")
            print(f"      {finding['description']}")

    # Save report if requested
    if args.output:
        with open(args.output, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"\nReport saved to: {args.output}")
