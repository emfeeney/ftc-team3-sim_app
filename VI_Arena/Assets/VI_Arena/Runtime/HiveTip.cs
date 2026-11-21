using System;
using System.Collections.Generic;
using UnityEngine;

namespace VI_Arena
{
    /// <summary>
    /// The rule for when a HIVE tips, and how it moves. Each HIVE has two stable positions (manual 9.5,
    /// 9.6.2): it holds one until enough POLLEN or NECTAR is in its upward CELL, then swings over so the
    /// other CELL is up. The rule is plain weight, not free physics, so the same balls always tip it
    /// the same way and a replay matches its run.
    ///
    /// <para>A ball is in a CELL when its state is "cell" and it is inside the HIVE's band along y; the
    /// side of the pivot it is on says which CELL. <b>A recorded CELL ball's position is where it sits
    /// with the HIVE in its starting position</b>; the HIVE's swing moves it from there.</para>
    ///
    /// <para><b>Placeholder:</b> the manual gives no weight, only "enough", so each HIVE's
    /// <see cref="HiveData.tipMassGrams"/> and <see cref="HiveData.tipSeconds"/> in the field file are
    /// guesses. <b>Not simulated:</b> balls spilling out of the CELL that just went down (that needs the
    /// harness to move them), so they stay drawn in it, and a ball landing in the other CELL slowing a
    /// tip. They would have spilled, so they stop counting (<see cref="HiveTipTrack"/>); otherwise the
    /// HIVE would tip straight back whenever that CELL came up again.</para>
    /// </summary>
    public static class HiveTip
    {
        /// <summary>A ball's weight in grams, from the harness's PieceModel (AndyMark store pages): POLLEN 24.9, NECTAR 41.3.</summary>
        public static double MassGrams(string kind) => kind == "pollen" ? 24.9 : 41.3;

        /// <summary>The CELL a ball is in: +1 on the pivot's +x side, -1 on its -x side, 0 if it isn't in this HIVE.</summary>
        public static int CellSide(HiveData hive, PieceData piece)
        {
            if (piece == null || piece.state != "cell" || piece.yInches < hive.minYInches || piece.yInches > hive.maxYInches)
            {
                return 0;
            }
            return piece.xInches >= hive.pivotXInches ? 1 : -1;
        }

        /// <summary>
        /// The weight of the balls in one CELL of this HIVE (<paramref name="side"/> as in <see cref="CellSide"/>),
        /// leaving out the ids in <paramref name="spilled"/>, if any.
        /// </summary>
        public static double MassInCell(HiveData hive, IReadOnlyList<PieceData> pieces, int side, ICollection<string> spilled = null)
        {
            double grams = 0;
            foreach (PieceData piece in pieces)
            {
                if (CellSide(hive, piece) == side && (spilled == null || !spilled.Contains(piece.id)))
                {
                    grams += MassGrams(piece.kind);
                }
            }
            return grams;
        }

        /// <summary>The side whose CELL is up after <paramref name="tips"/> tips.</summary>
        public static int UpSide(HiveData hive, int tips)
        {
            return tips % 2 == 0 ? hive.startUpSide : -hive.startUpSide;
        }

        /// <summary>True if this much weight in the upward CELL tips the HIVE.</summary>
        public static bool Tips(HiveData hive, double gramsInUpCell)
        {
            return gramsInUpCell >= hive.tipMassGrams;
        }

        /// <summary>
        /// How far the HIVE turns from its starting position to its other stable one, in degrees about
        /// FTC +y, with + taking +x up toward +z: twice its tilt, against the side that starts up.
        /// </summary>
        public static double FullSwingDegrees(HiveData hive)
        {
            return -hive.startUpSide * 2 * hive.tiltDegrees;
        }

        /// <summary>A point turned with the HIVE by <paramref name="degrees"/> (see <see cref="FullSwingDegrees"/>). y doesn't change.</summary>
        public static void Turn(HiveData hive, double degrees, double xInches, double zInches, out double turnedX, out double turnedZ)
        {
            double cos = Math.Cos(degrees * Math.PI / 180), sin = Math.Sin(degrees * Math.PI / 180);
            double dx = xInches - hive.pivotXInches, dz = zInches - hive.pivotZInches;
            turnedX = hive.pivotXInches + dx * cos - dz * sin;
            turnedZ = hive.pivotZInches + dx * sin + dz * cos;
        }

        /// <summary>The HIVE this AprilTag rides on (one that moves, inside its band), or null.</summary>
        public static int HiveOfTag(IReadOnlyList<HiveData> hives, FieldTagData tag)
        {
            for (int i = 0; tag.moves && tag.HasFieldPose && i < hives.Count; i++)
            {
                if (tag.yInches.Value >= hives[i].minYInches && tag.yInches.Value <= hives[i].maxYInches)
                {
                    return i;
                }
            }
            return -1;
        }

        /// <summary>
        /// The field's AprilTags with those on a HIVE turned by that HIVE's swing
        /// (<paramref name="swingDegrees"/>, one per HIVE), so the webcam marks stay on the tags.
        /// </summary>
        public static List<FieldTagData> TagsAt(IReadOnlyList<FieldTagData> tags, IReadOnlyList<HiveData> hives, IReadOnlyList<double> swingDegrees)
        {
            var moved = new List<FieldTagData>(tags.Count);
            foreach (FieldTagData tag in tags)
            {
                int hive = HiveOfTag(hives, tag);
                if (hive < 0 || swingDegrees[hive] == 0)
                {
                    moved.Add(tag);
                    continue;
                }
                HiveData on = hives[hive];
                Turn(on, swingDegrees[hive], tag.xInches.Value, tag.zInches.Value, out double x, out double z);
                // A direction turns like a point about the pivot with its tail at the pivot.
                Turn(on, swingDegrees[hive], on.pivotXInches + tag.facing.x, on.pivotZInches + tag.facing.z, out double facingX, out double facingZ);
                moved.Add(new FieldTagData
                {
                    id = tag.id, name = tag.name, cluster = tag.cluster, sizeInches = tag.sizeInches,
                    clusterOffsetInches = tag.clusterOffsetInches, moves = tag.moves, accuracy = tag.accuracy,
                    xInches = x, yInches = tag.yInches, zInches = z,
                    facing = new FieldVectorData { x = facingX - on.pivotXInches, y = tag.facing.y, z = facingZ - on.pivotZInches },
                });
            }
            return moved;
        }
    }

    /// <summary>
    /// Where each HIVE is at every loop of a recording, and what is in its CELLs.
    ///
    /// <para><b>Version 6 and later:</b> the harness decides when a HIVE tips and records each HIVE's swing
    /// and every ball's true position, so this only reads them. <b>Older recordings:</b> it works the
    /// tips out itself with <see cref="HiveTip"/>'s rule. A HIVE holds its position, so where it is
    /// depends on everything before: it walks the loops in order once and keeps each tip, so the
    /// playhead can jump anywhere, and a live run's new loops just extend it.</para>
    /// </summary>
    public sealed class HiveTipTrack
    {
        private struct Tip
        {
            public int loop;
            public double seconds;
        }

        private readonly IReadOnlyList<HiveData> hives;
        private readonly RunRecordingData recording;
        private readonly GamePieceTrack pieces;
        private readonly List<Tip>[] tips;
        // Per HIVE: the balls still in a CELL that went down in a tip, which would have spilled.
        private readonly HashSet<string>[] spilled;
        private int walkedTo = -1;

        public HiveTipTrack(IReadOnlyList<HiveData> hives, RunRecordingData recording, GamePieceTrack pieces)
        {
            this.hives = hives;
            this.recording = recording;
            this.pieces = pieces;
            tips = new List<Tip>[hives.Count];
            spilled = new HashSet<string>[hives.Count];
            for (int i = 0; i < tips.Length; i++)
            {
                tips[i] = new List<Tip>();
                spilled[i] = new HashSet<string>();
            }
        }

        /// <summary>The HIVEs this track follows.</summary>
        public IReadOnlyList<HiveData> Hives => hives;

        /// <summary>True when the recording carries the HIVEs' tilt (version 6 and later), so the harness decided the tips.</summary>
        public bool Recorded => recording.header != null && recording.header.version >= 6;

        /// <summary>The recorded swing of a HIVE at a loop: that loop's, else the last one before it, else the header's, else 0.</summary>
        private double RecordedSwing(int hive, int loopIndex)
        {
            string id = hives[hive].id;
            for (int i = Mathf.Min(loopIndex, recording.loops.Count - 1); i >= 0; i--)
            {
                HiveTiltData tilt = recording.loops[i].hives?.Find(h => h.id == id);
                if (tilt != null)
                {
                    return tilt.swingDegrees;
                }
            }
            HiveTiltData start = recording.header.hives?.Find(h => h.id == id);
            return start != null ? start.swingDegrees : 0;
        }

        /// <summary>The CELL that is up at this loop (+1 the +x side, -1 the -x side), or 0 while the HIVE is swinging.</summary>
        public int UpSideAt(int hive, int loopIndex)
        {
            double tilt = hives[hive].startUpSide * hives[hive].tiltDegrees + SwingDegrees(hive, loopIndex);
            if (Math.Abs(Math.Abs(tilt) - hives[hive].tiltDegrees) > 0.5)
            {
                return 0;
            }
            return tilt > 0 ? 1 : -1;
        }

        /// <summary>How many NECTAR and POLLEN are in one CELL at this loop.</summary>
        public void CountCell(int hive, int side, int loopIndex, PieceData[] states, out int nectar, out int pollen)
        {
            nectar = pollen = 0;
            // Every part of a CELL stays on its own side of the pivot at either tilt (in the CAD, at least
            // 1.9 in out), so a ball's side says which CELL it is in, tipped or not.
            foreach (PieceData piece in states)
            {
                if (HiveTip.CellSide(hives[hive], piece) == side)
                {
                    if (piece.kind == "pollen")
                    {
                        pollen++;
                    }
                    else
                    {
                        nectar++;
                    }
                }
            }
        }

        /// <summary>How many times a HIVE has tipped by this loop.</summary>
        public int TipsBy(int hive, int loopIndex)
        {
            WalkTo(loopIndex);
            int count = 0;
            foreach (Tip tip in tips[hive])
            {
                if (tip.loop <= loopIndex)
                {
                    count++;
                }
            }
            return count;
        }

        /// <summary>How far a HIVE has turned from its starting position at this loop, in degrees (see <see cref="HiveTip.FullSwingDegrees"/>).</summary>
        public double SwingDegrees(int hive, int loopIndex)
        {
            if (Recorded)
            {
                return recording.loops.Count == 0 ? 0 : RecordedSwing(hive, loopIndex);
            }
            int count = TipsBy(hive, loopIndex);
            double full = HiveTip.FullSwingDegrees(hives[hive]);
            if (count == 0)
            {
                return 0;
            }
            double to = count % 2 == 1 ? full : 0, from = count % 2 == 1 ? 0 : full;
            double done = Mathf.Clamp01((float)((recording.loops[loopIndex].sinceInitSeconds - tips[hive][count - 1].seconds) / hives[hive].tipSeconds));
            return from + (to - from) * Mathf.SmoothStep(0f, 1f, (float)done);
        }

        /// <summary>
        /// The balls to draw at this loop: <paramref name="states"/>, with those in a CELL turned with their
        /// HIVE. The same array if no HIVE has moved.
        /// </summary>
        public PieceData[] PiecesToShow(int loopIndex, PieceData[] states)
        {
            if (Recorded)
            {
                // The harness recorded every ball where it really is.
                return states;
            }
            PieceData[] shown = states;
            for (int h = 0; h < hives.Count; h++)
            {
                double swing = SwingDegrees(h, loopIndex);
                if (swing == 0)
                {
                    continue;
                }
                for (int i = 0; i < states.Length; i++)
                {
                    if (HiveTip.CellSide(hives[h], states[i]) == 0)
                    {
                        continue;
                    }
                    if (ReferenceEquals(shown, states))
                    {
                        shown = (PieceData[])states.Clone();
                    }
                    PieceData piece = states[i];
                    HiveTip.Turn(hives[h], swing, piece.xInches, piece.zInches, out double x, out double z);
                    shown[i] = new PieceData { id = piece.id, kind = piece.kind, state = piece.state, xInches = x, yInches = piece.yInches, zInches = z };
                }
            }
            return shown;
        }

        private void WalkTo(int loopIndex)
        {
            int last = Mathf.Min(loopIndex, recording.loops.Count - 1);
            while (walkedTo < last)
            {
                walkedTo++;
                PieceData[] states = pieces.At(walkedTo);
                double seconds = recording.loops[walkedTo].sinceInitSeconds;
                for (int h = 0; h < hives.Count; h++)
                {
                    List<Tip> done = tips[h];
                    // A ball the harness has taken out of the CELL can count again if it comes back.
                    HiveData hive = hives[h];
                    spilled[h].RemoveWhere(id => !Array.Exists(states, p => p != null && p.id == id && HiveTip.CellSide(hive, p) != 0));
                    // A HIVE that is still swinging finishes first.
                    bool settled = done.Count == 0 || seconds - done[done.Count - 1].seconds >= hive.tipSeconds;
                    int up = HiveTip.UpSide(hive, done.Count);
                    if (settled && HiveTip.Tips(hive, HiveTip.MassInCell(hive, states, up, spilled[h])))
                    {
                        done.Add(new Tip { loop = walkedTo, seconds = seconds });
                        foreach (PieceData piece in states)
                        {
                            if (HiveTip.CellSide(hive, piece) == up)
                            {
                                spilled[h].Add(piece.id);
                            }
                        }
                    }
                }
            }
        }
    }
}
