#!/bin/bash

# Uninstall YATT KDE Plasma Widget

INSTALL_DIR="$HOME/.local/share/plasma/plasmoids/org.kde.yatt"

if [ -d "$INSTALL_DIR" ]; then
    echo "Removing $INSTALL_DIR..."
    rm -rf "$INSTALL_DIR"
    echo "Done! Restart Plasma to complete removal."
else
    echo "Widget not installed."
fi
