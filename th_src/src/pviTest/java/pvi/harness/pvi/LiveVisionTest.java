package pvi.harness.pvi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pvi.harness.bridge.BridgeProtocol;
import pvi.harness.bridge.BridgeSession;
import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PVI extra (D47): AprilTags reach robot code in a Live session (VI Arena through the bridge), with
 * no test wiring. The user found that in Live a Limelight never saw a tag.
 */
class LiveVisionTest {
    private final BridgeSession session = new BridgeSession();

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String line) {
        return (Map<String, Object>) JsonReader.parse(session.handle(line));
    }

    /** Loads {@code opMode} in Live, presses INIT and PLAY and runs {@code steps} loops; returns every frame. */
    private List<Map<String, Object>> live(String opMode, String loadExtras, String stepExtras, int steps) {
        assertEquals("welcome", send("{\"type\": \"hello\", \"version\": " + BridgeProtocol.VERSION + "}").get("type"));
        Map<String, Object> loaded = send("{\"type\": \"load\", \"opMode\": \"" + opMode + "\"" + loadExtras + "}");
        assertEquals("loaded", loaded.get("type"), String.valueOf(loaded));
        List<Map<String, Object>> frames = new ArrayList<>();
        frames.add(send("{\"type\": \"init\"" + stepExtras + "}"));
        frames.add(send("{\"type\": \"play\"" + stepExtras + "}"));
        for (int i = 0; i < steps; i++) {
            frames.add(send("{\"type\": \"step\"" + stepExtras + "}"));
        }
        return frames;
    }

    /** The cameras named in the frames' AprilTag lists. */
    @SuppressWarnings("unchecked")
    private static List<String> camerasSeeingTags(List<Map<String, Object>> frames) {
        List<String> cameras = new ArrayList<>();
        for (Map<String, Object> frame : frames) {
            Map<String, Object> loop = (Map<String, Object>) frame.get("loop");
            if (loop != null) {
                for (Map<String, Object> tag : (List<Map<String, Object>>) loop.get("aprilTags")) {
                    cameras.add((String) tag.get("camera"));
                }
            }
        }
        return cameras;
    }

    /**
     * WHAT: Claude Corner Lock Auto (Red) on the hive robot, run Live, reads raised CELL tags through
     * its Limelight, which sees the hive's limelightCamera ({@code limelight.limelight.camera}).
     */
    @Test
    void aLimelightSeesTagsInLive() {
        List<Map<String, Object>> frames = live("ClaudeCornerLockAutoRed", "", "", 1500);

        int most = 0;
        Matcher seen = Pattern.compile("Raised CELL tags seen\\s*:\\s*(\\d+)").matcher(frames.toString());
        while (seen.find()) {
            most = Math.max(most, Integer.parseInt(seen.group(1)));
        }
        assertTrue(most > 0, "the Auto's Limelight saw no raised CELL tag in 30 s of Live");
        assertTrue(camerasSeeingTags(frames).contains("limelightCamera"), "the camera's tags are in the frames");
    }

    /**
     * WHAT: Team 2's AprilTag Vision Test, whose VisionPortal uses the webcam, sees the field's red
     * tags in Live when Unity's physics holds the robot facing them.
     */
    @Test
    void aVisionPortalWebcamSeesTagsInLive() {
        // Toward the audience, facing the red tags, as in VisionFieldTagsRecordingTest.
        String facingRedTags = ", \"physicsState\": {\"xInches\": 55, \"yInches\": -12, \"headingRadians\": "
                + Math.PI + ", \"forwardInchesPerSecond\": 0, \"leftInchesPerSecond\": 0,"
                + " \"counterClockwiseRadiansPerSecond\": 0, \"wallContact\": \"\"}";
        List<Map<String, Object>> frames = live("TeamAAprilTagVisionTestOpMode", ", \"physics\": \"unity\"",
                facingRedTags, 50);

        assertTrue(camerasSeeingTags(frames).contains("logitechVisionWebcam"),
                "the webcam saw no tag in 1 s of Live facing the red tags");
    }
}
