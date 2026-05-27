package gregtech.api.util;

import gregtech.api.GTValues;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockGodforgeGlass;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Centralized registry that maps glass (Block, meta) pairs to voltage tiers.
 * Used by the {@code BOROSILICATE_GLASS} structure channel to enable tiered glass matching
 * in multiblock structures.
 *
 * <p>Supports subTier — multiple glass types can exist at the same voltage tier,
 * differentiated by their subTier index (0 = primary glass for that tier).
 *
 * <p>Registration is done in {@link RegisterGlassTiers#run()}, which should be called
 * during mod initialization after all glass blocks are created.
 *
 * @see gregtech.api.pattern.casing.GTStructureChannels#BOROSILICATE_GLASS
 * @see gregtech.api.pattern.casing.GTCasingGroups#borosilicateGlasses()
 */
public final class GlassTier {

    private static final HashMap<Pair<Block, Integer>, Pair<Integer, Integer>> glassToTierAndChannel = new HashMap<>();
    private static final List<GlassTierEntry> glassList = new ArrayList<>();

    private GlassTier() {}

    /**
     * A registered glass entry containing block, metadata, voltage tier, and channel value.
     */
    public static final class GlassTierEntry {

        private final Block block;
        private final int meta;
        private final int tier;
        private final int channelValue;

        GlassTierEntry(Block block, int meta, int tier, int channelValue) {
            this.block = block;
            this.meta = meta;
            this.tier = tier;
            this.channelValue = channelValue;
        }

        public Block getBlock() {
            return block;
        }

        public int getMeta() {
            return meta;
        }

        public int getTier() {
            return tier;
        }

        public int getChannelValue() {
            return channelValue;
        }

        /**
         * Get the block state for this glass entry.
         *
         * @return the block state corresponding to this entry's block and metadata
         */
        public IBlockState getState() {
            return block.getStateFromMeta(meta);
        }

        /**
         * Get the translation key for this glass entry.
         *
         * @return the translation key (e.g. "tile.blockglass.name.1")
         */
        public String getTranslationKey() {
            return block.getTranslationKey() + "." + meta + ".name";
        }
    }

    /**
     * Get the voltage tier of a glass block.
     *
     * @param block the glass block
     * @param meta  the block metadata
     * @return the voltage tier (matching {@link GTValues} tier constants), or null if not registered
     */
    @Nullable
    public static Integer getGlassBlockTier(Block block, int meta) {
        Pair<Integer, Integer> pair = glassToTierAndChannel.get(Pair.of(block, meta));
        return pair != null ? pair.getLeft() : null;
    }

    /**
     * Get the channel value of a glass block.
     * The channel value is a unique integer identifier used by the structure channel system.
     *
     * @param block the glass block
     * @param meta  the block metadata
     * @return the channel value, or -1 if not registered
     */
    public static int getGlassChannelValue(Block block, int meta) {
        Pair<Integer, Integer> pair = glassToTierAndChannel.get(Pair.of(block, meta));
        return pair != null ? pair.getRight() : -1;
    }

    /**
     * @return an unmodifiable view of all registered glass entries, sorted by tier then channel value
     */
    public static List<GlassTierEntry> getGlassList() {
        return new ArrayList<>(glassList);
    }

    /**
     * Register a glass block with its voltage tier and sub-tier index.
     *
     * @param block   the glass block
     * @param meta    the block metadata
     * @param tier    the voltage tier (e.g. HV=3, EV=4, ..., UMV=12)
     * @param subtier the sub-tier index within the same voltage tier (0 = primary glass)
     */
    public static void addCustomGlass(@NotNull Block block, int meta, int tier, int subtier) {
        Objects.requireNonNull(block, "Glass block cannot be null");
        int channelValue = glassList.size() + 1;
        glassToTierAndChannel.put(Pair.of(block, meta), Pair.of(tier, channelValue));
        glassList.add(new GlassTierEntry(block, meta, tier, channelValue));
    }

    /**
     * Registration helper called during mod initialization.
     * Registers all built-in glass blocks with their voltage tiers.
     */
    public static final class RegisterGlassTiers {

        private RegisterGlassTiers() {}

        public static void run() {
            Block transparentCasing = MetaBlocks.TRANSPARENT_CASING;
            Block godforgeGlass = MetaBlocks.GODFORGE_GLASS;

            addCustomGlass(transparentCasing, BlockGlassCasing.CasingType.TEMPERED_GLASS.ordinal(),
                    GTValues.HV, 0);
            addCustomGlass(Blocks.GLASS, 0,
                    GTValues.HV, 1);

            addCustomGlass(transparentCasing, BlockGlassCasing.CasingType.CLEANROOM_GLASS.ordinal(),
                    GTValues.EV, 0);

            addCustomGlass(transparentCasing, BlockGlassCasing.CasingType.LAMINATED_GLASS.ordinal(),
                    GTValues.IV, 0);

            addCustomGlass(transparentCasing, BlockGlassCasing.CasingType.FUSION_GLASS.ordinal(),
                    GTValues.LuV, 0);

            addCustomGlass(godforgeGlass, BlockGodforgeGlass.GlassType.SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS.ordinal(),
                    GTValues.UHV, 0);
        }
    }
}
