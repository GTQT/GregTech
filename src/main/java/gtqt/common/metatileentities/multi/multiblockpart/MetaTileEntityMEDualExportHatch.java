package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.InaccessibleInfiniteSlot;
import gregtech.api.capability.InaccessibleInfiniteTank;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.gui.widget.appeng.AEFluidGridWidget;
import gregtech.common.gui.widget.appeng.AEItemGridWidget;
import gregtech.common.inventory.appeng.SerializableFluidList;
import gregtech.common.inventory.appeng.SerializableItemList;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MetaTileEntityMEDualExportHatch extends MetaTileEntityMEControlBase
        implements IMultiblockAbilityPart<DualHandler>, IControllable {

    public final static String ITEM_BUFFER_TAG = "ItemBuffer";
    public final static String FLUID_BUFFER_TAG = "FluidBuffer";

    private SerializableItemList internalItemBuffer;
    private SerializableFluidList internalFluidBuffer;

    public MetaTileEntityMEDualExportHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 5, false);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEDualExportHatch(this.metaTileEntityId);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag(ITEM_BUFFER_TAG, this.internalItemBuffer.serializeNBT());
        data.setTag(FLUID_BUFFER_TAG, this.internalFluidBuffer.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(ITEM_BUFFER_TAG, 9)) {
            this.internalItemBuffer.deserializeNBT((NBTTagList) data.getTag(ITEM_BUFFER_TAG));
        }
        if (data.hasKey(FLUID_BUFFER_TAG, 9)) {
            this.internalFluidBuffer.deserializeNBT((NBTTagList) data.getTag(FLUID_BUFFER_TAG));
        }
    }

    @Override
    protected void initializeInventory() {
        this.internalItemBuffer = new SerializableItemList();
        this.internalFluidBuffer = new SerializableFluidList();
        super.initializeInventory();
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && this.isWorkingEnabled() && this.shouldSyncME() && this.updateMEStatus()) {
            if (!this.internalItemBuffer.isEmpty()) {

                IMEMonitor<IAEItemStack> monitor = getItemMonitor();
                if (monitor == null) return;

                for (IAEItemStack item : this.internalItemBuffer) {
                    IAEItemStack notInserted = monitor.injectItems(item.copy(), Actionable.MODULATE, getActionSource());
                    if (notInserted != null && notInserted.getStackSize() > 0) {
                        item.setStackSize(notInserted.getStackSize());
                    } else {
                        item.reset();
                    }
                }
            }

            if (!this.internalFluidBuffer.isEmpty()) {

                IMEMonitor<IAEFluidStack> monitor = getFluidMonitor();
                if (monitor == null) return;

                for (IAEFluidStack fluid : this.internalFluidBuffer) {
                    IAEFluidStack notInserted = monitor.injectItems(fluid.copy(), Actionable.MODULATE,
                            getActionSource());
                    if (notInserted != null && notInserted.getStackSize() > 0) {
                        fluid.setStackSize(notInserted.getStackSize());
                    } else {
                        fluid.reset();
                    }
                }
            }
        }
    }

    @Override
    public void onRemoval() {
        IMEMonitor<IAEItemStack> itemMonitor = getItemMonitor();
        if (itemMonitor != null) {
            for (IAEItemStack item : this.internalItemBuffer) {
                itemMonitor.injectItems(item.copy(), Actionable.MODULATE, this.getActionSource());
            }
        }

        IMEMonitor<IAEFluidStack> fluidMonitor = getFluidMonitor();
        if (fluidMonitor == null) return;

        for (IAEFluidStack fluid : this.internalFluidBuffer) {
            fluidMonitor.injectItems(fluid.copy(), Actionable.MODULATE, this.getActionSource());
        }

        super.onRemoval();
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        ModularUI.Builder builder = ModularUI
                .builder(GuiTextures.BACKGROUND, 176, 18 + 18 * 8 + 94)
                .label(10, 5, getMetaFullName());
        // ME Network status
        builder.dynamicLabel(10, 15, () -> this.isOnline ?
                        I18n.format("gregtech.gui.me_network.online") :
                        I18n.format("gregtech.gui.me_network.offline"),
                0x404040);
        builder.label(10, 25, "gregtech.gui.waiting_list", 0xFFFFFFFF);
        builder.widget(new AEItemGridWidget(10, 35, 3, this.internalItemBuffer));

        // ME Network status
        builder.label(10, 25 + 18 * 4, "gregtech.gui.waiting_list", 0xFFFFFFFF);
        builder.widget(new AEFluidGridWidget(10, 35 + 18 * 4, 3, this.internalFluidBuffer));

        builder.bindPlayerInventory(entityPlayer.inventory, GuiTextures.SLOT, 7, 18 + 18 * 8 + 12);
        return builder.build(this.getHolder(), entityPlayer);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            if (isOnline) {
                Textures.ME_DUAL_OUTPUT_HATCH_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
            } else {
                Textures.ME_DUAL_OUTPUT_HATCH.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.item_bus.export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.fluid_hatch.export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return Arrays.asList(MultiblockAbility.EXPORT_ITEMS, MultiblockAbility.EXPORT_FLUIDS);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_ITEMS))
            abilityInstances.add(new InaccessibleInfiniteSlot(this, this.internalItemBuffer, this.getController()));
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_FLUIDS))
            abilityInstances.add(new InaccessibleInfiniteTank(this, this.internalFluidBuffer, this.getController()));
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        if (controllerBase instanceof MultiblockWithDisplayBase) {
            ((MultiblockWithDisplayBase) controllerBase).enableItemInfSink();
            ((MultiblockWithDisplayBase) controllerBase).enableFluidInfSink();
        }
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        // override here is gross, but keeps things in order despite
        // IDs being out of order, due to UEV+ being added later
        if (this == GTQTMetaTileEntities.ME_DUAL_EXPORT_HATCH) {
            subItems.add(GTQTMetaTileEntities.ME_DUAL_EXPORT_HATCH.getStackForm());
        } else if (this.getClass() != MetaTileEntityMEDualExportHatch.class) {
            // let subclasses fall through this override
            super.getSubItems(creativeTab, subItems);
        }
    }
}
