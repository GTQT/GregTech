package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for multiblocks that support multiple variants stored as NBT sub-types
 * within a single MTE ID.
 *
 * <p>This drastically reduces ID consumption and memory usage: instead of registering
 * N separate MTEs for N variants, only one MTE is registered and the variant is
 * stored in NBT.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * public class MetaTileEntityMultiblockTank extends ParametricMultiblockController<TankMaterial> {
 *
 *     public MetaTileEntityMultiblockTank(ResourceLocation id) {
 *         super(id, TankMaterial.class, TankMaterial.WOOD);
 *     }
 *
 *     @Override protected BlockPatternTemplate buildStructureTemplate(TankMaterial mat) { return createTemplate(mat); }
 *     @Override protected String getVariantTranslationPrefix() { return "gregtech.machine.tank"; }
 * }
 * }</pre>
 *
 * <h3>Memory Model:</h3>
 * <ul>
 *   <li>1 MTE ID in the registry (regardless of variant count)</li>
 *   <li>1 SoftTemplate per variant (created on first use, reclaimable under memory pressure)</li>
 *   <li>Variant stored in NBT for TileEntity persistence, ItemStack, and network sync</li>
 * </ul>
 *
 * @param <V> the variant value type
 */
public abstract class ParametricMultiblockController<V>
        extends MultiblockWithDisplayBase {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> variantRegistry;
    private final Class<V> variantClass;
    private final V defaultVariant;
    private final Map<ResourceLocation, SoftTemplate> templateCache = new ConcurrentHashMap<>();
    private V variant;

    protected ParametricMultiblockController(@NotNull ResourceLocation metaTileEntityId,
                                             @NotNull gregtech.api.metatileentity.variant.ParametricVariantRegistry<V> variantRegistry) {
        super(metaTileEntityId);
        this.variantRegistry = variantRegistry;
        this.variantClass = null;
        this.defaultVariant = variantRegistry.getDefaultVariant();
        this.variant = defaultVariant;
    }

    /**
     * @deprecated Prefer passing a {@link ParametricVariantRegistry}. This constructor keeps enum-backed
     *             parametric multiblocks source-compatible while the base class moves to open registries.
     */
    @Deprecated
    protected ParametricMultiblockController(@NotNull ResourceLocation metaTileEntityId,
                                             @NotNull Class<V> variantClass,
                                             @NotNull V defaultVariant) {
        super(metaTileEntityId);
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
     * Returns the current variant of this multiblock instance.
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
     * Sets the variant. Called during initialization (NBT load, item placement)
     * or when creating variant copies for JEI integration.
     */
    public void setVariant(@NotNull V variant) {
        applyVariant(variant, false);
    }

    private void applyVariant(@NotNull V variant, boolean force) {
        if (!force && this.variant == variant) {
            return;
        }
        this.variant = variant;
        onVariantChanged();
        if (this.patternTemplate != null) {
            reinitializeStructurePattern();
        }
    }

    @NotNull
    public Class<V> getVariantClass() {
        if (variantClass == null) {
            throw new UnsupportedOperationException("This parametric multiblock is backed by an open registry");
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

    // region Template Cache

    /**
     * Utility to build a template cache for a given enum class, registering each variant
     * into the global {@link TemplatePool}.
     *
     * @param poolKeyPrefix the pool key prefix (e.g. "gregtech:multiblock_tank")
     * @param enumClass     the variant enum class
     * @param factory       function that creates a template Supplier for each variant
     * @return immutable EnumMap of variant → SoftTemplate
     * @see TemplatePool#buildEnumCache(String, Class, Function) for the underlying implementation
     */
    @NotNull
    protected static <V extends Enum<V>> Map<V, SoftTemplate> buildTemplateCache(
            @NotNull String poolKeyPrefix,
            @NotNull Class<V> enumClass,
            @NotNull Function<V, Supplier<BlockPatternTemplate>> factory) {
        return TemplatePool.buildEnumCache(poolKeyPrefix, enumClass, factory);
    }

    @Override
    @NotNull
    protected BlockPatternTemplate createStructureTemplate() {
        V currentVariant = getVariant();
        ResourceLocation variantId = variantRegistry.getId(currentVariant);
        return templateCache.computeIfAbsent(variantId,
                id -> TemplatePool.getInstance().register(getTemplatePoolKey(currentVariant),
                        () -> buildStructureTemplate(currentVariant)))
                .get();
    }

    @NotNull
    protected String getTemplatePoolKey(@NotNull V variantValue) {
        return metaTileEntityId + "/" + variantRegistry.getId(variantValue);
    }

    @NotNull
    protected abstract BlockPatternTemplate buildStructureTemplate(@NotNull V variantValue);

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

    /**
     * Safely reads a variant from an ordinal value with bounds checking.
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

    private int getVariantOrdinal(@NotNull V variantValue) {
        int index = 0;
        for (V value : variantRegistry.getVariants()) {
            if (value == variantValue) {
                return index;
            }
            index++;
        }
        throw new IllegalArgumentException("Unknown parametric variant: " + variantValue);
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
     * Creates an ItemStack for this multiblock with a specific variant.
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
     * The full key will be: {@code prefix + "." + variant_name_lowercase + ".name"}
     *
     * <p>Example: prefix = "gregtech.machine.tank" → key = "gregtech.machine.tank.bronze.name"
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
