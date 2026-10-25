package gregtech.integration.jei.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MBPattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GregFakePlayer;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.client.renderer.handler.MetaTileEntityRenderer;
import gregtech.client.renderer.scene.PreviewBlockAccess;
import gregtech.client.renderer.scene.StructurePreviewRenderer;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.BlockRenderer;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Translation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.recipes.RecipeLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class MultiblockInfoRecipeWrapper implements IRecipeWrapper {

    private static final int SLOT_SIZE = 18;
    private int initializedPartsCount;
    private static final int ICON_SIZE = 20;
    private static final int RIGHT_PADDING = 5;
    /** Cursor movement, in GUI pixels, a drag has to cover before it starts moving the camera. */
    private static final int CAMERA_DRAG_THRESHOLD = 2;
    /** Vertical field of view of the preview camera, matching the renderer's projection. */
    private static final double CAMERA_FOV_DEGREES = 60.0D;
    private static final int INFO_ICON_Y = CompactPreviewLayout.CONTROLS_Y + 3;
    private static final int CHANNEL_TOP = CompactPreviewLayout.CONTROLS_Y + 4;
    private static final int CHANNEL_ROWS = CompactPreviewLayout.CHANNEL_ROWS;
    private int channelPage;
    private final ChannelSliderDrag channelDrag = new ChannelSliderDrag();
    private long lastClickDiagnostic;
    private static final int LAYER_BUTTON_Y = INFO_ICON_Y;
    private static final int CANDIDATE_SLOT_START_Y = 20;
    // Right candidates panel layout constants
    private static final int CANDIDATES_COLUMNS = 1;
    private static final int CANDIDATES_PER_COL = 6;
    private static final int MAX_CANDIDATES = CANDIDATES_COLUMNS * CANDIDATES_PER_COL;
    private static final int CANDIDATE_SLOT_BASE = -MAX_CANDIDATES;
    // Candidate cycling interval in milliseconds
    private static final long CANDIDATE_CYCLE_INTERVAL_MS = 1000L;
    // Hard cap for the offscreen preview buffer. The buffer follows the displayed size (see
    // matchBufferAspect); this only stops very high GUI scales from rendering millions of unnecessary pixels.
    private static final int PREVIEW_MAX_RESOLUTION = 1024;
    private static final long PREVIEW_LOADING_FRAME_BUDGET_NANOS = 6_000_000L;
    private static final int PREVIEW_ASSEMBLY_BATCH_SIZE = 512;
    private static final int PREVIEW_WORLD_BATCH_SIZE = 256;
    private static final int PREVIEW_PARTS_BATCH_SIZE = 64;
    private static final int PREVIEW_VBO_BATCH_SIZE = 128;
    private static final Set<String> MISSING_TYPED_PREVIEW_DIAGNOSTICS = new HashSet<>();
    private static final Set<String> TYPED_PREVIEW_ENTRY_DIAGNOSTICS = new HashSet<>();
    private static ItemStack tooltipBlockStack;
    private static long lastRender;
    private static MultiblockInfoRecipeWrapper lastWrapper;
    private final MultiblockControllerBase controller;
    private final Map<GuiButton, Runnable> buttons = new HashMap<>();
    private final List<ItemStack> ingredientInputs = new ArrayList<>();
    private final GuiButton nextLayerButton;
    private final List<PreviewCandidate> previewCandidates;
    private final List<PreviewCandidate> cachedCandidateGroups = new ArrayList<>();
    private final List<BlockInfo> cachedCandidateBlocks = new ArrayList<>();
    private final Map<String, Integer> channelValues = new HashMap<>();
    private List<StructureChannel> supportedChannels = Collections.emptyList();
    private int[][] channelRanges = new int[0][]; // [channelIdx][0=min, 1=max]
    /** Height of the compact channel strip, following the channel count instead of always reserving four rows. */
    private int compactControlsHeight = CompactPreviewLayout.CONTROLS_HEIGHT;
    @Nullable
    private MBPattern[] patterns;
    private StructurePreviewRenderer previewRenderer;
    @Nullable
    private PreviewLoadTask previewLoadTask;
    @Nullable
    private String previewLoadFailure;
    private boolean previewMetadataInitialized;
    private boolean ingredientInputsInitialized;
    private boolean previewLayoutInitialized;
    private boolean rendererContainsFullStructure;
    private boolean resetViewWhenPreviewReady = true;
    /** True once a default camera framing has been applied to the currently loaded preview. */
    private boolean previewFrameApplied;
    private int pendingMouseWheel;
    private RecipeLayout recipeLayout;
    private int layerIndex = -1;
    private Vector3f center;
    private float rotationYaw;
    private float rotationPitch;
    private float zoom;
    // Camera drag state. Both preview views feed this instead of tracking their own anchor positions.
    @Nullable
    private CameraDragMode cameraDragMode;
    private int cameraDragAnchorX;
    private int cameraDragAnchorY;
    private int cameraDragLastX;
    private int cameraDragLastY;
    private boolean cameraDragStarted;
    private IDrawable slot;
    private IDrawable infoIcon;
    private boolean drawInfoIcon;
    private List<String> previewTips;
    private BlockPos selected;
    // Candidate cycling state for 3D in-place rendering
    private int candidateCycleIndex = 0;
    private long lastCandidateCycleTime = 0L;

    public MultiblockInfoRecipeWrapper(@NotNull MultiblockControllerBase controller) {
        this.controller = controller;
        this.nextLayerButton = new GuiButton(0, 176 - (2 * ICON_SIZE + RIGHT_PADDING + 2), LAYER_BUTTON_Y, ICON_SIZE,
                ICON_SIZE, "");
        this.buttons.put(nextLayerButton, this::toggleNextLayer);
        this.previewCandidates = new ArrayList<>();
        GuiButton channelPageButton = new GuiButton(1, 176 - (3 * ICON_SIZE + RIGHT_PADDING + 4), INFO_ICON_Y, 20, 20, ">>");
        this.buttons.put(channelPageButton, () -> {
            int pages = Math.max(1, (supportedChannels.size() + CHANNEL_ROWS - 1) / CHANNEL_ROWS);
            channelPage = (channelPage + 1) % pages;
        });
    }

    /**
     * JEI creates every wrapper while registering recipes. Keep that phase free of preview allocation; once a recipe
     * page is opened, the actual preview is built in bounded client-thread batches so the UI can show progress.
     */
    private void ensurePreviewLoadStarted() {
        if (hasLivePreview() || previewLoadTask != null || previewLoadFailure != null) {
            return;
        }
        startPreviewLoading();
    }

    private boolean hasLivePreview() {
        return patterns != null && patterns.length > 0 && !patterns[0].isDisposed();
    }

    private void initializePreviewMetadata() {
        if (previewMetadataInitialized) return;

        this.supportedChannels = controller.getSupportedChannels();
        this.channelRanges = new int[supportedChannels.size()][];
        for (int i = 0; i < supportedChannels.size(); i++) {
            channelRanges[i] = controller.getChannelRange(supportedChannels.get(i));
        }
        this.compactControlsHeight = CompactPreviewLayout.controlsHeight(supportedChannels.size());
        this.previewMetadataInitialized = true;
    }

    private void startPreviewLoading() {
        long preparationStart = System.nanoTime();
        releasePreviewPatterns();
        cancelPreviewLoading();
        // A rebuild interrupts camera interaction, so the next drag starts from a fresh anchor instead of
        // applying the cursor movement of the whole loading phase at once.
        resetCameraDrag();
        previewLoadFailure = null;
        previewLayoutInitialized = false;
        rendererContainsFullStructure = false;
        clearParts();
        initializePreviewMetadata();
        previewFrameApplied = false;
        previewLoadTask = new PreviewLoadTask(controller.beginIncrementalMultiPiecePreview(channelValues));
        GTLog.logger.debug("[JEIMultiblockPreview] started incremental loading controller={} channels={} preparationMs={}",
                controller.metaTileEntityId, new TreeMap<>(channelValues),
                (System.nanoTime() - preparationStart) / 1_000_000L);
    }

    private void advancePreviewLoading() {
        PreviewLoadTask task = previewLoadTask;
        if (task == null) {
            return;
        }
        try {
            task.advance(System.nanoTime() + PREVIEW_LOADING_FRAME_BUDGET_NANOS);
        } catch (RuntimeException e) {
            failPreviewLoading(task, e);
            return;
        }
        if (!task.isComplete()) {
            return;
        }

        MBPattern loaded = task.takePattern();
        this.patterns = new MBPattern[] { loaded };
        this.previewRenderer = task.renderer;
        this.rendererContainsFullStructure = true;
        GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);
        previewLoadTask = null;
        GTLog.logger.debug("[JEIMultiblockPreview] incrementally initialized controller={} channels={} blocks={} ms={}",
                controller.metaTileEntityId, new TreeMap<>(channelValues), task.getBlockCount(),
                task.getElapsedMillis());
    }

    /**
     * Abandons a broken preview build. The loading indicator has to leave its progress state, otherwise a
     * failed rebuild (for example after a channel change) would keep reporting progress forever.
     */
    private void failPreviewLoading(@Nullable PreviewLoadTask task, @NotNull Throwable error) {
        if (task != null) {
            task.dispose();
        }
        previewLoadTask = null;
        previewLoadFailure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        GTLog.logger.error("[JEIMultiblockPreview] failed to incrementally initialize controller={} channels={}",
                controller.metaTileEntityId, new TreeMap<>(channelValues), error);
    }

    /**
     * Drives the pending preview build by one bounded client-thread step. The compact recipe page calls this
     * from {@link #drawInfo(Minecraft, int, int, int, int)}; the fullscreen viewer, which suppresses that
     * call, has to drive it itself or a rebuild started from a channel change would never finish.
     */
    public void advancePreviewLoadingStep() {
        advancePreviewLoading();
    }

    /**
     * Advances the in-place candidate rotation when its interval elapsed. The preview renderer caches its
     * output in an FBO, so a new candidate must also invalidate that cache or the scene would keep showing
     * the first alternative forever. Called by the compact recipe page and by the fullscreen viewer, so at
     * most one of them advances it per frame.
     */
    void advanceCandidateCycle() {
        if (previewCandidates.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCandidateCycleTime < CANDIDATE_CYCLE_INTERVAL_MS) {
            return;
        }
        lastCandidateCycleTime = now;
        candidateCycleIndex++;
    }

    private void releasePreviewResources() {
        channelDrag.cancel();
        resetCameraDrag();
        cachedCandidateGroups.clear();
        cachedCandidateBlocks.clear();
        selected = null;
        previewCandidates.clear();
        previewTips = null;
        candidateCycleIndex = 0;
        lastCandidateCycleTime = 0L;
        cancelPreviewLoading();
        previewLoadFailure = null;
        previewLayoutInitialized = false;
        rendererContainsFullStructure = false;
        previewFrameApplied = false;
        releasePreviewPatterns();
        if (lastWrapper == this) {
            lastWrapper = null;
        }
    }

    private void cancelPreviewLoading() {
        if (previewLoadTask != null) {
            previewLoadTask.dispose();
            previewLoadTask = null;
        }
    }

    private void releasePreviewPatterns() {
        MBPattern[] previous = this.patterns;
        this.patterns = null;
        this.previewRenderer = null;
        if (previous == null) return;

        GregTechAPI.removePatterns(controller.metaTileEntityId, previous);
        for (MBPattern pattern : previous) {
            if (pattern != null) {
                pattern.dispose();
            }
        }
        GTLog.logger.debug("[JEIMultiblockPreview] released renderer resources for controller={}",
                controller.metaTileEntityId);
    }

    private static void collectStructureBlock(@NotNull World world,
                                              @NotNull BlockPos pos,
                                              @NotNull BlockInfo blockInfo,
                                              @NotNull Map<ItemStack, PartInfo> partsMap) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        ItemStack stack = ItemStack.EMPTY;

        // first check if the block is a GT machine
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IGregTechTileEntity) {
            MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            // For parametric controllers, include variant NBT so the correct
            // variant item is shown in the JEI parts list
            stack = mte.getStackForm();
        }
        if (stack.isEmpty()) {
            // first, see what the block has to say for itself before forcing it to use a particular meta value
            stack = block.getPickBlock(state, new RayTraceResult(Vec3d.ZERO, EnumFacing.UP, pos), world, pos,
                    new GregFakePlayer(world));
        }
        if (stack.isEmpty()) {
            // try the default itemstack constructor if we're not a GT machine
            stack = GTUtility.toItem(state);
        }
        if (stack.isEmpty()) {
            // add the first of the block's drops if the others didn't work
            NonNullList<ItemStack> list = NonNullList.create();
            state.getBlock().getDrops(list, world, pos, state, 0);
            if (!list.isEmpty()) {
                ItemStack is = list.get(0);
                if (!is.isEmpty()) {
                    stack = is;
                }
            }
        }

        // if we got a stack, add it to the parts map
        if (!stack.isEmpty()) {
            PartInfo partInfo = partsMap.get(stack);
            if (partInfo == null) {
                partInfo = new PartInfo(stack, blockInfo);
                partsMap.put(stack, partInfo);
            }
            partInfo.amount++;
        }
    }

    @SideOnly(Side.CLIENT)
    private static void renderBlockOverLay(BlockPos pos, int r, int g, int b) {
        if (pos == null) return;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.translate((pos.getX() + 0.5), (pos.getY() + 0.5), (pos.getZ() + 0.5));
        GlStateManager.scale(1.01, 1.01, 1.01);

        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.disableTexture2D();
        CCRenderState renderState = CCRenderState.instance();
        renderState.startDrawing(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR, tessellator.getBuffer());
        ColourMultiplier multiplier = new ColourMultiplier(0);
        renderState.setPipeline(new Translation(-0.5, -0.5, -0.5), multiplier);
        BlockRenderer.BlockFace blockFace = new BlockRenderer.BlockFace();
        renderState.setModel(blockFace);
        for (EnumFacing renderSide : EnumFacing.VALUES) {
            multiplier.colour = RenderUtil.packColor(r, g, b, 255);
            blockFace.loadCuboidFace(Cuboid6.full, renderSide.getIndex());
            renderState.render();
        }
        renderState.draw();
        GlStateManager.scale(1 / 1.01, 1 / 1.01, 1 / 1.01);
        GlStateManager.translate(-(pos.getX() + 0.5), -(pos.getY() + 0.5), -(pos.getZ() + 0.5));
        GlStateManager.enableTexture2D();

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);
    }

    /**
     * Renders the current cycling candidate block at the selected position in the 3D scene.
     * This provides GT5-style in-place block cycling preview.
     */
    @SideOnly(Side.CLIENT)
    private void renderCandidateBlockAtPosition(World world, BlockPos pos) {
        advanceCandidateCycle();

        // Flatten alternatives only when the selection changes, not on every overlay frame.
        if (!cachedCandidateGroups.equals(previewCandidates)) {
            cachedCandidateGroups.clear();
            cachedCandidateGroups.addAll(previewCandidates);
            cachedCandidateBlocks.clear();
            for (PreviewCandidate candidate : previewCandidates) {
                for (BlockInfo info : candidate.getBlockCandidates()) {
                    if (info != null && info.getBlockState().getBlock() != net.minecraft.init.Blocks.AIR) {
                        cachedCandidateBlocks.add(info);
                    }
                }
            }
        }
        List<BlockInfo> allCandidateBlocks = cachedCandidateBlocks;
        if (allCandidateBlocks.isEmpty()) return;

        int index = candidateCycleIndex % allCandidateBlocks.size();
        BlockInfo candidateInfo = allCandidateBlocks.get(index);
        PreviewBlockAccess candidateAccess = new PreviewBlockAccess(world, pos, candidateInfo);
        IBlockState candidateState = candidateInfo.getBlockState().getActualState(candidateAccess, pos);

        // Render the candidate block at the selected position using immediate mode
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Minecraft mc = Minecraft.getMinecraft();
        mc.renderEngine.bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        net.minecraft.client.renderer.BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();

        net.minecraft.client.renderer.BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        BlockRenderLayer oldLayer = net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
        TileEntity candidateTile = candidateInfo.getTileEntity();
        World previousWorld = candidateTile == null ? null : candidateTile.getWorld();
        BlockPos previousPos = candidateTile == null ? null : candidateTile.getPos();
        if (candidateTile != null) {
            candidateTile.setWorld(world);
            candidateTile.setPos(pos);
        }
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                if (!candidateState.getBlock().canRenderInLayer(candidateState, layer)) continue;

                int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
                WorldSceneRenderer.setDefaultPassRenderState(pass);

                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                if (candidateState.getRenderType() == MetaTileEntityRenderer.BLOCK_RENDER_TYPE) {
                    MetaTileEntityRenderer.INSTANCE.renderBlock(candidateAccess, pos, candidateState, buffer);
                } else {
                    dispatcher.renderBlock(candidateState, pos, candidateAccess, buffer);
                }
                Tessellator.getInstance().draw();
            }
        } finally {
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(oldLayer);
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            if (candidateTile != null) {
                candidateTile.setWorld(previousWorld);
                candidateTile.setPos(previousPos);
            }
        }

        GlStateManager.disableBlend();
    }

    public static ItemStack getHoveredItemStack() {
        if (lastRender > System.currentTimeMillis() - 100) {
            return tooltipBlockStack;
        }
        return null;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        // JEI calls this while building its recipe index. Use the uncompiled
        // definition so structure parts stay searchable without constructing a
        // DummyWorld, compiled pattern or 3D renderer for every multiblock.
        initializeIngredientInputs();
        ingredients.setInputs(VanillaTypes.ITEM, ingredientInputs);
        ingredients.setOutput(VanillaTypes.ITEM, getControllerStack());
    }

    private void initializeIngredientInputs() {
        if (ingredientInputsInitialized) {
            return;
        }

        Set<ItemStack> inputs = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        controller.getStructureDefinitionForTooling().forEachToolingPreviewGroup(group -> {
            for (BlockInfo info : group.getCandidates()) {
                ItemStack stack = getIngredientStack(info);
                if (!stack.isEmpty()) {
                    inputs.add(stack);
                }
            }
        });
        ingredientInputs.addAll(inputs);
        ingredientInputsInitialized = true;
        GTLog.logger.debug("[JEIMultiblockPreview] indexed uncompiled structure inputs controller={} inputs={}",
                controller.metaTileEntityId, ingredientInputs.size());
    }

    @NotNull
    private static ItemStack getIngredientStack(@Nullable BlockInfo info) {
        if (info == null) {
            return ItemStack.EMPTY;
        }
        TileEntity tileEntity = info.getTileEntity();
        if (tileEntity instanceof IGregTechTileEntity) {
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (metaTileEntity != null) {
                ItemStack stack = metaTileEntity.getStackForm();
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return GTUtility.toItem(info.getBlockState());
    }


    @NotNull
    private ItemStack getControllerStack() {
        return controller.getStackForm();
    }

    /**
     * Replaces the controller MTE in the preview block map when the preview contains a
     * different controller instance from the same class (selfPredicateByClass scenario).
     * Creates a fresh copy of {@link #controller} to ensure the correct model/texture is rendered.
     */
    private void replaceControllerInPreview(
            @NotNull Map<BlockPos, BlockInfo> blockMap,
            @NotNull BlockPos controllerPos,
            @NotNull MultiblockControllerBase previewController) {
        MetaTileEntity copy = controller.createMetaTileEntity(null);
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(copy);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(previewController.getFrontFacing());
        blockMap.put(controllerPos, new BlockInfo(
                copy.getBlock().getDefaultState(), holder));
    }

    @Nullable
    private MultiblockControllerBase correctPreviewController(@NotNull Map<BlockPos, BlockInfo> blockMap) {
        MultiblockControllerBase controllerBase = null;
        BlockPos controllerBlockPos = null;
        MultiblockControllerBase controllerClassFallback = null;
        BlockPos controllerClassFallbackPos = null;
        for (Entry<BlockPos, BlockInfo> entry : blockMap.entrySet()) {
            TileEntity tileEntity = entry.getValue().getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity gregTechTile) ||
                    !(gregTechTile.getMetaTileEntity() instanceof MultiblockControllerBase previewController)) {
                continue;
            }
            if (controllerBlockPos == null && controller.metaTileEntityId.equals(previewController.metaTileEntityId)) {
                controllerBase = previewController;
                controllerBlockPos = entry.getKey();
            } else if (controllerClassFallbackPos == null && controller.getClass().isInstance(previewController)) {
                controllerClassFallback = previewController;
                controllerClassFallbackPos = entry.getKey();
            }
        }
        if (controllerBlockPos == null) {
            controllerBase = controllerClassFallback;
            controllerBlockPos = controllerClassFallbackPos;
        }
        if (controllerBlockPos != null && controllerBase != null &&
                !controller.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
            replaceControllerInPreview(blockMap, controllerBlockPos, controllerBase);
        }
        return controllerBase;
    }

    public void setRecipeLayout(RecipeLayout layout, IGuiHelper guiHelper) {
        boolean layoutChanged = this.recipeLayout != layout;
        this.recipeLayout = layout;
        if (layoutChanged) initializedPartsCount = 0;
        boolean switchedWrapper = lastWrapper != this;
        if (layoutChanged || switchedWrapper) channelDrag.cancel();
        if (lastWrapper != null && lastWrapper != this) {
            lastWrapper.releasePreviewResources();
        }

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, SLOT_SIZE, SLOT_SIZE)
                .setTextureSize(SLOT_SIZE, SLOT_SIZE).build();
        this.infoIcon = guiHelper.drawableBuilder(GuiTextures.INFO_ICON.imageLocation, 0, 0, ICON_SIZE, ICON_SIZE)
                .setTextureSize(ICON_SIZE, ICON_SIZE).build();

        IDrawable border = layout.getRecipeCategory().getBackground();
        preparePlaceForParts();
        if (layoutChanged) {
            // JEI recreates the item-stack group while handling wheel input. Its slots are empty even when the
            // preview renderer remains valid, so the layout must be populated again below.
            previewLayoutInitialized = false;
        }
        pendingMouseWheel = Mouse.getEventDWheel();
        resetViewWhenPreviewReady = pendingMouseWheel == 0 || switchedWrapper;
        this.nextLayerButton.x = border.getWidth() - (2 * ICON_SIZE + RIGHT_PADDING + 2);
        this.nextLayerButton.y = LAYER_BUTTON_Y;
        if (resetViewWhenPreviewReady) {
            selected = null;
            this.previewCandidates.clear();
            lastWrapper = this;
        }
        ensurePreviewLoadStarted();
        configureLoadedPreviewLayout();
        if (hasLivePreview()) {
            updateParts();
        }
        // JEI reads the category background for the recipe border and the page layout, so it has to know how many
        // channel rows this multiblock actually needs.
        MultiblockInfoCategory.setRecipeHeight(CompactPreviewLayout.CONTROLS_Y + getCompactControlsHeight());
        applyHiddenRenderBlock();
    }

    private void configureLoadedPreviewLayout() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null || recipeLayout == null) {
            return;
        }
        if (!previewLayoutInitialized || resetViewWhenPreviewReady) {
            if (resetViewWhenPreviewReady) {
                // Default framing, applied once per loaded preview. The fullscreen viewer reframes itself
                // through setPreviewFrameApplied(true) and drives the camera directly from then on.
                if (!previewFrameApplied) {
                    Vector3f size = ((TrackedDummyWorld) renderer.world).getSize();
                    float largest = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1.0F);
                    // Restored to the original framing: the square root keeps small structures from being
                    // pulled right up to the camera. resolutionFactor compensates the offscreen buffer being
                    // larger than it used to be, because the field of view is measured against that buffer.
                    this.zoom = (float) (3.5D * Math.sqrt(largest)) * resolutionFactor(renderer);
                    this.rotationYaw = 20.0f;
                    this.rotationPitch = getDefaultHorizontalCameraAngle();
                    previewFrameApplied = true;
                }
                setNextLayer(-1);
            } else {
                zoom = (float) MathHelper.clamp(zoom + (pendingMouseWheel < 0 ? 0.5 : -0.5), 3, 999);
                setNextLayer(getLayerIndex());
                if (!previewCandidates.isEmpty()) {
                    setItemStackGroup();
                }
            }
            resetCenter((TrackedDummyWorld) renderer.world, renderer);
            updateParts();
            previewLayoutInitialized = true;
        } else if (pendingMouseWheel != 0) {
            zoom = (float) MathHelper.clamp(zoom + (pendingMouseWheel < 0 ? 0.5 : -0.5), 3, 999);
            setNextLayer(getLayerIndex());
            if (!previewCandidates.isEmpty()) {
                setItemStackGroup();
            }
        }
        resetViewWhenPreviewReady = false;
        pendingMouseWheel = 0;
    }

    private static StructurePreviewRenderer createPreviewRenderer(TrackedDummyWorld world) {
        return new StructurePreviewRenderer(world, PREVIEW_MAX_RESOLUTION, PREVIEW_MAX_RESOLUTION);
    }

    @Nullable
    public StructurePreviewRenderer getCurrentRenderer() {
        return hasLivePreview() ? previewRenderer : null;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    private void toggleNextLayer() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return;
        }
        int height = (int) ((TrackedDummyWorld) renderer.world).getSize().getY() - 1;
        if (++this.layerIndex > height) {
            // if current layer index is more than max height, reset it
            // to display all layers
            this.layerIndex = -1;
        }
        setNextLayer(layerIndex);
    }

    private void setNextLayer(int newLayer) {
        this.layerIndex = newLayer;
        this.nextLayerButton.displayString = "L:" + (layerIndex == -1 ? "A" : Integer.toString(layerIndex + 1));
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer != null) {
            TrackedDummyWorld world = ((TrackedDummyWorld) renderer.world);
            resetCenter(world, renderer);
            renderer.disableClipPlanes();
            if (newLayer == -1 && rendererContainsFullStructure) {
                return;
            }
            renderer.renderedBlocks.clear();
            int minY = (int) world.getMinPos().getY();
            Collection<BlockPos> renderBlocks;
            if (newLayer == -1) {
                renderBlocks = world.renderedBlocks;
            } else {
                renderBlocks = world.renderedBlocks.stream()
                        .filter(pos -> pos.getY() - minY == newLayer)
                        .collect(Collectors.toSet());
            }
            renderer.addRenderedBlocks(renderBlocks);
            rendererContainsFullStructure = newLayer == -1;
        }
    }

    private void resetCenter(TrackedDummyWorld world, StructurePreviewRenderer renderer) {
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        if (layerIndex != -1) {
            center.y = minPos.y + layerIndex + 0.5f;
        }
        renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    private float getDefaultHorizontalCameraAngle() {
        EnumFacing frontFacing = getPreviewControllerFacing();
        if (frontFacing.getAxis().isVertical()) {
            return 50.0f;
        }

        float frontAngle = (float) Math.toDegrees(Math.atan2(frontFacing.getZOffset(), frontFacing.getXOffset()));
        // Keep the old 40-degree side offset while placing the camera on the controller's front side.
        return (frontAngle + 320.0f) % 360.0f;
    }

    @NotNull
    private EnumFacing getPreviewControllerFacing() {
        MultiblockControllerBase classFallback = null;
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return EnumFacing.SOUTH;
        }
        for (BlockPos pos : renderer.renderedBlocks) {
            TileEntity tileEntity = renderer.world.getTileEntity(pos);
            if (!(tileEntity instanceof IGregTechTileEntity gregTechTile)) continue;
            MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
            if (!(metaTileEntity instanceof MultiblockControllerBase previewController)) continue;
            if (controller.metaTileEntityId.equals(previewController.metaTileEntityId)) {
                return previewController.getFrontFacing();
            }
            if (classFallback == null && controller.getClass().isInstance(previewController)) {
                classFallback = previewController;
            }
        }
        return classFallback == null ? EnumFacing.SOUTH : classFallback.getFrontFacing();
    }

    private void updateChannelValue(int channelIndex, int delta) {
        if (channelIndex < 0 || channelIndex >= supportedChannels.size()) return;
        String channelName = supportedChannels.get(channelIndex).getName();
        int min = channelRanges[channelIndex][0];
        int max = channelRanges[channelIndex][1];
        int current = channelValues.getOrDefault(channelName, 0);
        int currentIndex = channelValueToSliderIndex(current, min, max);
        int newIndex = Math.max(0, Math.min(getSliderStepCount(min, max), currentIndex + delta));
        int newValue = sliderIndexToChannelValue(newIndex, min, max);
        if (newValue == 0) {
            channelValues.remove(channelName);
        } else {
            channelValues.put(channelName, newValue);
        }
        regeneratePatterns();
    }

    private void setChannelValue(int channelIndex, int value) {
        if (channelIndex < 0 || channelIndex >= supportedChannels.size()) return;
        String channelName = supportedChannels.get(channelIndex).getName();
        int min = channelRanges[channelIndex][0];
        int max = channelRanges[channelIndex][1];
        int firstValue = getFirstSelectableValue(min);
        int clamped = value <= 0 || max < firstValue ? 0 : Math.max(firstValue, Math.min(max, value));
        if (channelValues.getOrDefault(channelName, 0) == clamped) return;
        if (clamped == 0) {
            channelValues.remove(channelName);
        } else {
            channelValues.put(channelName, clamped);
        }
        regeneratePatterns();
    }

    private void regeneratePatterns() {
        clearPreviewSelection();
        initializePreviewMetadata();
        resetViewWhenPreviewReady = true;
        startPreviewLoading();
    }

    private void clearPreviewSelection() {
        cachedCandidateGroups.clear();
        cachedCandidateBlocks.clear();
        this.selected = null;
        this.candidateCycleIndex = 0;
        this.lastCandidateCycleTime = 0L;
        if (recipeLayout != null) {
            IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
            for (int i = 0; i < MAX_CANDIDATES; i++) {
                try {
                    itemStackGroup.set(i + CANDIDATE_SLOT_BASE, ItemStack.EMPTY);
                } catch (Exception ignored) {
                    // Candidate slots are created lazily; ignore slots that do not exist yet.
                }
            }
        }
        previewCandidates.clear();
        previewTips = null;
        applyHiddenRenderBlock();
    }

    public int getCompactPartCount() {
        return hasLivePreview() ? patterns[0].getParts().size() : 0;
    }

    private void preparePlaceForParts() {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        int count = getCompactPartCount();
        for (int i = count; i < initializedPartsCount; i++) {
            itemStackGroup.set(i, (ItemStack) null);
        }
        for (int i = 0; i < count; i++) {
            itemStackGroup.init(i, true, CompactPreviewLayout.partSlotX(i), CompactPreviewLayout.partSlotY(i));
        }
        initializedPartsCount = count;
    }

    private void updateParts() {
        preparePlaceForParts();
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        List<ItemStack> parts = this.patterns[0].getParts();
        for (int i = 0; i < parts.size(); i++) {
            itemStackGroup.set(i, parts.get(i));
        }
    }

    private void clearParts() {
        if (recipeLayout == null) {
            return;
        }
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        for (int i = 0; i < initializedPartsCount; i++) {
            itemStackGroup.set(i, (ItemStack) null);
        }
    }
    @Override
    public void drawInfo(@NotNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        boolean draggingChannel = channelDrag.isActive();
        updateChannelDrag(mouseX, Mouse.isButtonDown(0));
        ensurePreviewLoadStarted();
        advancePreviewLoading();
        advanceCandidateCycle();
        if (!hasLivePreview()) {
            drawPreviewLoading(minecraft, recipeWidth, CompactPreviewLayout.HEIGHT);
            tooltipBlockStack = null;
            this.previewTips = null;
            lastRender = System.currentTimeMillis();
            return;
        }
        configureLoadedPreviewLayout();
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            drawPreviewLoading(minecraft, recipeWidth, CompactPreviewLayout.HEIGHT);
            return;
        }
        int sceneX = 0;
        int sceneY = 0;
        int sceneWidth = CompactPreviewLayout.WIDTH;
        int sceneHeight = CompactPreviewLayout.HEIGHT;
        int panelBackground = 0xFF000000 | ConfigHolder.client.multiblockPreviewColor;
        int partCount = getCompactPartCount();
        if (partCount > 0) {
            int partsX = CompactPreviewLayout.partsX(partCount);
            int partsWidth = CompactPreviewLayout.partsWidth(partCount);
            int partsHeight = CompactPreviewLayout.partsHeight(partCount);
            drawRect(partsX, 0, partsX + partsWidth, partsHeight, panelBackground);
            drawPreviewFrame(partsX, 0, partsWidth, partsHeight);
        }
        int controlsHeight = getCompactControlsHeight();
        drawRect(0, CompactPreviewLayout.CONTROLS_Y, recipeWidth,
                CompactPreviewLayout.CONTROLS_Y + controlsHeight, panelBackground);
        drawPreviewFrame(0, CompactPreviewLayout.CONTROLS_Y, recipeWidth, controlsHeight);
        matchBufferAspect(renderer, sceneWidth, sceneHeight);

        boolean isMouseOverButton = false;
        for (GuiButton button : buttons.keySet()) {
            if (mouseX >= button.x && mouseX <= button.x + button.width &&
                    mouseY >= button.y && mouseY <= button.y + button.height) {
                isMouseOverButton = true;
                break;
            }
        }
        // Camera interaction is confined to the structure viewport.
        boolean insideView = mouseX >= sceneX && mouseY >= sceneY &&
                mouseX < sceneX + sceneWidth && mouseY < sceneY + sceneHeight &&
                !isMouseOverButton && !draggingChannel;

        boolean leftClickHeld = Mouse.isButtonDown(0);
        boolean rightClickHeld = Mouse.isButtonDown(1);
        // Right drag pans, left drag orbits; the right button wins when both are held. The drag itself is handled
        // by the shared camera-drag state, which the fullscreen viewer feeds as well.
        CameraDragMode dragMode = null;
        if (insideView) {
            dragMode = rightClickHeld ? CameraDragMode.PAN : (leftClickHeld ? CameraDragMode.ROTATE : null);
        }
        updateCameraDrag(dragMode, mouseX, mouseY, sceneHeight);

        // 悬停检测（仅当未拖拽时）

        // Render 3D scene (OpenGL state safety)
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableRescaleNormal();
            // The preview renderer disables lighting for the baked block mesh and only enables it
            // around tile entity rendering itself. Enabling it here would leave GL_LIGHTING on
            // after the renderer's popAttrib, which GlStateManager's cache cannot observe, and the
            // final scene texture would be multiplied by the fixed-function lighting (dark preview).
            renderer.render(sceneX, sceneY, sceneWidth, sceneHeight, mouseX, mouseY);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
        drawPreviewFrame(sceneX, sceneY, sceneWidth, sceneHeight);

        // Draw the title and channel controls outside the structure viewport.
        drawMultiblockName(recipeWidth);
        drawMultiblockTier(recipeWidth);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Fullscreen action in the bottom toolbar.
        int iconX = recipeWidth - (ICON_SIZE + RIGHT_PADDING);
        int iconY = INFO_ICON_Y;
        this.infoIcon.draw(minecraft, iconX, iconY);
        this.drawInfoIcon = mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                mouseY >= iconY && mouseY <= iconY + ICON_SIZE;

        // Draw left-side parts slots (only for actual items, no empty slots to avoid blocking 3D)
        for (int i = 0; i < partCount; ++i) {
            this.slot.draw(minecraft, CompactPreviewLayout.partSlotX(i), CompactPreviewLayout.partSlotY(i));
        }

        // Candidate slots have a dedicated rail beside the viewport.
        for (int i = 0; i < previewCandidates.size() && i < MAX_CANDIDATES; i++) {
            int col = i / CANDIDATES_PER_COL;
            int row = i % CANDIDATES_PER_COL;
            int slotX = recipeWidth - RIGHT_PADDING - (col + 1) * SLOT_SIZE;
            int slotY = row * SLOT_SIZE + CANDIDATE_SLOT_START_Y;
            this.slot.draw(minecraft, slotX, slotY);
        }

        for (GuiButton button : buttons.keySet()) {
            if (button.id == 1) {
                button.visible = supportedChannels.size() > CHANNEL_ROWS;
                button.displayString = (channelPage + 1) + "/" + Math.max(1, (supportedChannels.size() + CHANNEL_ROWS - 1) / CHANNEL_ROWS);
            }
            button.drawButton(minecraft, mouseX, mouseY, 0.0f);
        }

        tooltipBlockStack = null;
        this.previewTips = null;
        RayTraceResult rayTraceResult = renderer.getLastTraceResult();

        if (!(leftClickHeld || rightClickHeld) && insideView && rayTraceResult != null &&
                !renderer.world.isAirBlock(rayTraceResult.getBlockPos())) {

            IBlockState blockState = renderer.world.getBlockState(rayTraceResult.getBlockPos());
            ItemStack itemStack = getPreviewBlockStack(renderer.world, rayTraceResult.getBlockPos(),
                    blockState, rayTraceResult, minecraft.player);

            this.previewTips = previewTooltipFor(rayTraceResult.getBlockPos());
            if (!itemStack.isEmpty()) {
                tooltipBlockStack = itemStack;
            }
        }

        lastRender = System.currentTimeMillis();

        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
    }

    private void drawPreviewLoading(@NotNull Minecraft minecraft, int recipeWidth, int recipeHeight) {
        drawMultiblockName(recipeWidth);
        FontRenderer fontRenderer = minecraft.fontRenderer;
        int barWidth = Math.min(132, Math.max(80, recipeWidth - 44));
        int barHeight = 8;
        int barX = (recipeWidth - barWidth) / 2;
        int barY = Math.max(42, (recipeHeight - barHeight) / 2);
        PreviewLoadTask task = previewLoadTask;
        float progress = task == null ? 0.0F : task.getProgress();
        int percent = Math.max(0, Math.min(100, Math.round(progress * 100.0F)));
        String status;
        if (previewLoadFailure != null) {
            status = I18n.format("gregtech.multiblock.preview.loading_failed");
        } else if (task != null) {
            status = I18n.format("gregtech.multiblock.preview.loading_progress",
                    I18n.format(task.getStageTranslationKey()), percent);
        } else {
            status = I18n.format("gregtech.multiblock.preview.loading_prepare");
        }

        drawRect(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF1C1C1C);
        drawRect(barX, barY, barX + barWidth, barY + barHeight, 0xFF595959);
        drawRect(barX, barY, barX + Math.round(barWidth * progress), barY + barHeight, 0xFF4A9F5A);
        fontRenderer.drawString(status, (recipeWidth - fontRenderer.getStringWidth(status)) / 2,
                barY - fontRenderer.FONT_HEIGHT - 4, 0xF0F0F0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawMultiblockName(int recipeWidth) {
        String localizedName = I18n.format(controller.getMetaFullName());
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        List<String> lines = fontRenderer.listFormattedStringToWidth(localizedName, recipeWidth - 8);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(lines.get(i), (recipeWidth - fontRenderer.getStringWidth(lines.get(i))) / 2,
                    fontRenderer.FONT_HEIGHT * i, ConfigHolder.client.multiblockPreviewFontColor);
        }
    }

    private void drawMultiblockTier(int recipeWidth) {
        if (supportedChannels.isEmpty()) return;
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        // Fixed-height channel pages keep the camera viewport stable across structures.
        int sliderStartY = CHANNEL_TOP;
        int sliderWidth = getSliderWidth(recipeWidth);
        int sliderX = getSliderX();

        for (int i = channelPage * CHANNEL_ROWS; i < Math.min(supportedChannels.size(), (channelPage + 1) * CHANNEL_ROWS); i++) {
            StructureChannel channel = supportedChannels.get(i);
            String channelName = channel.getName();
            int min = channelRanges[i][0];
            int max = channelRanges[i][1];
            int value = getChannelValue(channel);
            int rowY = sliderStartY + (i - channelPage * CHANNEL_ROWS) * 16;

            // Draw channel label (localized)
            String label = I18n.format(channel.getDefaultTooltip());
            fontRenderer.drawString(fontRenderer.trimStringToWidth(label, sliderWidth - 28), sliderX, rowY,
                    ConfigHolder.client.multiblockPreviewFontColor);

            // Draw slider track
            int trackX = sliderX;
            int trackY = rowY + fontRenderer.FONT_HEIGHT + 1;
            int trackHeight = 4;
            drawRect(trackX, trackY, trackX + sliderWidth, trackY + trackHeight, 0xFF56646E);

            // Draw slider handle
            int stepCount = getSliderStepCount(min, max);
            int sliderIndex = channelValueToSliderIndex(value, min, max);
            float ratio = stepCount == 0 ? 0.0F : (float) sliderIndex / stepCount;
            int handleX = trackX + (int) (ratio * (sliderWidth - 4));
            drawRect(handleX, trackY - 1, handleX + 4, trackY + trackHeight + 1, 0xFF4488CC);

            // Draw value text (right-aligned)
            String valueText = value == 0 ? "Auto" : String.valueOf(value);
            fontRenderer.drawString(valueText, sliderX + sliderWidth - fontRenderer.getStringWidth(valueText),
                    rowY, ConfigHolder.client.multiblockPreviewFontColor);
        }
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }

    /**
     * Opens the fullscreen multiblock preview viewer for this wrapper, closing a previously opened one.
     * Called from the recipe click handler, so the screen switch is deferred to the next client tick to
     * avoid replacing the current screen in the middle of its own input handling.
     */
    public void openFullscreenPreview() {
        MultiblockInfoFullscreenScreen previous = MultiblockInfoFullscreenScreen.getOpenScreen();
        if (previous != null) {
            previous.close();
        }
        initializePreviewMetadata();
        MultiblockInfoFullscreenScreen screen = new MultiblockInfoFullscreenScreen(this);
        screen.initFullscreenView();
        // Remembers the JEI recipe page so collapsing (ESC) returns to it instead of closing the view.
        screen.openFromCurrentScreen();
    }

    /**
     * @return true when this wrapper currently owns a usable preview renderer.
     */
    public boolean isPreviewReady() {
        return hasLivePreview();
    }

    @NotNull
    public List<ItemStack> getPreviewParts() {
        return hasLivePreview() ? patterns[0].getParts() : Collections.emptyList();
    }

    @NotNull
    public List<StructureChannel> getSupportedChannels() {
        initializePreviewMetadata();
        return supportedChannels;
    }

    /**
     * @return height the compact page has to reserve for its channel strip for this multiblock. Until the channel
     *         metadata is known the maximum height is reported, so the strip never shrinks before it is drawn.
     */
    public int getCompactControlsHeight() {
        return compactControlsHeight;
    }

    /**
     * @return [min, max] selectable values for the channel at the given index.
     */
    public int[] getChannelRange(int channelIndex) {
        initializePreviewMetadata();
        if (channelIndex < 0 || channelIndex >= channelRanges.length) {
            return new int[] { 0, 0 };
        }
        return channelRanges[channelIndex];
    }

    public int getChannelValue(@NotNull StructureChannel channel) {
        int index = supportedChannels.indexOf(channel);
        int committed = channelValues.getOrDefault(channel.getName(), 0);
        if (index < 0) return committed;
        int min = channelRanges[index][0], max = channelRanges[index][1];
        return sliderIndexToChannelValue(channelDrag.indexFor(index,
                channelValueToSliderIndex(committed, min, max)), min, max);
    }

    public void beginChannelDrag(int channel, int trackX, int trackWidth, int mouseX) {
        channelDrag.begin(channel, trackX, trackWidth, getSliderSteps(channel), mouseX);
    }

    public boolean isDraggingChannel() {
        return channelDrag.isActive();
    }

    public void updateChannelDrag(int mouseX, boolean held) {
        channelDrag.update(mouseX, held, this::applyChannelSliderValue);
    }

    public void cancelChannelDrag() {
        channelDrag.cancel();
    }

    /**
     * Applies a slider index (0 = auto, 1..n = concrete values) to the channel and regenerates the preview.
     */
    private void applyChannelSliderValue(int channelIndex, int sliderIndex) {
        if (channelIndex < 0 || channelIndex >= supportedChannels.size()) {
            return;
        }
        int min = channelRanges[channelIndex][0];
        int max = channelRanges[channelIndex][1];
        setChannelValue(channelIndex, sliderIndexToChannelValue(sliderIndex, min, max));
    }

    public int getSliderSteps(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelRanges.length) {
            return 0;
        }
        return getSliderStepCount(channelRanges[channelIndex][0], channelRanges[channelIndex][1]);
    }

    public int getSliderIndexFor(@NotNull StructureChannel channel, int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channelRanges.length) {
            return 0;
        }
        int min = channelRanges[channelIndex][0];
        int max = channelRanges[channelIndex][1];
        return channelValueToSliderIndex(getChannelValue(channel), min, max);
    }

    /**
     * Sets the layer shown in the preview; -1 displays the whole structure.
     */
    public void setLayer(int layer) {
        setNextLayer(layer);
    }

    public void toggleLayer() {
        toggleNextLayer();
    }

    /**
     * @return the components currently chosen as alternatives for the selected position. Empty when no
     *         position is selected or the element carries no typed preview metadata.
     */
    @NotNull
    public List<PreviewCandidate> getCandidates() {
        return previewCandidates;
    }

    /**
     * @return the alternative currently cycled in-place in the 3D scene, or null when nothing is selected or
     *         the selected element carries no typed alternatives.
     */
    @Nullable
    public PreviewCandidate getActiveCandidate() {
        List<PreviewCandidate> candidates = getCandidates();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(Math.floorMod(candidateCycleIndex, candidates.size()));
    }

    /**
     * @return 1-based position of the cycled alternative, or -1 when nothing is selected.
     */
    public int getActiveCandidateIndex() {
        List<PreviewCandidate> candidates = getCandidates();
        return candidates.isEmpty() ? -1 : Math.floorMod(candidateCycleIndex, candidates.size()) + 1;
    }

    /**
     * @return the maximum selectable layer index (structure height - 1), or -1 when no preview is loaded.
     */
    public int getMaxLayerIndex() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return -1;
        }
        return (int) ((TrackedDummyWorld) renderer.world).getSize().getY() - 1;
    }

    @NotNull
    public String getMultiblockName() {
        return I18n.format(controller.getMetaFullName());
    }

    @NotNull
    public Vector3f getCameraCenter() {
        return center;
    }

    public void setCameraCenter(@NotNull Vector3f center) {
        this.center = center;
    }

    public float getRotationPitch() {
        return rotationPitch;
    }

    public float getRotationYaw() {
        return rotationYaw;
    }

    public void setRotation(float pitch, float yaw) {
        this.rotationPitch = pitch;
        this.rotationYaw = yaw;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    /**
     * Pushes the current camera state into the renderer, marking the cached FBO content as dirty.
     */
    public void applyCamera() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return;
        }
        renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    public void markRendererDirty() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer != null) {
            renderer.markFBODirty();
        }
    }

    /** How the preview camera reacts to a cursor drag. */
    public enum CameraDragMode {
        /** Left drag: orbit the structure around the camera target. */
        ROTATE,
        /** Right drag: slide the structure along with the cursor. */
        PAN
    }

    /**
     * Feeds one frame of preview camera dragging. The compact recipe page polls the button state while the
     * fullscreen viewer forwards its mouse-move events, but both go through this method, so the two views keep
     * the same drag feel and cannot drift apart again.
     *
     * <p>
     * The camera only starts moving once the cursor left a small threshold, and the first step is measured from
     * the position the drag started in. Without that, a click that lands while the preview is sampled would
     * already shift the view by a pixel or two and the structure would crawl away under the cursor.
     *
     * @param mode       drag in progress, or {@code null} when the preview is not being dragged this frame
     * @param mouseX     cursor x in GUI coordinates
     * @param mouseY     cursor y in GUI coordinates
     * @param viewHeight rendered viewport height in GUI pixels, used to scale panning
     */
    public void updateCameraDrag(@Nullable CameraDragMode mode, int mouseX, int mouseY, int viewHeight) {
        if (mode == null) {
            resetCameraDrag();
            return;
        }
        if (mode != cameraDragMode) {
            // (Re-)anchor on the cursor: a drag must never inherit a position from an earlier, unrelated one.
            cameraDragMode = mode;
            cameraDragAnchorX = mouseX;
            cameraDragAnchorY = mouseY;
            cameraDragLastX = mouseX;
            cameraDragLastY = mouseY;
            cameraDragStarted = false;
            return;
        }
        if (!cameraDragStarted) {
            if (Math.abs(mouseX - cameraDragAnchorX) <= CAMERA_DRAG_THRESHOLD &&
                    Math.abs(mouseY - cameraDragAnchorY) <= CAMERA_DRAG_THRESHOLD) {
                return;
            }
            cameraDragStarted = true;
        }
        int deltaX = mouseX - cameraDragLastX;
        int deltaY = mouseY - cameraDragLastY;
        if (deltaX == 0 && deltaY == 0) {
            return;
        }
        cameraDragLastX = mouseX;
        cameraDragLastY = mouseY;
        if (mode == CameraDragMode.PAN) {
            panCamera(deltaX, deltaY, viewHeight);
        } else {
            orbitCamera(deltaX, deltaY);
        }
    }

    /**
     * Drops the drag in progress. The next drag anchors on the cursor again instead of continuing from a stale
     * position, which is what made the view jump when a drag was interrupted by a panel or the window edge.
     */
    public void resetCameraDrag() {
        cameraDragMode = null;
        cameraDragStarted = false;
    }

    /**
     * Orbits the camera around the preview target using the drag-the-structure convention: dragging right turns
     * the visible face of the structure to the right, dragging down tips its top towards the viewer.
     */
    public void orbitCamera(float deltaX, float deltaY) {
        if (deltaX == 0.0F && deltaY == 0.0F) {
            return;
        }
        rotationPitch = (rotationPitch + deltaX) % 360.0F;
        if (rotationPitch < 0.0F) {
            rotationPitch += 360.0F;
        }
        rotationYaw = MathHelper.clamp(rotationYaw + deltaY, -89.9F, 89.9F);
        applyCamera();
    }

    /**
     * Slides the camera target so the structure follows the cursor. The translation is derived from the
     * projection at the view target instead of a fixed sensitivity, so one pixel of cursor movement moves the
     * structure by one pixel at any zoom level and in any viewport size.
     */
    public void panCamera(double deltaX, double deltaY, int viewHeight) {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null || center == null || (deltaX == 0.0D && deltaY == 0.0D)) {
            return;
        }
        Vec3d forward = new Vec3d(renderer.getLookAt().x - renderer.getEyePos().x,
                renderer.getLookAt().y - renderer.getEyePos().y,
                renderer.getLookAt().z - renderer.getEyePos().z).normalize();
        Vec3d right = forward.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(forward).normalize();

        // The projection at the target plane spans 2 * distance * tan(fov / 2) over the viewport height, which
        // is exactly how far the structure has to move for a pixel of cursor movement to stay under the cursor.
        double unitsPerPixel = 2.0D * Math.max(0.01D, zoom) * Math.tan(Math.toRadians(CAMERA_FOV_DEGREES / 2.0D))
                / Math.max(1, viewHeight);
        // The camera moves against the cursor; that is what makes the structure move along with it.
        double slideRight = -deltaX * unitsPerPixel;
        double slideUp = deltaY * unitsPerPixel;
        this.center = new Vector3f(
                (float) (center.x + right.x * slideRight + up.x * slideUp),
                (float) (center.y + right.y * slideRight + up.y * slideUp),
                (float) (center.z + right.z * slideRight + up.z * slideUp));
        applyCamera();
    }

    /**
     * @return a freshly laid out candidate list for the given preview position; empty when the element
     *         carries no typed alternatives.
     */
    @NotNull
    public List<PreviewCandidate> loadCandidatesFor(@NotNull BlockPos pos) {
        if (!hasLivePreview()) {
            return Collections.emptyList();
        }
        return loadPreviewCandidates(pos);
    }

    @NotNull
    public List<String> getSelectionTips() {
        return previewTips == null ? Collections.emptyList() : previewTips;
    }

    /**
     * @return the tooltip lines for the block currently under the cursor in the preview, or an empty list.
     */
    @NotNull
    public List<String> getBlockTooltip() {
        if (tooltipBlockStack == null || tooltipBlockStack.isEmpty() || Mouse.isButtonDown(0)) {
            return Collections.emptyList();
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        ITooltipFlag flag = minecraft.gameSettings.advancedItemTooltips ? TooltipFlags.ADVANCED : TooltipFlags.NORMAL;
        List<String> tooltip = new ArrayList<>(tooltipBlockStack.getTooltip(minecraft.player, flag));
        EnumRarity rarity = tooltipBlockStack.getRarity();
        for (int i = 0; i < tooltip.size(); i++) {
            tooltip.set(i, i == 0 ? rarity.color + tooltip.get(i) : TextFormatting.GRAY + tooltip.get(i));
        }
        if (previewTips != null) {
            tooltip.addAll(previewTips);
        }
        return tooltip;
    }

    @Nullable
    public BlockPos getSelectedBlock() {
        return selected;
    }

    /**
     * @return true when the last preview build attempt failed; the fullscreen viewer shows the failure state.
     */
    public boolean hasPreviewFailure() {
        return previewLoadFailure != null;
    }

    /**
     * @return loading progress of the pending preview build, 0.0 when nothing is being built.
     */
    public float getPreviewProgress() {
        PreviewLoadTask task = previewLoadTask;
        return task == null ? (hasLivePreview() ? 1.0F : 0.0F) : task.getProgress();
    }

    /**
     * Restores the item slot positions that the compact JEI layout expects. The fullscreen viewer moves the
     * shared JEI slots to its own coordinates, so they have to be put back before the recipe page draws again.
     */
    public void restoreCompactLayout() {
        if (recipeLayout == null) {
            return;
        }
        preparePlaceForParts();
        if (hasLivePreview()) {
            updateParts();
        }
        if (!previewCandidates.isEmpty()) {
            setItemStackGroup();
        }
    }

    /**
     * Renders only the 3D preview into the given rectangle. The fullscreen viewer draws its own overlay on
     * top, so the compact layout's name, tier sliders, slots and buttons must not be drawn here. The offscreen
     * buffer is sized to the rectangle's aspect ratio so the scene fills it exactly without being stretched.
     * Mouse coordinates are expected in the same space as the rectangle (screen space).
     */
    public void renderScene(@NotNull Minecraft minecraft, int x, int y, int width, int height, int mouseX,
                            int mouseY) {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null || width <= 0 || height <= 0) {
            return;
        }
        // Fullscreen viewers give the preview a wide window; matching the buffer keeps the whole window used
        // and renders fewer pixels than a square buffer, which is what made dragging feel heavy.
        matchBufferAspect(renderer, width, height);
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableRescaleNormal();
            // Lighting is managed by the renderer; enabling it here breaks the cached GL state
            // after the renderer's popAttrib and makes the whole preview look dark.
            renderer.render(x, y, width, height, mouseX, mouseY);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Resizes the offscreen buffer to the aspect ratio of the area it will be drawn into. The buffer is the
     * preview's field of view, so matching the area is what lets the preview fill that area exactly without
     * being stretched.
     *
     * <p>
     * The buffer is deliberately kept close to the displayed size instead of oversampling: the preview is a
     * full-screen 3D scene, and rendering several times more pixels than the window shows is what made
     * dragging the camera stutter. A little softness is the accepted trade for a smooth drag.
     */
    private void matchBufferAspect(@NotNull StructurePreviewRenderer renderer, int areaWidth, int areaHeight) {
        int displayedLongest = Math.max(1, Math.max(areaWidth, areaHeight)) * guiScale();
        int quality = Math.max(256, Math.min(displayedLongest, PREVIEW_MAX_RESOLUTION));
        float aspect = (float) Math.max(1, areaWidth) / Math.max(1, areaHeight);
        int width;
        int height;
        if (aspect >= 1.0F) {
            width = quality;
            height = Math.max(96, Math.round(quality / aspect));
        } else {
            height = quality;
            width = Math.max(96, Math.round(quality * aspect));
        }
        int currentWidth = renderer.getResolutionWidth();
        int currentHeight = Math.max(1, renderer.getResolutionHeight());
        // Reallocate only on a meaningful change: a few pixels of viewport drift must not rebuild the buffer
        // (and force a full scene re-render) every frame.
        float ratio = Math.abs(width - currentWidth) / (float) Math.max(1, Math.max(width, currentWidth));
        float currentAspect = currentWidth / (float) currentHeight;
        float aspectDelta = Math.abs(currentAspect - aspect) / Math.max(0.01F, aspect);
        if (ratio <= 0.15F && aspectDelta <= 0.06F) {
            return;
        }
        renderer.setFBOSize(width, height);
    }

    private static int guiScale() {
        try {
            ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
            return Math.max(1, resolution.getScaleFactor());
        } catch (RuntimeException ignored) {
            // Fall back to a sane scale; the buffer size only affects sharpness, never correctness.
            return 2;
        }
    }

    /**
     * Recomputes the hovered block and its tooltip data for the given mouse position. Mirrors the hover
     * handling of {@link #drawInfo(Minecraft, int, int, int, int)} so the fullscreen viewer shows the same
     * tooltips without duplicating the ray trace result plumbing.
     */
    public void updateHoverState(@NotNull Minecraft minecraft, int mouseX, int mouseY, boolean insideView) {
        tooltipBlockStack = null;
        this.previewTips = null;
        StructurePreviewRenderer renderer = getCurrentRenderer();
        lastRender = System.currentTimeMillis();
        if (renderer == null || !insideView || Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            return;
        }
        RayTraceResult rayTraceResult = renderer.getLastTraceResult();
        if (rayTraceResult == null || renderer.world.isAirBlock(rayTraceResult.getBlockPos())) {
            return;
        }
        IBlockState blockState = renderer.world.getBlockState(rayTraceResult.getBlockPos());
        ItemStack itemStack = getPreviewBlockStack(renderer.world, rayTraceResult.getBlockPos(), blockState,
                rayTraceResult, minecraft.player);
        this.previewTips = previewTooltipFor(rayTraceResult.getBlockPos());
        if (!itemStack.isEmpty()) {
            tooltipBlockStack = itemStack;
        }
    }

    /**
     * Resolves the item shown by the hover tooltip. Picking the preview scene only produces a plain
     * {@link RayTraceResult}, while {@link gregtech.api.block.machines.BlockMachine#getPickBlock} only answers
     * {@link codechicken.lib.raytracer.CuboidRayTraceResult}s and returns empty for everything else. Controllers
     * and hatches are machine blocks, which is why they used to show no name at all. Read their MetaTileEntity
     * directly and keep the block lookup for every other block.
     */
    @NotNull
    private static ItemStack getHoveredStack(@NotNull World world, @NotNull BlockPos pos,
                                             @NotNull IBlockState blockState, @NotNull RayTraceResult hitResult,
                                             @Nullable EntityPlayer player) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IGregTechTileEntity gregTechTile) {
            MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
            if (metaTileEntity != null) {
                ItemStack stack = metaTileEntity.getStackForm();
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return blockState.getBlock().getPickBlock(blockState, hitResult, world, pos, player);
    }

    /**
     * Resolves the tooltip item for the block under the cursor. When the cursor is on the selected position, the
     * in-place candidate rotation is what the player actually sees, so the tooltip has to describe that alternative
     * instead of the block that happens to be stored in the preview world.
     */
    @NotNull
    private ItemStack getPreviewBlockStack(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState blockState,
                                           @NotNull RayTraceResult hitResult, @Nullable EntityPlayer player) {
        BlockInfo displayed = getDisplayedCandidate(pos);
        if (displayed != null) {
            return getCandidateStack(displayed);
        }
        return getHoveredStack(world, pos, blockState, hitResult, player);
    }

    /**
     * @return the alternative that {@link #renderCandidateBlockAtPosition(World, BlockPos)} currently draws at
     *         {@code pos}, or {@code null} when that position is not the selected one or has no typed alternatives.
     */
    @Nullable
    private BlockInfo getDisplayedCandidate(@NotNull BlockPos pos) {
        if (selected == null || !selected.equals(pos) || previewCandidates.isEmpty()) {
            return null;
        }
        if (cachedCandidateBlocks.isEmpty()) {
            return null;
        }
        // Same flattened list and index the in-place renderer uses, so name and rendered block always match.
        return cachedCandidateBlocks.get(Math.floorMod(candidateCycleIndex, cachedCandidateBlocks.size()));
    }

    @NotNull
    private static ItemStack getCandidateStack(@NotNull BlockInfo candidate) {
        TileEntity tileEntity = candidate.getTileEntity();
        if (tileEntity instanceof IGregTechTileEntity gregTechTile) {
            MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
            if (metaTileEntity != null) {
                ItemStack stack = metaTileEntity.getStackForm();
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return GTUtility.toItem(candidate.getBlockState());
    }

    /**
     * Selects the block under the given preview-world position, loading its typed alternatives. Also used by
     * the fullscreen viewer so both views share the same selection state.
     */
    public RayTraceResult pickPreview(int mouseX, int mouseY, int x, int y, int width, int height) {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        RayTraceResult hit = renderer == null ? null : renderer.pick(mouseX, mouseY, x, y, width, height);
        long now = System.currentTimeMillis();
        if (now - lastClickDiagnostic >= 5000) {
            if(ConfigHolder.misc.debug) GTLog.logger.info("[JEIMultiblockPreview] click controller={} mouse=({}, {}) viewport=({}, {}, {}, {}) hit={} blocks={}",
                    controller.metaTileEntityId, mouseX, mouseY, x, y, width, height,
                    hit == null ? "none" : hit.getBlockPos(), renderer == null ? 0 : renderer.renderedBlocks.size());
            lastClickDiagnostic = now;
        }
        return hit;
    }

    public void selectBlock(@NotNull BlockPos pos) {
        if (Objects.equals(this.selected, pos)) {
            return;
        }
        previewCandidates.clear();
        this.selected = pos;
        this.candidateCycleIndex = 0;
        this.lastCandidateCycleTime = 0L;
        previewCandidates.addAll(loadPreviewCandidates(pos));
        applyHiddenRenderBlock();
    }

    /**
     * Removes the preview world's block at the selected position while its candidate rotation is drawn in place, so
     * the scene shows one block instead of the candidate stacked on top of the original. Picking keeps the position,
     * because only rendering is skipped.
     */
    private void applyHiddenRenderBlock() {
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return;
        }
        BlockPos hidden = selected != null && !previewCandidates.isEmpty() ? selected : null;
        if (!Objects.equals(renderer.getHiddenBlock(), hidden)) {
            renderer.setHiddenBlock(hidden);
        }
    }

    public void clearSelection() {
        clearPreviewSelection();
    }

    /**
     * @return the item slots of the compact JEI layout, or null when the recipe page has not been laid out.
     */
    @Nullable
    public IGuiItemStackGroup getItemStackGroup() {
        return recipeLayout == null ? null : recipeLayout.getItemStacks();
    }

    /**
     * Lets a caller (the fullscreen viewer) take over camera framing for the loaded preview.
     */
    public void setPreviewFrameApplied(boolean applied) {
        this.previewFrameApplied = applied;
    }

    /**
     * The camera distance has to grow with the offscreen resolution, because the renderer's field of view is
     * measured against the buffer size. Returns 1.0 for the baseline square buffer and scales from there.
     */
    private static float resolutionFactor(@NotNull StructurePreviewRenderer renderer) {
        int width = renderer.getResolutionWidth();
        return Math.max(1.0F, width / (float) PREVIEW_MAX_RESOLUTION);
    }

    /**
     * Draws a thin frame around the rendered area, so the preview reads as a deliberate viewport rather than
     * floating over the recipe background.
     */
    private static void drawPreviewFrame(int x, int y, int width, int height) {
        int color = 0x33FFFFFF;
        drawRect(x, y, x + width, y + 1, color);
        drawRect(x, y + height - 1, x + width, y + height, color);
        drawRect(x, y, x + 1, y + height, color);
        drawRect(x + width - 1, y, x + width, y + height, color);
    }

    private int getSliderTrackY(int channelIdx, int sliderStartY) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        return sliderStartY + (channelIdx - channelPage * CHANNEL_ROWS) * 16 + fontRenderer.FONT_HEIGHT + 1;
    }

    private int getSliderX() {
        return 5;
    }

    private int getSliderWidth(int recipeWidth) {
        return recipeWidth - 3 * ICON_SIZE - RIGHT_PADDING - 14;
    }

    private static int getFirstSelectableValue(int min) {
        return Math.max(1, min);
    }

    private static int getSliderStepCount(int min, int max) {
        int firstValue = getFirstSelectableValue(min);
        return max < firstValue ? 0 : max - firstValue + 1;
    }

    private static int channelValueToSliderIndex(int value, int min, int max) {
        int firstValue = getFirstSelectableValue(min);
        if (value <= 0 || max < firstValue) return 0;
        int clamped = Math.max(firstValue, Math.min(max, value));
        return clamped - firstValue + 1;
    }

    private static int sliderIndexToChannelValue(int index, int min, int max) {
        int firstValue = getFirstSelectableValue(min);
        if (index <= 0 || max < firstValue) return 0;
        return Math.min(max, firstValue + index - 1);
    }

    @Override
    public boolean handleClick(@NotNull Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        ensurePreviewLoadStarted();
        if (!hasLivePreview()) {
            return false;
        }
        StructurePreviewRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return false;
        }
        // The info icon expands the preview into the fullscreen viewer. The screen switch is queued for the
        // next client tick: replacing the current screen from inside its own mouse handling corrupts JEI's
        // input state.
        if (mouseButton == 0 && recipeLayout != null) {
            int iconX = recipeLayout.getRecipeCategory().getBackground().getWidth() - (ICON_SIZE + RIGHT_PADDING);
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                    && mouseY >= INFO_ICON_Y && mouseY <= INFO_ICON_Y + ICON_SIZE) {
                minecraft.addScheduledTask(this::openFullscreenPreview);
                return true;
            }
        }
        // Handle channel slider clicks
        if (mouseButton == 0 && !supportedChannels.isEmpty()) {
            int sliderStartY = CHANNEL_TOP;
            int sliderX = getSliderX();
            int sliderWidth = getSliderWidth(176);
            for (int i = channelPage * CHANNEL_ROWS; i < Math.min(supportedChannels.size(), (channelPage + 1) * CHANNEL_ROWS); i++) {
                int trackY = getSliderTrackY(i, sliderStartY);
                // Click area: track region with some vertical tolerance
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth
                        && mouseY >= trackY - 3 && mouseY <= trackY + 7) {
                    beginChannelDrag(i, sliderX, sliderWidth, mouseX);
                    return true;
                }
            }
        }

        for (Entry<GuiButton, Runnable> button : buttons.entrySet()) {
            if (button.getKey().mousePressed(minecraft, mouseX, mouseY)) {
                button.getValue().run();
                selected = null;
                return true;
            }
        }
        if (mouseButton == 0 || mouseButton == 1) {
            if (!CompactPreviewLayout.containsViewport(mouseX, mouseY)) return false;
            if (!previewCandidates.isEmpty() && mouseX >= 176 - RIGHT_PADDING - SLOT_SIZE &&
                    mouseY >= CANDIDATE_SLOT_START_Y && mouseY < CANDIDATE_SLOT_START_Y +
                    Math.min(MAX_CANDIDATES, previewCandidates.size()) * SLOT_SIZE) return false;
            pickPreview(mouseX, mouseY, 0, 0, CompactPreviewLayout.WIDTH, CompactPreviewLayout.HEIGHT);
            if (renderer.getLastTraceResult() == null) {
                boolean hadSelection = selected != null;
                clearSelection();
                return hadSelection;
            }
            BlockPos target = renderer.getLastTraceResult().getBlockPos();
            if (!Objects.equals(selected, target)) {
                clearPreviewSelection();
                selectBlock(target);
                setItemStackGroup();
            }
            return true;
        }
        return false;
    }

    private void setItemStackGroup() {
        if (previewCandidates.isEmpty())
            return;
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IDrawable border = recipeLayout.getRecipeCategory().getBackground();
        int recipeWidth = border.getWidth();
        
        // First, clear all old candidate slots (CANDIDATE_SLOT_BASE to CANDIDATE_SLOT_BASE + MAX_CANDIDATES)
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            try {
                itemStackGroup.set(i + CANDIDATE_SLOT_BASE, ItemStack.EMPTY);
            } catch (Exception e) {
                // Ignore if slot is not initialized yet
            }
        }
        
        // Place candidate slots on the right side (no overlap with left parts panel)
        int count = Math.min(previewCandidates.size(), MAX_CANDIDATES);
        for (int i = 0; i < count; i++) {
            int col = i / CANDIDATES_PER_COL;
            int row = i % CANDIDATES_PER_COL;
            int slotX = recipeWidth - RIGHT_PADDING - (col + 1) * SLOT_SIZE;
            int slotY = row * SLOT_SIZE + CANDIDATE_SLOT_START_Y;
            itemStackGroup.init(i + CANDIDATE_SLOT_BASE, true, slotX, slotY);
            PreviewCandidate candidate = previewCandidates.get(i);
            if (candidate != null && !candidate.getItemCandidates().isEmpty()) {
                itemStackGroup.set(i + CANDIDATE_SLOT_BASE, candidate.getItemCandidates());
            } else {
                itemStackGroup.set(i + CANDIDATE_SLOT_BASE, ItemStack.EMPTY);
            }
        }

        // Add tooltip callback with safety checks
        itemStackGroup.addTooltipCallback((slotIndex, input, itemStack, tooltip) -> {
            if (slotIndex >= CANDIDATE_SLOT_BASE && slotIndex < CANDIDATE_SLOT_BASE + Math.min(MAX_CANDIDATES, previewCandidates.size())) {
                int predIndex = slotIndex - CANDIDATE_SLOT_BASE;
                if (predIndex >= 0 && predIndex < previewCandidates.size()) {
                    tooltip.addAll(previewCandidates.get(predIndex).getTooltip());
                }
            }
        });
    }

    @NotNull
    private List<String> previewTooltipFor(@NotNull BlockPos pos) {
        StructureElementPreviewEntry entry = patterns[0].getPreviewEntry(pos);
        if (entry != null && !entry.getTooltip().isEmpty()) {
            return localizeTooltip(entry.getTooltip());
        }
        if (entry == null) {
            logMissingTypedPreview(pos, "tooltip");
        }
        return Collections.emptyList();
    }

    /**
     * Typed preview metadata stores translation keys, so they have to be resolved before display.
     */
    @NotNull
    private static List<String> localizeTooltip(@NotNull List<String> tooltip) {
        List<String> localized = new ArrayList<>(tooltip.size());
        for (String line : tooltip) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            localized.add(localizeTooltipLine(line));
        }
        return localized;
    }

    /**
     * A preview tooltip line is either a bare translation key or a key followed by space-separated arguments, as
     * produced by {@link gregtech.api.pattern.element.IStructureElement#addTooltip(String, Object...)}. Formatting the
     * whole line as a key left those arguments glued into it and surfaced the raw key in the tooltip, so split the line
     * first and let the translation fill its placeholders.
     */
    @NotNull
    private static String localizeTooltipLine(@NotNull String line) {
        int separator = line.indexOf(' ');
        String key = separator < 0 ? line : line.substring(0, separator);
        if (!I18n.hasKey(key)) {
            // Already localized text, or a line without a translation: leave it untouched.
            return line;
        }
        if (separator < 0) {
            return I18n.format(key);
        }
        return I18n.format(key, parseTooltipArguments(line.substring(separator + 1)));
    }

    /**
     * The arguments of an {@code addTooltip(key, args...)} line are written as text, but keys may still use
     * {@code %d}, so whole-number tokens are restored to integers before formatting.
     */
    @NotNull
    private static Object[] parseTooltipArguments(@NotNull String arguments) {
        String[] tokens = arguments.split(" ");
        Object[] parsed = new Object[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                parsed[i] = Integer.valueOf(tokens[i]);
            } catch (NumberFormatException ignored) {
                parsed[i] = tokens[i];
            }
        }
        return parsed;
    }

    @NotNull
    private List<PreviewCandidate> loadPreviewCandidates(@NotNull BlockPos pos) {
        StructureElementPreviewEntry entry = patterns[0].getPreviewEntry(pos);
        if (entry != null && !entry.getPreview().isEmpty()) {
            List<PreviewCandidate> candidates = previewCandidatesFromEntry(entry);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        logMissingTypedPreview(pos, "candidate");
        return Collections.emptyList();
    }

    private void logMissingTypedPreview(@NotNull BlockPos pos, @NotNull String surface) {
        String key = controller.metaTileEntityId + "|" + surface + "|" + pos + "|"
                + new TreeMap<>(channelValues);
        if (MISSING_TYPED_PREVIEW_DIAGNOSTICS.add(key)) {
            GTLog.logger.debug(
                    "Missing typed JEI multiblock preview {} for {} at preview position {} with channels {}. "
                            + "Add StructureElementPreviewEntry metadata.",
                    surface, controller.metaTileEntityId, pos, channelValues);
        }
    }

    @NotNull
    private static List<PreviewCandidate> previewCandidatesFromEntry(@NotNull StructureElementPreviewEntry entry) {
        List<PreviewCandidate> candidates = new ArrayList<>();
        StructureElementPreview preview = entry.getPreview();
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            PreviewCandidate candidate = PreviewCandidate.fromGroup(entry, group);
            if (candidate.hasCandidates()) {
                candidates.add(candidate);
            }
        }
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            PreviewCandidate candidate = PreviewCandidate.fromGroup(entry, group);
            if (candidate.hasCandidates()) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    @NotNull
    private static List<ItemStack> itemCandidatesFrom(@NotNull BlockInfo[] infos) {
        List<ItemStack> result = new ArrayList<>();
        for (BlockInfo info : infos) {
            if (info == null || info.getBlockState().getBlock() == net.minecraft.init.Blocks.AIR) {
                continue;
            }
            IBlockState blockState = info.getBlockState();
            MetaTileEntity metaTileEntity = info.getTileEntity() instanceof IGregTechTileEntity
                    ? ((IGregTechTileEntity) info.getTileEntity()).getMetaTileEntity()
                    : null;
            if (metaTileEntity != null) {
                result.add(metaTileEntity.getStackForm());
            } else {
                result.add(new ItemStack(Item.getItemFromBlock(blockState.getBlock()), 1,
                        blockState.getBlock().damageDropped(blockState)));
            }
        }
        return result;
    }

    @NotNull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (!hasLivePreview()) {
            return Collections.emptyList();
        }
        // Channel slider tooltips
        if (!supportedChannels.isEmpty()) {
            int sliderStartY = CHANNEL_TOP;
            int sliderX = getSliderX();
            int sliderWidth = getSliderWidth(176);
            for (int i = channelPage * CHANNEL_ROWS; i < Math.min(supportedChannels.size(), (channelPage + 1) * CHANNEL_ROWS); i++) {
                int rowY = sliderStartY + (i - channelPage * CHANNEL_ROWS) * 16;
                int trackY = getSliderTrackY(i, sliderStartY);
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth
                        && mouseY >= rowY && mouseY <= trackY + 7) {
                    StructureChannel channel = supportedChannels.get(i);
                    int min = channelRanges[i][0];
                    int max = channelRanges[i][1];
                    int value = getChannelValue(channel);
                    List<String> tips = new ArrayList<>();
                    tips.add(TextFormatting.WHITE + I18n.format(channel.getDefaultTooltip()));
                    tips.add(TextFormatting.GRAY + I18n.format("gregtech.multiblock.preview.channel_range",
                            min, max));
                    if (value > 0) {
                        ItemStack indicator = channel.getIndicatorItem(value);
                        if (!indicator.isEmpty()) {
                            tips.add(TextFormatting.AQUA + I18n.format("gregtech.multiblock.preview.channel_current",
                                    indicator.getDisplayName()));
                        }
                    } else {
                        tips.add(TextFormatting.YELLOW + I18n.format("gregtech.multiblock.preview.channel_auto"));
                    }
                    tips.add(TextFormatting.DARK_GRAY + I18n.format("gregtech.multiblock.preview.channel_click"));
                    // Advanced tooltip: show raw channel key for debugging/porting
                    if (Minecraft.getMinecraft().gameSettings.advancedItemTooltips) {
                        tips.add(TextFormatting.DARK_GRAY + "Key: " + channel.getName());
                    }
                    return tips;
                }
            }
        }

        if (drawInfoIcon) {
            return Arrays.asList(
                    I18n.format("gregtech.multiblock.preview.zoom"),
                    I18n.format("gregtech.multiblock.preview.rotate"),
                    I18n.format("gregtech.multiblock.preview.select")
            );
        } else if (tooltipBlockStack != null && !tooltipBlockStack.isEmpty() && !Mouse.isButtonDown(0)) {
            Minecraft minecraft = Minecraft.getMinecraft();
            ITooltipFlag flag = minecraft.gameSettings.advancedItemTooltips ? TooltipFlags.ADVANCED :
                    TooltipFlags.NORMAL;
            List<String> tooltip = tooltipBlockStack.getTooltip(minecraft.player, flag);
            EnumRarity rarity = tooltipBlockStack.getRarity();
            for (int k = 0; k < tooltip.size(); ++k) {
                if (k == 0) {
                    tooltip.set(k, rarity.color + tooltip.get(k));
                } else {
                    tooltip.set(k, TextFormatting.GRAY + tooltip.get(k));
                }
            }
            if (previewTips != null) {
                tooltip.addAll(previewTips);
            }
            return tooltip;
        }
        return Collections.emptyList();
    }

    private void logTypedPreviewEntrySource(@Nullable MultiblockControllerBase previewController,
                                            int blockCount,
                                            int sourceEntryCount,
                                            int retainedEntryCount) {
        String previewControllerId = previewController == null
                ? "none"
                : previewController.metaTileEntityId.toString();
        String key = controller.metaTileEntityId + "|" + previewControllerId + "|"
                + new TreeMap<>(channelValues);
        if (TYPED_PREVIEW_ENTRY_DIAGNOSTICS.add(key)) {
            GTLog.logger.debug("[JEIMultiblockPreview] typed preview entries controller={} " +
                            "previewController={} pieces={} channels={} blocks={} sourceEntries={} " +
                            "retainedEntries={}",
                    controller.metaTileEntityId, previewControllerId,
                    controller.getStructureDefinition().getCompiledPattern().getPieceCount(),
                    new TreeMap<>(channelValues), blockCount, sourceEntryCount, retainedEntryCount);
        }
    }

    private enum PreviewLoadStage {
        ASSEMBLING("gregtech.multiblock.preview.loading_assemble"),
        POPULATING_WORLD("gregtech.multiblock.preview.loading_world"),
        COLLECTING_PARTS("gregtech.multiblock.preview.loading_parts"),
        UPLOADING_MESH("gregtech.multiblock.preview.loading_mesh"),
        COMPLETE("gregtech.multiblock.preview.loading_complete");

        @NotNull
        private final String translationKey;

        PreviewLoadStage(@NotNull String translationKey) {
            this.translationKey = translationKey;
        }
    }

    /**
     * JEI's render callback is the only safe place to build this preview: blocks, MetaTileEntities and OpenGL buffers
     * are all client-thread objects. Each stage is intentionally bounded so the loading indicator can be redrawn.
     */
    private final class PreviewLoadTask {

        @NotNull
        private final MultiPiecePreviewAssembler.IncrementalPreview previewBuild;
        private final long startedNanos = System.nanoTime();
        @NotNull
        private PreviewLoadStage stage = PreviewLoadStage.ASSEMBLING;
        @Nullable
        private Map<BlockPos, BlockInfo> blockMap;
        @Nullable
        private Map<BlockPos, StructureElementPreviewEntry> previewEntries;
        @Nullable
        private Iterator<Entry<BlockPos, BlockInfo>> worldBlocks;
        @Nullable
        private Iterator<Entry<BlockPos, BlockInfo>> partBlocks;
        @Nullable
        private TrackedDummyWorld world;
        @Nullable
        private StructurePreviewRenderer renderer;
        @Nullable
        private MultiblockControllerBase previewController;
        @NotNull
        private final Map<ItemStack, PartInfo> partsMap = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        private int populatedBlocks;
        private int collectedParts;
        @Nullable
        private MBPattern pattern;

        private PreviewLoadTask(@NotNull MultiPiecePreviewAssembler.IncrementalPreview previewBuild) {
            this.previewBuild = previewBuild;
        }

        private void advance(long deadlineNanos) {
            while (System.nanoTime() < deadlineNanos && !isComplete()) {
                switch (stage) {
                    case ASSEMBLING -> advanceAssembly();
                    case POPULATING_WORLD -> advanceWorld(deadlineNanos);
                    case COLLECTING_PARTS -> advanceParts(deadlineNanos);
                    case UPLOADING_MESH -> advanceMesh();
                    case COMPLETE -> { return; }
                }
                if (stage == PreviewLoadStage.ASSEMBLING || stage == PreviewLoadStage.UPLOADING_MESH) {
                    // One bounded assembly/mesh batch per frame keeps worst-case render work predictable.
                    return;
                }
            }
        }

        private void advanceAssembly() {
            previewBuild.advance(PREVIEW_ASSEMBLY_BATCH_SIZE);
            if (!previewBuild.isComplete()) {
                return;
            }

            blockMap = new HashMap<>(previewBuild.getBlocks());
            previewEntries = new HashMap<>(previewBuild.getPreviewEntries());
            previewController = correctPreviewController(blockMap);
            world = new TrackedDummyWorld();
            renderer = createPreviewRenderer(world);
            renderer.setClearColor(ConfigHolder.client.multiblockPreviewColor);
            worldBlocks = blockMap.entrySet().iterator();
            transitionTo(PreviewLoadStage.POPULATING_WORLD);
        }

        private void advanceWorld(long deadlineNanos) {
            if (worldBlocks == null || world == null) {
                throw new IllegalStateException("Preview world was not initialized");
            }
            int batch = 0;
            while (worldBlocks.hasNext() && batch < PREVIEW_WORLD_BATCH_SIZE && System.nanoTime() < deadlineNanos) {
                Entry<BlockPos, BlockInfo> entry = worldBlocks.next();
                world.addBlock(entry.getKey(), entry.getValue());
                populatedBlocks++;
                batch++;
            }
            if (worldBlocks.hasNext()) {
                return;
            }

            configureRenderer();
            if (previewEntries == null || blockMap == null) {
                throw new IllegalStateException("Preview metadata was not initialized");
            }
            logTypedPreviewEntrySource(previewController, blockMap.size(), previewEntries.size(), previewEntries.size());
            partBlocks = blockMap.entrySet().iterator();
            transitionTo(PreviewLoadStage.COLLECTING_PARTS);
        }

        private void configureRenderer() {
            if (world == null || renderer == null) {
                throw new IllegalStateException("Preview renderer was not initialized");
            }
            int totalBlocks = world.renderedBlocks.size();
            if (totalBlocks > 50) {
                renderer.setCullInternalBlocks(true);
            }
            renderer.addRenderedBlocks(world.renderedBlocks);

            if (renderer.renderedBlocks.size() > 100) {
                renderer.setTileEntityFilter(te ->
                        te instanceof IGregTechTileEntity gtte &&
                                gtte.getMetaTileEntity() instanceof MultiblockControllerBase);
            } else if (renderer.renderedBlocks.size() > 50) {
                renderer.setMaxTileEntityRenderers(8);
                renderer.setMaxTileEntityRenderDistance(16.0);
            }

            renderer.setAfterWorldRender(ignored -> {
                BlockPos look = renderer.getLastTraceResult() == null ? null :
                        renderer.getLastTraceResult().getBlockPos();
                if (look != null && look.equals(selected)) {
                    renderBlockOverLay(selected, 200, 75, 75);
                } else {
                    renderBlockOverLay(look, 150, 150, 150);
                    renderBlockOverLay(selected, 255, 0, 0);
                }
                if (selected != null && !previewCandidates.isEmpty()) {
                    renderCandidateBlockAtPosition(world, selected);
                }
            });
            world.updateEntities();
            world.setRenderFilter(renderer.renderedBlocks::contains);
        }

        private void advanceParts(long deadlineNanos) {
            if (partBlocks == null || world == null) {
                throw new IllegalStateException("Preview part collection was not initialized");
            }
            int batch = 0;
            while (partBlocks.hasNext() && batch < PREVIEW_PARTS_BATCH_SIZE && System.nanoTime() < deadlineNanos) {
                Entry<BlockPos, BlockInfo> entry = partBlocks.next();
                collectStructureBlock(world, entry.getKey(), entry.getValue(), partsMap);
                collectedParts++;
                batch++;
            }
            if (partBlocks.hasNext()) {
                return;
            }
            transitionTo(PreviewLoadStage.UPLOADING_MESH);
        }

        private void advanceMesh() {
            if (renderer == null || world == null || previewEntries == null) {
                throw new IllegalStateException("Preview mesh upload was not initialized");
            }
            if (!renderer.uploadVBOChunk(PREVIEW_VBO_BATCH_SIZE)) {
                return;
            }

            List<ItemStack> sortedParts = partsMap.values().stream()
                    .sorted((one, two) -> {
                        if (one.isController) return -1;
                        if (two.isController) return +1;
                        if (one.isTile && !two.isTile) return -1;
                        if (two.isTile && !one.isTile) return +1;
                        if (one.blockId != two.blockId) return two.blockId - one.blockId;
                        return two.amount - one.amount;
                    })
                    .map(PartInfo::getItemStack)
                    .collect(Collectors.toList());
            pattern = new MBPattern(renderer, sortedParts, previewEntries);
            transitionTo(PreviewLoadStage.COMPLETE);
        }

        private void transitionTo(@NotNull PreviewLoadStage newStage) {
            stage = newStage;
            GTLog.logger.debug("[JEIMultiblockPreview] loading stage controller={} stage={} blocks={} parts={} ms={}",
                    controller.metaTileEntityId, stage, populatedBlocks, collectedParts, getElapsedMillis());
        }

        private boolean isComplete() {
            return stage == PreviewLoadStage.COMPLETE && pattern != null;
        }

        @NotNull
        private MBPattern takePattern() {
            if (pattern == null) {
                throw new IllegalStateException("Preview loading task has not completed");
            }
            return pattern;
        }

        private int getBlockCount() {
            return blockMap == null ? 0 : blockMap.size();
        }

        private long getElapsedMillis() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }

        @NotNull
        private String getStageTranslationKey() {
            return stage.translationKey;
        }

        private float getProgress() {
            return switch (stage) {
                case ASSEMBLING -> 0.55F * previewBuild.getProgress();
                case POPULATING_WORLD -> 0.55F + 0.20F * fraction(populatedBlocks, getBlockCount());
                case COLLECTING_PARTS -> 0.75F + 0.12F * fraction(collectedParts, getBlockCount());
                case UPLOADING_MESH -> 0.87F + 0.12F * (renderer == null ? 0.0F : renderer.getVBOUploadProgress());
                case COMPLETE -> 1.0F;
            };
        }

        private static float fraction(int completed, int total) {
            return total == 0 ? 1.0F : Math.min(1.0F, (float) completed / total);
        }

        private void dispose() {
            if (pattern == null && renderer != null) {
                renderer.dispose();
            }
        }
    }

    /**
     * One typed alternative group of a selected preview element: the candidate blocks, their item forms and
     * the tooltip lines describing them.
     */
    public static final class PreviewCandidate {

        @NotNull
        private final BlockInfo[] blockCandidates;
        @NotNull
        private final List<ItemStack> itemCandidates;
        @NotNull
        private final List<String> tooltip;
        @NotNull
        private final String displayName;

        private PreviewCandidate(@NotNull BlockInfo[] blockCandidates,
                                 @NotNull List<ItemStack> itemCandidates,
                                 @NotNull List<String> tooltip,
                                 @NotNull String displayName) {
            this.blockCandidates = blockCandidates;
            this.itemCandidates = itemCandidates;
            this.tooltip = tooltip;
            this.displayName = displayName;
        }

        @NotNull
        private static PreviewCandidate fromGroup(@NotNull StructureElementPreviewEntry entry,
                                                  @NotNull StructureElementPreview.CandidateGroup group) {
            BlockInfo[] infos = group.getCandidates();
            List<String> tooltip = new ArrayList<>(localizeTooltip(entry.getTooltip()));
            tooltip.addAll(localizeTooltip(group.getTooltip()));
            return new PreviewCandidate(infos, itemCandidatesFrom(infos), tooltip, displayNameOf(infos));
        }

        /**
         * Resolves a readable label for the group. The item form is preferred because block localization keys
         * are frequently untranslated and would surface as raw {@code unname.*} keys.
         */
        @NotNull
        private static String displayNameOf(@NotNull BlockInfo[] infos) {
            for (BlockInfo info : infos) {
                if (info == null || info.getBlockState().getBlock() == Blocks.AIR) {
                    continue;
                }
                TileEntity tileEntity = info.getTileEntity();
                if (tileEntity instanceof IGregTechTileEntity gregTechTile) {
                    MetaTileEntity metaTileEntity = gregTechTile.getMetaTileEntity();
                    if (metaTileEntity != null && !metaTileEntity.getStackForm().isEmpty()) {
                        return metaTileEntity.getStackForm().getDisplayName();
                    }
                }
                ItemStack stack = GTUtility.toItem(info.getBlockState());
                if (!stack.isEmpty() && !stack.getDisplayName().startsWith("tile.")) {
                    return stack.getDisplayName();
                }
                // Last resort: the block's own localized name, which may fall back to its translation key.
                String blockName = info.getBlockState().getBlock().getLocalizedName();
                return blockName == null ? "" : blockName;
            }
            return "";
        }

        private boolean hasCandidates() {
            return !itemCandidates.isEmpty();
        }

        @NotNull
        public BlockInfo[] getBlockCandidates() {
            return blockCandidates;
        }

        @NotNull
        public List<ItemStack> getItemCandidates() {
            return itemCandidates;
        }

        @NotNull
        public List<String> getTooltip() {
            return tooltip;
        }

        /**
         * @return the label of the alternative this group describes, never a raw translation key.
         */
        @NotNull
        public String getDisplayName() {
            return displayName;
        }
    }

    private static class PartInfo {

        final ItemStack itemStack;
        final int blockId;
        boolean isController = false;
        boolean isTile = false;
        int amount = 0;

        PartInfo(final ItemStack itemStack, final BlockInfo blockInfo) {
            this.itemStack = itemStack;
            this.blockId = Block.getIdFromBlock(blockInfo.getBlockState().getBlock());
            TileEntity tileEntity = blockInfo.getTileEntity();
            if (tileEntity != null) {
                this.isTile = true;
                if (tileEntity instanceof IGregTechTileEntity iGregTechTileEntity) {
                    MetaTileEntity mte = iGregTechTileEntity.getMetaTileEntity();
                    this.isController = mte instanceof MultiblockControllerBase;
                }
            }
        }

        @NotNull
        ItemStack getItemStack() {
            ItemStack result = this.itemStack.copy();
            result.setCount(this.amount);
            return result;
        }
    }
}
