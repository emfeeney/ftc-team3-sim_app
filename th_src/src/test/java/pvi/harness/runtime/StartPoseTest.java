package pvi.harness.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.Pose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an OpMode starts: a {@code start.<name>=x, y, headingDegrees} key (in a robot profile or the settings
 * file) puts the runner's own position estimate there, so the viewer and Unity's Live mode, which use it, start
 * the robot where the OpMode expects. A test OpMode, not any team's code.
 */
class StartPoseTest {
    /** An autonomous that does nothing. */
    @Autonomous(name = "StartPoseAuto")
    public static final class StartPoseAuto extends OpMode {
        @Override
        public void init() {
        }

        @Override
        public void loop() {
        }
    }

    private static HarnessConfig settingsWith(String extraLines) throws IOException {
        return HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                + "\n" + extraLines + "\n", "test");
    }

    private static void assertPose(Pose expected, Pose actual) {
        assertEquals(expected.xInches(), actual.xInches(), 1e-9, "x");
        assertEquals(expected.yInches(), actual.yInches(), 1e-9, "y");
        assertEquals(expected.headingRadians(), actual.headingRadians(), 1e-9, "heading");
    }

    /** WHAT: An OpMode with a start key, here by its Driver Station name, starts there. */
    @Test
    void theRunnerStartsWhereTheKeySays() throws IOException {
        HarnessConfig settings = settingsWith("start.StartPoseAuto=-12, -61.5, 90");
        OpModeRunner runner = new OpModeRunner(new StartPoseAuto(), RobotConfigs.robot(settings));

        assertPose(Pose.inches(-12, -61.5, 90), runner.positionEstimator().start());
        assertPose(Pose.inches(-12, -61.5, 90), runner.positionEstimator().pose());
    }

    /** WHAT: An OpMode without one starts at the field's center facing +x, as before. */
    @Test
    void withoutAKeyItStartsAtTheCenter() throws IOException {
        HarnessConfig settings = settingsWith("start.SomeOtherAuto=-12, -61.5, 90");
        OpModeRunner runner = new OpModeRunner(new StartPoseAuto(), RobotConfigs.robot(settings));

        assertPose(Pose.ORIGIN, runner.positionEstimator().start());
    }

    /** WHAT: A start is found by the Driver Station name first, then the class name. */
    @Test
    void aStartIsFoundByEitherName() throws IOException {
        HarnessConfig settings = settingsWith("start.ByClass=1, 2, 3\nstart.By\\ Name=4, 5, 6");

        assertPose(Pose.inches(1, 2, 3), settings.startFor("Something Else", "ByClass"));
        assertPose(Pose.inches(4, 5, 6), settings.startFor("By Name", "ByClass"));
        assertNull(settings.startFor("Nothing", "Nothing"));
    }

    /** WHAT: A start that isn't three numbers, or is off the field, is refused, naming the key. */
    @Test
    void aWrongStartIsRefused() {
        for (String value : new String[] {"1, 2", "a, b, c", "80, 0, 0"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> settingsWith("start.Bad=" + value));
            assertTrue(e.getMessage().contains("start.Bad"), e.getMessage());
        }
    }
}
