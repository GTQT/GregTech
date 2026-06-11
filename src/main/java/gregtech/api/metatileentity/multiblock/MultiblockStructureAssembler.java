package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pattern.PatternMatchContext;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MultiblockStructureAssembler {

    private MultiblockStructureAssembler() {}

    @NotNull
    static Assembly assemble(@NotNull MultiblockControllerBase controller,
                             @NotNull PatternMatchContext context) {
        Set<IMultiblockPart> rawPartsSet = context.getOrCreate("MultiblockParts", HashSet::new);
        ArrayList<IMultiblockPart> parts = new ArrayList<>(rawPartsSet);
        if (!canAttachAll(parts, Collections.emptyList())) {
            return Assembly.failed();
        }
        sortParts(controller, parts);
        return Assembly.success(parts, collectAbilities(controller, parts));
    }

    @NotNull
    static Reassembly reassemble(@NotNull MultiblockControllerBase controller,
                                 @NotNull PatternMatchContext context,
                                 @NotNull List<IMultiblockPart> currentParts) {
        Set<IMultiblockPart> newPartsSet = context.getOrCreate("MultiblockParts", HashSet::new);
        ArrayList<IMultiblockPart> newParts = new ArrayList<>(newPartsSet);
        if (!canAttachAll(newParts, currentParts)) {
            return Reassembly.failed();
        }

        Set<IMultiblockPart> oldPartsSet = new HashSet<>(currentParts);
        Set<IMultiblockPart> removedParts = new HashSet<>(oldPartsSet);
        removedParts.removeAll(newPartsSet);
        Set<IMultiblockPart> addedParts = new HashSet<>(newPartsSet);
        addedParts.removeAll(oldPartsSet);

        if (removedParts.isEmpty() && addedParts.isEmpty()) {
            return Reassembly.unchanged();
        }

        sortParts(controller, newParts);
        return Reassembly.changed(newParts, collectAbilities(controller, newParts), removedParts, addedParts);
    }

    private static boolean canAttachAll(@NotNull List<IMultiblockPart> parts,
                                        @NotNull Collection<IMultiblockPart> alreadyAttachedParts) {
        for (IMultiblockPart part : parts) {
            if (part.isAttachedToMultiBlock() && !alreadyAttachedParts.contains(part) && !part.canPartShare()) {
                return false;
            }
        }
        return true;
    }

    private static void sortParts(@NotNull MultiblockControllerBase controller,
                                  @NotNull List<IMultiblockPart> parts) {
        parts.sort(Comparator.comparing(it -> controller.multiblockPartSorter().apply(((MetaTileEntity) it).getPos())));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @NotNull
    private static Map<MultiblockAbility<Object>, AbilityInstances> collectAbilities(
            @NotNull MultiblockControllerBase controller,
            @NotNull List<IMultiblockPart> parts) {
        Map<MultiblockAbility<Object>, AbilityInstances> abilities = new HashMap<>();
        for (IMultiblockPart part : parts) {
            if (part instanceof IMultiblockAbilityPart abilityPart) {
                List<MultiblockAbility> abilityList = abilityPart.getAbilities();
                for (MultiblockAbility ability : abilityList) {
                    BlockPos pos = ((MetaTileEntity) abilityPart).getPos();
                    if (!controller.checkAbilityPart(ability, pos)) {
                        continue;
                    }

                    AbilityInstances instances = abilities.computeIfAbsent(ability, AbilityInstances::new);
                    abilityPart.registerAbilities(instances);
                }
            }
        }
        return abilities;
    }

    static final class Assembly {

        final boolean successful;
        @NotNull
        final List<IMultiblockPart> parts;
        @NotNull
        final Map<MultiblockAbility<Object>, AbilityInstances> abilities;

        private Assembly(boolean successful,
                         @NotNull List<IMultiblockPart> parts,
                         @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities) {
            this.successful = successful;
            this.parts = parts;
            this.abilities = abilities;
        }

        @NotNull
        static Assembly success(@NotNull List<IMultiblockPart> parts,
                                @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities) {
            return new Assembly(true, parts, abilities);
        }

        @NotNull
        static Assembly failed() {
            return new Assembly(false, Collections.emptyList(), Collections.emptyMap());
        }
    }

    static final class Reassembly {

        final boolean successful;
        final boolean changed;
        @NotNull
        final List<IMultiblockPart> parts;
        @NotNull
        final Map<MultiblockAbility<Object>, AbilityInstances> abilities;
        @NotNull
        final Set<IMultiblockPart> removedParts;
        @NotNull
        final Set<IMultiblockPart> addedParts;

        private Reassembly(boolean successful, boolean changed,
                           @NotNull List<IMultiblockPart> parts,
                           @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
                           @NotNull Set<IMultiblockPart> removedParts,
                           @NotNull Set<IMultiblockPart> addedParts) {
            this.successful = successful;
            this.changed = changed;
            this.parts = parts;
            this.abilities = abilities;
            this.removedParts = removedParts;
            this.addedParts = addedParts;
        }

        @NotNull
        static Reassembly changed(@NotNull List<IMultiblockPart> parts,
                                  @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
                                  @NotNull Set<IMultiblockPart> removedParts,
                                  @NotNull Set<IMultiblockPart> addedParts) {
            return new Reassembly(true, true, parts, abilities, removedParts, addedParts);
        }

        @NotNull
        static Reassembly unchanged() {
            return new Reassembly(true, false, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptySet(), Collections.emptySet());
        }

        @NotNull
        static Reassembly failed() {
            return new Reassembly(false, false, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptySet(), Collections.emptySet());
        }
    }
}
