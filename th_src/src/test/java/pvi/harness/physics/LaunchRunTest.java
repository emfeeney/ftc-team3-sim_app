package pvi.harness.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
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
 * The real path from the motors the robot code runs to a ball in the air: the wheel's encoder sets the
 * ball's speed, the feed motor pushes it, and the wheel slows when it fires. The OpMode here is a small
 * test one, so this doesn't depend on any team's code. Saves {@code build/recordings/launch-shot.json}.
 */
class LaunchRunTest {
    private static final String ROBOT_ID = "launch-test";
    private static final String FILE = """
            intake.motor=
            hold.capacity=4 | placeholder | test
            hold.accepts=pollen
            hold.preloaded=2 | placeholder | test
            launcher.motor=testWheel
            launcher.direction=REVERSE
            launcher.ticksPerRev=28 | placeholder | test
            launcher.freeSpeedRpm=6000 | placeholder | test
            launcher.spinUpSeconds=0.3 | placeholder | test
            launcher.speedLossPerShot=0.2 | placeholder | test
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
            launch.pitchDegrees=50 | placeholder | test
            launch.yawDegrees=0 | placeholder | test
            """;
    private static final Pose START = Pose.inches(-40, -40, Math.toDegrees(Math.PI / 4));
    /** The wheel's speed the OpMode asks for, ticks per second: 6 wheel turns a second less than full. */
    private static final double WHEEL_TICKS_PER_SECOND = 600;

    @TempDir
    Path robotsFolder;

    private HarnessConfig config() throws IOException {
        Files.createDirectories(robotsFolder.resolve(ROBOT_ID));
        Files.writeString(robotsFolder.resolve(ROBOT_ID).resolve(HarnessConfig.MECHANISM_FILE_NAME), FILE);
        String text = Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testWheel, testFeed");
        return HarnessConfig.fromText(text, "test", robotsFolder);
    }

    /** Spins the wheel the launching way (REVERSE, as the file says), then feeds from 1.5 s to 2.0 s. */
    private static final class SpinThenFeed extends OpMode {
        private final double feedFromSeconds;
        private double startedSeconds = -1;

        SpinThenFeed(double feedFromSeconds) {
            this.feedFromSeconds = feedFromSeconds;
        }

        @Override
        public void init() {
            DcMotorEx wheel = hardwareMap.get(DcMotorEx.class, "testWheel");
            wheel.setDirection(Direction.REVERSE);
            wheel.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }

        @Override
        public void loop() {
            if (startedSeconds < 0) {
                startedSeconds = time;
            }
            hardwareMap.get(DcMotorEx.class, "testWheel").setVelocity(WHEEL_TICKS_PER_SECOND);
            double sincePlay = time - startedSeconds;
            hardwareMap.get(DcMotor.class, "testFeed").setPower(sincePlay >= feedFromSeconds && sincePlay < feedFromSeconds + 0.5 ? 0.8 : 0);
        }
    }

    /** A run; the wheel's speed after every loop goes into {@code wheelSpeeds}. */
    private RunRecorder run(double feedFromSeconds, List<Double> wheelSpeeds, String save) throws IOException {
        HarnessConfig config = config();
        FakeHardwareMap robot = RobotConfigs.robot(config);
        OpModeRunner runner = new OpModeRunner(new SpinThenFeed(feedFromSeconds), robot);
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config), START));
        runner.everyLoop(r -> wheelSpeeds.add(robot.motor("testWheel").getVelocity()));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 4.0).assertNoException();
        if (save != null) {
            System.out.println("Recording to watch: " + recorder.save(save));
        }
        return recorder;
    }

    /**
     * WHAT: A whole run: the first pre-load leaves once the feed has run, flies higher than where it
     * left, and lands loose; the recording shows it flying; the second stays held (one feed, one ball).
     */
    @Test
    void aRunLaunchesABallAndTheRecordingShowsIt() throws IOException {
        RunRecording recording = run(1.5, new ArrayList<>(), "launch-shot").recording();

        List<GamePiece> first = recording.loops().stream().flatMap(l -> l.pieces().stream())
                .filter(p -> p.id().equals("pollen-preload-1")).toList();
        assertTrue(first.stream().filter(p -> p.state().equals(GamePiece.FLYING)).count() > 5, "seen flying");
        assertTrue(first.stream().mapToDouble(GamePiece::zInches).max().orElse(0) > 14 + 5, "it rose above the launch point");
        assertEquals(GamePiece.FIELD, first.get(first.size() - 1).state(), "landed");
        assertTrue(recording.loops().stream().flatMap(l -> l.pieces().stream())
                .filter(p -> p.id().equals("pollen-preload-2")).allMatch(p -> p.state().equals(GamePiece.HELD)), "the second stays held");
    }

    /** WHAT: The shot takes launcher.speedLossPerShot of the wheel's speed, and the wheel speeds back up. */
    @Test
    void theShotSlowsTheWheel() throws IOException {
        List<Double> speeds = new ArrayList<>();
        run(1.5, speeds, null);

        double biggestDrop = 0;
        int dropAt = 0;
        for (int i = 1; i < speeds.size(); i++) {
            double drop = speeds.get(i - 1) - speeds.get(i);
            if (drop > biggestDrop) {
                biggestDrop = drop;
                dropAt = i;
            }
        }
        double before = speeds.get(dropAt - 1);
        // The wheel also speeds back up a little within that loop, so the drop is a bit under 20%.
        assertEquals(0.2 * before, biggestDrop, 0.08 * before, "about 20% of " + before);
        assertEquals(WHEEL_TICKS_PER_SECOND, speeds.get(speeds.size() - 1), 5.0, "back up to speed by the end");
    }

    /** WHAT: Feeding before the wheel has spun up throws the ball shorter than feeding after. */
    @Test
    void aSlowWheelGivesAShortShot() throws IOException {
        double early = landingDistance(run(0.0, new ArrayList<>(), null).recording());
        double late = landingDistance(run(1.5, new ArrayList<>(), null).recording());
        assertTrue(early < 0.8 * late, "early " + early + " in, late " + late + " in");
    }

    /** How far from the robot's start the first pre-load first touched down, along the floor. */
    private static double landingDistance(RunRecording recording) {
        boolean flew = false;
        for (RunRecording.LoopRecord loop : recording.loops()) {
            for (GamePiece p : loop.pieces()) {
                if (!p.id().equals("pollen-preload-1")) {
                    continue;
                }
                flew = flew || p.state().equals(GamePiece.FLYING);
                if (flew && p.state().equals(GamePiece.FIELD)) {
                    return Math.hypot(p.xInches() - START.xInches(), p.yInches() - START.yInches());
                }
            }
        }
        throw new AssertionError("it never landed");
    }

    private static MechanismSetup setupWith(Direction launcherDirection) {
        ModelValue one = ModelValue.placeholder(1, "test", "test");
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", launcherDirection,
                ModelValue.placeholder(28, "test", "test"), one, one, ModelValue.placeholder(0.25, "test", "test"),
                ModelValue.placeholder(4, "test", "test"), ModelValue.placeholder(0.5, "test", "test"), one, "feed",
                Direction.FORWARD, one, one, one, one, one, one, one);
        return new MechanismSetup(null, new MechanismSetup.Hold(one, List.of(GamePiece.POLLEN), one), launcher);
    }

    /**
     * WHAT: The ball speed is the wheel's real (encoder) speed as edge speed times exitFraction, positive
     * the launching way: 2800 ticks/s on 28 ticks a turn is 100 turns/s, a 4 in wheel's edge is 400π in/s,
     * and half of that is 200π.
     */
    @Test
    void theBallSpeedComesFromTheWheelsEncoder() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeDcMotorEx wheel = robot.addMotor("wheel").setEncoderModel(28, 6000);
        MechanismReader reader = new MechanismReader(setupWith(Direction.REVERSE));
        wheel.setDirection(Direction.REVERSE);
        wheel.setPower(1.0);
        wheel.step(0.02);

        assertEquals(200 * Math.PI, reader.launcherBallSpeed(robot), 1e-6);
        wheel.setDirection(Direction.FORWARD);
        assertEquals(-200 * Math.PI, reader.launcherBallSpeed(robot), 1e-6, "turning the other way");
        assertEquals(0.0, new MechanismReader(new MechanismSetup(null, setupWith(Direction.FORWARD).hold(), null))
                .launcherBallSpeed(robot), 1e-9, "no launcher");
        assertEquals(0.0, reader.launcherBallSpeed(new FakeHardwareMap()), 1e-9, "no motor");
    }

    /** WHAT: Each shot takes speedLossPerShot: two at once take 1 - 0.75² of it. */
    @Test
    void eachShotTakesItsShare() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeDcMotorEx wheel = robot.addMotor("wheel").setEncoderModel(28, 6000);
        MechanismReader reader = new MechanismReader(setupWith(Direction.FORWARD));
        wheel.setPower(1.0);
        wheel.step(0.02);

        reader.slowLauncher(robot, 2);
        assertEquals(2800 * 0.75 * 0.75, wheel.getVelocity(), 1e-6);
        reader.slowLauncher(robot, 0);
        assertEquals(2800 * 0.75 * 0.75, wheel.getVelocity(), 1e-6, "no shot, no change");
    }

    /** WHAT: The feed's power reads with the file's direction inward, like the intake's. */
    @Test
    void theFeedReadsLikeTheIntake() {
        FakeHardwareMap robot = new FakeHardwareMap();
        FakeDcMotorEx feed = robot.addMotor("feed");
        MechanismReader reader = new MechanismReader(setupWith(Direction.FORWARD));
        feed.setDirection(Direction.REVERSE);
        feed.setPower(0.6);
        assertEquals(-0.6, reader.inwardFeedPower(robot), 1e-9);
        feed.setDirection(Direction.FORWARD);
        assertEquals(0.6, reader.inwardFeedPower(robot), 1e-9);
    }
}
