package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.drawable.GTObjectDrawable;
import gregtech.api.util.FluidTooltipUtil;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputBase;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IRichTextBuilder;
import com.cleanroommc.modularui.utils.serialization.IByteBufDeserializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEGasHatch extends MetaTileEntityMEOutputBase<IAEFluidStack>
        implements IMultiblockAbilityPart<IMufflerHatch>, IMufflerHatch {

    public final static String FLUID_BUFFER_TAG = "FluidBuffer";
    private final int recoveryChance;

    public MetaTileEntityMEGasHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, IFluidStorageChannel.class);
        this.recoveryChance = Math.min((tier - 1) * 10, 100);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEGasHatch(this.metaTileEntityId, getTier());
    }

    @Override
    protected @NotNull IByteBufDeserializer<IAEFluidStack> getDeserializer() {
        return AEFluidStack::fromPacket;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addStackLine(@NotNull IRichTextBuilder<?> text,
                                @NotNull IAEFluidStack wrappedStack) {
        FluidStack stack = wrappedStack.getFluidStack();
        text.add(new GTObjectDrawable(stack, 0)
                .asIcon()
                .asHoverable()
                .tooltip(tooltip -> {
                    tooltip.addLine(KeyUtil.fluid(stack));
                    FluidTooltipUtil.handleFluidTooltip(tooltip, stack);
                }));
        text.space();
        text.addLine(KeyUtil.number(TextFormatting.WHITE, wrappedStack.getStackSize(), "L"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagList nbtList = new NBTTagList();
        for (IAEFluidStack stack : internalBuffer) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            nbtList.appendTag(stackTag);
        }
        data.setTag(FLUID_BUFFER_TAG, nbtList);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        for (NBTBase tag : data.getTagList(FLUID_BUFFER_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tagCompound = (NBTTagCompound) tag;
            internalBuffer.add(AEFluidStack.fromNBT(tagCompound));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            if (isOnline()) {
                Textures.ME_OUTPUT_HATCH_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
            } else {
                Textures.ME_OUTPUT_HATCH.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.gas_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_export.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.muffler.recovery_tooltip", recoveryChance));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
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
    public void recoverItemsTable(List<ItemStack> recoveryItems, int parallel) {

    }

    @Override
    public void recoverFluidsTable(FluidStack recoveryFluids) {
        if (calculateChance()) {
            IAEFluidStack aeStack = AEFluidStack.fromFluidStack(recoveryFluids);
            internalBuffer.add(aeStack);
        }
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
    public boolean mufflerWaste() {
        return true;
    }
}
