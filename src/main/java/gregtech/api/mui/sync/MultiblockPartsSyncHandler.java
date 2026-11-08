package gregtech.api.mui.sync;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.factory.MetaTileEntityGuiFactory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步多方块控制器的仓室列表，并处理“点击仓室图标打开对应界面”的请求。
 * <p>
 * 仓室列表只在服务端有效（结构成型时由结构提交器填充），所以必须由服务端收集后同步到客户端。
 */
public class MultiblockPartsSyncHandler extends SyncHandler {

    public static final int UPDATE_PARTS = 0;
    public static final int OPEN_PART = 1;

    private final MultiblockControllerBase controller;
    private final List<PartEntry> parts = new ArrayList<>();
    private List<PartEntry> lastSent = new ArrayList<>();
    private int version;
    private int tickCounter;

    public MultiblockPartsSyncHandler(@NotNull MultiblockControllerBase controller) {
        this.controller = controller;
    }

    /** 客户端可读的仓室列表。 */
    public List<PartEntry> getParts() {
        return parts;
    }

    /** 每收到一次新的仓室列表就自增，客户端 widget 据此重建图标网格。 */
    public int getVersion() {
        return version;
    }

    /** 客户端点击仓室图标时调用。 */
    public void requestOpen(@NotNull BlockPos pos) {
        syncToServer(OPEN_PART, buffer -> buffer.writeBlockPos(pos));
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        PanelSyncManager syncManager = getSyncManager();
        if (syncManager == null || syncManager.isClient()) return;
        // 列表内容变化很慢，没必要每 tick 都重新收集一遍仓室和物品图标
        if (!init && (++tickCounter % 10) != 0) return;

        List<PartEntry> current = collectParts();
        if (!init && sameParts(current, lastSent)) return;

        lastSent = current;
        syncToClient(UPDATE_PARTS, buffer -> writeParts(buffer, current));
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id != UPDATE_PARTS) return;

        int size = buf.readVarInt();
        parts.clear();
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            ItemStack stack = NetworkUtils.readItemStack(buf);
            parts.add(new PartEntry(pos, stack == null ? ItemStack.EMPTY : stack));
        }
        version++;
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        if (id != OPEN_PART) return;

        BlockPos pos = buf.readBlockPos();
        PanelSyncManager syncManager = getSyncManager();
        EntityPlayer player = syncManager == null ? null : syncManager.getPlayer();
        if (player == null) return;

        openPart(player, pos);
    }

    private void openPart(@NotNull EntityPlayer player, @NotNull BlockPos pos) {
        for (IMultiblockPart part : controller.getMultiblockParts()) {
            if (!(part instanceof MetaTileEntity mte)) continue;
            if (!mte.getPos().equals(pos) || !mte.isValid()) continue;

            MetaTileEntityGuiFactory.open(player, mte);
            return;
        }
    }

    private List<PartEntry> collectParts() {
        List<PartEntry> list = new ArrayList<>();
        for (IMultiblockPart part : controller.getMultiblockParts()) {
            if (!(part instanceof MetaTileEntity mte) || !mte.isValid()) continue;
            list.add(new PartEntry(mte.getPos(), mte.getStackForm()));
        }
        return list;
    }

    private static boolean sameParts(List<PartEntry> a, List<PartEntry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            PartEntry left = a.get(i);
            PartEntry right = b.get(i);
            if (!left.pos().equals(right.pos())) return false;
            if (!ItemStack.areItemStacksEqual(left.stack(), right.stack())) return false;
        }
        return true;
    }

    private static void writeParts(PacketBuffer buffer, List<PartEntry> parts) {
        buffer.writeVarInt(parts.size());
        for (PartEntry part : parts) {
            buffer.writeBlockPos(part.pos());
            NetworkUtils.writeItemStack(buffer, part.stack());
        }
    }

    public static final class PartEntry {

        private final BlockPos pos;
        private final ItemStack stack;

        public PartEntry(@NotNull BlockPos pos, @NotNull ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }

        public @NotNull BlockPos pos() {
            return pos;
        }

        public @NotNull ItemStack stack() {
            return stack;
        }
    }
}
