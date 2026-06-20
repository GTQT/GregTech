package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.drawable.GTObjectDrawable;
import gregtech.api.util.FluidTooltipUtil;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;
import gregtech.common.mui.widget.ScrollableTextWidget;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.drawable.IRichTextBuilder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.IByteBufDeserializer;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class MetaTileEntityMEDualExportHatch extends MetaTileEntityAEHostablePart
        implements IMultiblockAbilityPart<DualHandler>, IControllable {

    public static final String WORKING_TAG = "WorkingEnabled";
    public final static String ITEM_BUFFER_TAG = "ItemBuffer";
    public final static String FLUID_BUFFER_TAG = "FluidBuffer";
    protected boolean workingEnabled = true;
    protected List<IAEItemStack> internalItemBuffer;
    protected List<IAEFluidStack> internalFluidBuffer;

    public MetaTileEntityMEDualExportHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 6, true);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.internalItemBuffer = new ObjectArrayList<>();
        this.internalFluidBuffer = new ObjectArrayList<>();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEDualExportHatch(this.metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && workingEnabled && isOnline && shouldSyncME()) {
            // 分别处理物品和流体缓冲
            processBuffer(internalItemBuffer, getItemMonitor());
            processBuffer(internalFluidBuffer, getFluidMonitor());
        }
    }

    /**
     * 通用缓冲处理逻辑，避免代码重复
     */
    private <T extends IAEStack<T>> void processBuffer(List<T> buffer, @Nullable IMEMonitor<T> monitor) {
        if (buffer.isEmpty() || monitor == null) return;

        Iterator<T> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            T stackInBuffer = iterator.next();
            T notPushed = monitor.injectItems(stackInBuffer.copy(), Actionable.MODULATE, getActionSource());

            if (notPushed != null && notPushed.getStackSize() > 0) {
                stackInBuffer.setStackSize(notPushed.getStackSize());
            } else {
                iterator.remove();
            }
        }
    }

    /**
     * 添加物品Stack到文本显示
     */
    @SideOnly(Side.CLIENT)
    protected void addItemStackLine(@NotNull IRichTextBuilder<?> text,
                                    @NotNull IAEItemStack wrappedStack) {
        ItemStack stack = wrappedStack.getDefinition();
        text.add(new GTObjectDrawable(stack, 0)
                .asIcon()
                .asHoverable()
                // Auto update has to be true for "Press CTRL for Advanced Info" to work
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addFromItem(stack)));
        text.space();
        text.addLine(KeyUtil.number(TextFormatting.WHITE, wrappedStack.getStackSize(), "x"));
    }

    /**
     * 添加流体Stack到文本显示
     */
    @SideOnly(Side.CLIENT)
    protected void addFluidStackLine(@NotNull IRichTextBuilder<?> text, @NotNull IAEFluidStack wrappedStack) {
        FluidStack stack = wrappedStack.getFluidStack();
        text.add(new GTObjectDrawable(stack, 0)
                .asIcon()
                .asHoverable()
                .tooltip(tooltip -> {
                    tooltip.addLine(KeyUtil.fluid(stack));
                    FluidTooltipUtil.handleFluidTooltip(tooltip, stack);
                }));
        text.space();
        text.addLine(KeyUtil.number(TextFormatting.WHITE, wrappedStack.getStackSize(), "L"));
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        BooleanSyncValue onlineSync = new BooleanSyncValue(this::isOnline);
        panelSyncManager.syncValue("online", 0, onlineSync);

        // 双通道同步处理器
        DualStackListSyncHandler bufferSync = new DualStackListSyncHandler();
        panelSyncManager.syncValue("buffer", 0, bufferSync);

        ScrollableTextWidget textList = new ScrollableTextWidget();
        bufferSync.setChangeListener(textList::markDirty);

        return GTGuis.createPanel(this, 176, 18 + 18 * 4 + 94)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(IKey.lang(() -> onlineSync.getBoolValue() ?
                                "gregtech.gui.me_network.online" : "gregtech.gui.me_network.offline")
                        .asWidget().marginLeft(5).widthRel(1.0f).top(15))
                .child(textList.pos(9, 25 + 4)
                        .size(158, 18 * 4 - 6)
                        .textBuilder(text -> {
                            // 先显示物品，再显示流体（可自定义顺序）
                            bufferSync.cacheForEachItem(stack -> addItemStackLine(text, stack));
                            bufferSync.cacheForEachFluid(stack -> addFluidStackLine(text, stack));
                        })
                        .alignment(Alignment.TopLeft)
                        .background(GTGuiTextures.DISPLAY.asIcon().margin(-2, -2)))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    @Override
    public void onRemoval() {
        IMEMonitor<IAEItemStack> itemMonitor = getItemMonitor();
        if (itemMonitor != null) {
            for (IAEItemStack stack : internalItemBuffer) {
                itemMonitor.injectItems(stack.copy(), Actionable.MODULATE, getActionSource());
            }
        }

        IMEMonitor<IAEFluidStack> fluidMonitor = getFluidMonitor();
        if (fluidMonitor != null) {
            for (IAEFluidStack stack : internalFluidBuffer) {
                fluidMonitor.injectItems(stack.copy(), Actionable.MODULATE, getActionSource());
            }
        }
        super.onRemoval();
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.workingEnabled = buf.readBoolean();
    }

    @Override
    protected boolean shouldSerializeInventories() {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean(WORKING_TAG, workingEnabled);
        NBTTagList nbtList = new NBTTagList();
        for (IAEItemStack stack : internalItemBuffer) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            nbtList.appendTag(stackTag);
        }
        data.setTag(ITEM_BUFFER_TAG, nbtList);

        nbtList = new NBTTagList();
        for (IAEFluidStack stack : internalFluidBuffer) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            nbtList.appendTag(stackTag);
        }
        data.setTag(FLUID_BUFFER_TAG, nbtList);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(WORKING_TAG)) {
            this.workingEnabled = data.getBoolean(WORKING_TAG);
        }
        for (NBTBase tag : data.getTagList(ITEM_BUFFER_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tagCompound = (NBTTagCompound) tag;
            internalItemBuffer.add(AEItemStack.fromNBT(tagCompound));
        }
        for (NBTBase tag : data.getTagList(FLUID_BUFFER_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tagCompound = (NBTTagCompound) tag;
            internalFluidBuffer.add(AEFluidStack.fromNBT(tagCompound));
        }
    }

    protected @NotNull IByteBufDeserializer<IAEFluidStack> getFluidDeserializer() {
        return AEFluidStack::fromPacket;
    }

    protected @NotNull IByteBufDeserializer<IAEItemStack> getItemDeserializer() {
        return AEItemStack::fromPacket;
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return Arrays.asList(MultiblockAbility.EXPORT_ITEMS, MultiblockAbility.EXPORT_FLUIDS);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_ITEMS))
            abilityInstances.add(new InaccessibleInfiniteSlot(this, this.getController()));
        if (abilityInstances.isKey(MultiblockAbility.EXPORT_FLUIDS))
            abilityInstances.add(new InaccessibleInfiniteTank(this, this.getController()));
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        if (controllerBase instanceof MultiblockWithDisplayBase multiblockWithDisplayBase) {
            multiblockWithDisplayBase.enableFluidInfSink();
            multiblockWithDisplayBase.enableItemInfSink();
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            if (isOnline) {
                Textures.ME_DUAL_OUTPUT_HATCH_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
            } else {
                Textures.ME_DUAL_OUTPUT_HATCH.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    protected abstract static class InaccessibleInfiniteHandler implements INotifiableHandler {

        protected final List<MetaTileEntity> notifiableEntities = new ArrayList<>();
        protected final MetaTileEntity holder;

        public InaccessibleInfiniteHandler(@NotNull MetaTileEntity holder, @NotNull MetaTileEntity mte) {
            this.holder = holder;
            this.notifiableEntities.add(mte);
        }

        @Override
        public void addNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
            this.notifiableEntities.add(metaTileEntity);
        }

        @Override
        public void removeNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
            this.notifiableEntities.remove(metaTileEntity);
        }

        protected void trigger() {
            this.holder.markDirty();
            for (MetaTileEntity mte : this.notifiableEntities) {
                if (mte != null && mte.isValid()) {
                    this.addToNotifiedList(mte, this, true);
                }
            }
        }
    }

    protected class DualStackListSyncHandler extends SyncHandler {

        // 客户端缓存
        private final ObjectArrayList<IAEItemStack> itemCache = new ObjectArrayList<>();
        private final ObjectArrayList<IAEFluidStack> fluidCache = new ObjectArrayList<>();
        // 变更追踪
        private final IntSet changedItemIndexes = new IntOpenHashSet();
        private final IntSet changedFluidIndexes = new IntOpenHashSet();
        @Nullable
        private Runnable changeListener;

        @Override
        public void detectAndSendChanges(boolean init) {
            boolean hasChanges = false;

            // 检测物品缓冲区变更
            hasChanges |= detectBufferChanges(init, internalItemBuffer, itemCache, changedItemIndexes);

            // 检测流体缓冲区变更
            hasChanges |= detectBufferChanges(init, internalFluidBuffer, fluidCache, changedFluidIndexes);

            if (hasChanges) {
                syncToClient(0, buf -> {
                    try {
                        // 写入物品数据
                        buf.writeVarInt(itemCache.size());
                        buf.writeVarInt(changedItemIndexes.size());
                        for (int idx : changedItemIndexes) {
                            buf.writeVarInt(idx);
                            itemCache.get(idx).writeToPacket(buf);
                        }
                        // 写入流体数据
                        buf.writeVarInt(fluidCache.size());
                        buf.writeVarInt(changedFluidIndexes.size());
                        for (int idx : changedFluidIndexes) {
                            buf.writeVarInt(idx);
                            fluidCache.get(idx).writeToPacket(buf);
                        }
                    } catch (IOException e) {
                        // 网络同步异常，包装为运行时异常避免破坏 syncToClient 签名
                        throw new RuntimeException("Failed to sync ME output buffer", e);
                    }
                });
                onChange();
                changedItemIndexes.clear();
                changedFluidIndexes.clear();
            }
        }

        /**
         * 通用缓冲区变更检测逻辑
         *
         * @return 是否有变更
         */
        private <T extends IAEStack<T>> boolean detectBufferChanges(boolean init,
                                                                    List<T> source,
                                                                    List<T> cache,
                                                                    IntSet changedIndexes) {
            int sourceSize = source.size();

            resizeList(cache, sourceSize);

            boolean hasChanges = false;
            for (int i = 0; i < sourceSize; i++) {
                T newStack = source.get(i);
                T cached = cache.get(i);
                if (init || !newStack.equals(cached)) {
                    T copy = newStack.copy();
                    cache.set(i, copy);
                    changedIndexes.add(i);
                    hasChanges = true;
                }
            }
            return hasChanges;
        }

        private <T> void resizeList(List<T> list, int newSize) {
            int currentSize = list.size();
            if (currentSize < newSize) {
                for (int i = currentSize; i < newSize; i++) {
                    list.add(null);
                }
            } else if (currentSize > newSize) {
                list.subList(newSize, currentSize).clear();
            }
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {
            if (id != 0) return;

            // 读取物品数据
            int itemCount = buf.readVarInt();
            resizeList(itemCache, itemCount);
            int itemChanges = buf.readVarInt();
            for (int i = 0; i < itemChanges; i++) {
                int idx = buf.readVarInt();
                IAEItemStack stack = getItemDeserializer().deserialize(buf);
                itemCache.set(idx, stack);
            }

            // 读取流体数据
            int fluidCount = buf.readVarInt();
            resizeList(fluidCache, fluidCount);
            int fluidChanges = buf.readVarInt();
            for (int i = 0; i < fluidChanges; i++) {
                int idx = buf.readVarInt();
                IAEFluidStack stack = getFluidDeserializer().deserialize(buf);
                fluidCache.set(idx, stack);
            }

            onChange();
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            // Server -> Client only
        }

        public void setChangeListener(@NotNull Runnable listener) {
            this.changeListener = listener;
        }

        private void onChange() {
            if (changeListener != null) changeListener.run();
        }

        public void cacheForEachItem(@NotNull Consumer<IAEItemStack> consumer) {
            for (IAEItemStack stack : itemCache) consumer.accept(stack);
        }

        public void cacheForEachFluid(@NotNull Consumer<IAEFluidStack> consumer) {
            for (IAEFluidStack stack : fluidCache) consumer.accept(stack);
        }
    }

    private class InaccessibleInfiniteSlot extends InaccessibleInfiniteHandler
            implements IItemHandlerModifiable {

        public InaccessibleInfiniteSlot(@NotNull MetaTileEntity holder,
                                        @NotNull MetaTileEntity mte) {
            super(holder, mte);
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            insertItem(slot, stack, false);
            this.trigger();
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stackToInsert, boolean simulate) {
            if (stackToInsert.isEmpty() || simulate) {
                return ItemStack.EMPTY;
            }

            int amount = stackToInsert.getCount();
            for (IAEItemStack bufferedStack : internalItemBuffer) {
                long bufferedStackSize = bufferedStack.getStackSize();
                if (bufferedStack.equals(stackToInsert) && bufferedStackSize < Long.MAX_VALUE) {
                    int amountToAdd = (int) Math.min(amount, Long.MAX_VALUE - bufferedStackSize);
                    bufferedStack.incStackSize(amountToAdd);
                    amount -= amountToAdd;
                    if (amount < 1) break;
                }
            }

            if (amount > 0) {
                IAEItemStack newStack = AEItemStack.fromItemStack(stackToInsert);
                // noinspection DataFlowIssue
                newStack.setStackSize(amount);
                internalItemBuffer.add(newStack);
            }

            trigger();
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE - 1;
        }
    }

    protected class InaccessibleInfiniteTank extends InaccessibleInfiniteHandler
            implements IFluidTank {

        public InaccessibleInfiniteTank(@NotNull MetaTileEntity holder,
                                        @NotNull MetaTileEntity mte) {
            super(holder, mte);
        }

        @Nullable
        @Override
        public FluidStack getFluid() {
            return null;
        }

        @Override
        public int getFluidAmount() {
            return 0;
        }

        @Override
        public int getCapacity() {
            return Integer.MAX_VALUE - 1;
        }

        @Override
        public FluidTankInfo getInfo() {
            return null;
        }

        @Override
        public int fill(@Nullable FluidStack stackToInsert, boolean doFill) {
            if (stackToInsert == null || stackToInsert.amount < 1) {
                return 0;
            }

            if (doFill) {
                int amount = stackToInsert.amount;
                for (IAEFluidStack bufferedStack : internalFluidBuffer) {
                    long bufferedStackSize = bufferedStack.getStackSize();
                    if (bufferedStack.equals(stackToInsert) && bufferedStackSize < Long.MAX_VALUE) {
                        int amountToAdd = (int) Math.min(amount, Long.MAX_VALUE - bufferedStackSize);
                        bufferedStack.incStackSize(amountToAdd);
                        amount -= amountToAdd;
                        if (amount < 1) break;
                    }
                }

                if (amount > 0) {
                    IAEFluidStack newStack = AEFluidStack.fromFluidStack(stackToInsert);
                    newStack.setStackSize(amount);
                    internalFluidBuffer.add(newStack);
                }

                this.trigger();
            }

            return stackToInsert.amount;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }
}
