package gregtech.common.terminal2.game.pong;

import gregtech.api.GTValues;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole pong playfield: the ball, both paddles, the score board and the physics step.
 * Drawn in the same coordinate frame the mui1 app used, where the court spans x 5..328 and
 * y 5..227, so the numbers match the original constants.
 */
public class PongWidget extends Widget<PongWidget> {

    /** Half the size of the square collision box of the ball, as used by the ray tracer. */
    private static final float BALL_HALF_SIZE = 4F;
    private static final float BALL_SIZE = 2 * BALL_HALF_SIZE;

    private static final int PLAYFIELD_COLOR = 0xFF000000;
    private static final int NET_COLOR = 0xAAAAAAAA;
    private static final int PADDLE_COLOR = 0xFFFFFFFF;

    private static final int PADDLE_WIDTH = 4;
    private static final int PADDLE_HEIGHT = 20;
    private static final int PADDLE_SPEED = 2;
    private static final int PADDLE_MIN_Y = 30;
    private static final int PADDLE_MAX_Y = 202;

    private static final int LEFT_PADDLE_X = 20;
    private static final int RIGHT_PADDLE_X = 313;
    private static final int BALL_START_X = 333 / 2 - 1;
    private static final int BALL_START_Y = 232 / 2 - 1;

    private static final UITexture BALL_TEXTURE = UITexture
            .fullImage(GTValues.MODID, "textures/gui/widget/pong_ball.png");

    /** Top and bottom walls. The player paddle is appended to this list and removed every tick. */
    private final List<Rectangle> solidObjects = new ArrayList<>();

    private float ballX;
    private float ballY;
    private double theta;
    private float leftPaddleY;
    private float rightPaddleY;

    private int leftScore;
    private int rightScore;
    private int timer;
    /** -1 for no input, 0 to move down, 1 to move up. */
    private int userInput = -1;

    public PongWidget() {
        this.leftPaddleY = BALL_START_Y;
        this.rightPaddleY = BALL_START_Y;
        this.ballX = BALL_START_X;
        this.ballY = BALL_START_Y;
        this.theta = (Math.random() > 0.5 ? Math.PI : Math.PI / 2) + Math.random() * 0.2;
        this.solidObjects.add(new Rectangle(0, 0, 333, 10));
        this.solidObjects.add(new Rectangle(0, 222, 333, 10));
    }

    /** Advances the ball one tick and moves the player paddle to follow the arrow keys. */
    @SideOnly(Side.CLIENT)
    public void updateGame() {
        if (ballX < 10) {
            score(false); // Right side gains a point
        } else if (ballX > 323) {
            score(true); // Left side gains a point
        } else {
            stepPhysics();
        }

        if (ballY > 222) {
            ballY = 211;
        } else if (ballY < 10) {
            ballY = 21;
        }

        timer++;

        if (Keyboard.isKeyDown(Keyboard.KEY_UP) ^ Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
            if (Keyboard.isKeyDown(Keyboard.KEY_UP))
                userInput = 1;
            else
                userInput = 0;
        } else {
            userInput = -1;
        }

        leftPaddleY = movePaddle(leftPaddleY, userInput);
    }

    /** The right paddle is steered by {@link #simplePaddleAI()}. */
    @SideOnly(Side.CLIENT)
    public void updatePaddleAI() {
        rightPaddleY = movePaddle(rightPaddleY, simplePaddleAI());
    }

    /** Moves a paddle one step, keeping it inside the court. */
    private float movePaddle(float paddleY, int input) {
        float moved = paddleY + (input == 0 ? PADDLE_SPEED : input == 1 ? -PADDLE_SPEED : 0);
        if (moved < PADDLE_MIN_Y) return PADDLE_MIN_Y;
        if (moved > PADDLE_MAX_Y) return PADDLE_MAX_Y;
        return moved;
    }

    /** Sweeps the ball along its heading, reflecting it off of every box it would run into. */
    @SideOnly(Side.CLIENT)
    private void stepPhysics() {
        solidObjects.add(new Rectangle(LEFT_PADDLE_X - PADDLE_WIDTH / 2 - 2, (int) leftPaddleY - PADDLE_HEIGHT / 2,
                PADDLE_WIDTH, PADDLE_HEIGHT));
        solidObjects.add(new Rectangle(RIGHT_PADDLE_X - PADDLE_WIDTH / 2 - 2, (int) rightPaddleY - PADDLE_HEIGHT / 2,
                PADDLE_WIDTH, PADDLE_HEIGHT));
        int timeLeft = 1;

        var result = TwoDimensionalRayTracer.nearestBoxSegmentCollision(
                new Vector2f(ballX, ballY),
                new Vector2f((float) (Math.cos(theta) * 2), (float) (Math.sin(theta) * 2)),
                solidObjects,
                new Vector2f(BALL_HALF_SIZE, BALL_HALF_SIZE));
        while (result.time != 1 && timeLeft != 0) {
            float angleMod = 0;
            if (result.pos.y < result.collidedWith.getCenterY() - 2) {
                angleMod -= Math.signum(result.normal.x) * 0.6;
            } else if (result.pos.x > result.collidedWith.getCenterY() + 2) {
                angleMod += Math.signum(result.normal.x) * 0.6;
            }
            // Reflects with a slight angle modification.
            theta = (Math.acos(result.normal.x) * 2 - theta + Math.PI + angleMod) % (2 * Math.PI);

            if (theta > Math.PI / 2 - 0.5 && theta < Math.PI / 2 + 0.5) {
                if (theta <= Math.PI / 2)
                    theta = Math.PI / 2 - 0.51;
                else
                    theta = Math.PI / 2 + 0.51;
            }
            if (theta > 3 * Math.PI / 2 - 0.5 && theta < 3 * Math.PI / 2 + 0.5) {
                if (theta < 3 * Math.PI / 2)
                    theta = 3 * Math.PI / 2 - 0.51;
                else
                    theta = 3 * Math.PI / 2 + 0.51;
            }
            timeLeft -= result.time * timeLeft;
            result = TwoDimensionalRayTracer.nearestBoxSegmentCollision(
                    new Vector2f(ballX, ballY),
                    new Vector2f((float) (Math.cos(theta) * 3 * timeLeft),
                            (float) (Math.sin(theta) * 3 * timeLeft)),
                    solidObjects,
                    new Vector2f(BALL_HALF_SIZE, BALL_HALF_SIZE));
            // To prevent it getting permanently lodged into something.
            ballX += (float) (Math.cos(theta) * 2 * (result.time + 0.1) * (timeLeft + 0.1));
            ballY += (float) (Math.sin(theta) * 2 * (result.time + 0.1) * (timeLeft + 0.1));
        }
        ballX += (float) (Math.cos(theta) * 2 * timeLeft);
        ballY += (float) (Math.sin(theta) * 2 * timeLeft);

        solidObjects.remove(3);
        solidObjects.remove(2);
    }

    @SideOnly(Side.CLIENT)
    private void score(boolean side) {
        if (side) {
            leftScore++;
            theta = Math.PI;
        } else {
            rightScore++;
            theta = 0;
        }
        theta += Math.random() * 0.2;
        ballX = BALL_START_X;
        ballY = BALL_START_Y;
    }

    /** Deliberates once every three ticks, otherwise it takes a breather. */
    @SideOnly(Side.CLIENT)
    private int simplePaddleAI() {
        if (timer % 3 == 0) return -1;
        float target = (ballY + 2 * rightPaddleY) / 3;
        if (target < rightPaddleY) return 1;
        if (target > rightPaddleY) return 0;
        return -1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        WidgetTheme theme = widgetTheme.getTheme();

        GuiDraw.drawRect(5, 5, 323, 222, PLAYFIELD_COLOR);
        GuiDraw.drawRect(333 / 2 - 4, 5, 6, 222, NET_COLOR);

        GuiDraw.drawRect(LEFT_PADDLE_X - PADDLE_WIDTH / 2, (int) leftPaddleY - PADDLE_HEIGHT / 2, PADDLE_WIDTH,
                PADDLE_HEIGHT, PADDLE_COLOR);
        GuiDraw.drawRect(RIGHT_PADDLE_X - PADDLE_WIDTH / 2, (int) rightPaddleY - PADDLE_HEIGHT / 2, PADDLE_WIDTH,
                PADDLE_HEIGHT, PADDLE_COLOR);

        BALL_TEXTURE.draw((int) ballX, (int) ballY, (int) BALL_SIZE, (int) BALL_SIZE);

        IKey.str(String.valueOf(leftScore)).draw(context, 50, 20, 20, 12, theme);
        IKey.str(String.valueOf(rightScore)).draw(context, 283, 20, 20, 12, theme);
    }
}
