package gregtech.common.covers;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.wireless.WirelessChannelUi;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentTranslation;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Tiered cover that bridges a normal energy container to a selected wireless
 * channel. Input covers feed the host; output covers deposit its stored EU.
 */
public final class CoverWirelessEnergy extends CoverBase implements ITickable {

    private static final int TRANSFER_INTERVAL = 20;

    private final int tier;
    private final boolean input;
    private UUID owner;
    private int channelId;

    public CoverWirelessEnergy(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                               @NotNull EnumFacing attachedSide, int tier, boolean input) {
        super(definition, coverableView, attachedSide);
        this.tier = tier;
        this.input = input;
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return coverable.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, side) != null;
    }

    @Override
    public void onAttachment(@NotNull CoverableView coverableView, @NotNull EnumFacing side, EntityPlayer player,
                             @NotNull ItemStack itemStack) {
        if (player != null) owner = player.getUniqueID();
    }

    @Override
    public void update() {
        if (getWorld().isRemote || getOffsetTimer() % TRANSFER_INTERVAL != 0 || owner == null) return;
        IEnergyContainer container = getCoverableView().getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                getAttachedSide());
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (container == null || service == null) return;
        int effectiveChannel = service.getView(owner, channelId).isEmpty() ? 0 : channelId;
        service.updateEndpoint(owner, effectiveChannel, "cover:" + getPos().toLong() + ':' + getAttachedSide(),
                input ? "cover_input" : "cover_output", getWorld().provider.getDimension(), getPos().toLong(), true,
                false, getWorld().getTotalWorldTime());
        long voltage = GTValues.V[tier];
        long maxAmperage = Math.max(1, input ? container.getInputAmperage() : container.getOutputAmperage());
        long maxTransfer = saturatingMultiply(voltage, maxAmperage);

        if (input && container.inputsEnergy(getAttachedSide())) {
            long available = service.getView(owner, effectiveChannel).getStored()
                    .min(BigInteger.valueOf(maxTransfer)).longValue();
            long accepted = container.acceptEnergyFromNetwork(getAttachedSide(), voltage, available / voltage);
            if (accepted > 0) service.extract(owner, effectiveChannel, saturatingMultiply(voltage, accepted),
                    TransferContext.HATCH);
        } else if (!input && container.outputsEnergy(getAttachedSide())) {
            long transferable = Math.min(container.getEnergyStored(), maxTransfer);
            if (transferable > 0) {
                TransferResult result = service.insert(owner, effectiveChannel, transferable, TransferContext.HATCH);
                if (result.isSuccess()) container.removeEnergy(result.getAmountLong());
            }
        }
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (owner == null) owner = player.getUniqueID();
        channelId = WirelessChannelUi.nextChannelId(owner, channelId);
        markDirty();
        if (!player.world.isRemote) {
            player.sendStatusMessage(new TextComponentTranslation("gregtech.wireless.cover.channel", channelId), true);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.SOLAR_PANEL.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void writeToNBT(@NotNull NBTTagCompound tag) {
        tag.setInteger("channel", channelId);
        if (owner != null) tag.setUniqueId("owner", owner);
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tag) {
        channelId = Math.max(0, tag.getInteger("channel"));
        owner = tag.hasUniqueId("owner") ? tag.getUniqueId("owner") : null;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
