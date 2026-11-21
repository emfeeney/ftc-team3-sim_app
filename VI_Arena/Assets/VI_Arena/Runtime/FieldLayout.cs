using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Newtonsoft.Json;
using UnityEngine;

namespace VI_Arena
{
    /// <summary>
    /// One season's field, read from <c>TestHarness/th_src/fields/&lt;id&gt;.json</c> (for example
    /// <c>biobuzz-2026.json</c>): its fixed parts (boxes and cylinders in FTC inches), its AprilTags
    /// and where the numbers came from. The format is documented in TestHarness/docs/README.md ("Season field
    /// files"). It is plain JSON with no Unity in it, so the harness's Java can read the same files.
    /// </summary>
    public sealed class FieldLayoutData
    {
        public string format;
        public int version;
        /// <summary>Short id, the file name without .json: "biobuzz-2026".</summary>
        public string id;
        /// <summary>The game's name, shown in the field picker: "BIOBUZZ".</summary>
        public string name;
        public string season;
        public List<string> sources = new List<string>();
        public string frame;
        public string accuracyNote;
        public string startingNote;
        public List<FieldElementData> elements = new List<FieldElementData>();
        public List<FieldTagData> aprilTags = new List<FieldTagData>();
        /// <summary>What a ball rolling on the tiles can hit (version 2); empty in version 1 files.</summary>
        public List<PieceObstacleData> pieceObstacles = new List<PieceObstacleData>();
        /// <summary>The parts that swing during a match (version 3); empty in older files.</summary>
        public List<HiveData> hives = new List<HiveData>();
        /// <summary>What a ball in the air can hit (version 4), for the harness's flight; empty in older files.</summary>
        public List<FlightObstacleData> flightObstacles = new List<FlightObstacleData>();
    }

    /// <summary>
    /// One HIVE (field format version 3): where it pivots, which of its parts swing, and the rule for when
    /// it tips (<see cref="HiveTip"/>). FTC inches. The axle runs along y.
    /// </summary>
    public sealed class HiveData
    {
        public string id;
        public string name;
        public double pivotXInches;
        public double pivotYInches;
        public double pivotZInches;
        /// <summary>The HIVE's reach along y: a ball in a CELL is inside it.</summary>
        public double minYInches;
        public double maxYInches;
        /// <summary>How far each stable position is tilted from level.</summary>
        public double tiltDegrees;
        /// <summary>Which CELL is up at the start of a match: +1 the one on the pivot's +x side, -1 the -x side.</summary>
        public int startUpSide;
        /// <summary>The weight in the upward CELL that tips it. A placeholder: the manual only says "enough".</summary>
        public double tipMassGrams;
        /// <summary>How long a tip takes. A placeholder.</summary>
        public double tipSeconds;
        /// <summary>The CAD parts that swing with it.</summary>
        public List<string> cadParts = new List<string>();
        /// <summary>The simple shapes that swing with it, when the CAD isn't there.</summary>
        public List<string> elementIds = new List<string>();
        public string accuracy;
        /// <summary>Its two CELLs (version 4); empty in older files.</summary>
        public List<CellData> cells = new List<CellData>();
    }

    /// <summary>
    /// The inside of one CELL (field format version 4), in its HIVE's own frame at the starting tilt:
    /// along the arm from the pivot (+ toward the +x side) and square to it (+ up at the start), FTC
    /// inches. The CELL is open at its outer end, so the CELL that goes down spills its balls.
    /// </summary>
    public sealed class CellData
    {
        public string id;
        /// <summary>+1 the CELL on the pivot's +x side, -1 the -x side.</summary>
        public int side;
        /// <summary>The back wall, this far out from the pivot along the arm.</summary>
        public double backInches;
        /// <summary>The open end, this far out.</summary>
        public double openingInches;
        /// <summary>The floor and the top, square to the arm.</summary>
        public double floorInches;
        /// <summary>
        /// The floor's underside (CAD: the frame under the inner floor, -2.97); the floor is solid from here
        /// up to <see cref="floorInches"/> (manager decision D31). Optional: 0 when the file doesn't say.
        /// </summary>
        public double floorUndersideInches;
        public double topInches;
        public double minYInches;
        public double maxYInches;
    }

    /// <summary>
    /// Something a ball in the air can hit (field format version 4): a "capsule" bar
    /// (<see cref="fromInches"/> to <see cref="toInches"/>, <see cref="radiusInches"/>), an upright "box"
    /// (<see cref="minInches"/> to <see cref="maxInches"/>), or a "hiveBox" that swings with its HIVE
    /// (along, across and y, as in <see cref="CellData"/>). FTC inches. The harness's flight uses these;
    /// VI Arena only keeps them as data.
    /// </summary>
    public sealed class FlightObstacleData
    {
        public string id;
        public string shape;
        public string hive;
        public List<double> fromInches;
        public List<double> toInches;
        public double radiusInches;
        public List<double> minInches;
        public List<double> maxInches;
        public double minAlongInches;
        public double maxAlongInches;
        public double minAcrossInches;
        public double maxAcrossInches;
        public double minYInches;
        public double maxYInches;
    }

    /// <summary>
    /// Something a POLLEN or NECTAR ball on the tiles can hit (field format version 2), for the
    /// harness's ball physics. It has straight upright sides from <see cref="bottomInches"/> to
    /// <see cref="topInches"/> above the tiles; a ball of radius r can hit it only if its bottom is
    /// below the ball's top (2 r). A "box" lies along the x and y axes (<see cref="minXInches"/> to
    /// <see cref="maxYInches"/>); a "cylinder" is upright (<see cref="xInches"/>, <see cref="yInches"/>,
    /// <see cref="radiusInches"/>). FTC inches.
    /// </summary>
    public sealed class PieceObstacleData
    {
        public string id;
        public string name;
        /// <summary>"box" or "cylinder".</summary>
        public string shape;
        public double minXInches;
        public double maxXInches;
        public double minYInches;
        public double maxYInches;
        /// <summary>Cylinders only: the center and radius.</summary>
        public double xInches;
        public double yInches;
        public double radiusInches;
        public double bottomInches;
        public double topInches;
        public string accuracy;
    }

    /// <summary>
    /// A fixed part of the field: a box or an upright cylinder. Position is the part's center, in
    /// FTC inches (z = height of the center above the tiles). A box's length runs along +x and its
    /// width along +y before it is turned: first tilted (<see cref="tiltDegrees"/>, positive raises
    /// its +x end), then turned to <see cref="headingDegrees"/> (counter-clockwise from +x).
    /// </summary>
    public sealed class FieldElementData
    {
        public string id;
        public string name;
        /// <summary>"box" or "cylinder".</summary>
        public string shape;
        public double xInches;
        public double yInches;
        public double zInches;
        public double lengthInches;
        public double widthInches;
        public double heightInches;
        /// <summary>Cylinders only.</summary>
        public double diameterInches;
        public double headingDegrees;
        public double tiltDegrees;
        /// <summary>"#RRGGBB".</summary>
        public string color;
        /// <summary>"nominal" (the manual's numbers), "approximate" (worked out from its figures) or "sdk".</summary>
        public string accuracy;
    }

    /// <summary>
    /// One AprilTag. Position (its center) and <see cref="facing"/> (the way its printed side
    /// points, a unit vector) are in the FTC field frame, or null when the field position isn't
    /// known (such tags aren't drawn).
    /// </summary>
    public sealed class FieldTagData
    {
        public int id;
        public string name;
        /// <summary>The SDK's cluster name for tags printed together (BIOBUZZ), or null.</summary>
        public string cluster;
        public double sizeInches;
        /// <summary>The SDK's position of this tag inside its cluster, copied exactly; null without a cluster.</summary>
        public FieldVectorData clusterOffsetInches;
        public double? xInches;
        public double? yInches;
        public double? zInches;
        public FieldVectorData facing;
        /// <summary>True if the tag moves during a match (BIOBUZZ HIVE tags); its pose here is the starting one.</summary>
        public bool moves;
        public string accuracy;

        /// <summary>True when the file says where the tag is and which way it faces.</summary>
        public bool HasFieldPose => xInches.HasValue && yInches.HasValue && zInches.HasValue && facing != null;
    }

    /// <summary>A direction or offset with x, y and z (FTC frame unless the field says otherwise).</summary>
    public sealed class FieldVectorData
    {
        public double x;
        public double y;
        public double z;
    }

    /// <summary>
    /// Finds and reads the season field files, and picks which one to show.
    /// </summary>
    public static class FieldLayouts
    {
        /// <summary>The "no season" choice: only the plain tiled field.</summary>
        public const string PlainFieldId = "plain";
        /// <summary>The format versions this project can read.</summary>
        public static readonly int[] SupportedVersions = { 1, 2, 3, 4 };

        /// <summary>TestHarness/th_src/fields, or the built app's own copy (see <see cref="ResolveFolder"/>).</summary>
        public static string DefaultFolder => ResolveFolder(Environment.GetCommandLineArgs(), TestHarnessFolder.Current, Application.dataPath);

        /// <summary>
        /// The field files folder: <c>-fields &lt;folder&gt;</c> on the command line, else
        /// TestHarness/th_src/fields when the TestHarness folder is known, else the built app's own copy
        /// (VI_Arena_Data/harness-files/fields, see <see cref="TestHarnessFolder.HarnessFiles"/>).
        /// </summary>
        public static string ResolveFolder(string[] args, string testHarness, string dataPath)
        {
            for (int i = 0; i < args.Length - 1; i++)
            {
                if (string.Equals(args[i], "-fields", StringComparison.OrdinalIgnoreCase))
                {
                    return Path.GetFullPath(args[i + 1]);
                }
            }
            return TestHarnessFolder.HarnessFiles(testHarness, dataPath, "fields");
        }

        /// <summary>Reads one field file. Throws <see cref="RecordingFormatException"/> saying what is wrong.</summary>
        public static FieldLayoutData Load(string path)
        {
            FieldLayoutData field;
            try
            {
                field = JsonConvert.DeserializeObject<FieldLayoutData>(File.ReadAllText(path));
            }
            catch (Exception e) when (e is IOException || e is JsonException || e is UnauthorizedAccessException)
            {
                throw new RecordingFormatException("Can't read field file " + path + ": " + e.Message, e);
            }
            string file = Path.GetFileName(path);
            if (field == null || field.format != "pvi-harness-field")
            {
                throw new RecordingFormatException(file + " is not a field file (expected \"format\": \"pvi-harness-field\").");
            }
            if (!SupportedVersions.Contains(field.version))
            {
                throw new RecordingFormatException(file + " is field format version " + field.version
                    + "; this project reads version " + string.Join(", ", SupportedVersions) + ".");
            }
            if (string.IsNullOrWhiteSpace(field.id) || string.IsNullOrWhiteSpace(field.name))
            {
                throw new RecordingFormatException(file + " needs an \"id\" and a \"name\".");
            }
            foreach (FieldElementData element in field.elements)
            {
                if (element.shape != "box" && element.shape != "cylinder")
                {
                    throw new RecordingFormatException(file + ": element \"" + element.id + "\" has shape \""
                        + element.shape + "\"; expected \"box\" or \"cylinder\".");
                }
            }
            foreach (PieceObstacleData obstacle in field.pieceObstacles)
            {
                bool box = obstacle.shape == "box" && obstacle.minXInches < obstacle.maxXInches && obstacle.minYInches < obstacle.maxYInches;
                bool cylinder = obstacle.shape == "cylinder" && obstacle.radiusInches > 0;
                if ((!box && !cylinder) || obstacle.bottomInches < 0 || obstacle.topInches <= obstacle.bottomInches)
                {
                    throw new RecordingFormatException(file + ": piece obstacle \"" + obstacle.id + "\" needs shape \"box\" "
                        + "(min below max in x and y) or \"cylinder\" (radius above 0), and a top above its bottom (0 or more).");
                }
            }
            return field;
        }

        /// <summary>
        /// Every readable field file in a folder, sorted by id. Files that can't be read are skipped
        /// and reported in <paramref name="problems"/>, so one bad file doesn't hide the others.
        /// </summary>
        public static List<FieldLayoutData> LoadAll(string folder, List<string> problems)
        {
            var fields = new List<FieldLayoutData>();
            if (!Directory.Exists(folder))
            {
                return fields;
            }
            foreach (string path in Directory.GetFiles(folder, "*.json").OrderBy(p => p, StringComparer.OrdinalIgnoreCase))
            {
                try
                {
                    fields.Add(Load(path));
                }
                catch (RecordingFormatException e)
                {
                    problems?.Add(e.Message);
                }
            }
            return fields;
        }

        /// <summary>
        /// Which field to show: the one a recording asks for (its header's optional "field"), else
        /// the one picked last time, else the plain field. Ids that aren't available are ignored.
        /// </summary>
        public static string Pick(string recordingField, string remembered, IEnumerable<string> availableIds)
        {
            var available = new HashSet<string>(availableIds, StringComparer.OrdinalIgnoreCase);
            if (!string.IsNullOrEmpty(recordingField) && available.Contains(recordingField))
            {
                return recordingField;
            }
            if (!string.IsNullOrEmpty(remembered) && available.Contains(remembered))
            {
                return remembered;
            }
            return PlainFieldId;
        }

        /// <summary>The field id after <c>-field</c> on the command line ("-field biobuzz-2026"), or null.</summary>
        public static string FieldFromCommandLine(string[] args)
        {
            for (int i = 0; i < args.Length - 1; i++)
            {
                if (string.Equals(args[i], "-field", StringComparison.OrdinalIgnoreCase))
                {
                    return args[i + 1];
                }
            }
            return null;
        }

        /// <summary>A "#RRGGBB" color, or grey if it can't be read.</summary>
        public static Color ParseColor(string hex)
        {
            return ColorUtility.TryParseHtmlString(hex ?? "", out Color color) ? color : new Color(0.5f, 0.5f, 0.5f);
        }
    }
}
