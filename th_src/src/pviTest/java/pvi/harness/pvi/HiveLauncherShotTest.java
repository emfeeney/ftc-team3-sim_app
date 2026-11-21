package pvi.harness.pvi;

import java.util.List;

import org.junit.jupiter.api.Test;

import pvi.harness.ds.Button;
import pvi.harness.ds.GamepadScript;
import pvi.harness.hardware.FakeHardwareMap;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.Pose;
import pvi.harness.physics.PoseEstimator;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeCatalog;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PVI extra: Hive Launcher Test on the Hive robot (D26), firing its pre-loads through the real KimLauncher.
 * Saves {@code build/recordings/hive-launcher-shot.json} to watch.
 *
 * <p><b>Every number behind these shots is a guess</b> (robots/hive/mechanism.properties): the launcher
 * motor, wheel, launch height and angle aren't measured. These tests check that the code runs and the
 * balls fly; where they land says nothing about the real robot.</p>
 */
class HiveLauncherShotTest {
    /** Facing the HIVE's red side, where Hive Auto launches from (HiveField's LAUNCH_X_INCHES). */
    private static final Pose START = Pose.inches(52, -12.75, 180);

    private static OpModeCatalog.Entry hiveLauncherTest() {
        return OpModeCatalog.fromConfig(HarnessConfig.load()).stream()
                .filter(e -> e.simpleName().equals("HiveLauncherTest")).findFirst().orElseThrow();
    }

    /** WHAT: Hive Launcher Test runs on the Hive robot now, with its pollen and intake motors (D26). */
    @Test
    void hiveLauncherTestIsOnTheHiveRobot() {
        FakeHardwareMap robot = RobotConfigs.forOpMode(hiveLauncherTest(), HarnessConfig.load());

        assertEquals("hive", robot.configName());
        assertTrue(robot.motor("pollen") != null && robot.motor("intake") != null);
    }

    /**
     * WHAT: A wheel on (A), then 4 shots (Y): KimLauncher spins up, feeds four times, and all four
     * pre-loads leave the robot, fly, and land loose. (Since flying balls hit other balls, W-PG, a later shot
     * can knock a landed ball back to the robot, where the running intake takes it from the tiles again; so
     * this checks that each one landed, not where it ends.)
     */
    @Test
    void fourShotsFlyAndLand() {
        GamepadScript driver = new GamepadScript()
                .at(0.2).press(Button.A).at(0.3).release(Button.A)
                .at(2.0).press(Button.Y).at(2.1).release(Button.Y);
        OpModeRunner runner = new OpModeRunner(hiveLauncherTest().newOpMode(),
                RobotConfigs.forOpMode(hiveLauncherTest(), HarnessConfig.load())).gamepad1(driver);
        PoseEstimator estimator = runner.positionEstimator();
        runner.usePositionEstimator(new PoseEstimator(estimator.model(), estimatorWheels(runner), START));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 8.0).assertNoException();
        System.out.println("Recording to watch: " + recorder.save("hive-launcher-shot"));

        RunRecording recording = recorder.recording();
        List<String> preloads = List.of("pollen-preload-1", "pollen-preload-2", "pollen-preload-3", "pollen-preload-4");
        for (String id : preloads) {
            List<String> states = recording.loops().stream().flatMap(l -> l.pieces().stream())
                    .filter(p -> p.id().equals(id)).map(GamePiece::state).toList();
            int flew = states.indexOf(GamePiece.FLYING);
            assertTrue(flew >= 0, id + " flew");
            assertTrue(states.subList(flew, states.size()).contains(GamePiece.FIELD), id + " landed: " + states);
        }
    }

    private static pvi.harness.physics.WheelReader estimatorWheels(OpModeRunner runner) {
        return pvi.harness.physics.WheelReader.fromConfig(runner.hardwareMap().settings());
    }
}
