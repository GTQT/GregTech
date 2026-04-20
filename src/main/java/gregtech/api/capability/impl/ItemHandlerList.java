package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleNotifiableHandler;
import gregtech.api.capability.INotifiableHandler;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Efficiently delegates calls into multiple item handlers.
 * 使用惰性构建策略：构造时仅记录 handler 列表和偏移量，
 * handlerBySlotIndex 延迟到首次按 slot 访问时才填充。
 */
public class ItemHandlerList implements IItemHandlerModifiable, IMultipleNotifiableHandler {

    private Int2ObjectMap<IItemHandler> handlerBySlotIndex;
    private final Object2IntMap<IItemHandler> baseIndexOffset = new Object2IntArrayMap<>();
    /** 缓存的总槽位数，在构造函数中计算 */
    private final int totalSlots;

    public ItemHandlerList(@NotNull IItemHandler @NotNull... handlers) {
        this(Arrays.asList(handlers));
    }

    public ItemHandlerList(@NotNull List<? extends @NotNull IItemHandler> itemHandlerList) {
        int currentSlotIndex = 0;
        for (IItemHandler itemHandler : itemHandlerList) {
            Objects.requireNonNull(itemHandler, "Handler passed to ItemHandlerList was null.");
            if (baseIndexOffset.containsKey(itemHandler)) {
                throw new IllegalArgumentException("Attempted to add item handler " + itemHandler + " twice");
            }
            baseIndexOffset.put(itemHandler, currentSlotIndex);
            currentSlotIndex += itemHandler.getSlots();
        }
        this.totalSlots = currentSlotIndex;
    }

    /**
     * 惰性构建 handlerBySlotIndex，仅在首次按 slot 访问时执行。
     */
    private void ensureSlotIndexBuilt() {
        if (handlerBySlotIndex != null) return;
        handlerBySlotIndex = new Int2ObjectOpenHashMap<>(totalSlots);
        for (var entry : baseIndexOffset.entrySet()) {
            IItemHandler handler = entry.getKey();
            int baseIndex = entry.getValue();
            int slotsCount = handler.getSlots();
            for (int slotIndex = 0; slotIndex < slotsCount; slotIndex++) {
                handlerBySlotIndex.put(baseIndex + slotIndex, handler);
            }
        }
    }

    public int getIndexOffset(IItemHandler handler) {
        return baseIndexOffset.getOrDefault(handler, -1);
    }

    @Override
    public int getSlots() {
        return totalSlots;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (invalidSlot(slot)) return;
        ensureSlotIndexBuilt();
        IItemHandler itemHandler = handlerBySlotIndex.get(slot);
        int actualSlot = slot - baseIndexOffset.get(itemHandler);
        if (itemHandler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(actualSlot, stack);
        } else {
            itemHandler.extractItem(actualSlot, Integer.MAX_VALUE, false);
            itemHandler.insertItem(actualSlot, stack, false);
        }
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (invalidSlot(slot)) return ItemStack.EMPTY;
        ensureSlotIndexBuilt();
        IItemHandler itemHandler = handlerBySlotIndex.get(slot);
        return itemHandler.getStackInSlot(slot - baseIndexOffset.get(itemHandler));
    }

    @Override
    public int getSlotLimit(int slot) {
        if (invalidSlot(slot)) return 0;
        ensureSlotIndexBuilt();
        IItemHandler itemHandler = handlerBySlotIndex.get(slot);
        return itemHandler.getSlotLimit(slot - baseIndexOffset.get(itemHandler));
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (invalidSlot(slot)) return stack;
        ensureSlotIndexBuilt();
        IItemHandler itemHandler = handlerBySlotIndex.get(slot);
        return itemHandler.insertItem(slot - baseIndexOffset.get(itemHandler), stack, simulate);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (invalidSlot(slot)) return ItemStack.EMPTY;
        ensureSlotIndexBuilt();
        IItemHandler itemHandler = handlerBySlotIndex.get(slot);
        return itemHandler.extractItem(slot - baseIndexOffset.get(itemHandler), amount, simulate);
    }

    @NotNull
    public Collection<IItemHandler> getBackingHandlers() {
        return Collections.unmodifiableCollection(baseIndexOffset.keySet());
    }

    @Override
    public @NotNull Collection<INotifiableHandler> getBackingNotifiers() {
        ImmutableList.Builder<INotifiableHandler> notifiableHandlers = ImmutableList.builder();

        for (var handler : getBackingHandlers()) {
            if (handler instanceof INotifiableHandler notifiableHandler) {
                notifiableHandlers.add(notifiableHandler);
            }
        }

        return notifiableHandlers.build();
    }


    private boolean invalidSlot(int slot) {
        return slot < 0 || slot >= this.getSlots();
    }
}
