package pvi.harness.physics;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import java.util.ArrayList;
import java.util.List;

import org.firstinspires.ftc.teamcode.robots.teamA.TeamAPedroPathRoute;
import org.junit.jupiter.api.Test;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.PedroPathWatcher;
import pvi.harness.runtime.PlannedPath;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;
import pvi.harness.runtime.RunRecordingJson;
import pvi.harness.viewer.RecordingView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D39: the harness finds a Pedro {@code Follower} in any robot code and reads the path it plans to
 * follow, for the yellow line in the viewer and VI Arena ({@link PedroPathWatcher}).
 *
 * <p>Team A's follower sits four objects deep (OpMode, robot, drive controller, follower), and the
 * watcher finds it by Pedro's class name only, so these tests also show it would find another
 * team's follower without any change to their code.</p>
 */
class PedroPlannedPathTest {
    /** The plans a watcher reported during a run, in order. */
    private record Watched(PedroPathWatcher watcher, List<List<PlannedPath.Point>> plans) {
        List<List<PlannedPath.Point>> nonEmpty() {
            return plans.stream().filter(p -> !p.isEmpty()).toList();
        }
    }

    /** Runs a Team A route for 30 s, watching for planned paths after every OpMode call. */
    private static Watched watchRoute(TeamAPedroPathRoute route) {
        OpModeRunner runner = PedroRouteTest.routeRunner(route, DriveModels.teamA(), RobotConfigs.teamA(), false);
        return watch(runner, 30.0);
    }

    private static Watched watch(OpModeRunner runner, double seconds) {
        PedroPathWatcher watcher = new PedroPathWatcher(runner.opMode());
        List<List<PlannedPath.Point>> plans = new ArrayList<>();
        runner.everyLoop(r -> watcher.poll(r.positionEstimator() == null ? null : r.positionEstimator().pose())
                .ifPresent(plans::add));
        runner.run(1.0, seconds).assertNoException();
        return new Watched(watcher, plans);
    }

    /** The route's point in Pedro's frame, in the harness's field frame. */
    private static Pose field(double pedroX, double pedroY) {
        return Pose.fromPedro(pedroX, pedroY, 0.0);
    }

    /**
     * Where a route's plan should end on the field: the harness starts the robot at
     * {@link PedroRouteTest#startOf} (moved 1 in inside the wall, LP-11 at the center), and the plan is
     * placed relative to where the robot really is, so the end moves with the start.
     */
    private static Pose expectedEnd(TeamAPedroPathRoute route) {
        Pose start = PedroRouteTest.startOf(route, DriveModels.teamA());
        Pose pedroStart = Pose.fromPedro(route.getStartXInches(), route.getStartYInches(),
                Math.toDegrees(route.getStartHeadingRadians()));
        Pose pedroEnd = field(route.getEndXInches(), route.getEndYInches());
        double turn = start.headingRadians() - pedroStart.headingRadians();
        double dx = pedroEnd.xInches() - pedroStart.xInches();
        double dy = pedroEnd.yInches() - pedroStart.yInches();
        return new Pose(start.xInches() + dx * Math.cos(turn) - dy * Math.sin(turn),
                start.yInches() + dx * Math.sin(turn) + dy * Math.cos(turn), 0);
    }

    private static void assertNear(Pose expected, PlannedPath.Point actual, double inches, String what) {
        double off = Math.hypot(actual.xInches() - expected.xInches(), actual.yInches() - expected.yInches());
        assertTrue(off <= inches, what + ": " + actual + " is " + off + " in from " + expected);
    }

    /**
     * WHAT: DECODE Route's plan starts at the route's start and ends at its target, with points at
     * most 2 in apart.
     *
     * <p>WHY: This is the line drawn on the field, so it must be the route Pedro was given, in the
     * harness's own field frame (Pedro's frame is turned and shifted).</p>
     */
    @Test
    void aRoutesPlanRunsFromItsStartToItsTarget() {
        TeamAPedroPathRoute route = TeamAPedroPathRoute.DECODE_ROUTE;
        Watched watched = watchRoute(route);

        assertTrue(watched.watcher().foundFollower(), "Team A's follower, deep in its robot, is found");
        List<PlannedPath.Point> plan = watched.nonEmpty().get(0);
        assertNear(PedroRouteTest.startOf(route, DriveModels.teamA()), plan.get(0), 0.01, "start, where the robot is");
        assertNear(expectedEnd(route), plan.get(plan.size() - 1), 0.01, "end");
        for (int i = 1; i < plan.size(); i++) {
            PlannedPath.Point a = plan.get(i - 1);
            PlannedPath.Point b = plan.get(i);
            double gap = Math.hypot(b.xInches() - a.xInches(), b.yInches() - a.yInches());
            assertTrue(gap <= PedroPathWatcher.SAMPLE_SPACING_INCHES + 1e-6, "points " + (i - 1) + " and " + i
                    + " are " + gap + " in apart");
        }
    }

    /**
     * WHAT: LP-11's plan runs from the field's center 24 in straight ahead, where the robot really
     * drives, though its code calls the start Pedro's (0, 0), a field corner.
     *
     * <p>WHY: Pedro's coordinates alone would put the line along the field's edge (found by UnityBot2
     * in VI Arena), far from the robot.</p>
     */
    @Test
    void aPlanIsPlacedWhereTheRobotReallyIs() {
        List<PlannedPath.Point> plan = watchRoute(TeamAPedroPathRoute.LP11_PILOT).nonEmpty().get(0);

        assertNear(Pose.ORIGIN, plan.get(0), 0.01, "start");
        assertNear(new Pose(24, 0, 0), plan.get(plan.size() - 1), 0.01, "end");
    }

    /** WHAT: A curved route's plan follows the curve, not a straight line from start to end. */
    @Test
    void aCurvedRoutesPlanBends() {
        List<PlannedPath.Point> plan = watchRoute(TeamAPedroPathRoute.CURVING_TEST).nonEmpty().get(0);
        PlannedPath.Point a = plan.get(0);
        PlannedPath.Point b = plan.get(plan.size() - 1);
        double chord = Math.hypot(b.xInches() - a.xInches(), b.yInches() - a.yInches());
        double farthest = 0.0;
        for (PlannedPath.Point p : plan) {
            // Distance from the straight line a-b.
            double cross = (b.xInches() - a.xInches()) * (p.yInches() - a.yInches())
                    - (b.yInches() - a.yInches()) * (p.xInches() - a.xInches());
            farthest = Math.max(farthest, Math.abs(cross) / chord);
        }
        assertTrue(farthest > 5.0, "the curve bends " + farthest + " in away from the straight line");
    }

    /**
     * WHAT: Once the route is done (Pedro holds the end), the plan is cleared, so no line stays on
     * the field.
     */
    @Test
    void thePlanClearsWhenTheRouteIsDone() {
        Watched watched = watchRoute(TeamAPedroPathRoute.LP11_PILOT);

        assertFalse(watched.nonEmpty().isEmpty(), "LP-11 had a plan: " + watched.plans());
        assertTrue(watched.plans().get(watched.plans().size() - 1).isEmpty(), "the last plan is empty");
        for (int i = 1; i < watched.plans().size(); i++) {
            assertFalse(watched.plans().get(i).equals(watched.plans().get(i - 1)),
                    "plan " + i + " repeats the one before: " + watched.plans().get(i));
        }
    }

    /** A robot's OpMode that never makes a Pedro follower. */
    static final class NoPedroOpMode extends OpMode {
        private final List<Object> parts = new ArrayList<>(List.of("arm", new double[] {1, 2}));

        @Override
        public void init() {
            telemetry.addData("Parts", parts.size());
        }

        @Override
        public void loop() {
        }
    }

    /** WHAT: A robot without Pedro gets one empty plan (no line) and nothing more. */
    @Test
    void aRobotWithoutPedroHasNoPlan() {
        Watched watched = watch(new OpModeRunner(new NoPedroOpMode(), RobotConfigs.teamA()), 2.0);

        assertFalse(watched.watcher().foundFollower());
        assertEquals(List.of(List.of()), watched.plans());
    }

    /**
     * WHAT: A recorded route keeps its plans, they survive saving to JSON and loading back, and the
     * viewer's line at a moment mid-route starts at the robot and ends at the route's target.
     */
    @Test
    void aRecordedRoutesPlanIsSavedAndShownFromTheRobot() {
        TeamAPedroPathRoute route = TeamAPedroPathRoute.DECODE_ROUTE;
        OpModeRunner runner = PedroRouteTest.routeRunner(route, DriveModels.teamA(), RobotConfigs.teamA(), false);
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(1.0, 30.0).assertNoException();
        RunRecording recording = recorder.recording();

        assertFalse(recording.plannedPaths().isEmpty(), "the route's plan is recorded");
        // Saved to 6 decimal places, so compare to a millionth of an inch.
        List<PlannedPath> loaded = RunRecordingJson.parse(RunRecordingJson.format(recording)).plannedPaths();
        assertEquals(recording.plannedPaths().size(), loaded.size());
        for (int i = 0; i < loaded.size(); i++) {
            PlannedPath saved = recording.plannedPaths().get(i);
            assertEquals(saved.sinceInitSeconds(), loaded.get(i).sinceInitSeconds(), 1e-6);
            assertEquals(saved.points().size(), loaded.get(i).points().size());
            for (int k = 0; k < saved.points().size(); k++) {
                assertNear(new Pose(saved.points().get(k).xInches(), saved.points().get(k).yInches(), 0),
                        loaded.get(i).points().get(k), 1e-5, "plan " + i + " point " + k);
            }
        }

        RecordingView view = new RecordingView(recording);
        PlannedPath first = recording.plannedPaths().stream().filter(p -> !p.isEmpty()).findFirst().orElseThrow();
        int mid = -1;
        for (int i = 0; i < view.size(); i++) {
            if (view.loop(i).sinceInitSeconds() > first.sinceInitSeconds() + 1.0) {
                mid = i;
                break;
            }
        }
        List<PlannedPath.Point> line = view.plannedLine(mid);
        Pose robot = view.loop(mid).pose();
        assertEquals(robot.xInches(), line.get(0).xInches(), 1e-9, "the line starts at the robot");
        assertEquals(robot.yInches(), line.get(0).yInches(), 1e-9, "the line starts at the robot");
        assertNear(expectedEnd(route), line.get(line.size() - 1), 0.01, "end");
        assertTrue(line.size() < first.points().size() + 1, "the part already driven is left off");
        assertEquals(List.of(), view.plannedLine(view.size() - 1), "no line once the route is done");
    }

    /** WHAT: A recording of a robot without Pedro has no planned paths at all. */
    @Test
    void aRobotWithoutPedroRecordsNoPlans() {
        OpModeRunner runner = new OpModeRunner(new NoPedroOpMode(), RobotConfigs.teamA());
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.5, 1.0).assertNoException();

        assertEquals(List.of(), recorder.recording().plannedPaths());
    }

    /** WHAT: The line for a robot starts at the robot and skips the plan's points behind the nearest one. */
    @Test
    void theLineRunsFromTheRobotToTheNearestPointOnward() {
        PlannedPath plan = new PlannedPath(1.0, 0.5, List.of(new PlannedPath.Point(0, 0),
                new PlannedPath.Point(10, 0), new PlannedPath.Point(20, 0)));

        assertEquals(List.of(new PlannedPath.Point(9, 3), new PlannedPath.Point(10, 0), new PlannedPath.Point(20, 0)),
                plan.aheadOf(9, 3));
        assertEquals(List.of(), new PlannedPath(1.0, 0.5, List.of()).aheadOf(9, 3));
    }
}
