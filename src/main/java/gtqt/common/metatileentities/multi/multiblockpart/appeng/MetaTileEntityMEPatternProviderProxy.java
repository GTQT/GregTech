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

public class MetaTileEntityMEPatternProviderProxy extends MetaTileEntityMultiblockNotifiablePart
        implements IMultiblockAbilityPart<IItemHandlerModifiable>,
                   IDataStickIntractable {

    private MetaTileEntityMEPatternProvider main;
    private BlockPos mainPos;
    private boolean checkForMain = true;

    public MetaTileEntityMEPatternProviderProxy(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 6, false);
    }

    private void tryToSetMain() {
        if (getWorld() == null || mainPos == null) {
            unregisterFromMain();
            this.checkForMain = true;
            return;
        }

        TileEntity tileEntity = getWorld().getTileEntity(mainPos);
        if (!(tileEntity instanceof IGregTechTileEntity iGregTechTileEntity)) {
            unregisterFromMain();
            this.checkForMain = true;
            return;
        }

        MetaTileEntity metaTileEntity = iGregTechTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MetaTileEntityMEPatternProvider budgetCRIB)) {
            unregisterFromMain();
            this.checkForMain = true;
            return;
        }

        if (this.main != null && this.main != budgetCRIB) {
            this.main.removeProxy(this);
        }
        this.main = budgetCRIB;
        this.main.addProxy(this);
        this.checkForMain = false;

        MultiblockControllerBase controllerBase = getController();
        if (controllerBase != null) {
            addNotifiedInput(getMain().getImportItems());
        }
    }

    private void unregisterFromMain() {
        if (this.main != null) {
            this.main.removeProxy(this);
            this.main = null;
        }
    }

    /**
     * Called by master when it is being removed from the world.
     */
    public void onMasterRemoved() {
        this.main = null;
        this.checkForMain = true;
    }

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        MetaTileEntityMEPatternProvider resolvedMain = getResolvedMainForLink();
        if (resolvedMain == null) {
            DualHandler dualHandler = new DualHandler(this.getImportItems(), this.getImportFluids(), false);
            abilityInstances.add(dualHandler);
        } else {
            // 委托 master 注册所有缓冲区的 DualHandler
            resolvedMain.registerAbilities(abilityInstances);
        }
    }

    private MetaTileEntityMEPatternProvider getMain() {
        return main;
    }

    @Nullable
    MetaTileEntityMEPatternProvider getResolvedMainForLink() {
        if ((main == null || !main.isValid()) && mainPos != null) {
            tryToSetMain();
        }
        return main != null && main.isValid() ? main : null;
    }

    public boolean hasMain() {
        return getResolvedMainForLink() != null;
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

        if (mainPos != null) {
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
        return new MetaTileEntityMEPatternProviderProxy(metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();

        if (getWorld() != null && getOffsetTimer() % 20 == 0) {
            if (checkForMain && !hasMain()) {
                tryToSetMain();
            }
        }
    }

    @Override
    public void onRemoval() {
        if (this.main != null) {
            this.main.removeProxy(this);
        }
        super.onRemoval();
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        MetaTileEntityMEPatternProvider resolvedMain = getResolvedMainForLink();
        return resolvedMain == null ? super.getImportItems() : resolvedMain.getImportItems();
    }

    public FluidTankList getImportFluids() {
        MetaTileEntityMEPatternProvider resolvedMain = getResolvedMainForLink();
        return resolvedMain == null ? super.getImportFluids() : resolvedMain.getImportFluids();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        MetaTileEntityMEPatternProvider resolvedMain = getResolvedMainForLink();
        if (capability.equals(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)) {
            if (resolvedMain != null) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(resolvedMain.getImportItems());
            }
            return super.getCapability(capability, side);
        }
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            if (resolvedMain != null) {
                return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(resolvedMain);
            }
            return super.getCapability(capability, side);
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return hasMain();
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {

        MetaTileEntityMEPatternProvider resolvedMain = getResolvedMainForLink();
        if (!playerIn.isSneaking() && resolvedMain != null) {
            if (getWorld() != null && !getWorld().isRemote) {
                if (usesMui2()) {
                    MetaTileEntityGuiFactory.open(playerIn, resolvedMain);
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
