package gregtech.api.capability;

import gregtech.common.pipelike.fiber.FiberLightCommand;

import org.jetbrains.annotations.NotNull;

/**
 * Implemented by a multiblock controller that drives optical source hatches.
 * <p>
 * Light is <b>pushed</b>, not polled: the controller calls {@link #sendLightCommand} whenever it
 * wants a colour and a flux put on the line, and every optical source hatch attached to the
 * structure accepts it through {@link IFiberLightSource#receiveLightCommand}.
 * <p>
 * The requested flux is a wish, not a guarantee: the hatch clamps it to its own tier rating, so a
 * controller can never push more than the hatch is rated for.
 *
 * @see IFiberLightSource
 */
public interface IFiberColorController {

    /**
     * Pushes a light setting to every optical source hatch on this structure.
     *
     * @param command the colour and requested flux
     * @return how many hatches took the command
     */
    int sendLightCommand(@NotNull FiberLightCommand command);
}
