package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.VariantActiveBlock;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityLaserHatch;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Legacy predicate factory helpers used by {@link TraceabilityPredicate} and
 * the legacy element bridges ({@link gregtech.api.pattern.element.impl.HatchElement},
 * {@link gregtech.api.pattern.element.impl.AbilityElement}).
 *
 * <p>Scheduled for removal together with {@link TraceabilityPredicate} in
 * Phase 5 of the structure system cleanup.
 */
public final class MultiblockPredicates {

    private MultiblockPredicates() {}

    public static TraceabilityPredicate tilePredicate(
            @NotNull BiFunction<BlockWorldState, MetaTileEntity, Boolean> predicate,
            @Nullable Supplier<BlockInfo[]> candidates) {
        return new TraceabilityPredicate(blockWorldState -> {
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity))
                return false;
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (predicate.apply(blockWorldState, metaTileEntity)) {
                if (metaTileEntity instanceof IMultiblockPart) {
                    Set<IMultiblockPart> partsFound = blockWorldState.getMatchContext().getOrCreate("MultiblockParts",
                            java.util.HashSet::new);
                    partsFound.add((IMultiblockPart) metaTileEntity);
                }
                return true;
            }
            return false;
        }, candidates);
    }

    public static TraceabilityPredicate metaTileEntities(MetaTileEntity... metaTileEntities) {
        ResourceLocation[] ids = Arrays.stream(metaTileEntities).filter(Objects::nonNull)
                .map(tile -> tile.metaTileEntityId).toArray(ResourceLocation[]::new);
        return tilePredicate((state, tile) -> ArrayUtils.contains(ids, tile.metaTileEntityId),
                getCandidates(metaTileEntities));
    }

    public static TraceabilityPredicate abilities(MultiblockAbility<?>... allowedAbilities) {
        TraceabilityPredicate predicate = tilePredicate((state, tile) -> {
            if (tile instanceof IMultiblockAbilityPart<?> abilityPart) {
                for (var ability : abilityPart.getAbilities()) {
                    if (ArrayUtils.contains(allowedAbilities, ability))
                        return true;
                }
            }
            return false;
        }, getCandidates(allowedAbilities));
        if (allowedAbilities.length == 1) {
            predicate.setAbility(allowedAbilities[0]);
        }
        return predicate;
    }

    public static TraceabilityPredicate states(IBlockState... allowedStates) {
        return new TraceabilityPredicate(blockWorldState -> {
            IBlockState state = blockWorldState.getBlockState();
            if (state.getBlock() instanceof VariantActiveBlock) {
                blockWorldState.getMatchContext().getOrPut("VABlock", new LinkedList<>()).add(blockWorldState.getPos());
            }
            return ArrayUtils.contains(allowedStates, state);
        }, getCandidates(allowedStates));
    }

    public static TraceabilityPredicate frames(Material... frameMaterials) {
        return states(Arrays.stream(frameMaterials).map(m -> MetaBlocks.FRAMES.get(m).getBlock(m))
                .toArray(IBlockState[]::new))
                .or(new TraceabilityPredicate(blockWorldState -> {
                    TileEntity tileEntity = blockWorldState.getTileEntity();
                    if (!(tileEntity instanceof IPipeTile<?, ?> pipeTile)) {
                        return false;
                    }
                    return ArrayUtils.contains(frameMaterials, pipeTile.getFrameMaterial());
                }));
    }

    public static TraceabilityPredicate blocks(Block... block) {
        return new TraceabilityPredicate(
                blockWorldState -> ArrayUtils.contains(block, blockWorldState.getBlockState().getBlock()),
                getCandidates(Arrays.stream(block).map(Block::getDefaultState).toArray(IBlockState[]::new)));
    }

    @NotNull
    public static TraceabilityPredicate selfPredicate(
            @NotNull Class<? extends MultiblockControllerBase> controllerClass) {
        return tilePredicate((state, tile) -> controllerClass.isInstance(tile),
                getCandidatesByClass(controllerClass)).setCenter();
    }

    @NotNull
    public static TraceabilityPredicate energyOutput(int tier, boolean isMinTier) {
        return metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_ENERGY).stream()
                .filter(mte -> {
                    IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                            null);
                    return container != null && ( isMinTier ? (container.getOutputVoltage() * container.getOutputAmperage() >=
                            GTValues.V[tier]) : (container.getOutputVoltage() * container.getOutputAmperage() <=
                            GTValues.V[tier]));
                })
                .toArray(MetaTileEntity[]::new));
    }

    @NotNull
    public static TraceabilityPredicate energyInput(int tier, boolean isMinTier) {
        return metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.INPUT_ENERGY).stream()
                .filter(mte -> {
                    IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                            null);
                    return container != null && ( isMinTier ? (container.getInputVoltage() * container.getInputAmperage() >=
                            GTValues.V[tier]) : (container.getInputVoltage() * container.getInputAmperage() <=
                            GTValues.V[tier]));
                })
                .toArray(MetaTileEntity[]::new));
    }

    @NotNull
    public static TraceabilityPredicate laserOutput(int tier, boolean isMinTier) {
        return metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_LASER).stream()
                .filter(mte -> {
                    if (mte instanceof MetaTileEntityLaserHatch laserHatch) {
                        if(isMinTier) return laserHatch.getTier() >= tier;
                        return laserHatch.getTier() <= tier;
                    }
                    return false;
                })
                .toArray(MetaTileEntity[]::new));
    }

    @NotNull
    public static TraceabilityPredicate laserInput(int tier, boolean isMinTier) {
        return metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.INPUT_LASER).stream()
                .filter(mte -> {
                    if (mte instanceof MetaTileEntityLaserHatch laserHatch) {
                        if(isMinTier) return laserHatch.getTier() >= tier;
                        return laserHatch.getTier() <= tier;
                    }
                    return false;
                })
                .toArray(MetaTileEntity[]::new));
    }

    private static Supplier<BlockInfo[]> getCandidates(MetaTileEntity... metaTileEntities) {
        return () -> Arrays.stream(metaTileEntities).filter(Objects::nonNull).map(tile -> {
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            holder.setMetaTileEntity(tile);
            holder.getMetaTileEntity().onPlacement();
            holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
            return new BlockInfo(tile.getBlock().getDefaultState(), holder);
        }).toArray(BlockInfo[]::new);
    }

    private static Supplier<BlockInfo[]> getCandidates(MultiblockAbility<?>... allowedAbilities) {
        return () -> Arrays.stream(allowedAbilities)
                .filter(Objects::nonNull)
                .map(MultiblockAbility.REGISTRY::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(tile -> {
                    MetaTileEntityHolder holder = new MetaTileEntityHolder();
                    holder.setMetaTileEntity(tile);
                    holder.getMetaTileEntity().onPlacement();
                    holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
                    return new BlockInfo(tile.getBlock().getDefaultState(), holder);
                }).toArray(BlockInfo[]::new);
    }

    private static Supplier<BlockInfo[]> getCandidates(IBlockState... allowedStates) {
        return () -> Arrays.stream(allowedStates).map(state -> new BlockInfo(state, null)).toArray(BlockInfo[]::new);
    }

    @NotNull
    private static Supplier<BlockInfo[]> getCandidatesByClass(
            @NotNull Class<? extends MultiblockControllerBase> controllerClass) {
        return () -> {
            List<MetaTileEntity> matches = new ArrayList<>();
            for (var registry : GregTechAPI.mteManager.getRegistries()) {
                for (MetaTileEntity mte : registry) {
                    if (controllerClass.isInstance(mte)) {
                        matches.add(mte);
                    }
                }
            }
            if (matches.isEmpty()) {
                return new BlockInfo[] { BlockInfo.EMPTY };
            }
            return matches.stream().map(tile -> {
                MetaTileEntityHolder holder = new MetaTileEntityHolder();
                holder.setMetaTileEntity(tile);
                holder.getMetaTileEntity().onPlacement();
                holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
                return new BlockInfo(tile.getBlock().getDefaultState(), holder);
            }).toArray(BlockInfo[]::new);
        };
    }

}
