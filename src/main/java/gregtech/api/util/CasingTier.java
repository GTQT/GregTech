package gregtech.api.util;

import gregtech.api.GTValues;
import gregtech.common.blocks.BlockMachineCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.Block;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Centralized registry that maps machine casing (Block, meta) pairs to voltage tiers.
 * Used by the {@code MACHINE_CASING} structure channel to enable tiered casing matching
 * in multiblock structures.
 *
 * <p>In GT5, this is used with {@code GTStructureChannels.TIER_CASING} and
 * {@code GTStructureChannels.TIER_MACHINE_CASING}. Here it integrates with
 * {@link gregtech.api.pattern.casing.GTStructureChannels#MACHINE_CASING}.
 *
 * <p>Registration is done in {@link RegisterCasingTiers#run()}, which should be called
 * during mod initialization after all casing blocks are created.
 *
 * @see gregtech.api.pattern.casing.GTCasingGroups#machineCasings()
 */
public final class CasingTier {

    private static final HashMap<Pair<Block, Integer>, Integer> casingToTier = new HashMap<>();
    private static final List<CasingTierEntry> casingList = new ArrayList<>();

    private CasingTier() {}

    /**
     * A registered casing entry containing block, metadata, and its voltage tier.
     * Avoids repeated HashMap lookups when iterating all registered casings.
     */
    public static final class CasingTierEntry {

        private final Block block;
        private final int meta;
        private final int tier;

        CasingTierEntry(Block block, int meta, int tier) {
            this.block = block;
            this.meta = meta;
            this.tier = tier;
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
    }

    /**
     * Get the voltage tier of a casing block.
     *
     * @param block the casing block
     * @param meta  the block metadata
     * @return the voltage tier (1-based, matching {@link GTValues} tier constants + 1), or null if not registered
     */
    @Nullable
    public static Integer getCasingBlockTier(Block block, int meta) {
        return casingToTier.get(Pair.of(block, meta));
    }

    /**
     * @return an unmodifiable view of all registered casing entries with their tiers
     */
    public static List<CasingTierEntry> getCasingList() {
        return new ArrayList<>(casingList);
    }

    /**
     * Register a casing block with its voltage tier.
     *
     * @param block the casing block
     * @param meta  the block metadata
     * @param tier  the voltage tier (1-based, e.g. ULV=1, LV=2, ..., MAX=15)
     */
    public static void addCasing(@NotNull Block block, int meta, int tier) {
        Objects.requireNonNull(block, "Casing block cannot be null");
        casingToTier.put(Pair.of(block, meta), tier);
        casingList.add(new CasingTierEntry(block, meta, tier));
    }

    /**
     * Registration helper called during mod initialization.
     * Registers all voltage-tiered machine casings from {@link BlockMachineCasing}.
     */
    public static final class RegisterCasingTiers {

        private RegisterCasingTiers() {}

        public static void run() {
            Block machineCasing = MetaBlocks.MACHINE_CASING;
            for (BlockMachineCasing.MachineCasingType type : BlockMachineCasing.MachineCasingType.values()) {
                int meta = type.ordinal();
                int tier = meta + 1;
                addCasing(machineCasing, meta, tier);
            }
        }
    }
}
