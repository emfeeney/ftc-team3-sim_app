package pvi.harness.runtime;

import java.util.List;

/**
 * Where a Pedro Pathing robot's code plans to drive next, from one moment of a run: the rest of
 * the path its {@code Follower} is following, as points on the field (D39). The viewer and VI Arena
 * draw it as a yellow line.
 *
 * <p>A run records one of these each time the plan changes: a new path, the follower moving on to
 * the path's next segment, or the follower starting or stopping following. An empty
 * {@link #points()} means there is no plan at that moment (the follower is holding, driven by hand,
 * idle, or not made yet). Displays draw from the robot to the point nearest it, then on to the end.</p>
 *
 * @param sinceInitSeconds seconds since INIT when the plan changed
 * @param matchTimeSeconds seconds since PLAY, or 0 before PLAY
 * @param points           from the start of the current path segment to the path's end, in the FTC
 *                         field frame (inches), at most {@link PedroPathWatcher#SAMPLE_SPACING_INCHES}
 *                         apart; empty for no plan
 */
public record PlannedPath(double sinceInitSeconds, double matchTimeSeconds, List<Point> points) {

    /** Copies the list, so the plan can't change after it is made. */
    public PlannedPath {
        points = List.copyOf(points);
    }

    /** True if there is no plan at this moment. */
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * The line to draw for a robot at ({@code xInches}, {@code yInches}): from the robot to the plan's
     * point nearest it, then on through the rest of the plan. Empty if there is no plan. Every display
     * uses this same rule, so the Swing viewer and VI Arena draw the same line.
     */
    public List<Point> aheadOf(double xInches, double yInches) {
        if (points.isEmpty()) {
            return List.of();
        }
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double d = Math.hypot(points.get(i).xInches() - xInches, points.get(i).yInches() - yInches);
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        List<Point> line = new java.util.ArrayList<>();
        line.add(new Point(xInches, yInches));
        line.addAll(points.subList(nearest, points.size()));
        return List.copyOf(line);
    }

    /**
     * One point of a planned path, in the FTC field frame.
     *
     * @param xInches x from the field's center, in inches
     * @param yInches y from the field's center, in inches
     */
    public record Point(double xInches, double yInches) {
        /** Rejects numbers that aren't finite, so a broken path fails early. */
        public Point {
            if (!Double.isFinite(xInches) || !Double.isFinite(yInches)) {
                throw new IllegalArgumentException("A planned path point needs finite inches, got ("
                        + xInches + ", " + yInches + ").");
            }
        }
    }
}
