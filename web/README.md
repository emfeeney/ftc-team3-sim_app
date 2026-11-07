# Team 3 FTC Simulator & Mission Control — Web Port (`web/`)

This directory contains the **Chromebook / Chrome browser web port** of the Team 3 BIOBUZZ 2026 Simulator, Replay Viewer, and Mission Control dashboard.

## Can this run on a Chromebook in the Chrome browser?

**Yes!** The architecture of this project makes it work on a Chromebook in two complementary ways:

### Mode A: 100% Client-Side in Chrome (Zero Install / GitHub Pages Ready)
Everything inside `web/` runs natively in the Chromebook's Chrome browser at 60 FPS with no plugins or installations required:
- **3D WebGL Arena & 2D Tactical View**: Renders the 144×144 in BIOBUZZ 2026 field, Red & Blue HIVEs, 4 Flowers, 16 AprilTags (`30..45`), and all 44 game pieces (Pollen & Nectar).
- **Exact Team 3 Physics Engine (`50 Hz` / `20 ms` steps)**: Direct JavaScript port of `MomentumPhysics`, `MecanumKinematics`, `BioBuzzObstacles`, `GamePieces` (ball pushing, rolling friction, wall & obstacle restitution, and pairwise ball-ball collisions), and `CameraView` (3D camera frustum AprilTag detector for Team 3's `Webcam 1` at `forward=+6"`, `up=+12"`, `640×480`).
- **Live TeleOp & Autonomous Modes**:
  - **Live TeleOp (`Team 3 Mecanum Drive`)**: Uses the browser's built-in **HTML5 Gamepad API** (`navigator.getGamepads()`) so students can plug an Xbox, PS4/PS5, or Logitech F310 controller straight into a Chromebook USB port (or pair via Bluetooth), plus full keyboard controls (`WASD` to strafe/drive, `J`/`L` or `◀`/`▶` to turn, `I`/`K` for intake/outtake, `U` for midtake, `M`/`N` for `intake-deploy` servo).
  - **Live Autonomous**: Runs Team 3's Autonomous routines directly in the browser.
- **Replay Viewer & Recording Export**:
  - Plays back any `.json` recording produced by the Java test harness (including the bundled Team 3 Autonomous runs in `web/data/recordings/`).
  - Can **record live drives in Chrome** and export a `pvi-harness-recording` `.json` file that opens in desktop `view.bat`, `VI_Arena`, or converts into a JUnit regression test via `.\gradlew.bat makeTest`.

### Mode B: Live Java `TeamCode` Execution via WebSocket Bridge
Because raw Java `.java` OpModes and the `-javaagent` bytecode rewriter (`SimTime`) require a JVM, a Chromebook can also drive the **exact unchanged Java `TeamCode`** in real time by connecting Chrome's WebSocket (`ws://<host>:7466`) to the Java harness's `BridgeServer`:
1. **GitHub Codespaces / Cloud VM / Team Laptop on LAN**: Run the Java harness + [`bridge-proxy.mjs`](bridge-proxy.mjs) on a Codespace or pit laptop, and open the web app on any Chromebook to drive it over WebSocket.
2. **Chromebook Linux (Crostini)**: If Linux development environment is enabled on the Chromebook, run `.\gradlew.bat unityBridge` and `node web/bridge-proxy.mjs` locally in the terminal and connect in Chrome at `ws://localhost:7466`.

---

## Quick Start

### 1. Run Locally (or in GitHub Codespaces)
Serve the `web/` folder over any static HTTP server:

```powershell
# Using Python (built-in on most systems / Crostini):
python -m http.server 8080 --directory web

# Or using Node npx:
npx serve web -l 8080
```

Then open **`http://localhost:8080`** in Chrome.

### 2. Host on GitHub Pages (Instant Chromebook URL)
1. On GitHub, go to **Settings → Pages** in `https://github.com/emfeeney/ftc-team3-sim_app`.
2. Set **Build and deployment** to **GitHub Actions** (or deploy from `/web` folder).
3. Anyone on a Chromebook can open the GitHub Pages URL and immediately drive or inspect replays.

### 3. Connect to the Live Java `BridgeServer` (Optional)
If you want the web app to step the Java JVM directly:

```powershell
# Terminal 1: Start the Java TCP BridgeServer (listens on 127.0.0.1:7465)
cd th_src
.\gradlew.bat unityBridge

# Terminal 2: Start the zero-dependency WebSocket-to-TCP proxy (listens on ws://localhost:7466)
node web/bridge-proxy.mjs
```

In the web app header, switch **MODE / OPMODE** to **`Java Bridge (ws://localhost:7466)`**.

---

## Controls & Shortcuts

| Input | Action |
| :--- | :--- |
| **USB / Bluetooth Gamepad** | Plug in & press any button — Left Stick: Drive/Strafe, Right Stick: Turn, RT/LT: Intake/Reverse, RB: Outtake/Midtake, A/B: Deploy/Retract Servo |
| **`W` `A` `S` `D`** | Drive forward/back & strafe left/right |
| **`J` / `L`** or **`◀` / `▶`** | Turn counter-clockwise / clockwise |
| **`I` / `K`** or **`▲` / `▼`** | Run Intake / Reverse Intake |
| **`U` / `O`** | Run Midtake + Outtake |
| **`M` / `N`** | Deploy (`1.0`) / Retract (`0.0`) `intake-deploy` servo |
| **`1` – `7`** | Switch 3D Camera Preset (Audience, Overhead, Chase, Red DS, Blue DS, Red HIVE, Blue HIVE) |
| **`Space`** | Play / Pause simulation or replay |
| **`R`** | Reset simulation to selected Start Pose / Rewind replay |
| **`[` / `]`** | Single-step backward / forward (`20 ms` per step) |
