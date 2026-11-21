package pvi.harness.runtime;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.qualcomm.robotcore.hardware.DcMotorSimple.Direction;

import pvi.harness.hardware.CameraSetup;
import pvi.harness.hardware.MechanismSetup;
import pvi.harness.physics.Dynamics;
import pvi.harness.physics.ModelValue;
import pvi.harness.physics.Pose;

/**
 * The harness settings from {@code harness.properties}: the one file a team changes to use the
 * harness with their own robot (RP1).
 *
 * <p>It says where the robot code is, what the drive motors and Pinpoint are called, and the
 * robot facts the simulation needs (size, turn rate, ...). Each fact is written as
 * {@code number | measured or placeholder | where it came from}, so the harness can never show a
 * guess as a measurement. Every key is listed in README.md.</p>
 *
 * <p>Reading checks everything at once and fails early: a missing key, a key the harness doesn't
 * know (usually a typo), or a value that isn't a number gives one plain message listing every
 * problem. The runner, physics and fake robot read their robot facts from here (RP2), so none of
 * them needs the team's Pedro configuration.</p>
 */
public final class HarnessConfig {
    /** The file name, in the Harness folder. */
    public static final String FILE_NAME = "harness.properties";
    /** The layout of the file this harness reads. Goes up whenever keys change (README.md). */
    public static final int VERSION = 6;

    /** The file in a robot's folder that holds its cameras: the only place they are set. */
    public static final String CAMERA_FILE_NAME = "camera.properties";
    /** The file in a robot's folder that holds its intake and launcher. No file means it has neither. */
    public static final String MECHANISM_FILE_NAME = "mechanism.properties";

    /**
     * One dead-wheel odometry pod from {@code devices.deadWheels}.
     *
     * @param port         the motor port its encoder is plugged into
     * @param parallel     true for a pod along the robot (measures forward), false for one across it
     * @param offsetInches parallel: how far left of the center it is; perpendicular: how far forward
     */
    public record DeadWheel(String port, boolean parallel, double offsetInches) {
    }

    /** The four mecanum drive motors' names in the robot configuration. */
    public record DriveMotorNames(String frontLeft, String frontRight, String rearLeft, String rearRight) {
    }

    /**
     * For each drive motor, the direction setting that makes that wheel drive the robot forward on
     * positive power (see {@code WheelReader}).
     */
    public record DriveMotorDirections(Direction frontLeft, Direction frontRight, Direction rearLeft,
                                       Direction rearRight) {
    }

    /** Every key the file may contain, so a misspelled key is caught instead of ignored. */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "config.version", "teamCode.sourceDir", "teamCode.opModePackages", "host.dependenciesFile",
            "host.skipLibraries",
            "robot.name", "robot.id", "drive.frontLeft", "drive.frontRight", "drive.rearLeft", "drive.rearRight", "pinpoint",
            "hardware.otherMotors", "hardware.servos", "devices.imu", "devices.crServos", "devices.distanceSensors",
            "devices.touchSensors", "devices.colorSensors", "devices.analogInputs", "devices.otos",
            "devices.limelights", "devices.blinkers", "devices.deadWheels",
            "drive.frontLeft.direction", "drive.frontRight.direction", "drive.rearLeft.direction",
            "drive.rearRight.direction",
            "drive.forwardSpeedInchesPerSecond", "drive.strafeSpeedInchesPerSecond",
            "drive.forwardCoastInchesPerSecondSquared", "drive.lateralCoastInchesPerSecondSquared",
            "robot.lengthInches", "robot.widthInches", "drive.turnRateDegreesPerSecond",
            "drive.responseTimeSeconds", "drive.maxAccelerationInchesPerSecondSquared", "drive.speedVoltage",
            "pvi.extras", "field", "field.robotObstacles", "viewer.open", "viewer.defaultTeleOp",
            "smokeTest.skipOpModes", "smokeTest.notDrivingTeleOps");

    /** The start of a key that says where an OpMode starts: {@code start.<OpMode name>=x, y, headingDegrees}. */
    static final String START_PREFIX = "start.";

    /** The start of a key that says an OpMode's alliance, for scoring: {@code alliance.<OpMode name>=red|blue}. */
    static final String ALLIANCE_PREFIX = "alliance.";

    /** The keys as read, so a robot profile can start from them. */
    private final Properties properties;
    /** Where OpModes start, by the name after {@link #START_PREFIX}. */
    private final Map<String, Pose> starts = new TreeMap<>();
    /** OpModes' alliances, by the name after {@link #ALLIANCE_PREFIX}. */
    private final Map<String, pvi.harness.scoring.Alliance> alliances = new TreeMap<>();
    private final String teamCodeSourceDir;
    private final List<String> opModePackages;
    private final String hostDependenciesFile;
    private final List<String> skipLibraries;
    private final String robotName;
    private final String robotId;
    private final DriveMotorNames driveMotors;
    private final DriveMotorDirections driveDirections;
    private final String pinpointName;
    private final List<String> otherMotors;
    private final List<String> servos;
    private final List<String> imus;
    private final List<String> crServos;
    private final List<String> distanceSensors;
    private final List<String> touchSensors;
    private final List<String> colorSensors;
    private final List<String> analogInputs;
    private final List<String> otos;
    private final List<String> limelights;
    private final List<CameraSetup> cameras;
    private final Map<String, String> limelightCameras = new TreeMap<>();
    private final MechanismSetup mechanism;
    private final List<String> blinkers;
    private final List<DeadWheel> deadWheels;
    private final ModelValue forwardSpeed;
    private final ModelValue strafeSpeed;
    private final ModelValue forwardCoast;
    private final ModelValue lateralCoast;
    private final boolean pviExtras;
    private final ModelValue lengthInches;
    private final ModelValue widthInches;
    private final ModelValue turnRate;
    private final ModelValue responseTime;
    private final ModelValue maxAcceleration;
    private final ModelValue speedVoltage;
    private final String field;
    private final boolean viewerOpen;
    private final boolean robotObstacles;
    private final List<String> smokeTestSkipOpModes;
    private final List<String> smokeTestNotDrivingTeleOps;
    private final String viewerDefaultTeleOp;

    /** Reads {@code harness.properties} from the current folder (the Harness folder in Gradle runs). */
    public static HarnessConfig load() {
        return load(Path.of(FILE_NAME));
    }

    /** Reads a settings file. Fails with a plain message if it is missing or has problems. */
    public static HarnessConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("No harness settings file at " + file.toAbsolutePath()
                    + ". Copy harness.properties into the TestHarness/th_src folder (see docs/README.md).");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            HarnessConfig config = read(reader, file.toString(), RobotProfile.FOLDER);
            // A misspelled robot.id would otherwise just mean "no cameras", with no message.
            if (!Files.isDirectory(RobotProfile.FOLDER.resolve(config.robotId()))) {
                throw new IllegalArgumentException("Problems in " + file + ":\n  robot.id: there's no folder "
                        + RobotProfile.FOLDER.resolve(config.robotId()) + ". Make it, or correct the name.");
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.toAbsolutePath(), e);
        }
    }

    /** Reads settings written as text, for tests. {@code name} is used in error messages. */
    public static HarnessConfig fromText(String text, String name) {
        return fromText(text, name, RobotProfile.FOLDER);
    }

    /** The same, with the robots' folders (and so their cameras) in {@code robotsFolder}. */
    public static HarnessConfig fromText(String text, String name, Path robotsFolder) {
        try {
            return read(new StringReader(text), name, robotsFolder);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);   // a StringReader never fails
        }
    }

    private static HarnessConfig read(Reader reader, String name, Path robotsFolder) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        return check(properties, name, robotsFolder, new ArrayList<>());
    }

    /** Makes the settings from {@code properties}, failing with every problem at once. */
    private static HarnessConfig check(Properties properties, String name, Path robotsFolder, List<String> problems) {
        HarnessConfig config = new HarnessConfig(properties, robotsFolder, problems);
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (!KNOWN_KEYS.contains(key) && !isOldCameraKey(key) && !key.startsWith(START_PREFIX)
                    && !key.startsWith(ALLIANCE_PREFIX)) {
                problems.add(key + ": not a harness setting (check the spelling against docs/README.md)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Problems in " + name + ":\n  " + String.join("\n  ", problems));
        }
        return config;
    }

    /**
     * These settings with a robot profile's keys applied ({@link RobotProfile}). A profile may only
     * change the robot: {@code robot.*}, {@code drive.*}, {@code pinpoint}, {@code hardware.*},
     * {@code devices.*} and {@code webcam.*}. The rest (where the code is, the field, the viewer)
     * is one setting for the whole harness. The profile's cameras are its own
     * ({@code <robotsFolder>/<id>/camera.properties}), never this robot's.
     *
     * @param profile      the profile's keys
     * @param name         used in error messages
     * @param id           the profile's folder name
     * @param robotsFolder where the robots' folders are
     */
    HarnessConfig forProfile(Properties profile, String name, String id, Path robotsFolder) {
        Properties merged = new Properties();
        merged.putAll(properties);
        merged.putAll(profile);
        merged.setProperty("robot.id", id);
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(profile.stringPropertyNames())) {
            if (KNOWN_KEYS.contains(key) && !isRobotKey(key)) {
                problems.add(key + ": a robot profile can't change this; it belongs in " + FILE_NAME);
            }
            if (key.equals("robot.id")) {
                problems.add(key + ": a robot profile's id is its folder's name, here " + id);
            }
        }
        return check(merged, name, robotsFolder, problems);
    }

    /** True for a key that describes the robot, which a profile may change. */
    private static boolean isRobotKey(String key) {
        return key.equals("pinpoint") || key.startsWith("robot.") || key.startsWith("drive.")
                || key.startsWith("hardware.") || key.startsWith("devices.");
    }

    /** True for a camera key in a settings file: cameras moved to each robot's own file. */
    private static boolean isOldCameraKey(String key) {
        return key.equals("devices.webcams") || key.startsWith("webcam.");
    }

    /** Reads every value, adding a line to {@code problems} for each one that is wrong. */
    private HarnessConfig(Properties properties, Path robotsFolder, List<String> problems) {
        this.properties = properties;
        Values values = new Values(properties, problems);
        String version = values.text("config.version");
        if (version != null && !version.equals(Integer.toString(VERSION))) {
            problems.add("config.version: this harness reads version " + VERSION + ", the file says " + version);
        }
        teamCodeSourceDir = values.text("teamCode.sourceDir");
        opModePackages = values.list("teamCode.opModePackages");
        if (opModePackages.isEmpty() && properties.getProperty("teamCode.opModePackages") != null) {
            problems.add("teamCode.opModePackages: needs at least one package, e.g. org.firstinspires.ftc.teamcode");
        }
        hostDependenciesFile = values.text("host.dependenciesFile");
        skipLibraries = values.list("host.skipLibraries");
        robotName = values.text("robot.name");
        robotId = values.text("robot.id");
        if (robotId != null && !robotId.matches("[A-Za-z0-9._-]+")) {
            problems.add("robot.id: must be a folder's name in robots/, got \"" + robotId + "\"");
        }
        if (properties.stringPropertyNames().stream().anyMatch(HarnessConfig::isOldCameraKey)) {
            problems.add("devices.webcams, webcam.*: cameras don't go in this file any more. Move these lines to "
                    + robotsFolder.resolve(String.valueOf(robotId)).resolve(CAMERA_FILE_NAME));
        }
        driveMotors = new DriveMotorNames(values.text("drive.frontLeft"), values.text("drive.frontRight"),
                values.text("drive.rearLeft"), values.text("drive.rearRight"));
        driveDirections = new DriveMotorDirections(values.direction("drive.frontLeft.direction"),
                values.direction("drive.frontRight.direction"), values.direction("drive.rearLeft.direction"),
                values.direction("drive.rearRight.direction"));
        pinpointName = values.text("pinpoint");
        otherMotors = values.list("hardware.otherMotors");
        servos = values.list("hardware.servos");
        imus = values.list("devices.imu");
        crServos = values.list("devices.crServos");
        distanceSensors = values.list("devices.distanceSensors");
        touchSensors = values.list("devices.touchSensors");
        colorSensors = values.list("devices.colorSensors");
        analogInputs = values.list("devices.analogInputs");
        otos = values.list("devices.otos");
        limelights = values.list("devices.limelights");
        cameras = readCameras(robotId, robotsFolder, limelights, limelightCameras, problems);
        List<String> robotMotors = new ArrayList<>(otherMotors);
        robotMotors.addAll(Arrays.asList(driveMotors.frontLeft(), driveMotors.frontRight(), driveMotors.rearLeft(),
                driveMotors.rearRight()));
        mechanism = readMechanism(robotId, robotsFolder, robotMotors, problems);
        blinkers = values.list("devices.blinkers");
        deadWheels = values.deadWheels("devices.deadWheels");
        forwardSpeed = values.fact("drive.forwardSpeedInchesPerSecond", ModelValue.INCHES_PER_SECOND);
        strafeSpeed = values.fact("drive.strafeSpeedInchesPerSecond", ModelValue.INCHES_PER_SECOND);
        forwardCoast = values.fact("drive.forwardCoastInchesPerSecondSquared", Dynamics.INCHES_PER_SECOND_SQUARED);
        lateralCoast = values.fact("drive.lateralCoastInchesPerSecondSquared", Dynamics.INCHES_PER_SECOND_SQUARED);
        pviExtras = values.yesNo("pvi.extras");
        lengthInches = values.fact("robot.lengthInches", ModelValue.INCHES);
        widthInches = values.fact("robot.widthInches", ModelValue.INCHES);
        turnRate = values.fact("drive.turnRateDegreesPerSecond", ModelValue.DEGREES_PER_SECOND);
        responseTime = values.fact("drive.responseTimeSeconds", Dynamics.SECONDS);
        maxAcceleration = values.fact("drive.maxAccelerationInchesPerSecondSquared",
                Dynamics.INCHES_PER_SECOND_SQUARED);
        speedVoltage = values.fact("drive.speedVoltage", ModelValue.VOLTS);
        field = values.text("field");
        viewerOpen = values.yesNo("viewer.open");
        robotObstacles = values.yesNo("field.robotObstacles");
        smokeTestSkipOpModes = values.list("smokeTest.skipOpModes");
        smokeTestNotDrivingTeleOps = values.list("smokeTest.notDrivingTeleOps");
        viewerDefaultTeleOp = values.mayBeEmpty("viewer.defaultTeleOp");
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (key.startsWith(START_PREFIX)) {
                Pose start = values.start(key);
                if (start != null) {
                    starts.put(key.substring(START_PREFIX.length()), start);
                }
            }
            if (key.startsWith(ALLIANCE_PREFIX)) {
                pvi.harness.scoring.Alliance alliance = pvi.harness.scoring.Alliance.parse(properties.getProperty(key));
                if (alliance == null) {
                    problems.add(key + ": expected red or blue, got \"" + properties.getProperty(key).trim() + "\"");
                } else {
                    alliances.put(key.substring(ALLIANCE_PREFIX.length()), alliance);
                }
            }
        }
    }

    /**
     * Where an OpMode starts on the field, from a {@code start.<name>=x, y, headingDegrees} key (inches, the FTC
     * field frame, heading counter-clockwise from +x), by its Driver Station name or its class name; null if
     * neither has one (the harness then starts it at the field's center, facing +x).
     */
    public Pose startFor(String driverStationName, String simpleName) {
        Pose byName = driverStationName == null ? null : starts.get(driverStationName);
        return byName != null ? byName : simpleName == null ? null : starts.get(simpleName);
    }

    /**
     * An OpMode's alliance, for scoring: the {@code alliance.<name>=red|blue} key for its Driver Station name or its
     * class name, else "Red" or "Blue" in either name ({@link pvi.harness.scoring.Alliance#fromName}), else null
     * (the robot then earns no LEAVE or PARK points).
     */
    public pvi.harness.scoring.Alliance allianceFor(String driverStationName, String simpleName) {
        pvi.harness.scoring.Alliance byKey = driverStationName == null ? null : alliances.get(driverStationName);
        if (byKey == null && simpleName != null) {
            byKey = alliances.get(simpleName);
        }
        if (byKey != null) {
            return byKey;
        }
        pvi.harness.scoring.Alliance byName = pvi.harness.scoring.Alliance.fromName(driverStationName);
        return byName != null ? byName : pvi.harness.scoring.Alliance.fromName(simpleName);
    }

    /** Where the robot code is, relative to the Harness folder. */
    public String teamCodeSourceDir() {
        return teamCodeSourceDir;
    }

    /** Java packages searched for {@code @TeleOp} / {@code @Autonomous} OpModes (at least one). */
    public List<String> opModePackages() {
        return opModePackages;
    }

    /** The robot build's library list, relative to the Harness folder. */
    public String hostDependenciesFile() {
        return hostDependenciesFile;
    }

    /** {@code group:name} of the host libraries the harness leaves out (Android only). */
    public List<String> skipLibraries() {
        return skipLibraries;
    }

    /** This robot's folder in {@code robots/}, e.g. "team-a". */
    public String robotId() {
        return robotId;
    }

    /** The robot's name in reports, recordings and the viewer, e.g. "Team A". */
    public String robotName() {
        return robotName;
    }

    /** The drive motors' names in the robot configuration. */
    public DriveMotorNames driveMotors() {
        return driveMotors;
    }

    /** The direction setting that makes each drive wheel push the robot forward. */
    public DriveMotorDirections driveDirections() {
        return driveDirections;
    }

    /** The Pinpoint's name in the robot configuration. */
    public String pinpointName() {
        return pinpointName;
    }

    /** Names of the fake robot's other motors (not the drive), e.g. "intake". May be empty. */
    public List<String> otherMotors() {
        return otherMotors;
    }

    /** Names of the fake robot's servos ({@code hardware.servos}). May be empty. */
    public List<String> servos() {
        return servos;
    }

    /** Names of the fake robot's IMUs ({@code devices.imu}). May be empty. */
    public List<String> imus() {
        return imus;
    }

    /** Names of its continuous-rotation servos ({@code devices.crServos}). May be empty. */
    public List<String> crServos() {
        return crServos;
    }

    /** Names of its distance sensors ({@code devices.distanceSensors}). May be empty. */
    public List<String> distanceSensors() {
        return distanceSensors;
    }

    /** Names of its touch sensors ({@code devices.touchSensors}). May be empty. */
    public List<String> touchSensors() {
        return touchSensors;
    }

    /** Names of its color sensors ({@code devices.colorSensors}). May be empty. */
    public List<String> colorSensors() {
        return colorSensors;
    }

    /** Names of its analog inputs ({@code devices.analogInputs}). May be empty. */
    public List<String> analogInputs() {
        return analogInputs;
    }

    /** Names of its SparkFun OTOS sensors ({@code devices.otos}). May be empty. */
    public List<String> otos() {
        return otos;
    }

    /** Names of its Limelight cameras ({@code devices.limelights}). May be empty. */
    public List<String> limelights() {
        return limelights;
    }

    /** Names of its USB webcams ({@code devices.webcams} in its camera file). May be empty. */
    public List<String> webcams() {
        return cameras.stream().map(CameraSetup::name).toList();
    }

    /**
     * Each webcam's mount and view ({@code webcam.<name>.<fact>}), in the order of
     * {@code devices.webcams}, from this robot's own {@code robots/<id>/camera.properties}. Empty
     * if it has no such file.
     */
    public List<CameraSetup> cameras() {
        return cameras;
    }

    /**
     * D47: which webcam each Limelight sees through ({@code limelight.<name>.camera=<webcam>} in the
     * camera file), Limelight name to webcam name. A linked Limelight sees the AprilTags in that
     * webcam's pictures with no test wiring. Limelights not listed see only what a script or
     * {@code setView} gives them.
     */
    public Map<String, String> limelightCameras() {
        return java.util.Collections.unmodifiableMap(limelightCameras);   // sorted by name
    }

    /** Names of its hub status lights ({@code devices.blinkers}). May be empty. */
    public List<String> blinkers() {
        return blinkers;
    }

    /**
     * The webcams in {@code <robotsFolder>/<robotId>/camera.properties}: none if there's no such
     * file. Each thing wrong in the file is added to {@code problems}, naming the file.
     */
    private static List<CameraSetup> readCameras(String robotId, Path robotsFolder, List<String> limelights,
                                                 Map<String, String> limelightCameras, List<String> problems) {
        if (robotId == null) {
            return List.of();
        }
        Path file = robotsFolder.resolve(robotId).resolve(CAMERA_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        Properties keys = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            keys.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.toAbsolutePath(), e);
        }
        List<String> fileProblems = new ArrayList<>();
        Values values = new Values(keys, fileProblems);
        List<String> webcams = values.list("devices.webcams");
        List<CameraSetup> setups = values.cameras(webcams);
        for (String key : new TreeSet<>(keys.stringPropertyNames())) {
            if (key.startsWith(LIMELIGHT_PREFIX) && key.endsWith(LIMELIGHT_CAMERA_SUFFIX)) {
                // D47: limelight.<name>.camera=<webcam>, both names must exist.
                String limelight = key.substring(LIMELIGHT_PREFIX.length(), key.length() - LIMELIGHT_CAMERA_SUFFIX.length());
                String webcam = keys.getProperty(key).trim();
                if (!limelights.contains(limelight)) {
                    fileProblems.add(key + ": no Limelight \"" + limelight + "\" in devices.limelights " + limelights);
                } else if (!webcams.contains(webcam)) {
                    fileProblems.add(key + ": no webcam \"" + webcam + "\" in devices.webcams " + webcams);
                } else {
                    limelightCameras.put(limelight, webcam);
                }
            } else if (!key.equals("devices.webcams") && !isWebcamKey(key, webcams)) {
                fileProblems.add(key + ": not a camera setting (check the spelling, and that the webcam is in"
                        + " devices.webcams)");
            }
        }
        fileProblems.forEach(problem -> problems.add(file + ": " + problem));
        return setups;
    }

    /** This robot's intake and launcher ({@code robots/<id>/mechanism.properties}), or null if it has no such file. */
    public MechanismSetup mechanism() {
        return mechanism;
    }

    /**
     * The mechanism in {@code <robotsFolder>/<robotId>/mechanism.properties}: null if there's no such
     * file. Each thing wrong in the file is added to {@code problems}, naming the file.
     */
    private static MechanismSetup readMechanism(String robotId, Path robotsFolder, List<String> robotMotors,
                                                List<String> problems) {
        if (robotId == null) {
            return null;
        }
        Path file = robotsFolder.resolve(robotId).resolve(MECHANISM_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties keys = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            keys.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.toAbsolutePath(), e);
        }
        List<String> fileProblems = new ArrayList<>();
        Values values = new Values(keys, fileProblems);
        String intakeMotor = values.mayBeEmpty("intake.motor");
        String launcherMotor = values.mayBeEmpty("launcher.motor");
        Set<String> known = new TreeSet<>(MechanismSetup.HOLD_KEYS);
        known.add("intake.motor");
        known.add("launcher.motor");
        if (!intakeMotor.isEmpty()) {
            known.addAll(MechanismSetup.INTAKE_KEYS);
        }
        if (!launcherMotor.isEmpty()) {
            known.addAll(MechanismSetup.LAUNCHER_KEYS);
        }
        for (String key : new TreeSet<>(keys.stringPropertyNames())) {
            if (!known.contains(key)) {
                fileProblems.add(key + ": not a mechanism setting (check the spelling, and that its motor is named)");
            }
        }
        MechanismSetup setup = null;
        try {
            setup = values.mechanism(intakeMotor, launcherMotor);
        } catch (IllegalArgumentException e) {
            fileProblems.add(e.getMessage());
        }
        if (setup != null) {
            values.checkMotor("intake.motor", robotMotors);
            values.checkMotor("launcher.motor", robotMotors);
            values.checkMotor("feed.motor", robotMotors);
        }
        fileProblems.forEach(problem -> problems.add(file + ": " + problem));
        return fileProblems.isEmpty() ? setup : null;
    }

    /** D47: {@code limelight.<name>.camera}, in the camera file. */
    private static final String LIMELIGHT_PREFIX = "limelight.";
    private static final String LIMELIGHT_CAMERA_SUFFIX = ".camera";

    /** True for a {@code webcam.<name>.<fact>} key of a webcam in {@code webcams}. */
    private static boolean isWebcamKey(String key, List<String> webcams) {
        for (String name : webcams) {
            for (String fact : CameraSetup.FACT_NAMES) {
                if (key.equals("webcam." + name + "." + fact)) {
                    return true;
                }
            }
            if (key.equals("webcam." + name + ".aprilTags")) {
                return true;
            }
        }
        return false;
    }

    /** Its dead-wheel odometry pods ({@code devices.deadWheels}). May be empty. */
    public List<DeadWheel> deadWheels() {
        return deadWheels;
    }

    /** Full-power forward speed. */
    public ModelValue forwardSpeed() {
        return forwardSpeed;
    }

    /** Full-power sideways speed. */
    public ModelValue strafeSpeed() {
        return strafeSpeed;
    }

    /** How fast the robot slows when coasting forward with zero power on FLOAT (see {@link Dynamics}). */
    public ModelValue forwardCoast() {
        return forwardCoast;
    }

    /** The same for sideways coasting. */
    public ModelValue lateralCoast() {
        return lateralCoast;
    }

    /** True to build PVI's own extras (src/pvi/java and src/pviTest/java). */
    public boolean pviExtras() {
        return pviExtras;
    }

    /** Robot length, front bumper to back bumper. */
    public ModelValue lengthInches() {
        return lengthInches;
    }

    /** Robot width, side bumper to side bumper. */
    public ModelValue widthInches() {
        return widthInches;
    }

    /** Fastest spin at full power. */
    public ModelValue turnRate() {
        return turnRate;
    }

    /** The drive motors' response time (see {@link Dynamics}). */
    public ModelValue responseTime() {
        return responseTime;
    }

    /** The traction limit (see {@link Dynamics}). */
    public ModelValue maxAcceleration() {
        return maxAcceleration;
    }

    /** Battery voltage the drive speeds were measured at. */
    public ModelValue speedVoltage() {
        return speedVoltage;
    }

    /**
     * True if the field elements (BIOBUZZ: the HIVE frame's feet and lips, the FLOWERs) block the
     * simulated robot, as the walls do.
     */
    public boolean robotObstacles() {
        return robotObstacles;
    }

    /** OpModes (Driver Station names) the run-every-OpMode smoke test leaves out. May be empty. */
    public List<String> smokeTestSkipOpModes() {
        return smokeTestSkipOpModes;
    }

    /**
     * TeleOps (Driver Station names) that aren't meant to drive, such as camera or tuning tools:
     * the smoke test's left-stick drive check skips them (they still get the crash check). May be
     * empty.
     */
    public List<String> smokeTestNotDrivingTeleOps() {
        return smokeTestNotDrivingTeleOps;
    }

    /** The season field: a file in {@code fields/} without ".json". */
    public String field() {
        return field;
    }

    /** Step 15: open the viewer after "gradlew test" finishes. The tests themselves never open a window. */
    public boolean viewerOpen() {
        return viewerOpen;
    }

    /**
     * The TeleOp the viewer drives when it opens: a Driver Station name (e.g. "Team A Mecanum
     * Drive") or a class's simple name (e.g. "TeamATeleOp"). Empty means the first TeleOp.
     */
    public String viewerDefaultTeleOp() {
        return viewerDefaultTeleOp;
    }

    /** Small helper that reads one kind of value and writes down what was wrong. */
    private record Values(Properties properties, List<String> problems) {

        /** A value that must be there and not blank. */
        String text(String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                problems.add(key + ": missing (see docs/README.md for an example)");
                return null;
            }
            return value.trim();
        }

        /** Text that may be empty, but the key must be there (so a forgotten line is caught). */
        String mayBeEmpty(String key) {
            String value = properties.getProperty(key);
            if (value == null) {
                problems.add(key + ": missing (it may be empty, as in \"" + key + "=\")");
                return "";
            }
            return value.trim();
        }

        /** A comma-separated list, spaces around commas removed. The key must be there; it may be empty. */
        List<String> list(String key) {
            String value = properties.getProperty(key);
            if (value == null) {
                problems.add(key + ": missing (it may be empty, as in \"" + key + "=\")");
                return List.of();
            }
            return value.isBlank() ? List.of() : List.of(value.trim().split("\\s*,\\s*"));
        }

        /** {@code name:parallel:inches} or {@code name:perpendicular:inches} items, comma-separated. */
        List<DeadWheel> deadWheels(String key) {
            List<DeadWheel> pods = new ArrayList<>();
            for (String item : list(key)) {
                String[] parts = item.split("\\s*:\\s*");
                if (parts.length != 3 || !(parts[1].equals("parallel") || parts[1].equals("perpendicular"))) {
                    problems.add(key + ": expected name:parallel:inches or name:perpendicular:inches, got \"" + item + "\"");
                    continue;
                }
                try {
                    pods.add(new DeadWheel(parts[0], parts[1].equals("parallel"), Double.parseDouble(parts[2])));
                } catch (NumberFormatException e) {
                    problems.add(key + ": \"" + parts[2] + "\" in \"" + item + "\" is not a number");
                }
            }
            return List.copyOf(pods);
        }

        /** A start: {@code x, y, headingDegrees}, inches inside the field and degrees. */
        Pose start(String key) {
            String value = text(key);
            if (value == null) {
                return null;
            }
            String[] parts = value.split("\\s*,\\s*");
            try {
                if (parts.length == 3) {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double heading = Double.parseDouble(parts[2]);
                    if (Math.abs(x) < Pose.FIELD_HALF_WIDTH_INCHES && Math.abs(y) < Pose.FIELD_HALF_WIDTH_INCHES
                            && Double.isFinite(heading)) {
                        return Pose.inches(x, y, heading);
                    }
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            problems.add(key + ": expected x, y, headingDegrees (inches inside the field, e.g. -12, -61.5, 90), got \""
                    + value + "\"");
            return null;
        }

        /** {@code FORWARD} or {@code REVERSE}. */
        Direction direction(String key) {
            String value = text(key);
            if (value == null) {
                return null;
            }
            if (value.equals("FORWARD")) {
                return Direction.FORWARD;
            }
            if (value.equals("REVERSE")) {
                return Direction.REVERSE;
            }
            problems.add(key + ": expected FORWARD or REVERSE, got \"" + value + "\"");
            return null;
        }

        /** {@code true} or {@code false}. */
        boolean yesNo(String key) {
            String value = text(key);
            if (value != null && !value.equals("true") && !value.equals("false")) {
                problems.add(key + ": expected true or false, got \"" + value + "\"");
            }
            return "true".equals(value);
        }

        /** The mechanism's keys as a setup, or null if any of them is missing or wrong (already in problems). */
        MechanismSetup mechanism(String intakeMotor, String launcherMotor) {
            int before = problems.size();
            MechanismSetup.Intake intake = null;
            MechanismSetup.Hold hold;
            MechanismSetup.Launcher launcher = null;
            // Reads every key first, so one message lists every missing one.
            Direction intakeDirection = intakeMotor.isEmpty() ? null : direction("intake.direction");
            ModelValue[] intakeFacts = intakeMotor.isEmpty() ? null : new ModelValue[] {
                    number("intake.minPower", MechanismSetup.RATIO),
                    number("intake.forwardInches", ModelValue.INCHES),
                    number("intake.leftInches", ModelValue.INCHES),
                    fact("intake.lengthInches", ModelValue.INCHES),
                    fact("intake.widthInches", ModelValue.INCHES),
                    fact("intake.spitSeconds", MechanismSetup.SECONDS),
                    fact("intake.spitInchesPerSecond", ModelValue.INCHES_PER_SECOND)};
            ModelValue capacity = number("hold.capacity", MechanismSetup.BALLS);
            List<String> accepts = list("hold.accepts");
            ModelValue preloaded = number("hold.preloaded", MechanismSetup.BALLS);
            Direction launcherDirection = launcherMotor.isEmpty() ? null : direction("launcher.direction");
            Direction feedDirection = launcherMotor.isEmpty() ? null : direction("feed.direction");
            String feedMotor = launcherMotor.isEmpty() ? null : text("feed.motor");
            ModelValue[] launcherFacts = launcherMotor.isEmpty() ? null : new ModelValue[] {
                    fact("launcher.ticksPerRev", MechanismSetup.TICKS),
                    fact("launcher.freeSpeedRpm", MechanismSetup.RPM),
                    fact("launcher.spinUpSeconds", MechanismSetup.SECONDS),
                    number("launcher.speedLossPerShot", MechanismSetup.RATIO),
                    fact("launcher.wheelDiameterInches", ModelValue.INCHES),
                    number("launcher.exitFraction", MechanismSetup.RATIO),
                    number("launcher.minBallInchesPerSecond", ModelValue.INCHES_PER_SECOND),
                    number("feed.minPower", MechanismSetup.RATIO),
                    fact("feed.secondsPerBall", MechanismSetup.SECONDS),
                    number("launch.forwardInches", ModelValue.INCHES),
                    number("launch.leftInches", ModelValue.INCHES),
                    number("launch.upInches", ModelValue.INCHES),
                    number("launch.pitchDegrees", MechanismSetup.DEGREES),
                    number("launch.yawDegrees", MechanismSetup.DEGREES)};
            if (problems.size() > before) {
                return null;
            }
            if (intakeFacts != null) {
                intake = new MechanismSetup.Intake(intakeMotor, intakeDirection, intakeFacts[0], intakeFacts[1],
                        intakeFacts[2], intakeFacts[3], intakeFacts[4], intakeFacts[5], intakeFacts[6]);
            }
            hold = new MechanismSetup.Hold(capacity, accepts, preloaded);
            if (launcherFacts != null) {
                launcher = new MechanismSetup.Launcher(launcherMotor, launcherDirection, launcherFacts[0], launcherFacts[1],
                        launcherFacts[2], launcherFacts[3], launcherFacts[4], launcherFacts[5], launcherFacts[6], feedMotor,
                        feedDirection, launcherFacts[7], launcherFacts[8], launcherFacts[9], launcherFacts[10],
                        launcherFacts[11], launcherFacts[12], launcherFacts[13]);
            }
            return new MechanismSetup(intake, hold, launcher);
        }

        /** Refuses a motor name in this key that isn't one of the robot's. An empty or absent one is fine here. */
        void checkMotor(String key, List<String> robotMotors) {
            String motor = properties.getProperty(key);
            if (motor != null && !motor.isBlank() && !robotMotors.contains(motor.trim())) {
                problems.add(key + ": " + motor.trim() + " isn't one of this robot's motors " + robotMotors);
            }
        }

        /** Each webcam's {@code webcam.<name>.<fact>} keys, as camera setups. */
        List<CameraSetup> cameras(List<String> names) {
            List<CameraSetup> setups = new ArrayList<>();
            for (String name : names) {
                List<ModelValue> facts = new ArrayList<>();
                for (String fact : CameraSetup.FACT_NAMES) {
                    facts.add(number("webcam." + name + "." + fact, CameraSetup.unitOf(fact)));
                }
                String aprilTags = text("webcam." + name + ".aprilTags");
                if (facts.contains(null) || aprilTags == null) {
                    continue;   // the missing or wrong key is already in problems
                }
                try {
                    setups.add(new CameraSetup(name, facts.get(0), facts.get(1), facts.get(2), facts.get(3),
                            facts.get(4), facts.get(5), facts.get(6), facts.get(7), facts.get(8), facts.get(9),
                            facts.get(10), facts.get(11), aprilTags));
                } catch (IllegalArgumentException e) {
                    problems.add("webcam." + name + ": " + e.getMessage());
                }
            }
            return List.copyOf(setups);
        }

        /** {@code number | measured or placeholder | source}, as a {@link ModelValue}; the number must be above zero. */
        ModelValue fact(String key, String unit) {
            ModelValue fact = number(key, unit);
            if (fact != null && !(fact.value() > 0)) {
                problems.add(key + ": must be a number above zero, got " + fact.value());
                return null;
            }
            return fact;
        }

        /** {@code number | measured or placeholder | source}, as a {@link ModelValue}; any number, zero or below too. */
        ModelValue number(String key, String unit) {
            String value = text(key);
            if (value == null) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3 || parts[2].isBlank()) {
                problems.add(key + ": expected \"number | measured or placeholder | where it came from\", got \""
                        + value + "\"");
                return null;
            }
            double number;
            try {
                number = Double.parseDouble(parts[0].trim());
            } catch (NumberFormatException e) {
                problems.add(key + ": \"" + parts[0].trim() + "\" is not a number");
                return null;
            }
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                problems.add(key + ": must be a real number, got " + parts[0].trim());
                return null;
            }
            String kind = parts[1].trim();
            String source = parts[2].trim();
            if (kind.equals("measured")) {
                return ModelValue.measured(number, unit, source);
            }
            if (kind.equals("placeholder")) {
                return ModelValue.placeholder(number, unit, source);
            }
            problems.add(key + ": the middle part must be measured or placeholder, got \"" + kind + "\"");
            return null;
        }
    }
}
