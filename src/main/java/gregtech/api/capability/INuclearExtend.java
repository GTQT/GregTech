package gregtech.api.capability;

import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.NuclearAbility;

import java.util.List;

public interface INuclearExtend {
    List<NuclearAbility> getUpdateAbilities();
}
