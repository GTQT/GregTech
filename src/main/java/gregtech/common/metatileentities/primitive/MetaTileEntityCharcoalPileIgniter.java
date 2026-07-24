package gregtech.common.metatileentities.primitive;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IWorkable;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PieceRuntimeState;
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
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.Mods;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.behaviors.LighterBehaviour;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.block.IBlock;
import crafttweaker.api.minecraft.CraftTweakerMC;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ZenClass("mods.gregtech.machines.CharcoalPileIgniter")
@ZenRegister
public class MetaTileEntityCharcoalPileIgniter extends MultiblockControllerBase implements IWorkable {

    private static final String DYNAMIC_PIECE_NAME = "charcoal_pile_dynamic";

    private static final int MIN_RADIUS = 1;
    private static final int MIN_DEPTH = 2;
    private static final int MAX_REPEAT = 4;
    private static final int MIN_LOG_WIDTH = 1;
    private static final int MAX_LOG_WIDTH = 9;
    private static final int MIN_LOG_HEIGHT = 1;
    private static final int MAX_LOG_HEIGHT = 4;
    private static final int MIN_LOG_LENGTH = 1;
    private static final int MAX_LOG_LENGTH = 9;

    private static final Set<Block> WALL_BLOCKS = new ObjectOpenHashSet<>();
    private static final StructureContributionKey<CharcoalDimensions, CharcoalDimensions>
            CHARCOAL_DIMENSIONS_KEY = StructureContributionKey.uniform(
                    "gregtech:charcoal_pile/dimensions");
    private static final StructureContributionKey<BlockPos, Set<BlockPos>>
            CHARCOAL_LOG_POSITIONS_KEY = StructureContributionKey.setUnion(
                    "gregtech:charcoal_pile/log_positions");
    private static final StructureContributionKey<Integer, Integer> CHARCOAL_WIDTH_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_WIDTH.getName());
    private static final StructureContributionKey<Integer, Integer> CHARCOAL_HEIGHT_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_HEIGHT.getName());
    private static final StructureContributionKey<Integer, Integer> CHARCOAL_LENGTH_KEY =
            StructureMatchCollector.channelValueKey(
                    GTStructureChannels.STRUCTURE_LENGTH.getName());
    private static final CharcoalLogElement CHARCOAL_LOG_ELEMENT =
            new CharcoalLogElement();
    private static final CharcoalWallElement CHARCOAL_WALL_ELEMENT =
            new CharcoalWallElement();
    private static final StructureDefinition<MetaTileEntityCharcoalPileIgniter>
            STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
                    "gregtech:charcoal_pile",
                    () -> StructureDefinition
                            .<MetaTileEntityCharcoalPileIgniter>builder(
                                    RelativeDirection.RIGHT,
                                    RelativeDirection.UP,
                                    RelativeDirection.BACK)
                            .piece(DYNAMIC_PIECE_NAME, "S")
                            .where('S', gregtech.api.pattern.element.Elements.self(
                                    MetaTileEntityCharcoalPileIgniter.class))
                            .end()
                            .runtimeDetector(
                                    MetaTileEntityCharcoalPileIgniter::detectRuntimeStructure)
                            .build());

    private final Collection<BlockPos> logPositions = new ObjectOpenHashSet<>();

    static {
        WALL_BLOCKS.add(Blocks.DIRT);
        WALL_BLOCKS.add(Blocks.GRASS);
        WALL_BLOCKS.add(Blocks.GRASS_PATH);
        WALL_BLOCKS.add(Blocks.SAND);
    }

    private int lDist = 0;
    private int rDist = 0;
    private int hDist = 0;

    private boolean isActive;
    private int progressTime = 0;
    private int maxProgress = 0;

    public MetaTileEntityCharcoalPileIgniter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        MinecraftForge.EVENT_BUS.register(MetaTileEntityCharcoalPileIgniter.class);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCharcoalPileIgniter(metaTileEntityId);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.CHARCOAL_PILE_OVERLAY.renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                isActive, true);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        setActive(false);
        this.progressTime = 0;
        this.maxProgress = 0;
        this.logPositions.clear();
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        CharcoalDimensions dimensions = formed.getAggregate(
                CHARCOAL_DIMENSIONS_KEY);
        if (dimensions == null) {
            invalidateStructure();
            return;
        }
        applyStructureDimensions(dimensions);
        Set<BlockPos> matchedLogs = formed.getAggregate(
                CHARCOAL_LOG_POSITIONS_KEY);
        logPositions.clear();
        if (matchedLogs != null) {
            logPositions.addAll(matchedLogs);
        }
        updateMaxProgressTime();
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
        if (GTStructureChannels.STRUCTURE_WIDTH.getName().equals(channelName)) {
            return new int[] { 0, MAX_LOG_WIDTH };
        }
        if (GTStructureChannels.STRUCTURE_HEIGHT.getName().equals(channelName)) {
            return new int[] { 0, MAX_LOG_HEIGHT };
        }
        if (GTStructureChannels.STRUCTURE_LENGTH.getName().equals(channelName)) {
            return new int[] { 0, MAX_LOG_LENGTH };
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
                runtime.previewSingle(StructureOperationRequest.preview(
                        getToolingRepetitions(channelValues), channelValues))));
    }

    @NotNull
    @Override
    public Map<BlockPos, StructureElementPreviewEntry> buildStructurePreviewEntries(
            @Nullable Map<String, Integer> channelValues) {
        StructureRuntime runtime = createToolingRuntime(channelValues);
        StructurePreviewResult result = runtime.previewSingleResult(
                StructureOperationRequest.preview(getToolingRepetitions(channelValues), channelValues));
        PieceRuntimeState.PreviewCells cells = result.getSinglePieceCells();
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
        Map<BlockPos, StructureElementPreviewEntry> normalized =
                new java.util.HashMap<>();
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
    @Override
    protected StructureRuntime createToolingPreviewRuntime(
            @Nullable Map<String, Integer> channelValues) {
        return createToolingRuntime(channelValues);
    }

    @NotNull
    private StructureDefinition<MetaTileEntityCharcoalPileIgniter> buildToolingDefinition(
            @Nullable Map<String, Integer> channelValues) {
        return StructureDefinition.<MetaTileEntityCharcoalPileIgniter>builder(
                RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                .pieceFromTemplate(DYNAMIC_PIECE_NAME, buildToolingTemplate(channelValues))
                .end()
                .build();
    }

    @NotNull
    private PieceTemplate buildToolingTemplate(@Nullable Map<String, Integer> channelValues) {
        CharcoalDimensions dimensions = resolveToolingDimensions(channelValues);
        RuntimeCellElements elements = createRuntimeCellElements();
        IStructureElement<?>[][][] template = new IStructureElement<?>[dimensions.getStructureLength()]
                [dimensions.getStructureHeight()][dimensions.getStructureWidth()];
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
        int[][] repetitions = new int[dimensions.getStructureLength()][2];
        for (int i = 0; i < repetitions.length; i++) {
            repetitions[i][0] = 1;
            repetitions[i][1] = 1;
        }
        return new PieceTemplate(
                template,
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                repetitions,
                new String[repetitions.length],
                centerOffset,
                null);
    }

    @NotNull
    private static CharcoalDimensions resolveToolingDimensions(@Nullable Map<String, Integer> channelValues) {
        int width = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_WIDTH.getName(),
                MIN_LOG_WIDTH, MAX_LOG_WIDTH);
        int height = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_HEIGHT.getName(),
                MIN_LOG_HEIGHT, MAX_LOG_HEIGHT);
        int length = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_LENGTH.getName(),
                MIN_LOG_LENGTH, MAX_LOG_LENGTH);
        return CharcoalDimensions.fromLogSize(width, height, length);
    }

    private static int resolveChannelSize(@Nullable Map<String, Integer> channelValues,
                                          @NotNull String channelName,
                                          int min, int max) {
        if (channelValues == null) return max;
        Integer value = channelValues.get(channelName);
        if (value == null || value <= 0) return max;
        if (value == 1) return min;
        return Math.max(min, Math.min(max, value));
    }

    @NotNull
    private static int[] getToolingRepetitions(@Nullable Map<String, Integer> channelValues) {
        int length = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_LENGTH.getName(),
                MIN_LOG_LENGTH, MAX_LOG_LENGTH) + 2;
        int[] repetitions = new int[length];
        Arrays.fill(repetitions, 1);
        return repetitions;
    }

    @NotNull
    private DimensionScanResult scanStructureDimensions() {
        World world = getWorld();
        if (world == null) {
            return DimensionScanResult.failure(
                    getPos(), "loaded world",
                    "charcoal pile controller has no world");
        }
        EnumFacing front = getFrontFacing();
        EnumFacing left = front.rotateYCCW();
        EnumFacing right = front.rotateY();
        EnumFacing back = front.getOpposite();
        BlockPos origin = getPos();
        BlockPos wallOrigin = origin.down();

        int leftDistance = findWallBoundary(world, wallOrigin, left);
        int rightDistance = findWallBoundary(world, wallOrigin, right);
        int backDistance = findWallBoundary(world, wallOrigin, back);
        int frontDistance = findWallBoundary(world, wallOrigin, front);
        int heightDistance = findFloorBoundary(world, origin);

        if (leftDistance < MIN_RADIUS) {
            return missingBoundary(
                    origin.offset(left, Math.max(1, leftDistance)),
                    "left", leftDistance);
        }
        if (rightDistance < MIN_RADIUS) {
            return missingBoundary(
                    origin.offset(right, Math.max(1, rightDistance)),
                    "right", rightDistance);
        }
        if (backDistance < MIN_RADIUS) {
            return missingBoundary(
                    origin.offset(back, Math.max(1, backDistance)),
                    "back", backDistance);
        }
        if (frontDistance < MIN_RADIUS) {
            return missingBoundary(
                    origin.offset(front, Math.max(1, frontDistance)),
                    "front", frontDistance);
        }
        if (heightDistance < MIN_DEPTH) {
            return DimensionScanResult.failure(
                    origin.down(Math.max(1, heightDistance)),
                    "brick floor at least " + MIN_DEPTH
                            + " blocks below the controller",
                    "detected depth " + heightDistance);
        }
        return DimensionScanResult.success(new CharcoalDimensions(
                leftDistance, rightDistance,
                backDistance, frontDistance, heightDistance));
    }

    private static int findWallBoundary(
            @NotNull World world,
            @NotNull BlockPos origin,
            @NotNull EnumFacing direction) {
        for (int distance = 1; distance <= MAX_REPEAT + 1; distance++) {
            if (WALL_BLOCKS.contains(world.getBlockState(
                    origin.offset(direction, distance)).getBlock())) {
                return distance;
            }
        }
        return 0;
    }

    private static int findFloorBoundary(
            @NotNull World world,
            @NotNull BlockPos origin) {
        for (int distance = 1; distance <= MAX_LOG_HEIGHT + 1; distance++) {
            if (world.getBlockState(origin.down(distance)).getBlock()
                    == Blocks.BRICK_BLOCK) {
                return distance;
            }
        }
        return 0;
    }

    @NotNull
    private static DimensionScanResult missingBoundary(
            @NotNull BlockPos pos,
            @NotNull String side,
            int distance) {
        return DimensionScanResult.failure(
                pos,
                "charcoal pile wall " + side + " boundary",
                distance == 0
                        ? "no boundary detected"
                        : "detected distance " + distance);
    }

    private void applyStructureDimensions(
            @NotNull CharcoalDimensions dimensions) {
        boolean changed = lDist != dimensions.left
                || rDist != dimensions.right
                || hDist != dimensions.height;
        lDist = dimensions.left;
        rDist = dimensions.right;
        hDist = dimensions.height;
        if (!changed || getWorld() == null || getWorld().isRemote) {
            return;
        }
        writeCustomData(GregtechDataCodes.UPDATE_STRUCTURE_SIZE, buf -> {
            buf.writeInt(this.lDist);
            buf.writeInt(this.rDist);
            buf.writeInt(this.hDist);
        });
    }

    private static boolean detectRuntimeStructure(
            @NotNull StructureRuntimeDetectionContext<
                    MetaTileEntityCharcoalPileIgniter> context) {
        MetaTileEntityCharcoalPileIgniter controller =
                context.getController();
        DimensionScanResult scan = controller.scanStructureDimensions();
        if (!scan.isSuccess()) {
            return context.fail(
                    scan.failurePos, scan.expected, scan.actual);
        }

        CharcoalDimensions dimensions = scan.dimensions;
        context.emit(CHARCOAL_DIMENSIONS_KEY, dimensions);
        context.emit(CHARCOAL_WIDTH_KEY, dimensions.getLogWidth());
        context.emit(CHARCOAL_HEIGHT_KEY, dimensions.getLogHeight());
        context.emit(CHARCOAL_LENGTH_KEY, dimensions.getLogLength());

        RuntimeCellElements elements = createRuntimeCellElements();

        BlockPos origin = context.getControllerPos();
        EnumFacing front = controller.getFrontFacing();
        EnumFacing right = front.rotateY();
        for (int depth = 0; depth <= dimensions.height; depth++) {
            for (int forward = -dimensions.back;
                 forward <= dimensions.front;
                 forward++) {
                for (int lateral = -dimensions.left;
                     lateral <= dimensions.right;
                     lateral++) {
                    CharcoalCellType type = classifyCell(
                            lateral, forward, depth, dimensions);
                    BlockPos pos = offset(
                            origin, right, lateral, front, forward)
                            .down(depth);
                    if (!context.match(pos, elements.get(type))) {
                        return context.fail(
                                pos, type.expected,
                                String.valueOf(
                                        context.getWorld().getBlockState(pos)));
                    }
                }
            }
        }
        return true;
    }

    @NotNull
    private static BlockPos offset(
            @NotNull BlockPos origin,
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
    private static CharcoalCellType classifyCell(
            int lateral,
            int forward,
            int depth,
            @NotNull CharcoalDimensions dimensions) {
        boolean lateralBoundary =
                lateral == -dimensions.left
                        || lateral == dimensions.right;
        boolean forwardBoundary =
                forward == -dimensions.back
                        || forward == dimensions.front;

        if (depth == 0) {
            if (lateral == 0 && forward == 0) {
                return CharcoalCellType.CONTROLLER;
            }
            return lateralBoundary || forwardBoundary
                    ? CharcoalCellType.ANY
                    : CharcoalCellType.WALL;
        }
        if (depth == dimensions.height) {
            return lateralBoundary || forwardBoundary
                    ? CharcoalCellType.ANY
                    : CharcoalCellType.BRICK;
        }
        if (lateralBoundary || forwardBoundary) {
            return lateralBoundary && forwardBoundary
                    ? CharcoalCellType.ANY
                    : CharcoalCellType.WALL;
        }
        return CharcoalCellType.LOG;
    }

    @NotNull
    private static RuntimeCellElements createRuntimeCellElements() {
        return new RuntimeCellElements(
                gregtech.api.pattern.element.Elements.self(
                        MetaTileEntityCharcoalPileIgniter.class),
                CHARCOAL_WALL_ELEMENT,
                gregtech.api.pattern.element.Elements.block(
                        Blocks.BRICK_BLOCK.getDefaultState()),
                CHARCOAL_LOG_ELEMENT,
                gregtech.api.pattern.element.Elements.any());
    }

    private void setActive(boolean active) {
        this.isActive = active;
        writeCustomData(GregtechDataCodes.WORKABLE_ACTIVE, buf -> buf.writeBoolean(this.isActive));
    }

    private void updateMaxProgressTime() {
        this.maxProgress = Math.max(1, (int) Math.sqrt(logPositions.size() * 240_000));
    }

    @Override
    public void update() {
        super.update();
        if (getWorld() != null) {
            if (isActive) {
                BlockPos pos = getPos();
                EnumFacing facing = EnumFacing.UP;
                float xPos = facing.getXOffset() * 0.76F + pos.getX() + 0.5F;
                float yPos = facing.getYOffset() * 0.76F + pos.getY() + 0.25F;
                float zPos = facing.getZOffset() * 0.76F + pos.getZ() + 0.5F;
                float ySpd = facing.getYOffset() * 0.1F + 0.2F + 0.1F * GTValues.RNG.nextFloat();

                getWorld().spawnParticle(EnumParticleTypes.SMOKE_NORMAL, xPos, yPos, zPos, 0, ySpd, 0);
            }
        }
    }

    @Override
    protected void updateFormedValid() {
        if (isActive && maxProgress > 0) {
            if (++progressTime == maxProgress) {
                progressTime = 0;
                maxProgress = 0;
                convertLogBlocks();
                setActive(false);
            }
        }
    }

    private void convertLogBlocks() {
        World world = getWorld();
        for (BlockPos pos : logPositions) {
            world.setBlockState(pos, MetaBlocks.BRITTLE_CHARCOAL.getDefaultState());
        }
        logPositions.clear();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.BRONZE_PLATED_BRICKS;
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.charcoal_pile.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.charcoal_pile.tooltip.2"));
        if (TooltipHelper.isCtrlDown()) {
            tooltip.add(I18n.format("gregtech.machine.charcoal_pile.tooltip.3"));
            tooltip.add(I18n.format("gregtech.machine.charcoal_pile.tooltip.4"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.hold_ctrl"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("lDist", this.lDist);
        data.setInteger("rDist", this.rDist);
        data.setInteger("hDist", this.hDist);
        data.setInteger("progressTime", this.progressTime);
        data.setInteger("maxProgress", this.maxProgress);
        data.setBoolean("isActive", this.isActive);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.lDist = data.hasKey("lDist") ? data.getInteger("lDist") : this.lDist;
        this.rDist = data.hasKey("rDist") ? data.getInteger("rDist") : this.rDist;
        this.hDist = data.hasKey("hDist") ? data.getInteger("hDist") : this.hDist;
        this.progressTime = data.getInteger("progressTime");
        this.maxProgress = data.getInteger("maxProgress");
        this.isActive = data.getBoolean("isActive");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.lDist);
        buf.writeInt(this.rDist);
        buf.writeInt(this.hDist);
        buf.writeInt(this.progressTime);
        buf.writeInt(this.maxProgress);
        buf.writeBoolean(this.isActive);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.lDist = buf.readInt();
        this.rDist = buf.readInt();
        this.hDist = buf.readInt();
        this.progressTime = buf.readInt();
        this.maxProgress = buf.readInt();
        this.isActive = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_STRUCTURE_SIZE) {
            this.lDist = buf.readInt();
            this.rDist = buf.readInt();
            this.hDist = buf.readInt();
        } else if (dataId == GregtechDataCodes.WORKABLE_ACTIVE) {
            this.isActive = buf.readBoolean();
            scheduleRenderUpdate();
        }
    }

    /**
     * Add a block to the valid Charcoal Pile valid wall/roof blocks
     *
     * @param block the block to add
     */
    @SuppressWarnings("unused")
    public static void addWallBlock(@NotNull Block block) {
        WALL_BLOCKS.add(block);
    }

    @ZenMethod("addWallBlock")
    @Optional.Method(modid = Mods.Names.CRAFT_TWEAKER)
    @SuppressWarnings("unused")
    public static void addWallBlockCT(@NotNull IBlock block) {
        WALL_BLOCKS.add(CraftTweakerMC.getBlock(block));
    }

    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    @Override
    public void setWorkingEnabled(boolean isActivationAllowed) {}

    @Override
    public int getProgress() {
        return progressTime;
    }

    @Override
    public int getMaxProgress() {
        return maxProgress;
    }

    @Override
    public boolean isActive() {
        return this.isActive;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_WORKABLE) {
            return GregtechTileCapabilities.CAPABILITY_WORKABLE.cast(this);
        }

        return super.getCapability(capability, side);
    }

    @SubscribeEvent
    public static void onItemUse(@NotNull PlayerInteractEvent.RightClickBlock event) {
        TileEntity tileEntity = event.getWorld().getTileEntity(event.getPos());
        MetaTileEntity mte = null;
        if (tileEntity instanceof IGregTechTileEntity) {
            mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        }
        if (mte instanceof MetaTileEntityCharcoalPileIgniter && ((IMultiblockController) mte).isStructureFormed()) {
            if (event.getSide().isClient()) {
                event.setCanceled(true);
                event.getEntityPlayer().swingArm(EnumHand.MAIN_HAND);
            } else if (!mte.isActive()) {
                boolean shouldActivate = false;
                ItemStack stack = event.getItemStack();
                if (stack.getItem() instanceof ItemFlintAndSteel) {
                    // flint and steel
                    stack.damageItem(1, event.getEntityPlayer());

                    // flint and steel sound does not get played when handled like this
                    event.getWorld().playSound(null, event.getPos(), SoundEvents.ITEM_FLINTANDSTEEL_USE,
                            SoundCategory.PLAYERS, 1.0F, 1.0F);

                    shouldActivate = true;
                } else if (stack.getItem() instanceof ItemFireball) {
                    // fire charge
                    stack.shrink(1);

                    // fire charge sound does not get played when handled like this
                    event.getWorld().playSound(null, event.getPos(), SoundEvents.ITEM_FIRECHARGE_USE,
                            SoundCategory.PLAYERS, 1.0F, 1.0F);

                    shouldActivate = true;
                } else if (stack.getItem() instanceof MetaItem) {
                    // lighters
                    MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) stack.getItem()).getItem(stack);
                    if (valueItem != null) {
                        for (IItemBehaviour behaviour : valueItem.getBehaviours()) {
                            if (behaviour instanceof LighterBehaviour &&
                                    ((LighterBehaviour) behaviour).consumeFuel(event.getEntityPlayer(), stack)) {
                                // lighter sound does not get played when handled like this
                                event.getWorld().playSound(null, event.getPos(), SoundEvents.ITEM_FLINTANDSTEEL_USE,
                                        SoundCategory.PLAYERS, 1.0F, 1.0F);

                                shouldActivate = true;
                                break;
                            }
                        }
                    }
                }

                if (shouldActivate) {
                    ((MetaTileEntityCharcoalPileIgniter) mte).setActive(true);
                    event.setCancellationResult(EnumActionResult.FAIL);
                    event.setCanceled(true);
                }
            }
        }
    }

    @Override
    public boolean hasFrontFacing() {
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

    private enum CharcoalCellType {

        CONTROLLER("charcoal pile controller"),
        WALL("configured charcoal pile wall block"),
        BRICK("brick floor"),
        LOG("wood log"),
        ANY("unrestricted edge cell");

        @NotNull
        private final String expected;

        CharcoalCellType(@NotNull String expected) {
            this.expected = expected;
        }
    }

    private static final class RuntimeCellElements {

        @NotNull
        private final IStructureElement<?> controller;
        @NotNull
        private final IStructureElement<?> wall;
        @NotNull
        private final IStructureElement<?> brick;
        @NotNull
        private final IStructureElement<?> log;
        @NotNull
        private final IStructureElement<?> any;

        private RuntimeCellElements(
                @NotNull IStructureElement<?> controller,
                @NotNull IStructureElement<?> wall,
                @NotNull IStructureElement<?> brick,
                @NotNull IStructureElement<?> log,
                @NotNull IStructureElement<?> any) {
            this.controller = controller.compile();
            this.wall = wall.compile();
            this.brick = brick.compile();
            this.log = log.compile();
            this.any = any.compile();
        }

        @NotNull
        private IStructureElement<?> get(@NotNull CharcoalCellType type) {
            switch (type) {
                case CONTROLLER:
                    return controller;
                case WALL:
                    return wall;
                case BRICK:
                    return brick;
                case LOG:
                    return log;
                case ANY:
                    return any;
                default:
                    throw new IllegalStateException(
                            "Unhandled charcoal pile cell type " + type);
            }
        }
    }

    private static final class CharcoalDimensions {

        private final int left;
        private final int right;
        private final int back;
        private final int front;
        private final int height;

        private CharcoalDimensions(
                int left,
                int right,
                int back,
                int front,
                int height) {
            this.left = left;
            this.right = right;
            this.back = back;
            this.front = front;
            this.height = height;
        }

        @NotNull
        private static CharcoalDimensions fromLogSize(int width, int height, int length) {
            int leftLogs = (width - 1) / 2;
            int rightLogs = width - 1 - leftLogs;
            int backLogs = (length - 1) / 2;
            int frontLogs = length - 1 - backLogs;
            return new CharcoalDimensions(
                    leftLogs + 1,
                    rightLogs + 1,
                    backLogs + 1,
                    frontLogs + 1,
                    height + 1);
        }

        private int getLogWidth() {
            return left + right - 1;
        }

        private int getLogHeight() {
            return height - 1;
        }

        private int getLogLength() {
            return back + front - 1;
        }

        private int getStructureWidth() {
            return left + right + 1;
        }

        private int getStructureHeight() {
            return height + 1;
        }

        private int getStructureLength() {
            return back + front + 1;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CharcoalDimensions)) return false;
            CharcoalDimensions that = (CharcoalDimensions) object;
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
    }

    private static final class DimensionScanResult {

        @Nullable
        private final CharcoalDimensions dimensions;
        @NotNull
        private final BlockPos failurePos;
        @NotNull
        private final String expected;
        @NotNull
        private final String actual;

        private DimensionScanResult(
                @Nullable CharcoalDimensions dimensions,
                @NotNull BlockPos failurePos,
                @NotNull String expected,
                @NotNull String actual) {
            this.dimensions = dimensions;
            this.failurePos = failurePos.toImmutable();
            this.expected = expected;
            this.actual = actual;
        }

        @NotNull
        private static DimensionScanResult success(
                @NotNull CharcoalDimensions dimensions) {
            return new DimensionScanResult(
                    dimensions, BlockPos.ORIGIN,
                    "detected charcoal pile bounds", "matched");
        }

        @NotNull
        private static DimensionScanResult failure(
                @NotNull BlockPos pos,
                @NotNull String expected,
                @NotNull String actual) {
            return new DimensionScanResult(
                    null, pos, expected, actual);
        }

        private boolean isSuccess() {
            return dimensions != null;
        }
    }

    private static final class CharcoalLogElement
            implements ITypedStructureElement<Object> {

        private final StructureElementPreview preview =
                StructureElementPreview.of(this::getCandidates);

        @Override
        public boolean check(
                @NotNull StructureEvaluationContext<Object> context) {
            if (!context.getBlockState().getBlock().isWood(
                    context.getWorld(), context.getPos())) {
                return false;
            }
            context.getCollector().emit(
                    CHARCOAL_LOG_POSITIONS_KEY, context.getPos());
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] {
                    new BlockInfo(Blocks.LOG.getDefaultState())
            };
        }

        @Override
        public boolean placeBlock(
                @NotNull StructureEvaluationContext<Object> context,
                EntityPlayer player) {
            World world = context.getWorld();
            return world != null && world.setBlockState(
                    context.getPos(), Blocks.LOG.getDefaultState());
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

    private static final class CharcoalWallElement
            implements ITypedStructureElement<Object> {

        private final StructureElementPreview preview =
                StructureElementPreview.of(this::getCandidates);

        @Override
        public boolean check(
                @NotNull StructureEvaluationContext<Object> context) {
            return WALL_BLOCKS.contains(context.getBlockState().getBlock());
        }

        @Override
        public BlockInfo[] getCandidates() {
            return WALL_BLOCKS.stream()
                    .map(Block::getDefaultState)
                    .map(BlockInfo::new)
                    .toArray(BlockInfo[]::new);
        }

        @Override
        public boolean placeBlock(
                @NotNull StructureEvaluationContext<Object> context,
                EntityPlayer player) {
            BlockInfo[] candidates = getCandidates();
            if (candidates.length == 0) {
                return false;
            }
            World world = context.getWorld();
            return world != null && world.setBlockState(context.getPos(), candidates[0].getBlockState());
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
}
