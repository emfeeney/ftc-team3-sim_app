package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flying balls meet the FLOWERs and other balls (W-PG item 1), as they already do in Unity's physics. Before,
 * they passed through both. The robot box is placed by hand and the launcher's effect set directly, so these
 * don't depend on any team's code. The launch numbers are made up.
 */
class FlightCollisionTest {
    private static final double SIZE = 18;
    /** Small steps, to see where a ball turns around. */
    private static final double FINE = 0.002;
    private static final double FAR_FLOWER_X = -68.04;
    private static final double FAR_FLOWER_Y = -23.39;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /** A robot at {@code robot} holding one ball, which it throws at {@code speed}, from {@code up} in up, {@code pitch} degrees up. */
    private static GamePieces throwing(List<GamePiece> others, Pose robot, double up, double pitch, double speed) {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1),
                guess(0.3), guess(6), guess(0), guess(up), guess(pitch), guess(0));
        List<GamePiece> start = new ArrayList<>(others);
        start.add(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, 0, 1.4));
        GamePieces pieces = new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), BioBuzzPieces.flowers());
        pieces.useMechanism(new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                launcher), robot);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(speed);
        return pieces;
    }

    /** Runs until the thrown ball has flown {@code seconds}; returns its smallest x on the way. */
    private static double smallestX(GamePieces pieces, Pose robot, double seconds) {
        double smallest = Double.POSITIVE_INFINITY;
        for (double t = 0; t < seconds; t += FINE) {
            pieces.step(robot, robot, SIZE, SIZE, FINE, true);
            GamePiece ball = piece(pieces, "pollen-preload-1");
            if (!ball.state().equals(GamePiece.HELD)) {
                smallest = Math.min(smallest, ball.xInches());
            }
        }
        return smallest;
    }

    /**
     * WHAT: A ball thrown toward the far wall at FLOWER height bounces off the FLOWER's head (radius 3.13 in,
     * 3.75 to 22.65 in up, the CAD) instead of flying through it to the wall.
     */
    @Test
    void aFlyingBallBouncesOffAFlowersHead() {
        Pose robot = Pose.inches(-40, FAR_FLOWER_Y, 180);
        GamePieces pieces = throwing(List.of(), robot, 14, 10, 200);

        double smallest = smallestX(pieces, robot, 0.6);

        double headFace = FAR_FLOWER_X + 3.13 + 1.4;
        assertEquals(headFace, smallest, 0.05, "it turned at the head's face, not at the wall (-70.5 in)");
    }

    /** WHAT: A ball thrown higher passes over the FLOWER's top (22.65 in) and reaches the wall behind it. */
    @Test
    void aBallAboveTheFlowerFliesOverIt() {
        Pose robot = Pose.inches(-40, FAR_FLOWER_Y, 180);
        GamePieces pieces = throwing(List.of(), robot, 14, 45, 200);

        double smallest = smallestX(pieces, robot, 0.6);

        assertTrue(smallest < -68.5, "it reached the far wall (-70.5 in) over the FLOWER: " + smallest);
    }

    /**
     * WHAT: A ball thrown low over a loose ball on the tiles clips its top and knocks it along; without the
     * collision it would fly over it and land beyond, leaving it still.
     */
    @Test
    void aFlyingBallKnocksALooseBall() {
        Pose robot = Pose.inches(0, -40, 0);
        GamePieces pieces = throwing(List.of(new GamePiece("target", GamePiece.POLLEN, GamePiece.FIELD, 20, -40, 1.4)),
                robot, 5, 0, 150);
        double fastest = 0;
        for (double t = 0; t < 0.6; t += FINE) {
            pieces.step(robot, robot, SIZE, SIZE, FINE, true);
            fastest = Math.max(fastest, pieces.speedInchesPerSecond("target"));
        }

        assertTrue(fastest > 10, "the loose ball was knocked: " + fastest + " in/s");
        GamePiece target = piece(pieces, "target");
        assertEquals(GamePiece.FIELD, target.state());
        assertEquals(1.4, target.zInches(), 1e-9, "and stayed on the tiles");
    }

    /**
     * WHAT: A ball thrown low at the bottom POLLEN in the far FLOWER (under the FLOWER's head) bounces off it,
     * and the FLOWER's ball stays where it is.
     */
    @Test
    void aFlyingBallBouncesOffABallInAFlower() {
        Pose robot = Pose.inches(-50, FAR_FLOWER_Y, 180);
        GamePiece inFlower = new GamePiece("stacked", GamePiece.POLLEN, GamePiece.FLOWER, FAR_FLOWER_X, FAR_FLOWER_Y, 1.4);
        GamePieces pieces = throwing(List.of(inFlower), robot, 3, 0, 150);

        double smallest = smallestX(pieces, robot, 0.5);

        assertEquals(FAR_FLOWER_X + 2.8, smallest, 0.2, "it turned against the FLOWER's ball, not the brackets (-67.8 in)");
        GamePiece stacked = piece(pieces, "stacked");
        assertEquals(GamePiece.FLOWER, stacked.state());
        assertEquals(FAR_FLOWER_X, stacked.xInches(), 1e-9, "the FLOWER's ball didn't move");
    }

    /** WHAT: Two flying balls that overlap are pushed apart: they don't pass through each other. */
    @Test
    void twoFlyingBallsDontPassThroughEachOther() {
        GamePieces pieces = new GamePieces(List.of(
                new GamePiece("lower", GamePiece.POLLEN, GamePiece.FLYING, 0.3, 0, 27.5),
                new GamePiece("upper", GamePiece.POLLEN, GamePiece.FLYING, 0, 0, 30)), PieceModel.bioBuzz());
        pieces.step(Pose.inches(0, -50, 0), Pose.inches(0, -50, 0), SIZE, SIZE, FINE, true);

        GamePiece lower = piece(pieces, "lower");
        GamePiece upper = piece(pieces, "upper");
        double apart = Math.sqrt(Math.pow(upper.xInches() - lower.xInches(), 2) + Math.pow(upper.zInches() - lower.zInches(), 2));
        assertTrue(apart >= 2.8 - 1e-6, "no longer overlapping: " + apart);
    }
}
