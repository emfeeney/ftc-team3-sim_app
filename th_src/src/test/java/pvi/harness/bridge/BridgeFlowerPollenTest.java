package pvi.harness.bridge;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pvi.harness.physics.GamePiece;
import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * With Unity's physics, Java still owns the FLOWERs: a robot with no intake that Unity puts against a FLOWER gets
 * nothing out, just as with the harness's own physics (G418, manager decision D38), and the frames keep the ball in
 * the FLOWER. (An intake taking a FLOWER's POLLEN through the bridge: {@code BridgeHeldBallsTest}.)
 */
class BridgeFlowerPollenTest {
    private final BridgeSession session = new BridgeSession();

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    /** A physicsState with the robot standing still at (x, y), facing the far wall. */
    private static String at(double x, double y) {
        return "{\"xInches\": " + x + ", \"yInches\": " + y + ", \"headingRadians\": " + Math.PI
                + ", \"forwardInchesPerSecond\": 0, \"leftInchesPerSecond\": 0"
                + ", \"counterClockwiseRadiansPerSecond\": 0, \"wallContact\": \"\"}";
    }

    /** True if this frame says the far FLOWER's bottom ball is no longer in the FLOWER. */
    @SuppressWarnings("unchecked")
    private static boolean leftTheFlower(Map<String, Object> frame) {
        Object loop = frame.get("loop");
        if (!(loop instanceof Map)) {
            return false;
        }
        Object pieces = ((Map<String, Object>) loop).get("pieces");
        return pieces instanceof List && ((List<Map<String, Object>>) pieces).stream()
                .anyMatch(p -> "pollen-flower-far-4".equals(p.get("id")) && !GamePiece.FLOWER.equals(p.get("state")));
    }

    /**
     * WHAT: Unity reports the robot (no intake) away from the far FLOWER and then pressed against its head; no frame
     * says the bottom ball left the FLOWER.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unitysRobotWithNoIntakeGetsNoPollenOut() {
        send("{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\", \"physics\": \"unity\"}");
        send("{\"type\": \"init\"}");
        Map<String, Object> played = send("{\"type\": \"play\", \"physicsState\": " + at(-40, -21.39) + "}");
        // From x = -40 to where the robot's 18 in box touches the FLOWER's head (3.13 in from its center), 0.5 in
        // per step, then a second standing there.
        boolean released = leftTheFlower(played);
        int frames = 0;
        for (double x = -40; frames < 100; x = Math.max(x - 0.5, -68.04 + 9 + 3.13)) {
            frames++;
            Map<String, Object> frame = send("{\"type\": \"step\", \"physicsState\": " + at(x, -21.39) + "}");
            assertEquals("frame", frame.get("type"), String.valueOf(frame));
            released = released || leftTheFlower(frame);
        }
        assertFalse(released, "no frame should say the bottom ball left the FLOWER");
    }
}
