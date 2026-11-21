package pvi.harness.scoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pvi.harness.physics.Pose;

/**
 * The parts of a season's field that scoring needs, read from its field file ({@code fields/<id>.json}), so no
 * zone is typed into Java twice: each alliance's LOADING ZONE and GARDEN, the FLOWERs' scoring volume, and the HIVEs.
 *
 * <p><b>LOADING ZONE:</b> the manual's zone is bounded by tape and the field perimeter and includes the tape (TU06
 * 9.4). The field file gives the tape strips ({@code <alliance>-loading-zone-*} elements), so the zone is the box
 * around them, stretched out to the alliance's wall ({@code redWall} / {@code blueWall}).</p>
 *
 * <p><b>GARDEN:</b> the field file's {@code <alliance>-garden} element, a box along the wall.</p>
 *
 * <p><b>FLOWER scoring volume:</b> between the top and middle rings (TU06 10.5.2): each {@code flower-<name>} element's
 * {@code scoringBottomInches} (the middle ring's top), {@code scoringTopInches} (the top ring's bottom) and
 * {@code scoringRadiusInches} (the top ring's inside), measured from the CAD (manager decision D50). A FLOWER without
 * them scores nothing, and {@link #notes()} says so; nothing is guessed.</p>
 *
 * @param loadingZones each alliance's LOADING ZONE
 * @param gardens      each alliance's GARDEN
 * @param flowers      the FLOWERs
 * @param hives        the HIVEs
 * @param notes        what the field file lacks for scoring, e.g. a FLOWER without its scoring volume; empty if nothing
 */
public record ScoringField(Map<Alliance, Zone> loadingZones, Map<Alliance, Zone> gardens, List<Flower> flowers,
                           List<Hive> hives, List<String> notes) {

    /** The BIOBUZZ field's id. */
    public static final String BIOBUZZ = "biobuzz-2026";

    /** A box on the field, seen from above (infinitely tall), in inches, FTC field frame. */
    public record Zone(double minXInches, double maxXInches, double minYInches, double maxYInches) {
    }

    /**
     * One FLOWER's scoring volume: an upright cylinder between its middle and top rings.
     *
     * @param name         e.g. "far", from the element {@code flower-far}
     * @param radiusInches the volume's radius: a ball whose center is this close to the FLOWER's axis is in it
     * @param bottomInches the bottom of the scoring volume (the middle ring's top), above the tiles
     * @param topInches    its top (the top ring's bottom)
     */
    public record Flower(String name, double xInches, double yInches, double radiusInches,
                         double bottomInches, double topInches) {
    }

    /**
     * One HIVE: whose it is, where it swings, and which CELL is up at the start.
     *
     * @param id           e.g. "red-hive"
     * @param alliance     whose HIVE it is (from the id)
     * @param pivotXInches the pivot: the CELL on the +x side of it is the "+1" CELL
     * @param minYInches   the HIVE's band across the field, which its CELLs' balls sit in
     * @param maxYInches   the other side of the band
     * @param tiltDegrees  how far it leans in a stable position; a full tip swings it twice this
     * @param startUpSide  +1 if the +x CELL is up at the start, -1 if the -x one is
     */
    public record Hive(String id, Alliance alliance, double pivotXInches, double minYInches, double maxYInches,
                       double tiltDegrees, int startUpSide) {
    }

    /** Copies the maps and lists. */
    public ScoringField {
        loadingZones = Map.copyOf(loadingZones);
        gardens = Map.copyOf(gardens);
        flowers = List.copyOf(flowers);
        hives = List.copyOf(hives);
        notes = List.copyOf(notes);
    }

    /** A field with nothing to score (for a season with no field file, or a field without these parts). */
    public static ScoringField none() {
        return new ScoringField(Map.of(), Map.of(), List.of(), List.of(), List.of("no field to score on"));
    }

    /** The BIOBUZZ field, from {@code fields/biobuzz-2026.json}. */
    public static ScoringField bioBuzz() {
        return forField(BIOBUZZ);
    }

    /**
     * A season's field by its id (the file name without {@code .json}), looked for in {@code fields/} and then
     * {@code fields/disabled/} (relative to the Harness folder), the same as the AprilTags.
     */
    public static ScoringField forField(String fieldId) {
        for (Path file : new Path[] {Path.of("fields", fieldId + ".json"), Path.of("fields", "disabled", fieldId + ".json")}) {
            if (Files.isRegularFile(file)) {
                return read(file);
            }
        }
        throw new IllegalArgumentException("No field file for \"" + fieldId + "\" in fields/ or fields/disabled/.");
    }

    /** The scoring parts of one field file. Parts the file doesn't have are left out. */
    public static ScoringField read(Path fieldFile) {
        try {
            JSONObject field = new JSONObject(Files.readString(fieldFile, StandardCharsets.UTF_8));
            Map<Alliance, Zone> loadingZones = new EnumMap<>(Alliance.class);
            Map<Alliance, Zone> gardens = new EnumMap<>(Alliance.class);
            JSONArray elements = field.optJSONArray("elements");
            for (Alliance alliance : Alliance.values()) {
                Zone tape = null;
                for (int i = 0; elements != null && i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);
                    String id = element.getString("id");
                    if (id.startsWith(alliance.key() + "-loading-zone-")) {
                        tape = around(tape, box(element));
                    } else if (id.equals(alliance.key() + "-garden")) {
                        gardens.put(alliance, box(element));
                    }
                }
                if (tape != null) {
                    loadingZones.put(alliance, outToWall(tape, field.optString(alliance.key() + "Wall", "")));
                }
            }
            List<Flower> flowers = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (int i = 0; elements != null && i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                String id = element.getString("id");
                if (!id.startsWith("flower-")) {
                    continue;
                }
                if (element.has("scoringBottomInches") && element.has("scoringTopInches")
                        && element.has("scoringRadiusInches")) {
                    flowers.add(new Flower(id.substring("flower-".length()), element.getDouble("xInches"),
                            element.getDouble("yInches"), element.getDouble("scoringRadiusInches"),
                            element.getDouble("scoringBottomInches"), element.getDouble("scoringTopInches")));
                } else {
                    notes.add(id + " has no scoringBottomInches / scoringTopInches / scoringRadiusInches in the field file,"
                            + " so it scores no FLOWER points");
                }
            }
            List<Hive> hives = new ArrayList<>();
            JSONArray hiveItems = field.optJSONArray("hives");
            for (int i = 0; hiveItems != null && i < hiveItems.length(); i++) {
                JSONObject h = hiveItems.getJSONObject(i);
                String id = h.getString("id");
                Alliance alliance = Alliance.ofId(id);
                if (alliance == null) {
                    throw new IllegalArgumentException("HIVE \"" + id + "\" doesn't start with red- or blue-");
                }
                hives.add(new Hive(id, alliance, h.getDouble("pivotXInches"), h.getDouble("minYInches"),
                        h.getDouble("maxYInches"), h.getDouble("tiltDegrees"), h.getInt("startUpSide")));
            }
            return new ScoringField(loadingZones, gardens, flowers, hives, notes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the field file " + fieldFile.toAbsolutePath(), e);
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("The field file " + fieldFile + " has a bad scoring part: " + e.getMessage(), e);
        }
    }

    /** A field-file box element (center, lengthInches along x, widthInches along y) seen from above. */
    private static Zone box(JSONObject element) throws JSONException {
        double x = element.getDouble("xInches");
        double y = element.getDouble("yInches");
        double halfLength = element.getDouble("lengthInches") / 2;
        double halfWidth = element.getDouble("widthInches") / 2;
        return new Zone(x - halfLength, x + halfLength, y - halfWidth, y + halfWidth);
    }

    /** The smallest box around both (the first may be null). */
    private static Zone around(Zone a, Zone b) {
        if (a == null) {
            return b;
        }
        return new Zone(Math.min(a.minXInches(), b.minXInches()), Math.max(a.maxXInches(), b.maxXInches()),
                Math.min(a.minYInches(), b.minYInches()), Math.max(a.maxYInches(), b.maxYInches()));
    }

    /** The zone stretched out to the field edge on the wall's side ("-y", "+y", "-x" or "+x"). */
    private static Zone outToWall(Zone zone, String wall) {
        double edge = Pose.FIELD_HALF_WIDTH_INCHES;
        return switch (wall) {
            case "-y" -> new Zone(zone.minXInches(), zone.maxXInches(), -edge, zone.maxYInches());
            case "+y" -> new Zone(zone.minXInches(), zone.maxXInches(), zone.minYInches(), edge);
            case "-x" -> new Zone(-edge, zone.maxXInches(), zone.minYInches(), zone.maxYInches());
            case "+x" -> new Zone(zone.minXInches(), edge, zone.minYInches(), zone.maxYInches());
            default -> throw new IllegalArgumentException("an alliance wall must be -x, +x, -y or +y, not \"" + wall + "\"");
        };
    }
}
