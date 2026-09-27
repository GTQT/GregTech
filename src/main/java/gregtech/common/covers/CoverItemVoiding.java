package gregtech.common.covers;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

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

public class CoverItemVoiding extends CoverConveyor {

    protected final NullItemHandler nullItemHandler = new NullItemHandler();

    public CoverItemVoiding(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                            @NotNull EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide, 0, Integer.MAX_VALUE);
        this.isWorkingAllowed = false;
    }

    @Override
    public void update() {
        if (isWorkingAllowed && getCoverableView().getOffsetTimer() % 20 == 0) {
            doTransferItems();
        }
    }

    protected void doTransferItems() {
        IItemHandler myItemHandler = getCoverableView().getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                getAttachedSide());
        if (myItemHandler == null) {
            return;
        }
        voidAny(myItemHandler);
    }

    void voidAny(IItemHandler myItemHandler) {
        for (int srcIndex = 0; srcIndex < myItemHandler.getSlots(); srcIndex++) {
            ItemStack sourceStack = myItemHandler.extractItem(srcIndex, Integer.MAX_VALUE, true);
            if (sourceStack.isEmpty()) {
                continue;
            }
            if (!itemFilterContainer.test(sourceStack)) {
                continue;
            }
            myItemHandler.extractItem(srcIndex, Integer.MAX_VALUE, false);
        }
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        return super.buildUI(guiData, guiSyncManager, settings).height(210 - 22);
    }

    /**
     * 机器主界面侧边按钮栏里的按钮：点击后在机器界面内部展开 / 收起物品销毁设置子面板。
     *
     * <p>
     * 子面板复用 {@link #createUI}，和主界面里的控件完全一致；但不调
     * {@code bindPlayerInventory()}，所以高度比 {@link #buildUI} 少 48（玩家背包那一块）。
     */
    @Override
    public @NotNull IWidget initUILeisure(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                          int index) {
        getItemFilterContainer().setMaxTransferSize(getMaxStackSize());

        IPanelHandler panelHandler = guiSyncManager.syncedPanel("item_voiding_leisure_panel" + index, true,
                (syncManager, panel) -> GTGuis.createPopupPanel("item_voiding_leisure" + index, 176, 120)
                        .child(CoverWithUI.createTitleRow(getPickItem()))
                        .child(createUI(guiData, syncManager)));

        return new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(getPickItem()).asIcon().size(16))
                .addTooltipLine(IKey.str("物品销毁覆盖板" + " 方位：" + EnumFacing.byIndex(index).getName()))
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
    protected Flow createUI(GuiData data, PanelSyncManager guiSyncManager) {
        // noinspection DuplicatedCode
        BooleanSyncValue isWorking = new BooleanSyncValue(this::isWorkingEnabled, this::setWorkingEnabled);

        return super.createUI(data, guiSyncManager)
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
    protected boolean createThroughputRow() {
        return false;
    }

    @Override
    protected boolean createConveyorModeRow() {
        return false;
    }

    @Override
    protected boolean createDistributionModeRow() {
        return false;
    }

    @Override
    public void renderCover(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline,
                            Cuboid6 plateBox, BlockRenderLayer layer) {
        Textures.ITEM_VOIDING.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
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
    public <T> T getCapability(Capability<T> capability, T defaultValue) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(nullItemHandler);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return defaultValue;
    }

    class NullItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 9;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!itemFilterContainer.test(stack)) {
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }
}
