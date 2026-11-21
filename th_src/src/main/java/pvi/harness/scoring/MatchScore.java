package pvi.harness.scoring;

import java.util.List;

import pvi.harness.runtime.MatchPeriod;

/**
 * Both alliances' points for one run, as plain data, for tests, the run recording and the displays.
 *
 * <p>A run covers one period, so it gets that period's column of the point table (manager decision D50): an
 * AUTONOMOUS run scores LEAVE, PARK and HIVE TIPs; a TELEOP run scores PARK, HIVE TIPs and the end-of-match
 * items (CELL, FLOWER, BOTTOM NECTAR, GARDEN).</p>
 *
 * @param period        which column of the point table
 * @param robotAlliance the simulated robot's alliance, or null if it has none (it then earns no LEAVE or PARK)
 * @param settled       true once the pieces have come to rest after STOP (the final score); false for a score
 *                      taken during the run
 * @param settleSeconds how long the pieces took to come to rest after STOP, in seconds (0 during the run; 5 if they
 *                      were still moving after the 5 s limit)
 * @param red           the red alliance's points
 * @param blue          the blue alliance's points
 * @param notes         what couldn't be scored and why, e.g. a FLOWER whose scoring volume the field file lacks;
 *                      empty if nothing
 */
public record MatchScore(MatchPeriod period, Alliance robotAlliance, boolean settled, double settleSeconds,
                         AllianceScore red, AllianceScore blue, List<String> notes) {

    /** Refuses a missing period or alliance score, or a score filed under the wrong alliance. */
    public MatchScore {
        if (period == null || red == null || blue == null) {
            throw new IllegalArgumentException("A match score needs a period and both alliances' scores.");
        }
        if (red.alliance() != Alliance.RED || blue.alliance() != Alliance.BLUE) {
            throw new IllegalArgumentException("The red and blue scores are swapped.");
        }
        if (!Double.isFinite(settleSeconds) || settleSeconds < 0) {
            throw new IllegalArgumentException("settleSeconds must be 0 or more, not " + settleSeconds + ".");
        }
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** A score with nothing left unscored. */
    public MatchScore(MatchPeriod period, Alliance robotAlliance, boolean settled, double settleSeconds,
                      AllianceScore red, AllianceScore blue) {
        this(period, robotAlliance, settled, settleSeconds, red, blue, List.of());
    }

    /** The same score taken at another moment: settled or not, after this long. */
    public MatchScore withSettle(boolean nowSettled, double nowSettleSeconds) {
        return new MatchScore(period, robotAlliance, nowSettled, nowSettleSeconds, red, blue, notes);
    }

    /** One alliance's points. */
    public AllianceScore of(Alliance alliance) {
        return alliance == Alliance.RED ? red : blue;
    }

    /** Nothing scored yet, in this period. */
    public static MatchScore none(MatchPeriod period) {
        return new MatchScore(period, null, false, 0, nothingFor(Alliance.RED, period), nothingFor(Alliance.BLUE, period));
    }

    private static AllianceScore nothingFor(Alliance alliance, MatchPeriod period) {
        List<ScoreItem> items = MatchScorer.linesFor(period).stream()
                .map(line -> ScoreItem.of(line.name(), 0, line.pointsEach())).toList();
        return new AllianceScore(alliance, items, 0, false, false, false);
    }
}
