package pvi.harness.physics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balls in a CELL roll down its tilted floor to the back wall (W-PG item 2). The upward CELL of a HIVE tilted
 * 30 degrees slopes down toward the pivot, so a ball that lands near the opening rolls back until it reaches
 * the back wall, or a ball already resting there.
 *
 * <p>Each test puts balls straight into a CELL (state {@link GamePiece#CELL}), on its floor, at a place
 * "along" the arm from the pivot and "across" it from the pivot's y, then lets time pass with the robot far
 * away.</p>
 */
class CellRollTest {
    private static final double LOOP = 0.02;
    private static final double SIZE = 18;
    /** Out of everything's way. */
    private static final Pose FAR_AWAY = Pose.inches(-60, -60, 0);
    private static final double PIVOT_Z = 43.95;
    private static final double RED_Y = -12.75;
    private static final double BLUE_Y = 12.75;
    private static final double POLLEN_RADIUS = GamePiece.radiusInches(GamePiece.POLLEN);

    /**
     * A POLLEN on the floor of a CELL. {@code levelAlong} is its level x: positive for the CELL on the field's
     * +x side (red's upward CELL), negative for the -x side (blue's).
     */
    private static GamePiece inCell(String id, double tiltDegrees, double pivotY, double levelAlong, double across) {
        double tilt = Math.toRadians(tiltDegrees);
        double square = BioBuzzHive.FLOOR_INCHES + POLLEN_RADIUS;
        return new GamePiece(id, GamePiece.POLLEN, GamePiece.CELL,
                levelAlong * Math.cos(tilt) - square * Math.sin(tilt), pivotY + across,
                PIVOT_Z + levelAlong * Math.sin(tilt) + square * Math.cos(tilt));
    }

    private static GamePieces field(List<GamePiece> start) {
        return new GamePieces(start, PieceModel.bioBuzz(), BioBuzzObstacles.all(), BioBuzzPieces.flowers(),
                BioBuzzHive.hives(), BioBuzzHive.frame());
    }

    private static void stay(GamePieces pieces, double seconds) {
        for (double t = 0; t < seconds - 1e-9; t += LOOP) {
            pieces.step(FAR_AWAY, FAR_AWAY, SIZE, SIZE, LOOP, true);
        }
    }

    private static GamePiece piece(GamePieces pieces, String id) {
        return pieces.snapshot().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    /** A piece's level x (along the arm) in a HIVE tilted this much. */
    private static double levelAlong(GamePiece p, double tiltDegrees) {
        double tilt = Math.toRadians(tiltDegrees);
        return p.xInches() * Math.cos(tilt) + (p.zInches() - PIVOT_Z) * Math.sin(tilt);
    }

    /** A piece's level z (square to the arm) in a HIVE tilted this much. */
    private static double levelSquare(GamePiece p, double tiltDegrees) {
        double tilt = Math.toRadians(tiltDegrees);
        return -p.xInches() * Math.sin(tilt) + (p.zInches() - PIVOT_Z) * Math.cos(tilt);
    }

    /** The 3 NECTAR staged in the red upward CELL, as at the start of a match. */
    private static List<GamePiece> redStagedNectar() {
        return new ArrayList<>(BioBuzzPieces.start(Pose.ORIGIN, SIZE).stream()
                .filter(p -> p.id().startsWith("red-nectar-cell")).toList());
    }

    /** WHAT: A ball near the opening of the red upward CELL rolls down to its back wall and stays in the CELL, on its floor. */
    @Test
    void aBallRollsDownToTheBackWall() {
        GamePieces pieces = field(List.of(inCell("pollen", 30, RED_Y, 19, 0)));

        stay(pieces, 1.0);

        GamePiece p = piece(pieces, "pollen");
        assertEquals(GamePiece.CELL, p.state());
        assertEquals(BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS, levelAlong(p, 30), 1e-6, "against the back wall: " + p);
        assertEquals(BioBuzzHive.FLOOR_INCHES + POLLEN_RADIUS, levelSquare(p, 30), 1e-6, "on the floor: " + p);
        assertEquals(RED_Y, p.yInches(), 1e-9, "it didn't move across the CELL");
        assertEquals(List.of("pollen"), pieces.ballsInCell("red-hive", 1));
    }

    /**
     * WHAT: It speeds up with gravity along the floor: after 0.2 s it has rolled about half of
     * g x sin 30 x t squared (3.9 in), within the steps' rounding.
     */
    @Test
    void itSpeedsUpWithGravityAlongTheFloor() {
        GamePieces pieces = field(List.of(inCell("pollen", 30, RED_Y, 20, 0)));

        stay(pieces, 0.2);

        double rolled = 20 - levelAlong(piece(pieces, "pollen"), 30);
        double expected = 0.5 * PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED * Math.sin(Math.toRadians(30)) * 0.2 * 0.2;
        assertEquals(expected, rolled, 0.15 * expected, "rolled " + rolled + " in");
    }

    /**
     * WHAT: A ball rolling down in line with staged NECTAR stops against the last of them, touching it. (The 3
     * staged NECTAR fill their line to the opening, so this CELL has only the first 2.)
     */
    @Test
    void itStopsAgainstABallAlreadyThere() {
        List<GamePiece> start = new ArrayList<>(redStagedNectar().subList(0, 2));
        GamePiece last = start.get(1);
        double across = last.yInches() - RED_Y;
        start.add(inCell("pollen", 30, RED_Y, 19.5, across));
        GamePieces pieces = field(start);

        stay(pieces, 1.0);

        double touching = GamePiece.radiusInches(GamePiece.RED_NECTAR) + POLLEN_RADIUS;
        assertEquals(levelAlong(last, 30) + touching, levelAlong(piece(pieces, "pollen"), 30), 1e-6, "touching the second NECTAR");
        assertEquals(3, pieces.ballsInCell("red-hive", 1).size());
    }

    /** WHAT: A ball beside the NECTAR's line, clear of them across the CELL, rolls past them to the back wall. */
    @Test
    void aBallBesideTheLineRollsPastItToTheBackWall() {
        List<GamePiece> start = redStagedNectar();
        start.add(inCell("pollen", 30, RED_Y, 20, 0));
        GamePieces pieces = field(start);

        stay(pieces, 1.0);

        assertEquals(BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS, levelAlong(piece(pieces, "pollen"), 30), 1e-6);
    }

    /** WHAT: The blue HIVE is the mirror image: its upward CELL is on the -x side and a ball there rolls to its back wall. */
    @Test
    void theBlueUpwardCellWorksTheSame() {
        GamePieces pieces = field(List.of(inCell("pollen", -30, BLUE_Y, -19, 0)));

        stay(pieces, 1.0);

        GamePiece p = piece(pieces, "pollen");
        assertEquals(GamePiece.CELL, p.state());
        assertEquals(-(BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS), levelAlong(p, -30), 1e-6, "against the back wall: " + p);
        assertEquals(List.of("pollen"), pieces.ballsInCell("blue-hive", -1));
    }

    /**
     * WHAT: Nothing rolls while a HIVE tips: 4 POLLEN with the 3 staged NECTAR (223.5 g) start a tip at once, and
     * half way through the swing a POLLEN placed near the opening is still where it was in the CELL.
     */
    @Test
    void ballsDontRollWhileTheHiveTips() {
        List<GamePiece> start = redStagedNectar();
        start.add(inCell("pollen-1", 30, RED_Y, 12, 4));
        start.add(inCell("pollen-2", 30, RED_Y, 15, 4));
        start.add(inCell("pollen-3", 30, RED_Y, 18, 4));
        start.add(inCell("pollen-4", 30, RED_Y, 12, 0));
        GamePieces pieces = field(start);

        stay(pieces, 0.5);

        double tilt = 30 + pieces.hiveTilts().stream().filter(t -> t.id().equals("red-hive")).findFirst().orElseThrow()
                .swingDegrees();
        assertTrue(tilt < 25 && tilt > -25, "half way through the tip: " + tilt);
        GamePiece p = piece(pieces, "pollen-3");
        assertEquals(GamePiece.CELL, p.state());
        assertEquals(18, levelAlong(p, tilt), 1e-6, "it swung with the CELL without rolling: " + p);
    }

    /** A piece's place across the CELL, from the pivot's y. */
    private static double across(GamePiece p, double pivotY) {
        return p.yInches() - pivotY;
    }

    /** Distance between two balls on a CELL's floor, along the arm and across the CELL. */
    private static double apartOnTheFloor(GamePiece a, GamePiece b, double tiltDegrees, double pivotY) {
        return Math.hypot(levelAlong(a, tiltDegrees) - levelAlong(b, tiltDegrees), across(a, pivotY) - across(b, pivotY));
    }

    /**
     * WHAT: A ball that landed overlapping one already at the back wall is moved across the CELL until it's clear of
     * it, then rolls down beside it to the back wall, just touching it.
     */
    @Test
    void anOverlappingBallIsMovedAsideAndRollsDownBesideIt() {
        GamePieces pieces = field(List.of(inCell("first", 30, RED_Y, BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS, 0),
                inCell("second", 30, RED_Y, 12, 0.5)));

        stay(pieces, 1.0);

        GamePiece first = piece(pieces, "first");
        GamePiece second = piece(pieces, "second");
        assertEquals(2 * POLLEN_RADIUS, apartOnTheFloor(first, second, 30, RED_Y), 1e-6, "just touching");
        assertEquals(BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS, levelAlong(second, 30), 1e-6, "beside it at the back wall");
        assertEquals(2 * POLLEN_RADIUS, across(second, RED_Y) - across(first, RED_Y), 1e-6, "on the side it was already on");
        assertEquals(0, across(first, RED_Y), 1e-9, "the one already there didn't move");
    }

    /**
     * WHAT: Moving aside never goes through the CELL's side wall: with no room beside, the overlapping ball is moved out
     * along the arm instead, and stays inside the CELL.
     */
    @Test
    void anOverlappingBallNeverGoesThroughTheSideWall() {
        double wall = BioBuzzHive.CELL_HALF_WIDTH_INCHES - POLLEN_RADIUS;
        GamePieces pieces = field(List.of(inCell("first", 30, RED_Y, BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS, wall - 1.2),
                inCell("second", 30, RED_Y, BioBuzzHive.BACK_WALL_INCHES + POLLEN_RADIUS + 0.5, wall)));

        stay(pieces, 1.0);

        GamePiece first = piece(pieces, "first");
        GamePiece second = piece(pieces, "second");
        assertTrue(Math.abs(across(second, RED_Y)) <= wall + 1e-9, "inside the CELL: " + across(second, RED_Y));
        assertEquals(2 * POLLEN_RADIUS, apartOnTheFloor(first, second, 30, RED_Y), 1e-6, "just touching");
        assertEquals(GamePiece.CELL, second.state());
    }

    /** WHAT: Rolling down doesn't change which CELL a ball counts in, or tip a HIVE: the staged NECTAR and one POLLEN stay at 30 degrees. */
    @Test
    void rollingDoesntTipTheHive() {
        List<GamePiece> start = redStagedNectar();
        start.add(inCell("pollen", 30, RED_Y, 20, 0));
        GamePieces pieces = field(start);

        stay(pieces, 1.0);

        assertTrue(pieces.hiveTilts().stream().allMatch(t -> t.tips() == 0 && t.swingDegrees() == 0), "no tip");
    }
}
