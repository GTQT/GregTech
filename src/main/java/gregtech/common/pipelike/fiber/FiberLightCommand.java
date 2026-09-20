package gregtech.common.pipelike.fiber;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;

/**
 * A light setting pushed from a multiblock controller to an optical source hatch.
 * <p>
 * The controller decides both the wavelength and how much flux it wants to be sent; the hatch is the one
 * that checks the request against its own rating and clamps anything oversized before emitting.
 *
 * @param color     color to send
 * @param intensity requested flux, before the hatch applies its tier limit
 */
@Desugar
public record FiberLightCommand(@NotNull FiberColor color, long intensity) {

    /** Turn the line off. */
    public static final FiberLightCommand OFF = new FiberLightCommand(FiberColor.WHITE, 0L);

    public boolean isEmpty() {
        return intensity <= 0L;
    }
}
