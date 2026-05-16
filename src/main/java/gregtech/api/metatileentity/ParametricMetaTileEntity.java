package gregtech.api.metatileentity;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Base class for single-block MTEs that support multiple variants stored as NBT sub-types
 * within a single MTE ID.
 *
 * <p>This drastically reduces ID consumption and memory usage: instead of registering
 * N separate MTEs for N variants, only one MTE is registered and the variant is
 * stored in NBT.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * public class MetaTileEntityDrum extends ParametricMetaTileEntity<DrumMaterial> {
 *
 *     public MetaTileEntityDrum(ResourceLocation id) {
 *         super(id, DrumMaterial.class, DrumMaterial.WOOD);
 *     }
 *
 *     @Override protected String getVariantTranslationPrefix() { return "gregtech.machine.drum"; }
 * }
 * }</pre>
 *
 * <h3>Memory Model:</h3>
 * <ul>
 *   <li>1 MTE ID in the registry (regardless of variant count)</li>
 *   <li>Variant stored in NBT for TileEntity persistence, ItemStack, and network sync</li>
 * </ul>
 *
 * @param <V> the variant enum type
 * @see gregtech.api.metatileentity.multiblock.ParametricMultiblockController for the multiblock equivalent
 */
public abstract class ParametricMetaTileEntity<V extends Enum<V>> extends MetaTileEntity {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final Class<V> variantClass;
    private final V defaultVariant;
    private V variant;

    protected ParametricMetaTileEntity(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull Class<V> variantClass,
                                       @NotNull V defaultVariant) {
        super(metaTileEntityId);
        this.variantClass = variantClass;
        this.defaultVariant = defaultVariant;
        this.variant = defaultVariant;
    }

    // region Variant Access

    /**
     * Returns the current variant of this MTE instance.
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

    /**
     * Sets the variant. Should only be called during initialization (NBT load, item placement).
     */
    protected void setVariant(@NotNull V variant) {
        applyVariant(variant, false);
    }

    private void applyVariant(@NotNull V variant, boolean force) {
        if (!force && this.variant == variant) {
            return;
        }
        this.variant = variant;
        onVariantChanged();
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
        applyVariant(readVariantFromOrdinal(data.getInteger(NBT_KEY_VARIANT)), true);
        super.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeByte(variant.ordinal());
        super.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        applyVariant(readVariantFromOrdinal(buf.readByte()), true);
        super.receiveInitialSyncData(buf);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(NBT_KEY_VARIANT)) {
            applyVariant(readVariantFromOrdinal(itemStack.getInteger(NBT_KEY_VARIANT)), true);
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        itemStack.setInteger(NBT_KEY_VARIANT, variant.ordinal());
    }

    /**
     * Safely reads a variant from an ordinal value with bounds checking.
     */
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
     * Subclasses can override to re-initialize inventory or other variant-dependent state.
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

    /**
     * Creates an ItemStack for this MTE with a specific variant.
     */
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

    /**
     * Extracts the variant from an ItemStack's NBT.
     */
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
     * The full key will be: {@code prefix + "." + variant_name_lowercase + ".name"}
     *
     * <p>Example: prefix = "gregtech.machine.drum" → key = "gregtech.machine.drum.bronze.name"
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
