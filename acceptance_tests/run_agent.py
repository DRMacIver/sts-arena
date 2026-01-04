#!/usr/bin/env python3
"""
STS Arena Acceptance Test Agent.

This script is the entry point that CommunicationMod spawns. It:
1. Saves the original stdout for game communication
2. Redirects stdout to stderr for pytest output
3. Runs pytest with the test suite

The coordinator is created lazily by conftest.py when needed.

To configure CommunicationMod:
    uv run --directory /path/to/acceptance_tests python run_agent.py
"""

import sys
import logging
from pathlib import Path

# Save original stdout for game communication BEFORE any other imports
# This MUST happen before anything else that might use stdout
original_stdout = sys.stdout

# Redirect stdout to stderr for all other output (pytest, print statements, etc.)
# This is needed because CommunicationMod reads from our stdout, so pytest output
# would be interpreted as commands
sys.stdout = sys.stderr

# Now it's safe to import other modules
import pytest

# Set up logging to a file (not stderr, so only test output is visible)
log_file = Path(__file__).parent.parent / "lib" / "communication_mod_errors.log"
log_file.parent.mkdir(parents=True, exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format='[TEST] %(levelname)s: %(message)s',
    filename=str(log_file),
    filemode='w',
)

logger = logging.getLogger(__name__)


def main():
    """Main entry point - just run pytest."""
    logger.info("Starting test run...")

    # Run pytest on the test files
    # The coordinator will be created by conftest.py when the first fixture is used
    test_dir = Path(__file__).parent

    exit_code = pytest.main([
        str(test_dir),
        "-v",
        "--tb=short",
        "-p", "no:cacheprovider",  # Disable cache to avoid issues
    ])

    # Write final status to stderr
    logger.info(f"Exit code: {exit_code}")
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
