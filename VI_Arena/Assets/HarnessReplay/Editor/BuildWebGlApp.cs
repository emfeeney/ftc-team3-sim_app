using System.IO;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace PviHarness.Replay.Editor
{
    public static class BuildWebGlApp
    {
        public const string Scene = "Assets/HarnessReplay/Replay.unity";
        public static string OutputDir => Path.GetFullPath(Path.Combine(Application.dataPath, "..", "..", "web", "vi_arena"));

        [MenuItem("Harness/Build WebGL App")]
        public static void Build()
        {
            BuildReport report = BuildApp();
            Debug.Log("[HarnessReplay] WebGL Build " + report.summary.result + ": " + OutputDir);
        }

        public static void BuildFromCommandLine()
        {
            BuildReport report = BuildApp();
            System.Console.WriteLine("[HarnessReplay] WebGL Build " + report.summary.result + ": " + OutputDir);
            EditorApplication.Exit(report.summary.result == BuildResult.Succeeded ? 0 : 1);
        }

        private static BuildReport BuildApp()
        {
            PlayerSettings.productName = "VI_Arena";
            PlayerSettings.companyName = "PVI FTC";
            PlayerSettings.runInBackground = true;

            // Configure WebGL settings
            PlayerSettings.WebGL.compressionFormat = WebGLCompressionFormat.Disabled; // faster load and no server gzip configuration issues
            PlayerSettings.WebGL.decompressionFallback = true;
            PlayerSettings.WebGL.linkerTarget = WebGLLinkerTarget.Wasm;

            BuildWindowsApp.KeepNormalMappedVariant();

            if (!Directory.Exists(OutputDir))
            {
                Directory.CreateDirectory(OutputDir);
            }

            var options = new BuildPlayerOptions
            {
                scenes = new[] { Scene },
                locationPathName = OutputDir,
                target = BuildTarget.WebGL,
                options = BuildOptions.None,
            };
            return BuildPipeline.BuildPlayer(options);
        }
    }
}

