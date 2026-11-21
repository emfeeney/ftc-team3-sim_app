using System.Collections.Generic;
using NUnit.Framework;
using UnityEngine;

namespace VI_Arena.Tests
{
    /// <summary>
    /// The yellow line of a Pedro robot's planned path (manager decision D39, <see cref="PlannedPathView"/>):
    /// which plan is shown at a moment, and how the line runs from the robot along the plan.
    /// </summary>
    public class PlannedPathViewTests
    {
        private GameObject parent;

        [TearDown]
        public void Remove()
        {
            if (parent != null)
            {
                Object.DestroyImmediate(parent);
            }
        }

        /// <summary>A straight plan along +x from (0, 0), 2 in apart, made at <paramref name="since"/> seconds.</summary>
        private static PlannedPathData Plan(double since, params double[] xs)
        {
            var plan = new PlannedPathData { sinceInitSeconds = since, matchTimeSeconds = since };
            foreach (double x in xs)
            {
                plan.points.Add(new[] { x, 0.0 });
            }
            return plan;
        }

        /// <summary>WHAT: No plans, a plan made later than the moment, or a plan with no points (the follower stopped) draw nothing.</summary>
        [Test]
        public void NoPlanAtTheMomentDrawsNothing()
        {
            var robot = new PoseData { xInches = 1, yInches = 1 };
            Assert.IsEmpty(PlannedPathView.LinePoints(null, 5, robot));
            Assert.IsEmpty(PlannedPathView.LinePoints(new List<PlannedPathData>(), 5, robot));
            Assert.IsEmpty(PlannedPathView.LinePoints(new List<PlannedPathData> { Plan(6, 0, 2) }, 5, robot), "made later");
            Assert.IsEmpty(PlannedPathView.LinePoints(new List<PlannedPathData> { Plan(1, 0, 2), Plan(3) }, 5, robot), "stopped at 3 s");
            Assert.IsEmpty(PlannedPathView.LinePoints(new List<PlannedPathData> { Plan(1, 0, 2) }, double.NaN, robot), "no moment");
        }

        /// <summary>WHAT: The newest plan made at or before the moment is the one drawn.</summary>
        [Test]
        public void TheNewestPlanAtTheMomentIsDrawn()
        {
            var plans = new List<PlannedPathData> { Plan(1, 0, 2, 4), Plan(3, 20, 22) };
            Assert.AreEqual(new Vector2(4, 0), PlannedPathView.LinePoints(plans, 2.99, null)[2]);
            Assert.AreEqual(2, PlannedPathView.LinePoints(plans, 3, null).Count, "the new plan from 3 s");
            Assert.AreEqual(new Vector2(20, 0), PlannedPathView.LinePoints(plans, 3, null)[0]);
        }

        /// <summary>
        /// WHAT: The line starts at the robot, goes to the plan's point nearest it, then on to the plan's end.
        /// WHY: D39: the line shows where the robot is about to go, not the part of the path already behind it.
        /// </summary>
        [Test]
        public void TheLineRunsFromTheRobotToTheNearestPointThenToTheEnd()
        {
            var plans = new List<PlannedPathData> { Plan(0, 0, 2, 4, 6, 8, 10) };
            List<Vector2> line = PlannedPathView.LinePoints(plans, 1, new PoseData { xInches = 5.2, yInches = 3 });

            CollectionAssert.AreEqual(new[] { new Vector2(5.2f, 3f), new Vector2(6, 0), new Vector2(8, 0), new Vector2(10, 0) }, line);
        }

        /// <summary>WHAT: Without a robot pose the whole plan is drawn.</summary>
        [Test]
        public void WithoutARobotTheWholePlanIsDrawn()
        {
            Assert.AreEqual(3, PlannedPathView.LinePoints(new List<PlannedPathData> { Plan(0, 0, 2, 4) }, 1, null).Count);
        }

        /// <summary>
        /// WHAT: The line is drawn in the Swing viewer's yellow (#FFD600), on the field just above the trail, on the
        /// sight-line layer the webcam picture leaves out; and hides when there's no plan.
        /// </summary>
        [Test]
        public void TheLineIsYellowOnTheFieldAndHidesWithoutAPlan()
        {
            parent = new GameObject("test");
            var view = new PlannedPathView(parent.transform);
            var plans = new List<PlannedPathData> { Plan(0, 0, 2, 4) };

            view.Show(plans, 1, new PoseData { xInches = 0, yInches = 2 });
            var line = parent.GetComponentInChildren<LineRenderer>();
            Assert.AreEqual(4, view.PointCount);
            Assert.AreEqual(new Color(1f, 214f / 255f, 0f), line.startColor);
            Assert.AreEqual(AprilTagSightView.Layer, line.gameObject.layer);
            Assert.AreEqual(FtcToUnity.Position(4, 0, PlannedPathView.HeightMeters), line.GetPosition(3));

            view.Show(plans, -1, null);
            Assert.AreEqual(0, view.PointCount, "before the plan");
        }

        /// <summary>WHAT: A version 7 recording's plannedPaths load as [x, y] points; a version 6 recording has none.</summary>
        [Test]
        public void Version7RecordingsCarryThePlans()
        {
            string json = "{\"header\": {\"version\": 7, \"opMode\": \"Test\", \"startPose\": null, \"driveModel\": null},"
                + " \"gamepad1Rumbles\": [], \"gamepad2Rumbles\": [], \"events\": [],"
                + " \"plannedPaths\": [{\"sinceInitSeconds\": 0.5, \"matchTimeSeconds\": 0.1, \"points\": [[1.5, -2], [3.5, -2]]},"
                + " {\"sinceInitSeconds\": 4, \"matchTimeSeconds\": 3.6, \"points\": []}],"
                + " \"loops\": [{\"sinceInitSeconds\": 0, \"matchTimeSeconds\": 0, \"stage\": \"INIT\", \"pose\": null,"
                + " \"motors\": [], \"servos\": [], \"autoStep\": null, \"telemetry\": []}]}";
            RunRecordingData run = RecordingLoader.Parse(json, "plans.json");

            Assert.AreEqual(2, run.plannedPaths.Count);
            Assert.AreEqual(0.5, run.plannedPaths[0].sinceInitSeconds);
            Assert.AreEqual(new[] { 3.5, -2.0 }, run.plannedPaths[0].points[1]);
            Assert.IsEmpty(run.plannedPaths[1].points);

            int from = json.IndexOf(" \"plannedPaths\"", System.StringComparison.Ordinal);
            int to = json.IndexOf(" \"loops\"", System.StringComparison.Ordinal);
            string v6 = json.Substring(0, from) + json.Substring(to);
            RunRecordingData old = RecordingLoader.Parse(v6.Replace("\"version\": 7", "\"version\": 6"), "old.json");
            Assert.IsEmpty(old.plannedPaths, "older files have none");
        }
    }
}
