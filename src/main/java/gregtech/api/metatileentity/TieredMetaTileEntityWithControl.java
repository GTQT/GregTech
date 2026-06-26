package gregtech.api.metatileentity;

import gregtech.api.capability.GregtechDataCodes;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public abstract class TieredMetaTileEntityWithControl extends TieredMetaTileEntity {

    private boolean isActive;
    private boolean isWorkingEnabled = true;
    public TieredMetaTileEntityWithControl(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    /**
     *
     * @return true if the rig is active
     */
    public boolean isActive() {
        return this.isActive;
    }

    /**
     *
     * @param active the new state of the rig's activity: true to change to active, else false
     */
    public void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
            this.markDirty();
            if (getWorld() != null && !getWorld().isRemote) {
                this.writeCustomData(GregtechDataCodes.WORKABLE_ACTIVE, buf -> buf.writeBoolean(active));
            }
        }
    }

    /**
     *
     * @return whether working is enabled for the logic
     */
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    /**
     *
     * @param isWorkingEnabled the new state of the rig's ability to work: true to change to enabled, else false
     */
    public void setWorkingEnabled(boolean isWorkingEnabled) {
        if (this.isWorkingEnabled != isWorkingEnabled) {
            this.isWorkingEnabled = isWorkingEnabled;
            markDirty();
            if (getWorld() != null && !getWorld().isRemote) {
                this.writeCustomData(GregtechDataCodes.WORKING_ENABLED,
                        buf -> buf.writeBoolean(isWorkingEnabled));
            }
        }
    }

    /**
     * writes all needed values to NBT This MUST be called and returned in the MetaTileEntity's
     * {@link MetaTileEntity#writeToNBT(NBTTagCompound)} method
     */
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isActive", this.isActive);
        data.setBoolean("isWorkingEnabled", this.isWorkingEnabled);
        return data;
    }

    /**
     * reads all needed values from NBT This MUST be called and returned in the MetaTileEntity's
     * {@link MetaTileEntity#readFromNBT(NBTTagCompound)} method
     */
    public void readFromNBT(@NotNull NBTTagCompound data) {
        super.readFromNBT(data);
        this.isActive = data.getBoolean("isActive");
        this.isWorkingEnabled = data.getBoolean("isWorkingEnabled");
    }

    /**
     * writes all needed values to InitialSyncData This MUST be called and returned in the MetaTileEntity's
     * {@link MetaTileEntity#writeInitialSyncData(PacketBuffer)} method
     */
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(this.isActive);
        buf.writeBoolean(this.isWorkingEnabled);
    }

    /**
     * reads all needed values from InitialSyncData This MUST be called and returned in the MetaTileEntity's
     * {@link MetaTileEntity#receiveInitialSyncData(PacketBuffer)} method
     */
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        setActive(buf.readBoolean());
        setWorkingEnabled(buf.readBoolean());
    }

    /**
     * reads all needed values from CustomData This MUST be called and returned in the MetaTileEntity's
     * {@link MetaTileEntity#receiveCustomData(int, PacketBuffer)} method
     */
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKABLE_ACTIVE) {
            this.isActive = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.isWorkingEnabled = buf.readBoolean();
            scheduleRenderUpdate();
        }
    }
}
