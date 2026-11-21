package pvi.harness.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import pvi.harness.physics.Pose;

/**
 * Finds the Pedro Pathing {@code Follower} that a robot's code made, and reads the path it is about
 * to follow (D39), so a run can record it and the displays can draw it as a yellow line.
 *
 * <p><b>How it finds the follower:</b> robot code keeps its follower in a field somewhere, often
 * deep inside (the OpMode holds a robot, the robot a drive subsystem, the subsystem a controller,
 * the controller the follower). So the watcher looks through the OpMode's fields, and the fields of
 * what they hold, until it finds an object whose class is Pedro's {@code Follower}. It works for
 * any team's code without changes, because it goes by Pedro's class name, not by the team's names.
 * It never looks inside the FTC SDK, Java's own classes or the harness, and it gives up after
 * {@link #MAX_DEPTH} steps or {@link #MAX_OBJECTS} objects, so it stays quick. Until a follower is
 * found, it looks again on every call (code often makes its follower in INIT or at PLAY).</p>
 *
 * <p><b>Why reflection:</b> the harness core must build and run for teams that don't use Pedro at
 * all, so this class can't name any Pedro class in its code. It reads Pedro 3's public methods
 * ({@code mode()}, {@code currentPath()}, {@code pathIndex()}, {@code Path.getSegments()},
 * {@code PathSegment.curve}, {@code Curve.get(t)}) by name instead. A robot without Pedro, or with
 * an older Pedro that lacks these methods, simply has no planned path.</p>
 *
 * <p>It only reads. It never changes the follower or the robot code, so a run gives the same result
 * with or without it.</p>
 */
public final class PedroPathWatcher {
    /** Pedro's follower class, found by name. */
    static final String FOLLOWER_CLASS = "com.pedropathing.follower.Follower";
    /** Pedro's curve interface, whose {@code get(t)} gives a point of a segment. */
    static final String CURVE_CLASS = "com.pedropathing.paths.curves.Curve";
    /** Planned path points are at most this far apart, in inches. */
    public static final double SAMPLE_SPACING_INCHES = 2.0;
    /** At most this many points per path segment, however long it is. */
    static final int MAX_POINTS_PER_SEGMENT = 200;
    /** How many fields deep the search for the follower goes from the OpMode. */
    static final int MAX_DEPTH = 8;
    /** How many objects the search looks at, at most, each time. */
    static final int MAX_OBJECTS = 5000;

    /** Packages whose objects are never looked inside: Java, Android, the FTC SDK, the harness. */
    private static final List<String> SKIPPED_PACKAGES = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
            "kotlin.", "android.", "androidx.", "com.qualcomm.", "org.firstinspires.ftc.robotcore.",
            "org.firstinspires.ftc.vision.", "org.openftc.", "com.google.", "pvi.harness.", "com.pedropathing.");

    /** Where the search starts: the OpMode. */
    private final Object root;
    /** The follower once found, else null. */
    private Object follower;
    /** What the last plan was made from: path object, segment index and whether it was following. */
    private Object lastPath;
    private int lastIndex = Integer.MIN_VALUE;
    private boolean lastFollowing;
    /** The points last reported, so the same plan (for example "none") is never reported twice in a row. */
    private List<PlannedPath.Point> lastPoints;
    /** True until the first call, so the first call always reports a plan (possibly empty). */
    private boolean first = true;

    /** Watches the follower that {@code opMode} (or anything it holds) makes. */
    public PedroPathWatcher(Object opMode) {
        if (opMode == null) {
            throw new IllegalArgumentException("PedroPathWatcher needs the OpMode to look in.");
        }
        this.root = opMode;
    }

    /** True once a Pedro follower has been found. */
    public boolean foundFollower() {
        return follower != null;
    }

    /**
     * Like {@link #poll(Pose)}, placing the plan where Pedro's coordinates say, as if Pedro's frame were
     * always fixed to the field ({@link Pose#fromPedro}).
     */
    public Optional<List<PlannedPath.Point>> poll() {
        return poll(null);
    }

    /**
     * The plan's points if the plan changed since the last call (or on the first call), else empty.
     * An empty list inside means "no plan now". Points are in the FTC field frame.
     *
     * <p><b>Placing the plan on the field:</b> robot code tells Pedro where the robot starts, and that
     * isn't always where the harness put the robot (LP-11 says it starts at Pedro's (0, 0), a corner,
     * while the harness starts it at the field's center). So when {@code robotPose} (where the robot
     * really is) is given, the plan is moved and turned by the difference between it and where the
     * follower thinks the robot is. The line then shows where the robot will really go.</p>
     *
     * @param robotPose where the robot is on the field now, or null to use Pedro's coordinates as they are
     */
    public Optional<List<PlannedPath.Point>> poll(Pose robotPose) {
        if (follower == null) {
            follower = findFollower(root);
        }
        Object path = null;
        int index = -1;
        boolean following = false;
        if (follower != null) {
            following = "FOLLOW".equals(String.valueOf(call(follower, "mode")));
            path = following ? call(follower, "currentPath") : null;
            Object indexValue = path == null ? null : call(follower, "pathIndex");
            index = indexValue instanceof Integer i ? i : -1;
            following = path != null && index >= 0;
        }
        if (!first && path == lastPath && index == lastIndex && following == lastFollowing) {
            return Optional.empty();
        }
        first = false;
        lastPath = path;
        lastIndex = index;
        lastFollowing = following;
        List<PlannedPath.Point> points = following ? placed(sample(path, index), robotPose) : List.of();
        // Past the path's last segment, and then stopping, both mean "no plan": say it once.
        if (points.equals(lastPoints)) {
            return Optional.empty();
        }
        lastPoints = points;
        return Optional.of(points);
    }

    /**
     * {@code points} (from {@link Pose#fromPedro}) moved and turned so that where the follower thinks the
     * robot is lands on {@code robotPose}. Unchanged if {@code robotPose} is null or the follower's pose
     * can't be read.
     */
    private List<PlannedPath.Point> placed(List<PlannedPath.Point> points, Pose robotPose) {
        Object pedroPose = robotPose == null || points.isEmpty() ? null : call(follower, "pose");
        Object x = pedroPose == null ? null : call(pedroPose, "x");
        Object y = pedroPose == null ? null : call(pedroPose, "y");
        Object heading = pedroPose == null ? null : call(pedroPose, "heading");
        if (!(x instanceof Number) || !(y instanceof Number) || !(heading instanceof Number)) {
            return points;
        }
        Pose believed = Pose.fromPedro(((Number) x).doubleValue(), ((Number) y).doubleValue(),
                Math.toDegrees(((Number) heading).doubleValue()));
        double turn = robotPose.headingRadians() - believed.headingRadians();
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        List<PlannedPath.Point> moved = new ArrayList<>();
        for (PlannedPath.Point p : points) {
            double dx = p.xInches() - believed.xInches();
            double dy = p.yInches() - believed.yInches();
            moved.add(new PlannedPath.Point(robotPose.xInches() + dx * cos - dy * sin,
                    robotPose.yInches() + dx * sin + dy * cos));
        }
        return moved;
    }

    /** The points from the start of segment {@code fromIndex} to the path's end, or none if it can't be read. */
    private List<PlannedPath.Point> sample(Object path, int fromIndex) {
        Object segments = call(path, "getSegments");
        if (!(segments instanceof List<?> list) || fromIndex >= list.size()) {
            return List.of();
        }
        Method get;
        Method length;
        try {
            Class<?> curveClass = Class.forName(CURVE_CLASS, false, path.getClass().getClassLoader());
            get = curveClass.getMethod("get", double.class);
            length = curveClass.getMethod("length");
            get.setAccessible(true);
            length.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
        List<PlannedPath.Point> points = new ArrayList<>();
        for (Object segment : list.subList(fromIndex, list.size())) {
            Object curve = readField(segment, "curve");
            if (curve == null) {
                return List.of();
            }
            try {
                double lengthInches = ((Number) length.invoke(curve)).doubleValue();
                int pieces = (int) Math.min(MAX_POINTS_PER_SEGMENT - 1,
                        Math.max(1, Math.ceil(lengthInches / SAMPLE_SPACING_INCHES)));
                // Each later segment starts where the one before ended, so skip its first point.
                for (int k = points.isEmpty() ? 0 : 1; k <= pieces; k++) {
                    Object vector = get.invoke(curve, (double) k / pieces);
                    double pedroX = ((Number) call(vector, "x")).doubleValue();
                    double pedroY = ((Number) call(vector, "y")).doubleValue();
                    Pose field = Pose.fromPedro(pedroX, pedroY, 0.0);
                    points.add(new PlannedPath.Point(field.xInches(), field.yInches()));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                return List.of();
            }
        }
        return points;
    }

    /**
     * Searches {@code start}'s fields, breadth first, for a Pedro follower. Looks inside arrays,
     * collections and map values too. Returns null if there is none within the limits.
     */
    static Object findFollower(Object start) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Class<?>> staticsRead = new HashSet<>();
        Deque<Object[]> queue = new ArrayDeque<>();   // {object, depth}
        queue.add(new Object[] {start, 0});
        while (!queue.isEmpty() && seen.size() < MAX_OBJECTS) {
            Object[] next = queue.poll();
            Object object = next[0];
            int depth = (Integer) next[1];
            if (object == null || !seen.add(object)) {
                continue;
            }
            Class<?> type = object.getClass();
            if (isFollower(type)) {
                return object;
            }
            if (depth >= MAX_DEPTH) {
                continue;
            }
            List<Object> inside = new ArrayList<>();
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive()) {
                    for (int i = 0; i < Array.getLength(object); i++) {
                        inside.add(Array.get(object, i));
                    }
                }
            } else if (object instanceof Collection<?> collection) {
                try {
                    inside.addAll(collection);
                } catch (RuntimeException e) {
                    // A collection that can't be copied right now is skipped.
                }
            } else if (object instanceof Map<?, ?> map) {
                try {
                    inside.addAll(map.values());
                } catch (RuntimeException e) {
                    // As above.
                }
            } else if (!isSkipped(type)) {
                for (Class<?> c = type; c != null && !isSkipped(c); c = c.getSuperclass()) {
                    boolean readStatics = staticsRead.add(c);
                    for (Field field : c.getDeclaredFields()) {
                        boolean isStatic = Modifier.isStatic(field.getModifiers());
                        if (field.getType().isPrimitive() || (isStatic && !readStatics)) {
                            continue;
                        }
                        try {
                            field.setAccessible(true);
                            inside.add(field.get(isStatic ? null : object));
                        } catch (RuntimeException | IllegalAccessException e) {
                            // A field Java won't let us read is skipped.
                        }
                    }
                }
            }
            for (Object child : inside) {
                if (child != null) {
                    queue.add(new Object[] {child, depth + 1});
                }
            }
        }
        return null;
    }

    /** True for Pedro's follower class (or a team's subclass of it). */
    private static boolean isFollower(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(FOLLOWER_CLASS)) {
                return true;
            }
        }
        return false;
    }

    /** True for classes the search never looks inside. */
    private static boolean isSkipped(Class<?> type) {
        String name = type.getName();
        for (String prefix : SKIPPED_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Calls a public method with no arguments by name, or gives null if it can't. */
    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);   // the object's own class may not be public
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Reads a public field by name, or gives null if it can't. */
    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
