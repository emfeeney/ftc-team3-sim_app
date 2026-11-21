package pvi.harness.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.FakeDcMotorEx;
import pvi.harness.hardware.FakeHardwareMap;
import pvi.harness.hardware.MechanismSetup;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real path from a motor the robot code runs to a ball being taken, through the runner and the
 * estimator. The OpMode here sets the motor directions from the settings, so it doesn't depend on any
 * team's drive code. Saves {@code build/recordings/intake-hold.json} to watch.
 */
class MechanismReaderTest {
    private static final String ROBOT_ID = "intake-test";
    private static final String FILE = """
            intake.motor=testIntake
            intake.direction=REVERSE
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
    /** Faces a line of 4 POLLEN in the red GARDEN, along the audience wall. */
    private static final Pose START = Pose.inches(45, -66, 0);

    @TempDir
    Path robotsFolder;

    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), FILE);
        String text = Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testIntake");
        return HarnessConfig.fromText(text, "test", robotsFolder);
    }

    /** Drives forward at a steady power, with the intake at this power (it sets the REVERSE direction, as KimLauncher does). */
    private static final class DriveWithIntake extends OpMode {
        private final HarnessConfig config;
        private final double intakePower;

        DriveWithIntake(HarnessConfig config, double intakePower) {
            this.config = config;
            this.intakePower = intakePower;
        }

        @Override
        public void init() {
            DcMotor intake = hardwareMap.get(DcMotor.class, "testIntake");
            intake.setDirection(Direction.REVERSE);
        }

        private void drive(String name, Direction direction) {
            DcMotor motor = hardwareMap.get(DcMotor.class, name);
            motor.setDirection(direction);
            motor.setPower(0.3);
        }

        @Override
        public void loop() {
            hardwareMap.get(DcMotor.class, "testIntake").setPower(intakePower);
            drive(config.driveMotors().frontLeft(), config.driveDirections().frontLeft());
            drive(config.driveMotors().frontRight(), config.driveDirections().frontRight());
            drive(config.driveMotors().rearLeft(), config.driveDirections().rearLeft());
            drive(config.driveMotors().rearRight(), config.driveDirections().rearRight());
        }
    }

    private RunRecorder run(double intakePower, String save) throws IOException {
        HarnessConfig config = config();
        OpModeRunner runner = new OpModeRunner(new DriveWithIntake(config, intakePower), RobotConfigs.robot(config));
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config), START));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 1.5).assertNoException();
        if (save != null) {
            System.out.println("Recording to watch: " + recorder.save(save));
        }
        return recorder;
    }

    /**
     * WHAT: A whole run: a pre-load starts held in the recording's header, the robot drives into the
     * GARDEN with its intake running, and the recording shows balls becoming held and riding along.
     */
    @Test
    void aRunTakesBallsAndTheRecordingShowsIt() throws IOException {
        RunRecording recording = run(0.8, "intake-hold").recording();

        List<GamePiece> first = recording.header().pieces();
        assertEquals(GamePiece.HELD, stateIn(first, "pollen-preload-1"));
        assertEquals(GamePiece.FIELD, stateIn(first, "pollen-preload-2"), "only one is pre-loaded");
        assertEquals(GamePiece.FIELD, stateIn(first, "pollen-garden-red-2"));

        List<GamePiece> changes = recording.loops().stream().flatMap(l -> l.pieces().stream()).toList();
        assertTrue(changes.stream().anyMatch(p -> p.id().startsWith("pollen-garden-red") && p.state().equals(GamePiece.HELD)),
                "a GARDEN ball was taken");
        assertTrue(changes.stream().filter(p -> p.id().equals("pollen-preload-1")).count() > 20, "the held ball rides along");
    }

    /** WHAT: The same drive with the intake off takes nothing: the motor really decides. */
    @Test
    void theIntakeOffTakesNothing() throws IOException {
        RunRecording recording = run(0.0, null).recording();

        assertTrue(recording.loops().stream().flatMap(l -> l.pieces().stream())
                .noneMatch(p -> p.id().startsWith("pollen-garden-red") && p.state().equals(GamePiece.HELD)));
    }

    private static String stateIn(List<GamePiece> pieces, String id) {
        return pieces.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().state();
    }

    private static MechanismSetup setupWith(Direction direction) {
        ModelValue guess = ModelValue.placeholder(1, "test", "test");
        return new MechanismSetup(new MechanismSetup.Intake("intake", direction, guess, guess, guess, guess, guess, guess, guess),
                new MechanismSetup.Hold(guess, List.of(GamePiece.POLLEN), guess), null);
    }

    /**
     * WHAT: Inward means what the file says, whichever way the code writes it. Hive's file says REVERSE,
     * and the same physical way round is REVERSE with +power (KimLauncher) or FORWARD with -power (TeleopV1).
     */
    @Test
    void inwardFollowsTheFilesDirection() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeDcMotorEx motor = robot.addMotor("intake");
        MechanismReader reader = new MechanismReader(setupWith(Direction.REVERSE));

        motor.setDirection(Direction.REVERSE);
        motor.setPower(0.8);
        assertEquals(0.8, reader.inwardIntakePower(robot), 1e-9, "KimLauncher's way");
        motor.setDirection(Direction.FORWARD);
        motor.setPower(-0.8);
        assertEquals(0.8, reader.inwardIntakePower(robot), 1e-9, "TeleopV1's way");
        motor.setPower(0.5);
        assertEquals(-0.5, reader.inwardIntakePower(robot), 1e-9, "the other way is outward");

        motor.setDirection(Direction.FORWARD);
        assertEquals(0.5, new MechanismReader(setupWith(Direction.FORWARD)).inwardIntakePower(robot), 1e-9);
    }

    /** WHAT: Swapped wires reverse it, whatever the code set, and a disabled or missing motor does nothing. */
    @Test
    void wiresAndMissingMotors() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeDcMotorEx motor = robot.addMotor("intake");
        MechanismReader reader = new MechanismReader(setupWith(Direction.FORWARD));
        motor.setPower(0.5);

        motor.setMiswired(true);
        assertEquals(-0.5, reader.inwardIntakePower(robot), 1e-9);
        motor.setMiswired(false);
        motor.setMotorDisable();
        assertEquals(0.0, reader.inwardIntakePower(robot), 1e-9);
        assertEquals(0.0, reader.inwardIntakePower(new FakeHardwareMap()), 1e-9);
        assertEquals(0.0, new MechanismReader(new MechanismSetup(null, setupWith(Direction.FORWARD).hold(), null))
                .inwardIntakePower(robot), 1e-9);
    }
}
