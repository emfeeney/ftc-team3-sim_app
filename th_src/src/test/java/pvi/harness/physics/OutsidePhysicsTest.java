package pvi.harness.physics;

import java.util.ArrayList;
import java.util.List;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import org.junit.jupiter.api.Test;

import pvi.harness.hardware.MechanismSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Outside physics (D30, W-UP1): Unity moves the loose, flying and CELL balls and supplies where they are;
 * Java moves none of them but keeps every rule. The robot box is placed by hand and the motors' effects are
 * set directly, so these don't depend on any team's code. The numbers are made up.
 */
class OutsidePhysicsTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    /** The middle of the red HIVE's +x CELL, which starts up (tilt +30): 15.45 in out along the arm, 5.53 in square to it. */
    private static final double[] RED_UP_CELL = cellPoint(1, 15.45, 5.53);
    /** The same point in the red HIVE's -x CELL, which starts down. */
    private static final double[] RED_DOWN_CELL = cellPoint(-1, 15.45, 5.53);

    private static double[] cellPoint(int side, double along, double square) {
        return cellPointAt(30, side, along, square);
    }

    /** A point in the red HIVE's CELL on {@code side} with the HIVE at {@code tiltDegrees}: field x and z. */
    private static double[] cellPointAt(double tiltDegrees, int side, double along, double square) {
        double tilt = Math.toRadians(tiltDegrees);
        return new double[] {side * along * Math.cos(tilt) - square * Math.sin(tilt),
                43.95 + side * along * Math.sin(tilt) + square * Math.cos(tilt)};
    }

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static GamePieces withHives(List<GamePiece> start) {
        GamePieces pieces = new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), List.of(),
                BioBuzzHive.hives(), BioBuzzHive.frame());
        pieces.useOutsidePhysics();
        return pieces;
    }

    private static void step(GamePieces pieces, Pose robot) {
        pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
    }

    private static PieceMotion at(String id, double x, double y, double z) {
        return new PieceMotion(id, x, y, z, 0, 0, 0);
    }

    /**
     * WHAT: Java moves none of the outside's balls: a loose ball supplied rolling at 100 in/s, toward a wall, is
     * where it was supplied after a step, and a held ball ignores what is supplied for it.
     */
    @Test
    void javaMovesNoneOfTheOutsidesBalls() {
        GamePieces pieces = new GamePieces(List.of(new GamePiece("ball", GamePiece.POLLEN, GamePiece.FIELD, 40, 0, 1.4)),
                PieceModel.bioBuzz());
        pieces.useOutsidePhysics();
        pieces.supply(List.of(new PieceMotion("ball", 70, 10, 1.4, 100, 0, 0)));
        for (int i = 0; i < 10; i++) {
            step(pieces, Pose.ORIGIN);
        }
        GamePiece ball = piece(pieces, "ball");
        assertEquals(70, ball.xInches(), 1e-9);
        assertEquals(10, ball.yInches(), 1e-9);
        assertEquals(GamePiece.FIELD, ball.state());
    }

    /**
     * WHAT: A loose ball supplied inside the running intake's zone becomes held, and one supplied outside it
     * stays loose.
     */
    @Test
    void aSuppliedBallInsideTheIntakeBecomesHeld() {
        MechanismSetup.Intake intake = new MechanismSetup.Intake("intake", Direction.FORWARD, guess(0.1), guess(11), guess(0),
                guess(3), guess(10), guess(0.3), guess(30));
        GamePieces pieces = new GamePieces(List.of(
                new GamePiece("in", GamePiece.POLLEN, GamePiece.FIELD, 40, 0, 1.4),
                new GamePiece("out", GamePiece.POLLEN, GamePiece.FIELD, 40, 20, 1.4)), PieceModel.bioBuzz());
        pieces.useMechanism(new MechanismSetup(intake, new MechanismSetup.Hold(guess(4), List.of(GamePiece.POLLEN), guess(0)),
                null), Pose.ORIGIN);
        pieces.useOutsidePhysics();
        pieces.intakePower(1.0);

        pieces.supply(List.of(at("in", 11.5, 0, 1.4), at("out", 30, 0, 1.4)));
        step(pieces, Pose.ORIGIN);

        assertEquals(GamePiece.HELD, piece(pieces, "in").state());
        assertEquals(GamePiece.FIELD, piece(pieces, "out").state());

        pieces.supply(List.of(at("in", 50, 50, 1.4)));
        step(pieces, Pose.ORIGIN);
        assertEquals(GamePiece.HELD, piece(pieces, "in").state(), "a held ball ignores a late entry for it");
        assertTrue(piece(pieces, "in").xInches() < 20, "and stays on the robot: " + piece(pieces, "in"));
    }

    /**
     * WHAT: A flying ball supplied inside the red HIVE's upward CELL is now in it (and counted); one supplied at
     * the same place in the lowered CELL isn't.
     */
    @Test
    void aSuppliedBallInsideTheUpwardCellIsInIt() {
        GamePieces pieces = withHives(List.of(
                new GamePiece("up", GamePiece.POLLEN, GamePiece.FLYING, 30, -30, 30),
                new GamePiece("down", GamePiece.POLLEN, GamePiece.FLYING, 30, -40, 30)));

        pieces.supply(List.of(at("up", RED_UP_CELL[0], -12.75, RED_UP_CELL[1]), at("down", RED_DOWN_CELL[0], -12.75, RED_DOWN_CELL[1])));
        step(pieces, Pose.inches(0, -50, 0));

        assertEquals(GamePiece.CELL, piece(pieces, "up").state());
        assertEquals(List.of("up"), pieces.ballsInCell("red-hive", 1));
        assertEquals(GamePiece.FLYING, piece(pieces, "down").state(), "the lowered CELL catches nothing");
        assertEquals(List.of(), pieces.ballsInCell("red-hive", -1));
    }

    /**
     * WHAT: A CELL ball the outside moves out of its CELL is flying again; once it is supplied down on the
     * tiles it is loose.
     */
    @Test
    void aBallLeavingItsCellIsFlyingThenLoose() {
        GamePieces pieces = withHives(List.of(new GamePiece("ball", GamePiece.POLLEN, GamePiece.FLYING, 30, -30, 30)));
        Pose robot = Pose.inches(0, -50, 0);
        pieces.supply(List.of(at("ball", RED_UP_CELL[0], -12.75, RED_UP_CELL[1])));
        step(pieces, robot);
        assertEquals(GamePiece.CELL, piece(pieces, "ball").state());

        pieces.supply(List.of(at("ball", 26, -12.75, 40)));
        step(pieces, robot);
        assertEquals(GamePiece.FLYING, piece(pieces, "ball").state(), "out of the CELL, in the air");
        assertEquals(List.of(), pieces.ballsInCell("red-hive", 1));

        pieces.supply(List.of(at("ball", 30, -12.75, 1.4)));
        step(pieces, robot);
        assertEquals(GamePiece.FIELD, piece(pieces, "ball").state(), "landed");
    }

    /**
     * WHAT: A launch is reported once, with where the ball leaves and its velocity: 200 in/s at 60 degrees up,
     * straight ahead of a robot facing +y. In Java's own physics nothing is reported.
     */
    @Test
    void aLaunchIsReportedWithItsVelocity() {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1),
                guess(0.3), guess(6), guess(0), guess(14), guess(60), guess(0));
        Pose robot = Pose.inches(0, -40, 90);
        List<List<PieceMotion>> reported = new ArrayList<>();
        for (boolean outside : new boolean[] {true, false}) {
            GamePieces pieces = new GamePieces(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, 0, 1.4)),
                    PieceModel.bioBuzz());
            pieces.useMechanism(new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                    launcher), robot);
            if (outside) {
                pieces.useOutsidePhysics();
            }
            pieces.feedPower(1.0);
            pieces.launcherBallSpeed(200);
            List<PieceMotion> all = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
                all.addAll(pieces.takeLaunches());
            }
            reported.add(all);
            if (outside) {
                assertEquals(GamePiece.FLYING, piece(pieces, "pollen-preload-1").state());
            }
        }

        assertEquals(1, reported.get(0).size(), "reported once: " + reported.get(0));
        PieceMotion shot = reported.get(0).get(0);
        assertEquals("pollen-preload-1", shot.id());
        assertEquals(0, shot.xInches(), 1e-9);
        assertEquals(-34, shot.yInches(), 1e-9);
        assertEquals(14, shot.zInches(), 1e-9);
        assertEquals(0, shot.vxInchesPerSecond(), 1e-9);
        assertEquals(100, shot.vyInchesPerSecond(), 1e-9);
        assertEquals(200 * Math.sin(Math.toRadians(60)), shot.vzInchesPerSecond(), 1e-9);
        assertEquals(List.of(), reported.get(1), "Java's own physics reports nothing");
    }

    /**
     * WHAT: The tip still comes from supplied CELL balls: the red upward CELL's 3 staged NECTAR and 3 supplied
     * POLLEN (199 g) don't tip it; a 4th POLLEN (224 g) does, and the HIVE swings the full 60 degrees. Nothing
     * gets into a CELL during the tip. Java neither swings a CELL ball with the HIVE nor spills it at the end:
     * the 4th POLLEN, supplied going down with its CELL, stays exactly where it was supplied, in its CELL.
     */
    @Test
    void theTipComesFromSuppliedCellBalls() {
        List<GamePiece> start = new ArrayList<>(BioBuzzPieces.start(Pose.ORIGIN, SIZE).stream()
                .filter(p -> p.id().startsWith("red-nectar-cell")).toList());
        for (int i = 1; i <= 5; i++) {
            start.add(new GamePiece("pollen-" + i, GamePiece.POLLEN, GamePiece.FLYING, 30, -30 - 4 * i, 30));
        }
        GamePieces pieces = withHives(start);
        Pose robot = Pose.inches(0, -50, 0);
        List<PieceMotion> inCell = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            double[] p = cellPoint(1, 12 + 2 * i, 0);
            inCell.add(at("pollen-" + i, p[0], -12.75 + 3 * i - 6, p[1]));
        }
        pieces.supply(inCell);
        step(pieces, robot);
        assertEquals(6, pieces.ballsInCell("red-hive", 1).size());
        assertEquals(0, tips(pieces), "199 g doesn't tip it");

        double[] fourth = cellPoint(1, 19, 0);
        pieces.supply(List.of(at("pollen-4", fourth[0], -12.75, fourth[1])));
        step(pieces, robot);
        assertEquals(1, tips(pieces), "224 g starts a tip");
        double[] supplied = fourth;
        for (int i = 0; i < 60; i++) {
            double tilt = 30 + pieces.hiveTilts().get(0).swingDegrees();
            supplied = cellPointAt(tilt, 1, 19, 0);
            List<PieceMotion> motions = new ArrayList<>(List.of(at("pollen-4", supplied[0], -12.75, supplied[1])));
            if (i == 10) {
                double[] late = cellPointAt(tilt, 1, 15, 3);
                motions.add(at("pollen-5", late[0], -12.75, late[1]));
            }
            pieces.supply(motions);
            step(pieces, robot);
            if (i == 10) {
                assertEquals(GamePiece.FLYING, piece(pieces, "pollen-5").state(), "nothing gets in during a tip");
            }
            if (i == 20) {
                assertEquals(supplied[0], piece(pieces, "pollen-4").xInches(), 1e-9, "Java doesn't swing it mid-tip");
                assertEquals(supplied[1], piece(pieces, "pollen-4").zInches(), 1e-9, "Java doesn't swing it mid-tip");
            }
        }
        assertEquals(-60, pieces.hiveTilts().get(0).swingDegrees(), 1e-9, "the full tip");
        GamePiece ball = piece(pieces, "pollen-4");
        assertEquals(supplied[0], ball.xInches(), 1e-9, "Java didn't move it");
        assertEquals(supplied[1], ball.zInches(), 1e-9, "Java didn't move it");
        assertEquals(GamePiece.CELL, ball.state(), "not spilled by Java: still in its (now lowered) CELL");
    }

    /**
     * WHAT: The intake's spit is reported too, with its start and its 30 in/s; a FLOWER ball ignores what is supplied
     * for it, and a robot with no intake gets nothing out of a FLOWER, so nothing is reported for it (D38).
     */
    @Test
    void aSpitIsReportedAndAFlowerReleasesNothing() {
        FlowerSpot far = new FlowerSpot("far", -68.04, -23.39, 1, 0);
        GamePieces flower = new GamePieces(List.of(new GamePiece("stack-1", GamePiece.POLLEN, GamePiece.FLOWER, far.xInches(),
                far.yInches(), 1.4)), PieceModel.bioBuzz(), BioBuzzObstacles.all(), List.of(far));
        flower.useOutsidePhysics();
        flower.supply(List.of(at("stack-1", 0, 0, 1.4)));
        step(flower, Pose.inches(-40, far.yInches() + 2, 180));
        assertEquals(far.xInches(), piece(flower, "stack-1").xInches(), 1e-9, "a FLOWER ball ignores what is supplied for it");
        step(flower, Pose.inches(far.xInches() + SIZE / 2 + 3.5, far.yInches() + 2, 180));
        assertEquals(List.of(), flower.takeLaunches(), "no intake, nothing out");
        assertEquals(GamePiece.FLOWER, piece(flower, "stack-1").state());

        MechanismSetup.Intake intake = new MechanismSetup.Intake("intake", Direction.FORWARD, guess(0.1), guess(11), guess(0),
                guess(3), guess(10), guess(0.3), guess(30));
        GamePieces spitting = new GamePieces(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD, 0, -40, 1.4)),
                PieceModel.bioBuzz());
        spitting.useMechanism(new MechanismSetup(intake, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                null), Pose.ORIGIN);
        spitting.useOutsidePhysics();
        spitting.intakePower(-1.0);
        List<PieceMotion> spat = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            step(spitting, Pose.ORIGIN);
            spat.addAll(spitting.takeLaunches());
        }
        assertEquals(1, spat.size(), "spat once: " + spat);
        assertEquals(11, spat.get(0).xInches(), 1e-9);
        assertEquals(30, spat.get(0).vxInchesPerSecond(), 1e-9);
        assertEquals(GamePiece.FIELD, piece(spitting, "pollen-preload-1").state());
    }

    /** WHAT: supply(...) is refused outside outside physics, and for a piece that doesn't exist. */
    @Test
    void supplyRefusesWrongUse() {
        GamePieces pieces = new GamePieces(List.of(new GamePiece("ball", GamePiece.POLLEN, GamePiece.FIELD, 40, 0, 1.4)),
                PieceModel.bioBuzz());
        assertThrows(IllegalStateException.class, () -> pieces.supply(List.of(at("ball", 0, 0, 1.4))));
        pieces.useOutsidePhysics();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> pieces.supply(List.of(at("nope", 0, 0, 1.4))));
        assertTrue(e.getMessage().contains("nope"), e.getMessage());
    }

    private static int tips(GamePieces pieces) {
        return pieces.hiveTilts().stream().filter(h -> h.id().equals("red-hive")).findFirst().orElseThrow().tips();
    }
}
