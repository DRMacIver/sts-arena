#!/usr/bin/env python3
"""
STS Arena Acceptance Test Agent.

This script is the entry point that CommunicationMod spawns. It:
1. Signals ready to CommunicationMod
2. Sets up communication
3. Runs pytest with the test suite

To configure CommunicationMod:
    uv run --directory /path/to/acceptance_tests python run_agent.py
"""

import sys
import pytest
from pathlib import Path

# Import the communicator module to initialize it before pytest runs
from communicator import communicator, initialize_communicator


def main():
    """Main entry point - initialize communication and run pytest."""
    # Initialize communication with the game
    initialize_communicator()

    # Run pytest on the test files
    test_dir = Path(__file__).parent
    exit_code = pytest.main([
        str(test_dir),
        "-v",
        "--tb=short",
        "-p", "no:cacheprovider",  # Disable cache to avoid issues
    ])

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
