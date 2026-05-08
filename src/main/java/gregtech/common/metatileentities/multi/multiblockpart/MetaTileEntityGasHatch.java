package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.capability.impl.FilteredItemHandler;
import gregtech.api.capability.impl.FluidTankList;
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
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.ConfigHolder;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.RichTextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityGasHatch extends MetaTileEntityMultiblockPart implements
                                                                                IMultiblockAbilityPart<IMufflerHatch>,
                                                                                ITieredMetaTileEntity, IMufflerHatch {

    public static final int INITIAL_INVENTORY_SIZE = 8000;
    protected final FluidTank fluidTank;
    private final int recoveryChance;
    @Getter
    @Setter
    boolean waste;

    public MetaTileEntityGasHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.recoveryChance = Math.min((tier - 1) * 10, 100);
        this.fluidTank = new FluidTank(getInventorySize());
    }

    protected int getInventorySize() {
        return INITIAL_INVENTORY_SIZE * Math.min(Integer.MAX_VALUE, 1 << getTier());
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new FilteredItemHandler(this, 1).setFillPredicate(
                FilteredItemHandler.getCapabilityFilter(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY));
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new GTItemStackHandler(this, 1);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return new FluidTankList(false, fluidTank);
    }

    @Override
    public void recoverItemsTable(List<ItemStack> recoveryItems, int parallel) {

    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance > GTValues.RNG.nextInt(100);
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            fillContainerFromInternalTank(fluidTank);
            pushFluidsIntoNearbyHandlers(getFrontFacing());
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
        if (getController() == null) return 0;
        else return (1 - getTier() * 0.1) *
                (getController().getPollutionAmount() == 0 ? getController().getPollutionAmount() : 0.001);
    }

    @Override
    public void recoverFluidsTable(FluidStack recoveryFluids) {
        if (calculateChance()) {
            fluidTank.fill(recoveryFluids, false);
        }
    }

    @Override
    public boolean isFrontFaceFree() {
        return true;
    }

    @Override
    public boolean isMufflerFull() {
        return fluidTank.getFluidAmount() == fluidTank.getCapacity();
    }

    @Override
    public boolean mufflerWaste() {
        return waste;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityGasHatch(metaTileEntityId, getTier());
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
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay())
            Textures.MUFFLER_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.gas_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.gas_hatch.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.gas_hatch.tooltip.3"));

        if(ConfigHolder.machines.enablePollution) {
            tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.pollution_mte_available"));
            tooltip.add(I18n.format("gregtech.multiblock.pollution_hatch.tooltip.1"));
            tooltip.add(I18n.format("gregtech.multiblock.pollution_hatch.tooltip.2"));
        }

        tooltip.add(I18n.format("gregtech.gas.recovery_tooltip", recoveryChance));
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
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var fluidSyncHandler = GTFluidSlot.sync(fluidTank)
                .showAmountOnSlot(false)
                .accessibility(true, false);

        BooleanSyncValue outputStateValue = new BooleanSyncValue(this::isWaste, this::setWaste);
        guiSyncManager.syncValue("output_state", outputStateValue);

        return GTGuis.createPanel(this, 176, 166)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6))

                // export specific
                .child(new ItemSlot()
                        .pos(90, 44)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                        .slot(new ModularSlot(exportItems, 0)
                                .accessibility(false, true)))
                .child(new ToggleButton()
                        .pos(7, 63)
                        .overlay(GTGuiTextures.OUT_SLOT_OVERLAY)
                        .value(new BooleanSyncValue(outputStateValue::getBoolValue, outputStateValue::setBoolValue))
                        .addTooltip(true, IKey.lang("gregtech.gui.output_item.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.output_item.tooltip.disabled")))

                // common ui
                .child(new RichTextWidget()
                        .size(81 - 6, 38)
                        // .padding(3, 4)
                        .background(GTGuiTextures.DISPLAY.asIcon().size(81, 46))
                        .pos(7 + 3, 16 + 4)
                        .textColor(Color.WHITE.main)
                        .alignment(Alignment.TopLeft)
                        .autoUpdate(true)
                        .textBuilder(richText -> {
                            richText.addLine(IKey.lang("gregtech.gui.fluid_amount"));

                            IKey nameKey = fluidSyncHandler.getFluidNameKey();
                            if (nameKey == IKey.EMPTY) return;

                            String formatted = nameKey.getFormatted();
                            if (formatted.length() > 25) {
                                nameKey = IKey.str(formatted.substring(0, 25) + TextFormatting.WHITE + "...");
                            }

                            richText.addLine(nameKey);
                            richText.addLine(IKey.str(fluidSyncHandler.getFormattedFluidAmount()));
                        }))
                .child(new GTFluidSlot()
                        .disableBackground()
                        .pos(69, 43)
                        .size(18)
                        .syncHandler(fluidSyncHandler))
                .child(new ItemSlot()
                        .pos(90, 16)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.IN_SLOT_OVERLAY)
                        .slot(new ModularSlot(importItems, 0)
                                .singletonSlotGroup()
                                .filter(stack -> {
                                    var h = FluidUtil.getFluidHandler(stack);
                                    if (h == null) return false;
                                    return h.getTankProperties()[0].getContents() == null;
                                })
                                .accessibility(true, true)))
                .bindPlayerInventory();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("waste", waste);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        waste = data.getBoolean("waste");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        NetworkUtils.writeFluidStack(buf, fluidTank.getFluid());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        fluidTank.fill(NetworkUtils.readFluidStack(buf), true);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.fluidTank);
        }
        return super.getCapability(capability, side);
    }
}
