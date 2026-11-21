package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * How the game pieces move: how fast a rolling ball slows down, how much bounce is left after it
 * hits a wall, another ball or the robot, and how heavy each kind is.
 *
 * <p>The official BIOBUZZ manual (see {@link GamePiece#MANUAL}) gives the balls' size and material
 * but no mass, bounce or friction. The masses come from the balls' maker, AndyMark (its store
 * pages for the part numbers the manual names; manager decision D6); bounce and rolling are still
 * guesses until someone rolls real balls. Only the masses' ratio matters (it decides who moves
 * more when two balls collide).</p>
 *
 * @param rollingDeceleration how fast a rolling ball slows down on the tiles (in/s²)
 * @param wallBounce          share of a ball's speed into a wall that it bounces back with (0 to 1)
 * @param pieceBounce         the same, for two balls hitting each other
 * @param robotBounce         the same, for the robot hitting a ball
 * @param pollenMass          a POLLEN ball's mass (g)
 * @param nectarMass          a NECTAR ball's mass (g)
 * @param dragCoefficient     how much the air holds back a flying ball, its drag coefficient (no unit): the drag
 *                            force is half the air's density, times this, times the ball's cross-section, times
 *                            its speed squared
 */
public record PieceModel(ModelValue rollingDeceleration, ModelValue wallBounce, ModelValue pieceBounce,
                         ModelValue robotBounce, ModelValue pollenMass, ModelValue nectarMass,
                         ModelValue dragCoefficient) {

    /** Unit label for a bounce: a share of the speed, 0 to 1. */
    public static final String RATIO = "ratio";
    /** Unit label for masses. */
    public static final String GRAMS = "g";

    /** Below this speed (in/s) a ball stops (rolling resistance holds it). */
    public static final double STOP_INCHES_PER_SECOND = 0.05;
    /** How fast a falling ball speeds up: standard gravity, 9.80665 m/s². */
    public static final double GRAVITY_INCHES_PER_SECOND_SQUARED = 386.09;
    /**
     * How heavy air is, in grams per cubic inch: the standard 1.204 kg/m³ of dry air at 20 °C at sea level. A hall
     * high up, or hot, has thinner air; the harness doesn't model that.
     */
    public static final double AIR_DENSITY_GRAMS_PER_CUBIC_INCH = 1204.0 / 61023.74;
    /** Unit label for a number with no unit, such as a drag coefficient. */
    public static final String NO_UNIT = "no unit";

    /** Refuses missing values, bounces outside 0 to 1, and masses or friction that aren't positive. */
    public PieceModel {
        for (ModelValue v : new ModelValue[] {rollingDeceleration, wallBounce, pieceBounce, robotBounce,
                pollenMass, nectarMass, dragCoefficient}) {
            if (v == null) {
                throw new IllegalArgumentException("A piece model needs every value.");
            }
        }
        for (ModelValue bounce : new ModelValue[] {wallBounce, pieceBounce, robotBounce}) {
            if (bounce.value() < 0 || bounce.value() > 1) {
                throw new IllegalArgumentException("A bounce must be between 0 and 1, got " + bounce.value());
            }
        }
        if (rollingDeceleration.value() < 0 || pollenMass.value() <= 0 || nectarMass.value() <= 0) {
            throw new IllegalArgumentException("Rolling deceleration can't be negative and masses must be positive.");
        }
        if (dragCoefficient.value() < 0) {
            throw new IllegalArgumentException("A drag coefficient can't be negative, got " + dragCoefficient.value());
        }
    }

    /** Added to each source: the official manual was checked and has no number for this. */
    private static final String NOT_IN_MANUAL = "not given in the " + GamePiece.MANUAL + "; ";

    /** The BIOBUZZ balls, all values placeholders until someone measures them. */
    public static PieceModel bioBuzz() {
        return new PieceModel(
                ModelValue.placeholder(15.0, Dynamics.INCHES_PER_SECOND_SQUARED,
                        NOT_IN_MANUAL + "not measured; roll a ball across the tiles and time how far it goes"),
                ModelValue.placeholder(0.4, RATIO,
                        NOT_IN_MANUAL + "not measured; a guess for a polyethylene ball against the wall"),
                ModelValue.placeholder(0.6, RATIO, NOT_IN_MANUAL + "not measured; a guess for two balls"),
                ModelValue.placeholder(0.2, RATIO, NOT_IN_MANUAL + "not measured; a guess for the robot's bumper"),
                ModelValue.measured(24.9, GRAMS, "AndyMark store page, am-5851 (POLLEN): 0.055 lb"),
                ModelValue.measured(41.3, GRAMS, "AndyMark store page, am-5852 (NECTAR): 0.091 lb"),
                ModelValue.placeholder(0.47, NO_UNIT, NOT_IN_MANUAL + "not measured; the textbook value for a smooth"
                        + " sphere at a shot's speed (Reynolds number about 30,000). Film a shot and fit its path"));
    }

    /**
     * How fast the air slows a flying ball of this kind, per (in/s)² of its speed, in 1/in: half the air's density,
     * times {@link #dragCoefficient}, times the ball's cross-section, over its mass. The slowing down is this times
     * the speed squared.
     */
    public double dragPerInch(String kind) {
        double radius = GamePiece.radiusInches(kind);
        return 0.5 * AIR_DENSITY_GRAMS_PER_CUBIC_INCH * dragCoefficient.value() * Math.PI * radius * radius / massOf(kind);
    }

    /** A ball's mass for its kind, in grams. */
    public double massOf(String kind) {
        return GamePiece.POLLEN.equals(kind) ? pollenMass.value() : nectarMass.value();
    }

    /** The names of every value still to measure, for reports. */
    public List<String> unmeasured() {
        List<String> names = new ArrayList<>();
        String[] labels = {"ball rolling deceleration", "ball wall bounce", "ball-to-ball bounce", "robot-to-ball bounce",
                "pollen mass", "nectar mass", "ball drag coefficient"};
        ModelValue[] values = {rollingDeceleration, wallBounce, pieceBounce, robotBounce, pollenMass, nectarMass,
                dragCoefficient};
        for (int i = 0; i < values.length; i++) {
            if (!values[i].measured()) {
                names.add(labels[i]);
            }
        }
        return names;
    }
}
