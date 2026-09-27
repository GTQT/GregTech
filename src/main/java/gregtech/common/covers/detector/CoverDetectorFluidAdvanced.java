package gregtech.common.covers.detector;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithLeisureUI;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.RedstoneUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.covers.filter.FluidFilterContainer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

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
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;

public class CoverDetectorFluidAdvanced extends CoverDetectorFluid implements CoverWithUI, CoverWithLeisureUI {

    private static final int DEFAULT_MIN = 1000; // 1 Bucket
    private static final int DEFAULT_MAX = 16000; // 16 Buckets

    private long min = DEFAULT_MIN;
    private long max = DEFAULT_MAX;
    private int outputAmount;
    private boolean isLatched = false;

    protected FluidFilterContainer fluidFilter;

    public CoverDetectorFluidAdvanced(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                                      @NotNull EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide);
        this.fluidFilter = new FluidFilterContainer(this);
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer playerIn, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            openUI((EntityPlayerMP) playerIn);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.DETECTOR_FLUID_ADVANCED.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        return GTGuis.defaultPanel(this)
                .height(202)
                .child(CoverWithUI.createTitleRow(getPickItem()))
                .child(buildContent(guiData, guiSyncManager))
                .bindPlayerInventory();
    }

    /**
     * 机器主界面侧边按钮栏里的按钮：点击后在机器界面内部展开 / 收起进阶流体探测器子面板。
     *
     * <p>
     * 子面板复用 {@link #buildContent}，和主界面里的控件完全一致；但不调
     * {@code bindPlayerInventory()}，所以高度比 {@link #buildUI} 少 48（玩家背包那一块）。
     */
    @Override
    public @NotNull IWidget initUILeisure(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                          int index) {
        IPanelHandler panelHandler = guiSyncManager.syncedPanel("fluid_detector_adv_leisure_panel" + index, true,
                (syncManager, panel) -> GTGuis.createPopupPanel("fluid_detector_adv_leisure" + index, 176, 120)
                        .child(CoverWithUI.createTitleRow(getPickItem()))
                        .child(buildContent(guiData, syncManager)));

        return new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(getPickItem()).asIcon().size(16))
                .addTooltipLine(IKey.str("进阶流体探测器覆盖板" + " 方位：" + EnumFacing.byIndex(index).getName()))
                .onMousePressed(mouseButton -> {
                    if (panelHandler.isPanelOpen()) {
                        panelHandler.closePanel();
                    } else {
                        panelHandler.openPanel();
                    }
                    return true;
                });
    }

    /**
     * 面板内容：主界面与侧边浮动子面板共用，两者的控件与同步值完全一致。
     */
    private @NotNull IWidget buildContent(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager) {
        return Flow.column()
                .name("min/max parent column")
                .top(28)
                .margin(5, 0)
                .coverChildrenHeight()
                .child(createMinMaxRow("cover.advanced_fluid_detector.min",
                        this::getMinValue, this::setMinValue,
                        this::getPostFix, w -> w.setMaxLength(10)))
                .child(createMinMaxRow("cover.advanced_fluid_detector.max",
                        this::getMaxValue, this::setMaxValue,
                        this::getPostFix, w -> w.setMaxLength(10)))
                .child(Flow.row()
                        .name("config row")
                        .coverChildrenHeight()
                        .marginBottom(5)
                        .child(new ToggleButton()
                                .name("inverted button")
                                .size(72, 18)
                                .value(new BooleanSyncValue(this::isInverted, this::setInverted))
                                .addTooltipLine(IKey.lang("cover.generic.advanced_detector.invert_tooltip"))
                                .overlay(true, IKey.lang("cover.advanced_energy_detector.inverted")
                                        .style(IKey.WHITE))
                                .overlay(false, IKey.lang("cover.advanced_energy_detector.normal")
                                        .style(IKey.WHITE)))
                        .child(new ToggleButton()
                                .name("latch button")
                                .size(72, 18)
                                .right(0)
                                .overlay(true, IKey.lang("cover.generic.advanced_detector.latched")
                                        .style(IKey.WHITE))
                                .overlay(false, IKey.lang("cover.generic.advanced_detector.continuous")
                                        .style(IKey.WHITE))
                                .addTooltipLine(IKey.lang("cover.generic.advanced_detector.latch_tooltip"))
                                .value(new BooleanSyncValue(this::isLatched, this::setLatched))))
                .child(this.fluidFilter.initUI(guiData, guiSyncManager));
    }

    private @NotNull String getPostFix() {
        return " mL";
    }

    private long getMinValue() {
        return min;
    }

    private long getMaxValue() {
        return max;
    }

    private void setMinValue(long val) {
        this.min = clamp(val, 0, max - 1);
    }

    private void setMaxValue(long val) {
        this.max = clamp(val, min + 1, Long.MAX_VALUE);
    }

    private void setLatched(boolean isLatched) {
        this.isLatched = isLatched;
    }

    public boolean isLatched() {
        return this.isLatched;
    }

    @Override
    public void update() {
        if (getOffsetTimer() % 20 != 0) return;

        IFluidHandler fluidHandler = getCoverableView().getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                null);
        if (fluidHandler == null) return;

        IFluidTankProperties[] tankProperties = fluidHandler.getTankProperties();
        int storedFluid = 0;

        for (IFluidTankProperties properties : tankProperties) {
            FluidStack contents = properties.getContents();

            if (contents != null && fluidFilter.test(contents))
                storedFluid += contents.amount;
        }

        if (isLatched) {
            outputAmount = RedstoneUtil.computeLatchedRedstoneBetweenValues(storedFluid, max, min, isInverted(),
                    outputAmount);
        } else {
            outputAmount = RedstoneUtil.computeRedstoneBetweenValues(storedFluid, max, min, isInverted());
        }

        setRedstoneSignalOutput(outputAmount);
    }

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setLong(MIN_KEY, this.min);
        tagCompound.setLong(MAX_KEY, this.max);
        tagCompound.setBoolean("isLatched", this.isLatched);
        tagCompound.setTag("filter", this.fluidFilter.serializeNBT());
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        if (tagCompound.hasKey(MIN_KEY, Constants.NBT.TAG_INT)) {
            // if one of them is int, so is the other
            this.min = tagCompound.getInteger(MIN_KEY);
            this.max = tagCompound.getInteger(MAX_KEY);
        } else {
            this.min = tagCompound.getLong(MIN_KEY);
            this.max = tagCompound.getLong(MAX_KEY);
        }
        this.isLatched = tagCompound.getBoolean("isLatched");
        this.fluidFilter.deserializeNBT(tagCompound.getCompoundTag("filter"));
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.writeInitialSyncData(packetBuffer);
        packetBuffer.writeLong(this.min);
        packetBuffer.writeLong(this.max);
        packetBuffer.writeBoolean(this.isLatched);
    }

    @Override
    public void readInitialSyncData(@NotNull PacketBuffer packetBuffer) {
        super.readInitialSyncData(packetBuffer);
        this.min = packetBuffer.readLong();
        this.max = packetBuffer.readLong();
        this.isLatched = packetBuffer.readBoolean();
    }
}
