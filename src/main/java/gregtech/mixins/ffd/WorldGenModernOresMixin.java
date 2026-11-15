package gregtech.mixins.ffd;

import gregtech.common.ConfigHolder;
import gregtech.integration.ffd.FFDDeepslateCompat;

import net.minecraft.block.state.IBlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xy177.farmersfuturedelight.common.worldgen.FFDModernStoneProvider;
import xy177.farmersfuturedelight.common.worldgen.WorldGenModernOres;

/**
 * Takes over Farmer's Future Delight's ore generation for GregTech.
 *
 * <p>When GregTech is present it owns ore generation (vanilla ore generation is disabled and replaced by GT's vein
 * system), but Farmer's Future Delight's ores are placed by its own chunk generator through
 * {@link WorldGenModernOres#generateChunk(int, int, WorldGenModernOres.Access)} and never fire
 * {@code OreGenEvent.GenerateMinable}, so {@code ConfigHolder.worldgen.disableVanillaOres} cannot see them. Cancelling
 * the entry point while that option is enabled removes all of its ore veins and its stone blobs; GT's own spheres
 * (dirt, gravel, granite, diorite, andesite, tuff, ...) replace the stone blobs.</p>
 *
 * <p>The two redirects below stay active when the option is disabled: they remove the deepslate ores (GT's deepslate
 * stone type is meant to be filled by GT veins, not duplicated by FFD) and the tuff blobs (GT's tuff sphere takes
 * over) without touching the rest of FFD's generation.</p>
 */
@Mixin(value = WorldGenModernOres.class, remap = false)
public abstract class WorldGenModernOresMixin {

    /**
     * Skips Farmer's Future Delight's whole ore pass - ore veins and the stone blobs placed next to them - when GT
     * has taken over ore generation. Only the entry point is touched so no FFD internals are needed.
     */
    @Inject(method = "generateChunk", at = @At("HEAD"), cancellable = true)
    private void gregtech$skipFfdOreGeneration(int chunkX, int chunkZ, WorldGenModernOres.Access access,
                                               CallbackInfo callbackInfo) {
        if (ConfigHolder.worldgen.disableVanillaOres) {
            callbackInfo.cancel();
        }
    }

    @Redirect(method = "replacement",
              at = @At(value = "INVOKE",
                       target = "Lxy177/farmersfuturedelight/common/worldgen/FFDModernStoneProvider;" +
                                "isDeepBase(Lnet/minecraft/block/state/IBlockState;)Z"))
    private boolean gregtech$skipFfdDeepslateOres(FFDModernStoneProvider stones, IBlockState state) {
        // GregTech registers its own deepslate stone type, so Farmer's Future Delight must not fill it with ores.
        return false;
    }

    /**
     * Skips the tuff vein in {@code generateFromOrigin} so Farmer's Future Delight does not scatter tuff blobs;
     * GregTech registers its own tuff sphere instead. If GregTech cannot resolve the tuff block (e.g. the mod
     * delegates tuff to another mod through {@code deepslateMode=AUTO}), the original check is kept so tuff is not
     * lost entirely.
     */
    @Redirect(method = "generateFromOrigin",
              at = @At(value = "INVOKE",
                       target = "Lxy177/farmersfuturedelight/common/worldgen/FFDModernStoneProvider;hasTuff()Z"))
    private boolean gregtech$skipFfdTuffBlobs(FFDModernStoneProvider stones) {
        return FFDDeepslateCompat.tuffState() == null && stones.hasTuff();
    }
}
