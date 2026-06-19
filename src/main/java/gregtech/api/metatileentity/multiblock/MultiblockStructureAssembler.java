package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.pattern.StructureOperationState;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    static PreparedCommit prepare(@NotNull MultiblockControllerBase controller,
                                  @NotNull StructureOperationState operationState,
                                  @NotNull List<IMultiblockPart> currentParts,
                                  boolean formed) {
        Set<IMultiblockPart> rawPartsSet = operationState.getParts();
        ArrayList<IMultiblockPart> parts = new ArrayList<>(rawPartsSet);
        IMultiblockPart conflict = findAttachConflict(
                parts, formed ? currentParts : Collections.emptyList());
        if (conflict != null) {
            return PreparedCommit.failed(describeAttachConflict(conflict));
        }

        if (!formed) {
            sortParts(controller, parts);
            return PreparedCommit.initial(parts, collectAbilities(controller, parts));
        }

        Set<IMultiblockPart> oldPartsSet = new HashSet<>(currentParts);
        Set<IMultiblockPart> removedParts = new HashSet<>(oldPartsSet);
        removedParts.removeAll(rawPartsSet);
        Set<IMultiblockPart> addedParts = new HashSet<>(rawPartsSet);
        addedParts.removeAll(oldPartsSet);

        if (removedParts.isEmpty() && addedParts.isEmpty()) {
            return PreparedCommit.unchanged();
        }

        sortParts(controller, parts);
        return PreparedCommit.changed(
                parts, collectAbilities(controller, parts), removedParts, addedParts);
    }

    @Nullable
    private static IMultiblockPart findAttachConflict(
            @NotNull List<IMultiblockPart> parts,
            @NotNull Collection<IMultiblockPart> alreadyAttachedParts) {
        for (IMultiblockPart part : parts) {
            if (part.isAttachedToMultiBlock() && !alreadyAttachedParts.contains(part) && !part.canPartShare()) {
                return part;
            }
        }
        return null;
    }

    @NotNull
    private static String describeAttachConflict(@NotNull IMultiblockPart part) {
        if (part instanceof MetaTileEntity) {
            MetaTileEntity metaTileEntity = (MetaTileEntity) part;
            return "Part " + metaTileEntity.getMetaName() + " at " + metaTileEntity.getPos()
                    + " is already attached to another multiblock";
        }
        return "A non-shareable part is already attached to another multiblock: "
                + part.getClass().getName();
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

    static final class PreparedCommit {

        final boolean successful;
        final boolean initial;
        final boolean changed;
        @NotNull
        final List<IMultiblockPart> parts;
        @NotNull
        final Map<MultiblockAbility<Object>, AbilityInstances> abilities;
        @Nullable
        final String failureMessage;
        @NotNull
        final Set<IMultiblockPart> removedParts;
        @NotNull
        final Set<IMultiblockPart> addedParts;

        private PreparedCommit(boolean successful, boolean initial, boolean changed,
                               @NotNull List<IMultiblockPart> parts,
                               @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
                               @NotNull Set<IMultiblockPart> removedParts,
                               @NotNull Set<IMultiblockPart> addedParts,
                               @Nullable String failureMessage) {
            this.successful = successful;
            this.initial = initial;
            this.changed = changed;
            this.parts = parts;
            this.abilities = abilities;
            this.removedParts = removedParts;
            this.addedParts = addedParts;
            this.failureMessage = failureMessage;
        }

        @NotNull
        static PreparedCommit initial(
                @NotNull List<IMultiblockPart> parts,
                @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities) {
            return new PreparedCommit(
                    true, true, true, parts, abilities,
                    Collections.emptySet(), new HashSet<>(parts), null);
        }

        @NotNull
        static PreparedCommit changed(
                @NotNull List<IMultiblockPart> parts,
                @NotNull Map<MultiblockAbility<Object>, AbilityInstances> abilities,
                @NotNull Set<IMultiblockPart> removedParts,
                @NotNull Set<IMultiblockPart> addedParts) {
            return new PreparedCommit(
                    true, false, true, parts, abilities, removedParts, addedParts, null);
        }

        @NotNull
        static PreparedCommit unchanged() {
            return new PreparedCommit(
                    true, false, false, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptySet(), Collections.emptySet(), null);
        }

        @NotNull
        static PreparedCommit failed(@NotNull String failureMessage) {
            return new PreparedCommit(
                    false, false, false, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptySet(), Collections.emptySet(), failureMessage);
        }
    }
}
