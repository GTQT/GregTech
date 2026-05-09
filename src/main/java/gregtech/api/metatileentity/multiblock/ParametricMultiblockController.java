package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.LazyTemplate;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

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
 *     private static final Map<TankMaterial, LazyTemplate> TEMPLATES = buildTemplateCache(
 *             TankMaterial.class, mat -> LazyTemplate.of(() -> createTemplate(mat)));
 *
 *     public MetaTileEntityMultiblockTank(ResourceLocation id) {
 *         super(id, TankMaterial.class, TankMaterial.WOOD);
 *     }
 *
 *     @Override protected Map<TankMaterial, LazyTemplate> getTemplateCache() { return TEMPLATES; }
 *     @Override protected String getVariantTranslationPrefix() { return "gregtech.machine.tank"; }
 * }
 * }</pre>
 *
 * <h3>Memory Model:</h3>
 * <ul>
 *   <li>1 MTE ID in the registry (regardless of variant count)</li>
 *   <li>1 LazyTemplate per variant (created on first use)</li>
 *   <li>Variant stored in NBT for TileEntity persistence, ItemStack, and network sync</li>
 * </ul>
 *
 * @param <V> the variant enum type
 */
public abstract class ParametricMultiblockController<V extends Enum<V>>
        extends MultiblockWithDisplayBase {

    protected static final String NBT_KEY_VARIANT = "Variant";

    private final Class<V> variantClass;
    private final V defaultVariant;
    private V variant;

    protected ParametricMultiblockController(@NotNull ResourceLocation metaTileEntityId,
                                             @NotNull Class<V> variantClass,
                                             @NotNull V defaultVariant) {
        super(metaTileEntityId);
        this.variantClass = variantClass;
        this.defaultVariant = defaultVariant;
        this.variant = defaultVariant;
    }

    // region Variant Access

    /**
     * Returns the current variant of this multiblock instance.
     */
    @NotNull
    public V getVariant() {
        return variant;
    }

    /**
     * Sets the variant. Should only be called during initialization (NBT load, item placement).
     */
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

    // region Template Cache

    /**
     * Returns the template cache mapping variants to their lazy templates.
     * Subclasses should return a static field to ensure templates are shared across instances.
     */
    @NotNull
    protected abstract Map<V, LazyTemplate> getTemplateCache();

    /**
     * Utility to build a template cache for a given enum class.
     *
     * @param enumClass the variant enum class
     * @param factory   function that creates a LazyTemplate for each variant
     * @return immutable EnumMap of variant → LazyTemplate
     */
    @NotNull
    protected static <V extends Enum<V>> Map<V, LazyTemplate> buildTemplateCache(
            @NotNull Class<V> enumClass,
            @NotNull Function<V, LazyTemplate> factory) {
        Map<V, LazyTemplate> cache = new EnumMap<>(enumClass);
        for (V value : enumClass.getEnumConstants()) {
            cache.put(value, factory.apply(value));
        }
        return cache;
    }

    @Override
    @NotNull
    protected BlockPatternTemplate createStructureTemplate() {
        return getTemplateCache().get(variant).get();
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
        return mat.name().toLowerCase();
    }

    /**
     * Creates an ItemStack for this multiblock with a specific variant.
     */
    @NotNull
    public ItemStack getStackForm(@NotNull V variantValue) {
        ItemStack stack = getStackForm();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(NBT_KEY_VARIANT, variantValue.ordinal());
        return stack;
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
     * <p>Example: prefix = "gregtech.machine.tank" → key = "gregtech.machine.tank.bronze.name"
     */
    @NotNull
    protected abstract String getVariantTranslationPrefix();

    @Override
    public String getMetaName() {
        return getVariantTranslationPrefix() + "." + variant.name().toLowerCase();
    }

    // endregion
}
