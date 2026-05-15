package gregtech.api.metatileentity.interfaces;

import gregtech.api.gui.IUIHolder;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A simple compound Interface for all my TileEntities.
 * <p/>
 * Also delivers most of the Information about TileEntities.
 * <p/>
 */
public interface IGregTechTileEntity extends IHasWorldObjectAndCoords, INeighborCache, ISyncedTileEntity, IUIHolder {

    MetaTileEntity getMetaTileEntity();

    default MetaTileEntity setMetaTileEntity(MetaTileEntity metaTileEntity) {
        return setMetaTileEntity(metaTileEntity, null, null);
    }

    default MetaTileEntity setMetaTileEntity(@NotNull MetaTileEntity metaTileEntity,
                                             @Nullable NBTTagCompound tagCompound) {
        return setMetaTileEntity(metaTileEntity, tagCompound, null);
    }

    /**
     * Sets the meta tile entity, creating a copy of the sample.
     * If {@code tagCompound} is non-null, it is applied via {@link MetaTileEntity#readFromNBT(NBTTagCompound)}.
     * If {@code itemStackData} is non-null, it is applied via {@link MetaTileEntity#initFromItemStackData(NBTTagCompound)}
     * <em>before</em> the initial sync packet is sent, ensuring variant data reaches the client correctly.
     *
     * @param metaTileEntity the sample MTE from the registry
     * @param tagCompound    optional full MTE NBT (e.g. from clipboard paste / BLOCK_ENTITY_TAG)
     * @param itemStackData  optional ItemStack tag for lightweight init (e.g. variant ordinal)
     */
    MetaTileEntity setMetaTileEntity(@NotNull MetaTileEntity metaTileEntity,
                                     @Nullable NBTTagCompound tagCompound,
                                     @Nullable NBTTagCompound itemStackData);

    long getOffsetTimer(); // todo might not keep this one

    @Deprecated
    boolean isFirstTick();
}
