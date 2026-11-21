package pvi.harness.physics;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After STOP, the final score waits for the pieces to come to rest (TU06 10.5, manager decision D50):
 * {@link GamePieces#settledCopy} moves a copy on with the robot standing still, up to a time limit, and leaves the
 * run's own pieces alone.
 */
class SettleTest {
    private static final Pose ROBOT = Pose.inches(0, -50, 0);

    private static GamePieces oneBall(double zInches) {
        return new GamePieces(List.of(new GamePiece("ball", GamePiece.POLLEN,
                zInches > 1.4 ? GamePiece.FLYING : GamePiece.FIELD, 30, 30, zInches)), PieceModel.bioBuzz());
    }

    /** WHAT: A ball in the air falls and comes to rest in the copy; the original is still in the air. */
    @Test
    void aFallingBallSettlesInTheCopyOnly() {
        GamePieces pieces = oneBall(30);
        assertFalse(pieces.atRest());

        GamePieces settled = pieces.settledCopy(ROBOT, 18, 18, true, 0.02, 5);

        assertTrue(settled.atRest());
        GamePiece ball = settled.snapshot().get(0);
        assertEquals(GamePiece.FIELD, ball.state());
        assertTrue(settled.settleSeconds() > 0.3 && settled.settleSeconds() < 5, "took " + settled.settleSeconds() + " s");
        assertEquals(GamePiece.FLYING, pieces.snapshot().get(0).state(), "the run's own pieces don't change");
        assertEquals(30, pieces.snapshot().get(0).zInches(), 1e-9);
        assertEquals(0, pieces.settleSeconds());
    }

    /** WHAT: Pieces already at rest take no time. */
    @Test
    void piecesAtRestTakeNoTime() {
        GamePieces settled = oneBall(1.4).settledCopy(ROBOT, 18, 18, true, 0.02, 5);

        assertTrue(settled.atRest());
        assertEquals(0, settled.settleSeconds());
    }

    /** WHAT: Settling stops at the limit, even if something still moves. */
    @Test
    void settlingStopsAtTheLimit() {
        GamePieces settled = oneBall(20000).settledCopy(ROBOT, 18, 18, true, 0.02, 0.5);

        assertFalse(settled.atRest());
        assertEquals(0.5, settled.settleSeconds(), 1e-9);
    }

    /** WHAT: The staged field, untouched, is at rest at once (so an ordinary run doesn't wait for it). */
    @Test
    void theStagedFieldIsAtRest() {
        GamePieces pieces = new GamePieces(BioBuzzPieces.start(ROBOT, 18), PieceModel.bioBuzz(), BioBuzzObstacles.all(),
                BioBuzzPieces.flowers(), BioBuzzHive.hives(), BioBuzzHive.frame());

        GamePieces settled = pieces.settledCopy(ROBOT, 18, 18, true, 0.02, 5);

        assertTrue(settled.atRest());
        assertTrue(settled.settleSeconds() < 0.1, "took " + settled.settleSeconds() + " s");
    }
}
