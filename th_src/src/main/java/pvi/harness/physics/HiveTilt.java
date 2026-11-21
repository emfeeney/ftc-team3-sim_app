package pvi.harness.physics;

/**
 * One HIVE's position at one moment, as plain data, for the run recording and displays.
 *
 * @param id           the HIVE, e.g. "red-hive"
 * @param tips         how many times it has started to tip so far
 * @param swingDegrees how far it has turned from its starting position, about the field's y axis: positive
 *                     takes its +x side up. A full tip is 60 degrees: the red HIVE goes to -60, the blue to
 *                     +60, and back to 0 on the next tip.
 */
public record HiveTilt(String id, int tips, double swingDegrees) {
}
