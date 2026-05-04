package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.blocks.BlockGodforgeGlass;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityForgeOfGods extends MultiblockWithDisplayBase {

    private final ForgeOfGodsData data = new ForgeOfGodsData();

    public MetaTileEntityForgeOfGods(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityForgeOfGods(metaTileEntityId);
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        // GT5's main structure is BEAM_SHAFT + FIRST_RING checked from controller offset (63, 14, 1).
        // Once the star renderer is active, the physical first ring is replaced with air.
        String[][] beamShaft = ForgeOfGodsStructureString.BEAM_SHAFT;
        String[][] firstRing = data.isRenderActive() ?
                ForgeOfGodsStructureString.FIRST_RING_AIR :
                ForgeOfGodsStructureString.FIRST_RING;

        FactoryBlockPattern builder = FactoryBlockPattern.start();

        for (String[] layer : beamShaft) {
            builder.aisle(layer);
        }

        for (String[] layer : firstRing) {
            builder.aisle(layer);
        }

        return builder
                // Controller
                .where('S', selfPredicate())
                // Hatches (InputBus, InputHatch, OutputBus) or Transcendentally Amplified Magnetic Confinement Casing
                .where('A', states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING))
                        .or(abilities(MultiblockAbility.IMPORT_ITEMS))
                        .or(abilities(MultiblockAbility.IMPORT_FLUIDS))
                        .or(abilities(MultiblockAbility.EXPORT_ITEMS)))
                // Singularity Reinforced Stellar Shielding Casing
                .where('B', states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)))
                // Celestial Matter Guidance Casing
                .where('C', states(getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING)))
                // Boundless Gravitationally Severed Structure Casing
                .where('D', states(getCasingState(BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING)))
                // Transcendentally Amplified Magnetic Confinement Casing
                .where('E', states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)))
                // Stellar Energy Siphon Casing
                .where('F', states(getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING)))
                // Remote Graviton Flow Modulator
                .where('G', states(getCasingState(BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR)))
                // Spatially Transcendent Gravitational Lens (Glass)
                .where('H', states(getGlassState()))
                // Module Hatches or Singularity Reinforced Stellar Shielding Casing
                .where('J', godforgeModules()
                        .or(states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING))))
                // Medial Graviton Flow Modulator
                .where('I', states(getCasingState(BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR)))
                // Central Graviton Flow Modulator
                .where('K', states(getCasingState(BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR)))
                // Air placeholder used by ring removal/render-state templates
                .where('L', air())
                .build();
    }

    // ==================== Block State Helpers ====================

    private static IBlockState getCasingState(BlockGodforgeCasing.CasingType type) {
        return MetaBlocks.GODFORGE_CASING.getState(type);
    }

    private static IBlockState getGlassState() {
        return MetaBlocks.GODFORGE_GLASS.getState(BlockGodforgeGlass.GlassType.SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS);
    }

    private static TraceabilityPredicate godforgeModules() {
        return metaTileEntities(
                MetaTileEntities.GODFORGE_SMELTING_MODULE,
                MetaTileEntities.GODFORGE_MOLTEN_MODULE,
                MetaTileEntities.GODFORGE_PLASMA_MODULE,
                MetaTileEntities.GODFORGE_EXOTIC_MODULE);
    }

    // ==================== Rendering ====================

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.SOLID_STEEL_CASING;
    }

    @Override
    protected void updateFormedValid() {
        // TODO: Implement Forge of Gods logic
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    public ForgeOfGodsData getData() {
        return data;
    }

    public void updateRenderer() {}

    public void destroyRenderer() {}

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tag = super.writeToNBT(data);
        this.data.writeToNBT(tag, false);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.data.readFromNBT(data);
        reinitializeStructurePattern();
    }
}
