package gregtech.api.mui.widget.side;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widgets.SchemaWidget;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 侧面总览的场景控件：在 MUI 自带的拖动/缩放之上，加“点一下机器某个面就选中它”。
 */
@SideOnly(Side.CLIENT)
public final class SideSceneWidget extends SchemaWidget {

    /** 按下到松开位移不超过这个像素数才算点击，否则视为转视角。 */
    private static final int CLICK_TOLERANCE = 3;

    private final BlockPos machinePos;
    private final BiConsumer<EnumFacing, Integer> onFacePicked;

    private int pressedX;
    private int pressedY;
    private boolean dragging;

    public SideSceneWidget(@NotNull World world, @NotNull BlockPos machinePos,
                           @NotNull Supplier<SideState> stateSupplier,
                           @NotNull Supplier<EnumFacing> selectedSupplier,
                           @NotNull BiConsumer<EnumFacing, Integer> onFacePicked) {
        super(SideSceneRenderer.create(world, machinePos, stateSupplier, selectedSupplier));
        this.machinePos = machinePos;
        this.onFacePicked = onFacePicked;
        this.background(new Rectangle().setColor(0xFF222222));
        // GTM 的 SceneWidget 只有拖动旋转和滚轮缩放，没有中键平移。
        this.enableInteraction(true, false, true);

        // GTM 的原版朝向页始终使用玩家当前视角；只有拿不到玩家时才退回固定 3/4 视角。
        this.scale(2.2f);
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null) {
            this.yaw((float) Math.toRadians(player.rotationYaw - 90.0f));
            this.pitch((float) Math.toRadians(player.rotationPitch));
        } else {
            lookOverview(stateSupplier.get().front);
        }
    }

    /**
     * 和 GTM 的 {@code lookOverview} 一样，从正面偏左、偏上 45° 左右看向机器。
     */
    private void lookOverview(@NotNull EnumFacing front) {
        EnumFacing up = front.getAxis().isHorizontal() ? EnumFacing.UP : EnumFacing.NORTH;
        EnumFacing left = front.getAxis().isHorizontal() ? front.rotateYCCW() : EnumFacing.EAST;
        Vec3d offset = new Vec3d(
                front.getXOffset() + left.getXOffset() * 0.6D + up.getXOffset() * 0.6D,
                front.getYOffset() + left.getYOffset() * 0.6D + up.getYOffset() * 0.6D,
                front.getZOffset() + left.getZOffset() * 0.6D + up.getZOffset() * 0.6D);
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        this.yaw((float) Math.atan2(offset.z, offset.x));
        this.pitch((float) Math.atan2(offset.y, horizontal));
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int mouseButton) {
        Interactable.Result result = super.onMousePressed(mouseButton);
        this.pressedX = getContext().getAbsMouseX();
        this.pressedY = getContext().getAbsMouseY();
        this.dragging = false;
        return result;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (!this.dragging &&
                (Math.abs(getContext().getAbsMouseX() - this.pressedX) > CLICK_TOLERANCE ||
                        Math.abs(getContext().getAbsMouseY() - this.pressedY) > CLICK_TOLERANCE)) {
            this.dragging = true;
        }
        super.onMouseDrag(mouseButton, timeSinceClick);
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        boolean result = super.onMouseRelease(mouseButton);
        if ((mouseButton == 0 || mouseButton == 1) && !this.dragging) {
            RayTraceResult hit = getBlockUnderMouse();
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK &&
                    SideSceneRenderer.SCHEMA_CENTER.equals(hit.getBlockPos())) {
                this.onFacePicked.accept(hit.sideHit, mouseButton);
            }
        }
        return result;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        return super.onMouseScroll(scrollDirection, amount);
    }

    /**
     * 当前机器坐标。保留给后续可能的相机/提示使用。
     */
    public BlockPos getMachinePos() {
        return this.machinePos;
    }
}
