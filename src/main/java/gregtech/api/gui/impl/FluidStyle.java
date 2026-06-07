package gregtech.api.gui.impl;

import gregtech.api.gui.IFluidStyle;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter @Setter
@Accessors(chain = true, fluent = true)
@NoArgsConstructor
public class FluidStyle implements IFluidStyle {

    private int width = 16;
    private int height = 16;

    public IFluidStyle copy() {
        return new FluidStyle().bounds(this.width, this.height);
    }
}
