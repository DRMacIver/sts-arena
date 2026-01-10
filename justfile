# STS Arena Justfile
# Run `just --list` to see available recipes

# Default recipe - show help
default:
    @just --list

# Build the project
build:
    mvn package -DskipTests -q

# Build with verbose output
build-verbose:
    mvn package -DskipTests

# Clean and build
clean-build:
    mvn clean package -DskipTests -q

# Run unit tests
test:
    mvn test

# Run unit tests quietly
test-quiet:
    mvn test -q

# Run acceptance tests (requires x86_64 Linux)
acceptance *ARGS:
    ./scripts/run-acceptance-tests.sh {{ARGS}}

# Generate documentation screenshots
screenshots:
    ./scripts/run-acceptance-tests.sh test_generate_screenshots.py

# Run headless mod load test (fast - patch discovery only)
headless-fast:
    ./scripts/headless-mod-load-test.sh --fast

# Run headless mod load test (full - with patch compilation)
headless:
    ./scripts/headless-mod-load-test.sh --stsarena-only

# Validate patches exist in game code
validate:
    mvn test -Dtest=HeadlessPatchTest -q

# Create a GitHub release (auto-increments version)
release:
    ./release.sh

# Create a GitHub release with specific version
release-version VERSION:
    ./release.sh {{VERSION}}

# Run all pre-commit checks (build + test)
check: build test

# Run full test suite (unit + headless + acceptance)
test-all: test headless acceptance

# Sync beads issue tracker
sync:
    bd sync

# Show beads issues ready to work on
ready:
    bd ready

# Start devcontainer (for local development)
dev *ARGS:
    ./dev.sh {{ARGS}}
