package gregtech.api.mui.widget;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.fakeworld.ArraySchema;
import com.cleanroommc.modularui.utils.fakeworld.BlockHighlight;
import com.cleanroommc.modularui.utils.fakeworld.BlockInfo;
import com.cleanroommc.modularui.utils.fakeworld.ISchema;
import com.cleanroommc.modularui.utils.fakeworld.SchemaRenderer;
import com.cleanroommc.modularui.widgets.SchemaWidget;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * 机器界面里的方向预览：把机器周围 3x3x3 的方块快照成一份 {@link ISchema}，用 MUI 的
 * {@link SchemaWidget} 在界面里实时画出来，同时把鼠标指到的方块/面回报给调用方。
 *
 * <p>
 * 视角和交互都是 MUI {@code SchemaWidget} 自带的：左键拖动转视角、中键拖动平移、滚轮缩放。
 *
 * <p>
 * <b>只能客户端用</b>：{@link ArraySchema#of(World, BlockPos, int)} 会读真实世界的方块和
 * 方块实体（副本），服务端没有渲染上下文，也不该在开界面时去读世界。
 */
@SideOnly(Side.CLIENT)
public final class WorldPreviewWidget {

    /** 固定抓取以机器为中心的 3x3x3。 */
    public static final int RADIUS = 1;

    private WorldPreviewWidget() {}

    /**
     * 以 {@code center} 为中心抓取 3x3x3 的世界快照，并构造带射线检测的预览控件。
     *
     * <p>
     * 悬停的那个面只画白框；<b>点一下</b>才成为"选中面"（绿框），并把结果回报出去 ——
     * 和旧终端 {@code MachineSceneWidget.setOnSelected} 的模型一致。
     *
     * @param world         机器所在的世界
     * @param center        机器坐标
     * @param selectedFace  取当前"点选中"的那个面，用来画绿框；可为 {@code null}
     * @param onFacePicked  鼠标在预览里点下时回调它指着的面（{@link RayTraceResult#sideHit} 就是那个面）；
     *                      点空处或没指到方块时回调 {@code null}
     * @return 预览控件；坐标所在区块没加载时返回 {@code null}
     */
    public static @Nullable SchemaWidget of(@NotNull World world, @NotNull BlockPos center,
                                            @Nullable FaceSupplier selectedFace,
                                            @NotNull Consumer<RayTraceResult> onFacePicked) {
        if (!world.isBlockLoaded(center)) return null;

        RefreshableSchema schema = new RefreshableSchema(world, center);
        schema.refresh();
        return new FacePickerSchemaWidget(schema, selectedFace, onFacePicked);
    }

    /**
     * 抓取 3x3x3 的世界快照。区块没加载时返回 {@code null} —— 这时候读方块只会拿到空气，
     * 预览会是一片空白，不如不显示。
     */
    public static @Nullable ISchema snapshot(@NotNull World world, @NotNull BlockPos center) {
        if (!world.isBlockLoaded(center)) return null;
        return ArraySchema.of(world, center, RADIUS);
    }

    /**
     * 可以换掉内核的快照：每帧比一下机器那一格的方块状态，变了就整份重抓。
     *
     * <p>
     * {@code ArraySchema.blocks} 是私有的、{@link BlockInfo} 也是不可变的，MUI 没有提供"就地刷新"的接口，
     * 所以只能整份重建；用一个包装把 {@code foreach}/{@code getWorld()} 转给当前那份即可，
     * 几何和 origin 都不变（永远是同一个 3x3x3），重建出来的快照可以直接替换。
     *
     * <p>
     * 触发条件是<b>方块状态</b>变了。GT 机器的朝向/输出面设置都会改方块状态
     * （{@code setFrontFacing} 里就写了 {@code notifyBlockUpdate()}），正好能覆盖。
     */
    static final class RefreshableSchema implements ISchema {

        private final World world;
        private final BlockPos center;
        private ArraySchema current;

        private RefreshableSchema(@NotNull World world, @NotNull BlockPos center) {
            this.world = world;
            this.center = center;
        }

        /** 机器那一格的状态变了就重抓；返回是否有变化。 */
        boolean refresh() {
            IBlockState state = this.world.getBlockState(this.center);
            if (!this.world.isBlockLoaded(this.center)) return false;
            if (this.current == null || this.current.getWorld().getBlockState(this.center) != state) {
                this.current = ArraySchema.of(this.world, this.center, RADIUS);
                return true;
            }
            return false;
        }

        @NotNull
        @Override
        public Iterator<Pair<BlockPos, BlockInfo>> iterator() {
            return this.current.iterator();
        }

        @Override
        public World getWorld() {
            return this.current.getWorld();
        }

        @Override
        public Vec3d getFocus() {
            return this.current.getFocus();
        }

        @Override
        public BlockPos getOrigin() {
            return this.current.getOrigin();
        }

        @Override
        public void setRenderFilter(@Nullable BiPredicate<BlockPos, BlockInfo> renderFilter) {
            this.current.setRenderFilter(renderFilter);
        }

        @Override
        public @Nullable BiPredicate<BlockPos, BlockInfo> getRenderFilter() {
            return this.current.getRenderFilter();
        }
    }

    /** 一个面：哪一格 + 朝哪边。 */
    public static final class Face {

        private final BlockPos pos;
        private final EnumFacing facing;

        public Face(@NotNull BlockPos pos, @NotNull EnumFacing facing) {
            this.pos = pos;
            this.facing = facing;
        }

        public @NotNull BlockPos getPos() {
            return this.pos;
        }

        public @NotNull EnumFacing getFacing() {
            return this.facing;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Face other)) return false;
            return this.pos.equals(other.pos) && this.facing == other.facing;
        }

        @Override
        public int hashCode() {
            return 31 * this.pos.hashCode() + this.facing.hashCode();
        }

        @Override
        public String toString() {
            return this.pos + "@" + this.facing;
        }
    }

    /** 取"当前点选的那个面"，每帧都会问一次。 */
    @FunctionalInterface
    public interface FaceSupplier {

        /** @return 选中的面；没选中任何面时返回 {@code null} */
        @Nullable
        Face get();
    }
}

/**
 * 打开射线检测，画出悬停面（白框）和已选中面（绿框）。
 *
 * <p>
 * 两个高亮都用 {@code allSides = false}：只描被指/被选的那一个面的边框，不挡方块本身。
 *
 * <p>
 * 高亮放在 {@code afterRender} 里做：那时 FBO 还绑着、相机变换还在，
 * {@code BlockHighlight.renderHighlight} 才能画进预览画面里。
 */
@SideOnly(Side.CLIENT)
final class FacePickerSchemaRenderer extends SchemaRenderer {

    private static final int HOVER_BORDER_COLOR = Color.withAlpha(Color.WHITE.main, 0.9f);
    private static final int SELECTED_BORDER_COLOR = Color.withAlpha(Color.GREEN.brighter(1), 0.9f);

    private final BlockHighlight hoverBorder = new BlockHighlight(HOVER_BORDER_COLOR, false, 0.0f);
    private final BlockHighlight selectedBorder = new BlockHighlight(SELECTED_BORDER_COLOR, false, 0.0f);
    private final WorldPreviewWidget.FaceSupplier selectedFace;
    private final WorldPreviewWidget.RefreshableSchema schema;

    private RayTraceResult lastResult;

    FacePickerSchemaRenderer(@NotNull WorldPreviewWidget.RefreshableSchema schema,
                             @Nullable WorldPreviewWidget.FaceSupplier selectedFace) {
        super(schema);
        this.schema = schema;
        this.selectedFace = selectedFace;
        rayTracing(true);
        afterRender(renderer -> {
            // 先刷新快照（机器朝向/输出面变了要立刻反映出来），再画边框
            this.schema.refresh();
            this.drawBorders();
        });
    }

    @Override
    protected void onSuccessfulRayTrace(@NotNull RayTraceResult result) {
        this.lastResult = result;
    }

    @Override
    protected void onRayTraceFailed() {
        this.lastResult = null;
    }

    /** 悬停面白框、选中面绿框；两者是同一个面时只画绿框。 */
    private void drawBorders() {
        var camera = getCamera().getPos();

        WorldPreviewWidget.Face selected = this.selectedFace == null ? null : this.selectedFace.get();
        boolean hoveredIsSelected = this.lastResult != null && selected != null &&
                this.lastResult.getBlockPos().equals(selected.getPos()) &&
                this.lastResult.sideHit == selected.getFacing();

        if (selected != null) {
            this.selectedBorder.renderHighlight(selected.getPos(), selected.getFacing(), camera);
        }
        if (this.lastResult != null && !hoveredIsSelected) {
            this.hoverBorder.renderHighlight(this.lastResult, camera);
        }
    }
}

/**
 * 预览控件本体：在 {@code SchemaWidget} 原有的拖动/缩放之上，加"点击选中一个面"。
 *
 * <p>
 * {@code SchemaWidget} 的左键拖动是转视角，所以这里不能直接把左键当点击 ——
 * 记录按下位置，松开时位移很小才算一次点击（旧终端的 {@code MachineSceneWidget} 也是这个思路）。
 */
@SideOnly(Side.CLIENT)
final class FacePickerSchemaWidget extends SchemaWidget {

    /** 鼠标按下到松开位移不超过这个像素数，才算一次"点击"而不是转视角。 */
    private static final int CLICK_TOLERANCE = 3;

    private final Consumer<RayTraceResult> onFacePicked;

    private int pressedX;
    private int pressedY;
    private boolean dragging;

    FacePickerSchemaWidget(@NotNull WorldPreviewWidget.RefreshableSchema schema,
                           @Nullable WorldPreviewWidget.FaceSupplier selectedFace,
                           @NotNull Consumer<RayTraceResult> onFacePicked) {
        super(new FacePickerSchemaRenderer(schema, selectedFace));
        this.onFacePicked = onFacePicked;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        Result result = super.onMousePressed(mouseButton);
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
        if (mouseButton == 0 && !this.dragging) {
            this.onFacePicked.accept(getBlockUnderMouse());
        }
        return result;
    }
}
