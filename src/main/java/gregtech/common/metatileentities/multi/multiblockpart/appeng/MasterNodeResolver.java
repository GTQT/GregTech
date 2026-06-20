package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Utility class for resolving a master {@link MetaTileEntityMEPatternProvider}
 * from a given world position. Supports resolving through a
 * {@link MetaTileEntityMEPatternProviderProxy} indirection.
 * <p>
 * Used by slave nodes ({@link MetaTileEntityAEPatternRegistrar},
 * {@link MetaTileEntityPatternProviderMappingSlave}) to avoid duplicating
 * the same master-lookup logic.
 */
public final class MasterNodeResolver {

    private MasterNodeResolver() {}

    /**
     * Attempt to resolve a {@link MetaTileEntityMEPatternProvider} at the given position.
     * If the position contains a {@link MetaTileEntityMEPatternProviderProxy},
     * the proxy's resolved main will be returned instead.
     *
     * @param world     the world instance (may be null during early init)
     * @param masterPos the target position (may be null if not configured)
     * @return the resolved master, or null if resolution fails
     */
    @Nullable
    public static MetaTileEntityMEPatternProvider resolve(@Nullable World world,
                                                          @Nullable BlockPos masterPos) {
        if (world == null || masterPos == null) {
            return null;
        }

        TileEntity tileEntity = world.getTileEntity(masterPos);
        if (!(tileEntity instanceof IGregTechTileEntity iGregTechTileEntity)) {
            return null;
        }

        MetaTileEntity metaTileEntity = iGregTechTileEntity.getMetaTileEntity();

        if (metaTileEntity instanceof MetaTileEntityMEPatternProvider provider) {
            return provider;
        }

        if (metaTileEntity instanceof MetaTileEntityMEPatternProviderProxy proxy) {
            return proxy.getResolvedMainForLink();
        }

        return null;
    }
}
