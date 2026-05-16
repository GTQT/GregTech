package gregtech.api.metatileentity;

import gregtech.api.metatileentity.variant.ParametricVariantRegistries;
import gregtech.api.metatileentity.variant.ParametricVariantRegistry;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Base class for single-block MTEs that support multiple variants stored as NBT sub-types
 * within a single MTE ID.
 *
 * <p>This drastically reduces ID consumption and memory usage: instead of registering
 * N separate MTEs for N variants, only one MTE is registered and the variant is
 * stored in NBT.</p>
 *
 * @param <V> the variant value type
 * @see gregtech.api.metatileentity.multiblock.ParametricMultiblockController for the multiblock equivalent
 */
public abstract class ParametricMetaTileEntity<V> extends MetaTileEntity {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final ParametricVariantRegistry<V> variantRegistry;
    @Nullable
    private final Class<V> variantClass;
    private final V defaultVariant;
    private V variant;

    protected ParametricMetaTileEntity(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull ParametricVariantRegistry<V> variantRegistry) {
        this(metaTileEntityId, variantRegistry, variantRegistry.getDefaultVariant());
    }

    protected ParametricMetaTileEntity(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull ParametricVariantRegistry<V> variantRegistry,
                                       @NotNull V defaultVariant) {
        this(metaTileEntityId, variantRegistry, defaultVariant, null);
    }

    /**
     * @deprecated Prefer passing a {@link ParametricVariantRegistry}. This constructor keeps enum-backed
     *             parametric single-block MTEs source-compatible while the base class moves to open registries.
     */
    @Deprecated
    protected ParametricMetaTileEntity(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull Class<V> enumClass,
                                       @NotNull V defaultVariant) {
        this(metaTileEntityId, createEnumRegistry(metaTileEntityId.getNamespace(), enumClass, defaultVariant),
                defaultVariant, enumClass);
    }

    private ParametricMetaTileEntity(@NotNull ResourceLocation metaTileEntityId,
                                     @NotNull ParametricVariantRegistry<V> variantRegistry,
                                     @NotNull V defaultVariant,
                                     @Nullable Class<V> variantClass) {
        super(metaTileEntityId);
        this.variantRegistry = Objects.requireNonNull(variantRegistry, "variantRegistry");
        this.variantClass = variantClass;
        this.defaultVariant = Objects.requireNonNull(defaultVariant, "defaultVariant");
        this.variantRegistry.getId(defaultVariant);
        this.variant = defaultVariant;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @NotNull
    private static <V> ParametricVariantRegistry<V> createEnumRegistry(@NotNull String namespace,
                                                                       @NotNull Class<V> enumClass,
                                                                       @NotNull V defaultVariant) {
        Objects.requireNonNull(enumClass, "enumClass");
        Objects.requireNonNull(defaultVariant, "defaultVariant");
        if (!Enum.class.isAssignableFrom(enumClass) || !(defaultVariant instanceof Enum)) {
            throw new IllegalArgumentException("Legacy parametric constructor requires an enum variant class");
        }
        return (ParametricVariantRegistry<V>) ParametricVariantRegistries.enumRegistry(namespace,
                (Class) enumClass.asSubclass(Enum.class), (Enum) defaultVariant);
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
        this.variant = Objects.requireNonNull(variant, "variant");
        onVariantChanged();
    }

    /**
     * @deprecated Open registries do not necessarily have an enum class. Use {@link #getVariantRegistry()} instead.
     */
    @Deprecated
    @NotNull
    public Class<V> getVariantClass() {
        if (variantClass == null) {
            throw new UnsupportedOperationException("This parametric MTE is backed by an open registry");
        }
        return variantClass;
    }

    @NotNull
    public V getDefaultVariant() {
        return defaultVariant;
    }

    @NotNull
    public ParametricVariantRegistry<V> getVariantRegistry() {
        return variantRegistry;
    }

    @NotNull
    public Collection<V> getVariants() {
        return variantRegistry.getVariants();
    }

    // endregion

    // region NBT Serialization

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString(NBT_KEY_VARIANT, variantRegistry.getId(variant).toString());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        applyVariant(readVariantFromNBT(data), true);
        super.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeString(variantRegistry.getId(variant).toString());
        super.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        applyVariant(readVariantFromId(buf.readString(32767)), true);
        super.receiveInitialSyncData(buf);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound itemStack) {
        super.initFromItemStackData(itemStack);
        if (itemStack.hasKey(NBT_KEY_VARIANT)) {
            applyVariant(readVariantFromNBT(itemStack), true);
        }
    }

    @Override
    public void writeItemStackData(NBTTagCompound itemStack) {
        super.writeItemStackData(itemStack);
        itemStack.setString(NBT_KEY_VARIANT, variantRegistry.getId(variant).toString());
    }

    /**
     * Safely reads a variant from a legacy ordinal value with bounds checking.
     */
    @NotNull
    protected V readVariantFromOrdinal(int ordinal) {
        List<V> values = new ArrayList<>(variantRegistry.getVariants());
        if (ordinal >= 0 && ordinal < values.size()) {
            return values.get(ordinal);
        }
        return defaultVariant;
    }

    /**
     * Reads the current NBT variant format, falling back to the legacy ordinal format.
     */
    @NotNull
    protected V readVariantFromNBT(@NotNull NBTTagCompound data) {
        if (data.hasKey(NBT_KEY_VARIANT, Constants.NBT.TAG_STRING)) {
            return readVariantFromId(data.getString(NBT_KEY_VARIANT));
        }
        if (data.hasKey(NBT_KEY_VARIANT, Constants.NBT.TAG_INT)) {
            return readVariantFromOrdinal(data.getInteger(NBT_KEY_VARIANT));
        }
        return defaultVariant;
    }

    /**
     * Resolves a stable variant id string.
     */
    @NotNull
    protected V readVariantFromId(@Nullable String variantId) {
        if (variantId == null || variantId.isEmpty()) {
            return defaultVariant;
        }
        try {
            return variantRegistry.getOrDefault(new ResourceLocation(variantId));
        } catch (RuntimeException ignored) {
            return defaultVariant;
        }
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
        for (V value : variantRegistry.getVariants()) {
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
        tag.setString(NBT_KEY_VARIANT, variantRegistry.getId(variantValue).toString());
    }

    /**
     * Extracts the variant from an ItemStack's NBT.
     */
    @NotNull
    public V getVariantFromStack(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(NBT_KEY_VARIANT)) {
            return readVariantFromNBT(tag);
        }
        return defaultVariant;
    }

    // endregion

    // region Localization

    /**
     * Returns the translation key prefix for variant-specific names.
     * The full key will be: {@code prefix + "." + variant_name + ".name"}
     */
    @NotNull
    protected abstract String getVariantTranslationPrefix();

    @NotNull
    protected String getVariantName(@NotNull V variantValue) {
        return variantRegistry.getName(variantValue);
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
