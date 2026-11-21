package pvi.harness.viewer;

import java.util.List;

import org.junit.jupiter.api.Test;

import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.MatchPeriod;
import pvi.harness.scoring.Alliance;
import pvi.harness.scoring.MatchScore;
import pvi.harness.scoring.MatchScorer;
import pvi.harness.scoring.ScoreInput;
import pvi.harness.scoring.ScoringField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The side panel's "Score" card text (D50), checked without a screen. */
class ScoreViewTest {

    /**
     * WHAT: The card says the period, the robot's alliance and whether it's final, then each alliance's total, ranking
     * points and every line of the point table.
     */
    @Test
    void theCardListsEachAlliancesTotalAndLines() {
        MatchScore score = new MatchScorer(ScoringField.bioBuzz()).score(new ScoreInput(MatchPeriod.AUTONOMOUS,
                Alliance.RED, Pose.inches(-35, -60, 90), 18, 18, List.of(),
                List.of(new HiveTilt("red-hive", 4, 0)), true, 1.25));

        List<String> lines = ScoreView.lines(score);

        assertEquals("AUTONOMOUS · robot: RED · final, settled after 1.25 s", lines.get(0));
        assertEquals("RED     88 pts   RP 1 (POLLINATOR 1)", lines.get(1));
        assertEquals("  LEAVE           1 x  3 =   3", lines.get(2));
        assertEquals("  PARK            1 x  5 =   5", lines.get(3));
        assertEquals("  HIVE TIP        4 x 20 =  80", lines.get(4));
        assertEquals("BLUE     0 pts   RP 0", lines.get(5));
        assertEquals(1 + 2 * 4, lines.size());
    }

    /** WHAT: A score taken during the run says so. */
    @Test
    void aLiveScoreSaysAsItStands() {
        List<String> lines = ScoreView.lines(MatchScore.none(MatchPeriod.TELEOP));
        assertTrue(lines.get(0).endsWith("robot: no alliance · as it stands"), lines.get(0));
        assertEquals(1 + 2 * 7, lines.size());
    }
}
