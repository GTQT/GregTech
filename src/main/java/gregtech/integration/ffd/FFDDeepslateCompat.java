package gregtech.integration.ffd;

import gregtech.api.util.Mods;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import xy177.farmersfuturedelight.common.registry.FFDBlocks;
import xy177.farmersfuturedelight.common.registry.FFDItems;
import xy177.farmersfuturedelight.common.worldgen.FFDModernStoneProvider;

public final class FFDDeepslateCompat {

    private FFDDeepslateCompat() {}

    /**
     * @return the deepslate block Farmer's Future Delight generates, or {@code null} when the mod is absent or has
     *         its deepslate content disabled
     */
    public static Block deepslate() {
        return Mods.FarmersFutureDelight.isModLoaded() ? FFDBlockAccess.deepslate() : null;
    }

    /**
     * @return the block state to use as ore base and for the ore variant models; never {@code null} so the ore block
     *         variant can always be baked, even when Farmer's Future Delight has deepslate generation disabled
     */
    public static IBlockState deepslateState() {
        Block block = deepslate();
        return block == null ? Blocks.STONE.getDefaultState() : block.getDefaultState();
    }

    public static boolean isDeepslate(IBlockState state) {
        Block block = deepslate();
        return block != null && state != null && state.getBlock() == block;
    }

    /**
     * @return the tuff block state Farmer's Future Delight generates, or {@code null} when the mod is absent or has no
     *         tuff block
     */
    public static IBlockState tuffState() {
        return Mods.FarmersFutureDelight.isModLoaded() ? FFDBlockAccess.tuffState() : null;
    }

    public static boolean isTuff(IBlockState state) {
        IBlockState tuff = tuffState();
        return tuff != null && state != null && state.getBlock() == tuff.getBlock();
    }
}

/**
 * Holder for the direct Farmer's Future Delight references. This class is only loaded when the mod is present.
 */
final class FFDBlockAccess {

    private FFDBlockAccess() {}

    static Block deepslate() {
        // The stone provider resolves the block the mod actually generates; with deepslateMode=AUTO it can delegate
        // the deep base to another mod (e.g. Quark), in which case the mod's own deepslate block is not registered.
        IBlockState state = FFDModernStoneProvider.get().deepslate();
        if (state != null) {
            return state.getBlock();
        }
        return FFDItems.isBlockRegistered(FFDBlocks.DEEPSLATE) ? FFDBlocks.DEEPSLATE : null;
    }

    static IBlockState tuffState() {
        IBlockState state = FFDModernStoneProvider.get().tuff();
        if (state != null) {
            return state;
        }
        return FFDItems.isBlockRegistered(FFDBlocks.TUFF) ? FFDBlocks.TUFF.getDefaultState() : null;
    }
}
