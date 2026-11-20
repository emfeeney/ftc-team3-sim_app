package pvi.harness;

import org.junit.jupiter.api.Test;
import pvi.harness.hardware.RobotConfigs;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.MatchPeriod;
import pvi.harness.runtime.OpModeCatalog;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;
import pvi.harness.runtime.RunRecording;
import pvi.harness.runtime.RunRecordingJson;

import java.nio.file.Path;

public class GenerateTeam3AutonomousRecordingsTest {

    @Test
    void generateAllAutonomousRecordings() {
        HarnessConfig config = HarnessConfig.load();
        for (OpModeCatalog.Entry e : OpModeCatalog.fromConfig(config)) {
            if (e.period() == MatchPeriod.AUTONOMOUS && !config.smokeTestSkipOpModes().contains(e.name())) {
                try {
                    System.out.println("Simulating Autonomous: " + e.name() + " (" + e.simpleName() + ")...");
                    OpModeRunner runner = new OpModeRunner(e.newOpMode(), RobotConfigs.forOpMode(e, config));
                    RunRecorder recorder = RunRecorder.attachTo(runner);
                    runner.run(1.0, MatchPeriod.AUTONOMOUS.seconds());
                    RunRecording recording = recorder.recording();
                    Path file = RunRecordingJson.fileInDefaultFolder("auto-" + e.simpleName());
                    RunRecordingJson.save(recording, file);
                    System.out.println("Saved 3D recording to: " + file.getFileName());
                } catch (Throwable t) {
                    System.err.println("Skipped Autonomous " + e.name() + " due to error: " + t.getMessage());
                }
            }
        }
    }
}
