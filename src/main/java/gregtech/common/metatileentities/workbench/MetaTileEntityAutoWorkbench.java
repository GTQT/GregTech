package gregtech.common.metatileentities.workbench;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.DummyContainer;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.RenderUtil;
import gregtech.common.mui.widget.workbench.AutoWorkbenchUI;

import net.minecraft.block.SoundType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_OUTPUT_FACING;

/**
 * 自动工作台 (Auto Workbench) —— 由 GT5-Unofficial 的 {@code MTEElectricAutoWorkbench} 移植。
 * <p>
 * 机器从自己的 18 格缓冲区(槽位 0-17)中自动取出物品, 按当前模式尝试合成, 成品放入 18 号结果槽, 由
 * 输出面自动输出。0 号模式使用 9 个幻影槽位作为合成模板, 其余模式则直接以缓冲区中的单个物品推断配方。
 * <p>
 * 槽位布局(与源实现一一对应):
 *
 * <pre>
 *   0-8    输入/合成网格   (模式 0 与模式 9 直接作为 3x3 合成网格使用)
 *   9-17   输出区          (合成后返还的容器物品, 例如空桶 / 受损工具)
 *   18     结果槽          (成品, 仅可被自动化抽出)
 *   幻影槽 0-8  (源 19-27) 合成模板, 仅模式 0 使用
 *   预览槽 0    (源 28)    当前配方产物预览, 仅用于 GUI 显示
 * </pre>
 *
 * 实现了 {@link IActiveOutputSide}: 物品与能量共用一个可调的输出面(参考 {@code MetaTileEntityBuffer}),
 * 未显式设置前默认为正面的背面。普通扳手点击设置输出面, 潜行扳手旋转正面; 也可以通过世界预览面板里的
 * 方向按钮调整。能量输出面跟随该输出面, 输出面默认只暴露结果槽, 用螺丝刀可以允许从输出面输入。
 */
public class MetaTileEntityAutoWorkbench extends TieredMetaTileEntity implements IActiveOutputSide {

    /** 合成模式数量 */
    public static final int MAX_MODES = 10;
    /** 吞吐量状态数量 */
    public static final int MAX_THROUGHPUT = 4;

    /** 输入/合成网格槽位 */
    public static final int INPUT_SLOT_COUNT = 9;
    /** 输出区(容器物品)起始槽位 */
    public static final int OUTPUT_SLOT_OFFSET = INPUT_SLOT_COUNT;
    /** 输出区槽位数量 */
    public static final int OUTPUT_SLOT_COUNT = 9;
    /** 结果槽 */
    public static final int RESULT_SLOT = OUTPUT_SLOT_OFFSET + OUTPUT_SLOT_COUNT;
    /** 机器真实库存大小(输入 + 输出区 + 结果槽) */
    public static final int BENCH_SLOT_COUNT = RESULT_SLOT + 1;
    /** 幻影合成模板槽位数量 */
    public static final int PHANTOM_SLOT_COUNT = 9;

    /** 流体储罐容量, 用于给缓冲区内的流体容器装罐 */
    public static final int TANK_CAPACITY = 16000;
    /** 空闲时的重试间隔(tick) */
    public static final int UPDATE_INTERVAL = 32;
    /** 普通模式每次合成耗能 */
    public static final long CRAFT_COST = 2048L;
    /** 统一/粉尘/小块模式每次合成耗能 */
    public static final long SIMPLE_CRAFT_COST = 128L;

    /** 合成模式, 0 = 普通工作台 */
    private int mode = 0;
    /** 当前扫描到的缓冲区槽位 */
    private int currentSlot = 0;
    /** 吞吐量状态, 0/1 = 自动输出物品, 偶数 = 允许对外输出能量 */
    private int throughput = 0;
    /** 距离下一次尝试合成的 tick 数 */
    private int ticksUntilNextUpdate = 20;
    /** 上一次合成是否成功 */
    private boolean lastCraftSuccessful = false;

    /** 真实库存: 0-8 输入/合成网格, 9-17 输出区, 18 结果槽 */
    private GTItemStackHandler benchInventory;
    /** 幻影合成模板(模式 0) */
    private GTItemStackHandler templateInventory;
    /** 配方预览(仅显示) */
    private GTItemStackHandler previewInventory;
    /** 用于给流体容器装罐的内部储罐 */
    private FluidTank tank;
    /** 显式设置的输出面; 为 null 时跟随正面的背面 */
    private EnumFacing outputFacing;
    /** 是否允许从输出面输入物品(螺丝刀切换, 参考 SimpleMachineMetaTileEntity) */
    private boolean allowInputFromOutputSide = false;

    // 配方查询缓存: 合成网格内容不变时避免重复遍历全部配方
    private final ItemStack[] cachedRecipeGrid = new ItemStack[9];
    private boolean cachedRecipeValid = false;
    private ItemStack cachedRecipeOutput = ItemStack.EMPTY;

    public MetaTileEntityAutoWorkbench(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        // 内部库存必须在 createImportItemHandler() 中构建, 见该方法说明
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAutoWorkbench(metaTileEntityId, getTier());
    }

    // ==================== 能量 ====================

    /**
     * 机器默认接收能量, 但吞吐量为偶数时也会从输出面输出能量(对应源实现的 maxEUOutput)。
     * 输出面可调整, 见 {@link #getOutputFacing()}。
     */
    @Override
    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        long capacity = Math.max(2048L, tierVoltage * (long) getTier() * tierVoltage);
        EnergyContainerHandler handler = new EnergyContainerHandler(this, capacity, tierVoltage, 1L, tierVoltage, 1L) {

            @Override
            public long getOutputVoltage() {
                return isEnergyOutputEnabled() ? tierVoltage : 0L;
            }
        };
        // 输出面输出能量, 其余面输入
        handler.setSideInputCondition(side -> side != getOutputFacing());
        handler.setSideOutputCondition(side -> side == getOutputFacing());
        this.energyContainer = handler;
    }

    /** 吞吐量为偶数时对外输出能量 */
    public boolean isEnergyOutputEnabled() {
        return throughput % 2 == 0;
    }

    /** 吞吐量小于 2 时自动输出物品 */
    public boolean isItemOutputEnabled() {
        return throughput < 2;
    }

    /** 当前模式每次合成消耗的能量 */
    public long getEnergyCostPerCraft() {
        return mode == 5 || mode == 6 || mode == 7 ? SIMPLE_CRAFT_COST : CRAFT_COST;
    }

    // ==================== IActiveOutputSide: 可调输出面 ====================

    /**
     * @return 输出面; 未显式设置时默认为正面的背面, 永不为 null
     */
    public EnumFacing getOutputFacing() {
        return outputFacing == null ? getFrontFacing().getOpposite() : outputFacing;
    }

    /**
     * 设置输出面。正面不能作为输出面, 因此该面上的点击会被忽略(参考 {@code MetaTileEntityBuffer})。
     */
    public void setOutputFacing(EnumFacing facing) {
        if (facing == null) return;
        if (hasFrontFacing() && facing == getFrontFacing()) return;
        this.outputFacing = facing;
        if (getWorld() != null && !getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_OUTPUT_FACING, buf -> buf.writeByte(facing.getIndex()));
            markDirty();
        }
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        // 正面转到输出面上时, 输出面回到默认位置(正面的背面), 并同步给客户端
        if (outputFacing == null || outputFacing == frontFacing) {
            setOutputFacing(frontFacing.getOpposite());
        }
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    /**
     * 普通扳手点击 = 设置输出面; 潜行扳手 = 交给父类旋转正面。
     */
    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (facing == null || facing == getOutputFacing()) return false;
            if (hasFrontFacing() && facing == getFrontFacing()) return false;
            if (!getWorld().isRemote) {
                setOutputFacing(facing);
            }
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public boolean isAutoOutputItems() {
        return isItemOutputEnabled();
    }

    @Override
    public boolean isAutoOutputFluids() {
        // 内部储罐只用于给容器装罐, 机器不会主动输出流体
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideItems() {
        return allowInputFromOutputSide;
    }

    /**
     * 螺丝刀点击 = 是否允许从输出面输入物品(参考 {@code SimpleMachineMetaTileEntity})。
     * 关闭时输出面只暴露结果槽, 开启后输出面也能把物品送进缓冲区。
     */
    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            boolean allow = !isAllowInputFromOutputSideItems();
            setAllowInputFromOutputSideItems(allow);
            playerIn.sendStatusMessage(new TextComponentTranslation(allow ?
                    "gregtech.machine.basic.input_from_output_side.allow" :
                    "gregtech.machine.basic.input_from_output_side.disallow"), true);
        }
        return true;
    }

    public void setAllowInputFromOutputSideItems(boolean allowInputFromOutputSide) {
        if (this.allowInputFromOutputSide == allowInputFromOutputSide) return;
        this.allowInputFromOutputSide = allowInputFromOutputSide;
        if (getWorld() != null && !getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public boolean isAllowInputFromOutputSideFluids() {
        // 储罐只进不出, 输出面上依旧可以注入流体
        return true;
    }

    /** 物品与流体通道共用同一个输出面 */
    @Override
    public EnumFacing getOutputFacingItems() {
        return getOutputFacing();
    }

    @Override
    public EnumFacing getOutputFacingFluids() {
        return getOutputFacing();
    }

    @Override
    public void setOutputFacingItems(EnumFacing facing) {
        setOutputFacing(facing);
    }

    @Override
    public void setOutputFacingFluids(EnumFacing facing) {
        setOutputFacing(facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE) {
            return side != null && side == getOutputFacing() ?
                    GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE.cast(this) : null;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY &&
                side != null && side == getOutputFacing() && !isAllowInputFromOutputSideItems()) {
            // 输出面是纯输出: 只暴露结果槽
            return exportItems.getSlots() > 0 ?
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(exportItems) : null;
        }
        return super.getCapability(capability, side);
    }

    // ==================== 库存 ====================

    /**
     * 内部库存需要在 {@code createImportItemHandler()} 中创建: 该工厂方法由父类构造函数调用, 此时本类的
     * 字段初始化尚未执行, 因此不能依赖字段初始化器。
     */
    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        if (benchInventory == null) {
            benchInventory = new GTItemStackHandler(this, BENCH_SLOT_COUNT);
            templateInventory = new GTItemStackHandler(this, PHANTOM_SLOT_COUNT);
            previewInventory = new GTItemStackHandler(this, 1);
        }
        return new BenchImportHandler();
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        if (benchInventory == null) {
            benchInventory = new GTItemStackHandler(this, BENCH_SLOT_COUNT);
            templateInventory = new GTItemStackHandler(this, PHANTOM_SLOT_COUNT);
            previewInventory = new GTItemStackHandler(this, 1);
        }
        return new BenchResultHandler();
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        if (tank == null) {
            tank = new FluidTank(TANK_CAPACITY);
        }
        return new FluidTankList(false, tank);
    }

    /** 输入侧: 0-8(模式 0)或 0-17(其余模式)可被自动化插入 */
    private class BenchImportHandler implements IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return OUTPUT_SLOT_OFFSET;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return benchInventory.getStackInSlot(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (mode == 0 ? slot >= INPUT_SLOT_COUNT : slot >= OUTPUT_SLOT_OFFSET) {
                return stack;
            }
            return benchInventory.insertItem(slot, stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // 模式 0 下输出区(9-17)是合成返还物, 允许自动化取出; 其余模式下缓冲区只能被机器自身消耗
            if (mode != 0 || slot < INPUT_SLOT_COUNT) {
                return ItemStack.EMPTY;
            }
            return benchInventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return benchInventory.getSlotLimit(slot);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            benchInventory.setStackInSlot(slot, stack);
        }
    }

    /** 输出侧: 仅结果槽 18 */
    private class BenchResultHandler implements IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return benchInventory.getStackInSlot(RESULT_SLOT);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return benchInventory.extractItem(RESULT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return benchInventory.getSlotLimit(RESULT_SLOT);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            benchInventory.setStackInSlot(RESULT_SLOT, stack);
        }
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        // super 会清空 importItems(0-17)与 exportItems(结果槽)
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, templateInventory);
        clearInventory(itemBuffer, previewInventory);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("Mode", mode);
        data.setInteger("Throughput", throughput);
        data.setInteger("OutputFacing", getOutputFacing().getIndex());
        data.setBoolean("AllowInputFromOutputSide", allowInputFromOutputSide);
        // 幻影模板与预览不属于 import/export, 需要单独持久化
        GTUtility.writeItems(templateInventory, "Templates", data);
        GTUtility.writeItems(previewInventory, "Preview", data);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.mode = Math.floorMod(data.getInteger("Mode"), MAX_MODES);
        this.throughput = Math.floorMod(data.getInteger("Throughput"), MAX_THROUGHPUT);
        if (data.hasKey("OutputFacing")) {
            EnumFacing facing = EnumFacing.VALUES[Math.floorMod(data.getInteger("OutputFacing"),
                    EnumFacing.VALUES.length)];
            // 正面不能作为输出面, 存档异常时退回默认位置
            this.outputFacing = (hasFrontFacing() && facing == getFrontFacing()) ? null : facing;
        }
        this.allowInputFromOutputSide = data.getBoolean("AllowInputFromOutputSide");
        GTUtility.readItems(templateInventory, "Templates", data);
        GTUtility.readItems(previewInventory, "Preview", data);
        this.ticksUntilNextUpdate = 1;
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(getOutputFacing().getIndex());
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.outputFacing = EnumFacing.VALUES[buf.readByte()];
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_OUTPUT_FACING) {
            this.outputFacing = EnumFacing.VALUES[buf.readByte()];
            scheduleRenderUpdate();
        }
    }

    // ==================== 逻辑 ====================

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote) return;

        long energyCost = getEnergyCostPerCraft();
        if (energyContainer.getEnergyStored() >= energyCost && --this.ticksUntilNextUpdate < 1) {
            this.ticksUntilNextUpdate = UPDATE_INTERVAL;
            performWork(energyCost);
        }
    }

    private void performWork(long energyCost) {
        // 结果槽被占用时停止一切操作, 等待物品被抽出
        if (getBenchStack(RESULT_SLOT) == null) {
            applyTemplateDamageWorkaround();
            fillFluidContainers();
            craftOnce(energyCost);
        }
        if (isItemOutputEnabled()) {
            pushItemsIntoNearbyHandlers(getOutputFacing());
        }
    }

    /**
     * 源实现把带容器物品(工具等)的幻影模板耐久设为通配值, 这样模板可以匹配任意耐久的物品。
     */
    private void applyTemplateDamageWorkaround() {
        boolean changed = false;
        for (int i = 0; i < PHANTOM_SLOT_COUNT; i++) {
            ItemStack template = getTemplateStack(i);
            if (template != null && template.isItemStackDamageable() && template.getItem().hasContainerItem(template)) {
                template.setItemDamage(OreDictionary.WILDCARD_VALUE);
                changed = true;
            }
        }
        if (changed) {
            // 原地修改不会触发 onContentsChanged, 手动标记以便保存
            markDirty();
        }
    }

    /**
     * 用内部储罐给缓冲区(0-17)中的流体容器装罐, 装好的容器放入输入网格(0-8)。
     */
    private void fillFluidContainers() {
        for (int i = 0; i < OUTPUT_SLOT_OFFSET; i++) {
            if (tank.getFluid() == null) return;
            ItemStack input = getBenchStack(i);
            if (input == null) continue;

            ItemStack single = GTUtility.copy(1, input);
            IFluidHandlerItem handler = single.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler == null) continue;

            FluidStack fluid = tank.getFluid();
            if (fluid == null) return;
            int filled = handler.fill(fluid.copy(), true);
            if (filled <= 0) continue;

            ItemStack filledContainer = handler.getContainer();
            if (filledContainer.isEmpty()) continue;

            for (int j = 0; j < INPUT_SLOT_COUNT; j++) {
                if (j == i) {
                    // 同一槽位: 仅当只剩 1 个时才可以直接替换, 否则会把剩余物品覆盖掉
                    if (input.getCount() > 1) continue;
                    tank.drain(filled, true);
                    benchInventory.setStackInSlot(i, filledContainer);
                    break;
                }
                ItemStack output = getBenchStack(j);
                if (output == null) {
                    tank.drain(filled, true);
                    shrinkSlot(i, 1);
                    benchInventory.setStackInSlot(j, filledContainer);
                    break;
                } else if (areStacksEqual(filledContainer, output) &&
                        output.getCount() + filledContainer.getCount() <= filledContainer.getMaxStackSize()) {
                            tank.drain(filled, true);
                            shrinkSlot(i, 1);
                            output.grow(filledContainer.getCount());
                            benchInventory.onContentsChanged(j);
                            break;
                        }
            }
        }
    }

    /**
     * 单次合成尝试: 依据模式构造 3x3 配方网格 -> 匹配配方 -> 校验缓冲区材料 -> 消耗材料并输出成品。
     */
    private void craftOnce(long energyCost) {
        ItemStack[] recipe = new ItemStack[9];
        ItemStack tempStack;
        ItemStack output = null;

        if (getBenchStack(17) != null && isItemOutputEnabled() && mode != 0) {
            // 17 号槽位有物品且允许输出时优先把它送到结果槽
            if (getBenchStack(RESULT_SLOT) == null) {
                ejectToResult(17);
            }
        } else {
            if (!lastCraftSuccessful) {
                currentSlot = (currentSlot + 1) % OUTPUT_SLOT_OFFSET;
                for (int i = 0; i < OUTPUT_SLOT_OFFSET - 1 && getBenchStack(currentSlot) == null; i++) {
                    currentSlot = (currentSlot + 1) % OUTPUT_SLOT_OFFSET;
                }
            }
            switch (mode) {
                case 0 -> {
                    System.arraycopy(buildModeZeroRecipe(), 0, recipe, 0, 9);
                    if (isStrayBeingEjectedInModeZero()) {
                        ejectToResult(currentSlot);
                    }
                }
                case 1 -> {
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(getBenchStack(currentSlot))) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(getBenchStack(currentSlot));
                    recipe[0] = tempStack;
                    if (findRecipeOutput(recipe).isEmpty()) {
                        recipe[1] = tempStack;
                        recipe[3] = tempStack;
                        recipe[4] = tempStack;
                    } else break;
                    if (findRecipeOutput(recipe).isEmpty()) {
                        recipe[2] = tempStack;
                        recipe[5] = tempStack;
                        recipe[6] = tempStack;
                        recipe[7] = tempStack;
                        recipe[8] = tempStack;
                    } else break;
                    if (findRecipeOutput(recipe).isEmpty()) {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 2 -> {
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(getBenchStack(currentSlot))) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(getBenchStack(currentSlot));
                    recipe[0] = tempStack;
                    if (findRecipeOutput(recipe).isEmpty()) {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 3 -> {
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(getBenchStack(currentSlot))) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(getBenchStack(currentSlot));
                    recipe[0] = tempStack;
                    recipe[1] = tempStack;
                    recipe[3] = tempStack;
                    recipe[4] = tempStack;
                    if (findRecipeOutput(recipe).isEmpty()) {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 4 -> {
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(getBenchStack(currentSlot))) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(getBenchStack(currentSlot));
                    for (int i = 0; i < 9; i++) {
                        recipe[i] = tempStack;
                    }
                    if (findRecipeOutput(recipe).isEmpty()) {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 5 -> {
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(getBenchStack(currentSlot))) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(getBenchStack(currentSlot));
                    recipe[0] = tempStack;
                    output = OreDictUnifier.getUnificated(tempStack);
                    if (areStacksEqual(output, tempStack)) {
                        output = null;
                    }
                    if (output == null) {
                        recipe[0] = null;
                        ejectIfPossible(currentSlot);
                    }
                }
                case 6 -> {
                    ItemStack current = getBenchStack(currentSlot);
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(current)) {
                        ejectIfPossible(currentSlot);
                    } else if (OreDictUnifier.getPrefix(current) == OrePrefix.dustSmall) {
                        tempStack = singleCopy(current);
                        recipe[0] = tempStack;
                        recipe[1] = tempStack;
                        recipe[3] = tempStack;
                        recipe[4] = tempStack;
                        if (findRecipeOutput(recipe).isEmpty()) {
                            ejectIfPossible(currentSlot);
                        }
                    } else if (OreDictUnifier.getPrefix(current) == OrePrefix.dustTiny) {
                        tempStack = singleCopy(current);
                        for (int i = 0; i < 9; i++) {
                            recipe[i] = tempStack;
                        }
                        if (findRecipeOutput(recipe).isEmpty()) {
                            ejectIfPossible(currentSlot);
                        }
                    } else {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 7 -> {
                    ItemStack current = getBenchStack(currentSlot);
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(current) ||
                            OreDictUnifier.getPrefix(current) != OrePrefix.nugget) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(current);
                    for (int i = 0; i < 9; i++) {
                        recipe[i] = tempStack;
                    }
                    if (findRecipeOutput(recipe).isEmpty()) {
                        ejectIfPossible(currentSlot);
                    }
                }
                case 8 -> {
                    ItemStack current = getBenchStack(currentSlot);
                    if (isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(current) || current.getItemDamage() <= 0 ||
                            !current.getItem().isRepairable()) {
                        ejectIfPossible(currentSlot);
                        break;
                    }
                    tempStack = singleCopy(current);
                    for (int i = currentSlot + 1; i < OUTPUT_SLOT_OFFSET; i++) {
                        ItemStack other = getBenchStack(i);
                        if (other != null && other.getItem() == tempStack.getItem() &&
                                current.getItemDamage() + other.getItemDamage() > tempStack.getMaxDamage()) {
                            recipe[0] = tempStack;
                            recipe[1] = GTUtility.copy(other);
                            if (findRecipeOutput(recipe).isEmpty()) {
                                ejectIfPossible(currentSlot);
                            }
                            break;
                        }
                    }
                }
                case 9 -> System.arraycopy(buildCircleRecipe(), 0, recipe, 0, 9);
                default -> {}
            }
        }

        if (output == null || output.isEmpty()) {
            output = findRecipeOutput(recipe);
        }
        setRecipePreview(output);

        if (output.isEmpty()) {
            lastCraftSuccessful = false;
            return;
        }

        ItemStack unified = OreDictUnifier.getUnificated(output);
        if (!unified.isEmpty()) {
            unified = GTUtility.copy(output.getCount(), unified);
            output = unified;
        }
        setRecipePreview(GTUtility.copy(output));

        ArrayList<ItemStack> required = recipeContent(recipe);
        ArrayList<ItemStack> available = benchContent();
        if (!required.isEmpty() && !available.isEmpty()) {
            boolean success = (mode == 6 || mode == 7 || getBenchStack(17) == null);
            for (int i = 0; i < required.size() && success; i++) {
                success = false;
                for (ItemStack content : available) {
                    if (areStacksEqual(required.get(i), content) &&
                            required.get(i).getCount() <= content.getCount()) {
                        success = true;
                        break;
                    }
                }
            }

            if (success) {
                lastCraftSuccessful = true;
                consumeIngredients(recipe);
                setBenchStack(RESULT_SLOT, GTUtility.copy(output));
                energyContainer.removeEnergy(energyCost);
                this.ticksUntilNextUpdate = 1;
            } else {
                lastCraftSuccessful = false;
                ejectIfPossible(mode == 0 ? 8 : 17);
            }
        }

        // 结果槽空闲时, 把输入网格中重复的整堆物品挪出去, 避免堵塞
        if (getBenchStack(RESULT_SLOT) == null && isItemOutputEnabled()) {
            for (int i = 0; i < INPUT_SLOT_COUNT - 1; i++) {
                ItemStack stack = getBenchStack(i);
                if (stack == null || stack.getMaxStackSize() <= 8) continue;
                boolean moved = false;
                for (int j = i + 1; j < INPUT_SLOT_COUNT; j++) {
                    if (areStacksEqual(stack, getBenchStack(j))) {
                        ejectToResult(j);
                        moved = true;
                        break;
                    }
                }
                // 与源实现不同, 这里直接跳出: 结果槽只能容纳一堆物品, 继续搬运会覆盖并丢失物品
                if (moved) break;
            }
        }
    }

    /**
     * 按配方网格消耗缓冲区材料。容器的返还物(空桶/受损工具)放入输出区(9-17)。
     */
    private void consumeIngredients(ItemStack[] recipe) {
        for (int i = 8; i > -1; i--) {
            for (int j = OUTPUT_SLOT_OFFSET - 1; j > -1; j--) {
                if (recipe[i] == null || getBenchStack(j) == null) continue;
                if (!areStacksEqual(recipe[i], getBenchStack(j))) continue;

                ItemStack container = getContainerItem(getBenchStack(j));
                if (!container.isEmpty()) {
                    shrinkSlot(j, 1);
                    if (!container.isItemStackDamageable() || container.getItemDamage() < container.getMaxDamage()) {
                        for (int k = OUTPUT_SLOT_OFFSET; k < RESULT_SLOT; k++) {
                            ItemStack out = getBenchStack(k);
                            if (out == null) {
                                setBenchStack(k, GTUtility.copy(container));
                                break;
                            } else if (areStacksEqual(out, container) &&
                                    out.getCount() + container.getCount() <= container.getMaxStackSize()) {
                                        out.grow(container.getCount());
                                        benchInventory.onContentsChanged(k);
                                        break;
                                    }
                        }
                    }
                } else {
                    shrinkSlot(j, 1);
                }
                break;
            }
        }
    }

    /**
     * 模式 0 的配方网格来自幻影模板; 若有非模板物品阻塞在输入网格中, 该 tick 内网格保持为空,
     * 以免机器"合成掉"正被移出的物品。
     */
    private ItemStack[] buildModeZeroRecipe() {
        ItemStack[] recipe = new ItemStack[9];
        if (isStrayBeingEjectedInModeZero()) return recipe;
        for (int i = 0; i < 9; i++) {
            ItemStack template = getTemplateStack(i);
            recipe[i] = template == null ? null : singleCopy(template);
        }
        return recipe;
    }

    /** 模式 9(Circle)直接按位置读取输入网格, 空槽保持为空以支持有形状配方。 */
    private ItemStack[] buildCircleRecipe() {
        ItemStack[] recipe = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = getBenchStack(i);
            recipe[i] = stack == null ? null : singleCopy(stack);
        }
        return recipe;
    }

    /** 模式 0 中, 输入网格里出现的非模板物品会被当作杂物输出 */
    private boolean isStrayBeingEjectedInModeZero() {
        ItemStack stack = getBenchStack(currentSlot);
        return stack != null && !isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(stack) &&
                getBenchStack(RESULT_SLOT) == null && isItemOutputEnabled() && currentSlot < 8;
    }

    private boolean isItemTypeOrItsEmptyLiquidContainerInCraftingGrid(@Nullable ItemStack stack) {
        if (stack == null) return true;
        for (int i = 0; i < PHANTOM_SLOT_COUNT; i++) {
            ItemStack template = getTemplateStack(i);
            if (template == null) continue;
            if (areStacksEqual(template, stack)) return true;
            if (areStacksEqual(getContainerForFilledItem(template), stack)) return true;
        }
        return false;
    }

    /** 结果槽空闲且允许输出时, 把指定槽位的物品移到结果槽 */
    private void ejectIfPossible(int slot) {
        if (getBenchStack(slot) != null && getBenchStack(RESULT_SLOT) == null && isItemOutputEnabled()) {
            ejectToResult(slot);
        }
    }

    private void ejectToResult(int slot) {
        setBenchStack(RESULT_SLOT, getBenchStack(slot));
        setBenchStack(slot, null);
        this.ticksUntilNextUpdate = 1;
    }

    /** 写入配方预览槽 */
    private void setRecipePreview(@Nullable ItemStack output) {
        ItemStack preview = output == null || output.isEmpty() ? ItemStack.EMPTY : GTUtility.copy(output);
        previewInventory.setStackInSlot(0, preview);
    }

    /** 切换模式时清空预览(源实现里的 switchMode) */
    public void onModeChanged() {
        previewInventory.setStackInSlot(0, ItemStack.EMPTY);
        lastCraftSuccessful = false;
        this.ticksUntilNextUpdate = 1;
        markDirty();
    }

    /** 当前配方预览, 供 JEI/UI 使用 */
    public ItemStack getRecipePreview() {
        return previewInventory.getStackInSlot(0);
    }

    // ==================== 配方工具 ====================

    /**
     * 查询 3x3 网格的合成产物。网格内容未变化时直接返回缓存, 避免每 tick 遍历全部配方。
     */
    private ItemStack findRecipeOutput(ItemStack[] recipe) {
        boolean empty = true;
        for (ItemStack stack : recipe) {
            if (stack != null && !stack.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) return ItemStack.EMPTY;

        if (cachedRecipeValid && gridMatchesCache(recipe)) {
            return cachedRecipeOutput;
        }

        InventoryCrafting inventory = new InventoryCrafting(new DummyContainer(), 3, 3);
        for (int i = 0; i < 9; i++) {
            inventory.setInventorySlotContents(i, recipe[i] == null ? ItemStack.EMPTY : singleCopy(recipe[i]));
        }
        IRecipe matched = CraftingManager.findMatchingRecipe(inventory, getWorld());
        ItemStack result = matched == null ? ItemStack.EMPTY : matched.getCraftingResult(inventory);

        for (int i = 0; i < 9; i++) {
            cachedRecipeGrid[i] = recipe[i] == null ? ItemStack.EMPTY : GTUtility.copy(recipe[i]);
        }
        cachedRecipeOutput = result;
        cachedRecipeValid = true;
        return result;
    }

    private boolean gridMatchesCache(ItemStack[] recipe) {
        for (int i = 0; i < 9; i++) {
            ItemStack cached = cachedRecipeGrid[i];
            ItemStack current = recipe[i];
            boolean cachedEmpty = cached == null || cached.isEmpty();
            boolean currentEmpty = current == null || current.isEmpty();
            if (cachedEmpty != currentEmpty) return false;
            if (cachedEmpty) continue;
            if (!ItemStack.areItemStacksEqual(cached, current)) return false;
        }
        return true;
    }

    /** 配方中每种物品的数量统计 */
    private static ArrayList<ItemStack> recipeContent(ItemStack[] recipe) {
        ArrayList<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (recipe[i] == null || recipe[i].isEmpty()) continue;
            boolean found = false;
            for (ItemStack stack : list) {
                if (areStacksEqual(recipe[i], stack)) {
                    stack.grow(1);
                    found = true;
                    break;
                }
            }
            if (!found) list.add(singleCopy(recipe[i]));
        }
        return list;
    }

    /** 缓冲区(0-17)中每种物品的总数量 */
    private ArrayList<ItemStack> benchContent() {
        ArrayList<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SLOT_OFFSET; i++) {
            ItemStack stack = getBenchStack(i);
            if (stack == null) continue;
            boolean found = false;
            for (ItemStack content : list) {
                if (areStacksEqual(stack, content)) {
                    content.grow(stack.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) list.add(GTUtility.copy(stack));
        }
        return list;
    }

    /**
     * 与 GT5U 的 {@code GTUtility.areStacksEqual} 一致: 忽略堆叠数量, 但支持通配耐久。
     */
    private static boolean areStacksEqual(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        int damageA = a.getItemDamage();
        int damageB = b.getItemDamage();
        if (damageA != damageB && damageA != OreDictionary.WILDCARD_VALUE &&
                damageB != OreDictionary.WILDCARD_VALUE) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(a, b);
    }

    /** 取得物品的容器返还物, 没有则返回空 */
    private static ItemStack getContainerItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.getItem().hasContainerItem(stack)) return ItemStack.EMPTY;
        ItemStack container = ForgeHooks.getContainerItem(GTUtility.copy(1, stack));
        return container == null ? ItemStack.EMPTY : container;
    }

    /** 取得已装罐流体容器的空容器(如满桶 -> 空桶), 没有则返回空 */
    private static ItemStack getContainerForFilledItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        IFluidHandlerItem handler = GTUtility.copy(1, stack)
                .getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) return ItemStack.EMPTY;
        FluidStack drained = handler.drain(Integer.MAX_VALUE, true);
        if (drained == null || drained.amount == 0) return ItemStack.EMPTY;
        return handler.getContainer();
    }

    // ==================== 槽位与库存访问 ====================

    @Nullable
    private ItemStack getBenchStack(int slot) {
        ItemStack stack = benchInventory.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack;
    }

    private void setBenchStack(int slot, @Nullable ItemStack stack) {
        benchInventory.setStackInSlot(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Nullable
    private ItemStack getTemplateStack(int slot) {
        ItemStack stack = templateInventory.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack;
    }

    private void shrinkSlot(int slot, int amount) {
        ItemStack stack = benchInventory.getStackInSlot(slot);
        if (stack.isEmpty()) return;
        stack.shrink(amount);
        if (stack.isEmpty()) {
            benchInventory.setStackInSlot(slot, ItemStack.EMPTY);
        } else {
            benchInventory.onContentsChanged(slot);
        }
    }

    private static ItemStack singleCopy(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : GTUtility.copy(1, stack);
    }

    // ==================== 属性 / 渲染 ====================

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = Math.floorMod(mode, MAX_MODES);
        if (getWorld() != null && !getWorld().isRemote) {
            onModeChanged();
        }
    }

    public int getThroughput() {
        return throughput;
    }

    public void setThroughput(int throughput) {
        if (this.throughput == throughput) return;
        this.throughput = Math.floorMod(throughput, MAX_THROUGHPUT);
        this.ticksUntilNextUpdate = 1;
        if (getWorld() != null && !getWorld().isRemote) {
            markDirty();
        }
    }

    public GTItemStackHandler getBenchInventory() {
        return benchInventory;
    }

    public GTItemStackHandler getTemplateInventory() {
        return templateInventory;
    }

    public GTItemStackHandler getPreviewInventory() {
        return previewInventory;
    }

    public FluidTank getTank() {
        return tank;
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.CRAFTING_TABLE.getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        int paintingColor = getPaintingColorForRendering();
        IVertexOperation[] colouredPipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(paintingColor)));
        // 底层
        Textures.CRAFTING_TABLE.renderOriented(renderState, translation, colouredPipeline, getFrontFacing());
        Textures.AUTO_WORKBENCH_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);

        // IO: 输出面画输出口; 开启自动输出物品时再叠一个箭头(参考 MetaTileEntityBuffer)
        EnumFacing output = getOutputFacing();
        Textures.PIPE_OUT_OVERLAY.renderSided(output, renderState, translation, pipeline);
        if (isAutoOutputItems()) {
            Textures.ITEM_OUTPUT_OVERLAY.renderSided(output, renderState,
                    RenderUtil.adjustTrans(translation, output, 2), pipeline);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        return AutoWorkbenchUI.build(this, syncManager);
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return SoundType.WOOD;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip, boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.auto_workbench.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.auto_workbench.tooltip.mode", MAX_MODES));
        tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", energyContainer.getInputVoltage(),
                GTValues.VNF[getTier()]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                energyContainer.getEnergyCapacity()));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_covers"));
        tooltip.add(I18n.format("gregtech.machine.auto_workbench.tool_action.wrench"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
