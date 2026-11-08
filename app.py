#!/usr/bin/env python3
"""
Hugging Face Spaces Simulation Engine for FTC Sim App
Works on 100% Free Gradio Spaces (no credit card or Docker needed).
Exposes POST /simulate with CORS and a friendly dashboard on port 7860.
"""

import os
import sys
import json
import subprocess
import tempfile

PORT = int(os.environ.get("PORT", 7860))
REPO_ROOT = os.path.abspath(os.path.dirname(__file__))
TH_SRC = os.path.join(REPO_ROOT, "th_src")
JAR_PATH = os.path.join(REPO_ROOT, "web", "harness-viewer.jar")
AGENT_PATH = os.path.join(TH_SRC, "build", "agent", "harness-agent.jar")

def find_java():
    if os.environ.get("JAVA_HOME"):
        cand = os.path.join(os.environ["JAVA_HOME"], "bin", "java")
        if os.path.isfile(cand) and os.access(cand, os.X_OK):
            return cand
    for standard_path in [
        os.path.expanduser("~/jdk17/Contents/Home/bin/java"),
        "/Users/elliott/jdk17/Contents/Home/bin/java",
        "/usr/lib/jvm/java-17-openjdk-amd64/bin/java",
        "/usr/lib/jvm/java-17-openjdk-arm64/bin/java",
        "/usr/lib/jvm/default-java/bin/java",
        "/opt/java/openjdk/bin/java"
    ]:
        if os.path.isfile(standard_path) and os.access(standard_path, os.X_OK):
            return standard_path
    return "java"

def run_simulation(team_id, op_mode, x, y, heading_deg):
    team_prop = os.path.join(REPO_ROOT, "configs", "teams", f"{team_id}.properties")
    if os.path.isfile(team_prop) and team_id != "team3":
        import shutil
        shutil.copy(team_prop, os.path.join(TH_SRC, "harness.properties"))

    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp_out:
        tmp_json_path = tmp_out.name

    java_bin = find_java()
    cmd = [
        java_bin,
        "-cp", JAR_PATH,
        "pvi.harness.runtime.SimulateSingleRun",
        op_mode,
        tmp_json_path,
        str(x),
        str(y),
        str(heading_deg)
    ]
    if os.path.isfile(AGENT_PATH):
        cmd.insert(1, f"-javaagent:{AGENT_PATH}")

    print(f"[simulate] Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, cwd=TH_SRC, capture_output=True, text=True)

    if result.returncode != 0 or not os.path.isfile(tmp_json_path):
        return {
            "success": False,
            "error": "Simulation execution failed",
            "stderr": result.stderr,
            "stdout": result.stdout
        }

    try:
        with open(tmp_json_path, "r", encoding="utf-8") as f:
            sim_data = f.read()
        return {"success": True, "json": sim_data}
    finally:
        if os.path.isfile(tmp_json_path):
            os.unlink(tmp_json_path)

# Try launching with Gradio / FastAPI if installed
try:
    import gradio as gr
    from fastapi import FastAPI, Request
    from fastapi.responses import JSONResponse, Response
    from fastapi.middleware.cors import CORSMiddleware
    import uvicorn

    fastapi_app = FastAPI(title="FTC Simulation Engine")

    fastapi_app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @fastapi_app.get("/health")
    @fastapi_app.get("/api/health")
    def health_check():
        return {
            "status": "online",
            "service": "FTC Live Autonomous Simulation Engine (Free Gradio Backend)",
            "version": "1.0",
            "ready": os.path.isfile(JAR_PATH)
        }

    @fastapi_app.post("/simulate")
    async def simulate_endpoint(request: Request):
        try:
            req = await request.json()
        except Exception as e:
            return JSONResponse(status_code=400, content={"error": f"Invalid JSON payload: {e}"})

        team_id = req.get("team", "team3")
        op_mode = req.get("opMode", "Team3PedroPilotAutoOpMode")
        pose = req.get("startPose", {})
        x = float(pose.get("xInches", 0.0))
        y = float(pose.get("yInches", 0.0))
        heading_deg = float(pose.get("headingDegrees", 0.0))

        res = run_simulation(team_id, op_mode, x, y, heading_deg)
        if not res["success"]:
            return JSONResponse(status_code=500, content=res)
        return Response(content=res["json"], media_type="application/json")

    with gr.Blocks(title="FTC Live Simulation API") as demo:
        gr.Markdown("""
        # 🤖 FTC Live Autonomous Simulation Engine (Free Backend)
        This Hugging Face Space powers instant autonomous re-simulation for the **VI_Arena 3D Web App**.

        * **Status:** 🟢 Online & Ready
        * **API Endpoint:** `POST /simulate`
        * **Execution Time:** ~300ms per 30-second autonomous run
        """)

    app = gr.mount_gradio_app(fastapi_app, demo, path="/")

    if __name__ == "__main__":
        print(f"Starting Gradio/FastAPI Simulation Server on port {PORT}...")
        uvicorn.run(app, host="0.0.0.0", port=PORT)

except ImportError:
    # Fallback to standard library HTTPServer when gradio/fastapi aren't installed
    from http.server import HTTPServer, BaseHTTPRequestHandler

    class StandaloneHandler(BaseHTTPRequestHandler):
        def _send_cors(self):
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")

        def do_OPTIONS(self):
            self.send_response(204)
            self._send_cors()
            self.end_headers()

        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self._send_cors()
            self.end_headers()
            resp = {
                "status": "online",
                "service": "FTC Live Autonomous Simulation Engine (Free Gradio Backend)",
                "version": "1.0",
                "ready": os.path.isfile(JAR_PATH)
            }
            self.wfile.write(json.dumps(resp, indent=2).encode("utf-8"))

        def do_POST(self):
            if self.path != "/simulate":
                self.send_response(404)
                self._send_cors()
                self.end_headers()
                self.wfile.write(b'{"error": "Endpoint not found"}')
                return

            content_len = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_len)
            try:
                req = json.loads(body.decode("utf-8"))
            except Exception as e:
                self.send_response(400)
                self._send_cors()
                self.end_headers()
                self.wfile.write(json.dumps({"error": f"Invalid JSON payload: {e}"}).encode("utf-8"))
                return

            team_id = req.get("team", "team3")
            op_mode = req.get("opMode", "Team3PedroPilotAutoOpMode")
            pose = req.get("startPose", {})
            x = float(pose.get("xInches", 0.0))
            y = float(pose.get("yInches", 0.0))
            heading_deg = float(pose.get("headingDegrees", 0.0))

            res = run_simulation(team_id, op_mode, x, y, heading_deg)
            if not res["success"]:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self._send_cors()
                self.end_headers()
                self.wfile.write(json.dumps(res).encode("utf-8"))
                return

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self._send_cors()
            self.end_headers()
            self.wfile.write(res["json"].encode("utf-8"))

    if __name__ == "__main__":
        server = HTTPServer(("0.0.0.0", PORT), StandaloneHandler)
        print(f"Starting Standalone Simulation Server on port {PORT}...")
        server.serve_forever()
