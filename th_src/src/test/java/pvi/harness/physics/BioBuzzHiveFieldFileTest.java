package pvi.harness.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import pvi.harness.runtime.JsonReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BioBuzzHive} against {@code fields/biobuzz-2026.json} (field format version 4), which VI Arena
 * draws: the same HIVEs and tip rule, the same CELLs ({@code hives[].cells}) and the same parts a flying
 * ball can hit ({@code flightObstacles}).
 */
class BioBuzzHiveFieldFileTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldFile() throws IOException {
        return (Map<String, Object>) JsonReader.parse(Files.readString(Path.of("fields", "biobuzz-2026.json")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertNotNull(value, key);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            items.add((Map<String, Object>) item);
        }
        return items;
    }

    private static Map<String, Object> byId(List<Map<String, Object>> items, String id) {
        return items.stream().filter(m -> id.equals(m.get("id"))).findFirst()
                .orElseThrow(() -> new AssertionError("no " + id + " in the field file"));
    }

    private static double number(Map<String, Object> m, String key) {
        Object value = m.get(key);
        assertNotNull(value, key + " in " + m.get("id"));
        return ((Number) value).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static double[] point(Map<String, Object> m, String key) {
        List<Object> xyz = (List<Object>) m.get(key);
        assertNotNull(xyz, key + " in " + m.get("id"));
        return new double[] {((Number) xyz.get(0)).doubleValue(), ((Number) xyz.get(1)).doubleValue(),
                ((Number) xyz.get(2)).doubleValue()};
    }

    /** WHAT: The file is field format version 4, the first with the CELLs and the flight obstacles. */
    @Test
    void theFieldFileIsVersion4OrLater() throws IOException {
        assertTrue(number(fieldFile(), "version") >= 4);
    }

    /** WHAT: Each HIVE's pivot, starting tilt and tip rule are the field file's. */
    @Test
    void theHivesMatchTheFieldFile() throws IOException {
        List<Map<String, Object>> hives = list(fieldFile(), "hives");
        assertEquals(BioBuzzHive.hives().size(), hives.size());
        for (BioBuzzHive.Hive hive : BioBuzzHive.hives()) {
            Map<String, Object> f = byId(hives, hive.id());
            assertEquals(number(f, "pivotXInches"), hive.pivotXInches(), 1e-9, hive.id());
            assertEquals(number(f, "pivotYInches"), hive.pivotYInches(), 1e-9, hive.id());
            assertEquals(number(f, "pivotZInches"), hive.pivotZInches(), 1e-9, hive.id());
            assertEquals(number(f, "startUpSide") * number(f, "tiltDegrees"), hive.startTiltDegrees(), 1e-9, hive.id());
            assertEquals(number(f, "tipMassGrams"), BioBuzzHive.TIP_MASS.value(), 1e-9, hive.id());
            assertEquals(number(f, "tipSeconds"), BioBuzzHive.TIP_SECONDS.value(), 1e-9, hive.id());
        }
    }

    /** WHAT: Each HIVE's two CELLs are the field file's: back wall, opening, floor (and its underside, D31), top and sides, one each side. */
    @Test
    void theCellsMatchTheFieldFile() throws IOException {
        for (BioBuzzHive.Hive hive : BioBuzzHive.hives()) {
            List<Map<String, Object>> cells = list(byId(list(fieldFile(), "hives"), hive.id()), "cells");
            assertEquals(List.of(1.0, -1.0), cells.stream().map(c -> number(c, "side")).toList(), hive.id());
            for (Map<String, Object> cell : cells) {
                String id = (String) cell.get("id");
                assertEquals(number(cell, "backInches"), BioBuzzHive.BACK_WALL_INCHES, 1e-9, id);
                assertEquals(number(cell, "openingInches"), BioBuzzHive.OPENING_INCHES, 1e-9, id);
                assertEquals(number(cell, "floorInches"), BioBuzzHive.FLOOR_INCHES, 1e-9, id);
                assertEquals(number(cell, "topInches"), BioBuzzHive.TOP_INCHES, 1e-9, id);
                assertEquals(number(cell, "floorUndersideInches"), BioBuzzHive.FLOOR_UNDERSIDE_INCHES, 1e-9, id);
                assertEquals(number(cell, "minYInches"), hive.pivotYInches() - BioBuzzHive.CELL_HALF_WIDTH_INCHES, 1e-9, id);
                assertEquals(number(cell, "maxYInches"), hive.pivotYInches() + BioBuzzHive.CELL_HALF_WIDTH_INCHES, 1e-9, id);
            }
        }
    }

    /** WHAT: The frame's legs, bases, top bar, axle blocks and logo panels are the field file's, and nothing else is. */
    @Test
    void theFrameMatchesTheFieldFile() throws IOException {
        List<Map<String, Object>> obstacles = list(fieldFile(), "flightObstacles");
        TreeSet<String> javaIds = new TreeSet<>();
        for (BioBuzzHive.Rod leg : BioBuzzHive.legs()) {
            javaIds.add(leg.id());
            Map<String, Object> f = byId(obstacles, leg.id());
            assertEquals("capsule", f.get("shape"), leg.id());
            double[] from = point(f, "fromInches");
            double[] to = point(f, "toInches");
            assertEquals(from[0], leg.bottomX(), 1e-9, leg.id());
            assertEquals(from[1], leg.bottomY(), 1e-9, leg.id());
            assertEquals(from[2], leg.bottomZ(), 1e-9, leg.id());
            assertEquals(to[0], leg.topX(), 1e-9, leg.id());
            assertEquals(to[1], leg.topY(), 1e-9, leg.id());
            assertEquals(to[2], leg.topZ(), 1e-9, leg.id());
            assertEquals(number(f, "radiusInches"), leg.radiusInches(), 1e-9, leg.id());
        }
        for (BioBuzzHive.Box box : BioBuzzHive.frame()) {
            javaIds.add(box.id());
            Map<String, Object> f = byId(obstacles, box.id());
            assertEquals("box", f.get("shape"), box.id());
            assertEquals(0.0, box.tiltDegrees(), 1e-9, box.id() + ": the file's boxes are square to the field");
            double[] min = point(f, "minInches");
            double[] max = point(f, "maxInches");
            assertEquals(min[0], box.xInches() - box.length() / 2, 0.01, box.id());
            assertEquals(max[0], box.xInches() + box.length() / 2, 0.01, box.id());
            assertEquals(min[1], box.yInches() - box.width() / 2, 0.01, box.id());
            assertEquals(max[1], box.yInches() + box.width() / 2, 0.01, box.id());
            assertEquals(min[2], box.zInches() - box.height() / 2, 0.01, box.id());
            assertEquals(max[2], box.zInches() + box.height() / 2, 0.01, box.id());
        }
        TreeSet<String> fileIds = new TreeSet<>();
        obstacles.stream().filter(o -> !"hiveBox".equals(o.get("shape"))).forEach(o -> fileIds.add((String) o.get("id")));
        assertEquals(fileIds, javaIds, "the same parts in both");
    }

    /**
     * WHAT: Each HIVE's arm is the field file's across it and along y. Along it, Java's arm runs from 3 in
     * to the CELL's back wall, inside the file's (to 21.56 in): past the back wall the CELL's floor stops a ball.
     */
    @Test
    void theArmsMatchTheFieldFile() throws IOException {
        List<Map<String, Object>> obstacles = list(fieldFile(), "flightObstacles");
        for (BioBuzzHive.Hive hive : BioBuzzHive.hives()) {
            Map<String, Object> f = byId(obstacles, hive.id() + "-arm");
            assertEquals("hiveBox", f.get("shape"));
            assertEquals(hive.id(), f.get("hive"));
            assertEquals(number(f, "minAcrossInches"), BioBuzzHive.ARM_CENTER_W_INCHES - BioBuzzHive.ARM_HALF_THICKNESS_INCHES, 1e-9);
            assertEquals(number(f, "maxAcrossInches"), BioBuzzHive.ARM_CENTER_W_INCHES + BioBuzzHive.ARM_HALF_THICKNESS_INCHES, 1e-9);
            assertEquals(number(f, "minYInches"), hive.pivotYInches() - BioBuzzHive.ARM_HALF_WIDTH_INCHES, 0.01);
            assertEquals(number(f, "maxYInches"), hive.pivotYInches() + BioBuzzHive.ARM_HALF_WIDTH_INCHES, 0.01);
            double javaEnd = BioBuzzHive.ARM_CENTER_INCHES + BioBuzzHive.ARM_HALF_LENGTH_INCHES;
            assertEquals(BioBuzzHive.BACK_WALL_INCHES, javaEnd, 1e-9);
            assertTrue(number(f, "maxAlongInches") >= javaEnd && number(f, "minAlongInches") <= -javaEnd);
        }
    }
}
