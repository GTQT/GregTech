package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchSession;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.AutoPlaceEnvironment;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
 * Element that chains multiple alternative elements together (any may match).
 */
public class ChainElement implements IStructureElement<Object> {

    private final IStructureElement[] elements;

    public ChainElement(IStructureElement... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("ChainElement requires at least one element");
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

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        for (IStructureElement e : elements) {
            StructureMatchSession session = context.getSession();
            StructureMatchSession.Checkpoint sessionCheckpoint =
                    session == null ? null : session.checkpoint();
            PatternMatchContext.Checkpoint contextCheckpoint =
                    session == null ? context.getLegacyContext().checkpoint() : null;
            if (e.check(context)) {
                return true;
            }
            if (session != null) {
                session.restore(sessionCheckpoint);
            } else {
                context.getLegacyContext().restore(contextCheckpoint);
            }
        }
        return false;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        for (IStructureElement e : elements) {
            if (e.check(world, pos, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                @NotNull ItemStack trigger) {
        for (IStructureElement e : elements) {
            if (e.couldBeValid(world, pos, context, trigger)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        List<BlockInfo> all = new ArrayList<>();
        for (IStructureElement e : elements) {
            BlockInfo[] c = e.getCandidates();
            if (c != null) {
                all.addAll(Arrays.asList(c));
            }
        }
        return all.toArray(new BlockInfo[0]);
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        for (IStructureElement e : elements) {
            if (e.placeBlock(world, pos, context, player, skipHatches)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    public PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                          @NotNull ItemStack trigger,
                                          @NotNull AutoPlaceEnvironment env,
                                          boolean skipHatches) {
        boolean allContinue = true;
        for (IStructureElement e : elements) {
            PlaceResult result = e.survivalPlaceBlock(world, pos, context, trigger, env, skipHatches);
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

    @Override
    public void spawnHint(World world, BlockPos pos) {
        for (IStructureElement e : elements) {
            if (e.spawnHint(world, pos, ItemStack.EMPTY)) {
                return;
            }
        }
    }

    @Override
    public boolean spawnHint(World world, BlockPos pos, @NotNull ItemStack trigger) {
        for (IStructureElement e : elements) {
            if (e.spawnHint(world, pos, trigger)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMinGlobalCount() {
        int max = 0;
        for (IStructureElement e : elements) {
            max = Math.max(max, e.getMinGlobalCount());
        }
        return max;
    }

    @Override
    public int getMaxGlobalCount() {
        for (IStructureElement e : elements) {
            if (e.getMaxGlobalCount() == -1) {
                return -1;
            }
        }
        int max = 0;
        for (IStructureElement e : elements) {
            max = Math.max(max, e.getMaxGlobalCount());
        }
        return max;
    }

    @Override
    public boolean isCenter() {
        for (IStructureElement e : elements) {
            if (e.isCenter()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addTooltip(List<String> tooltip) {
        for (IStructureElement e : elements) {
            e.addTooltip(tooltip);
        }
    }

    @Nullable
    @Override
    public List<String> getDescription(@Nullable Object context) {
        Set<String> descriptions = new LinkedHashSet<>();
        for (IStructureElement e : elements) {
            List<String> desc = e.getDescription(context);
            if (desc != null) {
                descriptions.addAll(desc);
            }
        }
        return descriptions.isEmpty() ? null : new ArrayList<>(descriptions);
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        for (IStructureElement e : elements) {
            e.collectRequirements(context);
        }
    }

    @Nullable
    @Override
    public TraceabilityPredicate toPredicate() {
        TraceabilityPredicate result = elements[0].toPredicate();
        if (result == null) {
            return null;
        }
        for (int i = 1; i < elements.length; i++) {
            TraceabilityPredicate predicate = elements[i].toPredicate();
            if (predicate == null) {
                return null;
            }
            result = result.or(predicate);
        }
        return result;
    }
}
