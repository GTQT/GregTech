package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.drawable.GTObjectDrawable;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputBase;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IRichTextBuilder;
import com.cleanroommc.modularui.utils.serialization.IByteBufDeserializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEMufflerHatch extends MetaTileEntityMEOutputBase<IAEItemStack> implements
                                                                                           IMultiblockAbilityPart<IMufflerHatch>,
                                                                                           ITieredMetaTileEntity,
                                                                                           IMufflerHatch {

    public final static String ITEM_BUFFER_TAG = "ItemBuffer";
    private final int recoveryChance;

    public MetaTileEntityMEMufflerHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, IItemStorageChannel.class);
        this.recoveryChance = Math.min((tier - 1) * 10, 100);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEMufflerHatch(metaTileEntityId, getTier());
    }

    @Override
    public void recoverItemsTable(List<ItemStack> recoveryItems, int parallel) {
        if (calculateChance()) {
            for (ItemStack recoveryItem : recoveryItems) {
                ItemStack itemstack = recoveryItem.copy();
                itemstack.setCount(itemstack.getCount() * parallel);
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemstack);
                internalBuffer.add(aeStack);
            }
        }
    }

    @Override
    protected @NotNull IByteBufDeserializer<IAEItemStack> getDeserializer() {
        return AEItemStack::fromPacket;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addStackLine(@NotNull IRichTextBuilder<?> text,
                                @NotNull IAEItemStack wrappedStack) {
        ItemStack stack = wrappedStack.getDefinition();
        text.add(new GTObjectDrawable(stack, 0)
                .asIcon()
                .asHoverable()
                // Auto update has to be true for "Press CTRL for Advanced Info" to work
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addFromItem(stack)));
        text.space();
        text.addLine(KeyUtil.number(TextFormatting.WHITE, wrappedStack.getStackSize(), "x"));
    }

    @Override
    public void onRemoval() {
        IMEMonitor<IAEItemStack> monitor = getMonitor();
        if (monitor != null) {
            for (IAEItemStack item : this.internalBuffer) {
                monitor.injectItems(item.copy(), Actionable.MODULATE, this.getActionSource());
            }
        }
        super.onRemoval();
    }

    private boolean calculateChance() {
        return recoveryChance >= 100 || recoveryChance > GTValues.RNG.nextInt(100);
    }

    @Override
    public boolean isFrontFaceFree() {
        return true;
    }

    @Override
    public boolean isMufflerFull() {
        return false;
    }

    @Override
    public boolean mufflerDust() {
        return true;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagList nbtList = new NBTTagList();
        for (IAEItemStack stack : internalBuffer) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            nbtList.appendTag(stackTag);
        }
        data.setTag(ITEM_BUFFER_TAG, nbtList);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        for (NBTBase tag : data.getTagList(ITEM_BUFFER_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tagCompound = (NBTTagCompound) tag;
            internalBuffer.add(AEItemStack.fromNBT(tagCompound));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            Textures.ME_MUFFLER_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public MultiblockAbility<IMufflerHatch> getAbility() {
        return MultiblockAbility.MUFFLER_HATCH;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.muffler_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.muffler.recovery_tooltip", recoveryChance));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
