package pvi.harness.viewer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pvi.harness.hardware.RobotConfigs;
import pvi.harness.physics.DriveModels;
import pvi.harness.physics.GamePiece;
import pvi.harness.physics.HiveTilt;
import pvi.harness.physics.Pose;
import pvi.harness.physics.PoseEstimator;
import pvi.harness.physics.WheelReader;
import pvi.harness.runtime.HarnessConfig;
import pvi.harness.runtime.OpModeRunner;
import pvi.harness.runtime.RunRecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The intake, launcher and HIVE in the desktop viewer: held balls drawn on the robot, flying balls
 * drawn bigger the higher they are, each CELL seen from above, and the CELL counts from a real run.
 * Nothing here opens a window.
 */
class HiveDrawingTest {
    /** 20 pixels per inch. */
    private final FieldCoordinates coords = new FieldCoordinates(2880, 30);

    /**
     * WHAT: Loose balls go under the robot, held ones over it, balls in a FLOWER or CELL over it as rings,
     * and flying balls on top of everything.
     */
    @Test
    void eachBallStateHasItsLayer() {
        assertEquals(FieldPainter.LAYER_TILES, FieldPainter.layer(ball(GamePiece.FIELD, 1.4)));
        assertEquals(FieldPainter.LAYER_HELD, FieldPainter.layer(ball(GamePiece.HELD, 8)));
        assertEquals(FieldPainter.LAYER_UP, FieldPainter.layer(ball(GamePiece.CELL, 50)));
        assertEquals(FieldPainter.LAYER_UP, FieldPainter.layer(ball(GamePiece.FLOWER, 10)));
        assertEquals(FieldPainter.LAYER_FLYING, FieldPainter.layer(ball(GamePiece.FLYING, 30)));
    }

    /**
     * WHAT: A flying ball is drawn its own size bigger for every 36 in up, at most 2.5 times its size;
     * balls in any other state are drawn their real size, however high (a CELL ball is 50 in up).
     */
    @Test
    void flyingBallsAreDrawnBiggerTheHigherTheyAre() {
        double r = ball(GamePiece.FIELD, 1.4).radiusInches();
        assertEquals(r, FieldPainter.drawnRadiusInches(ball(GamePiece.FLYING, 0)), 1e-9);
        assertEquals(2 * r, FieldPainter.drawnRadiusInches(ball(GamePiece.FLYING, 36)), 1e-9);
        assertEquals(2.5 * r, FieldPainter.drawnRadiusInches(ball(GamePiece.FLYING, 200)), 1e-9);
        assertEquals(r, FieldPainter.drawnRadiusInches(ball(GamePiece.CELL, 50)), 1e-9);
    }

    /** WHAT: A held ball in the middle of the robot shows, in its color, on top of the robot. */
    @Test
    void aHeldBallIsDrawnOnTheRobot() {
        BufferedImage image = paint(List.of(new GamePiece("p", GamePiece.POLLEN, GamePiece.HELD, 0, 0, 8)), List.of());

        assertEquals(FieldPainter.POLLEN, colorAt(image, 0, 0));
    }

    /**
     * WHAT: A ball flying 36 in up is drawn twice its size: 2 in from its center is still its color, where
     * the same ball on the tiles leaves the field showing.
     */
    @Test
    void aFlyingBallIsDrawnBigger() {
        BufferedImage flying = paint(List.of(new GamePiece("p", GamePiece.POLLEN, GamePiece.FLYING, 30, 30, 36)), List.of());
        BufferedImage onTiles = paint(List.of(new GamePiece("p", GamePiece.POLLEN, GamePiece.FIELD, 30, 30, 1.4)), List.of());

        assertEquals(FieldPainter.POLLEN, colorAt(flying, 32, 30));
        assertNotEquals(FieldPainter.POLLEN, colorAt(onTiles, 32, 30));
    }

    /**
     * WHAT: The upward CELL is tinted in its alliance's color; the lowered CELL is only outlined, so the
     * field shows through its middle.
     */
    @Test
    void theUpwardCellIsTinted() {
        List<HiveView.Cell> cells = HiveView.cells(List.of(new HiveTilt("red-hive", 0, 0)), List.of());
        BufferedImage with = paint(List.of(), cells);
        BufferedImage without = paint(List.of(), List.of());

        Color up = colorAt(with, 10.6, -12.75);
        assertNotEquals(colorAt(without, 10.6, -12.75), up, "the red upward CELL (+x) is tinted");
        assertTrue(up.getRed() > up.getBlue() + 20, "in red: " + up);
        assertEquals(colorAt(without, -16, -12.75), colorAt(with, -16, -12.75), "the lowered CELL (-x) isn't filled");
    }

    // ---- A real run ----------------------------------------------------------------------------

    @TempDir
    Path robotsFolder;

    /** The wheel speed that throws at 250 in/s, which goes into the red CELL from (58, -12.75) facing -x. */
    private static final double WHEEL_TICKS_PER_SECOND = 250 / (Math.PI * 4 * 0.5) * 28;

    /** Spins the wheel and feeds for 0.32 s every 0.8 s, from 2 s on, four times. */
    private static final class ShootFour extends OpMode {
        private int loops;

        @Override
        public void init() {
            hardwareMap.get(DcMotorEx.class, "testWheel").setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }

        @Override
        public void loop() {
            loops++;
            hardwareMap.get(DcMotorEx.class, "testWheel").setVelocity(WHEEL_TICKS_PER_SECOND);
            int sinceFirst = loops - 100;
            boolean feeding = sinceFirst >= 0 && sinceFirst % 40 < 16 && sinceFirst < 160;
            hardwareMap.get(DcMotor.class, "testFeed").setPower(feeding ? 1.0 : 0.0);
        }
    }

    /**
     * WHAT: Replaying a run where a test robot (no team's code) shoots its 4 POLLEN into the red upward
     * CELL: at the start the CELL shows its 3 staged NECTAR; with 3 POLLEN in it shows 3 and 3; at the end
     * the HIVE has tipped once, its -x CELL is up and the viewer's counts match the simulation's.
     */
    @Test
    void theViewCountsTheCellsOfARealRun() throws IOException {
        String id = "hive-drawing-test";
        Files.createDirectories(robotsFolder.resolve(id));
        Files.writeString(robotsFolder.resolve(id).resolve(HarnessConfig.MECHANISM_FILE_NAME), String.join("\n",
                "intake.motor=", "hold.capacity=4 | placeholder | test", "hold.accepts=pollen",
                "hold.preloaded=4 | placeholder | test", "launcher.motor=testWheel", "launcher.direction=FORWARD",
                "launcher.ticksPerRev=28 | placeholder | test", "launcher.freeSpeedRpm=6000 | placeholder | test",
                "launcher.spinUpSeconds=0.3 | placeholder | test", "launcher.speedLossPerShot=0 | placeholder | test",
                "launcher.wheelDiameterInches=4 | placeholder | test", "launcher.exitFraction=0.5 | placeholder | test",
                "launcher.minBallInchesPerSecond=20 | placeholder | test", "feed.motor=testFeed", "feed.direction=FORWARD",
                "feed.minPower=0.1 | placeholder | test", "feed.secondsPerBall=0.3 | placeholder | test",
                "launch.forwardInches=6 | placeholder | test", "launch.leftInches=0 | placeholder | test",
                "launch.upInches=14 | placeholder | test", "launch.pitchDegrees=60 | placeholder | test",
                "launch.yawDegrees=0 | placeholder | test", ""));
        HarnessConfig config = HarnessConfig.fromText(Files.readString(Path.of(HarnessConfig.FILE_NAME)).replace("\r\n", "\n")
                .replaceAll("(?m)^robot\\.id=.*$", "robot.id=" + id)
                .replaceAll("(?m)^hardware\\.otherMotors=.*$", "hardware.otherMotors=testWheel, testFeed"), "test", robotsFolder);
        OpModeRunner runner = new OpModeRunner(new ShootFour(), RobotConfigs.robot(config));
        runner.usePositionEstimator(new PoseEstimator(DriveModels.fromConfig(config), WheelReader.fromConfig(config),
                Pose.inches(58, -12.75, 180)));
        RunRecorder recorder = RunRecorder.attachTo(runner);
        runner.run(0.1, 8.0).assertNoException();
        RecordingView view = new RecordingView(recorder.recording());

        HiveView.Cell first = redCell(view, 0, 1);
        assertTrue(first.up());
        assertEquals(3, first.nectar());
        assertEquals(0, first.pollen());
        boolean sawThree = false;
        for (int i = 0; i < view.size(); i++) {
            HiveView.Cell c = redCell(view, i, 1);
            sawThree |= c.up() && c.nectar() == 3 && c.pollen() == 3;
        }
        assertTrue(sawThree, "the view showed 3 NECTAR and 3 POLLEN before the 4th went in");

        int last = view.size() - 1;
        assertTrue(redCell(view, last, -1).up(), "after the tip the -x CELL is up");
        for (int side : new int[] {1, -1}) {
            HiveView.Cell c = redCell(view, last, side);
            assertEquals(runner.positionEstimator().pieces().ballsInCell("red-hive", side).size(), c.nectar() + c.pollen(),
                    "the viewer counts what the simulation counts, side " + side);
        }
        List<String> panel = SidePanelText.describe(view, last);
        assertTrue(panel.contains("HIVE CELLs"), panel.toString());
        assertTrue(panel.contains("  red-hive  tipped 1 time"), panel.toString());
    }

    private static HiveView.Cell redCell(RecordingView view, int index, int side) {
        return view.cells(index).stream().filter(c -> c.hiveId().equals("red-hive") && c.side() == side)
                .findFirst().orElseThrow();
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static GamePiece ball(String state, double zInches) {
        return new GamePiece("b", GamePiece.POLLEN, state, 30, 30, zInches);
    }

    private BufferedImage paint(List<GamePiece> pieces, List<HiveView.Cell> cells) {
        BufferedImage image = new BufferedImage(coords.totalPixels(), coords.totalPixels(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        FieldPainter.paint(g, coords, FieldPainter.RobotBox.UNKNOWN, Pose.ORIGIN, List.of(), null, null, null, pieces, cells);
        g.dispose();
        return image;
    }

    private Color colorAt(BufferedImage image, double xInches, double yInches) {
        return new Color(image.getRGB((int) Math.floor(coords.screenX(xInches)), (int) Math.floor(coords.screenY(yInches))));
    }
}
