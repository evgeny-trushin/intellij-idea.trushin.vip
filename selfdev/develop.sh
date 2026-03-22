#!/bin/bash
#
# Self-Development System
# Analyzes codebase state and generates prompts from multiple perspectives
#
# Usage:
#   ./develop.sh           # Run all perspectives
#   ./develop.sh --user    # User perspective: features, UX, documentation
#   ./develop.sh --test    # Test perspective: coverage, robustness
#   ./develop.sh --system  # System perspective: architecture, complexity
#   ./develop.sh --analytics # Analytics perspective: trends, patterns
#   ./develop.sh --debug   # Debug perspective: issues, TODOs

#
# Additional options:
#   --state     Show current organism state
#   --advance   Advance to next generation
#   --revert=N  Revert increment N using git history
#   --revert_from=N  Revert from increment N to current todo
#   --redo=N    Revert and re-implement increment N
#   --selfdev   Analyze the selfdev system itself
#   --root DIR  Analyze a specific directory
#   --help      Show this help message
#

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/organism.py"

# Banner
show_banner() {
    echo ""
    echo "  ╔══════════════════════════════════════════════════════════╗"
    echo "  ║     SELF-DEVELOPMENT ORGANISM                            ║"
    echo "  ║     Biological Development Principles for Software       ║"
    echo "  ║     https://github.com/evgeny-trushin/selfdev            ║"
    echo "  ╚══════════════════════════════════════════════════════════╝"
    echo ""
}

# Help message
show_help() {
    show_banner
    echo "Usage: $(basename "$0") [OPTIONS]"
    echo ""
    echo "Perspectives (run one or more):"
    echo "  --user       Analyze from user's point of view"
    echo "               Features, documentation, UX, onboarding"
    echo ""
    echo "  --test       Analyze test coverage and quality"
    echo "               Coverage ratio, missing tests, robustness"
    echo ""
    echo "  --system     Analyze architecture and code quality"
    echo "               Complexity, coupling, cohesion, patterns"
    echo ""
    echo "  --analytics  Analyze trends and patterns over time"
    echo "               Fitness trends, commit patterns, predictions"
    echo ""
    echo "  --debug      Analyze for bugs and issues"
    echo "               TODOs, FIXMEs, errors, uncommitted changes"
    echo ""

    echo "Options:"
    echo "  --all        Run all perspectives (default if no perspective specified)"
    echo "  --state      Show current organism state"
    echo "  --advance    Advance to next generation after analysis"
    echo "  --revert=N   Generate prompt to revert increment N using git history"
    echo "  --revert_from=N  Revert from increment N to current todo using git history"
    echo "  --redo=N     Revert increment N and generate prompt to re-implement it"
    echo "  --selfdev    Analyze the selfdev system itself (instead of the project)"
    echo "  --root DIR   Analyze a specific directory"
    echo "  --help       Show this help message"
    echo ""
    echo "Examples:"
    echo "  $(basename "$0")               # Run all perspectives"
    echo "  $(basename "$0") --user --test # Run user and test perspectives"
    echo "  $(basename "$0") --debug       # Only debug perspective"
    echo "  $(basename "$0") --state       # Show current state"
    echo "  $(basename "$0") --selfdev     # Analyze the selfdev system itself"
    echo "  $(basename "$0") --root /path  # Analyze a specific project"
    echo "  $(basename "$0") --revert=0001 # Revert increment 0001"
    echo "  $(basename "$0") --revert_from=0020  # Revert from 0020 to current"
    echo "  $(basename "$0") --redo=0001   # Revert and redo increment 0001"
    echo ""
    echo "The system generates development prompts based on:"
    echo "  - Sequential increments from requirements/"
    echo "  - Principles injected from principles/"
    echo "  - Current codebase state"
    echo "  - Evolutionary history (stored in organism_state.json)"
    echo ""
    echo "Increment-driven workflow:"
    echo "  1. Run ./develop.sh           \u2192 see current TODO increment"
    echo "  2. Implement the requirement"
    echo "  3. git add -A && git commit -m \"INCREMENT XXXX: desc\" && git push"
    echo "  4. Run ./develop.sh           → verify done, see next increment"
    echo "  5. Repeat from step 2"
    echo ""
}

# Check Python is available
check_python() {
    if command -v python3 &> /dev/null; then
        PYTHON_CMD="python3"
    elif command -v python &> /dev/null; then
        PYTHON_CMD="python"
    else
        echo "Error: Python not found. Please install Python 3.8+"
        exit 1
    fi

    # Check Python version
    PYTHON_VERSION=$($PYTHON_CMD -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
    PYTHON_MAJOR=$($PYTHON_CMD -c 'import sys; print(sys.version_info.major)')
    PYTHON_MINOR=$($PYTHON_CMD -c 'import sys; print(sys.version_info.minor)')

    if [ "$PYTHON_MAJOR" -lt 3 ] || { [ "$PYTHON_MAJOR" -eq 3 ] && [ "$PYTHON_MINOR" -lt 8 ]; }; then
        echo "Warning: Python 3.8+ recommended. Found Python ${PYTHON_VERSION}"
    fi
}

# Check if the Python script exists
check_script() {
    if [ ! -f "$PYTHON_SCRIPT" ]; then
        echo "Error: organism.py not found at ${PYTHON_SCRIPT}"
        echo "Please ensure the Self-Development System is properly installed."
        exit 1
    fi
}

# Main function
main() {
    # Handle help first
    for arg in "$@"; do
        if [ "$arg" = "--help" ] || [ "$arg" = "-h" ]; then
            show_help
            exit 0
        fi
    done

    # Check prerequisites
    check_python
    check_script

    # Show banner
    show_banner

    # Pass all arguments to Python script
    $PYTHON_CMD "$PYTHON_SCRIPT" "$@"

    # Capture exit code
    EXIT_CODE=$?

    # Show footer message if successful
    if [ $EXIT_CODE -eq 0 ]; then
        echo ""
        echo "Analysis complete."
        echo ""
        echo "╔══════════════════════════════════════════════════════════════════════╗"
        echo "║  INCREMENT WORKFLOW:                                                 ║"
        echo "║                                                                      ║"
        echo "║  1. Implement the increment requirement above                        ║"
        echo "║  2. Run tests:  python -m pytest selfdev/tests/                      ║"
        echo "║  3. Run linter: python -m py_compile <changed files>                 ║"
        echo "║  4. List changed files in your commit body                           ║"
        echo "║  5. Commit with the suggested message above                          ║"
        echo "║  6. Run ./develop.sh  (verify & get next increment)                  ║"
        echo "║                                                                      ║"
        echo "║  RULES:                                                              ║"
        echo "║  • Do NOT auto-advance by rerunning develop.sh prematurely           ║"
        echo "║  • Only mark done after tests pass & files are implemented           ║"
        echo "║  • Map review comments to exact diff hunks in your summary           ║"
        echo "╚══════════════════════════════════════════════════════════════════════╝"
    fi

    exit $EXIT_CODE
}

# Run main with all arguments
main "$@"
