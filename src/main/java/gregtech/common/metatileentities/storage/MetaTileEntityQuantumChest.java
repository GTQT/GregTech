package gregtech.common.metatileentities.storage;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.cover.CoverRayTracer;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityUIFactory;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.custom.QuantumStorageRenderer;
import gregtech.common.mui.widget.FakeItemSlot;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_ITEM;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_ITEM_COUNT;
import static net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer;

public class MetaTileEntityQuantumChest extends MetaTileEntityQuantumStorage<IItemHandler>
        implements ITieredMetaTileEntity, IFastRenderMetaTileEntity {

    private static final String NBT_ITEMSTACK = "ItemStack";
    private static final String NBT_PARTIALSTACK = "PartialStack";
    private static final String NBT_ITEMCOUNT = "ItemAmount";
    protected final long maxStoredItems;
    private final int tier;
    /** The ItemStack that the Quantum Chest is storing */
    protected ItemStack virtualItemStack = ItemStack.EMPTY;
    protected long itemsStoredInside = 0L;
    protected IItemHandler outputItemInventory;
    protected ItemStack previousStack;
    protected ItemStack lockedStack = ItemStack.EMPTY;
    protected long previousStackSize;
    protected boolean voiding;
    private ItemHandlerList combinedInventory;
    private int coolDown = 0;

    public MetaTileEntityQuantumChest(ResourceLocation metaTileEntityId, int tier, long maxStoredItems) {
        super(metaTileEntityId);
        this.tier = tier;
        this.maxStoredItems = maxStoredItems;
    }

    protected static boolean areItemStackIdentical(ItemStack first, ItemStack second) {
        return ItemStack.areItemsEqual(first, second) &&
                ItemStack.areItemStackTagsEqual(first, second);
    }

    public long getMaxStoredItems() {
        return maxStoredItems;
    }

    public IItemHandler getOutputItemInventory() {
        return outputItemInventory;
    }

    public long getPreviousStackSize() {
        return previousStackSize;
    }

    public int getCoolDown() {
        return coolDown;
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQuantumChest(metaTileEntityId, tier, maxStoredItems);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        Textures.QUANTUM_STORAGE_RENDERER.renderMachine(renderState, translation,
                ArrayUtils.add(pipeline,
                        new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering()))),
                this);
        Textures.QUANTUM_CHEST_OVERLAY.renderSided(EnumFacing.UP, renderState, translation, pipeline);
        var outputFacing = getOutputFacing();
        if (outputFacing != null) {
            Textures.PIPE_OUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            if (isAutoOutputItems()) {
                Textures.ITEM_OUTPUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            }
        }
        renderIndicatorOverlay(renderState, translation, pipeline);
    }

    @Override
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        QuantumStorageRenderer.renderChestStack(x, y, z, this, virtualItemStack, itemsStoredInside, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[tier].getParticleSprite(), getPaintingColorForRendering());
    }

    public void refreshCoolDown() {
        this.coolDown = 5;
    }

    @Override
    public void update() {
        super.update();
        EnumFacing currentOutputFacing = getOutputFacing();
        if (!getWorld().isRemote) {
            if (this.coolDown > 0) {
                this.coolDown--;
            }
            if (shouldTransferImport()) {
                ItemStack inputStack = importItems.getStackInSlot(0);
                ItemStack outputStack = exportItems.getStackInSlot(0);
                if (!inputStack.isEmpty() &&
                        (virtualItemStack.isEmpty() || areItemStackIdentical(outputStack, inputStack))) {
                    GTTransferUtils.moveInventoryItems(importItems, combinedInventory);

                    markDirty();
                }
            }
            if (shouldTransferExport()) {
                ItemStack outputStack = exportItems.getStackInSlot(0);
                int maxStackSize = virtualItemStack.getMaxStackSize();
                if (outputStack.isEmpty() || (areItemStackIdentical(virtualItemStack, outputStack) &&
                        outputStack.getCount() < maxStackSize)) {
                    GTTransferUtils.moveInventoryItems(itemInventory, exportItems);

                    markDirty();
                }
            }

            if (isAutoOutputItems()) {
                pushItemsIntoNearbyHandlers(currentOutputFacing);
            }

            if (isVoiding() && !importItems.getStackInSlot(0).isEmpty()) {
                importItems.setStackInSlot(0, ItemStack.EMPTY);
            }

            if (previousStack == null || !areItemStackIdentical(previousStack, virtualItemStack)) {
                writeCustomData(UPDATE_ITEM, buf -> {
                    virtualItemStack.setCount(1);
                    NetworkUtils.writeItemStack(buf, this.virtualItemStack);
                });
                previousStack = virtualItemStack;
            }
            if (previousStackSize != itemsStoredInside) {
                writeCustomData(UPDATE_ITEM_COUNT, buf -> buf.writeLong(itemsStoredInside));
                previousStackSize = itemsStoredInside;
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_total", maxStoredItems));

        long count = getStoredItemCountFromNBT(stack);
        int exportCount = getExportItemCountFromNBT(stack);
        String translationKey = getExportItemNameFromNBT(stack);

        if (translationKey != null) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.item_stored",
                    I18n.format(translationKey), count, exportCount));
        }
    }

    /**
     * 从物品栈的NBT数据中获取存储的物品数量
     *
     * @param stack 包含量子箱数据的物品栈
     * @return 存储的物品总数，如果无数据则返回0
     */
    public long getStoredItemCountFromNBT(ItemStack stack) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null && compound.hasKey(NBT_ITEMSTACK)) {
            return compound.getLong(NBT_ITEMCOUNT);
        }
        return 0L;
    }

    /**
     * 从物品栈的NBT数据中获取导出槽位的物品数量
     *
     * @param stack 包含量子箱数据的物品栈
     * @return 导出槽位中的物品数量，如果无数据则返回0
     */
    public int getExportItemCountFromNBT(ItemStack stack) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null && compound.hasKey(NBT_PARTIALSTACK)) {
            ItemStack tempStack = new ItemStack(compound.getCompoundTag(NBT_PARTIALSTACK));
            return tempStack.getCount();
        }
        return 0;
    }

    /**
     * 从物品栈的NBT数据中获取导出槽位物品的显示名称
     *
     * @param stack 包含量子箱数据的物品栈
     * @return 导出物品的显示名称，如果无数据则返回null
     */
    @Nullable
    public String getExportItemNameFromNBT(ItemStack stack) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound != null && compound.hasKey(NBT_PARTIALSTACK)) {
            ItemStack tempStack = new ItemStack(compound.getCompoundTag(NBT_PARTIALSTACK));
            return tempStack.getDisplayName();
        }
        return null;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.itemInventory = new QuantumChestItemHandler();
        List<IItemHandler> temp = new ArrayList<>();
        temp.add(this.exportItems);
        temp.add(this.itemInventory);
        this.combinedInventory = new ItemHandlerList(temp);
        this.outputItemInventory = new ItemHandlerProxy(new GTItemStackHandler(this, 0), combinedInventory);
    }

    protected boolean shouldTransferImport() {
        return this.importItems.getSlots() > 0 && maxStoredItems > 0 && itemsStoredInside < maxStoredItems;
    }

    protected boolean shouldTransferExport() {
        return this.exportItems.getSlots() > 0 && itemsStoredInside > 0 && !virtualItemStack.isEmpty();
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new GTItemStackHandler(this, 1) {

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!isItemValid(slot, stack)) return stack;
                return GTTransferUtils.insertItem(getCombinedInventory(), stack, simulate);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                NBTTagCompound compound = stack.getTagCompound();
                ItemStack outStack = getExportItems().getStackInSlot(0);
                boolean outStackMatch = true;
                if (!outStack.isEmpty()) {
                    outStackMatch = areItemStackIdentical(stack, outStack);
                }
                if (compound == null) return true;
                return outStackMatch && !(compound.hasKey(NBT_ITEMSTACK, NBT.TAG_COMPOUND) ||
                        compound.hasKey("Fluid", NBT.TAG_COMPOUND)); // prevents inserting items with NBT to the Quantum
                // Chest
            }
        };
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new GTItemStackHandler(this, 1);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tagCompound = super.writeToNBT(data);
        if (!virtualItemStack.isEmpty() && itemsStoredInside > 0L) {
            tagCompound.setTag(NBT_ITEMSTACK, virtualItemStack.writeToNBT(new NBTTagCompound()));
            tagCompound.setLong(NBT_ITEMCOUNT, itemsStoredInside);
        }
        if (locked && !lockedStack.isEmpty())
            data.setTag("LockedStack", lockedStack.serializeNBT());
        return tagCompound;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("ItemStack", NBT.TAG_COMPOUND)) {
            this.virtualItemStack = new ItemStack(data.getCompoundTag("ItemStack"));
            if (!virtualItemStack.isEmpty()) {
                this.itemsStoredInside = data.getLong(NBT_ITEMCOUNT);
            }
        }
        if (locked) this.lockedStack = new ItemStack(data.getCompoundTag("LockedStack"));
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(NBT_ITEMSTACK, NBT.TAG_COMPOUND)) {
            this.virtualItemStack = new ItemStack(itemStack.getCompoundTag(NBT_ITEMSTACK));
            if (!this.virtualItemStack.isEmpty()) {
                this.itemsStoredInside = itemStack.getLong(NBT_ITEMCOUNT);
            }
        }
        if (itemStack.hasKey(NBT_PARTIALSTACK, NBT.TAG_COMPOUND)) {
            exportItems.setStackInSlot(0, new ItemStack(itemStack.getCompoundTag(NBT_PARTIALSTACK)));
        }

        if (itemStack.getBoolean(IS_VOIDING)) {
            setVoiding(true);
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        if (!this.virtualItemStack.isEmpty()) {
            itemStack.setTag(NBT_ITEMSTACK, this.virtualItemStack.writeToNBT(new NBTTagCompound()));
            itemStack.setLong(NBT_ITEMCOUNT, itemsStoredInside);
        }
        ItemStack partialStack = exportItems.extractItem(0, 64, false);
        if (!partialStack.isEmpty()) {
            itemStack.setTag(NBT_PARTIALSTACK, partialStack.writeToNBT(new NBTTagCompound()));
        }

        if (isVoiding()) {
            itemStack.setBoolean(IS_VOIDING, true);
        }

        this.virtualItemStack = ItemStack.EMPTY;
        this.itemsStoredInside = 0;
        exportItems.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    protected void createWidgets(ModularPanel mainPanel, PanelSyncManager syncManager) {
        mainPanel.child(createQuantumDisplay("gregtech.machine.quantum_chest.items_stored",
                        () -> virtualItemStack.getDisplayName(),
                        textWidget -> !virtualItemStack.isEmpty(),
                        () -> TextFormattingUtil.formatNumbers(itemsStoredInside)))
                .child(new FakeItemSlot(true)
                        .showTooltip(true)
                        .showAmount(false)
                        .background(IDrawable.NONE)
                        .slot(itemInventory, 0)
                        // TODO: lock from ghost item .receiveItemFromClient(this::setLocked)
                        .pos(148, 41));
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getOutputFacing() == facing || getFrontFacing() == facing) {
                return false;
            }
            if (!getWorld().isRemote) {
                setOutputFacing(facing);
            }
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        this.virtualItemStack.setCount(1);
        NetworkUtils.writeItemStack(buf, virtualItemStack);
        NetworkUtils.writeItemStack(buf, lockedStack);
        buf.writeLong(itemsStoredInside);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.virtualItemStack = NetworkUtils.readItemStack(buf);
        this.lockedStack = NetworkUtils.readItemStack(buf);
        this.itemsStoredInside = buf.readLong();
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        // use direct outputFacing field instead of getter method because otherwise
        // it will just return SOUTH for null output facing
        return super.isValidFrontFacing(facing) && facing != outputFacing;
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ITEM) {
            this.virtualItemStack = NetworkUtils.readItemStack(buf);
        } else if (dataId == UPDATE_ITEM_COUNT) {
            this.itemsStoredInside = buf.readLong();
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE) {
            if (side == getOutputFacing()) {
                return GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE.cast(this);
            }
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {

            // for TOP
            if (side == null) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combinedInventory);

            // try fix being able to insert through output hole when input on output is disabled
            IItemHandler itemHandler = (side == getOutputFacing() && !isAllowInputFromOutputSideItems()) ?
                    outputItemInventory : combinedInventory;
            if (itemHandler.getSlots() > 0) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
            }
            return null;
        }
        return super.getCapability(capability, side);
    }

    public IItemHandler getCombinedInventory() {
        return this.combinedInventory;
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        if (this.outputFacing == null) {
            // set initial output facing as opposite to front
            setOutputFacing(frontFacing.getOpposite());
        }
    }

    @Override
    protected void setLocked(boolean locked) {
        super.setLocked(locked);
        if (locked && !this.virtualItemStack.isEmpty() && this.lockedStack.isEmpty()) {
            this.lockedStack = this.virtualItemStack.copy();
        } else if (!locked) {
            this.lockedStack = ItemStack.EMPTY;
        }
    }

    protected void setLocked(ItemStack stack) {
        this.lockedStack = stack;
        super.setLocked(!stack.isEmpty());
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        clearInventory(itemBuffer, importItems);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        EnumFacing hitFacing = CoverRayTracer.determineGridSideHit(hitResult);
        if (facing == getOutputFacing() || (hitFacing == getOutputFacing() && playerIn.isSneaking())) {
            if (!getWorld().isRemote) {
                if (isAllowInputFromOutputSideItems()) {
                    setAllowInputFromOutputSide(false);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.disallow"),
                            true);
                } else {
                    setAllowInputFromOutputSide(true);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.allow"), true);
                }
            }
            return true;
        }
        return super.onScrewdriverClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public Type getType() {
        return Type.ITEM;
    }

    @Override
    public IItemHandler getTypeValue() {
        return this.combinedInventory;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos());
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (facing == this.getFrontFacing()) {
            if (playerIn.isSneaking() && openGUIOnRightClick()) {
                if (getWorld() != null && !getWorld().isRemote) {
                    MetaTileEntityUIFactory.INSTANCE.openUI(getHolder(), (EntityPlayerMP) playerIn);
                }
                return true;
            }

            IItemHandler qChestInv = this.getCombinedInventory();
            //如果没有连续点击：放入手持物品
            //如果连续点击：放入所有与抽屉内容物相同的物品
            if (getCoolDown() > 0) {
                refreshCoolDown();

                ItemStack candidate = qChestInv.extractItem(0, 1, true);
                if (candidate.isEmpty()) return true;

                //全部转移就是转移玩家背包
                var playerInv = new PlayerMainInvWrapper(playerIn.inventory);
                GTTransferUtils.moveInventoryItems(playerInv, qChestInv);

                markDirty();
            } else {
                refreshCoolDown();

                ItemStack sourceStack = playerIn.getHeldItem(hand);

                ItemStack candidate = qChestInv.insertItem(1, sourceStack, true);
                if (sourceStack.getCount() == candidate.getCount()) return true;

                ItemStack remining = qChestInv.insertItem(1, sourceStack, false);
                sourceStack.setCount(remining.getCount());

                markDirty();
            }
            return true;
        }

        return super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public void onLeftClick(EntityPlayer player, EnumFacing facing, CuboidRayTraceResult hitResult) {

        if (facing == this.getFrontFacing()) {
            ItemStack candidate = getOutputItemInventory().extractItem(0, 1, true);
            //没有按住shift:取出超级箱的一个物品
            //按住shift:取出超级箱一组(最大)物品
            refreshCoolDown();
            ItemStack stack = getOutputItemInventory().extractItem(0,
                    player.isSneaking() ? candidate.getMaxStackSize() : 1, false);
            giveItemToPlayer(player, stack, player.inventory.currentItem);
            markDirty();
        }

        super.onLeftClick(player, facing, hitResult);
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    protected class QuantumChestItemHandler extends SyncHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack itemStack = MetaTileEntityQuantumChest.this.virtualItemStack;
            long itemsStored = MetaTileEntityQuantumChest.this.itemsStoredInside;

            if (itemStack.isEmpty() || itemsStored == 0L) {
                return ItemStack.EMPTY;
            }

            return GTUtility.copy((int) itemsStored, itemStack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (int) MetaTileEntityQuantumChest.this.maxStoredItems;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int extractedAmount = (int) Math.min(amount, itemsStoredInside);
            if (virtualItemStack.isEmpty() || extractedAmount == 0) {
                return ItemStack.EMPTY;
            }

            ItemStack extractedStack = virtualItemStack.copy();
            extractedStack.setCount(extractedAmount);

            if (!simulate) {
                MetaTileEntityQuantumChest.this.itemsStoredInside -= extractedAmount;
                if (itemsStoredInside == 0L) {
                    MetaTileEntityQuantumChest.this.virtualItemStack = ItemStack.EMPTY;
                }
                updateClient();
            }
            return extractedStack;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack insertedStack, boolean simulate) {
            if (insertedStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            // check locked and if locked stack matches
            if (locked && !areItemStackIdentical(lockedStack, insertedStack))
                return insertedStack;

            // If there is a virtualized stack and the stack to insert does not match it, do not insert anything
            if (itemsStoredInside > 0L &&
                    !virtualItemStack.isEmpty() &&
                    !areItemStackIdentical(virtualItemStack, insertedStack)) {
                return insertedStack;
            }

            ItemStack exportItems = getExportItems().getStackInSlot(0);

            // if there is an item in the export slot and the inserted stack does not match, do not insert
            if (!exportItems.isEmpty() && !areItemStackIdentical(exportItems, insertedStack)) {
                return insertedStack;
            }

            ItemStack remainingStack = insertedStack.copy();

            // Virtualize the items
            long amountLeftInChest = maxStoredItems - itemsStoredInside;
            int maxPotentialVirtualizedAmount = insertedStack.getCount();

            int actualVirtualizedAmount = (int) Math.min(maxPotentialVirtualizedAmount, amountLeftInChest);

            // if there are any items left over, shrink it by the amount virtualized,
            // which should always be between 0 and the amount left in chest
            remainingStack.shrink(actualVirtualizedAmount);

            if (!simulate) {
                if (actualVirtualizedAmount > 0) {
                    if (virtualItemStack.isEmpty()) {
                        ItemStack virtualStack = insertedStack.copy();

                        // set the virtual stack to 1, since it's mostly for display
                        virtualStack.setCount(1);
                        MetaTileEntityQuantumChest.this.virtualItemStack = virtualStack;
                        MetaTileEntityQuantumChest.this.itemsStoredInside = actualVirtualizedAmount;
                    } else {
                        MetaTileEntityQuantumChest.this.itemsStoredInside += actualVirtualizedAmount;
                    }
                    updateClient();
                }
            }

            if (isVoiding() && remainingStack.getCount() > 0) {
                return ItemStack.EMPTY;
            } else {
                return remainingStack;
            }
        }

        protected void updateClient() {
            if (isValid() && !getSyncManager().isClient()) {
                syncToClient(1, buffer -> {
                    buffer.writeInt((int) itemsStoredInside);
                    buffer.writeItemStack(virtualItemStack);
                });
            }
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {
            if (id == 1) {
                itemsStoredInside = buf.readInt();
                virtualItemStack = buf.readItemStack();
            }
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {}
    }
}
