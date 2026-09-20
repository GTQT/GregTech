package gregtech.common.pipelike.fiber.recipe;

import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberLight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optical recipe requirement: which colour a recipe wants, and what intensity range it accepts.
 * <p>
 * The range is expressed with a lower and an upper bound, each optional. That single shape covers
 * all three cases asked for:
 * <ul>
 * <li>"above X" — {@link FiberLightRequirement#min} only</li>
 * <li>"below X" — {@link FiberLightRequirement#max} only</li>
 * <li>an interval — both bounds</li>
 * </ul>
 * Both bounds are inclusive, so {@code atLeast(64).atMost(256)} reads as {@code 64 <= flux <= 256}.
 * Bounds may also carry a strict operator ({@code >}, {@code <}) for the open-ended forms.
 */
public final class FiberLightProperty extends RecipeProperty<FiberLightProperty.FiberLightRequirement> {

    public static final String KEY = "fiber_light";

    private static FiberLightProperty INSTANCE;

    private FiberLightProperty() {
        super(KEY, FiberLightRequirement.class);
    }

    public static FiberLightProperty getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FiberLightProperty();
            gregtech.api.GregTechAPI.RECIPE_PROPERTIES.register(KEY, INSTANCE);
        }
        return INSTANCE;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        FiberLightRequirement requirement = castValue(value);
        NBTTagCompound tag = new NBTTagCompound();
        if (requirement.color != null) {
            tag.setInteger("color", requirement.color.getDyeMeta());
        }
        if (requirement.min != null) {
            tag.setLong("min", requirement.min);
            tag.setInteger("minOp", requirement.minOperator.ordinal());
        }
        if (requirement.max != null) {
            tag.setLong("max", requirement.max);
            tag.setInteger("maxOp", requirement.maxOperator.ordinal());
        }
        return tag;
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        NBTTagCompound tag = (NBTTagCompound) nbt;
        FiberLightRequirement requirement = new FiberLightRequirement();
        if (tag.hasKey("color")) {
            requirement.color = FiberColor.byDyeMeta(tag.getInteger("color"));
        }
        if (tag.hasKey("min")) {
            requirement.min = tag.getLong("min");
            requirement.minOperator = FiberIntensityOperator.values()[tag.getInteger("minOp")];
        }
        if (tag.hasKey("max")) {
            requirement.max = tag.getLong("max");
            requirement.maxOperator = FiberIntensityOperator.values()[tag.getInteger("maxOp")];
        }
        return requirement;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {
        minecraft.fontRenderer.drawString(describe(castValue(value), color), x, y, color);
    }

    /** Human readable form of a requirement, e.g. {@code Light: Red, Flux >= 64}. */
    @SideOnly(Side.CLIENT)
    public static String describe(@NotNull FiberLightRequirement requirement, int color) {
        String colourName = requirement.color == null ?
                I18n.format("gregtech.recipe.fiber_light.any_color") :
                I18n.format(requirement.color.getName());
        return I18n.format("gregtech.recipe.fiber_light", colourName, describeIntensity(requirement));
    }

    /** Human readable intensity part of a requirement. */
    @NotNull
    public static String describeIntensity(@NotNull FiberLightRequirement requirement) {
        String min = requirement.min == null ? null :
                requirement.minOperator.getSymbol() + " " +
                        TextFormattingUtil.formatNumbers(requirement.min);
        String max = requirement.max == null ? null :
                requirement.maxOperator.getSymbol() + " " +
                        TextFormattingUtil.formatNumbers(requirement.max);
        if (min != null && max != null) return min + " & " + max;
        if (min != null) return min;
        if (max != null) return max;
        return TextFormatting.GREEN + I18n.format("gregtech.recipe.fiber_light.any_intensity");
    }

    /**
     * The requirement carried by an optical recipe.
     * <p>
     * Mutable builder-style so it can be filled in field by field. A {@code null} colour means any
     * colour; a {@code null} bound means that side is unbounded.
     */
    @Desugar
    public static final class FiberLightRequirement {

        @Nullable
        private FiberColor color;
        @Nullable
        private Long min;
        @Nullable
        private Long max;
        @NotNull
        private FiberIntensityOperator minOperator = FiberIntensityOperator.GREATER_OR_EQUAL;
        @NotNull
        private FiberIntensityOperator maxOperator = FiberIntensityOperator.LESS_OR_EQUAL;

        public FiberLightRequirement() {}

        /** Restricts the requirement to one colour. */
        @NotNull
        public FiberLightRequirement color(@NotNull FiberColor color) {
            this.color = color;
            return this;
        }

        /** Requires the flux to be at least {@code min} (inclusive). */
        @NotNull
        public FiberLightRequirement atLeast(long min) {
            this.min = min;
            this.minOperator = FiberIntensityOperator.GREATER_OR_EQUAL;
            return this;
        }

        /** Requires the flux to be strictly above {@code min}. */
        @NotNull
        public FiberLightRequirement above(long min) {
            this.min = min;
            this.minOperator = FiberIntensityOperator.GREATER_THAN;
            return this;
        }

        /** Requires the flux to be at most {@code max} (inclusive). */
        @NotNull
        public FiberLightRequirement atMost(long max) {
            this.max = max;
            this.maxOperator = FiberIntensityOperator.LESS_OR_EQUAL;
            return this;
        }

        /** Requires the flux to be strictly below {@code max}. */
        @NotNull
        public FiberLightRequirement below(long max) {
            this.max = max;
            this.maxOperator = FiberIntensityOperator.LESS_THAN;
            return this;
        }

        /** Requires the exact flux {@code intensity}. */
        @NotNull
        public FiberLightRequirement exactly(long intensity) {
            this.min = intensity;
            this.max = intensity;
            this.minOperator = FiberIntensityOperator.GREATER_OR_EQUAL;
            this.maxOperator = FiberIntensityOperator.LESS_OR_EQUAL;
            return this;
        }

        /** A closed interval {@code min <= flux <= max}. */
        @NotNull
        public FiberLightRequirement between(long min, long max) {
            if (min > max) {
                throw new IllegalArgumentException("min " + min + " > max " + max);
            }
            return atLeast(min).atMost(max);
        }

        @Nullable
        public FiberColor getColor() {
            return color;
        }

        @Nullable
        public Long getMin() {
            return min;
        }

        @Nullable
        public Long getMax() {
            return max;
        }

        /** Whether {@code light} satisfies both the colour and the intensity range. */
        public boolean test(@NotNull FiberLight light) {
            if (light.isEmpty()) return false;
            if (color != null && color != light.color()) return false;
            if (min != null && !minOperator.test(light.intensity(), min)) return false;
            if (max != null && !maxOperator.test(light.intensity(), max)) return false;
            return true;
        }

        /** Whether {@code other} is at least as permissive as this requirement. */
        public boolean test(@NotNull FiberLightRequirement other) {
            if (color != null && color != other.color) return false;
            if (min != null && (other.min == null || other.min < min)) return false;
            if (max != null && (other.max == null || other.max > max)) return false;
            return true;
        }
    }
}
