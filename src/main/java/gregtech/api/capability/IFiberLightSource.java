package gregtech.api.capability;

import gregtech.common.pipelike.fiber.FiberLight;
import gregtech.common.pipelike.fiber.FiberLightCommand;

import org.jetbrains.annotations.NotNull;

/**
 * Implemented by anything that can emit light onto a fiber optic line, such as an optical source
 * hatch.
 * <p>
 * Light is <b>pushed</b> onto the line: a multiblock controller calls
 * {@link #receiveLightCommand} with the colour and flux it wants, and the emitter checks that
 * request against its own rating before it starts sending. {@link #getEmittedLight()} is then a
 * read-only view of what is actually going out, and the same light is readable on every link of the
 * line.
 */
public interface IFiberLightSource {

    /**
     * Takes a light setting from the controller. The requested flux is clamped to this emitter's
     * own limit, so an oversized request is reduced rather than rejected outright.
     *
     * @param command the colour and requested flux
     * @return {@code true} when this emitter accepted the command
     */
    boolean receiveLightCommand(@NotNull FiberLightCommand command);

    /** The light currently being sent down the line, never {@code null}. */
    @NotNull
    FiberLight getEmittedLight();
}
