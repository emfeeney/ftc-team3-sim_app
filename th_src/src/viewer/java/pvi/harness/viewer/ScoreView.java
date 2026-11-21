package pvi.harness.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pvi.harness.runtime.RunRecording;
import pvi.harness.scoring.AllianceScore;
import pvi.harness.scoring.MatchScore;
import pvi.harness.scoring.ScoreItem;

/**
 * The match points as text lines for the side panel's "Score" card (manager decision D50), kept apart from the
 * drawing so tests can check them without a screen.
 */
final class ScoreView {
    private ScoreView() {
    }

    /**
     * The score to show at one moment: the loop's own (as it stood), except at the last loop, where the recording's
     * final score (after the pieces came to rest) is shown. Null for a recording older than version 8.
     */
    static MatchScore scoreAt(RunRecording recording, int index) {
        RunRecording.LoopRecord loop = recording.loops().get(index);
        boolean last = index == recording.loops().size() - 1;
        return last && recording.score() != null ? recording.score() : loop.score();
    }

    /**
     * The lines: what kind of score it is, then each alliance's total and ranking points, then its point-table
     * lines ("  PARK            1 x  5 =   5").
     */
    static List<String> lines(MatchScore score) {
        List<String> lines = new ArrayList<>();
        String robot = score.robotAlliance() == null ? "robot: no alliance" : "robot: " + score.robotAlliance().name();
        String when = score.settled() ? String.format(Locale.ROOT, "final, settled after %.2f s", score.settleSeconds())
                : score.settleSeconds() > 0 ? "final, still moving after the settle limit" : "as it stands";
        lines.add(score.period().name() + " · " + robot + " · " + when);
        for (AllianceScore alliance : List.of(score.red(), score.blue())) {
            lines.add(String.format(Locale.ROOT, "%-5s %4d pts   RP %d%s", alliance.alliance().name(), alliance.total(),
                    alliance.rankingPoints(), rpNames(alliance)));
            for (ScoreItem item : alliance.items()) {
                lines.add(String.format(Locale.ROOT, "  %-14s %2d x %2d = %3d", item.name(), item.count(),
                        item.pointsEach(), item.points()));
            }
        }
        for (String note : score.notes()) {
            lines.add("note: " + note);
        }
        return lines;
    }

    private static String rpNames(AllianceScore a) {
        List<String> names = new ArrayList<>();
        if (a.swarmRp()) {
            names.add("SWARM");
        }
        if (a.pollinator1Rp()) {
            names.add("POLLINATOR 1");
        }
        if (a.pollinator2Rp()) {
            names.add("POLLINATOR 2");
        }
        return names.isEmpty() ? "" : " (" + String.join(", ", names) + ")";
    }
}
