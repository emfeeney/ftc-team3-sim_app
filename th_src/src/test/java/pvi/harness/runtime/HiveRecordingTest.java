package pvi.harness.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.DriveModels;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.physics.PoseEstimator;
import pvi.harness.physics.WheelReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recording format version 6: each HIVE's tilt in the header and every loop, and CELL balls at their
 * true position. A test robot (not any team's code) shoots its 4 pre-loads into the red upward CELL,
 * which tips the HIVE and spills it. Saves {@code build/recordings/hive-tip-spill.json}, and
 * {@code build/recordings/intake-spit.json}, where an intake takes a ball and spits it back out.
 */
class HiveRecordingTest {
    private static final String ROBOT_ID = "hive-recording-test";
    private static final String LAUNCHER = """
            launcher.motor=testWheel
            launcher.direction=FORWARD
            launcher.ticksPerRev=28 | placeholder | test
            launcher.freeSpeedRpm=6000 | placeholder | test
            launcher.spinUpSeconds=0.3 | placeholder | test
            launcher.speedLossPerShot=0 | placeholder | test
            launcher.wheelDiameterInches=4 | placeholder | test
            launcher.exitFraction=0.5 | placeholder | test
            launcher.minBallInchesPerSecond=20 | placeholder | test
            feed.motor=testFeed
            feed.direction=FORWARD
            feed.minPower=0.1 | placeholder | test
            feed.secondsPerBall=0.3 | placeholder | test
            launch.forwardInches=6 | placeholder | test
            launch.leftInches=0 | placeholder | test
            launch.upInches=14 | placeholder | test
            launch.pitchDegrees=60 | placeholder | test
            launch.yawDegrees=0 | placeholder | test
            """;
    private static final String INTAKE = """
            intake.motor=testFeed
            intake.direction=FORWARD
            intake.minPower=0.1 | placeholder | test
            intake.forwardInches=10 | placeholder | test
            intake.leftInches=0 | placeholder | test
            intake.lengthInches=3 | placeholder | test
            intake.widthInches=10 | placeholder | test
            intake.spitSeconds=0.3 | placeholder | test
            intake.spitInchesPerSecond=30 | placeholder | test
            """;
    /** The wheel speed that throws at 250 in/s, which goes into the red CELL from (58, -12.75) facing -x. */
    private static final double WHEEL_TICKS_PER_SECOND = 250 / (Math.PI * 4 * 0.5) * 28;

    @TempDir
    Path robotsFolder;

    private HarnessConfig config(String mechanism) throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), mechanism);
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testWheel, testFeed"), "test", robotsFolder);
    }

    /** Spins the wheel and feeds for 0.32 s every 0.8 s, from 2 s on, four times. */
    private static final class ShootFour extends OpMode {
        private int loops;

        @Override
        public void init() {
            hardwareMap.get(DcMotorEx.class, "testWheel").setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            hardwareMap.get(DcMotor.class, "testFeed").setDirection(Direction.FORWARD);
        }

        @Override
        public void loop() {
            loops++;
            hardwareMap.get(DcMotorEx.class, "testWheel").setVelocity(WHEEL_TICKS_PER_SECOND);
            int sinceFirst = loops - 100;
            boolean feeding = sinceFirst >= 0 && sinceFirst % 40 < 16 && sinceFirst < 160;
            hardwareMap.get(DcMotor.class, "testFeed").setPower(feeding ? 1.0 : 0.0);
        }
    }

    private RunRecording shootFour(String save) throws IOException {
        HarnessConfig config = config("intake.motor=\nhold.capacity=4 | placeholder | test\nhold.accepts=pollen\n"
                + "hold.preloaded=4 | placeholder | test\n" + LAUNCHER);
        OpModeRunner runner = new OpModeRunner(new ShootFour(), RobotConfigs.robot(config));
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config),
                Pose.inches(58, -12.75, 180)));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 8.0).assertNoException();
        if (save != null) {
            System.out.println("Recording to watch: " + recorder.save(save));
        }
        return recorder.recording();
    }

    private static HiveTilt red(List<HiveTilt> hives) {
        return hives.stream().filter(h -> h.id().equals("red-hive")).findFirst().orElseThrow();
    }

    /**
     * WHAT: The recording says the red HIVE starts unswung, swings 60 degrees (+x side down) once the 4th
     * ball is in, counting one tip, and stays; the blue HIVE doesn't move; the CELL balls go down with the
     * CELL and spill out loose.
     */
    @Test
    void theRecordingShowsTheTipAndTheSpill() throws IOException {
        RunRecording recording = shootFour("hive-tip-spill");

        assertEquals(RunRecording.VERSION, recording.header().version());
        assertEquals(new HiveTilt("red-hive", 0, 0), red(recording.header().hives()), "at INIT");
        assertEquals(List.of("red-hive", "blue-hive"), recording.header().hives().stream().map(HiveTilt::id).toList());
        List<HiveTilt> red = recording.loops().stream().map(l -> red(l.hives())).toList();
        assertEquals(-60.0, red.get(red.size() - 1).swingDegrees(), 1e-9, "tipped by the end");
        assertEquals(1, red.get(red.size() - 1).tips());
        assertTrue(red.stream().anyMatch(t -> t.swingDegrees() < -5 && t.swingDegrees() > -55 && t.tips() == 1),
                "seen on its way over, already counted");
        assertTrue(recording.loops().stream().allMatch(l -> l.hives().get(1).equals(new HiveTilt("blue-hive", 0, 0))),
                "blue didn't move");

        List<GamePiece> nectar = recording.loops().stream().flatMap(l -> l.pieces().stream())
                .filter(p -> p.id().equals("red-nectar-cell-1")).toList();
        assertTrue(nectar.stream().anyMatch(p -> p.state().equals(GamePiece.CELL)), "moved with the CELL while in it");
        GamePiece firstOut = nectar.stream().filter(p -> p.state().equals(GamePiece.FLYING)).findFirst().orElseThrow();
        // Along the arm from the pivot at the tipped HIVE's tilt (-30): past the opening, 21.46 in.
        double tilt = Math.toRadians(-30);
        double along = firstOut.xInches() * Math.cos(tilt) + (firstOut.zInches() - 43.95) * Math.sin(tilt);
        assertTrue(along > 21.46, "spilled out through the opening: " + along + " in out along the arm");
        assertEquals(GamePiece.FIELD, nectar.get(nectar.size() - 1).state(), "and landed");
    }

    /** WHAT: Saved and read back, version 6 keeps the HIVEs' tilts in the header and every loop. */
    @Test
    void theTiltsSurviveSavingAndReading() throws IOException {
        RunRecording recording = shootFour(null);
        RunRecording back = RunRecordingJson.parse(RunRecordingJson.format(recording));

        assertEquals(recording.header().hives(), back.header().hives());
        for (int i = 0; i < recording.loops().size(); i += 37) {
            List<HiveTilt> want = recording.loops().get(i).hives();
            List<HiveTilt> got = back.loops().get(i).hives();
            assertEquals(want.size(), got.size());
            for (int k = 0; k < want.size(); k++) {
                assertEquals(want.get(k).id(), got.get(k).id());
                assertEquals(want.get(k).tips(), got.get(k).tips(), "loop " + i);
                assertEquals(want.get(k).swingDegrees(), got.get(k).swingDegrees(), 1e-6, "loop " + i);
            }
        }
    }

    /** Takes a GARDEN ball with the intake, then spits it back out. */
    private static final class TakeThenSpit extends OpMode {
        private final HarnessConfig config;
        private int loops;

        TakeThenSpit(HarnessConfig config) {
            this.config = config;
        }

        @Override
        public void init() {
            hardwareMap.get(DcMotor.class, "testFeed").setDirection(Direction.FORWARD);
        }

        @Override
        public void loop() {
            loops++;
            double drive = loops < 60 ? 0.3 : loops < 90 ? -0.3 : 0;
            hardwareMap.get(DcMotor.class, "testFeed").setPower(loops < 60 ? 1.0 : loops >= 100 ? -1.0 : 0);
            for (String[] motor : new String[][] {{config.driveMotors().frontLeft(), config.driveDirections().frontLeft().name()},
                    {config.driveMotors().frontRight(), config.driveDirections().frontRight().name()},
                    {config.driveMotors().rearLeft(), config.driveDirections().rearLeft().name()},
                    {config.driveMotors().rearRight(), config.driveDirections().rearRight().name()}}) {
                DcMotor m = hardwareMap.get(DcMotor.class, motor[0]);
                m.setDirection(Direction.valueOf(motor[1]));
                m.setPower(drive);
            }
        }
    }

    /** WHAT: A recording where the intake takes a GARDEN ball and later spits it out, loose. */
    @Test
    void anIntakeTakesAndSpitsABall() throws IOException {
        HarnessConfig config = config(INTAKE + "hold.capacity=4 | placeholder | test\nhold.accepts=pollen\n"
                + "hold.preloaded=0 | placeholder | test\nlauncher.motor=\n");
        OpModeRunner runner = new OpModeRunner(new TakeThenSpit(config), RobotConfigs.robot(config));
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config),
                Pose.inches(45, -66, 0)));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 4.0).assertNoException();
        System.out.println("Recording to watch: " + recorder.save("intake-spit"));

        List<GamePiece> taken = recorder.recording().loops().stream().flatMap(l -> l.pieces().stream())
                .filter(p -> p.id().startsWith("pollen-garden-red")).toList();
        String id = taken.stream().filter(p -> p.state().equals(GamePiece.HELD)).findFirst().orElseThrow().id();
        List<GamePiece> ball = taken.stream().filter(p -> p.id().equals(id)).toList();
        assertEquals(GamePiece.FIELD, ball.get(ball.size() - 1).state(), id + " was spat back out");
    }
}
