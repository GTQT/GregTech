package gregtech.api.pattern.casing;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.util.CasingTier;
import gregtech.api.util.GlassTier;
import gregtech.common.blocks.BlockMachineCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Centralized registration of commonly-used ICasingGroup instances.
 * These groups are lazily initialized from runtime registries (e.g. {@link GregTechAPI#HEATING_COILS}).
 *
 * <p>Usage:
 * <pre>{@code
 * DeclarativePatternBuilder.start()
 *     .tieredCasing('C', GTCasingGroups.heatingCoils())
 *         .withChannel(GTStructureChannels.HEATING_COIL)
 *     .build();
 * }</pre>
 *
 * <p>In formStructure, retrieve the matched casing:
 * <pre>{@code
 * ICasing matched = GTStructureChannels.HEATING_COIL.getMatchedCasing(context);
 * if (matched instanceof HeatingCoilCasing) {
 *     IHeatingCoilBlockStats stats = ((HeatingCoilCasing) matched).getCoilStats();
 * }
 * }</pre>
 */
public final class GTCasingGroups {

    private static ICasingGroup heatingCoilGroup;
    private static ICasingGroup machineCasingGroup;
    private static ICasingGroup borosilicateGlassGroup;

    private GTCasingGroups() {}

    /**
     * Get the heating coil casing group. Built from {@link GregTechAPI#HEATING_COILS}.
     * Lazily initialized on first call (must be called after coil registration).
     *
     * @return the heating coil ICasingGroup
     */
    public static ICasingGroup heatingCoils() {
        if (heatingCoilGroup == null) {
            heatingCoilGroup = buildHeatingCoilGroup();
        }
        return heatingCoilGroup;
    }

    private static ICasingGroup buildHeatingCoilGroup() {
        List<ICasing> casings = new ArrayList<>();
        for (Map.Entry<IBlockState, IHeatingCoilBlockStats> entry : GregTechAPI.HEATING_COILS.entrySet()) {
            casings.add(new HeatingCoilCasing(entry.getKey(), entry.getValue()));
        }
        casings.sort(Comparator.comparingInt(ICasing::getTier));
        return CasingDefinition.tieredGroup(
                "heating_coils",
                "gregtech.casing_group.heating_coils",
                true,
                GTStructureChannels.HEATING_COIL.getName(),
                casings);
    }

    /**
     * Get the machine casing group. Built from {@link CasingTier} registry.
     * Lazily initialized on first call (must be called after {@link CasingTier.RegisterCasingTiers#run()}).
     *
     * @return the machine casing ICasingGroup
     */
    public static ICasingGroup machineCasings() {
        if (machineCasingGroup == null) {
            machineCasingGroup = buildMachineCasingGroup();
        }
        return machineCasingGroup;
    }

    private static ICasingGroup buildMachineCasingGroup() {
        List<ICasing> casings = new ArrayList<>();
        Block machineCasing = MetaBlocks.MACHINE_CASING;
        for (CasingTier.CasingTierEntry entry : CasingTier.getCasingList()) {
            int meta = entry.getMeta();
            int tier = entry.getTier();
            IBlockState state = machineCasing.getStateFromMeta(meta);
            BlockMachineCasing.MachineCasingType type =
                    BlockMachineCasing.MachineCasingType.values()[meta];
            casings.add(new MachineCasingCasing(state, type, tier));
        }
        casings.sort(Comparator.comparingInt(ICasing::getTier));
        return CasingDefinition.tieredGroup(
                "machine_casings",
                "gregtech.casing_group.machine_casings",
                true,
                GTStructureChannels.MACHINE_CASING.getName(),
                casings);
    }

    /**
     * Get the borosilicate glass casing group. Built from {@link GlassTier} registry.
     * Lazily initialized on first call (must be called after {@link GlassTier.RegisterGlassTiers#run()}).
     *
     * @return the borosilicate glass ICasingGroup
     */
    public static ICasingGroup borosilicateGlasses() {
        if (borosilicateGlassGroup == null) {
            borosilicateGlassGroup = buildBorosilicateGlassGroup();
        }
        return borosilicateGlassGroup;
    }

    private static ICasingGroup buildBorosilicateGlassGroup() {
        List<ICasing> casings = new ArrayList<>();
        for (GlassTier.GlassTierEntry entry : GlassTier.getGlassList()) {
            Block block = entry.getBlock();
            int meta = entry.getMeta();
            int tier = entry.getTier();
            IBlockState state = block.getStateFromMeta(meta);
            casings.add(new GlassCasing(state, block, meta, tier));
        }
        casings.sort(Comparator.comparingInt(ICasing::getTier));
        return CasingDefinition.tieredGroup(
                "borosilicate_glasses",
                "gregtech.casing_group.borosilicate_glasses",
                true,
                GTStructureChannels.BOROSILICATE_GLASS.getName(),
                casings);
    }

    /**
     * Force re-initialization (e.g. after late coil registration).
     */
    public static void invalidateCache() {
        heatingCoilGroup = null;
        machineCasingGroup = null;
        borosilicateGlassGroup = null;
    }

    /**
     * ICasing implementation that wraps an {@link IHeatingCoilBlockStats}.
     * Allows retrieval of the original coil stats from a matched ICasing in formStructure.
     */
    public static class HeatingCoilCasing implements ICasing {

        private final IBlockState state;
        private final IHeatingCoilBlockStats coilStats;

        public HeatingCoilCasing(IBlockState state, IHeatingCoilBlockStats coilStats) {
            this.state = state;
            this.coilStats = coilStats;
        }

        @Override
        public IBlockState getBlockState() {
            return state;
        }

        @Override
        public String getTranslationKey() {
            return coilStats.getName();
        }

        @Override
        public boolean isTiered() {
            return true;
        }

        @Override
        public int getTier() {
            return coilStats.getTier();
        }

        /**
         * @return the original IHeatingCoilBlockStats for this coil
         */
        public IHeatingCoilBlockStats getCoilStats() {
            return coilStats;
        }
    }

    /**
     * ICasing implementation that wraps a {@link BlockMachineCasing.MachineCasingType}.
     * Allows retrieval of the casing type from a matched ICasing in formStructure.
     */
    public static class MachineCasingCasing implements ICasing {

        private final IBlockState state;
        private final BlockMachineCasing.MachineCasingType casingType;
        private final int tier;

        public MachineCasingCasing(IBlockState state, BlockMachineCasing.MachineCasingType casingType, int tier) {
            this.state = state;
            this.casingType = casingType;
            this.tier = tier;
        }

        @Override
        public IBlockState getBlockState() {
            return state;
        }

        @Override
        public String getTranslationKey() {
            return "tile.machine_casing." + casingType.getName() + ".name";
        }

        @Override
        public boolean isTiered() {
            return true;
        }

        @Override
        public int getTier() {
            return tier;
        }

        /**
         * @return the MachineCasingType for this casing
         */
        public BlockMachineCasing.MachineCasingType getCasingType() {
            return casingType;
        }
    }

    /**
     * ICasing implementation that wraps a glass block.
     * Allows retrieval of the glass block and metadata from a matched ICasing in formStructure.
     */
    public static class GlassCasing implements ICasing {

        private final IBlockState state;
        private final Block block;
        private final int meta;
        private final int tier;

        public GlassCasing(IBlockState state, Block block, int meta, int tier) {
            this.state = state;
            this.block = block;
            this.meta = meta;
            this.tier = tier;
        }

        @Override
        public IBlockState getBlockState() {
            return state;
        }

        @Override
        public String getTranslationKey() {
            return block.getTranslationKey() + "." + meta + ".name";
        }

        @Override
        public boolean isTiered() {
            return true;
        }

        @Override
        public int getTier() {
            return tier;
        }

        public Block getBlock() {
            return block;
        }

        public int getMeta() {
            return meta;
        }
    }
}
