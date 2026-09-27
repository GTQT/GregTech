package gregtech.common.covers;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.covers.filter.FluidFilterContainer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;

public class CoverFluidVoiding extends CoverPump {

    protected final NullFluidTank nullFluidTank = new NullFluidTank();

    public CoverFluidVoiding(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                             @NotNull EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide, 0, Integer.MAX_VALUE);
        this.isWorkingAllowed = false;
        this.fluidFilterContainer = new FluidFilterContainer(this);
        this.fluidFilterContainer.setMaxTransferSize(Integer.MAX_VALUE);
    }

    @Override
    public void update() {
        if (isWorkingAllowed && getOffsetTimer() % 20 == 0) {
            doTransferFluids();
        }
    }

    protected void doTransferFluids() {
        IFluidHandler myFluidHandler = getCoverableView().getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                getAttachedSide());
        if (myFluidHandler == null) {
            return;
        }
        GTTransferUtils.transferFluids(myFluidHandler, nullFluidTank, Integer.MAX_VALUE,
                fluidFilterContainer::test);
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.FLUID_VOIDING.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        return super.buildUI(guiData, guiSyncManager, settings).height(188);
    }

    /**
     * 机器主界面侧边按钮栏里的按钮：点击后在机器界面内部展开 / 收起流体销毁设置子面板。
     *
     * <p>
     * 子面板复用 {@link #createUI}，和主界面里的控件完全一致；但不调
     * {@code bindPlayerInventory()}，所以高度比 {@link #buildUI} 少 48（玩家背包那一块）。
     */
    @Override
    public @NotNull IWidget initUILeisure(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                          int index) {
        getFluidFilterContainer().setMaxTransferSize(getMaxTransferRate());

        IPanelHandler panelHandler = guiSyncManager.syncedPanel("fluid_voiding_leisure_panel" + index, true,
                (syncManager, panel) -> GTGuis.createPopupPanel("fluid_voiding_leisure" + index, 176, 120)
                        .child(CoverWithUI.createTitleRow(getPickItem()))
                        .child(createUI(guiData, syncManager)));

        return new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(getPickItem()).asIcon().size(16))
                .addTooltipLine(IKey.str("流体销毁覆盖板" + " 方位：" + EnumFacing.byIndex(index).getName()))
                .onMousePressed(mouseButton -> {
                    if (panelHandler.isPanelOpen()) {
                        panelHandler.closePanel();
                    } else {
                        panelHandler.openPanel();
                    }
                    return true;
                });
    }

    @Override
    protected Flow createUI(GuiData data, PanelSyncManager syncManager) {
        // noinspection DuplicatedCode
        BooleanSyncValue isWorking = new BooleanSyncValue(this::isWorkingEnabled, this::setWorkingEnabled);

        return super.createUI(data, syncManager)
                .child(Flow.row().height(18).widthRel(1f)
                        .marginBottom(2)
                        .child(new ToggleButton()
                                .value(isWorking)
                                .overlay(createEnabledKey("behaviour.soft_hammer", () -> this.isWorkingAllowed)
                                        .color(Color.WHITE.darker(1)))
                                .widthRel(0.6f)
                                .left(0)));
    }

    @Override
    protected boolean createPumpModeRow() {
        return false;
    }

    @Override
    protected boolean createThroughputRow() {
        return false;
    }

    @Override
    public @NotNull EnumActionResult onSoftMalletClick(@NotNull EntityPlayer playerIn, @NotNull EnumHand hand,
                                                       @NotNull CuboidRayTraceResult hitResult) {
        this.isWorkingAllowed = !this.isWorkingAllowed;
        if (!playerIn.world.isRemote) {
            playerIn.sendStatusMessage(new TextComponentTranslation(isWorkingEnabled() ?
                    "cover.voiding.message.enabled" : "cover.voiding.message.disabled"), true);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, T defaultValue) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(nullFluidTank);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return defaultValue;
    }

    class NullFluidTank extends FluidTank {

        public NullFluidTank() {
            super(Integer.MAX_VALUE);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (fluidFilterContainer.test(resource)) {
                return resource.amount;
            }
            return 0;
        }
    }
}
