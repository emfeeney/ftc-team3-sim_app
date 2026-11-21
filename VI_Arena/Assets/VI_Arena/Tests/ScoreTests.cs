using System.Collections.Generic;
using NUnit.Framework;

namespace VI_Arena.Tests
{
    /// <summary>
    /// Match points in VI Arena (manager decision D50): a version 8 recording's scores, and the Score card's title and
    /// lines (<see cref="HudText.ScoreAt"/>, <see cref="HudText.ScoreTitle"/>, <see cref="HudText.ScoreLines"/>).
    /// </summary>
    public class ScoreTests
    {
        private static string Alliance(int total, string items) =>
            "{\"total\": " + total + ", \"rankingPoints\": 0, \"swarm\": false, \"pollinator1\": false, \"pollinator2\": false, \"items\": [" + items + "]}";

        private static string Score(bool settled, int redTotal, string redItems) =>
            "{\"period\": \"AUTONOMOUS\", \"robotAlliance\": \"red\", \"settled\": " + (settled ? "true" : "false")
            + ", \"settleSeconds\": 1.24, \"red\": " + Alliance(redTotal, redItems) + ", \"blue\": " + Alliance(0,
                "{\"name\": \"LEAVE\", \"count\": 0, \"pointsEach\": 3, \"points\": 0}") + "}";

        private const string Leave = "{\"name\": \"LEAVE\", \"count\": 1, \"pointsEach\": 3, \"points\": 3}";
        private const string NoPark = "{\"name\": \"PARK\", \"count\": 0, \"pointsEach\": 5, \"points\": 0}";
        private const string Park = "{\"name\": \"PARK\", \"count\": 1, \"pointsEach\": 5, \"points\": 5}";
        private const string Tip = "{\"name\": \"HIVE TIP\", \"count\": 1, \"pointsEach\": 20, \"points\": 20}";

        private static string Loop(double t, string stage, string score) =>
            "{\"sinceInitSeconds\": " + t + ", \"matchTimeSeconds\": " + t + ", \"stage\": \"" + stage + "\", \"pose\": null,"
            + " \"motors\": [], \"servos\": [], \"autoStep\": null, \"telemetry\": [], \"score\": " + score + "}";

        /// <summary>A version 8 run: no points at INIT, LEAVE and a tip while it runs, and PARK in the final score.</summary>
        private static readonly string Version8 = "{\"header\": {\"version\": 8, \"opMode\": \"Test\", \"startPose\": null, \"driveModel\": null},"
            + " \"gamepad1Rumbles\": [], \"gamepad2Rumbles\": [], \"events\": [], \"plannedPaths\": [],"
            + " \"loops\": [" + Loop(0, "INIT", Score(false, 0, NoPark)) + ", " + Loop(30, "STOP", Score(false, 23, Leave + ", " + NoPark + ", " + Tip)) + "],"
            + " \"score\": " + Score(true, 28, Leave + ", " + Park + ", " + Tip) + "}";

        /// <summary>WHAT: A version 8 recording carries each loop's score and the final one, with every line and the totals.</summary>
        [Test]
        public void Version8RecordingsCarryTheScore()
        {
            RunRecordingData run = RecordingLoader.Parse(Version8, "score.json");

            Assert.AreEqual(23, run.loops[1].score.red.total, "the moment's score");
            Assert.IsFalse(run.loops[1].score.settled);
            Assert.AreEqual(28, run.score.red.total, "the final score");
            Assert.IsTrue(run.score.settled);
            Assert.AreEqual("AUTONOMOUS", run.score.period);
            Assert.AreEqual("red", run.score.robotAlliance);
            Assert.AreEqual(1.24, run.score.settleSeconds, 1e-9);
            Assert.AreEqual("HIVE TIP", run.score.red.items[2].name);
            Assert.AreEqual(20, run.score.red.items[2].pointsEach);
            Assert.IsTrue(HudText.AnyScore(run));
        }

        /// <summary>
        /// WHAT: The card shows each loop's own score, and the final score at the last loop; its title says "final",
        /// or "settling" at STOP before the final score is in (a live run that has just stopped).
        /// </summary>
        [Test]
        public void TheCardShowsTheFinalScoreAtTheEnd()
        {
            RunRecordingData run = RecordingLoader.Parse(Version8, "score.json");

            Assert.AreEqual(0, HudText.ScoreAt(run, 0).red.total);
            Assert.AreEqual("Score", HudText.ScoreTitle(run, 0));
            Assert.AreEqual(28, HudText.ScoreAt(run, 1).red.total, "the final score at the end");
            Assert.AreEqual("Score · final", HudText.ScoreTitle(run, 1));

            run.score.settled = false;
            Assert.AreEqual("Score · final (still moving at 5 s)", HudText.ScoreTitle(run, 1));
            run.score = null;
            Assert.AreEqual(23, HudText.ScoreAt(run, 1).red.total, "before the final score: the moment's");
            Assert.AreEqual("Score · settling", HudText.ScoreTitle(run, 1));
        }

        /// <summary>WHAT: Older recordings have no score, and the HUD shows no Score card for them.</summary>
        [Test]
        public void OlderRecordingsHaveNoScore()
        {
            RunRecordingData old = RecordingLoader.Parse(Version8.Replace("\"version\": 8", "\"version\": 7"), "old.json");
            old.score = null;
            old.loops.ForEach(l => l.score = null);
            Assert.IsFalse(HudText.AnyScore(old));
            Assert.IsNull(HudText.ScoreAt(old, 1));
            Assert.IsEmpty(HudText.ScoreLines(null));
        }

        /// <summary>
        /// WHAT: The card lists each line that earned points, red first, with its count and points; lines with nothing
        /// earned are left out.
        /// WHY: The user asked to see what the points came from (D50); all-zero lines would fill the card.
        /// </summary>
        [Test]
        public void TheLinesListWhatScoredRedFirst()
        {
            RunRecordingData run = RecordingLoader.Parse(Version8, "score.json");
            run.score.blue.items.Add(new ScoreItemData { name = "GARDEN", count = 3, pointsEach = 1, points = 3 });
            run.score.notes.Add("no FLOWER scoring volume on this field");

            List<string> lines = HudText.ScoreLines(run.score);

            CollectionAssert.AreEqual(new[]
            {
                "Red   LEAVE          x1     3",
                "Red   PARK           x1     5",
                "Red   HIVE TIP       x1    20",
                "Blue  GARDEN         x3     3",
                "Note: no FLOWER scoring volume on this field",
            }, lines);
        }
    }
}
