package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockNotifiablePart;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MetaTileEntityCreativeInputHatch extends MetaTileEntityMultiblockNotifiablePart
        implements IMultiblockAbilityPart<IFluidTank>, IGhostSlotConfigurable {

    private static final int TEMPLATE_TANK_CAPACITY = 1;
    private static final int CREATIVE_FLUID_AMOUNT = Integer.MAX_VALUE / 64;

    private NotifiableFluidTank templateTank;
    private CreativeFluidTank creativeTank;
    private GhostCircuitItemStackHandler circuitInventory;

    public MetaTileEntityCreativeInputHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.MAX, false);
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCreativeInputHatch(metaTileEntityId);
    }

    @Override
    protected void initializeInventory() {
        this.templateTank = new NotifiableFluidTank(TEMPLATE_TANK_CAPACITY, this, false);
        this.creativeTank = new CreativeFluidTank(this.templateTank);
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        super.initializeInventory();
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false, this.templateTank);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return new FluidTankList(false);
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return Arrays.asList(MultiblockAbility.IMPORT_FLUIDS, MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.IMPORT_FLUIDS)) {
            abilityInstances.add(this.creativeTank);
        } else if (abilityInstances.isKey(MultiblockAbility.IMPORT_ITEMS)) {
            abilityInstances.add(this.circuitInventory);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var fluidSyncHandler = GTFluidSlot.sync(this.templateTank)
                .phantom(true)
                .showAmount(false, false);

        return GTGuis.createPanel(this, 176, 166)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6))
                .child(GTGuiTextures.TANK_ICON.asWidget()
                        .pos(78, 35)
                        .size(14, 15))
                .child(new GTFluidSlot()
                        .syncHandler(fluidSyncHandler)
                        .pos(77, 52)
                        .size(18))
                .child(new GhostCircuitSlotWidget()
                        .slot(this.circuitInventory, 0)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY)
                        .pos(101, 52))
                .bindPlayerInventory();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.CREATIVE_CONTAINER_OVERLAY.renderSided(EnumFacing.UP, renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = Textures.FLUID_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.circuitInventory.write(data);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.circuitInventory.read(data);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeVarInt(this.circuitInventory.getCircuitValue());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        setGhostCircuitConfig(buf.readVarInt());
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory == null || this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public int getGhostCircuitConfig() {
        return this.circuitInventory == null ? 0 : this.circuitInventory.getCircuitValue();
    }

    @Override
    public void setGhostCustomStack(@NotNull ItemStack stack) {
        if (this.circuitInventory == null) {
            return;
        }
        this.circuitInventory.setCustomStack(stack);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.creative_input_hatch.tooltip"));
    }

    private static class CreativeFluidTank extends FluidTank {

        private final FluidTank template;

        private CreativeFluidTank(FluidTank template) {
            super(CREATIVE_FLUID_AMOUNT);
            this.template = template;
        }

        @Override
        public @Nullable FluidStack getFluid() {
            FluidStack stack = this.template.getFluid();
            return GTUtility.isEmpty(stack) ? null : GTUtility.copy(CREATIVE_FLUID_AMOUNT, stack);
        }

        @Override
        public int getFluidAmount() {
            return GTUtility.isEmpty(this.template.getFluid()) ? 0 : CREATIVE_FLUID_AMOUNT;
        }

        @Override
        public int getCapacity() {
            return CREATIVE_FLUID_AMOUNT;
        }

        @Override
        public void setFluid(FluidStack fluid) {
            this.template.setFluid(GTUtility.isEmpty(fluid) ? null : GTUtility.copy(TEMPLATE_TANK_CAPACITY, fluid));
        }

        @Override
        public @Nullable FluidStack drain(FluidStack resource, boolean doDrain) {
            FluidStack stack = this.template.getFluid();
            if (GTUtility.isEmpty(stack) || GTUtility.isEmpty(resource) || !stack.isFluidEqual(resource)) {
                return null;
            }
            return drain(resource.amount, doDrain);
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack stack = this.template.getFluid();
            if (maxDrain <= 0 || GTUtility.isEmpty(stack)) {
                return null;
            }
            return GTUtility.copy(Math.min(maxDrain, CREATIVE_FLUID_AMOUNT), stack);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(getFluid(), getCapacity());
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[] {
                    new IFluidTankProperties() {

                        @Override
                        public @Nullable FluidStack getContents() {
                            return CreativeFluidTank.this.getFluid();
                        }

                        @Override
                        public int getCapacity() {
                            return CreativeFluidTank.this.getCapacity();
                        }

                        @Override
                        public boolean canFill() {
                            return false;
                        }

                        @Override
                        public boolean canDrain() {
                            return true;
                        }

                        @Override
                        public boolean canFillFluidType(FluidStack fluidStack) {
                            return false;
                        }

                        @Override
                        public boolean canDrainFluidType(FluidStack fluidStack) {
                            FluidStack stack = CreativeFluidTank.this.template.getFluid();
                            return !GTUtility.isEmpty(stack) && !GTUtility.isEmpty(fluidStack) &&
                                    stack.isFluidEqual(fluidStack);
                        }
                    }
            };
        }
    }
}
