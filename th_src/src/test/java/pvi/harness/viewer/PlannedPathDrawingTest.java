package pvi.harness.viewer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import pvi.harness.physics.Pose;
import pvi.harness.runtime.PlannedPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D39: the viewer draws a Pedro robot's planned path as a yellow line, and nothing without one. */
class PlannedPathDrawingTest {
    /** 20 pixels per inch. */
    private final FieldCoordinates coords = new FieldCoordinates(2880, 30);

    /** How many pixels along y = 40 in, from x = -40 to 40 in, are the planned path's yellow. */
    private int yellowPixelsAlongTheLine(List<PlannedPath.Point> line) {
        BufferedImage image = new BufferedImage(coords.totalPixels(), coords.totalPixels(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        FieldPainter.paint(g, coords, FieldPainter.RobotBox.UNKNOWN, Pose.ORIGIN, List.of(), null, null, null,
                List.of(), List.of(), line);
        g.dispose();
        int y = (int) Math.floor(coords.screenY(40));
        int yellow = 0;
        for (int x = (int) coords.screenX(-40); x <= (int) coords.screenX(40); x++) {
            if ((image.getRGB(x, y) & 0xFFFFFF) == (FieldPainter.PLANNED_PATH.getRGB() & 0xFFFFFF)) {
                yellow++;
            }
        }
        return yellow;
    }

    /** WHAT: A planned path is drawn in yellow along its points (dashed, so in pieces). */
    @Test
    void aPlannedPathIsDrawnInYellow() {
        int yellow = yellowPixelsAlongTheLine(List.of(new PlannedPath.Point(-40, 40), new PlannedPath.Point(40, 40)));

        assertTrue(yellow > 400, yellow + " of the 1600 pixels along the line are yellow");
    }

    /** WHAT: The legend says what the yellow line is. */
    @Test
    void theLegendNamesThePlannedPath() {
        assertTrue(FieldPainter.legendText(FieldPainter.RobotBox.UNKNOWN).contains("yellow = Pedro's planned path"));
    }

    /** WHAT: With no plan, nothing yellow is drawn there. */
    @Test
    void noPlanDrawsNothing() {
        assertEquals(0, yellowPixelsAlongTheLine(List.of()));
    }
}
