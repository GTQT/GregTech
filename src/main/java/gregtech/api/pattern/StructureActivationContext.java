package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Explicit input used to decide whether a conditional structure piece is active.
 *
 * @param <T> controller type expected by the condition
 */
public final class StructureActivationContext<T> {

    private static final StructureActivationContext<?> EMPTY =
            new StructureActivationContext<>(null, null, null, null, null);

    @Nullable
    private final T controller;
    @Nullable
    private final World world;
    @Nullable
    private final BlockPos controllerPos;
    @Nullable
    private final FormedStructureMetadata prior;
    @Nullable
    private final StructureMatchSession session;

    public StructureActivationContext(@Nullable T controller, @Nullable World world,
                                      @Nullable BlockPos controllerPos,
                                      @Nullable FormedStructureMetadata prior,
                                      @Nullable StructureMatchSession session) {
        this.controller = controller;
        this.world = world;
        this.controllerPos = controllerPos;
        this.prior = prior;
        this.session = session;
    }

    @SuppressWarnings("unchecked")
    public static <T> StructureActivationContext<T> empty() {
        return (StructureActivationContext<T>) EMPTY;
    }

    @Nullable
    public T getController() {
        return controller;
    }

    @Nullable
    public World getWorld() {
        return world;
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Nullable
    public FormedStructureMetadata getPrior() {
        return prior;
    }

    @Nullable
    public StructureMatchSession getSession() {
        return session;
    }
}
