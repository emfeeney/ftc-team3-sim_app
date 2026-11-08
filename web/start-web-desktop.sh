#!/usr/bin/env bash
# ===========================================================================
# Starts the virtual X11 display, VNC server, noVNC web streamer (port 6080),
# and launches the original unmodified FTC Test Harness application.
#
# Modes (set via APP_MODE environment variable or first argument):
#   viewer   (default) -> runs ./gradlew viewRecording (same as view.bat)
#   vi_arena           -> runs VI_Arena/dist/VI_Arena.exe + unityBridge (same as VI_Arena.bat)
#   both               -> runs both windows side-by-side on the virtual desktop
# ===========================================================================
set -e

export DISPLAY="${DISPLAY:-:99}"
SCREEN_WIDTH="${SCREEN_WIDTH:-1600}"
SCREEN_HEIGHT="${SCREEN_HEIGHT:-1000}"
SCREEN_DEPTH="${SCREEN_DEPTH:-24}"
VNC_PORT="${VNC_PORT:-5900}"
NOVNC_PORT="${NOVNC_PORT:-6080}"
APP_MODE="${1:-${APP_MODE:-viewer}}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ ! -d "$REPO_ROOT/th_src" ] && [ -d "/workspace/th_src" ]; then
  REPO_ROOT="/workspace"
fi

echo "[web-wrapper] Starting virtual display ${DISPLAY} (${SCREEN_WIDTH}x${SCREEN_HEIGHT}x${SCREEN_DEPTH})..."
rm -f "/tmp/.X${DISPLAY#:}-lock"
Xvfb "${DISPLAY}" -screen 0 "${SCREEN_WIDTH}x${SCREEN_HEIGHT}x${SCREEN_DEPTH}" -ac +extension GLX +render -noreset &
sleep 1

echo "[web-wrapper] Starting Fluxbox window manager..."
fluxbox -display "${DISPLAY}" >/dev/null 2>&1 &
sleep 1

echo "[web-wrapper] Starting x11vnc on port ${VNC_PORT}..."
x11vnc -display "${DISPLAY}" -forever -shared -nopw -rfbport "${VNC_PORT}" -xkb -ncache 10 -quiet &
sleep 1

echo "[web-wrapper] Starting noVNC web server on http://0.0.0.0:${NOVNC_PORT}/vnc.html?autoconnect=true&resize=scale ..."
websockify --web=/usr/share/novnc/ "${NOVNC_PORT}" "localhost:${VNC_PORT}" >/dev/null 2>&1 &

echo "==========================================================================="
echo " Original FTC Test Harness is now streaming to Chrome!"
echo " Open in Chrome: http://localhost:${NOVNC_PORT}/vnc.html?autoconnect=true&resize=scale"
echo "==========================================================================="

cd "${REPO_ROOT}/th_src"
chmod +x ./gradlew

launch_java_viewer() {
  echo "[web-wrapper] Launching original Java Swing viewer (./gradlew viewRecording)..."
  while true; do
    ./gradlew viewRecording --no-daemon || true
    echo "[web-wrapper] Viewer window closed; restarting in 2s..."
    sleep 2
  done
}

launch_vi_arena() {
  echo "[web-wrapper] Starting ./gradlew unityBridge in the background for Live mode..."
  ./gradlew unityBridge --no-daemon &
  if [ -f "${REPO_ROOT}/VI_Arena/dist/VI_Arena.exe" ]; then
    echo "[web-wrapper] Launching unmodified VI_Arena/dist/VI_Arena.exe via Wine..."
    while true; do
      wine "${REPO_ROOT}/VI_Arena/dist/VI_Arena.exe" -recordings "${REPO_ROOT}/th_src/build/recordings" || true
      sleep 2
    done
  else
    echo "[web-wrapper] VI_Arena/dist/VI_Arena.exe not found; falling back to ./gradlew viewRecording..."
    launch_java_viewer
  fi
}

case "${APP_MODE}" in
  vi_arena)
    launch_vi_arena
    ;;
  both)
    ./gradlew viewRecording --no-daemon &
    launch_vi_arena
    ;;
  *)
    launch_java_viewer
    ;;
esac

