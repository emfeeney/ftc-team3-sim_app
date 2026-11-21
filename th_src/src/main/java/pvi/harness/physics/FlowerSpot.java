package pvi.harness.physics;

/**
 * Where one FLOWER stands, as plain data, for the POLLEN stacked in it.
 *
 * <p>TU06 section 9.7: POLLEN goes in at the top and leaves at the bottom, through the Retrieval
 * Opening. Nothing holds a ball up inside the tube (field CAD), so the stack rests on the tiles and
 * drops by one ball when the bottom one leaves. The opening faces every way but the wall: the FLOWER's
 * middle and lower rings are joined only on the wall side.</p>
 *
 * <p><b>Getting a ball out:</b> only a robot's intake reaching in through the Retrieval Opening takes the
 * bottom ball, and only if the ball fits through it ({@link #RETRIEVAL_OPENING_HEIGHT}): POLLEN does, NECTAR
 * doesn't (G418). Driving into a FLOWER removes nothing, so a robot with no intake never gets a ball out
 * (manager decision D38, which replaced D24's stand-in).</p>
 *
 * @param name       a fixed name, e.g. "far"
 * @param xInches    the FLOWER's center, FTC field frame
 * @param yInches    the FLOWER's center, FTC field frame
 * @param openXInches the direction straight off the wall, a unit vector: x part
 * @param openYInches the direction straight off the wall, a unit vector: y part
 */
public record FlowerSpot(String name, double xInches, double yInches, double openXInches, double openYInches) {
    /** How far apart in xy a ball can be from this center and still be in this FLOWER's stack. */
    static final double SAME_SPOT_INCHES = 0.01;

    /**
     * How tall the Retrieval Opening is: a ball this wide or wider can't come out through it. (It is also about 3.57 in
     * deep, TU06 9.7, from its back on the wall side to in front of the bottom ball, so an intake that touches the ball
     * from the field side is always inside it; the harness needs no number for that.)
     */
    public static final ModelValue RETRIEVAL_OPENING_HEIGHT = ModelValue.measured(3.55, ModelValue.INCHES,
            "BIOBUZZ Competition Manual TU06 9.7: the Retrieval Opening is approximately 3.55 in tall");
}
