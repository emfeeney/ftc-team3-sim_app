package pvi.harness.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bridge version 7 (manager decision D39): each frame carries {@code plannedPaths}, the Pedro robot's
 * planned path each time it changed since the last frame, in the run recording's layout, so VI Arena's
 * Live mode can draw the yellow line too.
 */
class BridgePlannedPathsTest {
    private final BridgeSession session = new BridgeSession();

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    private void connectAndLoad(String opMode) {
        assertEquals("welcome", send("{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}").get("type"));
        assertEquals("loaded", send("{\"type\": \"load\", \"opMode\": \"" + opMode + "\"}").get("type"));
    }

    /** Every planned path in the frames of INIT, PLAY and {@code steps} loops after it. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> plansSent(int steps) {
        List<Map<String, Object>> plans = new ArrayList<>();
        List<Map<String, Object>> frames = new ArrayList<>();
        frames.add(send("{\"type\": \"init\"}"));
        frames.add(send("{\"type\": \"play\"}"));
        for (int i = 0; i < steps; i++) {
            frames.add(send("{\"type\": \"step\"}"));
        }
        for (Map<String, Object> frame : frames) {
            assertEquals("frame", frame.get("type"), String.valueOf(frame));
            assertTrue(frame.get("plannedPaths") instanceof List, "every frame has the list: " + frame.keySet());
            plans.addAll((List<Map<String, Object>>) frame.get("plannedPaths"));
        }
        return plans;
    }

    /**
     * WHAT: Every frame of the TeleOp carries "plannedPaths", a list. WHY: Unity reads it in every frame;
     * a robot without a plan sends an empty one.
     */
    @Test
    void everyFrameCarriesPlannedPaths() {
        connectAndLoad(BridgeTestOpModes.teleOp().id());
        plansSent(5);
    }

    /**
     * WHAT: Team A's Pedro pilot autonomous sends its plan once, as field points (x, y in inches), and no
     * frame sends an entry a second time (each comes later than the last).
     * WHY: Unity adds each frame's entries to its copy of the run, as for events; a repeat would be drawn again.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aPedroAutonomousSendsEachPlanOnce() {
        BridgeTestOpModes.assumePvi();
        connectAndLoad("TeamAPedroPilotAutoOpMode");
        List<Map<String, Object>> plans = plansSent(250);

        assertFalse(plans.isEmpty(), "the pilot route's plan came through");
        List<Object> points = (List<Object>) plans.get(0).get("points");
        assertTrue(points.size() > 2, "a sampled path: " + points.size());
        List<Object> first = (List<Object>) points.get(0);
        assertEquals(2, first.size(), "[xInches, yInches]");
        double last = -1;
        for (Map<String, Object> plan : plans) {
            double since = (Double) plan.get("sinceInitSeconds");
            assertTrue(since > last, "each entry once, in order: " + since + " after " + last);
            assertTrue(plan.get("matchTimeSeconds") instanceof Double);
            last = since;
        }
    }
}
