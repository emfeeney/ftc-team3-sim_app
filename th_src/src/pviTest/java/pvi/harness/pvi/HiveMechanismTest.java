package pvi.harness.pvi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.FakeDcMotorEx;
import pvi.harness.hardware.FakeHardwareMap;
import pvi.harness.hardware.MechanismSetup;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.GamePiece;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeCatalog;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RobotProfile;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PVI extra: {@code robots/hive/mechanism.properties} against the code it describes. The numbers
 * are guesses until the team measures the robot; the names and directions come from
 * {@code KimLauncher}, read from its source because they're private.
 */
class HiveMechanismTest {
    private static final Path KIM_LAUNCHER = Path.of("org/firstinspires/ftc/teamcode/KimCode/ClaudeKimCode/KimLauncher.java");

    private static RobotProfile profile(String id) {
        return RobotProfile.all(HarnessConfig.load()).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static String kimLauncher() throws IOException {
        return Files.readString(Path.of(HarnessConfig.load().teamCodeSourceDir()).resolve(KIM_LAUNCHER));
    }

    /** WHAT: The file's motor names and directions are the ones KimLauncher uses. */
    @Test
    void theNamesAndDirectionsMatchKimLauncher() throws IOException {
        String code = kimLauncher();
        MechanismSetup.Launcher launcher = profile("hive").config().mechanism().launcher();

        assertTrue(code.contains("LAUNCHER_MOTOR_NAME = \"" + launcher.motor() + "\""), launcher.motor());
        assertTrue(code.contains("FEED_MOTOR_NAME = \"" + launcher.feedMotor() + "\""), launcher.feedMotor());
        assertEquals(Direction.REVERSE, launcher.direction());
        assertTrue(code.contains("LAUNCHER_DIRECTION =\n            DcMotorSimple.Direction.REVERSE")
                || code.contains("LAUNCHER_DIRECTION =\r\n            DcMotorSimple.Direction.REVERSE"), "LAUNCHER_DIRECTION");
        assertEquals(Direction.REVERSE, launcher.feedDirection());
        assertTrue(code.contains("FEED_DIRECTION = DcMotorSimple.Direction.REVERSE"), "FEED_DIRECTION");
    }

    /** WHAT: One feed in KimLauncher (FEED_SECONDS) is long enough to push out one ball. */
    @Test
    void oneFeedIsLongEnoughForOneBall() throws IOException {
        double feedSeconds = Double.parseDouble(kimLauncher().replaceAll("(?s).*FEED_SECONDS = ([0-9.]+);.*", "$1"));

        assertTrue(profile("hive").config().mechanism().launcher().feedSecondsPerBall().value() < feedSeconds);
    }

    /** WHAT: No number claims to be measured: the real robot's facts aren't in yet. */
    @Test
    void nothingClaimsToBeMeasured() {
        MechanismSetup setup = profile("hive").config().mechanism();

        assertEquals(List.copyOf(setup.facts().keySet()), setup.placeholders());
    }

    /** WHAT: Only the Hive robot has a mechanism: Team A and KimCode's have no file, so their motors don't change. */
    @Test
    void onlyHiveHasOne() {
        assertNull(HarnessConfig.load().mechanism(), "Team A has no file (user's decision)");
        assertNull(profile("kimcode").config().mechanism());
    }

    private static RunRecording runFor(String simpleName) {
        HarnessConfig config = HarnessConfig.load();
        OpModeCatalog.Entry entry = OpModeCatalog.fromConfig(config).stream()
                .filter(e -> e.simpleName().equals(simpleName)).findFirst().orElseThrow();
        OpModeRunner runner = new OpModeRunner(entry.newOpMode(), RobotConfigs.forOpMode(entry, config));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 0.2).assertNoException();
        return recorder.recording();
    }

    private static List<String> preloadStates(RunRecording recording) {
        return recording.header().pieces().stream().filter(p -> p.id().startsWith("pollen-preload-"))
                .map(GamePiece::state).toList();
    }

    /** WHAT: Hive Auto starts holding its 4 pre-loads; a robot with no file keeps them on the tiles, as before. */
    @Test
    void onlyHiveStartsHolding() {
        assertEquals(List.of(GamePiece.HELD, GamePiece.HELD, GamePiece.HELD, GamePiece.HELD),
                preloadStates(runFor("HiveAutoRed")));
        assertEquals(List.of(GamePiece.FIELD, GamePiece.FIELD, GamePiece.FIELD, GamePiece.FIELD),
                preloadStates(runFor("TeamATeleOp")));
        assertEquals(List.of(GamePiece.FIELD, GamePiece.FIELD, GamePiece.FIELD, GamePiece.FIELD),
                preloadStates(runFor("TeleopV1")));
    }

    /** WHAT: Hive Auto's fake robot has the file's launcher wheel, with its feed and drive motors as before. */
    @Test
    void hiveAutoRunsOnTheFilesWheel() {
        HarnessConfig config = HarnessConfig.load();
        FakeHardwareMap robot = RobotConfigs.forOpMode(OpModeCatalog.fromConfig(config).stream()
                .filter(e -> e.simpleName().equals("HiveAutoRed")).findFirst().orElseThrow(), config);

        assertEquals(2800.0, robot.motor("pollen").fullSpeedTicksPerSecond(), 1e-9);
        assertEquals(FakeDcMotorEx.FULL_SPEED_TICKS_PER_SECOND, robot.motor("intake").fullSpeedTicksPerSecond(), 1e-9);
    }
}
