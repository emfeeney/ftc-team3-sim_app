package pvi.harness.hardware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.physics.GamePiece;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.RobotProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each robot's intake and launcher come from its own {@code robots/<id>/mechanism.properties}, and
 * a broken file fails early, naming the file.
 */
class MechanismSetupTest {
    /** The robot these tests make, in a temporary robots folder. */
    private static final String ROBOT_ID = "mech-test";
    /** A motor every test robot has, added to the settings. */
    private static final String WHEEL = "testWheel";

    private static final String INTAKE = """
            intake.motor=testIntake
            intake.direction=REVERSE
            intake.minPower=0.1 | placeholder | test
            intake.forwardInches=10 | measured | tape
            intake.leftInches=-1 | placeholder | test
            intake.lengthInches=3 | placeholder | test
            intake.widthInches=8 | placeholder | test
            intake.spitSeconds=0.3 | placeholder | test
            intake.spitInchesPerSecond=30 | placeholder | test
            """;
    private static final String HOLD = """
            hold.capacity=4 | placeholder | test
            hold.accepts=pollen, red-nectar
            hold.preloaded=2 | placeholder | test
            """;
    private static final String LAUNCHER = """
            launcher.motor=testWheel
            launcher.direction=REVERSE
            launcher.ticksPerRev=28 | placeholder | test
            launcher.freeSpeedRpm=6000 | placeholder | test
            launcher.spinUpSeconds=0.25 | placeholder | test
            launcher.speedLossPerShot=0.15 | placeholder | test
            launcher.wheelDiameterInches=4 | placeholder | test
            launcher.exitFraction=0.5 | placeholder | test
            launcher.minBallInchesPerSecond=20 | placeholder | test
            feed.motor=testIntake
            feed.direction=FORWARD
            feed.minPower=0.1 | placeholder | test
            feed.secondsPerBall=0.3 | placeholder | test
            launch.forwardInches=6 | placeholder | test
            launch.leftInches=-2 | placeholder | test
            launch.upInches=14 | placeholder | test
            launch.pitchDegrees=60 | placeholder | test
            launch.yawDegrees=-5 | placeholder | test
            """;
    private static final String WHOLE_FILE = INTAKE + HOLD + LAUNCHER;

    @TempDir
    Path robotsFolder;

    private void file(String robotId, String name, String text) throws IOException {
        Files.createDirectories(robotsFolder.resolve(robotId));
        Files.writeString(robotsFolder.resolve(robotId).resolve(name), text);
    }

    /** The real settings file, for the robot {@link #ROBOT_ID} with two more motors. */
    private static String settings() throws IOException {
        return Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + ROBOT_ID)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testIntake, " + WHEEL);
    }

    private HarnessConfig withMechanism(String text) throws IOException {
        file(ROBOT_ID, HarnessConfig.MECHANISM_FILE_NAME, text);
        return HarnessConfig.fromText(settings(), "test", robotsFolder);
    }

    private String problemsIn(String text) {
        return assertThrows(IllegalArgumentException.class, () -> withMechanism(text)).getMessage();
    }

    /** WHAT: A robot's file becomes its setup: names, directions, numbers, which are measured, and the kinds. */
    @Test
    void aRobotsFileBecomesItsSetup() throws IOException {
        MechanismSetup setup = withMechanism(WHOLE_FILE).mechanism();

        assertEquals("testIntake", setup.intake().motor());
        assertEquals(Direction.REVERSE, setup.intake().direction());
        assertEquals(-1.0, setup.intake().leftInches().value(), 1e-9);
        assertTrue(setup.intake().forwardInches().measured());
        assertFalse(setup.intake().minPower().measured());
        assertEquals(List.of(GamePiece.POLLEN, GamePiece.RED_NECTAR), setup.hold().accepts());
        assertEquals(2.0, setup.hold().preloaded().value(), 1e-9);
        assertEquals(WHEEL, setup.launcher().motor());
        assertEquals("testIntake", setup.launcher().feedMotor());
        assertEquals(Direction.FORWARD, setup.launcher().feedDirection());
        assertEquals(-5.0, setup.launcher().yawDegrees().value(), 1e-9);
        assertFalse(setup.placeholders().contains("intake.forwardInches"));
        assertTrue(setup.placeholders().contains("launch.yawDegrees"));
    }

    /** WHAT: No file means no intake and no launcher. */
    @Test
    void noFileMeansNoMechanism() throws IOException {
        assertNull(HarnessConfig.fromText(settings(), "test", robotsFolder).mechanism());
    }

    /** WHAT: An empty motor name turns that half off, and that half's other keys are then refused. */
    @Test
    void anEmptyMotorTurnsThatHalfOff() throws IOException {
        MechanismSetup noLauncher = withMechanism(INTAKE + HOLD + "launcher.motor=\n").mechanism();
        assertNotNull(noLauncher.intake());
        assertNull(noLauncher.launcher());
        MechanismSetup noIntake = withMechanism("intake.motor=\n" + HOLD + LAUNCHER).mechanism();
        assertNull(noIntake.intake());
        assertNotNull(noIntake.launcher());

        assertTrue(problemsIn(WHOLE_FILE.replace("launcher.motor=testWheel", "launcher.motor="))
                .contains("launcher.ticksPerRev: not a mechanism setting"));
    }

    /** WHAT: A missing fact is refused, naming the key and the file. */
    @Test
    void aMissingFactIsRefused() {
        String message = problemsIn(WHOLE_FILE.replaceAll("(?m)^launch\\.upInches=.*\n", ""));

        assertTrue(message.contains("launch.upInches: missing"), message);
        assertTrue(message.contains(HarnessConfig.MECHANISM_FILE_NAME), message);
    }

    /** WHAT: A misspelled key is refused, naming the key and the file. */
    @Test
    void aMisspelledKeyIsRefused() {
        String message = problemsIn(WHOLE_FILE + "intake.widthInch=3 | placeholder | test\n");

        assertTrue(message.contains("intake.widthInch: not a mechanism setting"), message);
        assertTrue(message.contains(HarnessConfig.MECHANISM_FILE_NAME), message);
    }

    /** WHAT: A motor the robot doesn't have is refused, for each of the three motors. */
    @Test
    void aMotorNotOnTheRobotIsRefused() {
        for (String[] typo : new String[][] {{"intake.motor=testIntake", "intake.motor"},
                {"launcher.motor=testWheel", "launcher.motor"}, {"feed.motor=testIntake", "feed.motor"}}) {
            String message = problemsIn(WHOLE_FILE.replace(typo[0], typo[1] + "=noSuchMotor"));
            assertTrue(message.contains(typo[1] + ": noSuchMotor isn't one of this robot's motors"), message);
        }
    }

    /** WHAT: Numbers that make no sense are refused: part of a ball, more pre-loads than it holds, a bad share, an unknown kind. */
    @Test
    void numbersThatMakeNoSenseAreRefused() {
        assertTrue(problemsIn(WHOLE_FILE.replace("hold.capacity=4", "hold.capacity=3.5")).contains("whole number"));
        assertTrue(problemsIn(WHOLE_FILE.replace("hold.preloaded=2", "hold.preloaded=5")).contains("more than hold.capacity"));
        assertTrue(problemsIn(WHOLE_FILE.replace("exitFraction=0.5", "exitFraction=1.5")).contains("between 0 and 1"));
        assertTrue(problemsIn(WHOLE_FILE.replace("hold.accepts=pollen, red-nectar", "hold.accepts=bananas"))
                .contains("bananas"));
        assertTrue(problemsIn(WHOLE_FILE.replace("launcher.ticksPerRev=28", "launcher.ticksPerRev=-28")).contains("above zero"));
    }

    /** WHAT: A profile doesn't get the main robot's mechanism; its own file is its own. */
    @Test
    void aProfileDoesntInherit() throws IOException {
        HarnessConfig main = withMechanism(WHOLE_FILE);
        file("none", RobotProfile.FILE_NAME, "forOpModes=A\n");
        file("own", RobotProfile.FILE_NAME, "forOpModes=B\n");
        file("own", HarnessConfig.MECHANISM_FILE_NAME, WHOLE_FILE.replace("launcher.ticksPerRev=28", "launcher.ticksPerRev=50"));

        List<RobotProfile> profiles = RobotProfile.all(main, robotsFolder);

        assertNull(profiles.get(0).config().mechanism(), "none");
        assertEquals(50.0, profiles.get(1).config().mechanism().launcher().ticksPerRev().value(), 1e-9);
    }

    /** WHAT: Mechanism keys in the settings file are refused, as they belong to one robot. */
    @Test
    void mechanismKeysInTheSettingsFileAreRefused() throws IOException {
        String text = settings() + "launcher.motor=testWheel\n";

        String message = assertThrows(IllegalArgumentException.class,
                () -> HarnessConfig.fromText(text, "test", robotsFolder)).getMessage();

        assertTrue(message.contains("launcher.motor: not a harness setting"), message);
    }

    /**
     * WHAT: The robot's launcher motor gets the file's encoder and spin-up: 28 ticks at 6000 rpm is
     * 2800 ticks/s, and it takes time to get there. Its other motors are as before.
     */
    @Test
    void theLauncherMotorGetsTheFilesWheel() throws IOException {
        FakeHardwareMap robot = RobotConfigs.robot(withMechanism(WHOLE_FILE));

        FakeDcMotorEx wheel = robot.motor(WHEEL);
        assertEquals(2800.0, wheel.fullSpeedTicksPerSecond(), 1e-9);
        wheel.setPower(1.0);
        wheel.step(0.02);
        assertTrue(wheel.getVelocity() < 300, "only just starting: " + wheel.getVelocity());

        FakeDcMotorEx other = robot.motor("testIntake");
        assertEquals(FakeDcMotorEx.FULL_SPEED_TICKS_PER_SECOND, other.fullSpeedTicksPerSecond(), 1e-9);
        other.setPower(1.0);
        other.step(0.02);
        assertEquals(FakeDcMotorEx.FULL_SPEED_TICKS_PER_SECOND, other.getVelocity(), 1e-9, "no lag");
    }

    /** WHAT: A robot with no file gets plain motors. */
    @Test
    void noFileMeansPlainMotors() throws IOException {
        FakeHardwareMap robot = RobotConfigs.robot(HarnessConfig.fromText(settings(), "test", robotsFolder));

        assertEquals(FakeDcMotorEx.FULL_SPEED_TICKS_PER_SECOND, robot.motor(WHEEL).fullSpeedTicksPerSecond(), 1e-9);
    }
}
