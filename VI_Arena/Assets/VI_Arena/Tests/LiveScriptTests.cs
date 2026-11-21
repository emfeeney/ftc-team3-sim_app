using System;
using System.Collections.Generic;
using Newtonsoft.Json.Linq;
using NUnit.Framework;

namespace VI_Arena.Tests
{
    /// <summary>
    /// "-live &lt;OpMode&gt;" and "-livescript plan.json" (manager decision D37): a live run started
    /// from the command line, with the gamepads read from a plan.
    /// </summary>
    public class LiveScriptTests
    {
        private const string Plan = "{ \"steps\": ["
            + " { \"seconds\": 1.5, \"gamepad1\": { \"leftStickY\": -1, \"buttons\": [\"A\"] } },"
            + " { \"seconds\": 0.5, \"gamepad2\": { \"rightTrigger\": 0.8 } }"
            + "] }";

        private static string ReadPlan(string path)
        {
            Assert.AreEqual("plan.json", path);
            return Plan;
        }

        /// <summary>WHAT: Each step's gamepads hold for its seconds after PLAY, one step after the other; anything left out is released.</summary>
        [Test]
        public void GamepadsFollowThePlanInTurn()
        {
            var script = new LiveScript("HiveAutoRed", LiveScript.Parse(Plan));
            JObject[] start = script.GamepadsAt(0);
            Assert.AreEqual(-1.0, (double)start[0]["leftStickY"]);
            Assert.AreEqual(new[] { "A" }, start[0]["buttons"].ToObject<string[]>());
            Assert.AreEqual(0.0, (double)start[0]["leftStickX"], "left out means released");
            Assert.AreEqual(0.0, (double)start[1]["rightTrigger"], "gamepad 2 is released in step 1");
            Assert.AreEqual(-1.0, (double)script.GamepadsAt(1.48)[0]["leftStickY"]);

            JObject[] second = script.GamepadsAt(75 * 0.02); // the 76th loop after PLAY starts step 2
            Assert.AreEqual(0.0, (double)second[0]["leftStickY"]);
            Assert.AreEqual(0.8, (double)second[1]["rightTrigger"], 1e-9);
        }

        /// <summary>WHAT: After the last step both gamepads are released, and with no plan they always are.</summary>
        [Test]
        public void AfterThePlanEverythingIsReleased()
        {
            var script = new LiveScript("HiveAutoRed", LiveScript.Parse(Plan));
            foreach (JObject pad in script.GamepadsAt(2.0))
            {
                Assert.IsTrue(JToken.DeepEquals(LiveScript.Released(), pad), pad.ToString());
            }
            Assert.IsTrue(JToken.DeepEquals(LiveScript.Released(), new LiveScript("X", null).GamepadsAt(0)[0]));
        }

        /// <summary>WHAT: Changing a returned gamepad doesn't change the plan.</summary>
        [Test]
        public void ReturnedGamepadsAreCopies()
        {
            var script = new LiveScript("HiveAutoRed", LiveScript.Parse(Plan));
            script.GamepadsAt(0)[0]["leftStickY"] = 0.25;
            Assert.AreEqual(-1.0, (double)script.GamepadsAt(0)[0]["leftStickY"]);
        }

        /// <summary>WHAT: A plan with a mistake is refused, saying which step and what is wrong.</summary>
        [TestCase("{ }", "\"steps\"")]
        [TestCase("{ \"steps\": [] }", "\"steps\"")]
        [TestCase("not json", "isn't JSON")]
        [TestCase("{ \"steps\": [ { \"seconds\": 0 } ] }", "step 1: \"seconds\"")]
        [TestCase("{ \"steps\": [ { \"seconds\": \"2\" } ] }", "step 1: \"seconds\"")]
        [TestCase("{ \"steps\": [ { \"seconds\": 1 }, { \"seconds\": 1, \"gamepad3\": {} } ] }", "step 2: unknown key \"gamepad3\"")]
        [TestCase("{ \"steps\": [ { \"seconds\": 1, \"gamepad1\": { \"buttons\": [\"Z\"] } } ] }", "unknown button \"Z\"")]
        [TestCase("{ \"steps\": [ { \"seconds\": 1, \"gamepad1\": { \"leftStickX\": 2 } } ] }", "\"leftStickX\" must be a number from -1 to 1")]
        [TestCase("{ \"steps\": [ { \"seconds\": 1, \"gamepad1\": { \"leftTrigger\": -0.5 } } ] }", "\"leftTrigger\" must be a number from 0 to 1")]
        [TestCase("{ \"steps\": [ { \"seconds\": 1, \"gamepad2\": { \"leftStick\": 1 } } ] }", "step 1 gamepad2: unknown key \"leftStick\"")]
        public void BadPlansAreRefusedWithAReason(string json, string reason)
        {
            var e = Assert.Throws<FormatException>(() => LiveScript.Parse(json));
            StringAssert.Contains(reason, e.Message);
        }

        /// <summary>WHAT: "-live" alone runs with the controllers; with "-livescript" the gamepads come from the plan; neither means no live run.</summary>
        [Test]
        public void TheCommandLineNamesTheOpModeAndThePlan()
        {
            Assert.IsNull(LiveScript.FromCommandLine(new[] { "VI_Arena.exe", "-setting", "moon-base" }, ReadPlan));

            LiveScript controllers = LiveScript.FromCommandLine(new[] { "VI_Arena.exe", "-live", "HiveAutoRed" }, ReadPlan);
            Assert.AreEqual("HiveAutoRed", controllers.OpModeId);
            Assert.IsFalse(controllers.Scripted);

            LiveScript scripted = LiveScript.FromCommandLine(
                new[] { "VI_Arena.exe", "-LiveScript", "plan.json", "-live", "HiveLauncherTest" }, ReadPlan);
            Assert.AreEqual("HiveLauncherTest", scripted.OpModeId);
            Assert.IsTrue(scripted.Scripted);
            Assert.AreEqual(2, scripted.Steps.Count);
        }

        /// <summary>WHAT: A plan without an OpMode, or an option without its value, is refused.</summary>
        [TestCase(new[] { "VI_Arena.exe", "-livescript", "plan.json" }, "needs -live")]
        [TestCase(new[] { "VI_Arena.exe", "-live" }, "-live needs a value")]
        [TestCase(new[] { "VI_Arena.exe", "-live", "-setting", "moon-base" }, "-live needs a value")]
        public void IncompleteCommandLinesAreRefused(string[] args, string reason)
        {
            var e = Assert.Throws<FormatException>(() => LiveScript.FromCommandLine(args, ReadPlan));
            StringAssert.Contains(reason, e.Message);
        }

        /// <summary>WHAT: The OpMode is found by id in any case; a missing one names what the harness offers.</summary>
        [Test]
        public void TheOpModeIsPickedFromTheOffer()
        {
            var offered = new List<LiveSession.OpModeChoice>
            {
                new LiveSession.OpModeChoice { id = "TeamATeleOp", title = "Team A TeleOp" },
                new LiveSession.OpModeChoice { id = "HiveAutoRed", title = "Hive Auto Red" },
            };
            Assert.AreEqual("Hive Auto Red", new LiveScript("hiveautored", null).Pick(offered).title);
            var e = Assert.Throws<FormatException>(() => new LiveScript("HiveAutoBlue", null).Pick(offered));
            StringAssert.Contains("no OpMode \"HiveAutoBlue\" (it offers TeamATeleOp, HiveAutoRed)", e.Message);
        }

        /// <summary>WHAT: The plan file after "-livescript" isn't opened as a recording.</summary>
        [Test]
        public void ThePlanIsNotTakenForARecording()
        {
            Assert.IsNull(RecordingLoader.FileFromCommandLine(new[] { "VI_Arena.exe", "-live", "X", "-livescript", "plan.json" }));
            Assert.AreEqual("run.json", RecordingLoader.FileFromCommandLine(new[] { "VI_Arena.exe", "-livescript", "plan.json", "run.json" }));
        }
    }
}
