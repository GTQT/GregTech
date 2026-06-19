package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Request shape for iterating a repeatable multi-piece slice set.
 */
public final class RepeatIterationRequest {

    @NotNull
    private final BlockPos controllerOrigin;
    @NotNull
    private final StructureOrientation orientation;
    @Nullable
    private final FormedStructureMetadata prior;
    @Nullable
    private final Map<String, Integer> channelValues;

    private RepeatIterationRequest(@NotNull BlockPos controllerOrigin,
                                   @NotNull StructureOrientation orientation,
                                   @Nullable FormedStructureMetadata prior,
                                   @Nullable Map<String, Integer> channelValues) {
        this.controllerOrigin = controllerOrigin;
        this.orientation = orientation;
        this.prior = prior;
        this.channelValues = channelValues;
    }

    @NotNull
    public static RepeatIterationRequest of(@NotNull BlockPos controllerOrigin,
                                            @NotNull StructureOrientation orientation,
                                            @Nullable FormedStructureMetadata prior,
                                            @Nullable Map<String, Integer> channelValues) {
        return new RepeatIterationRequest(controllerOrigin, orientation, prior, channelValues);
    }

    @NotNull
    public BlockPos getControllerOrigin() {
        return controllerOrigin;
    }

    @NotNull
    public StructureOrientation getOrientation() {
        return orientation;
    }

    @Nullable
    public FormedStructureMetadata getPrior() {
        return prior;
    }

    @Nullable
    public Map<String, Integer> getChannelValues() {
        return channelValues;
    }
}
