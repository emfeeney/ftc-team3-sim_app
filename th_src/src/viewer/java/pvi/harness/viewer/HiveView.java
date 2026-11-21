package pvi.harness.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pvi.harness.physics.BioBuzzHive;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;

/**
 * What the viewer shows about the HIVEs at one moment: where each CELL is seen from above, whether it
 * is up, down or tipping, and how many balls are in it. Worked out from the recording's {@code hives}
 * and {@code pieces} with no window, so tests can check it.
 */
public final class HiveView {
    /** A HIVE counts as tipping while its tilt is less than this far from level (it rests at 30). */
    private static final double RESTING_TILT_DEGREES = 29.9;

    /**
     * One CELL at one moment.
     *
     * @param hiveId  the HIVE, e.g. "red-hive"
     * @param side    +1 for the CELL on the HIVE's +x side (the audience side), -1 for the -x side (far)
     * @param minX    the CELL seen from above: its smallest x, inches
     * @param maxX    its largest x
     * @param minY    its smallest y
     * @param maxY    its largest y
     * @param up      true if this is the upward CELL, the one that catches balls
     * @param tipping true while its HIVE is tipping (then neither CELL is up)
     * @param nectar  NECTAR balls in it
     * @param pollen  POLLEN balls in it
     */
    public record Cell(String hiveId, int side, double minX, double maxX, double minY, double maxY,
                       boolean up, boolean tipping, int nectar, int pollen) {
        /** "+x" or "-x". */
        public String sideName() {
            return side > 0 ? "+x" : "-x";
        }
    }

    /** Only static methods here; nothing to create. */
    private HiveView() {
    }

    /**
     * Every CELL of every HIVE in {@code hives} (two each, +x side first), with the balls of
     * {@code pieces} that are in it. Empty if there are no HIVEs (an older recording).
     */
    public static List<Cell> cells(List<HiveTilt> hives, List<GamePiece> pieces) {
        List<Cell> cells = new ArrayList<>();
        for (HiveTilt tilt : hives) {
            BioBuzzHive.Hive hive = find(tilt.id());
            if (hive == null) {
                continue;
            }
            double tiltDegrees = hive.startTiltDegrees() + tilt.swingDegrees();
            boolean tipping = Math.abs(tiltDegrees) < RESTING_TILT_DEGREES;
            for (int side : new int[] {1, -1}) {
                double[] x = footprintX(hive, tiltDegrees, side);
                int nectar = 0;
                int pollen = 0;
                for (GamePiece p : pieces) {
                    if (inCell(p, hive, side)) {
                        if (GamePiece.POLLEN.equals(p.kind())) {
                            pollen++;
                        } else {
                            nectar++;
                        }
                    }
                }
                cells.add(new Cell(hive.id(), side, x[0], x[1],
                        hive.pivotYInches() - BioBuzzHive.CELL_HALF_WIDTH_INCHES,
                        hive.pivotYInches() + BioBuzzHive.CELL_HALF_WIDTH_INCHES,
                        !tipping && side == Math.signum(tiltDegrees), tipping, nectar, pollen));
            }
        }
        return cells;
    }

    /** One line per CELL for the side panel, e.g. "red-hive +x  UP       2 NECTAR  1 POLLEN". */
    public static List<String> lines(List<HiveTilt> hives, List<Cell> cells) {
        List<String> lines = new ArrayList<>();
        for (Cell cell : cells) {
            String state = cell.tipping() ? "TIPPING" : cell.up() ? "UP" : "down";
            lines.add(String.format(Locale.ROOT, "%-9s %s  %-8s %d NECTAR  %d POLLEN", cell.hiveId(), cell.sideName(),
                    state, cell.nectar(), cell.pollen()));
        }
        for (HiveTilt tilt : hives) {
            lines.add(String.format(Locale.ROOT, "%-9s tipped %d time%s", tilt.id(), tilt.tips(), tilt.tips() == 1 ? "" : "s"));
        }
        return lines;
    }

    /** True if {@code p} is a ball resting in that CELL: state "cell", across the HIVE on that side. */
    static boolean inCell(GamePiece p, BioBuzzHive.Hive hive, int side) {
        return GamePiece.CELL.equals(p.state())
                && Math.abs(p.yInches() - hive.pivotYInches()) <= BioBuzzHive.CELL_HALF_WIDTH_INCHES + 0.5
                && Math.signum(p.xInches() - hive.pivotXInches()) == side;
    }

    /**
     * The CELL's smallest and largest x seen from above: the corners of its side view (back wall to
     * opening along the arm, floor to top square to it), turned by the HIVE's tilt.
     */
    static double[] footprintX(BioBuzzHive.Hive hive, double tiltDegrees, int side) {
        double t = Math.toRadians(tiltDegrees);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double along : new double[] {BioBuzzHive.BACK_WALL_INCHES, BioBuzzHive.OPENING_INCHES}) {
            for (double square : new double[] {BioBuzzHive.FLOOR_INCHES, BioBuzzHive.TOP_INCHES}) {
                double x = hive.pivotXInches() + side * along * Math.cos(t) - square * Math.sin(t);
                min = Math.min(min, x);
                max = Math.max(max, x);
            }
        }
        return new double[] {min, max};
    }

    private static BioBuzzHive.Hive find(String id) {
        for (BioBuzzHive.Hive hive : BioBuzzHive.hives()) {
            if (hive.id().equals(id)) {
                return hive;
            }
        }
        return null;
    }
}
