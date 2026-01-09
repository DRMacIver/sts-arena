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

# Determine version: use argument, or auto-increment from latest tag
if [ $# -ge 1 ]; then
    VERSION="$1"
else
    # Get latest tag version and increment minor
    LATEST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
    if [ -n "${LATEST_TAG}" ]; then
        LATEST_VERSION="${LATEST_TAG#v}"
        IFS='.' read -r MAJOR MINOR PATCH <<< "${LATEST_VERSION}"
        VERSION="${MAJOR}.$((MINOR + 1)).0"
        echo "Latest release: ${LATEST_TAG}"
    else
        # No tags yet, use pom.xml version
        VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    fi
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

echo "Build complete."
echo ""

# Check if tag already exists and points to HEAD
TAG_EXISTS=false
TAG_AT_HEAD=false
if git rev-parse "${TAG}" >/dev/null 2>&1; then
    TAG_EXISTS=true
    TAG_COMMIT=$(git rev-parse "${TAG}^{commit}" 2>/dev/null || git rev-parse "${TAG}")
    HEAD_COMMIT=$(git rev-parse HEAD)
    if [ "${TAG_COMMIT}" = "${HEAD_COMMIT}" ]; then
        TAG_AT_HEAD=true
    fi
fi

if [ "${TAG_EXISTS}" = true ] && [ "${TAG_AT_HEAD}" = false ]; then
    # Tag exists but not at HEAD - bump version
    while git rev-parse "${TAG}" >/dev/null 2>&1; do
        echo "Tag ${TAG} already exists (not at HEAD), bumping version..."

        # Parse version components (handles X.Y.Z format)
        IFS='.' read -r MAJOR MINOR PATCH <<< "${VERSION}"
        MINOR=$((MINOR + 1))
        PATCH=0
        VERSION="${MAJOR}.${MINOR}.${PATCH}"
        TAG="v${VERSION}"
        RELEASE_JAR="STSArena-${VERSION}.jar"
    done
fi

echo "Using version: ${VERSION}"
echo ""

# Update version in ModTheSpire.json
echo "Updating ModTheSpire.json with version ${VERSION}..."
sed -i.bak -E "s/\"version\": \"[0-9]+\.[0-9]+\.[0-9]+\"/\"version\": \"${VERSION}\"/" src/main/resources/ModTheSpire.json
rm -f src/main/resources/ModTheSpire.json.bak

# Update version in pom.xml (only the project version, not dependencies)
echo "Updating pom.xml with version ${VERSION}..."
# Match the version line that comes right after artifactId STSArena
sed -i.bak -E "/<artifactId>STSArena<\/artifactId>/,/<version>.*<\/version>/ { s/<version>[0-9]+\.[0-9]+\.[0-9]+<\/version>/<version>${VERSION}<\/version>/; }" pom.xml
rm -f pom.xml.bak

# Rebuild with updated version
echo "Rebuilding with updated version..."
mvn clean package -q

# Update download link in docs
echo "Updating docs/index.html with new version..."
sed -i.bak -E "s|releases/download/v[0-9]+\.[0-9]+\.[0-9]+/STSArena-[0-9]+\.[0-9]+\.[0-9]+\.jar\">STSArena-[0-9]+\.[0-9]+\.[0-9]+\.jar|releases/download/${TAG}/${RELEASE_JAR}\">${RELEASE_JAR}|g" docs/index.html
rm -f docs/index.html.bak

# Commit version and docs updates if changed
CHANGED_FILES=""
for f in src/main/resources/ModTheSpire.json pom.xml docs/index.html; do
    if ! git diff --quiet "$f" 2>/dev/null; then
        CHANGED_FILES="${CHANGED_FILES} ${f}"
    fi
done
if [ -n "${CHANGED_FILES}" ]; then
    echo "Committing updates:${CHANGED_FILES}..."
    git add ${CHANGED_FILES}
    git commit -m "Bump version to ${VERSION}"
    git push
fi

# Copy JAR with version in name for release
cp "${JAR_PATH}" "target/${RELEASE_JAR}"
echo "Release JAR: target/${RELEASE_JAR}"
echo ""

# Create and push tag if needed
if [ "${TAG_AT_HEAD}" = true ]; then
    echo "Tag ${TAG} already exists at HEAD, skipping tag creation."
else
    echo "Creating tag ${TAG}..."
    git tag -a "${TAG}" -m "Release ${VERSION}"

    echo "Pushing tag..."
    git push origin "${TAG}"
fi

# Check if GitHub release already exists
if gh release view "${TAG}" >/dev/null 2>&1; then
    echo "GitHub release ${TAG} already exists."
    echo "Uploading JAR to existing release..."
    gh release upload "${TAG}" "target/${RELEASE_JAR}" --clobber
else
    echo "Creating GitHub release..."
    gh release create "${TAG}" \
        --title "STS Arena ${VERSION}" \
        --notes "See [README](README.md) for installation and usage instructions." \
        "target/${RELEASE_JAR}"
fi

echo ""
echo "=== Release ${VERSION} created successfully! ==="
echo ""
gh release view "${TAG}" --web
