package gregtech.api.pattern.casing;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;

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
                casings);
    }

    /**
     * Force re-initialization (e.g. after late coil registration).
     */
    public static void invalidateCache() {
        heatingCoilGroup = null;
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
}
