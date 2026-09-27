package gregtech.api.mui.factory;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MetaTileEntityGuiFactory extends AbstractUIFactory<PosGuiData> {

    public static final MetaTileEntityGuiFactory INSTANCE = new MetaTileEntityGuiFactory();

    private MetaTileEntityGuiFactory() {
        super("gregtech:mte");
    }

    public static <T extends MetaTileEntity & IGuiHolder<PosGuiData>> void open(EntityPlayer player, T mte) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(mte);
        if (!mte.isValid()) {
            throw new IllegalArgumentException("Can't open invalid MetaTileEntity GUI!");
        }
        if (player.world != mte.getWorld()) {
            throw new IllegalArgumentException("MetaTileEntity must be in same dimension as the player!");
        }
        BlockPos pos = mte.getPos();
        PosGuiData data = new PosGuiData(player, pos.getX(), pos.getY(), pos.getZ());
        GuiManager.open(INSTANCE, data, (EntityPlayerMP) player);
    }

    @Override
    public ModularPanel createPanel(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = super.createPanel(guiData, syncManager, settings);
        // 覆盖板的入口按钮必须在**两端**都构建：initCoverLeisureUI 内部会用 syncManager
        // 注册子面板，服务端不注册就会导致客户端发来的同步包找不到处理器
        // （日志表现：SyncHandler 'xxx' does not exist for panel 'yyy'）。
        // 本方法是 IGuiHolder.buildUI 的唯一调用点，且服务端/客户端都会走到，
        // 因此是唯一能同时满足"两端都跑"和"拿得到 PanelSyncManager"的时机。
        IGuiHolder<PosGuiData> holder = getGuiHolder(guiData);
        if (holder instanceof MetaTileEntity mte && panel != null) {
            mte.initCoverLeisureUI(guiData, syncManager, panel);
        }
        return panel;
    }

    @Override
    public @NotNull IGuiHolder<PosGuiData> getGuiHolder(PosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te instanceof IGregTechTileEntity gtte) {
            MetaTileEntity mte = gtte.getMetaTileEntity();
            return Objects.requireNonNull(castGuiHolder(mte), "Found MetaTileEntity is not a gui holder!");
        }
        throw new IllegalStateException("Found TileEntity is not a MetaTileEntity!");
    }

    @Override
    public void writeGuiData(PosGuiData guiData, PacketBuffer buffer) {
        buffer.writeVarInt(guiData.getX());
        buffer.writeVarInt(guiData.getY());
        buffer.writeVarInt(guiData.getZ());
    }

    @Override
    public @NotNull PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(player, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
}
