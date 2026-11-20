package gregtech.api.mui.widget.side;

import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.mui.GTGuiTextures;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 侧面总览页：GTM {@code CombinedDirectionalConfigurator} 在 MUI2 上的对应实现。
 *
 * <p>
 * 整页只有一块 3D 场景；左下角是物品/流体模式按钮，右下角是“允许从输出面输入”开关，
 * 左上/右上是自动输出标签。左键点面选中，再点同一个面：左键切换物品模式、右键切换流体模式。
 */
public final class SideOverviewPage {

    private static final String ACTION_KEY = "mte_side_overview";
    private static final int ITEM_COLOR = 0xFFFF6E0F;
    private static final int FLUID_COLOR = 0xFF00B4FF;

    private static final int CONTROL_SIZE = 18;
    private static final int CONTROL_GAP = 1;
    private static final int ROW_GAP = 3;
    private static final int EDGE = 6;

    private final PanelSyncManager syncManager;
    private final MetaTileEntity machine;
    private final World world;
    private final int bottomReserve;
    private final boolean showOutputControls;
    private final IntSyncValue itemSync;
    private final IntSyncValue fluidSync;

    private @Nullable EnumFacing selectedFace;

    private SideOverviewPage(@NotNull PanelSyncManager syncManager, @NotNull MetaTileEntity machine,
                             int bottomReserve, boolean showOutputControls) {
        this.syncManager = syncManager;
        this.machine = machine;
        this.world = machine.getWorld();
        this.bottomReserve = bottomReserve;
        this.showOutputControls = showOutputControls;
        this.selectedFace = null;

        IntSyncValue itemSync = new IntSyncValue(() -> SideState.encode(stateFromMachine().itemFace,
                stateFromMachine().itemAuto, stateFromMachine().itemAllowInput));
        IntSyncValue fluidSync = new IntSyncValue(() -> SideState.encode(stateFromMachine().fluidFace,
                stateFromMachine().fluidAuto, stateFromMachine().fluidAllowInput));
        this.itemSync = itemSync;
        this.fluidSync = fluidSync;
        syncManager.syncValue("mte_side_items", itemSync);
        syncManager.syncValue("mte_side_fluids", fluidSync);
        syncManager.registerSyncedAction(ACTION_KEY, false, true, this::handleAction);
    }

    public static @NotNull IWidget create(@NotNull PanelSyncManager syncManager, @NotNull MetaTileEntity machine,
                                          int width, int height, int bottomReserve, boolean showOutputControls) {
        SideOverviewPage page = new SideOverviewPage(syncManager, machine, bottomReserve, showOutputControls);
        return page.build(width, height);
    }


    private @NotNull IWidget build(int width, int height) {
        ParentWidget<?> root = new ParentWidget<>().name("side_overview_page");
        if (width > 0 && height > 0) {
            root.size(width, height);
        } else {
            root.sizeRel(1f);
        }

        Widget<?> scene = this.world != null && this.world.isRemote ? createScene() : new Widget<>();
        scene.name("side_overview_scene").pos(4, 4);
        root.child(new SceneBox(scene, this.bottomReserve));

        int allowCount = 0;
        if (this.showOutputControls) {
            SideState state = state();
            Flow column = Flow.column()
                    .name("side_controls")
                    .left(EDGE)
                    // 场景本身在底部留 4px，再留 6px 才是 GTM 的控件底边距。
                    .bottom(this.bottomReserve + EDGE + 4)
                    .coverChildren()
                    .childPadding(ROW_GAP)
                    .crossAxisAlignment(Alignment.CrossAxis.START);
            // 截图里的顺序是物品行在上、流体行在下。
            if (state.hasItems) {
                column.child(controls(false));
            }
            if (state.hasFluids) {
                column.child(controls(true));
            }
            root.child(column);

            // IO 面板里的“允许从输出面输入”挪到页面最右侧，和左侧模式按钮同一行。
            Flow allowColumn = Flow.column()
                    .name("side_allow_controls")
                    .right(EDGE)
                    .bottom(this.bottomReserve + EDGE + 4)
                    .coverChildren()
                    .childPadding(ROW_GAP)
                    .crossAxisAlignment(Alignment.CrossAxis.END);
            if (state.hasItems && supportsAllowInput(false)) {
                allowColumn.child(allowInputButton(false));
                allowCount++;
            }
            if (state.hasFluids && supportsAllowInput(true)) {
                allowColumn.child(allowInputButton(true));
                allowCount++;
            }
            root.child(allowColumn);

            root.child(autoLabel(false));
            root.child(autoLabel(true));
        }
        return root;
    }

    @SideOnly(Side.CLIENT)
    private @NotNull Widget<?> createScene() {
        return new SideSceneWidget(this.world, this.machine.getPos(), this::state, () -> this.selectedFace,
                this::onFaceClicked);
    }

    private void onFaceClicked(@NotNull EnumFacing face, int mouseButton) {
        if (face != this.selectedFace) {
            this.selectedFace = face;
            return;
        }
        SideState state = state();
        if (mouseButton == 0) {
            if (state.hasItems) {
                sendMode(false);
            } else if (state.hasFluids) {
                sendMode(true);
            }
        } else if (mouseButton == 1) {
            if (state.hasFluids) {
                sendMode(true);
            } else if (state.hasItems) {
                sendMode(false);
            }
        }
    }

    private @NotNull Flow controls(boolean fluid) {
        Flow row = Flow.row()
                .name(fluid ? "side_controls_fluid" : "side_controls_item")
                .coverChildren()
                .childPadding(CONTROL_GAP)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        row.child(modeButton(fluid));
        return row;
    }

    private @NotNull IWidget modeButton(boolean fluid) {
        ButtonWidget<?> button = new ButtonWidget<>()
                .name(fluid ? "side_mode_fluid" : "side_mode_item")
                .size(CONTROL_SIZE);
        button.onUpdateListener(widget -> {
            int mode = mode(fluid);
            widget.overlay((fluid ? GTGuiTextures.IO_CONFIG_FLUID_MODES :
                    GTGuiTextures.IO_CONFIG_ITEM_MODES)[mode].asIcon().size(CONTROL_SIZE));
        });
        button.setEnabledIf(widget -> canConfigure(fluid));
        button.addTooltipLine(IKey.lang(fluid ? "gregtech.mui.side.mode.fluid" : "gregtech.mui.side.mode.item"));
        button.onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            sendMode(fluid);
            return true;
        });
        return button;
    }

    private @NotNull IWidget allowInputButton(boolean fluid) {
        ToggleButton button = new ToggleButton()
                .name(fluid ? "side_allow_fluid" : "side_allow_item")
                .size(CONTROL_SIZE)
                .overlay(fluid ? GTGuiTextures.BUTTON_FLUID_OUTPUT : GTGuiTextures.BUTTON_ITEM_OUTPUT)
                .value(new BoolValue.Dynamic(
                        () -> fluid ? state().fluidAllowInput : state().itemAllowInput,
                        value -> sendAllowInput(fluid, value)))
                .setEnabledIf(widget -> supportsAllowInput(fluid));
        button.addTooltipLine(IKey.lang(fluid ? "gregtech.mui.side.allow_fluid" : "gregtech.mui.side.allow_item"));
        return button;
    }

    private @NotNull IWidget autoLabel(boolean fluid) {
        TextWidget<?> label = new TextWidget<>(IKey.dynamic(() -> {
            SideState state = state();
            boolean auto = fluid ? state.fluidAuto : state.itemAuto;
            EnumFacing face = fluid ? state.fluidFace : state.itemFace;
            return auto && face != null ? IKey.lang("gregtech.mui.side.auto").getFormatted() : "";
        }));
        label.width(30).shadow(false).color(fluid ? FLUID_COLOR : ITEM_COLOR).top(4);
        if (fluid) {
            label.right(4).textAlign(Alignment.CenterRight);
        } else {
            label.left(4).textAlign(Alignment.CenterLeft);
        }
        return label;
    }

    /** 场景容器：给场景留 4px 内边距，底部扣掉玩家背包高度。 */
    private static final class SceneBox extends ParentWidget<SceneBox> {

        private final Widget<?> scene;
        private final int reserve;
        private int lastWidth = -1;
        private int lastHeight = -1;

        private SceneBox(@NotNull Widget<?> scene, int reserve) {
            this.scene = scene;
            this.reserve = reserve;
            name("side_overview_scene_box");
            sizeRel(1f);
            child(scene);
        }

        @Override
        public void onResized() {
            updateScene();
        }

        @Override
        public void onUpdate() {
            updateScene();
        }

        private void updateScene() {
            int width = Math.max(16, getArea().w() - 8);
            int height = Math.max(16, getArea().h() - this.reserve - 8);
            if (width == this.lastWidth && height == this.lastHeight) return;
            this.lastWidth = width;
            this.lastHeight = height;
            this.scene.size(width, height);
            scheduleResize();
        }
    }

    private int mode(boolean fluid) {
        SideState state = state();
        EnumFacing face = fluid ? state.fluidFace : state.itemFace;
        if (face == null || face != this.selectedFace) return 0;
        return (fluid ? state.fluidAuto : state.itemAuto) ? 2 : 1;
    }

    private boolean canConfigure(boolean fluid) {
        SideState state = state();
        if (this.selectedFace == null || (fluid ? !state.hasFluids : !state.hasItems)) return false;
        return !this.machine.hasFrontFacing() || this.selectedFace != this.machine.getFrontFacing();
    }

    private boolean supportsAllowInput(boolean fluid) {
        IActiveOutputSide side = outputSide();
        if (side == null) return false;
        return fluid ? side.supportsAllowInputFromOutputSideFluids() : side.supportsAllowInputFromOutputSideItems();
    }

    private void sendMode(boolean fluid) {
        if (!canConfigure(fluid) || this.selectedFace == null) return;
        SideState state = state();
        EnumFacing face = fluid ? state.fluidFace : state.itemFace;
        boolean auto = fluid ? state.fluidAuto : state.itemAuto;
        boolean value = face == this.selectedFace && !auto;
        // 先在本地更新缓存，点击后按钮/边框立刻变化；服务端下一 tick 会再校准。
        boolean allowInput = fluid ? state.fluidAllowInput : state.itemAllowInput;
        boolean newAuto = face == this.selectedFace && value;
        updateLocalState(fluid, SideState.encode(this.selectedFace, newAuto, allowInput));
        sendAction(fluid ? SideAction.SET_FLUID_MODE : SideAction.SET_ITEM_MODE, this.selectedFace, value);
    }

    private void sendAllowInput(boolean fluid, boolean value) {
        SideState state = state();
        EnumFacing outputFace = fluid ? state.fluidFace : state.itemFace;
        boolean auto = fluid ? state.fluidAuto : state.itemAuto;
        updateLocalState(fluid, SideState.encode(outputFace, auto, value));

        EnumFacing face = outputFace != null ? outputFace :
                this.machine.hasFrontFacing() ? this.machine.getFrontFacing() : EnumFacing.NORTH;
        sendAction(fluid ? SideAction.SET_FLUID_ALLOW_INPUT : SideAction.SET_ITEM_ALLOW_INPUT, face, value);
    }

    private void updateLocalState(boolean fluid, int encoded) {
        if (this.world == null || !this.world.isRemote) return;
        (fluid ? this.fluidSync : this.itemSync).setIntValue(encoded, false, false);
    }

    private void sendAction(@NotNull SideAction action, @NotNull EnumFacing face, boolean value) {
        this.syncManager.callSyncedAction(ACTION_KEY, buffer -> {
            buffer.writeByte(action.ordinal());
            buffer.writeByte(face.getIndex());
            buffer.writeBoolean(value);
        });
    }

    private void handleAction(@NotNull PacketBuffer buffer) {
        if (buffer.readableBytes() < 3) return;
        int actionIndex = buffer.readByte();
        int faceIndex = buffer.readByte();
        boolean value = buffer.readBoolean();
        if (actionIndex < 0 || actionIndex >= SideAction.values().length) return;
        if (faceIndex < 0 || faceIndex >= EnumFacing.VALUES.length) return;

        EntityPlayer player = this.syncManager.getPlayer();
        if (!(player instanceof EntityPlayerMP)) return;
        if (this.machine.getWorld() == null || this.machine.getWorld().isRemote) return;
        if (player.world != this.machine.getWorld()) return;
        if (player.getDistanceSq(this.machine.getPos()) > 64.0D * 64.0D) return;

        this.machine.applySideAction(SideAction.values()[actionIndex], EnumFacing.VALUES[faceIndex], value);
    }

    private @Nullable IActiveOutputSide outputSide() {
        return this.machine instanceof IActiveOutputSide side ? side : null;
    }

    private @NotNull SideState state() {
        int itemValue = this.itemSync.getIntValue();
        int fluidValue = this.fluidSync.getIntValue();
        IActiveOutputSide side = outputSide();
        boolean hasItems = side != null && side.supportsAutoOutputItems();
        boolean hasFluids = side != null && side.supportsAutoOutputFluids();
        EnumFacing front = this.machine.hasFrontFacing() ? this.machine.getFrontFacing() : EnumFacing.NORTH;
        return new SideState(front, hasItems, hasFluids,
                hasItems ? SideState.decodeFace(itemValue) : null,
                hasFluids ? SideState.decodeFace(fluidValue) : null,
                hasItems && SideState.decodeAuto(itemValue),
                hasFluids && SideState.decodeAuto(fluidValue),
                hasItems && SideState.decodeAllowInput(itemValue),
                hasFluids && SideState.decodeAllowInput(fluidValue));
    }

    /** 服务端编码同步值时的原始状态；界面显示用 {@link #state()} 的同步缓存。 */
    private @NotNull SideState stateFromMachine() {
        IActiveOutputSide side = outputSide();
        boolean hasItems = side != null && side.supportsAutoOutputItems();
        boolean hasFluids = side != null && side.supportsAutoOutputFluids();
        EnumFacing front = this.machine.hasFrontFacing() ? this.machine.getFrontFacing() : EnumFacing.NORTH;
        return new SideState(front, hasItems, hasFluids,
                hasItems ? side.getConfiguredOutputFacingItems() : null,
                hasFluids ? side.getConfiguredOutputFacingFluids() : null,
                hasItems && side.isAutoOutputItems(),
                hasFluids && side.isAutoOutputFluids(),
                hasItems && side.supportsAllowInputFromOutputSideItems() && side.isAllowInputFromOutputSideItems(),
                hasFluids && side.supportsAllowInputFromOutputSideFluids() &&
                        side.isAllowInputFromOutputSideFluids());
    }

    public enum SideAction {
        SET_FRONT,
        SET_ITEM_MODE,
        SET_FLUID_MODE,
        SET_ITEM_ALLOW_INPUT,
        SET_FLUID_ALLOW_INPUT
    }
}
