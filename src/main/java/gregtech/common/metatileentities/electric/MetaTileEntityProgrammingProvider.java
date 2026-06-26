package gregtech.common.metatileentities.electric;

import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.ProgrammableCircuit;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可编程提供器方块。 对外通过 IItemHandler capability 暴露虚拟无限库存的可编程电路。 默认提供空白可编程电路和包裹了数字电路 1~32 的可编程电路（共34种）。 玩家可以在 GUI
 * 中放入额外物品，提供器会追加对应的可编程电路。 连接 AE 存储总线后可直接向 ME 网络无限量提供。 当外部向此方块输入可编程电路时，直接销毁。
 */
public class MetaTileEntityProgrammingProvider extends MetaTileEntity implements ITieredMetaTileEntity {

    // 用户自定义配置槽位数无上限限制
    // （仅在 adjustSlots 中保持至少一个空槽位尾部追加逻辑）

    private final int tier;
    // 用户自定义的物品模板列表（存储在 NBT 中）
    private final List<ItemStack> customTemplates = new ArrayList<>();
    // 内部配置槽位处理器（用于 GUI 中放入模板物品）
    private ConfigSlotHandler configSlotHandler;
    // 对外暴露的虚拟无限物品处理器（保持单一实例，AE 存储总线缓存引用）
    private VirtualInfiniteItemHandler virtualItemHandler;

    public MetaTileEntityProgrammingProvider(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId);
        this.tier = tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityProgrammingProvider(metaTileEntityId, tier);
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    // ==================== 默认可编程电路列表 ====================

    /**
     * 构建所有需要提供的可编程电路列表（默认 + 自定义）。
     */
    private List<ItemStack> buildProvidedCircuits() {
        if (!isProgrammableCircuitReady()) {
            return Collections.emptyList();
        }

        List<ItemStack> circuits = new ArrayList<>();

        // 空白可编程电路
        circuits.add(MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1));

        // 包裹数字电路 1~32
        for (int i = 1; i <= IntCircuitIngredient.CIRCUIT_MAX; i++) {
            ItemStack intCircuit = IntCircuitIngredient.getIntegratedCircuit(i);
            ItemStack wrappedCircuit = MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
            ProgrammableCircuit.wrap(intCircuit, wrappedCircuit);
            circuits.add(wrappedCircuit);
        }

        // 用户自定义模板
        for (ItemStack template : customTemplates) {
            if (!template.isEmpty()) {
                ItemStack wrappedCircuit = MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
                ProgrammableCircuit.wrap(template, wrappedCircuit);
                circuits.add(wrappedCircuit);
            }
        }

        return circuits;
    }

    /**
     * 重建虚拟物品处理器内容。 保持同一个 handler 实例不变，只更新内容列表， 避免 AE 存储总线缓存的旧引用失效。
     */
    private void rebuildVirtualHandler() {
        List<ItemStack> circuits = buildProvidedCircuits();
        if (this.virtualItemHandler == null) {
            this.virtualItemHandler = new VirtualInfiniteItemHandler(circuits);
        } else {
            this.virtualItemHandler.updateCircuits(circuits);
        }
    }

    private boolean isProgrammableCircuitReady() {
        return MetaItems.PROGRAMMABLE_CIRCUIT != null;
    }

    private @NotNull VirtualInfiniteItemHandler getVirtualItemHandler() {
        if (virtualItemHandler == null || (virtualItemHandler.isEmpty() && isProgrammableCircuitReady())) {
            rebuildVirtualHandler();
        }
        return virtualItemHandler;
    }

    // ==================== GUI ====================

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        // 确保 configSlotHandler 已初始化
        if (configSlotHandler == null) {
            configSlotHandler = new ConfigSlotHandler(this,
                    customTemplates.size() + ConfigSlotHandler.TRAILING_EMPTY_SLOTS);
            for (int i = 0; i < customTemplates.size(); i++) {
                configSlotHandler.setStackInSlot(i, customTemplates.get(i).copy());
            }
        }

        int slotCount = configSlotHandler.getSlots();
        int columns = 9;
        int rows = (slotCount + columns - 1) / columns;

        guiSyncManager.registerSlotGroup("config_slots", columns);

        List<List<IWidget>> slotWidgets = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            List<IWidget> rowWidgets = new ArrayList<>();
            for (int x = 0; x < columns; x++) {
                int index = y * columns + x;
                if (index < slotCount) {
                    rowWidgets.add(new ItemSlot()
                            .slot(SyncHandlers.itemSlot(configSlotHandler, index)
                                    .slotGroup("config_slots")
                                    .accessibility(true, true))
                            .background(GTGuiTextures.SLOT));
                }
            }
            if (!rowWidgets.isEmpty()) {
                slotWidgets.add(rowWidgets);
            }
        }

        return GTGuis.createPanel(this, 176, 200)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                // 提示信息
                .child(IKey.str(I18n.format("gregtech.machine.programming_provider.hint"))
                        .asWidget().pos(5, 16))
                // 配置槽位（可滚动）
                .child(new Grid()
                        .scrollable(new VerticalScrollData())
                        .top(28)
                        .width(18 * columns + 4)
                        .height(18 * 4)
                        .leftRel(0.5f)
                        .matrix(slotWidgets))
                .bindPlayerInventory();
    }

    // ==================== Capability ====================

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getVirtualItemHandler());
        }
        return super.getCapability(capability, side);
    }

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        NBTTagList templateList = new NBTTagList();
        for (ItemStack template : customTemplates) {
            templateList.appendTag(template.writeToNBT(new NBTTagCompound()));
        }
        data.setTag("CustomTemplates", templateList);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        customTemplates.clear();
        NBTTagList templateList = data.getTagList("CustomTemplates", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < templateList.tagCount(); i++) {
            ItemStack stack = new ItemStack(templateList.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                customTemplates.add(stack);
            }
        }
        rebuildVirtualHandler();
        configSlotHandler = null;
    }

    // ==================== 渲染 ====================

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[tier].getParticleSprite(), this.getPaintingColorForRendering());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        Textures.VOLTAGE_CASINGS[tier].render(renderState, translation, ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering()))));
        for (EnumFacing facing : EnumFacing.VALUES) {
            Textures.PIPE_OUT_OVERLAY.renderSided(facing, renderState, translation, pipeline);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.programming_provider.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.programming_provider.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.programming_provider.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.programming_provider.tooltip.4"));
    }

    /**
     * 当用户在 GUI 中修改配置槽位时调用。 重新同步 customTemplates 列表并重建虚拟处理器。
     */
    void onConfigChanged() {
        customTemplates.clear();
        if (configSlotHandler != null) {
            for (int i = 0; i < configSlotHandler.getSlots(); i++) {
                ItemStack stack = configSlotHandler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ItemStack template = stack.copy();
                    template.setCount(1);
                    customTemplates.add(template);
                }
            }
        }
        rebuildVirtualHandler();
        if (getWorld() != null && !getWorld().isRemote) {
            markDirty();
            // 通知邻居方块（AE 存储总线）内容变更，使其重新读取 IItemHandler
            notifyBlockUpdate();
        }
    }

    // ==================== 内部类：配置槽位处理器 ====================

    /**
     * GUI 中用于放入模板物品的可变长度槽位处理器。 放入物品后自动扩展一个空槽位，取出后自动收缩。 每个槽位只保留 1 个物品作为模板。
     */
    static class ConfigSlotHandler extends ItemStackHandler {

        /** 尾部始终保留的空槽位数量 */
        private static final int TRAILING_EMPTY_SLOTS = 8;
        private final MetaTileEntityProgrammingProvider provider;

        ConfigSlotHandler(MetaTileEntityProgrammingProvider provider, int size) {
            // ItemStackHandler(int) uses NonNullList.withSize(), which is fixed-size and
            // throws UnsupportedOperationException on add/remove.
            // This handler needs dynamic slot growth/shrink, so use a mutable backing list.
            super(0);
            this.provider = provider;
            this.stacks = NonNullList.create();
            for (int i = 0; i < Math.max(1, size); i++) {
                this.stacks.add(ItemStack.EMPTY);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            adjustSlots();
            provider.onConfigChanged();
        }

        /**
         * 动态调整槽位数量： - 确保尾部始终有 {@link #TRAILING_EMPTY_SLOTS} 个空槽位 - 移除多余的空槽位，补充不足的空槽位
         */
        private void adjustSlots() {
            // 找到最后一个非空槽位的 index
            int lastNonEmpty = -1;
            for (int i = stacks.size() - 1; i >= 0; i--) {
                if (!stacks.get(i).isEmpty()) {
                    lastNonEmpty = i;
                    break;
                }
            }

            // 期望的总槽位数 = 最后一个非空物品 + 1 + TRAILING_EMPTY_SLOTS
            int desiredSize = lastNonEmpty + 1 + TRAILING_EMPTY_SLOTS;

            // 收缩多余的空槽位
            while (stacks.size() > desiredSize) {
                stacks.remove(stacks.size() - 1);
            }

            // 扩展不足的空槽位
            while (stacks.size() < desiredSize) {
                stacks.add(ItemStack.EMPTY);
            }
        }
    }

    // ==================== 内部类：虚拟无限物品处理器 ====================

    /**
     * 对外暴露的虚拟 IItemHandler。 每个槽位返回对应的可编程电路，数量为 Integer.MAX_VALUE（约 2.1G）模拟无限。 提取物品时返回副本，不修改内部状态。
     * 插入物品时（insertItem）直接吞噬（返回 EMPTY 表示接受成功），实现可编程电路销毁。
     * <p>
     * 保持单一实例，通过 {@link #updateCircuits(List)} 原地更新内容， 避免 AE 存储总线缓存的旧引用失效。
     */
    static class VirtualInfiniteItemHandler implements IItemHandler {

        private List<ItemStack> providedCircuits;

        VirtualInfiniteItemHandler(List<ItemStack> providedCircuits) {
            this.providedCircuits = providedCircuits;
        }

        /** 原地更新内容列表（不创建新实例） */
        void updateCircuits(List<ItemStack> newCircuits) {
            this.providedCircuits = newCircuits;
        }

        public boolean isEmpty() {
            return this.providedCircuits.isEmpty();
        }

        @Override
        public int getSlots() {
            return providedCircuits.size();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= providedCircuits.size()) return ItemStack.EMPTY;
            ItemStack stack = providedCircuits.get(slot).copy();
            stack.setCount(Integer.MAX_VALUE);
            return stack;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // 接受所有可编程电路输入并直接销毁（返回 EMPTY 表示全部被吞噬）
            if (ProgrammableCircuit.getInstanceFor(stack) != null) {
                return ItemStack.EMPTY;
            }
            // 非可编程电路不接受
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= providedCircuits.size()) return ItemStack.EMPTY;
            ItemStack template = providedCircuits.get(slot);
            if (template.isEmpty()) return ItemStack.EMPTY;

            // 无限供应：返回副本，不修改内部状态
            ItemStack extracted = template.copy();
            extracted.setCount(Math.min(amount, Integer.MAX_VALUE));
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }
    }
}
