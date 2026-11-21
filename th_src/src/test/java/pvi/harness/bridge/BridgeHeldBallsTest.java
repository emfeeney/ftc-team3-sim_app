package pvi.harness.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.MechanismSetup;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.BioBuzzObstacles;
import pvi.harness.physics.BioBuzzPieces;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.GamePieces;
import pvi.harness.physics.PieceModel;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.JsonReader;
import pvi.harness.runtime.MatchPeriod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With Unity's physics (outside physics, W-UP1), Unity moves the robot and the loose balls, but Java keeps the
 * rules: the same intake rules run from the poses Unity sends, and the frames carry the result. Here Unity sends
 * no ball positions, and the balls end up exactly where {@link GamePieces} alone, in outside physics, puts them
 * for the same poses.
 */
class BridgeHeldBallsTest {
    private static final String ROBOT_ID = "bridge-intake-test";
    private static final String FILE = """
            intake.motor=testIntake
            intake.direction=FORWARD
            intake.minPower=0.1 | placeholder | test
            intake.forwardInches=10 | placeholder | test
            intake.leftInches=0 | placeholder | test
            intake.lengthInches=3 | placeholder | test
            intake.widthInches=10 | placeholder | test
            intake.spitSeconds=0.3 | placeholder | test
            intake.spitInchesPerSecond=30 | placeholder | test
            hold.capacity=4 | placeholder | test
            hold.accepts=pollen
            hold.preloaded=1 | placeholder | test
            launcher.motor=
            """;

    private static final double LOOP = 0.02;
    private static final double MOVED_INCHES = 0.02;

    @TempDir
    Path robotsFolder;

    /** Runs the intake on the gamepad's left trigger, in and out by the right bumper. */
    private static final class IntakeOnTheGamepad extends OpMode {
        @Override
        public void init() {
        }

        @Override
        public void loop() {
            double power = gamepad1.left_trigger > 0.5 ? 1.0 : gamepad1.right_trigger > 0.5 ? -1.0 : 0.0;
            hardwareMap.get(DcMotor.class, "testIntake").setDirection(Direction.FORWARD);
            hardwareMap.get(DcMotor.class, "testIntake").setPower(power);
        }
    }

    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), FILE);
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testIntake"), "test", robotsFolder);
    }

    private BridgeSession session() throws IOException {
        HarnessConfig config = config();
        BridgeOpModes.Entry entry = new BridgeOpModes.Entry("IntakeTest", "Intake test", MatchPeriod.TELEOP,
                IntakeOnTheGamepad::new, () -> RobotConfigs.robot(config));
        return new BridgeSession(() -> {
            throw new AssertionError("no controllers here");
        }, () -> List.of(entry));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> send(BridgeSession session, String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    /** A physicsState with the robot standing still at (x, y), facing along -x. */
    private static String at(double x, double y) {
        return "{\"xInches\": " + x + ", \"yInches\": " + y + ", \"headingRadians\": " + Math.PI
                + ", \"forwardInchesPerSecond\": 0, \"leftInchesPerSecond\": 0"
                + ", \"counterClockwiseRadiansPerSecond\": 0, \"wallContact\": \"\"}";
    }

    private static String gamepad(double leftTrigger, double rightTrigger) {
        return "{\"buttons\": [], \"leftStickX\": 0, \"leftStickY\": 0, \"rightStickX\": 0, \"rightStickY\": 0, "
                + "\"leftTrigger\": " + leftTrigger + ", \"rightTrigger\": " + rightTrigger + "}";
    }

    /** Every ball as the frames have told it so far, by id. */
    private final Map<String, Map<String, Object>> balls = new HashMap<>();

    @SuppressWarnings("unchecked")
    private void step(BridgeSession session, double x, double y, double leftTrigger, double rightTrigger) {
        Map<String, Object> frame = send(session, "{\"type\": \"step\", \"gamepad1\": " + gamepad(leftTrigger, rightTrigger)
                + ", \"physicsState\": " + at(x, y) + "}");
        assertEquals("frame", frame.get("type"), String.valueOf(frame));
        for (Map<String, Object> p : (List<Map<String, Object>>) ((Map<String, Object>) frame.get("loop")).get("pieces")) {
            balls.put((String) p.get("id"), p);
        }
    }

    /**
     * WHAT: In Unity-physics mode, the pre-load starts held in "loaded"'s header and rides where Unity puts
     * the robot; driving to the far FLOWER with the intake running takes its bottom POLLEN, without a
     * trigger it stays (no stand-in), and the frames say so; reverse spits it out loose.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unitysRobotTakesHoldsAndSpitsBalls() throws IOException {
        BridgeSession session = session();
        send(session, "{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        Map<String, Object> loaded = send(session, "{\"type\": \"load\", \"opMode\": \"IntakeTest\", \"physics\": \"unity\"}");
        assertEquals("unity", loaded.get("physics"), String.valueOf(loaded));

        List<Map<String, Object>> first = (List<Map<String, Object>>) ((Map<String, Object>) loaded.get("header")).get("pieces");
        assertEquals(GamePiece.HELD, first.stream().filter(p -> p.get("id").equals("pollen-preload-1")).findFirst().orElseThrow().get("state"));

        send(session, "{\"type\": \"init\"}");
        send(session, "{\"type\": \"play\", \"physicsState\": " + at(-40, -23.39) + "}");
        step(session, -40, -23.39, 0, 0);
        step(session, -41, -23.39, 0, 0);
        Map<String, Object> preload = balls.get("pollen-preload-1");
        assertTrue(Math.abs((Double) preload.get("xInches") + 41) < 12, "rides where Unity put the robot: " + preload);

        // Up to the FLOWER head with the intake off: no stand-in for a robot with an intake.
        double closeX = -68.04 + 3.2 + 9;
        List<Double> path = new ArrayList<>();
        for (double x = -40; x > closeX; x -= 0.5) {
            path.add(x);
        }
        path.forEach(x -> step(session, x, -23.39, 0, 0));
        step(session, closeX, -23.39, 0, 0);
        assertTrue(!balls.containsKey("pollen-flower-far-4"), "it hasn't moved: " + balls.get("pollen-flower-far-4"));

        // The intake on: taken, and the next ball starts to drop.
        step(session, closeX, -23.39, 1, 0);
        step(session, closeX, -23.39, 1, 0);
        step(session, closeX, -23.39, 1, 0);
        assertEquals(GamePiece.HELD, balls.get("pollen-flower-far-4").get("state"));

        // Reverse: the newest (that ball) goes back out, loose.
        for (int i = 0; i < 20; i++) {
            step(session, closeX, -23.39, 0, 1);
        }
        assertEquals(GamePiece.FIELD, balls.get("pollen-flower-far-4").get("state"));
        assertEquals(GamePiece.HELD, balls.get("pollen-preload-1").get("state"), "the older ball stays");
    }

    /**
     * WHAT: The same poses and the same intake, once through Unity-physics mode and once straight into
     * {@link GamePieces} in outside physics, leave every ball in the same state and place.
     *
     * <p>WHY: With Unity's physics Java still decides the intake, holding and spitting (W-UP1); the bridge
     * changes none of it. (No ball positions are supplied here, so a spat-out ball stays where it left.)</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void theBallsAreTheSameAsWithNoBridge() throws IOException {
        // Each step: robot x, left trigger (intake in), right trigger (intake out).
        List<double[]> script = new ArrayList<>();
        double closeX = -68.04 + 3.2 + 9;
        for (double x = -40; x > closeX; x -= 0.5) {
            script.add(new double[] {x, 0, 0});
        }
        for (int i = 0; i < 10; i++) {
            script.add(new double[] {closeX, 1, 0});
        }
        for (int i = 0; i < 40; i++) {
            script.add(new double[] {closeX, 0, 1});
        }

        BridgeSession session = session();
        send(session, "{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        Map<String, Object> loaded = send(session, "{\"type\": \"load\", \"opMode\": \"IntakeTest\", \"physics\": \"unity\"}");
        for (Map<String, Object> p : (List<Map<String, Object>>) ((Map<String, Object>) loaded.get("header")).get("pieces")) {
            balls.put((String) p.get("id"), p);
        }
        send(session, "{\"type\": \"init\"}");
        send(session, "{\"type\": \"play\", \"physicsState\": " + at(-40, -23.39) + "}");
        script.forEach(s -> step(session, s[0], -23.39, s[1], s[2]));

        MechanismSetup setup = config().mechanism();
        GamePieces alone = new GamePieces(BioBuzzPieces.start(Pose.ORIGIN, 18), PieceModel.bioBuzz(), BioBuzzObstacles.all(),
                BioBuzzPieces.flowers());
        alone.useMechanism(setup, Pose.ORIGIN);
        alone.useOutsidePhysics();
        Pose robot = new Pose(-40, -23.39, Math.PI);
        alone.step(Pose.ORIGIN, robot, 18, 18, LOOP, true);
        for (double[] s : script) {
            // The OpMode sees the gamepad in this step; its power acts from the next, as for the wheels.
            Pose next = new Pose(s[0], -23.39, Math.PI);
            alone.step(robot, next, 18, 18, LOOP, true);
            alone.intakePower(s[1] > 0.5 ? 1.0 : s[2] > 0.5 ? -1.0 : 0.0);
            robot = next;
        }

        for (GamePiece p : alone.snapshot()) {
            Map<String, Object> seen = balls.get(p.id());
            assertEquals(p.state(), seen.get("state"), p.id());
            // A frame only reports a ball that moved more than 0.01 in since the last report.
            assertEquals(p.xInches(), (Double) seen.get("xInches"), MOVED_INCHES, p.id());
            assertEquals(p.yInches(), (Double) seen.get("yInches"), MOVED_INCHES, p.id());
            assertEquals(p.zInches(), (Double) seen.get("zInches"), MOVED_INCHES, p.id());
        }
        assertEquals(GamePiece.FIELD, balls.get("pollen-flower-far-4").get("state"), "something did happen in this run");
    }
}
