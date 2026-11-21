package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flying balls and the robot (W-IO2): a flying ball bounces off the robot's box instead of passing through
 * it, even the robot that launched it, once the ball is out of the launcher; and the intake takes only
 * balls on the tiles, never one in the air. The robot box is moved by hand and the motors' effects are set
 * directly, so these don't depend on any team's code. The numbers are made up.
 */
class FlyingBallRobotTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    private static final double HALF = SIZE / 2;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /**
     * WHAT: A ball launched straight up from the middle of the robot (14 in up, inside the box) leaves the
     * box without bouncing, rises, and on the way down lands on the robot's top ({@link GamePieces#ROBOT_HEIGHT})
     * and bounces up off it, instead of falling through the robot to the tiles.
     */
    @Test
    void aBallFallingBackOntoItsOwnRobotBouncesOffItsTop() {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1),
                guess(0.3), guess(0), guess(0), guess(14), guess(90), guess(0));
        GamePieces pieces = new GamePieces(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, -40, 1.4)),
                PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                launcher), Pose.ORIGIN);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(200);

        assertEquals(18, GamePieces.ROBOT_HEIGHT.value(), "the robot box is 18 in tall");
        assertFalse(GamePieces.ROBOT_HEIGHT.measured(), "a placeholder: no robot's height is measured");
        double top = GamePieces.ROBOT_HEIGHT.value() + piece(pieces, "pollen-preload-1").radiusInches();
        double highest = 0;
        double lowestAfterTop = Double.POSITIVE_INFINITY;
        int firstTouch = -1;
        double highestAfterTouch = 0;
        for (int i = 0; i < 150; i++) {
            pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
            GamePiece ball = piece(pieces, "pollen-preload-1");
            if (i == 20) {
                pieces.feedPower(0);
                assertEquals(GamePiece.FLYING, ball.state(), "launched, and out of the box without bouncing");
            }
            highest = Math.max(highest, ball.zInches());
            if (highest > 60) {
                lowestAfterTop = Math.min(lowestAfterTop, ball.zInches());
                if (firstTouch < 0 && ball.zInches() < top + 0.5) {
                    firstTouch = i;
                }
                if (firstTouch >= 0 && i <= firstTouch + 10) {
                    highestAfterTouch = Math.max(highestAfterTouch, ball.zInches());
                }
            }
        }

        assertTrue(highest > 60, "it went up: " + highest);
        assertTrue(lowestAfterTop >= top - 1e-6, "never below the robot's top: lowest " + lowestAfterTop);
        assertTrue(firstTouch >= 0, "it came down to the robot's top");
        assertTrue(highestAfterTouch > top + 1, "it bounced back up off the top: " + highestAfterTouch);
        assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-1").state(), "still up on the robot, not on the tiles");
    }

    /**
     * WHAT: A robot driving at 100 in/s into a ball hanging in the air in front of it knocks it ahead at
     * 120 in/s (the robot's speed plus its bounce, 0.2, of the speed between them), instead of passing under it.
     */
    @Test
    void aMovingRobotKnocksAFlyingBallAhead() {
        List<GamePiece> start = new ArrayList<>(List.of(new GamePiece("ball", GamePiece.POLLEN, GamePiece.FLYING, 20, 0, 10)));
        GamePieces pieces = new GamePieces(start, PieceModel.bioBuzz());
        double speed = 100;
        Pose robot = Pose.ORIGIN;
        double before = 0;
        double ballSpeed = 0;
        for (int i = 0; i < 9; i++) {
            Pose next = Pose.inches(robot.xInches() + speed * LOOP, 0, 0);
            before = piece(pieces, "ball").xInches();
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
            ballSpeed = (piece(pieces, "ball").xInches() - before) / LOOP;
        }

        GamePiece ball = piece(pieces, "ball");
        assertTrue(ball.xInches() >= robot.xInches() + HALF + ball.radiusInches() - 1e-6, "in front of the robot: " + ball);
        double bounce = PieceModel.bioBuzz().robotBounce().value();
        assertEquals(speed * (1 + bounce), ballSpeed, 3, "knocked ahead faster than the robot, by its bounce");
    }

    /**
     * WHAT: A ball launched from inside its own robot's box (6 in forward, 6 in up) back across the robot
     * passes through the robot on its way out and leaves past the back, instead of bouncing off the box's
     * nearest face, the front.
     */
    @Test
    void aJustLaunchedBallPassesThroughItsOwnRobot() {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1),
                guess(0.3), guess(6), guess(0), guess(6), guess(20), guess(180));
        GamePieces pieces = new GamePieces(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, -40, 1.4)),
                PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                launcher), Pose.ORIGIN);
        pieces.feedPower(1.0);
        pieces.launcherBallSpeed(200);
        for (int i = 0; i < 25; i++) {
            pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
        }

        GamePiece ball = piece(pieces, "pollen-preload-1");
        assertTrue(ball.xInches() < -HALF - ball.radiusInches(), "out past the back of the robot: " + ball);
    }

    /**
     * WHAT: A ball dropping from 20 in up through the running intake's zone isn't taken while it's in the
     * air (about 15 loops); once it is down on the tiles, there, it is.
     */
    @Test
    void theIntakeTakesNoBallInTheAir() {
        MechanismSetup.Intake intake = new MechanismSetup.Intake("intake", Direction.FORWARD, guess(0.1), guess(11), guess(0),
                guess(3), guess(10), guess(0.3), guess(30));
        GamePieces pieces = new GamePieces(List.of(new GamePiece("ball", GamePiece.POLLEN, GamePiece.FLYING, 11, 0, 20)),
                PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(intake, new MechanismSetup.Hold(guess(4), List.of(GamePiece.POLLEN), guess(0)),
                null), Pose.ORIGIN);
        pieces.intakePower(1.0);

        String state = GamePiece.FLYING;
        int loops = 0;
        while (GamePiece.FLYING.equals(state) && loops < 100) {
            pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
            state = piece(pieces, "ball").state();
            loops++;
        }
        assertTrue(loops > 10, "it was in the air over the zone for " + loops + " loops, and not taken");
        assertTrue(loops < 100, "it landed");
        pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state(), "on the tiles, the intake takes it");
    }
}
