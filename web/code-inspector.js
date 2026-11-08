/**
 * Code Inspector for FTC Robot Framework (PVI-FTC/ftc-robot-framework)
 * Connects directly to GitHub to provide live-synced access to branches and code.
 */
(function () {
  const GITHUB_REPO = "PVI-FTC/ftc-robot-framework";
  const DEFAULT_BRANCH = "feature/team-3-setup";

  const FALLBACK_BRANCHES = [
    "feature/team-3-setup",
    "master",
    "Team2-Robot-Code",
    "Team_4_Branch",
    "TeamA_TeleOp_Update",
    "TeamA-Mini1-Basic-Auto",
    "Pedro-3-Implementation",
    "HeadingControl",
    "VI_Arena-Harness",
    "codex/Vision"
  ];

  const PRESET_FILES = {
    teleop: [
      { name: "Team3TeleOp.java (Driver Controls)", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/Team3TeleOp.java" },
      { name: "RumbleDemoTeleOp.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/RumbleDemoTeleOp.java" },
      { name: "Team3TableTestTeleOp.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/testing/Team3TableTestTeleOp.java" },
      { name: "TeamATeleOp.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/TeamATeleOp.java" },
      { name: "TeamBTeleOp.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/TeamBTeleOp.java" },
      { name: "TeamCTeleOp.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/TeamCTeleOp.java" }
    ],
    autonomous: [
      { name: "Team3PedroPilotAutoOpMode.java (Pedro Pathing)", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/Team3PedroPilotAutoOpMode.java" },
      { name: "Team3AutoOpMode.java (State Machine Auto)", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/Team3AutoOpMode.java" },
      { name: "Team3AutonomousOpMode.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/Team3AutonomousOpMode.java" },
      { name: "Team3TempStrafeRight8AutoOpMode.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/Team3TempStrafeRight8AutoOpMode.java" },
      { name: "Team3WebcamTestAutoOpMode.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/Team3WebcamTestAutoOpMode.java" },
      { name: "TeamAPedroPilotAutoOpMode.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/TeamAPedroPilotAutoOpMode.java" },
      { name: "TeamAAutoOpMode.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/autonomous/TeamAAutoOpMode.java" }
    ],
    subsystems: [
      { name: "Team3Robot.java (Hardware & Subsystems)", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3Robot.java" },
      { name: "Team3PedroRobot.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3PedroRobot.java" },
      { name: "Team3IntakeDeploySubsystem.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3IntakeDeploySubsystem.java" },
      { name: "Team3OuttakeSubsystem.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3OuttakeSubsystem.java" },
      { name: "Team3MidtakeSubsystem.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3MidtakeSubsystem.java" },
      { name: "Team3PinpointHardware.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3PinpointHardware.java" },
      { name: "Team3VisionConfiguration.java", path: "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/robots/team3/Team3VisionConfiguration.java" }
    ]
  };

  let currentBranch = DEFAULT_BRANCH;
  let currentCategory = "teleop";
  let currentFilePath = PRESET_FILES.teleop[0].path;
  let dynamicAllFiles = [];
  let currentFileCode = "";

  function createInspectorDOM() {
    if (document.getElementById("code-inspector-modal")) return;

    const modal = document.createElement("div");
    modal.id = "code-inspector-modal";
    modal.innerHTML = `
      <div class="ci-drawer">
        <div class="ci-header">
          <div class="ci-title-group">
            <span class="ci-title">💻 Robot Code & Branch Inspector</span>
            <a class="ci-repo-badge" href="https://github.com/${GITHUB_REPO}" target="_blank" title="View repository on GitHub">
              📂 ${GITHUB_REPO} ↗
            </a>
          </div>
          <div class="ci-header-actions">
            <button class="ci-close-btn" id="ci-close-btn" title="Close Code Inspector (Esc)">✕</button>
          </div>
        </div>

        <div class="ci-branch-bar">
          <div class="ci-branch-selector">
            <label for="ci-branch-select">Target Branch:</label>
            <select class="ci-select" id="ci-branch-select"></select>
            <button class="ci-btn" id="ci-sync-btn" title="Re-fetch latest commits and files from GitHub">
              🔄 Sync
            </button>
          </div>
          <div class="ci-live-indicator" title="Connected to live GitHub repository">
            <span class="ci-pulse-dot"></span> Live GitHub Synced
          </div>
        </div>

        <div class="ci-commit-bar">
          <div style="display: flex; align-items: center; gap: 8px; overflow: hidden;">
            <span class="ci-commit-sha" id="ci-commit-sha">--------</span>
            <span class="ci-commit-msg" id="ci-commit-msg">Loading commit details...</span>
          </div>
          <div id="ci-commit-meta" style="flex-shrink: 0;">--</div>
        </div>

        <div class="ci-nav-bar">
          <div class="ci-tabs">
            <button class="ci-tab active" data-cat="teleop">🎮 TeleOp</button>
            <button class="ci-tab" data-cat="autonomous">🤖 Autonomous</button>
            <button class="ci-tab" data-cat="subsystems">⚙️ Subsystems</button>
            <button class="ci-tab" data-cat="all">📂 All Files</button>
          </div>
          <div class="ci-file-dropdown-wrap">
            <select class="ci-select ci-file-select" id="ci-file-select"></select>
          </div>
        </div>

        <div class="ci-file-bar">
          <div class="ci-current-file-path" id="ci-file-path">--</div>
          <div class="ci-file-tools">
            <button class="ci-btn" id="ci-copy-btn">📋 Copy Code</button>
            <a class="ci-btn" id="ci-gh-view-btn" href="#" target="_blank">↗ View on GitHub</a>
          </div>
        </div>

        <div class="ci-code-viewport">
          <div class="ci-loading-spinner" id="ci-loading" style="display: none;">
            <div class="spinner"></div>
            <div>Fetching live source code from GitHub...</div>
          </div>
          <div id="ci-error-area"></div>
          <pre class="line-numbers"><code class="language-java" id="ci-code-element"></code></pre>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    // Bind event listeners
    modal.addEventListener("click", (e) => {
      if (e.target === modal) closeCodeInspector();
    });

    document.getElementById("ci-close-btn").addEventListener("click", closeCodeInspector);

    window.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && modal.classList.contains("active")) {
        closeCodeInspector();
      }
    });

    document.getElementById("ci-branch-select").addEventListener("change", (e) => {
      currentBranch = e.target.value;
      onBranchChanged();
    });

    document.getElementById("ci-sync-btn").addEventListener("click", () => {
      onBranchChanged(true);
    });

    document.querySelectorAll(".ci-tab").forEach((tab) => {
      tab.addEventListener("click", () => {
        document.querySelectorAll(".ci-tab").forEach((t) => t.classList.remove("active"));
        tab.classList.add("active");
        currentCategory = tab.dataset.cat;
        populateFileDropdown();
        loadSelectedFile();
      });
    });

    document.getElementById("ci-file-select").addEventListener("change", (e) => {
      currentFilePath = e.target.value;
      loadSelectedFile();
    });

    document.getElementById("ci-copy-btn").addEventListener("click", () => {
      if (currentFileCode) {
        navigator.clipboard.writeText(currentFileCode).then(() => {
          const btn = document.getElementById("ci-copy-btn");
          btn.textContent = "✓ Copied!";
          setTimeout(() => { btn.textContent = "📋 Copy Code"; }, 2000);
        });
      }
    });

    // Populate branches initially
    populateBranches();
    populateFileDropdown();
  }

  async function populateBranches() {
    const select = document.getElementById("ci-branch-select");
    select.innerHTML = "";

    let branches = [...FALLBACK_BRANCHES];

    try {
      const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/branches`);
      if (res.ok) {
        const data = await res.json();
        branches = data.map((b) => b.name);
      }
    } catch (e) {
      console.warn("[CodeInspector] Using fallback branches list due to API limit:", e);
    }

    // Ensure default branch is at top or present
    if (!branches.includes(DEFAULT_BRANCH)) {
      branches.unshift(DEFAULT_BRANCH);
    }

    branches.forEach((b) => {
      const opt = document.createElement("option");
      opt.value = b;
      opt.textContent = b + (b === DEFAULT_BRANCH ? " (Team 3 Default)" : "");
      if (b === currentBranch) opt.selected = true;
      select.appendChild(opt);
    });
  }

  function populateFileDropdown() {
    const select = document.getElementById("ci-file-select");
    select.innerHTML = "";

    let fileList = [];
    if (currentCategory === "all") {
      fileList = dynamicAllFiles.length > 0 ? dynamicAllFiles : [
        ...PRESET_FILES.teleop,
        ...PRESET_FILES.autonomous,
        ...PRESET_FILES.subsystems
      ];
    } else {
      fileList = PRESET_FILES[currentCategory] || [];
    }

    fileList.forEach((f) => {
      const opt = document.createElement("option");
      opt.value = f.path;
      opt.textContent = f.name || f.path.split("/").pop();
      if (f.path === currentFilePath) opt.selected = true;
      select.appendChild(opt);
    });

    if (fileList.length > 0 && !fileList.some((f) => f.path === currentFilePath)) {
      currentFilePath = fileList[0].path;
      select.value = currentFilePath;
    }
  }

  async function onBranchChanged(forceRefresh = false) {
    fetchCommitDetails();
    if (currentCategory === "all" || forceRefresh) {
      fetchAllFilesTree();
    }
    loadSelectedFile();
  }

  async function fetchCommitDetails() {
    const shaEl = document.getElementById("ci-commit-sha");
    const msgEl = document.getElementById("ci-commit-msg");
    const metaEl = document.getElementById("ci-commit-meta");

    shaEl.textContent = "fetching...";
    msgEl.textContent = "Loading commit for " + currentBranch + "...";
    metaEl.textContent = "";

    try {
      const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/commits/${encodeURIComponent(currentBranch)}`);
      if (res.ok) {
        const data = await res.json();
        const shortSha = data.sha.slice(0, 7);
        const commitMsg = data.commit.message.split("\n")[0];
        const author = (data.commit.author && data.commit.author.name) || "Unknown";
        const dateStr = new Date(data.commit.author.date).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });

        shaEl.textContent = shortSha;
        msgEl.textContent = commitMsg;
        metaEl.textContent = `${author} · ${dateStr}`;
      } else {
        shaEl.textContent = currentBranch;
        msgEl.textContent = "Branch ready (commit details unavailable)";
      }
    } catch (e) {
      shaEl.textContent = currentBranch;
      msgEl.textContent = "Branch: " + currentBranch;
    }
  }

  async function fetchAllFilesTree() {
    try {
      const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/git/trees/${encodeURIComponent(currentBranch)}?recursive=1`);
      if (res.ok) {
        const data = await res.json();
        dynamicAllFiles = (data.tree || [])
          .filter((item) => item.type === "blob" && item.path.endsWith(".java"))
          .map((item) => ({
            name: item.path.split("/").pop() + " (" + item.path.replace("TeamCode/src/main/java/org/firstinspires/ftc/teamcode/", "") + ")",
            path: item.path
          }));

        if (currentCategory === "all") {
          populateFileDropdown();
        }
      }
    } catch (e) {
      console.warn("[CodeInspector] Failed to fetch git tree:", e);
    }
  }

  async function loadSelectedFile() {
    const loadingEl = document.getElementById("ci-loading");
    const errorEl = document.getElementById("ci-error-area");
    const codeEl = document.getElementById("ci-code-element");
    const pathEl = document.getElementById("ci-file-path");
    const ghViewBtn = document.getElementById("ci-gh-view-btn");

    pathEl.textContent = currentFilePath;
    ghViewBtn.href = `https://github.com/${GITHUB_REPO}/blob/${encodeURIComponent(currentBranch)}/${currentFilePath}`;

    loadingEl.style.display = "flex";
    errorEl.innerHTML = "";
    codeEl.textContent = "";

    try {
      // Direct raw fetch with cache busting to guarantee real-time sync with git pushes
      const rawUrl = `https://raw.githubusercontent.com/${GITHUB_REPO}/${encodeURIComponent(currentBranch)}/${currentFilePath}?_t=${Date.now()}`;
      const res = await fetch(rawUrl);

      if (!res.ok) {
        throw new Error(`File not found on branch "${currentBranch}" (HTTP ${res.status})`);
      }

      currentFileCode = await res.text();
      codeEl.textContent = currentFileCode;

      // Apply Prism syntax highlighting if loaded
      if (window.Prism && window.Prism.highlightElement) {
        Prism.highlightElement(codeEl);
      }
    } catch (err) {
      console.error("[CodeInspector] Error loading file:", err);
      errorEl.innerHTML = `
        <div class="ci-error-banner">
          <strong>⚠️ Could not load file from branch "${currentBranch}":</strong><br>
          ${err.message}<br><br>
          <em>Note:</em> If this file was moved or renamed in this branch, switch category to "All Files" to pick another file or select a different branch.
        </div>
      `;
    } finally {
      loadingEl.style.display = "none";
    }
  }

  function openCodeInspector(requestedFilePath, requestedBranch) {
    createInspectorDOM();
    const modal = document.getElementById("code-inspector-modal");
    if (requestedBranch) {
      currentBranch = requestedBranch;
      document.getElementById("ci-branch-select").value = requestedBranch;
    }
    if (requestedFilePath) {
      currentFilePath = requestedFilePath;
      // Match category
      if (PRESET_FILES.autonomous.some((f) => f.path === requestedFilePath)) {
        currentCategory = "autonomous";
      } else if (PRESET_FILES.teleop.some((f) => f.path === requestedFilePath)) {
        currentCategory = "teleop";
      } else if (PRESET_FILES.subsystems.some((f) => f.path === requestedFilePath)) {
        currentCategory = "subsystems";
      } else {
        currentCategory = "all";
      }
      document.querySelectorAll(".ci-tab").forEach((t) => {
        t.classList.toggle("active", t.dataset.cat === currentCategory);
      });
      populateFileDropdown();
    }

    modal.classList.add("active");
    onBranchChanged();
  }

  function closeCodeInspector() {
    const modal = document.getElementById("code-inspector-modal");
    if (modal) modal.classList.remove("active");
  }

  // Export to global scope
  window.openCodeInspector = openCodeInspector;
  window.closeCodeInspector = closeCodeInspector;

  // Auto-init DOM on load
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", createInspectorDOM);
  } else {
    createInspectorDOM();
  }
})();

