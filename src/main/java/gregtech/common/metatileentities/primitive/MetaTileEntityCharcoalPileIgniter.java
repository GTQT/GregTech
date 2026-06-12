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
import gregtech.api.pattern.*;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.Mods;
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
import net.minecraft.util.*;
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
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        // calculate the duration upon formation
        updateMaxProgressTime();
    }

    @NotNull
    @Override
    // Retained on FactoryBlockPattern: structure is dynamically generated from variable dimensions.
    // Aisle strings are built at runtime based on detected pile boundaries.
    protected BlockPattern createStructurePattern() {
        // update the structure's dimensions just before we create it
        // return the default structure, even if there is no valid size found
        // this means auto-build will still work, and prevents terminal crashes.
        if (getWorld() != null) updateStructureDimensions();

        // these can sometimes get set to 0 when loading the game, breaking JEI
        if (lDist < 1) lDist = MIN_RADIUS;
        if (rDist < 1) rDist = MIN_RADIUS;
        if (hDist < 2) hDist = MIN_DEPTH;

        // swap the left and right distances if the front facing is east or west
        // i guess allows BlockPattern checkPatternAt to get the correct relative position, somehow.
        if (this.frontFacing == EnumFacing.EAST || this.frontFacing == EnumFacing.WEST) {
            int tmp = lDist;
            lDist = rDist;
            rDist = tmp;
        }

        return buildStructurePattern(lDist, rDist, hDist, 0, MAX_REPEAT, 0, MAX_REPEAT);
    }

    @NotNull
    private BlockPattern buildStructurePattern(int leftDistance, int rightDistance, int heightDistance,
                                               int backRepeatMin, int backRepeatMax,
                                               int frontRepeatMin, int frontRepeatMax) {
        StringBuilder wallBuilder = new StringBuilder();       // " XXX "
        StringBuilder floorBuilder = new StringBuilder();      // " BBB "
        StringBuilder cornerBuilder = new StringBuilder();     // " "
        StringBuilder ctrlBuilder = new StringBuilder();       // " XSX "
        StringBuilder woodBuilder = new StringBuilder();       // "XCCCX"

        // everything to the left of the controller
        wallBuilder.append(" ");
        floorBuilder.append(" ");
        ctrlBuilder.append(" ");
        woodBuilder.append("X");

        for (int i = 0; i < leftDistance; i++) {
            cornerBuilder.append(" ");
            if (i > 0) {
                wallBuilder.append("X");
                floorBuilder.append("B");
                ctrlBuilder.append("X");
                woodBuilder.append("C");
            }
        }

        // everything in-line with the controller
        wallBuilder.append("X");
        floorBuilder.append("B");
        cornerBuilder.append(" ");
        ctrlBuilder.append("S");
        woodBuilder.append("C");

        // everything to the right of the controller
        for (int i = 0; i < rightDistance; i++) {
            cornerBuilder.append(" ");
            if (i < rightDistance - 1) {
                wallBuilder.append("X");
                floorBuilder.append("B");
                ctrlBuilder.append("X");
                woodBuilder.append("C");
            }
        }

        wallBuilder.append(" ");
        floorBuilder.append(" ");
        ctrlBuilder.append(" ");
        woodBuilder.append("X");

        String[] wall = new String[heightDistance + 1]; // " ", " XXX ", " "
        Arrays.fill(wall, wallBuilder.toString());
        wall[0] = cornerBuilder.toString();
        wall[wall.length - 1] = cornerBuilder.toString();

        String[] slice = new String[heightDistance + 1]; // " BBB ", "XCCCX", " XXX "
        Arrays.fill(slice, woodBuilder.toString());
        slice[0] = floorBuilder.toString();

        String[] center = Arrays.copyOf(slice, slice.length); // " BBB ", "XCCCX", " XSX "
        // inverse the center slice if facing east or west.
        if (this.frontFacing == EnumFacing.EAST || this.frontFacing == EnumFacing.WEST) {
            center[center.length - 1] = ctrlBuilder.reverse().toString();
        } else {
            center[center.length - 1] = ctrlBuilder.toString();
        }

        // slice is finished after center, so we can re-use it a bit more
        slice[slice.length - 1] = wallBuilder.toString();

        return FactoryBlockPattern.start()
                .aisle(wall)
                .aisle(slice).setRepeatable(backRepeatMin, backRepeatMax)
                .aisle(center)
                .aisle(slice).setRepeatable(frontRepeatMin, frontRepeatMax)
                .aisle(wall)
                .where('S', selfPredicate())
                .where('B', blocks(Blocks.BRICK_BLOCK))
                .where('X', blocks(WALL_BLOCKS.toArray(new Block[0])))
                .where('C', logPredicate())
                .where(' ', any())
                .build();
    }

    @NotNull
    private BlockPattern buildStructurePatternForLogSize(int width, int height, int length) {
        int leftLogs = (width - 1) / 2;
        int rightLogs = width - 1 - leftLogs;
        int backRepeats = (length - 1) / 2;
        int frontRepeats = length - 1 - backRepeats;

        return buildStructurePattern(leftLogs + 1, rightLogs + 1, height + 1,
                backRepeats, backRepeats, frontRepeats, frontRepeats);
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
        BlockPattern pattern = buildStructurePatternForChannelValues(channelValues);
        return Collections.singletonList(new MultiblockShapeInfo(
                pattern.getPreview(getFixedRepetitions(pattern), Collections.emptyMap())));
    }

    @Override
    public boolean autoBuildStructure(@NotNull StructureOperationRequest request) {
        BlockPattern pattern = buildStructurePatternForChannelValues(request.getChannelValues());
        return autoBuildDynamicStructure(request.withChannelValues(Collections.emptyMap()),
                "charcoal_pile_dynamic", pattern.getTemplate());
    }

    @Override
    @Deprecated
    public boolean autoBuildStructure(@NotNull EntityPlayer player,
                                      @Nullable Map<String, Integer> channelValues,
                                      boolean skipHatches) {
        return autoBuildStructure(StructureOperationRequest.build(
                player, this, StructureOrientation.fromController(this),
                channelValues, skipHatches, ItemStack.EMPTY));
    }

    @NotNull
    private BlockPattern buildStructurePatternForChannelValues(@Nullable Map<String, Integer> channelValues) {
        int width = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_WIDTH.getName(),
                MIN_LOG_WIDTH, MAX_LOG_WIDTH);
        int height = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_HEIGHT.getName(),
                MIN_LOG_HEIGHT, MAX_LOG_HEIGHT);
        int length = resolveChannelSize(channelValues, GTStructureChannels.STRUCTURE_LENGTH.getName(),
                MIN_LOG_LENGTH, MAX_LOG_LENGTH);
        return buildStructurePatternForLogSize(width, height, length);
    }

    private static int resolveChannelSize(@Nullable Map<String, Integer> channelValues,
                                          @NotNull String channelName,
                                          int min, int max) {
        if (channelValues == null) return max;
        Integer value = channelValues.get(channelName);
        return value == null ? max : MultiblockState.resolveRepetitionValue(value, min, max);
    }

    @NotNull
    private static int[] getFixedRepetitions(@NotNull BlockPattern pattern) {
        int[][] ranges = pattern.getAisleRepetitions();
        int[] repetitions = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            repetitions[i] = ranges[i][0];
        }
        return repetitions;
    }

    @NotNull
    private TraceabilityPredicate logPredicate() {
        return new TraceabilityPredicate(blockWorldState -> {
            if (blockWorldState.getBlockState().getBlock().isWood(blockWorldState.getWorld(),
                    blockWorldState.getPos())) {
                // store the position of every log, so we can easily turn them into charcoal
                logPositions.add(blockWorldState.getPos());
                return true;
            }
            return false;
        }, () -> new BlockInfo[] { new BlockInfo(Blocks.LOG.getDefaultState()) });
    }

    private boolean updateStructureDimensions() {
        World world = getWorld();
        EnumFacing left = getFrontFacing().getOpposite().rotateYCCW();
        EnumFacing right = left.getOpposite();

        // l, r move down 1 block because the top layer has no bricks
        BlockPos.MutableBlockPos lPos = new BlockPos.MutableBlockPos(getPos()).move(EnumFacing.DOWN);
        BlockPos.MutableBlockPos rPos = new BlockPos.MutableBlockPos(getPos()).move(EnumFacing.DOWN);
        BlockPos.MutableBlockPos hPos = new BlockPos.MutableBlockPos(getPos());

        // find the distances from the controller to the brick blocks on one horizontal axis and the Y axis
        // repeatable aisles take care of the second horizontal axis
        int lDist = 0;
        int rDist = 0;
        int hDist = 0;

        // find the left, right, height distances for the structure pattern
        // maximum size is 11x11x6 including walls, so check 5 block radius around the controller for blocks
        for (int i = 1; i < 6; i++) {
            if (lDist != 0 && rDist != 0 && hDist != 0) break;
            if (lDist == 0 && isBlockWall(world, lPos, left)) lDist = i;
            if (rDist == 0 && isBlockWall(world, rPos, right)) rDist = i;
            if (hDist == 0 && isBlockFloor(world, hPos)) hDist = i;
        }

        if (lDist < MIN_RADIUS || rDist < MIN_RADIUS || hDist < MIN_DEPTH) {
            invalidateStructure();
            return false;
        }

        this.lDist = lDist;
        this.rDist = rDist;
        this.hDist = hDist;

        writeCustomData(GregtechDataCodes.UPDATE_STRUCTURE_SIZE, buf -> {
            buf.writeInt(this.lDist);
            buf.writeInt(this.rDist);
            buf.writeInt(this.hDist);
        });
        return true;
    }

    private static boolean isBlockWall(@NotNull World world, @NotNull BlockPos.MutableBlockPos pos,
                                       @NotNull EnumFacing direction) {
        return WALL_BLOCKS.contains(world.getBlockState(pos.move(direction)).getBlock());
    }

    private static boolean isBlockFloor(@NotNull World world, @NotNull BlockPos.MutableBlockPos pos) {
        return world.getBlockState(pos.move(EnumFacing.DOWN)).getBlock() == Blocks.BRICK_BLOCK;
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
            if (!getWorld().isRemote && !this.isStructureFormed() && getOffsetTimer() % 20 == 0) {
                this.reinitializeStructurePattern();
            } else if (isActive) {
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
}
