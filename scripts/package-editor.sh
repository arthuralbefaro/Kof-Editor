#!/usr/bin/env bash
#
# package-editor.sh — empacota o Kof Editor (instalador por SO).
#
# O pacote é pequeno: contém o editor-web.kf + install.sh (linux/mac) ou
# install.bat (windows). Na instalação, o script baixa a distro do Kof
# (com JDK embutido) do GitHub Releases e instala em ~/.local/share/kof-editor.
#
# Produz em dist/:
#   kof-editor-install-<os>-<arch>.tar.gz  (linux/macos)
#   kof-editor-install-<os>-<arch>.zip     (windows)
#
# Usage: scripts/package-editor.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="0.1.0-beta"
KOF_URL_LINUX="https://github.com/KofLang/Kof4j/releases/download/kof-0.2.6-beta-linux-x86_64/kof-0.2.5-beta-linux-x86_64.tar.gz"
KOF_URL_MACOS="https://github.com/KofLang/Kof4j/releases/download/kof-0.2.6-beta-macos-arm64/kof-0.2.5-beta-macos-arm64.tar.gz"
KOF_URL_WINDOWS="https://github.com/KofLang/Kof4j/releases/download/kof-0.2.6-beta-windows-x86_64/kof-0.2.5-beta-windows-x86_64.zip"
OUT="${OUTPUT_DIR:-$ROOT/dist}"

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$OS" in
    linux*) OS=linux; EXT=tar.gz ;;
    darwin*) OS=macos; EXT=tar.gz ;;
    mingw*|msys*|cygwin*) OS=windows; EXT=zip ;;
esac
ARCH="$(uname -m)"
case "$ARCH" in arm64|aarch64) ARCH=arm64 ;; *) ARCH=x86_64 ;; esac

TARGET="${OS}-${ARCH}"
BASE="kof-editor-install-$VERSION-$TARGET"
STAGE="$(mktemp -d)/$BASE"
trap 'rm -rf "$(dirname "$STAGE")"' EXIT
mkdir -p "$OUT" "$STAGE/editor"

echo "== package-editor: instalador ($TARGET) =="

# 1) front web do editor (o .kf serve é o app inteiro)
"$ROOT/scripts/web-combine.sh" >/dev/null
cp "$ROOT/build/editor-web.kf" "$STAGE/editor/src.kf"
cp "$ROOT/kof.png" "$STAGE/editor/kof.png" 2>/dev/null || true

# 2) instalador unix (linux/macos)
cat > "$STAGE/install.sh" <<'INSTALL'
#!/usr/bin/env bash
# Kof Editor — instalador (linux/macos)
# Uso: ./install.sh [pasta-de-destino]
set -euo pipefail

DEST="${1:-$HOME/.local/share/kof-editor}"
BIN_DIR="$HOME/.local/bin"
HERE="$(cd "$(dirname "$0")" && pwd)"
KOF_URL="${KOF_DISTRO_URL:-__KOF_URL__}"

echo "== Kof Editor: instalando em $DEST =="

# 1) distro do Kof (bin/kof + JDK embutido, baixado 1x)
if [ ! -x "$DEST/bin/kof" ]; then
    echo "  baixando runtime Kof..."
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    curl -L "$KOF_URL" -o "$TMP/kof.tar.gz"
    mkdir -p "$DEST"
    tar xzf "$TMP/kof.tar.gz" -C "$DEST" --strip-components=1
fi

# 2) editor (front web)
mkdir -p "$DEST/editor"
cp "$HERE/editor/src.kf" "$DEST/editor/src.kf"
cp "$HERE/editor/kof.png" "$DEST/editor/" 2>/dev/null || true

# 3) launcher no PATH
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/kof-editor" <<LAUNCH
#!/usr/bin/env bash
exec "$DEST/bin/kof" serve "$DEST/editor/src.kf" --port "\${KOF_EDITOR_PORT:-8765}"
LAUNCH
chmod +x "$BIN_DIR/kof-editor"

echo "== pronto =="
echo "  launcher: $BIN_DIR/kof-editor"
echo "  rode:     kof-editor    (e abra http://localhost:8765)"
case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *) echo "  (adicione $BIN_DIR ao PATH se necessário)" ;;
esac
INSTALL
case "$OS" in
    macos) DEF_KOF_URL="$KOF_URL_MACOS" ;;
    *)     DEF_KOF_URL="$KOF_URL_LINUX" ;;
esac
sed -i "s|__KOF_URL__|${KOF_DISTRO_URL:-$DEF_KOF_URL}|" "$STAGE/install.sh"
chmod +x "$STAGE/install.sh"

# 3) instalador windows
cat > "$STAGE/install.bat" <<'INSTALL'
@echo off
REM Kof Editor — instalador (windows)
setlocal
set HERE=%~dp0
set DEST=%LOCALAPPDATA%\kof-editor

echo == Kof Editor: instalando em %DEST% ==

if not exist "%DEST%\bin\kof.bat" (
    echo   baixando runtime Kof...
    powershell -Command "Invoke-WebRequest -Uri '__KOF_URL_WINDOWS__' -OutFile '%TEMP%\kof.zip'"
    powershell -Command "Expand-Archive -Force '%TEMP%\kof.zip' '%TEMP%\kof-distro'"
    for /d %%D in ("%TEMP%\kof-distro\*") do xcopy "%%D" "%DEST%" /E /I /Y >nul
)

mkdir "%DEST%\editor" 2>nul
copy /Y "%HERE%editor\src.kf" "%DEST%\editor\src.kf" >nul
copy /Y "%HERE%editor\kof.png" "%DEST%\editor\" >nul 2>&1

set LAUNCHER=%USERPROFILE%\kof-editor.bat
(
    echo @echo off
    echo "%DEST%\bin\kof.bat" serve "%DEST%\editor\src.kf" --port %%KOF_EDITOR_PORT%%
) > "%LAUNCHER%"

echo == pronto ==
echo   launcher: %LAUNCHER%
echo   rode kof-editor e abra http://localhost:8765
INSTALL
sed -i "s|__KOF_URL_WINDOWS__|$KOF_URL_WINDOWS|" "$STAGE/install.bat"

# 4) arquivar
cd "$(dirname "$STAGE")"
if [ "$EXT" = "zip" ]; then
    python3 -m zipfile -c "$OUT/$BASE.zip" "$BASE"
    echo "  -> $OUT/$BASE.zip"
else
    tar -czf "$OUT/$BASE.tar.gz" "$BASE"
    echo "  -> $OUT/$BASE.tar.gz"
fi

cd "$OUT"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$BASE.$EXT" > "$OUT/SHA256SUMS" 2>/dev/null || true
fi
echo "== pronto: $OUT =="
ls -la "$OUT"
