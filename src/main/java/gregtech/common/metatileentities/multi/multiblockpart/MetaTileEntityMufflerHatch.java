package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.ConfigHolder;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.CapabilityItemHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import gtqt.common.metatileentities.electric.MetaTileEntityDustCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MetaTileEntityMufflerHatch extends MetaTileEntityMultiblockPart implements
                                                                             IMultiblockAbilityPart<IMufflerHatch>,
                                                                             ITieredMetaTileEntity, IMufflerHatch {

    private final int recoveryChance;
    private final GTItemStackHandler inventory;

    private boolean frontFaceFree;
    private boolean mufflerDust;

    public MetaTileEntityMufflerHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.recoveryChance = Math.min((tier - 1) * 10, 100);
        this.inventory = new GTItemStackHandler(this, getInventorySize());
        this.frontFaceFree = false;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMufflerHatch(metaTileEntityId, getTier());
    }

    public int getInventorySize() {
        int sizeRoot = 1 + Math.min(GTValues.UHV, getTier());
        return sizeRoot * sizeRoot;
    }

    @Override
    public void update() {
        super.update();

        if (!getWorld().isRemote) {
            if (getOffsetTimer() % 20 == 0) {
                if (!mufflerDust) {
                    this.frontFaceFree = checkFrontFaceFree();
                } else {
                    BlockPos up = this.getPos().offset(this.getFrontFacing());
                    TileEntity te = getWorld().getTileEntity(up);
                    if (te instanceof IGregTechTileEntity igtte) {
                        MetaTileEntity mte = igtte.getMetaTileEntity();
                        if (mte instanceof MetaTileEntityDustCollector) {
                            pushItemsIntoNearbyHandlers(getFrontFacing());
                            this.frontFaceFree = true;
                        } else this.frontFaceFree = checkFrontFaceFree();
                    } else this.frontFaceFree = checkFrontFaceFree();
                }
            }
        }

        if (getWorld().isRemote && getController() instanceof MultiblockWithDisplayBase controller &&
                controller.isActive()) {
            VanillaParticleEffects.mufflerEffect(this, controller.getMufflerParticle());
            pollution(this.getPollutionAmount(), this.getPollutionTicks());
        }
    }

    @Override
    public double getPollutionAmount() {
        //没有控制器 排放0
        //如果有控制器 控制器自己定义了污染那就按控制器的来，否则按照消声仓自己的来
        if(getController() ==  null) return 0;
        else return  (1 - getTier() * 0.1) * (getController().getPollutionAmount()== 0 ? getController().getPollutionAmount() : 0.001);
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        clearInventory(itemBuffer, inventory);
    }

    public void recoverItemsTable(List<ItemStack> recoveryItems, int parallel) {
        if (calculateChance()) {
            for (ItemStack recoveryItem : recoveryItems) {
                ItemStack itemstack = recoveryItem.copy();
                itemstack.setCount(itemstack.getCount() * parallel);
                GTTransferUtils.insertItem(inventory, itemstack, false);
            }
        }
    }

    @Override
    public void recoverFluidsTable(FluidStack recoveryFluids) {

    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance > GTValues.RNG.nextInt(100);
    }

    /**
     * @return true if front face is free and contains only air blocks in 1x1 area
     */
    public boolean isFrontFaceFree() {
        return frontFaceFree;
    }

    @Override
    public boolean isMufflerFull() {
        if (mufflerDust) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (inventory.getStackInSlot(slot).isEmpty())
                    return false;
            }
            return true;
        }
        else return false;
    }

    @Override
    public boolean mufflerWaste() {
        return mufflerDust;
    }

    private boolean checkFrontFaceFree() {
        BlockPos frontPos = getPos().offset(getFrontFacing());
        IBlockState blockState = getWorld().getBlockState(frontPos);

        if (blockState.getBlock().isAir(blockState, getWorld(), frontPos)) {
            return true;
        }

        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) getController();

        // break a snow layer if it exists, and if this machine is running
        if (controller != null && controller.isActive()) {
            if (GTUtility.tryBreakSnow(getWorld(), frontPos, blockState, true)) {
                return true;
            }
        }

        return GTUtility.isBlockSnow(blockState);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay())
            Textures.MUFFLER_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.4"));
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.5"));
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.6"));

        if(ConfigHolder.machines.enablePollution) {
            tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.pollution_mte_available"));
            tooltip.add(I18n.format("gregtech.multiblock.pollution_hatch.tooltip.1"));
            tooltip.add(I18n.format("gregtech.multiblock.pollution_hatch.tooltip.2"));
        }

        tooltip.add(I18n.format("gregtech.muffler.recovery_tooltip", recoveryChance));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
        tooltip.add(TooltipHelper.BLINKING_RED + I18n.format("gregtech.machine.muffler_hatch.tooltip.2"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public MultiblockAbility<IMufflerHatch> getAbility() {
        return MultiblockAbility.MUFFLER_HATCH;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = (int) Math.sqrt(this.inventory.getSlots());
        int xOffset = rowSize == 10 ? 9 : 0;

        guiSyncManager.registerSlotGroup("item_inv", rowSize);

        List<List<IWidget>> widgets = new ArrayList<>();
        for (int y = 0; y < rowSize; y++) {
            widgets.add(new ArrayList<>());
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                widgets.get(y).add(new ItemSlot().slot(SyncHandlers.itemSlot(this.inventory, index)
                        .slotGroup("item_inv")
                        .accessibility(false, true)));
            }
        }
        BooleanSyncValue outputStateValue = new BooleanSyncValue(() -> mufflerDust, val -> mufflerDust = val);
        guiSyncManager.syncValue("output_state", outputStateValue);

        int backgroundWidth = 176 + xOffset * 2 + 18 + 5;
        int backgroundHeight = 18 + 18 * rowSize + 94;
        // TODO: Change the position of the name when it's standardized.
        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new Grid()
                        .top(18).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .matrix(widgets))

                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(18).height(18 * 4 + 5)
                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget().size(17).top(18 * 3 + 5))

                        .child(new ToggleButton()
                                .top(0)
                                .value(new BoolValue.Dynamic(outputStateValue::getBoolValue,
                                        outputStateValue::setBoolValue))
                                .overlay(GTGuiTextures.OUT_SLOT_OVERLAY)
                                .tooltipBuilder(t -> t.setAutoUpdate(true)
                                        .addLine(outputStateValue.getBoolValue() ?
                                                IKey.lang("gregtech.gui.output_item.tooltip.enabled") :
                                                IKey.lang("gregtech.gui.output_item.tooltip.disabled"))))
                );
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("RecoveryInventory", inventory.serializeNBT());
        data.setBoolean("outputItem", mufflerDust);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.inventory.deserializeNBT(data.getCompoundTag("RecoveryInventory"));
        mufflerDust = data.getBoolean("outputItem");
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.inventory);
        }
        return super.getCapability(capability, side);
    }
}
