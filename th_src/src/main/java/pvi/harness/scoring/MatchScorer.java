package pvi.harness.scoring;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.MatchPeriod;

/**
 * Works out both alliances' points for one moment, by the BIOBUZZ Competition Manual TU06, §10.5 (Table 10-2 points,
 * Table 10-3 "All Other Events" ranking-point thresholds), as manager decision D50 sets out.
 *
 * <p>Plain data in, plain data out: give it a {@link ScoreInput} (the period, the robot, the balls and the HIVEs)
 * and it gives a {@link MatchScore}. It keeps nothing between calls, so a run can be scored at every loop (the score
 * "as it stands") and once more at the end, after the pieces have come to rest.</p>
 *
 * <ul>
 *   <li><b>HIVE TIP</b> (20, AUTO and TELEOP): to the HIVE's colour, once its tip is complete (it has swung all the way
 *       to its other side).</li>
 *   <li><b>LEAVE</b> (3, AUTO): the robot box doesn't touch the perimeter wall ({@link #WALL_TOUCH_INCHES}).</li>
 *   <li><b>PARK</b> (5, AUTO and TELEOP): the robot box is at least partly in its own alliance's LOADING ZONE.</li>
 *   <li><b>CELL</b> (2 each, TELEOP): POLLEN and NECTAR in a HIVE's upward CELL, to the HIVE's colour; none while it
 *       tips.</li>
 *   <li><b>FLOWER</b> (2 each, TELEOP): balls at least partly in a FLOWER's scoring volume, to the colour of the
 *       top-most scoring NECTAR (the owner); none without a scoring NECTAR.</li>
 *   <li><b>BOTTOM NECTAR</b> (5, TELEOP): per FLOWER, to the colour of the bottom-most scoring NECTAR.</li>
 *   <li><b>GARDEN</b> (1 each, TELEOP): balls at least partly in a GARDEN, to the GARDEN's colour, whoever put them
 *       there. Held, flying and alliance-area balls don't count.</li>
 * </ul>
 *
 * <p>Only the simulated robot earns LEAVE and PARK, and only for its alliance. No fouls, penalties, WIN or TIE
 * (D50).</p>
 */
public final class MatchScorer {
    /** The robot touches the perimeter wall if a corner of its box is this close to the wall (or past it), inches. */
    public static final double WALL_TOUCH_INCHES = 0.1;
    /** A HIVE within this many degrees of a stable position has finished its tip. */
    static final double STABLE_DEGREES = 0.5;
    /** SWARM RP: LEAVE + PARK points at or above this (Table 10-3, All Other Events). */
    public static final int SWARM_POINTS = 16;
    /** POLLINATOR 1 RP: HIVE TIPs at or above this. */
    public static final int POLLINATOR_1_TIPS = 4;
    /** POLLINATOR 2 RP: HIVE TIPs at or above this. */
    public static final int POLLINATOR_2_TIPS = 7;

    private final ScoringField field;

    /** A scorer for this field. */
    public MatchScorer(ScoringField field) {
        if (field == null) {
            throw new IllegalArgumentException("A scorer needs a field (ScoringField.none() for one with nothing to score).");
        }
        this.field = field;
    }

    /** The field it scores on. */
    public ScoringField field() {
        return field;
    }

    /**
     * The point table's lines for one period, in order, each with its points for one (and a count of 0).
     * AUTONOMOUS: LEAVE 3, PARK 5, HIVE TIP 20. TELEOP: PARK 5, HIVE TIP 20, CELL 2, FLOWER 2, BOTTOM NECTAR 5, GARDEN 1.
     */
    public static List<ScoreItem> linesFor(MatchPeriod period) {
        if (period == MatchPeriod.AUTONOMOUS) {
            return List.of(ScoreItem.of(ScoreItem.LEAVE, 0, 3), ScoreItem.of(ScoreItem.PARK, 0, 5),
                    ScoreItem.of(ScoreItem.HIVE_TIP, 0, 20));
        }
        return List.of(ScoreItem.of(ScoreItem.PARK, 0, 5), ScoreItem.of(ScoreItem.HIVE_TIP, 0, 20),
                ScoreItem.of(ScoreItem.CELL, 0, 2), ScoreItem.of(ScoreItem.FLOWER, 0, 2),
                ScoreItem.of(ScoreItem.BOTTOM_NECTAR, 0, 5), ScoreItem.of(ScoreItem.GARDEN, 0, 1));
    }

    /** Both alliances' points for this moment. */
    public MatchScore score(ScoreInput input) {
        Map<Alliance, Map<String, Integer>> counts = new EnumMap<>(Alliance.class);
        for (Alliance alliance : Alliance.values()) {
            counts.put(alliance, new java.util.HashMap<>());
        }
        countTips(input, counts);
        countRobot(input, counts);
        if (input.period() == MatchPeriod.TELEOP) {
            countCells(input, counts);
            countFlowers(input, counts);
            countGardens(input, counts);
        }
        return new MatchScore(input.period(), input.robotAlliance(), input.settled(), input.settleSeconds(),
                allianceScore(Alliance.RED, input.period(), counts.get(Alliance.RED)),
                allianceScore(Alliance.BLUE, input.period(), counts.get(Alliance.BLUE)),
                input.period() == MatchPeriod.TELEOP ? field.notes() : List.of());
    }

    private static AllianceScore allianceScore(Alliance alliance, MatchPeriod period, Map<String, Integer> counts) {
        List<ScoreItem> items = new ArrayList<>();
        for (ScoreItem line : linesFor(period)) {
            items.add(ScoreItem.of(line.name(), counts.getOrDefault(line.name(), 0), line.pointsEach()));
        }
        AllianceScore draft = new AllianceScore(alliance, items, items.stream().mapToInt(ScoreItem::points).sum(),
                false, false, false);
        int robotPoints = draft.item(ScoreItem.LEAVE).points() + draft.item(ScoreItem.PARK).points();
        int tips = draft.item(ScoreItem.HIVE_TIP).count();
        return new AllianceScore(alliance, items, draft.total(), robotPoints >= SWARM_POINTS,
                tips >= POLLINATOR_1_TIPS, tips >= POLLINATOR_2_TIPS);
    }

    private static void add(Map<Alliance, Map<String, Integer>> counts, Alliance alliance, String line, int howMany) {
        if (alliance != null && howMany > 0) {
            counts.get(alliance).merge(line, howMany, Integer::sum);
        }
    }

    // ---- HIVEs ---------------------------------------------------------------------------------------------

    private void countTips(ScoreInput input, Map<Alliance, Map<String, Integer>> counts) {
        for (HiveTilt tilt : input.hives()) {
            ScoringField.Hive hive = hive(tilt.id());
            if (hive != null) {
                add(counts, hive.alliance(), ScoreItem.HIVE_TIP, completedTips(hive, tilt));
            }
        }
    }

    /** Tips started, less one if the last is still under way (the HIVE isn't yet at the stable position it's heading for). */
    static int completedTips(ScoringField.Hive hive, HiveTilt tilt) {
        return Math.abs(tilt.swingDegrees() - stableSwing(hive, tilt.tips())) <= STABLE_DEGREES ? tilt.tips() : tilt.tips() - 1;
    }

    /** The swing after {@code tips} complete tips: 0 after an even number, twice the tilt (taking the up side down) after an odd one. */
    private static double stableSwing(ScoringField.Hive hive, int tips) {
        return tips % 2 == 0 ? 0 : -hive.startUpSide() * 2 * hive.tiltDegrees();
    }

    /** Which CELL is up now: +1 for the +x one, -1 for the -x one, 0 while the HIVE is between them. */
    static int upSide(ScoringField.Hive hive, HiveTilt tilt) {
        if (Math.abs(tilt.swingDegrees() - stableSwing(hive, tilt.tips())) > STABLE_DEGREES) {
            return 0;
        }
        return tilt.tips() % 2 == 0 ? hive.startUpSide() : -hive.startUpSide();
    }

    private ScoringField.Hive hive(String id) {
        for (ScoringField.Hive hive : field.hives()) {
            if (hive.id().equals(id)) {
                return hive;
            }
        }
        return null;
    }

    private void countCells(ScoreInput input, Map<Alliance, Map<String, Integer>> counts) {
        for (HiveTilt tilt : input.hives()) {
            ScoringField.Hive hive = hive(tilt.id());
            int up = hive == null ? 0 : upSide(hive, tilt);
            if (up == 0) {
                continue;
            }
            int balls = 0;
            for (GamePiece p : input.pieces()) {
                if (GamePiece.CELL.equals(p.state()) && p.yInches() >= hive.minYInches() && p.yInches() <= hive.maxYInches()
                        && Math.signum(p.xInches() - hive.pivotXInches()) == up) {
                    balls++;
                }
            }
            add(counts, hive.alliance(), ScoreItem.CELL, balls);
        }
    }

    // ---- The robot -----------------------------------------------------------------------------------------

    private void countRobot(ScoreInput input, Map<Alliance, Map<String, Integer>> counts) {
        Alliance alliance = input.robotAlliance();
        if (alliance == null || input.robot() == null) {
            return;
        }
        double[][] corners = corners(input.robot(), input.robotLengthInches(), input.robotWidthInches());
        if (input.period() == MatchPeriod.AUTONOMOUS && !touchesWall(corners)) {
            add(counts, alliance, ScoreItem.LEAVE, 1);
        }
        ScoringField.Zone zone = field.loadingZones().get(alliance);
        if (zone != null && overlaps(corners, zone)) {
            add(counts, alliance, ScoreItem.PARK, 1);
        }
    }

    /** The robot box's four corners, {x, y} in inches. */
    static double[][] corners(Pose robot, double lengthInches, double widthInches) {
        double cos = Math.cos(robot.headingRadians());
        double sin = Math.sin(robot.headingRadians());
        double[][] corners = new double[4][];
        int k = 0;
        for (int forward = -1; forward <= 1; forward += 2) {
            for (int left = -1; left <= 1; left += 2) {
                double f = forward * lengthInches / 2;
                double l = left * widthInches / 2;
                corners[k++] = new double[] {robot.xInches() + f * cos - l * sin, robot.yInches() + f * sin + l * cos};
            }
        }
        return corners;
    }

    private static boolean touchesWall(double[][] corners) {
        double near = Pose.FIELD_HALF_WIDTH_INCHES - WALL_TOUCH_INCHES;
        for (double[] c : corners) {
            if (Math.abs(c[0]) >= near || Math.abs(c[1]) >= near) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the robot box and the zone share any part (touching counts: the zone includes its tape). Two boxes
     * overlap unless some line separates them; for boxes only their own four side directions need checking.
     */
    static boolean overlaps(double[][] robot, ScoringField.Zone zone) {
        double[][] box = {{zone.minXInches(), zone.minYInches()}, {zone.maxXInches(), zone.minYInches()},
                {zone.minXInches(), zone.maxYInches()}, {zone.maxXInches(), zone.maxYInches()}};
        double[][] axes = {{1, 0}, {0, 1},
                {robot[2][0] - robot[0][0], robot[2][1] - robot[0][1]},
                {robot[1][0] - robot[0][0], robot[1][1] - robot[0][1]}};
        for (double[] axis : axes) {
            double[] a = span(robot, axis);
            double[] b = span(box, axis);
            if (a[1] < b[0] || b[1] < a[0]) {
                return false;
            }
        }
        return true;
    }

    private static double[] span(double[][] points, double[] axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] p : points) {
            double d = p[0] * axis[0] + p[1] * axis[1];
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[] {min, max};
    }

    // ---- GARDENs and FLOWERs -------------------------------------------------------------------------------

    private void countGardens(ScoreInput input, Map<Alliance, Map<String, Integer>> counts) {
        for (Map.Entry<Alliance, ScoringField.Zone> garden : field.gardens().entrySet()) {
            int balls = 0;
            for (GamePiece p : input.pieces()) {
                if (onTheField(p) && circleTouches(p, garden.getValue())) {
                    balls++;
                }
            }
            add(counts, garden.getKey(), ScoreItem.GARDEN, balls);
        }
    }

    /** A ball that isn't held, flying or waiting outside the field. */
    private static boolean onTheField(GamePiece p) {
        return !GamePiece.HELD.equals(p.state()) && !GamePiece.FLYING.equals(p.state())
                && !GamePiece.ALLIANCE_AREA.equals(p.state());
    }

    /** True if the ball, seen from above, is at least partly in the zone. */
    static boolean circleTouches(GamePiece p, ScoringField.Zone zone) {
        double nearestX = Math.max(zone.minXInches(), Math.min(p.xInches(), zone.maxXInches()));
        double nearestY = Math.max(zone.minYInches(), Math.min(p.yInches(), zone.maxYInches()));
        return Math.hypot(p.xInches() - nearestX, p.yInches() - nearestY) <= p.radiusInches();
    }

    private void countFlowers(ScoreInput input, Map<Alliance, Map<String, Integer>> counts) {
        for (ScoringField.Flower flower : field.flowers()) {
            int balls = 0;
            GamePiece topNectar = null;
            GamePiece bottomNectar = null;
            for (GamePiece p : input.pieces()) {
                if (!GamePiece.FLOWER.equals(p.state()) || !inScoringVolume(p, flower)) {
                    continue;
                }
                balls++;
                if (nectarAlliance(p) != null) {
                    if (topNectar == null || p.zInches() > topNectar.zInches()) {
                        topNectar = p;
                    }
                    if (bottomNectar == null || p.zInches() < bottomNectar.zInches()) {
                        bottomNectar = p;
                    }
                }
            }
            if (topNectar != null) {
                add(counts, nectarAlliance(topNectar), ScoreItem.FLOWER, balls);
                add(counts, nectarAlliance(bottomNectar), ScoreItem.BOTTOM_NECTAR, 1);
            }
        }
    }

    /** True if the ball is in this FLOWER and at least partly between the scoring volume's bottom and top. */
    static boolean inScoringVolume(GamePiece p, ScoringField.Flower flower) {
        boolean inTube = Math.hypot(p.xInches() - flower.xInches(), p.yInches() - flower.yInches()) <= flower.radiusInches();
        double r = p.radiusInches();
        return inTube && p.zInches() + r > flower.bottomInches() && p.zInches() - r < flower.topInches();
    }

    /** A NECTAR's alliance; null for POLLEN. */
    static Alliance nectarAlliance(GamePiece p) {
        return GamePiece.RED_NECTAR.equals(p.kind()) ? Alliance.RED
                : GamePiece.BLUE_NECTAR.equals(p.kind()) ? Alliance.BLUE : null;
    }
}
