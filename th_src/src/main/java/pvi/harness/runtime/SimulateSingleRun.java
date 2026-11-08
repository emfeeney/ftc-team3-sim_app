package pvi.harness.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.Pose;

/**
 * Fast CLI entrypoint to simulate a single autonomous OpMode with custom starting pose.
 * Used by the Hugging Face Spaces / Docker simulation API server.
 */
public final class SimulateSingleRun {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: SimulateSingleRun <opModeSimpleName> <outputJsonPath> [xInches] [yInches] [headingDegrees]");
            System.exit(1);
        }

        String opModeName = args[0];
        Path outputPath = Paths.get(args[1]);

        Pose customStartPose = null;
        if (args.length >= 5) {
            try {
                double x = Double.parseDouble(args[2].trim());
                double y = Double.parseDouble(args[3].trim());
                double hDeg = Double.parseDouble(args[4].trim());
                customStartPose = Pose.inches(x, y, Math.toRadians(hDeg));
                System.setProperty("sim.startPose", x + "," + y + "," + hDeg);
            } catch (Exception e) {
                System.err.println("Warning: Invalid custom start pose numbers. Using default origin.");
            }
        }

        HarnessConfig config = HarnessConfig.load();
        boolean found = false;

        for (OpModeCatalog.Entry e : OpModeCatalog.fromConfig(config)) {
            if (e.simpleName().equalsIgnoreCase(opModeName) || e.name().equalsIgnoreCase(opModeName)) {
                found = true;
                OpModeRunner runner = new OpModeRunner(e.newOpMode(), RobotConfigs.forOpMode(e, config));
                if (customStartPose != null) {
                    runner.startAt(customStartPose);
                }
                RunRecorder recorder = RunRecorder.attachTo(runner);
                runner.run(1.0, MatchPeriod.AUTONOMOUS.seconds()).assertNoException();
                RunRecording recording = recorder.recording();
                RunRecordingJson.save(recording, outputPath);
                System.out.println("SIMULATION_SUCCESS: " + outputPath.toAbsolutePath());
                break;
            }
        }

        if (!found) {
            System.err.println("Error: Autonomous OpMode not found: " + opModeName);
            System.exit(2);
        }
    }
}

