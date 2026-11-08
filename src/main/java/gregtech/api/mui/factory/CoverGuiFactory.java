package gregtech.api.mui.factory;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverHolder;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.widget.SidePanel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class CoverGuiFactory extends AbstractUIFactory<SidedPosGuiData> {

    public static final CoverGuiFactory INSTANCE = new CoverGuiFactory();

    private CoverGuiFactory() {
        super("gregtech:cover");
    }

    public static <T extends Cover & IGuiHolder<SidedPosGuiData>> void open(EntityPlayer player, T cover) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(cover);
        if (!cover.getCoverableView().isValid()) {
            throw new IllegalArgumentException("Can't open Cover GUI on invalid cover holder!");
        }
        if (player.world != cover.getWorld()) {
            throw new IllegalArgumentException("Cover must be in same dimension as the player!");
        }
        BlockPos pos = cover.getPos();
        SidedPosGuiData data = new SidedPosGuiData(player, pos.getX(), pos.getY(), pos.getZ(), cover.getAttachedSide());
        GuiManager.open(INSTANCE, data, (EntityPlayerMP) player);
    }

    @Override
    public @NotNull IGuiHolder<SidedPosGuiData> getGuiHolder(SidedPosGuiData data) {
        TileEntity te = data.getTileEntity();
        if (te == null) {
            throw new IllegalStateException("Could not get gui for null TileEntity!");
        }
        CoverHolder coverHolder = te.getCapability(GregtechTileCapabilities.CAPABILITY_COVER_HOLDER, data.getSide());
        if (coverHolder == null) {
            throw new IllegalStateException("Could not get CoverHolder for found TileEntity!");
        }
        Cover cover = coverHolder.getCoverAtSide(data.getSide());
        if (cover == null) {
            throw new IllegalStateException("Could not find cover at side " + data.getSide() +
                    " for found CoverHolder!");
        }
        if (!(cover instanceof CoverWithUI coverWithUI)) {
            throw new IllegalStateException("Cover at side " + data.getSide() + " is not a gui holder!");
        }
        return coverWithUI;
    }

    @Override
    public void writeGuiData(SidedPosGuiData guiData, PacketBuffer buffer) {
        buffer.writeVarInt(guiData.getX());
        buffer.writeVarInt(guiData.getY());
        buffer.writeVarInt(guiData.getZ());
        buffer.writeByte(guiData.getSide().getIndex());
    }

    @Override
    public @NotNull SidedPosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new SidedPosGuiData(player, buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                EnumFacing.VALUES[buffer.readByte()]);
    }

    @Override
    public ModularPanel createPanel(SidedPosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = super.createPanel(guiData, syncManager, settings);
        if (panel != null) {
            IGuiHolder<SidedPosGuiData> holder = getGuiHolder(guiData);
            if (holder instanceof CoverWithUI cover) {
                attachHostButton(cover, guiData, syncManager, panel);
            }
        }
        return panel;
    }

    /**
     * 在覆盖板界面右侧挂一个“返回宿主”按钮：宿主指覆盖板所安装的方块（机器、仓、管道等）。
     *
     * <p>
     * 客户端点击只发包，服务端重新按覆盖板当前位置解析宿主并打开宿主的界面；解析不到
     * MetaTileEntity（宿主不是 GT 机器）时不添加按钮。
     */
    private static void attachHostButton(@NotNull CoverWithUI cover, @NotNull SidedPosGuiData guiData,
                                         @NotNull PanelSyncManager syncManager, @NotNull ModularPanel panel) {
        MetaTileEntity host = resolveHost(cover);
        if (host == null) return;

        syncManager.registerSyncedAction("open_host", false, true, buffer -> {
            EntityPlayer player = syncManager.getPlayer();
            if (!(player instanceof EntityPlayerMP playerMP)) return;
            MetaTileEntity current = resolveHost(cover);
            if (current == null || !current.isValid()) return;
            if (player.world != current.getWorld()) return;
            MetaTileEntityGuiFactory.open(playerMP, current);
        });

        ItemStack icon = host.getStackForm();
        SidePanel sidePanel = new SidePanel(guiData, syncManager);
        sidePanel.add(new ButtonWidget<>()
                .name("open_host")
                .size(18)
                .overlay(new ItemDrawable(icon).asIcon().size(16))
                .addTooltipLine(IKey.lang("gregtech.mui.cover.open_host"))
                .onMousePressed(button -> {
                    if (button != 0) return false;
                    syncManager.callSyncedAction("open_host");
                    return true;
                }));
        sidePanel.attachTo(panel);
    }

    /** 解析覆盖板的宿主机器；宿主不是 GT 机器时返回 {@code null}。 */
    private static @Nullable MetaTileEntity resolveHost(@NotNull Cover cover) {
        CoverableView view = cover.getCoverableView();
        World world = view.getWorld();
        if (world == null) return null;
        TileEntity te = world.getTileEntity(view.getPos());
        if (te instanceof IGregTechTileEntity gtte) {
            return gtte.getMetaTileEntity();
        }
        return null;
    }
}
