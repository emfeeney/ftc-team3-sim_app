using System;
using System.Collections.Generic;
using UnityEngine;

namespace VI_Arena
{
    /// <summary>
    /// The balls moved by Unity's physics engine (PhysX) in a Live run with Unity physics on (manager
    /// decision D30). Java still runs the robot code and the mechanism rules; this moves whatever is
    /// loose: a ball on the tiles, in the air, or in a CELL. A ball the robot holds, one in a FLOWER and
    /// NECTAR off the field stay where Java puts them (<see cref="OwnedByUnity"/>).
    ///
    /// <para>The balls have their own copy of the field to hit, on their own layers, so the robot's
    /// physics (<see cref="UnityRobotPhysics"/>) is untouched: the tiles, the walls, the field's ball
    /// obstacles (HIVE frame lips and feet, FLOWERs), what a flying ball can hit (field version 4
    /// <c>flightObstacles</c>), each HIVE's arm and CELL walls on a body that turns with the HIVE, and a
    /// box that follows the robot.</para>
    ///
    /// <para>Each loop: <see cref="Apply"/> what Java said (state changes, launches, the HIVEs' swing,
    /// the robot's pose), then PhysX runs (<see cref="Step"/>, or the robot physics' own step), then
    /// <see cref="AfterStep"/> and <see cref="Motions"/> give the balls back to Java.</para>
    /// </summary>
    public sealed class UnityBallPhysics : IDisposable
    {
        /// <summary>The layer of the balls, and of everything the balls hit. Neither touches anything else in the scene.</summary>
        public const int BallLayer = 29, BallWorldLayer = 28;
        /// <summary>How tall the robot box is for the balls: the harness's GamePieces.ROBOT_HEIGHT (a placeholder).</summary>
        public const double RobotHeightInches = 18;
        /// <summary>Thickness of a CELL wall.</summary>
        public const double CellWallInches = 1;
        /// <summary>Slower than this, a ball doesn't bounce (PhysX's bounce threshold while the balls exist; 0.05 m/s is about 2 in/s).</summary>
        public const float BounceThresholdMetersPerSecond = 0.05f;
        /// <summary>How far outside the field (or below the tiles) a ball counts as lost.</summary>
        public const double LostBeyondInches = 30;

        private readonly BallFacts facts;
        private readonly GameObject root;
        private readonly Dictionary<string, Rigidbody> balls = new Dictionary<string, Rigidbody>();
        private readonly Dictionary<string, PieceData> state = new Dictionary<string, PieceData>();
        // Java's last word on each ball's position, for a ball that gets lost (D30 b).
        private readonly Dictionary<string, Vector3> lastFromJava = new Dictionary<string, Vector3>();
        private readonly List<(HiveData hive, Rigidbody body)> hives = new List<(HiveData, Rigidbody)>();
        private readonly Rigidbody robotShell;
        private bool robotPlaced;
        private readonly bool[] ignoredBefore = new bool[32];
        private readonly float bounceThresholdBefore;
        private readonly SimulationMode simulationModeBefore;

        /// <summary>How many times a lost ball was put back (logged each time).</summary>
        public int LostBallsPutBack { get; private set; }

        /// <summary>Builds the balls at <paramref name="start"/> and what they can hit on <paramref name="field"/> (or only tiles and walls).</summary>
        public UnityBallPhysics(FieldLayoutData field, IReadOnlyList<PieceData> start, BallFacts facts = null)
        {
            this.facts = facts ?? BallFacts.Harness;
            simulationModeBefore = Physics.simulationMode;
            Physics.simulationMode = SimulationMode.Script;
            // PhysX doesn't bounce anything slower than this (2 m/s, about 80 in/s, by default); the
            // harness's balls bounce at any speed.
            bounceThresholdBefore = Physics.bounceThreshold;
            Physics.bounceThreshold = BounceThresholdMetersPerSecond;
            // Balls meet only the balls' world and each other.
            for (int layer = 0; layer < 32; layer++)
            {
                ignoredBefore[layer] = Physics.GetIgnoreLayerCollision(BallLayer, layer);
                Physics.IgnoreLayerCollision(BallLayer, layer, layer != BallWorldLayer && layer != BallLayer);
            }
            root = new GameObject("Unity ball physics (invisible)");

            PhysicsMaterial walls = Material("Ball walls", this.facts.wallBounce);
            double half = FtcToUnity.FieldHalfWidthInches;
            const double thickness = 12, height = 36;
            double length = 2 * half + 2 * thickness;
            // The tiles don't bounce a ball back up, as in the harness, where a landing ball just rolls on.
            AddBox(root.transform, "Tiles", 0, 0, -thickness / 2, 2 * half + 2 * thickness, 2 * half + 2 * thickness, thickness, Material("Tiles", 0));
            AddBox(root.transform, "Wall +x", half + thickness / 2, 0, height / 2, thickness, length, height, walls);
            AddBox(root.transform, "Wall -x", -half - thickness / 2, 0, height / 2, thickness, length, height, walls);
            AddBox(root.transform, "Wall +y", 0, half + thickness / 2, height / 2, length, thickness, height, walls);
            AddBox(root.transform, "Wall -y", 0, -half - thickness / 2, height / 2, length, thickness, height, walls);
            if (field != null)
            {
                foreach (PieceObstacleData o in field.pieceObstacles)
                {
                    AddPieceObstacle(o, walls);
                }
                foreach (FlightObstacleData o in field.flightObstacles)
                {
                    if (o.shape == "capsule")
                    {
                        AddCapsule(o, walls);
                    }
                    else if (o.shape == "box")
                    {
                        AddBox(root.transform, o.id, (o.minInches[0] + o.maxInches[0]) / 2, (o.minInches[1] + o.maxInches[1]) / 2,
                            (o.minInches[2] + o.maxInches[2]) / 2, o.maxInches[0] - o.minInches[0], o.maxInches[1] - o.minInches[1],
                            o.maxInches[2] - o.minInches[2], walls);
                    }
                }
                foreach (HiveData hive in field.hives)
                {
                    hives.Add((hive, AddHive(hive, field.flightObstacles.Find(o => o.shape == "hiveBox" && o.hive == hive.id), walls)));
                }
            }

            robotShell = new GameObject("Robot (for the balls)").AddComponent<Rigidbody>();
            robotShell.transform.SetParent(root.transform, false);
            robotShell.isKinematic = true;
            robotShell.gameObject.layer = BallWorldLayer;
            var box = robotShell.gameObject.AddComponent<BoxCollider>();
            box.material = Material("Robot bumper", this.facts.robotBounce);
            box.enabled = false; // until the robot's pose is known

            PhysicsMaterial ballMaterial = Material("Ball", this.facts.pieceBounce);
            foreach (PieceData piece in start)
            {
                if (piece?.id == null || balls.ContainsKey(piece.id))
                {
                    continue;
                }
                var ball = new GameObject("Ball " + piece.id) { layer = BallLayer };
                ball.transform.SetParent(root.transform, false);
                var sphere = ball.AddComponent<SphereCollider>();
                sphere.radius = FtcToUnity.Meters(GamePieceView.DiameterInches(piece.kind) / 2);
                sphere.material = ballMaterial;
                Rigidbody body = ball.AddComponent<Rigidbody>();
                body.mass = (float)(HiveTip.MassGrams(piece.kind) / 1000.0);
                body.collisionDetectionMode = CollisionDetectionMode.ContinuousSpeculative;
                body.maxAngularVelocity = 200f;
                balls[piece.id] = body;
                PieceData copy = Copy(piece);
                state[piece.id] = copy;
                Vector3 at = FtcToUnity.Position(piece.xInches, piece.yInches, FtcToUnity.Meters(piece.zInches));
                lastFromJava[piece.id] = at;
                body.position = at;
                ball.transform.position = at;
                Own(body, copy, Vector3.zero);
            }
            Physics.SyncTransforms();
        }

        /// <summary>True for the states whose ball PhysX moves: on the tiles, in the air, in a CELL. Java moves the rest.</summary>
        public static bool OwnedByUnity(string state)
        {
            return state == "field" || state == GamePieceView.FlyingState || state == "cell";
        }

        /// <summary>
        /// Takes in Java's word for this loop, before PhysX runs: <paramref name="changed"/> (the loop's
        /// pieces), <paramref name="launches"/> (start velocities for balls Java just launched or released),
        /// the HIVEs' swing, and the robot's pose and size.
        /// </summary>
        public void Apply(IReadOnlyList<PieceData> changed, IReadOnlyList<PieceLaunchData> launches, IReadOnlyList<HiveTiltData> hiveSwings,
            PoseData robot, double robotLengthInches, double robotWidthInches)
        {
            var launched = new Dictionary<string, PieceLaunchData>();
            if (launches != null)
            {
                foreach (PieceLaunchData launch in launches)
                {
                    launched[launch.id] = launch;
                }
            }
            if (changed != null)
            {
                foreach (PieceData piece in changed)
                {
                    if (piece?.id == null || !balls.TryGetValue(piece.id, out Rigidbody body))
                    {
                        continue;
                    }
                    PieceData before = state[piece.id];
                    Vector3 at = FtcToUnity.Position(piece.xInches, piece.yInches, FtcToUnity.Meters(piece.zInches));
                    lastFromJava[piece.id] = at;
                    state[piece.id] = Copy(piece);
                    bool takeOver = launched.TryGetValue(piece.id, out PieceLaunchData launch);
                    if (takeOver)
                    {
                        at = FtcToUnity.Position(launch.xInches, launch.yInches, FtcToUnity.Meters(launch.zInches));
                        lastFromJava[piece.id] = at;
                    }
                    if (!OwnedByUnity(piece.state) || !OwnedByUnity(before.state) || takeOver)
                    {
                        // Java places it: always for its own balls, and once when a ball comes back to PhysX.
                        body.position = at;
                        body.transform.position = at;
                        Vector3 velocity = takeOver
                            ? FtcToUnity.Direction(FtcToUnity.Meters(launch.vxInchesPerSecond), FtcToUnity.Meters(launch.vyInchesPerSecond), FtcToUnity.Meters(launch.vzInchesPerSecond))
                            : Vector3.zero;
                        Own(body, state[piece.id], velocity);
                    }
                }
            }
            if (hiveSwings != null)
            {
                foreach ((HiveData hive, Rigidbody body) in hives)
                {
                    foreach (HiveTiltData swing in hiveSwings)
                    {
                        if (swing.id == hive.id)
                        {
                            body.MoveRotation(Quaternion.AngleAxis((float)swing.swingDegrees, Vector3.forward));
                        }
                    }
                }
            }
            var shell = robotShell.GetComponent<BoxCollider>();
            shell.enabled = robot != null;
            if (robot != null)
            {
                shell.size = new Vector3(FtcToUnity.Meters(robotWidthInches), FtcToUnity.Meters(RobotHeightInches), FtcToUnity.Meters(robotLengthInches));
                Vector3 position = FtcToUnity.Position(robot.xInches, robot.yInches, FtcToUnity.Meters(RobotHeightInches / 2));
                Quaternion rotation = FtcToUnity.Rotation(robot.headingRadians);
                if (!robotPlaced)
                {
                    robotPlaced = true;
                    robotShell.position = position;
                    robotShell.rotation = rotation;
                    robotShell.transform.SetPositionAndRotation(position, rotation);
                }
                robotShell.MovePosition(position);
                robotShell.MoveRotation(rotation);
            }
        }

        /// <summary>
        /// Runs PhysX for <paramref name="seconds"/> with only the balls (no robot physics), in
        /// <see cref="UnityRobotPhysics.SubSteps"/> steps as in Live: before and physics each sub-step, then after.
        /// </summary>
        public void Step(double seconds)
        {
            double subSeconds = seconds / UnityRobotPhysics.SubSteps;
            for (int i = 0; i < UnityRobotPhysics.SubSteps; i++)
            {
                BeforeStep(subSeconds);
                Physics.Simulate((float)subSeconds);
            }
            AfterStep();
        }

        /// <summary>
        /// Slows each ball rolling on the tiles by the harness's rolling deceleration for this step (PhysX
        /// balls here slide without friction, so this is what stops them, as in the harness), and each
        /// flying ball by the air: k times its speed squared, against its velocity (D40; PhysX's own
        /// linear drag is a different law, so it stays off).
        /// </summary>
        public void BeforeStep(double seconds)
        {
            foreach (KeyValuePair<string, Rigidbody> pair in balls)
            {
                PieceData piece = state[pair.Key];
                if (pair.Value.isKinematic || piece.state != GamePieceView.FlyingState)
                {
                    continue;
                }
                // k is per inch; per meter it is k / 0.0254. The speed change is k v^2 dt along the velocity.
                double perMeter = facts.DragPerInch(piece.kind) / FtcToUnity.Meters(1);
                Vector3 v = pair.Value.linearVelocity;
                double airSlow = perMeter * v.sqrMagnitude * seconds;
                pair.Value.linearVelocity = airSlow >= v.magnitude ? Vector3.zero : v - v.normalized * (float)airSlow;
            }
            float slow = FtcToUnity.Meters(facts.rollingDecelerationInchesPerSecondSquared * seconds);
            foreach (KeyValuePair<string, Rigidbody> pair in balls)
            {
                Rigidbody body = pair.Value;
                if (body.isKinematic)
                {
                    continue;
                }
                float radius = ((SphereCollider)body.GetComponent<Collider>()).radius;
                bool onTiles = body.position.y < radius + FtcToUnity.Meters(0.2) && Mathf.Abs(body.linearVelocity.y) < 0.05f;
                if (!onTiles)
                {
                    continue;
                }
                Vector3 flat = new Vector3(body.linearVelocity.x, 0f, body.linearVelocity.z);
                float speed = flat.magnitude;
                body.linearVelocity = speed <= slow ? new Vector3(0f, body.linearVelocity.y, 0f)
                    : body.linearVelocity - flat / speed * slow;
            }
        }

        /// <summary>After PhysX ran: a ball it lost (out of the field, under the tiles, or not a number) goes back to Java's last position, and the loss is logged.</summary>
        public void AfterStep()
        {
            foreach (KeyValuePair<string, Rigidbody> pair in balls)
            {
                Rigidbody body = pair.Value;
                if (body.isKinematic || !IsLost(body.position))
                {
                    continue;
                }
                Vector3 back = lastFromJava[pair.Key];
                Debug.LogWarning("[VI_Arena] Unity physics lost " + pair.Key + " at " + body.position.ToString("F3") + "; put it back where the harness last had it.");
                body.position = back;
                body.transform.position = back;
                body.linearVelocity = Vector3.zero;
                body.angularVelocity = Vector3.zero;
                LostBallsPutBack++;
            }
        }

        /// <summary>True if a ball here is lost: not a number, below the tiles, or more than <see cref="LostBeyondInches"/> outside the field.</summary>
        public static bool IsLost(Vector3 unityPosition)
        {
            if (float.IsNaN(unityPosition.x) || float.IsNaN(unityPosition.y) || float.IsNaN(unityPosition.z)
                || float.IsInfinity(unityPosition.x) || float.IsInfinity(unityPosition.y) || float.IsInfinity(unityPosition.z))
            {
                return true;
            }
            double limit = FtcToUnity.FieldHalfWidthInches + LostBeyondInches;
            return Math.Abs(FtcToUnity.Inches(unityPosition.x)) > limit || Math.Abs(FtcToUnity.Inches(unityPosition.z)) > limit
                || FtcToUnity.Inches(unityPosition.y) < -1;
        }

        /// <summary>Where every ball PhysX owns is, and how fast it goes: what Unity sends Java each loop.</summary>
        public List<PieceMotionData> Motions()
        {
            var motions = new List<PieceMotionData>();
            foreach (KeyValuePair<string, Rigidbody> pair in balls)
            {
                if (!OwnedByUnity(state[pair.Key].state))
                {
                    continue;
                }
                Vector3 p = pair.Value.position, v = pair.Value.linearVelocity;
                motions.Add(new PieceMotionData
                {
                    id = pair.Key,
                    xInches = FtcToUnity.Inches(p.x), yInches = FtcToUnity.Inches(p.z), zInches = FtcToUnity.Inches(p.y),
                    vxInchesPerSecond = FtcToUnity.Inches(v.x), vyInchesPerSecond = FtcToUnity.Inches(v.z), vzInchesPerSecond = FtcToUnity.Inches(v.y),
                });
            }
            return motions;
        }

        /// <summary>The body of one ball (for tests).</summary>
        public Rigidbody Ball(string id) => balls.TryGetValue(id, out Rigidbody body) ? body : null;

        /// <summary>Removes the invisible objects and puts the layer settings back.</summary>
        public void Dispose()
        {
            if (root == null)
            {
                return;
            }
            for (int layer = 0; layer < 32; layer++)
            {
                Physics.IgnoreLayerCollision(BallLayer, layer, ignoredBefore[layer]);
            }
            Physics.bounceThreshold = bounceThresholdBefore;
            Physics.simulationMode = simulationModeBefore;
            if (Application.isPlaying)
            {
                UnityEngine.Object.Destroy(root);
            }
            else
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        /// <summary>Makes a ball PhysX's (dynamic, with <paramref name="velocity"/>) or Java's (kinematic; a held ball also stops colliding, D30 a).</summary>
        private static void Own(Rigidbody body, PieceData piece, Vector3 velocity)
        {
            bool unity = OwnedByUnity(piece.state);
            body.GetComponent<Collider>().enabled = piece.state != GamePieceView.HeldState;
            if (unity)
            {
                body.isKinematic = false;
                body.linearVelocity = velocity;
                body.angularVelocity = Vector3.zero;
            }
            else
            {
                if (!body.isKinematic)
                {
                    body.linearVelocity = Vector3.zero;
                    body.angularVelocity = Vector3.zero;
                }
                body.isKinematic = true;
            }
        }

        /// <summary>A HIVE's turning body at its axle: its arm and each CELL's walls (open at the outer end), in the HIVE's own frame.</summary>
        private Rigidbody AddHive(HiveData hive, FlightObstacleData arm, PhysicsMaterial material)
        {
            var pivot = new GameObject("HIVE " + hive.id + " (for the balls)");
            pivot.transform.SetParent(root.transform, false);
            pivot.transform.localPosition = FtcToUnity.Position(hive.pivotXInches, hive.pivotYInches, FtcToUnity.Meters(hive.pivotZInches));
            Rigidbody body = pivot.AddComponent<Rigidbody>();
            body.isKinematic = true;
            // The HIVE frame: x along the arm (+ toward the +x side), y square to it, z the field's y, at the starting tilt.
            var frame = new GameObject("HIVE frame at the start").transform;
            frame.SetParent(pivot.transform, false);
            frame.localRotation = Quaternion.AngleAxis((float)(hive.startUpSide * hive.tiltDegrees), Vector3.forward);
            const double t = CellWallInches;
            foreach (CellData cell in hive.cells)
            {
                double s = cell.side, width = cell.maxYInches - cell.minYInches, depth = cell.openingInches - cell.backInches;
                double tall = cell.topInches - cell.floorInches, y = (cell.minYInches + cell.maxYInches) / 2 - hive.pivotYInches;
                double middle = (cell.backInches + cell.openingInches) / 2;
                AddLocalBox(frame, cell.id + " back", s * (cell.backInches - t / 2), (cell.floorInches + cell.topInches) / 2, y, t, tall + 2 * t, width + 2 * t, material);
                // Solid down to the floor's underside (D31; the harness's floor is the same slab).
                double underside = cell.floorUndersideInches < cell.floorInches ? cell.floorUndersideInches : cell.floorInches - t;
                AddLocalBox(frame, cell.id + " floor", s * middle, (cell.floorInches + underside) / 2, y, depth, cell.floorInches - underside, width + 2 * t, material);
                AddLocalBox(frame, cell.id + " top", s * middle, cell.topInches + t / 2, y, depth, t, width + 2 * t, material);
                AddLocalBox(frame, cell.id + " side low y", s * middle, (cell.floorInches + cell.topInches) / 2, cell.minYInches - t / 2 - hive.pivotYInches, depth, tall, t, material);
                AddLocalBox(frame, cell.id + " side high y", s * middle, (cell.floorInches + cell.topInches) / 2, cell.maxYInches + t / 2 - hive.pivotYInches, depth, tall, t, material);
            }
            if (arm != null)
            {
                // Only between the CELLs: beyond the back walls the CELL floors already stop a ball.
                double back = hive.cells.Count > 0 ? hive.cells[0].backInches : arm.maxAlongInches;
                double along = Math.Min(back, arm.maxAlongInches);
                AddLocalBox(frame, arm.id, 0, (arm.minAcrossInches + arm.maxAcrossInches) / 2, (arm.minYInches + arm.maxYInches) / 2 - hive.pivotYInches,
                    2 * along, arm.maxAcrossInches - arm.minAcrossInches, arm.maxYInches - arm.minYInches, material);
            }
            return body;
        }

        private void AddPieceObstacle(PieceObstacleData o, PhysicsMaterial material)
        {
            double height = o.topInches - o.bottomInches, center = (o.topInches + o.bottomInches) / 2;
            if (o.shape == "cylinder")
            {
                var solid = new GameObject(o.id) { layer = BallWorldLayer };
                solid.transform.SetParent(root.transform, false);
                solid.transform.localPosition = FtcToUnity.Position(o.xInches, o.yInches, FtcToUnity.Meters(center));
                var capsule = solid.AddComponent<CapsuleCollider>();
                capsule.direction = 1;
                capsule.radius = FtcToUnity.Meters(o.radiusInches);
                capsule.height = Mathf.Max(FtcToUnity.Meters(height), 2 * capsule.radius);
                capsule.material = material;
            }
            else
            {
                AddBox(root.transform, o.id, (o.minXInches + o.maxXInches) / 2, (o.minYInches + o.maxYInches) / 2, center,
                    o.maxXInches - o.minXInches, o.maxYInches - o.minYInches, height, material);
            }
        }

        private void AddCapsule(FlightObstacleData o, PhysicsMaterial material)
        {
            Vector3 from = FtcToUnity.Position(o.fromInches[0], o.fromInches[1], FtcToUnity.Meters(o.fromInches[2]));
            Vector3 to = FtcToUnity.Position(o.toInches[0], o.toInches[1], FtcToUnity.Meters(o.toInches[2]));
            var solid = new GameObject(o.id) { layer = BallWorldLayer };
            solid.transform.SetParent(root.transform, false);
            solid.transform.localPosition = (from + to) / 2;
            solid.transform.localRotation = Quaternion.FromToRotation(Vector3.up, to - from);
            var capsule = solid.AddComponent<CapsuleCollider>();
            capsule.direction = 1;
            capsule.radius = FtcToUnity.Meters(o.radiusInches);
            capsule.height = (to - from).magnitude + 2 * capsule.radius;
            capsule.material = material;
        }

        /// <summary>An upright box on the field (FTC inches: center x, y, z; size along x, y, z).</summary>
        private static void AddBox(Transform parent, string name, double x, double y, double z, double sizeX, double sizeY, double sizeZ, PhysicsMaterial material)
        {
            var solid = new GameObject(name) { layer = BallWorldLayer };
            solid.transform.SetParent(parent, false);
            solid.transform.localPosition = FtcToUnity.Position(x, y, FtcToUnity.Meters(z));
            var box = solid.AddComponent<BoxCollider>();
            box.size = new Vector3(FtcToUnity.Meters(sizeX), FtcToUnity.Meters(sizeZ), FtcToUnity.Meters(sizeY));
            box.material = material;
        }

        /// <summary>A box in a HIVE frame (inches: along, across, field y offset; sizes the same way).</summary>
        private static void AddLocalBox(Transform frame, string name, double along, double across, double y, double sizeAlong, double sizeAcross, double sizeY, PhysicsMaterial material)
        {
            var solid = new GameObject(name) { layer = BallWorldLayer };
            solid.transform.SetParent(frame, false);
            solid.transform.localPosition = new Vector3(FtcToUnity.Meters(along), FtcToUnity.Meters(across), FtcToUnity.Meters(y));
            var box = solid.AddComponent<BoxCollider>();
            box.size = new Vector3(FtcToUnity.Meters(sizeAlong), FtcToUnity.Meters(sizeAcross), FtcToUnity.Meters(sizeY));
            box.material = material;
        }

        /// <summary>No friction (the harness's balls slide and slow by rolling deceleration), and this bounce.</summary>
        private static PhysicsMaterial Material(string name, double bounce)
        {
            return new PhysicsMaterial(name)
            {
                dynamicFriction = 0, staticFriction = 0, bounciness = (float)bounce,
                frictionCombine = PhysicsMaterialCombine.Minimum, bounceCombine = PhysicsMaterialCombine.Minimum,
            };
        }

        private static PieceData Copy(PieceData p)
        {
            return new PieceData { id = p.id, kind = p.kind, state = p.state, xInches = p.xInches, yInches = p.yInches, zInches = p.zInches };
        }
    }

    /// <summary>
    /// The ball facts PhysX uses. Until the bridge sends the harness's PieceModel (bridge v6
    /// "pieceModel"), they are copied from it: all placeholders except the masses (HiveTip.MassGrams).
    /// </summary>
    public sealed class BallFacts
    {
        /// <summary>How fast a ball rolling on the tiles slows (in/s²). Placeholder in the harness.</summary>
        public double rollingDecelerationInchesPerSecondSquared;
        /// <summary>Bounce off a wall or field part. Placeholder.</summary>
        public double wallBounce;
        /// <summary>Bounce of two balls. Placeholder.</summary>
        public double pieceBounce;
        /// <summary>Bounce off the robot's bumper. Placeholder.</summary>
        public double robotBounce;
        /// <summary>Air drag on a flying POLLEN: it slows by this times its speed squared (1/in, D40). Placeholder.</summary>
        public double pollenDragPerInch;
        /// <summary>Air drag on a flying NECTAR, as for POLLEN (1/in). Placeholder.</summary>
        public double nectarDragPerInch;

        /// <summary>The harness's numbers (PieceModel.bioBuzz(), 2026-11-09).</summary>
        public static BallFacts Harness => new BallFacts
        {
            rollingDecelerationInchesPerSecondSquared = 15, wallBounce = 0.4, pieceBounce = 0.6, robotBounce = 0.2,
            pollenDragPerInch = 0.00115, nectarDragPerInch = 0.00114,
        };

        /// <summary>The air drag for a ball of this kind (1/in).</summary>
        public double DragPerInch(string kind) => kind == "pollen" ? pollenDragPerInch : nectarDragPerInch;
    }

    /// <summary>Where a ball PhysX moves is and how fast (FTC inches and in/s), sent to the harness each loop (bridge v6).</summary>
    public sealed class PieceMotionData
    {
        public string id;
        public double xInches;
        public double yInches;
        public double zInches;
        public double vxInchesPerSecond;
        public double vyInchesPerSecond;
        public double vzInchesPerSecond;
    }

    /// <summary>A ball Java just launched, released or spat out: where it starts and its velocity (FTC inches, in/s; bridge v6 "pieceLaunches").</summary>
    public sealed class PieceLaunchData
    {
        public string id;
        public double xInches;
        public double yInches;
        public double zInches;
        public double vxInchesPerSecond;
        public double vyInchesPerSecond;
        public double vzInchesPerSecond;
    }
}
