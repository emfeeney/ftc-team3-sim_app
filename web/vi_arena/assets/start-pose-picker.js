/**
 * FTC Custom Robot Start Pose Controller
 * Provides interactive 2D field canvas placement, heading angle controls,
 * and instant in-browser trajectory replay.
 */
(function () {
  let modal = null;
  let canvas = null;
  let ctx = null;
  let isDragging = false;
  let isRotating = false;

  // State in field inches (-72 to +72) and degrees (-180 to +180)
  let currentPose = {
    x: -60.0,
    y: 36.0,
    deg: 0.0
  };

  const DEFAULT_HF_URL = "https://ftc-sim-api.hf.space";
  let serverUrl = localStorage.getItem("ftc_sim_server_url") || (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1" ? "http://127.0.0.1:7860" : DEFAULT_HF_URL);

  function getActiveTeam() {
    const sel = document.getElementById("team-select");
    return sel ? sel.value : "team3";
  }

  function getActiveOpMode() {
    const sel = document.getElementById("spp-opmode-select") || document.getElementById("recording-select");
    if (!sel) return "Team3PedroPilotAutoOpMode";
    let val = sel.value;
    return val.replace(/^auto-/, "").replace(/\.json$/, "");
  }

  function initModal() {
    if (document.getElementById("start-pose-modal")) return;

    modal = document.createElement("div");
    modal.id = "start-pose-modal";
    modal.innerHTML = `
      <div class="spp-dialog" role="dialog" aria-label="Start Pose Picker">
        <div class="spp-header">
          <div class="spp-title-group">
            <span class="spp-title">🎯 Set Robot Start Pose</span>
            <span id="spp-robot-badge" class="spp-badge">Team 3 (18"×18")</span>
          </div>
          <button class="spp-close-btn" onclick="closeStartPoseModal()" title="Close (Esc)">✕</button>
        </div>

        <div class="spp-body">
          <!-- LEFT: 2D Field Top-down Canvas -->
          <div class="spp-field-col">
            <div class="spp-canvas-wrapper" id="spp-canvas-wrap">
              <canvas id="spp-field-canvas" width="440" height="440"></canvas>
              <div class="spp-field-hud">
                <span class="spp-hud-pill" id="spp-hud-coords">X: -60.0" | Y: 36.0" | 0.0°</span>
                <span class="spp-hud-pill" style="color: #8b949e;">Click/Drag on Field</span>
              </div>
            </div>

            <!-- Preset Buttons -->
            <div class="spp-presets-bar">
              <button class="spp-preset-btn spp-preset-blue" onclick="applyPreset(-60, 36, 0)">🔵 Blue Aud (0°)</button>
              <button class="spp-preset-btn spp-preset-blue" onclick="applyPreset(-60, -36, 0)">🔵 Blue Sub (0°)</button>
              <button class="spp-preset-btn spp-preset-red" onclick="applyPreset(60, -36, 180)">🔴 Red Aud (180°)</button>
              <button class="spp-preset-btn spp-preset-red" onclick="applyPreset(60, 36, 180)">🔴 Red Sub (180°)</button>
              <button class="spp-preset-btn" onclick="applyPreset(0, 0, 0)">🎯 Center (0,0)</button>
            </div>
          </div>

          <!-- RIGHT: Coordinate Controls & Simulation CTA -->
          <div class="spp-controls-col">
            <!-- Precise Coordinate Sliders -->
            <div class="spp-card">
              <div class="spp-card-title">
                <span>Field Position & Heading</span>
                <span style="font-size: 11px; color: #58a6ff; font-weight: normal;">FTC Inches & Degrees</span>
              </div>

              <!-- X Slider -->
              <div class="spp-slider-group">
                <div class="spp-slider-row">
                  <span class="spp-slider-label">X Position:</span>
                  <input type="range" class="spp-slider-input" id="spp-slider-x" min="-63" max="63" step="0.5" value="-60">
                  <input type="number" class="spp-num-input" id="spp-num-x" min="-63" max="63" step="0.5" value="-60.0">
                </div>
              </div>

              <!-- Y Slider -->
              <div class="spp-slider-group">
                <div class="spp-slider-row">
                  <span class="spp-slider-label">Y Position:</span>
                  <input type="range" class="spp-slider-input" id="spp-slider-y" min="-63" max="63" step="0.5" value="36">
                  <input type="number" class="spp-num-input" id="spp-num-y" min="-63" max="63" step="0.5" value="36.0">
                </div>
              </div>

              <!-- Heading Dial/Slider -->
              <div class="spp-slider-group">
                <div class="spp-slider-row">
                  <span class="spp-slider-label">Heading (θ):</span>
                  <input type="range" class="spp-slider-input" id="spp-slider-deg" min="-180" max="180" step="1" value="0">
                  <input type="number" class="spp-num-input" id="spp-num-deg" min="-180" max="180" step="1" value="0">
                </div>
                <div class="spp-quick-angles">
                  <button class="spp-angle-btn" onclick="setAngle(0)">0° (Audience)</button>
                  <button class="spp-angle-btn" onclick="setAngle(90)">90° (Left)</button>
                  <button class="spp-angle-btn" onclick="setAngle(180)">180° (Back)</button>
                  <button class="spp-angle-btn" onclick="setAngle(-90)">-90° (Right)</button>
                </div>
              </div>
            </div>

            <!-- Autonomous Routine Selection -->
            <div class="spp-card">
              <div class="spp-card-title">
                <span>Autonomous OpMode</span>
                <span id="spp-opmode-type" style="font-size: 11px; color: #3fb950; font-weight: normal;">Pedro Pathing</span>
              </div>
              <select id="spp-opmode-select" class="spp-select">
                <option value="Team3PedroPilotAutoOpMode" selected>Team3PedroPilotAutoOpMode (Pedro Pathing)</option>
                <option value="Team3AutonomousOpMode">Team3AutonomousOpMode (Score Specimen)</option>
                <option value="Team3AutoOpMode">Team3AutoOpMode</option>
                <option value="Team3TempStrafeRight8AutoOpMode">Team3TempStrafeRight8AutoOpMode</option>
                <option value="Team3WebcamTestAutoOpMode">Team3WebcamTestAutoOpMode</option>
              </select>
            </div>

            <!-- Default Pose Reset -->
            <div class="spp-card">
              <div class="spp-card-title">
                <span>Start Pose Mode</span>
                <span class="spp-status-badge spp-status-online">⚡ Instant (In-Browser)</span>
              </div>
              <div style="font-size: 11.5px; color: #8b949e; line-height: 1.45; margin-bottom: 8px;">
                Defaults automatically to the Pedro / OpMode start pose from the selected run. Drag on the field to test from anywhere.
              </div>
              <button class="spp-ping-btn" style="width: 100%;" onclick="resetToDefaultPedroPose()">↺ Reset to Pedro / Default Start Pose</button>
              <div id="spp-error-msg" style="display: none; margin-top: 8px; font-size: 11.5px; color: #f85149; line-height: 1.4; padding: 6px; background: rgba(248, 81, 73, 0.1); border-radius: 6px; border: 1px solid rgba(248, 81, 73, 0.3);"></div>
            </div>

            <!-- Action Button -->
            <button id="spp-simulate-btn" class="spp-cta-btn" onclick="runCustomSimulation()">
              <span id="spp-btn-icon">⚡</span>
              <span id="spp-btn-text">Replay From This Start Pose (Instant)</span>
            </button>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    // Toast element for completion feedback
    if (!document.getElementById("spp-toast")) {
      const toast = document.createElement("div");
      toast.id = "spp-toast";
      toast.innerHTML = `<span style="font-size: 16px;">✓</span> <span id="spp-toast-text">Replaying from custom start pose!</span>`;
      document.body.appendChild(toast);
    }

    setupCanvas();
    setupInputs();
    // In-browser instant pose transform — no external backend needed
  }

  function setupCanvas() {
    canvas = document.getElementById("spp-field-canvas");
    if (!canvas) return;
    ctx = canvas.getContext("2d");

    // Match canvas dimensions to actual client width
    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    canvas.width = (rect.width || 440) * dpr;
    canvas.height = (rect.height || 440) * dpr;
    ctx.scale(dpr, dpr);

    // Mouse / Touch handlers for dragging
    function getPointerFieldCoords(e) {
      const r = canvas.getBoundingClientRect();
      const clientX = e.touches ? e.touches[0].clientX : e.clientX;
      const clientY = e.touches ? e.touches[0].clientY : e.clientY;
      const px = clientX - r.left;
      const py = clientY - r.top;
      const width = r.width;
      const height = r.height;

      // Map canvas pixels (0 to width) to field inches (-72 to +72)
      // Field center is (width/2, height/2)
      const inchPerPixel = 144.0 / width;
      let x = (px - width / 2) * inchPerPixel;
      let y = (height / 2 - py) * inchPerPixel; // standard FTC coordinate: +Y is forward / up

      // Clamp to robot radius within field boundary (robot is 18x18, so half-width = 9)
      const limit = 63.0;
      x = Math.max(-limit, Math.min(limit, x));
      y = Math.max(-limit, Math.min(limit, y));
      return { x, y };
    }

    canvas.addEventListener("mousedown", (e) => {
      isDragging = true;
      const { x, y } = getPointerFieldCoords(e);
      updatePose(x, y, currentPose.deg);
    });

    window.addEventListener("mousemove", (e) => {
      if (!isDragging) return;
      const { x, y } = getPointerFieldCoords(e);
      updatePose(x, y, currentPose.deg);
    });

    window.addEventListener("mouseup", () => {
      isDragging = false;
    });

    // Touch support
    canvas.addEventListener("touchstart", (e) => {
      isDragging = true;
      const { x, y } = getPointerFieldCoords(e);
      updatePose(x, y, currentPose.deg);
      e.preventDefault();
    }, { passive: false });

    window.addEventListener("touchmove", (e) => {
      if (!isDragging) return;
      const { x, y } = getPointerFieldCoords(e);
      updatePose(x, y, currentPose.deg);
    });

    window.addEventListener("touchend", () => {
      isDragging = false;
    });

    // Mouse wheel over canvas adjusts heading angle
    canvas.addEventListener("wheel", (e) => {
      e.preventDefault();
      const delta = e.deltaY > 0 ? -5 : 5;
      let newDeg = currentPose.deg + delta;
      if (newDeg > 180) newDeg -= 360;
      if (newDeg < -180) newDeg += 360;
      updatePose(currentPose.x, currentPose.y, newDeg);
    }, { passive: false });

    renderCanvas();
  }

  function setupInputs() {
    const sliderX = document.getElementById("spp-slider-x");
    const numX = document.getElementById("spp-num-x");
    const sliderY = document.getElementById("spp-slider-y");
    const numY = document.getElementById("spp-num-y");
    const sliderDeg = document.getElementById("spp-slider-deg");
    const numDeg = document.getElementById("spp-num-deg");
    const serverInput = document.getElementById("spp-server-url");

    if (sliderX && numX) {
      sliderX.addEventListener("input", (e) => updatePose(parseFloat(e.target.value), currentPose.y, currentPose.deg));
      numX.addEventListener("change", (e) => updatePose(parseFloat(e.target.value) || 0, currentPose.y, currentPose.deg));
    }
    if (sliderY && numY) {
      sliderY.addEventListener("input", (e) => updatePose(currentPose.x, parseFloat(e.target.value), currentPose.deg));
      numY.addEventListener("change", (e) => updatePose(currentPose.x, parseFloat(e.target.value) || 0, currentPose.deg));
    }
    if (sliderDeg && numDeg) {
      sliderDeg.addEventListener("input", (e) => updatePose(currentPose.x, currentPose.y, parseFloat(e.target.value)));
      numDeg.addEventListener("change", (e) => updatePose(currentPose.x, currentPose.y, parseFloat(e.target.value) || 0));
    }

    if (serverInput) {
      serverInput.addEventListener("change", (e) => {
        serverUrl = e.target.value.trim().replace(/\/+$/, "");
        localStorage.setItem("ftc_sim_server_url", serverUrl);
        checkServerHealth(true);
      });
    }
  }

  function updatePose(x, y, deg) {
    currentPose.x = Math.round(x * 10) / 10;
    currentPose.y = Math.round(y * 10) / 10;
    currentPose.deg = Math.round(deg);

    // Update form elements
    const sx = document.getElementById("spp-slider-x");
    const nx = document.getElementById("spp-num-x");
    const sy = document.getElementById("spp-slider-y");
    const ny = document.getElementById("spp-num-y");
    const sd = document.getElementById("spp-slider-deg");
    const nd = document.getElementById("spp-num-deg");
    const hud = document.getElementById("spp-hud-coords");

    if (sx) sx.value = currentPose.x;
    if (nx) nx.value = currentPose.x.toFixed(1);
    if (sy) sy.value = currentPose.y;
    if (ny) ny.value = currentPose.y.toFixed(1);
    if (sd) sd.value = currentPose.deg;
    if (nd) nd.value = currentPose.deg;
    if (hud) {
      hud.textContent = `X: ${currentPose.x.toFixed(1)}" | Y: ${currentPose.y.toFixed(1)}" | ${currentPose.deg}°`;
    }

    renderCanvas();
  }

  function renderCanvas() {
    if (!canvas || !ctx) return;
    const r = canvas.getBoundingClientRect();
    const w = r.width || 440;
    const h = r.height || 440;

    ctx.clearRect(0, 0, w, h);

    const pxPerInch = w / 144.0;
    const cx = w / 2;
    const cy = h / 2;

    // Helper: inches to canvas coords
    function toCanvas(ix, iy) {
      return {
        x: cx + ix * pxPerInch,
        y: cy - iy * pxPerInch
      };
    }

    // 1. Field Background & 2ft Tile Grid (6x6 tiles, 24" each)
    ctx.fillStyle = "#161b22";
    ctx.fillRect(0, 0, w, h);

    ctx.strokeStyle = "#21262d";
    ctx.lineWidth = 1;
    for (let i = -72; i <= 72; i += 24) {
      const p1 = toCanvas(i, -72);
      const p2 = toCanvas(i, 72);
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();

      const p3 = toCanvas(-72, i);
      const p4 = toCanvas(72, i);
      ctx.beginPath();
      ctx.moveTo(p3.x, p3.y);
      ctx.lineTo(p4.x, p4.y);
      ctx.stroke();
    }

    // 2. Field Center Axes
    ctx.strokeStyle = "rgba(139, 148, 158, 0.25)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(cx, 0); ctx.lineTo(cx, h);
    ctx.moveTo(0, cy); ctx.lineTo(w, cy);
    ctx.stroke();

    // 3. Submersible Structure in Center (approx 48" x 24")
    const subTopLeft = toCanvas(-24, 12);
    const subW = 48 * pxPerInch;
    const subH = 24 * pxPerInch;
    ctx.fillStyle = "rgba(56, 139, 253, 0.08)";
    ctx.fillRect(subTopLeft.x, subTopLeft.y, subW, subH);
    ctx.strokeStyle = "rgba(56, 139, 253, 0.4)";
    ctx.lineWidth = 1.5;
    ctx.strokeRect(subTopLeft.x, subTopLeft.y, subW, subH);

    // Label submersible
    ctx.fillStyle = "rgba(88, 166, 255, 0.5)";
    ctx.font = "10px -apple-system, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("SUBMERSIBLE", cx, cy + 3);

    // 4. Alliance Zones (Blue Left -72 to -48, Red Right 48 to 72)
    // Blue Observation Zone (Bottom-Left)
    const bObs = toCanvas(-72, -48);
    ctx.fillStyle = "rgba(56, 139, 253, 0.12)";
    ctx.fillRect(bObs.x, bObs.y, 24 * pxPerInch, 24 * pxPerInch);
    ctx.strokeStyle = "rgba(56, 139, 253, 0.5)";
    ctx.strokeRect(bObs.x, bObs.y, 24 * pxPerInch, 24 * pxPerInch);

    // Red Observation Zone (Bottom-Right)
    const rObs = toCanvas(48, -48);
    ctx.fillStyle = "rgba(248, 81, 73, 0.12)";
    ctx.fillRect(rObs.x, rObs.y, 24 * pxPerInch, 24 * pxPerInch);
    ctx.strokeStyle = "rgba(248, 81, 73, 0.5)";
    ctx.strokeRect(rObs.x, rObs.y, 24 * pxPerInch, 24 * pxPerInch);

    // 5. Outer Perimeter Wall
    ctx.strokeStyle = "#388bfd";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, w - 2, h - 2);

    // 6. Draw Robot at currentPose
    const robCenter = toCanvas(currentPose.x, currentPose.y);
    const robSize = 18.0 * pxPerInch; // 18x18 inches
    const rad = (-currentPose.deg * Math.PI) / 180.0; // Invert for canvas coordinate system

    ctx.save();
    ctx.translate(robCenter.x, robCenter.y);
    ctx.rotate(rad);

    // Robot body shadow & glow
    ctx.shadowColor = "rgba(0, 0, 0, 0.6)";
    ctx.shadowBlur = 8;

    // Robot body (Chassis)
    ctx.fillStyle = "rgba(35, 134, 54, 0.45)"; // Dark emerald
    ctx.strokeStyle = "#3fb950"; // Bright green outline
    ctx.lineWidth = 2;
    ctx.fillRect(-robSize / 2, -robSize / 2, robSize, robSize);
    ctx.strokeRect(-robSize / 2, -robSize / 2, robSize, robSize);

    // Wheels at 4 corners
    const wheelW = 4 * pxPerInch;
    const wheelH = 2 * pxPerInch;
    ctx.fillStyle = "#8b949e";
    // Front Left & Right (top in local space)
    ctx.fillRect(-robSize / 2 - wheelH, -robSize / 2 + 2, wheelH, wheelW);
    ctx.fillRect(robSize / 2, -robSize / 2 + 2, wheelH, wheelW);
    // Rear Left & Right (bottom in local space)
    ctx.fillRect(-robSize / 2 - wheelH, robSize / 2 - wheelW - 2, wheelH, wheelW);
    ctx.fillRect(robSize / 2, robSize / 2 - wheelW - 2, wheelH, wheelW);

    // Forward Direction Arrow (Pointing towards local -Y, which is forward)
    ctx.shadowBlur = 0;
    ctx.fillStyle = "#ffffff";
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, -robSize / 2 - 8); // Tip
    ctx.lineTo(-6, -robSize / 2 + 4);
    ctx.lineTo(-2, -robSize / 2 + 4);
    ctx.lineTo(-2, robSize / 4);
    ctx.lineTo(2, robSize / 4);
    ctx.lineTo(2, -robSize / 2 + 4);
    ctx.lineTo(6, -robSize / 2 + 4);
    ctx.closePath();
    ctx.fill();

    // Center pivot dot
    ctx.fillStyle = "#58a6ff";
    ctx.beginPath();
    ctx.arc(0, 0, 3, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  window.applyPreset = function (x, y, deg) {
    updatePose(x, y, deg);
  };

  window.setAngle = function (deg) {
    updatePose(currentPose.x, currentPose.y, deg);
  };

  window.toggleHelpInstructions = function () {
    const el = document.getElementById("spp-help-content");
    if (el) {
      el.style.display = el.style.display === "none" ? "block" : "none";
    }
  };

  window.checkServerHealth = async function (alertUser) {
    const badge = document.getElementById("spp-api-badge");
    const errMsg = document.getElementById("spp-error-msg");
    const url = (document.getElementById("spp-server-url") ? document.getElementById("spp-server-url").value : serverUrl).trim().replace(/\/+$/, "");

    if (badge) {
      badge.className = "spp-status-badge spp-status-checking";
      badge.textContent = "Checking...";
    }
    if (errMsg) errMsg.style.display = "none";

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);
      const resp = await fetch(`${url}/`, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (resp.ok) {
        const data = await resp.json();
        if (badge) {
          badge.className = "spp-status-badge spp-status-online";
          badge.textContent = "🟢 Online (HF Free)";
        }
        if (alertUser) showToast("Simulation backend is online and ready!");
        return true;
      } else {
        throw new Error(`HTTP ${resp.status}`);
      }
    } catch (e) {
      if (badge) {
        badge.className = "spp-status-badge spp-status-offline";
        badge.textContent = "🔴 Offline / Need Setup";
      }
      if (errMsg) {
        errMsg.style.display = "block";
        errMsg.innerHTML = `Backend at <code>${url}</code> is unreachable (${e.message}). Ensure your Hugging Face Space is public and running, or run <code>python3 server/server.py</code> locally.`;
      }
      return false;
    }
  };

  function readActiveRecordingJson() {
    if (!window.globalUnityInstance || !window.globalUnityInstance.Module) return null;
    const module = window.globalUnityInstance.Module;
    let vPath = window.currentActiveVirtualPath;
    if (!vPath) {
      const recSelect = document.getElementById("recording-select");
      if (recSelect && recSelect.value) {
        vPath = recSelect.value.startsWith("/") ? recSelect.value : "/recordings/" + recSelect.value;
      }
    }
    if (!vPath) return null;
    try {
      let bytes = window.virtualFileCache ? window.virtualFileCache.get(vPath) : null;
      if (!bytes && module.FS && typeof module.FS.readFile === "function") {
        bytes = module.FS.readFile(vPath);
      }
      if (!bytes) {
        throw new Error("Cached recording bytes unavailable for " + vPath);
      }
      const text = new TextDecoder().decode(bytes);
      return { vPath, data: JSON.parse(text) };
    } catch (e) {
      console.warn("[start-pose-picker] Could not read active recording:", vPath, e);
      return null;
    }
  }

  window.resetToDefaultPedroPose = function () {
    const active = readActiveRecordingJson();
    if (active && active.data && active.data.header && active.data.header.startPose) {
      const sp = active.data.header.startPose;
      const x = Number(sp.xInches || 0);
      const y = Number(sp.yInches || 0);
      const deg = Math.round(((Number(sp.headingRadians || 0) * 180) / Math.PI) * 10) / 10;
      if (typeof window.applyPresetPose === "function") {
        window.applyPresetPose(x, y, deg);
      } else {
        currentPose = { x, y, deg };
      }
      showToast(`Reset to Pedro / Default Start Pose (${x.toFixed(1)}", ${y.toFixed(1)}", ${deg}°)`);
    } else {
      if (typeof window.applyPresetPose === "function") {
        window.applyPresetPose(0, 0, 0);
      }
    }
  };

  window.runCustomSimulation = async function () {
    const btn = document.getElementById("spp-simulate-btn");
    const btnText = document.getElementById("spp-btn-text");
    const btnIcon = document.getElementById("spp-btn-icon");
    const errMsg = document.getElementById("spp-error-msg");

    if (btn) btn.disabled = true;
    if (btnIcon) btnIcon.innerHTML = `<div class="spp-spinner"></div>`;
    if (btnText) btnText.textContent = "Applying Start Pose...";
    if (errMsg) errMsg.style.display = "none";

    try {
      const active = readActiveRecordingJson();
      if (!active || !active.data || !active.data.header || !Array.isArray(active.data.loops)) {
        throw new Error("No active autonomous recording loaded yet. Wait for the 3D Arena to finish loading.");
      }

      const simData = active.data;
      const origStart = simData.header.startPose || { xInches: 0, yInches: 0, headingRadians: 0 };
      const origX = Number(origStart.xInches || 0);
      const origY = Number(origStart.yInches || 0);
      const origTheta = Number(origStart.headingRadians || 0);

      const targetX = Number(currentPose.x || 0);
      const targetY = Number(currentPose.y || 0);
      const targetTheta = (Number(currentPose.deg || 0) * Math.PI) / 180;
      const dTheta = targetTheta - origTheta;
      const cosD = Math.cos(dTheta);
      const sinD = Math.sin(dTheta);

      const transformPoint = (x, y) => {
        const relX = Number(x) - origX;
        const relY = Number(y) - origY;
        return [
          targetX + (relX * cosD - relY * sinD),
          targetY + (relX * sinD + relY * cosD)
        ];
      };

      simData.header.startPose = {
        xInches: targetX,
        yInches: targetY,
        headingRadians: targetTheta
      };

      if (Array.isArray(simData.header.pieces)) {
        for (const piece of simData.header.pieces) {
          if (piece && piece.state === "held" && piece.xInches != null && piece.yInches != null) {
            const [nx, ny] = transformPoint(piece.xInches, piece.yInches);
            piece.xInches = nx;
            piece.yInches = ny;
          }
        }
      }

      if (Array.isArray(simData.plannedPaths)) {
        for (const plan of simData.plannedPaths) {
          if (!plan || !Array.isArray(plan.points)) continue;
          plan.points = plan.points.map(pt => {
            if (Array.isArray(pt) && pt.length >= 2) {
              return transformPoint(pt[0], pt[1]);
            }
            if (pt && pt.xInches != null && pt.yInches != null) {
              const [nx, ny] = transformPoint(pt.xInches, pt.yInches);
              return { ...pt, xInches: nx, yInches: ny };
            }
            return pt;
          });
        }
      }

      for (let i = 0; i < simData.loops.length; i++) {
        const loop = simData.loops[i];
        const pose = loop?.pose;
        if (pose && pose.xInches != null && pose.yInches != null) {
          const [nx, ny] = transformPoint(pose.xInches, pose.yInches);
          pose.xInches = nx;
          pose.yInches = ny;
          if (pose.headingRadians != null) {
            pose.headingRadians = Number(pose.headingRadians) + dTheta;
          }
        }
        if (Array.isArray(loop?.pieces)) {
          for (const piece of loop.pieces) {
            if (piece && piece.state === "held" && piece.xInches != null && piece.yInches != null) {
              const [nx, ny] = transformPoint(piece.xInches, piece.yInches);
              piece.xInches = nx;
              piece.yInches = ny;
            }
          }
        }
      }

      const module = window.globalUnityInstance.Module;
      let fileName = `custom_pose_${Date.now()}.json`;
      let vPath = `/recordings/${fileName}`;
      const encodedBytes = new TextEncoder().encode(JSON.stringify(simData));

      if (typeof module.FS_createPath === "function") {
        module.FS_createPath("/", "recordings", true, true);
      }
      try {
        module.FS_createDataFile("/recordings", fileName, encodedBytes, true, true, true);
      } catch (dup) {
        fileName = `custom_pose_${Date.now()}_${Math.random().toString(36).slice(2, 6)}.json`;
        vPath = `/recordings/${fileName}`;
        module.FS_createDataFile("/recordings", fileName, encodedBytes, true, true, true);
      }
      if (window.virtualFileCache) {
        window.virtualFileCache.set(vPath, encodedBytes);
      }

      window.openRecording(vPath);
      closeStartPoseModal();
      showToast(`✓ Replaying from (${targetX}", ${targetY}", ${currentPose.deg}°)`);
    } catch (e) {
      console.error("[runCustomSimulation] Error:", e);
      if (errMsg) {
        errMsg.style.display = "block";
        errMsg.textContent = e.message;
      }
    } finally {
      if (btn) btn.disabled = false;
      if (btnIcon) btnIcon.innerHTML = `⚡`;
      if (btnText) btnText.textContent = "Replay From This Start Pose (Instant)";
    }
  };

  function showToast(msg) {
    const toast = document.getElementById("spp-toast");
    const toastText = document.getElementById("spp-toast-text");
    if (!toast) return;
    if (toastText) toastText.textContent = msg;
    toast.classList.add("active");
    setTimeout(() => {
      toast.classList.remove("active");
    }, 4500);
  }

  window.openStartPoseModal = function () {
    initModal();
    if (modal) modal.classList.add("active");

    // Sync robot badge with current team
    const teamBadge = document.getElementById("spp-robot-badge");
    const activeTeam = getActiveTeam();
    if (teamBadge) {
      teamBadge.textContent = activeTeam === "team3" ? 'Team 3 (18"×18")' : `${activeTeam.toUpperCase()} Robot`;
    }

    // Sync opmode selector
    const recSelect = document.getElementById("recording-select");
    const opSelect = document.getElementById("spp-opmode-select");
    if (recSelect && opSelect) {
      const cur = recSelect.value.replace(/^auto-/, "").replace(/\.json$/, "");
      for (let opt of opSelect.options) {
        if (opt.value === cur) {
          opSelect.value = cur;
          break;
        }
      }
    }

    setTimeout(() => {
      setupCanvas();
      const active = readActiveRecordingJson();
      if (active && active.data && active.data.header && active.data.header.startPose) {
        const sp = active.data.header.startPose;
        const x = Number(sp.xInches || 0);
        const y = Number(sp.yInches || 0);
        const deg = Math.round(((Number(sp.headingRadians || 0) * 180) / Math.PI) * 10) / 10;
        if (typeof window.applyPresetPose === "function") {
          window.applyPresetPose(x, y, deg);
        }
      }
    }, 50);
  };

  window.closeStartPoseModal = function () {
    if (modal) modal.classList.remove("active");
  };

  // Keyboard shortcut: Esc closes modal
  window.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && modal && modal.classList.contains("active")) {
      closeStartPoseModal();
    }
  });

  // Export
  window.initStartPosePicker = initModal;
})();

