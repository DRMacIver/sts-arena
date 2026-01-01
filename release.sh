#!/bin/bash
set -euo pipefail

# STS Arena Release Script
# Builds the mod and creates a GitHub release

cd "$(dirname "$0")"

echo "=== STS Arena Release Script ==="
echo ""

# Check for gh CLI
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed."
    echo "Install it with: brew install gh"
    exit 1
fi

# Check if logged in to GitHub
if ! gh auth status &> /dev/null; then
    echo "Error: Not logged in to GitHub."
    echo "Run: gh auth login"
    exit 1
fi

# Extract version from pom.xml
VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

# Override version from argument if provided
if [ $# -ge 1 ]; then
    VERSION="$1"
fi

# JAR name doesn't include version (configured in pom.xml)
JAR_PATH="target/STSArena.jar"
RELEASE_JAR="STSArena-${VERSION}.jar"

TAG="v${VERSION}"

echo "Building version: ${VERSION}"
echo "Tag: ${TAG}"
echo ""

# Clean and build
echo "Building JAR..."
mvn clean package -q

if [ ! -f "${JAR_PATH}" ]; then
    echo "Error: JAR not found at ${JAR_PATH}"
    exit 1
fi

# Copy JAR with version in name for release
cp "${JAR_PATH}" "target/${RELEASE_JAR}"

echo "Built: target/${RELEASE_JAR}"
echo ""

# Check if tag already exists
if git rev-parse "${TAG}" >/dev/null 2>&1; then
    echo "Warning: Tag ${TAG} already exists."
    read -p "Delete and recreate? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git tag -d "${TAG}"
        gh release delete "${TAG}" --yes 2>/dev/null || true
    else
        echo "Aborting."
        exit 1
    fi
fi

# Create tag
echo "Creating tag ${TAG}..."
git tag -a "${TAG}" -m "Release ${VERSION}"

# Push tag
echo "Pushing tag..."
git push origin "${TAG}"

# Create GitHub release
echo "Creating GitHub release..."
gh release create "${TAG}" \
    --title "STS Arena ${VERSION}" \
    --notes "See [README](README.md) for installation and usage instructions." \
    "target/${RELEASE_JAR}"

echo ""
echo "=== Release ${VERSION} created successfully! ==="
echo ""
gh release view "${TAG}" --web
