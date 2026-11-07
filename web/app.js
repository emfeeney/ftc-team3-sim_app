import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// ============================================================================
// TEAM 3 & BIOBUZZ 2026 CONSTANTS (from harness.properties & TeamCode)
// ============================================================================
const TEAM3_CONFIG = {
  robotName: 'Team 3',
  robotHalfSizeIn: 9.0, // 18x18 inch robot chassis
  robotHeightIn: 14.0,
  fieldHalfSizeIn: 72.0, // 144x144 inch FTC field (-72..+72)
  stepMs: 20, // 50 Hz step loop
  // Team3PedroConfiguration.java measured speeds:
  maxForwardSpeedInPerSec: 70.01563114118758,
  maxStrafeSpeedInPerSec: 52.014594764628455,
  maxTurnRateDegPerSec: 270.0,
  forwardDecelInPerSec2: 32.54832913388586,
  strafeDecelInPerSec2: 62.89693210699509,
  accelGainFactor: 4.5, // Acceleration multiplier under power
  // Team3VisionConfiguration.java Webcam 1 mount:
  webcam: {
    name: 'Webcam 1',
    forwardIn: 6.0,
    leftIn: 0.0,
    upIn: 12.0,
    widthPx: 640,
    heightPx: 480,
    horizontalFovDeg: 55.0,
    verticalFovDeg: 42.0,
    maxRangeIn: 90.0,
  },
  // Game piece physics (from GamePieces.java)
  ballDecelInPerSec2: 24.0,
  wallRestitution: 0.5,
  ballRestitution: 0.8,
  robotPushLookaheadSec: 0.05,
};

const START_SLOTS = {
  'custom':     { x: -36.0, y: -60.0, headingDeg: 90.0 },
  'red-left':   { x: -48.0, y: -63.0, headingDeg: 90.0 },
  'red-right':  { x:  48.0, y: -63.0, headingDeg: 90.0 },
  'blue-left':  { x: -48.0, y:  63.0, headingDeg: 270.0 },
  'blue-right': { x:  48.0, y:  63.0, headingDeg: 270.0 },
  'center':     { x:   0.0, y:   0.0, headingDeg: 0.0 },
};

// ============================================================================
// APPLICATION STATE
// ============================================================================
const state = {
  field: null, // Loaded from data/biobuzz-2026.json
  mode: 'live:teleop', // 'live:teleop' | 'live:auto-*' | 'replay:*' | 'bridge:ws'
  viewMode: '3d', // '3d' | '2d'
  playing: true,
  speedMultiplier: 1.0,
  showSightlines: true,
  showPip: true,
  showObstacles: false,
  showTrail: true,
  isRecording: false,
  recordedSteps: [],

  // Current simulation step state
  stepNumber: 0,
  simTimeMs: 0,
  pose: { x: -36.0, y: -60.0, headingDeg: 90.0 },
  velocity: { vx: 0.0, vy: 0.0, omegaDegPerSec: 0.0 },
  collided: false,
  motors: {
    frontLeft: 0,
    frontRight: 0,
    rearLeft: 0,
    rearRight: 0,
    intake: 0,
    outtake: 0,
    midtake: 0,
  },
  servos: {
    'intake-deploy': 0.0,
  },
  pieces: [],
  tagsInView: [],
  telemetryLines: [],
  autoStepName: 'TELEOP READY',
  trail: [], // Array of {x, y}

  // Replay data when in replay mode
  replayData: null,
  replayIndex: 0,

  // Live TeleOp stabilizer state (matches Team3StabilizedDriveController)
  headingLockDeg: 90.0,
  wasTurning: false,

  // Keyboard & Gamepad input state
  keys: new Set(),
  gamepadInput: {
    lx: 0, ly: 0, rx: 0, ry: 0,
    lt: 0, rt: 0,
    a: false, b: false, x: false, y: false,
    lb: false, rb: false,
    connected: false,
    id: 'Keyboard (WASD + Arrows/JL)',
  },

  // WebSocket Bridge
  ws: null,
  wsConnected: false,
};

// ============================================================================
// DOM ELEMENTS
// ============================================================================
const dom = {
  statusBadge: document.getElementById('status-badge'),
  modeSelect: document.getElementById('mode-select'),
  startSlotSelect: document.getElementById('start-slot-select'),
  btnOpenFile: document.getElementById('btn-open-file'),
  fileInput: document.getElementById('file-input'),
  clockReadout: document.getElementById('clock-readout'),
  stepReadout: document.getElementById('step-readout'),
  btnView3d: document.getElementById('btn-view-3d'),
  btnView2d: document.getElementById('btn-view-2d'),
  camPresetsBar: document.getElementById('cam-presets-bar'),
  btnToggleSightlines: document.getElementById('btn-toggle-sightlines'),
  btnTogglePip: document.getElementById('btn-toggle-pip'),
  btnToggleObstacles: document.getElementById('btn-toggle-obstacles'),
  btnToggleTrail: document.getElementById('btn-toggle-trail'),
  arenaContainer: document.getElementById('arena-container'),
  canvas3d: document.getElementById('canvas-3d'),
  canvas2d: document.getElementById('canvas-2d'),
  webcamPip: document.getElementById('webcam-pip'),
  canvasPip: document.getElementById('canvas-pip'),
  pipTagCount: document.getElementById('pip-tag-count'),
  gpDot: document.getElementById('gp-dot'),
  gpStatusText: document.getElementById('gp-status-text'),
  vehicleName: document.getElementById('vehicle-name'),
  valX: document.getElementById('val-x'),
  valY: document.getElementById('val-y'),
  valH: document.getElementById('val-h'),
  valSpeed: document.getElementById('val-speed'),
  valVx: document.getElementById('val-vx'),
  valVy: document.getElementById('val-vy'),
  valOmega: document.getElementById('val-omega'),
  collisionIndicator: document.getElementById('collision-indicator'),
  actuatorsList: document.getElementById('actuators-list'),
  tagsSummary: document.getElementById('tags-summary'),
  tagsTbody: document.getElementById('tags-tbody'),
  autoStepBadge: document.getElementById('auto-step-badge'),
  telemetryBox: document.getElementById('telemetry-box'),
  gpIdLabel: document.getElementById('gp-id-label'),
  stickLeft: document.getElementById('stick-left'),
  stickRight: document.getElementById('stick-right'),
  barLt: document.getElementById('bar-lt'),
  barRt: document.getElementById('bar-rt'),
  gpButtonsRow: document.getElementById('gp-buttons-row'),
  btnPlayPause: document.getElementById('btn-play-pause'),
  btnReset: document.getElementById('btn-reset'),
  btnStepBack: document.getElementById('btn-step-back'),
  btnStepFwd: document.getElementById('btn-step-fwd'),
  timelineSlider: document.getElementById('timeline-slider'),
  speedSelect: document.getElementById('speed-select'),
  btnRecord: document.getElementById('btn-record'),
  btnExport: document.getElementById('btn-export'),
};

// ============================================================================
// MATH & GEOMETRY HELPERS
// ============================================================================
function degToRad(d) { return (d * Math.PI) / 180.0; }
function radToDeg(r) { return (r * 180.0) / Math.PI; }

function wrapDeg360(d) {
  let r = d % 360.0;
  if (r < 0) r += 360.0;
  return r;
}

function wrapDegSigned(d) {
  let r = (d + 180.0) % 360.0;
  if (r < 0) r += 360.0;
  return r - 180.0;
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

function moveToward(current, target, maxChange) {
  if (target > current) return Math.min(current + maxChange, target);
  if (target < current) return Math.max(current - maxChange, target);
  return target;
}

// ============================================================================
// PHYSICS ENGINE (Exact port of MomentumPhysics, MecanumKinematics, GamePieces)
// ============================================================================
function resetLiveSimulation() {
  const slotKey = dom.startSlotSelect.value || 'custom';
  const slot = START_SLOTS[slotKey] || START_SLOTS['custom'];

  state.stepNumber = 0;
  state.simTimeMs = 0;
  state.pose = { x: slot.x, y: slot.y, headingDeg: slot.headingDeg };
  state.velocity = { vx: 0, vy: 0, omegaDegPerSec: 0 };
  state.collided = false;
  state.headingLockDeg = slot.headingDeg;
  state.wasTurning = false;
  state.trail = [{ x: slot.x, y: slot.y }];

  for (const k of Object.keys(state.motors)) state.motors[k] = 0;
  state.servos['intake-deploy'] = 0.0;

  // Reset all 44 BIOBUZZ game pieces from field definition
  if (state.field && state.field.initialPieces) {
    state.pieces = state.field.initialPieces.map((p) => ({
      type: p.type,
      x: p.x,
      y: p.y,
      z: p.z || (p.type === 'pollen' ? 1.4 : 1.8),
      vx: 0,
      vy: 0,
      radiusIn: p.radiusIn || (p.type === 'pollen' ? 1.4 : 1.8),
    }));
  } else {
    state.pieces = [];
  }

  state.tagsInView = detectAprilTags(state.pose);
  state.telemetryLines = [
    `Robot  : ${TEAM3_CONFIG.robotName}`,
    `Mode   : ${state.mode}`,
    `Pose   : (${state.pose.x.toFixed(1)}, ${state.pose.y.toFixed(1)}, ${state.pose.headingDeg.toFixed(1)}°)`,
  ];

  if (state.isRecording) {
    state.recordedSteps = [];
    recordCurrentFrame();
  }

  updateTimelineBounds();
}

function readControllerInput() {
  // Poll HTML5 Gamepad API first
  const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
  let activeGp = null;
  for (const gp of gamepads) {
    if (gp && gp.connected) {
      activeGp = gp;
      break;
    }
  }

  let lx = 0, ly = 0, rx = 0, ry = 0, lt = 0, rt = 0;
  let a = false, b = false, x = false, y = false, lb = false, rb = false;

  if (activeGp) {
    const deadzone = (v) => (Math.abs(v) < 0.08 ? 0 : v);
    lx = deadzone(activeGp.axes[0] || 0);
    ly = deadzone(activeGp.axes[1] || 0); // +down on stick, just like FTC gamepad1.left_stick_y
    rx = deadzone(activeGp.axes[2] || 0);
    ry = deadzone(activeGp.axes[3] || 0);
    lt = activeGp.buttons[6] ? activeGp.buttons[6].value : 0;
    rt = activeGp.buttons[7] ? activeGp.buttons[7].value : 0;
    a = !!(activeGp.buttons[0] && activeGp.buttons[0].pressed);
    b = !!(activeGp.buttons[1] && activeGp.buttons[1].pressed);
    x = !!(activeGp.buttons[2] && activeGp.buttons[2].pressed);
    y = !!(activeGp.buttons[3] && activeGp.buttons[3].pressed);
    lb = !!(activeGp.buttons[4] && activeGp.buttons[4].pressed);
    rb = !!(activeGp.buttons[5] && activeGp.buttons[5].pressed);
    state.gamepadInput.connected = true;
    state.gamepadInput.id = activeGp.id.slice(0, 28);
  } else {
    state.gamepadInput.connected = false;
    state.gamepadInput.id = 'Keyboard (WASD + Arrows/JL)';
  }

  // Merge keyboard controls (so Chromebook keyboard works anytime)
  const k = state.keys;
  if (k.has('KeyW')) ly -= 1.0;
  if (k.has('KeyS')) ly += 1.0;
  if (k.has('KeyA')) lx -= 1.0;
  if (k.has('KeyD')) lx += 1.0;
  if (k.has('KeyJ') || k.has('ArrowLeft')) rx -= 0.85;
  if (k.has('KeyL') || k.has('ArrowRight')) rx += 0.85;
  if (k.has('KeyI') || k.has('ArrowUp')) rt = 1.0;
  if (k.has('KeyK') || k.has('ArrowDown')) lt = 1.0;
  if (k.has('KeyU')) rb = true;
  if (k.has('KeyO')) lb = true;
  if (k.has('KeyM')) a = true;
  if (k.has('KeyN')) b = true;

  state.gamepadInput.lx = clamp(lx, -1, 1);
  state.gamepadInput.ly = clamp(ly, -1, 1);
  state.gamepadInput.rx = clamp(rx, -1, 1);
  state.gamepadInput.ry = clamp(ry, -1, 1);
  state.gamepadInput.lt = clamp(lt, 0, 1);
  state.gamepadInput.rt = clamp(rt, 0, 1);
  state.gamepadInput.a = a;
  state.gamepadInput.b = b;
  state.gamepadInput.x = x;
  state.gamepadInput.y = y;
  state.gamepadInput.lb = lb;
  state.gamepadInput.rb = rb;
}

function runTeam3TeleOpControl() {
  const gp = state.gamepadInput;
  // Team 3 Mecanum Drive (matches Team3StabilizedDriveController + Team3TeleopOpMode)
  const forward = -gp.ly; // stick up is negative Y
  const strafe = gp.lx;
  let turn = gp.rx;

  // Heading lock stabilization when driver isn't actively commanding a turn
  if (Math.abs(turn) > 0.05) {
    state.wasTurning = true;
    state.headingLockDeg = state.pose.headingDeg;
  } else {
    if (state.wasTurning) {
      state.headingLockDeg = state.pose.headingDeg;
      state.wasTurning = false;
    }
    if (Math.hypot(forward, strafe) > 0.05) {
      const headingErr = wrapDegSigned(state.headingLockDeg - state.pose.headingDeg);
      turn = clamp(-headingErr * 0.025, -0.35, 0.35);
    }
  }

  // Standard FTC mecanum mixer (turn > 0 turns clockwise, so left wheels +, right wheels -)
  let fl = forward + strafe + turn;
  let fr = forward - strafe - turn;
  let rl = forward - strafe + turn;
  let rr = forward + strafe - turn;
  const maxMag = Math.max(1.0, Math.abs(fl), Math.abs(fr), Math.abs(rl), Math.abs(rr));
  state.motors.frontLeft = fl / maxMag;
  state.motors.frontRight = fr / maxMag;
  state.motors.rearLeft = rl / maxMag;
  state.motors.rearRight = rr / maxMag;

  // Team 3 Mechanisms (intake, outtake, midtake, intake-deploy servo)
  if (gp.rt > 0.05) {
    state.motors.intake = gp.rt;
    state.motors.midtake = gp.rt * 0.85;
    state.motors.outtake = gp.rb ? 1.0 : gp.rt * 0.7;
  } else if (gp.lt > 0.05) {
    state.motors.intake = -gp.lt;
    state.motors.midtake = -gp.lt * 0.85;
    state.motors.outtake = -gp.lt * 0.7;
  } else {
    state.motors.intake = gp.rb ? 0.8 : (gp.lb ? -0.8 : 0.0);
    state.motors.midtake = gp.rb ? 0.8 : (gp.lb ? -0.8 : 0.0);
    state.motors.outtake = gp.rb ? 1.0 : 0.0;
  }

  if (gp.a) state.servos['intake-deploy'] = 1.0;
  if (gp.b) state.servos['intake-deploy'] = 0.0;

  state.autoStepName = 'TELEOP: MECANUM DRIVE';
}

function runTeam3AutonomousControl(dtSec) {
  const t = state.simTimeMs / 1000.0;
  let targetX = state.pose.x;
  let targetY = state.pose.y;
  let targetH = state.pose.headingDeg;
  let stepLabel = 'AUTO IDLE';

  if (state.mode === 'live:auto-main') {
    // Team 3 Autonomous: Drive from Red wall to Red HIVE, align to AprilTags, score Nectar, cycle to Flower
    if (t < 1.8) {
      stepLabel = '1/5 DRIVE TO RED HIVE';
      targetX = -12.0;
      targetY = -42.0;
      targetH = 90.0;
      state.servos['intake-deploy'] = 1.0;
      state.motors.intake = 0.6;
    } else if (t < 3.6) {
      stepLabel = '2/5 SCORE PRELOAD AT HIVE';
      targetX = -8.0;
      targetY = -36.5;
      targetH = 90.0;
      state.motors.midtake = 0.9;
      state.motors.outtake = 1.0;
    } else if (t < 6.0) {
      stepLabel = '3/5 COLLECT AT RED FLOWER';
      targetX = -48.0;
      targetY = -28.0;
      targetH = 180.0;
      state.motors.intake = 1.0;
      state.motors.midtake = 0.7;
      state.motors.outtake = 0.0;
    } else if (t < 8.5) {
      stepLabel = '4/5 RETURN TO RED HIVE';
      targetX = -16.0;
      targetY = -37.0;
      targetH = 90.0;
      state.motors.intake = 0.3;
      state.motors.outtake = 1.0;
    } else {
      stepLabel = '5/5 PARK IN CLEAR ZONE';
      targetX = -48.0;
      targetY = -56.0;
      targetH = 90.0;
      state.motors.intake = 0;
      state.motors.midtake = 0;
      state.motors.outtake = 0;
    }
  } else if (state.mode === 'live:auto-pedro') {
    // Pedro Bezier multi-waypoint trajectory demo
    if (t < 2.2) {
      stepLabel = 'PEDRO PATH 1: SCORE HIVE';
      targetX = 0.0;
      targetY = -37.5;
      targetH = 90.0;
      state.servos['intake-deploy'] = 1.0;
      state.motors.outtake = 0.9;
    } else if (t < 4.8) {
      stepLabel = 'PEDRO PATH 2: SWEEP POLLEN';
      targetX = 36.0;
      targetY = -42.0;
      targetH = 30.0;
      state.motors.intake = 1.0;
      state.motors.midtake = 0.8;
      state.motors.outtake = 0.0;
    } else if (t < 7.2) {
      stepLabel = 'PEDRO PATH 3: SCORE SECOND';
      targetX = 12.0;
      targetY = -37.5;
      targetH = 90.0;
      state.motors.intake = 0.0;
      state.motors.outtake = 1.0;
    } else {
      stepLabel = 'PEDRO COMPLETE: HOLD POSE';
      targetX = 12.0;
      targetY = -46.0;
      targetH = 90.0;
      state.motors.outtake = 0.0;
    }
  } else if (state.mode === 'live:auto-strafe') {
    // Team 3 Temp Strafe Right 8 inches
    const slot = START_SLOTS[dom.startSlotSelect.value] || START_SLOTS['custom'];
    const rad = degToRad(slot.headingDeg - 90.0);
    targetX = slot.x + 8.0 * Math.cos(rad);
    targetY = slot.y + 8.0 * Math.sin(rad);
    targetH = slot.headingDeg;
    stepLabel = t < 1.2 ? 'STRAFING RIGHT 8.0 IN' : 'HOLDING TARGET POSE';
  }

  state.autoStepName = stepLabel;

  // Closed-loop P-controller in robot frame to drive toward (targetX, targetY, targetH)
  const dx = targetX - state.pose.x;
  const dy = targetY - state.pose.y;
  const headingRad = degToRad(state.pose.headingDeg);
  const cos = Math.cos(headingRad);
  const sin = Math.sin(headingRad);

  const errForward = dx * cos + dy * sin;
  const errRight = dx * sin - dy * cos;
  const errHeading = wrapDegSigned(targetH - state.pose.headingDeg);

  const cmdForward = clamp(errForward * 0.085, -0.85, 0.85);
  const cmdRight = clamp(errRight * 0.095, -0.85, 0.85);
  const cmdTurnCW = clamp(-errHeading * 0.03, -0.65, 0.65);

  let fl = cmdForward + cmdRight + cmdTurnCW;
  let fr = cmdForward - cmdRight - cmdTurnCW;
  let rl = cmdForward - cmdRight + cmdTurnCW;
  let rr = cmdForward + cmdRight - cmdTurnCW;
  const maxMag = Math.max(1.0, Math.abs(fl), Math.abs(fr), Math.abs(rl), Math.abs(rr));
  state.motors.frontLeft = fl / maxMag;
  state.motors.frontRight = fr / maxMag;
  state.motors.rearLeft = rl / maxMag;
  state.motors.rearRight = rr / maxMag;
}

function stepLivePhysics(dtSec) {
  // 1. MecanumKinematics (exact formulas from MecanumKinematics.java)
  const fl = state.motors.frontLeft;
  const fr = state.motors.frontRight;
  const rl = state.motors.rearLeft;
  const rr = state.motors.rearRight;

  const forwardFraction = (fl + fr + rl + rr) / 4.0;
  const strafeRightFraction = (fl - fr - rl + rr) / 4.0;
  const turnCcwFraction = (-fl + fr - rl + rr) / 4.0;

  const targetForward = forwardFraction * TEAM3_CONFIG.maxForwardSpeedInPerSec;
  const targetRight = strafeRightFraction * TEAM3_CONFIG.maxStrafeSpeedInPerSec;
  const targetOmega = turnCcwFraction * TEAM3_CONFIG.maxTurnRateDegPerSec;

  // 2. MomentumPhysics (exact velocity slew + world-frame integration from MomentumPhysics.java)
  const oldHeadingRad = degToRad(state.pose.headingDeg);
  const oldCos = Math.cos(oldHeadingRad);
  const oldSin = Math.sin(oldHeadingRad);

  const currentForward = state.velocity.vx * oldCos + state.velocity.vy * oldSin;
  const currentRight = state.velocity.vx * oldSin - state.velocity.vy * oldCos;

  const fwdAccel = Math.abs(targetForward) > Math.abs(currentForward)
    ? TEAM3_CONFIG.forwardDecelInPerSec2 * TEAM3_CONFIG.accelGainFactor
    : TEAM3_CONFIG.forwardDecelInPerSec2;
  const strAccel = Math.abs(targetRight) > Math.abs(currentRight)
    ? TEAM3_CONFIG.strafeDecelInPerSec2 * TEAM3_CONFIG.accelGainFactor
    : TEAM3_CONFIG.strafeDecelInPerSec2;

  const newForward = moveToward(currentForward, targetForward, fwdAccel * dtSec);
  const newRight = moveToward(currentRight, targetRight, strAccel * dtSec);
  const newOmega = targetOmega;

  const newHeadingDeg = wrapDeg360(state.pose.headingDeg + newOmega * dtSec);
  const newHeadingRad = degToRad(newHeadingDeg);
  const newCos = Math.cos(newHeadingRad);
  const newSin = Math.sin(newHeadingRad);

  let vx = newForward * newCos + newRight * newSin;
  let vy = newForward * newSin - newRight * newCos;

  const prevX = state.pose.x;
  const prevY = state.pose.y;
  let nextX = prevX + vx * dtSec;
  let nextY = prevY + vy * dtSec;

  // 3. Field wall & BIOBUZZ obstacle clamping (BioBuzzObstacles.java)
  const maxCoord = TEAM3_CONFIG.fieldHalfSizeIn - TEAM3_CONFIG.robotHalfSizeIn; // 63.0 in
  let hitObstacle = false;

  if (nextX < -maxCoord) { nextX = -maxCoord; vx = 0; hitObstacle = true; }
  if (nextX >  maxCoord) { nextX =  maxCoord; vx = 0; hitObstacle = true; }
  if (nextY < -maxCoord) { nextY = -maxCoord; vy = 0; hitObstacle = true; }
  if (nextY >  maxCoord) { nextY =  maxCoord; vy = 0; hitObstacle = true; }

  const rHalf = TEAM3_CONFIG.robotHalfSizeIn;
  if (state.field) {
    const robotObs = [
      ...(state.field.hives || []),
      ...(state.field.flowers || []),
    ];
    for (const box of robotObs) {
      const expandMinX = box.minX - rHalf;
      const expandMaxX = box.maxX + rHalf;
      const expandMinY = box.minY - rHalf;
      const expandMaxY = box.maxY + rHalf;

      if (nextX > expandMinX && nextX < expandMaxX && nextY > expandMinY && nextY < expandMaxY) {
        hitObstacle = true;
        // Slide along whichever axis resolves penetration with smallest push
        const pushLeft = Math.abs(nextX - expandMinX);
        const pushRight = Math.abs(expandMaxX - nextX);
        const pushDown = Math.abs(nextY - expandMinY);
        const pushUp = Math.abs(expandMaxY - nextY);
        const minPush = Math.min(pushLeft, pushRight, pushDown, pushUp);
        if (minPush === pushLeft) { nextX = expandMinX; vx = 0; }
        else if (minPush === pushRight) { nextX = expandMaxX; vx = 0; }
        else if (minPush === pushDown) { nextY = expandMinY; vy = 0; }
        else { nextY = expandMaxY; vy = 0; }
      }
    }
  }

  state.pose.x = nextX;
  state.pose.y = nextY;
  state.pose.headingDeg = newHeadingDeg;
  state.velocity.vx = vx;
  state.velocity.vy = vy;
  state.velocity.omegaDegPerSec = newOmega;
  state.collided = hitObstacle;

  // 4. Game pieces physics (GamePieces.java: robot pushing, piece obstacles, ball-ball collisions)
  stepGamePieces(dtSec);

  // 5. AprilTag detection (CameraView.java)
  state.tagsInView = detectAprilTags(state.pose);

  // 6. Trail & Telemetry
  state.stepNumber += 1;
  state.simTimeMs += TEAM3_CONFIG.stepMs;

  const lastTrail = state.trail[state.trail.length - 1];
  if (!lastTrail || Math.hypot(state.pose.x - lastTrail.x, state.pose.y - lastTrail.y) > 0.75) {
    state.trail.push({ x: state.pose.x, y: state.pose.y });
    if (state.trail.length > 600) state.trail.shift();
  }

  state.telemetryLines = [
    `Status : Running (${state.autoStepName})`,
    `Time   : ${(state.simTimeMs / 1000).toFixed(2)} s (Step ${state.stepNumber})`,
    `Pose   : X=${state.pose.x.toFixed(1)}" Y=${state.pose.y.toFixed(1)}" H=${state.pose.headingDeg.toFixed(1)}°`,
    `Drive  : FL=${state.motors.frontLeft.toFixed(2)} FR=${state.motors.frontRight.toFixed(2)} RL=${state.motors.rearLeft.toFixed(2)} RR=${state.motors.rearRight.toFixed(2)}`,
    `Mech   : In=${state.motors.intake.toFixed(2)} Mid=${state.motors.midtake.toFixed(2)} Out=${state.motors.outtake.toFixed(2)} Dep=${state.servos['intake-deploy'].toFixed(2)}`,
    `Vision : ${state.tagsInView.length} AprilTag(s) visible`,
  ];

  if (state.isRecording) {
    recordCurrentFrame();
  }
}

function stepGamePieces(dtSec) {
  const pieces = state.pieces;
  if (!pieces || pieces.length === 0) return;

  const rHalf = TEAM3_CONFIG.robotHalfSizeIn;
  const rx = state.pose.x;
  const ry = state.pose.y;
  const rHeadingRad = degToRad(state.pose.headingDeg);
  const rCos = Math.cos(rHeadingRad);
  const rSin = Math.sin(rHeadingRad);

  // Integrate ball motion & rolling friction
  for (const b of pieces) {
    b.x += b.vx * dtSec;
    b.y += b.vy * dtSec;
    const speed = Math.hypot(b.vx, b.vy);
    if (speed > 0) {
      const nextSpeed = Math.max(0, speed - TEAM3_CONFIG.ballDecelInPerSec2 * dtSec);
      const scale = nextSpeed / speed;
      b.vx *= scale;
      b.vy *= scale;
    }

    // Oriented robot square vs ball collision
    const dx = b.x - rx;
    const dy = b.y - ry;
    const localX = dx * rCos + dy * rSin;
    const localY = -dx * rSin + dy * rCos;
    const clampedX = clamp(localX, -rHalf, rHalf);
    const clampedY = clamp(localY, -rHalf, rHalf);
    const diffX = localX - clampedX;
    const diffY = localY - clampedY;
    const distSq = diffX * diffX + diffY * diffY;

    if (distSq < b.radiusIn * b.radiusIn) {
      let nLocalX = 0, nLocalY = 0;
      if (distSq > 1e-6) {
        const dist = Math.sqrt(distSq);
        nLocalX = diffX / dist;
        nLocalY = diffY / dist;
      } else {
        nLocalX = 1;
      }
      const pushDist = b.radiusIn - Math.sqrt(distSq) + 0.05;
      const newLocalX = localX + nLocalX * pushDist;
      const newLocalY = localY + nLocalY * pushDist;

      b.x = rx + newLocalX * rCos - newLocalY * rSin;
      b.y = ry + newLocalX * rSin + newLocalY * rCos;

      const nWorldX = nLocalX * rCos - nLocalY * rSin;
      const nWorldY = nLocalX * rSin + nLocalY * rCos;
      const robotAlongNormal = Math.max(0, state.velocity.vx * nWorldX + state.velocity.vy * nWorldY);
      b.vx = state.velocity.vx + nWorldX * (robotAlongNormal * 0.4 + 6.0);
      b.vy = state.velocity.vy + nWorldY * (robotAlongNormal * 0.4 + 6.0);
    }

    // Field perimeter bounce
    const wallLim = TEAM3_CONFIG.fieldHalfSizeIn - b.radiusIn;
    if (b.x < -wallLim) { b.x = -wallLim; b.vx = Math.abs(b.vx) * TEAM3_CONFIG.wallRestitution; }
    if (b.x >  wallLim) { b.x =  wallLim; b.vx = -Math.abs(b.vx) * TEAM3_CONFIG.wallRestitution; }
    if (b.y < -wallLim) { b.y = -wallLim; b.vy = Math.abs(b.vy) * TEAM3_CONFIG.wallRestitution; }
    if (b.y >  wallLim) { b.y =  wallLim; b.vy = -Math.abs(b.vy) * TEAM3_CONFIG.wallRestitution; }

    // Piece obstacle bounce (HIVE lips/feet, Flower brackets)
    if (state.field && state.field.pieceObstacles) {
      for (const obs of state.field.pieceObstacles) {
        const cx = clamp(b.x, obs.minX, obs.maxX);
        const cy = clamp(b.y, obs.minY, obs.maxY);
        const odx = b.x - cx;
        const ody = b.y - cy;
        const odSq = odx * odx + ody * ody;
        if (odSq < b.radiusIn * b.radiusIn && odSq > 1e-6) {
          const od = Math.sqrt(odSq);
          const nx = odx / od;
          const ny = ody / od;
          b.x = cx + nx * b.radiusIn;
          b.y = cy + ny * b.radiusIn;
          const vn = b.vx * nx + b.vy * ny;
          if (vn < 0) {
            b.vx -= (1 + TEAM3_CONFIG.wallRestitution) * vn * nx;
            b.vy -= (1 + TEAM3_CONFIG.wallRestitution) * vn * ny;
          }
        }
      }
    }
  }

  // Pairwise ball-ball elastic collisions
  for (let i = 0; i < pieces.length; i++) {
    for (let j = i + 1; j < pieces.length; j++) {
      const a = pieces[i];
      const b = pieces[j];
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const minDist = a.radiusIn + b.radiusIn;
      const dSq = dx * dx + dy * dy;
      if (dSq < minDist * minDist && dSq > 1e-6) {
        const d = Math.sqrt(dSq);
        const nx = dx / d;
        const ny = dy / d;
        const overlap = (minDist - d) * 0.5;
        a.x -= nx * overlap;
        a.y -= ny * overlap;
        b.x += nx * overlap;
        b.y += ny * overlap;

        const rvx = b.vx - a.vx;
        const rvy = b.vy - a.vy;
        const vn = rvx * nx + rvy * ny;
        if (vn < 0) {
          const impulse = -(1 + TEAM3_CONFIG.ballRestitution) * vn * 0.5;
          a.vx -= impulse * nx;
          a.vy -= impulse * ny;
          b.vx += impulse * nx;
          b.vy += impulse * ny;
        }
      }
    }
  }
}

function detectAprilTags(pose) {
  if (!state.field || !state.field.aprilTags) return [];
  const camCfg = TEAM3_CONFIG.webcam;
  const hRad = degToRad(pose.headingDeg);
  const cos = Math.cos(hRad);
  const sin = Math.sin(hRad);

  // Camera position in world inches
  const camX = pose.x + camCfg.forwardIn * cos - camCfg.leftIn * sin;
  const camY = pose.y + camCfg.forwardIn * sin + camCfg.leftIn * cos;
  const camZ = camCfg.upIn;
  const camHeadingDeg = pose.headingDeg;

  const visible = [];
  for (const tag of state.field.aprilTags) {
    const dx = tag.x - camX;
    const dy = tag.y - camY;
    const dz = (tag.z || 10.5) - camZ;
    const groundDist = Math.hypot(dx, dy);
    const rangeIn = Math.hypot(groundDist, dz);
    if (rangeIn < 2.0 || rangeIn > camCfg.maxRangeIn) continue;

    const angleToTagDeg = radToDeg(Math.atan2(dy, dx));
    // FTC convention: bearing > 0 is to the left of camera forward
    const bearingDeg = wrapDegSigned(angleToTagDeg - camHeadingDeg);
    if (Math.abs(bearingDeg) > camCfg.horizontalFovDeg * 0.5) continue;

    const elevationDeg = radToDeg(Math.atan2(dz, groundDist));
    if (Math.abs(elevationDeg) > camCfg.verticalFovDeg * 0.5) continue;

    // Tag face check: camera must be looking at the front face of the tag
    const tagNormalDeg = tag.yawDeg || 0;
    const viewVsNormal = Math.abs(wrapDegSigned(angleToTagDeg + 180.0 - tagNormalDeg));
    if (viewVsNormal > 78.0) continue;

    const yawDeg = wrapDegSigned(tagNormalDeg - (camHeadingDeg + 180.0));

    visible.push({
      id: tag.id,
      name: tag.name || `Tag ${tag.id}`,
      x: tag.x,
      y: tag.y,
      z: tag.z || 10.5,
      rangeIn,
      bearingDeg,
      elevationDeg,
      yawDeg,
    });
  }

  visible.sort((a, b) => a.id - b.id);
  return visible;
}

// ============================================================================
// RECORDING & EXPORT (Compatible with pvi-harness-recording v1 JSON format)
// ============================================================================
function recordCurrentFrame() {
  state.recordedSteps.push({
    stepNumber: state.stepNumber,
    simTimeMs: state.simTimeMs,
    pose: {
      x: Number(state.pose.x.toFixed(3)),
      y: Number(state.pose.y.toFixed(3)),
      headingDeg: Number(state.pose.headingDeg.toFixed(2)),
    },
    velocity: {
      vx: Number(state.velocity.vx.toFixed(2)),
      vy: Number(state.velocity.vy.toFixed(2)),
      omegaDegPerSec: Number(state.velocity.omegaDegPerSec.toFixed(2)),
    },
    motors: { ...state.motors },
    servos: { ...state.servos },
    collided: state.collided,
    autoStep: state.autoStepName,
    telemetry: [...state.telemetryLines],
    tagsInView: state.tagsInView.map((t) => t.id),
    pieces: state.pieces.map((p) => ({
      type: p.type,
      x: Number(p.x.toFixed(2)),
      y: Number(p.y.toFixed(2)),
      z: Number((p.z || 1.5).toFixed(2)),
      vx: Number(p.vx.toFixed(2)),
      vy: Number(p.vy.toFixed(2)),
      radiusIn: p.radiusIn,
    })),
  });
}

function exportRecordingJson() {
  const stepsToExport = state.mode.startsWith('replay:') && state.replayData
    ? state.replayData.steps
    : (state.recordedSteps.length > 0 ? state.recordedSteps : []);

  if (stepsToExport.length === 0) {
    recordCurrentFrame();
  }

  const payload = {
    format: 'pvi-harness-recording',
    version: 1,
    scenario: state.mode,
    opMode: state.autoStepName || 'Team 3 Web Session',
    opModeType: state.mode.includes('auto') ? 'AUTONOMOUS' : 'TELEOP',
    robot: TEAM3_CONFIG.robotName,
    stepMs: TEAM3_CONFIG.stepMs,
    robotSizeIn: TEAM3_CONFIG.robotHalfSizeIn * 2,
    fieldSizeIn: TEAM3_CONFIG.fieldHalfSizeIn * 2,
    steps: state.recordedSteps.length > 0 ? state.recordedSteps : stepsToExport,
  };

  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `team3-web-recording-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

// ============================================================================
// REPLAY LOADER
// ============================================================================
async function loadReplayFile(filename) {
  try {
    const res = await fetch(`data/recordings/${filename}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    applyReplayJson(json);
  } catch (err) {
    console.error('Failed to load replay:', err);
    state.telemetryLines = [`Error loading replay ${filename}:`, String(err)];
  }
}

function applyReplayJson(json) {
  state.replayData = json;
  state.replayIndex = 0;
  state.trail = [];
  if (json.steps && json.steps.length > 0) {
    for (const s of json.steps) {
      if (s.pose) state.trail.push({ x: s.pose.x, y: s.pose.y });
    }
    applyReplayStep(0);
  }
  updateTimelineBounds();
}

function applyReplayStep(index) {
  if (!state.replayData || !state.replayData.steps || state.replayData.steps.length === 0) return;
  const clampedIdx = clamp(index, 0, state.replayData.steps.length - 1);
  state.replayIndex = clampedIdx;
  const step = state.replayData.steps[clampedIdx];

  state.stepNumber = step.stepNumber ?? clampedIdx;
  state.simTimeMs = step.simTimeMs ?? clampedIdx * (state.replayData.stepMs || 20);
  if (step.pose) {
    state.pose = {
      x: step.pose.x ?? 0,
      y: step.pose.y ?? 0,
      headingDeg: step.pose.headingDeg ?? 0,
    };
  }
  if (step.velocity) {
    state.velocity = {
      vx: step.velocity.vx ?? 0,
      vy: step.velocity.vy ?? 0,
      omegaDegPerSec: step.velocity.omegaDegPerSec ?? 0,
    };
  }
  state.collided = !!step.collided;
  state.autoStepName = step.autoStep || state.replayData.opMode || 'REPLAY';

  // Motors & Servos
  for (const k of Object.keys(state.motors)) state.motors[k] = 0;
  if (step.motors) {
    for (const [k, v] of Object.entries(step.motors)) {
      state.motors[k] = v;
    }
  }
  if (step.servos) {
    for (const [k, v] of Object.entries(step.servos)) {
      state.servos[k] = v;
    }
  }

  // Pieces
  if (step.pieces) {
    state.pieces = step.pieces.map((p) => ({
      type: p.type,
      x: p.x,
      y: p.y,
      z: p.z || (p.type === 'pollen' ? 1.4 : 1.8),
      vx: p.vx || 0,
      vy: p.vy || 0,
      radiusIn: p.radiusIn || (p.type === 'pollen' ? 1.4 : 1.8),
    }));
  }

  state.tagsInView = detectAprilTags(state.pose);
  state.telemetryLines = step.telemetry && step.telemetry.length > 0
    ? step.telemetry
    : [
        `Replay : ${state.replayData.opMode || state.replayData.scenario || 'Recording'}`,
        `Frame  : ${clampedIdx + 1} / ${state.replayData.steps.length}`,
        `Pose   : (${state.pose.x.toFixed(1)}, ${state.pose.y.toFixed(1)}, ${state.pose.headingDeg.toFixed(1)}°)`,
      ];

  dom.timelineSlider.value = String(clampedIdx);
}

// ============================================================================
// WEBSOCKET BRIDGE TO JAVA HARNESS (ws://localhost:7466 -> TCP 127.0.0.1:7465)
// ============================================================================
function connectJavaBridge() {
  disconnectJavaBridge();
  state.autoStepName = 'CONNECTING WS://LOCALHOST:7466...';
  state.telemetryLines = [
    'Connecting to Java Bridge Proxy on ws://localhost:7466...',
    'To start the Java Bridge on your machine or Codespace:',
    '  1. cd th_src && .\\gradlew.bat unityBridge',
    '  2. node web/bridge-proxy.mjs',
  ];

  try {
    const ws = new WebSocket('ws://localhost:7466');
    state.ws = ws;

    ws.onopen = () => {
      state.wsConnected = true;
      state.autoStepName = 'JAVA BRIDGE CONNECTED';
    };

    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data);
        if (msg.type === 'step') {
          state.stepNumber = msg.stepNumber ?? state.stepNumber + 1;
          state.simTimeMs = msg.simTimeMs ?? state.stepNumber * 20;
          if (msg.pose) state.pose = msg.pose;
          if (msg.velocity) state.velocity = msg.velocity;
          if (msg.motors) Object.assign(state.motors, msg.motors);
          if (msg.servos) Object.assign(state.servos, msg.servos);
          if (msg.pieces) state.pieces = msg.pieces;
          state.collided = !!msg.collided;
          state.tagsInView = detectAprilTags(state.pose);
          if (msg.telemetry) state.telemetryLines = msg.telemetry;

          // Send back live gamepad state for next Java step
          ws.send(JSON.stringify({
            type: 'input',
            gamepad1: {
              leftStickX: state.gamepadInput.lx,
              leftStickY: state.gamepadInput.ly,
              rightStickX: state.gamepadInput.rx,
              rightStickY: state.gamepadInput.ry,
              leftTrigger: state.gamepadInput.lt,
              rightTrigger: state.gamepadInput.rt,
              a: state.gamepadInput.a,
              b: state.gamepadInput.b,
              x: state.gamepadInput.x,
              y: state.gamepadInput.y,
              leftBumper: state.gamepadInput.lb,
              rightBumper: state.gamepadInput.rb,
            },
          }));
        }
      } catch (e) {
        console.warn('Bridge parse error:', e);
      }
    };

    ws.onerror = () => {
      state.wsConnected = false;
      state.autoStepName = 'BRIDGE OFFLINE (USING BROWSER SIM)';
    };

    ws.onclose = () => {
      state.wsConnected = false;
    };
  } catch (e) {
    console.warn('WebSocket error:', e);
  }
}

function disconnectJavaBridge() {
  if (state.ws) {
    state.ws.close();
    state.ws = null;
  }
  state.wsConnected = false;
}

// ============================================================================
// 3D WEBGL RENDERER (Three.js)
// ============================================================================
let scene, camera, renderer, controls;
let robotGroup, intakeDeployMesh;
let pieceMeshes = [];
let obstacleGroup, sightlinesGroup, trailLine;

function init3DArena() {
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x090d14);

  // In Three.js we use Y-up:
  //   Three X = FTC +X (toward Audience)
  //   Three Y = FTC +Z (up)
  //   Three Z = -FTC +Y (so FTC +Y Blue is -Z, FTC -Y Red is +Z)
  camera = new THREE.PerspectiveCamera(46, 1, 1, 600);
  setCameraPreset(1);

  renderer = new THREE.WebGLRenderer({ canvas: dom.canvas3d, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxPolarAngle = Math.PI * 0.49;
  controls.minDistance = 25;
  controls.maxDistance = 320;

  // Lights
  const ambient = new THREE.AmbientLight(0xdbeafe, 0.75);
  scene.add(ambient);

  const dirLight = new THREE.DirectionalLight(0xffffff, 0.95);
  dirLight.position.set(60, 140, 80);
  scene.add(dirLight);

  const rimLight = new THREE.DirectionalLight(0x22d3ee, 0.3);
  rimLight.position.set(-80, 90, -80);
  scene.add(rimLight);

  build3DFieldGeometry();
  build3DRobot();

  window.addEventListener('resize', resizeCanvases);
  resizeCanvases();
}

function ftcToThree(xIn, yIn, zIn = 0) {
  return new THREE.Vector3(xIn, zIn, -yIn);
}

function build3DFieldGeometry() {
  // 144x144 inch floor made of 6x6 foam tiles (24" each)
  const tileGroup = new THREE.Group();
  for (let row = 0; row < 6; row++) {
    for (let col = 0; col < 6; col++) {
      const xCenter = -60 + col * 24;
      const yCenter = -60 + row * 24;
      const isAlt = (row + col) % 2 === 0;
      const geo = new THREE.BoxGeometry(23.8, 0.6, 23.8);
      const mat = new THREE.MeshStandardMaterial({
        color: isAlt ? 0x1e2636 : 0x232d40,
        roughness: 0.85,
      });
      const tile = new THREE.Mesh(geo, mat);
      tile.position.set(xCenter, -0.3, -yCenter);
      tileGroup.add(tile);
    }
  }
  scene.add(tileGroup);

  // Field Perimeter Walls (12" tall polycarb + frame)
  const wallMat = new THREE.MeshStandardMaterial({
    color: 0x475569,
    transparent: true,
    opacity: 0.35,
    roughness: 0.3,
  });
  const wallHeight = 12;
  const nWall = new THREE.Mesh(new THREE.BoxGeometry(145, wallHeight, 1), wallMat);
  nWall.position.set(0, wallHeight / 2, -72.5); // Blue wall (+Y)
  scene.add(nWall);

  const sWall = new THREE.Mesh(new THREE.BoxGeometry(145, wallHeight, 1), wallMat);
  sWall.position.set(0, wallHeight / 2, 72.5); // Red wall (-Y)
  scene.add(sWall);

  const eWall = new THREE.Mesh(new THREE.BoxGeometry(1, wallHeight, 145), wallMat);
  eWall.position.set(72.5, wallHeight / 2, 0); // Audience wall (+X)
  scene.add(eWall);

  const wWall = new THREE.Mesh(new THREE.BoxGeometry(1, wallHeight, 145), wallMat);
  wWall.position.set(-72.5, wallHeight / 2, 0); // Back wall (-X)
  scene.add(wWall);

  // Alliance Wall colored strips
  const redStrip = new THREE.Mesh(
    new THREE.BoxGeometry(144, 1.2, 1.4),
    new THREE.MeshBasicMaterial({ color: 0xef4444 })
  );
  redStrip.position.set(0, 12.2, 72.5);
  scene.add(redStrip);

  const blueStrip = new THREE.Mesh(
    new THREE.BoxGeometry(144, 1.2, 1.4),
    new THREE.MeshBasicMaterial({ color: 0x3b82f6 })
  );
  blueStrip.position.set(0, 12.2, -72.5);
  scene.add(blueStrip);

  if (!state.field) return;

  // Red & Blue HIVEs
  for (const hive of state.field.hives || []) {
    const w = hive.maxX - hive.minX;
    const d = hive.maxY - hive.minY;
    const h = hive.maxZ - hive.minZ;
    const cx = (hive.minX + hive.maxX) / 2;
    const cy = (hive.minY + hive.maxY) / 2;
    const cz = (hive.minZ + hive.maxZ) / 2;
    const isRed = hive.alliance === 'red';

    const hiveGroup = new THREE.Group();
    const baseMat = new THREE.MeshStandardMaterial({
      color: isRed ? 0xb91c1c : 0x1d4ed8,
      roughness: 0.45,
      metalness: 0.2,
    });
    const baseMesh = new THREE.Mesh(new THREE.BoxGeometry(w, h * 0.45, d), baseMat);
    baseMesh.position.set(cx, h * 0.225, -cy);
    hiveGroup.add(baseMesh);

    // Angled Honeycomb / AprilTag panel
    const panelMat = new THREE.MeshStandardMaterial({
      color: isRed ? 0xef4444 : 0x3b82f6,
      roughness: 0.35,
    });
    const panelMesh = new THREE.Mesh(new THREE.BoxGeometry(w * 0.96, h * 0.65, d * 0.55), panelMat);
    panelMesh.position.set(cx, cz + 2, -cy);
    panelMesh.rotation.x = degToRad(isRed ? 30 : -30);
    hiveGroup.add(panelMesh);

    scene.add(hiveGroup);
  }

  // 4 Flowers
  for (const fl of state.field.flowers || []) {
    const w = fl.maxX - fl.minX;
    const d = fl.maxY - fl.minY;
    const h = fl.maxZ - fl.minZ;
    const cx = (fl.minX + fl.maxX) / 2;
    const cy = (fl.minY + fl.maxY) / 2;

    const stemMat = new THREE.MeshStandardMaterial({ color: 0x10b981, roughness: 0.6 });
    const stem = new THREE.Mesh(new THREE.CylinderGeometry(1.5, 2.2, h * 0.75, 12), stemMat);
    stem.position.set(cx, h * 0.375, -cy);
    scene.add(stem);

    const headMat = new THREE.MeshStandardMaterial({ color: 0xf59e0b, roughness: 0.4 });
    const head = new THREE.Mesh(new THREE.CylinderGeometry(Math.max(w, d) * 0.52, Math.max(w, d) * 0.35, 3.5, 6), headMat);
    head.position.set(cx, h - 1.75, -cy);
    scene.add(head);
  }

  // 16 AprilTag plaques on the HIVEs
  for (const tag of state.field.aprilTags || []) {
    const tagGeo = new THREE.BoxGeometry(2.6, 2.6, 0.25);
    const tagMat = new THREE.MeshBasicMaterial({ color: 0xf8fafc });
    const m = new THREE.Mesh(tagGeo, tagMat);
    m.position.copy(ftcToThree(tag.x, tag.y, tag.z || 10.5));
    // Tag normal points along tag.yawDeg
    m.rotation.y = degToRad((tag.yawDeg || 0) - 90);
    scene.add(m);
  }

  // Collider wireframes (pieceObstacles + robotObstacles)
  obstacleGroup = new THREE.Group();
  obstacleGroup.visible = state.showObstacles;
  const allObs = [
    ...(state.field.hives || []),
    ...(state.field.flowers || []),
    ...(state.field.pieceObstacles || []),
  ];
  for (const obs of allObs) {
    const w = obs.maxX - obs.minX;
    const d = obs.maxY - obs.minY;
    const h = (obs.maxZ || 6) - (obs.minZ || 0);
    const cx = (obs.minX + obs.maxX) / 2;
    const cy = (obs.minY + obs.maxY) / 2;
    const cz = ((obs.minZ || 0) + (obs.maxZ || 6)) / 2;
    const edges = new THREE.EdgesGeometry(new THREE.BoxGeometry(w, h, d));
    const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0xef4444 }));
    line.position.set(cx, cz, -cy);
    obstacleGroup.add(line);
  }
  scene.add(obstacleGroup);

  // Sightlines Group
  sightlinesGroup = new THREE.Group();
  scene.add(sightlinesGroup);

  // Path Trail Line
  const trailGeo = new THREE.BufferGeometry();
  const trailMat = new THREE.LineBasicMaterial({ color: 0x10b981, linewidth: 2 });
  trailLine = new THREE.Line(trailGeo, trailMat);
  scene.add(trailLine);
}

function build3DRobot() {
  robotGroup = new THREE.Group();

  // 18x18" Mecanum Drivetrain Chassis
  const chassisMat = new THREE.MeshStandardMaterial({
    color: 0x0f172a,
    metalness: 0.5,
    roughness: 0.4,
  });
  const chassis = new THREE.Mesh(new THREE.BoxGeometry(18, 4.5, 18), chassisMat);
  chassis.position.set(0, 3.0, 0);
  robotGroup.add(chassis);

  // Cyan Team 3 side plates & superstructure
  const sideMat = new THREE.MeshStandardMaterial({ color: 0x22d3ee, roughness: 0.3 });
  const leftPlate = new THREE.Mesh(new THREE.BoxGeometry(17.5, 5.2, 0.8), sideMat);
  leftPlate.position.set(0, 3.2, -8.8);
  robotGroup.add(leftPlate);

  const rightPlate = new THREE.Mesh(new THREE.BoxGeometry(17.5, 5.2, 0.8), sideMat);
  rightPlate.position.set(0, 3.2, 8.8);
  robotGroup.add(rightPlate);

  // Outtake / Midtake Tower
  const towerMat = new THREE.MeshStandardMaterial({ color: 0x334155, metalness: 0.4, roughness: 0.5 });
  const tower = new THREE.Mesh(new THREE.BoxGeometry(8, 9, 12), towerMat);
  tower.position.set(-2, 9.0, 0);
  robotGroup.add(tower);

  // Deployable Intake Arm on front (+X in robot local coordinates)
  intakeDeployMesh = new THREE.Mesh(
    new THREE.BoxGeometry(4.5, 2.2, 13),
    new THREE.MeshStandardMaterial({ color: 0xf59e0b, roughness: 0.4 })
  );
  intakeDeployMesh.position.set(8.5, 2.5, 0);
  robotGroup.add(intakeDeployMesh);

  // Webcam 1 Mast at (forward +6", up +12")
  const camMesh = new THREE.Mesh(
    new THREE.BoxGeometry(1.8, 1.4, 2.8),
    new THREE.MeshStandardMaterial({ color: 0xf8fafc })
  );
  camMesh.position.set(6.0, 12.0, 0);
  robotGroup.add(camMesh);

  // Heading Arrow on top of robot (+X forward)
  const arrowGeo = new THREE.ConeGeometry(2.5, 6, 4);
  arrowGeo.rotateZ(-Math.PI / 2);
  const arrowMesh = new THREE.Mesh(arrowGeo, new THREE.MeshBasicMaterial({ color: 0x22d3ee }));
  arrowMesh.position.set(4.5, 14.2, 0);
  robotGroup.add(arrowMesh);

  scene.add(robotGroup);
}

function sync3DScene() {
  if (!scene || !robotGroup) return;

  // Update robot pose
  robotGroup.position.set(state.pose.x, 0, -state.pose.y);
  robotGroup.rotation.y = degToRad(state.pose.headingDeg);

  // Animate intake-deploy servo
  const deployPos = state.servos['intake-deploy'] || 0;
  intakeDeployMesh.position.x = 7.5 + deployPos * 2.5;
  intakeDeployMesh.rotation.z = degToRad(-deployPos * 25);

  // Sync game pieces pool
  while (pieceMeshes.length < state.pieces.length) {
    const geo = new THREE.SphereGeometry(1, 16, 12);
    const mat = new THREE.MeshStandardMaterial({ roughness: 0.35, metalness: 0.1 });
    const mesh = new THREE.Mesh(geo, mat);
    scene.add(mesh);
    pieceMeshes.push(mesh);
  }
  for (let i = 0; i < pieceMeshes.length; i++) {
    const mesh = pieceMeshes[i];
    if (i >= state.pieces.length) {
      mesh.visible = false;
      continue;
    }
    const p = state.pieces[i];
    mesh.visible = true;
    const r = p.radiusIn || (p.type === 'pollen' ? 1.4 : 1.8);
    mesh.scale.set(r, r, r);
    mesh.position.set(p.x, p.z || r, -p.y);

    if (p.type === 'pollen') mesh.material.color.setHex(0xfacc15);
    else if (p.type === 'red-nectar') mesh.material.color.setHex(0xef4444);
    else mesh.material.color.setHex(0x3b82f6);
  }

  // Sync AprilTag Sightlines
  while (sightlinesGroup.children.length > 0) {
    const c = sightlinesGroup.children[0];
    c.geometry.dispose();
    sightlinesGroup.remove(c);
  }
  if (state.showSightlines && state.tagsInView.length > 0) {
    const hRad = degToRad(state.pose.headingDeg);
    const camX = state.pose.x + TEAM3_CONFIG.webcam.forwardIn * Math.cos(hRad);
    const camY = state.pose.y + TEAM3_CONFIG.webcam.forwardIn * Math.sin(hRad);
    const camPt = ftcToThree(camX, camY, TEAM3_CONFIG.webcam.upIn);

    for (const tag of state.tagsInView) {
      const tagPt = ftcToThree(tag.x, tag.y, tag.z || 10.5);
      const lineGeo = new THREE.BufferGeometry().setFromPoints([camPt, tagPt]);
      const lineMat = new THREE.LineBasicMaterial({ color: 0x22d3ee });
      sightlinesGroup.add(new THREE.Line(lineGeo, lineMat));
    }
  }

  // Sync Path Trail
  trailLine.visible = state.showTrail;
  if (state.showTrail && state.trail.length > 1) {
    const pts = state.trail.map((pt) => new THREE.Vector3(pt.x, 0.5, -pt.y));
    trailLine.geometry.dispose();
    trailLine.geometry = new THREE.BufferGeometry().setFromPoints(pts);
  }

  obstacleGroup.visible = state.showObstacles;

  // Chase cam update if preset 3 active
  if (activeCamPreset === 3) {
    const hRad = degToRad(state.pose.headingDeg);
    const behindX = state.pose.x - 42 * Math.cos(hRad);
    const behindY = state.pose.y - 42 * Math.sin(hRad);
    camera.position.lerp(new THREE.Vector3(behindX, 32, -behindY), 0.15);
    controls.target.lerp(new THREE.Vector3(state.pose.x, 6, -state.pose.y), 0.2);
  }

  controls.update();
  renderer.render(scene, camera);
}

let activeCamPreset = 1;
function setCameraPreset(presetNum) {
  activeCamPreset = presetNum;
  document.querySelectorAll('#cam-presets-bar .chip-btn').forEach((btn) => {
    btn.classList.toggle('active', Number(btn.dataset.cam) === presetNum);
  });
  if (!camera) return;

  switch (presetNum) {
    case 1: // Audience (+X) overview
      camera.position.set(135, 95, 0);
      if (controls) controls.target.set(0, 0, 0);
      break;
    case 2: // Overhead
      camera.position.set(0.1, 185, 0);
      if (controls) controls.target.set(0, 0, 0);
      break;
    case 3: // Chase Camera
      break;
    case 4: // Red Driver Station (-Y)
      camera.position.set(0, 88, 138);
      if (controls) controls.target.set(0, 0, 0);
      break;
    case 5: // Blue Driver Station (+Y)
      camera.position.set(0, 88, -138);
      if (controls) controls.target.set(0, 0, 0);
      break;
    case 6: // Red HIVE Close-Up
      camera.position.set(35, 42, 62);
      if (controls) controls.target.set(0, 8, 24);
      break;
    case 7: // Blue HIVE Close-Up
      camera.position.set(35, 42, -62);
      if (controls) controls.target.set(0, 8, -24);
      break;
  }
  if (controls) controls.update();
}

// ============================================================================
// 2D TOP-DOWN TACTICAL CANVAS RENDERER (Exact port of FieldCanvas.java)
// ============================================================================
function render2DField() {
  const canvas = dom.canvas2d;
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  const margin = 34;
  const size = Math.min(w, h) - margin * 2;
  if (size <= 40) return;

  const ox = (w - size) / 2;
  const oy = (h - size) / 2;
  const scale = size / 144.0; // pixels per inch

  // Map FTC (x, y) to 2D canvas: +X right (Audience), +Y up (Blue wall at top, Red wall at bottom)
  const toPxX = (xIn) => ox + ((xIn + 72.0) / 144.0) * size;
  const toPxY = (yIn) => oy + ((72.0 - yIn) / 144.0) * size;

  // 6x6 Foam Tiles
  for (let r = 0; r < 6; r++) {
    for (let c = 0; c < 6; c++) {
      ctx.fillStyle = (r + c) % 2 === 0 ? '#182030' : '#1d2739';
      const tx = ox + (c * size) / 6;
      const ty = oy + (r * size) / 6;
      ctx.fillRect(tx, ty, size / 6 + 0.5, size / 6 + 0.5);
    }
  }

  // Center axes
  ctx.strokeStyle = 'rgba(148, 163, 184, 0.2)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(toPxX(-72), toPxY(0));
  ctx.lineTo(toPxX(72), toPxY(0));
  ctx.moveTo(toPxX(0), toPxY(-72));
  ctx.lineTo(toPxX(0), toPxY(72));
  ctx.stroke();

  // HIVEs & Flowers
  if (state.field) {
    for (const hive of state.field.hives || []) {
      const x1 = toPxX(hive.minX);
      const y1 = toPxY(hive.maxY);
      const bw = (hive.maxX - hive.minX) * scale;
      const bh = (hive.maxY - hive.minY) * scale;
      ctx.fillStyle = hive.alliance === 'red' ? 'rgba(239, 68, 68, 0.35)' : 'rgba(59, 130, 246, 0.35)';
      ctx.strokeStyle = hive.alliance === 'red' ? '#ef4444' : '#3b82f6';
      ctx.lineWidth = 2;
      ctx.fillRect(x1, y1, bw, bh);
      ctx.strokeRect(x1, y1, bw, bh);
    }

    for (const fl of state.field.flowers || []) {
      const x1 = toPxX(fl.minX);
      const y1 = toPxY(fl.maxY);
      const bw = (fl.maxX - fl.minX) * scale;
      const bh = (fl.maxY - fl.minY) * scale;
      ctx.fillStyle = 'rgba(245, 158, 11, 0.35)';
      ctx.strokeStyle = '#f59e0b';
      ctx.lineWidth = 1.5;
      ctx.fillRect(x1, y1, bw, bh);
      ctx.strokeRect(x1, y1, bw, bh);
    }

    if (state.showObstacles) {
      ctx.strokeStyle = 'rgba(239, 68, 68, 0.8)';
      ctx.setLineDash([4, 3]);
      for (const obs of state.field.pieceObstacles || []) {
        const x1 = toPxX(obs.minX);
        const y1 = toPxY(obs.maxY);
        ctx.strokeRect(x1, y1, (obs.maxX - obs.minX) * scale, (obs.maxY - obs.minY) * scale);
      }
      ctx.setLineDash([]);
    }
  }

  // Trail
  if (state.showTrail && state.trail.length > 1) {
    ctx.strokeStyle = '#10b981';
    ctx.lineWidth = 2;
    ctx.beginPath();
    state.trail.forEach((pt, idx) => {
      const px = toPxX(pt.x);
      const py = toPxY(pt.y);
      if (idx === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    });
    ctx.stroke();
  }

  // Game Pieces
  for (const p of state.pieces) {
    const px = toPxX(p.x);
    const py = toPxY(p.y);
    const pr = Math.max(3, (p.radiusIn || 1.5) * scale);
    ctx.beginPath();
    ctx.arc(px, py, pr, 0, Math.PI * 2);
    ctx.fillStyle = p.type === 'pollen' ? '#facc15' : (p.type === 'red-nectar' ? '#ef4444' : '#3b82f6');
    ctx.fill();
    ctx.strokeStyle = '#090d14';
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  // AprilTag Sightlines
  if (state.showSightlines && state.tagsInView.length > 0) {
    ctx.strokeStyle = '#22d3ee';
    ctx.lineWidth = 1.5;
    ctx.setLineDash([5, 4]);
    for (const tag of state.tagsInView) {
      ctx.beginPath();
      ctx.moveTo(toPxX(state.pose.x), toPxY(state.pose.y));
      ctx.lineTo(toPxX(tag.x), toPxY(tag.y));
      ctx.stroke();
    }
    ctx.setLineDash([]);
  }

  // Robot 18x18" Oriented Box
  ctx.save();
  ctx.translate(toPxX(state.pose.x), toPxY(state.pose.y));
  ctx.rotate(degToRad(-state.pose.headingDeg));
  const rPx = TEAM3_CONFIG.robotHalfSizeIn * scale;
  ctx.fillStyle = state.collided ? 'rgba(239, 68, 68, 0.35)' : 'rgba(34, 211, 238, 0.22)';
  ctx.strokeStyle = state.collided ? '#ef4444' : '#22d3ee';
  ctx.lineWidth = 2.2;
  ctx.fillRect(-rPx, -rPx, rPx * 2, rPx * 2);
  ctx.strokeRect(-rPx, -rPx, rPx * 2, rPx * 2);

  // Heading arrow (+X robot forward)
  ctx.beginPath();
  ctx.moveTo(0, 0);
  ctx.lineTo(rPx, 0);
  ctx.strokeStyle = '#f59e0b';
  ctx.lineWidth = 3;
  ctx.stroke();
  ctx.restore();

  // Field Perimeter Border & Labels
  ctx.strokeStyle = '#64748b';
  ctx.lineWidth = 2;
  ctx.strokeRect(ox, oy, size, size);

  ctx.fillStyle = '#94a3b8';
  ctx.font = 'bold 11px JetBrains Mono, monospace';
  ctx.textAlign = 'center';
  ctx.fillText('BLUE ALLIANCE (+Y = +72")', ox + size / 2, oy - 10);
  ctx.fillText('RED ALLIANCE (-Y = -72")', ox + size / 2, oy + size + 18);
}

// ============================================================================
// WEBCAM 1 PICTURE-IN-PICTURE RENDERER (640x480 aspect)
// ============================================================================
function renderWebcamPip() {
  if (!state.showPip) return;
  const canvas = dom.canvasPip;
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;

  // Horizon sky & floor
  ctx.fillStyle = '#0b1322';
  ctx.fillRect(0, 0, w, h * 0.52);
  ctx.fillStyle = '#1a2333';
  ctx.fillRect(0, h * 0.52, w, h * 0.48);

  // Crosshair reticle
  ctx.strokeStyle = 'rgba(34, 211, 238, 0.25)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(w / 2, 0);
  ctx.lineTo(w / 2, h);
  ctx.moveTo(0, h / 2);
  ctx.lineTo(w, h / 2);
  ctx.stroke();

  // Project visible AprilTags onto Webcam 1 image plane
  const hfov = TEAM3_CONFIG.webcam.horizontalFovDeg;
  const vfov = TEAM3_CONFIG.webcam.verticalFovDeg;

  for (const tag of state.tagsInView) {
    // bearing > 0 is left of center, so screen X decreases with +bearing
    const u = 0.5 - tag.bearingDeg / hfov;
    const v = 0.5 - tag.elevationDeg / vfov;
    const cx = u * w;
    const cy = v * h;
    const boxSize = clamp((380 / Math.max(10, tag.rangeIn)), 12, 64);

    const isRed = tag.id >= 38;
    ctx.strokeStyle = isRed ? '#ef4444' : '#22d3ee';
    ctx.fillStyle = 'rgba(248, 250, 252, 0.9)';
    ctx.lineWidth = 2;

    ctx.fillRect(cx - boxSize / 2, cy - boxSize / 2, boxSize, boxSize);
    ctx.strokeRect(cx - boxSize / 2, cy - boxSize / 2, boxSize, boxSize);

    ctx.fillStyle = '#06121c';
    ctx.font = 'bold 10px JetBrains Mono, monospace';
    ctx.textAlign = 'center';
    ctx.fillText(String(tag.id), cx, cy + 3.5);

    ctx.fillStyle = '#22d3ee';
    ctx.font = '9px JetBrains Mono, monospace';
    ctx.fillText(`${tag.rangeIn.toFixed(0)}"`, cx, cy + boxSize / 2 + 11);
  }

  dom.pipTagCount.textContent = `${state.tagsInView.length} TAG${state.tagsInView.length === 1 ? '' : 'S'}`;
}

// ============================================================================
// UI & TELEMETRY DASHBOARD UPDATER
// ============================================================================
function updateTelemetryUI() {
  // Top Clock
  dom.clockReadout.textContent = `T+ ${(state.simTimeMs / 1000).toFixed(2)}s`;
  dom.stepReadout.textContent = `STEP ${String(state.stepNumber).padStart(4, '0')} · 50 Hz`;

  // Pose Card
  dom.valX.textContent = `${state.pose.x.toFixed(1)}"`;
  dom.valY.textContent = `${state.pose.y.toFixed(1)}"`;
  dom.valH.textContent = `${state.pose.headingDeg.toFixed(1)}°`;
  const speed = Math.hypot(state.velocity.vx, state.velocity.vy);
  dom.valSpeed.textContent = `${speed.toFixed(1)} in/s`;
  dom.valVx.textContent = state.velocity.vx.toFixed(1);
  dom.valVy.textContent = state.velocity.vy.toFixed(1);
  dom.valOmega.textContent = state.velocity.omegaDegPerSec.toFixed(0);
  dom.collisionIndicator.classList.toggle('hidden', !state.collided);

  // Actuators Card
  const actuatorEntries = [
    ...Object.entries(state.motors).map(([k, v]) => ({ name: k, val: v, isServo: false })),
    ...Object.entries(state.servos).map(([k, v]) => ({ name: k, val: v, isServo: true })),
  ];
  dom.actuatorsList.innerHTML = actuatorEntries
    .map((a) => {
      const pct = Math.min(100, Math.abs(a.val) * 100).toFixed(0);
      const cls = a.isServo ? 'servo' : a.val < 0 ? 'neg' : '';
      return `
        <div class="actuator-row">
          <div class="actuator-top">
            <span class="actuator-name">${a.name}</span>
            <span class="actuator-val">${a.val.toFixed(2)}</span>
          </div>
          <div class="actuator-bar-bg">
            <div class="actuator-bar-fill ${cls}" style="width:${pct}%"></div>
          </div>
        </div>`;
    })
    .join('');

  // AprilTag Vision Table
  dom.tagsSummary.textContent = `${state.tagsInView.length} in view`;
  if (state.tagsInView.length === 0) {
    dom.tagsTbody.innerHTML = `<tr><td colspan="5" class="empty-row">No AprilTags in camera frustum</td></tr>`;
  } else {
    dom.tagsTbody.innerHTML = state.tagsInView
      .map(
        (t) => `
        <tr>
          <td><strong>#${t.id}</strong></td>
          <td>${t.rangeIn.toFixed(1)}"</td>
          <td>${t.bearingDeg >= 0 ? '+' : ''}${t.bearingDeg.toFixed(1)}°</td>
          <td>${t.elevationDeg.toFixed(1)}°</td>
          <td>${t.yawDeg.toFixed(1)}°</td>
        </tr>`
      )
      .join('');
  }

  // Driver Station Telemetry
  dom.autoStepBadge.textContent = state.autoStepName;
  dom.telemetryBox.textContent = state.telemetryLines.join('\n');

  // Gamepad Visualizer
  dom.gpIdLabel.textContent = state.gamepadInput.id;
  dom.gpDot.classList.toggle('gamepad', state.gamepadInput.connected);
  dom.gpStatusText.textContent = state.gamepadInput.connected
    ? `USB/BT Gamepad Active (${state.gamepadInput.id})`
    : 'Keyboard Active: WASD (Move) · J/L or ◀/▶ (Turn) · I/K (Intake/Outtake) · U (Midtake) · M/N (Deploy Servo)';

  drawStickCanvas(dom.stickLeft, state.gamepadInput.lx, state.gamepadInput.ly);
  drawStickCanvas(dom.stickRight, state.gamepadInput.rx, state.gamepadInput.ry);
  dom.barLt.style.width = `${(state.gamepadInput.lt * 100).toFixed(0)}%`;
  dom.barRt.style.width = `${(state.gamepadInput.rt * 100).toFixed(0)}%`;

  dom.gpButtonsRow.querySelectorAll('.gp-pill').forEach((el) => {
    const key = el.dataset.btn;
    el.classList.toggle('pressed', !!state.gamepadInput[key]);
  });
}

function drawStickCanvas(canvas, x, y) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;
  const r = w / 2 - 6;
  ctx.clearRect(0, 0, w, h);

  ctx.strokeStyle = '#24324a';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(w / 2, 4);
  ctx.lineTo(w / 2, h - 4);
  ctx.moveTo(4, h / 2);
  ctx.lineTo(w - 4, h / 2);
  ctx.stroke();

  const px = w / 2 + x * r;
  const py = h / 2 + y * r;
  ctx.fillStyle = '#22d3ee';
  ctx.beginPath();
  ctx.arc(px, py, 5, 0, Math.PI * 2);
  ctx.fill();
}

function updateTimelineBounds() {
  if (state.mode.startsWith('replay:') && state.replayData && state.replayData.steps) {
    dom.timelineSlider.max = String(Math.max(0, state.replayData.steps.length - 1));
    dom.timelineSlider.value = String(state.replayIndex);
    dom.timelineSlider.disabled = false;
  } else {
    dom.timelineSlider.max = '100';
    dom.timelineSlider.value = '0';
    dom.timelineSlider.disabled = true;
  }
}

function resizeCanvases() {
  const rect = dom.arenaContainer.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return;

  dom.canvas2d.width = rect.width;
  dom.canvas2d.height = rect.height;

  if (renderer && camera) {
    camera.aspect = rect.width / rect.height;
    camera.updateProjectionMatrix();
    renderer.setSize(rect.width, rect.height, false);
  }
}

// ============================================================================
// EVENT LISTENERS & MAIN ANIMATION LOOP
// ============================================================================
function bindEvents() {
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'SELECT' || e.target.tagName === 'INPUT') return;
    state.keys.add(e.code);

    if (e.code === 'Space') {
      e.preventDefault();
      togglePlayPause();
    } else if (e.code === 'KeyR') {
      resetCurrentMode();
    } else if (e.code === 'BracketLeft') {
      stepSingle(-1);
    } else if (e.code === 'BracketRight') {
      stepSingle(1);
    } else if (e.code === 'Digit1') setCameraPreset(1);
    else if (e.code === 'Digit2') setCameraPreset(2);
    else if (e.code === 'Digit3') setCameraPreset(3);
    else if (e.code === 'Digit4') setCameraPreset(4);
    else if (e.code === 'Digit5') setCameraPreset(5);
    else if (e.code === 'Digit6') setCameraPreset(6);
    else if (e.code === 'Digit7') setCameraPreset(7);
  });

  window.addEventListener('keyup', (e) => {
    state.keys.delete(e.code);
  });

  dom.modeSelect.addEventListener('change', () => {
    switchMode(dom.modeSelect.value);
  });

  dom.startSlotSelect.addEventListener('change', () => {
    if (state.mode.startsWith('live:')) {
      resetLiveSimulation();
    }
  });

  dom.btnOpenFile.addEventListener('click', () => dom.fileInput.click());
  dom.fileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const text = await file.text();
    const json = JSON.parse(text);
    state.mode = `replay:${file.name}`;
    dom.statusBadge.textContent = 'REPLAY FILE';
    dom.statusBadge.className = 'badge-live replay';
    applyReplayJson(json);
  });

  dom.btnView3d.addEventListener('click', () => setViewMode('3d'));
  dom.btnView2d.addEventListener('click', () => setViewMode('2d'));

  dom.camPresetsBar.querySelectorAll('.chip-btn').forEach((btn) => {
    btn.addEventListener('click', () => setCameraPreset(Number(btn.dataset.cam)));
  });

  dom.btnToggleSightlines.addEventListener('click', () => {
    state.showSightlines = !state.showSightlines;
    dom.btnToggleSightlines.classList.toggle('active', state.showSightlines);
  });

  dom.btnTogglePip.addEventListener('click', () => {
    state.showPip = !state.showPip;
    dom.btnTogglePip.classList.toggle('active', state.showPip);
    dom.webcamPip.classList.toggle('hidden', !state.showPip);
  });

  dom.btnToggleObstacles.addEventListener('click', () => {
    state.showObstacles = !state.showObstacles;
    dom.btnToggleObstacles.classList.toggle('active', state.showObstacles);
  });

  dom.btnToggleTrail.addEventListener('click', () => {
    state.showTrail = !state.showTrail;
    dom.btnToggleTrail.classList.toggle('active', state.showTrail);
  });

  dom.btnPlayPause.addEventListener('click', togglePlayPause);
  dom.btnReset.addEventListener('click', resetCurrentMode);
  dom.btnStepBack.addEventListener('click', () => stepSingle(-1));
  dom.btnStepFwd.addEventListener('click', () => stepSingle(1));

  dom.timelineSlider.addEventListener('input', () => {
    if (state.mode.startsWith('replay:')) {
      state.playing = false;
      updatePlayPauseButton();
      applyReplayStep(Number(dom.timelineSlider.value));
    }
  });

  dom.speedSelect.addEventListener('change', () => {
    state.speedMultiplier = Number(dom.speedSelect.value) || 1.0;
  });

  dom.btnRecord.addEventListener('click', () => {
    state.isRecording = !state.isRecording;
    dom.btnRecord.classList.toggle('recording', state.isRecording);
    dom.btnRecord.textContent = state.isRecording ? '■ Recording…' : '● Record';
    if (state.isRecording) {
      state.recordedSteps = [];
      recordCurrentFrame();
    }
  });

  dom.btnExport.addEventListener('click', exportRecordingJson);
}

function setViewMode(mode) {
  state.viewMode = mode;
  dom.btnView3d.classList.toggle('active', mode === '3d');
  dom.btnView2d.classList.toggle('active', mode === '2d');
  dom.canvas3d.classList.toggle('hidden', mode !== '3d');
  dom.canvas2d.classList.toggle('hidden', mode !== '2d');
  dom.camPresetsBar.classList.toggle('hidden', mode !== '3d');
  resizeCanvases();
}

function togglePlayPause() {
  state.playing = !state.playing;
  updatePlayPauseButton();
}

function updatePlayPauseButton() {
  dom.btnPlayPause.textContent = state.playing ? '⏸ Pause' : '▶ Play';
}

function resetCurrentMode() {
  if (state.mode.startsWith('live:')) {
    resetLiveSimulation();
  } else if (state.mode.startsWith('replay:')) {
    applyReplayStep(0);
  }
}

function stepSingle(delta) {
  state.playing = false;
  updatePlayPauseButton();
  if (state.mode.startsWith('replay:')) {
    applyReplayStep(state.replayIndex + delta);
  } else if (state.mode.startsWith('live:') && delta > 0) {
    readControllerInput();
    if (state.mode === 'live:teleop') runTeam3TeleOpControl();
    else runTeam3AutonomousControl(TEAM3_CONFIG.stepMs / 1000.0);
    stepLivePhysics(TEAM3_CONFIG.stepMs / 1000.0);
  }
}

function switchMode(newMode) {
  disconnectJavaBridge();
  state.mode = newMode;
  state.playing = true;
  updatePlayPauseButton();

  if (newMode.startsWith('live:')) {
    dom.statusBadge.textContent = 'LIVE SIM';
    dom.statusBadge.className = 'badge-live';
    resetLiveSimulation();
  } else if (newMode.startsWith('replay:')) {
    dom.statusBadge.textContent = 'REPLAY';
    dom.statusBadge.className = 'badge-live replay';
    const filename = newMode.replace('replay:', '');
    loadReplayFile(filename);
  } else if (newMode === 'bridge:ws') {
    dom.statusBadge.textContent = 'JAVA BRIDGE';
    dom.statusBadge.className = 'badge-live bridge';
    resetLiveSimulation();
    connectJavaBridge();
  }
}

// ============================================================================
// INITIALIZATION & 50 HZ FIXED-STEP LOOP
// ============================================================================
let lastFrameTime = performance.now();
let accumulatorMs = 0;

function mainLoop(now) {
  requestAnimationFrame(mainLoop);

  const elapsedMs = Math.min(100, now - lastFrameTime);
  lastFrameTime = now;

  readControllerInput();

  if (state.playing) {
    accumulatorMs += elapsedMs * state.speedMultiplier;
    while (accumulatorMs >= TEAM3_CONFIG.stepMs) {
      accumulatorMs -= TEAM3_CONFIG.stepMs;
      const dtSec = TEAM3_CONFIG.stepMs / 1000.0;

      if (state.mode.startsWith('live:')) {
        if (state.mode === 'live:teleop') {
          runTeam3TeleOpControl();
        } else {
          runTeam3AutonomousControl(dtSec);
        }
        stepLivePhysics(dtSec);
      } else if (state.mode.startsWith('replay:') && state.replayData && state.replayData.steps) {
        if (state.replayIndex + 1 < state.replayData.steps.length) {
          applyReplayStep(state.replayIndex + 1);
        } else {
          state.playing = false;
          updatePlayPauseButton();
        }
      }
    }
  }

  if (state.viewMode === '3d') {
    sync3DScene();
  } else {
    render2DField();
  }
  renderWebcamPip();
  updateTelemetryUI();
}

async function init() {
  try {
    const res = await fetch('data/biobuzz-2026.json');
    state.field = await res.json();
  } catch (e) {
    console.error('Could not load biobuzz-2026.json:', e);
  }

  init3DArena();
  bindEvents();
  resetLiveSimulation();
  requestAnimationFrame(mainLoop);
}

init();
