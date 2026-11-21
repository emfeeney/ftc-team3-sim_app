/**
 * FTC BIOBUZZ 2026 Match Strategy & Competition Suite
 * Reads real format-v8 recordings (score, hives, pieces, plannedPaths, events, autoStep)
 * directly from the active branch / Emscripten virtual cache or manifest branches.
 */
(function () {
  let modal = null;
  let currentRecordingData = null;
  let currentRunMeta = { branch: "feature/team-3-setup", opMode: "AutoOpMode", label: "Auto" };
  let computedRunStats = null;

  function escapeHtml(str) {
    return String(str == null ? "" : str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function decodeRecordingFromCache(vPath) {
    if (!vPath || !window.virtualFileCache) return null;
    const bytes = window.virtualFileCache.get(vPath);
    if (!bytes) return null;
    return JSON.parse(new TextDecoder().decode(bytes));
  }

  function branchFolder(info) {
    return info && info.folder && info.folder !== "." ? info.folder + "/" : "";
  }

  function inferAlliance(data) {
    const score = data?.score || data?.loops?.[data.loops.length - 1]?.score;
    if (score?.robotAlliance === "red" || score?.robotAlliance === "blue") {
      return score.robotAlliance;
    }
    const redPts = score?.red?.total || 0;
    const bluePts = score?.blue?.total || 0;
    if (redPts !== bluePts) return redPts > bluePts ? "red" : "blue";
    const startY = data?.header?.startPose?.yInches;
    if (typeof startY === "number" && startY !== 0) {
      return startY < 0 ? "red" : "blue";
    }
    const name = (data?.header?.opMode || "").toLowerCase();
    if (name.includes("blue")) return "blue";
    if (name.includes("red")) return "red";
    return "red";
  }

  function extractRunStats(data) {
    const loops = data?.loops || [];
    const header = data?.header || {};
    const firstPose = header.startPose || loops[0]?.pose || { xInches: 0, yInches: 0, headingRadians: 0 };
    const lastLoop = loops[loops.length - 1] || {};
    const lastPose = lastLoop.pose || firstPose;

    let totalDistInches = 0;
    let totalTurnDeg = 0;
    let maxSpeedInPerSec = 0;
    let maxSpeedTime = 0;
    let activeEndSeconds = 0;
    let totalDurationSeconds = 0;
    let launchedBallIds = new Set();
    let aprilTagFrames = 0;
    let hasLauncherActivity = false;

    for (let i = 0; i < loops.length; i++) {
      const l = loops[i];
      const mt = typeof l.matchTimeSeconds === "number" ? l.matchTimeSeconds : (l.sinceInitSeconds || 0);
      if (mt > totalDurationSeconds) totalDurationSeconds = mt;
      if ((l.aprilTags || []).length > 0) aprilTagFrames++;

      for (const p of l.pieces || []) {
        if (p.state === "flying" && p.id && !p.id.includes("-nectar-cell-")) {
          launchedBallIds.add(p.id);
        }
      }

      const motorsMoving = (l.motors || []).some(m => Math.abs(m.power || 0) > 0.02);
      if (motorsMoving && mt > activeEndSeconds) activeEndSeconds = mt;

      if (i > 0 && loops[i - 1].pose && l.pose) {
        const p0 = loops[i - 1].pose;
        const p1 = l.pose;
        const dx = (p1.xInches || 0) - (p0.xInches || 0);
        const dy = (p1.yInches || 0) - (p0.yInches || 0);
        const step = Math.hypot(dx, dy);
        totalDistInches += step;

        const dh = Math.atan2(
          Math.sin((p1.headingRadians || 0) - (p0.headingRadians || 0)),
          Math.cos((p1.headingRadians || 0) - (p0.headingRadians || 0))
        );
        totalTurnDeg += Math.abs((dh * 180) / Math.PI);

        if (step > 0.02 || Math.abs(dh) > 0.002) {
          if (mt > activeEndSeconds) activeEndSeconds = mt;
        }

        const dt = (l.sinceInitSeconds || 0) - (loops[i - 1].sinceInitSeconds || 0);
        if (dt > 0.001) {
          const spd = step / dt;
          if (spd > maxSpeedInPerSec) {
            maxSpeedInPerSec = spd;
            maxSpeedTime = mt;
          }
        }
      }

      if ((l.motors || []).some(m => /outtake|launcher|pollen/i.test(m.name) && Math.abs(m.power || 0) > 0.1)) {
        hasLauncherActivity = true;
      }
    }

    const runEnd = totalDurationSeconds > 0 ? Math.min(30.0, totalDurationSeconds) : 30.0;
    const effectiveEnd = activeEndSeconds > 0 ? Math.min(30.0, activeEndSeconds) : 0.0;
    const spareBuffer = Math.max(0, 30.0 - effectiveEnd);

    const dispInches = Math.hypot(
      (lastPose.xInches || 0) - (firstPose.xInches || 0),
      (lastPose.yInches || 0) - (firstPose.yInches || 0)
    );

    const scoreObj = data.score || lastLoop.score || null;
    const alliance = inferAlliance(data);
    const allianceScore = scoreObj ? (scoreObj[alliance] || scoreObj.red || scoreObj.blue) : null;
    const points = allianceScore ? Number(allianceScore.total || 0) : 0;
    const activeItems = (allianceScore?.items || []).filter(it => (it.points || 0) > 0);
    const breakdownText = activeItems.length > 0
      ? activeItems.map(it => `${it.name} (+${it.points})`).join(" · ")
      : (points === 0 ? "No autonomous points scored" : `${points} BIOBUZZ pts`);

    const finalHives = lastLoop.hives || header.hives || [];
    const totalHiveTips = finalHives.reduce((acc, h) => acc + (h.tips || 0), 0);

    return {
      header,
      firstPose,
      lastPose,
      effectiveEnd,
      runEnd,
      spareBuffer,
      totalDistInches,
      dispInches,
      totalTurnDeg,
      maxSpeedInPerSec,
      maxSpeedFtPerSec: maxSpeedInPerSec / 12.0,
      maxSpeedTime,
      alliance,
      points,
      breakdownText,
      activeItems,
      totalHiveTips,
      launchedCount: launchedBallIds.size,
      aprilTagFrames,
      hasLauncherActivity: hasLauncherActivity || launchedBallIds.size > 0,
      plannedPathCount: (data.plannedPaths || []).length,
      events: data.events || []
    };
  }

  function buildTimelineMilestones(data, stats) {
    const loops = data?.loops || [];
    if (loops.length === 0) return [];
    const milestones = [];
    const sx = (stats.firstPose.xInches || 0).toFixed(1);
    const sy = (stats.firstPose.yInches || 0).toFixed(1);
    const sh = (((stats.firstPose.headingRadians || 0) * 180) / Math.PI).toFixed(0);
    milestones.push({
      t: 0,
      icon: "🏁",
      label: `Match Start (${stats.header.opMode || currentRunMeta.opMode}) at (${sx}", ${sy}", ${sh}°)`
    });

    let lastStep = null;
    let prevLeave = 0;
    let prevPark = 0;
    const seenTips = new Map();
    const seenShots = new Set();
    const seenFlowerPulls = new Set();
    let seenPlay = false;

    for (const l of loops) {
      const mt = typeof l.matchTimeSeconds === "number" ? l.matchTimeSeconds : 0;
      if (l.stage === "RUN" && !seenPlay) {
        seenPlay = true;
        if (l.autoStep && l.autoStep !== lastStep) {
          lastStep = l.autoStep;
          milestones.push({ t: mt, icon: "🚀", label: `Step: ${l.autoStep}` });
        }
      } else if (l.stage === "RUN" && l.autoStep && l.autoStep !== lastStep) {
        lastStep = l.autoStep;
        milestones.push({ t: mt, icon: "📍", label: `Step: ${l.autoStep}` });
      }

      for (const p of l.pieces || []) {
        if (p.state === "flying" && !p.id.includes("-nectar-cell-") && !seenShots.has(p.id)) {
          seenShots.add(p.id);
          milestones.push({ t: mt, icon: "🟡", label: `Launched ${p.id}` });
        }
        if (p.id && p.id.startsWith("pollen-flower-") && p.state === "held" && !seenFlowerPulls.has(p.id)) {
          seenFlowerPulls.add(p.id);
          milestones.push({ t: mt, icon: "🌼", label: `Extracted ${p.id} from FLOWER` });
        }
      }

      for (const h of l.hives || []) {
        const prev = seenTips.get(h.id) || 0;
        if ((h.tips || 0) > prev) {
          seenTips.set(h.id, h.tips);
          milestones.push({
            t: mt,
            icon: "⚖️",
            label: `${h.id === "red-hive" ? "Red" : "Blue"} HIVE Tip #${h.tips} triggered`
          });
        }
      }

      const alScore = l.score?.[stats.alliance] || l.score?.red || l.score?.blue;
      if (alScore?.items) {
        for (const it of alScore.items) {
          if (it.name === "LEAVE" && it.count > prevLeave) {
            prevLeave = it.count;
            milestones.push({ t: mt, icon: "✅", label: `Earned LEAVE (+${it.pointsEach} pts)` });
          } else if (it.name === "PARK" && it.count > prevPark) {
            prevPark = it.count;
            milestones.push({ t: mt, icon: "🅿️", label: `Entered Loading Zone — PARK (+${it.pointsEach} pts)` });
          }
        }
      }
    }

    if (stats.maxSpeedFtPerSec > 0.5) {
      milestones.push({
        t: stats.maxSpeedTime,
        icon: "⚡",
        label: `Peak Velocity: ${stats.maxSpeedFtPerSec.toFixed(2)} ft/s (${stats.maxSpeedInPerSec.toFixed(1)} in/s)`
      });
    }

    const loggedEvents = new Set();
    for (const ev of stats.events) {
      const mt = typeof ev.matchTimeSeconds === "number" ? ev.matchTimeSeconds : (ev.sinceInitSeconds || 0);
      const key = `${Math.floor(mt)}_${ev.text}`;
      if (!loggedEvents.has(key)) {
        loggedEvents.add(key);
        milestones.push({ t: mt, icon: "💥", label: `Collision: ${ev.text}` });
      }
    }

    const ex = (stats.lastPose.xInches || 0).toFixed(1);
    const ey = (stats.lastPose.yInches || 0).toFixed(1);
    milestones.push({
      t: stats.effectiveEnd > 0 ? stats.effectiveEnd : stats.runEnd,
      icon: "⏹️",
      label: `Auto Finish at (${ex}", ${ey}") — Final Score: ${stats.points} pts`
    });

    milestones.sort((a, b) => a.t - b.t);
    return milestones.slice(0, 28);
  }

  function initCompetitionSuite() {
    if (document.querySelector("#comp-suite-modal")) return;

    modal = document.createElement("div");
    modal.id = "comp-suite-modal";
    modal.innerHTML = `
      <div class="cs-drawer">
        <div class="cs-header">
          <div class="cs-title-group">
            <span class="cs-title">🏆 BIOBUZZ Match Strategy & Analysis Suite</span>
            <span id="cs-active-run-badge" class="cs-badge">Active: Auto</span>
          </div>
          <button class="cs-close-btn" onclick="closeCompetitionSuite()" title="Close (Esc)">✕</button>
        </div>

        <div class="cs-tabs">
          <button class="cs-tab active" data-tab="score">📊 Auto Score & Timing</button>
          <button class="cs-tab" data-tab="collision">🤝 Alliance Collision Check</button>
          <button class="cs-tab" data-tab="stress">🔋 Battery & Grip Stress</button>
          <button class="cs-tab" data-tab="scouting">📋 Pit Strategy Sheet</button>
          <button class="cs-tab" data-tab="timeline">⏱️ Match Timeline</button>
        </div>

        <div class="cs-body">
          <!-- TAB 1: Auto Score & Timing -->
          <div id="cs-tab-score" class="cs-tab-content active">
            <div class="cs-card">
              <div class="cs-card-header">
                <span>BIOBUZZ 2026 Autonomous Point & Cycle Yield</span>
                <span id="cs-score-pill" style="font-size: 13px; color: #3fb950; font-weight: 600;">-- pts</span>
              </div>
              <div class="cs-metrics-grid">
                <div class="cs-metric-box">
                  <div class="cs-metric-label">BIOBUZZ Auto Score</div>
                  <div id="cs-metric-score" class="cs-metric-value">-- pts</div>
                  <div id="cs-metric-breakdown" class="cs-metric-sub">Loading score breakdown...</div>
                </div>
                <div class="cs-metric-box">
                  <div class="cs-metric-label">Active Execution Time</div>
                  <div id="cs-metric-time" class="cs-metric-value">-- s</div>
                  <div id="cs-metric-buffer" class="cs-metric-sub">-- s buffer before bell</div>
                </div>
                <div class="cs-metric-box">
                  <div class="cs-metric-label">Total Path Distance</div>
                  <div id="cs-metric-dist" class="cs-metric-value">-- ft</div>
                  <div id="cs-metric-disp" class="cs-metric-sub">-- ft straight-line</div>
                </div>
                <div class="cs-metric-box">
                  <div class="cs-metric-label">Peak Speed Reached</div>
                  <div id="cs-metric-speed" class="cs-metric-value">-- ft/s</div>
                  <div id="cs-metric-speed-sub" class="cs-metric-sub">-- in/s</div>
                </div>
              </div>
            </div>

            <div class="cs-card">
              <div class="cs-card-header">Match Time & Cycle Buffer (30.0s Autonomous)</div>
              <div class="cs-timeline-bar">
                <div id="cs-time-progress" class="cs-timeline-progress" style="width: 0%;"></div>
              </div>
              <div style="display: flex; justify-content: space-between; font-size: 11px; color: #8b949e; font-family: monospace;">
                <span>0.0s START</span>
                <span id="cs-time-finish-label">-- s ACTIVE END</span>
                <span>30.0s AUTO BELL</span>
              </div>
            </div>
          </div>

          <!-- TAB 2: Alliance Partner Collision Check -->
          <div id="cs-tab-collision" class="cs-tab-content">
            <div class="cs-card">
              <div class="cs-card-header">
                <span>Alliance Partner Path Collision Detector</span>
                <span style="font-size: 12px; color: #8b949e;">Checks center & corner clearance between both 18x18 robots</span>
              </div>
              <div style="display: flex; gap: 12px; align-items: center; margin-bottom: 14px; flex-wrap: wrap;">
                <label style="font-size: 13px; color: #8b949e;">Select Partner Route:</label>
                <select id="cs-partner-select" style="flex: 1; min-width: 220px; background: #0d1117; border: 1px solid #30363d; color: #f0f6fc; padding: 6px 10px; border-radius: 6px; font-size: 13px;"></select>
                <button class="cs-action-btn primary" onclick="runCollisionAnalysis()">🔍 Check Collision</button>
              </div>

              <div id="cs-collision-result" class="cs-alert-box cs-alert-success">
                <span>Select a partner route across any branch and click <b>Check Collision</b> to verify field clearance.</span>
              </div>
            </div>
          </div>

          <!-- TAB 3: Battery & Grip Stress Test -->
          <div id="cs-tab-stress" class="cs-tab-content">
            <div class="cs-card">
              <div class="cs-card-header">Match Stress-Test Conditions (Field Variability)</div>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px;">
                <div>
                  <label style="font-size: 12px; color: #8b949e; display: block; margin-bottom: 6px;">Battery Voltage Condition:</label>
                  <select id="cs-stress-battery" onchange="calculateStressImpact()" style="width: 100%; background: #0d1117; border: 1px solid #30363d; color: #f0f6fc; padding: 8px 10px; border-radius: 6px; font-size: 13px;">
                    <option value="12.5" selected>🔋 Fresh Charged Battery (12.5V - Nominal)</option>
                    <option value="11.5">⚠️ Mid-Match Sag (11.5V - -8% Speed)</option>
                    <option value="10.8">🚨 Low Battery Critical (10.8V - -14% Speed)</option>
                  </select>
                </div>
                <div>
                  <label style="font-size: 12px; color: #8b949e; display: block; margin-bottom: 6px;">Competition Tile Grip Condition:</label>
                  <select id="cs-stress-grip" onchange="calculateStressImpact()" style="width: 100%; background: #0d1117; border: 1px solid #30363d; color: #f0f6fc; padding: 8px 10px; border-radius: 6px; font-size: 13px;">
                    <option value="1.0" selected>🧼 Clean Practice Tiles (100% Traction)</option>
                    <option value="0.85">Dusty / Used Tiles (85% Traction - +18% Slide)</option>
                    <option value="0.70">Worn Competition Arena (70% Traction - +42% Slide)</option>
                  </select>
                </div>
              </div>

              <div id="cs-stress-output" class="cs-alert-box cs-alert-success">
                <span>Loading run dynamics...</span>
              </div>
            </div>
          </div>

          <!-- TAB 4: Pit Strategy Sheet -->
          <div id="cs-tab-scouting" class="cs-tab-content">
            <div style="display: flex; justify-content: flex-end; margin-bottom: 10px;">
              <button class="cs-action-btn primary" onclick="printScoutingCard()">🖨️ Print / Save Strategy Card</button>
            </div>
            <div id="cs-printable-scout" class="cs-scouting-card">
              <div class="cs-scouting-header">
                <div>
                  <h2 id="cs-scout-team" style="margin: 0; font-size: 22px; color: #0969da;">FTC Robot</h2>
                  <div id="cs-scout-route" style="color: #57606a; font-size: 14px; margin-top: 4px;">Autonomous: --</div>
                </div>
                <div style="text-align: right;">
                  <span style="background: #dafbe1; color: #1a7f37; padding: 4px 10px; border-radius: 12px; font-weight: 700; font-size: 13px;">BIOBUZZ 2026</span>
                  <div style="color: #57606a; font-size: 11px; margin-top: 4px;">Official Match Strategy Sheet</div>
                </div>
              </div>

              <table class="cs-scouting-table">
                <tr><th>Metric</th><th>Autonomous Performance</th></tr>
                <tr><td><b>Starting Pose</b></td><td id="cs-scout-start">--</td></tr>
                <tr><td><b>End Pose (Auto Finish)</b></td><td id="cs-scout-end">--</td></tr>
                <tr><td><b>Active Run Time</b></td><td id="cs-scout-time">--</td></tr>
                <tr><td><b>BIOBUZZ Auto Score</b></td><td id="cs-scout-points">--</td></tr>
                <tr><td><b>Field Mechanics & Vision</b></td><td id="cs-scout-clearance">--</td></tr>
              </table>

              <div id="cs-scout-notes" style="margin-top: 14px; font-size: 12px; color: #57606a; line-height: 1.5;">
                <b>Drive Coach Notes:</b> --
              </div>
            </div>
          </div>

          <!-- TAB 5: Match Timeline -->
          <div id="cs-tab-timeline" class="cs-tab-content">
            <div class="cs-card">
              <div class="cs-card-header">Match Action Milestones & Key Events</div>
              <div id="cs-milestones-list" class="cs-milestones">
                <div class="cs-milestone-item"><span>Loading recorded milestones...</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    modal.querySelectorAll(".cs-tab").forEach(tab => {
      tab.addEventListener("click", () => {
        modal.querySelectorAll(".cs-tab").forEach(t => t.classList.remove("active"));
        modal.querySelectorAll(".cs-tab-content").forEach(c => c.classList.remove("active"));
        tab.classList.add("active");
        const target = modal.querySelector("#cs-tab-" + tab.dataset.tab);
        if (target) target.classList.add("active");
      });
    });

    modal.addEventListener("click", (e) => {
      if (e.target === modal) closeCompetitionSuite();
    });

    window.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && modal && modal.classList.contains("active")) {
        closeCompetitionSuite();
      }
    });
  }

  function populatePartnerDropdown() {
    const select = document.querySelector("#cs-partner-select");
    if (!select) return;
    const prevVal = select.value;
    select.innerHTML = "";

    const manifest = window.manifestData;
    const activeBranch = document.querySelector("#team-select")?.value;
    const activeRec = document.querySelector("#recording-select")?.value;

    if (window.customFileVirtualPaths && window.customFileVirtualPaths.size > 0) {
      const grp = document.createElement("optgroup");
      grp.label = "Imported Runs";
      window.customFileVirtualPaths.forEach((vPath, fileName) => {
        const opt = document.createElement("option");
        opt.value = "vpath::" + vPath;
        opt.textContent = `📁 ${fileName}`;
        grp.appendChild(opt);
      });
      select.appendChild(grp);
    }

    if (manifest && manifest.branches) {
      Object.entries(manifest.branches).forEach(([branchName, info]) => {
        if (!info || !info.recordings || info.recordings.length === 0) return;
        const grp = document.createElement("optgroup");
        grp.label = branchName;
        const folder = branchFolder(info);
        info.recordings.forEach(rec => {
          const opt = document.createElement("option");
          opt.value = `url::recordings/${folder}${rec.file}`;
          opt.textContent = `${rec.label || rec.file} (${branchName})`;
          grp.appendChild(opt);
        });
        select.appendChild(grp);
      });
    }

    if (select.options.length === 0) {
      const opt = document.createElement("option");
      opt.value = "url::recordings/feature__team-3-setup/auto-Team3AutonomousOpMode.json";
      opt.textContent = "Team3AutonomousOpMode (default)";
      select.appendChild(opt);
    }

    if (prevVal && Array.from(select.options).some(o => o.value === prevVal)) {
      select.value = prevVal;
    } else {
      // Pick first partner route that isn't the currently open recording
      const alt = Array.from(select.options).find(o => !activeRec || !o.value.endsWith("/" + activeRec));
      if (alt) select.value = alt.value;
    }
  }

  function openCompetitionSuite() {
    initCompetitionSuite();
    populatePartnerDropdown();
    modal.classList.add("active");
    loadActiveRecordingAnalysis();
  }

  function closeCompetitionSuite() {
    if (modal) modal.classList.remove("active");
  }

  async function loadActiveRecordingAnalysis() {
    const teamSelect = document.querySelector("#team-select");
    const recSelect = document.querySelector("#recording-select");
    const branchName = teamSelect ? teamSelect.value : "feature/team-3-setup";
    const activeFile = recSelect ? recSelect.value : "auto-Team3AutonomousOpMode.json";
    const displayBranch = branchName.startsWith("custom_") ? "Imported" : branchName;

    try {
      let data = decodeRecordingFromCache(window.currentActiveVirtualPath);
      if (!data) {
        const info = window.manifestData?.branches?.[branchName];
        const url = info ? `recordings/${branchFolder(info)}${activeFile}` : `recordings/${activeFile}`;
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status} fetching ${url}`);
        data = await res.json();
      }
      currentRecordingData = data;
      const opMode = data?.header?.opMode || activeFile.replace(/^auto-/, "").replace(/\.json$/, "");
      currentRunMeta = { branch: displayBranch, opMode, file: activeFile };

      const badge = document.querySelector("#cs-active-run-badge");
      if (badge) badge.textContent = `${opMode} · ${displayBranch}`;

      analyzeRecording(data);
    } catch (err) {
      console.warn("Could not load recording JSON for competition analysis:", err);
    }
  }

  function analyzeRecording(data) {
    if (!data || !data.loops || data.loops.length === 0) return;
    const stats = extractRunStats(data);
    computedRunStats = stats;

    const setElem = (id, val) => {
      const el = document.querySelector("#" + id);
      if (el) el.textContent = val;
    };

    const allianceTitle = stats.alliance === "blue" ? "Blue" : "Red";
    const driveFwd = stats.header?.driveModel?.forward?.value;
    const speedSub = driveFwd
      ? `${stats.maxSpeedInPerSec.toFixed(1)} in/s (Rated ${Number(driveFwd).toFixed(1)} in/s)`
      : `${stats.maxSpeedInPerSec.toFixed(1)} in/s`;

    // Tab 1: BIOBUZZ Score & Timing
    setElem("cs-metric-score", `${stats.points} pts`);
    setElem("cs-score-pill", `${allianceTitle} Alliance: ${stats.points} Pts`);
    setElem("cs-metric-breakdown", stats.breakdownText);
    setElem("cs-metric-time", `${stats.effectiveEnd.toFixed(2)} s`);
    setElem("cs-metric-buffer", `${stats.spareBuffer.toFixed(2)}s buffer before buzzer`);
    setElem("cs-metric-dist", `${(stats.totalDistInches / 12.0).toFixed(2)} ft`);
    setElem("cs-metric-disp", `${(stats.dispInches / 12.0).toFixed(2)} ft straight-line`);
    setElem("cs-metric-speed", `${stats.maxSpeedFtPerSec.toFixed(2)} ft/s`);
    setElem("cs-metric-speed-sub", speedSub);

    const progress = document.querySelector("#cs-time-progress");
    if (progress) progress.style.width = Math.min(100, (stats.effectiveEnd / 30.0) * 100) + "%";
    setElem("cs-time-finish-label", `${stats.effectiveEnd.toFixed(2)}s ACTIVE END`);

    // Tab 4: BIOBUZZ Pit Strategy Sheet
    const robotName = stats.header?.driveModel?.name || "Robot";
    const startDeg = (((stats.firstPose.headingRadians || 0) * 180) / Math.PI).toFixed(1);
    const endDeg = (((stats.lastPose.headingRadians || 0) * 180) / Math.PI).toFixed(1);
    const cams = (stats.header?.cameras || []).map(c => c.name).join(", ") || "None";

    setElem("cs-scout-team", `${robotName} · ${currentRunMeta.branch}`);
    setElem("cs-scout-route", `Autonomous: ${currentRunMeta.opMode} (${allianceTitle} Alliance)`);
    setElem("cs-scout-start", `X: ${(stats.firstPose.xInches || 0).toFixed(1)} in, Y: ${(stats.firstPose.yInches || 0).toFixed(1)} in, H: ${startDeg}°`);
    setElem("cs-scout-end", `X: ${(stats.lastPose.xInches || 0).toFixed(1)} in, Y: ${(stats.lastPose.yInches || 0).toFixed(1)} in, H: ${endDeg}°`);
    setElem("cs-scout-time", `${stats.effectiveEnd.toFixed(2)}s active (${stats.spareBuffer.toFixed(2)}s spare buffer)`);
    setElem("cs-scout-points", `${stats.points} pts — ${stats.breakdownText}`);
    setElem(
      "cs-scout-clearance",
      `HIVE tips: ${stats.totalHiveTips} | Shots launched: ${stats.launchedCount} | Vision (${cams}): ${stats.aprilTagFrames} frames | Collisions: ${stats.events.length}`
    );

    const notesEl = document.querySelector("#cs-scout-notes");
    if (notesEl) {
      const quadX = (stats.firstPose.xInches || 0) >= 0 ? "Audience (+X)" : "Far (-X)";
      const sideY = stats.alliance === "blue" ? "Blue (+Y)" : "Red (-Y)";
      notesEl.innerHTML =
        `<b>Drive Coach Notes:</b> Starts in <b>${sideY} ${quadX}</b> zone, travels ${(stats.totalDistInches / 12.0).toFixed(1)} ft` +
        (stats.plannedPathCount > 0 ? ` across ${stats.plannedPathCount} Pedro spline updates` : "") +
        `. Completes active sequence in ${stats.effectiveEnd.toFixed(2)}s.` +
        (stats.events.length > 0 ? ` Note: ${stats.events.length} field/wall contact event(s) logged.` : " Clean path with zero wall/obstacle collisions.");
    }

    // Tab 5: Dynamic Timeline
    const listEl = document.querySelector("#cs-milestones-list");
    if (listEl) {
      const milestones = buildTimelineMilestones(data, stats);
      listEl.innerHTML = milestones
        .map(m => `
          <div class="cs-milestone-item">
            <span><b>${escapeHtml(m.icon)} ${escapeHtml(m.label)}</b></span>
            <span class="cs-milestone-time">T+${m.t.toFixed(2)}s</span>
          </div>
        `)
        .join("");
    }

    calculateStressImpact();
  }

  async function runCollisionAnalysis() {
    const partnerSelect = document.querySelector("#cs-partner-select");
    const selection = partnerSelect ? partnerSelect.value : "";
    const resultBox = document.querySelector("#cs-collision-result");
    if (!resultBox || !selection) return;

    resultBox.className = "cs-alert-box";
    resultBox.innerHTML = "<span>Checking multi-robot clearance...</span>";

    try {
      let pData = null;
      if (selection.startsWith("vpath::")) {
        pData = decodeRecordingFromCache(selection.slice("vpath::".length));
      } else {
        const url = selection.startsWith("url::") ? selection.slice("url::".length) : selection;
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        pData = await res.json();
      }

      if (!currentRecordingData || !currentRecordingData.loops?.length || !pData?.loops?.length) {
        resultBox.className = "cs-alert-box cs-alert-warning";
        resultBox.innerHTML = "<span>⚠️ Active or partner recording has no loop pose data to compare.</span>";
        return;
      }

      const loops1 = currentRecordingData.loops;
      const loops2 = pData.loops;
      let j = 0;
      let minCenterDist = Infinity;
      let minTime = 0;
      let colX = 0;
      let colY = 0;

      for (let i = 0; i < loops1.length; i++) {
        const p1 = loops1[i].pose;
        if (!p1) continue;
        const t1 = typeof loops1[i].sinceInitSeconds === "number" ? loops1[i].sinceInitSeconds : i * 0.02;
        while (j + 1 < loops2.length && (loops2[j + 1].sinceInitSeconds || 0) <= t1) {
          j++;
        }
        const p2 = loops2[j].pose;
        if (!p2) continue;

        const dist = Math.hypot((p1.xInches || 0) - (p2.xInches || 0), (p1.yInches || 0) - (p2.yInches || 0));
        if (dist < minCenterDist) {
          minCenterDist = dist;
          minTime = typeof loops1[i].matchTimeSeconds === "number" ? loops1[i].matchTimeSeconds : t1;
          colX = ((p1.xInches || 0) + (p2.xInches || 0)) / 2;
          colY = ((p1.yInches || 0) + (p2.yInches || 0)) / 2;
        }
      }

      const partnerName = escapeHtml(pData.header?.opMode || "Partner Auto");
      const alignedClearance = minCenterDist - 18.0; // Two 18x18 robots side-by-side touch at 18" center-to-center

      if (minCenterDist < 19.5) {
        resultBox.className = "cs-alert-box cs-alert-danger";
        resultBox.innerHTML = `<span>🚨 <b>COLLISION DETECTED vs ${partnerName}:</b> Centers pass within <b>${minCenterDist.toFixed(1)}"</b> (${alignedClearance.toFixed(1)}" bumper gap for 18×18 chassis) at <b>T+${minTime.toFixed(2)}s</b> near (${colX.toFixed(1)}", ${colY.toFixed(1)}"). Add a delay or stagger lanes.</span>`;
      } else if (minCenterDist < 26.5) {
        resultBox.className = "cs-alert-box cs-alert-warning";
        resultBox.innerHTML = `<span>⚠️ <b>TIGHT CORNER CLEARANCE vs ${partnerName}:</b> Minimum center separation is <b>${minCenterDist.toFixed(1)}"</b> (${alignedClearance.toFixed(1)}" side gap; diagonal corners reach 25.5") at <b>T+${minTime.toFixed(2)}s</b> near (${colX.toFixed(1)}", ${colY.toFixed(1)}").</span>`;
      } else {
        resultBox.className = "cs-alert-box cs-alert-success";
        resultBox.innerHTML = `<span>✅ <b>SAFE ALLIANCE CLEARANCE vs ${partnerName}:</b> Minimum separation is <b>${minCenterDist.toFixed(1)}"</b> (${alignedClearance.toFixed(1)}" bumper clearance) at <b>T+${minTime.toFixed(2)}s</b> near (${colX.toFixed(1)}", ${colY.toFixed(1)}").</span>`;
      }
    } catch (err) {
      resultBox.className = "cs-alert-box cs-alert-warning";
      resultBox.innerHTML = `<span>⚠️ Could not compare partner recording (${escapeHtml(err.message)}).</span>`;
    }
  }

  function calculateStressImpact() {
    const battSelect = document.querySelector("#cs-stress-battery");
    const gripSelect = document.querySelector("#cs-stress-grip");
    const output = document.querySelector("#cs-stress-output");
    if (!output) return;

    const v = parseFloat(battSelect ? battSelect.value : "12.5");
    const grip = parseFloat(gripSelect ? gripSelect.value : "1.0");
    const speedRatio = v / 12.5;
    const stopDistRatio = (1.0 / grip) * speedRatio;
    const stats = computedRunStats;

    if (!stats) {
      output.className = "cs-alert-box cs-alert-success";
      output.innerHTML = "<span>Select a recording to compute voltage & grip stress impact.</span>";
      return;
    }

    const baseTime = stats.effectiveEnd > 0 ? stats.effectiveEnd : stats.runEnd;
    const projectedTime = baseTime / speedRatio;
    const speedDropPct = Math.round((1 - speedRatio) * 100);
    const slideIncreasePct = Math.round((1.0 / grip - 1) * 100);
    const overTimeRisk = projectedTime > 29.0;
    const launcherNote = stats.hasLauncherActivity
      ? (v < 11.8
          ? ` Launcher wheel RPM sags ~${speedDropPct}%, so HIVE shots may drop short unless using velocity PID / voltage compensation.`
          : " Launcher velocity PID has headroom at this voltage.")
      : "";

    if (overTimeRisk || v < 11.2 || grip < 0.75) {
      output.className = "cs-alert-box cs-alert-warning";
      output.innerHTML = `<span>⚠️ <b>Stress Warning (${v.toFixed(1)}V, ${Math.round(grip * 100)}% grip):</b> Top speed drops <b>-${speedDropPct}%</b> and braking/corner slide expands <b>+${slideIncreasePct}%</b> (stop factor ×${stopDistRatio.toFixed(2)}). Projected completion stretches from <b>${baseTime.toFixed(2)}s → ${projectedTime.toFixed(2)}s</b>.${launcherNote}</span>`;
    } else {
      output.className = "cs-alert-box cs-alert-success";
      output.innerHTML = `<span>🛡️ <b>Healthy Match Margin (${v.toFixed(1)}V, ${Math.round(grip * 100)}% grip):</b> Completion shifts from <b>${baseTime.toFixed(2)}s → ${projectedTime.toFixed(2)}s</b> (${Math.max(0, 30 - projectedTime).toFixed(2)}s buffer before buzzer), with +${slideIncreasePct}% slip variance well inside closed-loop bounds.${launcherNote}</span>`;
    }
  }

  function printScoutingCard() {
    window.print();
  }

  window.openCompetitionSuite = openCompetitionSuite;
  window.closeCompetitionSuite = closeCompetitionSuite;
  window.runCollisionAnalysis = runCollisionAnalysis;
  window.calculateStressImpact = calculateStressImpact;
  window.printScoutingCard = printScoutingCard;
})();
