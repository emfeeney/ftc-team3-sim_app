package pvi.harness.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.scoring.Alliance;
import pvi.harness.scoring.MatchScore;
import pvi.harness.scoring.ScoreItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Match points from whole runs (manager decision D50): the run's period, the robot's alliance from its profile or
 * its name, the final score after the pieces settle, and the score in the run recording. Test OpModes that stand
 * still, not any team's code.
 */
class MatchScoreRunTest {
    /** An autonomous that does nothing; "Red" in its name makes it red. */
    @Autonomous(name = "ScoreRedAuto")
    public static final class ScoreRedAuto extends OpMode {
        @Override
        public void init() {
        }

        @Override
        public void loop() {
        }
    }

    /** A TeleOp that does nothing; its alliance comes from a profile key. */
    @TeleOp(name = "ScoreTeleOp")
    public static final class ScoreTeleOp extends OpMode {
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

    /**
     * WHAT: A red autonomous that starts off the wall in its LOADING ZONE ends with LEAVE and AUTO PARK, 8 points,
     * for red only, settled.
     */
    @Test
    void anAutonomousScoresLeaveAndParkForItsAlliance() throws IOException {
        HarnessConfig settings = settingsWith("start.ScoreRedAuto=-35, -60, 90");
        OpModeRunner runner = new OpModeRunner(new ScoreRedAuto(), RobotConfigs.robot(settings));

        MatchScore score = runner.run(MatchPeriod.AUTONOMOUS).assertNoException().score();

        assertEquals(MatchPeriod.AUTONOMOUS, score.period());
        assertEquals(Alliance.RED, score.robotAlliance());
        assertEquals(1, score.red().item(ScoreItem.LEAVE).count());
        assertEquals(1, score.red().item(ScoreItem.PARK).count());
        assertEquals(8, score.red().total());
        assertEquals(0, score.blue().total());
        assertTrue(score.settled());
    }

    /**
     * WHAT: A TeleOp scores PARK for the alliance its profile key gives, and the untouched field's end-of-match items
     * for both alliances: the 3 NECTAR staged in each upward CELL (2 each) and the 4 POLLEN staged in each GARDEN
     * (1 each), as the manual stages them (TU06 10.3).
     */
    @Test
    void aTeleOpScoresParkAndTheUntouchedFieldsItems() throws IOException {
        HarnessConfig settings = settingsWith("alliance.ScoreTeleOp=blue\nstart.ScoreTeleOp=35, 60, -90");
        OpModeRunner runner = new OpModeRunner(new ScoreTeleOp(), RobotConfigs.robot(settings));

        MatchScore score = runner.run(1.0, 2.0).assertNoException().score();

        assertEquals(MatchPeriod.TELEOP, score.period());
        assertEquals(Alliance.BLUE, score.robotAlliance());
        assertEquals(1, score.blue().item(ScoreItem.PARK).count());
        assertEquals(0, score.red().item(ScoreItem.PARK).count());
        for (Alliance alliance : Alliance.values()) {
            assertEquals(3, score.of(alliance).item(ScoreItem.CELL).count(), alliance + " CELL");
            assertEquals(4, score.of(alliance).item(ScoreItem.GARDEN).count(), alliance + " GARDEN");
        }
        assertEquals(5 + 6 + 4, score.blue().total());
        assertEquals(6 + 4, score.red().total());
    }

    /** WHAT: The alliance comes from the key first (by either name), then "Red"/"Blue" in a name, else none. */
    @Test
    void theAllianceComesFromTheKeyThenTheName() throws IOException {
        HarnessConfig settings = settingsWith("alliance.ByClass=red\nalliance.By\\ Name=BLUE");

        assertEquals(Alliance.RED, settings.allianceFor("Something Else", "ByClass"));
        assertEquals(Alliance.BLUE, settings.allianceFor("By Name", "ByClass"));
        assertEquals(Alliance.BLUE, settings.allianceFor("Hive Auto Blue", "Whatever"));
        assertEquals(Alliance.RED, settings.allianceFor(null, "HiveAutoRed"));
        assertNull(settings.allianceFor("Teleop", "TeleopV1"));
        assertNull(settings.allianceFor(null, "RedVersusBlue"), "both colours: none");
    }

    /** WHAT: An alliance key that isn't red or blue is refused, naming the key. */
    @Test
    void aWrongAllianceIsRefused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settingsWith("alliance.ScoreTeleOp=green"));
        assertTrue(error.getMessage().contains("alliance.ScoreTeleOp: expected red or blue"), error.getMessage());
    }

    /**
     * WHAT: The recording holds each loop's score as it stood and the final settled score, which is the run result's;
     * the final score survives saving and loading.
     */
    @Test
    void theRecordingHoldsEachLoopsScoreAndTheFinalOne() throws IOException {
        HarnessConfig settings = settingsWith("start.ScoreRedAuto=-35, -60, 90");
        OpModeRunner runner = new OpModeRunner(new ScoreRedAuto(), RobotConfigs.robot(settings));
        RunRecorder recorder = RunRecorder.attachTo(runner);

        RunResult result = runner.run(1.0, 1.0).assertNoException();
        RunRecording recording = recorder.recording();

        assertEquals(result.score(), recording.score());
        for (RunRecording.LoopRecord loop : recording.loops()) {
            assertNotNull(loop.score(), "every loop has a score");
            assertFalse(loop.score().settled(), "a loop's score is as it stood, not settled");
        }
        assertEquals(8, recording.loops().get(recording.loops().size() - 1).score().red().total());
        RunRecording loaded = RunRecordingJson.parse(RunRecordingJson.format(recording));
        assertEquals(recording.score(), loaded.score());
        assertEquals(recording.loops().get(3).score(), loaded.loops().get(3).score());
    }
}
