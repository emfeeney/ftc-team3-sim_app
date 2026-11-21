package pvi.harness.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.PieceModel;
import pvi.harness.physics.PieceMotion;

/**
 * A plain stand-in for Unity's ball physics in outside-physics tests (W-UP1): it moves every ball Java says
 * is loose, flying or in a CELL, the same way every time, so a run through the bridge and one straight through
 * a runner can be compared. A launched ball flies under gravity, from Java's start and speed, through
 * everything; a ball Java says is in a CELL rests where it is (as on the CELL's floor); one that reaches the
 * tiles stops there. It is nothing like PhysX: it only feeds Java's rules the same positions both ways.
 */
final class StandInBallPhysics {
    private static final double LOOP_SECONDS = 0.02;

    /** One ball this stand-in moves: where it is and how fast it moves. */
    private static final class Ball {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final double radius;

        Ball(double x, double y, double z, double radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    private final Map<String, Ball> balls = new LinkedHashMap<>();

    /**
     * One loop: takes Java's latest word on every ball ({@code java}, the states and places it reported) and the
     * balls it just started moving ({@code launches}), moves its balls on by one loop, and returns where they are
     * now, to supply before the next step.
     */
    List<PieceMotion> advance(List<GamePiece> java, List<PieceMotion> launches) {
        for (GamePiece p : java) {
            boolean ours = p.state().equals(GamePiece.FIELD) || p.state().equals(GamePiece.FLYING) || p.state().equals(GamePiece.CELL);
            if (!ours) {
                balls.remove(p.id());
            } else if (!balls.containsKey(p.id())) {
                balls.put(p.id(), new Ball(p.xInches(), p.yInches(), p.zInches(), p.radiusInches()));
            }
        }
        for (PieceMotion m : launches) {
            Ball b = balls.get(m.id());
            if (b != null) {
                b.x = m.xInches();
                b.y = m.yInches();
                b.z = m.zInches();
                b.vx = m.vxInchesPerSecond();
                b.vy = m.vyInchesPerSecond();
                b.vz = m.vzInchesPerSecond();
            }
        }
        Map<String, String> states = new LinkedHashMap<>();
        java.forEach(p -> states.put(p.id(), p.state()));
        List<PieceMotion> now = new ArrayList<>();
        for (Map.Entry<String, Ball> e : balls.entrySet()) {
            Ball b = e.getValue();
            if (GamePiece.CELL.equals(states.get(e.getKey()))) {
                b.vx = 0;
                b.vy = 0;
                b.vz = 0;
            } else if (b.z > b.radius || b.vz > 0) {
                b.vz -= PieceModel.GRAVITY_INCHES_PER_SECOND_SQUARED * LOOP_SECONDS;
                b.x += b.vx * LOOP_SECONDS;
                b.y += b.vy * LOOP_SECONDS;
                b.z += b.vz * LOOP_SECONDS;
                if (b.z <= b.radius) {
                    b.z = b.radius;
                    b.vx = 0;
                    b.vy = 0;
                    b.vz = 0;
                }
            } else {
                // Rolling isn't part of this stand-in: a ball on the tiles stays put.
                b.vx = 0;
                b.vy = 0;
                b.vz = 0;
            }
            now.add(new PieceMotion(e.getKey(), b.x, b.y, b.z, b.vx, b.vy, b.vz));
        }
        return now;
    }

    /** The motions as the bridge's {@code step.pieces} JSON array. */
    static String json(List<PieceMotion> motions) {
        List<String> items = new ArrayList<>();
        for (PieceMotion m : motions) {
            items.add(String.format(Locale.ROOT, "{\"id\": \"%s\", \"xInches\": %s, \"yInches\": %s, \"zInches\": %s,"
                            + " \"vxInchesPerSecond\": %s, \"vyInchesPerSecond\": %s, \"vzInchesPerSecond\": %s}", m.id(),
                    m.xInches(), m.yInches(), m.zInches(), m.vxInchesPerSecond(), m.vyInchesPerSecond(), m.vzInchesPerSecond()));
        }
        return "[" + String.join(", ", items) + "]";
    }

    /** A frame's {@code pieceLaunches} as motions. */
    @SuppressWarnings("unchecked")
    static List<PieceMotion> launchesIn(Map<String, Object> frame) {
        List<PieceMotion> launches = new ArrayList<>();
        Object list = frame.get("pieceLaunches");
        if (list == null) {
            throw new AssertionError("the frame has no pieceLaunches: " + frame.keySet());
        }
        for (Map<String, Object> m : (List<Map<String, Object>>) list) {
            launches.add(new PieceMotion((String) m.get("id"), number(m, "xInches"), number(m, "yInches"), number(m, "zInches"),
                    number(m, "vxInchesPerSecond"), number(m, "vyInchesPerSecond"), number(m, "vzInchesPerSecond")));
        }
        return launches;
    }

    /** Every ball as the bridge has reported it so far ({@code id -> its last JSON object}), as pieces. */
    static List<GamePiece> pieces(Map<String, Map<String, Object>> reported) {
        List<GamePiece> pieces = new ArrayList<>();
        for (Map<String, Object> p : reported.values()) {
            pieces.add(new GamePiece((String) p.get("id"), (String) p.get("kind"), (String) p.get("state"), number(p, "xInches"),
                    number(p, "yInches"), number(p, "zInches")));
        }
        return pieces;
    }

    private static double number(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }
}
