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
 * Flying balls and the HIVE (TU06 9.6): the frame and a lowered CELL's outside stop them, an upward
 * CELL's opening takes them in, and enough weight in it tips the HIVE and spills that CELL. The robot is
 * placed by hand and the launcher's ball speed set directly, so this doesn't depend on any team's code.
 */
class HiveCellTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    /**
     * A robot facing the red HIVE from the audience side: a shot at 250 in/s and 60 degrees, from 14 in up and
     * 6 in in front of its center, goes through the middle of the upward CELL's opening, clear of its rim.
     */
    private static final Pose AT_RED_HIVE = Pose.inches(58, -12.75, 180);
    private static final double INTO_THE_CELL = 250;

    private static ModelValue guess(double value) {
        return ModelValue.placeholder(value, "test", "made up for this test");
    }

    private static MechanismSetup launcher(int preloaded) {
        MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1), guess(0.3),
                guess(6), guess(0), guess(14), guess(60), guess(0));
        return new MechanismSetup(null, new MechanismSetup.Hold(guess(4), List.of(GamePiece.POLLEN), guess(preloaded)), launcher);
    }

    /** The BIOBUZZ field with the HIVE, these pieces, and optionally a robot holding pre-loads. */
    private static GamePieces field(List<GamePiece> start, MechanismSetup setup, Pose robot) {
        GamePieces pieces = new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), BioBuzzPieces.flowers(),
                BioBuzzHive.hives(), BioBuzzHive.frame());
        if (setup != null) {
            pieces.useMechanism(setup, robot);
        }
        return pieces;
    }

    /** The real start of a match, with a robot holding all 4 pre-loads at the red HIVE. */
    private static GamePieces matchAtTheRedHive() {
        return field(BioBuzzPieces.start(AT_RED_HIVE, SIZE), launcher(4), AT_RED_HIVE);
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static void stay(GamePieces pieces, Pose robot, double seconds) {
        for (double t = 0; t < seconds - 1e-9; t += LOOP) {
            pieces.step(robot, robot, SIZE, SIZE, LOOP, true);
        }
    }

    /** Fires one held ball at the given ball speed and waits for it to settle. */
    private static void fire(GamePieces pieces, double ballSpeed) {
        pieces.launcherBallSpeed(ballSpeed);
        pieces.feedPower(1.0);
        stay(pieces, AT_RED_HIVE, 0.32);
        pieces.feedPower(0);
        stay(pieces, AT_RED_HIVE, 2.0);
    }

    /** A HIVE's tilt now: its starting tilt plus its swing. */
    private static double tilt(GamePieces pieces, String hiveId) {
        double start = BioBuzzHive.hives().stream().filter(h -> h.id().equals(hiveId)).findFirst().orElseThrow().startTiltDegrees();
        return start + pieces.hiveTilts().stream().filter(t -> t.id().equals(hiveId)).findFirst().orElseThrow().swingDegrees();
    }

    private static int tips(GamePieces pieces, String hiveId) {
        return pieces.hiveTilts().stream().filter(t -> t.id().equals(hiveId)).findFirst().orElseThrow().tips();
    }

    /** A flying ball let go, standing still, at this point. */
    private static GamePiece dropped(double x, double y, double z) {
        return new GamePiece("dropped", GamePiece.POLLEN, GamePiece.FLYING, x, y, z);
    }

    /** WHAT: The HIVEs start as the manual's Figure 10-2 shows: red tilted +30 (its audience CELL up), blue -30. */
    @Test
    void theHivesStartAsTheManualShows() {
        GamePieces pieces = matchAtTheRedHive();
        assertEquals(30.0, tilt(pieces, "red-hive"), 1e-9);
        assertEquals(-30.0, tilt(pieces, "blue-hive"), 1e-9);
        assertEquals(List.of("red-nectar-cell-1", "red-nectar-cell-2", "red-nectar-cell-3"), pieces.ballsInCell("red-hive", 1));
        assertEquals(List.of("blue-nectar-cell-1", "blue-nectar-cell-2", "blue-nectar-cell-3"), pieces.ballsInCell("blue-hive", -1));
        assertEquals(List.of(), pieces.ballsInCell("red-hive", -1));
    }

    /** WHAT: The staged NECTAR rest where they started: on the CELL's floor, the first against its back wall. */
    @Test
    void theStagedNectarStayPut() {
        GamePieces pieces = matchAtTheRedHive();
        List<GamePiece> before = pieces.snapshot().stream().filter(p -> p.state().equals(GamePiece.CELL)).toList();
        stay(pieces, AT_RED_HIVE, 1.0);
        for (GamePiece p : before) {
            GamePiece now = piece(pieces, p.id());
            assertEquals(p.xInches(), now.xInches(), 1e-9, p.id());
            assertEquals(p.zInches(), now.zInches(), 1e-9, p.id());
        }
    }

    /** WHAT: A shot through the upward CELL's opening comes to rest in it, counted with the NECTAR there. */
    @Test
    void aBallEntersTheUpwardCell() {
        GamePieces pieces = matchAtTheRedHive();
        fire(pieces, INTO_THE_CELL);

        GamePiece in = piece(pieces, "pollen-preload-1");
        assertEquals(GamePiece.CELL, in.state());
        // Resting on the floor: square to the tilted arm, a radius above the floor's -1.47 in.
        double tilt = Math.toRadians(30);
        double square = -in.xInches() * Math.sin(tilt) + (in.zInches() - 43.95) * Math.cos(tilt);
        assertEquals(BioBuzzHive.FLOOR_INCHES + in.radiusInches(), square, 1e-6, "on the CELL's floor: " + in);
        assertEquals(4, pieces.ballsInCell("red-hive", 1).size());
        assertTrue(pieces.ballsInCell("red-hive", 1).contains("pollen-preload-1"));
        assertEquals(30.0, tilt(pieces, "red-hive"), 1e-9, "3 NECTAR and 1 POLLEN (148.8 g) don't tip it");
    }

    /** WHAT: A ball that doesn't reach the opening (too slow) falls short and lands loose, outside. */
    @Test
    void aShortShotDoesntGetIn() {
        GamePieces pieces = matchAtTheRedHive();
        fire(pieces, 200);

        assertEquals(GamePiece.FIELD, piece(pieces, "pollen-preload-1").state());
        assertEquals(3, pieces.ballsInCell("red-hive", 1).size());
    }

    /**
     * WHAT: A ball dropped onto the lowered CELL bounces off its outside: it lands on the CELL's tilted top
     * and rolls off its low end toward -x, never into the CELL.
     */
    @Test
    void aBallBouncesOffTheLoweredCell() {
        // Above the middle of the red far CELL, which hangs down on the pivot's -x side.
        GamePieces pieces = field(List.of(dropped(-15.77, -12.75, 70)), null, Pose.ORIGIN);
        double xWhenBelow45 = Double.NaN;
        for (int i = 0; i < 100 && Double.isNaN(xWhenBelow45); i++) {
            pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
            GamePiece p = piece(pieces, "dropped");
            assertTrue(p.state().equals(GamePiece.FLYING), "never in the CELL: " + p);
            if (p.zInches() < 45) {
                xWhenBelow45 = p.xInches();
            }
        }
        assertTrue(xWhenBelow45 < -15.77 - 10, "turned aside by the CELL, not through it: x " + xWhenBelow45);
        stay(pieces, Pose.ORIGIN, 3.0);
        assertEquals(GamePiece.FIELD, piece(pieces, "dropped").state());
        assertEquals(List.of(), pieces.ballsInCell("red-hive", -1));
    }

    /** WHAT: A ball dropped onto the frame's top bar bounces off it. */
    @Test
    void aBallBouncesOffTheTopBar() {
        GamePieces pieces = field(List.of(dropped(0, 0, 55)), null, Pose.ORIGIN);
        double lowest = 55;
        for (int i = 0; i < 60; i++) {
            pieces.step(Pose.ORIGIN, Pose.ORIGIN, SIZE, SIZE, LOOP, true);
            lowest = Math.min(lowest, piece(pieces, "dropped").zInches());
        }
        assertEquals(41.95 + 1.4, lowest, 0.3, "it stopped on the bar's top (41.95 in)");
    }

    /**
     * WHAT: A ball dropped onto one of the frame's leaning legs is turned aside by it. The red far leg runs
     * from (-18.33, -23.62) on the floor up to (-0.93, -12.85) by the top bar (CAD); at 30 in up it passes
     * (-5.42, -15.63).
     */
    @Test
    void aBallBouncesOffAFrameLeg() {
        GamePieces pieces = field(List.of(dropped(-5.42, -15.63, 37)), null, Pose.ORIGIN);
        stay(pieces, Pose.ORIGIN, 3.0);
        GamePiece end = piece(pieces, "dropped");
        assertEquals(GamePiece.FIELD, end.state());
        assertTrue(Math.hypot(end.xInches() + 5.42, end.yInches() + 15.63) > 3, "turned aside, not straight down: " + end);
    }

    /**
     * WHAT: A ball dropped onto the red HIVE's arm, between the axle and the CELL's back wall, is caught by
     * it. Dropped at (3.2, -12.75) it meets the arm about 4.3 in out from the pivot, near 47 in up; without
     * the arm it would fall straight past, onto a logo panel 40 in up.
     */
    @Test
    void aBallLandsOnAnArm() {
        // From 52 in up, below the tilted CELL: higher up, the CELL reaches over this spot.
        GamePieces pieces = field(List.of(dropped(3.2, -12.75, 52)), null, Pose.ORIGIN);
        // It meets the arm after about 0.16 s; it then slides down the arm toward the pivot and off its end.
        stay(pieces, Pose.ORIGIN, 0.3);
        double z = piece(pieces, "dropped").zInches();
        assertTrue(z > 44, "held up by the arm, not through it: at 0.3 s it's " + z + " in up");
        assertEquals(List.of(), pieces.ballsInCell("red-hive", 1), "and not in the CELL");
    }

    /**
     * WHAT: Nothing gets into a CELL while its HIVE tips: a ball let go inside the red upward CELL just as
     * 4 POLLEN on its 3 NECTAR start a tip is pushed out and falls.
     */
    @Test
    void nothingGetsInDuringATip() {
        List<GamePiece> start = new ArrayList<>(BioBuzzPieces.start(Pose.ORIGIN, SIZE).stream()
                .filter(p -> p.id().startsWith("red-nectar-cell")).toList());
        for (int i = 0; i < 4; i++) {
            // On the CELL's floor beside the NECTAR: 13 in out along the arm, a radius up, at the starting tilt.
            double tilt = Math.toRadians(30);
            double along = 13 + 2 * i;
            double square = BioBuzzHive.FLOOR_INCHES + 1.4;
            start.add(new GamePiece("pollen-" + i, GamePiece.POLLEN, GamePiece.CELL, along * Math.cos(tilt) - square * Math.sin(tilt),
                    -8, 43.95 + along * Math.sin(tilt) + square * Math.cos(tilt)));
        }
        // Inside the same CELL: 11.35 in out along the arm, 7.38 in square to it.
        start.add(dropped(6.15, -12.75, 56));
        GamePieces pieces = field(start, null, Pose.ORIGIN);

        stay(pieces, Pose.ORIGIN, 0.1);
        assertEquals(1, tips(pieces, "red-hive"), "the tip has started");
        assertTrue(!pieces.ballsInCell("red-hive", 1).contains("dropped"), "not caught while tipping");
    }

    /**
     * WHAT: A lowered CELL never holds a ball: one let go inside it falls out instead of resting on its
     * floor, because only the upward CELL catches.
     */
    @Test
    void theLoweredCellHoldsNothing() {
        // The middle of the red far CELL, which starts down: 15.45 in out along the arm, 5.53 in square to it.
        double tilt = Math.toRadians(30);
        double x = -15.45 * Math.cos(tilt) - 5.53 * Math.sin(tilt);
        double z = 43.95 - 15.45 * Math.sin(tilt) + 5.53 * Math.cos(tilt);
        GamePieces pieces = field(List.of(dropped(x, -12.75, z)), null, Pose.ORIGIN);
        stay(pieces, Pose.ORIGIN, 3.0);

        assertEquals(List.of(), pieces.ballsInCell("red-hive", -1));
        assertEquals(GamePiece.FIELD, piece(pieces, "dropped").state(), "it fell to the tiles");
    }

    /**
     * WHAT: A ball thrown straight up under the red upward CELL stops at its floor's underside, 1.5 in below
     * the inner floor (D31, the CAD), at two places along the tilted floor (12 and 19 in out along the arm). Under
     * the middle, the old thin floor let it rise to 49.2 in; now it stops near 47.5 in.
     */
    @Test
    void aBallFromBelowMeetsTheFloorsUnderside() {
        double tilt = Math.toRadians(30);
        double underside = -2.97;   // the CAD, as UnityBot measured it (D31); not the constant, so a wrong constant fails
        for (double along : new double[] {12, 19}) {
            double x = along * Math.cos(tilt) - underside * Math.sin(tilt);
            MechanismSetup.Launcher launcher = new MechanismSetup.Launcher("wheel", Direction.FORWARD, guess(28), guess(6000),
                    guess(0.3), guess(0.1), guess(4), guess(0.5), guess(20), "feed", Direction.FORWARD, guess(0.1), guess(0.3),
                    guess(0), guess(0), guess(14), guess(90), guess(0));
            Pose robot = Pose.inches(x, -12.75, 0);
            GamePieces pieces = field(new ArrayList<>(List.of(new GamePiece("pollen-preload-1", GamePiece.POLLEN, GamePiece.FIELD,
                    40, -60, 1.4))), new MechanismSetup(null, new MechanismSetup.Hold(guess(1), List.of(GamePiece.POLLEN), guess(1)),
                    launcher), robot);
            pieces.launcherBallSpeed(200);
            pieces.feedPower(1.0);
            double highest = 0;
            for (int i = 0; i < 600; i++) {
                pieces.step(robot, robot, SIZE, SIZE, LOOP / 10, true);   // small steps, to see the highest point
                highest = Math.max(highest, piece(pieces, "pollen-preload-1").zInches());
            }

            // Touching the underside: the ball's center a radius below it, square to the tilted floor.
            double touching = 43.95 + (x * Math.sin(tilt) + underside - 1.4) / Math.cos(tilt);
            assertEquals(touching, highest, 0.15, "at " + along + " in along: it rose to the floor's underside and no higher");
        }
    }

    /** WHAT: A ball dropped well clear of the HIVE just falls, as before. */
    @Test
    void aBallClearOfTheHiveJustFalls() {
        GamePieces pieces = field(List.of(dropped(40, 40, 30)), null, Pose.ORIGIN);
        stay(pieces, Pose.ORIGIN, 2.0);
        GamePiece end = piece(pieces, "dropped");
        assertEquals(40.0, end.xInches(), 1e-9);
        assertEquals(1.4, end.zInches(), 1e-9);
    }

    /**
     * WHAT: Four POLLEN shot into the red upward CELL with its 3 NECTAR (223.5 g, over the 200 g
     * placeholder) tip the HIVE: it swings from +30 to -30 over 1 s, easing in and out; the CELL that went
     * down spills its 7 balls, which fall out and end loose on the tiles; the other CELL is up now, empty.
     */
    @Test
    void enoughWeightTipsTheHiveAndSpillsIt() {
        GamePieces pieces = matchAtTheRedHive();
        for (int shot = 1; shot <= 3; shot++) {
            fire(pieces, INTO_THE_CELL);
            assertEquals(30.0, tilt(pieces, "red-hive"), 1e-9, "not yet after shot " + shot);
        }
        assertEquals(6, pieces.ballsInCell("red-hive", 1).size());

        pieces.launcherBallSpeed(INTO_THE_CELL);
        pieces.feedPower(1.0);
        List<Double> tilts = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            pieces.step(AT_RED_HIVE, AT_RED_HIVE, SIZE, SIZE, LOOP, true);
            tilts.add(tilt(pieces, "red-hive"));
        }
        pieces.feedPower(0);

        int started = 0;
        while (tilts.get(started) == 30.0) {
            started++;
        }
        int ended = started;
        while (tilts.get(ended) != -30.0) {
            ended++;
        }
        assertEquals(1.0, (ended - started + 1) * LOOP, 2 * LOOP, "the swing takes 1 s");
        double quarter = tilts.get(started + 12);
        assertTrue(quarter > 18, "easing in: a quarter of the way through it has turned less than a quarter: " + quarter);
        assertEquals(-30.0, tilt(pieces, "red-hive"), 1e-9);
        assertEquals(1, tips(pieces, "red-hive"));
        assertEquals(0, tips(pieces, "blue-hive"));

        stay(pieces, AT_RED_HIVE, 3.0);
        assertEquals(List.of(), pieces.ballsInCell("red-hive", 1), "spilled");
        assertEquals(List.of(), pieces.ballsInCell("red-hive", -1), "the CELL that came up was empty");
        for (String id : List.of("red-nectar-cell-1", "red-nectar-cell-2", "red-nectar-cell-3", "pollen-preload-1",
                "pollen-preload-2", "pollen-preload-3", "pollen-preload-4")) {
            GamePiece p = piece(pieces, id);
            assertEquals(GamePiece.FIELD, p.state(), id);
            assertEquals(p.radiusInches(), p.zInches(), 1e-9, id + " is on the tiles");
        }
        assertEquals(-30.0, tilt(pieces, "blue-hive"), 1e-9, "the blue HIVE didn't move");
    }

    /** WHAT: The balls in a CELL swing with their HIVE while it tips: at the end they're where the CELL now is. */
    @Test
    void cellBallsSwingWithTheHive() {
        GamePieces pieces = matchAtTheRedHive();
        for (int shot = 1; shot <= 3; shot++) {
            fire(pieces, INTO_THE_CELL);
        }
        double before = piece(pieces, "red-nectar-cell-1").zInches();
        pieces.launcherBallSpeed(INTO_THE_CELL);
        pieces.feedPower(1.0);
        boolean sawItMove = false;
        for (int i = 0; i < 60 && !sawItMove; i++) {
            pieces.step(AT_RED_HIVE, AT_RED_HIVE, SIZE, SIZE, LOOP, true);
            GamePiece p = piece(pieces, "red-nectar-cell-1");
            sawItMove = p.state().equals(GamePiece.CELL) && p.zInches() < before - 1;
        }
        assertTrue(sawItMove, "a NECTAR went down with the CELL while still in it");
    }

    /**
     * WHAT: Without the HIVE given, a flying ball goes through where the HIVE would be, as before. (The robot is
     * out of the way: a flying ball bounces off it.)
     */
    @Test
    void noHiveChangesNothing() {
        GamePieces pieces = new GamePieces(List.of(dropped(0, 0, 55)), PieceModel.bioBuzz(), BioBuzzObstacles.all());
        stay(pieces, Pose.inches(0, -50, 0), 2.0);
        assertEquals(1.4, piece(pieces, "dropped").zInches(), 1e-9, "fell through the top bar");
        assertTrue(pieces.hiveTilts().isEmpty());
    }

    /** WHAT: The tip's numbers are marked as guesses (D26). */
    @Test
    void theTipsNumbersAreGuesses() {
        assertFalse(BioBuzzHive.TIP_MASS.measured());
        assertFalse(BioBuzzHive.TIP_SECONDS.measured());
        assertFalse(BioBuzzHive.SPILL_SPEED.measured());
    }

    /** WHAT: The same shots give exactly the same balls and tilts every time (02-unity-readiness §3). */
    @Test
    void theSameShotsGiveTheSameHive() {
        List<Object> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            GamePieces pieces = matchAtTheRedHive();
            for (int shot = 0; shot < 4; shot++) {
                fire(pieces, INTO_THE_CELL);
            }
            runs.add(List.of(pieces.snapshot(), pieces.hiveTilts()));
        }
        assertEquals(runs.get(0), runs.get(1));
    }
}
