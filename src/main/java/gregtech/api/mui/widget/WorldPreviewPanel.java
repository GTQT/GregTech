package gregtech.api.mui.widget;

import gregtech.api.mui.GTGuiTextures;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SchemaWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 机器界面左侧那一列里的"方向预览"入口按钮，以及它点开的浮动面板。
 *
 * <p>
 * 面板上半部分是机器周围 3x3x3 的方向渲染（见 {@link WorldPreviewWidget}），
 * 下半部分是 20x18 的方向按钮：设为正面 / 设为物品输出口 / 设为流体输出口。
 * 点选预览里的某个面后，按钮就作用于那个面。
 *
 * <p>
 * <b>面板在两端都会构建</b>（{@code syncedPanel} 的 builder 服务端也要跑一遍来注册同步值），
 * 所以面板尺寸两端必须一致：预览控件在服务端拿不到时用等尺寸的透明占位控件补上。
 * 真正的世界快照只有客户端会抓。
 *
 * <p>
 * 同理，<b>这个类不能整体标 {@code @SideOnly(CLIENT)}</b> —— {@link #create} 会被机器界面构建流程
 * 在服务端也调用一次。
 */
public final class WorldPreviewPanel {

    /** 面板宽度。 */
    public static final int PANEL_WIDTH = 114;
    /** 面板内边距。 */
    public static final int PADDING = 7;
    /** 方向渲染控件的边长。 */
    public static final int PREVIEW_SIZE = 100;
    /** 预览和下面按钮行之间的间距。 */
    public static final int PREVIEW_GAP = 6;
    /** 方向按钮尺寸。 */
    public static final int BUTTON_WIDTH = 20;
    public static final int BUTTON_HEIGHT = 18;
    /** 并排按钮之间的间距。 */
    public static final int BUTTON_GAP = 2;

    /** 面板高度：上下 padding + 预览 + 间距 + 按钮行。 */
    public static final int PANEL_HEIGHT = PADDING * 2 + PREVIEW_SIZE + PREVIEW_GAP + BUTTON_HEIGHT;

    /** 浮动面板的 id，一台机器同时只会开一个。 */
    private static final String PANEL_NAME = "world_preview";
    /** 同步 key，两端必须一致。 */
    private static final String SYNC_KEY = "world_preview_panel";

    /** 面板里那个预览控件的显示名。 */
    private static final String PREVIEW_NAME = "world_preview_render";

    private final World world;
    private final BlockPos pos;
    private final boolean hasFrontFacing;
    private final boolean hasOutputSides;
    private final FaceActionHandler actionHandler;

    /** 点选中的那个面；没点任何面时为 {@code null}。纯客户端状态。 */
    private @Nullable EnumFacing selectedFace;

    /**
     * 待同步给服务端的那个面。恒定非 {@code null}。
     *
     * <p>
     * <b>不能用 {@link #selectedFace} 直接当同步值的 getter</b>：主面板一打开就会做一次初始同步
     * （{@code ModularContainer.detectAndSendChanges}），那时 {@code selectedFace} 还是 {@code null}，
     * {@code PacketBuffer.writeEnumValue(null)} 会直接 NPE 崩服务端。
     * 所以这里放在一个恒定非 null 的字段上，按钮点击时先把选中面写进来、再赋值触发同步。
     */
    private EnumFacing syncFace = EnumFacing.NORTH;

    /** 三颗按钮各自的同步值，在 {@link #create} 里注册一次。 */
    private final EnumSyncValue<EnumFacing> frontSync;
    private final EnumSyncValue<EnumFacing> itemsSync;
    private final EnumSyncValue<EnumFacing> fluidsSync;

    private WorldPreviewPanel(@NotNull World world, @NotNull BlockPos pos, boolean hasFrontFacing,
                              boolean hasOutputSides, @NotNull FaceActionHandler actionHandler) {
        this.world = world;
        this.pos = pos;
        this.hasFrontFacing = hasFrontFacing;
        this.hasOutputSides = hasOutputSides;
        this.actionHandler = actionHandler;
        this.frontSync = createFaceSync(FaceAction.FRONT);
        this.itemsSync = createFaceSync(FaceAction.ITEMS);
        this.fluidsSync = createFaceSync(FaceAction.FLUIDS);
    }

    /**
     * 一个方向动作的同步值：读 {@link #syncFace}（恒定非 null），点一下写回同一个值，
     * 由服务端的 setter 真正执行。
     */
    private @NotNull EnumSyncValue<EnumFacing> createFaceSync(@NotNull FaceAction action) {
        return new EnumSyncValue<>(EnumFacing.class, () -> this.syncFace, facing -> {
            if (facing == null) return;
            this.actionHandler.apply(action, facing);
        });
    }

    /**
     * 构建入口按钮，并把浮动面板注册进主面板的同步管理器。
     *
     * <p>
     * <b>这个方法两端都会跑</b>（由机器界面构建流程调用），{@code syncedPanel} 必须在服务端也注册一遍，
     * 否则客户端点开时发来的同步包在服务端找不到处理器（{@code SyncHandler ... does not exist}），
     * 按钮点不开。所以这里不做"是不是客户端"的早退，按钮本身两端都造，只是图标和预览只有客户端有。
     *
     * @param syncManager     机器主面板的同步管理器
     * @param world           机器所在的世界
     * @param pos             机器坐标
     * @param hasFrontFacing  这台机器有没有"正面"这个概念（决定要不要出"设为正面"按钮）
     * @param hasOutputSides  这台机器有没有物品/流体输出面（决定要不要出两个输出口按钮）
     * @param actionHandler   真正执行方向设置的回调，两端都会调用，实现里要判 {@code isRemote}
     * @return 挂到左侧那一列的按钮
     */
    public static @NotNull IWidget create(@NotNull PanelSyncManager syncManager, @NotNull World world,
                                          @NotNull BlockPos pos, boolean hasFrontFacing, boolean hasOutputSides,
                                          @NotNull FaceActionHandler actionHandler) {
        WorldPreviewPanel preview = new WorldPreviewPanel(world, pos, hasFrontFacing, hasOutputSides, actionHandler);

        // 三个动作的同步值在这里注册一次（主 PSM 此刻还没 lock）；面板 builder 里不能再注册
        syncManager.syncValue("world_preview_front", preview.frontSync);
        syncManager.syncValue("world_preview_items", preview.itemsSync);
        syncManager.syncValue("world_preview_fluids", preview.fluidsSync);

        IPanelHandler panelHandler = syncManager.syncedPanel(SYNC_KEY, true,
                (panelSyncManager, $) -> preview.buildPanel());

        boolean client = world.isRemote;
        ButtonWidget<?> button = new ButtonWidget<>().size(18, 18);
        if (client) {
            button.overlay(GTGuiTextures.BUTTON_STRUCTURE.asIcon().size(16));
        }
        return button
                .addTooltipLine(IKey.str("机器朝向预览"))
                .onMousePressed(mouseButton -> {
                    if (panelHandler.isPanelOpen()) {
                        panelHandler.closePanel();
                    } else {
                        panelHandler.openPanel();
                    }
                    return true;
                });
    }

    /** 浮动面板：上面方向渲染，下面按钮行。 */
    private @NotNull ModularPanel buildPanel() {
        ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, PANEL_WIDTH, PANEL_HEIGHT);

        SchemaWidget preview = createPreview();
        if (preview != null) {
            panel.child(preview.name(PREVIEW_NAME).size(PREVIEW_SIZE, PREVIEW_SIZE).top(PADDING).left(PADDING));
        } else {
            panel.child(new Widget<>().name(PREVIEW_NAME).size(PREVIEW_SIZE, PREVIEW_SIZE).top(PADDING).left(PADDING));
        }

        panel.child(createButtonRow(PADDING + PREVIEW_SIZE + PREVIEW_GAP));
        return panel;
    }

    /**
     * 方向渲染控件。只有客户端能拿到，服务端返回 {@code null}（用占位控件补位，保证两端布局一致）。
     *
     * <p>
     * 悬停的面画白框；点一下才成为选中面（绿框），也就是按钮的作用目标。
     */
    @SideOnly(Side.CLIENT)
    private @Nullable SchemaWidget createPreview() {
        if (!this.world.isRemote) return null;
        return WorldPreviewWidget.of(this.world, this.pos, this::getPickedFace, this::onFacePicked);
    }

    /** 绿框要画哪个面：点选中的那个。 */
    private WorldPreviewWidget.@Nullable Face getPickedFace() {
        EnumFacing face = this.selectedFace;
        return face == null ? null : new WorldPreviewWidget.Face(this.pos, face);
    }

    /**
     * 预览里点了某个面：记成选中面。
     *
     * <p>
     * <b>射线结果是快照自己的索引坐标，不是世界坐标。</b>
     * {@code ArraySchema.of(world, center, radius)} 铺块时从 {@code center - radius} 起步、原地自增，
     * 所以块键是 {@code 0..2*radius} 的索引，机器自己落在正中 {@code (RADIUS, RADIUS, RADIUS)}。
     * 这里按索引判断，不做任何世界坐标换算。
     *
     * <p>
     * {@link RayTraceResult#sideHit} 是预览里那个面的朝向，直接就是设置面要用的方向。
     */
    private void onFacePicked(@Nullable RayTraceResult result) {
        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK) {
            this.selectedFace = null;
            return;
        }

        BlockPos local = result.getBlockPos();
        int r = WorldPreviewWidget.RADIUS;
        if (local.getX() != r || local.getY() != r || local.getZ() != r) {
            // 点到的是旁边那几格，不是机器
            this.selectedFace = null;
            return;
        }
        this.selectedFace = result.sideHit;
    }

    /**
     * 按钮行：水平居中，每颗 20x18，间距 2。
     *
     * <p>
     * 有正面概念的机器才有"设为正面"；有 {@link IActiveOutputSide} 的机器才有两个输出口按钮。
     */
    private @NotNull Flow createButtonRow(int top) {
        Flow row = Flow.row()
                .name("face_actions")
                .height(BUTTON_HEIGHT)
                .top(top)
                .coverChildrenWidth()
                .left((PANEL_WIDTH - rowWidth(this.hasFrontFacing, this.hasOutputSides)) / 2)
                .childPadding(BUTTON_GAP)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);

        if (this.hasFrontFacing) {
            row.child(createButton("设为正面", GTGuiTextures.BUTTON_EXPORT_FACE, this.frontSync));
        }
        if (this.hasOutputSides) {
            row.child(createButton("设为物品输出口", GTGuiTextures.OVERLAY_ITEM_EXPORT[1], this.itemsSync));
            row.child(createButton("设为流体输出口", GTGuiTextures.OVERLAY_FLUID_EXPORT[1], this.fluidsSync));
        }
        return row;
    }

    /** 按钮行实际宽度：n 颗按钮 + (n-1) 段间距。 */
    private static int rowWidth(boolean hasFront, boolean hasOutputSides) {
        int buttons = (hasFront ? 1 : 0) + (hasOutputSides ? 2 : 0);
        return buttons == 0 ? 0 : buttons * BUTTON_WIDTH + (buttons - 1) * BUTTON_GAP;
    }

    /**
     * 单颗方向按钮：作用于预览里点选中的那个面，没点选时点了没反应。
     *
     * <p>
     * 点一下就是把"选中面"再设一次，MUI 会把这次赋值打包发给服务端，
     * 由服务端那次 setter（{@link FaceActionHandler#apply}）真正执行。
     */
    private @NotNull ButtonWidget<?> createButton(@NotNull String tooltip, @NotNull IDrawable icon,
                                                  @NotNull EnumSyncValue<EnumFacing> syncValue) {
        return new ButtonWidget<>()
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .overlay(icon.asIcon().size(16))
                .addTooltipLine(IKey.str(tooltip))
                .onMousePressed(mouseButton -> {
                    EnumFacing face = this.selectedFace;
                    if (face == null) return false;
                    // 先把面搬进同步字段（恒定非 null），再赋值触发同步 —— 值没变也会发包
                    this.syncFace = face;
                    syncValue.setValue(face, true, true);
                    return true;
                });
    }

    /** 方向按钮各自的用途。 */
    public enum FaceAction {
        FRONT,
        ITEMS,
        FLUIDS
    }

    /**
     * 真正执行一次方向设置。
     *
     * <p>
     * 由按钮的同步值触发，<b>两端都会被调用</b>，实现里必须自己判 {@code world.isRemote}，
     * 只在服务端改机器状态。
     */
    @FunctionalInterface
    public interface FaceActionHandler {

        /** 执行一次方向设置；两端都会调用，实现里要判 {@code isRemote}。 */
        void apply(@NotNull FaceAction action, @NotNull EnumFacing facing);
    }
}
