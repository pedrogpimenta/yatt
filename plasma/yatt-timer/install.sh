#!/bin/bash
set -e

WIDGET_DIR="$(cd "$(dirname "$0")" && pwd)"
WIDGET_ID="com.pimenta.yatt-timer"

echo "Installing YATT Timer widget..."

# Try upgrade first (if already installed), then fresh install
if kpackagetool6 --type Plasma/Applet --show "$WIDGET_ID" &>/dev/null; then
    echo "Upgrading existing installation..."
    kpackagetool6 --type Plasma/Applet --upgrade "$WIDGET_DIR"
else
    echo "Installing fresh..."
    kpackagetool6 --type Plasma/Applet --install "$WIDGET_DIR"
fi

echo ""
echo "Done! To add the widget:"
echo "  Right-click the panel → 'Add or Manage Widgets' → search for 'YATT Timer'"
echo ""
echo "To reload Plasma without logging out (if needed):"
echo "  kquitapp6 plasmashell && kstart plasmashell"
