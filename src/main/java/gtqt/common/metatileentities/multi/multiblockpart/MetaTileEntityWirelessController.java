package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.IWirelessController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gtqt.api.util.wireless.NetworkManager;
import gtqt.api.util.wireless.NetworkNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public class MetaTileEntityWirelessController extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IWirelessController>, IWirelessController {

    private int priority;

    // ==================== 构造与基础方法 ====================

    public MetaTileEntityWirelessController(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        priority = tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityWirelessController(metaTileEntityId, getTier());
    }

    @Override
    public MultiblockAbility<IWirelessController> getAbility() {
        return MultiblockAbility.WIRELESS_CONTROLLER;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("安装在已成形的PSS上后，该PSS将成为无线网络的中枢节点。"));
        tooltip.add(I18n.format("网络内的所有无线能源仓（输入模式）和动力仓（输出模式）将直接与绑定的PSS交互，"));
        tooltip.add(I18n.format("能量在各个PSS的物理缓存之间流动，多个PSS的缓存总和构成网络的总能量。"));
        tooltip.add(I18n.format("FTB同组玩家自动共享同一网络，无需额外操作，放置即自动连入。"));
        tooltip.add(I18n.format("仓的等级决定了能量交互的优先级，等级越高在网络充放电顺序中越优先。"));
        tooltip.add(I18n.format("注意：每个无线代理仓必须安装在已成形且有效的PSS上才能正常工作，多方块被拆除后会自动注销本仓的代理行为。"));
    }
    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @NotNull
    private SimpleOverlayRenderer getOverlay() {
        return Textures.FUSION_TEXTURE;
    }

    // ==================== 网络绑定 ====================

    /**
     * 获取当前仓所属的网络（如果不存在则自动创建）
     */
    private NetworkNode getOrCreateNetwork() {
        World world = this.getWorld();
        if (world == null || world.isRemote) return null;

        UUID ownerId = this.getOwnerGT();
        if (ownerId == null) return null;

        return NetworkManager.INSTANCE.getOrCreateNetwork(world, ownerId, "无线网络");
    }

    /**
     * 将当前仓添加到网络（多方块成形时调用）
     */
    public void sentMTE() {
        NetworkNode node = getOrCreateNetwork();
        if (node != null) {
            node.addNewHatch(this);
        }
    }

    /**
     * 从网络中移除当前仓（多方块拆解时调用）
     */
    public void removeMTE() {
        NetworkNode node = getOrCreateNetwork();
        if (node != null) {
            node.removeHatch(this);
        }
    }

    // ==================== 能量代理 ====================

    public MetaTileEntityPowerSubstation getPSS() {
        if (this.getController() instanceof MetaTileEntityPowerSubstation powerStation && powerStation.isStructureFormed()) {
            return powerStation;
        }
        return null;
    }

    public BigInteger getCapacity() {
        return getPSS() != null ? getPSS().getEnergyBank().getCapacity() : BigInteger.ZERO;
    }

    public BigInteger getStored() {
        return getPSS() != null ? getPSS().getEnergyBank().getStored() : BigInteger.ZERO;
    }

    public long fill(long amount) {
        return getPSS() != null ? getPSS().getEnergyBank().fill(amount) : 0;
    }

    public long drain(long amount) {
        return getPSS() != null ? getPSS().getEnergyBank().drain(amount) : 0;
    }

    // ==================== 优先级 ====================

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ==================== 接口实现（IWirelessController）====================

    @Override
    public MetaTileEntityPowerSubstation.PowerStationEnergyBank getEnergyBank() {
        MetaTileEntityPowerSubstation pss = getPSS();
        return pss != null ? pss.getEnergyBank() : null;
    }

    @Override
    public void setEnergyBank(MetaTileEntityPowerSubstation.PowerStationEnergyBank energyBank) {
        MetaTileEntityPowerSubstation pss = getPSS();
        if (pss != null) {
            pss.setEnergyBank(energyBank);
        }
    }

    // ==================== 数据持久化 ====================

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        data.setInteger("priority", this.priority);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.priority);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.priority = buf.readInt();
    }
}
