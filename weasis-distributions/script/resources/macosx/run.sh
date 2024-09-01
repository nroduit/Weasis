#!/bin/bash
# Get the path to the directory containing this script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/Weasis" "$@"