using System.Collections.Generic;
using NUnit.Framework;
using UnityEngine;

namespace VI_Arena.Tests
{
    /// <summary>The game pieces (POLLEN, NECTAR) in run recordings, format version 4.</summary>
    public class GamePieceTests
    {
        // Two pieces at INIT; the POLLEN moves in loop 1 and again in loop 3; nothing changes in loop 2.
        private const string Version4 = @"{
            ""header"": { ""version"": 4, ""distanceUnit"": ""inches"", ""angleUnit"": ""radians"", ""timeUnit"": ""seconds"",
                ""robotConfig"": ""teamA"", ""opMode"": ""TeamATeleOp"", ""period"": ""TELEOP"", ""loopPeriodSeconds"": 0.02,
                ""pieces"": [
                    { ""id"": ""pollen-garden-red-1"", ""kind"": ""pollen"", ""state"": ""field"", ""xInches"": 70.6, ""yInches"": -70.6, ""zInches"": 1.4 },
                    { ""id"": ""red-nectar-cell-1"", ""kind"": ""red-nectar"", ""state"": ""cell"", ""xInches"": 7.37, ""yInches"": -20.95, ""zInches"": 51.8 } ] },
            ""loops"": [
                { ""sinceInitSeconds"": 0, ""stage"": ""INIT"" },
                { ""sinceInitSeconds"": 0.02, ""stage"": ""LOOP"", ""pieces"": [
                    { ""id"": ""pollen-garden-red-1"", ""kind"": ""pollen"", ""state"": ""field"", ""xInches"": 69.0, ""yInches"": -70.6, ""zInches"": 1.4 } ] },
                { ""sinceInitSeconds"": 0.04, ""stage"": ""LOOP"", ""pieces"": [] },
                { ""sinceInitSeconds"": 0.06, ""stage"": ""LOOP"", ""pieces"": [
                    { ""id"": ""pollen-garden-red-1"", ""kind"": ""pollen"", ""state"": ""field"", ""xInches"": 66.0, ""yInches"": -70.0, ""zInches"": 1.4 } ] } ] }";

        private GameObject parent;

        [SetUp]
        public void MakeParent()
        {
            parent = new GameObject("Test pieces");
        }

        [TearDown]
        public void RemoveParent()
        {
            Object.DestroyImmediate(parent);
        }

        /// <summary>
        /// WHAT: A version 4 recording loads its pieces, and each loop shows the latest change of every
        /// piece (a piece not listed stays where it was).
        /// WHY: Recordings only list the pieces that moved, to stay small.
        /// </summary>
        [Test]
        public void EachLoopAddsUpThePieceChanges()
        {
            var track = new GamePieceTrack(RecordingLoader.Parse(Version4));

            Assert.AreEqual(2, track.Start.Count);
            Assert.AreEqual(70.6, track.At(0)[0].xInches, 1e-9, "INIT: where it starts");
            Assert.AreEqual(69.0, track.At(1)[0].xInches, 1e-9, "loop 1 moved it");
            Assert.AreEqual(69.0, track.At(2)[0].xInches, 1e-9, "loop 2 left it");
            Assert.AreEqual(66.0, track.At(3)[0].xInches, 1e-9);
            Assert.AreEqual(-70.0, track.At(3)[0].yInches, 1e-9);
            Assert.AreEqual(69.0, track.At(1)[0].xInches, 1e-9, "going back works");
            Assert.AreEqual("cell", track.At(3)[1].state, "the NECTAR never moved");
            Assert.AreEqual(51.8, track.At(3)[1].zInches, 1e-9);
        }

        /// <summary>WHAT: A live run's recording keeps growing, and the track catches up with new loops.</summary>
        [Test]
        public void ALiveRecordingCatchesUp()
        {
            RunRecordingData recording = RecordingLoader.Parse(Version4);
            var track = new GamePieceTrack(recording);
            Assert.AreEqual(66.0, track.At(3)[0].xInches, 1e-9);

            var next = new LoopData { sinceInitSeconds = 0.08, stage = "LOOP" };
            next.pieces.Add(new PieceData { id = "pollen-garden-red-1", kind = "pollen", state = "field", xInches = 60, yInches = -70, zInches = 1.4 });
            recording.loops.Add(next);

            Assert.AreEqual(60.0, track.At(4)[0].xInches, 1e-9);
        }

        /// <summary>WHAT: Recordings older than version 4 load with no pieces.</summary>
        [Test]
        public void OlderRecordingsHaveNoPieces()
        {
            const string version3 = @"{ ""header"": { ""version"": 3, ""robotConfig"": ""teamA"", ""loopPeriodSeconds"": 0.02 },
                ""loops"": [ { ""sinceInitSeconds"": 0, ""stage"": ""INIT"" } ] }";
            RunRecordingData recording = RecordingLoader.Parse(version3);

            Assert.IsEmpty(new GamePieceTrack(recording).Start);
        }

        /// <summary>
        /// WHAT: Each piece is drawn as a ball of its real size at its FTC position (converted to Unity),
        /// without colliders, and a ball that moves turns as it rolls.
        /// </summary>
        [Test]
        public void PiecesAreDrawnAtTheirSpotsAndRoll()
        {
            var track = new GamePieceTrack(RecordingLoader.Parse(Version4));
            var view = new GamePieceView(parent.transform, track.Start);
            view.Show(track.At(0));

            Transform pollen = view.Ball("pollen-garden-red-1");
            Transform nectar = view.Ball("red-nectar-cell-1");
            Vector3 expected = FtcToUnity.Position(70.6, -70.6, FtcToUnity.Meters(1.4));
            Assert.Less(Vector3.Distance(expected, pollen.position), 1e-4f, "POLLEN on its spot");
            Assert.AreEqual(FtcToUnity.Meters(2.8), SizeOf(pollen), FtcToUnity.Meters(0.15), "POLLEN is 2.8 in");
            Assert.AreEqual(FtcToUnity.Meters(3.6), SizeOf(nectar), FtcToUnity.Meters(0.15), "NECTAR is 3.6 in");
            Assert.IsEmpty(parent.GetComponentsInChildren<Collider>());

            Quaternion pollenBefore = pollen.rotation;
            Quaternion nectarBefore = nectar.rotation;
            view.Show(track.At(1));
            Assert.Greater(Quaternion.Angle(pollenBefore, pollen.rotation), 30f, "rolled about 65 degrees for 1.6 in");
            Assert.AreEqual(0f, Quaternion.Angle(nectarBefore, nectar.rotation), 1e-3f, "the NECTAR didn't move, so didn't turn");
        }

        private static float SizeOf(Transform ball)
        {
            Bounds b = ball.GetComponentInChildren<Renderer>().bounds;
            return b.size.x;
        }

        /// <summary>
        /// WHAT: A held ball rides with the robot without rolling: it moves, but doesn't spin as it goes,
        /// and only turns when the robot turns (by the same angle, the right way). A loose ball moving the
        /// same way does roll.
        /// WHY: A ball in the intake isn't rolling on the tiles, so a spinning ball there looked wrong.
        /// </summary>
        [Test]
        public void AHeldBallRidesWithTheRobotWithoutRolling()
        {
            var view = new GamePieceView(parent.transform, new List<PieceData> { Piece("held-1", "held", 0), Piece("loose-1", "field", 0) });
            view.Show(new[] { Piece("held-1", "held", 0), Piece("loose-1", "field", 0) }, 0.0);
            Quaternion start = view.Ball("held-1").rotation;

            view.Show(new[] { Piece("held-1", "held", 10), Piece("loose-1", "field", 10) }, 0.0);

            Assert.AreEqual(0f, Quaternion.Angle(start, view.Ball("held-1").rotation), 1e-3f, "moved 10 in, no spin");
            Assert.AreEqual(FtcToUnity.Position(10, 0, FtcToUnity.Meters(1.4)).x, view.Ball("held-1").localPosition.x, 1e-5f, "it still moved");
            Assert.Greater(Quaternion.Angle(start, view.Ball("loose-1").rotation), 1f, "the loose ball rolled");

            view.Show(new[] { Piece("held-1", "held", 10), Piece("loose-1", "field", 10) }, Mathf.PI / 2);

            Quaternion turned = view.Ball("held-1").rotation;
            Assert.AreEqual(90f, Quaternion.Angle(start, turned), 1e-2f, "turned with the robot");
            Vector3 forward = turned * Quaternion.Inverse(start) * Vector3.right;
            Assert.AreEqual(1f, forward.z, 1e-3f, "counter-clockwise from above: Unity +x (FTC +x) turns to +z (FTC +y)");
        }

        /// <summary>
        /// WHAT: A ball in the air doesn't spin as it moves, and rolls again once it has landed.
        /// WHY: Rolling is for a ball on the tiles; a launched ball flying 10 in shouldn't turn over and over.
        /// </summary>
        [Test]
        public void AFlyingBallDoesNotSpinAndRollsOnceLanded()
        {
            var view = new GamePieceView(parent.transform, new List<PieceData> { Piece("ball", "flying", 0) });
            view.Show(new[] { Piece("ball", "flying", 0) }, 0.0);
            Quaternion start = view.Ball("ball").rotation;

            view.Show(new[] { Piece("ball", "flying", 10) }, Mathf.PI / 2);

            Assert.AreEqual(0f, Quaternion.Angle(start, view.Ball("ball").rotation), 1e-3f, "in the air: no spin, and the robot turning doesn't turn it");

            view.Show(new[] { Piece("ball", "field", 15) }, Mathf.PI / 2);

            Assert.Greater(Quaternion.Angle(start, view.Ball("ball").rotation), 1f, "landed and rolled");
        }

        /// <summary>WHAT: Without a pose, a held ball neither rolls nor turns.</summary>
        [Test]
        public void AHeldBallWithoutAPoseStaysStill()
        {
            var view = new GamePieceView(parent.transform, new List<PieceData> { Piece("held-1", "held", 0) });
            view.Show(new[] { Piece("held-1", "held", 0) });
            Quaternion start = view.Ball("held-1").rotation;

            view.Show(new[] { Piece("held-1", "held", 5) });

            Assert.AreEqual(0f, Quaternion.Angle(start, view.Ball("held-1").rotation), 1e-3f);
        }

        private static PieceData Piece(string id, string state, double x)
        {
            return new PieceData { id = id, kind = "pollen", state = state, xInches = x, yInches = 0, zInches = 1.4 };
        }
    }
}
