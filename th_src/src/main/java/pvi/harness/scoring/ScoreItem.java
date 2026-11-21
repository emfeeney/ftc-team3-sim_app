package pvi.harness.scoring;

/**
 * One line of the manual's point table (Competition Manual TU06 §10.5, Table 10-2) for one alliance: what was
 * done, how many times, and the points it earned.
 *
 * @param name       one of the names below, e.g. {@link #PARK}
 * @param count      how many times it was earned (balls, tips, or 1 for a robot that parked)
 * @param pointsEach the table's points for one, in this period
 * @param points     {@code count * pointsEach}
 */
public record ScoreItem(String name, int count, int pointsEach, int points) {
    /** The robot no longer touches the perimeter wall at the end of AUTO. */
    public static final String LEAVE = "LEAVE";
    /** The robot is at least partly in its alliance's LOADING ZONE. */
    public static final String PARK = "PARK";
    /** The alliance's HIVE finished tipping. */
    public static final String HIVE_TIP = "HIVE TIP";
    /** A POLLEN or NECTAR left in the alliance's upward CELL at the end of the match. */
    public static final String CELL = "CELL";
    /** A POLLEN or NECTAR in a FLOWER that the alliance owns. */
    public static final String FLOWER = "FLOWER";
    /** The alliance has the bottom-most scoring NECTAR in a FLOWER. */
    public static final String BOTTOM_NECTAR = "BOTTOM NECTAR";
    /** A POLLEN or NECTAR in the alliance's GARDEN. */
    public static final String GARDEN = "GARDEN";

    /** Refuses a missing name, negative numbers and points that don't add up. */
    public ScoreItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A score line needs a name.");
        }
        if (count < 0 || pointsEach < 0 || points != count * pointsEach) {
            throw new IllegalArgumentException("Score line " + name + ": " + count + " x " + pointsEach
                    + " should be " + count * pointsEach + " points, not " + points + ".");
        }
    }

    /** A line earned {@code count} times at {@code pointsEach}. */
    public static ScoreItem of(String name, int count, int pointsEach) {
        return new ScoreItem(name, count, pointsEach, count * pointsEach);
    }
}
