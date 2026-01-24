package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.gui.widget.appeng.AEItemGridWidget;
import gregtech.common.inventory.appeng.SerializableItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEMufflerHatch extends MetaTileEntityAEHostablePart<IAEItemStack> implements
                                                                                             IMultiblockAbilityPart<IMufflerHatch>,
                                                                                             ITieredMetaTileEntity,
                                                                                             IMufflerHatch {

    public final static String ITEM_BUFFER_TAG = "ItemBuffer";
    public final static String WORKING_TAG = "WorkingEnabled";
    private final int recoveryChance;
    private boolean workingEnabled = true;
    private SerializableItemList internalBuffer;

    public MetaTileEntityMEMufflerHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, true, IItemStorageChannel.class);
        this.recoveryChance = Math.min((tier-1) * 10, 100);
    }

    @Override
    protected void initializeInventory() {
        this.internalBuffer = new SerializableItemList();
        super.initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEMufflerHatch(metaTileEntityId, getTier());
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = this.getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    public void recoverItemsTable(List<ItemStack> recoveryItems, int parallel) {
        if (calculateChance()) {
            for (ItemStack recoveryItem : recoveryItems) {
                ItemStack itemstack = recoveryItem.copy();
                itemstack.setCount(itemstack.getCount() * parallel);
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemstack);
                internalBuffer.add(aeStack);
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && this.workingEnabled && this.shouldSyncME() && this.updateMEStatus()) {
            if (this.internalBuffer.isEmpty()) return;

            IMEMonitor<IAEItemStack> monitor = getMonitor();
            if (monitor == null) return;

            for (IAEItemStack item : this.internalBuffer) {
                IAEItemStack notInserted = monitor.injectItems(item.copy(), Actionable.MODULATE, getActionSource());
                if (notInserted != null && notInserted.getStackSize() > 0) {
                    item.setStackSize(notInserted.getStackSize());
                } else {
                    item.reset();
                }
            }
        }
    }

    @Override
    public void onRemoval() {
        IMEMonitor<IAEItemStack> monitor = getMonitor();
        if (monitor != null) {
            for (IAEItemStack item : this.internalBuffer) {
                monitor.injectItems(item.copy(), Actionable.MODULATE, this.getActionSource());
            }
        }
        super.onRemoval();
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        ModularUI.Builder builder = ModularUI
                .builder(GuiTextures.BACKGROUND, 176, 18 + 18 * 4 + 94)
                .label(10, 5, getMetaFullName());
        // ME Network status
        builder.dynamicLabel(10, 15, () -> this.isOnline ?
                        I18n.format("gregtech.gui.me_network.online") :
                        I18n.format("gregtech.gui.me_network.offline"),
                0x404040);
        builder.label(10, 25, "gregtech.gui.waiting_list", 0xFFFFFFFF);
        builder.widget(new AEItemGridWidget(10, 35, 3, this.internalBuffer));

        builder.bindPlayerInventory(entityPlayer.inventory, GuiTextures.SLOT, 7, 18 + 18 * 4 + 12);
        return builder.build(this.getHolder(), entityPlayer);
    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance > GTValues.RNG.nextInt(100);
    }

    @Override
    public boolean isFrontFaceFree() {
        return true;
    }

    @Override
    public boolean isMufflerFull() {
        return false;
    }

    @Override
    public boolean mufflerDust() {
        return true;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean(WORKING_TAG, this.workingEnabled);
        data.setTag(ITEM_BUFFER_TAG, this.internalBuffer.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(WORKING_TAG)) {
            this.workingEnabled = data.getBoolean(WORKING_TAG);
        }
        if (data.hasKey(ITEM_BUFFER_TAG, 9)) {
            this.internalBuffer.deserializeNBT((NBTTagList) data.getTag(ITEM_BUFFER_TAG));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            Textures.ME_MUFFLER_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
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
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.muffler.recovery_tooltip", recoveryChance));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
