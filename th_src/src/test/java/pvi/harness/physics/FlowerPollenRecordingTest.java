package pvi.harness.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.FakeHardwareMap;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole run with real motors and physics: a test robot with an intake drives up to the far FLOWER, the head
 * stops it, its intake takes the bottom POLLEN out through the Retrieval Opening, and the recording shows it.
 * Saved as {@code build/recordings/flower-pollen-release.json} to watch.
 *
 * <p>The robot is the settings file's, with a made-up intake (a temporary robot profile), and the OpMode here sets
 * the motor directions from the settings file, so it doesn't depend on any team's code.</p>
 */
class FlowerPollenRecordingTest {
    private static final String ROBOT_ID = "flower-recording-test";
    private static final String MECHANISM = """
            intake.motor=testIntake
            intake.direction=FORWARD
            intake.minPower=0.1 | placeholder | test
            intake.forwardInches=10 | placeholder | test
            intake.leftInches=0 | placeholder | test
            intake.lengthInches=3 | placeholder | test
            intake.widthInches=10 | placeholder | test
            intake.spitSeconds=0.3 | placeholder | test
            intake.spitInchesPerSecond=30 | placeholder | test
            hold.capacity=1 | placeholder | test
            hold.accepts=pollen
            hold.preloaded=0 | placeholder | test
            launcher.motor=
            """;
    private static final Pose START = Pose.inches(-35, -21.39, 180);
    private static final double FORWARD_SECONDS = 2.0;
    private static final double RUN_SECONDS = 5.0;

    @TempDir
    Path robotsFolder;

    /** The settings file's robot, with the intake above. */
    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), MECHANISM);
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testIntake"), "test", robotsFolder);
    }

    /** Runs the intake, drives forward at a steady power for a while, then back. */
    private static final class DriveToTheFlowerAndBack extends OpMode {
        private final HarnessConfig config;
        private double startedSeconds = -1;

        DriveToTheFlowerAndBack(HarnessConfig config) {
            this.config = config;
        }

        @Override
        public void init() {
            hardwareMap.get(DcMotor.class, "testIntake").setDirection(DcMotorSimple.Direction.FORWARD);
        }

        private void drive(String name, DcMotorSimple.Direction direction, double power) {
            DcMotor motor = hardwareMap.get(DcMotor.class, name);
            motor.setDirection(direction);
            motor.setPower(power);
        }

        @Override
        public void loop() {
            if (startedSeconds < 0) {
                startedSeconds = time;
            }
            hardwareMap.get(DcMotor.class, "testIntake").setPower(1.0);
            double power = time - startedSeconds < FORWARD_SECONDS ? 0.4 : -0.4;
            drive(config.driveMotors().frontLeft(), config.driveDirections().frontLeft(), power);
            drive(config.driveMotors().frontRight(), config.driveDirections().frontRight(), power);
            drive(config.driveMotors().rearLeft(), config.driveDirections().rearLeft(), power);
            drive(config.driveMotors().rearRight(), config.driveDirections().rearRight(), power);
        }
    }

    /**
     * WHAT: The intake takes the bottom POLLEN of the far FLOWER out during the run and the robot ends holding it;
     * the other 3 drop by one ball and stay in the FLOWER; the recording's loops say so.
     *
     * <p>WHY: This is what displays read, and it goes through the real estimator, the head collision and the
     * intake's motor.</p>
     */
    @Test
    void anIntakeTakesAPollenOutAndTheRecordingShowsIt() throws IOException {
        HarnessConfig config = config();
        FakeHardwareMap robot = RobotConfigs.robot(config);
        OpModeRunner runner = new OpModeRunner(new DriveToTheFlowerAndBack(config), robot);
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config), START));
        RunRecorder recorder = RunRecorder.attachTo(runner);

        runner.run(0.1, RUN_SECONDS).assertNoException();
        Path saved = recorder.save("flower-pollen-release");
        System.out.println("Recording to watch: " + saved);

        RunRecording recording = recorder.recording();
        List<GamePiece> taken = recording.loops().stream().flatMap(l -> l.pieces().stream())
                .filter(p -> p.id().equals("pollen-flower-far-4") && p.state().equals(GamePiece.HELD)).toList();
        assertTrue(taken.size() > 5, "the recording shows the ball held on the robot: " + taken.size());

        GamePieces pieces = runner.positionEstimator().pieces();
        assertEquals(GamePiece.HELD, stateOf(pieces, "pollen-flower-far-4"));
        for (int i = 1; i <= 3; i++) {
            assertEquals(GamePiece.FLOWER, stateOf(pieces, "pollen-flower-far-" + i));
        }
        assertEquals(1.4, zOf(pieces, "pollen-flower-far-3"), 1e-9, "dropped by one ball");
        assertEquals(GamePiece.FLOWER, stateOf(pieces, "pollen-flower-audience-4"), "the other FLOWERs don't change");
        assertTrue(runner.positionEstimator().pose().xInches() > -55, "the robot backed away");
    }

    private static String stateOf(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().state();
    }

    private static double zOf(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().zInches();
    }
}
