package pvi.harness.scoring;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.MatchPeriod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each scoring rule on made-up moments, on the BIOBUZZ field file's zones (manager decision D50; Competition Manual
 * TU06 §10.5). TestBot2's tests check the numbers against the manual; these check that each rule picks the right
 * robot, balls and HIVEs.
 */
class MatchScorerTest {
    private static final MatchScorer SCORER = new MatchScorer(ScoringField.bioBuzz());
    private static final double ROBOT = 18;
    /** No HIVE has tipped. */
    private static final List<HiveTilt> START = List.of(new HiveTilt("red-hive", 0, 0), new HiveTilt("blue-hive", 0, 0));

    private static MatchScore score(MatchPeriod period, Alliance alliance, Pose robot, List<GamePiece> pieces,
                                    List<HiveTilt> hives) {
        return SCORER.score(new ScoreInput(period, alliance, robot, ROBOT, ROBOT, pieces, hives, false, 0));
    }

    private static int count(MatchScore score, Alliance alliance, String line) {
        return score.of(alliance).item(line).count();
    }

    private static GamePiece pollen(String id, String state, double x, double y, double z) {
        return new GamePiece(id, GamePiece.POLLEN, state, x, y, z);
    }

    private static GamePiece nectar(String id, String kind, double x, double y, double z) {
        return new GamePiece(id, kind, GamePiece.FLOWER, x, y, z);
    }

    // ---- The field file -------------------------------------------------------------------------------------

    /**
     * WHAT: The scoring zones come from the field file: each LOADING ZONE is the box around its 3 tape strips, out to
     * its alliance's wall; the GARDENs, the 4 FLOWER heads and the 2 HIVEs are there too.
     */
    @Test
    void theZonesComeFromTheFieldFile() {
        ScoringField field = ScoringField.bioBuzz();
        ScoringField.Zone red = field.loadingZones().get(Alliance.RED);
        assertEquals(-46.6, red.minXInches(), 1e-9);
        assertEquals(-23.91, red.maxXInches(), 1e-9);
        assertEquals(-72, red.minYInches(), 1e-9, "out to the red wall");
        assertEquals(-59.1, red.maxYInches(), 1e-9, "the inner tape's far edge: the zone includes the tape");
        ScoringField.Zone blue = field.loadingZones().get(Alliance.BLUE);
        assertEquals(72, blue.maxYInches(), 1e-9, "out to the blue wall");
        assertEquals(59.1, blue.minYInches(), 1e-9);
        assertEquals(68.1, field.gardens().get(Alliance.RED).minXInches(), 1e-9);
        assertEquals(4, field.flowers().size());
        assertEquals(5.25, field.flowers().get(0).bottomInches(), 1e-9, "the middle ring's top");
        assertEquals(20.25, field.flowers().get(0).topInches(), 1e-9, "the top ring's bottom");
        assertEquals(1.99, field.flowers().get(0).radiusInches(), 1e-9);
        assertTrue(field.notes().isEmpty(), "nothing missing: " + field.notes());
        assertEquals(2, field.hives().size());
        assertEquals(Alliance.BLUE, field.hives().get(1).alliance());
    }

    // ---- The robot -------------------------------------------------------------------------------------------

    /** WHAT: LEAVE in AUTO only once the robot box no longer touches a wall; TELEOP has no LEAVE line. */
    @Test
    void leaveNeedsTheRobotOffTheWall() {
        Pose againstTheWall = Pose.inches(-12, -63, 90);   // 18 in box: its back is at y -72
        Pose justOff = Pose.inches(-12, -62.8, 90);
        assertEquals(0, count(score(MatchPeriod.AUTONOMOUS, Alliance.RED, againstTheWall, List.of(), START), Alliance.RED,
                ScoreItem.LEAVE));
        MatchScore off = score(MatchPeriod.AUTONOMOUS, Alliance.RED, justOff, List.of(), START);
        assertEquals(1, count(off, Alliance.RED, ScoreItem.LEAVE));
        assertEquals(3, off.red().item(ScoreItem.LEAVE).points());
        assertEquals(0, count(off, Alliance.BLUE, ScoreItem.LEAVE), "only the robot's alliance");
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.RED, justOff, List.of(), START), Alliance.RED,
                ScoreItem.LEAVE));
    }

    /** WHAT: A turned robot touching the wall with one corner hasn't left it. */
    @Test
    void aCornerOnTheWallIsStillTouching() {
        // Turned 45 degrees, its corner reaches 12.73 in from its center: center 59.3 in up puts it at 72.03.
        assertEquals(0, count(score(MatchPeriod.AUTONOMOUS, Alliance.BLUE, Pose.inches(0, 59.3, 45), List.of(), START),
                Alliance.BLUE, ScoreItem.LEAVE));
        assertEquals(1, count(score(MatchPeriod.AUTONOMOUS, Alliance.BLUE, Pose.inches(0, 59.0, 45), List.of(), START),
                Alliance.BLUE, ScoreItem.LEAVE));
    }

    /** WHAT: PARK when the robot box is at least partly in its own LOADING ZONE, never in the other alliance's. */
    @Test
    void parkNeedsPartOfTheRobotInItsOwnLoadingZone() {
        // Zone x -46.6..-23.91, y -72..-59.1; an 18 in box reaches 9 in from its center.
        Pose besideTheZone = Pose.inches(-23.91 + 8.9, -40, 0);
        Pose partly = Pose.inches(-46.6 - 8.9, -60, 0);
        Pose outside = Pose.inches(-23.91 + 9.1, -60, 0);
        assertEquals(1, count(score(MatchPeriod.TELEOP, Alliance.RED, partly, List.of(), START), Alliance.RED, ScoreItem.PARK));
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.RED, outside, List.of(), START), Alliance.RED, ScoreItem.PARK));
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.RED, besideTheZone, List.of(), START), Alliance.RED, ScoreItem.PARK),
                "y -40 is well clear of the zone");
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.BLUE, partly, List.of(), START), Alliance.BLUE, ScoreItem.PARK),
                "a blue robot in the red zone");
        MatchScore auto = score(MatchPeriod.AUTONOMOUS, Alliance.RED, partly, List.of(), START);
        assertEquals(5, auto.red().item(ScoreItem.PARK).points(), "AUTO PARK too");
    }

    /** WHAT: A turned robot whose corner pokes into the zone is parked; its box (not just its center) counts. */
    @Test
    void aTurnedRobotsCornerInTheZoneParks() {
        // Turned 45 degrees: the corner is 12.73 in out. Center at y -59.1 + 12.6 just reaches into the zone.
        assertEquals(1, count(score(MatchPeriod.TELEOP, Alliance.RED, Pose.inches(-35, -59.1 + 12.6, 45), List.of(), START),
                Alliance.RED, ScoreItem.PARK));
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.RED, Pose.inches(-35, -59.1 + 12.9, 45), List.of(), START),
                Alliance.RED, ScoreItem.PARK));
        // Off the zone's corner (-23.91, -59.1), diagonally out by 8 in each way: the turned box's side faces the corner
        // 11.3 in away but only reaches 9 in, though the box's square outline (12.7 in each way) would overlap.
        assertEquals(0, count(score(MatchPeriod.TELEOP, Alliance.RED, Pose.inches(-23.91 + 8, -59.1 + 8, 45), List.of(), START),
                Alliance.RED, ScoreItem.PARK), "the box itself, not its outline");
        assertEquals(1, count(score(MatchPeriod.TELEOP, Alliance.RED, Pose.inches(-23.91 + 6, -59.1 + 6, 45), List.of(), START),
                Alliance.RED, ScoreItem.PARK), "8.5 in away: the side reaches it");
    }

    /** WHAT: A robot with no alliance, or no robot, earns no LEAVE or PARK. */
    @Test
    void aRobotWithNoAllianceEarnsNothing() {
        Pose inRedZone = Pose.inches(-35, -62, 90);
        MatchScore none = score(MatchPeriod.AUTONOMOUS, null, inRedZone, List.of(), START);
        assertEquals(0, none.red().total());
        assertEquals(0, none.blue().total());
        assertNull(none.robotAlliance());
        assertEquals(0, score(MatchPeriod.AUTONOMOUS, Alliance.RED, null, List.of(), START).red().total());
    }

    // ---- HIVEs -----------------------------------------------------------------------------------------------

    /** WHAT: A tip counts for its HIVE's colour once it's complete, not while the HIVE is still swinging. */
    @Test
    void aTipCountsForItsHiveOnceComplete() {
        List<HiveTilt> redMidTip = List.of(new HiveTilt("red-hive", 1, -30), new HiveTilt("blue-hive", 0, 0));
        assertEquals(0, count(score(MatchPeriod.AUTONOMOUS, null, null, List.of(), redMidTip), Alliance.RED, ScoreItem.HIVE_TIP));
        List<HiveTilt> redTipped = List.of(new HiveTilt("red-hive", 1, -60), new HiveTilt("blue-hive", 2, 0));
        MatchScore done = score(MatchPeriod.TELEOP, null, null, List.of(), redTipped);
        assertEquals(1, count(done, Alliance.RED, ScoreItem.HIVE_TIP));
        assertEquals(20, done.red().item(ScoreItem.HIVE_TIP).points());
        assertEquals(2, count(done, Alliance.BLUE, ScoreItem.HIVE_TIP), "blue tipped twice, back to its start");
        List<HiveTilt> blueSecondUnderWay = List.of(new HiveTilt("blue-hive", 2, 50));
        assertEquals(1, count(score(MatchPeriod.TELEOP, null, null, List.of(), blueSecondUnderWay), Alliance.BLUE,
                ScoreItem.HIVE_TIP), "blue's second tip has started (it swings back from +60) but isn't complete");
    }

    /** WHAT: POLLINATOR 1 at 4 tips, POLLINATOR 2 at 7; SWARM needs 16 LEAVE + PARK points. */
    @Test
    void rankingPointsAtTheThresholds() {
        MatchScore three = score(MatchPeriod.TELEOP, null, null, List.of(), List.of(new HiveTilt("red-hive", 3, -60)));
        assertFalse(three.red().pollinator1Rp());
        MatchScore four = score(MatchPeriod.TELEOP, null, null, List.of(), List.of(new HiveTilt("red-hive", 4, 0)));
        assertTrue(four.red().pollinator1Rp());
        assertFalse(four.red().pollinator2Rp());
        assertEquals(1, four.red().rankingPoints());
        MatchScore seven = score(MatchPeriod.TELEOP, null, null, List.of(), List.of(new HiveTilt("red-hive", 7, -60)));
        assertTrue(seven.red().pollinator2Rp());
        assertEquals(2, seven.red().rankingPoints());
        assertFalse(seven.blue().pollinator1Rp());
        MatchScore leaveAndPark = score(MatchPeriod.AUTONOMOUS, Alliance.RED, Pose.inches(-35, -60, 90), List.of(), START);
        assertEquals(8, leaveAndPark.red().total());
        assertFalse(leaveAndPark.red().swarmRp(), "one robot in one period can't reach 16");
    }

    /** WHAT: CELL balls score in TELEOP for the HIVE's colour, only in its upward CELL, and none while it tips. */
    @Test
    void cellBallsCountInTheUpwardCellOnly() {
        // Red starts with its +x CELL up; blue with its -x CELL up.
        List<GamePiece> balls = List.of(
                pollen("up-red", GamePiece.CELL, 12, -12.75, 56),
                pollen("up-red-2", GamePiece.CELL, 13, -16, 56),
                pollen("down-red", GamePiece.CELL, -14, -12.75, 40),
                pollen("up-blue", GamePiece.CELL, -12, 12.75, 56),
                pollen("loose", GamePiece.FIELD, 12, -12.75, 1.4));
        MatchScore start = score(MatchPeriod.TELEOP, null, null, balls, START);
        assertEquals(2, count(start, Alliance.RED, ScoreItem.CELL));
        assertEquals(4, start.red().item(ScoreItem.CELL).points());
        assertEquals(1, count(start, Alliance.BLUE, ScoreItem.CELL));
        List<HiveTilt> redTipped = List.of(new HiveTilt("red-hive", 1, -60), new HiveTilt("blue-hive", 0, 0));
        assertEquals(1, count(score(MatchPeriod.TELEOP, null, null, balls, redTipped), Alliance.RED, ScoreItem.CELL),
                "after a tip the -x CELL is up");
        List<HiveTilt> redTipping = List.of(new HiveTilt("red-hive", 1, -20), new HiveTilt("blue-hive", 0, 0));
        assertEquals(0, count(score(MatchPeriod.TELEOP, null, null, balls, redTipping), Alliance.RED, ScoreItem.CELL));
        assertEquals(0, count(score(MatchPeriod.AUTONOMOUS, null, null, balls, START), Alliance.RED, ScoreItem.CELL),
                "AUTO has no CELL line");
    }

    // ---- GARDENs and FLOWERs ---------------------------------------------------------------------------------

    /** WHAT: Balls at least partly in a GARDEN score 1 each for the GARDEN's colour; held balls don't. */
    @Test
    void gardenBallsCountForTheGardensColour() {
        // Red GARDEN: x 68.1..70.1, y -70.1..-47.4. A POLLEN (radius 1.4) centered 1.3 in short of it touches it.
        List<GamePiece> balls = new ArrayList<>(List.of(
                pollen("in", GamePiece.FIELD, 69.1, -60, 1.4),
                pollen("partly", GamePiece.FIELD, 68.1 - 1.3, -55, 1.4),
                pollen("short", GamePiece.FIELD, 68.1 - 1.5, -55, 1.4),
                pollen("held", GamePiece.HELD, 69.1, -50, 8),
                new GamePiece("blue-nectar-in-red", GamePiece.BLUE_NECTAR, GamePiece.FIELD, 69.1, -65, 1.8),
                pollen("in-blue", GamePiece.FIELD, -69.1, 60, 1.4)));
        MatchScore score = score(MatchPeriod.TELEOP, null, null, balls, START);
        assertEquals(3, count(score, Alliance.RED, ScoreItem.GARDEN), "in, partly and the blue NECTAR");
        assertEquals(3, score.red().item(ScoreItem.GARDEN).points());
        assertEquals(1, count(score, Alliance.BLUE, ScoreItem.GARDEN));
        assertEquals(0, count(score(MatchPeriod.AUTONOMOUS, null, null, balls, START), Alliance.RED, ScoreItem.GARDEN));
    }

    /**
     * WHAT: In a FLOWER, the top-most scoring NECTAR's alliance owns it and gets 2 for every scoring ball; the
     * bottom-most scoring NECTAR's alliance gets the 5-point bonus; balls below the scoring volume don't count.
     */
    @Test
    void theTopNectarOwnsTheFlowerAndTheBottomNectarGetsTheBonus() {
        double x = -68.04;
        double y = -23.39;
        List<GamePiece> balls = List.of(
                pollen("low", GamePiece.FLOWER, x, y, 1.4),                // top 2.8: under the volume (5.25)
                pollen("p1", GamePiece.FLOWER, x, y, 4.2),
                nectar("blue-low", GamePiece.BLUE_NECTAR, x, y, 7.4),
                pollen("p2", GamePiece.FLOWER, x, y, 10.6),
                nectar("red-top", GamePiece.RED_NECTAR, x, y, 13.8));
        MatchScore score = score(MatchPeriod.TELEOP, null, null, balls, START);
        assertEquals(4, count(score, Alliance.RED, ScoreItem.FLOWER), "4 balls in the volume, owned by red");
        assertEquals(8, score.red().item(ScoreItem.FLOWER).points());
        assertEquals(0, count(score, Alliance.BLUE, ScoreItem.FLOWER));
        assertEquals(1, count(score, Alliance.BLUE, ScoreItem.BOTTOM_NECTAR));
        assertEquals(5, score.blue().item(ScoreItem.BOTTOM_NECTAR).points());
        assertEquals(0, count(score, Alliance.RED, ScoreItem.BOTTOM_NECTAR));
    }

    /** WHAT: A FLOWER with only POLLEN has no owner and scores nothing; a ball outside the tube isn't in it. */
    @Test
    void aFlowerWithNoNectarScoresNothing() {
        double x = 68.04;
        double y = 23.39;
        List<GamePiece> pollenOnly = List.of(pollen("a", GamePiece.FLOWER, x, y, 5), pollen("b", GamePiece.FLOWER, x, y, 8));
        MatchScore score = score(MatchPeriod.TELEOP, null, null, pollenOnly, START);
        assertEquals(0, score.red().total());
        assertEquals(0, score.blue().total());
        List<GamePiece> beside = List.of(nectar("red-beside", GamePiece.RED_NECTAR, x - 4, y, 8));
        assertEquals(0, score(MatchPeriod.TELEOP, null, null, beside, START).red().total());
    }

    /** WHAT: Every line of the period is listed, and the total is their sum. */
    @Test
    void eachPeriodListsItsLinesAndTheTotalAddsUp() {
        MatchScore auto = MatchScore.none(MatchPeriod.AUTONOMOUS);
        assertEquals(List.of(ScoreItem.LEAVE, ScoreItem.PARK, ScoreItem.HIVE_TIP),
                auto.red().items().stream().map(ScoreItem::name).toList());
        MatchScore teleOp = score(MatchPeriod.TELEOP, Alliance.RED, Pose.inches(-35, -62, 90),
                List.of(pollen("g", GamePiece.FIELD, 69.1, -60, 1.4)), List.of(new HiveTilt("red-hive", 1, -60)));
        assertEquals(List.of(ScoreItem.PARK, ScoreItem.HIVE_TIP, ScoreItem.CELL, ScoreItem.FLOWER, ScoreItem.BOTTOM_NECTAR,
                ScoreItem.GARDEN), teleOp.red().items().stream().map(ScoreItem::name).toList());
        assertEquals(5 + 20 + 1, teleOp.red().total());
    }
}
