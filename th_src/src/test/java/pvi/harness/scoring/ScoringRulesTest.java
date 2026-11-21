package pvi.harness.scoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.MatchPeriod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D50: each scoring rule of the BIOBUZZ Competition Manual (TU06 §10.5, Tables 10-2 and 10-3), one
 * test per rule. Each test places the robot and the balls straight where the rule looks, and scores
 * that one moment with {@link MatchScorer}.
 *
 * <p>Field places used (from {@code fields/biobuzz-2026.json}):</p>
 * <ul>
 *   <li>Red LOADING ZONE: x -46.6 to -23.9, y -72 (the red wall) to -59.1 (the inner tape's edge).</li>
 *   <li>Red GARDEN: x 68.1 to 70.1, y -70.1 to -47.4. Blue GARDEN: the same, mirrored through the center.</li>
 *   <li>The FLOWER on the red wall: center (23.39, -68.04). A ball counts when it is at least partly
 *       between the middle ring's top (5.25 in) and the top ring's bottom (20.25 in), CAD values.</li>
 *   <li>At the start the red HIVE's audience CELL (+x) is up and its far CELL (-x) down; for blue
 *       the far CELL is up.</li>
 * </ul>
 */
class ScoringRulesTest {
    private static final double ROBOT_INCHES = 18;
    /** The red wall's FLOWER. */
    private static final double FLOWER_X = 23.39;
    private static final double FLOWER_Y = -68.04;
    /**
     * A red HIVE's swing from the start once a tip is finished: a full tip is twice its 30 degree tilt,
     * toward -60 for red (+60 for blue), and back to 0 after an even number of tips.
     */
    private static final double RED_TIPPED_SWING = -60;

    private static List<HiveTilt> noTips() {
        return List.of(new HiveTilt("red-hive", 0, 0), new HiveTilt("blue-hive", 0, 0));
    }

    private static MatchScore score(MatchPeriod period, Alliance robotAlliance, Pose robot, List<GamePiece> pieces,
                                    List<HiveTilt> hives) {
        return new MatchScorer(ScoringField.bioBuzz()).score(new ScoreInput(period, robotAlliance, robot,
                ROBOT_INCHES, ROBOT_INCHES, pieces, hives, true, 0.0));
    }

    /** Only the robot, no balls, no tips. */
    private static MatchScore robotAt(MatchPeriod period, Pose robot) {
        return score(period, Alliance.RED, robot, List.of(), noTips());
    }

    private static int count(MatchScore score, Alliance alliance, String item) {
        return score.of(alliance).item(item).count();
    }

    private static int points(MatchScore score, Alliance alliance, String item) {
        return score.of(alliance).item(item).points();
    }

    private static GamePiece ball(String id, String kind, String state, double x, double y, double z) {
        return new GamePiece(id, kind, state, x, y, z);
    }

    // ---- LEAVE ---------------------------------------------------------------------------------

    /** WHAT: A robot off the wall at the end of AUTO earns LEAVE, 3 points. */
    @Test
    void aRobotOffTheWallLeaves() {
        MatchScore score = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(0, -40, 90));

        assertEquals(1, count(score, Alliance.RED, ScoreItem.LEAVE));
        assertEquals(3, points(score, Alliance.RED, ScoreItem.LEAVE));
        assertEquals(0, count(score, Alliance.BLUE, ScoreItem.LEAVE), "only the robot's alliance");
    }

    /** WHAT: A robot still touching the perimeter wall earns no LEAVE (§10.5.4). */
    @Test
    void aRobotStillTouchingTheWallDoesNotLeave() {
        // 18 in robot, center 9 in from the red wall: its back edge is on the wall.
        MatchScore score = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(0, -63, 90));

        assertEquals(0, count(score, Alliance.RED, ScoreItem.LEAVE));
    }

    // ---- PARK ----------------------------------------------------------------------------------

    /** WHAT: Partly in its LOADING ZONE counts as PARK (§10.5.4: "at least partially"). */
    @Test
    void partlyInTheLoadingZoneParks() {
        // Box y -61 to -43: 1.9 in over the zone's inner edge (-59.1).
        MatchScore partly = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(-35, -52, 90));
        // Box y -55 to -37: clear of the zone.
        MatchScore outside = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(-35, -46, 90));

        assertEquals(5, points(partly, Alliance.RED, ScoreItem.PARK));
        assertEquals(0, points(outside, Alliance.RED, ScoreItem.PARK));
    }

    /** WHAT: Only its own alliance's LOADING ZONE counts: a red robot in the blue zone doesn't PARK. */
    @Test
    void theOtherAlliancesLoadingZoneDoesNotPark() {
        MatchScore score = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(35, 52, -90));

        assertEquals(0, count(score, Alliance.RED, ScoreItem.PARK));
        assertEquals(0, count(score, Alliance.BLUE, ScoreItem.PARK));
    }

    /**
     * WHAT: AUTO PARK and end-of-match PARK are separate lines, each 5: an AUTO run has LEAVE and
     * PARK, a TELEOP run has PARK and no LEAVE (§10.5 F and G).
     */
    @Test
    void autoParkAndMatchParkAreSeparate() {
        Pose parked = Pose.inches(-35, -52, 90);
        MatchScore auto = robotAt(MatchPeriod.AUTONOMOUS, parked);
        MatchScore teleOp = robotAt(MatchPeriod.TELEOP, parked);

        assertEquals(MatchPeriod.AUTONOMOUS, auto.period());
        assertEquals(5, points(auto, Alliance.RED, ScoreItem.PARK));
        assertEquals(3, points(auto, Alliance.RED, ScoreItem.LEAVE));
        assertEquals(8, auto.of(Alliance.RED).total());
        assertEquals(5, points(teleOp, Alliance.RED, ScoreItem.PARK));
        assertEquals(0, points(teleOp, Alliance.RED, ScoreItem.LEAVE), "no LEAVE in TELEOP");
        assertEquals(5, teleOp.of(Alliance.RED).total());
    }

    // ---- HIVE TIP ------------------------------------------------------------------------------

    /** WHAT: A tip is 20 to the HIVE's colour, in AUTO and in TELEOP, each in its own run's column. */
    @Test
    void aTipIsTwentyInAutoAndInTeleOp() {
        List<HiveTilt> redTipped = List.of(new HiveTilt("red-hive", 1, RED_TIPPED_SWING), new HiveTilt("blue-hive", 0, 0));
        MatchScore auto = score(MatchPeriod.AUTONOMOUS, Alliance.RED, null, List.of(), redTipped);
        MatchScore teleOp = score(MatchPeriod.TELEOP, Alliance.RED, null, List.of(), redTipped);

        assertEquals(20, points(auto, Alliance.RED, ScoreItem.HIVE_TIP));
        assertEquals(20, points(teleOp, Alliance.RED, ScoreItem.HIVE_TIP));
        assertEquals(0, points(auto, Alliance.BLUE, ScoreItem.HIVE_TIP), "the blue HIVE didn't tip");
        assertEquals(20, auto.of(Alliance.RED).total(), "no robot: only the tip");
    }

    /** WHAT: A tip still under way (the HIVE hasn't reached its other stable side) scores nothing yet. */
    @Test
    void aTipUnderWayDoesNotScoreYet() {
        List<HiveTilt> halfway = List.of(new HiveTilt("red-hive", 1, -30), new HiveTilt("blue-hive", 0, 0));

        assertEquals(0, points(score(MatchPeriod.AUTONOMOUS, Alliance.RED, null, List.of(), halfway), Alliance.RED,
                ScoreItem.HIVE_TIP));
    }

    // ---- CELL ----------------------------------------------------------------------------------

    /**
     * WHAT: A ball left in an upward CELL scores 2 at the end of the match; one in a downward CELL
     * scores nothing (§10.5.1). Red's audience CELL (+x) is up at the start.
     */
    @Test
    void aBallInTheUpwardCellScoresAndTheDownwardOneDoesNot() {
        GamePiece up = ball("up", GamePiece.RED_NECTAR, GamePiece.CELL, 12.69, -21.03, 51.66);
        GamePiece down = ball("down", GamePiece.POLLEN, GamePiece.CELL, -12.69, -21.03, 40.0);

        MatchScore score = score(MatchPeriod.TELEOP, Alliance.RED, null, List.of(up, down), noTips());

        assertEquals(1, count(score, Alliance.RED, ScoreItem.CELL));
        assertEquals(2, points(score, Alliance.RED, ScoreItem.CELL));
    }

    /** WHAT: After a tip the other CELL is up, so the same ball no longer scores. */
    @Test
    void afterATipTheOtherCellIsUp() {
        GamePiece audienceCell = ball("a", GamePiece.RED_NECTAR, GamePiece.CELL, 12.69, -21.03, 51.66);
        List<HiveTilt> redTipped = List.of(new HiveTilt("red-hive", 1, RED_TIPPED_SWING), new HiveTilt("blue-hive", 0, 0));

        MatchScore score = score(MatchPeriod.TELEOP, Alliance.RED, null, List.of(audienceCell), redTipped);

        assertEquals(0, count(score, Alliance.RED, ScoreItem.CELL));
    }

    /** WHAT: CELL balls score only at the end of the match, never in AUTO (Table 10-2 has no AUTO value). */
    @Test
    void cellBallsDoNotScoreInAuto() {
        GamePiece up = ball("up", GamePiece.RED_NECTAR, GamePiece.CELL, 12.69, -21.03, 51.66);

        MatchScore score = score(MatchPeriod.AUTONOMOUS, Alliance.RED, null, List.of(up), noTips());

        assertEquals(0, points(score, Alliance.RED, ScoreItem.CELL));
        assertEquals(0, score.of(Alliance.RED).total());
    }

    // ---- GARDEN --------------------------------------------------------------------------------

    /**
     * WHAT: Any ball partly in a GARDEN scores 1 for the GARDEN's colour, even the other alliance's
     * NECTAR (§10.5.3).
     */
    @Test
    void aGardenScoresForItsColourWhateverTheBall() {
        List<GamePiece> pieces = List.of(
                // In the red GARDEN: blue NECTAR and POLLEN.
                ball("blue-in-red", GamePiece.BLUE_NECTAR, GamePiece.FIELD, 69, -55, 1.8),
                ball("pollen-in-red", GamePiece.POLLEN, GamePiece.FIELD, 69, -65, 1.4),
                // Partly in the blue GARDEN: its circle reaches 0.7 in over the GARDEN's edge.
                ball("red-in-blue", GamePiece.RED_NECTAR, GamePiece.FIELD, -67, 55, 1.8),
                // Clear of the red GARDEN (circle stops 0.3 in short).
                ball("near", GamePiece.RED_NECTAR, GamePiece.FIELD, 66, -55, 1.8));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(2, points(score, Alliance.RED, ScoreItem.GARDEN));
        assertEquals(1, points(score, Alliance.BLUE, ScoreItem.GARDEN));
    }

    /** WHAT: A held ball over a GARDEN doesn't score there, and GARDENs don't score in AUTO. */
    @Test
    void heldBallsAndAutoDoNotScoreInAGarden() {
        GamePiece held = ball("held", GamePiece.POLLEN, GamePiece.HELD, 69, -55, 4);
        GamePiece loose = ball("loose", GamePiece.POLLEN, GamePiece.FIELD, 69, -65, 1.4);

        assertEquals(0, points(score(MatchPeriod.TELEOP, null, null, List.of(held), noTips()), Alliance.RED,
                ScoreItem.GARDEN));
        assertEquals(0, score(MatchPeriod.AUTONOMOUS, null, null, List.of(loose), noTips()).of(Alliance.RED).total());
    }

    // ---- FLOWER --------------------------------------------------------------------------------

    private static GamePiece inFlower(String id, String kind, double z) {
        return ball(id, kind, GamePiece.FLOWER, FLOWER_X, FLOWER_Y, z);
    }

    /**
     * WHAT: The top-most scoring NECTAR's alliance owns the FLOWER and gets 2 for every scoring ball,
     * POLLEN and the other colour's NECTAR included; the bottom-most scoring NECTAR's alliance gets
     * the 5-point bonus (§10.5.2).
     */
    @Test
    void theTopNectarOwnsTheFlowerAndTheBottomNectarGetsTheBonus() {
        List<GamePiece> pieces = List.of(
                inFlower("red-bottom", GamePiece.RED_NECTAR, 5.6),
                inFlower("pollen", GamePiece.POLLEN, 8.8),
                inFlower("blue-top", GamePiece.BLUE_NECTAR, 12.0));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(3, count(score, Alliance.BLUE, ScoreItem.FLOWER), "blue owns it: every scoring ball");
        assertEquals(6, points(score, Alliance.BLUE, ScoreItem.FLOWER));
        assertEquals(0, points(score, Alliance.RED, ScoreItem.FLOWER));
        assertEquals(5, points(score, Alliance.RED, ScoreItem.BOTTOM_NECTAR));
        assertEquals(0, points(score, Alliance.BLUE, ScoreItem.BOTTOM_NECTAR));
    }

    /** WHAT: Only POLLEN and no NECTAR: nobody owns the FLOWER, so its balls score nothing. */
    @Test
    void aFlowerWithNoNectarHasNoOwner() {
        List<GamePiece> pieces = List.of(inFlower("p1", GamePiece.POLLEN, 5.6), inFlower("p2", GamePiece.POLLEN, 8.4));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(0, score.of(Alliance.RED).total());
        assertEquals(0, score.of(Alliance.BLUE).total());
    }

    /**
     * WHAT: A ball wholly below the middle ring (5.25 in) doesn't count, even a
     * NECTAR: it neither owns the FLOWER nor earns the bonus.
     */
    @Test
    void aBallBelowTheScoringVolumeDoesNotCount() {
        List<GamePiece> pieces = List.of(
                inFlower("blue-low", GamePiece.BLUE_NECTAR, 1.8),    // top at 3.6 in: below the middle ring
                inFlower("red", GamePiece.RED_NECTAR, 6.0));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(2, points(score, Alliance.RED, ScoreItem.FLOWER), "red owns it, one scoring ball");
        assertEquals(5, points(score, Alliance.RED, ScoreItem.BOTTOM_NECTAR), "the low blue ball isn't the bottom");
        assertEquals(0, score.of(Alliance.BLUE).total());
    }

    /**
     * WHAT: Of the staged POLLEN (centers 1.4 and 4.2 in), the bottom one is wholly below the middle
     * ring and doesn't score; the next one reaches 5.6 in, over the ring, and scores for the owner.
     */
    @Test
    void theBottomStagedPollenDoesNotScoreButTheNextOneDoes() {
        List<GamePiece> pieces = List.of(
                inFlower("pollen-1", GamePiece.POLLEN, 1.4),
                inFlower("pollen-2", GamePiece.POLLEN, 4.2),
                inFlower("red", GamePiece.RED_NECTAR, 12.0));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(2, count(score, Alliance.RED, ScoreItem.FLOWER), "the 4.2 in POLLEN and the NECTAR");
    }

    /** WHAT: The FLOWERs as staged at the start (POLLEN only, no NECTAR) have no owner and score 0. */
    @Test
    void theStagedFlowersScoreNothing() {
        List<GamePiece> pieces = List.of(
                inFlower("p1", GamePiece.POLLEN, 1.4), inFlower("p2", GamePiece.POLLEN, 4.2),
                inFlower("p3", GamePiece.POLLEN, 7.0), inFlower("p4", GamePiece.POLLEN, 9.8));

        MatchScore score = score(MatchPeriod.TELEOP, null, null, pieces, noTips());

        assertEquals(0, count(score, Alliance.RED, ScoreItem.FLOWER));
        assertEquals(0, count(score, Alliance.BLUE, ScoreItem.FLOWER));
    }

    /** Removes the FLOWER ring keys (scoringBottomInches, scoringTopInches, scoringRadiusInches) wherever they are. */
    private static void dropRingKeys(Object json) throws JSONException {
        if (json instanceof JSONObject o) {
            o.remove("scoringBottomInches");
            o.remove("scoringTopInches");
            o.remove("scoringRadiusInches");
            for (String key : o.keySet().toArray(new String[0])) {
                dropRingKeys(o.get(key));
            }
        } else if (json instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                dropRingKeys(a.get(i));
            }
        }
    }

    /**
     * WHAT: A field file without the FLOWER ring heights scores no FLOWER points, even for a ball
     * that would count with them.
     *
     * <p>WHY: the scoring volume is only between the rings; without them the harness must not guess.</p>
     */
    @Test
    void aFlowerWithoutRingHeightsScoresNothing(@TempDir Path folder) throws IOException, JSONException {
        JSONObject field = new JSONObject(Files.readString(Path.of("fields", ScoringField.BIOBUZZ + ".json")));
        dropRingKeys(field);
        Path copy = folder.resolve("no-rings.json");
        Files.writeString(copy, field.toString());
        List<GamePiece> pieces = List.of(inFlower("red", GamePiece.RED_NECTAR, 12.0));

        MatchScore score = new MatchScorer(ScoringField.read(copy)).score(new ScoreInput(MatchPeriod.TELEOP, null,
                null, ROBOT_INCHES, ROBOT_INCHES, pieces, noTips(), true, 0.0));

        assertEquals(0, score.of(Alliance.RED).total());
    }

    /** WHAT: FLOWERs don't score in AUTO (Table 10-2). */
    @Test
    void flowersDoNotScoreInAuto() {
        MatchScore score = score(MatchPeriod.AUTONOMOUS, null, null,
                List.of(inFlower("red", GamePiece.RED_NECTAR, 6.0)), noTips());

        assertEquals(0, score.of(Alliance.RED).total());
    }

    // ---- Ranking points ------------------------------------------------------------------------

    private static AllianceScore redAfterTips(int tips) {
        return score(MatchPeriod.TELEOP, null, null, List.of(),
                List.of(new HiveTilt("red-hive", tips, tips % 2 == 0 ? 0 : RED_TIPPED_SWING), new HiveTilt("blue-hive", 0, 0))).of(Alliance.RED);
    }

    /** WHAT: POLLINATOR 1 needs 4 TIPS, POLLINATOR 2 needs 7 (Table 10-3, "at or above"). */
    @Test
    void pollinatorRankingPointsNeedFourAndSevenTips() {
        assertFalse(redAfterTips(3).pollinator1Rp());
        assertTrue(redAfterTips(4).pollinator1Rp());
        assertFalse(redAfterTips(4).pollinator2Rp());
        assertFalse(redAfterTips(6).pollinator2Rp());
        assertTrue(redAfterTips(7).pollinator2Rp());
        assertEquals(2, redAfterTips(7).rankingPoints());
        assertEquals(0, redAfterTips(3).rankingPoints());
    }

    /**
     * WHAT: SWARM needs 16 LEAVE + PARK points. One robot in one run earns at most 8 (LEAVE 3 + PARK
     * 5 in AUTO), so it is never reached, and the most a run can earn is below it.
     */
    @Test
    void swarmIsNotReachedByOneRobotInOneRun() {
        MatchScore auto = robotAt(MatchPeriod.AUTONOMOUS, Pose.inches(-35, -52, 90));

        assertEquals(8, points(auto, Alliance.RED, ScoreItem.LEAVE) + points(auto, Alliance.RED, ScoreItem.PARK));
        assertFalse(auto.of(Alliance.RED).swarmRp());
    }

    // ---- Alliance ------------------------------------------------------------------------------

    /** WHAT: The alliance key's value is read in any case; anything else is no alliance. */
    @Test
    void anAllianceIsParsedFromTheKey() {
        assertEquals(Alliance.RED, Alliance.parse("red"));
        assertEquals(Alliance.BLUE, Alliance.parse("BLUE"));
        assertNull(Alliance.parse("green"));
    }

    /** WHAT: "Red" or "Blue" in an OpMode's name gives its alliance; neither or both gives none. */
    @Test
    void anAllianceComesFromTheOpModesName() {
        assertEquals(Alliance.RED, Alliance.fromName("ClaudeCornerLockAutoRed"));
        assertEquals(Alliance.BLUE, Alliance.fromName("Hive Auto Blue"));
        assertNull(Alliance.fromName("SimpleLeave"));
        assertNull(Alliance.fromName("RedOrBlueTest"));
    }

    /**
     * WHAT: A profile's {@code alliance.<OpMode>} key wins over the name; without it the name decides;
     * with neither there's no alliance; a value that isn't red or blue is refused.
     */
    @Test
    void anAllianceComesFromTheProfileKeyFirst() {
        String settings = readSettings();
        HarnessConfig config = HarnessConfig.fromText(settings + "\nalliance.SimpleLeave=blue\n"
                + "alliance.Hive\\ Auto\\ Red=blue\n", "alliance-test");

        assertEquals(Alliance.BLUE, config.allianceFor("Simple Leave", "SimpleLeave"), "key by class name");
        assertEquals(Alliance.BLUE, config.allianceFor("Hive Auto Red", "HiveAutoRed"), "key beats the name");
        assertEquals(Alliance.RED, config.allianceFor("Claude Corner Lock Red", "ClaudeCornerLockAutoRed"), "name");
        assertNull(config.allianceFor("Teleop V1", "TeleopV1"), "neither");
        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class,
                () -> HarnessConfig.fromText(settings + "\nalliance.SimpleLeave=green\n", "bad-alliance"));
        assertTrue(bad.getMessage().contains("alliance.SimpleLeave: expected red or blue"), bad.getMessage());
    }

    private static String readSettings() {
        try {
            return Files.readString(Path.of(HarnessConfig.FILE_NAME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** WHAT: A robot with no alliance earns no LEAVE or PARK for either side. */
    @Test
    void aRobotWithNoAllianceEarnsNothing() {
        MatchScore score = score(MatchPeriod.AUTONOMOUS, null, Pose.inches(-35, -52, 90), List.of(), noTips());

        assertNull(score.robotAlliance());
        assertEquals(0, score.of(Alliance.RED).total());
        assertEquals(0, score.of(Alliance.BLUE).total());
    }
}
