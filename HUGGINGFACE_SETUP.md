# 🚀 100% Free Live Simulation Backend Setup (No Credit Card / No Paid Tier)

Hugging Face recently made the custom Docker SDK a paid feature. However, **Gradio Spaces remain 100% FREE forever with NO credit card or subscription needed** (2 vCPU, 16 GB RAM).

We configured this repository with `packages.txt` (which automatically installs OpenJDK 17 on Hugging Face's free Debian image) and `app.py`, allowing you to run the live simulation backend on Hugging Face completely free.

You also have the option to run the server locally on your laptop with a single command!

---

## 🌟 Option A: Free Hugging Face Space (Gradio SDK — 100% Free)

### Step 1: Create a Free Hugging Face Account
1. Go to [huggingface.co/join](https://huggingface.co/join) and create a free account (if you don't already have one).

---

### Step 2: Create a New Space
1. Click your profile avatar (top right) and select **"New Space"** (or visit [huggingface.co/new-space](https://huggingface.co/new-space)).
2. Configure your Space:
   * **Space name:** e.g. `ftc-sim-api`
   * **License:** `mit` or `openrail`
   * **Select the Space SDK:** Choose **Gradio** 🚀 *(This is the 100% free option! Do NOT choose Docker)*
   * **Space hardware:** Keep default **CPU Basic · 2 vCPU · 16 GB RAM · Free**
   * **Privacy:** Set to **Public**
3. Click **"Create Space"**.

---

### Step 3: Push the Repository to Your Space
In your local terminal (from the `ftc-team3-sim_app` directory):

```bash
# 1. Add your Hugging Face Space as a git remote (replace <YOUR_USERNAME> with your HF username):
git remote add hf https://huggingface.co/spaces/<YOUR_USERNAME>/ftc-sim-api

# 2. Push to your Space:
git push hf main
```

*(When prompted for a password, enter your Hugging Face **Access Token** with "Write" access from [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)).*

Hugging Face will:
1. Read `packages.txt` and automatically install **OpenJDK 17 JRE**.
2. Read `requirements.txt` and install FastAPI/Gradio.
3. Launch `app.py` on port 7860!

---

### Step 4: Get Your Live Direct API URL
1. Once built (~60 seconds), your Space will show a green **"Running"** badge.
2. In the top-right corner of the Space, click the **three dots (`...`)** and select **"Embed this Space"** or find your direct URL.
3. Your Direct API URL is:
   ```text
   https://<YOUR_USERNAME>-ftc-sim-api.hf.space
   ```
4. You can test it by visiting it in your browser — you will see the FTC Live Autonomous Simulation dashboard!

---

### Step 5: Connect It in the VI_Arena 3D Web App
1. Open the VI_Arena 3D web application in your browser.
2. In the top toolbar, click **"🎯 Start Pose"**.
3. Under **Simulation Backend (HF Spaces)**, paste your URL:
   ```text
   https://<YOUR_USERNAME>-ftc-sim-api.hf.space
   ```
4. Click **"Ping"** — the indicator will turn green: **🟢 Online (HF Free)**.
5. Drag your robot anywhere on the field, set the heading angle, and click **"⚡ Simulate From This Pose & Replay Live"**!

---

## 💻 Option B: Run Locally on Your Laptop (Instant & Offline)

If you don't want to use any cloud service, you can run the backend server right on your computer. It requires no configuration:

```bash
python3 app.py
```

Then in the VI_Arena web app:
1. Click **"🎯 Start Pose"**.
2. Set the URL to: `http://localhost:7860`
3. Click **"Ping"** to connect!
