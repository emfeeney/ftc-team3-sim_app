package pvi.harness.hardware;

import com.qualcomm.robotcore.hardware.DcMotor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A launcher wheel takes time to change speed and slows when it fires; other motors don't change. */
class FakeMotorSpinUpTest {
    private static final double STEP = 0.02;

    private static FakeDcMotorEx wheel(double spinUpSeconds) {
        FakeDcMotorEx motor = new FakeDcMotorEx("wheel", 0).setEncoderModel(28, 6000).setSpinUpSeconds(spinUpSeconds);
        motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        return motor;
    }

    private static void run(FakeDcMotorEx motor, double seconds) {
        for (double t = 0; t < seconds - 1e-9; t += STEP) {
            motor.step(STEP);
        }
    }

    /** WHAT: With no spin-up set, a motor reaches its speed in one step, exactly as before. */
    @Test
    void noSpinUpReachesSpeedAtOnce() {
        FakeDcMotorEx motor = new FakeDcMotorEx("plain", 0);
        motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        motor.setVelocity(1500);
        motor.step(STEP);
        assertEquals(1500.0, motor.getVelocity(), 1e-9);
    }

    /** WHAT: A wheel gets about 63% of the way in its spin-up time, and nearly there in five times that. */
    @Test
    void aWheelTakesItsSpinUpTime() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setVelocity(1500);

        run(motor, 0.3);
        assertEquals(1500 * (1 - Math.exp(-1)), motor.getVelocity(), 1.0);
        run(motor, 1.2);
        assertEquals(1500.0, motor.getVelocity(), 15.0);
    }

    /** WHAT: Its encoder counts what the wheel really turned, not what it was told. */
    @Test
    void theEncoderCountsTheRealTurning() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setVelocity(1500);
        run(motor, 0.3);

        assertTrue(motor.getCurrentPosition() < 0.5 * 1500 * 0.3, "well under full speed for 0.3 s: " + motor.getCurrentPosition());
        assertTrue(motor.getCurrentPosition() > 0, "it did turn");
    }

    /** WHAT: Power works the same way, and the wheel coasts down when told to stop. */
    @Test
    void powerAndStoppingTakeTimeToo() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setPower(0.5);
        run(motor, 2.0);
        assertEquals(1400.0, motor.getVelocity(), 5.0);

        motor.setPower(0);
        motor.step(STEP);
        assertTrue(motor.getVelocity() > 1000, "still turning: " + motor.getVelocity());
        run(motor, 3.0);
        assertEquals(0.0, motor.getVelocity(), 1.0);
    }

    /** WHAT: A wheel running backward keeps its sign while it changes speed. */
    @Test
    void itWorksBackwardToo() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setVelocity(-1500);
        run(motor, 0.3);
        assertEquals(-1500 * (1 - Math.exp(-1)), motor.getVelocity(), 1.0);
    }

    /** WHAT: A shot takes its share of the speed, and the wheel recovers. */
    @Test
    void aShotSlowsTheWheelAndItRecovers() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setVelocity(1500);
        run(motor, 3.0);

        motor.loseSpeed(0.2);
        assertEquals(1200.0, motor.getVelocity(), 2.0);
        run(motor, 0.3);
        assertEquals(1500 - 300 * Math.exp(-1), motor.getVelocity(), 3.0);
    }

    /** WHAT: Resetting the encoder stops the wheel at once, as on a real motor's reset. */
    @Test
    void resettingStopsAtOnce() {
        FakeDcMotorEx motor = wheel(0.3);
        motor.setVelocity(1500);
        run(motor, 1.0);

        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.step(STEP);
        assertEquals(0.0, motor.getVelocity(), 1e-9);
    }

    /** WHAT: Nonsense is refused: a negative spin-up, and a shot taking more than all the speed. */
    @Test
    void nonsenseIsRefused() {
        FakeDcMotorEx motor = wheel(0.3);
        assertThrows(IllegalArgumentException.class, () -> motor.setSpinUpSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> motor.loseSpeed(1.5));
    }
}
