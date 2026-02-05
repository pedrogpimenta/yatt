#!/bin/bash

# Install YATT KDE Plasma Widget

PACKAGE_DIR="$(dirname "$0")/package"
INSTALL_DIR="$HOME/.local/share/plasma/plasmoids/org.kde.yatt"

# Remove old installation
if [ -d "$INSTALL_DIR" ]; then
    echo "Removing old installation..."
    rm -rf "$INSTALL_DIR"
fi

# Install
echo "Installing to $INSTALL_DIR..."
mkdir -p "$INSTALL_DIR"
cp -r "$PACKAGE_DIR"/* "$INSTALL_DIR"

echo "Done! You may need to restart Plasma or log out/in."
echo ""
echo "To restart Plasma (Wayland), run:"
echo "  systemctl --user restart plasma-plasmashell"
echo ""
echo "Or for X11:"
echo "  kquitapp6 plasmashell && kstart plasmashell"
echo ""
echo "Then right-click your panel > Add Widgets > Search for 'Time Command'"
