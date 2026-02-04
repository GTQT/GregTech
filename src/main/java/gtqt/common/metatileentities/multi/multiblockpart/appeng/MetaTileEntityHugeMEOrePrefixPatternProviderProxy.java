package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.factory.MetaTileEntityGuiFactory;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockNotifiablePart;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.util.text.TextFormatting.GREEN;

public class MetaTileEntityHugeMEOrePrefixPatternProviderProxy extends MetaTileEntityMultiblockNotifiablePart
        implements IMultiblockAbilityPart<IItemHandlerModifiable>,
                   IDataStickIntractable {

    private MetaTileEntityHugeMEOrePrefixPatternProvider main;
    private BlockPos mainPos;
    private boolean checkForMain = true;

    public MetaTileEntityHugeMEOrePrefixPatternProviderProxy(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 6, false);
    }

    private void tryToSetMain() {
        if (getWorld() == null || mainPos == null) return;

        TileEntity tileEntity = getWorld().getTileEntity(mainPos);
        if (!(tileEntity instanceof IGregTechTileEntity iGregTechTileEntity)) {
            this.checkForMain = true;
            return;
        }

        MetaTileEntity metaTileEntity = iGregTechTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MetaTileEntityHugeMEOrePrefixPatternProvider budgetCRIB)) {
            this.checkForMain = true;
            return;
        }

        this.main = budgetCRIB;
        this.checkForMain = false;

        MultiblockControllerBase controllerBase = getController();
        if (controllerBase != null) {
            addNotifiedInput(getMain().getImportItems());
        }
    }

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        DualHandler dualHandler;
        if (getMain() == null) {
            dualHandler = new DualHandler(this.getImportItems(), this.getImportFluids(), false);
        } else dualHandler = getMain().getDualHandler();
        abilityInstances.add(dualHandler);
    }

    private MetaTileEntityHugeMEOrePrefixPatternProvider getMain() {
        return main;
    }

    public boolean hasMain() {
        return main != null && main.isValid();
    }

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {}

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null || !tag.hasKey("BudgetCRIB")) return false;

        readLocationFromTag(tag.getCompoundTag("BudgetCRIB"));
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.budget_crib_proxy.data_stick_use",
                TextFormattingUtil.formatNumbers(mainPos.getX()),
                TextFormattingUtil.formatNumbers(mainPos.getY()),
                TextFormattingUtil.formatNumbers(mainPos.getZ())), true);

        tryToSetMain();

        return true;
    }

    private void readLocationFromTag(NBTTagCompound tag) {
        this.mainPos = new BlockPos(tag.getInteger("MainX"), tag.getInteger("MainY"), tag.getInteger("MainZ"));
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        if (data.getBoolean("HasMain")) {
            readLocationFromTag(data);
        }

        tryToSetMain();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        if (hasMain()) {
            data.setBoolean("HasMain", true);
            data.setInteger("MainX", mainPos.getX());
            data.setInteger("MainY", mainPos.getY());
            data.setInteger("MainZ", mainPos.getZ());
        } else {
            data.setBoolean("HasMain", false);
        }

        return super.writeToNBT(data);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_PROXY_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);

        if (main != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(mainPos);
        } else {
            buf.writeBoolean(false);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        if (buf.readBoolean()) {
            mainPos = buf.readBlockPos();

            tryToSetMain();
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHugeMEOrePrefixPatternProviderProxy(metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();

        if (!getWorld().isRemote && getOffsetTimer() % 100 == 0) {
            if (checkForMain && !hasMain()) tryToSetMain();
        }
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return getMain() == null ? super.getImportItems() : getMain().getImportItems();
    }

    public FluidTankList getImportFluids() {
        return getMain() == null ? super.getImportFluids() : getMain().getImportFluids();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability.equals(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getMain().getImportItems());
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(getMain());
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return getMain() != null;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {

        if (!playerIn.isSneaking() && openGUIOnRightClick()) {
            if (getWorld() != null && !getWorld().isRemote) {
                if (usesMui2()) {
                    MetaTileEntityGuiFactory.open(playerIn, getMain());
                }
            }
            return true;
        }
        return super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(GREEN + I18n.format("gtqt.machine.me_pattern_proxy.tooltip.function"));
        tooltip.add(I18n.format("gtqt.machine.me_pattern_proxy.tooltip.features"));
        tooltip.add(I18n.format("gtqt.machine.me_pattern_proxy.tooltip.usage"));
        tooltip.add(I18n.format("gtqt.machine.me_pattern_proxy.tooltip.requirements"));
    }
}
