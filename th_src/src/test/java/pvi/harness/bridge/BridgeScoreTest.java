package pvi.harness.bridge;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bridge version 8 (manager decision D50): match points. Every frame's loop carries the score at that moment; the
 * frame that ends the run carries the final one. With Unity's physics Unity moves its balls on after STOP and sends
 * them in "settle" messages until they're still, and the last answer has the final score.
 */
class BridgeScoreTest {
    private final BridgeSession session = new BridgeSession();

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    private static final String STILL = "{\"xInches\": 0, \"yInches\": 0, \"headingRadians\": 0, \"forwardInchesPerSecond\": 0,"
            + " \"leftInchesPerSecond\": 0, \"counterClockwiseRadiansPerSecond\": 0, \"wallContact\": \"\"}";

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String physics) {
        send("{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}");
        return send("{\"type\": \"load\", \"opMode\": \"" + BridgeTestOpModes.teleOp().id() + "\", \"physics\": \"" + physics + "\"}");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loopScore(Map<String, Object> frame) {
        return (Map<String, Object>) ((Map<String, Object>) frame.get("loop")).get("score");
    }

    /**
     * WHAT: With the harness's physics every frame's loop has a score (red and blue totals), the frames while it
     * runs have no top-level score, and the frame answering "stop" has the final one.
     * WHY: VI Arena shows the score live and the final one after STOP (D50), the same as a replay.
     */
    @Test
    void framesCarryTheScoreAndStopGivesTheFinalOne() {
        load("harness");
        Map<String, Object> init = send("{\"type\": \"init\"}");
        send("{\"type\": \"play\"}");
        Map<String, Object> step = send("{\"type\": \"step\"}");
        Map<String, Object> stop = send("{\"type\": \"stop\"}");

        for (Map<String, Object> frame : List.of(init, step, stop)) {
            assertTrue(loopScore(frame).get("red") instanceof Map, "a score in every loop: " + frame.keySet());
        }
        assertTrue(step.containsKey("score") && step.get("score") == null, "no final score while it runs");
        assertEquals(Boolean.TRUE, stop.get("finished"));
        @SuppressWarnings("unchecked")
        Map<String, Object> score = (Map<String, Object>) stop.get("score");
        assertEquals("TELEOP", score.get("period"));
        assertTrue(((Map<?, ?>) score.get("blue")).get("total") instanceof Double);
    }

    /**
     * WHAT: With Unity's physics the stop frame has no final score; each "settle" takes Unity's balls, answering
     * "settling" with done false while a ball still moves, and done true with the final score once they're still:
     * slower than 0.5 in/s counts, since PhysX leaves resting balls a hair above 0.
     * WHY: D50 option B: the balls are Unity's to move after STOP too, so the end items are scored from them.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unityPhysicsSettlesTheBallsAfterStop() {
        Map<String, Object> loaded = load("unity");
        List<Object> pieces = (List<Object>) ((Map<String, Object>) loaded.get("header")).get("pieces");
        Map<String, Object> ball = (Map<String, Object>) pieces.stream()
                .filter(p -> "field".equals(((Map<String, Object>) p).get("state"))).findFirst().orElseThrow();
        String at = "\"id\": \"" + ball.get("id") + "\", \"xInches\": " + ball.get("xInches") + ", \"yInches\": "
                + ball.get("yInches") + ", \"zInches\": " + ball.get("zInches");
        send("{\"type\": \"init\"}");
        send("{\"type\": \"play\", \"physicsState\": " + STILL + "}");
        Map<String, Object> stop = send("{\"type\": \"stop\"}");
        assertNull(stop.get("score"), "no final score yet: Unity still moves the balls");

        Map<String, Object> rolling = send("{\"type\": \"settle\", \"pieces\": [{" + at
                + ", \"vxInchesPerSecond\": 20, \"vyInchesPerSecond\": 0, \"vzInchesPerSecond\": 0}]}");
        assertEquals("settling", rolling.get("type"), String.valueOf(rolling));
        assertEquals(Boolean.FALSE, rolling.get("done"));
        assertEquals(0.02, (Double) rolling.get("settleSeconds"), 1e-9);

        Map<String, Object> still = send("{\"type\": \"settle\", \"pieces\": [{" + at
                + ", \"vxInchesPerSecond\": 0.2, \"vyInchesPerSecond\": 0.1, \"vzInchesPerSecond\": 0}]}");
        assertEquals(Boolean.TRUE, still.get("done"), "0.2 in/s is still: " + still);
        Map<String, Object> score = (Map<String, Object>) still.get("score");
        assertEquals(Boolean.TRUE, score.get("settled"));
        assertEquals(0.04, (Double) score.get("settleSeconds"), 1e-9);
    }

    /** WHAT: "settle" is refused with the harness's own physics, and before the run has ended. */
    @Test
    void settleIsOnlyForUnityPhysicsAfterStop() {
        load("unity");
        send("{\"type\": \"init\"}");
        assertEquals("error", send("{\"type\": \"settle\", \"pieces\": []}").get("type"), "before STOP");

        load("harness");
        send("{\"type\": \"init\"}");
        send("{\"type\": \"stop\"}");
        Map<String, Object> refused = send("{\"type\": \"settle\", \"pieces\": []}");
        assertEquals("error", refused.get("type"));
        assertTrue(String.valueOf(refused.get("message")).contains("Unity's physics only"), String.valueOf(refused));
    }
}
