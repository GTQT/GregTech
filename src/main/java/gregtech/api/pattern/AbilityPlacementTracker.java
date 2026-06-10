package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks globally limited multiblock abilities while assembling a multi-piece
 * preview or auto-building several pieces and repeat slices.
 */
public final class AbilityPlacementTracker {

    private final Map<MultiblockAbility<?>, int[]> limits;
    private final Map<MultiblockAbility<?>, Integer> counts = new HashMap<>();
    private final LongSet recordedWorldPositions = new LongOpenHashSet();

    AbilityPlacementTracker(@NotNull Map<MultiblockAbility<?>, int[]> limits) {
        Map<MultiblockAbility<?>, int[]> copied = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : limits.entrySet()) {
            copied.put(entry.getKey(), entry.getValue().clone());
        }
        this.limits = Collections.unmodifiableMap(copied);
    }

    public boolean canPlace(@NotNull BlockInfo info) {
        for (MultiblockAbility<?> ability : getAbilities(info.getTileEntity())) {
            int[] range = limits.get(ability);
            if (range != null && range[1] >= 0 && counts.getOrDefault(ability, 0) >= range[1]) {
                return false;
            }
        }
        return true;
    }

    public boolean isStillRequired(@NotNull BlockInfo info) {
        for (MultiblockAbility<?> ability : getAbilities(info.getTileEntity())) {
            int[] range = limits.get(ability);
            if (range != null && counts.getOrDefault(ability, 0) < range[0]) {
                return true;
            }
        }
        return false;
    }

    public void record(@NotNull BlockInfo info) {
        recordAbilities(getAbilities(info.getTileEntity()));
    }

    public void recordWorldTile(@NotNull BlockPos pos, TileEntity tileEntity) {
        if (tileEntity == null || !recordedWorldPositions.add(pos.toLong())) return;
        recordAbilities(getAbilities(tileEntity));
    }

    private void recordAbilities(@NotNull List<MultiblockAbility<?>> abilities) {
        for (MultiblockAbility<?> ability : abilities) {
            if (limits.containsKey(ability)) {
                counts.merge(ability, 1, Integer::sum);
            }
        }
    }

    @NotNull
    private static List<MultiblockAbility<?>> getAbilities(TileEntity tileEntity) {
        if (!(tileEntity instanceof IGregTechTileEntity gregTechTile)) {
            return Collections.emptyList();
        }
        MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
        if (!(metaTileEntity instanceof IMultiblockAbilityPart<?> abilityPart)) {
            return Collections.emptyList();
        }
        return abilityPart.getAbilities();
    }
}
