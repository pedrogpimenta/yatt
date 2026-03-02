#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET="$HOME/.local/share/plasma/plasmoids/org.kde.plasma.yatt"

echo "Checking dependencies..."
if ! pacman -Qq qt6-websockets &>/dev/null; then
    echo "qt6-websockets is required. Installing..."
    sudo pacman -S --noconfirm qt6-websockets
fi

echo "Installing YATT Timer widget..."

rm -rf "$TARGET"
mkdir -p "$HOME/.local/share/plasma/plasmoids"
cp -r "$SCRIPT_DIR/package" "$TARGET"

echo "Installed to $TARGET"
echo ""
echo "Restart the Plasma shell to apply:"
echo "  Wayland: systemctl --user restart plasma-plasmashell"
echo "  X11:     kquitapp6 plasmashell && kstart plasmashell"
