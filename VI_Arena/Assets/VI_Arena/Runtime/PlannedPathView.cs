using System.Collections.Generic;
using UnityEngine;

namespace VI_Arena
{
    /// <summary>
    /// The yellow line of a Pedro robot's planned path (manager decision D39): where its follower is about
    /// to go, from the robot to the point of the plan nearest it, then on to the plan's end. The plan is
    /// the newest one at the playhead (the recording's <c>plannedPaths</c>, version 7; in Live mode, the
    /// bridge's frames). A robot without Pedro, or a moment with no plan, shows nothing. Same yellow as
    /// the Swing viewer (FieldPainter.PLANNED_PATH, #FFD600).
    /// </summary>
    public sealed class PlannedPathView
    {
        /// <summary>The Swing viewer's planned-path yellow.</summary>
        public static readonly Color LineColor = new Color(1f, 0xD6 / 255f, 0f);

        /// <summary>Height above the tiles, in meters: just over the cyan trail (4 mm), so the two don't flicker.</summary>
        public const float HeightMeters = 0.006f;

        private readonly LineRenderer line;

        public PlannedPathView(Transform parent)
        {
            line = new GameObject("Planned path (Pedro)").AddComponent<LineRenderer>();
            line.transform.SetParent(parent, false);
            // Like the AprilTag sight lines: drawn on the field, left out of the webcam picture.
            line.gameObject.layer = AprilTagSightView.Layer;
            line.useWorldSpace = true;
            line.widthMultiplier = 0.014f;
            line.numCapVertices = 2;
            line.material = new Material(Shader.Find("Sprites/Default"));
            line.startColor = LineColor;
            line.endColor = LineColor;
            line.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            line.positionCount = 0;
        }

        /// <summary>How many points the line has now (0 = hidden), for tests.</summary>
        public int PointCount => line.positionCount;

        /// <summary>Draws the plan at <paramref name="sinceInitSeconds"/> from <paramref name="robot"/> (or hides it).</summary>
        public void Show(IReadOnlyList<PlannedPathData> plans, double sinceInitSeconds, PoseData robot)
        {
            List<Vector2> points = LinePoints(plans, sinceInitSeconds, robot);
            line.positionCount = points.Count;
            for (int i = 0; i < points.Count; i++)
            {
                line.SetPosition(i, FtcToUnity.Position(points[i].x, points[i].y, HeightMeters));
            }
        }

        /// <summary>
        /// The line in field inches (x, y): empty with no plan at that moment; otherwise the robot's spot (when
        /// known), then the plan from its point nearest the robot to its end.
        /// </summary>
        public static List<Vector2> LinePoints(IReadOnlyList<PlannedPathData> plans, double sinceInitSeconds, PoseData robot)
        {
            var result = new List<Vector2>();
            PlannedPathData plan = null;
            if (plans != null)
            {
                foreach (PlannedPathData p in plans)
                {
                    // A hair of slack so a plan made in this very loop counts despite rounding.
                    if (p.sinceInitSeconds <= sinceInitSeconds + 1e-9)
                    {
                        plan = p;
                    }
                }
            }
            if (plan?.points == null || plan.points.Count == 0)
            {
                return result;
            }
            int nearest = 0;
            if (robot != null)
            {
                result.Add(new Vector2((float)robot.xInches, (float)robot.yInches));
                double best = double.MaxValue;
                for (int i = 0; i < plan.points.Count; i++)
                {
                    double dx = plan.points[i][0] - robot.xInches, dy = plan.points[i][1] - robot.yInches;
                    double d = dx * dx + dy * dy;
                    if (d < best)
                    {
                        best = d;
                        nearest = i;
                    }
                }
            }
            for (int i = nearest; i < plan.points.Count; i++)
            {
                result.Add(new Vector2((float)plan.points[i][0], (float)plan.points[i][1]));
            }
            return result;
        }
    }
}
