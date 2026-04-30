package gregtech.common.blocks;

import gregtech.api.block.VariantActiveBlock;
import gregtech.api.items.toolitem.ToolClasses;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.NotNull;

public class BlockGodforgeCasing extends VariantActiveBlock<BlockGodforgeCasing.CasingType> {

    public BlockGodforgeCasing() {
        super(Material.IRON);
        setTranslationKey("godforge_casing");
        setHardness(5.0f);
        setResistance(10.0f);
        setSoundType(SoundType.METAL);
        setHarvestLevel(ToolClasses.WRENCH, 4);
        setDefaultState(getState(CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING));
    }

    @Override
    public boolean canCreatureSpawn(@NotNull IBlockState state, @NotNull IBlockAccess world, @NotNull BlockPos pos,
                                    @NotNull SpawnPlacementType type) {
        return false;
    }

    public enum CasingType implements IStringSerializable {

        // Singularity Reinforced Stellar Shielding Casing
        SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING("singularity_reinforced_stellar_shielding"),
        // Celestial Matter Guidance Casing
        CELESTIAL_MATTER_GUIDANCE_CASING("celestial_matter_guidance"),
        // Boundless Gravitationally Severed Structure Casing
        BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING("boundless_gravitationally_severed_structure"),
        // Transcendentally Amplified Magnetic Confinement Casing
        TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING("transcendentally_amplified_magnetic_confinement"),
        // Stellar Energy Siphon Casing
        STELLAR_ENERGY_SIPHON_CASING("stellar_energy_siphon"),
        // Remote Graviton Flow Modulator
        REMOTE_GRAVITON_FLOW_MODULATOR("remote_graviton_flow_modulator"),
        // Medial Graviton Flow Modulator
        MEDIAL_GRAVITON_FLOW_MODULATOR("medial_graviton_flow_modulator"),
        // Central Graviton Flow Modulator
        CENTRAL_GRAVITON_FLOW_MODULATOR("central_graviton_flow_modulator"),
        // Harmonic Phonon Transmission Conduit
        HARMONIC_PHONON_TRANSMISSION_CONDUIT("harmonic_phonon_transmission_conduit");

        private final String name;

        CasingType(String name) {
            this.name = name;
        }

        @NotNull
        @Override
        public String getName() {
            return this.name;
        }
    }
}
