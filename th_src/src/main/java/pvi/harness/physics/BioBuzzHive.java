package pvi.harness.physics;

import java.util.List;

/**
 * The BIOBUZZ HIVE structure as a flying ball meets it, in 3D: the frame, the two HIVEs that swing on
 * their pivots, and the CELL at each end of a HIVE that a ball can land in (TU06 9.6). Written into
 * Java, like {@link BioBuzzObstacles}, so the simulation never reads files; a test checks it against
 * {@code fields/biobuzz-2026.json} (version 4: its {@code hives[].cells} and {@code flightObstacles}).
 *
 * <p><b>A HIVE's level frame:</b> its own frame with the HIVE level, from its pivot: x along the arm
 * (the +x CELL is on the field's +x side), y along the field's y, and z square to the arm. A HIVE's tilt
 * turns that frame about the field's y axis, positive raising its +x side.</p>
 *
 * <p><b>The CELL:</b> a box 12 in deep along the arm, 20.16 in wide and 14 in tall, open on its outer
 * face (TU06 9.6.2: "approximately 20 in wide by 14 in tall and 12 in deep"). The back wall's and the
 * floor's places are from the field CAD, and they put the 3 staged NECTAR where {@link BioBuzzPieces}
 * has them. Simplified: the CAD's walls are panels with an arched rim and side rails; here they are
 * five flat, thin walls, so a ball skimming a rail or the rim's arch may go in or out differently.</p>
 */
public final class BioBuzzHive {
    /** Where TU06 and the CAD say the HIVE is. */
    static final String SOURCE = "BIOBUZZ Competition Manual TU06 9.6 and FIRST's field CAD";

    /** Where UnityBot measured the frame and CELLs in the field CAD. */
    static final String CAD = "CAD, measured by UnityBot 2026-11-08";

    /** The CELL's back wall, along the arm from the pivot (CAD). */
    public static final double BACK_WALL_INCHES = 9.44;
    /** The CELL's opening, its outer face (CAD: 12.02 in past the back wall; TU06 9.6.2: about 12 in deep). */
    public static final double OPENING_INCHES = 21.46;
    /** The CELL's floor, square to the arm (CAD: 1.47 in below the pivot line). */
    public static final double FLOOR_INCHES = -1.47;
    /**
     * The underside of the CELL's floor, square to the arm (CAD, measured by UnityBot 2026-11-08: the frame under
     * the CELL's inner floor). From here up to {@link #FLOOR_INCHES} the floor is solid, so a ball hitting the
     * CELL from below bounces off a 1.5 in thick floor (D31).
     */
    public static final double FLOOR_UNDERSIDE_INCHES = -2.97;
    /** The CELL's top: 14 in above its floor (TU06 9.6.2). */
    public static final double TOP_INCHES = FLOOR_INCHES + 14;
    /** Half the CELL's inside width, either side of the pivot's y (CAD: its sides are 20.16 in apart). */
    public static final double CELL_HALF_WIDTH_INCHES = 10.08;
    /**
     * Each half of the arm (CAD): from 3.0 in along it, past the axle, to the CELL's back wall. The CAD's arm
     * runs on under the CELL to 21.56 in, but there the CELL's floor already stops a ball.
     */
    static final double ARM_CENTER_INCHES = (3.0 + BACK_WALL_INCHES) / 2;
    static final double ARM_HALF_LENGTH_INCHES = (BACK_WALL_INCHES - 3.0) / 2;
    /** The arm's center square to it, and its half thickness (CAD: -2.71 to -0.35). */
    static final double ARM_CENTER_W_INCHES = (-2.71 - 0.35) / 2;
    static final double ARM_HALF_THICKNESS_INCHES = (2.71 - 0.35) / 2;
    /** The arm's half width along y (CAD: 12.15 to 13.33 from the field's center line). */
    static final double ARM_HALF_WIDTH_INCHES = (13.33 - 12.15) / 2;

    /** The weight in the upward CELL that tips a HIVE (manager decisions D26, D29). */
    public static final ModelValue TIP_MASS = ModelValue.placeholder(200, PieceModel.GRAMS,
            "not in the manual, which says only \"enough POLLEN or NECTAR\"; picked so the 3 staged NECTAR"
                    + " (123.9 g) hold and 4 POLLEN on them (223.5 g) tip it (D26)");
    /** How long a tip takes, easing in and out as VI Arena draws it. */
    public static final ModelValue TIP_SECONDS = ModelValue.placeholder(1, "s", "not in the manual; a guess (D26)");
    /** How fast a ball leaves a CELL that has tipped down. */
    public static final ModelValue SPILL_SPEED = ModelValue.placeholder(20, ModelValue.INCHES_PER_SECOND,
            "not in the manual; a guess for a ball rolling out of the opening");

    /**
     * One HIVE.
     *
     * @param id                the field file's id, e.g. "red-hive"
     * @param pivotXInches      the pivot, FTC field frame
     * @param pivotYInches      the pivot
     * @param pivotZInches      the pivot's height (TU06 9.6.1: 43.95 in)
     * @param startTiltDegrees  its tilt at the start of a match: +30 lifts its +x CELL
     */
    public record Hive(String id, double pivotXInches, double pivotYInches, double pivotZInches, double startTiltDegrees) {
    }

    /**
     * A straight bar or plate of the frame: a box along the field's x and y, turned about y by
     * {@code tiltDegrees} (positive raising its +x end).
     *
     * @param id      the field file's id
     * @param xInches its center, FTC field frame
     * @param yInches its center
     * @param zInches its center
     * @param length  along its own x (along the tilted bar)
     * @param width   along y
     * @param height  square to the other two
     * @param tiltDegrees its tilt about y
     */
    public record Box(String id, double xInches, double yInches, double zInches, double length, double width,
                      double height, double tiltDegrees) {
    }

    /**
     * A round bar between two points, such as one of the frame's legs, which lean in both x and y.
     *
     * @param id           a fixed name
     * @param radiusInches the bar's radius
     */
    public record Rod(String id, double bottomX, double bottomY, double bottomZ, double topX, double topY, double topZ,
                      double radiusInches) {
    }

    /** Only static methods here; nothing to create. */
    private BioBuzzHive() {
    }

    /** The two HIVEs, red then blue: red's audience CELL starts up, blue's far CELL does (TU06 Figure 10-2). */
    public static List<Hive> hives() {
        return List.of(
                new Hive("red-hive", 0, -12.75, 43.95, 30),
                new Hive("blue-hive", 0, 12.75, 43.95, -30));
    }

    /**
     * The frame's straight parts a flying ball can hit (CAD): its two bases, its top bar, the two blocks the
     * HIVEs' axles sit in, and the two logo panels under the top bar.
     */
    public static List<Box> frame() {
        return List.of(
                new Box("frame-base-red", 0, -23.74, 1.075, 38.94, 1.98, 2.15, 0),
                new Box("frame-base-blue", 0, 23.74, 1.075, 38.94, 1.98, 2.15, 0),
                new Box("frame-top-bar", 0, 0.01, 41.45, 1, 24, 1, 0),
                new Box("frame-axle-red", 0, -12.125, 41.855, 3.06, 3.27, 4.73, 0),
                new Box("frame-axle-blue", 0, 12.125, 41.855, 3.06, 3.27, 4.73, 0),
                new Box("frame-panel-audience", 2.92, 0, 37.045, 2.74, 30.2, 5.95, 0),
                new Box("frame-panel-far", -2.92, 0, 37.045, 2.74, 30.2, 5.95, 0));
    }

    /**
     * The frame's four legs (CAD center lines, about 0.75 in thick): each leans in toward the top bar in x
     * and also in y, about 11 in from bottom to top. The field file's
     * {@code flightObstacles} has the same ones.
     */
    public static List<Rod> legs() {
        return List.of(
                new Rod("frame-leg-red-audience", 18.28, -23.65, 1.19, 0.97, -12.91, 40.00, 0.75),
                new Rod("frame-leg-red-far", -18.33, -23.62, 1.05, -0.93, -12.85, 40.08, 0.75),
                new Rod("frame-leg-blue-audience", 18.28, 23.71, 1.16, 1.06, 12.96, 39.96, 0.75),
                new Rod("frame-leg-blue-far", -18.27, 23.74, 1.23, -0.97, 12.86, 39.97, 0.75));
    }
}
