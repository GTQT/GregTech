package gregtech.common.metatileentities.multi.multiblockpart.qc;

import gregtech.api.GTValues;
import gregtech.api.capability.IQCUncertaintyHatch;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
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
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 量子计算机 (Quantum Computer) 的不确定性舱 —— GT5U 式 4×4 矩阵平衡小游戏。
 * <p>
 * 舱内维护 16 格数值（每格 0~16，不变）。玩家在 GUI 中点击格子：首次点击选中，
 * 再次点击与选中格交换数值，将对称位置调至接近即配平。3×3 焦点指示器显示
 * 各焦点的配平状态（激活且配平 = 绿，激活未配平 = 红，未激活 = 暗色）。
 * 按模式（1~5，结构层数决定）检查不同对称区域的平衡，每处失衡置一个 error bit；
 * status == 0 表示解析成功，量子计算机才允许产出算力。
 */
public class MetaTileEntityQCUncertaintyHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IQCUncertaintyHatch>, IQCUncertaintyHatch {

    private static final int MATRIX_SIZE = 16;
    /** 每格数值上限（0~16） */
    private static final int MAX_VALUE = 16;
    private static final byte STATUS_ALL_FAIL = (byte) 0b11111111;
    /** 矩阵状态整体同步（服务端 → 客户端），GUI getter 读客户端本地字段实现实时刷新 */
    private static final int MATRIX_SYNC = 0x53;

    private final short[] matrix = new short[MATRIX_SIZE];
    private byte selection = (byte) -1;
    private byte mode = 0;
    private byte status = STATUS_ALL_FAIL;

    public MetaTileEntityQCUncertaintyHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.UV);
        regenerate();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQCUncertaintyHatch(metaTileEntityId);
    }

    // region 矩阵小游戏逻辑（GT5U 移植）

    public short getMatrixElement(int index) {
        return matrix[index];
    }

    public void setMatrixElement(short element, int index) {
        matrix[index] = element;
        compute();
    }

    public byte getSelection() {
        return selection;
    }

    public void setSelection(byte selection) {
        this.selection = selection;
    }

    public byte getMode() {
        return mode;
    }

    public void setMode(byte mode) {
        this.mode = mode;
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    /** 随机重新生成 16 格数值（0~MAX_VALUE）。 */
    public void regenerate() {
        for (int i = 0; i < MATRIX_SIZE; i++) {
            matrix[i] = (short) GTValues.RNG.nextInt(MAX_VALUE + 1);
        }
    }

    /**
     * 按当前模式检查矩阵对称平衡，返回 error bits（0 = 完全平衡）。
     */
    public byte compute() {
        int result = 0;
        switch (mode) {
            case 1: // ooo oxo ooo —— 全矩阵水平对称
                result = balanceCheck(matrix) ? 0 : 1;
                break;
            case 2: // ooo xox ooo —— 上下两半各自水平对称
                result += balanceCheck(
                        matrix[0], matrix[4], matrix[1], matrix[5],
                        matrix[2], matrix[6], matrix[3], matrix[7]) ? 0 : 1;
                result += balanceCheck(
                        matrix[8], matrix[12], matrix[9], matrix[13],
                        matrix[10], matrix[14], matrix[11], matrix[15]) ? 0 : 2;
                break;
            case 3: // oxo xox oxo —— 外环四段
                result += balanceCheck(
                        matrix[0], matrix[4], matrix[8], matrix[12],
                        matrix[1], matrix[5], matrix[9], matrix[13]) ? 0 : 1;
                result += balanceCheck(
                        matrix[0], matrix[4], matrix[1], matrix[5],
                        matrix[2], matrix[6], matrix[3], matrix[7]) ? 0 : 2;
                result += balanceCheck(
                        matrix[8], matrix[12], matrix[9], matrix[13],
                        matrix[10], matrix[14], matrix[11], matrix[15]) ? 0 : 4;
                result += balanceCheck(
                        matrix[2], matrix[6], matrix[10], matrix[14],
                        matrix[3], matrix[7], matrix[11], matrix[15]) ? 0 : 8;
                break;
            case 4: // xox ooo xox —— 四角象限
                result += balanceCheck(matrix[0], matrix[4], matrix[1], matrix[5]) ? 0 : 1;
                result += balanceCheck(matrix[8], matrix[12], matrix[9], matrix[13]) ? 0 : 2;
                result += balanceCheck(matrix[2], matrix[6], matrix[3], matrix[7]) ? 0 : 4;
                result += balanceCheck(matrix[10], matrix[14], matrix[11], matrix[15]) ? 0 : 8;
                break;
            case 5: // xox oxo xox —— 全部区域
                result += balanceCheck(matrix[0], matrix[4], matrix[1], matrix[5]) ? 0 : 1;
                result += balanceCheck(matrix[8], matrix[12], matrix[9], matrix[13]) ? 0 : 2;
                result += balanceCheck(matrix) ? 0 : 4;
                result += balanceCheck(matrix[2], matrix[6], matrix[3], matrix[7]) ? 0 : 8;
                result += balanceCheck(matrix[10], matrix[14], matrix[11], matrix[15]) ? 0 : 16;
                break;
        }
        return status = (byte) result;
    }

    /**
     * 对称位置两两比较，差值总和小则平衡。
     * 阈值按值域（0~16）等比缩放：GT5U 原版值域 1000 时每对阈值 128，即每对允许差约 1/8 值域。
     */
    private static boolean balanceCheck(short... masses) {
        float inequality = 0;
        for (int i = 0; i < masses.length >> 1; i++) {
            inequality += Math.abs(masses[i] - masses[masses.length - i - 1]);
        }
        return inequality < masses.length * 2;
    }

    /**
     * 切换解析模式：矩阵重新生成并立即重算。
     */
    public byte update(int newMode) {
        if (newMode == mode) {
            return status;
        }
        if (newMode < 1 || newMode > 5) {
            newMode = 0;
        }
        mode = (byte) newMode;
        regenerate();
        compute();
        return status;
    }

    // endregion

    // region IQCUncertaintyHatch

    @Override
    public int getUncertaintyMode() {
        return mode;
    }

    @Override
    public void updateUncertaintyMode(int mode) {
        update(mode);
        markDirty();
        syncMatrix();
    }

    @Override
    public boolean isResolved() {
        return status == 0;
    }

    // endregion

    /** 服务端 → 客户端推送完整矩阵状态（GUI 实时刷新依赖此通道）。 */
    private void syncMatrix() {
        if (getWorld() == null || getWorld().isRemote) return;
        writeCustomData(MATRIX_SYNC, buf -> {
            for (int i = 0; i < MATRIX_SIZE; i++) {
                buf.writeShort(matrix[i]);
            }
            buf.writeByte(selection);
            buf.writeByte(mode);
            buf.writeByte(status);
        });
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == MATRIX_SYNC) {
            for (int i = 0; i < MATRIX_SIZE; i++) {
                matrix[i] = buf.readShort();
            }
            selection = buf.readByte();
            mode = buf.readByte();
            status = buf.readByte();
            scheduleRenderUpdate();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setByte("Selection", selection);
        data.setByte("Mode", mode);
        data.setByte("Status", status);
        for (int i = 0; i < MATRIX_SIZE; i++) {
            data.setShort("Matrix" + i, matrix[i]);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        selection = data.getByte("Selection");
        mode = data.getByte("Mode");
        status = data.getByte("Status");
        for (int i = 0; i < MATRIX_SIZE; i++) {
            matrix[i] = data.getShort("Matrix" + i);
        }
    }

    @Override
    public MultiblockAbility<IQCUncertaintyHatch> getAbility() {
        return MultiblockAbility.QC_UNCERTAINTY;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public boolean canPartShare() {
        return false;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    // region GUI —— 16 格点击交换 + 状态显示

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        // 服务端权威同步（S2C）：matrix / selection / mode / status 均只读展示
        IntSyncValue[] matrixSyncers = new IntSyncValue[MATRIX_SIZE];
        for (int i = 0; i < MATRIX_SIZE; i++) {
            final int index = i;
            matrixSyncers[i] = new IntSyncValue(() -> (int) getMatrixElement(index),
                    val -> setMatrixElement((short) val, index));
            panelSyncManager.syncValue("matrix", i, matrixSyncers[i]);
        }
        IntSyncValue selectionSyncer = new IntSyncValue(() -> (int) getSelection(), val -> setSelection((byte) val));
        panelSyncManager.syncValue("selection", selectionSyncer);
        IntSyncValue modeSyncer = new IntSyncValue(() -> (int) getMode(), val -> setMode((byte) val));
        panelSyncManager.syncValue("mode", modeSyncer);
        IntSyncValue statusSyncer = new IntSyncValue(() -> (int) getStatus(), val -> setStatus((byte) val));
        panelSyncManager.syncValue("status", statusSyncer);

        // 点击格子：客户端发服务端 action，服务端执行选中/交换并重算（GT5U 同款交互）
        panelSyncManager.registerServerSyncedAction("qc_cell_click", packet -> {
            int index = packet.readInt();
            byte sel = selection;
            if (sel == -1) {
                selection = (byte) index;
            } else {
                short a = matrix[sel];
                matrix[sel] = matrix[index];
                matrix[index] = a;
                selection = (byte) -1;
                compute();
            }
            markDirty();
            syncMatrix();
        });

        // ==== NH (GT5U) 布局：中间屏幕 + 左右按钮列 ====
        // 屏幕用 GT 自带 DISPLAY 纹理（143×75 自适应），内含状态文字、4×4 亮度点阵、3×3 焦点灯
        ParentWidget screen = new ParentWidget<>()
                .pos(42, 20)
                .size(90, 72)
                .background(GTGuiTextures.DISPLAY);

        // 状态与模式文字（屏幕左上）
        screen.child(IKey.dynamic(() -> statusSyncer.getIntValue() == 0
                ? I18n.format("gregtech.machine.qc.uncertainty_hatch.status.resolved")
                : I18n.format("gregtech.machine.qc.uncertainty_hatch.status.unresolved"))
                .asWidget()
                .pos(3, 2));
        screen.child(IKey.dynamic(() -> I18n.format("gregtech.machine.qc.uncertainty_hatch.mode",
                modeSyncer.getIntValue()))
                .asWidget()
                .pos(3, 11));

        // 4×4 亮度点阵（46×46 区域，8px 点、9px 步进，起点 (5, 22)）
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                final int index = row * 4 + col;
                final IntSyncValue matrixSyncer = matrixSyncers[index];
                screen.child(new Widget<>()
                        .pos(5 + col * 9, 22 + row * 9)
                        .size(8, 8)
                        .background(new DynamicDrawable(() -> new Rectangle()
                                .color(cellDotColor(matrixSyncer, selectionSyncer, index)).asIcon()))
                        .tooltip(t -> t.setAutoUpdate(true))
                        .tooltipBuilder(t -> t.addLine(IKey.lang("gregtech.machine.qc.uncertainty_hatch.cell_value",
                                matrixSyncer.getIntValue()))));
            }
        }

        // 3×3 焦点灯（2×2）：角的边角对准 4 个色块的边角交汇处。
        // 点 (col,row) 占 [5+9col, 12+9col]×[22+9row, 29+9row]，四色块交汇角 = (13+9col, 30+9row)，
        // 灯以交汇角为中心 → 左上 = (11+9col, 28+9row)
        for (int i = 0; i < 9; i++) {
            final int focalIndex = i;
            int fx = i % 3, fy = i / 3;
            screen.child(new Widget<>()
                    .pos(11 + fx * 9, 28 + fy * 9)
                    .size(4, 4)
                    .background(new DynamicDrawable(() -> new Rectangle()
                            .color(focalPointColor(modeSyncer.getIntValue(), statusSyncer.getIntValue(), focalIndex))
                            .asIcon()))
                    .tooltip(t -> t.setAutoUpdate(true))
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gregtech.machine.qc.uncertainty_hatch.focal_tooltip"))));
        }

        // 左右各 2 列按钮（每列 4 个，18px 紧贴与屏幕等高）：点击选中/交换，按钮显示数值
        ModularPanel panel = GTGuis.createPanel(this, 176, 194);
        for (int i = 0; i < MATRIX_SIZE; i++) {
            final int index = i;
            int col = i % 4; // GT5U 列序：列 0/1 在左，列 2/3 在右
            int row = i / 4;
            int x = col < 2 ? 2 + col * 20 : 134 + (col - 2) * 20;
            int y = 20 + row * 18;
            final IntSyncValue matrixSyncer = matrixSyncers[index];
            panel.child(new ButtonWidget<>()
                    .pos(x, y)
                    .size(18, 18)
                    .background(GTGuiTextures.BUTTON)
                    .overlay(IKey.dynamic(() -> String.valueOf(matrixSyncer.getIntValue())))
                    .tooltip(t -> t.setAutoUpdate(true))
                    .tooltipBuilder(t -> t
                            .addLine(IKey.lang("gregtech.machine.qc.uncertainty_hatch.cell_value",
                                    matrixSyncer.getIntValue()))
                            .addLine(IKey.lang("gregtech.machine.qc.uncertainty_hatch.cell_tooltip")))
                    .onMousePressed(mouse -> {
                        panelSyncManager.callSyncedAction("qc_cell_click", buf -> buf.writeInt(index));
                        return true;
                    }));
        }

        return panel
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(screen)
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    /** 点阵颜色：亮度随数值（0 熄灭），选中点黄色高亮（GT5U 选择框）。 */
    private static int cellDotColor(IntSyncValue matrixSyncer, IntSyncValue selectionSyncer, int index) {
        int value = matrixSyncer.getIntValue();
        float brightness = Math.min(1f, value / (float) MAX_VALUE);
        if (selectionSyncer.getIntValue() == index) {
            return Color.rgb(255, 220, 60);
        }
        return Color.rgb((int) (brightness * 51), (int) (brightness * 102), (int) (brightness * 255));
    }

    /**
     * 3×3 焦点灯颜色（GT5U 同款映射）：mode 决定激活的焦点位置，
     * 激活且对应 error bit 为 0（配平）→ 绿；激活未配平 → 红；未激活 → 暗灰。
     */
    private static int focalPointColor(int mode, int status, int index) {
        int bit = 0;
        switch (mode) {
            case 1: // 全矩阵水平对称（中心焦点）
                if (index == 4) bit = 1;
                break;
            case 2: // 上下两半（上中/下中）
                if (index == 3) bit = 1;
                else if (index == 5) bit = 2;
                break;
            case 3: // 外环四段（十字）
                if (index == 1) bit = 1;
                else if (index == 3) bit = 2;
                else if (index == 5) bit = 4;
                else if (index == 7) bit = 8;
                break;
            case 4: // 四角象限
                if (index == 0) bit = 1;
                else if (index == 2) bit = 2;
                else if (index == 6) bit = 4;
                else if (index == 8) bit = 8;
                break;
            case 5: // 四角 + 中心
                if (index == 0) bit = 1;
                else if (index == 2) bit = 2;
                else if (index == 4) bit = 4;
                else if (index == 6) bit = 8;
                else if (index == 8) bit = 16;
                break;
            default:
                break;
        }
        if (bit == 0) {
            return Color.rgb(40, 40, 40); // 未激活焦点
        }
        return (status & bit) == 0 ? Color.rgb(0, 255, 90) : Color.rgb(255, 60, 60);
    }

    // endregion
    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            var controller = getController();
            if (controller != null && controller.isActive()) {
                Textures.QC_UNCERTAINTY_HATCH_ACTIVE_OVERLAY.renderSided(getFrontFacing(), renderState, translation,
                        pipeline);
            } else {
                Textures.QC_UNCERTAINTY_HATCH_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.qc.uncertainty_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.qc.uncertainty_hatch.tooltip.2"));
    }
}
