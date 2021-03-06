package org.firstinspires.ftc.teamcode.Samples;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.teamcode.Hardware;

import java.util.Timer;
import java.util.TimerTask;

import motionProfileGenerator.ftc.tools.PathFollower;
import motionProfileGenerator.ftc.tools.StraightProfileGenerator;
import motionProfileGenerator.ftc.tools.WheelTrajectory;
import motionProfileGenerator.ftc.tools.Config;

/**
 * /**
 * * This file illustrates the concept of driving a motion profiling path using encoders and periods of time
 * * <p>
 * * This class uses encoders to help with more precise movement
 * * <p>
 * * The code assumes that you do have encoders on the wheels,
 * *
 * * <p>
 * * Use Android Studios to Copy this Class, and Paste it into your team's code folder with a new name.
 * * Remove or comment out the @Disabled line to add this opmode to the Driver Station OpMode list
 * <p>
 * For this class to work the Hardware file constants need to match your robot
 */
@Autonomous(name = "MPDriveForward With Gyro", group = "MPbot")
@Disabled
public class StraightProfileGyroFollower_Linear extends LinearOpMode {

    /* Declare OpMode members. */
    Hardware robot = new Hardware();   // Use a bot's hardware

    //Constants are inside the hardware file

    Timer time = new Timer();

    Config config;
    WheelTrajectory trajectory;
    PathFollower follower;

    /**
     * Leave these variables alone
     */
    boolean isRunning = false;
    int index = 0;
    double targetDistance;

    @Override
    public void runOpMode() {
        /* Initialize the hardware variables.
         * The init() method of the hardware class does all the work here
         */
        robot.init(hardwareMap);


        //Sets both motors to brake mode
        robot.leftDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        robot.rightDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        config = new Config(robot.dt, robot.max_velocity, robot.max_acceleration);

        //Generate Trajectory by entering how far you want to travels
        generateTrajectory(20);

        // Wait for the game to start (driver presses PLAY)
        waitForStart();

        // run until the end of the match (driver presses STOP)
        if (opModeIsActive()) {
            follower.configureEncoder(robot.rightDrive.getCurrentPosition(), robot.leftDrive.getCurrentPosition(), robot.ticks_per_revolution, robot.wheel_diameter);
            follower.configurePV(robot.kP_DriveForward, robot.kV_Drive);
            robot.rightDrive.setTargetPosition(robot.rightDrive.getCurrentPosition() + follower.unitToCounts(targetDistance));
            robot.leftDrive.setTargetPosition(robot.leftDrive.getCurrentPosition() + follower.unitToCounts(targetDistance));
            robot.rightDrive.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            robot.leftDrive.setMode(DcMotor.RunMode.RUN_TO_POSITION);

            final double startAngle = robot.imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle;
            time.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (opModeIsActive()) {
                        isRunning = true;
                        int currLeftPos = robot.leftDrive.getCurrentPosition();
                        int currRightPos = robot.leftDrive.getCurrentPosition();

                        double left = follower.calculateLeftPower(index, currLeftPos);
                        double right = follower.calculateRightPower(index, currRightPos);

                        double thetaError = PathFollower.boundHalfDegrees((robot.imu.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle - startAngle) - trajectory.getLeftTrajectory().get(index).getHeading());

                        double turnValue = thetaError * robot.kP_TurnAdjustment;

                        left -= turnValue;
                        right += turnValue;

                        // Normalize the values so neither exceed +/- 1.0
                        double max = Math.max(Math.abs(left), Math.abs(right));
                        if (max > 1.0) {
                            left /= max;
                            right /= max;
                        }

                        robot.leftDrive.setPower(left);
                        robot.rightDrive.setPower(right);
                    } else {
                        isRunning = false;
                        robot.leftDrive.setPower(0);
                        robot.rightDrive.setPower(0);
                        robot.leftDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                        robot.rightDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                        time.cancel();
                    }
                    if (index >= trajectory.getLeftTrajectory().length()) {
                        isRunning = false;
                        robot.leftDrive.setPower(0);
                        robot.rightDrive.setPower(0);
                        robot.leftDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                        robot.rightDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                        time.cancel();
                    }

                    index++;
                }
            }, 0, (long) (robot.dt * 1000));
        }

        while (opModeIsActive()) {
            telemetry.addData("Left Power", robot.leftDrive.getPower());
            telemetry.addData("Right Power", robot.rightDrive.getPower());
            telemetry.addData("Current Segment", index);
            telemetry.update();
        }

        robot.leftDrive.setPower(0);
        robot.rightDrive.setPower(0);
    }

    private void generateTrajectory(double distance) {
        // Send telemetry message to signify robot waiting;
        telemetry.addData("Generating ", "Trajectory");    //
        telemetry.update();
        targetDistance = distance;
        trajectory = StraightProfileGenerator.generateTrajectory(config, robot.wheelbase_width, distance);
        follower = new PathFollower(trajectory);
    }
}
