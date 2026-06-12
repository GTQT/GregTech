package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Last observable structure failure for a controller runtime.
 *
 * <p>This value intentionally stores only information already known by the
 * existing checker so diagnostics can improve without changing match behavior.
 */
public final class StructureFailureTrace {

    @NotNull
    private final String controllerId;
    @NotNull
    private final BlockPos controllerPos;
    private final boolean formed;
    @NotNull
    private final EnumFacing front;
    @NotNull
    private final EnumFacing structureFront;
    @NotNull
    private final EnumFacing up;
    private final boolean flipped;
    @NotNull
    private final String path;
    @NotNull
    private final String operation;
    @NotNull
    private final String result;
    @Nullable
    private final BlockPos errorPos;
    @Nullable
    private final String expected;
    @Nullable
    private final String actual;
    @Nullable
    private final PatternError error;
    @NotNull
    private final String missingAbilities;

    private StructureFailureTrace(@NotNull Builder builder) {
        this.controllerId = builder.controllerId;
        this.controllerPos = builder.controllerPos;
        this.formed = builder.formed;
        this.front = builder.front;
        this.structureFront = builder.structureFront;
        this.up = builder.up;
        this.flipped = builder.flipped;
        this.path = builder.path;
        this.operation = builder.operation;
        this.result = builder.result;
        this.errorPos = builder.errorPos;
        this.expected = builder.expected;
        this.actual = builder.actual;
        this.error = builder.error;
        this.missingAbilities = builder.missingAbilities;
    }

    @NotNull
    public static StructureFailureTrace fromController(@NotNull MultiblockControllerBase controller,
                                                       @NotNull String path,
                                                       @NotNull String operation,
                                                       @Nullable PatternError error,
                                                       @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        Builder builder = new Builder(controller.getMetaName(), controller.getPos())
                .formed(controller.isStructureFormed())
                .orientation(StructureOrientation.fromController(controller))
                .path(path)
                .operation(operation)
                .result("failed")
                .missingAbilities(missingAbilities);
        return builder.error(error).build();
    }

    @NotNull
    public String getControllerId() {
        return controllerId;
    }

    @NotNull
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isFormed() {
        return formed;
    }

    @NotNull
    public EnumFacing getFront() {
        return front;
    }

    @NotNull
    public EnumFacing getStructureFront() {
        return structureFront;
    }

    @NotNull
    public EnumFacing getUp() {
        return up;
    }

    public boolean isFlipped() {
        return flipped;
    }

    @NotNull
    public String getPath() {
        return path;
    }

    @NotNull
    public String getOperation() {
        return operation;
    }

    @NotNull
    public String getResult() {
        return result;
    }

    @Nullable
    public BlockPos getErrorPos() {
        return errorPos;
    }

    @Nullable
    public String getExpected() {
        return expected;
    }

    @Nullable
    public String getActual() {
        return actual;
    }

    @Nullable
    public PatternError getError() {
        return error;
    }

    @NotNull
    public String getMissingAbilities() {
        return missingAbilities;
    }

    @Override
    public String toString() {
        return "controller=" + controllerId +
                ", controllerPos=" + controllerPos +
                ", formed=" + formed +
                ", front=" + front +
                ", structureFront=" + structureFront +
                ", up=" + up +
                ", flipped=" + flipped +
                ", path=" + path +
                ", operation=" + operation +
                ", result=" + result +
                ", errorPos=" + errorPos +
                ", expected=" + expected +
                ", actual=" + actual +
                ", missingAbilities=" + missingAbilities;
    }

    @NotNull
    public static String describeMissingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (missingAbilities.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (Map.Entry<MultiblockAbility<?>, Integer> entry : missingAbilities.entrySet()) {
            if (entry.getValue() > 0) {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return joiner.toString();
    }

    @Nullable
    private static BlockPos getErrorPos(@Nullable PatternError error) {
        if (error == null) {
            return null;
        }
        try {
            return error.getPos();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String describeExpected(@Nullable PatternError error) {
        if (error == null) {
            return null;
        }
        try {
            List<List<ItemStack>> candidates = error.getCandidates();
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            StringJoiner joiner = new StringJoiner(", ");
            for (List<ItemStack> group : candidates) {
                if (group == null || group.isEmpty()) {
                    continue;
                }
                ItemStack stack = group.get(0);
                if (stack != null && !stack.isEmpty()) {
                    joiner.add(stack.getDisplayName());
                }
            }
            String result = joiner.toString();
            return result.isEmpty() ? null : result;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static String describeActual(@Nullable PatternError error) {
        if (error == null) {
            return null;
        }
        try {
            BlockWorldState worldState = error.getWorldState();
            if (worldState == null) {
                return null;
            }
            IBlockState blockState = worldState.getBlockState();
            TileEntity tileEntity = worldState.getTileEntity();
            String block = blockState == null ? "null" : String.valueOf(blockState);
            return tileEntity == null ? block : block + ", tile=" + tileEntity.getClass().getName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static final class Builder {

        @NotNull
        private final String controllerId;
        @NotNull
        private final BlockPos controllerPos;
        private boolean formed;
        @NotNull
        private EnumFacing front = EnumFacing.NORTH;
        @NotNull
        private EnumFacing structureFront = EnumFacing.NORTH;
        @NotNull
        private EnumFacing up = EnumFacing.UP;
        private boolean flipped;
        @NotNull
        private String path = "unknown";
        @NotNull
        private String operation = "CHECK";
        @NotNull
        private String result = "failed";
        @Nullable
        private BlockPos errorPos;
        @Nullable
        private String expected;
        @Nullable
        private String actual;
        @Nullable
        private PatternError error;
        @NotNull
        private String missingAbilities = "{}";

        public Builder(@NotNull String controllerId, @NotNull BlockPos controllerPos) {
            this.controllerId = controllerId;
            this.controllerPos = controllerPos;
        }

        @NotNull
        public Builder formed(boolean formed) {
            this.formed = formed;
            return this;
        }

        @NotNull
        public Builder orientation(@NotNull EnumFacing front, @NotNull EnumFacing structureFront,
                                   @NotNull EnumFacing up, boolean flipped) {
            this.front = front;
            this.structureFront = structureFront;
            this.up = up;
            this.flipped = flipped;
            return this;
        }

        @NotNull
        public Builder orientation(@NotNull StructureOrientation orientation) {
            return orientation(
                    orientation.getFront(),
                    orientation.getStructureFront(),
                    orientation.getUp(),
                    orientation.isFlipped());
        }

        @NotNull
        public Builder path(@NotNull String path) {
            this.path = path;
            return this;
        }

        @NotNull
        public Builder operation(@NotNull String operation) {
            this.operation = operation;
            return this;
        }

        @NotNull
        public Builder result(@NotNull String result) {
            this.result = result;
            return this;
        }

        @NotNull
        public Builder error(@Nullable PatternError error) {
            this.error = error;
            this.errorPos = getErrorPos(error);
            this.expected = describeExpected(error);
            this.actual = describeActual(error);
            return this;
        }

        @NotNull
        public Builder errorPosition(@Nullable BlockPos errorPos) {
            this.errorPos = errorPos;
            return this;
        }

        @NotNull
        public Builder actual(@Nullable String actual) {
            this.actual = actual;
            return this;
        }

        @NotNull
        public Builder missingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
            this.missingAbilities = describeMissingAbilities(missingAbilities);
            return this;
        }

        @NotNull
        public StructureFailureTrace build() {
            return new StructureFailureTrace(this);
        }
    }
}
