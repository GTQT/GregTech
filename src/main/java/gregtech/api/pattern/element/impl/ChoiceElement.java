package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.element.AutoPlaceEnvironment;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Alternative element that declares requirements for every branch before matching.
 */
public final class ChoiceElement implements IStructureElement<Object> {

    private final IStructureElement[] elements;

    public ChoiceElement(@NotNull IStructureElement... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("ChoiceElement requires at least one element");
        }
        this.elements = elements;
    }

    @NotNull
    @Override
    public Set<StructureElementCapability> getCapabilities() {
        EnumSet<StructureElementCapability> capabilities =
                EnumSet.allOf(StructureElementCapability.class);
        for (IStructureElement element : elements) {
            capabilities.retainAll(element.getCapabilities());
        }
        return Collections.unmodifiableSet(capabilities);
    }

    @NotNull
    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        StructureIncrementalSupport support = StructureIncrementalSupport.TYPED_CONTRIBUTION;
        for (IStructureElement element : elements) {
            StructureIncrementalSupport childSupport = element.getIncrementalSupport();
            if (childSupport == StructureIncrementalSupport.OPAQUE) {
                return StructureIncrementalSupport.OPAQUE;
            }
            if (childSupport == StructureIncrementalSupport.MATCH_ONLY) {
                support = StructureIncrementalSupport.MATCH_ONLY;
            }
        }
        return support;
    }

    @NotNull
    @Override
    public Set<StructureDependency> getDependencies() {
        Set<StructureDependency> dependencies = new LinkedHashSet<>();
        for (IStructureElement element : elements) {
            dependencies.addAll(element.getDependencies());
        }
        return Collections.unmodifiableSet(dependencies);
    }

    @Override
    public boolean hasExplicitIncrementalContract() {
        for (IStructureElement element : elements) {
            if (!element.hasExplicitIncrementalContract()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        for (IStructureElement element : elements) {
            if (context.transaction(element::check)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean match(@NotNull StructureEvaluationContext<Object> context) {
        collectRequirements(context);
        return check(context);
    }

    @Override
    public BlockInfo[] getCandidates() {
        List<BlockInfo> all = new ArrayList<>();
        for (IStructureElement element : elements) {
            BlockInfo[] candidates = element.getCandidates();
            if (candidates != null) {
                all.addAll(Arrays.asList(candidates));
            }
        }
        return all.toArray(new BlockInfo[0]);
    }

    @NotNull
    @Override
    public StructureElementPreview getPreview() {
        StructureElementPreview.Builder builder = StructureElementPreview.builder();
        for (IStructureElement element : elements) {
            StructureElementPreview preview = element.getPreview();
            for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
                builder.limited(group);
            }
            for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
                builder.common(group);
            }
        }
        return builder.build();
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        for (IStructureElement element : elements) {
            if (context.transaction(childContext ->
                    element.placeBlock(childContext, player, skipHatches))) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<Object> context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        boolean allContinue = true;
        for (IStructureElement element : elements) {
            PlaceResult result = context.transactionValue(
                    childContext -> element.survivalPlaceBlock(childContext, trigger, env, skipHatches),
                    ChoiceElement::isCommittedSurvivalResult);
            switch (result) {
                case REJECT_CONTINUE:
                    break;
                case REJECT:
                    allContinue = false;
                    break;
                case SKIP:
                case STOP:
                case ACCEPT:
                case ACCEPT_STOP:
                    return result;
                default:
                    break;
            }
        }
        return allContinue ? PlaceResult.REJECT_CONTINUE : PlaceResult.REJECT;
    }

    private static boolean isCommittedSurvivalResult(@NotNull PlaceResult result) {
        return result != PlaceResult.REJECT_CONTINUE && result != PlaceResult.REJECT;
    }

    @NotNull
    @Override
    public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<Object> context,
                                                         @NotNull ItemStack trigger) {
        for (IStructureElement element : elements) {
            StructureHintRenderResult result = context.probeValue(probeContext ->
                    element.spawnHintWithResult(probeContext, trigger));
            if (result.rendered() || result.failed()) {
                return result;
            }
        }
        return StructureHintRenderResult.skipped(StructureHintRenderResult.Source.TRIGGER);
    }

    @Override
    public int getMinGlobalCount() {
        int max = 0;
        for (IStructureElement element : elements) {
            max = Math.max(max, element.getMinGlobalCount());
        }
        return max;
    }

    @Override
    public int getMaxGlobalCount() {
        for (IStructureElement element : elements) {
            if (element.getMaxGlobalCount() == -1) {
                return -1;
            }
        }
        int max = 0;
        for (IStructureElement element : elements) {
            max = Math.max(max, element.getMaxGlobalCount());
        }
        return max;
    }

    @Override
    public boolean isCenter() {
        for (IStructureElement element : elements) {
            if (element.isCenter()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        for (IStructureElement element : elements) {
            element.addTooltip(tooltip);
        }
    }

    @Override
    public void addPreviewTooltip(@NotNull List<String> tooltip) {
        for (IStructureElement element : elements) {
            element.addPreviewTooltip(tooltip);
        }
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable Object context) {
        Set<String> descriptions = new LinkedHashSet<>();
        for (IStructureElement element : elements) {
            List<String> description = element.getDescription(context);
            if (description != null) {
                descriptions.addAll(description);
            }
        }
        return descriptions.isEmpty() ? null : new ArrayList<>(descriptions);
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        for (IStructureElement element : elements) {
            element.collectRequirements(context);
        }
    }
}
