package pvi.harness.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import pvi.harness.physics.DriveModels;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.PieceModel;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage U3, harness side: with {@code "physics": "unity"}, Unity's physics moves the robot. The
 * harness tells Unity the wheels' command in each frame and takes the robot state Unity sends
 * back with the next step (no Unity needed: these tests play Unity's part).
 */
class BridgeUnityPhysicsTest {
    /** Full forward on the left stick (up is negative). */
    private static final String FORWARD = "{\"buttons\": [], \"leftStickX\": 0, \"leftStickY\": -1, \"rightStickX\": 0,"
            + " \"rightStickY\": 0, \"leftTrigger\": 0, \"rightTrigger\": 0}";

    private final BridgeSession session = new BridgeSession();

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    /** A physicsState with the robot at (x, y), facing heading, moving forward at speed in/s. */
    private static String state(double x, double y, double headingRadians, double forwardSpeed, String wall) {
        return "{\"xInches\": " + x + ", \"yInches\": " + y + ", \"headingRadians\": " + headingRadians
                + ", \"forwardInchesPerSecond\": " + forwardSpeed + ", \"leftInchesPerSecond\": 0"
                + ", \"counterClockwiseRadiansPerSecond\": 0, \"wallContact\": \"" + wall + "\"}";
    }

    private Map<String, Object> loadTeleOp(String physics) {
        send("{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        return send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\", \"physics\": \"" + physics + "\"}");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pose(Map<String, Object> frame) {
        return (Map<String, Object>) ((Map<String, Object>) frame.get("loop")).get("pose");
    }

    /**
     * WHAT: The robot is wherever Unity's physics says: after Unity reports (10, 4) at 30°, the
     * frame's pose (the same one the recording and the encoders use) is exactly that.
     * WHY: This is the whole point of U3: Unity, not the harness, decides where the robot ends up
     * (walls and field parts included).
     */
    @Test
    void theRobotIsWhereUnitysPhysicsPutsIt() {
        assertEquals("unity", loadTeleOp("unity").get("physics"));
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        Map<String, Object> frame = send("{\"type\": \"step\", \"physicsState\": " + state(10, 4, Math.toRadians(30), 12, "") + "}");

        assertEquals("frame", frame.get("type"), String.valueOf(frame));
        assertEquals(10.0, (Double) pose(frame).get("xInches"), 1e-9);
        assertEquals(4.0, (Double) pose(frame).get("yInches"), 1e-9);
        assertEquals(30.0, Math.toDegrees((Double) pose(frame).get("headingRadians")), 1e-3, "frames keep 6 decimals");
    }

    /**
     * WHAT: Each frame tells Unity what the wheels are doing: full forward on the stick gives four
     * positive pushes, and whether the motors brake.
     */
    @Test
    @SuppressWarnings("unchecked")
    void framesTellUnityTheDriveCommand() {
        loadTeleOp("unity");
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        Map<String, Object> frame = send("{\"type\": \"step\", \"gamepad1\": " + FORWARD
                + ", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        Map<String, Object> command = (Map<String, Object>) frame.get("driveCommand");
        assertNotNull(command, String.valueOf(frame));
        for (String wheel : new String[] {"frontLeft", "frontRight", "rearLeft", "rearRight"}) {
            if (BridgeTestOpModes.pvi()) {
                assertTrue((Double) command.get(wheel) > 0.5, wheel + " pushes forward: " + command);
            } else {
                assertTrue(Math.abs((Double) command.get(wheel)) > 0.1, wheel + " pushes (another team's TeleOp may map sticks differently): " + command);
            }
        }
        assertTrue(command.get("braking") instanceof Boolean, "braking is true or false: " + command);
    }

    /**
     * WHAT: "loaded" tells Unity how to move this robot like the harness would: the configured robot's speeds,
     * response and traction numbers, size and start pose.
     */
    @Test
    @SuppressWarnings("unchecked")
    void loadedDescribesTheRobotForUnitysPhysics() {
        Map<String, Object> robot = (Map<String, Object>) loadTeleOp("unity").get("robotPhysics");

        assertEquals(DriveModels.fromConfig(HarnessConfig.load()).forwardInchesPerSecond(), (Double) robot.get("forwardInchesPerSecond"), 1e-5);
        assertEquals(DriveModels.fromConfig(HarnessConfig.load()).dynamics().maxAcceleration().value(),
                (Double) robot.get("maxAccelerationInchesPerSecondSquared"), 1e-5);
        assertEquals(DriveModels.fromConfig(HarnessConfig.load()).lengthInches(), (Double) robot.get("lengthInches"), 1e-5);
        assertEquals(0.0, (Double) ((Map<String, Object>) robot.get("start")).get("xInches"), 1e-9);
    }

    /**
     * WHAT: A wall contact Unity reports becomes a wall hit in the run's events, as with the
     * harness's own physics.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aWallContactFromUnityIsAWallHit() {
        loadTeleOp("unity");
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        Map<String, Object> frame = send("{\"type\": \"step\", \"physicsState\": " + state(63, 0, 0, 0, "+x") + "}");

        assertTrue(String.valueOf(frame.get("events")).contains("+x wall"), "events: " + frame.get("events"));
    }

    /**
     * WHAT: An obstacle contact Unity reports (version 5) becomes the same "Robot hit the HIVE frame"
     * event the harness's own physics makes; "loaded" tells Unity whether the field elements block
     * the robot (harness.properties field.robotObstacles).
     * WHY: Both physics modes must give the same events, so recorded tests can check them.
     */
    @Test
    @SuppressWarnings("unchecked")
    void anObstacleContactFromUnityIsAnObstacleHit() {
        Map<String, Object> robot = (Map<String, Object>) loadTeleOp("unity").get("robotPhysics");
        assertEquals(HarnessConfig.load().robotObstacles(), robot.get("robotObstacles"));
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        String touching = state(0, -15.16, 0, 0, "").replace("\"wallContact\": \"\"",
                "\"wallContact\": \"\", \"obstacleContact\": \"HIVE frame\"");
        Map<String, Object> frame = send("{\"type\": \"step\", \"physicsState\": " + touching + "}");

        assertTrue(String.valueOf(frame.get("events")).contains("Robot hit the HIVE frame"), "events: " + frame.get("events"));
        Map<String, Object> bad = send("{\"type\": \"step\", \"physicsState\": "
                + touching.replace("\"HIVE frame\"", "7") + "}");
        assertEquals("error", bad.get("type"));
        assertTrue(String.valueOf(bad.get("message")).contains("obstacleContact"), String.valueOf(bad));
    }

    /**
     * WHAT: Mix-ups get clear errors and the session carries on: a step without physicsState under
     * Unity's physics, a physicsState under the harness's own, a wrong "physics" value, a
     * non-number in physicsState.
     */
    /**
     * WHAT: With Unity's physics (bridge version 6), the harness leaves the loose balls to Unity: a ball
     * Unity reports in "pieces" is where the next frame says, and the robot backing into a ball doesn't
     * move it on the harness's side (Unity's physics pushes it).
     * WHY: Manager decision D30: Unity moves the balls, the harness keeps the rules. Moving a ball on both
     * sides would make it jump between two answers.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unitysBallsAreWhereUnitySays() {
        Map<String, Object> loaded = loadTeleOp("unity");
        double halfLength = (Double) ((Map<String, Object>) loaded.get("robotPhysics")).get("lengthInches") / 2;
        Map<String, Object> ball = pollenBehindTheStart((List<Object>) ((Map<String, Object>) loaded.get("header")).get("pieces"));
        String id = (String) ball.get("id");
        double ballX = (Double) ball.get("xInches");
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");

        // The robot backs up through the ball's spot; Unity doesn't say the ball moved.
        double x = 0;
        double seenX = ballX;
        while (x - halfLength > ballX - 4) {
            x -= 1;
            seenX = pieceX(send("{\"type\": \"step\", \"physicsState\": " + state(x, 0, 0, -50, "") + "}"), id, seenX);
        }
        assertEquals(ballX, seenX, 1e-9, "the harness didn't push it");

        // Unity says where it is now.
        Map<String, Object> frame = send("{\"type\": \"step\", \"physicsState\": " + state(x, 0, 0, 0, "")
                + ", \"pieces\": [{\"id\": \"" + id + "\", \"xInches\": -40.5, \"yInches\": 2.25, \"zInches\": 1.4,"
                + " \"vxInchesPerSecond\": -12, \"vyInchesPerSecond\": 0, \"vzInchesPerSecond\": 0}]}");
        assertEquals("frame", frame.get("type"), String.valueOf(frame));
        assertEquals(-40.5, pieceX(frame, id, seenX), 1e-9, "where Unity put it");
    }

    /**
     * WHAT: "loaded" gives Unity the balls' facts from the harness's PieceModel, each with its unit and
     * whether it was measured: the masses are measured, the bounces and rolling are guesses.
     * WHY: Unity's physics uses the same numbers as the harness's, without a second copy to keep in step.
     */
    @Test
    @SuppressWarnings("unchecked")
    void loadedGivesTheBallFacts() {
        Map<String, Object> model = (Map<String, Object>) loadTeleOp("unity").get("pieceModel");
        PieceModel harness = PieceModel.bioBuzz();

        Map<String, Object> pollen = (Map<String, Object>) model.get("pollenMass");
        assertEquals(harness.pollenMass().value(), (Double) pollen.get("value"), 1e-9);
        assertEquals("g", pollen.get("unit"));
        assertEquals(true, pollen.get("measured"));
        Map<String, Object> wall = (Map<String, Object>) model.get("wallBounce");
        assertEquals(harness.wallBounce().value(), (Double) wall.get("value"), 1e-9);
        assertEquals(false, wall.get("measured"), "a placeholder says so");
        for (String name : List.of("rollingDeceleration", "pieceBounce", "robotBounce", "nectarMass")) {
            assertNotNull(model.get(name), name);
        }
    }

    /**
     * WHAT: "loaded" also gives each ball kind's air drag (version 7, D40): the harness's own
     * PieceModel.dragPerInch, in 1/in, a placeholder like the drag coefficient it comes from.
     * WHY: Unity slows flying balls by the same k times speed squared as the harness.
     */
    @Test
    @SuppressWarnings("unchecked")
    void loadedGivesTheAirDragPerBallKind() {
        Map<String, Object> model = (Map<String, Object>) loadTeleOp("unity").get("pieceModel");
        PieceModel harness = PieceModel.bioBuzz();

        Map<String, Object> pollen = (Map<String, Object>) model.get("pollenDragPerInch");
        Map<String, Object> nectar = (Map<String, Object>) model.get("nectarDragPerInch");
        assertEquals(harness.dragPerInch(GamePiece.POLLEN), (Double) pollen.get("value"), 1e-8, "6 significant digits");
        assertEquals(harness.dragPerInch(GamePiece.RED_NECTAR), (Double) nectar.get("value"), 1e-8);
        assertEquals("1/in", pollen.get("unit"));
        assertEquals(harness.dragCoefficient().measured(), pollen.get("measured"));
        assertEquals(harness.dragCoefficient().measured(), nectar.get("measured"));
    }

    /** WHAT: Every frame carries "pieceLaunches", a list (empty when nothing was launched), in either physics. */
    @Test
    void framesCarryPieceLaunches() {
        loadTeleOp("unity");
        send("{\"type\": \"init\"}");
        Map<String, Object> unity = send("{\"type\": \"play\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");
        assertTrue(unity.get("pieceLaunches") instanceof List, String.valueOf(unity));

        send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\"}");
        Map<String, Object> harness = send("{\"type\": \"init\"}");
        assertEquals(List.of(), harness.get("pieceLaunches"));
    }

    /** The preloaded POLLEN right behind the robot's start (the one nearest the middle). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> pollenBehindTheStart(List<Object> pieces) {
        Map<String, Object> best = null;
        for (Object o : pieces) {
            Map<String, Object> p = (Map<String, Object>) o;
            double x = (Double) p.get("xInches");
            double y = (Double) p.get("yInches");
            if ("pollen".equals(p.get("kind")) && x < 0 && x > -20 && Math.abs(y) < 6
                    && (best == null || Math.abs(y) < Math.abs((Double) best.get("yInches")))) {
                best = p;
            }
        }
        if (best == null) {
            throw new AssertionError("no preloaded POLLEN behind the start in " + pieces);
        }
        return best;
    }

    /** Piece {@code id}'s x in this frame, or {@code previous} if it didn't move (frames only list pieces that moved). */
    @SuppressWarnings("unchecked")
    private static double pieceX(Map<String, Object> frame, String id, double previous) {
        for (Object o : (List<Object>) ((Map<String, Object>) frame.get("loop")).get("pieces")) {
            Map<String, Object> p = (Map<String, Object>) o;
            if (id.equals(p.get("id"))) {
                return (Double) p.get("xInches");
            }
        }
        return previous;
    }

    @Test
    void mixUpsGetClearErrors() {
        loadTeleOp("unity");
        send("{\"type\": \"init\"}");
        Map<String, Object> missing = send("{\"type\": \"step\"}");
        assertEquals("error", missing.get("type"));
        assertTrue(((String) missing.get("message")).contains("needs a \"physicsState\""), String.valueOf(missing));

        Map<String, Object> badNumber = send("{\"type\": \"step\", \"physicsState\": {\"xInches\": \"far\"}}");
        assertTrue(((String) badNumber.get("message")).contains("physicsState.xInches must be a number"), String.valueOf(badNumber));
        Map<String, Object> badPiece = send("{\"type\": \"step\", \"physicsState\": " + state(0, 0, 0, 0, "")
                + ", \"pieces\": [{\"id\": \"pollen-preload-1\", \"xInches\": \"here\"}]}");
        assertTrue(((String) badPiece.get("message")).contains("pieces[pollen-preload-1].xInches must be a number"), String.valueOf(badPiece));

        Map<String, Object> wrong = send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\", \"physics\": \"real\"}");
        assertTrue(((String) wrong.get("message")).contains("\"physics\" must be"), String.valueOf(wrong));

        assertEquals("harness", send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\"}").get("physics"));
        send("{\"type\": \"init\"}");
        Map<String, Object> notWanted = send("{\"type\": \"step\", \"physicsState\": " + state(0, 0, 0, 0, "") + "}");
        assertTrue(((String) notWanted.get("message")).contains("must not carry"), String.valueOf(notWanted));
        Map<String, Object> noPieces = send("{\"type\": \"step\", \"pieces\": []}");
        assertTrue(((String) noPieces.get("message")).contains("must not carry \"pieces\""), String.valueOf(noPieces));
        assertEquals("frame", send("{\"type\": \"step\"}").get("type"), "the session carries on");
    }
}
