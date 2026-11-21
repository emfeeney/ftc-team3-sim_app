using System.Collections.Generic;

namespace VI_Arena
{
    // These classes mirror the harness's run recording JSON, format versions 1-4 (described field by
    // field in TestHarness/docs/README.md, "Recording a whole run (JSON)"). Field names are written exactly
    // as in the JSON (camelCase), so anyone can match a C# field to the file at a glance.
    //
    // Units are the harness's FTC units: inches, radians, seconds. Nothing here converts to Unity
    // units; FtcToUnity does that, in one place.

    /// <summary>A whole recorded run: what was run, and one entry per OpMode call.</summary>
    public sealed class RunRecordingData
    {
        public HeaderData header;
        public List<RumbleData> gamepad1Rumbles = new List<RumbleData>();
        public List<RumbleData> gamepad2Rumbles = new List<RumbleData>();
        /// <summary>Marked moments (version 2). Version 1 files have none.</summary>
        public List<EventData> events = new List<EventData>();
        /// <summary>A Pedro robot's planned path each time it changed, oldest first (version 7, D39). Older files have none.</summary>
        public List<PlannedPathData> plannedPaths = new List<PlannedPathData>();
        public List<LoopData> loops = new List<LoopData>();
        /// <summary>
        /// The final match points (version 8, D50): after STOP the pieces were left to come to rest (up to 5 s), then
        /// scored. Null in older recordings, and in a live run until it has stopped.
        /// </summary>
        public ScoreData score;
    }

    /// <summary>
    /// The path a Pedro follower was about to follow, from this moment on (version 7, D39): field points
    /// [xInches, yInches] at most 2 in apart, from the start of its current segment to the path's end. No
    /// points means no plan from then on.
    /// </summary>
    public sealed class PlannedPathData
    {
        public double sinceInitSeconds;
        public double matchTimeSeconds;
        public List<double[]> points = new List<double[]>();
    }

    /// <summary>What was run, on which robot, and the format version.</summary>
    public sealed class HeaderData
    {
        public int version;
        public string distanceUnit;
        public string angleUnit;
        public string timeUnit;
        public string robotConfig;
        public string opMode;
        /// <summary>"AUTONOMOUS", "TELEOP", or null.</summary>
        public string period;
        public double loopPeriodSeconds;
        /// <summary>Null if position wasn't tracked.</summary>
        public PoseData startPose;
        /// <summary>Null if position wasn't tracked.</summary>
        public DriveModelData driveModel;
        /// <summary>
        /// Optional, not written by the harness yet: the season field file id ("biobuzz-2026") the
        /// replay should show. Null means the viewer's own choice.
        /// </summary>
        public string field;
        /// <summary>
        /// Every game piece (POLLEN, NECTAR) at INIT (version 4). Empty in older files, and when position
        /// wasn't tracked.
        /// </summary>
        public List<PieceData> pieces = new List<PieceData>();
        /// <summary>Each HIVE's tips and swing at INIT (version 6); empty in older recordings.</summary>
        public List<HiveTiltData> hives = new List<HiveTiltData>();
        /// <summary>
        /// Each webcam's setup (version 5): where it sits on the robot, which way it looks, its picture
        /// size, field of view and range. Empty in older files and for a robot without a webcam.
        /// </summary>
        public List<CameraData> cameras = new List<CameraData>();
    }

    /// <summary>
    /// One webcam's setup (version 5), from harness.properties: where it sits on the robot, which
    /// way it looks, its picture size, field of view and range. Robot frame: x forward, y left, z up,
    /// from the robot's center on the floor (the drive pose's point). Angles: all 0 is level, looking
    /// straight forward; yaw + turns the lens left, pitch + tilts it up, roll + turns the picture
    /// clockwise as seen from behind the camera; yaw, then pitch, then roll, each about the camera's
    /// own axes.
    /// </summary>
    public sealed class CameraData
    {
        /// <summary>The webcam's configuration name, the same as <see cref="AprilTagSeenData.camera"/>.</summary>
        public string name;
        public double forwardInches;
        public double leftInches;
        public double upInches;
        public double yawDegrees;
        public double pitchDegrees;
        public double rollDegrees;
        public double horizontalFovDegrees;
        public double verticalFovDegrees;
        public int imageWidthPixels;
        public int imageHeightPixels;
        /// <summary>How far away the camera can still read a tag.</summary>
        public double maxRangeInches;
        /// <summary>How far a tag can be turned away from the camera and still be read (degrees); 0 if the recording doesn't say.</summary>
        public double maxViewAngleDegrees;
        /// <summary>The names of the fields above that are guesses, not measurements (e.g. "horizontalFovDegrees").</summary>
        public List<string> placeholders = new List<string>();
    }

    /// <summary>A robot position on the FTC field: inches from the center, heading counter-clockwise from +x.</summary>
    public sealed class PoseData
    {
        public double xInches;
        public double yInches;
        public double headingRadians;
    }

    /// <summary>The robot's speeds and size, each with its unit and whether it was measured.</summary>
    public sealed class DriveModelData
    {
        public string name;
        public ModelValueData forward;
        public ModelValueData strafe;
        public ModelValueData turn;
        public ModelValueData length;
        public ModelValueData width;
    }

    /// <summary>One robot fact: the number, its unit, where it came from, and if it was measured.</summary>
    public sealed class ModelValueData
    {
        public double value;
        public string unit;
        public bool measured;
        public string source;
    }

    /// <summary>The robot right after one OpMode call.</summary>
    public sealed class LoopData
    {
        public double sinceInitSeconds;
        public double matchTimeSeconds;
        /// <summary>"INIT", "INIT_LOOP", "START", "LOOP" or "STOP".</summary>
        public string stage;
        /// <summary>Null if position wasn't tracked.</summary>
        public PoseData pose;
        public List<MotorData> motors = new List<MotorData>();
        public List<ServoData> servos = new List<ServoData>();
        /// <summary>The "Auto Step" telemetry value sent during this call, or null.</summary>
        public string autoStep;
        public List<TelemetryScreenData> telemetry = new List<TelemetryScreenData>();
        public GamepadData gamepad1;
        public GamepadData gamepad2;
        /// <summary>
        /// The game pieces that changed since the previous loop (version 4): moved, or changed state.
        /// Pieces not listed are where they were. <see cref="GamePieceTrack"/> adds these up.
        /// </summary>
        public List<PieceData> pieces = new List<PieceData>();
        /// <summary>
        /// Each HIVE's tips and swing after this call (version 6): the harness decides when a HIVE tips. Empty in
        /// older recordings, where VI Arena works it out itself (<see cref="HiveTip"/>).
        /// </summary>
        public List<HiveTiltData> hives = new List<HiveTiltData>();
        /// <summary>
        /// The AprilTags in each fake webcam's latest picture during this call (version 3): what robot code
        /// could detect. Empty with no webcam or no tag in view.
        /// </summary>
        public List<AprilTagSeenData> aprilTags = new List<AprilTagSeenData>();
        /// <summary>
        /// Each alliance's match points after this call (version 8, manager decision D50), worked out by the harness.
        /// Null in older recordings.
        /// </summary>
        public ScoreData score;
    }

    /// <summary>
    /// The match points of both alliances (version 8, D50; the harness's MatchScore). A run scores the period it
    /// covers. In a loop it is the score at that moment; the recording's own <see cref="RunRecordingData.score"/> is
    /// the final one, taken once the pieces have come to rest after STOP (<see cref="settled"/>).
    /// </summary>
    public sealed class ScoreData
    {
        /// <summary>"AUTONOMOUS" or "TELEOP": which column of the manual's table.</summary>
        public string period;
        /// <summary>"red", "blue" or null: the alliance the simulated robot earns LEAVE and PARK for.</summary>
        public string robotAlliance;
        /// <summary>True once the pieces came to rest after STOP (false: still moving when the 5 s were up, or a live moment).</summary>
        public bool settled;
        /// <summary>How long the pieces took to come to rest after STOP.</summary>
        public double settleSeconds;
        public AllianceScoreData red = new AllianceScoreData();
        public AllianceScoreData blue = new AllianceScoreData();
        /// <summary>What the harness couldn't score, if anything (usually empty).</summary>
        public List<string> notes = new List<string>();
    }

    /// <summary>One alliance's points: the total, its ranking points, and every line of the period (count 0 if not earned).</summary>
    public sealed class AllianceScoreData
    {
        public int total;
        public int rankingPoints;
        public bool swarm;
        public bool pollinator1;
        public bool pollinator2;
        public List<ScoreItemData> items = new List<ScoreItemData>();
    }

    /// <summary>One line of the manual's Table 10-2, e.g. "HIVE TIP", 1 time, 20 each, 20 points.</summary>
    public sealed class ScoreItemData
    {
        /// <summary>"LEAVE", "PARK", "HIVE TIP", "CELL", "FLOWER", "BOTTOM NECTAR" or "GARDEN".</summary>
        public string name;
        public int count;
        public int pointsEach;
        /// <summary>count times pointsEach.</summary>
        public int points;
    }

    /// <summary>
    /// One AprilTag a webcam saw, measured from its lens like the SDK's ftcPose: right, forward and up
    /// (inches) and yaw, pitch and roll (degrees). Where the webcam sits on the robot isn't recorded.
    /// </summary>
    public sealed class AprilTagSeenData
    {
        /// <summary>The webcam's configuration name, e.g. "logitechVisionWebcam".</summary>
        public string camera;
        public int id;
        public double rightInches;
        public double forwardInches;
        public double upInches;
        public double yawDegrees;
        public double pitchDegrees;
        public double rollDegrees;
    }

    /// <summary>
    /// Where one HIVE is (version 6): how many times it has tipped, and how far it has swung from its
    /// starting position, in degrees about FTC +y with + taking +x up toward +z (a full tip is -60 for
    /// red and +60 for blue; see <see cref="HiveTip.FullSwingDegrees"/>).
    /// </summary>
    public sealed class HiveTiltData
    {
        /// <summary>The field file's HIVE id, e.g. "red-hive".</summary>
        public string id;
        public int tips;
        public double swingDegrees;
    }

    /// <summary>
    /// One game piece: a POLLEN or NECTAR ball, where its center is, and what it is doing.
    /// </summary>
    public sealed class PieceData
    {
        /// <summary>Stays the same for the whole run, e.g. "pollen-garden-red-1".</summary>
        public string id;
        /// <summary>"pollen", "red-nectar" or "blue-nectar".</summary>
        public string kind;
        /// <summary>
        /// "field" (on the tiles, can be pushed), "flower", "cell", "alliance-area", "held" (picked up by
        /// the intake, riding on the robot) or "flying" (in the air after a launch).
        /// </summary>
        public string state;
        public double xInches;
        public double yInches;
        /// <summary>Height of the ball's center above the tiles.</summary>
        public double zInches;
    }

    /// <summary>One motor's power, -1 to 1.</summary>
    public sealed class MotorData
    {
        public string name;
        public double power;
    }

    /// <summary>One servo's position, 0 to 1, or null if the code never set one.</summary>
    public sealed class ServoData
    {
        public string name;
        public double? position;
    }

    /// <summary>One Driver Station screen, as sent by one telemetry.update().</summary>
    public sealed class TelemetryScreenData
    {
        public List<string> lines = new List<string>();
        public List<string> log = new List<string>();
    }

    /// <summary>A gamepad's held buttons, sticks (up is negative) and triggers.</summary>
    public sealed class GamepadData
    {
        public List<string> buttons = new List<string>();
        public double leftStickX;
        public double leftStickY;
        public double rightStickX;
        public double rightStickY;
        public double leftTrigger;
        public double rightTrigger;
    }

    /// <summary>One rumble command sent to a gamepad.</summary>
    public sealed class RumbleData
    {
        public double matchTimeSeconds;
        public int totalDurationMs;
        public int pulses;
    }

    /// <summary>A marked moment, e.g. a controller disconnecting during a live drive.</summary>
    public sealed class EventData
    {
        public double sinceInitSeconds;
        public double matchTimeSeconds;
        public string text;
    }
}
