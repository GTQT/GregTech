package gregtech.api.recipes.ingredients;

import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.ListNBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.util.GTLog;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Definition of ItemStacks, Ore dicts, of ingredients for
 * use on RecipeMaps Recipes go here.
 * <p>
 * Forge uses are nor Hashable neither implement equals for these cases,
 * as they use a list of ItemStacks internally.
 * <p>
 * The behavior of the ingredient is determined by the GTRecipeInput used.
 * <p>
 * Each GTRecipeInput is cached by an internal hashtable, and any duplicative
 * instances will be replaced by identical object previously created. This
 * caching strategy is turned off after recipe registration is over.
 */
public abstract class GTRecipeInput {

    /**
     * Sorting order of standard recipe inputs.
     */
    public static final int SORTING_ORDER_COMMON = 0;
    /**
     * Sorting order of non-consumable recipe inputs.
     */
    public static final int SORTING_ORDER_NC = 5;
    /**
     * Sorting order of non-consumable {@link IntCircuitIngredient}s.
     */
    public static final int SORTING_ORDER_INT_CIRCUIT = 10;

    public static final Comparator<GTRecipeInput> RECIPE_INPUT_COMPARATOR = Comparator
            .comparingInt(GTRecipeInput::getSortingOrder);

    /**
     * All items will initially match the with is NBT (OreDicts have a null tag?)
     * but this behavior can be changed by using a NBTMatcher and an appropriate NBTCondition.
     */

    protected int amount;
    protected boolean isConsumable = true;
    protected NBTMatcher nbtMatcher;
    protected NBTCondition nbtCondition;

    private boolean cached;

    private int hash;
    protected boolean hashCached;

    /**
     * @deprecated Calling this function is unnecessary. Use the ingredient directly.
     */
    @Deprecated
    public static GTRecipeInput getOrCreate(GTRecipeInput gtRecipeIngredient) {
        return gtRecipeIngredient;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached() {
        this.cached = true;
    }

    protected abstract GTRecipeInput copy();

    /**
     * Returns a copy of the ingredient with the given amount.
     * Used by the parallel logic to multiply the amount of the ingredients.
     * If you're not using the parallel logic, you can ignore this.
     *
     * @return returns a copy of the GTRecipeInput with the given amount.
     */
    public abstract GTRecipeInput copyWithAmount(int amount);

    /**
     * Returns either this instance with {@link GTRecipeInput#amount} field modified (for non-cached recipe inputs)
     * or new copy with given amount (for cached recipe inputs).
     */
    public GTRecipeInput withAmount(int amount) {
        if (getAmount() == amount) {
            return this;
        } else if (isCached()) {
            return copyWithAmount(amount);
        } else {
            this.amount = amount;
            this.hashCached = false;
            return this;
        }
    }

    public GTRecipeInput setNonConsumable() {
        if (!isConsumable) return this;
        GTRecipeInput recipeInput = cached ? copy() : this;
        recipeInput.isConsumable = false;
        recipeInput.hashCached = false;
        return recipeInput;
    }

    public GTRecipeInput setNBTMatchingCondition(NBTMatcher nbtMatcher, NBTCondition nbtCondition) {
        GTRecipeInput recipeInput = cached ? copy() : this;
        recipeInput.nbtMatcher = nbtMatcher;
        recipeInput.nbtCondition = nbtCondition;
        recipeInput.hashCached = false;
        return recipeInput;
    }

    public boolean hasNBTMatchingCondition() {
        return nbtMatcher != null;
    }

    public NBTMatcher getNBTMatcher() {
        return nbtMatcher;
    }

    public NBTCondition getNBTMatchingCondition() {
        return nbtCondition;
    }

    public boolean isNonConsumable() {
        return !isConsumable;
    }

    public ItemStack[] getInputStacks() {
        return null;
    }

    public FluidStack getInputFluidStack() {
        return null;
    }

    public boolean isOreDict() {
        return false;
    }

    public int getOreDict() {
        return -1;
    }

    public boolean acceptsStack(@Nullable ItemStack input) {
        return false;
    }

    public boolean acceptsFluid(@Nullable FluidStack input) {
        return false;
    }

    @Override
    public int hashCode() {
        if (!this.hashCached) {
            this.hash = computeHash();
            this.hashCached = true;
        }
        return this.hash;
    }

    protected abstract int computeHash();

    @Override
    public abstract boolean equals(Object obj);

    /**
     * @return true if the input matches another input, while ignoring its amount field and
     *         non-consumable status.
     *         <p>
     *         used for unique input matching in RecipeMap
     * @see gregtech.api.recipes.RecipeMap#uniqueIngredientsList(Collection) RecipeMap#uniqueIngredientsList(Collection)
     */
    public abstract boolean equalIgnoreAmount(GTRecipeInput input);

    /**
     * Get sorting order of this recipe input instance. Recipe inputs are sorted with ascending order.
     *
     * @return sorting order of this recipe input instance
     * @see #SORTING_ORDER_COMMON
     * @see #SORTING_ORDER_NC
     * @see #SORTING_ORDER_INT_CIRCUIT
     */
    public int getSortingOrder() {
        return this.isNonConsumable() ? SORTING_ORDER_NC : SORTING_ORDER_COMMON;
    }

    public static NBTTagCompound writeToNBT(GTRecipeInput input) {
        NBTTagCompound tag = new NBTTagCompound();
        if (input instanceof GTRecipeItemInput) {
            NBTTagList stackList = new NBTTagList();
            for (ItemStack stack : input.getInputStacks()) {
                stackList.appendTag(stack.serializeNBT());
            }
            tag.setTag("stacks", stackList);
        } else if (input instanceof GTRecipeOreInput) {
            tag.setInteger("ore", input.getOreDict());
        } else if (input instanceof GTRecipeFluidInput) {
            tag.setTag("fluid", input.getInputFluidStack().writeToNBT(new NBTTagCompound()));
        }
        tag.setInteger("amount", input.getAmount());
        return tag;
    }

    /**
     * Returns every stable fact that affects this input's recipe-matching semantics.
     *
     * <p>This is deliberately separate from {@link #writeToNBT(GTRecipeInput)}. The latter is a legacy
     * round-trip format and stores an OreDictionary's runtime numeric ID, which is not valid as a persistent
     * identity across a restart. This format is an identity record only and is not accepted by
     * {@link #readFromNBT(NBTTagCompound)}.</p>
     */
    public static NBTTagCompound writePersistentIdentityToNBT(GTRecipeInput input) {
        Objects.requireNonNull(input, "input");

        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("inputClass", input.getClass().getName());
        tag.setInteger("amount", input.getAmount());
        tag.setBoolean("consumable", !input.isNonConsumable());
        appendPersistentMatchingIdentity(tag, input);

        if (input instanceof GTRecipeOreInput) {
            tag.setString("kind", "ore_dict");
            tag.setString("oreName", OreDictionary.getOreName(input.getOreDict()));
        } else if (input instanceof GTRecipeFluidInput) {
            tag.setString("kind", "fluid");
            FluidStack fluid = input.getInputFluidStack();
            if (fluid != null) {
                tag.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
            }
        } else if (input instanceof GTRecipeItemInput || input instanceof IntCircuitIngredient) {
            tag.setString("kind", input instanceof IntCircuitIngredient ? "integrated_circuit" : "item_stacks");
            NBTTagList stackList = new NBTTagList();
            ItemStack[] stacks = input.getInputStacks();
            if (stacks != null) {
                for (ItemStack stack : stacks) {
                    stackList.appendTag(stack == null ? new NBTTagCompound() : stack.serializeNBT());
                }
            }
            tag.setTag("stacks", stackList);
        } else {
            // Third-party input implementations do not expose a complete persistent identity contract. Keep them
            // run-local so an external implementation cannot bind to a different recipe after restart.
            tag.setString("kind", "custom");
            tag.setString("instance", Integer.toUnsignedString(System.identityHashCode(input)));
        }
        return tag;
    }

    private static void appendPersistentMatchingIdentity(NBTTagCompound tag, GTRecipeInput input) {
        NBTMatcher matcher = input.getNBTMatcher();
        tag.setString("nbtMatcher", describePersistentMatcher(matcher));
        NBTCondition condition = input.getNBTMatchingCondition();
        if (condition != null) {
            tag.setTag("nbtCondition", writePersistentConditionIdentity(condition));
        }
    }

    private static String describePersistentMatcher(@Nullable NBTMatcher matcher) {
        if (matcher == null) return "none";
        if (matcher == NBTMatcher.ANY) return "any";
        if (matcher == NBTMatcher.LESS_THAN) return "less_than";
        if (matcher == NBTMatcher.LESS_THAN_OR_EQUAL_TO) return "less_than_or_equal_to";
        if (matcher == NBTMatcher.GREATER_THAN) return "greater_than";
        if (matcher == NBTMatcher.GREATER_THAN_OR_EQUAL_TO) return "greater_than_or_equal_to";
        if (matcher == NBTMatcher.EQUAL_TO) return "equal_to";
        if (matcher == NBTMatcher.RECURSIVE_EQUAL_TO) return "recursive_equal_to";
        if (matcher == NBTMatcher.NOT_PRESENT_OR_DEFAULT) return "not_present_or_default";
        if (matcher == NBTMatcher.NOT_PRESENT_OR_HAS_KEY) return "not_present_or_has_key";

        // Unknown matcher implementations have no public persistence contract. Keep their identity run-local so a
        // saved external matcher is rejected after restart instead of being mistaken for a different matcher.
        return "custom:" + matcher.getClass().getName() + ':' +
                Integer.toUnsignedString(System.identityHashCode(matcher));
    }

    private static NBTTagCompound writePersistentConditionIdentity(NBTCondition condition) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("conditionClass", condition.getClass().getName());
        tag.setString("tagType", condition.tagType == null ? "none" : condition.tagType.name());
        tag.setString("key", condition.nbtKey == null ? "" : condition.nbtKey);
        if (condition instanceof ListNBTCondition listCondition) {
            tag.setString("listTagType", listCondition.listTagType == null ? "none" :
                    listCondition.listTagType.name());
        }
        tag.setTag("value", writePersistentConditionValue(condition.value));
        return tag;
    }

    private static NBTTagCompound writePersistentConditionValue(@Nullable Object value) {
        NBTTagCompound tag = new NBTTagCompound();
        if (value == null) {
            tag.setString("kind", "null");
        } else if (value instanceof NBTBase nbt) {
            tag.setString("kind", "nbt");
            tag.setTag("value", nbt.copy());
        } else if (value instanceof NBTCondition condition) {
            tag.setString("kind", "condition");
            tag.setTag("value", writePersistentConditionIdentity(condition));
        } else if (value instanceof List<?>) {
            NBTTagList values = new NBTTagList();
            for (Object element : (List<?>) value) {
                if (!(element instanceof NBTBase nbt)) {
                    return writeRunLocalConditionValue(value);
                }
                values.appendTag(nbt.copy());
            }
            tag.setString("kind", "nbt_list");
            tag.setTag("value", values);
        } else if (value instanceof byte[]) {
            tag.setString("kind", "byte_array");
            tag.setString("value", Arrays.toString((byte[]) value));
        } else if (value instanceof int[]) {
            tag.setString("kind", "int_array");
            tag.setString("value", Arrays.toString((int[]) value));
        } else if (value instanceof long[]) {
            tag.setString("kind", "long_array");
            tag.setString("value", Arrays.toString((long[]) value));
        } else if (value instanceof Number || value instanceof Boolean || value instanceof String ||
                value instanceof Character || value.getClass().isEnum()) {
            tag.setString("kind", value.getClass().getName());
            tag.setString("value", String.valueOf(value));
        } else {
            return writeRunLocalConditionValue(value);
        }
        return tag;
    }

    private static NBTTagCompound writeRunLocalConditionValue(Object value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("kind", "custom:" + value.getClass().getName());
        tag.setString("instance", Integer.toUnsignedString(System.identityHashCode(value)));
        return tag;
    }

    public static GTRecipeInput readFromNBT(NBTTagCompound tag) {
        int amount = tag.getInteger("amount");
        if (tag.hasKey("stacks")) {
            NBTTagList list = tag.getTagList("stacks", Constants.NBT.TAG_COMPOUND);
            ItemStack[] stacks = new ItemStack[list.tagCount()];
            Arrays.setAll(stacks, i -> new ItemStack(list.getCompoundTagAt(i)));
            return new GTRecipeItemInput(stacks, amount);

        } else if (tag.hasKey("ore")) {
            return new GTRecipeOreInput(tag.getInteger("ore"), amount);
        } else if (tag.hasKey("fluid")) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluid"));
            return new GTRecipeFluidInput(Objects.requireNonNull(stack), amount);
        }
        GTLog.logger.warn("unable to read tag!: " + tag);
        return null;
    }

    protected static class ItemToMetaList implements Object2ObjectMap.Entry<Item, List<MetaToTAGList>> {

        protected Item item;
        protected List<MetaToTAGList> metaToTAGList;

        public ItemToMetaList(ItemStack stack) {
            this.item = stack.getItem();
            this.metaToTAGList = ObjectLists.singleton(new MetaToTAGList(stack));
        }

        void addStackToLists(ItemStack stack) {
            if (this.metaToTAGList instanceof ObjectLists.Singleton) {
                this.metaToTAGList = new ObjectArrayList<>(this.metaToTAGList);
            }
            this.metaToTAGList.add(new MetaToTAGList(stack));
        }

        @Override
        public Item getKey() {
            return item;
        }

        @Override
        public List<MetaToTAGList> getValue() {
            return metaToTAGList;
        }

        @Override
        public List<MetaToTAGList> setValue(List<MetaToTAGList> value) {
            return metaToTAGList = value;
        }
    }

    protected static class MetaToTAGList implements Int2ObjectMap.Entry<List<TagToStack>> {

        protected int meta;
        protected List<TagToStack> tagToStack;

        public MetaToTAGList(ItemStack stack) {
            this.meta = stack.getMetadata();
            this.tagToStack = ObjectLists.singleton(new TagToStack(stack));
        }

        void addStackToList(ItemStack stack) {
            if (this.tagToStack instanceof ObjectLists.Singleton) {
                this.tagToStack = new ObjectArrayList<>(this.tagToStack);
            }
            this.tagToStack.add(new TagToStack(stack.getTagCompound(), stack));
        }

        @Override
        public Integer getKey() {
            return meta;
        }

        @Override
        public int getIntKey() {
            return meta;
        }

        @Override
        public List<TagToStack> getValue() {
            return tagToStack;
        }

        @Override
        public List<TagToStack> setValue(List<TagToStack> value) {
            return tagToStack = value;
        }
    }

    protected static class TagToStack implements Object2ObjectMap.Entry<NBTTagCompound, ItemStack> {

        NBTTagCompound tag;
        ItemStack stack;

        TagToStack(NBTTagCompound tag, ItemStack stack) {
            this.tag = tag;
            this.stack = stack;
        }

        TagToStack(ItemStack stack) {
            this.tag = stack.getTagCompound();
            this.stack = stack;
        }

        @Override
        public NBTTagCompound getKey() {
            return tag;
        }

        @Override
        public ItemStack getValue() {
            return stack;
        }

        @Override
        public ItemStack setValue(ItemStack value) {
            return stack = value;
        }
    }
}
