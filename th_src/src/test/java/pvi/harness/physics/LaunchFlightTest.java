package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A held ball fed into the spinning launcher wheel flies off under gravity and lands loose. The robot box
 * is moved by hand and the motors' effects are set directly, so these don't depend on any team's code. The
 * numbers are made up: this checks the rules, not a real robot.
 */
class LaunchFlightTest {
    private static final double LOOP = 0.02;
    /** Small steps for checking a flight against the textbook. */
    private static final double FINE = 0.002;
    private static final double SIZE = 18;
    private static final double SECONDS_PER_BALL = 0.3;
    private static final double FEED_MIN = 0.1;
    private static final double MIN_BALL_SPEED = 20;
    private static final double G = PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED;
    /** Where a ball leaves, in the robot's frame. */
    private static final double LAUNCH_FORWARD = -4;
    private static final double LAUNCH_LEFT = 1;
    private static final double LAUNCH_UP = 14;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static MechanismSetup setup(double pitchDegrees, double yawDegrees) {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(MIN_BALL_SPEED), "feed", Direction.FORWARD,
                guess(FEED_MIN), guess(SECONDS_PER_BALL), guess(LAUNCH_FORWARD), guess(LAUNCH_LEFT), guess(LAUNCH_UP),
                guess(pitchDegrees), guess(yawDegrees));
        return new MechanismSetup(null, new MechanismSetup.Hold(guess(4), List.of(GamePiece.POLLEN), guess(2)), launcher);
    }

    /**
     * The BIOBUZZ ball facts with no air: these tests check flights against the textbook's no-air path, so air drag
     * is off here (W-PG item 3; {@code AirDragTest} checks the drag).
     */
    private static PieceModel noAir() {
        PieceModel m = PieceModel.bioBuzz();
        return new PieceModel(m.rollingDeceleration(), m.wallBounce(), m.pieceBounce(), m.robotBounce(), m.pollenMass(),
                m.nectarMass(), ModelValue.placeholder(0, PieceModel.NO_UNIT, "no air, for the textbook checks"));
    }

    /** Two pre-loads held by a robot at {@code robot}, with this launch angle; the walls are far away for most tests. */
    private static GamePieces holding(Pose robot, double pitchDegrees, double yawDegrees, List<PieceObstacle> obstacles) {
        List<GamePiece> start = new ArrayList<>();
        start.add(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, -40, 1.4));
        start.add(new GamePiece("pollen-preload-2", GamePiece.POLLEN, GamePiece.FIELD, 3, -40, 1.4));
        GamePieces pieces = new GamePieces(start, noAir(), obstacles);
        pieces.useMechanism(setup(pitchDegrees, yawDegrees), robot);
        return pieces;
    }

    private static GamePieces holding(Pose robot) {
        return holding(robot, 45, 0, List.of());
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static void stay(GamePieces pieces, Pose robot, double seconds, double dt) {
        for (double t = 0; t < seconds - 1e-9; t += dt) {
            pieces.step(robot, robot, SIZE, SIZE, dt, true);
        }
    }

    /** Feeds at full power with this ball speed until the first ball is airborne; returns the ball. */
    private static void fire(GamePieces pieces, Pose robot, double ballSpeed, double dt) {
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(ballSpeed);
        for (int i = 0; i < 10_000 && piece(pieces, "pollen-preload-1").state().equals(GamePiece.HELD); i++) {
            pieces.step(robot, robot, SIZE, SIZE, dt, true);
        }
        pieces.feedPower(0);
    }

    /** Where a ball launched at {@code speed} from height LAUNCH_UP at this pitch lands, measured along the shot. */
    private static double textbookRange(double speed, double pitchDegrees) {
        double vUp = speed * Math.sin(Math.toRadians(pitchDegrees));
        double vAlong = speed * Math.cos(Math.toRadians(pitchDegrees));
        double t = (vUp + Math.sqrt(vUp * vUp + 2 * G * (LAUNCH_UP - 1.4))) / G;
        return vAlong * t;
    }

    /** Steps until the ball has landed; returns where it touched down (the first point it's loose). */
    private static GamePiece land(GamePieces pieces, Pose robot, String id) {
        for (int i = 0; i < 100_000; i++) {
            pieces.step(robot, robot, SIZE, SIZE, FINE, true);
            GamePiece p = piece(pieces, id);
            if (p.state().equals(GamePiece.FIELD)) {
                return p;
            }
        }
        throw new AssertionError("never landed");
    }

    /** WHAT: After the feed has run for feed.secondsPerBall, the oldest held ball flies and the other stays. */
    @Test
    void theOldestBallLeavesAfterTheFeedTime() {
        GamePieces pieces = holding(Pose.ORIGIN);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(200);

        stay(pieces, Pose.ORIGIN, SECONDS_PER_BALL - 2 * LOOP, LOOP);
        assertEquals(GamePiece.HELD, piece(pieces, "pollen-preload-1").state(), "not yet");
        stay(pieces, Pose.ORIGIN, 3 * LOOP, LOOP);
        assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-1").state());
        assertEquals(GamePiece.HELD, piece(pieces, "pollen-preload-2").state(), "one at a time");
        assertEquals(1, pieces.takeShots());
        assertEquals(0, pieces.takeShots(), "counted once");
    }

    /** WHAT: The next ball needs another feed.secondsPerBall. */
    @Test
    void theNextBallNeedsItsOwnFeedTime() {
        GamePieces pieces = holding(Pose.ORIGIN);
        fire(pieces, Pose.ORIGIN, 200, LOOP);
        pieces.feedPower(1.0);
        stay(pieces, Pose.ORIGIN, SECONDS_PER_BALL - 2 * LOOP, LOOP);
        assertEquals(GamePiece.HELD, piece(pieces, "pollen-preload-2").state());
        stay(pieces, Pose.ORIGIN, 3 * LOOP, LOOP);
        assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-2").state());
    }

    /** WHAT: Nothing leaves with the wheel stopped, backward or too slow, or with the feed off or too weak. */
    @Test
    void noShotWithoutAFastWheelAndAFeed() {
        double[][] cases = {{1.0, 0}, {1.0, -200}, {1.0, MIN_BALL_SPEED / 2}, {0, 200}, {FEED_MIN / 2, 200}};
        for (double[] c : cases) {
            GamePieces pieces = holding(Pose.ORIGIN);
            pieces.feedPower(c[0]);
            pieces.launcherBallSpeed(c[1]);
            stay(pieces, Pose.ORIGIN, 2.0, LOOP);
            assertEquals(GamePiece.HELD, piece(pieces, "pollen-preload-1").state(), "feed " + c[0] + ", ball speed " + c[1]);
        }
    }

    /** WHAT: The least ball speed and the least feed power are enough. */
    @Test
    void theLeastIsEnough() {
        GamePieces pieces = holding(Pose.ORIGIN);
        pieces.feedPower(FEED_MIN);
        pieces.launcherBallSpeed(MIN_BALL_SPEED);
        stay(pieces, Pose.ORIGIN, SECONDS_PER_BALL + 2 * LOOP, LOOP);
        assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-1").state());
    }

    /** WHAT: A ball fed against a still wheel waits there, and leaves as soon as the wheel is fast enough. */
    @Test
    void aBallWaitsForTheWheel() {
        GamePieces pieces = holding(Pose.ORIGIN);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(0);
        stay(pieces, Pose.ORIGIN, 1.0, LOOP);
        pieces.feedPower(0);
        pieces.launcherBallSpeed(200);
        stay(pieces, Pose.ORIGIN, LOOP, LOOP);
        assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-1").state());
    }

    /** WHAT: Stopping the feed keeps the ball where it got to; running the feed backward pulls it back. */
    @Test
    void stoppingKeepsProgressAndBackwardLosesIt() {
        GamePieces kept = holding(Pose.ORIGIN);
        kept.launcherBallSpeed(200);
        kept.feedPower(1.0);
        stay(kept, Pose.ORIGIN, 0.2, LOOP);
        kept.feedPower(0);
        stay(kept, Pose.ORIGIN, 0.5, LOOP);
        kept.feedPower(1.0);
        stay(kept, Pose.ORIGIN, SECONDS_PER_BALL - 0.2 + 2 * LOOP, LOOP);
        assertEquals(GamePiece.FLYING, piece(kept, "pollen-preload-1").state(), "0.2 s + 0.1 s");

        GamePieces lost = holding(Pose.ORIGIN);
        lost.launcherBallSpeed(200);
        lost.feedPower(1.0);
        stay(lost, Pose.ORIGIN, 0.2, LOOP);
        lost.feedPower(-1.0);
        stay(lost, Pose.ORIGIN, LOOP, LOOP);
        lost.feedPower(1.0);
        stay(lost, Pose.ORIGIN, SECONDS_PER_BALL - 0.2 + 2 * LOOP, LOOP);
        assertEquals(GamePiece.HELD, piece(lost, "pollen-preload-1").state(), "pulled back, so it starts again");
    }

    /**
     * WHAT: A flight matches the textbook: a robot facing +y launches from its launch point at 45 degrees
     * and 200 in/s, with gravity and no drag, and the ball touches down where the textbook says (115 in on,
     * short of the far wall).
     */
    @Test
    void aFlightMatchesTheTextbook() {
        Pose robot = Pose.inches(10, -60, 90);
        GamePieces pieces = holding(robot);
        fire(pieces, robot, 200, FINE);
        GamePiece down = land(pieces, robot, "pollen-preload-1");

        // At 90 degrees, the robot's forward is +y and its left is -x.
        double startX = 10 - LAUNCH_LEFT;
        double startY = -60 + LAUNCH_FORWARD;
        assertEquals(startX, down.xInches(), 1e-6, "straight ahead, no drift sideways");
        assertEquals(startY + textbookRange(200, 45), down.yInches(), 1.0, "range");
        assertEquals(1.4, down.zInches(), 1e-9, "on the tiles");
    }

    /** WHAT: A faster wheel throws further, in proportion to the speed squared once it's well above the launch height. */
    @Test
    void aFasterWheelThrowsFurther() {
        double[] ranges = new double[2];
        double[] speeds = {100, 200};
        for (int k = 0; k < 2; k++) {
            Pose robot = Pose.inches(0, -60, 90);
            GamePieces pieces = holding(robot, 45, 0, List.of());
            fire(pieces, robot, speeds[k], FINE);
            ranges[k] = land(pieces, robot, "pollen-preload-1").yInches() - (-60 + LAUNCH_FORWARD);
            assertEquals(textbookRange(speeds[k], 45), ranges[k], 1.5, "speed " + speeds[k]);
        }
        assertTrue(ranges[1] > 3 * ranges[0], ranges[0] + " then " + ranges[1]);
    }

    /** WHAT: The launch yaw turns the shot: 90 degrees goes to the robot's left. */
    @Test
    void theYawTurnsTheShot() {
        Pose robot = Pose.inches(0, -60, 0);
        GamePieces pieces = holding(robot, 45, 90, List.of());
        fire(pieces, robot, 200, FINE);
        GamePiece down = land(pieces, robot, "pollen-preload-1");

        assertEquals(LAUNCH_FORWARD, down.xInches(), 1e-6);
        assertEquals(-60 + LAUNCH_LEFT + textbookRange(200, 45), down.yInches(), 1.0);
    }

    /** WHAT: The robot's own speed adds to the ball's: moving sideways at 40 in/s carries it 40 in/s times its flight. */
    @Test
    void theRobotsSpeedAdds() {
        Pose robot = Pose.inches(-30, -60, 90);
        GamePieces pieces = holding(robot);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(200);
        // Driving along +x at 40 in/s (sideways for a robot facing +y) until the ball leaves.
        double x = -30;
        while (piece(pieces, "pollen-preload-1").state().equals(GamePiece.HELD)) {
            Pose next = Pose.inches(x + 40 * FINE, -60, 90);
            pieces.step(Pose.inches(x, -60, 90), next, SIZE, SIZE, FINE, true);
            x = next.xInches();
        }
        Pose stopped = Pose.inches(x, -60, 90);
        double launchedX = piece(pieces, "pollen-preload-1").xInches();
        double vUp = 200 * Math.sin(Math.toRadians(45));
        double flight = (vUp + Math.sqrt(vUp * vUp + 2 * G * (LAUNCH_UP - 1.4))) / G;

        GamePiece down = land(pieces, stopped, "pollen-preload-1");
        assertEquals(launchedX + 40 * flight, down.xInches(), 1.0);
    }

    /** WHAT: A landed ball is loose again: it keeps its speed along the tiles, rolls on and stops. */
    @Test
    void aLandedBallRollsOnAndStops() {
        Pose robot = Pose.inches(0, -60, 90);
        GamePieces pieces = holding(robot);
        fire(pieces, robot, 120, FINE);
        GamePiece down = land(pieces, robot, "pollen-preload-1");

        stay(pieces, robot, 0.2, LOOP);
        assertTrue(piece(pieces, "pollen-preload-1").yInches() > down.yInches() + 5, "rolled on");
        stay(pieces, robot, 20, LOOP);
        GamePiece end = piece(pieces, "pollen-preload-1");
        stay(pieces, robot, LOOP, LOOP);
        assertEquals(end, piece(pieces, "pollen-preload-1"), "and stopped");
        assertEquals(GamePiece.FIELD, end.state());
    }

    /** WHAT: A flying ball meets the walls at any height: a hard shot at a wall stays on the field. */
    @Test
    void theWallsStopAFlyingBall() {
        Pose robot = Pose.inches(0, 50, 90);
        GamePieces pieces = holding(robot, 20, 0, List.of());
        fire(pieces, robot, 400, FINE);
        double highest = 0;
        for (int i = 0; i < 2000; i++) {
            pieces.step(robot, robot, SIZE, SIZE, FINE, true);
            GamePiece p = piece(pieces, "pollen-preload-1");
            assertTrue(p.yInches() <= Pose.FIELD_HALF_WIDTH_INCHES - 1.4 + 1e-9, "inside the wall: " + p);
            highest = Math.max(highest, p.zInches());
        }
        assertTrue(piece(pieces, "pollen-preload-1").yInches() < 60, "bounced back off the wall");
        assertTrue(highest > LAUNCH_UP, "it flew");
    }

    /**
     * WHAT: For now a flying ball goes through the field's parts: shot low across the HIVE frame's foot
     * bar, it isn't stopped. Stage 4 adds the 3D parts and changes this.
     */
    @Test
    void aFlyingBallGoesThroughTheHiveForNow() {
        Pose robot = Pose.inches(0, -40, 90);
        GamePieces pieces = holding(robot, 10, 0, BioBuzzObstacles.all());
        fire(pieces, robot, 250, FINE);
        GamePiece down = land(pieces, robot, "pollen-preload-1");

        assertTrue(down.yInches() > -24.16 + 1.4, "landed beyond the lip at y = -24.16: " + down);
    }

    /** WHAT: The same shots give exactly the same balls every time (02-unity-readiness §3). */
    @Test
    void theSameShotsGiveTheSameBalls() {
        List<List<GamePiece>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            Pose robot = Pose.inches(5, -50, 80);
            GamePieces pieces = holding(robot);
            pieces.feedPower(1.0);
            pieces.launcherBallSpeed(230);
            stay(pieces, robot, 2.0, LOOP);
            runs.add(pieces.snapshot());
        }
        assertEquals(runs.get(0), runs.get(1));
    }
}
