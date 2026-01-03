#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEVCONTAINER_DIR="$SCRIPT_DIR/.devcontainer"
HASH_FILE="$DEVCONTAINER_DIR/.config-hash"

# Compute hash of devcontainer config files
compute_config_hash() {
    cat "$DEVCONTAINER_DIR/devcontainer.json" "$DEVCONTAINER_DIR/Dockerfile" 2>/dev/null | shasum -a 256 | cut -d' ' -f1
}

CURRENT_HASH=$(compute_config_hash)
STORED_HASH=""
if [ -f "$HASH_FILE" ]; then
    STORED_HASH=$(cat "$HASH_FILE")
fi

# Check if rebuild is needed
REBUILD_FLAG=""
if [ "$CURRENT_HASH" != "$STORED_HASH" ]; then
    echo "Devcontainer config changed, rebuilding..."
    REBUILD_FLAG="--remove-existing-container --build-no-cache"
fi

# Ensure devcontainer is up (rebuild if config changed)
devcontainer up --workspace-folder "$SCRIPT_DIR" $REBUILD_FLAG

# Store hash after successful build
echo "$CURRENT_HASH" > "$HASH_FILE"

# If arguments passed, run that command; otherwise run Claude Code
if [ $# -gt 0 ]; then
    # Run custom command (e.g., ./dev.sh bash)
    if [ -n "$ANTHROPIC_API_KEY" ]; then
        devcontainer exec --workspace-folder "$SCRIPT_DIR" \
            --remote-env "ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY" \
            "$@"
    else
        devcontainer exec --workspace-folder "$SCRIPT_DIR" \
            "$@"
    fi
else
    # Default: Launch Claude Code with skip permissions
    if [ -n "$ANTHROPIC_API_KEY" ]; then
        devcontainer exec --workspace-folder "$SCRIPT_DIR" \
            --remote-env "ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY" \
            claude --dangerously-skip-permissions
    else
        devcontainer exec --workspace-folder "$SCRIPT_DIR" \
            claude --dangerously-skip-permissions
    fi
fi
