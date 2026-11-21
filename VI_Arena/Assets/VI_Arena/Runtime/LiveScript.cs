using System;
using System.Collections.Generic;
using Newtonsoft.Json.Linq;

namespace VI_Arena
{
    /// <summary>
    /// A live run started from the command line (manager decision D37), so a live run with Unity's
    /// physics can be checked and filmed without anyone clicking or holding a controller:
    /// <code>VI_Arena.exe -live HiveAutoRed</code>
    /// turns Live mode on, picks Unity's physics for this run, loads the OpMode and presses INIT and
    /// PLAY. With <c>-livescript plan.json</c> the gamepads come from the plan instead of the game
    /// controllers (the bridge's <c>"input": "messages"</c>).
    ///
    /// <para>The plan is a list of steps, each held for its <c>seconds</c> of simulated time, one
    /// after the other from PLAY. Each step gives <c>gamepad1</c> and/or <c>gamepad2</c> in the
    /// bridge's gamepad layout (<c>buttons</c>, <c>leftStickX</c>, <c>leftStickY</c>,
    /// <c>rightStickX</c>, <c>rightStickY</c>, <c>leftTrigger</c>, <c>rightTrigger</c>); anything
    /// left out is released. After the last step both gamepads are released.</para>
    /// <code>
    /// { "steps": [
    ///     { "seconds": 1.5, "gamepad1": { "leftStickY": -1 } },
    ///     { "seconds": 0.5, "gamepad1": { "buttons": ["X"] } }
    /// ] }
    /// </code>
    /// </summary>
    public sealed class LiveScript
    {
        /// <summary>The gamepad values a plan may give (the bridge's gamepad layout).</summary>
        private static readonly string[] Axes =
            { "leftStickX", "leftStickY", "rightStickX", "rightStickY", "leftTrigger", "rightTrigger" };

        /// <summary>The buttons a plan may hold (the bridge's names).</summary>
        private static readonly HashSet<string> ButtonNames = new HashSet<string>
        {
            "A", "B", "X", "Y", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LEFT_BUMPER", "RIGHT_BUMPER",
            "LEFT_STICK_BUTTON", "RIGHT_STICK_BUTTON", "START", "BACK", "GUIDE", "TOUCHPAD",
        };

        /// <summary>One step of the plan: both gamepads, held for <see cref="Seconds"/>.</summary>
        public sealed class Step
        {
            public double Seconds;
            public JObject Gamepad1;
            public JObject Gamepad2;
        }

        /// <summary>The OpMode to run, by its bridge id (the class's simple name, e.g. "HiveAutoRed").</summary>
        public string OpModeId { get; }

        /// <summary>The scripted gamepads, or null to let the harness read the game controllers.</summary>
        public IReadOnlyList<Step> Steps { get; }

        /// <summary>True when the gamepads come from the plan.</summary>
        public bool Scripted => Steps != null;

        public LiveScript(string opModeId, IReadOnlyList<Step> steps)
        {
            if (string.IsNullOrWhiteSpace(opModeId))
            {
                throw new FormatException("-live needs an OpMode id, e.g. -live HiveAutoRed");
            }
            OpModeId = opModeId;
            Steps = steps;
        }

        /// <summary>
        /// Reads <c>-live &lt;OpMode&gt;</c> and <c>-livescript &lt;plan.json&gt;</c>; null without
        /// <c>-live</c>. Throws <see cref="FormatException"/> with what is wrong.
        /// </summary>
        /// <param name="args">The command line (Environment.GetCommandLineArgs()).</param>
        /// <param name="readFile">Reads the plan file (File.ReadAllText; a fake in tests).</param>
        public static LiveScript FromCommandLine(string[] args, Func<string, string> readFile)
        {
            string opMode = ValueAfter(args, "-live");
            string plan = ValueAfter(args, "-livescript");
            if (opMode == null)
            {
                if (plan != null)
                {
                    throw new FormatException("-livescript needs -live <OpMode> too");
                }
                return null;
            }
            return new LiveScript(opMode, plan == null ? null : Parse(readFile(plan)));
        }

        /// <summary>Reads a plan's steps. Throws <see cref="FormatException"/> with what is wrong.</summary>
        public static IReadOnlyList<Step> Parse(string json)
        {
            JObject root;
            try
            {
                root = JObject.Parse(json);
            }
            catch (Exception e) when (!(e is FormatException))
            {
                throw new FormatException("The live script isn't JSON: " + e.Message);
            }
            if (!(root["steps"] is JArray array) || array.Count == 0)
            {
                throw new FormatException("The live script needs a \"steps\" list with at least one step");
            }
            var steps = new List<Step>();
            for (int i = 0; i < array.Count; i++)
            {
                string where = "step " + (i + 1);
                if (!(array[i] is JObject step))
                {
                    throw new FormatException(where + " isn't an object");
                }
                foreach (JProperty property in step.Properties())
                {
                    if (property.Name != "seconds" && property.Name != "gamepad1" && property.Name != "gamepad2")
                    {
                        throw new FormatException(where + ": unknown key \"" + property.Name + "\" (seconds, gamepad1, gamepad2)");
                    }
                }
                double seconds = step["seconds"]?.Type == JTokenType.Integer || step["seconds"]?.Type == JTokenType.Float
                    ? step["seconds"].Value<double>() : double.NaN;
                if (!(seconds > 0) || double.IsInfinity(seconds))
                {
                    throw new FormatException(where + ": \"seconds\" must be a number above 0");
                }
                steps.Add(new Step
                {
                    Seconds = seconds,
                    Gamepad1 = Gamepad(step["gamepad1"], where + " gamepad1"),
                    Gamepad2 = Gamepad(step["gamepad2"], where + " gamepad2"),
                });
            }
            return steps;
        }

        /// <summary>
        /// Both gamepads <paramref name="secondsSincePlay"/> after PLAY (simulated time): the step
        /// that covers that moment, or released after the last one. Index 0 is gamepad 1.
        /// </summary>
        public JObject[] GamepadsAt(double secondsSincePlay)
        {
            double start = 0;
            if (Steps != null)
            {
                foreach (Step step in Steps)
                {
                    // A tiny margin so 20 ms steps don't land a hair past an edge through rounding.
                    if (secondsSincePlay < start + step.Seconds - 1e-9)
                    {
                        return new[] { (JObject)step.Gamepad1.DeepClone(), (JObject)step.Gamepad2.DeepClone() };
                    }
                    start += step.Seconds;
                }
            }
            return new[] { Released(), Released() };
        }

        /// <summary>
        /// The OpMode to start among those the harness offers (id, any case). Throws
        /// <see cref="FormatException"/> naming the ids on offer when it isn't one of them.
        /// </summary>
        public LiveSession.OpModeChoice Pick(List<LiveSession.OpModeChoice> offered)
        {
            LiveSession.OpModeChoice choice = offered?.Find(c => string.Equals(c.id, OpModeId, StringComparison.OrdinalIgnoreCase));
            if (choice == null)
            {
                throw new FormatException("the harness offers no OpMode \"" + OpModeId + "\""
                    + (offered == null || offered.Count == 0 ? "" : " (it offers " + string.Join(", ", offered.ConvertAll(c => c.id)) + ")"));
            }
            return choice;
        }

        /// <summary>A gamepad with nothing pressed, in the bridge's layout.</summary>
        public static JObject Released()
        {
            var pad = new JObject { ["buttons"] = new JArray() };
            foreach (string axis in Axes)
            {
                pad[axis] = 0.0;
            }
            return pad;
        }

        private static JObject Gamepad(JToken token, string where)
        {
            JObject pad = Released();
            if (token == null || token.Type == JTokenType.Null)
            {
                return pad;
            }
            if (!(token is JObject given))
            {
                throw new FormatException(where + " isn't an object");
            }
            foreach (JProperty property in given.Properties())
            {
                if (property.Name == "buttons")
                {
                    if (!(property.Value is JArray buttons))
                    {
                        throw new FormatException(where + ": \"buttons\" must be a list of button names");
                    }
                    foreach (JToken button in buttons)
                    {
                        string name = button.Type == JTokenType.String ? (string)button : null;
                        if (name == null || !ButtonNames.Contains(name))
                        {
                            throw new FormatException(where + ": unknown button " + button.ToString(Newtonsoft.Json.Formatting.None));
                        }
                    }
                    pad["buttons"] = buttons.DeepClone();
                }
                else if (Array.IndexOf(Axes, property.Name) >= 0)
                {
                    bool number = property.Value.Type == JTokenType.Integer || property.Value.Type == JTokenType.Float;
                    double value = number ? property.Value.Value<double>() : double.NaN;
                    bool trigger = property.Name.EndsWith("Trigger", StringComparison.Ordinal);
                    if (!(value >= (trigger ? 0 : -1) && value <= 1))
                    {
                        throw new FormatException(where + ": \"" + property.Name + "\" must be a number from "
                            + (trigger ? "0" : "-1") + " to 1");
                    }
                    pad[property.Name] = value;
                }
                else
                {
                    throw new FormatException(where + ": unknown key \"" + property.Name + "\"");
                }
            }
            return pad;
        }

        private static string ValueAfter(string[] args, string name)
        {
            for (int i = 0; i < args.Length; i++)
            {
                if (string.Equals(args[i], name, StringComparison.OrdinalIgnoreCase))
                {
                    if (i + 1 >= args.Length || args[i + 1].StartsWith("-", StringComparison.Ordinal))
                    {
                        throw new FormatException(name + " needs a value after it");
                    }
                    return args[i + 1];
                }
            }
            return null;
        }
    }
}
