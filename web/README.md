# Web Wrapper for the Original FTC Test Harness & VI_Arena (`web/`)

This directory wraps the **author's original, unmodified application code** ([`view.bat`](../view.bat) / `pvi.harness.viewer.RecordingViewer` and [`VI_Arena.bat`](../VI_Arena.bat) / `VI_Arena.exe`) so that the **exact original UI and simulation engine** can be run inside a Chrome browser on a Chromebook without rewriting any of the app code.

---

## Way 1: Run the Unmodified Java Swing Viewer Directly in Chrome (CheerpJ WebAssembly JVM)

[`web/index.html`](./index.html) uses **CheerpJ 3.0** (a WebAssembly Java 17 Virtual Machine) to execute the compiled [`harness-viewer.jar`](./harness-viewer.jar) (`pvi.harness.viewer.RecordingViewer` / `ViewerWindow`) directly inside Chrome.

### How to Run Locally or on GitHub Pages
1. From the repository root (or inside `web/`), start any static HTTP server:
   ```powershell
   npx --yes serve web -l 8080
   ```
2. Open **`http://localhost:8080`** in Chrome.
3. CheerpJ boots the WebAssembly JVM, reads [`harness.properties`](./harness.properties), [`fields/biobuzz-2026.json`](./fields/biobuzz-2026.json), and [`build/recordings/`](./build/recordings/), and opens the **exact original Java Swing `ViewerWindow`**.

### Rebuilding `harness-viewer.jar` After Updating `TeamCode` or Recordings
Whenever you change `TeamCode` or generate new test recordings in [`th_src/`](../th_src/), refresh the files in `web/` by running:
```powershell
cd th_src
.\gradlew.bat buildWebWrapper
```

---

## Way 2: Full Desktop Container Wrapper (Docker / GitHub Codespaces + noVNC)

For 100% native JVM `-javaagent` support (`harness-agent.jar` + `SimTime`), full file saving (`Save as Test`), and running [`VI_Arena.bat`](../VI_Arena.bat) (`VI_Arena.exe`) alongside the Java bridge:

1. **In GitHub Codespaces (from a Chromebook):**
   - Click **Code → Codespaces → Create codespace on main** on GitHub (using [`web/devcontainer.json`](./devcontainer.json)).
   - Port `6080` automatically opens in Chrome, streaming the live desktop window running `./gradlew viewRecording` (or `VI_Arena`).
2. **With Docker:**
   ```bash
   docker build -f web/Dockerfile -t ftc-team3-sim .
   docker run --rm -p 6080:6080 ftc-team3-sim
   ```
   Then open **`http://localhost:6080/vnc.html?autoconnect=true&resize=scale`** in Chrome.
   - To launch `VI_Arena` instead of the 2D Java Swing viewer, pass `-e APP_MODE=vi_arena` (or `-e APP_MODE=both` for both windows).
