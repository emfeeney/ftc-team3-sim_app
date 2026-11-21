using System.Collections.Generic;
using System.Linq;
using NUnit.Framework;
using UnityEngine;
using UnityEngine.TestTools;

namespace VI_Arena.Tests
{
    /// <summary>
    /// The balls moved by Unity's physics (<see cref="UnityBallPhysics"/>, D30): rolling, bouncing, the
    /// robot, the CELLs and the HIVE tip, and handing balls over to and from the harness.
    /// </summary>
    public class UnityBallPhysicsTests
    {
        private const double Step = 0.02;
        private UnityBallPhysics physics;

        [TearDown]
        public void RemoveThePhysics()
        {
            physics?.Dispose();
            physics = null;
        }

        /// <summary>
        /// WHAT: A ball rolling at 30 in/s slows by the harness's 15 in/s² and has stopped after 2 s, having
        /// gone about 30 in (v²/2a), on the tiles.
        /// WHY: The same rolling as the harness's own physics, so a pushed ball stops at a similar spot.
        /// </summary>
        [Test]
        public void ABallRollsAndSlowsLikeTheHarness()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", 0, 0, 1.4) });
            Launch("a", "field", 0, 0, 1.4, vx: 30);

            Run(1.0);
            Assert.AreEqual(15, Motion("a").vxInchesPerSecond, 1.0, "half the speed after 1 s");
            Run(1.2);

            PieceMotionData end = Motion("a");
            Assert.AreEqual(0, end.vxInchesPerSecond, 0.01, "stopped");
            Assert.AreEqual(30, end.xInches, 1.5, "v²/2a = 30 in");
            Assert.AreEqual(1.4, end.zInches, 0.1, "on the tiles");
        }

        /// <summary>
        /// WHAT: A flying POLLEN thrown at 300 in/s slows by the air as in the harness: k v² against its velocity,
        /// so after 0.1 s it goes 300 / (1 + k 300 0.1) = about 290 in/s across (D40).
        /// WHY: Unity's and the harness's shots should travel the same distance; PhysX's own drag is a different law.
        /// </summary>
        [Test]
        public void AFlyingBallSlowsByTheAirLikeTheHarness()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "held", 0, 0, 40) });
            Launch("a", "flying", 0, 0, 40, vx: 300);

            Run(0.1);
            double k = BallFacts.Harness.pollenDragPerInch;
            Assert.AreEqual(300 / (1 + k * 300 * 0.1), Motion("a").vxInchesPerSecond, 0.3);
            Assert.Less(Motion("a").vxInchesPerSecond, 295, "the air slowed it");
        }

        /// <summary>
        /// WHAT: A ball rolling on the tiles isn't slowed by the air: after 0.1 s at 30 in/s it has lost exactly
        /// the rolling deceleration's 1.5 in/s.
        /// WHY: The harness's drag is for flying balls only (D40).
        /// </summary>
        [Test]
        public void ARollingBallFeelsNoAirDrag()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", 0, 0, 1.4) });
            Launch("a", "field", 0, 0, 1.4, vx: 30);

            Run(0.1);
            Assert.AreEqual(28.5, Motion("a").vxInchesPerSecond, 0.03);
        }

        /// <summary>WHAT: A ball dropped from 20 in falls and comes to rest on the tiles.</summary>
        [Test]
        public void ADroppedBallRestsOnTheTiles()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "flying", 0, 0, 20) });

            Run(3.0);

            Assert.AreEqual(1.4, Motion("a").zInches, 0.1);
        }

        /// <summary>WHAT: A ball rolling into a wall comes back at about the harness's 0.4 of its speed.</summary>
        [Test]
        public void ABallBouncesOffAWall()
        {
            double nearWall = FtcToUnity.FieldHalfWidthInches - 6;
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", nearWall, 0, 1.4) });
            Launch("a", "field", nearWall, 0, 1.4, vx: 50);

            Run(0.3);

            Assert.Less(Motion("a").vxInchesPerSecond, 0, "coming back");
            Assert.AreEqual(-0.4 * 50, Motion("a").vxInchesPerSecond, 6, "about 0.4 of 50 in/s, less a little rolling");
        }

        /// <summary>WHAT: A rolling ball pushes a ball at rest; balls hit each other.</summary>
        [Test]
        public void BallsHitEachOther()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", 0, 0, 1.4), Ball("b", "field", 10, 0, 1.4) });
            Launch("a", "field", 0, 0, 1.4, vx: 40);

            Run(0.6);

            Assert.Greater(Motion("b").xInches, 11, "b was pushed");
        }

        /// <summary>WHAT: The robot box pushes a ball it drives into.</summary>
        [Test]
        public void TheRobotPushesABall()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", 12, 0, 1.4) });
            for (int i = 0; i <= 25; i++)
            {
                // The robot (18 x 18 in) drives +x at 30 in/s from x = 0.
                physics.Apply(null, null, null, new PoseData { xInches = i * Step * 30 }, 18, 18);
                physics.Step(Step);
            }

            Assert.Greater(Motion("a").xInches, 12.5, "pushed ahead of the robot");
        }

        /// <summary>
        /// WHAT: A held ball sits exactly where Java says, isn't moved by PhysX, and doesn't collide (a ball
        /// rolling through it passes); it isn't sent back to Java.
        /// WHY: D30 (a): the harness moves a held ball, with collisions off.
        /// </summary>
        [Test]
        public void AHeldBallFollowsTheHarnessAndDoesNotCollide()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("held", "held", 10, 0, 1.4), Ball("rolling", "field", 0, 0, 1.4) });
            Launch("rolling", "field", 0, 0, 1.4, vx: 40);

            Run(0.6);

            Assert.AreEqual(10, FtcToUnity.Inches(physics.Ball("held").position.x), 1e-4, "where Java put it");
            Assert.Greater(Motion("rolling").xInches, 12, "rolled through it");
            Assert.IsFalse(physics.Motions().Any(m => m.id == "held"), "not sent back to Java");
            physics.Apply(new[] { Ball("held", "held", 20, 5, 7) }, null, null, null, 0, 0);
            Run(0.1);
            Assert.AreEqual(20, FtcToUnity.Inches(physics.Ball("held").position.x), 1e-4, "follows Java");
            Assert.AreEqual(7, FtcToUnity.Inches(physics.Ball("held").position.y), 1e-4, "and stays up there: gravity doesn't move a held ball");
        }

        /// <summary>
        /// WHAT: A launch hands the ball to PhysX at the launch's start point with Java's velocity; from then it
        /// flies under gravity. Before the launch it was held.
        /// </summary>
        [Test]
        public void ALaunchHandsTheBallToPhysX()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "held", 0, 0, 15) });
            physics.Apply(new[] { Ball("a", "flying", 0, 0, 15) }, new[] { new PieceLaunchData { id = "a", xInches = 5, zInches = 15, vxInchesPerSecond = 100, vzInchesPerSecond = 100 } }, null, null, 0, 0);

            physics.Step(Step);
            PieceMotionData m = Motion("a");

            Assert.AreEqual(100, m.vxInchesPerSecond, 0.5, "Java's speed along x");
            Assert.AreEqual(100 - 386.09 * Step, m.vzInchesPerSecond, 1.0, "Java's speed up, less one step of gravity");
            Assert.AreEqual(7, m.xInches, 0.1, "from the launch's start point (5 in), 100 in/s for 20 ms");
        }

        /// <summary>
        /// WHAT: A ball Java moves without a launch, while PhysX owns it (Java only renamed its state, say
        /// field to cell), stays where PhysX has it.
        /// WHY: PhysX is the source of truth for a loose ball; Java's copy is a loop old.
        /// </summary>
        [Test]
        public void ALooseBallStaysWherePhysXHasIt()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "field", 0, 0, 1.4) });
            Launch("a", "field", 0, 0, 1.4, vx: 20);
            Run(0.5);
            double x = Motion("a").xInches;

            physics.Apply(new[] { Ball("a", "cell", -30, 0, 1.4) }, null, null, null, 0, 0);

            Assert.AreEqual(x, Motion("a").xInches, 1e-3);
        }

        /// <summary>WHAT: A ball in the up CELL (the staged NECTAR's spot) stays in it; when the HIVE swings over, it spills out of the open end.</summary>
        [Test]
        public void ABallRestsInTheUpCellAndSpillsWhenTheHiveTips()
        {
            FieldLayoutData field = Biobuzz();
            HiveData red = field.hives.Single(h => h.id == "red-hive");
            physics = new UnityBallPhysics(field, new[] { new PieceData { id = "n", kind = "red-nectar", state = "cell", xInches = 9.57, yInches = -21.03, zInches = 49.86 } });

            for (int i = 0; i < 50; i++)
            {
                Swing(red, 0);
            }
            Assert.IsTrue(InCell(red, 1, Motion("n")), "still in the up CELL after 1 s");

            for (int i = 1; i <= 50; i++)
            {
                Swing(red, -60.0 * i / 50); // a 1 s tip
            }
            for (int i = 0; i < 50; i++)
            {
                Swing(red, -60);
            }

            Assert.IsFalse(InCell(red, 1, Motion("n")), "spilled out");
            Assert.Less(Motion("n").zInches, 30, "and fell");
        }

        /// <summary>
        /// WHAT: A ball PhysX loses (launched over the wall and out of the field) goes back to Java's last
        /// position, and the loss is logged.
        /// WHY: D30 (b): never drop a ball.
        /// </summary>
        [Test]
        public void ALostBallGoesBackToTheHarnessPosition()
        {
            physics = new UnityBallPhysics(null, new[] { Ball("a", "held", 60, 0, 15) });
            physics.Apply(new[] { Ball("a", "flying", 60, 0, 15) }, new[] { new PieceLaunchData { id = "a", xInches = 60, zInches = 15, vxInchesPerSecond = 600, vzInchesPerSecond = 300 } }, null, null, 0, 0);
            LogAssert.Expect(LogType.Warning, new System.Text.RegularExpressions.Regex("lost a"));

            Run(1.0);

            Assert.AreEqual(1, physics.LostBallsPutBack);
            Assert.IsFalse(UnityBallPhysics.IsLost(physics.Ball("a").position));
        }

        /// <summary>WHAT: Lost means not a number, under the tiles or well outside the field; on the field isn't.</summary>
        [Test]
        public void WhatCountsAsLost()
        {
            Assert.IsFalse(UnityBallPhysics.IsLost(FtcToUnity.Position(70, -70, FtcToUnity.Meters(1.4))));
            Assert.IsTrue(UnityBallPhysics.IsLost(FtcToUnity.Position(110, 0, FtcToUnity.Meters(1.4))));
            Assert.IsTrue(UnityBallPhysics.IsLost(FtcToUnity.Position(0, 0, FtcToUnity.Meters(-3))));
            Assert.IsTrue(UnityBallPhysics.IsLost(new Vector3(float.NaN, 0, 0)));
        }

        /// <summary>WHAT: Only loose balls are PhysX's: on the tiles, in the air, in a CELL.</summary>
        [Test]
        public void OnlyLooseBallsArePhysX()
        {
            Assert.IsTrue(UnityBallPhysics.OwnedByUnity("field"));
            Assert.IsTrue(UnityBallPhysics.OwnedByUnity("flying"));
            Assert.IsTrue(UnityBallPhysics.OwnedByUnity("cell"));
            Assert.IsFalse(UnityBallPhysics.OwnedByUnity("held"));
            Assert.IsFalse(UnityBallPhysics.OwnedByUnity("flower"));
            Assert.IsFalse(UnityBallPhysics.OwnedByUnity("alliance-area"));
        }

        /// <summary>
        /// WHAT: Hive Auto Red's shots hit the outside of the red upward CELL, under its opening, near x 24,
        /// and come back: they never get past x 15 toward the HIVE's middle while in the air. Both the
        /// harness's own launch from its recording, (42.85, -12.78, 19.30) at (-157.3, 0, 264.6) in/s, and
        /// the one it sent in Live with Unity physics, (46, -12.75, 14) at (-176, 0, 268.5) in/s.
        /// WHY: Unity's physics and the harness's must agree on what is solid; a ball must not fly through a
        /// CELL. (Where it rolls after landing may differ: the bounce off the CELL isn't the same.)
        /// </summary>
        [TestCase(42.85, -12.78, 19.30, -157.3, 264.6)]
        [TestCase(46.0, -12.75, 14.0, -176.0, 268.5)]
        public void HiveAutosShotsHitTheCellOutside(double x, double y, double z, double vx, double vz)
        {
            physics = new UnityBallPhysics(Biobuzz(), new[] { Ball("p", "held", x, y, z) });
            physics.Apply(new[] { Ball("p", "flying", x, y, z) },
                new[] { new PieceLaunchData { id = "p", xInches = x, yInches = y, zInches = z, vxInchesPerSecond = vx, vzInchesPerSecond = vz } },
                new[] { new HiveTiltData { id = "red-hive", swingDegrees = 0 } }, Shooter, 18, 18);

            double leastX = double.MaxValue, highest = 0;
            bool cameBack = false;
            var path = new System.Text.StringBuilder();
            for (int i = 0; i < 25; i++) // 0.5 s: up to the CELL and back down
            {
                physics.Apply(null, null, null, Shooter, 18, 18); // the robot stands where it shot, as in Live
                physics.Step(Step);
                PieceMotionData m = Motion("p");
                leastX = System.Math.Min(leastX, m.xInches);
                highest = System.Math.Max(highest, m.zInches);
                cameBack |= m.vxInchesPerSecond > 0;
                path.AppendFormat(System.Globalization.CultureInfo.InvariantCulture, "({0:0.0}, {1:0.0}) ", m.xInches, m.zInches);
            }

            Assert.Greater(leastX, 15, "the ball went past the CELL: " + path);
            Assert.IsTrue(cameBack, "it hit something and came back: " + path);
            Assert.Less(highest, 60, "stopped under the CELL, not flying over it: " + path);
        }

        /// <summary>
        /// WHAT: A 300 in/s shot whose path clears the red raised CELL's lower lip corner by 0.5 in (center 1.9 in from
        /// it, the ball's radius 1.4 in) goes on past it, into the CELL: it isn't turned back by an early contact.
        /// WHY: C-U6 (manager decision D51): with one 20 ms PhysX step a shot moved about 5 in at once, and PhysX's
        /// speculative contact bounced Corner Lock's shots off the lip about 0.85 in before they reached it.
        /// </summary>
        [Test]
        public void AShotThatClearsTheCellLipIsNotTurnedBackEarly()
        {
            // The lip's lower outer corner (the floor slab's underside at the opening), in field x, z.
            const double cornerX = 20.07, cornerZ = 52.11;
            // The path: up and toward the HIVE at 300 in/s, 9 in before its nearest point to the corner, which is
            // 1.9 in out from the corner on the CELL's side; a little higher to allow for gravity's 0.17 in.
            double dx = -0.588, dz = 0.809, nx = 0.809, nz = 0.588;
            double nearX = cornerX + 1.9 * nx, nearZ = cornerZ + 1.9 * nz;
            double x = nearX - 9 * dx, y = -12.2, z = nearZ - 9 * dz + 0.17;
            physics = new UnityBallPhysics(Biobuzz(), new[] { Ball("p", "held", x, y, z) });
            physics.Apply(new[] { Ball("p", "flying", x, y, z) },
                new[] { new PieceLaunchData { id = "p", xInches = x, yInches = y, zInches = z, vxInchesPerSecond = 300 * dx, vzInchesPerSecond = 300 * dz } },
                new[] { new HiveTiltData { id = "red-hive", swingDegrees = 0 } }, Shooter, 18, 18);

            physics.Step(Step);
            physics.Step(Step);   // 12 in along: 3 in past the corner
            PieceMotionData m = Motion("p");

            Assert.Less(m.vxInchesPerSecond, -150, "still going toward the HIVE, not turned back: " + m.xInches + ", " + m.zInches);
            Assert.Less(m.xInches, nearX, "past the lip");
        }

        /// <summary>
        /// WHAT: Each CELL's floor is solid from the field file's underside (-2.97 in, the CAD) up to its inside
        /// floor (-1.47 in), across the CELL's depth, the same slab as the harness's (manager decision D31).
        /// </summary>
        [Test]
        public void TheCellFloorIsSolidDownToItsUnderside()
        {
            FieldLayoutData field = Biobuzz();
            physics = new UnityBallPhysics(field, new PieceData[0]);
            foreach (HiveData hive in field.hives)
            {
                foreach (CellData cell in hive.cells)
                {
                    Assert.AreEqual(-2.97, cell.floorUndersideInches, 1e-9, cell.id + " in the field file");
                    BoxCollider floor = Object.FindObjectsByType<BoxCollider>(FindObjectsSortMode.None).Single(b => b.name == cell.id + " floor");
                    double bottom = FtcToUnity.Inches(floor.transform.localPosition.y - floor.size.y / 2);
                    double top = FtcToUnity.Inches(floor.transform.localPosition.y + floor.size.y / 2);
                    Assert.AreEqual(-2.97, bottom, 1e-3, cell.id + " underside");
                    Assert.AreEqual(-1.47, top, 1e-3, cell.id + " inside floor");
                    Assert.AreEqual(cell.openingInches - cell.backInches, FtcToUnity.Inches(floor.size.x), 1e-3, cell.id + " back wall to opening");
                }
            }
        }

        /// <summary>
        /// WHAT: The walls, the HIVE parts and the CELLs bounce a ball with the harness's wall bounce, the robot
        /// with its robot bounce, the balls with each other with its ball bounce, all with no friction (the
        /// harness's rolling deceleration slows a ball instead); the tiles don't bounce.
        /// WHY: Manager decision D31: PhysX uses the harness's numbers (sent in "loaded"'s pieceModel), not its defaults.
        /// </summary>
        [Test]
        public void EverythingUsesTheHarnessBounces()
        {
            var facts = new BallFacts { rollingDecelerationInchesPerSecondSquared = 15, wallBounce = 0.7, pieceBounce = 0.55, robotBounce = 0.15 };
            physics = new UnityBallPhysics(Biobuzz(), new[] { Ball("p", "field", 0, 30, 1.4) }, facts);
            physics.Apply(null, null, null, new PoseData(), 18, 18);
            Collider[] all = Object.FindObjectsByType<Collider>(FindObjectsSortMode.None);

            foreach (string name in new[] { "Wall +x", "red-cell-audience floor", "red-cell-far back", "red-hive-arm", "frame-leg-red-audience", "frame-top-bar" })
            {
                PhysicsMaterial m = all.Single(c => c.name == name).sharedMaterial;
                Assert.AreEqual(0.7f, m.bounciness, 1e-5f, name);
                Assert.AreEqual(0f, m.dynamicFriction, 1e-5f, name + " friction");
            }
            Assert.AreEqual(0.15f, all.Single(c => c.name == "Robot (for the balls)").sharedMaterial.bounciness, 1e-5f, "robot");
            Assert.AreEqual(0.55f, physics.Ball("p").GetComponent<Collider>().sharedMaterial.bounciness, 1e-5f, "ball");
            Assert.AreEqual(0f, all.Single(c => c.name == "Tiles").sharedMaterial.bounciness, 1e-5f, "tiles");
        }

        /// <summary>Hive Auto Red's robot when it shoots: at (52, -12.75), facing -x (18 x 18 in).</summary>
        private static PoseData Shooter => new PoseData { xInches = 52, yInches = -12.75, headingRadians = System.Math.PI };

        private void Swing(HiveData hive, double degrees)
        {
            physics.Apply(null, null, new[] { new HiveTiltData { id = hive.id, swingDegrees = degrees } }, null, 0, 0);
            physics.Step(Step);
        }

        /// <summary>True if a ball's center is inside a CELL's box (in the HIVE's frame at its starting tilt; the HIVE assumed at its start or tipped, by side only).</summary>
        private static bool InCell(HiveData hive, int side, PieceMotionData m)
        {
            CellData cell = hive.cells.Single(c => c.side == side);
            double a = hive.startUpSide * hive.tiltDegrees * Mathf.Deg2Rad;
            double dx = m.xInches - hive.pivotXInches, dz = m.zInches - hive.pivotZInches;
            double along = (dx * System.Math.Cos(a) + dz * System.Math.Sin(a)) * side;
            double across = -dx * System.Math.Sin(a) + dz * System.Math.Cos(a);
            return along > cell.backInches - 0.5 && along < cell.openingInches && across > cell.floorInches - 0.5 && across < cell.topInches
                && m.yInches > cell.minYInches - 0.5 && m.yInches < cell.maxYInches + 0.5;
        }

        private void Run(double seconds)
        {
            for (int i = 0; i < System.Math.Round(seconds / Step); i++)
            {
                physics.Step(Step);
            }
        }

        private void Launch(string id, string state, double x, double y, double z, double vx = 0, double vy = 0, double vz = 0)
        {
            physics.Apply(new[] { Ball(id, state, x, y, z) },
                new[] { new PieceLaunchData { id = id, xInches = x, yInches = y, zInches = z, vxInchesPerSecond = vx, vyInchesPerSecond = vy, vzInchesPerSecond = vz } }, null, null, 0, 0);
        }

        private PieceMotionData Motion(string id) => physics.Motions().Single(m => m.id == id);

        private static PieceData Ball(string id, string state, double x, double y, double z)
        {
            return new PieceData { id = id, kind = "pollen", state = state, xInches = x, yInches = y, zInches = z };
        }

        private static FieldLayoutData Biobuzz()
        {
            string folder = FieldLayouts.ResolveFolder(new string[0], TestHarnessFolder.Search(Application.dataPath), Application.dataPath);
            return FieldLayouts.LoadAll(folder, new List<string>()).Single(f => f.id == "biobuzz-2026");
        }
    }
}
