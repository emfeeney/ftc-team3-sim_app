package pvi.harness.physics;

/**
 * Something on the field a loose ball can hit, as plain data: a part of the HIVE frame or a
 * FLOWER. Every obstacle stands upright with straight sides, so a ball hits it in the same place
 * whatever its height, as long as the obstacle reaches down to the ball.
 *
 * <p>A ball of radius {@code r} sits with its center at {@code r} and its top at {@code 2r}. It
 * can only hit an obstacle whose bottom is below the ball's top ({@code bottomInches < 2r}); it
 * rolls under anything higher (for example a FLOWER's raised head).</p>
 *
 * @param id           a fixed name, the same as in {@code fields/biobuzz-2026.json}
 * @param cylinder     true for an upright cylinder (center and radius), false for an
 *                     axis-aligned box (min and max x and y)
 * @param minXInches   box: the side nearest -x (FTC field frame, inches). Cylinder: center x.
 * @param maxXInches   box: the side nearest +x. Cylinder: radius.
 * @param minYInches   box: the side nearest -y. Cylinder: center y.
 * @param maxYInches   box: the side nearest +y. Cylinder: unused (0).
 * @param bottomInches height of its lowest point above the tiles
 * @param topInches    height of its highest point (a flying ball meets it only between the two); infinite
 *                     for one that only rolling balls meet
 */
public record PieceObstacle(String id, boolean cylinder, double minXInches, double maxXInches,
                            double minYInches, double maxYInches, double bottomInches, double topInches) {

    /** An upright box, axis-aligned, that only rolling balls meet. */
    public static PieceObstacle box(String id, double minX, double maxX, double minY, double maxY, double bottom) {
        return box(id, minX, maxX, minY, maxY, bottom, Double.POSITIVE_INFINITY);
    }

    /** An upright box, axis-aligned, from {@code bottom} up to {@code top}. */
    public static PieceObstacle box(String id, double minX, double maxX, double minY, double maxY, double bottom, double top) {
        if (!(minX < maxX) || !(minY < maxY) || !(bottom < top)) {
            throw new IllegalArgumentException("Obstacle " + id + ": a box needs min below max on all three axes.");
        }
        return new PieceObstacle(id, false, minX, maxX, minY, maxY, bottom, top);
    }

    /** An upright cylinder that only rolling balls meet. */
    public static PieceObstacle cylinder(String id, double x, double y, double radius, double bottom) {
        return cylinder(id, x, y, radius, bottom, Double.POSITIVE_INFINITY);
    }

    /** An upright cylinder, from {@code bottom} up to {@code top}. */
    public static PieceObstacle cylinder(String id, double x, double y, double radius, double bottom, double top) {
        if (!(radius > 0) || !(bottom < top)) {
            throw new IllegalArgumentException("Obstacle " + id + ": a cylinder needs a radius above zero and its bottom below its top.");
        }
        return new PieceObstacle(id, true, x, radius, y, 0, bottom, top);
    }

    /** True if a ball of this radius, sitting on the tiles, reaches this obstacle. */
    public boolean reachedBy(double ballRadiusInches) {
        return bottomInches < 2 * ballRadiusInches;
    }
}
