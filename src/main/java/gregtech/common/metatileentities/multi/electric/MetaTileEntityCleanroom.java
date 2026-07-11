package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.ICleanroomFilter;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IGenerator;
import gregtech.api.capability.IMufflerHatch;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.CleanroomLogic;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleGeneratorMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.StructureContributionKey;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructurePreviewResult;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureRuntimeDetectionContext;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.pattern.element.impl.ChainElement;
import gregtech.api.pattern.element.impl.HatchElement;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.Mods;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockCleanroomCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.MetaTileEntityCokeOven;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveBlastFurnace;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveWaterPump;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public class MetaTileEntityCleanroom extends MultiblockWithDisplayBase
        implements ICleanroomProvider, IWorkable, IDataInfoProvider {

    public static final int CLEAN_AMOUNT_THRESHOLD = 90;
    public static final int MIN_CLEAN_AMOUNT = 0;

    public static final int MIN_RADIUS = 2;
    public static final int MIN_DEPTH = 4;
    private static final int MIN_STRUCTURE_SIZE = 5;
    private static final int MAX_STRUCTURE_SIZE = 15;
    private static final int MAX_CLEANROOM_DOORS = 4;
    private static final String CLEANROOM_RUNTIME_PIECE = "runtime";
    private static final StructureContributionKey<ICleanroomFilter, CleanroomFilterAggregate> CLEANROOM_FILTER_KEY =
            StructureContributionKey.create(
                    "gregtech:cleanroom/filter",
                    "cleanroom-filter",
                    CleanroomFilterAggregate::new,
                    (current, emitted) -> {
                        current.add(emitted);
                        return current;
                    },
                    CleanroomFilterAggregate::validate,
                    UnaryOperator.identity(),
                    CleanroomFilterAggregate::copy);
    private static final StructureContributionKey<BlockPos, Set<BlockPos>> CLEANROOM_DOORS_KEY =
            StructureContributionKey.create(
                    "gregtech:cleanroom/doors",
                    "set-union",
                    LinkedHashSet::new,
                    (current, emitted) -> {
                        Set<BlockPos> result = new LinkedHashSet<>(current);
                        if (emitted != null) {
                            result.add(emitted.toImmutable());
                        }
                        return result;
                    },
                    aggregate -> {
                        int doorCount = aggregate == null ? 0 : aggregate.size();
                        if (doorCount > MAX_CLEANROOM_DOORS) {
                            return StructureContributionKey.Validation.failure(
                                    "Cleanroom accepts at most " + MAX_CLEANROOM_DOORS + " doors");
                        }
                        return StructureContributionKey.Validation.success();
                    },
                    BlockPos::toImmutable,
                    value -> Collections.unmodifiableSet(new LinkedHashSet<>(value)));
    private static final StructureContributionKey<CleanroomDimensions, CleanroomDimensions>
            CLEANROOM_DIMENSIONS_KEY = StructureContributionKey.uniform(
                    "gregtech:cleanroom/dimensions");
    private static final StructureContributionKey<ICleanroomReceiver, Set<ICleanroomReceiver>>
            CLEANROOM_RECEIVERS_KEY = StructureContributionKey.setUnion(
                    "gregtech:cleanroom/receivers");
    private static final StructureContributionKey<Integer, Integer> CLEANROOM_WIDTH_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_WIDTH.getName());
    private static final StructureContributionKey<Integer, Integer> CLEANROOM_HEIGHT_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_HEIGHT.getName());
    private static final StructureContributionKey<Integer, Integer> CLEANROOM_LENGTH_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_LENGTH.getName());
    private static final CleanroomDoorElement CLEANROOM_DOOR_ELEMENT = new CleanroomDoorElement();
    private static final CleanroomFilterElement CLEANROOM_FILTER_ELEMENT = new CleanroomFilterElement();
    private static final CleanroomInnerElement CLEANROOM_INNER_ELEMENT = new CleanroomInnerElement();
    private static final StructureDefinition<MetaTileEntityCleanroom> STRUCTURE_DEFINITION =
            StructureDefinition.getOrBuild("gregtech:cleanroom", () ->
                    StructureDefinition.<MetaTileEntityCleanroom>builder(
                            RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                            .piece(CLEANROOM_RUNTIME_PIECE, "S")
                            .where('S', gregtech.api.pattern.element.Elements.self(
                                    MetaTileEntityCleanroom.class))
                            .end()
                            .globalAbilityLimit(MultiblockAbility.INPUT_ENERGY, 1, 3)
                            .globalAbilityLimit(
                                    MultiblockAbility.MAINTENANCE_HATCH,
                                    ConfigHolder.machines.enableMaintenance ? 1 : 0,
                                    1)
                            .runtimeDetector(MetaTileEntityCleanroom::detectRuntimeStructure)
                            .build());

    private int lDist = 0;
    private int rDist = 0;
    private int bDist = 0;
    private int fDist = 0;
    private int hDist = 0;

    private CleanroomType cleanroomType = null;
    private int cleanAmount;

    private IEnergyContainer energyContainer;

    private ICleanroomFilter cleanroomFilter;
    private final CleanroomLogic cleanroomLogic;
    private final Collection<ICleanroomReceiver> cleanroomReceivers = new HashSet<>();

    private Set<BlockPos> doors = Collections.emptySet();
    private int openBlocks = 0;

    public MetaTileEntityCleanroom(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        this.cleanroomLogic = new CleanroomLogic(this, GTValues.LV);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCleanroom(metaTileEntityId);
    }

    protected void initializeAbilities() {
        this.energyContainer = new EnergyContainerList(getAbilities(MultiblockAbility.INPUT_ENERGY));
    }

    private void resetTileAbilities() {
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        CleanroomDimensions dimensions = formed.getAggregate(CLEANROOM_DIMENSIONS_KEY);
        if (dimensions == null) {
            invalidateStructure();
            return;
        }
        applyStructureDimensions(dimensions);
        initializeAbilities();
        CleanroomFilterAggregate filterAggregate = formed.getAggregate(CLEANROOM_FILTER_KEY);
        this.cleanroomFilter = filterAggregate == null ? null : filterAggregate.getFilter();
        if (cleanroomFilter == null) {
            invalidateStructure();
            return;
        }
        this.cleanroomType = cleanroomFilter.getCleanroomType();

        // max progress is based on the dimensions of the structure: (x^3)-(x^2)
        // taller cleanrooms take longer than wider ones
        // minimum of 100 is a 5x5x5 cleanroom: 125-25=100 ticks
        this.cleanroomLogic.setMaxProgress(Math.max(100,
                ((lDist + rDist + 1) * (bDist + fDist + 1) * hDist) - ((lDist + rDist + 1) * (bDist + fDist + 1))));
        this.cleanroomLogic.setMinEnergyTier(cleanroomFilter.getMinTier());

        Set<BlockPos> matchedDoors = formed.getAggregate(CLEANROOM_DOORS_KEY);
        this.doors = matchedDoors == null ? Collections.emptySet() : matchedDoors;
        Set<ICleanroomReceiver> matchedReceivers = formed.getAggregate(CLEANROOM_RECEIVERS_KEY);
        updateCleanroomReceivers(
                matchedReceivers == null ? Collections.emptySet() : matchedReceivers);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
        this.cleanroomLogic.invalidate();
        this.cleanAmount = MIN_CLEAN_AMOUNT;
        cleanroomReceivers.forEach(receiver -> {
            if (receiver.getCleanroom() == this) {
                receiver.unsetCleanroom();
            }
        });
        cleanroomReceivers.clear();
        this.doors = Collections.emptySet();
        this.openBlocks = 0;
    }

    @Override
    protected void updateFormedValid() {
        if (!getWorld().isRemote) {
            this.cleanroomLogic.updateLogic();
            if (this.cleanroomLogic.wasActiveAndNeedsUpdate()) {
                this.cleanroomLogic.setWasActiveAndNeedsUpdate(false);
                this.cleanroomLogic.setActive(false);
            }
        }
    }

    @Override
    public void checkStructurePattern() {
        super.checkStructurePattern();
        if (isStructureFormed()) {
            if (doors != null) checkDoors();
        }
    }

    protected static class DoorCheckingContext {

        private World world;
        private BlockPos doorPos;
        private IBlockState doorState;
        private EnumFacing doorFacing;
        private EnumFacing actualDoorFacing;
        private boolean doorOpen;
        private int openDoors;
        private int checkX;
        private int checkZ;
        private boolean doorOnPositive;
        private boolean doorOnNegative;

        public void init(BlockPos pos, IBlockState state) {
            this.doorPos = pos;
            this.doorState = state.getActualState(this.world, this.doorPos);
            this.doorFacing = this.doorState.getValue(BlockDoor.FACING);
            this.doorOpen = this.doorState.getValue(BlockDoor.OPEN);
            this.actualDoorFacing = getActualDoorFacing(this.doorFacing, this.doorState.getValue(BlockDoor.HINGE),
                    this.doorOpen);
            this.checkX = this.doorOpen ? Math.abs(this.doorFacing.getXOffset()) :
                    1 - Math.abs(this.doorFacing.getXOffset()); // 1 or 0
            this.checkZ = 1 - this.checkX; // inversion of x since facing can only face in x or z
            this.doorOnPositive = false;
            this.doorOnNegative = false;
        }

        public void setDoor(boolean positive) {
            if (positive) this.doorOnPositive = true;
            else this.doorOnNegative = true;
        }

        public boolean isDoor(boolean positive) {
            return positive ? this.doorOnPositive : this.doorOnNegative;
        }
    }

    public void checkDoors() {
        DoorCheckingContext context = new DoorCheckingContext();
        context.world = getWorld();
        context.openDoors = 0;
        for (BlockPos pos : this.doors) {
            IBlockState state = getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof BlockDoor)) {
                invalidateStructure();
                return;
            }
            context.init(pos, state);
            determineOpenDoors(context);
        }
        if (this.openBlocks != context.openDoors && context.world instanceof WorldServer worldServer) {
            List<EntityPlayerMP> players = worldServer.getMinecraftServer().getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                // for debug
                players.get(0).sendMessage(new TextComponentString("Open blocks: " + context.openDoors));
            }
        }
        this.openBlocks = context.openDoors;
    }

    protected void determineOpenDoors(DoorCheckingContext context) {
        int x = context.doorPos.getX();
        int z = context.doorPos.getZ();
        int y = context.doorPos.getY();
        int cx = context.checkX;
        int cz = context.checkZ;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // use negative facing on positive side since we are considering the neighboring block
        if (!isBlockBlockingDoor(context, pos.setPos(x + cx, y, z + cz), false) ||
                !isBlockBlockingDoor(context, pos.setPos(x - cx, y, z - cz), true)) {
            context.openDoors++;
        }
        if ((!context.doorOnPositive &&
                !isBlockBlockingDoor(context, pos.setPos(x + cx, y + 1, z + cz), false)) ||
                (!context.doorOnNegative &&
                        !isBlockBlockingDoor(context, pos.setPos(x - cx, y + 1, z - cz), true))) {
            context.openDoors++;
        }
    }

    private static EnumFacing getActualDoorFacing(EnumFacing facing, BlockDoor.EnumHingePosition hinge, boolean open) {
        if (!open) return facing;
        return hinge == BlockDoor.EnumHingePosition.LEFT ? facing.rotateY() : facing.rotateYCCW();
    }

    protected boolean isBlockBlockingDoor(DoorCheckingContext context, BlockPos neighborPos, boolean positive) {
        // we could make a generalized check with bounding box here but this would leave room for bypassing this check
        // simply checking if the block is potentially part of the wall is enough
        IBlockState state = context.world.getBlockState(neighborPos);
        // casing and glass
        if (isCleanroomWallState(state)) {
            return true;
        }
        // multiblock abilities
        MetaTileEntity mte = GTUtility.getMetaTileEntity(context.world, neighborPos);
        if (mte instanceof IMultiblockAbilityPart<?> multiblockAbilityPart) {
            List<MultiblockAbility<?>> abilities = multiblockAbilityPart.getAbilities();
            if (abilities.isEmpty()) return false;
            return abilities.contains(MultiblockAbility.MUFFLER_HATCH) ||
                    abilities.contains(MultiblockAbility.MAINTENANCE_HATCH) ||
                    abilities.contains(MultiblockAbility.PASSTHROUGH_HATCH) ||
                    abilities.contains(MultiblockAbility.INPUT_ENERGY);
        } else if (mte != null) {
            return false;
        }
        // double doors
        if (state.getBlock() instanceof BlockDoor) {
            if (context.isDoor(positive)) {
                // the bottom already had doors, and we don't need to check again
                return true;
            }
            if (!this.doors.contains(neighborPos)) {
                // don't worry about doors which are not part of the structure
                return false;
            }
            state = state.getActualState(context.world, neighborPos);
            BlockDoor.EnumDoorHalf half = state.getValue(BlockDoor.HALF);
            BlockDoor.EnumHingePosition hinge = state.getValue(BlockDoor.HINGE);
            EnumFacing facing = state.getValue(BlockDoor.FACING);
            boolean open = state.getValue(BlockDoor.OPEN);
            EnumFacing actualFacing = getActualDoorFacing(facing, hinge, open);
            if (half == BlockDoor.EnumDoorHalf.LOWER) {
                context.setDoor(positive);
            }
            if (context.actualDoorFacing == actualFacing) {
                // if door face the same direction and the other door is open it will count that by itself so we accept
                return true;
            }
            // I can't really explain why, but this needed
            return context.actualDoorFacing.rotateY() == actualFacing ||
                    context.actualDoorFacing.rotateYCCW() == actualFacing;
        }
        return false;
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    public boolean allowsFlip() {
        return false;
    }

    /**
     * Scans for blocks around the controller to update the dimensions
     */
    public boolean updateStructureDimensions() {
        DimensionScanResult result = scanStructureDimensions();
        if (!result.isSuccess()) {
            invalidateStructure();
            return false;
        }
        applyStructureDimensions(result.dimensions);
        return true;
    }

    @NotNull
    private DimensionScanResult scanStructureDimensions() {
        World world = getWorld();
        if (world == null) {
            return DimensionScanResult.failure(
                    getPos(), "loaded world", "cleanroom controller has no world");
        }

        EnumFacing front = getFrontFacing();
        EnumFacing back = front.getOpposite();
        EnumFacing left = front.rotateYCCW();
        EnumFacing right = left.getOpposite();
        BlockPos origin = getPos();

        int leftDistance = findHorizontalBoundary(world, origin, left);
        int rightDistance = findHorizontalBoundary(world, origin, right);
        int backDistance = findHorizontalBoundary(world, origin, back);
        int frontDistance = findHorizontalBoundary(world, origin, front);
        int heightDistance = findFloorBoundary(world, origin);

        if (leftDistance < MIN_RADIUS) {
            return missingBoundary(origin.offset(left, Math.max(1, leftDistance)), "left", leftDistance);
        }
        if (rightDistance < MIN_RADIUS) {
            return missingBoundary(origin.offset(right, Math.max(1, rightDistance)), "right", rightDistance);
        }
        if (backDistance < MIN_RADIUS) {
            return missingBoundary(origin.offset(back, Math.max(1, backDistance)), "back", backDistance);
        }
        if (frontDistance < MIN_RADIUS) {
            return missingBoundary(origin.offset(front, Math.max(1, frontDistance)), "front", frontDistance);
        }
        if (heightDistance < MIN_DEPTH) {
            return DimensionScanResult.failure(
                    origin.down(Math.max(1, heightDistance)),
                    "cleanroom floor at least " + MIN_DEPTH + " blocks below the controller",
                    "detected depth " + heightDistance);
        }
        return DimensionScanResult.success(new CleanroomDimensions(
                leftDistance, rightDistance, backDistance, frontDistance, heightDistance));
    }

    private int findHorizontalBoundary(@NotNull World world,
                                       @NotNull BlockPos origin,
                                       @NotNull EnumFacing direction) {
        for (int distance = 1; distance <= MAX_STRUCTURE_SIZE / 2; distance++) {
            if (world.getBlockState(origin.offset(direction, distance)).equals(getCasingState())) {
                return distance;
            }
        }
        return 0;
    }

    private int findFloorBoundary(@NotNull World world, @NotNull BlockPos origin) {
        for (int distance = 1; distance < MAX_STRUCTURE_SIZE; distance++) {
            IBlockState state = world.getBlockState(origin.down(distance));
            if (state.equals(getCasingState()) || state.equals(getGlassState())) {
                return distance;
            }
        }
        return 0;
    }

    @NotNull
    private static DimensionScanResult missingBoundary(@NotNull BlockPos pos,
                                                       @NotNull String side,
                                                       int distance) {
        return DimensionScanResult.failure(
                pos,
                "plascrete " + side + " boundary at least " + MIN_RADIUS
                        + " blocks from the controller",
                distance == 0 ? "no boundary detected" : "detected distance " + distance);
    }

    private void applyStructureDimensions(@NotNull CleanroomDimensions dimensions) {
        boolean changed = this.lDist != dimensions.left
                || this.rDist != dimensions.right
                || this.bDist != dimensions.back
                || this.fDist != dimensions.front
                || this.hDist != dimensions.height;
        this.lDist = dimensions.left;
        this.rDist = dimensions.right;
        this.bDist = dimensions.back;
        this.fDist = dimensions.front;
        this.hDist = dimensions.height;
        if (!changed || getWorld() == null || getWorld().isRemote) {
            return;
        }
        writeCustomData(GregtechDataCodes.UPDATE_STRUCTURE_SIZE, buf -> {
            buf.writeInt(this.lDist);
            buf.writeInt(this.rDist);
            buf.writeInt(this.bDist);
            buf.writeInt(this.fDist);
            buf.writeInt(this.hDist);
        });
    }

    /**
     * @param world     the world to check
     * @param pos       the pos to check and move
     * @param direction the direction to move
     * @return if a block is a valid wall block at pos moved in direction
     */
    public boolean isBlockEdge(@NotNull World world, @NotNull BlockPos.MutableBlockPos pos,
                               @NotNull EnumFacing direction) {
        return world.getBlockState(pos.move(direction)) ==
                MetaBlocks.CLEANROOM_CASING.getState(BlockCleanroomCasing.CasingType.PLASCRETE);
    }

    /**
     * @param world     the world to check
     * @param pos       the pos to check and move
     * @param direction the direction to move
     * @return if a block is a valid floor block at pos moved in direction
     */
    public boolean isBlockFloor(@NotNull World world, @NotNull BlockPos.MutableBlockPos pos,
                                @NotNull EnumFacing direction) {
        return isBlockEdge(world, pos, direction) || isCleanroomWallState(world.getBlockState(pos));
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    private static boolean detectRuntimeStructure(
            @NotNull StructureRuntimeDetectionContext<MetaTileEntityCleanroom> context) {
        MetaTileEntityCleanroom controller = context.getController();
        DimensionScanResult scan = controller.scanStructureDimensions();
        if (!scan.isSuccess()) {
            return context.fail(scan.failurePos, scan.expected, scan.actual);
        }

        CleanroomDimensions dimensions = scan.dimensions;
        context.emit(CLEANROOM_DIMENSIONS_KEY, dimensions);
        context.emit(CLEANROOM_WIDTH_KEY, dimensions.getWidth());
        context.emit(CLEANROOM_HEIGHT_KEY, dimensions.getStructureHeight());
        context.emit(CLEANROOM_LENGTH_KEY, dimensions.getLength());

        RuntimeCellElements elements = controller.createRuntimeCellElements();
        BlockPos origin = context.getControllerPos();
        EnumFacing front = controller.getFrontFacing();
        EnumFacing right = front.rotateY();

        for (int depth = 0; depth <= dimensions.height; depth++) {
            for (int forward = -dimensions.back; forward <= dimensions.front; forward++) {
                for (int lateral = -dimensions.left; lateral <= dimensions.right; lateral++) {
                    CleanroomCellType cellType = classifyCell(
                            lateral, forward, depth, dimensions);
                    BlockPos pos = offset(origin, right, lateral, front, forward).down(depth);
                    IStructureElement<?> element = elements.get(cellType);
                    if (!context.match(pos, element)) {
                        return context.fail(
                                pos, cellType.expected,
                                String.valueOf(context.getWorld().getBlockState(pos)));
                    }
                }
            }
        }
        return true;
    }

    @NotNull
    private RuntimeCellElements createRuntimeCellElements() {
        IStructureElement<?> casing = gregtech.api.pattern.element.Elements.blockPredicate(
                state -> state.equals(getCasingState()),
                () -> new BlockInfo[] { new BlockInfo(getCasingState()) });
        IStructureElement<?> wall = gregtech.api.pattern.element.Elements.blockPredicate(
                this::isCleanroomWallState,
                this::getCleanroomWallCandidates);
        List<IStructureElement<?>> baseAlternatives = new ArrayList<>();
        baseAlternatives.add(casing);
        List<IStructureElement<?>> wallAlternatives = new ArrayList<>();
        wallAlternatives.add(wall);

        if (hasMaintenanceMechanics()) {
            HatchElement maintenance = new HatchElement(
                    MultiblockAbility.MAINTENANCE_HATCH,
                    ConfigHolder.machines.enableMaintenance ? 1 : 0, 1);
            baseAlternatives.add(maintenance);
            wallAlternatives.add(maintenance);
        }
        if (hasMufflerMechanics()) {
            HatchElement muffler = new HatchElement(
                    MultiblockAbility.MUFFLER_HATCH, 1, 1);
            baseAlternatives.add(muffler);
            wallAlternatives.add(muffler);
        }

        HatchElement energy = new HatchElement(
                MultiblockAbility.INPUT_ENERGY, 1, 3);
        baseAlternatives.add(energy);
        wallAlternatives.add(energy);
        wallAlternatives.add(new HatchElement(
                MultiblockAbility.PASSTHROUGH_HATCH, 0, 30));
        wallAlternatives.add(CLEANROOM_DOOR_ELEMENT);

        return new RuntimeCellElements(
                gregtech.api.pattern.element.Elements.self(MetaTileEntityCleanroom.class),
                new ChainElement(baseAlternatives.toArray(new IStructureElement[0])),
                new ChainElement(wallAlternatives.toArray(new IStructureElement[0])),
                wall,
                CLEANROOM_FILTER_ELEMENT,
                CLEANROOM_INNER_ELEMENT);
    }

    private boolean isCleanroomWallState(@NotNull IBlockState state) {
        return state.equals(getCasingState()) || state.equals(getGlassState());
    }

    @NotNull
    private BlockInfo[] getCleanroomWallCandidates() {
        return new BlockInfo[] {
                new BlockInfo(getCasingState()),
                new BlockInfo(getGlassState())
        };
    }

    @NotNull
    private static BlockPos offset(@NotNull BlockPos origin,
                                   @NotNull EnumFacing right,
                                   int lateral,
                                   @NotNull EnumFacing front,
                                   int forward) {
        BlockPos result = lateral >= 0
                ? origin.offset(right, lateral)
                : origin.offset(right.getOpposite(), -lateral);
        return forward >= 0
                ? result.offset(front, forward)
                : result.offset(front.getOpposite(), -forward);
    }

    @NotNull
    private static CleanroomCellType classifyCell(
            int lateral,
            int forward,
            int depth,
            @NotNull CleanroomDimensions dimensions) {
        boolean lateralBoundary =
                lateral == -dimensions.left || lateral == dimensions.right;
        boolean forwardBoundary =
                forward == -dimensions.back || forward == dimensions.front;

        if (depth == 0) {
            if (lateral == 0 && forward == 0) {
                return CleanroomCellType.CONTROLLER;
            }
            return lateralBoundary || forwardBoundary
                    ? CleanroomCellType.BASE_BOUNDARY
                    : CleanroomCellType.FILTER;
        }
        if (depth == dimensions.height) {
            if (lateralBoundary || forwardBoundary) {
                return CleanroomCellType.BASE_BOUNDARY;
            }
            if (lateral == 0 && forward == 0) {
                return CleanroomCellType.FOUNDATION;
            }
            return CleanroomCellType.WALL_SLOT;
        }
        if (lateralBoundary && forwardBoundary) {
            return CleanroomCellType.BASE_BOUNDARY;
        }
        if (lateralBoundary || forwardBoundary) {
            return CleanroomCellType.WALL_SLOT;
        }
        return CleanroomCellType.INTERIOR;
    }

    private void updateCleanroomReceivers(@NotNull Set<ICleanroomReceiver> matchedReceivers) {
        cleanroomReceivers.removeIf(receiver -> {
            if (matchedReceivers.contains(receiver)) {
                return false;
            }
            if (receiver.getCleanroom() == this) {
                receiver.unsetCleanroom();
            }
            return true;
        });
        for (ICleanroomReceiver receiver : matchedReceivers) {
            if (receiver.getCleanroom() != this) {
                receiver.setCleanroom(this);
            }
            cleanroomReceivers.add(receiver);
        }
    }

    @NotNull
    @Override
    public List<StructureChannel> getSupportedChannels() {
        return Arrays.asList(
                GTStructureChannels.STRUCTURE_WIDTH,
                GTStructureChannels.STRUCTURE_HEIGHT,
                GTStructureChannels.STRUCTURE_LENGTH);
    }

    @NotNull
    @Override
    public int[] getChannelRange(@NotNull StructureChannel channel) {
        String channelName = channel.getName();
        if (GTStructureChannels.STRUCTURE_WIDTH.getName().equals(channelName) ||
                GTStructureChannels.STRUCTURE_HEIGHT.getName().equals(channelName) ||
                GTStructureChannels.STRUCTURE_LENGTH.getName().equals(channelName)) {
            return new int[] { MIN_STRUCTURE_SIZE, MAX_STRUCTURE_SIZE };
        }
        return super.getChannelRange(channel);
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        return getMatchingShapes(Collections.emptyMap());
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes(@Nullable Map<String, Integer> channelValues) {
        StructureRuntime runtime = createToolingRuntime(channelValues);
        return Collections.singletonList(new MultiblockShapeInfo(
                withRepresentativeCleanroomWallReplacements(
                        runtime.previewSingle(StructureOperationRequest.preview(
                                getToolingRepetitions(channelValues), channelValues)))));
    }

    @NotNull
    private BlockInfo[][][] withRepresentativeCleanroomWallReplacements(@NotNull BlockInfo[][][] blocks) {
        int width = blocks.length;
        if (width < MIN_STRUCTURE_SIZE || blocks[0].length < MIN_STRUCTURE_SIZE ||
                blocks[0][0].length < MIN_STRUCTURE_SIZE) {
            return blocks;
        }

        int height = blocks[0].length;
        int length = blocks[0][0].length;
        int glassY = Math.max(1, Math.min(height - 2, height / 2));
        int middleZ = length / 2;
        BlockInfo cleanroomGlass = new BlockInfo(getGlassState());
        blocks[0][glassY][middleZ] = cleanroomGlass;
        blocks[width - 1][glassY][middleZ] = cleanroomGlass;

        int doorX = width / 2;
        int doorZ = length - 1;
        blocks[doorX][1][doorZ] = new BlockInfo(Blocks.IRON_DOOR.getDefaultState()
                .withProperty(BlockDoor.FACING, EnumFacing.NORTH)
                .withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER));
        blocks[doorX][2][doorZ] = new BlockInfo(Blocks.IRON_DOOR.getDefaultState()
                .withProperty(BlockDoor.FACING, EnumFacing.NORTH)
                .withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER));
        return blocks;
    }

    @NotNull
    @Override
    public Map<BlockPos, StructureElementPreviewEntry> buildStructurePreviewEntries(
            @Nullable Map<String, Integer> channelValues) {
        StructureRuntime runtime = createToolingRuntime(channelValues);
        StructurePreviewResult result = runtime.previewSingleResult(
                StructureOperationRequest.preview(getToolingRepetitions(channelValues), channelValues));
        gregtech.api.pattern.PieceRuntimeState.PreviewCells cells =
                result.getSinglePieceCells();
        if (cells == null || cells.getPreviewEntries().isEmpty()) {
            return Collections.emptyMap();
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : cells.getBlocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        Map<BlockPos, StructureElementPreviewEntry> normalized = new java.util.HashMap<>();
        for (Map.Entry<BlockPos, StructureElementPreviewEntry> entry :
                cells.getPreviewEntries().entrySet()) {
            BlockPos pos = entry.getKey();
            normalized.put(
                    new BlockPos(
                            pos.getX() - minX,
                            pos.getY() - minY,
                            pos.getZ() - minZ),
                    entry.getValue());
        }
        return normalized;
    }

    @Override
    public boolean autoBuildStructure(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        createToolingRuntime(request.getChannelValues()).buildAllPieces(request);
        return true;
    }

    @Override
    public void spawnStructureHints(@NotNull StructureOperationRequest request) {
        hintStructure(request);
    }

    @Override
    @NotNull
    public StructureHintResult hintStructure(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        return createToolingRuntime(request.getChannelValues()).hintAllPieces(request);
    }

    @NotNull
    private StructureRuntime createToolingRuntime(@Nullable Map<String, Integer> channelValues) {
        return createDynamicStructureRuntime(buildToolingDefinition(channelValues));
    }

    @NotNull
    private StructureDefinition<MetaTileEntityCleanroom> buildToolingDefinition(
            @Nullable Map<String, Integer> channelValues) {
        return StructureDefinition.<MetaTileEntityCleanroom>builder(
                RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
                .pieceFromTemplate(CLEANROOM_RUNTIME_PIECE, buildToolingTemplate(channelValues))
                .end()
                .globalAbilityLimit(MultiblockAbility.INPUT_ENERGY, 1, 3)
                .globalAbilityLimit(
                        MultiblockAbility.MAINTENANCE_HATCH,
                        ConfigHolder.machines.enableMaintenance ? 1 : 0,
                        1)
                .build();
    }

    @NotNull
    private PieceTemplate buildToolingTemplate(@Nullable Map<String, Integer> channelValues) {
        int width = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_WIDTH.getName());
        int height = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_HEIGHT.getName());
        int length = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_LENGTH.getName());

        CleanroomDimensions dimensions = CleanroomDimensions.centered(width, height, length);
        RuntimeCellElements elements = createRuntimeCellElements();
        IStructureElement<?>[][][] template = new IStructureElement<?>[dimensions.getLength()]
                [dimensions.getStructureHeight()][dimensions.getWidth()];
        for (int forward = -dimensions.back; forward <= dimensions.front; forward++) {
            int z = forward + dimensions.back;
            for (int depth = dimensions.height; depth >= 0; depth--) {
                int y = dimensions.height - depth;
                for (int lateral = -dimensions.left; lateral <= dimensions.right; lateral++) {
                    int x = lateral + dimensions.left;
                    template[z][y][x] = elements.get(classifyCell(lateral, forward, depth, dimensions));
                }
            }
        }
        int[] centerOffset = new int[] {
                dimensions.left,
                dimensions.height,
                dimensions.back,
                dimensions.back,
                dimensions.back
        };
        int[][] repetitions = new int[dimensions.getLength()][2];
        for (int i = 0; i < repetitions.length; i++) {
            repetitions[i][0] = 1;
            repetitions[i][1] = 1;
        }
        return new PieceTemplate(
                template,
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT
                },
                repetitions,
                new String[repetitions.length],
                centerOffset,
                null);
    }

    private static int resolveChannelSize(@Nullable Map<String, Integer> channelValues,
                                          @NotNull String channelName) {
        if (channelValues == null) return MAX_STRUCTURE_SIZE;
        Integer value = channelValues.get(channelName);
        if (value == null || value <= 0) return MAX_STRUCTURE_SIZE;
        if (value == 1) return MIN_STRUCTURE_SIZE;
        return Math.max(MIN_STRUCTURE_SIZE, Math.min(MAX_STRUCTURE_SIZE, value));
    }

    @NotNull
    private int[] getToolingRepetitions(@Nullable Map<String, Integer> channelValues) {
        int length = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_LENGTH.getName());
        int[] repetitions = new int[length];
        Arrays.fill(repetitions, 1);
        return repetitions;
    }

    private enum CleanroomCellType {

        CONTROLLER("cleanroom controller"),
        BASE_BOUNDARY("plascrete casing or a permitted ability hatch"),
        WALL_SLOT("cleanroom wall, permitted ability hatch, passthrough hatch, or door"),
        FOUNDATION("cleanroom casing or cleanroom glass beneath the controller"),
        FILTER("cleanroom filter"),
        INTERIOR("valid cleanroom interior");

        @NotNull
        private final String expected;

        CleanroomCellType(@NotNull String expected) {
            this.expected = expected;
        }
    }

    private static final class RuntimeCellElements {

        @NotNull
        private final IStructureElement<?> controller;
        @NotNull
        private final IStructureElement<?> baseBoundary;
        @NotNull
        private final IStructureElement<?> wallSlot;
        @NotNull
        private final IStructureElement<?> foundation;
        @NotNull
        private final IStructureElement<?> filter;
        @NotNull
        private final IStructureElement<?> interior;

        private RuntimeCellElements(
                @NotNull IStructureElement<?> controller,
                @NotNull IStructureElement<?> baseBoundary,
                @NotNull IStructureElement<?> wallSlot,
                @NotNull IStructureElement<?> foundation,
                @NotNull IStructureElement<?> filter,
                @NotNull IStructureElement<?> interior) {
            this.controller = controller.compile();
            this.baseBoundary = baseBoundary.compile();
            this.wallSlot = wallSlot.compile();
            this.foundation = foundation.compile();
            this.filter = filter.compile();
            this.interior = interior.compile();
        }

        @NotNull
        private IStructureElement<?> get(@NotNull CleanroomCellType type) {
            switch (type) {
                case CONTROLLER:
                    return controller;
                case BASE_BOUNDARY:
                    return baseBoundary;
                case WALL_SLOT:
                    return wallSlot;
                case FOUNDATION:
                    return foundation;
                case FILTER:
                    return filter;
                case INTERIOR:
                    return interior;
                default:
                    throw new IllegalStateException("Unhandled cleanroom cell type " + type);
            }
        }
    }

    private static final class CleanroomDimensions {

        private final int left;
        private final int right;
        private final int back;
        private final int front;
        private final int height;

        private CleanroomDimensions(int left, int right, int back, int front, int height) {
            this.left = left;
            this.right = right;
            this.back = back;
            this.front = front;
            this.height = height;
        }

        @NotNull
        private static CleanroomDimensions centered(int width, int structureHeight, int length) {
            int left = (width - 1) / 2;
            int right = width - 1 - left;
            int back = (length - 1) / 2;
            int front = length - 1 - back;
            return new CleanroomDimensions(left, right, back, front, structureHeight - 1);
        }

        private int getWidth() {
            return left + right + 1;
        }

        private int getLength() {
            return back + front + 1;
        }

        private int getStructureHeight() {
            return height + 1;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CleanroomDimensions)) return false;
            CleanroomDimensions that = (CleanroomDimensions) object;
            return left == that.left
                    && right == that.right
                    && back == that.back
                    && front == that.front
                    && height == that.height;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + right;
            result = 31 * result + back;
            result = 31 * result + front;
            result = 31 * result + height;
            return result;
        }

        @Override
        public String toString() {
            return "left=" + left + ", right=" + right
                    + ", back=" + back + ", front=" + front
                    + ", height=" + height;
        }
    }

    private static final class DimensionScanResult {

        @Nullable
        private final CleanroomDimensions dimensions;
        @NotNull
        private final BlockPos failurePos;
        @NotNull
        private final String expected;
        @NotNull
        private final String actual;

        private DimensionScanResult(
                @Nullable CleanroomDimensions dimensions,
                @NotNull BlockPos failurePos,
                @NotNull String expected,
                @NotNull String actual) {
            this.dimensions = dimensions;
            this.failurePos = failurePos.toImmutable();
            this.expected = expected;
            this.actual = actual;
        }

        @NotNull
        private static DimensionScanResult success(@NotNull CleanroomDimensions dimensions) {
            return new DimensionScanResult(
                    dimensions, BlockPos.ORIGIN, "detected cleanroom bounds", "matched");
        }

        @NotNull
        private static DimensionScanResult failure(
                @NotNull BlockPos pos,
                @NotNull String expected,
                @NotNull String actual) {
            return new DimensionScanResult(null, pos, expected, actual);
        }

        private boolean isSuccess() {
            return dimensions != null;
        }
    }

    private static final class CleanroomInnerElement implements ITypedStructureElement<Object> {

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            Object rawController = context.getController();
            if (!(rawController instanceof MetaTileEntityCleanroom)) {
                return false;
            }
            MetaTileEntityCleanroom controller = (MetaTileEntityCleanroom) rawController;
            TileEntity tileEntity = context.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity)) {
                return true;
            }

            MetaTileEntity metaTileEntity =
                    ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (metaTileEntity instanceof ICleanroomProvider
                    || controller.isMachineBanned(metaTileEntity)) {
                return false;
            }
            if (metaTileEntity instanceof ICleanroomReceiver) {
                context.getCollector().emit(
                        CLEANROOM_RECEIVERS_KEY,
                        (ICleanroomReceiver) metaTileEntity);
            }
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
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
    }

    private static final class CleanroomFilterAggregate {

        private ICleanroomFilter filter;
        private boolean mismatched;

        private void add(@Nullable ICleanroomFilter emitted) {
            if (emitted == null) {
                return;
            }
            if (filter == null) {
                filter = emitted;
                return;
            }
            if (!filter.getCleanroomType().equals(emitted.getCleanroomType())) {
                mismatched = true;
            }
        }

        @Nullable
        private ICleanroomFilter getFilter() {
            return filter;
        }

        @NotNull
        private StructureContributionKey.Validation validate() {
            if (filter == null) {
                return StructureContributionKey.Validation.failure("Cleanroom filter was not matched");
            }
            if (mismatched) {
                return StructureContributionKey.Validation.failure(
                        "Cleanroom filters must share the same cleanroom type");
            }
            return StructureContributionKey.Validation.success();
        }

        @NotNull
        private CleanroomFilterAggregate copy() {
            CleanroomFilterAggregate copy = new CleanroomFilterAggregate();
            copy.filter = filter;
            copy.mismatched = mismatched;
            return copy;
        }
    }

    private static final class CleanroomDoorElement implements ITypedStructureElement<Object> {

        @NotNull
        @Override
        public Set<StructureElementCapability> getCapabilities() {
            return StructureElementCapability.snapshotSafe();
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            IBlockState state = context.getBlockState();
            if (!(state.getBlock() instanceof BlockDoor)) {
                return false;
            }
            if (state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER) {
                context.getCollector().emit(CLEANROOM_DOORS_KEY, context.getPos());
            }
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @NotNull
        @Override
        public StructureElementPreview getPreview() {
            return StructureElementPreview.empty();
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
    }

    private static final class CleanroomFilterElement implements ITypedStructureElement<Object> {

        private final StructureElementPreview preview = StructureElementPreview.builder()
                .common(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                        .tooltip(() -> Collections.singletonList(
                                "gregtech.multiblock.pattern.error.filters"))
                        .build())
                .build();

        @NotNull
        @Override
        public Set<StructureElementCapability> getCapabilities() {
            return StructureElementCapability.snapshotSafe();
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            ICleanroomFilter filter = GregTechAPI.CLEANROOM_FILTERS.get(context.getBlockState());
            if (filter == null || filter.getCleanroomType() == null) {
                return false;
            }
            return context.transaction(transactionContext -> {
                transactionContext.getCollector().emit(CLEANROOM_FILTER_KEY, filter);
                transactionContext.getCollector().recordVariantActiveBlock(transactionContext.getPos());
                return true;
            });
        }

        @Override
        public BlockInfo[] getCandidates() {
            return GregTechAPI.CLEANROOM_FILTERS.entrySet().stream()
                    .filter(entry -> entry.getValue().getCleanroomType() != null)
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().getTier()))
                    .map(entry -> new BlockInfo(entry.getKey(), null))
                    .toArray(BlockInfo[]::new);
        }

        @Override
        public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                                  EntityPlayer player) {
            BlockInfo[] candidates = getCandidates();
            if (candidates.length == 0) {
                return false;
            }
            World world = context.getWorld();
            if (world == null) {
                return false;
            }
            world.setBlockState(context.getPos(), candidates[0].getBlockState());
            return true;
        }

        @NotNull
        @Override
        public StructureElementPreview getPreview() {
            return preview;
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

    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.PLASCRETE;
    }

    // protected to allow easy addition of addon "cleanrooms"
    @NotNull
    public static IBlockState getCasingState() {
        return MetaBlocks.CLEANROOM_CASING.getState(BlockCleanroomCasing.CasingType.PLASCRETE);
    }

    @NotNull
    protected IBlockState getGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.CLEANROOM_GLASS);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_MECHANICAL;
    }

    protected boolean isMachineBanned(MetaTileEntity metaTileEntity) {
        // blacklisted machines: mufflers and all generators, miners/drills, primitives
        if (metaTileEntity instanceof IMufflerHatch) return true;
        if (metaTileEntity instanceof SimpleGeneratorMetaTileEntity) return true;
        if (metaTileEntity instanceof FuelMultiblockController) return true;
        if (metaTileEntity instanceof IGenerator) return true;
        if (metaTileEntity instanceof MetaTileEntityLargeMiner) return true;
        if (metaTileEntity instanceof MetaTileEntityFluidDrill) return true;
        if (metaTileEntity instanceof MetaTileEntityCentralMonitor) return true;
        if (metaTileEntity instanceof MetaTileEntityCleanroom) return true;
        if (metaTileEntity instanceof MetaTileEntityCokeOven) return true;
        if (metaTileEntity instanceof MetaTileEntityPrimitiveBlastFurnace) return true;
        return metaTileEntity instanceof MetaTileEntityPrimitiveWaterPump;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(cleanroomLogic.isWorkingEnabled(), cleanroomLogic.isActive())
                .addEnergyUsageLine(energyContainer)
                .addEnergyUsageExactLine(isClean() ? 4 : GTValues.VA[getEnergyTier()])
                .addCustom((list, syncer) -> {
                    // Cleanliness status line
                    if (isStructureFormed()) {
                        IKey cleanState;
                        int amount = syncer.syncInt(cleanAmount);
                        if (amount >= CLEAN_AMOUNT_THRESHOLD) {
                            cleanState = KeyUtil.lang(TextFormatting.GREEN,
                                    "gregtech.multiblock.cleanroom.clean_state", amount);
                        } else {
                            cleanState = KeyUtil.lang(TextFormatting.DARK_RED,
                                    "gregtech.multiblock.cleanroom.dirty_state", amount);
                        }

                        list.add(KeyUtil.lang(TextFormatting.GRAY, "gregtech.multiblock.cleanroom.clean_status",
                                cleanState));
                    }
                })
                .addProgressLine(getProgress(), getMaxProgress())
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        boolean lowPower = false;
        if (isStructureFormed() && !getWorld().isRemote) {
            lowPower = !drainEnergy(true);
        }
        builder.addLowPowerLine(lowPower)
                .addCustom((list, syncer) -> {
                    if (isStructureFormed() && !syncer.syncBoolean(isClean())) {
                        list.add(KeyUtil.lang(TextFormatting.YELLOW,
                                "gregtech.multiblock.cleanroom.warning_contaminated"));
                    }

                    if (!syncer.syncBoolean(cleanroomLogic.isVoltageHighEnough())) {
                        IKey energyNeeded = IKey.str(GTValues.VNF[cleanroomFilter.getMinTier()]);
                        list.add(KeyUtil.lang(TextFormatting.YELLOW, "gregtech.multiblock.cleanroom.low_tier",
                                energyNeeded));
                    }
                });
        super.configureWarningText(builder);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.4"));

        if (TooltipHelper.isCtrlDown()) {
            tooltip.add("");
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.5"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.6"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.7"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.8"));
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.9"));
            if (Mods.AppliedEnergistics2.isModLoaded()) {
                tooltip.add(I18n.format(false ?
                        "gregtech.machine.cleanroom.tooltip.ae2.channels" :
                        "gregtech.machine.cleanroom.tooltip.ae2.no_channels"));
            }
            tooltip.add("");
        } else {
            tooltip.add(I18n.format("gregtech.machine.cleanroom.tooltip.hold_ctrl"));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), isActive(),
                isWorkingEnabled());
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.CLEANROOM_OVERLAY;
    }

    @Override
    public boolean checkCleanroomType(@NotNull CleanroomType type) {
        return type == this.cleanroomType;
    }

    @Override
    public void setCleanAmount(int amount) {
        this.cleanAmount = amount;
    }

    @Override
    public void adjustCleanAmount(int amount) {
        // do not allow negative cleanliness nor cleanliness above 100
        this.cleanAmount = MathHelper.clamp(this.cleanAmount + amount, 0, 100);
    }

    @Override
    public boolean isClean() {
        return this.cleanAmount >= CLEAN_AMOUNT_THRESHOLD;
    }

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        return Collections.singletonList(new TextComponentTranslation(
                isClean() ? "gregtech.multiblock.cleanroom.clean_state" : "gregtech.multiblock.cleanroom.dirty_state",
                this.cleanAmount));
    }

    @Override
    public boolean isActive() {
        return super.isActive() && this.cleanroomLogic.isActive();
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.cleanroomLogic.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isActivationAllowed) {
        if (!isActivationAllowed) // pausing sets not clean
            setCleanAmount(MIN_CLEAN_AMOUNT);
        this.cleanroomLogic.setWorkingEnabled(isActivationAllowed);
    }

    @Override
    public int getProgress() {
        return cleanroomLogic.getProgressTime();
    }

    @Override
    public int getMaxProgress() {
        return cleanroomLogic.getMaxProgress();
    }

    public int getProgressPercent() {
        return cleanroomLogic.getProgressPercent();
    }

    @Override
    public int getEnergyTier() {
        if (energyContainer == null) return GTValues.LV;
        return Math.min(GTValues.MAX,
                Math.max(GTValues.LV, GTUtility.getFloorTierByVoltage(energyContainer.getInputVoltage())));
    }

    @Override
    public long getEnergyInputPerSecond() {
        return energyContainer.getInputPerSec();
    }

    public boolean drainEnergy(boolean simulate) {
        long energyToDrain = isClean() ? 4 :
                GTValues.VA[getEnergyTier()];
        long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.changeEnergy(-energyToDrain);
            return true;
        }
        return false;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_WORKABLE)
            return GregtechTileCapabilities.CAPABILITY_WORKABLE.cast(this);
        if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE)
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        return super.getCapability(capability, side);
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_STRUCTURE_SIZE) {
            this.lDist = buf.readInt();
            this.rDist = buf.readInt();
            this.bDist = buf.readInt();
            this.fDist = buf.readInt();
            this.hDist = buf.readInt();
        } else if (dataId == GregtechDataCodes.WORKABLE_ACTIVE) {
            this.cleanroomLogic.setActive(buf.readBoolean());
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            this.cleanroomLogic.setWorkingEnabled(buf.readBoolean());
            scheduleRenderUpdate();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("lDist", this.lDist);
        data.setInteger("rDist", this.rDist);
        data.setInteger("bDist", this.bDist);
        data.setInteger("fDist", this.fDist);
        data.setInteger("hDist", this.hDist);
        data.setInteger("cleanAmount", this.cleanAmount);
        return this.cleanroomLogic.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.lDist = data.hasKey("lDist") ? data.getInteger("lDist") : this.lDist;
        this.rDist = data.hasKey("rDist") ? data.getInteger("rDist") : this.rDist;
        this.hDist = data.hasKey("hDist") ? data.getInteger("hDist") : this.hDist;
        this.bDist = data.hasKey("bDist") ? data.getInteger("bDist") : this.bDist;
        this.fDist = data.hasKey("fDist") ? data.getInteger("fDist") : this.fDist;
        this.cleanAmount = data.getInteger("cleanAmount");
        this.cleanroomLogic.readFromNBT(data);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.lDist);
        buf.writeInt(this.rDist);
        buf.writeInt(this.bDist);
        buf.writeInt(this.fDist);
        buf.writeInt(this.hDist);
        buf.writeInt(this.cleanAmount);
        this.cleanroomLogic.writeInitialSyncData(buf);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.lDist = buf.readInt();
        this.rDist = buf.readInt();
        this.bDist = buf.readInt();
        this.fDist = buf.readInt();
        this.hDist = buf.readInt();
        this.cleanAmount = buf.readInt();
        this.cleanroomLogic.receiveInitialSyncData(buf);
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        if (ConfigHolder.machines.enableCleanroom) {
            super.getSubItems(creativeTab, subItems);
        }
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }
}
