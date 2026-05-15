package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Base class for multiblock parts that support multiple variants stored as NBT sub-types
 * within a single MTE ID.
 *
 * <p>Mirrors the design of {@link ParametricMultiblockController} but for multiblock parts
 * (hatches, valves, etc.) that also need material/variant differentiation.
 *
 * @param <V> the variant enum type
 * @see ParametricMultiblockController
 */
public abstract class ParametricMultiblockPart<V extends Enum<V>> extends MetaTileEntityMultiblockPart {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final Class<V> variantClass;
    private final V defaultVariant;
    private V variant;

    protected ParametricMultiblockPart(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull Class<V> variantClass,
                                       @NotNull V defaultVariant) {
        super(metaTileEntityId, 0);
        this.variantClass = variantClass;
        this.defaultVariant = defaultVariant;
        this.variant = defaultVariant;
    }

    // region Variant Access

    /**
     * Returns the current variant of this multiblock part instance.
     * When rendering as an item (renderContextStack is set), reads variant from the ItemStack NBT
     * to ensure each sub-item renders with its own appearance.
     */
    @NotNull
    public V getVariant() {
        if (getWorld() == null && renderContextStack != null) {
            return getVariantFromStack(renderContextStack);
        }
        return variant;
    }

    protected void setVariant(@NotNull V variant) {
        this.variant = variant;
    }

    @NotNull
    public Class<V> getVariantClass() {
        return variantClass;
    }

    @NotNull
    public V getDefaultVariant() {
        return defaultVariant;
    }

    // endregion

    // region NBT Serialization

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger(NBT_KEY_VARIANT, variant.ordinal());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.variant = readVariantFromOrdinal(data.getInteger(NBT_KEY_VARIANT));
        onVariantChanged();
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(variant.ordinal());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.variant = readVariantFromOrdinal(buf.readByte());
        onVariantChanged();
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(NBT_KEY_VARIANT)) {
            this.variant = readVariantFromOrdinal(itemStack.getInteger(NBT_KEY_VARIANT));
            onVariantChanged();
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        itemStack.setInteger(NBT_KEY_VARIANT, variant.ordinal());
    }

    @NotNull
    private V readVariantFromOrdinal(int ordinal) {
        V[] values = variantClass.getEnumConstants();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return defaultVariant;
    }

    /**
     * Called after the variant is changed (from NBT load or item placement).
     * Subclasses can override to re-initialize state.
     */
    protected void onVariantChanged() {}

    // endregion

    // region Sub-items and Item Differentiation

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        for (V value : variantClass.getEnumConstants()) {
            subItems.add(getStackForm(value));
        }
    }

    @Override
    public String getItemSubTypeId(ItemStack itemStack) {
        V mat = getVariantFromStack(itemStack);
        return getVariantName(mat);
    }

    @Override
    @NotNull
    public ItemStack getStackForm(int amount) {
        ItemStack stack = super.getStackForm(amount);
        writeStackVariant(stack, variant);
        return stack;
    }

    @NotNull
    public ItemStack getStackForm(@NotNull V variantValue) {
        ItemStack stack = super.getStackForm();
        writeStackVariant(stack, variantValue);
        return stack;
    }

    private void writeStackVariant(@NotNull ItemStack stack, @NotNull V variantValue) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(NBT_KEY_VARIANT, variantValue.ordinal());
    }

    @NotNull
    public V getVariantFromStack(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(NBT_KEY_VARIANT)) {
            return readVariantFromOrdinal(tag.getInteger(NBT_KEY_VARIANT));
        }
        return defaultVariant;
    }

    // endregion

    // region Localization

    /**
     * Returns the translation key prefix for variant-specific names.
     */
    @NotNull
    protected abstract String getVariantTranslationPrefix();

    @NotNull
    protected String getVariantName(@NotNull V variantValue) {
        return variantValue.name().toLowerCase();
    }

    @Override
    public String getMetaName() {
        return getVariantTranslationPrefix() + "." + getVariantName(getVariant());
    }

    @Override
    public String getMetaName(@NotNull ItemStack stack) {
        return getVariantTranslationPrefix() + "." + getVariantName(getVariantFromStack(stack));
    }

    // endregion
}
