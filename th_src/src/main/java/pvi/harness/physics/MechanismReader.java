package pvi.harness.physics;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import pvi.harness.hardware.FakeDcMotorEx;
import pvi.harness.hardware.FakeHardwareMap;
import pvi.harness.hardware.MechanismSetup;

/**
 * Reads what a robot's intake, feed and launcher motors are doing, the way {@link WheelReader} reads the drive motors. The
 * harness only watches the motor the robot code already runs, so the robot code doesn't change.
 */
public final class MechanismReader {
    private final String intakeMotor;
    private final Direction intakeDirection;
    /** The launcher's setup, or null for a robot with no launcher. */
    private final MechanismSetup.Launcher launcher;

    /** A reader for this robot's mechanism, which may have no intake. */
    public MechanismReader(MechanismSetup setup) {
        this.intakeMotor = setup.intake() == null ? null : setup.intake().motor();
        this.intakeDirection = setup.intake() == null ? null : setup.intake().direction();
        this.launcher = setup.launcher();
    }

    /** The feed motor's power with inward (toward the wheel) positive, -1 to 1: 0 with no launcher. */
    public double inwardFeedPower(FakeHardwareMap hardwareMap) {
        return launcher == null ? 0.0 : inwardPower(hardwareMap, launcher.feedMotor(), launcher.feedDirection());
    }

    /**
     * How fast a ball would leave the launcher wheel now, in/s: the wheel's real speed (its encoder) as
     * edge speed, times {@code launcher.exitFraction}. Negative if the wheel turns backward; 0 with no
     * launcher, or with the motor missing.
     */
    public double launcherBallSpeed(FakeHardwareMap hardwareMap) {
        FakeDcMotorEx motor = launcher == null ? null : hardwareMap.motor(launcher.motor());
        if (motor == null) {
            return 0.0;
        }
        // The encoder reports in the code's direction; this turns it into the launching direction.
        double ticksPerSecond = motor.getVelocity() * sign(motor.getDirection()) * sign(launcher.direction());
        double turnsPerSecond = ticksPerSecond / launcher.ticksPerRev().value();
        return turnsPerSecond * Math.PI * launcher.wheelDiameterInches().value() * launcher.exitFraction().value();
    }

    /** Takes {@code launcher.speedLossPerShot} off the launcher wheel's speed for each of {@code shots}. */
    public void slowLauncher(FakeHardwareMap hardwareMap, int shots) {
        FakeDcMotorEx motor = launcher == null ? null : hardwareMap.motor(launcher.motor());
        if (motor != null && shots > 0) {
            motor.loseSpeed(1 - Math.pow(1 - launcher.speedLossPerShot().value(), shots));
        }
    }

    /**
     * The intake motor's power with inward positive, -1 to 1: 0 if the robot has no intake, or the motor
     * is missing or disabled.
     */
    public double inwardIntakePower(FakeHardwareMap hardwareMap) {
        return intakeMotor == null ? 0.0 : inwardPower(hardwareMap, intakeMotor, intakeDirection);
    }

    /** A motor's power with the file's direction positive: 0 if it's missing or disabled. */
    private static double inwardPower(FakeHardwareMap hardwareMap, String name, Direction useful) {
        FakeDcMotorEx motor = hardwareMap.motor(name);
        if (motor == null || !motor.isMotorEnabled()) {
            return 0.0;
        }
        // Matching direction signs cancel (+1); mismatched ones flip it (-1).
        double power = motor.getPower() * sign(motor.getDirection()) * sign(useful);
        // Swapped wires reverse the motor physically, whatever the code set.
        return motor.isMiswired() ? -power : power;
    }

    private static double sign(Direction direction) {
        return direction == Direction.REVERSE ? -1.0 : 1.0;
    }
}
