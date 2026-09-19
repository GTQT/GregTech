package gregtech.common.pipelike.optical;

import gregtech.api.pipenet.block.material.IMaterialPipeType;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.NotNull;

public enum OpticalPipeType implements IMaterialPipeType<OpticalCableProperties> {

    NORMAL("normal", 0.375f, OrePrefix.pipeOptical);

    public static final OpticalPipeType[] VALUES = values();

    private final String name;
    private final float thickness;
    private final OrePrefix orePrefix;

    OpticalPipeType(String name, float thickness, OrePrefix orePrefix) {
        this.name = name;
        this.thickness = thickness;
        this.orePrefix = orePrefix;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public OrePrefix getOrePrefix() {
        return orePrefix;
    }

    @Override
    public OpticalCableProperties modifyProperties(OpticalCableProperties baseProperties) {
        // The material alone decides the cable's capabilities; there is only one optical pipe type.
        return baseProperties;
    }

    @Override
    public boolean isPaintable() {
        return true;
    }
}
