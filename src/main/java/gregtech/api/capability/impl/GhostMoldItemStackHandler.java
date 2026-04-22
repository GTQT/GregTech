package gregtech.api.capability.impl;

import gregtech.api.capability.INotifiableHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandlerModifiable;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GhostMoldItemStackHandler extends GTItemStackHandler
                                          implements IItemHandlerModifiable, INotifiableHandler {

    /**
     *特殊电路值表示不设置电路值。
     * */
    public static final int NO_CONFIG = -1;
    public static final int CUSTOM_ITEM_CONFIG = -2;

    private final List<MetaTileEntity> notifiableEntities = new ArrayList<>();

    /**
     * -- GETTER --
     * 返回电路值，或
     * 如果
     * 无电路值设置。
     * ＊
     *
     */
    @Getter
    private int circuitValue = NO_CONFIG;
    private ItemStack circuitStack = ItemStack.EMPTY;

    public GhostMoldItemStackHandler(MetaTileEntity metaTileEntity) {
        super(metaTileEntity);
    }

    /**
     *返回该实例是否包含有效的电路值。
     ＊
     * &#064;return此实例是否包含有效的电路值。
     */
    public boolean hasCircuitValue() {
        return this.circuitValue != NO_CONFIG;
    }

    public boolean hasCustomStack() {
        return this.circuitValue == CUSTOM_ITEM_CONFIG && !this.circuitStack.isEmpty();
    }

    /**
     * 将此库存的电路值设置为给定值并更新物品。物品被设置为对应整数值的电路物品，
     * 或如果给定 {@link GhostMoldItemStackHandler#NO_CONFIG} 则为一个空的物品堆栈。
     * <p>
     * 该值预期为有效的电路值
     * ({@link IntCircuitIngredient#CIRCUIT_MIN} ~ {@link IntCircuitIngredient#CIRCUIT_MAX}，包括两者)
     * 或 {@link GhostMoldItemStackHandler#NO_CONFIG}；任何其他值将产生 IllegalArgumentException。
     *
     * @param config 新的配置值
     * @throws IllegalArgumentException 输入无效时抛出
     */

    public void setCircuitValue(int config) {
        if (config == NO_CONFIG) {
            this.circuitValue = NO_CONFIG;
            this.circuitStack = ItemStack.EMPTY;
        } else if (config >= 0 && config < MetaItems.SHAPE_MOLDS.length) {
            this.circuitValue = config;
            this.circuitStack = MetaItems.SHAPE_MOLDS[config].getStackForm();
        } else {
            throw new IllegalArgumentException("Circuit value out of range: " + config);
        }
        for (MetaTileEntity mte : notifiableEntities) {
            if (mte != null && mte.isValid()) {
                addToNotifiedList(mte, this, false);
            }
        }
    }

    /**
     * 从给定物品设置此库存的电路值。只有当提供的物品为整数电路时，电路值才会被设置为有效的电路值；
     * 提供任何其他物品将把电路值设置为 {@link GhostMoldItemStackHandler#NO_CONFIG}。
     *
     * @param stack 要读取电路值的物品堆栈
     */

    public void setMoldValueFromStack(@NotNull ItemStack stack) {
        if(!stack.isEmpty())
        {
            if(stack.getItem()==MetaItems.SHAPE_MOLDS[0].getStackForm().getItem() &&
                    stack.getMetadata() >= MetaItems.SHAPE_MOLDS[0].getMetaValue() &&
                    stack.getMetadata() <= MetaItems.SHAPE_MOLDS[MetaItems.SHAPE_MOLDS.length-1].getMetaValue()
            )
            {
                for (int i = 0; i < MetaItems.SHAPE_MOLDS.length; i++) {
                    if(stack.getMetadata()==MetaItems.SHAPE_MOLDS[i].getMetaValue())
                    {
                        setCircuitValue(i);
                        break;
                    }
                }
            }
        }else
        {
            setCircuitValue(NO_CONFIG);
        }
    }

    public void setCustomStack(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            this.circuitValue = NO_CONFIG;
            this.circuitStack = ItemStack.EMPTY;
        } else {
            this.circuitValue = CUSTOM_ITEM_CONFIG;
            this.circuitStack = stack.copy();
            this.circuitStack.setCount(1);
        }
        for (MetaTileEntity mte : notifiableEntities) {
            if (mte != null && mte.isValid()) {
                addToNotifiedList(mte, this, false);
            }
        }
    }

    /**
     * 将给定值添加到现有的电路值中。结果值被限制在有效电路值的范围内。
     * 如果没有电路值存在，则此方法不执行任何操作。
     *
     * @param configDelta 要添加的电路值，可以是负数
     */
    public void addCircuitValue(int configDelta) {
        if (hasCircuitValue()) {
            setCircuitValue(MathHelper.clamp(getCircuitValue() + configDelta,
                    0, MetaItems.SHAPE_MOLDS.length));
        }
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        validateSlot(slot);
        setMoldValueFromStack(stack);
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        validateSlot(slot);
        return this.circuitStack;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        validateSlot(slot);
        return stack; // reject all item insertions
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        validateSlot(slot);
        if (!simulate) {
            setCircuitValue(NO_CONFIG);
        }
        return this.circuitStack;
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlot(slot);
        return 1;
    }

    protected void validateSlot(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException("Slot index out of bounds: " + slot);
    }

    @Override
    public void addNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        if (metaTileEntity == null) return;
        this.notifiableEntities.add(metaTileEntity);
    }

    @Override
    public void removeNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.notifiableEntities.remove(metaTileEntity);
    }

    public void write(@NotNull NBTTagCompound tag) {
        if (this.circuitValue == CUSTOM_ITEM_CONFIG && !this.circuitStack.isEmpty()) {
            tag.setTag("GhostCustomItem", this.circuitStack.writeToNBT(new NBTTagCompound()));
        } else if (this.circuitValue != NO_CONFIG) {
            tag.setByte("GhostMould", (byte) this.circuitValue);
        }
    }

    public void read(@NotNull NBTTagCompound tag) {
        if (tag.hasKey("GhostCustomItem", Constants.NBT.TAG_COMPOUND)) {
            ItemStack customStack = new ItemStack(tag.getCompoundTag("GhostCustomItem"));
            if (!customStack.isEmpty()) {
                setCustomStack(customStack);
                return;
            }
        }
        int circuitValue = tag.hasKey("GhostMould", Constants.NBT.TAG_ANY_NUMERIC) ? tag.getInteger("GhostMould") :
                NO_CONFIG;
        if (circuitValue < 0 || circuitValue > MetaItems.SHAPE_MOLDS.length)
            circuitValue = NO_CONFIG;
        setCircuitValue(circuitValue);
    }
}
