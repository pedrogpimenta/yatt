#!/bin/bash
set -e
TARGET="$HOME/.local/share/plasma/plasmoids/org.kde.plasma.yatt"
echo "Removing YATT Timer widget..."
rm -rf "$TARGET"
echo "Done."
