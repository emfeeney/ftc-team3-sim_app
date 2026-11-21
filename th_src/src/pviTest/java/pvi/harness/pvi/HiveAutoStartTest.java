package pvi.harness.pvi;

import org.firstinspires.ftc.teamcode.KimCode.ClaudeKimCode.HiveAutoBlue;
import org.firstinspires.ftc.teamcode.KimCode.ClaudeKimCode.HiveAutoRed;
import org.junit.jupiter.api.Test;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeCatalog;
import pvi.harness.runtime.OpModeRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PVI extra: Hive Auto Red and Blue start where {@code robots/hive/drive.properties} says, the same starts as
 * {@link HiveAutoRecordingTest}, when run the way the viewer and Unity's Live mode run them (the robot from the
 * catalog, the runner's own position estimate). Before W-UP1 they started at the field's center, under the HIVE.
 */
class HiveAutoStartTest {
    private static Pose startOf(OpMode opMode, String simpleName) {
        HarnessConfig config = HarnessConfig.load();
        OpModeCatalog.Entry entry = OpModeCatalog.fromConfig(config).stream()
                .filter(e -> e.simpleName().equals(simpleName)).findFirst().orElseThrow();
        return new OpModeRunner(opMode, RobotConfigs.forOpMode(entry, config)).positionEstimator().start();
    }

    /** WHAT: Red starts at (-12, -61.5) facing +y. */
    @Test
    void redStartsAtItsStart() {
        Pose start = startOf(new HiveAutoRed(), "HiveAutoRed");
        assertEquals(-12, start.xInches(), 1e-9);
        assertEquals(-61.5, start.yInches(), 1e-9);
        assertEquals(90, start.headingDegrees(), 1e-9);
    }

    /** WHAT: Blue starts at Red's start turned 180 degrees about the field's center: (12, 61.5) facing -y. */
    @Test
    void blueStartsAtItsOwnStart() {
        Pose start = startOf(new HiveAutoBlue(), "HiveAutoBlue");
        assertEquals(12, start.xInches(), 1e-9);
        assertEquals(61.5, start.yInches(), 1e-9);
        assertEquals(-90, start.headingDegrees(), 1e-9);
    }
}
