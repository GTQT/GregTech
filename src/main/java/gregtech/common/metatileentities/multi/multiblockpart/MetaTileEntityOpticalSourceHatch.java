package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IFiberLightReceiver;
import gregtech.api.capability.IFiberLightSource;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;
import gregtech.common.pipelike.fiber.FiberLightCommand;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Emits light onto a fiber optic line.
 * <p>
 * The hatch carries no light setting of its own: the multiblock controller pushes a color and a
 * requested flux with {@link #receiveLightCommand}, and the hatch is what actually decides what goes
 * out. Its tier is the hard ceiling — a request above {@code 4^(t - LV)} is clamped down to the
 * rating, and a request of zero turns the line off. Without any controller command the hatch emits
 * nothing.
 * <p>
 * Light only leaves the hatch when the machine on the far end accepts it, so a color or flux
 * mismatch leaves the line dark instead of silently pushing something the target cannot use.
 */
public class MetaTileEntityOpticalSourceHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IFiberLightSource>, IFiberLightSource {

    private final int tier;
    /** Color most recently pushed by the controller. */
    private FiberColor commandedColor = FiberColor.WHITE;
    /** Flux most recently applied, already clamped to this hatch's rating. */
    private long intensity;

    public MetaTileEntityOpticalSourceHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.tier = tier;
    }

    /** The highest flux this hatch can push. */
    public long getMaxIntensity() {
        return (long) Math.pow(4, tier - 1);
    }

    public int getTier() {
        return tier;
    }

    /** Current flux after clamping. */
    public long getIntensity() {
        return intensity;
    }

    /** Color most recently commanded. */
    @NotNull
    public FiberColor getCommandedColor() {
        return commandedColor;
    }

    /**
     * Applies a controller command: the color is taken as given and the requested flux is clamped
     * to this hatch's tier rating.
     */
    @Override
    public boolean receiveLightCommand(@NotNull FiberLightCommand command) {
        this.commandedColor = command.color();
        this.intensity = Math.max(0L, Math.min(command.intensity(), getMaxIntensity()));
        return true;
    }

    /** Turns the hatch off without changing the commanded colour. */
    public void clearLightCommand() {
        this.intensity = 0L;
    }

    @NotNull
    @Override
    public FiberLight getEmittedLight() {
        FiberLight light = new FiberLight(commandedColor, intensity);
        if (light.isEmpty()) return FiberLight.NONE;

        // only light the line up when the far end will actually take it
        IFiberLightReceiver receiver = getFarReceiver();
        return receiver != null && receiver.accepts(light) ? light : FiberLight.NONE;
    }

    @Nullable
    private IFiberLightReceiver getFarReceiver() {
        TileEntity tile = getNeighbor(getFrontFacing());
        if (tile == null) return null;
        return tile.getCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER,
                getFrontFacing().getOpposite());
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityOpticalSourceHatch(metaTileEntityId, tier);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public MultiblockAbility<IFiberLightSource> getAbility() {
        return MultiblockAbility.FIBER_LIGHT_SOURCE;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            Textures.LASER_SOURCE.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (side == getFrontFacing() && capability == GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE) {
            return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.optical_source_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.optical_source_hatch.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.optical_source_hatch.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.optical_hatch.max_intensity", getMaxIntensity()));
    }
}
