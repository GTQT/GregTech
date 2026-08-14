package gregtech.common.metatileentities.multi.multiblockpart.qc;

import gregtech.api.GTValues;
import gregtech.api.capability.IQCComponentHatch;
import gregtech.api.capability.QCComponentRegistry;
import gregtech.api.capability.QCComponentStats;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.inventory.handlers.SingleItemStackHandler;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityQuantumComputer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 量子计算机 (Quantum Computer) 的 Rack 舱。
 * <p>
 * 4 格物品容器（每格限 1 个），仅接受 {@link QCComponentRegistry} 认可的计算组件
 * （注册了 {@code circuit} 矿辞的量子电路）。冷却由控制器的主动水冷系统负责。
 * 运行中或高温时锁定，禁止取放。
 */
public class MetaTileEntityQCComponentRack extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IQCComponentHatch>, IQCComponentHatch {

    private static final int COMPONENT_SLOTS = 4;
    private static final int LOCKOUT_TEMPERATURE = 700;

    private SingleItemStackHandler componentInventory;

    public MetaTileEntityQCComponentRack(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GTValues.ZPM);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQCComponentRack(metaTileEntityId);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        componentInventory = new SingleItemStackHandler(COMPONENT_SLOTS) {

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                // 只收已注册组件；运行中/高温锁定（服务端权威，GUI 层不做动态禁用）
                return !isLocked() && QCComponentRegistry.isComponent(stack);
            }
        };
        itemInventory = componentInventory;
    }

    @Override
    protected boolean shouldSerializeInventories() {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("ComponentInventory", componentInventory.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        componentInventory.deserializeNBT(data.getCompoundTag("ComponentInventory"));
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        clearInventory(itemBuffer, componentInventory);
    }

    /**
     * 控制器运行中或温度过高时锁定，禁止取放组件。
     */
    private boolean isLocked() {
        if (getController() instanceof MetaTileEntityQuantumComputer qc) {
            return qc.isActive() || qc.getTemperature() >= LOCKOUT_TEMPERATURE;
        }
        return false;
    }

    // region IQCComponentHatch

    @Override
    public List<QCComponentStats> getComputingStats() {
        List<QCComponentStats> stats = new ArrayList<>(COMPONENT_SLOTS);
        for (int i = 0; i < COMPONENT_SLOTS; i++) {
            ItemStack stack = componentInventory.getStackInSlot(i);
            QCComponentStats stat = QCComponentRegistry.get(stack);
            if (stat != null && stat.computation() > 0) {
                stats.add(stat);
            }
        }
        return stats;
    }

    @Override
    public int getSlotCount() {
        return COMPONENT_SLOTS;
    }

    @Override
    public boolean destroyRandomComputingComponent() {
        List<Integer> computingSlots = new ArrayList<>();
        for (int i = 0; i < COMPONENT_SLOTS; i++) {
            QCComponentStats stat = QCComponentRegistry.get(componentInventory.getStackInSlot(i));
            if (stat != null && stat.computation() > 0) {
                computingSlots.add(i);
            }
        }
        if (computingSlots.isEmpty()) {
            return false;
        }
        int slot = computingSlots.get(GTValues.RNG.nextInt(computingSlots.size()));
        componentInventory.setStackInSlot(slot, ItemStack.EMPTY);
        markDirty();
        return true;
    }

    // endregion

    @Override
    public MultiblockAbility<IQCComponentHatch> getAbility() {
        return MultiblockAbility.QC_COMPONENT;
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

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        // 4 个 rack_large（40×40，中心 18×18 空槽）拼成 80×80 四宫格，每个 rack 是一个槽位
        guiSyncManager.registerSlotGroup("qc", 2);

        final int gridLeft = (176 - 80) / 2, gridTop = 18; // 80×80 区域居中
        final int slotOffset = (40 - 18) / 2; // 每个 40×40 格内槽位偏移（11px）

        ModularPanel panel = GTGuis.createPanel(this, 176, 194)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));

        for (int i = 0; i < 4; i++) {
            int cellX = gridLeft + (i % 2) * 40;
            int cellY = gridTop + (i / 2) * 40;
            panel.child(GTGuiTextures.RACK_LARGE.asWidget()
                    .left(cellX).top(cellY)
                    .size(40, 40));
            panel.child(componentSlot(i, cellX + slotOffset, cellY + slotOffset));
        }
        return panel;
    }

    private ItemSlot componentSlot(int index, int left, int top) {
        return new ItemSlot()
                .slot(SyncHandlers.itemSlot(componentInventory, index)
                        .slotGroup("qc")
                        .filter(itemStack -> !isLocked()))
                .background(GTGuiTextures.SLOT)
                .overlay((context, x, y, width, height, widgetTheme) -> {
                    if (isLocked()) {
                        GuiDraw.drawRect(x, y, width, height, 0x80404040);
                    }
                })
                .left(left).top(top);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            var controller = getController();
            if (controller != null && controller.isActive()) {
                Textures.HPCA_ADVANCED_COMPUTATION_ACTIVE_OVERLAY.renderSided(getFrontFacing(), renderState, translation,
                        pipeline);
            } else {
                Textures.HPCA_ADVANCED_COMPUTATION_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.qc.component_rack.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.qc.component_rack.tooltip.2"));
    }
}
