package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IColorChannelPart;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityCreativeInputBus extends MetaTileEntityMultiblockNotifiablePart
        implements IMultiblockAbilityPart<IItemHandlerModifiable>, IColorChannelPart {

    /**
     * 创造模式仓无法回溯所属仓参与颜色分组,不显示指示灯。
     */
    @Override
    public boolean showColorChannelPatch() {
        return false;
    }

    private static final int ROW_SIZE = 9;
    private static final int TEMPLATE_SLOTS = ROW_SIZE * ROW_SIZE;

    private TemplateItemStackHandler templateItems;
    private IItemHandlerModifiable creativeItems;

    public MetaTileEntityCreativeInputBus(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.MAX, false);
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCreativeInputBus(metaTileEntityId);
    }

    @Override
    protected void initializeInventory() {
        this.templateItems = new TemplateItemStackHandler();
        this.creativeItems = new CreativeTemplateItemHandler(this.templateItems);
        super.initializeInventory();
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return this.templateItems;
    }

    @Override
    public MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.IMPORT_ITEMS)) {
            abilityInstances.add(this.creativeItems);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        panelSyncManager.registerSlotGroup("creative_item_templates", ROW_SIZE);

        int backgroundWidth = Math.max(9 * 18 + 18 + 14 + 5, ROW_SIZE * 18 + 14);
        int backgroundHeight = 18 + 18 * ROW_SIZE + 94;

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new Grid()
                        .top(18).height(ROW_SIZE * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .mapTo(ROW_SIZE, TEMPLATE_SLOTS, index -> new PhantomItemSlot()
                                .slot(SyncHandlers.itemSlot(templateItems, index)
                                        .ignoreMaxStackSize(true)
                                        .slotGroup("creative_item_templates")
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                            if (!client && !init) {
                                                markDirty();
                                            }
                                        }))))
                .child(GTGuiTextures.getLogo(getUITheme()).asWidget()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 - 7)
                        .size(17));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.CREATIVE_CONTAINER_OVERLAY.renderSided(EnumFacing.UP, renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = Textures.ITEM_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        // Template slots are phantom configuration, not real inventory contents.
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.creative_tooltip.1") + TooltipHelper.RAINBOW +
                I18n.format("gregtech.creative_tooltip.2") + I18n.format("gregtech.creative_tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.creative_input_bus.tooltip"));
    }

    private class TemplateItemStackHandler extends NotifiableItemStackHandler {

        TemplateItemStackHandler() {
            super(MetaTileEntityCreativeInputBus.this, TEMPLATE_SLOTS, null, false);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            super.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : GTUtility.copy(1, stack));
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                setStackInSlot(slot, stack);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }
    }

    private static final class CreativeTemplateItemHandler implements IItemHandlerModifiable {

        private final IItemHandlerModifiable template;

        private CreativeTemplateItemHandler(IItemHandlerModifiable template) {
            this.template = template;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            this.template.setStackInSlot(slot, stack);
        }

        @Override
        public int getSlots() {
            return this.template.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return copyTemplate(slot, getCreativeCount());
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            return copyTemplate(slot, amount);
        }

        @Override
        public int getSlotLimit(int slot) {
            return getCreativeCount();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return this.template.isItemValid(slot, stack);
        }

        private @NotNull ItemStack copyTemplate(int slot, int amount) {
            ItemStack templateStack = this.template.getStackInSlot(slot);
            if (templateStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return ItemHandlerHelper.copyStackWithSize(templateStack, Math.min(amount, getCreativeCount()));
        }

        private int getCreativeCount() {
            return Integer.MAX_VALUE / Math.max(1, this.template.getSlots());
        }
    }
}
