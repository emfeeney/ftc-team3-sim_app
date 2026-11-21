package pvi.harness.physics;

/**
 * Where one ball is and how fast it is moving, as plain data, in the FTC field frame (inches, z up).
 * In outside physics (Unity moving the balls, decision D30) Unity sends these for the balls it moves
 * ({@link GamePieces#supply}), and Java sends them back for a ball it just started moving
 * ({@link GamePieces#takeLaunches}).
 *
 * @param id                the piece's id, e.g. "pollen-preload-1"
 * @param xInches           where its center is
 * @param yInches           where its center is
 * @param zInches           how high its center is (a ball on the tiles is at its radius)
 * @param vxInchesPerSecond its speed along x
 * @param vyInchesPerSecond its speed along y
 * @param vzInchesPerSecond its upward speed
 */
public record PieceMotion(String id, double xInches, double yInches, double zInches,
                          double vxInchesPerSecond, double vyInchesPerSecond, double vzInchesPerSecond) {
    /** Refuses a missing id and any number that isn't finite. */
    public PieceMotion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A piece motion needs the piece's id.");
        }
        for (double v : new double[] {xInches, yInches, zInches, vxInchesPerSecond, vyInchesPerSecond, vzInchesPerSecond}) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("Piece \"" + id + "\": every position and speed must be a finite number,"
                        + " got (" + xInches + ", " + yInches + ", " + zInches + ") moving (" + vxInchesPerSecond + ", "
                        + vyInchesPerSecond + ", " + vzInchesPerSecond + ").");
            }
        }
    }
}
