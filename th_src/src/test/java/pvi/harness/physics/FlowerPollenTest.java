package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The POLLEN stacked in a FLOWER (TU06 9.7): it rests on the tiles, a robot's intake takes the bottom ball out
 * through the Retrieval Opening, and the rest drop by one. A robot with no intake gets nothing out (G418, manager
 * decision D38).
 *
 * <p>The robot box is moved by hand here, so these don't depend on any team's drive code. Its intake is made up:
 * a zone 3 in long and 10 in wide, 10 in in front of its center, so it reaches 2.5 in past the robot's front.</p>
 */
class FlowerPollenTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    private static final double BALL = 2.8;
    /** The far FLOWER, on the wall at x = -70.5; straight off the wall is +x. */
    private static final FlowerSpot FAR = new FlowerSpot("far", -68.04, -23.39, 1, 0);
    /**
     * Robot centers, facing the wall: away from the FLOWER, and with its front 3.5 in from the FLOWER's center, just
     * clear of its head (3.13 in), so the intake reaches the bottom ball.
     */
    private static final double AWAY_X = -40;
    private static final double CLOSE_X = FAR.xInches() + SIZE / 2 + 3.5;
    private static final double ROBOT_Y = FAR.yInches() + 2;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    /** A made-up intake with its zone's middle {@code forward} in in front of the robot's center. */
    private static MechanismSetup.Intake intake(double forward) {
        return new MechanismSetup.Intake("intake", Direction.FORWARD, guess(0.1), guess(forward), guess(0), guess(3),
                guess(10), guess(0.3), guess(30));
    }

    /** Gives the robot that intake, holding up to {@code capacity} POLLEN or NECTAR, and runs it inward. */
    private static void giveIntake(GamePieces pieces, double forward, int capacity) {
        pieces.useMechanism(new MechanismSetup(intake(forward),
                new MechanismSetup.Hold(guess(capacity), List.of(GamePiece.POLLEN, GamePiece.RED_NECTAR), guess(0)), null),
                Pose.inches(AWAY_X, ROBOT_Y, 180));
        pieces.intakePower(1.0);
    }

    /** The 4 POLLEN of a far FLOWER, as at the start of a match; {@code stack-1} is the top. */
    private static List<GamePiece> fullStack() {
        List<GamePiece> start = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            start.add(new GamePiece("stack-" + (i + 1), GamePiece.POLLEN, GamePiece.FLOWER, FAR.xInches(), FAR.yInches(),
                    1.4 + (3 - i) * BALL));
        }
        return start;
    }

    /** A full far FLOWER, and a robot whose intake (reaching 2.5 in past its front) holds {@code capacity} balls. */
    private static GamePieces fullFlowerWithIntake(int capacity) {
        GamePieces pieces = flower(fullStack());
        giveIntake(pieces, 10, capacity);
        return pieces;
    }

    private static GamePieces flower(List<GamePiece> start) {
        return new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), List.of(FAR));
    }

    /** Moves the robot box, facing the wall, from {@code x} to {@code toX} at 30 in/s, then waits {@code wait} seconds. */
    private static void drive(GamePieces pieces, double x, double toX, double wait) {
        driveFacing(pieces, x, toX, 180, wait);
    }

    private static void driveFacing(GamePieces pieces, double x, double toX, double headingDegrees, double wait) {
        double step = Math.signum(toX - x) * 30 * LOOP;
        Pose robot = Pose.inches(x, ROBOT_Y, headingDegrees);
        while (Math.abs(toX - robot.xInches()) > Math.abs(step) / 2) {
            Pose next = Pose.inches(robot.xInches() + step, ROBOT_Y, headingDegrees);
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
        }
        for (double t = 0; t < wait; t += LOOP) {
            pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
        }
    }

    /** Drives up to the FLOWER and stays a moment. */
    private static void arrive(GamePieces pieces) {
        drive(pieces, AWAY_X, CLOSE_X, 1.0);
    }

    /** Backs away from the FLOWER and stays a moment. */
    private static void leave(GamePieces pieces) {
        drive(pieces, CLOSE_X, AWAY_X, 2.0);
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static long inFlower(GamePieces pieces) {
        return pieces.snapshot().stream().filter(p -> p.state().equals(GamePiece.FLOWER)).count();
    }

    /** WHAT: A match starts with 4 FLOWERs of 4 POLLEN each, resting on the tiles and touching (TU06 9.7, 10.3.1). */
    @Test
    void theStackRestsOnTheTiles() {
        List<GamePiece> start = BioBuzzPieces.start(Pose.ORIGIN, SIZE);
        assertEquals(4, BioBuzzPieces.flowers().size());
        for (FlowerSpot spot : BioBuzzPieces.flowers()) {
            List<Double> heights = start.stream().filter(p -> p.state().equals(GamePiece.FLOWER))
                    .filter(p -> p.id().contains("-" + spot.name() + "-"))
                    .map(GamePiece::zInches).sorted().toList();
            assertEquals(List.of(1.4, 4.2, 7.0, 9.8), heights, spot.name());
            assertEquals(1.0, Math.hypot(spot.openXInches(), spot.openYInches()), 1e-9, spot.name());
            assertTrue(Math.abs(spot.xInches() + spot.openXInches()) < Math.abs(spot.xInches())
                    || Math.abs(spot.yInches() + spot.openYInches()) < Math.abs(spot.yInches()),
                    spot.name() + " opens away from its wall, into the field");
        }
    }

    /** WHAT: The Retrieval Opening is the manual's: about 3.55 in tall (TU06 9.7). */
    @Test
    void theOpeningIsTheManuals() {
        assertEquals(3.55, FlowerSpot.RETRIEVAL_OPENING_HEIGHT.value(), 1e-9);
        assertTrue(FlowerSpot.RETRIEVAL_OPENING_HEIGHT.measured());
        assertTrue(FlowerSpot.RETRIEVAL_OPENING_HEIGHT.source().contains("TU06 9.7"));
    }

    /**
     * WHAT: An intake reaching into the opening takes only the bottom ball, which is held; the other 3 drop by one
     * ball, still in the FLOWER, resting on the tiles and touching.
     */
    @Test
    void anIntakeTakesOnlyTheBottomBallAndTheRestDrop() {
        GamePieces pieces = fullFlowerWithIntake(1);
        arrive(pieces);

        assertEquals(GamePiece.HELD, piece(pieces, "stack-4").state());
        for (int i = 1; i <= 3; i++) {
            GamePiece p = piece(pieces, "stack-" + i);
            assertEquals(GamePiece.FLOWER, p.state());
            assertEquals(1.4 + (3 - i) * BALL, p.zInches(), 1e-9, p.id());
            assertEquals(FAR.xInches(), p.xInches(), 1e-9, p.id());
        }
    }

    /** WHAT: A running intake that stays takes the balls one after another as each drops, until it's full. */
    @Test
    void aRunningIntakeTakesBallsUntilItsFull() {
        GamePieces pieces = fullFlowerWithIntake(3);
        arrive(pieces);
        drive(pieces, CLOSE_X, CLOSE_X, 2.0);

        assertEquals(1, inFlower(pieces), "it holds 3, so one stays");
        assertEquals(1.4, piece(pieces, "stack-1").zInches(), 1e-9, "the top ball is now the bottom");
        assertEquals(GamePiece.HELD, piece(pieces, "stack-2").state());
    }

    /**
     * WHAT: A robot with no intake gets nothing out, however it comes: arriving, pressing up to the FLOWER's head,
     * staying, leaving and coming back (G418: driving into a FLOWER removes nothing; D38).
     */
    @Test
    void aRobotWithNoIntakeGetsNothingOut() {
        GamePieces pieces = flower(fullStack());
        double touchingTheHead = FAR.xInches() + SIZE / 2 + 3.13;
        arrive(pieces);
        drive(pieces, CLOSE_X, touchingTheHead, 3.0);
        drive(pieces, touchingTheHead, AWAY_X, 1.0);
        arrive(pieces);

        assertEquals(4, inFlower(pieces));
    }

    /** WHAT: An intake that doesn't reach past the robot's front can't reach into the opening, so it takes nothing. */
    @Test
    void anIntakeThatDoesntReachInTakesNothing() {
        GamePieces pieces = flower(fullStack());
        giveIntake(pieces, 7.5, 4);   // its zone ends at the robot's front, 9 in from its center
        arrive(pieces);

        assertEquals(4, inFlower(pieces));
    }

    /**
     * WHAT: The opening is closed at its back, on the wall side: an intake reaching the ball from behind the FLOWER
     * (a robot placed past the wall by hand, facing back toward the field) takes nothing.
     */
    @Test
    void nothingComesOutThroughTheBack() {
        GamePieces pieces = flower(fullStack());
        giveIntake(pieces, 10, 4);
        double behind = FAR.xInches() - SIZE / 2 - 3.5;
        driveFacing(pieces, behind - 20, behind, 0, 1.0);

        assertEquals(4, inFlower(pieces));
    }

    /** WHAT: A ball taken out is a normal ball: spat out, it's loose on the tiles and the robot pushes it. */
    @Test
    void aBallTakenOutIsANormalBall() {
        GamePieces pieces = fullFlowerWithIntake(1);
        arrive(pieces);
        pieces.intakePower(-1.0);
        drive(pieces, CLOSE_X, CLOSE_X, 0.5);
        pieces.intakePower(0);
        leave(pieces);
        GamePiece out = piece(pieces, "stack-4");
        assertEquals(GamePiece.FIELD, out.state(), "spat out");
        assertEquals(1.4, out.zInches(), 1e-9, "on the tiles");

        // From the +y side of the ball, along -y.
        Pose robot = Pose.inches(out.xInches(), out.yInches() + SIZE / 2 + 4, -90);
        for (int i = 0; i < 25; i++) {
            Pose next = Pose.inches(robot.xInches(), robot.yInches() - 30 * LOOP, -90);
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
        }
        assertTrue(piece(pieces, "stack-4").yInches() < out.yInches() - 5, "pushed: " + piece(pieces, "stack-4"));
    }

    /** A ball the robot pushes toward the far FLOWER; returns how far toward the wall it got, with this start. */
    private static double furthestX(List<GamePiece> start) {
        GamePieces pieces = flower(start);
        // Pushed along -x, then the robot stops well short of the FLOWER.
        Pose robot = Pose.inches(-45, FAR.yInches(), 180);
        for (int i = 0; i < 10; i++) {
            Pose next = Pose.inches(robot.xInches() - 40 * LOOP, FAR.yInches(), 180);
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
        }
        double furthest = 0;
        for (int i = 0; i < 100; i++) {
            pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
            furthest = Math.min(furthest, piece(pieces, "loose").xInches());
        }
        return furthest;
    }

    /** WHAT: A loose ball bounces off the ball seated in a FLOWER, and rolls on under an empty one. */
    @Test
    void aLooseBallBouncesOffTheSeatedBall() {
        GamePiece loose = new GamePiece("loose", GamePiece.POLLEN, GamePiece.FIELD, -60, FAR.yInches(), 1.4);
        GamePiece seated = new GamePiece("seated", GamePiece.POLLEN, GamePiece.FLOWER, FAR.xInches(), FAR.yInches(), 1.4);

        double stopped = furthestX(List.of(loose, seated));
        assertTrue(stopped >= FAR.xInches() + BALL - 1e-6 && stopped < -60, "stops at the seated ball, x = " + stopped);
        assertTrue(furthestX(List.of(loose)) < FAR.xInches() + BALL - 1, "an empty FLOWER lets it on, to the wall brackets");
    }

    /**
     * WHAT: A NECTAR at the bottom of a FLOWER stays, even for an intake that takes NECTAR: at 3.6 in it doesn't fit
     * through the 3.55 in opening (G418: only POLLEN leaves through the bottom).
     */
    @Test
    void aNectarAtTheBottomStays() {
        GamePieces pieces = flower(List.of(
                new GamePiece("nectar", GamePiece.RED_NECTAR, GamePiece.FLOWER, FAR.xInches(), FAR.yInches(), 1.8)));
        giveIntake(pieces, 10, 4);
        arrive(pieces);
        assertEquals(GamePiece.FLOWER, piece(pieces, "nectar").state());
    }

    /** WHAT: Without a FLOWER's position, a ball marked as in a FLOWER just stays put, as before. */
    @Test
    void aBallInNoKnownFlowerStaysPut() {
        GamePiece ball = new GamePiece("ball", GamePiece.POLLEN, GamePiece.FLOWER, FAR.xInches() + 5, FAR.yInches(), 20);
        GamePieces pieces = flower(List.of(ball));
        giveIntake(pieces, 10, 4);
        arrive(pieces);
        assertEquals(ball, piece(pieces, "ball"));
    }

    /** WHAT: The same visits give exactly the same balls every time (02-unity-readiness §3). */
    @Test
    void theSameVisitsGiveTheSameBalls() {
        GamePieces first = fullFlowerWithIntake(2);
        GamePieces second = fullFlowerWithIntake(2);
        for (GamePieces pieces : List.of(first, second)) {
            arrive(pieces);
            leave(pieces);
            arrive(pieces);
        }
        assertEquals(first.snapshot(), second.snapshot());
    }
}
