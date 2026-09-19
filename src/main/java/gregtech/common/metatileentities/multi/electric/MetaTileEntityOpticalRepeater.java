package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockComputerCasing;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Regenerates an optical signal whose strength was spent by cable decay.
 * <p>
 * Every face that looks at an optical cable (or straight at a machine carrying an optical
 * capability) is treated as an input. Any other face re-broadcasts whatever its inputs carry at
 * full strength, so the signal starts a brand new decay budget from this block.
 * <p>
 * The relay never amplifies capacity: the forwarded CWU/t is still bounded by the cable materials
 * on both sides of it, and {@code seen} collections from the asking machine keep relay chains from
 * feeding back into themselves.
 */
public class MetaTileEntityOpticalRepeater extends MultiblockWithDisplayBase {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:optical_repeater", () -> DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XXX", "XSX", "XXX")
                    .self('S', MetaTileEntityOpticalRepeater.class)
                    .block('C',
                            MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.SUPERCONDUCTOR_COIL))
                    .casing('X', MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.HIGH_POWER_CASING))
                    .buildStructureDefinition());

    private final EnumFacing[] inputs = new EnumFacing[EnumFacing.VALUES.length];
    private final EnumFacing[] outputs = new EnumFacing[EnumFacing.VALUES.length];
    private int inputCount;
    private int outputCount;

    public MetaTileEntityOpticalRepeater(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_CASING);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityOpticalRepeater(metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        refreshFaceCache();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        clearFaceCache();
    }

    @Override
    protected void updateFormedValid() {
        // the surrounding cables can change at any time; re-classify faces periodically
        if ((getOffsetTimer() % 10) == 0) {
            refreshFaceCache();
        }
    }

    private void clearFaceCache() {
        inputCount = 0;
        outputCount = 0;
    }

    private void refreshFaceCache() {
        clearFaceCache();
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (hasOpticalNeighbor(facing)) {
                inputs[inputCount++] = facing;
            } else {
                outputs[outputCount++] = facing;
            }
        }
    }

    /**
     * A face is an input when it looks at an optical cable, or straight at a machine that already
     * carries an optical capability. Every other face re-broadcasts what the inputs receive.
     */
    private boolean hasOpticalNeighbor(@NotNull EnumFacing facing) {
        TileEntity tile = getNeighborTile(facing);
        if (tile == null) return false;
        if (tile instanceof TileEntityOpticalPipe) return true;
        return tile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, facing.getOpposite()) ||
                tile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, facing.getOpposite());
    }

    @Nullable
    private TileEntity getNeighborTile(EnumFacing facing) {
        World world = getWorld();
        BlockPos pos = getPos();
        if (world == null || pos == null) return null;
        return world.getTileEntity(pos.offset(facing));
    }

    /** The delegate handed to machines asking this relay for optical capabilities. */
    private final Forwarder forwarder = new Forwarder();

    private final class Forwarder implements IOpticalComputationProvider, IDataAccessHatch {

        @Override
        public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
            if (seen.contains(this)) return 0;
            seen.add(this);
            int allocated = 0;
            for (int i = 0; i < inputCount && allocated < cwut; i++) {
                IOpticalComputationProvider provider = getComputationInput(inputs[i]);
                if (provider == null || seen.contains(provider)) continue;
                allocated += provider.requestCWUt(cwut - allocated, simulate, seen);
            }
            return allocated;
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            if (seen.contains(this)) return 0;
            seen.add(this);
            int total = 0;
            for (int i = 0; i < inputCount; i++) {
                IOpticalComputationProvider provider = getComputationInput(inputs[i]);
                if (provider == null || seen.contains(provider)) continue;
                total += provider.getMaxCWUt(seen);
            }
            return total;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            if (seen.contains(this)) return false;
            seen.add(this);
            for (int i = 0; i < inputCount; i++) {
                IOpticalComputationProvider provider = getComputationInput(inputs[i]);
                if (provider != null && provider.canBridge(seen)) return true;
            }
            return false;
        }

        @Override
        public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
            if (seen.contains(this)) return false;
            seen.add(this);
            for (int i = 0; i < inputCount; i++) {
                IDataAccessHatch hatch = getDataInput(inputs[i]);
                if (hatch != null && !seen.contains(hatch) && hatch.isRecipeAvailable(recipe, seen)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    @Nullable
    private IOpticalComputationProvider getComputationInput(@NotNull EnumFacing facing) {
        TileEntity tile = getNeighborTile(facing);
        if (tile == null) return null;
        return tile.getCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, facing.getOpposite());
    }

    @Nullable
    private IDataAccessHatch getDataInput(@NotNull EnumFacing facing) {
        TileEntity tile = getNeighborTile(facing);
        if (tile == null) return null;
        return tile.getCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, facing.getOpposite());
    }

    /** Whether this relay currently has both something to receive and somewhere to send it. */
    public boolean isForwarding() {
        return inputCount > 0 && outputCount > 0;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.COMPUTER_CASING;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.DATA_BANK_OVERLAY;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), isActive(),
                isActive());
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(true, isActive())
                .setWorkingStatusKeys("gregtech.multiblock.idling", "gregtech.multiblock.idling",
                        "gregtech.machine.optical_repeater.forwarding")
                .addCustom((list, syncer) -> {
                    if (isStructureFormed()) {
                        list.add(KeyUtil.lang(TextFormatting.GREEN, "gregtech.machine.optical_repeater.channels",
                                inputCount, outputCount));
                    }
                })
                .addWorkingStatusLine();
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    public boolean isActive() {
        return super.isActive() && isForwarding();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER) {
            return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER.cast(forwarder);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_DATA_ACCESS) {
            return GregtechTileCapabilities.CAPABILITY_DATA_ACCESS.cast(forwarder);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.optical_repeater.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.optical_repeater.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.optical_repeater.tooltip.3"));
    }
}
