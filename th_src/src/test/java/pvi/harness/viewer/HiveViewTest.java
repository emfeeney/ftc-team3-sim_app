package pvi.harness.viewer;

import java.util.List;

import org.junit.jupiter.api.Test;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the viewer works out about the HIVEs from a recording's {@code hives} and {@code pieces}: which
 * CELL is up, where each CELL is seen from above, and how many balls are in each. No window needed.
 */
class HiveViewTest {
    private static final List<HiveTilt> AT_THE_START = List.of(new HiveTilt("red-hive", 0, 0), new HiveTilt("blue-hive", 0, 0));

    private static HiveView.Cell cell(List<HiveView.Cell> cells, String hive, int side) {
        return cells.stream().filter(c -> c.hiveId().equals(hive) && c.side() == side).findFirst().orElseThrow();
    }

    /**
     * WHAT: At the start of a match the red HIVE's +x CELL is up and the blue HIVE's -x CELL is; the other
     * two are down, and neither HIVE is tipping.
     */
    @Test
    void atTheStartRedsPlusXAndBluesMinusXCellsAreUp() {
        List<HiveView.Cell> cells = HiveView.cells(AT_THE_START, List.of());

        assertEquals(4, cells.size());
        assertTrue(cell(cells, "red-hive", 1).up());
        assertFalse(cell(cells, "red-hive", -1).up());
        assertTrue(cell(cells, "blue-hive", -1).up());
        assertFalse(cell(cells, "blue-hive", 1).up());
        assertTrue(cells.stream().noneMatch(HiveView.Cell::tipping));
    }

    /** WHAT: Halfway through a tip neither of that HIVE's CELLs is up; after it, the other one is. */
    @Test
    void aTipMovesWhichCellIsUp() {
        List<HiveView.Cell> halfway = HiveView.cells(List.of(new HiveTilt("red-hive", 1, -30)), List.of());
        assertTrue(cell(halfway, "red-hive", 1).tipping());
        assertFalse(cell(halfway, "red-hive", 1).up());
        assertFalse(cell(halfway, "red-hive", -1).up());

        List<HiveView.Cell> after = HiveView.cells(List.of(new HiveTilt("red-hive", 1, -60)), List.of());
        assertFalse(cell(after, "red-hive", 1).up());
        assertTrue(cell(after, "red-hive", -1).up());
        assertFalse(cell(after, "red-hive", -1).tipping());
    }

    /**
     * WHAT: Seen from above, the red +x CELL at the start runs from x = 1.91 to 19.32 in: its side view
     * (back wall 9.44 in to opening 21.46 in along the arm, floor -1.47 to top 12.53 in square to it)
     * turned 30 degrees. Across, it is the HIVE's y plus or minus 10.08 in.
     */
    @Test
    void aCellSeenFromAbove() {
        HiveView.Cell up = cell(HiveView.cells(AT_THE_START, List.of()), "red-hive", 1);

        assertEquals(9.44 * Math.cos(Math.PI / 6) - 12.53 * 0.5, up.minX(), 0.01);
        assertEquals(21.46 * Math.cos(Math.PI / 6) + 1.47 * 0.5, up.maxX(), 0.01);
        assertEquals(-12.75 - 10.08, up.minY(), 1e-9);
        assertEquals(-12.75 + 10.08, up.maxY(), 1e-9);
        HiveView.Cell down = cell(HiveView.cells(AT_THE_START, List.of()), "red-hive", -1);
        assertTrue(down.maxX() < 0, "the -x CELL is on the -x side: " + down);
    }

    /**
     * WHAT: Each CELL counts its NECTAR and POLLEN: only balls in state "cell", on that side of that HIVE.
     * A loose ball under a CELL doesn't count.
     */
    @Test
    void eachCellCountsItsBalls() {
        List<GamePiece> pieces = List.of(
                new GamePiece("n1", GamePiece.RED_NECTAR, GamePiece.CELL, 12, -12.75, 50),
                new GamePiece("n2", GamePiece.RED_NECTAR, GamePiece.CELL, 14, -18, 50),
                new GamePiece("p1", GamePiece.POLLEN, GamePiece.CELL, 16, -8, 51),
                new GamePiece("p2", GamePiece.POLLEN, GamePiece.CELL, -14, 12.75, 50),
                new GamePiece("p3", GamePiece.POLLEN, GamePiece.CELL, -12, -12.75, 30),
                new GamePiece("loose", GamePiece.POLLEN, GamePiece.FIELD, 12, -12.75, 1.4));

        List<HiveView.Cell> cells = HiveView.cells(AT_THE_START, pieces);

        assertEquals(2, cell(cells, "red-hive", 1).nectar());
        assertEquals(1, cell(cells, "red-hive", 1).pollen());
        assertEquals(1, cell(cells, "red-hive", -1).pollen());
        assertEquals(0, cell(cells, "red-hive", -1).nectar());
        assertEquals(1, cell(cells, "blue-hive", -1).pollen());
        assertEquals(0, cell(cells, "blue-hive", 1).pollen() + cell(cells, "blue-hive", 1).nectar());
    }

    /** WHAT: The side panel's lines: one per CELL, then how many times each HIVE has tipped. */
    @Test
    void theSidePanelLines() {
        List<HiveTilt> hives = List.of(new HiveTilt("red-hive", 1, -30));
        List<GamePiece> pieces = List.of(new GamePiece("n1", GamePiece.RED_NECTAR, GamePiece.CELL, 12, -12.75, 50));

        List<String> lines = HiveView.lines(hives, HiveView.cells(hives, pieces));

        assertEquals(List.of(
                "red-hive  +x  TIPPING  1 NECTAR  0 POLLEN",
                "red-hive  -x  TIPPING  0 NECTAR  0 POLLEN",
                "red-hive  tipped 1 time"), lines);
        assertEquals("red-hive  +x  UP       0 NECTAR  0 POLLEN",
                HiveView.lines(AT_THE_START, HiveView.cells(AT_THE_START, List.of())).get(0));
    }

    /** WHAT: An older recording, without HIVEs, has no CELLs to show. */
    @Test
    void noHivesNoCells() {
        assertEquals(List.of(), HiveView.cells(List.of(), List.of()));
    }
}
