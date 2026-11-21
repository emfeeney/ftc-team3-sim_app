package pvi.harness.scoring;

import java.util.List;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.runtime.MatchPeriod;

/**
 * Everything {@link MatchScorer} looks at for one moment, as plain data.
 *
 * @param period            which column of the point table
 * @param robotAlliance     the simulated robot's alliance, or null (it then earns nothing)
 * @param robot             where the robot is, or null for no robot
 * @param robotLengthInches the robot box, front to back
 * @param robotWidthInches  the robot box, side to side
 * @param pieces            every ball now
 * @param hives             every HIVE now (its tips so far and its swing)
 * @param settled           true if the pieces have come to rest after STOP (the final score)
 * @param settleSeconds     how long they took, in seconds (0 during the run)
 */
public record ScoreInput(MatchPeriod period, Alliance robotAlliance, Pose robot, double robotLengthInches,
                         double robotWidthInches, List<GamePiece> pieces, List<HiveTilt> hives, boolean settled,
                         double settleSeconds) {

    /** Copies the lists and refuses a missing period or list. */
    public ScoreInput {
        if (period == null || pieces == null || hives == null) {
            throw new IllegalArgumentException("Scoring needs a period, the pieces and the HIVEs (lists may be empty).");
        }
        pieces = List.copyOf(pieces);
        hives = List.copyOf(hives);
    }
}
