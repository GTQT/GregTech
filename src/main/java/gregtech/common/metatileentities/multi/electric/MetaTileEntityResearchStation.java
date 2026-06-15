package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.IObjectHolder;
import gregtech.api.capability.IOpticalComputationHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalComputationReceiver;
import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.PatternStringError;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.ExplicitFrontFacingBlockInfo;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockComputerCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.behaviors.DataItemBehavior;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MetaTileEntityResearchStation extends RecipeMapMultiblockController
        implements IOpticalComputationReceiver {

    @NotNull
    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:research_station", () -> DeclarativePatternBuilder.start()
                    .aisle("XXX", "VVV", "PPP", "PPP", "PPP", "VVV", "XXX")
                    .aisle("XXX", "VAV", "AAA", "AAA", "AAA", "VAV", "XXX")
                    .aisle("XXX", "VAV", "XAX", "XSX", "XAX", "VAV", "XXX")
                    .aisle("XXX", "XAX", "---", "---", "---", "XAX", "XXX")
                    .aisle(" X ", "XAX", "---", "---", "---", "XAX", " X ")
                    .aisle(" X ", "XAX", "-A-", "-H-", "-A-", "XAX", " X ")
                    .aisle("   ", "XXX", "---", "---", "---", "XXX", "   ")
                    .where('S', selfPredicate(MetaTileEntityResearchStation.class))
                    .where('X', states(getCasingState()))
                    .where(' ', any())
                    .where('-', air())
                    .where('V', states(getVentState()))
                    .where('A', states(getAdvancedState()))
                    .where('H', objectHolderFacingController())
                    .casing('P', CasingDefinition.simple(getCasingState()))
                    .hatch(MultiblockAbility.INPUT_ENERGY, 1, 4,
                            () -> MetaTileEntities.ENERGY_INPUT_HATCH[GTValues.LuV])
                    .hatch(MultiblockAbility.MAINTENANCE_HATCH, 1, 1,
                            () -> MetaTileEntities.MAINTENANCE_HATCH)
                    .hatch(MultiblockAbility.COMPUTATION_DATA_RECEPTION, 1, 1,
                            () -> MetaTileEntities.COMPUTATION_HATCH_RECEIVER[GTValues.LuV])
                    .buildStructureDefinition());
    private IOpticalComputationProvider computationProvider;
    private IObjectHolder objectHolder;

    public MetaTileEntityResearchStation(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.RESEARCH_STATION_RECIPES);
        this.recipeMapWorkable = new ResearchStationRecipeLogic(this);
    }

    @NotNull
    private static IBlockState getVentState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_HEAT_VENT);
    }

    @NotNull
    private static IBlockState getAdvancedState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.ADVANCED_COMPUTER_CASING);
    }

    @NotNull
    private static IBlockState getCasingState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_CASING);
    }

    @NotNull
    private static IStructureElement<Object> objectHolderFacingController() {
        return ObjectHolderElement.INSTANCE;
    }

    @NotNull
    private static BlockInfo[] getObjectHolderCandidates() {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(MetaTileEntities.OBJECT_HOLDER);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.NORTH);
        return new BlockInfo[] {
                new ExplicitFrontFacingBlockInfo(
                        MetaTileEntities.OBJECT_HOLDER.getBlock().getDefaultState(), holder,
                        controller -> controller.getFrontFacing().getOpposite())
        };
    }

    private static boolean isObjectHolder(TileEntity tileEntity) {
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            return false;
        }
        return ((IGregTechTileEntity) tileEntity).getMetaTileEntity() instanceof IObjectHolder;
    }

    private enum ObjectHolderElement implements IStructureElement<Object> {
        INSTANCE;

        private final TraceabilityPredicate legacyPredicate = new TraceabilityPredicate(
                blockWorldState -> isObjectHolder(blockWorldState.getTileEntity()),
                MetaTileEntityResearchStation::getObjectHolderCandidates)
                        .addTooltips("gregtech.multiblock.pattern.error.object_holder_facing")
                        .setAbility(MultiblockAbility.OBJECT_HOLDER);

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            MetaTileEntityResearchStation controller = context.getController() instanceof MetaTileEntityResearchStation ?
                    (MetaTileEntityResearchStation) context.getController() : null;
            if (controller == null) {
                return false;
            }

            TileEntity tileEntity = context.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity)) {
                return false;
            }
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (!(metaTileEntity instanceof IObjectHolder objectHolder)) {
                return false;
            }

            if (objectHolder.getFrontFacing() != controller.getFrontFacing().getOpposite()) {
                context.setError(new PatternStringError(
                        "gregtech.multiblock.pattern.error.object_holder_facing"));
                return false;
            }

            return context.transaction(transactionContext ->
                    transactionContext.getCollector().recordAbility(this, (IMultiblockPart) metaTileEntity));
        }

        @Override
        public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
            context.getCollector().declareAbility(
                    this, MultiblockAbility.OBJECT_HOLDER, 1, 1);
        }

        @NotNull
        @Override
        public StructureIncrementalSupport getIncrementalSupport() {
            return StructureIncrementalSupport.TYPED_CONTRIBUTION;
        }

        @NotNull
        @Override
        public Set<StructureDependency> getDependencies() {
            return Collections.emptySet();
        }

        @NotNull
        @Override
        public StructureElementPreview getPreview() {
            return StructureElementPreview.builder()
                    .limited(MetaTileEntityResearchStation::getObjectHolderCandidates, 1, 1, -1, -1, 1)
                    .build();
        }

        @Override
        public boolean check(World world, BlockPos pos, gregtech.api.pattern.PatternMatchContext context) {
            return isObjectHolder(world.getTileEntity(pos));
        }

        @Override
        public BlockInfo[] getCandidates() {
            return getObjectHolderCandidates();
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, gregtech.api.pattern.PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @Override
        public TraceabilityPredicate toPredicate() {
            return legacyPredicate;
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityResearchStation(metaTileEntityId);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        formResearchStationStructure();
    }

    private void formResearchStationStructure() {
        List<IOpticalComputationHatch> providers = getAbilities(MultiblockAbility.COMPUTATION_DATA_RECEPTION);
        if (providers != null && providers.size() >= 1) {
            computationProvider = providers.get(0);
        }
        List<IObjectHolder> holders = getAbilities(MultiblockAbility.OBJECT_HOLDER);
        if (holders != null && holders.size() >= 1) {
            objectHolder = holders.get(0);
            // cannot set in initializeAbilities since super() calls it before setting the objectHolder field here
            this.inputInventory = new ItemHandlerList(Collections.singletonList(objectHolder.getAsHandler()));
        }

        // should never happen, but would rather do this than have an obscure NPE
        if (computationProvider == null || objectHolder == null) {
            invalidateStructure();
        }
    }

    // force object holder to be facing the controller
    @Override
    public void checkStructurePattern() {
        super.checkStructurePattern();
        if (isStructureFormed() && objectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
            invalidateStructure();
        }
    }

    @Override
    public @NotNull ComputationRecipeLogic getRecipeMapWorkable() {
        return (ComputationRecipeLogic) recipeMapWorkable;
    }

    @Override
    public void invalidateStructure() {
        computationProvider = null;
        // recheck the ability to make sure it wasn't the one broken
        List<IObjectHolder> holders = getAbilities(MultiblockAbility.OBJECT_HOLDER);
        if (holders != null && holders.size() >= 1 && holders.get(0) == objectHolder) {
            objectHolder.setLocked(false);
        }
        objectHolder = null;
        super.invalidateStructure();
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return computationProvider;
    }

    public IObjectHolder getObjectHolder() {
        return objectHolder;
    }

    @Override
    protected StructureDefinition createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (sourcePart == null || sourcePart instanceof IObjectHolder) {
            return Textures.ADVANCED_COMPUTER_CASING;
        }
        return Textures.COMPUTER_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.RESEARCH_STATION_OVERLAY;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    // let it think it can "void" since we replace an input item with the finished
    // item on completion, instead of outputting into a dedicated output slot.
    @Override
    public boolean canVoidRecipeItemOutputs() {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.4"));
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(this.getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addComputationUsageExactLine(getRecipeMapWorkable().getCurrentDrawnCWUt())
                .addParallelsLine(recipeMapWorkable.getParallelLimit());

        if (!recipeMapWorkable.isWorkingEnabled())
            builder.addWorkPausedLine(false);
        else if (recipeMapWorkable.isWorking()) {
            builder.addCustom(this::researchingLine);
        } else {
            builder.addIdlingLine(false);
        }

        builder.addComputationProgressLine(getRecipeMapWorkable());
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowComputationLine(getRecipeMapWorkable().isHasNotEnoughComputation());
        super.configureWarningText(builder);
    }

    private void researchingLine(KeyManager manager, UISyncer syncer) {
        var recipe = getRecipeMapWorkable().getPreviousRecipe();
        // todo fix recipe null on world load at some future point
        if (syncer.syncBoolean(recipe == null)) return;
        ItemStack stack = ItemStack.EMPTY;
        if (recipe != null) {
            List<ItemStack> outputs = recipe.getOutputs();
            stack = outputs.get(outputs.size() - 1);
        }
        stack = syncer.syncObject(stack, ByteBufAdapters.ITEM_STACK);
        if (stack.isEmpty()) return;
        String id = AssemblyLineManager.readResearchId(stack);
        if (id == null) return;
        List<IDrawable> stacks = new ArrayList<>();
        DataItemBehavior.collectResearchItems(id, stacks);
        stacks.remove(0);
        manager.add(KeyUtil.lang(TextFormatting.GREEN, "gregtech.machine.research_station.researching"));
        for (IDrawable line : stacks) {
            manager.add(line);
        }
    }

    private static class ResearchStationRecipeLogic extends ComputationRecipeLogic {

        public ResearchStationRecipeLogic(MetaTileEntityResearchStation metaTileEntity) {
            super(metaTileEntity, ComputationType.SPORADIC);
        }

        @NotNull
        @Override
        public MetaTileEntityResearchStation getMetaTileEntity() {
            return (MetaTileEntityResearchStation) super.getMetaTileEntity();
        }

        @Override
        public boolean isAllowOverclocking() {
            return false;
        }

        @Override
        protected @Nullable Recipe setupAndConsumeRecipeInputs(@NotNull Recipe recipe,
                                                               @NotNull IItemHandlerModifiable importInventory) {
            // this machine cannot overclock, so don't bother calling it
            if (!hasEnoughPower(recipe.getEUt(), recipe.getDuration())) {
                return null;
            }

            // skip "can fit" checks, it can always fit

            // do not consume inputs here, consume them on completion
            if (recipe.matches(false, importInventory, getInputTank())) {
                this.metaTileEntity.addNotifiedInput(importInventory);
                return recipe;
            }
            return null;
        }

        // lock the object holder on recipe start
        @Override
        protected void setupRecipe(@NotNull Recipe recipe) {
            IObjectHolder holder = getMetaTileEntity().getObjectHolder();
            holder.setLocked(true);
            super.setupRecipe(recipe);
        }

        // "replace" the items in the slots rather than outputting elsewhere
        // unlock the object holder
        @Override
        protected void outputRecipeOutputs() {
            IObjectHolder holder = getMetaTileEntity().getObjectHolder();
            holder.setHeldItem(ItemStack.EMPTY);

            ItemStack outputItem = ItemStack.EMPTY;
            if (itemOutputs != null && itemOutputs.size() >= 1) {
                outputItem = itemOutputs.get(0);
            }
            holder.setDataItem(outputItem);
            holder.setLocked(false);
        }
    }
}
