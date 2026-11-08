# 🚀 Hugging Face Spaces 100% Free Live Simulation Backend Setup

This guide walks you through setting up a **100% free cloud simulation backend** on **Hugging Face Spaces** for the FTC Sim App. 

With this setup, you can place your robot anywhere on the field, adjust its heading angle, and press **"⚡ Simulate From This Pose"** to re-run your autonomous routine and watch the 3D replay live in ~1 second.

---

## 📌 Why Hugging Face Spaces?
* **100% Free Forever:** Hugging Face provides a **Free CPU Basic** tier with **2 vCPU** and **16 GB RAM** with unlimited usage.
* **Instant Re-simulation:** The headless Java engine (`SimulateSingleRun`) executes complete 30-second autonomous routines in **~300ms**.
* **Zero Maintenance:** Runs in an isolated Docker container with automated SSL (`https://...`).

---

## 🛠️ Step-by-Step Setup (Takes ~2 Minutes)

### Step 1: Create a Free Hugging Face Account
1. Go to [huggingface.co/join](https://huggingface.co/join) and sign up (free).
2. Verify your email.

---

### Step 2: Create a New Docker Space
1. Click your profile avatar (top right) and select **"New Space"** (or visit [huggingface.co/new-space](https://huggingface.co/new-space)).
2. Configure your Space:
   * **Space name:** e.g. `ftc-sim-api` (or any name you prefer)
   * **License:** `mit` or `openrail`
   * **Select the Space SDK:** Choose **Docker** 🐳
   * **Choose a Docker template:** Choose **Blank**
   * **Space hardware:** Keep default **CPU Basic · 2 vCPU · 16 GB RAM · Free**
   * **Privacy:** Set to **Public** (required so your Render WebGL app can send POST requests to it)
3. Click **"Create Space"**.

---

### Step 3: Push the Repository Code to Your Space
Hugging Face Spaces are standard Git repositories. You can push this repository directly to your Space!

In your local terminal (from this project directory):

```bash
# 1. Add your Hugging Face space as a git remote (replace <YOUR_USERNAME> with your HF username):
git remote add hf https://huggingface.co/spaces/<YOUR_USERNAME>/ftc-sim-api

# 2. Push to your Hugging Face Space:
git push hf main
```

*(When prompted for a password, enter your Hugging Face **Access Token** from [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens) with "Write" permissions).*

---

### Step 4: Get Your Live Direct API URL
1. On your Space page, wait ~60–90 seconds while Docker builds the image. Once built, you'll see a green **"Running"** badge.
2. In the top right corner of the Space container, click the **three dots (`...`)** and select **"Embed this Space"** or look at the Direct URL.
3. Your Direct API URL looks like:
   ```text
   https://<YOUR_USERNAME>-ftc-sim-api.hf.space
   ```
4. Test it in your browser or with curl:
   ```bash
   curl https://<YOUR_USERNAME>-ftc-sim-api.hf.space/
   ```
   You should receive:
   ```json
   {
     "status": "online",
     "service": "FTC Live Autonomous Simulation Engine",
     "version": "1.0",
     "ready": true
   }
   ```

---

### Step 5: Connect It in the VI_Arena 3D Web App
1. Open the VI_Arena 3D web application in your browser.
2. In the top toolbar, click **"🎯 Start Pose"**.
3. Under **Simulation Engine / API Endpoint**, paste your URL:
   ```text
   https://<YOUR_USERNAME>-ftc-sim-api.hf.space
   ```
4. Click **"Check Status"** — the indicator will turn green: **🟢 Online (HF Free)**.
5. The URL is saved in your browser's local storage automatically so you only need to enter it once!

---

## 🎮 How to Move the Robot and Test Autonomous

1. In the **"🎯 Start Pose"** modal:
   * **Option A (Visual Drag):** Click and drag the robot anywhere on the top-down 2D field canvas. Drag the orientation arrow to adjust its heading!
   * **Option B (Precision Sliders):** Adjust the $X$ (inches), $Y$ (inches), and Heading (degrees) sliders or type exact values.
   * **Option C (Competition Presets):** Click **Blue Audience**, **Blue Submersible**, **Red Audience**, or **Red Submersible**.
2. Select which Autonomous routine to simulate (e.g. `Team3PedroPilotAutoOpMode`).
3. Click **"⚡ Simulate From This Pose & Replay"**.
4. In ~1 second, the new simulated path is calculated by your Hugging Face Space, transferred directly into the 3D arena, and automatically begins playing!

---

## 💡 Pro-Tips & FAQ

#### 1. What happens if the Space goes to sleep?
Free Hugging Face Spaces go to sleep after 48 hours of inactivity. When you visit or send a request, Hugging Face automatically wakes it up in about 20–30 seconds. Once awake, every simulation response takes **under 1 second**.

#### 2. Can I run the server locally on my own laptop instead?
Yes! If you're testing offline or during robotics lab without Wi-Fi:
```bash
python3 server/server.py
```
Then set your API URL in the app to: `http://localhost:7860`.

#### 3. Does this cost any money?
No. The Hugging Face CPU Basic tier is completely free.
