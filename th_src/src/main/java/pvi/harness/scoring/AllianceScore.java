package pvi.harness.scoring;

import java.util.List;

/**
 * One alliance's points: each line of the point table, the total, and the ranking points it reached (Table 10-3,
 * "All Other Events" thresholds). WIN and TIE need the other alliance's score and aren't given (manager decision D50).
 *
 * @param alliance       whose score this is
 * @param items          the lines for this period, in the table's order
 * @param total          the sum of the lines' points
 * @param swarmRp        LEAVE + PARK points reached {@link MatchScorer#SWARM_POINTS}
 * @param pollinator1Rp  HIVE TIPs reached {@link MatchScorer#POLLINATOR_1_TIPS}
 * @param pollinator2Rp  HIVE TIPs reached {@link MatchScorer#POLLINATOR_2_TIPS}
 */
public record AllianceScore(Alliance alliance, List<ScoreItem> items, int total,
                            boolean swarmRp, boolean pollinator1Rp, boolean pollinator2Rp) {

    /** Copies the list, and refuses a total that isn't the sum of the lines. */
    public AllianceScore {
        if (alliance == null) {
            throw new IllegalArgumentException("A score needs its alliance.");
        }
        items = List.copyOf(items);
        int sum = items.stream().mapToInt(ScoreItem::points).sum();
        if (sum != total) {
            throw new IllegalArgumentException(alliance + "'s total " + total + " isn't the sum of its lines, " + sum + ".");
        }
    }

    /** The ranking points reached: one each for SWARM, POLLINATOR 1 and POLLINATOR 2. */
    public int rankingPoints() {
        return (swarmRp ? 1 : 0) + (pollinator1Rp ? 1 : 0) + (pollinator2Rp ? 1 : 0);
    }

    /** The line called {@code name}, or a line with nothing earned (0 points each) if this period has no such line. */
    public ScoreItem item(String name) {
        for (ScoreItem item : items) {
            if (item.name().equals(name)) {
                return item;
            }
        }
        return ScoreItem.of(name, 0, 0);
    }
}
