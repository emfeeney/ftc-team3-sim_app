package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import pvi.harness.hardware.MechanismSetup;

/**
 * The game pieces on the field and how they move: the robot box pushes loose balls out of its
 * way, they roll and slow down, and they bounce off the walls and off each other.
 *
 * <p>Pieces in the {@link GamePiece#FIELD} state roll on the tiles; balls in an ALLIANCE AREA stay
 * where they are, and balls in a CELL roll down its tilted floor to its back wall (see below). Balls in a FLOWER
 * ({@link FlowerSpot}) rest in a stack: only a robot's intake reaching in through the Retrieval Opening takes the
 * bottom POLLEN out, and the rest drop by one. The robot is never slowed by a ball (it is far heavier).</p>
 *
 * <p>A robot with a mechanism ({@link #useMechanism}) can hold balls: its intake takes a loose ball, or a
 * FLOWER's bottom POLLEN, into a queue, and reverse power spits the newest back out. Held balls ride with
 * the robot. Its feed pushes the oldest into the spinning launcher wheel, and the ball flies off under
 * gravity, as {@link GamePiece#FLYING}, until it lands on the tiles and rolls like any loose ball.</p>
 *
 * <p>With the HIVE given ({@link BioBuzzHive}), a flying ball also hits its frame, its arms and the outside
 * of its CELLs, and one that comes in through an upward CELL's opening rests on its floor, as
 * {@link GamePiece#CELL}, and rolls down the tilted floor until it reaches the back wall or a ball already
 * resting there (W-PG). When the balls in a HIVE's upward CELL reach {@link BioBuzzHive#TIP_MASS}, the
 * HIVE tips over {@link BioBuzzHive#TIP_SECONDS}, taking its CELL balls with it, and the balls in the CELL
 * that went down spill out of its opening. A flying ball also bounces off the FLOWERs, the robot and other
 * balls.</p>
 *
 * <p>Every step works through the balls in the same order with no random numbers, so the same
 * run always gives exactly the same pushes, and a replay shows them again exactly.</p>
 *
 * <p>The position estimator owns one of these and calls {@link #step} each time the robot moves.</p>
 */
public final class GamePieces {
    /** Robot and balls never move further than this (inches) in one sub-step, so nothing passes through anything. */
    static final double MAX_MOVE_PER_SUBSTEP_INCHES = 0.5;
    /** Most sub-steps in one step (a teleport across the field would otherwise take forever). */
    static final int MAX_SUBSTEPS = 200;
    /** How high a held ball rides on a robot with no launcher (only for drawing it). */
    private static final double HELD_HEIGHT_WITH_NO_LAUNCHER_INCHES = 8;

    /**
     * How tall the robot box is, for flying balls, which bounce off it (its length and width come from the
     * robot's profile). No robot's height is measured yet.
     */
    public static final ModelValue ROBOT_HEIGHT = ModelValue.placeholder(18, ModelValue.INCHES,
            "the most a robot may be at the start of a match (an 18 in cube); no robot measured");

    private List<GamePiece> initial;
    private final PieceModel model;
    /** What the balls can bump into besides the walls (the HIVE frame, the FLOWERs). */
    private final List<PieceObstacle> obstacles;
    // Current state, one entry per piece in the initial order.
    private final String[] ids;
    private final String[] kinds;
    private final String[] states;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final double[] vx;
    private final double[] vy;
    private final double[] radius;
    private final double[] mass;
    /** Each flying ball's upward speed, in/s. */
    private final double[] vz;
    /** True for a ball just launched, until it has left its own robot's box: it doesn't bounce off it till then. */
    private final boolean[] leavingRobot;
    /** True in outside physics: something else (Unity) moves the loose, flying and CELL balls; see {@link #useOutsidePhysics}. */
    private boolean outside;
    /** In outside physics: the balls Java started moving since the last {@link #takeLaunches}. */
    private final List<PieceMotion> launches = new ArrayList<>();
    /** In outside physics, a flying ball this close to resting on the tiles has landed, and a ball out of a CELL higher than this is flying. */
    static final double LANDED_INCHES = 0.25;
    /** How fast each ball in a FLOWER is falling, in/s, while it drops onto the ball below. */
    private final double[] fallSpeed;
    /** The FLOWERs and the balls stacked in them. */
    private final List<FlowerStack> stacks = new ArrayList<>();
    /** The robot's intake and what it holds; null for a robot with neither. */
    private MechanismSetup mechanism;
    /** The balls the robot holds, by index, oldest first. */
    private final List<Integer> held = new ArrayList<>();
    /** The intake motor's power, inward positive, from the last {@link #intakePower}. */
    private double intakePower;
    /** How long the intake has been running in reverse with a ball to spit, in seconds. */
    private double spitSeconds;
    /** The feed motor's power, inward positive, from the last {@link #feedPower}. */
    private double feedPower;
    /** How fast a ball would leave the launcher wheel now, in/s (negative: the wheel turns backward). */
    private double launcherBallSpeed;
    /** How long the feed has pushed the oldest held ball toward the wheel, in seconds. */
    private double feedSeconds;
    /** Balls launched since the last {@link #takeShots}. */
    private int shots;
    /** The HIVEs, and the frame parts a flying ball can hit; empty for a field without them. */
    private final List<BioBuzzHive.Hive> hives;
    private final List<BioBuzzHive.Box> frame;
    /** Each HIVE's tilt now, and how its tip is going. */
    private final HiveState[] hiveStates;
    /** For a ball in a CELL: which HIVE (-1 for none), and where it sits in that HIVE's level frame. */
    private final int[] cellHive;
    private final double[] levelX;
    private final double[] levelY;
    private final double[] levelZ;
    /** For a ball in a CELL: how fast it rolls down the CELL's floor toward the back wall, in/s. */
    private final double[] cellSpeed;
    /** How long {@link #settledCopy} moved this copy on after STOP, in seconds; 0 for the run's own pieces. */
    private double settleSeconds;
    /**
     * A CELL ball standing still this close (inches) to the back wall, or to a ball nearer the back wall, is already
     * resting there. It stops a ball from creeping a few thousandths of an inch because the staged NECTAR's CAD
     * places are rounded.
     */
    static final double CELL_RESTING_INCHES = 0.01;

    /** One HIVE now. */
    private static final class HiveState {
        double tiltDegrees;
        boolean tipping;
        int tips;
        double fromDegrees;
        double tipSeconds;

        HiveState(double tiltDegrees) {
            this.tiltDegrees = tiltDegrees;
        }
    }

    /** One FLOWER now. */
    private static final class FlowerStack {
        final FlowerSpot spot;
        /** The balls in it, by index, bottom first. */
        final List<Integer> balls = new ArrayList<>();
        /** The bottom ball as something loose balls bounce off; null while it's falling, or if there's none. */
        PieceObstacle seat;

        FlowerStack(FlowerSpot spot) {
            this.spot = spot;
        }
    }

    /** The pieces at their starting spots, standing still, with nothing but the walls to hit. */
    public GamePieces(List<GamePiece> start, PieceModel model) {
        this(start, model, List.of());
    }

    /**
     * The pieces at their starting spots, standing still. Loose balls also bounce off
     * {@code obstacles} (for BIOBUZZ, {@link BioBuzzObstacles#all()}) whenever the walls are on.
     */
    public GamePieces(List<GamePiece> start, PieceModel model, List<PieceObstacle> obstacles) {
        this(start, model, obstacles, List.of());
    }

    /**
     * The same, with these FLOWERs: pieces that start in the {@link GamePiece#FLOWER} state at one of
     * them make up its stack.
     */
    public GamePieces(List<GamePiece> start, PieceModel model, List<PieceObstacle> obstacles, List<FlowerSpot> flowers) {
        this(start, model, obstacles, flowers, List.of(), List.of());
    }

    /**
     * The same, with the HIVE: these HIVEs and frame parts ({@link BioBuzzHive}). Pieces that start in the
     * {@link GamePiece#CELL} state within a HIVE's CELL width rest in that CELL.
     */
    public GamePieces(List<GamePiece> start, PieceModel model, List<PieceObstacle> obstacles, List<FlowerSpot> flowers,
                      List<BioBuzzHive.Hive> hives, List<BioBuzzHive.Box> frame) {
        if (start == null || model == null || obstacles == null || flowers == null || hives == null || frame == null) {
            throw new IllegalArgumentException("Game pieces need a starting list, a piece model, an obstacle list"
                    + " and a FLOWER list.");
        }
        this.initial = List.copyOf(start);
        this.model = model;
        this.obstacles = List.copyOf(obstacles);
        int n = initial.size();
        ids = new String[n];
        kinds = new String[n];
        states = new String[n];
        x = new double[n];
        y = new double[n];
        z = new double[n];
        vx = new double[n];
        vy = new double[n];
        radius = new double[n];
        mass = new double[n];
        fallSpeed = new double[n];
        vz = new double[n];
        leavingRobot = new boolean[n];
        this.hives = List.copyOf(hives);
        this.frame = List.copyOf(frame);
        hiveStates = new HiveState[hives.size()];
        for (int k = 0; k < hiveStates.length; k++) {
            hiveStates[k] = new HiveState(hives.get(k).startTiltDegrees());
        }
        cellHive = new int[n];
        levelX = new double[n];
        levelY = new double[n];
        levelZ = new double[n];
        cellSpeed = new double[n];
        java.util.Arrays.fill(cellHive, -1);
        for (int i = 0; i < n; i++) {
            GamePiece p = initial.get(i);
            for (int j = 0; j < i; j++) {
                if (ids[j].equals(p.id())) {
                    throw new IllegalArgumentException("Two game pieces are both called \"" + p.id() + "\".");
                }
            }
            ids[i] = p.id();
            kinds[i] = p.kind();
            states[i] = p.state();
            x[i] = p.xInches();
            y[i] = p.yInches();
            z[i] = p.zInches();
            radius[i] = p.radiusInches();
            mass[i] = model.massOf(p.kind());
        }
        for (FlowerSpot spot : flowers) {
            FlowerStack stack = new FlowerStack(spot);
            for (int i = 0; i < n; i++) {
                if (GamePiece.FLOWER.equals(states[i]) && Math.hypot(x[i] - spot.xInches(), y[i] - spot.yInches())
                        < FlowerSpot.SAME_SPOT_INCHES) {
                    stack.balls.add(i);
                }
            }
            stack.balls.sort((a, b) -> Double.compare(z[a], z[b]));
            stacks.add(stack);
        }
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < this.hives.size(); k++) {
                if (GamePiece.CELL.equals(states[i])
                        && Math.abs(y[i] - this.hives.get(k).pivotYInches()) <= BioBuzzHive.CELL_HALF_WIDTH_INCHES) {
                    cellHive[i] = k;
                    double[] level = toLevel(k, x[i], z[i]);
                    levelX[i] = level[0];
                    levelY[i] = y[i] - this.hives.get(k).pivotYInches();
                    levelZ[i] = level[1];
                }
            }
        }
    }

    /**
     * Gives the robot its intake and hold, once, before the run. The first {@code hold.preloaded} pre-loaded
     * POLLEN ({@link BioBuzzPieces#PRELOAD_ID_PREFIX}) start held; {@link #initial()} says so too. Without
     * this call nothing is ever held, and nothing ever comes out of a FLOWER ({@link FlowerSpot}).
     *
     * @param setup the robot's mechanism
     * @param robot where the robot starts
     */
    public void useMechanism(MechanismSetup setup, Pose robot) {
        if (setup == null) {
            throw new IllegalArgumentException("Leave out useMechanism for a robot with no mechanism.");
        }
        if (mechanism != null) {
            throw new IllegalStateException("This field's balls already have a mechanism.");
        }
        mechanism = setup;
        int preloads = (int) setup.hold().preloaded().value();
        for (int i = 0; i < ids.length && held.size() < preloads; i++) {
            if (ids[i].startsWith(BioBuzzPieces.PRELOAD_ID_PREFIX)) {
                hold(i);
            }
        }
        placeHeldBalls(robot);
        initial = snapshot();
    }

    /**
     * Switches to outside physics (decision D30): from now on something else, Unity's physics, moves the balls
     * that are loose ({@code field}), {@code flying} or in a CELL, and sends where they are with {@link #supply}
     * before each step. A step then moves none of those itself (no rolling, flight, bounces, robot push, CELL
     * balls swinging with their HIVE or spill), but keeps every rule: the intake, holding, the feed and launch,
     * spitting, the FLOWERs, which CELL a ball is in, and the HIVE tip. The balls it starts moving are reported by
     * {@link #takeLaunches}. Without this call nothing changes. It can't be switched back.
     */
    public void useOutsidePhysics() {
        outside = true;
    }

    /** True once {@link #useOutsidePhysics} was called. */
    public boolean outsidePhysics() {
        return outside;
    }

    /**
     * In outside physics: where the balls the outside moves are now, and how fast they move. Only a ball that is
     * loose, flying or in a CELL takes its entry; one Java holds, or has in a FLOWER or an ALLIANCE AREA, keeps its
     * place (the outside may still be a loop behind after a pickup). A ball not listed keeps its last position.
     *
     * @throws IllegalStateException    if outside physics is off
     * @throws IllegalArgumentException for an id that isn't one of these pieces
     */
    public void supply(List<PieceMotion> motions) {
        if (!outside) {
            throw new IllegalStateException("supply(...) is for outside physics: call useOutsidePhysics() first.");
        }
        for (PieceMotion m : motions) {
            int i = indexOf(m.id());
            if (i < 0) {
                throw new IllegalArgumentException("There is no game piece \"" + m.id() + "\" (pieces: " + String.join(", ", ids) + ").");
            }
            if (GamePiece.FIELD.equals(states[i]) || GamePiece.FLYING.equals(states[i]) || GamePiece.CELL.equals(states[i])) {
                x[i] = m.xInches();
                y[i] = m.yInches();
                z[i] = m.zInches();
                vx[i] = m.vxInchesPerSecond();
                vy[i] = m.vyInchesPerSecond();
                vz[i] = m.vzInchesPerSecond();
            }
        }
    }

    /**
     * In outside physics: every ball Java started moving since the last call, with where it starts and its
     * speed: launched (now {@code flying}) or spat out by the intake (now {@code field}).
     * The outside takes them over from there. Empty in Java's own physics.
     */
    public List<PieceMotion> takeLaunches() {
        List<PieceMotion> taken = List.copyOf(launches);
        launches.clear();
        return taken;
    }

    /** In outside physics, notes that ball {@code i} has just been started moving by Java. */
    private void launched(int i) {
        if (outside) {
            launches.add(new PieceMotion(ids[i], x[i], y[i], z[i], vx[i], vy[i], vz[i]));
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** What the intake motor is doing, inward positive, -1 to 1. It acts until the next call. */
    public void intakePower(double inwardPower) {
        this.intakePower = inwardPower;
    }

    /** What the feed motor is doing, inward positive, -1 to 1. It acts until the next call. */
    public void feedPower(double inwardPower) {
        this.feedPower = inwardPower;
    }

    /**
     * How fast a ball would leave the launcher wheel now, in/s: the wheel's edge speed times
     * {@code launcher.exitFraction}, negative if the wheel turns backward. It acts until the next call.
     */
    public void launcherBallSpeed(double inchesPerSecond) {
        this.launcherBallSpeed = inchesPerSecond;
    }

    /** How many balls were launched since the last call, so the launcher wheel can lose speed for each. */
    public int takeShots() {
        int taken = shots;
        shots = 0;
        return taken;
    }

    /** Each HIVE at the start of the match, in the order given: no tips, no swing. */
    public List<HiveTilt> startHiveTilts() {
        List<HiveTilt> tilts = new ArrayList<>();
        for (BioBuzzHive.Hive hive : hives) {
            tilts.add(new HiveTilt(hive.id(), 0, 0));
        }
        return tilts;
    }

    /** Each HIVE now, in the order given (none for a field without the HIVE): its tips so far and its swing from the start. */
    public List<HiveTilt> hiveTilts() {
        List<HiveTilt> tilts = new ArrayList<>();
        for (int k = 0; k < hives.size(); k++) {
            tilts.add(new HiveTilt(hives.get(k).id(), hiveStates[k].tips,
                    hiveStates[k].tiltDegrees - hives.get(k).startTiltDegrees()));
        }
        return tilts;
    }

    /**
     * The balls resting in one CELL now, oldest first by piece order.
     *
     * @param hiveId the HIVE, e.g. "red-hive"
     * @param side   +1 for its CELL on the pivot's +x side, -1 for the -x side
     */
    public List<String> ballsInCell(String hiveId, int side) {
        List<String> ballIds = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            if (cellHive[i] >= 0 && hives.get(cellHive[i]).id().equals(hiveId) && Math.signum(levelX[i]) == side) {
                ballIds.add(ids[i]);
            }
        }
        return ballIds;
    }

    /** A separate copy of every piece, FLOWER and HIVE as they are now, with the robot's motors off. */
    private GamePieces(GamePieces from) {
        initial = from.initial;
        model = from.model;
        obstacles = from.obstacles;
        ids = from.ids.clone();
        kinds = from.kinds.clone();
        states = from.states.clone();
        x = from.x.clone();
        y = from.y.clone();
        z = from.z.clone();
        vx = from.vx.clone();
        vy = from.vy.clone();
        radius = from.radius.clone();
        mass = from.mass.clone();
        vz = from.vz.clone();
        leavingRobot = from.leavingRobot.clone();
        outside = from.outside;
        fallSpeed = from.fallSpeed.clone();
        for (FlowerStack stack : from.stacks) {
            FlowerStack copy = new FlowerStack(stack.spot);
            copy.balls.addAll(stack.balls);
            copy.seat = stack.seat;
            stacks.add(copy);
        }
        mechanism = from.mechanism;
        held.addAll(from.held);
        hives = from.hives;
        frame = from.frame;
        hiveStates = new HiveState[from.hiveStates.length];
        for (int k = 0; k < hiveStates.length; k++) {
            HiveState was = from.hiveStates[k];
            HiveState now = new HiveState(was.tiltDegrees);
            now.tipping = was.tipping;
            now.tips = was.tips;
            now.fromDegrees = was.fromDegrees;
            now.tipSeconds = was.tipSeconds;
            hiveStates[k] = now;
        }
        cellHive = from.cellHive.clone();
        levelX = from.levelX.clone();
        levelY = from.levelY.clone();
        levelZ = from.levelZ.clone();
        cellSpeed = from.cellSpeed.clone();
        // A stopped robot's motors are off: no intake, feed, spit or launch while the pieces settle.
    }

    /**
     * After STOP: a copy of the pieces, moved on with the robot standing still and its motors off until every ball
     * and HIVE is at rest, or {@code maxSeconds} have passed. This object doesn't change. The manual assesses the
     * end-of-match items once everything has come to rest (TU06 10.5).
     *
     * @param robot        where the robot stopped
     * @param lengthInches the robot box, front to back
     * @param widthInches  the robot box, side to side
     * @param walls        true if the field walls stop the balls
     * @param stepSeconds  how long each step is (the run's loop period)
     * @param maxSeconds   the most simulated time to wait
     * @return the settled copy; {@link #settleSeconds()} on it says how long it took
     */
    public GamePieces settledCopy(Pose robot, double lengthInches, double widthInches, boolean walls,
                                  double stepSeconds, double maxSeconds) {
        if (stepSeconds <= 0 || maxSeconds < 0) {
            throw new IllegalArgumentException("Settling needs a step longer than 0 s and a limit of 0 s or more.");
        }
        GamePieces copy = new GamePieces(this);
        // Outside physics (Unity) moves the balls; Java can't settle them, so the copy stays as it is.
        while (!copy.outside && !copy.atRest() && copy.settleSeconds < maxSeconds - 1e-9) {
            double h = Math.min(stepSeconds, maxSeconds - copy.settleSeconds);
            copy.step(robot, robot, lengthInches, widthInches, h, walls);
            copy.settleSeconds += h;
        }
        return copy;
    }

    /** How long {@link #settledCopy} moved this copy on, in seconds (0 for the run's own pieces). */
    public double settleSeconds() {
        return settleSeconds;
    }

    /** True when nothing moves: no ball flying, rolling, falling in a FLOWER or rolling in a CELL, and no HIVE tipping. */
    public boolean atRest() {
        for (int i = 0; i < ids.length; i++) {
            if (GamePiece.HELD.equals(states[i])) {
                continue;
            }
            if (GamePiece.FLYING.equals(states[i]) || vx[i] != 0 || vy[i] != 0 || vz[i] != 0 || fallSpeed[i] != 0
                    || cellSpeed[i] != 0) {
                return false;
            }
        }
        for (HiveState hive : hiveStates) {
            if (hive.tipping) {
                return false;
            }
        }
        return true;
    }

    /** No pieces at all. */
    public static GamePieces none() {
        return new GamePieces(List.of(), PieceModel.bioBuzz());
    }

    /** The pieces as they were at the start, in their fixed order. */
    public List<GamePiece> initial() {
        return initial;
    }

    /** Every piece now, in the same order as {@link #initial()}. */
    public List<GamePiece> snapshot() {
        List<GamePiece> now = new ArrayList<>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            now.add(new GamePiece(ids[i], kinds[i], states[i], x[i], y[i], z[i]));
        }
        return now;
    }

    /** How the pieces move. */
    public PieceModel model() {
        return model;
    }

    /** A loose ball's speed over the tiles now, in in/s (0 for any other piece). */
    public double speedInchesPerSecond(String id) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(id)) {
                return Math.hypot(vx[i], vy[i]);
            }
        }
        throw new IllegalArgumentException("There is no game piece called \"" + id + "\".");
    }

    /**
     * Moves the loose balls on by {@code seconds}, while the robot box moves from {@code robotBefore}
     * to {@code robotAfter}. The step is cut into small sub-steps so a fast robot can't jump over
     * a ball.
     *
     * @param robotBefore  the robot at the start of the step
     * @param robotAfter   the robot at the end of the step
     * @param lengthInches the robot box, front to back
     * @param widthInches  the robot box, side to side
     * @param seconds      how long the step is
     * @param walls        true if the field walls stop the balls
     */
    public void step(Pose robotBefore, Pose robotAfter, double lengthInches, double widthInches, double seconds,
                     boolean walls) {
        if (seconds <= 0 || ids.length == 0) {
            return;
        }
        if (outside) {
            stepOutside(robotBefore, robotAfter, lengthInches / 2.0, widthInches / 2.0, seconds);
            return;
        }
        double turn = Math.IEEEremainder(robotAfter.headingRadians() - robotBefore.headingRadians(), 2 * Math.PI);
        double reach = Math.hypot(lengthInches, widthInches) / 2.0;
        double robotMove = Math.hypot(robotAfter.xInches() - robotBefore.xInches(),
                robotAfter.yInches() - robotBefore.yInches()) + Math.abs(turn) * reach;
        double ballMove = 0;
        for (int i = 0; i < ids.length; i++) {
            ballMove = Math.max(ballMove, Math.max(Math.hypot(Math.hypot(vx[i], vy[i]), vz[i]), cellSpeed[i]) * seconds);
        }
        int substeps = (int) Math.min(MAX_SUBSTEPS,
                Math.max(1, Math.ceil(Math.max(robotMove, ballMove) / MAX_MOVE_PER_SUBSTEP_INCHES)));
        double h = seconds / substeps;
        Pose last = robotBefore;
        for (int k = 1; k <= substeps; k++) {
            double share = (double) k / substeps;
            Pose robot = new Pose(
                    robotBefore.xInches() + (robotAfter.xInches() - robotBefore.xInches()) * share,
                    robotBefore.yInches() + (robotAfter.yInches() - robotBefore.yInches()) * share,
                    robotBefore.headingRadians() + turn * share);
            subStep(last, robot, lengthInches / 2.0, widthInches / 2.0, h, walls);
            last = robot;
        }
        placeHeldBalls(robotAfter);
    }

    /**
     * One step in outside physics: the rules only, on the positions the outside supplied. The loose, flying and
     * CELL balls stay where they were supplied; only their states change. The HIVEs come last, so a ball supplied
     * in a CELL counts toward a tip in the same loop.
     */
    private void stepOutside(Pose robotBefore, Pose robot, double halfLength, double halfWidth, double h) {
        stepSpit(robot, h);
        stepFeed(robotBefore, robot, h);
        for (FlowerStack stack : stacks) {
            stepFlower(stack, robot, halfLength, halfWidth, h);
        }
        for (int i = 0; i < ids.length; i++) {
            if (GamePiece.CELL.equals(states[i])) {
                followCell(i);
                continue;
            }
            boolean loose = GamePiece.FIELD.equals(states[i]);
            if (loose && canTake(i) && touchesIntakeZone(i, robot)) {
                hold(i);
                continue;
            }
            if (!loose && !GamePiece.FLYING.equals(states[i])) {
                continue;
            }
            if (enterCell(i)) {
                continue;
            }
            if (!loose && z[i] <= radius[i] + LANDED_INCHES) {
                states[i] = GamePiece.FIELD;
            }
        }
        stepHives(h);
        placeHeldBalls(robot);
    }

    /**
     * In outside physics: if loose or flying ball {@code i}'s center is inside an upward CELL, while its HIVE
     * isn't tipping, it is now in that CELL. Returns true if so. (The lowered CELL catches nothing, as in Java's
     * own physics.)
     */
    private boolean enterCell(int i) {
        for (int k = 0; k < hives.size(); k++) {
            HiveState state = hiveStates[k];
            if (state.tipping) {
                continue;
            }
            double side = Math.signum(state.tiltDegrees);
            if (insideCell(i, k, side)) {
                double[] level = toLevel(k, x[i], z[i]);
                cellHive[i] = k;
                levelX[i] = level[0];
                levelY[i] = y[i] - hives.get(k).pivotYInches();
                levelZ[i] = level[1];
                states[i] = GamePiece.CELL;
                return true;
            }
        }
        return false;
    }

    /**
     * In outside physics: CELL ball {@code i} where the outside put it. Still inside its CELL, it stays there (its
     * place in the CELL updated, which decides the tip); outside it, it is flying again, or loose if it's down on
     * the tiles.
     */
    private void followCell(int i) {
        int k = cellHive[i];
        if (k < 0) {
            return;   // a CELL ball on a field without its HIVE stays as it is, as in Java's own physics
        }
        double side = Math.signum(levelX[i]);
        if (insideCell(i, k, side)) {
            double[] level = toLevel(k, x[i], z[i]);
            levelX[i] = level[0];
            levelY[i] = y[i] - hives.get(k).pivotYInches();
            levelZ[i] = level[1];
            return;
        }
        cellHive[i] = -1;
        states[i] = z[i] > radius[i] + LANDED_INCHES ? GamePiece.FLYING : GamePiece.FIELD;
    }

    /** True if ball {@code i}'s center is inside HIVE {@code k}'s CELL on {@code side} (+1: its +x side), at its tilt now. */
    private boolean insideCell(int i, int k, double side) {
        double[] level = toLevel(k, x[i], z[i]);
        double along = side * level[0];
        return along > BioBuzzHive.BACK_WALL_INCHES && along < BioBuzzHive.OPENING_INCHES
                && Math.abs(y[i] - hives.get(k).pivotYInches()) < BioBuzzHive.CELL_HALF_WIDTH_INCHES
                && level[1] > BioBuzzHive.FLOOR_INCHES && level[1] < BioBuzzHive.TOP_INCHES;
    }

    /** One small step: roll, get pushed by the robot, bounce off the walls, then off each other. */
    private void subStep(Pose robotBefore, Pose robot, double halfLength, double halfWidth, double h, boolean walls) {
        stepSpit(robot, h);
        stepFeed(robotBefore, robot, h);
        stepHives(h);
        for (FlowerStack stack : stacks) {
            stepFlower(stack, robot, halfLength, halfWidth, h);
        }
        double slowBy = model.rollingDeceleration().value() * h;
        for (int i = 0; i < ids.length; i++) {
            if (GamePiece.FLYING.equals(states[i])) {
                fly(i, robotBefore, robot, halfLength, halfWidth, h, walls);
                continue;
            }
            if (!GamePiece.FIELD.equals(states[i])) {
                continue;
            }
            if (canTake(i) && touchesIntakeZone(i, robot)) {
                hold(i);
                continue;
            }
            // Roll on, slowing down.
            x[i] += vx[i] * h;
            y[i] += vy[i] * h;
            double speed = Math.hypot(vx[i], vy[i]);
            double slower = speed - slowBy;
            if (slower < PieceModel.STOP_INCHES_PER_SECOND) {
                vx[i] = 0;
                vy[i] = 0;
            } else {
                vx[i] *= slower / speed;
                vy[i] *= slower / speed;
            }
            pushOutOfRobot(i, robotBefore, robot, halfLength, halfWidth, h);
            if (walls) {
                bounceOffWalls(i);
                for (PieceObstacle obstacle : obstacles) {
                    bounceOff(i, obstacle);
                }
                for (FlowerStack stack : stacks) {
                    if (stack.seat != null) {
                        bounceOff(i, stack.seat);
                    }
                }
            }
        }
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                if (GamePiece.FIELD.equals(states[i]) && GamePiece.FIELD.equals(states[j])) {
                    collide(i, j);
                }
            }
        }
        // A flying ball meets every other ball that isn't held: the loose ones on the tiles, those up in a FLOWER
        // or a CELL (which stay put), and other flying ones (W-PG).
        for (int i = 0; i < ids.length; i++) {
            if (!GamePiece.FLYING.equals(states[i])) {
                continue;
            }
            for (int j = 0; j < ids.length; j++) {
                boolean otherFlying = GamePiece.FLYING.equals(states[j]);
                if (j == i || (otherFlying && j < i)) {
                    continue;   // each flying pair once
                }
                if (otherFlying || GamePiece.FIELD.equals(states[j]) || GamePiece.FLOWER.equals(states[j])
                        || GamePiece.CELL.equals(states[j])) {
                    collideInFlight(i, j);
                }
            }
        }
    }

    /**
     * Flying ball {@code i} against ball {@code j}, in three dimensions: if they overlap, they are pushed apart
     * and bounce off each other with the balls' bounce ({@link PieceModel#pieceBounce}), shared by their masses.
     * A ball in a FLOWER or a CELL doesn't move (as if it were fixed); a loose ball on the tiles takes only the
     * push along the tiles and stays on them.
     */
    private void collideInFlight(int i, int j) {
        double dx = x[j] - x[i];
        double dy = y[j] - y[i];
        double dz = z[j] - z[i];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double touching = radius[i] + radius[j];
        if (distance >= touching) {
            return;
        }
        double nx = distance > 0 ? dx / distance : 0.0;
        double ny = distance > 0 ? dy / distance : 0.0;
        double nz = distance > 0 ? dz / distance : -1.0;
        boolean jFlies = GamePiece.FLYING.equals(states[j]);
        boolean jRolls = GamePiece.FIELD.equals(states[j]);
        double inverseI = 1.0 / mass[i];
        double inverseJ = jFlies || jRolls ? 1.0 / mass[j] : 0.0;
        double overlap = touching - distance;
        double shareI = jFlies ? inverseI / (inverseI + inverseJ) : 1.0;   // only flying balls are moved apart
        x[i] -= nx * overlap * shareI;
        y[i] -= ny * overlap * shareI;
        z[i] -= nz * overlap * shareI;
        if (jFlies) {
            x[j] += nx * overlap * (1 - shareI);
            y[j] += ny * overlap * (1 - shareI);
            z[j] += nz * overlap * (1 - shareI);
        }
        double vzj = jFlies ? vz[j] : 0;
        double closing = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny + (vzj - vz[i]) * nz;
        if (closing < 0) {
            double impulse = -(1 + model.pieceBounce().value()) * closing / (inverseI + inverseJ);
            vx[i] -= impulse * inverseI * nx;
            vy[i] -= impulse * inverseI * ny;
            vz[i] -= impulse * inverseI * nz;
            vx[j] += impulse * inverseJ * nx;
            vy[j] += impulse * inverseJ * ny;
            if (jFlies) {
                vz[j] += impulse * inverseJ * nz;
            }
        }
    }

    /**
     * If flying ball {@code i} overlaps an upright obstacle (a FLOWER's head or bracket, a foot of the frame)
     * between its bottom and top, moves it out to just touch it and bounces it off as the walls do. One with no
     * top is for rolling balls only.
     */
    private void bounceOffUpright(int i, PieceObstacle o) {
        if (Double.isInfinite(o.topInches())) {
            return;
        }
        if (!o.cylinder()) {
            bounceOffBox(i, (o.minXInches() + o.maxXInches()) / 2, (o.minYInches() + o.maxYInches()) / 2,
                    (o.bottomInches() + o.topInches()) / 2, (o.maxXInches() - o.minXInches()) / 2,
                    (o.maxYInches() - o.minYInches()) / 2, (o.topInches() - o.bottomInches()) / 2, 0);
            return;
        }
        double r = radius[i];
        double dx = x[i] - o.minXInches();
        double dy = y[i] - o.minYInches();
        double out = Math.hypot(dx, dy);
        double nearOut = Math.min(out, o.maxXInches());
        double nearX = o.minXInches() + (out > 0 ? dx / out * nearOut : 0);
        double nearY = o.minYInches() + (out > 0 ? dy / out * nearOut : 0);
        double nearZ = clamp(z[i], o.bottomInches(), o.topInches());
        double gx = x[i] - nearX;
        double gy = y[i] - nearY;
        double gz = z[i] - nearZ;
        double gap = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gap >= r) {
            return;
        }
        double nx;
        double ny;
        double nz;
        if (gap > 0) {
            nx = gx / gap;
            ny = gy / gap;
            nz = gz / gap;
        } else {
            // The center is inside the cylinder: out through its side.
            nx = out > 0 ? dx / out : 1;
            ny = out > 0 ? dy / out : 0;
            nz = 0;
            nearX = o.minXInches() + nx * o.maxXInches();
            nearY = o.minYInches() + ny * o.maxXInches();
        }
        x[i] = nearX + nx * r;
        y[i] = nearY + ny * r;
        z[i] = nearZ + nz * r;
        double into = vx[i] * nx + vy[i] * ny + vz[i] * nz;
        if (into < 0) {
            double change = (1 + model.wallBounce().value()) * into;
            vx[i] -= change * nx;
            vy[i] -= change * ny;
            vz[i] -= change * nz;
        }
    }

    /**
     * One small step of a FLOWER: its balls fall onto each other, then a robot's intake may take the bottom one out
     * through the Retrieval Opening ({@link #takesThroughOpening}). A robot with no intake takes nothing: driving
     * into a FLOWER doesn't remove a ball (G418; manager decision D38).
     */
    private void stepFlower(FlowerStack stack, Pose robot, double halfLength, double halfWidth, double h) {
        double restingOn = 0;
        boolean bottomResting = false;
        for (int k = 0; k < stack.balls.size(); k++) {
            int ball = stack.balls.get(k);
            double target = restingOn + radius[ball];
            if (z[ball] > target) {
                fallSpeed[ball] += PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED * h;
                z[ball] = Math.max(target, z[ball] - fallSpeed[ball] * h);
            }
            if (z[ball] <= target) {
                fallSpeed[ball] = 0;
                bottomResting = bottomResting || k == 0;
            }
            restingOn = target + radius[ball];
        }
        if (stack.balls.isEmpty()) {
            stack.seat = null;
            return;
        }
        int bottom = stack.balls.get(0);
        stack.seat = bottomResting
                ? PieceObstacle.cylinder("flower-" + stack.spot.name() + "-seat", x[bottom], y[bottom], radius[bottom], 0)
                : null;
        if (bottomResting && canTake(bottom) && takesThroughOpening(stack, bottom, robot)) {
            stack.balls.remove(0);
            stack.seat = null;
            hold(bottom);
        }
    }

    /**
     * True if the robot's intake can take FLOWER ball {@code i} out through the FLOWER's Retrieval Opening (TU06
     * 9.7): the ball fits through the opening (POLLEN does, NECTAR doesn't, which is why G418 lets only POLLEN out of
     * the bottom), the intake's zone touches the ball, and the ball would come out through an open face of the
     * opening: toward the zone's middle, which must be off the wall from the ball or level with it (the opening is
     * open at the front and both sides, closed only at its back, on the wall side).
     * The harness's intake zone has no height, so every intake is taken to reach down to the tiles, where the
     * opening is (D38; a placeholder until a robot's intake height is known).
     */
    private boolean takesThroughOpening(FlowerStack stack, int i, Pose robot) {
        if (2 * radius[i] >= FlowerSpot.RETRIEVAL_OPENING_HEIGHT.value()) {
            return false;
        }
        MechanismSetup.Intake intake = mechanism.intake();
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        if (!touchesIntakeZone(i, robot)) {
            return false;
        }
        // The zone's middle on the field: the ball is pulled toward it, so it mustn't be behind the ball (toward the wall).
        double middleX = robot.xInches() + intake.forwardInches().value() * cos - intake.leftInches().value() * sin;
        double middleY = robot.yInches() + intake.forwardInches().value() * sin + intake.leftInches().value() * cos;
        double out = (middleX - x[i]) * stack.spot.openXInches() + (middleY - y[i]) * stack.spot.openYInches();
        return out >= 0;
    }

    /** True if the robot's intake is running inward and has room for ball {@code i}, of a kind it takes. */
    private boolean canTake(int i) {
        if (mechanism == null || mechanism.intake() == null) {
            return false;
        }
        return intakePower > 0 && intakePower >= mechanism.intake().minPower().value()
                && held.size() < mechanism.hold().capacity().value() && mechanism.hold().accepts().contains(kinds[i]);
    }

    /** True if ball {@code i} touches the intake's zone. */
    private boolean touchesIntakeZone(int i, Pose robot) {
        MechanismSetup.Intake intake = mechanism.intake();
        return distanceToBox(i, robot, intake.forwardInches().value(), intake.leftInches().value(),
                intake.lengthInches().value() / 2.0, intake.widthInches().value() / 2.0) <= radius[i];
    }

    /** Ball {@code i} joins the queue, newest last. {@link #placeHeldBalls} then puts it in its place. */
    private void hold(int i) {
        states[i] = GamePiece.HELD;
        vx[i] = 0;
        vy[i] = 0;
        held.add(i);
    }

    /**
     * While the feed runs inward, pushes the oldest held ball toward the launcher wheel. After
     * {@code feed.secondsPerBall} it leaves, if the wheel would throw it at least
     * {@code launcher.minBallInchesPerSecond}; otherwise it waits against the wheel. Stopping the feed keeps
     * the ball where it got to; running it backward pulls it back.
     */
    private void stepFeed(Pose robotBefore, Pose robot, double h) {
        if (mechanism == null || mechanism.launcher() == null || held.isEmpty()) {
            feedSeconds = 0;
            return;
        }
        MechanismSetup.Launcher launcher = mechanism.launcher();
        double minPower = launcher.feedMinPower().value();
        if (feedPower < 0 && -feedPower >= minPower) {
            feedSeconds = 0;
            return;
        }
        if (feedPower > 0 && feedPower >= minPower) {
            feedSeconds = Math.min(feedSeconds + h, launcher.feedSecondsPerBall().value());
        }
        if (feedSeconds < launcher.feedSecondsPerBall().value() || launcherBallSpeed <= 0
                || launcherBallSpeed < launcher.minBallSpeed().value()) {
            return;
        }
        launch(held.remove(0), robotBefore, robot, h);
        feedSeconds = 0;
        shots++;
    }

    /**
     * Ball {@code i} leaves from {@code launch.*} on the robot, at the launcher's ball speed in the
     * launch direction ({@code launch.pitchDegrees} up, {@code launch.yawDegrees} left), plus the robot's
     * own speed. No spread: the same shot always goes the same way.
     */
    private void launch(int i, Pose robotBefore, Pose robot, double h) {
        MechanismSetup.Launcher launcher = mechanism.launcher();
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double forward = launcher.launchForwardInches().value();
        double left = launcher.launchLeftInches().value();
        x[i] = robot.xInches() + forward * cos - left * sin;
        y[i] = robot.yInches() + forward * sin + left * cos;
        z[i] = launcher.launchUpInches().value();
        double pitch = Math.toRadians(launcher.pitchDegrees().value());
        double yaw = Math.toRadians(launcher.yawDegrees().value());
        double along = launcherBallSpeed * Math.cos(pitch);
        double aimForward = along * Math.cos(yaw);
        double aimLeft = along * Math.sin(yaw);
        vx[i] = aimForward * cos - aimLeft * sin + (robot.xInches() - robotBefore.xInches()) / h;
        vy[i] = aimForward * sin + aimLeft * cos + (robot.yInches() - robotBefore.yInches()) / h;
        vz[i] = launcherBallSpeed * Math.sin(pitch);
        states[i] = GamePiece.FLYING;
        leavingRobot[i] = true;
        launched(i);
    }

    /**
     * One small step of a flying ball: gravity, air drag ({@link PieceModel#dragPerInch}), the robot's box (see
     * {@link #bounceOffRobot}), the walls, which it meets at any height, the HIVE and the FLOWERs. (Other balls:
     * see {@link #collideInFlight}.) Once it's down to the tiles it's a
     * loose ball again, keeping its speed along them.
     */
    private void fly(int i, Pose robotBefore, Pose robot, double halfLength, double halfWidth, double h, boolean walls) {
        vz[i] -= PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED * h;
        // Air drag slows it by dragPerInch x speed squared, against the way it's going (never past standing still).
        double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
        double keep = Math.max(0, 1 - model.dragPerInch(kinds[i]) * speed * h);
        vx[i] *= keep;
        vy[i] *= keep;
        vz[i] *= keep;
        x[i] += vx[i] * h;
        y[i] += vy[i] * h;
        z[i] += vz[i] * h;
        bounceOffRobot(i, robotBefore, robot, halfLength, halfWidth, h);
        if (walls) {
            bounceOffWalls(i);
            for (BioBuzzHive.Box box : frame) {
                bounceOffBox(i, box.xInches(), box.yInches(), box.zInches(), box.length() / 2, box.width() / 2,
                        box.height() / 2, box.tiltDegrees());
            }
            if (!hives.isEmpty()) {
                for (BioBuzzHive.Rod leg : BioBuzzHive.legs()) {
                    bounceOffRod(i, leg);
                }
            }
            // The FLOWERs and the frame's feet, between their bottoms and tops (W-PG).
            for (PieceObstacle obstacle : obstacles) {
                bounceOffUpright(i, obstacle);
            }
            for (int k = 0; k < hives.size() && GamePiece.FLYING.equals(states[i]); k++) {
                BioBuzzHive.Hive hive = hives.get(k);
                for (double side : new double[] {1, -1}) {
                    double[] arm = toField(k, side * BioBuzzHive.ARM_CENTER_INCHES, BioBuzzHive.ARM_CENTER_W_INCHES);
                    bounceOffBox(i, arm[0], hive.pivotYInches(), arm[1], BioBuzzHive.ARM_HALF_LENGTH_INCHES,
                            BioBuzzHive.ARM_HALF_WIDTH_INCHES, BioBuzzHive.ARM_HALF_THICKNESS_INCHES, hiveStates[k].tiltDegrees);
                }
                meetCells(i, k);
            }
            if (!GamePiece.FLYING.equals(states[i])) {
                return;   // it came to rest in a CELL
            }
        }
        if (z[i] <= radius[i]) {
            z[i] = radius[i];
            vz[i] = 0;
            states[i] = GamePiece.FIELD;
        }
    }

    /** HIVE {@code k}'s level frame at its tilt now: a field point's x and z, as level x and z. */
    private double[] toLevel(int k, double fieldX, double fieldZ) {
        BioBuzzHive.Hive hive = hives.get(k);
        double tilt = Math.toRadians(hiveStates[k].tiltDegrees);
        double dx = fieldX - hive.pivotXInches();
        double dz = fieldZ - hive.pivotZInches();
        return new double[] {dx * Math.cos(tilt) + dz * Math.sin(tilt), -dx * Math.sin(tilt) + dz * Math.cos(tilt)};
    }

    /** Back from HIVE {@code k}'s level frame: a level x and z, as the field's x and z. */
    private double[] toField(int k, double lx, double lz) {
        BioBuzzHive.Hive hive = hives.get(k);
        double tilt = Math.toRadians(hiveStates[k].tiltDegrees);
        return new double[] {hive.pivotXInches() + lx * Math.cos(tilt) - lz * Math.sin(tilt),
                hive.pivotZInches() + lx * Math.sin(tilt) + lz * Math.cos(tilt)};
    }

    /**
     * One small step of the HIVEs: a tip in progress goes on (easing in and out, as VI Arena draws it), a
     * HIVE whose upward CELL holds {@link BioBuzzHive#TIP_MASS} starts to tip, the balls in a HIVE that isn't
     * tipping roll down its upward CELL's floor ({@link #rollInCell}), and every CELL ball moves with its HIVE.
     * When a tip ends, the balls in the CELL that went down spill out.
     */
    private void stepHives(double h) {
        for (int k = 0; k < hives.size(); k++) {
            HiveState hive = hiveStates[k];
            if (hive.tipping) {
                hive.tipSeconds += h;
                double done = Math.min(1, hive.tipSeconds / BioBuzzHive.TIP_SECONDS.value());
                double eased = done * done * (3 - 2 * done);
                hive.tiltDegrees = hive.fromDegrees + (-hive.fromDegrees - hive.fromDegrees) * eased;
                if (done >= 1) {
                    hive.tipping = false;
                    hive.tiltDegrees = -hive.fromDegrees;
                    if (!outside) {
                        spill(k, -Math.signum(hive.tiltDegrees));   // in outside physics the balls fall out by themselves
                    }
                }
            } else if (massInCell(k, Math.signum(hive.tiltDegrees)) >= BioBuzzHive.TIP_MASS.value()) {
                hive.tipping = true;
                hive.tips++;
                hive.fromDegrees = hive.tiltDegrees;
                hive.tipSeconds = 0;
            }
            if (!hive.tipping && !outside) {
                rollInCell(k, Math.signum(hive.tiltDegrees), h);
            }
            for (int i = 0; i < ids.length && !outside; i++) {
                if (cellHive[i] == k) {
                    double[] field = toField(k, levelX[i], levelZ[i]);
                    x[i] = field[0];
                    y[i] = hives.get(k).pivotYInches() + levelY[i];
                    z[i] = field[1];
                }
            }
        }
    }

    /**
     * The balls in HIVE {@code k}'s upward CELL, on {@code side}, roll down its tilted floor toward the back wall,
     * speeding up with the part of gravity along the floor, until each reaches the back wall or touches a ball
     * nearer the back wall, where it stops dead. Each ball keeps its place across the CELL; only its place along
     * the arm changes. The balls nearest the back wall go first, so the ones behind stop against where they are
     * now.
     *
     * <p>A ball that landed overlapping a ball nearer the back wall is first moved across the CELL, away from it,
     * until it's clear of it, as if it slid off it down the slope; if the CELL's side wall is in the way, it is moved
     * out along the arm instead, until they just touch. From there it rolls on as usual, so it can roll down beside
     * the other one ({@link #separateInCell}).</p>
     *
     * <p>Simplified: a ball slides without spinning (a rolling ball would speed up a little slower, which changes
     * only when it gets there, not where it ends), the floor has no friction, and balls stop without bouncing. The
     * lowered CELL never holds a ball (they spill when a tip ends), so nothing rolls there.</p>
     */
    private void rollInCell(int k, double side, double h) {
        double downhill = PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED
                * Math.sin(Math.toRadians(Math.abs(hiveStates[k].tiltDegrees)));
        List<Integer> balls = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            if (cellHive[i] == k && Math.signum(levelX[i]) == side) {
                balls.add(i);
            }
        }
        balls.sort((a, b) -> Double.compare(side * levelX[a], side * levelX[b]));
        for (int n = 0; n < balls.size(); n++) {
            int i = balls.get(n);
            for (int m = 0; m < n; m++) {
                separateInCell(i, balls.get(m), side);
            }
            double along = side * levelX[i];
            double stop = BioBuzzHive.BACK_WALL_INCHES + radius[i];
            for (int m = 0; m < n; m++) {
                int j = balls.get(m);
                double across = levelY[i] - levelY[j];
                double touching = radius[i] + radius[j];
                if (Math.abs(across) < touching) {
                    stop = Math.max(stop, side * levelX[j] + Math.sqrt(touching * touching - across * across));
                }
            }
            if (along - stop <= (cellSpeed[i] == 0 ? CELL_RESTING_INCHES : 0)) {   // a rolling ball goes all the way
                cellSpeed[i] = 0;
                continue;
            }
            cellSpeed[i] += downhill * h;
            double next = along - cellSpeed[i] * h;
            if (next <= stop) {
                next = stop;
                cellSpeed[i] = 0;
            }
            levelX[i] = side * next;
        }
    }

    /**
     * If CELL ball {@code i} overlaps CELL ball {@code j}, which is nearer the back wall, on the CELL's floor: moves
     * {@code i} across the CELL, away from {@code j} (toward the middle if they're level across), until it's clear
     * of {@code j}, but no further than the CELL's side wall; if that isn't far enough, moves {@code i} out along the
     * arm until they touch.
     */
    private void separateInCell(int i, int j, double side) {
        double along = side * levelX[i] - side * levelX[j];
        double across = levelY[i] - levelY[j];
        double touching = radius[i] + radius[j];
        if (Math.hypot(along, across) >= touching - CELL_RESTING_INCHES) {
            return;   // touching, or overlapping by less than the CAD's rounding (the staged NECTAR)
        }
        double away = across != 0 ? Math.signum(across) : (levelY[j] > 0 ? -1 : 1);
        double wall = BioBuzzHive.CELL_HALF_WIDTH_INCHES - radius[i];
        levelY[i] = clamp(levelY[j] + away * touching, -wall, wall);
        across = levelY[i] - levelY[j];
        if (Math.hypot(along, across) < touching - 1e-9) {
            levelX[i] = side * (side * levelX[j] + Math.sqrt(touching * touching - across * across));
        }
    }

    /** The weight in grams of the balls in HIVE {@code k}'s CELL on {@code side} (+1: its +x side). */
    private double massInCell(int k, double side) {
        double grams = 0;
        for (int i = 0; i < ids.length; i++) {
            if (cellHive[i] == k && Math.signum(levelX[i]) == side) {
                grams += mass[i];
            }
        }
        return grams;
    }

    /**
     * The balls in HIVE {@code k}'s CELL on {@code side}, which now faces down, leave through its opening:
     * each is put just outside it and flies off along the way the opening faces, at
     * {@link BioBuzzHive#SPILL_SPEED}.
     */
    private void spill(int k, double side) {
        double tilt = Math.toRadians(hiveStates[k].tiltDegrees);
        for (int i = 0; i < ids.length; i++) {
            if (cellHive[i] != k || Math.signum(levelX[i]) != side) {
                continue;
            }
            cellHive[i] = -1;
            cellSpeed[i] = 0;
            double[] field = toField(k, side * (BioBuzzHive.OPENING_INCHES + radius[i] + 0.05), levelZ[i]);
            x[i] = field[0];
            z[i] = field[1];
            double speed = BioBuzzHive.SPILL_SPEED.value();
            vx[i] = side * Math.cos(tilt) * speed;
            vy[i] = 0;
            vz[i] = side * Math.sin(tilt) * speed;
            states[i] = GamePiece.FLYING;
        }
    }

    /**
     * Flying ball {@code i} against HIVE {@code k}'s two CELLs. One that comes in through the opening of the
     * upward CELL, while the HIVE isn't tipping, comes to rest on its floor where it came in. Any other
     * CELL, and the outside of every CELL, is solid: its back wall, top and sides, all thin, and its floor, 1.5 in
     * thick ({@link BioBuzzHive#FLOOR_UNDERSIDE_INCHES}).
     */
    private void meetCells(int i, int k) {
        BioBuzzHive.Hive hive = hives.get(k);
        double ly = y[i] - hive.pivotYInches();
        double r = radius[i];
        if (Math.abs(ly) > BioBuzzHive.CELL_HALF_WIDTH_INCHES + r) {
            return;
        }
        HiveState state = hiveStates[k];
        for (double side : new double[] {1, -1}) {
            double[] level = toLevel(k, x[i], z[i]);
            ly = y[i] - hive.pivotYInches();
            double along = side * level[0];
            boolean inside = along > BioBuzzHive.BACK_WALL_INCHES && along < BioBuzzHive.OPENING_INCHES
                    && Math.abs(ly) < BioBuzzHive.CELL_HALF_WIDTH_INCHES
                    && level[1] > BioBuzzHive.FLOOR_INCHES && level[1] < BioBuzzHive.TOP_INCHES;
            boolean open = !state.tipping && side == Math.signum(state.tiltDegrees);
            if (inside && open) {
                rest(i, k, side, level[0], ly);
                return;
            }
            // The CELL as a box, with its outer face open unless it can't take a ball now.
            double cx = side * (BioBuzzHive.BACK_WALL_INCHES + BioBuzzHive.OPENING_INCHES) / 2;
            double cz = (BioBuzzHive.FLOOR_INCHES + BioBuzzHive.TOP_INCHES) / 2;
            double[] fieldCenter = toField(k, cx, cz);
            bounceOffBox(i, fieldCenter[0], hive.pivotYInches(), fieldCenter[1], 6, BioBuzzHive.CELL_HALF_WIDTH_INCHES,
                    7, state.tiltDegrees, open ? side : 0);
            // The floor's solid thickness under it, from its underside to the inner floor (D31), closed all round.
            double[] slab = toField(k, cx, (BioBuzzHive.FLOOR_UNDERSIDE_INCHES + BioBuzzHive.FLOOR_INCHES) / 2);
            bounceOffBox(i, slab[0], hive.pivotYInches(), slab[1], 6, BioBuzzHive.CELL_HALF_WIDTH_INCHES,
                    (BioBuzzHive.FLOOR_INCHES - BioBuzzHive.FLOOR_UNDERSIDE_INCHES) / 2, state.tiltDegrees);
        }
    }

    /** Flying ball {@code i} comes to rest on the floor of HIVE {@code k}'s CELL on {@code side}, where it came in. */
    private void rest(int i, int k, double side, double lx, double ly) {
        double r = radius[i];
        double along = clamp(side * lx, BioBuzzHive.BACK_WALL_INCHES + r, BioBuzzHive.OPENING_INCHES - r);
        cellHive[i] = k;
        cellSpeed[i] = 0;
        levelX[i] = side * along;
        levelY[i] = clamp(ly, -BioBuzzHive.CELL_HALF_WIDTH_INCHES + r, BioBuzzHive.CELL_HALF_WIDTH_INCHES - r);
        levelZ[i] = BioBuzzHive.FLOOR_INCHES + r;
        double[] field = toField(k, levelX[i], levelZ[i]);
        x[i] = field[0];
        y[i] = hives.get(k).pivotYInches() + levelY[i];
        z[i] = field[1];
        vx[i] = 0;
        vy[i] = 0;
        vz[i] = 0;
        states[i] = GamePiece.CELL;
    }

    /** If flying ball {@code i} overlaps a rod (a leg of the frame), moves it out to just touch and bounces it off as the walls do. */
    private void bounceOffRod(int i, BioBuzzHive.Rod rod) {
        double ax = rod.bottomX();
        double ay = rod.bottomY();
        double az = rod.bottomZ();
        double ux = rod.topX() - ax;
        double uy = rod.topY() - ay;
        double uz = rod.topZ() - az;
        double lengthSquared = ux * ux + uy * uy + uz * uz;
        double along = clamp(((x[i] - ax) * ux + (y[i] - ay) * uy + (z[i] - az) * uz) / lengthSquared, 0, 1);
        double gx = x[i] - (ax + ux * along);
        double gy = y[i] - (ay + uy * along);
        double gz = z[i] - (az + uz * along);
        double gap = Math.sqrt(gx * gx + gy * gy + gz * gz);
        double touching = rod.radiusInches() + radius[i];
        if (gap >= touching || gap == 0) {
            return;
        }
        double nx = gx / gap;
        double ny = gy / gap;
        double nz = gz / gap;
        x[i] += nx * (touching - gap);
        y[i] += ny * (touching - gap);
        z[i] += nz * (touching - gap);
        double into = vx[i] * nx + vy[i] * ny + vz[i] * nz;
        if (into < 0) {
            double change = (1 + model.wallBounce().value()) * into;
            vx[i] -= change * nx;
            vy[i] -= change * ny;
            vz[i] -= change * nz;
        }
    }

    /**
     * If flying ball {@code i} overlaps the robot's box ({@link #ROBOT_HEIGHT} tall, standing on the tiles),
     * moves it out to just touch the box and bounces it off with the robot's bounce, as seen from the robot,
     * so a moving robot also knocks it. A ball the robot has just launched passes until it is out of the box.
     */
    private void bounceOffRobot(int i, Pose robotBefore, Pose robot, double halfLength, double halfWidth, double h) {
        double halfHeight = ROBOT_HEIGHT.value() / 2;
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double dx = x[i] - robot.xInches();
        double dy = y[i] - robot.yInches();
        // The ball in the robot's own frame: forward, left, and up from the middle of the box.
        double forward = dx * cos + dy * sin;
        double left = -dx * sin + dy * cos;
        double up = z[i] - halfHeight;
        double r = radius[i];
        double nearForward = clamp(forward, -halfLength, halfLength);
        double nearLeft = clamp(left, -halfWidth, halfWidth);
        double nearUp = clamp(up, -halfHeight, halfHeight);
        double gf = forward - nearForward;
        double gl = left - nearLeft;
        double gu = up - nearUp;
        double gap = Math.sqrt(gf * gf + gl * gl + gu * gu);
        if (gap >= r) {
            leavingRobot[i] = false;
            return;
        }
        if (leavingRobot[i]) {
            return;   // still on its way out of the launcher
        }
        double nf;
        double nl;
        double nu;
        if (gap > 0) {
            nf = gf / gap;
            nl = gl / gap;
            nu = gu / gap;
        } else {
            // The center is inside the box: out through the nearest face (not the floor).
            double[] gaps = {halfLength - forward, forward + halfLength, halfWidth - left, left + halfWidth, halfHeight - up};
            int face = 0;
            for (int f = 1; f < gaps.length; f++) {
                if (gaps[f] < gaps[face]) {
                    face = f;
                }
            }
            nf = face == 0 ? 1 : face == 1 ? -1 : 0;
            nl = face == 2 ? 1 : face == 3 ? -1 : 0;
            nu = face == 4 ? 1 : 0;
            nearForward = face == 0 ? halfLength : face == 1 ? -halfLength : forward;
            nearLeft = face == 2 ? halfWidth : face == 3 ? -halfWidth : left;
            nearUp = face == 4 ? halfHeight : up;
        }
        double touchX = robot.xInches() + nearForward * cos - nearLeft * sin;
        double touchY = robot.yInches() + nearForward * sin + nearLeft * cos;
        double nx = nf * cos - nl * sin;
        double ny = nf * sin + nl * cos;
        x[i] = touchX + nx * r;
        y[i] = touchY + ny * r;
        z[i] = halfHeight + nearUp + nu * r;
        // How fast that point of the robot is moving: where it was a sub-step ago.
        double beforeCos = Math.cos(robotBefore.headingRadians());
        double beforeSin = Math.sin(robotBefore.headingRadians());
        double robotVx = (touchX - (robotBefore.xInches() + nearForward * beforeCos - nearLeft * beforeSin)) / h;
        double robotVy = (touchY - (robotBefore.yInches() + nearForward * beforeSin + nearLeft * beforeCos)) / h;
        double into = (vx[i] - robotVx) * nx + (vy[i] - robotVy) * ny + vz[i] * nu;
        if (into < 0) {
            double change = (1 + model.robotBounce().value()) * into;
            vx[i] -= change * nx;
            vy[i] -= change * ny;
            vz[i] -= change * nu;
        }
    }

    /** {@link #bounceOffBox(int, double, double, double, double, double, double, double, double)} with no open face. */
    private void bounceOffBox(int i, double cx, double cy, double cz, double halfLength, double halfWidth,
                              double halfHeight, double tiltDegrees) {
        bounceOffBox(i, cx, cy, cz, halfLength, halfWidth, halfHeight, tiltDegrees, 0);
    }

    /**
     * If flying ball {@code i} overlaps a box turned about y by {@code tiltDegrees}, moves it out to just
     * touch the box and bounces it off as the walls do. With {@code openSide} +1 or -1, the box's face on
     * that end of its own x has a hole the size of the face: a ball whose nearest point is in that face
     * passes.
     */
    private void bounceOffBox(int i, double cx, double cy, double cz, double halfLength, double halfWidth,
                              double halfHeight, double tiltDegrees, double openSide) {
        double tilt = Math.toRadians(tiltDegrees);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        double dx = x[i] - cx;
        double dy = y[i] - cy;
        double dz = z[i] - cz;
        // The ball in the box's own frame.
        double bx = dx * cos + dz * sin;
        double bz = -dx * sin + dz * cos;
        double r = radius[i];
        double nearX = clamp(bx, -halfLength, halfLength);
        double nearY = clamp(dy, -halfWidth, halfWidth);
        double nearZ = clamp(bz, -halfHeight, halfHeight);
        double gx = bx - nearX;
        double gy = dy - nearY;
        double gz = bz - nearZ;
        double gap = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gap >= r) {
            return;
        }
        if (openSide != 0 && gx * openSide > 0 && Math.abs(dy) < halfWidth && Math.abs(bz) < halfHeight) {
            return;   // in front of the open face
        }
        double nx;
        double ny;
        double nz;
        if (gap > 0) {
            nx = gx / gap;
            ny = gy / gap;
            nz = gz / gap;
        } else {
            // The center is inside the box: out through the nearest face.
            double[] gaps = {halfLength - bx, bx + halfLength, halfWidth - dy, dy + halfWidth, halfHeight - bz, bz + halfHeight};
            int face = 0;
            for (int f = 1; f < 6; f++) {
                if (gaps[f] < gaps[face]) {
                    face = f;
                }
            }
            nx = face == 0 ? 1 : face == 1 ? -1 : 0;
            ny = face == 2 ? 1 : face == 3 ? -1 : 0;
            nz = face == 4 ? 1 : face == 5 ? -1 : 0;
            nearX = face == 0 ? halfLength : face == 1 ? -halfLength : bx;
            nearY = face == 2 ? halfWidth : face == 3 ? -halfWidth : dy;
            nearZ = face == 4 ? halfHeight : face == 5 ? -halfHeight : bz;
        }
        double px = nearX + nx * r;
        double py = nearY + ny * r;
        double pz = nearZ + nz * r;
        x[i] = cx + px * cos - pz * sin;
        y[i] = cy + py;
        z[i] = cz + px * sin + pz * cos;
        // The bounce: the speed into the box reverses, shrunk by the wall bounce.
        double fnx = nx * cos - nz * sin;
        double fnz = nx * sin + nz * cos;
        double into = vx[i] * fnx + vy[i] * ny + vz[i] * fnz;
        if (into < 0) {
            double change = (1 + model.wallBounce().value()) * into;
            vx[i] -= change * fnx;
            vy[i] -= change * ny;
            vz[i] -= change * fnz;
        }
    }

    /** While the intake runs in reverse, spits the newest ball every {@code intake.spitSeconds}. */
    private void stepSpit(Pose robot, double h) {
        if (mechanism == null || mechanism.intake() == null || held.isEmpty() || intakePower >= 0
                || -intakePower < mechanism.intake().minPower().value()) {
            spitSeconds = 0;
            return;
        }
        spitSeconds += h;
        if (spitSeconds < mechanism.intake().spitSeconds().value()) {
            return;
        }
        spitSeconds = 0;
        spit(held.remove(held.size() - 1), robot);
    }

    /** Ball {@code i} leaves from the middle of the intake's zone, loose, going straight out from the robot's center. */
    private void spit(int i, Pose robot) {
        MechanismSetup.Intake intake = mechanism.intake();
        double forward = intake.forwardInches().value();
        double left = intake.leftInches().value();
        double distance = Math.hypot(forward, left);
        double outForward = distance > 0 ? forward / distance : 1.0;
        double outLeft = distance > 0 ? left / distance : 0.0;
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        x[i] = robot.xInches() + forward * cos - left * sin;
        y[i] = robot.yInches() + forward * sin + left * cos;
        z[i] = radius[i];
        vx[i] = (outForward * cos - outLeft * sin) * intake.spitSpeed().value();
        vy[i] = (outForward * sin + outLeft * cos) * intake.spitSpeed().value();
        vz[i] = 0;
        states[i] = GamePiece.FIELD;
        launched(i);
    }

    /**
     * Puts each held ball in its place on the robot: evenly along the line from where a ball leaves (the
     * launcher, or above the robot's center) to the intake (or the robot's center), oldest nearest the launcher.
     */
    private void placeHeldBalls(Pose robot) {
        if (held.isEmpty()) {
            return;
        }
        MechanismSetup.Launcher launcher = mechanism.launcher();
        MechanismSetup.Intake intake = mechanism.intake();
        double fromForward = launcher == null ? 0 : launcher.launchForwardInches().value();
        double fromLeft = launcher == null ? 0 : launcher.launchLeftInches().value();
        double fromUp = launcher == null ? HELD_HEIGHT_WITH_NO_LAUNCHER_INCHES : launcher.launchUpInches().value();
        double toForward = intake == null ? 0 : intake.forwardInches().value();
        double toLeft = intake == null ? 0 : intake.leftInches().value();
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double slots = mechanism.hold().capacity().value();
        for (int k = 0; k < held.size(); k++) {
            int ball = held.get(k);
            double share = (k + 0.5) / slots;
            double forward = fromForward + (toForward - fromForward) * share;
            double left = fromLeft + (toLeft - fromLeft) * share;
            x[ball] = robot.xInches() + forward * cos - left * sin;
            y[ball] = robot.yInches() + forward * sin + left * cos;
            z[ball] = fromUp + (radius[ball] - fromUp) * share;
        }
    }

    /** How far ball {@code i}'s center is from a box in the robot's frame (0 if it's inside the box). */
    private double distanceToBox(int i, Pose robot, double centerForward, double centerLeft, double halfLength,
                                 double halfWidth) {
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double dx = x[i] - robot.xInches();
        double dy = y[i] - robot.yInches();
        double forward = dx * cos + dy * sin - centerForward;
        double left = -dx * sin + dy * cos - centerLeft;
        return Math.hypot(forward - clamp(forward, -halfLength, halfLength), left - clamp(left, -halfWidth, halfWidth));
    }

    /**
     * If ball {@code i} overlaps the robot box, moves it out to just touch the box and gives it at
     * least the speed of the bit of robot that hit it (plus a little bounce).
     */
    private void pushOutOfRobot(int i, Pose robotBefore, Pose robot, double halfLength, double halfWidth, double h) {
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double dx = x[i] - robot.xInches();
        double dy = y[i] - robot.yInches();
        // The ball center in the robot's own frame: forward and left of the robot center.
        double forward = dx * cos + dy * sin;
        double left = -dx * sin + dy * cos;
        double r = radius[i];
        double nearForward = clamp(forward, -halfLength, halfLength);
        double nearLeft = clamp(left, -halfWidth, halfWidth);
        double normalForward;
        double normalLeft;
        boolean inside = forward == nearForward && left == nearLeft;
        if (inside) {
            // The center is inside the box: leave through the nearest side.
            double[] gaps = {halfLength - forward, forward + halfLength, halfWidth - left, left + halfWidth};
            int side = 0;
            for (int s = 1; s < 4; s++) {
                if (gaps[s] < gaps[side]) {
                    side = s;
                }
            }
            normalForward = side == 0 ? 1 : side == 1 ? -1 : 0;
            normalLeft = side == 2 ? 1 : side == 3 ? -1 : 0;
            nearForward = side == 0 ? halfLength : side == 1 ? -halfLength : forward;
            nearLeft = side == 2 ? halfWidth : side == 3 ? -halfWidth : left;
        } else {
            double gapForward = forward - nearForward;
            double gapLeft = left - nearLeft;
            double gap = Math.hypot(gapForward, gapLeft);
            if (gap >= r) {
                return; // not touching
            }
            normalForward = gapForward / gap;
            normalLeft = gapLeft / gap;
        }
        // Back to the field frame: the touching point on the box and the push direction.
        double nx = normalForward * cos - normalLeft * sin;
        double ny = normalForward * sin + normalLeft * cos;
        double touchX = robot.xInches() + nearForward * cos - nearLeft * sin;
        double touchY = robot.yInches() + nearForward * sin + nearLeft * cos;
        x[i] = touchX + nx * r;
        y[i] = touchY + ny * r;

        // How fast that point of the robot is moving: where it was a sub-step ago.
        double beforeCos = Math.cos(robotBefore.headingRadians());
        double beforeSin = Math.sin(robotBefore.headingRadians());
        double wasX = robotBefore.xInches() + nearForward * beforeCos - nearLeft * beforeSin;
        double wasY = robotBefore.yInches() + nearForward * beforeSin + nearLeft * beforeCos;
        double robotSpeedIn = ((touchX - wasX) * nx + (touchY - wasY) * ny) / h;
        double ballSpeedIn = vx[i] * nx + vy[i] * ny;
        if (ballSpeedIn < robotSpeedIn) {
            double newSpeed = robotSpeedIn + model.robotBounce().value() * (robotSpeedIn - ballSpeedIn);
            vx[i] += (newSpeed - ballSpeedIn) * nx;
            vy[i] += (newSpeed - ballSpeedIn) * ny;
        }
    }

    /** Keeps ball {@code i} inside the field walls, bouncing it back off any wall it reaches. */
    private void bounceOffWalls(int i) {
        double limit = Pose.FIELD_HALF_WIDTH_INCHES - radius[i];
        double bounce = model.wallBounce().value();
        if (x[i] > limit || x[i] < -limit) {
            x[i] = clamp(x[i], -limit, limit);
            if (Math.signum(vx[i]) == Math.signum(x[i])) {
                vx[i] = -vx[i] * bounce;
            }
        }
        if (y[i] > limit || y[i] < -limit) {
            y[i] = clamp(y[i], -limit, limit);
            if (Math.signum(vy[i]) == Math.signum(y[i])) {
                vy[i] = -vy[i] * bounce;
            }
        }
    }

    /**
     * If ball {@code i} overlaps {@code obstacle} (and reaches down to it), moves it out to just
     * touch the obstacle and bounces it back the way the walls do: the speed into the obstacle
     * reverses, shrunk by the wall bounce; the speed along it is kept.
     */
    private void bounceOff(int i, PieceObstacle obstacle) {
        double r = radius[i];
        if (!obstacle.reachedBy(r)) {
            return; // the ball rolls underneath
        }
        double nx;
        double ny;
        if (obstacle.cylinder()) {
            double dx = x[i] - obstacle.minXInches();
            double dy = y[i] - obstacle.minYInches();
            double distance = Math.hypot(dx, dy);
            double touching = obstacle.maxXInches() + r;
            if (distance >= touching) {
                return;
            }
            nx = distance > 0 ? dx / distance : 1.0;
            ny = distance > 0 ? dy / distance : 0.0;
            x[i] = obstacle.minXInches() + nx * touching;
            y[i] = obstacle.minYInches() + ny * touching;
        } else {
            double nearX = clamp(x[i], obstacle.minXInches(), obstacle.maxXInches());
            double nearY = clamp(y[i], obstacle.minYInches(), obstacle.maxYInches());
            if (nearX == x[i] && nearY == y[i]) {
                // The center is inside the box: leave through the nearest side.
                double[] gaps = {obstacle.maxXInches() - x[i], x[i] - obstacle.minXInches(),
                        obstacle.maxYInches() - y[i], y[i] - obstacle.minYInches()};
                int side = 0;
                for (int s = 1; s < 4; s++) {
                    if (gaps[s] < gaps[side]) {
                        side = s;
                    }
                }
                nx = side == 0 ? 1 : side == 1 ? -1 : 0;
                ny = side == 2 ? 1 : side == 3 ? -1 : 0;
                nearX = side == 0 ? obstacle.maxXInches() : side == 1 ? obstacle.minXInches() : x[i];
                nearY = side == 2 ? obstacle.maxYInches() : side == 3 ? obstacle.minYInches() : y[i];
            } else {
                double gapX = x[i] - nearX;
                double gapY = y[i] - nearY;
                double gap = Math.hypot(gapX, gapY);
                if (gap >= r) {
                    return; // not touching
                }
                nx = gapX / gap;
                ny = gapY / gap;
            }
            x[i] = nearX + nx * r;
            y[i] = nearY + ny * r;
        }
        double into = vx[i] * nx + vy[i] * ny;
        if (into < 0) {
            double change = -(1 + model.wallBounce().value()) * into;
            vx[i] += change * nx;
            vy[i] += change * ny;
        }
    }

    /** Two loose balls that overlap are moved apart and bounce off each other (the lighter one moves more). */
    private void collide(int i, int j) {
        double dx = x[j] - x[i];
        double dy = y[j] - y[i];
        double distance = Math.hypot(dx, dy);
        double touching = radius[i] + radius[j];
        if (distance >= touching) {
            return;
        }
        double nx = distance > 0 ? dx / distance : 1.0;
        double ny = distance > 0 ? dy / distance : 0.0;
        double inverseI = 1.0 / mass[i];
        double inverseJ = 1.0 / mass[j];
        double overlap = touching - distance;
        double shareI = inverseI / (inverseI + inverseJ);
        x[i] -= nx * overlap * shareI;
        y[i] -= ny * overlap * shareI;
        x[j] += nx * overlap * (1 - shareI);
        y[j] += ny * overlap * (1 - shareI);
        double closing = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny;
        if (closing < 0) {
            double impulse = -(1 + model.pieceBounce().value()) * closing / (inverseI + inverseJ);
            vx[i] -= impulse * inverseI * nx;
            vy[i] -= impulse * inverseI * ny;
            vx[j] += impulse * inverseJ * nx;
            vy[j] += impulse * inverseJ * ny;
        }
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
