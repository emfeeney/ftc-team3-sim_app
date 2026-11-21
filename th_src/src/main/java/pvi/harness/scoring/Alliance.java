package pvi.harness.scoring;

import java.util.Locale;

/**
 * The two alliances. Field parts that belong to one (a HIVE, a GARDEN, a LOADING ZONE) and NECTAR balls have one;
 * the simulated robot has one if its profile or its OpMode's name says so.
 */
public enum Alliance {
    RED,
    BLUE;

    /** "red" or "blue" in any case; null for anything else (including null). */
    public static Alliance parse(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "red" -> RED;
            case "blue" -> BLUE;
            default -> null;
        };
    }

    /**
     * The alliance an OpMode's name says: "Red" or "Blue" in it (with that capital letter, so "Bored" isn't red).
     * Null if the name has neither, or both.
     */
    public static Alliance fromName(String opModeName) {
        if (opModeName == null) {
            return null;
        }
        boolean red = opModeName.contains("Red");
        boolean blue = opModeName.contains("Blue");
        return red == blue ? null : red ? RED : BLUE;
    }

    /** "red" or "blue", as the field file and the recording write it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The alliance a field part belongs to from its id's start ("red-hive" is RED); null if neither. */
    static Alliance ofId(String id) {
        if (id == null) {
            return null;
        }
        return id.startsWith("red-") ? RED : id.startsWith("blue-") ? BLUE : null;
    }
}
