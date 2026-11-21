package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A robot's intake takes loose balls and a FLOWER's bottom POLLEN, holds them, and spits them back out.
 * The robot box is moved by hand and the intake's power is set directly, so these don't depend on any
 * team's drive code. The numbers are made up: this checks the rules, not a real robot.
 */
class IntakeHoldTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    private static final double MIN_POWER = 0.2;
    private static final double SPIT_SECONDS = 0.3;
    /** The zone is 3 in long, starting 8.5 in in front of the center, and 10 in wide. */
    private static final double ZONE_FORWARD = 10;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static MechanismSetup.Intake intake() {
        return new MechanismSetup.Intake("intake", Direction.FORWARD, guess(MIN_POWER), guess(ZONE_FORWARD), guess(0),
                guess(3), guess(10), guess(SPIT_SECONDS), guess(30));
    }

    private static MechanismSetup.Hold hold(int capacity, int preloaded) {
        return new MechanismSetup.Hold(guess(capacity), List.of(GamePiece.POLLEN), guess(preloaded));
    }

    private static MechanismSetup.Launcher launcher() {
        return new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000), guess(0.3), guess(0.1),
                guess(4), guess(0.5), guess(20), "intake", Direction.FORWARD, guess(0.1), guess(0.3), guess(-4), guess(1),
                guess(14), guess(60), guess(0));
    }

    private static GamePiece pollen(String id, double x, double y) {
        return new GamePiece(id, GamePiece.POLLEN, GamePiece.FIELD, x, y, 1.4);
    }

    private static GamePieces withIntake(int capacity, GamePiece... balls) {
        GamePieces pieces = new GamePieces(List.of(balls), PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(intake(), hold(capacity, 0), launcher()), Pose.ORIGIN);
        return pieces;
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /** Drives the box along +x at 30 in/s for this long, from x = fromX; returns where it ends. */
    private static Pose driveAlongX(GamePieces pieces, double fromX, double seconds) {
        Pose robot = Pose.inches(fromX, 0, 0);
        for (double t = 0; t < seconds - 1e-9; t += LOOP) {
            Pose next = Pose.inches(robot.xInches() + 30 * LOOP, 0, 0);
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
        }
        return robot;
    }

    private static void stay(GamePieces pieces, Pose robot, double seconds) {
        for (double t = 0; t < seconds - 1e-9; t += LOOP) {
            pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
        }
    }

    /** WHAT: A ball touching the zone with the motor running inward becomes held. */
    @Test
    void aBallInTheZoneIsTaken() {
        GamePieces pieces = withIntake(4, pollen("ball", 16, 0));
        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 0.4);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state());
    }

    /** WHAT: A ball reaches the zone only by touching it: one in front of it or beside it stays loose. */
    @Test
    void aBallOutsideTheZoneIsntTaken() {
        GamePieces pieces = withIntake(4, pollen("ahead", 13, 0), pollen("beside", 10, 6.5));
        pieces.intakePower(1.0);
        stay(pieces, Pose.ORIGIN, 0.2);

        assertEquals(GamePiece.FIELD, piece(pieces, "ahead").state());
        assertEquals(GamePiece.FIELD, piece(pieces, "beside").state());
    }

    /** WHAT: With the motor off, running in reverse, or below its least power, the robot just pushes the ball. */
    @Test
    void theMotorMustRunInward() {
        for (double power : new double[] {0, -1.0, MIN_POWER / 2}) {
            GamePieces pieces = withIntake(4, pollen("ball", 16, 0));
            pieces.intakePower(power);
            driveAlongX(pieces, 0, 0.4);
            assertEquals(GamePiece.FIELD, piece(pieces, "ball").state(), "power " + power);
            assertTrue(piece(pieces, "ball").xInches() > 16, "pushed at power " + power);
        }
    }

    /** WHAT: The least power is enough. */
    @Test
    void theLeastPowerIsEnough() {
        GamePieces pieces = withIntake(4, pollen("ball", 16, 0));
        pieces.intakePower(MIN_POWER);
        driveAlongX(pieces, 0, 0.4);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state());
    }

    /** WHAT: When it's full, the next ball isn't taken: the robot pushes it. */
    @Test
    void aFullRobotPushesTheNextBall() {
        GamePieces pieces = withIntake(1, pollen("first", 16, 0), pollen("second", 26, 0));
        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 0.9);

        assertEquals(GamePiece.HELD, piece(pieces, "first").state());
        assertEquals(GamePiece.FIELD, piece(pieces, "second").state());
        assertTrue(piece(pieces, "second").xInches() > 26, "pushed");
    }

    /** WHAT: A kind it doesn't take isn't taken: the robot pushes it. */
    @Test
    void aKindItDoesntTakeIsPushed() {
        GamePieces pieces = withIntake(4, new GamePiece("nectar", GamePiece.RED_NECTAR, GamePiece.FIELD, 16, 0, 1.8));
        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 0.4);

        assertEquals(GamePiece.FIELD, piece(pieces, "nectar").state());
        assertTrue(piece(pieces, "nectar").xInches() > 16, "pushed");
    }

    /**
     * WHAT: A held ball rides with the robot, through a drive and a turn, at its place on the line from the
     * launch point to the intake: with room for 2, the first is a quarter of the way along.
     */
    @Test
    void aHeldBallRidesWithTheRobot() {
        GamePieces pieces = withIntake(2, pollen("ball", 12, 0));
        pieces.intakePower(1.0);
        stay(pieces, Pose.ORIGIN, 0.02);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state());

        // A quarter of the way from (-4, 1, 14) to (10, 0, radius), in the robot's frame.
        double forward = -4 + 14 * 0.25;
        double left = 1 - 0.25;
        double up = 14 + (1.4 - 14) * 0.25;
        Pose robot = Pose.inches(20, -5, 90);
        pieces.step(Pose.ORIGIN, robot, SIZE, SIZE, LOOP, true);

        GamePiece ball = piece(pieces, "ball");
        assertEquals(20 - left, ball.xInches(), 1e-9, "at 90 degrees, its left is -x");
        assertEquals(-5 + forward, ball.yInches(), 1e-9, "at 90 degrees, its forward is +y");
        assertEquals(up, ball.zInches(), 1e-9);
    }

    /** WHAT: Balls wait in the order they came: the first taken is nearest the launcher. */
    @Test
    void theOldestBallIsNearestTheLauncher() {
        GamePieces pieces = withIntake(4, pollen("first", 16, 0), pollen("second", 26, 0));
        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 0.9);

        assertTrue(piece(pieces, "first").zInches() > piece(pieces, "second").zInches(), "the launcher end is high");
        assertTrue(piece(pieces, "first").xInches() < piece(pieces, "second").xInches(), "the launcher end is further back");
    }

    /** WHAT: Reverse power spits the newest ball, one per spitSeconds, and it rolls away in front of the robot. */
    @Test
    void reverseSpitsTheNewestBall() {
        GamePieces pieces = withIntake(4, pollen("first", 16, 0), pollen("second", 26, 0));
        pieces.intakePower(1.0);
        Pose robot = driveAlongX(pieces, 0, 0.9);

        pieces.intakePower(-1.0);
        stay(pieces, robot, SPIT_SECONDS - 2 * LOOP);
        assertEquals(GamePiece.HELD, piece(pieces, "second").state(), "not yet");
        stay(pieces, robot, 3 * LOOP);
        assertEquals(GamePiece.FIELD, piece(pieces, "second").state());
        assertEquals(GamePiece.HELD, piece(pieces, "first").state(), "one at a time, newest first");
        assertEquals(1.4, piece(pieces, "second").zInches(), 1e-9, "on the tiles");

        stay(pieces, robot, SPIT_SECONDS);
        assertEquals(GamePiece.FIELD, piece(pieces, "first").state());
        stay(pieces, robot, 0.3);
        assertTrue(piece(pieces, "first").xInches() > robot.xInches() + SIZE / 2 + 1.4, "rolled away: " + piece(pieces, "first"));
    }

    /** WHAT: Letting go of reverse early starts the wait again, and a little reverse power does nothing. */
    @Test
    void aShortOrWeakReverseSpitsNothing() {
        GamePieces pieces = withIntake(4, pollen("ball", 16, 0));
        pieces.intakePower(1.0);
        Pose robot = driveAlongX(pieces, 0, 0.4);

        pieces.intakePower(-1.0);
        stay(pieces, robot, SPIT_SECONDS - 3 * LOOP);
        pieces.intakePower(0);
        stay(pieces, robot, LOOP);
        pieces.intakePower(-1.0);
        stay(pieces, robot, SPIT_SECONDS - 3 * LOOP);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state(), "two short ones aren't one long one");

        pieces.intakePower(-MIN_POWER / 2);
        stay(pieces, robot, 2.0);
        assertEquals(GamePiece.HELD, piece(pieces, "ball").state(), "too little power");
    }

    /** WHAT: A spat ball isn't taken straight back while the motor is still in reverse. */
    @Test
    void aSpatBallIsntTakenBack() {
        GamePieces pieces = withIntake(4, pollen("ball", 16, 0));
        pieces.intakePower(1.0);
        Pose robot = driveAlongX(pieces, 0, 0.4);

        pieces.intakePower(-1.0);
        stay(pieces, robot, 1.0);
        assertEquals(GamePiece.FIELD, piece(pieces, "ball").state());
    }

    /** The full BIOBUZZ field at the start of a match. */
    private static GamePieces match(MechanismSetup setup) {
        GamePieces pieces = new GamePieces(BioBuzzPieces.start(Pose.ORIGIN, SIZE), PieceModel.bioBuzz(), BioBuzzObstacles.all(),
                BioBuzzPieces.flowers());
        if (setup != null) {
            pieces.useMechanism(setup, Pose.ORIGIN);
        }
        return pieces;
    }

    private static List<GamePiece> preloads(List<GamePiece> pieces) {
        return pieces.stream().filter(p -> p.id().startsWith(BioBuzzPieces.PRELOAD_ID_PREFIX)).toList();
    }

    /** WHAT: The file's number of pre-loads start held, in the first list too; any others stay on the tiles. */
    @Test
    void preloadsStartHeld() {
        GamePieces pieces = match(new MechanismSetup(intake(), hold(4, 3), launcher()));

        for (List<GamePiece> list : List.of(pieces.initial(), pieces.snapshot())) {
            assertEquals(List.of(GamePiece.HELD, GamePiece.HELD, GamePiece.HELD, GamePiece.FIELD),
                    preloads(list).stream().map(GamePiece::state).toList());
        }
    }

    /** WHAT: A robot with no mechanism starts exactly as before, its pre-loads on the tiles, and never holds a ball. */
    @Test
    void noMechanismChangesNothing() {
        GamePieces pieces = match(null);
        assertEquals(BioBuzzPieces.start(Pose.ORIGIN, SIZE), pieces.initial());
        assertEquals(BioBuzzPieces.start(Pose.ORIGIN, SIZE), pieces.snapshot());

        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 1.0);
        assertTrue(pieces.snapshot().stream().noneMatch(p -> p.state().equals(GamePiece.HELD)));
    }

    /** WHAT: A mechanism can only be given once, and not as nothing. */
    @Test
    void aMechanismIsGivenOnce() {
        GamePieces pieces = match(new MechanismSetup(intake(), hold(4, 0), launcher()));
        assertThrows(IllegalStateException.class, () -> pieces.useMechanism(new MechanismSetup(intake(), hold(4, 0), null), Pose.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> match(null).useMechanism(null, Pose.ORIGIN));
    }

    /** A far FLOWER with this bottom ball and one POLLEN on it; the robot faces it. */
    private static GamePieces flower(GamePiece bottom, MechanismSetup setup) {
        FlowerSpot far = new FlowerSpot("far", -68.04, -23.39, 1, 0);
        List<GamePiece> start = new ArrayList<>();
        start.add(new GamePiece("top", GamePiece.POLLEN, GamePiece.FLOWER, far.xInches(), far.yInches(), 2 * bottom.radiusInches() + 1.4));
        start.add(bottom);
        GamePieces pieces = new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), List.of(far));
        pieces.useMechanism(setup, Pose.inches(-40, far.yInches(), 180));
        return pieces;
    }

    /** Moves the box to just short of the FLOWER's head, 3.2 in from its center. */
    private static void arriveAtFlower(GamePieces pieces) {
        double fromX = -40;
        double closeX = -68.04 + 3.2 + SIZE / 2;
        Pose robot = Pose.inches(fromX, -23.39, 180);
        for (int k = 1; k <= 50; k++) {
            Pose next = Pose.inches(fromX + (closeX - fromX) * k / 50.0, -23.39, 180);
            pieces.step(robot, next, SIZE, SIZE, LOOP, true);
            robot = next;
        }
        Pose close = robot;
        for (int k = 0; k < 40; k++) {
            pieces.step(close, close, SIZE, SIZE, LOOP, true);
        }
    }

    private static final GamePiece SEATED = new GamePiece("bottom", GamePiece.POLLEN, GamePiece.FLOWER, -68.04, -23.39, 1.4);

    /** WHAT: An intake running at a FLOWER takes its bottom POLLEN, and the next ball drops. */
    @Test
    void anIntakeTakesTheSeatedBall() {
        GamePieces pieces = flower(SEATED, new MechanismSetup(intake(), hold(1, 0), launcher()));
        pieces.intakePower(1.0);
        arriveAtFlower(pieces);

        assertEquals(GamePiece.HELD, piece(pieces, "bottom").state());
        assertEquals(GamePiece.FLOWER, piece(pieces, "top").state(), "it holds only one, so the next stays");
        assertEquals(1.4, piece(pieces, "top").zInches(), 1e-9, "dropped by one ball");
    }

    /** WHAT: A robot with an intake that isn't running takes nothing out of a FLOWER. */
    @Test
    void anIntakeThatIsOffTakesNothing() {
        GamePieces pieces = flower(SEATED, new MechanismSetup(intake(), hold(4, 0), launcher()));
        arriveAtFlower(pieces);

        assertEquals(GamePiece.FLOWER, piece(pieces, "bottom").state());
    }

    /** WHAT: A robot with a launcher but no intake takes nothing out of a FLOWER (G418; D38 removed D24's stand-in). */
    @Test
    void noIntakeTakesNothingFromAFlower() {
        GamePieces pieces = flower(SEATED, new MechanismSetup(null, hold(4, 0), launcher()));
        pieces.intakePower(1.0);
        arriveAtFlower(pieces);

        assertEquals(GamePiece.FLOWER, piece(pieces, "bottom").state());
    }

    /** WHAT: Even a robot that takes NECTAR can't take one out of a FLOWER (G418), and one that doesn't take POLLEN leaves POLLEN. */
    @Test
    void onlyAcceptedPollenLeavesAFlower() {
        MechanismSetup.Hold anyKind = new MechanismSetup.Hold(guess(4), List.of(GamePiece.POLLEN, GamePiece.RED_NECTAR), guess(0));
        GamePieces nectar = flower(new GamePiece("bottom", GamePiece.RED_NECTAR, GamePiece.FLOWER, -68.04, -23.39, 1.8),
                new MechanismSetup(intake(), anyKind, launcher()));
        nectar.intakePower(1.0);
        arriveAtFlower(nectar);
        assertEquals(GamePiece.FLOWER, piece(nectar, "bottom").state());

        MechanismSetup.Hold nectarOnly = new MechanismSetup.Hold(guess(4), List.of(GamePiece.RED_NECTAR), guess(0));
        GamePieces pollen = flower(SEATED, new MechanismSetup(intake(), nectarOnly, launcher()));
        pollen.intakePower(1.0);
        arriveAtFlower(pollen);
        assertEquals(GamePiece.FLOWER, piece(pollen, "bottom").state());
    }

    /** WHAT: A robot with no launcher holds balls too, in a place of their own. */
    @Test
    void noLauncherStillHolds() {
        GamePieces pieces = withIntakeOnly(pollen("ball", 16, 0));
        pieces.intakePower(1.0);
        driveAlongX(pieces, 0, 0.4);

        GamePiece ball = piece(pieces, "ball");
        assertEquals(GamePiece.HELD, ball.state());
        assertTrue(ball.zInches() > 1.4, "above the tiles: " + ball);
        assertFalse(Double.isNaN(ball.xInches()));
    }

    private static GamePieces withIntakeOnly(GamePiece... balls) {
        GamePieces pieces = new GamePieces(List.of(balls), PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(intake(), hold(4, 0), null), Pose.ORIGIN);
        return pieces;
    }

    /** WHAT: The same drive gives exactly the same balls every time (02-unity-readiness §3). */
    @Test
    void theSameDriveGivesTheSameBalls() {
        List<List<GamePiece>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            GamePieces pieces = match(new MechanismSetup(intake(), hold(4, 2), launcher()));
            pieces.intakePower(1.0);
            Pose robot = driveAlongX(pieces, 0, 1.5);
            pieces.intakePower(-1.0);
            stay(pieces, robot, 0.5);
            runs.add(pieces.snapshot());
        }
        assertEquals(runs.get(0), runs.get(1));
    }
}
