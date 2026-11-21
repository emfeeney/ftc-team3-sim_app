using System.Collections.Generic;
using System.IO;
using System.Linq;
using NUnit.Framework;
using UnityEngine;
using UnityEngine.TestTools;

namespace VI_Arena.Tests
{
    /// <summary>
    /// When a HIVE tips (<see cref="HiveTip"/>), where it is at each loop (<see cref="HiveTipTrack"/>), and
    /// that the field really turns about the pivot (<see cref="FieldBuilder.BuildSeason"/>).
    /// </summary>
    public class HiveTipTests
    {
        private const double Tolerance = 1e-3;

        /// <summary>WHAT: The weight is POLLEN 24.9 g and NECTAR 41.3 g, from the harness's PieceModel.</summary>
        [Test]
        public void BallsWeighWhatThePieceModelSays()
        {
            Assert.AreEqual(24.9, HiveTip.MassGrams("pollen"));
            Assert.AreEqual(41.3, HiveTip.MassGrams("red-nectar"));
            Assert.AreEqual(41.3, HiveTip.MassGrams("blue-nectar"));
        }

        /// <summary>
        /// WHAT: With the 200 g in the field file, the 3 staged NECTAR hold, a robot's 4 pre-loaded POLLEN on
        /// top tip it, and 3 POLLEN don't. From empty, 9 POLLEN or 5 NECTAR tip it and 8 or 4 don't.
        /// WHY: The staged balls must never tip a HIVE by themselves, and these are the combinations people will ask about.
        /// </summary>
        [Test]
        public void TheCombinationsTipTheWayTheFieldFileSays()
        {
            HiveData red = Biobuzz().hives.Single(h => h.id == "red-hive");

            Assert.IsFalse(Tips(red, 3, 0), "the 3 staged NECTAR");
            Assert.IsFalse(Tips(red, 3, 3), "3 NECTAR + 3 POLLEN: 198.6 g");
            Assert.IsTrue(Tips(red, 3, 4), "3 NECTAR + 4 POLLEN: 223.5 g");
            Assert.IsFalse(Tips(red, 0, 8), "8 POLLEN: 199.2 g");
            Assert.IsTrue(Tips(red, 0, 9), "9 POLLEN: 224.1 g");
            Assert.IsFalse(Tips(red, 4, 0), "4 NECTAR: 165.2 g");
            Assert.IsTrue(Tips(red, 5, 0), "5 NECTAR: 206.5 g");
        }

        /// <summary>WHAT: Exactly the limit tips; just under doesn't.</summary>
        [Test]
        public void TheLimitItselfTips()
        {
            HiveData hive = Hive(startUpSide: 1);

            Assert.IsTrue(HiveTip.Tips(hive, hive.tipMassGrams));
            Assert.IsFalse(HiveTip.Tips(hive, hive.tipMassGrams - 0.01));
        }

        /// <summary>WHAT: Only a "cell" ball inside the HIVE's band counts, and the pivot's side says which CELL.</summary>
        [Test]
        public void OnlyBallsInTheCellCount()
        {
            HiveData hive = Hive(startUpSide: 1);

            Assert.AreEqual(1, HiveTip.CellSide(hive, Ball("a", "cell", x: 10, y: -20)));
            Assert.AreEqual(-1, HiveTip.CellSide(hive, Ball("b", "cell", x: -10, y: -20)));
            Assert.AreEqual(0, HiveTip.CellSide(hive, Ball("c", "field", x: 10, y: -20)), "on the tiles under the CELL");
            Assert.AreEqual(0, HiveTip.CellSide(hive, Ball("d", "cell", x: 10, y: 20)), "in the other HIVE");
            Assert.AreEqual(0, HiveTip.CellSide(hive, null));
            Assert.AreEqual(2 * 24.9, HiveTip.MassInCell(hive, new[] { Ball("a", "cell", 10, -20), Ball("b", "cell", 12, -20), Ball("c", "cell", -10, -20) }, 1), Tolerance);
        }

        /// <summary>
        /// WHAT: Weight in the downward CELL does nothing, and the balls of one HIVE don't tip the other.
        /// WHY: Only the upward CELL tips a HIVE (manual 9.5).
        /// </summary>
        [Test]
        public void OnlyTheUpwardCellOfThatHiveTips()
        {
            HiveData up = Hive(startUpSide: 1);
            HiveData other = Hive(startUpSide: -1);
            other.minYInches = 2;
            other.maxYInches = 24;
            RunRecordingData run = Run(Balls("cell", -10, -20, 9));

            var track = new HiveTipTrack(new[] { up, other }, run, new GamePieceTrack(run));

            Assert.AreEqual(0, track.TipsBy(0, 0), "all the weight is in the downward CELL");
            Assert.AreEqual(0, track.TipsBy(1, 0), "and it's in the other HIVE");
        }

        /// <summary>
        /// WHAT: The HIVE stays until the weight arrives, then swings over tipSeconds with a smooth start and
        /// end, and stays. Red, whose +x CELL starts up, turns -60 degrees; blue +60.
        /// </summary>
        [Test]
        public void TheHiveSwingsOverOnceAndStays()
        {
            RunRecordingData run = Run(new List<PieceData>(), new List<PieceData>(), Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>());
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));

            Assert.AreEqual(0, track.SwingDegrees(0, 1), "before the balls");
            Assert.AreEqual(0, track.SwingDegrees(0, 2), "the loop it starts");
            Assert.AreEqual(-30, track.SwingDegrees(0, 3), Tolerance, "half way, 0.5 of 1 s");
            Assert.AreEqual(-60, track.SwingDegrees(0, 4), Tolerance, "done");
            Assert.AreEqual(-60, track.SwingDegrees(0, 5), Tolerance, "and it stays, though the balls are still there");
            Assert.AreEqual(60, HiveTip.FullSwingDegrees(Hive(startUpSide: -1)));
            Assert.AreEqual(1, track.TipsBy(0, 5));
        }

        /// <summary>
        /// WHAT: After a tip the other CELL is up, so the balls that tipped it don't tip it back, and enough
        /// weight in the new upward CELL tips it back to the start.
        /// </summary>
        [Test]
        public void TheOtherCellTipsItBack()
        {
            RunRecordingData run = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>(), Balls("cell", -10, -20, 9, "b"), new List<PieceData>(), new List<PieceData>());
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));

            Assert.AreEqual(1, track.TipsBy(0, 3), "the first CELL's balls don't tip it again");
            Assert.AreEqual(-60, track.SwingDegrees(0, 3), Tolerance);
            Assert.AreEqual(2, track.TipsBy(0, 4));
            Assert.AreEqual(0, track.SwingDegrees(0, 6), Tolerance, "back at the start");
            Assert.AreEqual(2, track.TipsBy(0, 6), "and it stays: the first balls went down with the first tip");
        }

        /// <summary>
        /// WHAT: A ball that went down with a tip counts again once the harness has taken it out of the CELL
        /// and put it back.
        /// WHY: When the harness spills balls and a robot launches them again, they must count.
        /// </summary>
        [Test]
        public void ABallThatCameBackCountsAgain()
        {
            RunRecordingData run = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(),
                Balls("field", 10, -20, 9), Balls("cell", -10, -20, 9, "b"), new List<PieceData>(), new List<PieceData>(), Balls("cell", 10, -20, 9));
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));

            Assert.AreEqual(2, track.TipsBy(0, 6), "tipped and tipped back");
            Assert.AreEqual(3, track.TipsBy(0, 7), "the same balls, back in the upward CELL");
        }

        /// <summary>WHAT: A HIVE that is still swinging finishes before it can tip again.</summary>
        [Test]
        public void ASwingingHiveFinishesFirst()
        {
            RunRecordingData run = Run(Balls("cell", 10, -20, 9), Balls("cell", -10, -20, 9, "b"), new List<PieceData>(), new List<PieceData>());
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));

            Assert.AreEqual(1, track.TipsBy(0, 1), "0.5 s after the first tip, weight already in the new upward CELL");
            Assert.AreEqual(2, track.TipsBy(0, 2), "tips back once it has settled");
        }

        /// <summary>WHAT: Jumping around gives the same answers as playing in order, and a live run's new loops carry on.</summary>
        [Test]
        public void JumpingAroundAndLiveLoopsGiveTheSameAnswers()
        {
            RunRecordingData run = Run(new List<PieceData>(), Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>());
            // A live run's header lists every ball from the start.
            List<PieceData> more = Balls("cell", -10, -20, 9, "b");
            run.header.pieces.AddRange(more);
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));

            Assert.AreEqual(-60, track.SwingDegrees(0, 3), Tolerance, "jump to the end first");
            Assert.AreEqual(0, track.SwingDegrees(0, 0), "then back before the tip");
            Assert.AreEqual(-30, track.SwingDegrees(0, 2), Tolerance);

            run.loops.Add(new LoopData { sinceInitSeconds = 2.0, pieces = more });
            Assert.AreEqual(2, track.TipsBy(0, 4), "a new live loop");
        }

        /// <summary>
        /// WHAT: Balls in a tipped CELL turn with it about the pivot; other balls stay; before the tip, the
        /// same array is drawn. 60 degrees down: a ball 10 in out and level with the pivot ends 5 in out, 8.66 in down.
        /// </summary>
        [Test]
        public void BallsInTheCellMoveWithTheHive()
        {
            var inCell = new PieceData { id = "a", kind = "pollen", state = "cell", xInches = 10, yInches = -20, zInches = 44 };
            var loose = new PieceData { id = "b", kind = "pollen", state = "field", xInches = 10, yInches = -20, zInches = 1.4 };
            PieceData[] states = { inCell, loose };
            RunRecordingData run = Run(new List<PieceData>(), new List<PieceData>());
            var heavy = Hive(startUpSide: 1);
            RunRecordingData tipped = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>());

            Assert.AreSame(states, new HiveTipTrack(new[] { heavy }, run, new GamePieceTrack(run)).PiecesToShow(1, states), "not moved");

            PieceData[] shown = new HiveTipTrack(new[] { heavy }, tipped, new GamePieceTrack(tipped)).PiecesToShow(3, states);

            Assert.AreEqual(5, shown[0].xInches, Tolerance);
            Assert.AreEqual(44 - 8.660, shown[0].zInches, Tolerance);
            Assert.AreEqual(-20, shown[0].yInches, Tolerance);
            Assert.AreSame(loose, shown[1], "a loose ball stays where the recording says");
            Assert.AreEqual(10, inCell.xInches, "the recording's own ball isn't changed");
        }

        /// <summary>WHAT: An AprilTag on a HIVE turns with it, position and facing; tags not on a HIVE, and an unmoved HIVE's, stay.</summary>
        [Test]
        public void TagsOnAHiveTurnWithIt()
        {
            HiveData hive = Hive(startUpSide: 1);
            var onHive = new FieldTagData { id = 1, moves = true, sizeInches = 3, xInches = 10, yInches = -20, zInches = 44, facing = new FieldVectorData { x = 0, y = 0, z = -1 } };
            var fixedTag = new FieldTagData { id = 2, moves = false, sizeInches = 3, xInches = 10, yInches = -20, zInches = 44, facing = new FieldVectorData { x = 0, y = 0, z = -1 } };

            List<FieldTagData> moved = HiveTip.TagsAt(new[] { onHive, fixedTag }, new[] { hive }, new[] { -90.0 });

            Assert.AreEqual(0, moved[0].xInches.Value, Tolerance);
            Assert.AreEqual(34, moved[0].zInches.Value, Tolerance);
            Assert.AreEqual(-1, moved[0].facing.x, Tolerance, "the side that faced down now faces -x");
            Assert.AreEqual(0, moved[0].facing.z, Tolerance);
            Assert.AreSame(fixedTag, moved[1]);
            Assert.AreSame(onHive, HiveTip.TagsAt(new[] { onHive }, new[] { hive }, new[] { 0.0 })[0], "not swung");
        }

        /// <summary>
        /// WHAT: The field file's HIVEs match the CAD and the manual: pivots 43.95 in up, 30 degrees, red's
        /// audience CELL and blue's far CELL up, every CAD part and shape named really exists, and each
        /// HIVE's staged NECTAR is in its upward CELL.
        /// WHY: A wrong side would swing a HIVE through the frame or read the wrong CELL.
        /// </summary>
        [Test]
        public void TheFieldFileMatchesTheField()
        {
            FieldLayoutData field = Biobuzz();
            Assert.AreEqual(2, field.hives.Count);
            HiveData red = field.hives.Single(h => h.id == "red-hive"), blue = field.hives.Single(h => h.id == "blue-hive");
            foreach (HiveData hive in field.hives)
            {
                Assert.AreEqual(43.95, hive.pivotZInches, Tolerance, hive.id);
                Assert.AreEqual(0, hive.pivotXInches, Tolerance, hive.id);
                Assert.AreEqual(30, hive.tiltDegrees, Tolerance, hive.id);
                foreach (string part in hive.cadParts)
                {
                    Assert.IsNotNull(Resources.Load<GameObject>(FieldBuilder.CadFolder("biobuzz-2026") + "/" + part), part);
                }
                foreach (string element in hive.elementIds)
                {
                    Assert.IsTrue(field.elements.Exists(e => e.id == element), element);
                }
            }
            Assert.AreEqual(1, red.startUpSide, "red's audience CELL is up");
            Assert.AreEqual(-1, blue.startUpSide, "blue's far CELL is up");
            var staged = new[]
            {
                new PieceData { kind = "red-nectar", state = "cell", xInches = 9.57, yInches = -21.03 },
                new PieceData { kind = "blue-nectar", state = "cell", xInches = -9.57, yInches = 21.03 },
            };
            Assert.AreEqual(41.3, HiveTip.MassInCell(red, staged, HiveTip.UpSide(red, 0)), Tolerance);
            Assert.AreEqual(41.3, HiveTip.MassInCell(blue, staged, HiveTip.UpSide(blue, 0)), Tolerance);
            Assert.AreEqual(16, field.aprilTags.Count(t => HiveTip.HiveOfTag(field.hives, t) >= 0), "all 16 tags ride on a HIVE");
        }

        /// <summary>
        /// WHAT: Turning a HIVE's pivot in the built field moves its CAD, its tags and nothing else, the way
        /// <see cref="HiveTip.Turn"/> says: a tipped red HIVE's tags land where the rule puts them, and the frame stays.
        /// WHY: The rule and the picture must agree, or balls and tags would come off the HIVE.
        /// </summary>
        [Test]
        public void TurningThePivotMovesTheHiveAboutItsAxle()
        {
            LogAssert.ignoreFailingMessages = true;
            FieldLayoutData field = Biobuzz();
            var parent = new GameObject("Test field").transform;
            try
            {
                Transform root = FieldBuilder.BuildSeason(parent, field);
                HiveData red = field.hives.Single(h => h.id == "red-hive");
                Transform pivot = FieldBuilder.HivePivot(root, red);
                Assert.IsNotNull(pivot);
                AssertNear(FtcToUnity.Position(0, red.pivotYInches, FtcToUnity.Meters(43.95)), pivot.position, "on the axle");
                foreach (string part in red.cadParts)
                {
                    Assert.IsNotNull(pivot.Find(part), part + " rides on the pivot");
                }
                Transform frame = root.GetComponentsInChildren<Transform>().Single(t => t.name == "hive-frame");
                Vector3 frameBefore = frame.position;
                Transform tag = pivot.Find("AprilTag 34");
                Assert.IsNotNull(tag, "tag 34 rides on the pivot");
                FieldTagData data = field.aprilTags.Single(t => t.id == 34);
                float facingOffset = 0.001f; // AddTag sits it 1 mm in front of the CAD's plate.

                FieldBuilder.ShowHive(root, red, HiveTip.FullSwingDegrees(red));

                HiveTip.Turn(red, HiveTip.FullSwingDegrees(red), data.xInches.Value, data.zInches.Value, out double x, out double z);
                FieldTagData moved = HiveTip.TagsAt(new[] { data }, new[] { red }, new[] { HiveTip.FullSwingDegrees(red) })[0];
                Vector3 expected = FtcToUnity.Position(x, data.yInches.Value, FtcToUnity.Meters(z))
                    + FtcToUnity.Direction(moved.facing.x, moved.facing.y, moved.facing.z).normalized * facingOffset;
                AssertNear(expected, tag.position, "the tag lands where the rule says");
                AssertNear(frameBefore, frame.position, "the frame stays");
            }
            finally
            {
                Object.DestroyImmediate(parent.gameObject);
            }
        }

        /// <summary>
        /// WHAT: With the HIVE tipped, the CAD's axle stays put and the CELL that was up is where the rule puts it.
        /// WHY: A wrong turn direction would show as the HIVE swinging further up instead of over.
        /// </summary>
        [Test]
        public void TippingPutsTheOtherCellUp()
        {
            FieldLayoutData field = Biobuzz();
            foreach (HiveData hive in field.hives)
            {
                // A point 15 in out on the side that starts up, 30 degrees above level.
                double outX = hive.startUpSide * 15 * Mathf.Cos(30 * Mathf.Deg2Rad);
                double upZ = hive.pivotZInches + 15 * Mathf.Sin(30 * Mathf.Deg2Rad);

                HiveTip.Turn(hive, HiveTip.FullSwingDegrees(hive), outX, upZ, out double x, out double z);

                Assert.AreEqual(hive.startUpSide * 15 * Mathf.Cos(30 * Mathf.Deg2Rad), x, Tolerance, hive.id + " same side");
                Assert.AreEqual(hive.pivotZInches - 15 * Mathf.Sin(30 * Mathf.Deg2Rad), z, Tolerance, hive.id + " 30 degrees below level now");
            }
        }

        /// <summary>
        /// WHAT: A version 6 recording carries each HIVE's tilt: the HIVE is drawn exactly there, balls are
        /// drawn where the recording says (not turned again), and VI Arena's own rule isn't used, even when
        /// the weight would tip it. A loop without the HIVE keeps its last tilt.
        /// WHY: From version 6 the harness decides the tips (D29), so replay, Live and tests agree.
        /// </summary>
        [Test]
        public void AVersion6RecordingIsDrawnAsRecorded()
        {
            RunRecordingData run = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>());
            run.header.version = 6;
            run.header.hives.Add(new HiveTiltData { id = "test", swingDegrees = 0 });
            run.loops[1].hives.Add(new HiveTiltData { id = "test", swingDegrees = -30 });
            run.loops[2].hives.Add(new HiveTiltData { id = "test", tips = 1, swingDegrees = -60 });
            var track = new HiveTipTrack(new[] { Hive(startUpSide: 1) }, run, new GamePieceTrack(run));
            PieceData[] states = new GamePieceTrack(run).At(3);

            Assert.IsTrue(track.Recorded);
            Assert.AreEqual(0, track.SwingDegrees(0, 0), Tolerance, "the header's tilt, though 9 POLLEN are in the up CELL");
            Assert.AreEqual(-30, track.SwingDegrees(0, 1), Tolerance, "level");
            Assert.AreEqual(-60, track.SwingDegrees(0, 2), Tolerance, "tipped");
            Assert.AreEqual(-60, track.SwingDegrees(0, 3), Tolerance, "a loop without it keeps the last swing");
            Assert.AreSame(states, track.PiecesToShow(3, states), "balls are drawn where the recording says");
        }

        /// <summary>WHAT: Older recordings still use VI Arena's own rule; a version 6 one without any tilt yet keeps the starting position.</summary>
        [Test]
        public void OlderRecordingsKeepTheirOwnRule()
        {
            RunRecordingData old = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>());
            old.header.version = 5;
            RunRecordingData empty = Run(Balls("cell", 10, -20, 9), new List<PieceData>(), new List<PieceData>(), new List<PieceData>());
            empty.header.version = 6;

            Assert.AreEqual(-60, new HiveTipTrack(new[] { Hive(startUpSide: 1) }, old, new GamePieceTrack(old)).SwingDegrees(0, 3), Tolerance);
            Assert.AreEqual(0, new HiveTipTrack(new[] { Hive(startUpSide: 1) }, empty, new GamePieceTrack(empty)).SwingDegrees(0, 3), Tolerance);
        }

        /// <summary>
        /// WHAT: The CELL counts: in a version 6 recording a ball is counted in the CELL it is really in,
        /// even with the HIVE tipped; which CELL is up flips with the tilt; mid-swing it reads "tipping".
        /// </summary>
        [Test]
        public void CellsAreCountedWhereTheBallsReallyAre()
        {
            HiveData hive = Hive(startUpSide: 1);
            // A ball in the +x CELL with the HIVE tipped to -30: turned 60 degrees down from its start spot.
            HiveTip.Turn(hive, -60, 12, 50, out double x, out double z);
            var inTipped = new PieceData { id = "a", kind = "pollen", state = "cell", xInches = x, yInches = -20, zInches = z };
            var nectar = new PieceData { id = "b", kind = "red-nectar", state = "cell", xInches = -12, yInches = -20, zInches = 38 };
            RunRecordingData run = Run(new List<PieceData>(), new List<PieceData>());
            run.header.version = 6;
            run.loops[0].hives.Add(new HiveTiltData { id = "test", swingDegrees = -30 });
            run.loops[1].hives.Add(new HiveTiltData { id = "test", tips = 1, swingDegrees = -60 });
            var track = new HiveTipTrack(new[] { hive }, run, new GamePieceTrack(run));
            PieceData[] states = { inTipped, nectar };

            track.CountCell(0, 1, 1, states, out int n1, out int p1);
            track.CountCell(0, -1, 1, states, out int n2, out int p2);

            Assert.AreEqual((0, 1), (n1, p1), "the POLLEN, tipped down with its CELL");
            Assert.AreEqual((1, 0), (n2, p2), "the NECTAR");
            Assert.AreEqual(-1, track.UpSideAt(0, 1), "tipped: the -x CELL is up");
            Assert.AreEqual(0, track.UpSideAt(0, 0), "level: swinging");
        }

        /// <summary>WHAT: The card's line: HIVE, CELL, up/down/tipping and what's in it.</summary>
        [Test]
        public void TheCellLineSaysWhatIsInIt()
        {
            Assert.AreEqual("Red  audience  up       3 NECTAR, 4 POLLEN", HudText.CellLine("Red HIVE", 1, 1, 3, 4));
            Assert.AreEqual("Blue far       down     empty", HudText.CellLine("Blue HIVE", -1, 1, 0, 0));
            Assert.AreEqual("Red  far       tipping  2 POLLEN", HudText.CellLine("Red HIVE", -1, 0, 0, 2));
        }

        /// <summary>
        /// WHAT: The field file's CELLs hold the staged NECTAR the way the manual places them: each NECTAR
        /// touches its CELL's floor and back wall (within 0.05 in) and sits inside its sides, in the up CELL.
        /// WHY: The CELL numbers were measured from the CAD; the manual's staging is an independent check, and
        /// the harness's ball physics uses these walls.
        /// </summary>
        [Test]
        public void TheCellsHoldTheStagedNectar()
        {
            FieldLayoutData field = Biobuzz();
            var staged = new[] { (hive: "red-hive", x: 9.57, y: -21.03, z: 49.86), (hive: "blue-hive", x: -9.57, y: 21.03, z: 49.86) };
            foreach (var nectar in staged)
            {
                HiveData hive = field.hives.Single(h => h.id == nectar.hive);
                CellData up = hive.cells.Single(c => c.side == hive.startUpSide);
                double arm = hive.startUpSide * hive.tiltDegrees * Mathf.Deg2Rad;
                double dx = nectar.x - hive.pivotXInches, dz = nectar.z - hive.pivotZInches;
                double along = (dx * System.Math.Cos(arm) + dz * System.Math.Sin(arm)) * up.side;
                double across = -dx * System.Math.Sin(arm) + dz * System.Math.Cos(arm);
                const double radius = 1.8;

                Assert.AreEqual(up.backInches, along - radius, 0.05, hive.id + ": against the back wall");
                Assert.AreEqual(up.floorInches, across - radius, 0.05, hive.id + ": on the floor");
                Assert.AreEqual(nectar.y < 0 ? up.minYInches : up.maxYInches, nectar.y < 0 ? nectar.y - radius : nectar.y + radius, 0.05,
                    hive.id + ": against the side nearest its alliance");
                Assert.Less(along + radius, up.openingInches, hive.id + ": inside the opening");
                Assert.Less(across + radius, up.topInches, hive.id + ": under the top");
                Assert.AreEqual(14.0, up.topInches - up.floorInches, 0.01, "the manual's 14 in opening");
            }
        }

        /// <summary>WHAT: The field file's flight obstacles all have a shape the harness knows, and the arms swing with their HIVE.</summary>
        [Test]
        public void TheFlightObstaclesAreWellFormed()
        {
            FieldLayoutData field = Biobuzz();
            Assert.Greater(field.flightObstacles.Count, 0);
            foreach (FlightObstacleData o in field.flightObstacles)
            {
                switch (o.shape)
                {
                    case "capsule":
                        Assert.AreEqual(3, o.fromInches.Count, o.id);
                        Assert.AreEqual(3, o.toInches.Count, o.id);
                        Assert.Greater(o.radiusInches, 0, o.id);
                        break;
                    case "box":
                        for (int i = 0; i < 3; i++)
                        {
                            Assert.Less(o.minInches[i], o.maxInches[i], o.id);
                        }
                        break;
                    case "hiveBox":
                        Assert.IsTrue(field.hives.Exists(h => h.id == o.hive), o.id + " names a HIVE");
                        Assert.Less(o.minAlongInches, o.maxAlongInches, o.id);
                        Assert.Less(o.minAcrossInches, o.maxAcrossInches, o.id);
                        break;
                    default:
                        Assert.Fail(o.id + ": unknown shape " + o.shape);
                        break;
                }
            }
        }

        private static bool Tips(HiveData hive, int nectar, int pollen)
        {
            var balls = new List<PieceData>();
            for (int i = 0; i < nectar; i++)
            {
                balls.Add(new PieceData { kind = "red-nectar", state = "cell", xInches = 10, yInches = -20 });
            }
            for (int i = 0; i < pollen; i++)
            {
                balls.Add(new PieceData { kind = "pollen", state = "cell", xInches = 10, yInches = -20 });
            }
            return HiveTip.Tips(hive, HiveTip.MassInCell(hive, balls, HiveTip.UpSide(hive, 0)));
        }

        private static HiveData Hive(int startUpSide)
        {
            return new HiveData
            {
                id = "test", pivotXInches = 0, pivotYInches = -12.75, pivotZInches = 44, minYInches = -24, maxYInches = -2,
                tiltDegrees = 30, startUpSide = startUpSide, tipMassGrams = 200, tipSeconds = 1,
            };
        }

        private static PieceData Ball(string id, string state, double x, double y)
        {
            return new PieceData { id = id, kind = "pollen", state = state, xInches = x, yInches = y, zInches = 44 };
        }

        private static List<PieceData> Balls(string state, double x, double y, int count, string prefix = "a")
        {
            var balls = new List<PieceData>();
            for (int i = 0; i < count; i++)
            {
                balls.Add(Ball(prefix + i, state, x, y));
            }
            return balls;
        }

        /// <summary>A recording with one loop per list of changes, 0.5 s apart; its header lists every ball, off in a corner.</summary>
        private static RunRecordingData Run(params List<PieceData>[] changes)
        {
            var run = new RunRecordingData { header = new HeaderData() };
            for (int i = 0; i < changes.Length; i++)
            {
                foreach (PieceData piece in changes[i])
                {
                    run.header.pieces.Add(new PieceData { id = piece.id, kind = piece.kind, state = "field", xInches = 60, yInches = 60, zInches = 1.4 });
                }
                run.loops.Add(new LoopData { sinceInitSeconds = i * 0.5, pieces = changes[i] });
            }
            return run;
        }

        private static FieldLayoutData Biobuzz()
        {
            string folder = FieldLayouts.ResolveFolder(new string[0], TestHarnessFolder.Search(Application.dataPath), Application.dataPath);
            return FieldLayouts.LoadAll(folder, new List<string>()).Single(f => f.id == "biobuzz-2026");
        }

        private static void AssertNear(Vector3 expected, Vector3 actual, string what)
        {
            Assert.Less(Vector3.Distance(expected, actual), 1e-3f, what + ": expected " + expected.ToString("F4") + ", was " + actual.ToString("F4"));
        }
    }
}
