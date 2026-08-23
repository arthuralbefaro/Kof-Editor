#!/usr/bin/env bash
set -euo pipefail

KOF="${KOF:-kof}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$HOME/.local/share/kof-editor"
PID_DIR="$HOME/.config/kof-editor"

echo "== buildando o front web (Kof) =="
"$ROOT/scripts/web-combine.sh"
"$KOF" check "$ROOT/build/editor-web.kf" >/dev/null 2>&1 || { echo "falha ao validar o front web"; exit 1; }

echo "== instalando em $DEST =="
mkdir -p "$DEST" "$PID_DIR"
cp "$ROOT/build/editor-web.kf" "$DEST/editor-web.kf"
cp "$ROOT/kof.png" "$DEST/kof.png"

# webview nativo do Kof (WebKitGTK) — shell da janela
mkdir -p "$HOME/.local/share/kof-dev/bin"
cp "/home/mel/Documentos/Kof4j/bin/kof-webview" "$HOME/.local/share/kof-dev/bin/" 2>/dev/null || true
chmod +x "$HOME/.local/share/kof-dev/bin/kof-webview" 2>/dev/null || true

# pagina da janela: iframe para o editor servido (vira index.html quando J6 cair)
cat > "$DEST/editor-ui.html" <<'UIEOF'
<!DOCTYPE html>
<html>
<head><title>Kof Editor</title>
<link rel="icon" href="kof.png">
</head>
<body style="margin:0;background:#1e1e24">
<div id="boot" style="position:fixed;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#8be9fd;font-family:monospace">
  <img src="kof.png" alt="Kof" style="width:110px">
  <div style="color:#a0a8b0">iniciando Kof Editor...</div>
</div>
<iframe id="kof-frame" style="width:100vw;height:100vh;border:none;display:none"></iframe>
<script>
var f = document.getElementById('kof-frame');
var boot = document.getElementById('boot');
var u = new URL(location.href);
var openPath = u.searchParams.get('open') || '';
var tries = 0;
function start() {
  fetch('http://localhost:8765/', {mode: 'no-cors'}).then(function() {
    f.src = 'http://localhost:8765/?v=' + Date.now() + (openPath ? '&open=' + encodeURIComponent(openPath) : '');
    f.style.display = 'block';
    boot.style.display = 'none';
  }).catch(function() {
    tries++;
    boot.querySelector('div:last-child').textContent =
      'aguardando o servidor Kof... (' + tries + 's)';
    setTimeout(start, 1000);
  });
}
setTimeout(start, 500);
</script>
</body>
</html>
UIEOF

cat > "$HOME/.local/bin/kof-editor" <<'KOFEOF'
#!/usr/bin/env bash
# kof-editor — editor 100% Kof: kof serve + janela nativa (kof-webview/WebKitGTK)
KOF="$HOME/.local/bin/kof"
WEB="$HOME/.local/share/kof-editor/editor-web.kf"
UI="$HOME/.local/share/kof-editor/editor-ui.html"
WEBVIEW="$HOME/.local/share/kof-dev/bin/kof-webview"
PORT="${KOF_EDITOR_PORT:-8765}"
PID_DIR="$HOME/.config/kof-editor"
mkdir -p "$PID_DIR"

stop_all() {
    if [ -f "$PID_DIR/webview.pid" ]; then
        kill "$(cat "$PID_DIR/webview.pid")" 2>/dev/null
        rm -f "$PID_DIR/webview.pid"
    fi
    if [ -f "$PID_DIR/server.pid" ]; then
        kill "$(cat "$PID_DIR/server.pid")" 2>/dev/null
        rm -f "$PID_DIR/server.pid"
    fi
}

if [ "${1:-}" = "--stop" ]; then
    stop_all
    echo "kof-editor parado"
    exit 0
fi

if [ -f "$PID_DIR/server.pid" ] && kill -0 "$(cat "$PID_DIR/server.pid")" 2>/dev/null; then
    echo "servidor ja rodando"
else
    nohup "$KOF" serve "$WEB" --port "$PORT" >/dev/null 2>&1 &
    echo $! > "$PID_DIR/server.pid"
fi

if [ -f "$PID_DIR/webview.pid" ] && kill -0 "$(cat "$PID_DIR/webview.pid")" 2>/dev/null; then
    echo "janela ja aberta"
    exit 0
fi

OPEN_URL=""
if [ -n "${1:-}" ] && [ "${1:-}" != "--stop" ]; then
    ABS="$(realpath "$1" 2>/dev/null || echo "$1")"
    OPEN_URL="?open=$ABS"
fi

sleep 3
nohup "$WEBVIEW" "$UI" "Kof Editor" "$HOME/.local/share/kof-editor/kof.png" >/dev/null 2>&1 &
echo $! > "$PID_DIR/webview.pid"
echo "Kof Editor aberto (janela nativa WebKitGTK)${OPEN_URL:+ — abrindo: $1}"
KOFEOF
chmod +x "$HOME/.local/bin/kof-editor"

cat > "$HOME/.local/share/applications/kof-editor.desktop" <<EOF
[Desktop Entry]
Name=Kof Editor
Comment=Editor de codigo escrito inteiramente em Kof
Exec=$HOME/.local/bin/kof-editor %F
Icon=$DEST/kof.png
Terminal=false
Type=Application
Categories=Development;IDE;
MimeType=text/plain;text/x-kof;application/json;text/markdown;
EOF
chmod +x "$HOME/.local/share/applications/kof-editor.desktop"
xdg-mime default kof-editor.desktop text/x-kof application/json text/markdown 2>/dev/null || true

echo ""
echo "instalado:"
echo "  janela  -> kof-webview nativo (WebKitGTK)"
echo "  abra com: kof-editor [arquivo]   (pare com: kof-editor --stop)"