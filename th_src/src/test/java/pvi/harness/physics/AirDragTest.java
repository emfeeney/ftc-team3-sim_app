package pvi.harness.physics;

import org.junit.jupiter.api.Test;
import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;
import pvi.harness.hardware.MechanismSetup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Air drag on flying balls (W-PG item 3): the air slows a flying ball by
 * {@link PieceModel#dragPerInch} times its speed squared. The drag coefficient is a placeholder.
 *
 * <p>Each test throws one POLLEN from a test robot on an empty field, with the walls off.</p>
 */
class AirDragTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    /** The robot that throws, facing +x, near the -x wall so a long shot stays on the field. */
    private static final Pose ROBOT = Pose.inches(-60, 0, 0);

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    /** The BIOBUZZ ball facts with no air at all. */
    private static PieceModel noAir() {
        PieceModel m = PieceModel.bioBuzz();
        return new PieceModel(m.rollingDeceleration(), m.wallBounce(), m.pieceBounce(), m.robotBounce(), m.pollenMass(),
                m.nectarMass(), ModelValue.placeholder(0, PieceModel.NO_UNIT, "no air, for this test"));
    }

    /**
     * A robot at {@link #ROBOT} holding one POLLEN, which it throws along +x at {@code speed}, from {@code up} in up
     * and 6 in in front of its center, {@code pitch} degrees up. (The launcher's numbers are made up; the ball's
     * speed is set directly.)
     */
    private static GamePieces throwing(PieceModel model, double up, double pitch, double speed) {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1),
                guess(0.3), guess(6), guess(0), guess(up), guess(pitch), guess(0));
        GamePieces pieces = new GamePieces(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD,
                0, 0, 1.4)), model);
        pieces.useMechanism(new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                launcher), ROBOT);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(speed);
        return pieces;
    }

    private static GamePiece ball(GamePieces pieces) {
        return pieces.snapshot().get(0);
    }

    /** One loop, with the walls off so nothing but the air and the tiles matter. */
    private static void step(GamePieces pieces) {
        pieces.step(ROBOT, ROBOT, SIZE, SIZE, LOOP, false);
    }

    /** Steps until the ball is flying (it was just launched). */
    private static void untilFlying(GamePieces pieces) {
        for (int i = 0; i < 100 && !ball(pieces).state().equals(GamePiece.FLYING); i++) {
            step(pieces);
        }
        assertEquals(GamePiece.FLYING, ball(pieces).state(), "it was launched");
    }

    /** Steps until the ball is down on the tiles; returns where along x it landed. */
    private static double untilLanded(GamePieces pieces) {
        untilFlying(pieces);
        for (int i = 0; i < 500 && ball(pieces).state().equals(GamePiece.FLYING); i++) {
            step(pieces);
        }
        assertEquals(GamePiece.FIELD, ball(pieces).state(), "it landed");
        return ball(pieces).xInches();
    }

    /**
     * WHAT: A POLLEN flying flat at 300 in/s loses speed as the air predicts: dv/dt = -k v², so 0.1 s later it's
     * at v / (1 + k v 0.1), about 10 in/s slower.
     */
    @Test
    void aFlyingBallSlowsDownInTheAir() {
        GamePieces pieces = throwing(PieceModel.bioBuzz(), 50, 0, 300);
        untilFlying(pieces);
        step(pieces);
        double before = pieces.speedInchesPerSecond("pollen-preload-1");
        for (int i = 0; i < 5; i++) {
            step(pieces);
        }
        double after = pieces.speedInchesPerSecond("pollen-preload-1");

        double k = PieceModel.bioBuzz().dragPerInch(GamePiece.POLLEN);
        double expected = before / (1 + k * before * 0.1);
        assertTrue(before - expected > 8, "the air takes about 10 in/s: from " + before + " to " + expected);
        assertEquals(expected, after, 0.5, "from " + before);
    }

    /**
     * WHAT: Without air a 45 degree shot at 200 in/s lands where the textbook says; with air it lands several inches
     * shorter.
     */
    @Test
    void aShotLandsShorterWithAir() {
        double withoutAir = untilLanded(throwing(noAir(), 10, 45, 200));
        double withAir = untilLanded(throwing(PieceModel.bioBuzz(), 10, 45, 200));

        // From 6 in in front of the robot and 10 in up, it lands a radius up, when v t sin 45 - g t^2 / 2 = -8.6.
        double v = 200 / Math.sqrt(2);
        double g = PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED;
        double drop = 10 - GamePiece.radiusInches(GamePiece.POLLEN);
        double time = (v + Math.sqrt(v * v + 2 * g * drop)) / g;
        assertEquals(ROBOT.xInches() + 6 + v * time, withoutAir, 4, "no air: as the textbook says, within the steps' rounding");
        assertTrue(withAir < withoutAir - 5, "the air shortens it: " + withAir + " in, against " + withoutAir + " in");
    }

    /** WHAT: The air slows a ball going up too: a shot straight up at 300 in/s peaks well below its no-air height. */
    @Test
    void aShotStraightUpPeaksLower() {
        double withoutAir = peak(throwing(noAir(), 14, 90, 300));
        double withAir = peak(throwing(PieceModel.bioBuzz(), 14, 90, 300));

        double g = PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED;
        assertEquals(14 + 300 * 300 / (2 * g), withoutAir, 1.5, "no air: v squared over 2 g above the launch point");
        assertTrue(withAir < withoutAir - 5, "the air holds it down: " + withAir + " in, against " + withoutAir + " in");
    }

    /** Steps until the ball has been flying and comes back below its launch height; returns the highest it got. */
    private static double peak(GamePieces pieces) {
        untilFlying(pieces);
        double highest = 0;
        for (int i = 0; i < 100; i++) {
            step(pieces);
            highest = Math.max(highest, ball(pieces).zInches());
        }
        return highest;
    }

    /**
     * WHAT: The drag per inch is half the air's density (1.204 kg/m³) times the 0.47 coefficient times the ball's
     * cross-section over its mass: for a POLLEN (2.8 in, 24.9 g), about 0.00115 per inch.
     */
    @Test
    void theDragComesFromTheAirTheBallsSizeAndItsMass() {
        double airGramsPerCubicInch = 1.204 * 1000 / 61023.74;
        double area = Math.PI * 1.4 * 1.4;
        assertEquals(0.5 * airGramsPerCubicInch * 0.47 * area / 24.9, PieceModel.bioBuzz().dragPerInch(GamePiece.POLLEN), 1e-9);
        double nectarArea = Math.PI * 1.8 * 1.8;
        assertEquals(0.5 * airGramsPerCubicInch * 0.47 * nectarArea / 41.3,
                PieceModel.bioBuzz().dragPerInch(GamePiece.RED_NECTAR), 1e-9);
    }

    /** WHAT: Air drag is for flying balls only: once the ball has landed it slows by its rolling slowdown alone. */
    @Test
    void aRollingBallFeelsNoAirDrag() {
        GamePieces pieces = throwing(PieceModel.bioBuzz(), 10, 30, 150);
        untilLanded(pieces);
        step(pieces);
        double before = pieces.speedInchesPerSecond("pollen-preload-1");
        for (int i = 0; i < 25; i++) {
            step(pieces);
        }

        double expected = before - PieceModel.bioBuzz().rollingDeceleration().value() * 0.5;
        assertTrue(before > 50, "still rolling fast: " + before);
        assertEquals(expected, pieces.speedInchesPerSecond("pollen-preload-1"), 1e-6);
    }

    /** WHAT: The drag coefficient is a guess (0.47, a smooth sphere's), marked so, and listed with the values still to measure. */
    @Test
    void theDragCoefficientIsAGuess() {
        PieceModel model = PieceModel.bioBuzz();
        assertEquals(0.47, model.dragCoefficient().value(), 1e-9);
        assertFalse(model.dragCoefficient().measured());
        assertTrue(model.unmeasured().contains("ball drag coefficient"), model.unmeasured().toString());
    }
}
