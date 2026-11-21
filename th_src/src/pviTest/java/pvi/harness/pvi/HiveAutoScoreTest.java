package pvi.harness.pvi;

import org.firstinspires.ftc.teamcode.KimCode.ClaudeKimCode.HiveAutoRed;
import org.junit.jupiter.api.Test;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.MatchPeriod;
import pvi.harness.runtime.OpModeCatalog;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;
import pvi.harness.runtime.RunResult;
import pvi.harness.scoring.Alliance;
import pvi.harness.scoring.MatchScore;
import pvi.harness.scoring.ScoreItem;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PVI extra (D50): Hive Auto (Red), run on the hive profile with nothing wired by hand, is scored
 * with the AUTO column, and its recording keeps that score.
 */
class HiveAutoScoreTest {

    /** WHAT: HiveAutoRed's recorded score, explained item by item. */
    @Test
    void hiveAutoRedsRecordingHasItsScore() {
        HarnessConfig config = HarnessConfig.load();
        OpModeCatalog.Entry entry = OpModeCatalog.fromConfig(config).stream()
                .filter(e -> e.simpleName().equals("HiveAutoRed")).findFirst().orElseThrow();
        OpModeRunner runner = new OpModeRunner(new HiveAutoRed(), RobotConfigs.forOpMode(entry, config));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        RunResult result = runner.run(1.0, 30.0);
        result.assertNoException();
        RunRecording recording = recorder.recording();
        MatchScore score = recording.score();
        System.out.println("HiveAutoRed score: " + score);

        assertEquals(result.score(), score, "the recording keeps the run's final score");
        assertEquals(Alliance.RED, score.robotAlliance(), "\"Red\" in the name");
        // It ends at about (-33.7, -59.8) in, facing the HIVE: the 18 in box is 3 in off the red wall
        // (LEAVE, 3) and reaches over the LOADING ZONE's inner tape at y -59.1 (PARK, 5).
        assertEquals(3, score.red().item(ScoreItem.LEAVE).points());
        assertEquals(5, score.red().item(ScoreItem.PARK).points());
        // Its 4 shots hit the upward CELL's outside with the guessed Hive numbers (D33), so no tip.
        assertEquals(0, score.red().item(ScoreItem.HIVE_TIP).points());
        assertEquals(8, score.red().total());
        // Blue has no robot and no tip in AUTO; AUTO has no ball lines.
        assertEquals(0, score.blue().total());
        assertEquals(MatchPeriod.AUTONOMOUS, score.period());
    }
}
