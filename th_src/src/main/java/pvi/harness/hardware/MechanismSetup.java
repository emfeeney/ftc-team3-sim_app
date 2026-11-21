package pvi.harness.hardware;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import pvi.harness.physics.GamePiece;
import pvi.harness.physics.ModelValue;

/**
 * A robot's intake and launcher, as plain data, read from its {@code robots/<id>/mechanism.properties}.
 * The harness watches the motors the robot code already runs, so robot code doesn't change. No file
 * means no intake and no launcher, and a robot never gets another robot's.
 *
 * <p>Positions are in the robot's frame: forward and left of its center, up from the floor. A
 * direction is the setting that makes positive power run that motor the useful way, as with the drive
 * motors: inward for an intake or a feed, launching for a launcher.</p>
 *
 * @param intake   the intake, or null if {@code intake.motor} is empty
 * @param hold     how many balls it holds and of which kinds
 * @param launcher the launcher and what feeds it, or null if {@code launcher.motor} is empty
 */
public record MechanismSetup(Intake intake, Hold hold, Launcher launcher) {
    /** Unit label for a share, 0 to 1. */
    public static final String RATIO = "ratio";
    /** Unit label for a time. */
    public static final String SECONDS = "s";
    /** Unit label for a number of balls. */
    public static final String BALLS = "balls";
    /** Unit label for encoder counts per turn. */
    public static final String TICKS = "ticks";
    /** Unit label for a motor's turning speed. */
    public static final String RPM = "rpm";
    /** Unit label for angles. */
    public static final String DEGREES = "deg";

    /** The keys that describe the intake, after {@code intake.motor}. */
    public static final List<String> INTAKE_KEYS = List.of("intake.direction", "intake.minPower", "intake.forwardInches",
            "intake.leftInches", "intake.lengthInches", "intake.widthInches", "intake.spitSeconds",
            "intake.spitInchesPerSecond");
    /** The keys that describe what it holds. */
    public static final List<String> HOLD_KEYS = List.of("hold.capacity", "hold.accepts", "hold.preloaded");
    /** The keys that describe the launcher, after {@code launcher.motor}. */
    public static final List<String> LAUNCHER_KEYS = List.of("launcher.direction", "launcher.ticksPerRev",
            "launcher.freeSpeedRpm", "launcher.spinUpSeconds", "launcher.speedLossPerShot",
            "launcher.wheelDiameterInches", "launcher.exitFraction", "launcher.minBallInchesPerSecond",
            "feed.motor", "feed.direction", "feed.minPower", "feed.secondsPerBall",
            "launch.forwardInches", "launch.leftInches", "launch.upInches", "launch.pitchDegrees", "launch.yawDegrees");

    /**
     * The intake.
     *
     * @param motor          its motor's configuration name
     * @param direction      the direction setting that makes positive power run it inward
     * @param minPower       the least power (0 to 1) that still moves a ball
     * @param forwardInches  the middle of the zone a ball must touch, forward of the robot's center
     * @param leftInches     the middle of that zone, left of the robot's center
     * @param lengthInches   the zone, front to back
     * @param widthInches    the zone, side to side
     * @param spitSeconds    how long reverse power takes to spit one ball
     * @param spitSpeed      how fast a spat ball leaves, in/s
     */
    public record Intake(String motor, Direction direction, ModelValue minPower, ModelValue forwardInches,
                         ModelValue leftInches, ModelValue lengthInches, ModelValue widthInches,
                         ModelValue spitSeconds, ModelValue spitSpeed) {
        /** Refuses a power outside 0 to 1. */
        public Intake {
            checkShare("intake.minPower", minPower);
        }
    }

    /**
     * What the robot holds.
     *
     * @param capacity  the most balls it holds (the manual's G407 lets a robot control at most 4)
     * @param accepts   the kinds it can take in, e.g. {@link GamePiece#POLLEN}
     * @param preloaded how many it starts the match holding
     */
    public record Hold(ModelValue capacity, List<String> accepts, ModelValue preloaded) {
        /** Refuses a part of a ball, more pre-loads than it holds, no kinds, and an unknown kind. */
        public Hold {
            checkWhole("hold.capacity", capacity);
            checkWhole("hold.preloaded", preloaded);
            if (preloaded.value() > capacity.value()) {
                throw new IllegalArgumentException("hold.preloaded: " + preloaded.value() + " is more than hold.capacity, "
                        + capacity.value());
            }
            if (accepts.isEmpty()) {
                throw new IllegalArgumentException("hold.accepts: needs at least one kind, e.g. " + GamePiece.POLLEN);
            }
            accepts.forEach(GamePiece::radiusInches);   // fails clearly for an unknown kind
            accepts = List.copyOf(accepts);
        }
    }

    /**
     * The launcher, what feeds it, and where a ball leaves.
     *
     * @param motor               the launcher wheel motor's configuration name
     * @param direction           the direction setting that makes positive speed launch
     * @param ticksPerRev         its encoder counts per turn of the wheel's shaft
     * @param freeSpeedRpm        its fastest speed
     * @param spinUpSeconds       how long it takes to get 63% of the way to a new speed
     * @param speedLossPerShot    the share of its speed a shot takes (0 to 1)
     * @param wheelDiameterInches the wheel's diameter
     * @param exitFraction        ball speed as a share of the wheel's edge speed (0 to 1; about 0.5 for a ball
     *                            rolling along a fixed plate)
     * @param minBallSpeed        below this ball speed (in/s) the ball doesn't leave
     * @param feedMotor           the motor that pushes balls into the wheel; may be the intake's
     * @param feedDirection       the direction setting that makes positive power feed
     * @param feedMinPower        the least power (0 to 1) that still moves a ball
     * @param feedSecondsPerBall  how long the feed must run to push one ball into the wheel
     * @param launchForwardInches where a ball leaves, forward of the robot's center
     * @param launchLeftInches    where a ball leaves, left of the robot's center
     * @param launchUpInches      where a ball leaves, above the floor
     * @param pitchDegrees        the launch angle above level
     * @param yawDegrees          the launch direction, left of straight ahead
     */
    public record Launcher(String motor, Direction direction, ModelValue ticksPerRev, ModelValue freeSpeedRpm,
                           ModelValue spinUpSeconds, ModelValue speedLossPerShot, ModelValue wheelDiameterInches,
                           ModelValue exitFraction, ModelValue minBallSpeed, String feedMotor, Direction feedDirection,
                           ModelValue feedMinPower, ModelValue feedSecondsPerBall, ModelValue launchForwardInches,
                           ModelValue launchLeftInches, ModelValue launchUpInches, ModelValue pitchDegrees,
                           ModelValue yawDegrees) {
        /** Refuses shares outside 0 to 1. */
        public Launcher {
            checkShare("launcher.speedLossPerShot", speedLossPerShot);
            checkShare("launcher.exitFraction", exitFraction);
            checkShare("feed.minPower", feedMinPower);
        }
    }

    /** Every fact by its key, for reports and checks, in the file's order. */
    public Map<String, ModelValue> facts() {
        Map<String, ModelValue> facts = new LinkedHashMap<>();
        if (intake != null) {
            facts.put("intake.minPower", intake.minPower());
            facts.put("intake.forwardInches", intake.forwardInches());
            facts.put("intake.leftInches", intake.leftInches());
            facts.put("intake.lengthInches", intake.lengthInches());
            facts.put("intake.widthInches", intake.widthInches());
            facts.put("intake.spitSeconds", intake.spitSeconds());
            facts.put("intake.spitInchesPerSecond", intake.spitSpeed());
        }
        facts.put("hold.capacity", hold.capacity());
        facts.put("hold.preloaded", hold.preloaded());
        if (launcher != null) {
            facts.put("launcher.ticksPerRev", launcher.ticksPerRev());
            facts.put("launcher.freeSpeedRpm", launcher.freeSpeedRpm());
            facts.put("launcher.spinUpSeconds", launcher.spinUpSeconds());
            facts.put("launcher.speedLossPerShot", launcher.speedLossPerShot());
            facts.put("launcher.wheelDiameterInches", launcher.wheelDiameterInches());
            facts.put("launcher.exitFraction", launcher.exitFraction());
            facts.put("launcher.minBallInchesPerSecond", launcher.minBallSpeed());
            facts.put("feed.minPower", launcher.feedMinPower());
            facts.put("feed.secondsPerBall", launcher.feedSecondsPerBall());
            facts.put("launch.forwardInches", launcher.launchForwardInches());
            facts.put("launch.leftInches", launcher.launchLeftInches());
            facts.put("launch.upInches", launcher.launchUpInches());
            facts.put("launch.pitchDegrees", launcher.pitchDegrees());
            facts.put("launch.yawDegrees", launcher.yawDegrees());
        }
        return facts;
    }

    /** The keys of the facts that are still guesses, for reports and displays. */
    public List<String> placeholders() {
        List<String> names = new ArrayList<>();
        facts().forEach((key, value) -> {
            if (!value.measured()) {
                names.add(key);
            }
        });
        return names;
    }

    private static void checkShare(String key, ModelValue value) {
        if (!(value.value() >= 0 && value.value() <= 1)) {
            throw new IllegalArgumentException(key + ": must be between 0 and 1, got " + value.value());
        }
    }

    private static void checkWhole(String key, ModelValue value) {
        if (!(value.value() >= 0) || value.value() != Math.rint(value.value())) {
            throw new IllegalArgumentException(key + ": must be a whole number of balls, got " + value.value());
        }
    }
}
