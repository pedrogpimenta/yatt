#!/usr/bin/env bash
# Install Time Command KDE plasmoid for current user

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_DIR="$SCRIPT_DIR/package"
PLASMOID_DIR="$HOME/.local/share/plasma/plasmoids/org.kde.timecommand"

echo "Installing Time Command plasmoid to $PLASMOID_DIR"
mkdir -p "$(dirname "$PLASMOID_DIR")"
rm -rf "$PLASMOID_DIR"
cp -r "$PKG_DIR" "$PLASMOID_DIR"
echo "Done. Add the widget: right-click panel → Add Widgets → search for 'Time Command'"
echo "Then configure API URL and token in the widget settings."
