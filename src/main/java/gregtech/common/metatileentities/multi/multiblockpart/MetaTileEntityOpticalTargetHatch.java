package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IFiberLightReceiver;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
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
 * Receives light from a fiber optic line.
 * <p>
 * The hatch tier is its flux rating, the same 1, 4, 16, 64, ... scale as the source hatch, and it
 * can additionally be locked to a single color. Anything it does not accept is rejected at the
 * source, so the line stays dark rather than delivering light this hatch cannot use.
 *
 * @see MetaTileEntityOpticalSourceHatch
 */
public class MetaTileEntityOpticalTargetHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IFiberLightReceiver>, IFiberLightReceiver {

    private final int tier;
    /** The only color this hatch takes, or {@code null} for any color. */
    @Nullable
    private FiberColor filterColor;
    private FiberLight acceptedLight = FiberLight.NONE;

    public MetaTileEntityOpticalTargetHatch(ResourceLocation metaTileEntityId, int tier) {
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

    @Nullable
    @Override
    public FiberColor getAcceptedColor() {
        return filterColor;
    }

    /** Locks this hatch to one color, or clears the filter with {@code null}. */
    public void setFilterColor(@Nullable FiberColor color) {
        this.filterColor = color;
        if (color != null && !acceptedLight.isEmpty() && acceptedLight.color() != color) {
            acceptedLight = FiberLight.NONE;
        }
    }

    @Nullable
    @Override
    public IntensityBounds getIntensityBounds() {
        // a tiered hatch takes anything from a trickle up to its rating
        return IntensityBounds.atMost(getMaxIntensity());
    }

    @Override
    public boolean acceptLight(@NotNull FiberLight light) {
        if (!accepts(light)) {
            acceptedLight = FiberLight.NONE;
            return false;
        }
        acceptedLight = light;
        return true;
    }

    @NotNull
    @Override
    public FiberLight getAcceptedLight() {
        return acceptedLight;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityOpticalTargetHatch(metaTileEntityId, tier);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public MultiblockAbility<IFiberLightReceiver> getAbility() {
        return MultiblockAbility.FIBER_LIGHT_RECEIVER;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            Textures.LASER_TARGET.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (side == getFrontFacing() && capability == GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER) {
            return GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.optical_target_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.optical_target_hatch.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.optical_hatch.max_intensity", getMaxIntensity()));
        if (filterColor != null) {
            tooltip.add(I18n.format("gregtech.machine.optical_target_hatch.filter",
                    I18n.format(filterColor.getName())));
        }
    }
}
