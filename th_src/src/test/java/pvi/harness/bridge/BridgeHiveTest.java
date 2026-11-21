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

/**
 * The HIVE in Unity-physics mode (outside physics, W-UP1): Unity puts the robot by the red HIVE and moves the
 * balls (here a plain stand-in, {@link StandInBallPhysics}); Java throws the 4 pre-loads, sees them come into
 * the upward CELL, tips the HIVE, and sees the balls leave the CELL as it turns. Through the bridge and straight
 * through a runner, every ball ends the same.
 */
class BridgeHiveTest {
    private static final String ROBOT_ID = "bridge-hive-test";
    /** A shot from 14 in up at 60 degrees, from 6 in in front of a robot at (58, -12.75) facing -x, goes in at 250 in/s. */
    private static final String FILE = """
            intake.motor=
            hold.capacity=4 | placeholder | test
            hold.accepts=pollen
            hold.preloaded=4 | placeholder | test
            launcher.motor=testWheel
            launcher.direction=FORWARD
            launcher.ticksPerRev=28 | placeholder | test
            launcher.freeSpeedRpm=6000 | placeholder | test
            launcher.spinUpSeconds=0.3 | placeholder | test
            launcher.speedLossPerShot=0 | placeholder | test
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
            launch.pitchDegrees=60 | placeholder | test
            launch.yawDegrees=0 | placeholder | test
            """;
    /** The wheel speed that throws at 250 in/s: 250 / (π × 4 in × 0.5) turns a second, 28 ticks each. */
    private static final double WHEEL_TICKS_PER_SECOND = 250 / (Math.PI * 4 * 0.5) * 28;
    private static final Pose AT_RED_HIVE = Pose.inches(58, -12.75, 180);
    private static final int LOOPS = 500;
    private static final double MOVED_INCHES = 0.02;

    @TempDir
    Path robotsFolder;

    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), FILE);
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testWheel, testFeed"), "test", robotsFolder);
    }

    /** Spins the wheel from the start; feeds for 0.32 s every 0.8 s from 2 s on, one ball each time. */
    private static final class ShootFour extends OpMode {
        private int loops;

        @Override
        public void init() {
            hardwareMap.get(DcMotorEx.class, "testWheel").setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            hardwareMap.get(DcMotor.class, "testFeed").setDirection(Direction.FORWARD);
        }

        @Override
        public void loop() {
            loops++;
            hardwareMap.get(DcMotorEx.class, "testWheel").setVelocity(WHEEL_TICKS_PER_SECOND);
            int sinceFirst = loops - 100;
            boolean feeding = sinceFirst >= 0 && sinceFirst % 40 < 16 && sinceFirst < 160;
            hardwareMap.get(DcMotor.class, "testFeed").setPower(feeding ? 1.0 : 0.0);
        }
    }

    private static String physicsState(Pose p) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> throughTheBridge(HarnessConfig config) {
        BridgeOpModes.Entry entry = new BridgeOpModes.Entry("HiveTest", "HIVE test", MatchPeriod.TELEOP,
                ShootFour::new, () -> RobotConfigs.robot(config));
        BridgeSession session = new BridgeSession(() -> {
            throw new AssertionError("no controllers here");
        }, () -> List.of(entry));
        Map<String, Map<String, Object>> balls = new HashMap<>();
        send(session, "{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        Map<String, Object> loaded = send(session, "{\"type\": \"load\", \"opMode\": \"HiveTest\", \"physics\": \"unity\"}");
        for (Map<String, Object> p : (List<Map<String, Object>>) ((Map<String, Object>) loaded.get("header")).get("pieces")) {
            balls.put((String) p.get("id"), p);
        }
        StandInBallPhysics unity = new StandInBallPhysics();
        remember(send(session, "{\"type\": \"init\"}"), balls, unity);
        List<PieceMotion> next = remember(send(session, "{\"type\": \"play\", \"physicsState\": " + physicsState(AT_RED_HIVE) + "}"),
                balls, unity);
        for (int k = 1; k <= LOOPS; k++) {
            next = remember(send(session, "{\"type\": \"step\", \"physicsState\": " + physicsState(AT_RED_HIVE)
                    + ", \"pieces\": " + StandInBallPhysics.json(next) + "}"), balls, unity);
        }
        return balls;
    }

    private static PoseEstimator straight(HarnessConfig config) {
        OpModeRunner runner = new OpModeRunner(new ShootFour(), RobotConfigs.robot(config));
        RemotePhysics physics = new RemotePhysics(Pose.ORIGIN);
        PoseEstimator estimator = new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config), Pose.ORIGIN, physics);
        runner.usePositionEstimator(estimator);
        estimator.pieces().useOutsidePhysics();
        StandInBallPhysics unity = new StandInBallPhysics();
        PhysicsBackend.State still = new PhysicsBackend.State(AT_RED_HIVE, new MecanumKinematics.RobotVelocity(0, 0, 0), "");
        runner.init();
        physics.supply(still);
        runner.start();
        List<PieceMotion> next = unity.advance(estimator.pieces().snapshot(), estimator.pieces().takeLaunches());
        for (int k = 1; k <= LOOPS; k++) {
            physics.supply(still);
            estimator.pieces().supply(next);
            runner.loop(1);
            next = unity.advance(estimator.pieces().snapshot(), estimator.pieces().takeLaunches());
        }
        return estimator;
    }

    /**
     * WHAT: The 4 shots, flown by the stand-in, come into the red upward CELL, its weight (with the 3 staged
     * NECTAR, 223.5 g) tips the HIVE to -30, the balls leave the CELL as it turns and fall, and through the bridge
     * every ball ends where the straight run puts it.
     */
    @Test
    void theHiveIsTheSameWithAndWithoutTheBridge() throws IOException {
        HarnessConfig config = config();
        Map<String, Map<String, Object>> bridged = throughTheBridge(config);
        PoseEstimator straight = straight(config);

        assertEquals(-60.0, straight.pieces().hiveTilts().get(0).swingDegrees(), 1e-9, "the red HIVE tipped");
        assertEquals(GamePiece.FIELD, stateOf(straight, "red-nectar-cell-1"), "and spilled");
        for (GamePiece p : straight.pieces().snapshot()) {
            Map<String, Object> seen = bridged.get(p.id());
            assertEquals(p.state(), seen.get("state"), p.id());
            assertEquals(p.xInches(), (Double) seen.get("xInches"), MOVED_INCHES, p.id());
            assertEquals(p.yInches(), (Double) seen.get("yInches"), MOVED_INCHES, p.id());
            assertEquals(p.zInches(), (Double) seen.get("zInches"), MOVED_INCHES, p.id());
        }
    }

    private static String stateOf(PoseEstimator estimator, String id) {
        return estimator.pieces().snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().state();
    }
}
