package pvi.harness.hardware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D47: {@code limelight.<name>.camera=<webcam>} in a robot's camera file makes that Limelight see the
 * AprilTags in the webcam's pictures, in every run (Live, recordings, Scenarios), with no test wiring.
 */
class LimelightCameraLinkTest {

    /** A robot with one Limelight and one webcam, plus {@code extraCameraLines} in its camera file. */
    private static HarnessConfig profile(Path robotsFolder, String extraCameraLines) throws IOException {
        String text = Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replace("devices.limelights=\n", "devices.limelights=limelight\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=limelight-test");
        Path folder = Files.createDirectories(robotsFolder.resolve("limelight-test"));
        StringBuilder camera = new StringBuilder("devices.webcams=camera\n");
        String[] values = {"5", "0", "10", "0", "0", "0", "60", "45", "640", "480", "100", "60"};
        for (int i = 0; i < values.length; i++) {
            camera.append("webcam.camera.").append(CameraSetup.FACT_NAMES.get(i)).append('=').append(values[i])
                    .append(" | placeholder | test camera\n");
        }
        camera.append("webcam.camera.aprilTags=script\n").append(extraCameraLines);
        Files.writeString(folder.resolve(HarnessConfig.CAMERA_FILE_NAME), camera.toString());
        return HarnessConfig.fromText(text, "limelight-test", robotsFolder);
    }

    /** Runs {@code LimelightAim} (which reads the Limelight's tags) for 1 s with tag 20 in front of the camera. */
    private static String tagsSeen(HarnessConfig config) {
        FakeHardwareMap robot = RobotConfigs.robot(config);
        robot.webcam("camera").script(new AprilTagScript().duringInit().at(0.0).see(SeenAprilTag.straightAhead(20)));
        OpModeRunner runner = new OpModeRunner(new MoreSensorFakesTest.LimelightAim(), robot).init().start();
        runner.loopFor(1.0);
        String tags = runner.telemetry().lastValue("Tags");
        return tags == null ? "none" : tags;
    }

    /** WHAT: With the key, the Limelight sees the tag in front of its camera, with no hand wiring. */
    @Test
    void aLinkedLimelightSeesItsCamerasTags(@TempDir Path robotsFolder) throws IOException {
        HarnessConfig config = profile(robotsFolder, "limelight.limelight.camera=camera\n");

        assertEquals(java.util.Map.of("limelight", "camera"), config.limelightCameras());
        assertEquals("20", tagsSeen(config));
    }

    /** WHAT: Without the key, the same robot's Limelight sees no tags (today's behavior). */
    @Test
    void withoutTheKeyTheLimelightSeesNothing(@TempDir Path robotsFolder) throws IOException {
        assertEquals("none", tagsSeen(profile(robotsFolder, "")));
    }

    /** WHAT: A key naming a webcam or Limelight the robot doesn't have is rejected, naming the key. */
    @Test
    void aLinkToAMissingDeviceIsRejected(@TempDir Path robotsFolder) {
        IllegalArgumentException badWebcam = assertThrows(IllegalArgumentException.class,
                () -> profile(robotsFolder, "limelight.limelight.camera=noSuchCamera\n"));
        assertTrue(badWebcam.getMessage().contains("limelight.limelight.camera: no webcam \"noSuchCamera\""),
                badWebcam.getMessage());

        IllegalArgumentException badLimelight = assertThrows(IllegalArgumentException.class,
                () -> profile(robotsFolder, "limelight.front.camera=camera\n"));
        assertTrue(badLimelight.getMessage().contains("limelight.front.camera: no Limelight \"front\""),
                badLimelight.getMessage());
    }

    /** WHAT: A test's own {@code setView} unlinks the camera, so the test's view stays. */
    @Test
    void setViewTakesOverFromTheCamera() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeLimelight limelight = robot.addLimelight("limelight");
        FakeWebcam camera = robot.addWebcam("camera");
        camera.script(new AprilTagScript().duringInit().at(0.0).see(SeenAprilTag.straightAhead(20)));
        limelight.seeThrough(camera).setView(LimelightView.tags(SeenAprilTag.straightAhead(24)));
        OpModeRunner runner = new OpModeRunner(new MoreSensorFakesTest.LimelightAim(), robot).init().start();
        runner.loopFor(1.0);

        assertEquals("24", runner.telemetry().lastValue("Tags"));
    }
}
