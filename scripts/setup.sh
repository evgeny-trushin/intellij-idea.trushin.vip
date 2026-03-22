#!/usr/bin/env bash
# IntelliJ IDEA Git Flow Capture — Environment Setup (R1)
set -e -o pipefail

echo "=== IntelliJ Git Flow Capture Setup ==="
echo ""

# R1.1: Check macOS version
echo "1. Checking macOS version..."
SW_VER=$(sw_vers -productVersion)
echo "   macOS $SW_VER"

# Check Xcode CLI tools
if xcode-select -p &>/dev/null; then
    echo "   Xcode command-line tools: installed"
else
    echo "   ERROR: Xcode command-line tools not found. Run: xcode-select --install"
    exit 1
fi

# R1.2: Check IntelliJ IDEA installation
echo ""
echo "2. Checking IntelliJ IDEA installation..."
IDEA_PATHS=(
    "/Applications/IntelliJ IDEA.app"
    "/Applications/IntelliJ IDEA CE.app"
    "/Applications/IntelliJ IDEA Ultimate.app"
    "$HOME/Applications/IntelliJ IDEA.app"
    "$HOME/Applications/IntelliJ IDEA CE.app"
)

IDEA_FOUND=""
for path in "${IDEA_PATHS[@]}"; do
    if [ -d "$path" ]; then
        IDEA_FOUND="$path"
        echo "   Found: $path"
        break
    fi
done

if [ -z "$IDEA_FOUND" ]; then
    echo "   WARNING: IntelliJ IDEA not found in standard locations."
    echo "   Install from: https://www.jetbrains.com/idea/download/"
fi

# R1.3: Check JDK
echo ""
echo "3. Checking JDK..."
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    echo "   $JAVA_VER"
else
    echo "   ERROR: JDK not found. Install JDK 17+."
    exit 1
fi

# R1.4: Check accessibility permissions
echo ""
echo "4. Checking macOS Accessibility permissions (P6)..."
echo "   NOTE: Accessibility permissions must be granted manually."
echo "   Go to: System Settings > Privacy & Security > Accessibility"
echo "   Add your terminal application and IntelliJ IDEA."
echo ""

# Check if we can access System Events (basic accessibility test)
if osascript -e 'tell application "System Events" to get name of first process' &>/dev/null; then
    echo "   Accessibility permissions: GRANTED"
else
    echo "   WARNING: Accessibility permissions may not be granted."
    echo "   Some features (macOS fallback layer) may not work."
fi

# R1.5: Check/create IntelliJ properties for native dialog disabling
echo ""
echo "5. Setting up IntelliJ launch properties (R1.5)..."
IDEA_PROPERTIES="$HOME/Library/Application Support/JetBrains"
if [ -d "$IDEA_PROPERTIES" ]; then
    # Find the most recent IntelliJ config directory
    LATEST_CONFIG=$(ls -d "$IDEA_PROPERTIES"/IntelliJIdea* 2>/dev/null | sort -V | tail -1)
    if [ -z "$LATEST_CONFIG" ]; then
        LATEST_CONFIG=$(ls -d "$IDEA_PROPERTIES"/IdeaIC* 2>/dev/null | sort -V | tail -1)
    fi

    if [ -n "$LATEST_CONFIG" ]; then
        CUSTOM_PROPS="$LATEST_CONFIG/idea.properties"
        echo "   Config directory: $LATEST_CONFIG"

        # Append our properties if not already present
        PROPS_TO_ADD=(
            "ide.mac.file.chooser.native=false"
            "jbScreenMenuBar.enabled=false"
            "apple.laf.useScreenMenuBar=false"
        )

        for prop in "${PROPS_TO_ADD[@]}"; do
            if ! grep -q "$prop" "$CUSTOM_PROPS" 2>/dev/null; then
                echo "$prop" >> "$CUSTOM_PROPS"
                echo "   Added: $prop"
            else
                echo "   Already set: $prop"
            fi
        done
    fi
fi

# Check Gradle
echo ""
echo "6. Checking Gradle..."
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$SCRIPT_DIR/gradlew" ]; then
    echo "   Gradle wrapper found"
else
    echo "   Generating Gradle wrapper..."
    if command -v gradle &>/dev/null; then
        cd "$SCRIPT_DIR"
        gradle wrapper --gradle-version 8.5
        echo "   Gradle wrapper generated"
    else
        echo "   WARNING: Gradle not installed. Install via: brew install gradle"
        echo "   Then run: cd $SCRIPT_DIR && gradle wrapper --gradle-version 8.5"
    fi
fi

# Create captures directory
echo ""
echo "7. Creating output directories..."
mkdir -p "$SCRIPT_DIR/captures"
echo "   captures/ directory ready"

# Summary
echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "  1. Grant Accessibility permissions (System Settings > Privacy & Security > Accessibility)"
echo "  2. Install Remote Robot plugin in IntelliJ IDEA:"
echo "     - Settings > Plugins > Marketplace > Search 'Remote Robot'"
echo "     - Or download from: https://plugins.jetbrains.com/plugin/14200-robot-server-plugin"
echo "  3. Restart IntelliJ IDEA with robot-server enabled"
echo "  4. Run: ./gradlew test  (from project root)"
echo ""
echo "Manual IDE listener setup:"
echo "  1. Open IntelliJ IDEA"
echo "  2. Tools > IDE Scripting Console"
echo "  3. Paste contents of: ide-scripts/git-event-listener.kts"
echo "  4. Paste contents of: ide-scripts/git-push-pull-listener.kts"
echo ""
