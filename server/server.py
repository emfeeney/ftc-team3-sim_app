#!/usr/bin/env python3
"""
Hugging Face Spaces Simulation Server for FTC Sim App
Listens on port 7860 (Hugging Face default) and executes the headless
FTC Java simulator with custom robot starting poses in ~300ms.
"""

import os
import sys
import json
import subprocess
import tempfile
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = int(os.environ.get("PORT", 7860))
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TH_SRC = os.path.join(REPO_ROOT, "th_src")
JAR_PATH = os.path.join(REPO_ROOT, "web", "harness-viewer.jar")
AGENT_PATH = os.path.join(TH_SRC, "build", "agent", "harness-agent.jar")

class SimServerHandler(BaseHTTPRequestHandler):
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
        response = {
            "status": "online",
            "service": "FTC Live Autonomous Simulation Engine",
            "version": "1.0",
            "ready": os.path.isfile(JAR_PATH)
        }
        self.wfile.write(json.dumps(response, indent=2).encode("utf-8"))

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

        # Switch to requested team properties if exists
        team_prop = os.path.join(REPO_ROOT, "configs", "teams", f"{team_id}.properties")
        if os.path.isfile(team_prop):
            import shutil
            shutil.copy(team_prop, os.path.join(TH_SRC, "harness.properties"))

        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp_out:
            tmp_json_path = tmp_out.name

        java_bin = "java"
        if os.environ.get("JAVA_HOME"):
            candidate = os.path.join(os.environ["JAVA_HOME"], "bin", "java")
            if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
                java_bin = candidate
        elif os.path.isfile("/Users/elliott/jdk17/Contents/Home/bin/java"):
            java_bin = "/Users/elliott/jdk17/Contents/Home/bin/java"

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
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self._send_cors()
            self.end_headers()
            err_resp = {
                "error": "Simulation execution failed",
                "stderr": result.stderr,
                "stdout": result.stdout
            }
            self.wfile.write(json.dumps(err_resp).encode("utf-8"))
            return

        try:
            with open(tmp_json_path, "r", encoding="utf-8") as f:
                sim_data = f.read()

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self._send_cors()
            self.end_headers()
            self.wfile.write(sim_data.encode("utf-8"))
        finally:
            if os.path.isfile(tmp_json_path):
                os.unlink(tmp_json_path)

def run():
    server_address = ("0.0.0.0", PORT)
    httpd = HTTPServer(server_address, SimServerHandler)
    print(f"================================================================")
    print(f" FTC Simulation Server running on http://0.0.0.0:{PORT}")
    print(f"================================================================")
    httpd.serve_forever()

if __name__ == "__main__":
    run()
