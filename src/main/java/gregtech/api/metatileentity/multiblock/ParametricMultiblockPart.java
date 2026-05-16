package gregtech.api.metatileentity.multiblock;

import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

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

/**
 * Base class for multiblock parts that support multiple variants stored as NBT sub-types
 * within a single MTE ID.
 *
 * <p>Mirrors the design of {@link ParametricMultiblockController} but for multiblock parts
 * (hatches, valves, etc.) that also need material/variant differentiation.
 *
 * @param <V> the variant value type
 * @see ParametricMultiblockController
 */
public abstract class ParametricMultiblockPart<V> extends MetaTileEntityMultiblockPart {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> variantRegistry;
    private final Class<V> variantClass;
    private final V defaultVariant;
    private V variant;

    protected ParametricMultiblockPart(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> variantRegistry) {
        super(metaTileEntityId, 0);
        this.variantRegistry = variantRegistry;
        this.variantClass = null;
        this.defaultVariant = variantRegistry.getDefaultVariant();
        this.variant = defaultVariant;
    }

    /**
     * @deprecated Prefer passing a {@link ParametricVariantRegistry}. This constructor keeps enum-backed
     *             parametric multiblock parts source-compatible while the base class moves to open registries.
     */
    @Deprecated
    protected ParametricMultiblockPart(@NotNull ResourceLocation metaTileEntityId,
                                       @NotNull Class<V> variantClass,
                                       @NotNull V defaultVariant) {
        super(metaTileEntityId, 0);
        this.variantRegistry = createEnumRegistry(metaTileEntityId.getNamespace(), variantClass, defaultVariant);
        this.variantClass = variantClass;
        this.defaultVariant = defaultVariant;
        this.variant = defaultVariant;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @NotNull
    private static <V> gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> createEnumRegistry(
            @NotNull String namespace,
            @NotNull Class<V> variantClass,
            @NotNull V defaultVariant) {
        if (!Enum.class.isAssignableFrom(variantClass)) {
            throw new IllegalArgumentException("Legacy parametric constructor requires an enum variant class");
        }
        return (gregtech.api.metatileentity.variant.ParametricVariantRegistry<V>)
                gregtech.api.metatileentity.variant.ParametricVariantRegistries.enumRegistry(namespace,
                (Class) variantClass.asSubclass(Enum.class), (Enum) defaultVariant);
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
        if (variantClass == null) {
            throw new UnsupportedOperationException("This parametric multiblock part is backed by an open registry");
        }
        return variantClass;
    }

    @NotNull
    public V getDefaultVariant() {
        return defaultVariant;
    }

    @NotNull
    public gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> getVariantRegistry() {
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
    public void writeInitialSyncData(PacketBuffer buf) {
        buf.writeString(variantRegistry.getId(variant).toString());
        super.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
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

    @NotNull
    protected V readVariantFromOrdinal(int ordinal) {
        List<V> values = new ArrayList<>(variantRegistry.getVariants());
        if (ordinal >= 0 && ordinal < values.size()) {
            return values.get(ordinal);
        }
        return defaultVariant;
    }

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
     * Subclasses can override to re-initialize state.
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
