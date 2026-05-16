package gregtech.common.items.behaviors;

import gregtech.api.pipenet.tile.IPipeTile;

import net.minecraft.util.EnumFacing;

import java.util.List;
/**
 * Logic from Susy-Core:
 * <a href=
 * "https://https://github.com/SymmetricDevs/Susy-Core/blob/main/src/main/java/supersymmetry/common/item/behavior/ITraverseOption.java">...</a>
 */
public interface ITraverseOption {

    List<EnumFacing> findNext(EnumFacing from, IPipeTile<?, ?> pipe);

    void operate(EnumFacing from, IPipeTile<?, ?> self, IPipeTile<?, ?> other, boolean reverse);
}
