package pvi.harness.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.DriveModels;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.MecanumKinematics;
import pvi.harness.physics.PhysicsBackend;
import pvi.harness.physics.PieceMotion;
import pvi.harness.physics.Pose;
import pvi.harness.physics.PoseEstimator;
import pvi.harness.physics.WheelReader;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.JsonReader;
import pvi.harness.runtime.MatchPeriod;
import pvi.harness.runtime.OpModeRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Launched balls in Unity-physics mode (outside physics, W-UP1): Unity moves the robot and the balls, Java
 * throws them and keeps the rules. A run through the bridge and the same run straight through a runner, with
 * the robot put in the same places and the balls moved by the same stand-in for Unity's ball physics
 * ({@link StandInBallPhysics}), leave every ball the same.
 */
class BridgeFlyingBallsTest {
    private static final String ROBOT_ID = "bridge-launch-test";
    private static final String FILE = """
            intake.motor=
            hold.capacity=4 | placeholder | test
            hold.accepts=pollen
            hold.preloaded=2 | placeholder | test
            launcher.motor=testWheel
            launcher.direction=FORWARD
            launcher.ticksPerRev=28 | placeholder | test
            launcher.freeSpeedRpm=6000 | placeholder | test
            launcher.spinUpSeconds=0.3 | placeholder | test
            launcher.speedLossPerShot=0.2 | placeholder | test
            launcher.wheelDiameterInches=4 | placeholder | test
            launcher.exitFraction=0.5 | placeholder | test
            launcher.minBallInchesPerSecond=20 | placeholder | test
            feed.motor=testFeed
            feed.direction=FORWARD
            feed.minPower=0.1 | placeholder | test
            feed.secondsPerBall=0.3 | placeholder | test
            launch.forwardInches=6 | placeholder | test
            launch.leftInches=0 | placeholder | test
            launch.upInches=14 | placeholder | test
            launch.pitchDegrees=50 | placeholder | test
            launch.yawDegrees=0 | placeholder | test
            """;
    private static final double MOVED_INCHES = 0.02;
    /** Loops after PLAY; the robot drives along +x for the first 60, then stands. */
    private static final int LOOPS = 200;

    @TempDir
    Path robotsFolder;

    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), FILE);
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testWheel, testFeed"), "test", robotsFolder);
    }

    /** Spins the wheel all along; feeds for loops 40 to 120, which throws both pre-loads (one while driving). */
    private static final class SpinAndFeedByLoop extends OpMode {
        private int loops;

        @Override
        public void init() {
            hardwareMap.get(DcMotorEx.class, "testWheel").setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            hardwareMap.get(DcMotor.class, "testFeed").setDirection(Direction.FORWARD);
        }

        @Override
        public void loop() {
            loops++;
            hardwareMap.get(DcMotorEx.class, "testWheel").setVelocity(1000);
            hardwareMap.get(DcMotor.class, "testFeed").setPower(loops >= 40 && loops < 120 ? 1.0 : 0.0);
        }
    }

    /** Where Unity puts the robot after loop {@code k} (0 is PLAY). */
    private static Pose poseAt(int k) {
        return Pose.inches(-30 + 0.6 * Math.min(k, 60), -20, 30);
    }

    private static PhysicsBackend.State stateAt(int k) {
        return new PhysicsBackend.State(poseAt(k), new MecanumKinematics.RobotVelocity(0, 0, 0), "");
    }

    private static String physicsState(int k) {
        Pose p = poseAt(k);
        return "{\"xInches\": " + p.xInches() + ", \"yInches\": " + p.yInches() + ", \"headingRadians\": " + p.headingRadians()
                + ", \"forwardInchesPerSecond\": 0, \"leftInchesPerSecond\": 0, \"counterClockwiseRadiansPerSecond\": 0,"
                + " \"wallContact\": \"\"}";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> send(BridgeSession session, String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    /** Takes in a frame's balls; returns what the stand-in for Unity's ball physics supplies next. */
    @SuppressWarnings("unchecked")
    private static List<PieceMotion> remember(Map<String, Object> frame, Map<String, Map<String, Object>> balls,
                                              StandInBallPhysics unity) {
        assertEquals("frame", frame.get("type"), String.valueOf(frame));
        for (Map<String, Object> p : (List<Map<String, Object>>) ((Map<String, Object>) frame.get("loop")).get("pieces")) {
            balls.put((String) p.get("id"), p);
        }
        return unity.advance(StandInBallPhysics.pieces(balls), StandInBallPhysics.launchesIn(frame));
    }

    /** The run through the bridge, in Unity-physics mode: every ball as the frames last told it. */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> throughTheBridge(HarnessConfig config) {
        BridgeOpModes.Entry entry = new BridgeOpModes.Entry("LaunchTest", "Launch test", MatchPeriod.TELEOP,
                SpinAndFeedByLoop::new, () -> RobotConfigs.robot(config));
        BridgeSession session = new BridgeSession(() -> {
            throw new AssertionError("no controllers here");
        }, () -> List.of(entry));
        Map<String, Map<String, Object>> balls = new HashMap<>();
        send(session, "{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        Map<String, Object> loaded = send(session, "{\"type\": \"load\", \"opMode\": \"LaunchTest\", \"physics\": \"unity\"}");
        assertEquals("unity", loaded.get("physics"), String.valueOf(loaded));
        for (Map<String, Object> p : (List<Map<String, Object>>) ((Map<String, Object>) loaded.get("header")).get("pieces")) {
            balls.put((String) p.get("id"), p);
        }
        StandInBallPhysics unity = new StandInBallPhysics();
        remember(send(session, "{\"type\": \"init\"}"), balls, unity);
        List<PieceMotion> next = remember(send(session, "{\"type\": \"play\", \"physicsState\": " + physicsState(0) + "}"),
                balls, unity);
        for (int k = 1; k <= LOOPS; k++) {
            next = remember(send(session, "{\"type\": \"step\", \"physicsState\": " + physicsState(k)
                    + ", \"pieces\": " + StandInBallPhysics.json(next) + "}"), balls, unity);
        }
        return balls;
    }

    /** The same run straight through a runner, with the same poses and the same stand-in for Unity's balls. */
    private static List<GamePiece> straight(HarnessConfig config) {
        OpModeRunner runner = new OpModeRunner(new SpinAndFeedByLoop(), RobotConfigs.robot(config));
        RemotePhysics physics = new RemotePhysics(Pose.ORIGIN);
        PoseEstimator estimator = new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config), Pose.ORIGIN, physics);
        runner.usePositionEstimator(estimator);
        estimator.pieces().useOutsidePhysics();
        StandInBallPhysics unity = new StandInBallPhysics();
        runner.init();
        physics.supply(stateAt(0));
        runner.start();
        List<PieceMotion> next = unity.advance(estimator.pieces().snapshot(), estimator.pieces().takeLaunches());
        for (int k = 1; k <= LOOPS; k++) {
            physics.supply(stateAt(k));
            estimator.pieces().supply(next);
            runner.loop(1);
            next = unity.advance(estimator.pieces().snapshot(), estimator.pieces().takeLaunches());
        }
        return estimator.pieces().snapshot();
    }

    /**
     * WHAT: Both pre-loads are thrown through the bridge, reported in the frames' {@code pieceLaunches}, flown by
     * the stand-in from there, and every ball ends where the straight run puts it, flying, landed or held.
     *
     * <p>WHY: With Unity's physics Unity moves the balls from Java's launches and Java keeps the rules; the bridge
     * itself changes no ball.</p>
     */
    @Test
    void launchedBallsAreTheSameWithAndWithoutTheBridge() throws IOException {
        HarnessConfig config = config();
        Map<String, Map<String, Object>> bridged = throughTheBridge(config);
        List<GamePiece> straight = straight(config);

        for (GamePiece p : straight) {
            Map<String, Object> seen = bridged.get(p.id());
            assertEquals(p.state(), seen.get("state"), p.id());
            assertEquals(p.xInches(), (Double) seen.get("xInches"), MOVED_INCHES, p.id());
            assertEquals(p.yInches(), (Double) seen.get("yInches"), MOVED_INCHES, p.id());
            assertEquals(p.zInches(), (Double) seen.get("zInches"), MOVED_INCHES, p.id());
        }
        for (String thrown : List.of("pollen-preload-1", "pollen-preload-2")) {
            assertEquals(GamePiece.FIELD, straight.stream().filter(p -> p.id().equals(thrown)).findFirst().orElseThrow().state(),
                    thrown + " was thrown and landed");
        }
        GamePiece first = straight.stream().filter(p -> p.id().equals("pollen-preload-1")).findFirst().orElseThrow();
        assertTrue(Math.hypot(first.xInches() - poseAt(LOOPS).xInches(), first.yInches() - poseAt(LOOPS).yInches()) > 20,
                "and it went well away from the robot: " + first);
    }
}
