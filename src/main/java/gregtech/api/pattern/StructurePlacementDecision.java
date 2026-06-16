package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.StructureEvaluationContext.Operation;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.Mods;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.PlayerWirelessGridHelper;
import appeng.me.helpers.BaseActionSource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared candidate and item-source decision logic for creative and survival
 * auto-build. The caller still owns world mutation; this class only decides
 * which candidate should be placed and, for survival, commits the item drain
 * after placement has succeeded.
 */
final class StructurePlacementDecision {

    private StructurePlacementDecision() {}

    @Nullable
    static Selection select(@NotNull EntityPlayer player,
                            @NotNull BlockInfo[] infos,
                            @NotNull List<ItemStack> candidates,
                            @Nullable TraceabilityPredicate.SimplePredicate matchedPredicate,
                            @Nullable Map<String, Integer> channelValues,
                            @Nullable AbilityPlacementTracker abilityTracker,
                            @NotNull Operation operation) {
        int preferredCandidateIndex = getPreferredCandidateIndex(matchedPredicate, infos, channelValues);
        int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker, preferredCandidateIndex);
        if (operation.isSurvivalBuild() && !player.isCreative()) {
            Selection required = selectSurvivalCandidate(player, infos, candidates, requiredAbilityIndex);
            if (required != null) {
                return required;
            }
            Selection preferred = selectSurvivalCandidate(player, infos, candidates, preferredCandidateIndex);
            if (preferred != null) {
                return preferred;
            }
            Selection inventory = selectInventoryCandidate(player, infos, candidates);
            if (inventory != null) {
                return inventory;
            }
            return selectAeCandidate(player, infos, candidates);
        }

        int preferredIndex = requiredAbilityIndex >= 0 ? requiredAbilityIndex : preferredCandidateIndex;
        if (preferredIndex >= 0 && preferredIndex < candidates.size()) {
            return Selection.creative(candidates.get(preferredIndex), infos[preferredIndex]);
        }
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack found = candidates.get(i);
            if (!found.isEmpty()) {
                return Selection.creative(found, infos[i]);
            }
        }
        return null;
    }

    @Nullable
    static Selection select(@NotNull EntityPlayer player,
                            @NotNull BlockInfo[] infos,
                            @NotNull List<ItemStack> candidates,
                            @Nullable StructureElementPreview.CandidateGroup matchedGroup,
                            @Nullable Map<String, Integer> channelValues,
                            @Nullable AbilityPlacementTracker abilityTracker,
                            @NotNull Operation operation) {
        int preferredCandidateIndex = getPreferredCandidateIndex(matchedGroup, infos, channelValues);
        int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker, preferredCandidateIndex);
        if (operation.isSurvivalBuild() && !player.isCreative()) {
            Selection required = selectSurvivalCandidate(player, infos, candidates, requiredAbilityIndex);
            if (required != null) {
                return required;
            }
            Selection preferred = selectSurvivalCandidate(player, infos, candidates, preferredCandidateIndex);
            if (preferred != null) {
                return preferred;
            }
            Selection inventory = selectInventoryCandidate(player, infos, candidates);
            if (inventory != null) {
                return inventory;
            }
            return selectAeCandidate(player, infos, candidates);
        }

        int preferredIndex = requiredAbilityIndex >= 0 ? requiredAbilityIndex : preferredCandidateIndex;
        if (preferredIndex >= 0 && preferredIndex < candidates.size()) {
            return Selection.creative(candidates.get(preferredIndex), infos[preferredIndex]);
        }
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack found = candidates.get(i);
            if (!found.isEmpty()) {
                return Selection.creative(found, infos[i]);
            }
        }
        return null;
    }

    @Nullable
    static ItemStack representativeRequiredStack(@NotNull BlockInfo[] infos,
                                                 @NotNull List<ItemStack> candidates,
                                                 @Nullable TraceabilityPredicate.SimplePredicate matchedPredicate,
                                                 @Nullable Map<String, Integer> channelValues,
                                                 @Nullable AbilityPlacementTracker abilityTracker) {
        int preferredCandidateIndex = getPreferredCandidateIndex(matchedPredicate, infos, channelValues);
        int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker, preferredCandidateIndex);
        if (requiredAbilityIndex >= 0 && requiredAbilityIndex < candidates.size()) {
            return one(candidates.get(requiredAbilityIndex));
        }
        if (preferredCandidateIndex >= 0 && preferredCandidateIndex < candidates.size()) {
            return one(candidates.get(preferredCandidateIndex));
        }
        for (ItemStack candidate : candidates) {
            if (!candidate.isEmpty()) {
                return one(candidate);
            }
        }
        return null;
    }

    @Nullable
    static ItemStack representativeRequiredStack(@NotNull BlockInfo[] infos,
                                                 @NotNull List<ItemStack> candidates,
                                                 @Nullable StructureElementPreview.CandidateGroup matchedGroup,
                                                 @Nullable Map<String, Integer> channelValues,
                                                 @Nullable AbilityPlacementTracker abilityTracker) {
        int preferredCandidateIndex = getPreferredCandidateIndex(matchedGroup, infos, channelValues);
        int requiredAbilityIndex = findRequiredAbilityCandidate(infos, abilityTracker, preferredCandidateIndex);
        if (requiredAbilityIndex >= 0 && requiredAbilityIndex < candidates.size()) {
            return one(candidates.get(requiredAbilityIndex));
        }
        if (preferredCandidateIndex >= 0 && preferredCandidateIndex < candidates.size()) {
            return one(candidates.get(preferredCandidateIndex));
        }
        for (ItemStack candidate : candidates) {
            if (!candidate.isEmpty()) {
                return one(candidate);
            }
        }
        return null;
    }

    @NotNull
    static BlockInfo[] filterPlaceable(@Nullable BlockInfo[] infos,
                                       @Nullable AbilityPlacementTracker abilityTracker) {
        if (infos == null) {
            return new BlockInfo[0];
        }
        return Arrays.stream(infos)
                .filter(info -> info != null)
                .filter(info -> info.getBlockState() != null)
                .filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                .filter(info -> abilityTracker == null || abilityTracker.canPlace(info))
                .toArray(BlockInfo[]::new);
    }

    static int countPlaceable(@Nullable BlockInfo[] infos) {
        if (infos == null) {
            return 0;
        }
        return (int) Arrays.stream(infos)
                .filter(info -> info != null)
                .filter(info -> info.getBlockState() != null)
                .filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                .count();
    }

    @NotNull
    static List<ItemStack> toItemStacks(@NotNull BlockInfo[] infos) {
        return Arrays.stream(infos)
                .map(StructurePlacementDecision::getStackForBlockInfo)
                .collect(Collectors.toList());
    }

    @NotNull
    static ItemStack getStackForBlockInfo(@NotNull BlockInfo info) {
        IBlockState blockState = info.getBlockState();
        MetaTileEntity metaTileEntity = info.getTileEntity() instanceof IGregTechTileEntity ?
                ((IGregTechTileEntity) info.getTileEntity()).getMetaTileEntity() : null;
        if (metaTileEntity != null) {
            return one(metaTileEntity.getStackForm());
        }
        Item item = Item.getItemFromBlock(blockState.getBlock());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1, blockState.getBlock().damageDropped(blockState));
    }

    static int getChannelCandidateIndex(@Nullable TraceabilityPredicate.SimplePredicate predicate,
                                        @Nullable BlockInfo[] infos,
                                        @Nullable Map<String, Integer> channelValues) {
        int preferredIndex = getPreferredCandidateIndex(predicate, infos, channelValues);
        if (preferredIndex >= 0) return preferredIndex;
        return 0;
    }

    static int getChannelCandidateIndex(@Nullable StructureElementPreview.CandidateGroup group,
                                        @Nullable BlockInfo[] infos,
                                        @Nullable Map<String, Integer> channelValues) {
        int preferredIndex = getPreferredCandidateIndex(group, infos, channelValues);
        if (preferredIndex >= 0) return preferredIndex;
        return 0;
    }

    static int getPlaceableCandidateIndex(@Nullable StructureElementPreview.CandidateGroup group,
                                          @Nullable BlockInfo[] infos,
                                          @Nullable Map<String, Integer> channelValues,
                                          @Nullable AbilityPlacementTracker abilityTracker) {
        if (infos == null || infos.length == 0) {
            return -1;
        }
        int preferredIndex = getPreferredCandidateIndex(group, infos, channelValues);
        int requiredAbilityIndex = findRequiredPlaceableAbilityCandidate(
                infos, abilityTracker, preferredIndex);
        if (requiredAbilityIndex >= 0) {
            return requiredAbilityIndex;
        }
        if (preferredIndex >= 0
                && preferredIndex < infos.length
                && canPlaceCandidate(infos[preferredIndex], abilityTracker)) {
            return preferredIndex;
        }
        for (int i = 0; i < infos.length; i++) {
            if (canPlaceCandidate(infos[i], abilityTracker)) {
                return i;
            }
        }
        return -1;
    }

    static int getPreferredCandidateIndex(@Nullable TraceabilityPredicate.SimplePredicate predicate,
                                          @Nullable BlockInfo[] infos,
                                          @Nullable Map<String, Integer> channelValues) {
        int channelIndex = getPreferredChannelCandidateIndex(predicate, infos, channelValues);
        if (channelIndex >= 0) {
            return channelIndex;
        }
        if (predicate == null || infos == null || predicate.defaultCandidate == null) {
            return -1;
        }
        MetaTileEntity preferred = predicate.defaultCandidate.get();
        if (preferred == null) {
            return -1;
        }
        for (int i = 0; i < infos.length; i++) {
            TileEntity tileEntity = infos[i].getTileEntity();
            if (tileEntity instanceof IGregTechTileEntity gregTechTile) {
                MetaTileEntity candidate = gregTechTile.getMetaTileEntity();
                if (candidate != null && preferred.metaTileEntityId.equals(candidate.metaTileEntityId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    static int getPreferredCandidateIndex(@Nullable StructureElementPreview.CandidateGroup group,
                                          @Nullable BlockInfo[] infos,
                                          @Nullable Map<String, Integer> channelValues) {
        int channelIndex = getPreferredChannelCandidateIndex(group, infos, channelValues);
        if (channelIndex >= 0) {
            return channelIndex;
        }
        if (group == null || infos == null || group.getDefaultCandidate() == null) {
            return -1;
        }
        MetaTileEntity preferred = group.getDefaultCandidate().get();
        if (preferred == null) {
            return -1;
        }
        for (int i = 0; i < infos.length; i++) {
            TileEntity tileEntity = infos[i].getTileEntity();
            if (tileEntity instanceof IGregTechTileEntity gregTechTile) {
                MetaTileEntity candidate = gregTechTile.getMetaTileEntity();
                if (candidate != null && preferred.metaTileEntityId.equals(candidate.metaTileEntityId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int getPreferredChannelCandidateIndex(@Nullable TraceabilityPredicate.SimplePredicate predicate,
                                                         @Nullable BlockInfo[] infos,
                                                         @Nullable Map<String, Integer> channelValues) {
        if (predicate == null || infos == null || infos.length == 0) return -1;
        if (channelValues == null || predicate.channelName == null) return -1;
        Integer cv = channelValues.get(predicate.channelName);
        if (cv == null || cv <= 0) return -1;
        int idx = cv - 1;
        return idx < infos.length ? idx : -1;
    }

    private static int getPreferredChannelCandidateIndex(@Nullable StructureElementPreview.CandidateGroup group,
                                                         @Nullable BlockInfo[] infos,
                                                         @Nullable Map<String, Integer> channelValues) {
        if (group == null || infos == null || infos.length == 0) return -1;
        if (channelValues == null || group.getChannelName() == null) return -1;
        Integer cv = channelValues.get(group.getChannelName());
        if (cv == null || cv <= 0) return -1;
        int idx = cv - 1;
        return idx < infos.length ? idx : -1;
    }

    private static int findRequiredAbilityCandidate(@NotNull BlockInfo[] infos,
                                                    @Nullable AbilityPlacementTracker abilityTracker,
                                                    int preferredCandidateIndex) {
        if (abilityTracker == null) return -1;
        if (preferredCandidateIndex >= 0
                && preferredCandidateIndex < infos.length
                && abilityTracker.isStillRequired(infos[preferredCandidateIndex])) {
            return preferredCandidateIndex;
        }
        for (int i = 0; i < infos.length; i++) {
            if (abilityTracker.isStillRequired(infos[i])) {
                return i;
            }
        }
        return -1;
    }

    private static int findRequiredPlaceableAbilityCandidate(@NotNull BlockInfo[] infos,
                                                             @Nullable AbilityPlacementTracker abilityTracker,
                                                             int preferredCandidateIndex) {
        if (abilityTracker == null) return -1;
        if (preferredCandidateIndex >= 0
                && preferredCandidateIndex < infos.length
                && abilityTracker.isStillRequired(infos[preferredCandidateIndex])
                && abilityTracker.canPlace(infos[preferredCandidateIndex])) {
            return preferredCandidateIndex;
        }
        for (int i = 0; i < infos.length; i++) {
            if (abilityTracker.isStillRequired(infos[i]) && abilityTracker.canPlace(infos[i])) {
                return i;
            }
        }
        return -1;
    }

    private static boolean canPlaceCandidate(@Nullable BlockInfo info,
                                             @Nullable AbilityPlacementTracker abilityTracker) {
        return info != null && (abilityTracker == null || isEmptyBlockInfo(info) || abilityTracker.canPlace(info));
    }

    private static boolean isEmptyBlockInfo(@NotNull BlockInfo info) {
        return info == BlockInfo.EMPTY
                || info.getBlockState() == null
                || info.getBlockState().getBlock() == Blocks.AIR;
    }

    @Nullable
    private static Selection selectSurvivalCandidate(@NotNull EntityPlayer player,
                                                     @NotNull BlockInfo[] infos,
                                                     @NotNull List<ItemStack> candidates,
                                                     int candidateIndex) {
        if (candidateIndex < 0 || candidateIndex >= candidates.size()) {
            return null;
        }
        ItemStack candidate = candidates.get(candidateIndex);
        if (candidate.isEmpty()) {
            return null;
        }
        if (hasInInventory(player, candidate)) {
            return Selection.inventory(candidate, infos[candidateIndex]);
        }
        if (hasInAENetwork(player, candidate)) {
            return Selection.ae(candidate, infos[candidateIndex]);
        }
        return null;
    }

    @Nullable
    private static Selection selectInventoryCandidate(@NotNull EntityPlayer player,
                                                      @NotNull BlockInfo[] infos,
                                                      @NotNull List<ItemStack> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack candidate = candidates.get(i);
            if (!candidate.isEmpty() && hasInInventory(player, candidate)) {
                return Selection.inventory(candidate, infos[i]);
            }
        }
        return null;
    }

    @Nullable
    private static Selection selectAeCandidate(@NotNull EntityPlayer player,
                                               @NotNull BlockInfo[] infos,
                                               @NotNull List<ItemStack> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            ItemStack candidate = candidates.get(i);
            if (!candidate.isEmpty() && hasInAENetwork(player, candidate)) {
                return Selection.ae(candidate, infos[i]);
            }
        }
        return null;
    }

    private static boolean hasInInventory(@NotNull EntityPlayer player,
                                          @NotNull ItemStack candidate) {
        for (ItemStack itemStack : player.inventory.mainInventory) {
            if (itemMatches(candidate, itemStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean takeFromInventory(@NotNull EntityPlayer player,
                                             @NotNull ItemStack candidate) {
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack itemStack = player.inventory.mainInventory.get(i);
            if (itemMatches(candidate, itemStack)) {
                itemStack.shrink(1);
                if (itemStack.isEmpty()) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean hasInAENetwork(@NotNull EntityPlayer player,
                                          @NotNull ItemStack candidate) {
        return extractFromAENetwork(player, candidate, Actionable.SIMULATE);
    }

    private static boolean takeFromAENetwork(@NotNull EntityPlayer player,
                                             @NotNull ItemStack candidate) {
        return extractFromAENetwork(player, candidate, Actionable.MODULATE);
    }

    private static boolean extractFromAENetwork(@NotNull EntityPlayer player,
                                                @NotNull ItemStack candidate,
                                                @NotNull Actionable action) {
        if (!isAeLoaded()) {
            return false;
        }
        try {
            if (player.world.isRemote || candidate.isEmpty()) return false;

            IStorageGrid storageGrid = PlayerWirelessGridHelper.getStorageGrid(player);
            if (storageGrid == null) return false;

            IItemStorageChannel channel = AEApi.instance().storage()
                    .getStorageChannel(IItemStorageChannel.class);
            IMEMonitor<IAEItemStack> monitor = storageGrid.getInventory(channel);
            if (monitor == null) return false;

            IAEItemStack request = channel.createStack(candidate);
            request.setStackSize(1);
            IAEItemStack extracted = monitor.extractItems(request, action, new BaseActionSource());
            return extracted != null && extracted.getStackSize() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isAeLoaded() {
        try {
            return Mods.AppliedEnergistics2.isModLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean itemMatches(@NotNull ItemStack expected,
                                       @Nullable ItemStack actual) {
        return !expected.isEmpty() && actual != null && !actual.isEmpty() && expected.isItemEqual(actual);
    }

    @NotNull
    private static ItemStack one(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    static final class Selection {

        @NotNull
        private final ItemStack requiredStack;
        @NotNull
        private final BlockInfo matchedInfo;
        @NotNull
        private final Source source;

        private Selection(@NotNull ItemStack requiredStack,
                          @NotNull BlockInfo matchedInfo,
                          @NotNull Source source) {
            this.requiredStack = one(requiredStack);
            this.matchedInfo = matchedInfo;
            this.source = source;
        }

        @NotNull
        static Selection creative(@NotNull ItemStack requiredStack,
                                  @NotNull BlockInfo matchedInfo) {
            return new Selection(requiredStack, matchedInfo, Source.CREATIVE);
        }

        @NotNull
        static Selection inventory(@NotNull ItemStack requiredStack,
                                   @NotNull BlockInfo matchedInfo) {
            return new Selection(requiredStack, matchedInfo, Source.INVENTORY);
        }

        @NotNull
        static Selection ae(@NotNull ItemStack requiredStack,
                            @NotNull BlockInfo matchedInfo) {
            return new Selection(requiredStack, matchedInfo, Source.AE);
        }

        @NotNull
        ItemStack getRequiredStack() {
            return requiredStack.copy();
        }

        @NotNull
        BlockInfo getMatchedInfo() {
            return matchedInfo;
        }

        boolean consumesItem() {
            return source != Source.CREATIVE;
        }

        boolean consume(@NotNull EntityPlayer player) {
            switch (source) {
                case CREATIVE:
                    return true;
                case INVENTORY:
                    return takeFromInventory(player, requiredStack);
                case AE:
                    return takeFromAENetwork(player, requiredStack);
                default:
                    return false;
            }
        }
    }

    private enum Source {
        CREATIVE,
        INVENTORY,
        AE
    }
}
