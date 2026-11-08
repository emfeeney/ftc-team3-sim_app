# ===========================================================================
# Web-Desktop Wrapper for the Original FTC Test Harness & VI_Arena
# Runs the exact unmodified Java viewer (gradlew viewRecording) and VI_Arena
# inside an X11 virtual desktop and streams the live window to Chrome via noVNC.
# ===========================================================================
FROM eclipse-temurin:21-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive
ENV DISPLAY=:99
ENV SCREEN_WIDTH=1600
ENV SCREEN_HEIGHT=1000
ENV SCREEN_DEPTH=24
ENV NOVNC_PORT=6080
ENV VNC_PORT=5900

# Install Xvfb (virtual display), Fluxbox (window manager), x11vnc, noVNC,
# websockify, Wine (to run VI_Arena.exe unmodified), and SDL2 for Jamepad.
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
      xvfb \
      fluxbox \
      x11vnc \
      novnc \
      websockify \
      wine64 \
      wine32 \
      libsdl2-2.0-0 \
      libsdl2-dev \
      x11-utils \
      procps \
      curl \
      ca-certificates \
      unzip \
      git && \
    rm -rf /var/lib/apt/lists/*

# Redirect / on the noVNC web server directly to the auto-connecting VNC view
RUN ln -sf /usr/share/novnc/vnc.html /usr/share/novnc/index.html

WORKDIR /workspace

# Copy the startup wrapper script
COPY web/start-web-desktop.sh /usr/local/bin/start-web-desktop.sh
RUN chmod +x /usr/local/bin/start-web-desktop.sh

EXPOSE 6080

CMD ["/usr/local/bin/start-web-desktop.sh"]

