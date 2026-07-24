package gregtech.integration.jei.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MBPattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTLog;
import gregtech.api.util.GregFakePlayer;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.client.renderer.scene.FBOWorldSceneRenderer;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
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

    // Left parts panel layout constants
    private static final int PARTS_COLUMNS = 2;
    private static final int SLOTS_PER_COL = 10; // vertical slots per column on left panel
    private static final int MAX_PARTS = PARTS_COLUMNS * SLOTS_PER_COL;
    private static final int SLOT_SIZE = 18;
    private static final int PARTS_WIDTH = PARTS_COLUMNS * SLOT_SIZE + 4;
    private static final int ICON_SIZE = 20;
    private static final int RIGHT_PADDING = 5;
    private static final int INFO_ICON_Y = 22;
    private static final int LAYER_BUTTON_Y = INFO_ICON_Y + ICON_SIZE + 2;
    private static final int CANDIDATE_SLOT_START_Y = LAYER_BUTTON_Y + ICON_SIZE + 2;
    // Right candidates panel layout constants
    private static final int CANDIDATES_COLUMNS = 1;
    private static final int CANDIDATES_PER_COL = 6;
    private static final int MAX_CANDIDATES = CANDIDATES_COLUMNS * CANDIDATES_PER_COL;
    // Candidate cycling interval in milliseconds
    private static final long CANDIDATE_CYCLE_INTERVAL_MS = 1000L;
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
    private final Map<String, Integer> channelValues = new HashMap<>();
    private List<StructureChannel> supportedChannels = Collections.emptyList();
    private int[][] channelRanges = new int[0][]; // [channelIdx][0=min, 1=max]
    @Nullable
    private MBPattern[] patterns;
    @Nullable
    private PreviewLoadTask previewLoadTask;
    @Nullable
    private String previewLoadFailure;
    private boolean previewMetadataInitialized;
    private boolean ingredientInputsInitialized;
    private boolean previewLayoutInitialized;
    private boolean rendererContainsFullStructure;
    private boolean resetViewWhenPreviewReady = true;
    private int pendingMouseWheel;
    private RecipeLayout recipeLayout;
    private int layerIndex = -1;
    private int lastMouseX;
    private int lastMouseY;
    private Vector3f center;
    private float rotationYaw;
    private float rotationPitch;
    private float zoom;
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
        this.nextLayerButton = new GuiButton(0, 176 - (ICON_SIZE + RIGHT_PADDING), LAYER_BUTTON_Y, ICON_SIZE,
                ICON_SIZE, "");
        this.buttons.put(nextLayerButton, this::toggleNextLayer);
        this.previewCandidates = new ArrayList<>();
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
        this.previewMetadataInitialized = true;
    }

    private void startPreviewLoading() {
        long preparationStart = System.nanoTime();
        releasePreviewPatterns();
        cancelPreviewLoading();
        previewLoadFailure = null;
        previewLayoutInitialized = false;
        rendererContainsFullStructure = false;
        clearParts();
        initializePreviewMetadata();
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
            if (!task.isComplete()) {
                return;
            }

            MBPattern loaded = task.takePattern();
            this.patterns = new MBPattern[] { loaded };
            this.rendererContainsFullStructure = true;
            GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);
            previewLoadTask = null;
            GTLog.logger.debug("[JEIMultiblockPreview] incrementally initialized controller={} channels={} blocks={} ms={}",
                    controller.metaTileEntityId, new TreeMap<>(channelValues), task.getBlockCount(),
                    task.getElapsedMillis());
        } catch (RuntimeException e) {
            task.dispose();
            previewLoadTask = null;
            previewLoadFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            GTLog.logger.error("[JEIMultiblockPreview] failed to incrementally initialize controller={}",
                    controller.metaTileEntityId, e);
        }
    }

    private void releasePreviewResources() {
        selected = null;
        previewCandidates.clear();
        previewTips = null;
        candidateCycleIndex = 0;
        lastCandidateCycleTime = 0L;
        cancelPreviewLoading();
        previewLoadFailure = null;
        previewLayoutInitialized = false;
        rendererContainsFullStructure = false;
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

    @NotNull
    private static Collection<PartInfo> gatherStructureBlocks(World world, @NotNull Map<BlockPos, BlockInfo> blocks) {
        Map<ItemStack, PartInfo> partsMap = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        for (Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            collectStructureBlock(world, entry.getKey(), entry.getValue(), partsMap);
        }
        return partsMap.values();
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
        // Advance candidate cycle based on time
        long now = System.currentTimeMillis();
        if (now - lastCandidateCycleTime >= CANDIDATE_CYCLE_INTERVAL_MS) {
            lastCandidateCycleTime = now;
            candidateCycleIndex++;
        }

        // Collect all candidate BlockInfo from typed preview groups.
        List<BlockInfo> allCandidateBlocks = new ArrayList<>();
        for (PreviewCandidate candidate : previewCandidates) {
            for (BlockInfo info : candidate.getBlockCandidates()) {
                if (info != null && info.getBlockState().getBlock() != net.minecraft.init.Blocks.AIR) {
                    allCandidateBlocks.add(info);
                }
            }
        }
        if (allCandidateBlocks.isEmpty()) return;

        int index = candidateCycleIndex % allCandidateBlocks.size();
        BlockInfo candidateInfo = allCandidateBlocks.get(index);
        IBlockState candidateState = candidateInfo.getBlockState();

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
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                if (!candidateState.getBlock().canRenderInLayer(candidateState, layer)) continue;

                int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
                WorldSceneRenderer.setDefaultPassRenderState(pass);

                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                dispatcher.renderBlock(candidateState, pos, world, buffer);
                Tessellator.getInstance().draw();
            }
        } finally {
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(oldLayer);
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
        this.recipeLayout = layout;
        boolean switchedWrapper = lastWrapper != this;
        if (lastWrapper != null && lastWrapper != this) {
            lastWrapper.releasePreviewResources();
        }

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, SLOT_SIZE, SLOT_SIZE)
                .setTextureSize(SLOT_SIZE, SLOT_SIZE).build();
        this.infoIcon = guiHelper.drawableBuilder(GuiTextures.INFO_ICON.imageLocation, 0, 0, ICON_SIZE, ICON_SIZE)
                .setTextureSize(ICON_SIZE, ICON_SIZE).build();

        IDrawable border = layout.getRecipeCategory().getBackground();
        preparePlaceForParts(border.getHeight());
        pendingMouseWheel = Mouse.getEventDWheel();
        resetViewWhenPreviewReady = pendingMouseWheel == 0 || switchedWrapper;
        this.nextLayerButton.x = border.getWidth() - (ICON_SIZE + RIGHT_PADDING);
        this.nextLayerButton.y = LAYER_BUTTON_Y;
        if (resetViewWhenPreviewReady) {
            selected = null;
            this.previewCandidates.clear();
            lastWrapper = this;
        }
        ensurePreviewLoadStarted();
        configureLoadedPreviewLayout();
    }

    private void configureLoadedPreviewLayout() {
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null || recipeLayout == null) {
            return;
        }
        if (!previewLayoutInitialized || resetViewWhenPreviewReady) {
            if (resetViewWhenPreviewReady) {
                Vector3f size = ((TrackedDummyWorld) renderer.world).getSize();
                float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
                this.zoom = (float) (3.5 * Math.sqrt(max));
                this.rotationYaw = 20.0f;
                this.rotationPitch = getDefaultHorizontalCameraAngle();
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

    @Nullable
    public WorldSceneRenderer getCurrentRenderer() {
        return hasLivePreview() ? patterns[0].getSceneRenderer() : null;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    private void toggleNextLayer() {
        WorldSceneRenderer renderer = getCurrentRenderer();
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
        WorldSceneRenderer renderer = getCurrentRenderer();
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

    private void resetCenter(TrackedDummyWorld world, WorldSceneRenderer renderer) {
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
        WorldSceneRenderer renderer = getCurrentRenderer();
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
        this.selected = null;
        this.candidateCycleIndex = 0;
        this.lastCandidateCycleTime = 0L;
        if (recipeLayout != null) {
            IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
            for (int i = 0; i < MAX_CANDIDATES; i++) {
                try {
                    itemStackGroup.set(i + MAX_PARTS, ItemStack.EMPTY);
                } catch (Exception ignored) {
                    // Candidate slots are created lazily; ignore slots that do not exist yet.
                }
            }
        }
        previewCandidates.clear();
        previewTips = null;
    }

    private void preparePlaceForParts(int recipeHeight) {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        // Place item slots on the left panel: 2 columns, up to SLOTS_PER_COL rows each
        for (int i = 0; i < MAX_PARTS; ++i) {
            int col = i / SLOTS_PER_COL;
            int row = i % SLOTS_PER_COL;
            itemStackGroup.init(i, true,
                    col * SLOT_SIZE + 1,
                    row * SLOT_SIZE + 2);
        }
    }

    private void updateParts() {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        List<ItemStack> parts = this.patterns[0].getParts();
        int limit = Math.min(parts.size(), MAX_PARTS);
        for (int i = 0; i < limit; ++i) {
            itemStackGroup.set(i, parts.get(i));
        }
        for (int i = parts.size(); i < MAX_PARTS; ++i) {
            itemStackGroup.set(i, (ItemStack) null);
        }
    }

    private void clearParts() {
        if (recipeLayout == null) {
            return;
        }
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        for (int i = 0; i < MAX_PARTS; i++) {
            itemStackGroup.set(i, (ItemStack) null);
        }
    }
    @Override
    public void drawInfo(@NotNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        ensurePreviewLoadStarted();
        advancePreviewLoading();
        if (!hasLivePreview()) {
            drawPreviewLoading(minecraft, recipeWidth, recipeHeight);
            tooltipBlockStack = null;
            this.previewTips = null;
            lastRender = System.currentTimeMillis();
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return;
        }
        configureLoadedPreviewLayout();
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            drawPreviewLoading(minecraft, recipeWidth, recipeHeight);
            return;
        }
        // Full-screen 3D scene (GT5 style: scene covers entire area, UI overlaid on top)
        int sceneX = 0;
        int sceneWidth = recipeWidth;
        int sceneHeight = recipeHeight - (supportedChannels.size() * 16 + 10); // leave room for sliders

        // Render 3D scene full-screen (OpenGL state safety)
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableLighting();
            RenderHelper.enableStandardItemLighting();
            renderer.render(sceneX, 0, sceneWidth, sceneHeight, mouseX, mouseY);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }

        // Draw multiblock name and tier info (overlaid on 3D scene)
        drawMultiblockName(recipeWidth);
        drawMultiblockTier(recipeWidth);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw info icon (top-right corner)
        int iconX = recipeWidth - (ICON_SIZE + RIGHT_PADDING);
        int iconY = INFO_ICON_Y;
        this.infoIcon.draw(minecraft, iconX, iconY);
        this.drawInfoIcon = mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                mouseY >= iconY && mouseY <= iconY + ICON_SIZE;

        // Draw left-side parts slots (only for actual items, no empty slots to avoid blocking 3D)
        int actualPartsCount = Math.min(this.patterns[0].getParts().size(), MAX_PARTS);
        for (int i = 0; i < actualPartsCount; ++i) {
            int col = i / SLOTS_PER_COL;
            int row = i % SLOTS_PER_COL;
            int slotX = col * SLOT_SIZE + 1;
            int slotY = row * SLOT_SIZE + 2;
            this.slot.draw(minecraft, slotX, slotY);
        }

        // Draw right-side candidate slots (overlaid on 3D scene, no overlap with left panel)
        for (int i = 0; i < previewCandidates.size() && i < MAX_CANDIDATES; i++) {
            int col = i / CANDIDATES_PER_COL;
            int row = i % CANDIDATES_PER_COL;
            int slotX = recipeWidth - RIGHT_PADDING - (col + 1) * SLOT_SIZE;
            int slotY = row * SLOT_SIZE + CANDIDATE_SLOT_START_Y;
            this.slot.draw(minecraft, slotX, slotY);
        }

        for (GuiButton button : buttons.keySet()) {
            button.drawButton(minecraft, mouseX, mouseY, 0.0f);
        }

        boolean isMouseOverButton = false;
        for (GuiButton button : buttons.keySet()) {
            if (mouseX >= button.x && mouseX <= button.x + button.width &&
                    mouseY >= button.y && mouseY <= button.y + button.height) {
                isMouseOverButton = true;
                break;
            }
        }
        boolean insideView = mouseX >= sceneX && mouseY >= 0 &&
                mouseX < recipeWidth && mouseY < sceneHeight &&
                !isMouseOverButton;

        boolean leftClickHeld = Mouse.isButtonDown(0);
        boolean rightClickHeld = Mouse.isButtonDown(1);
        boolean cameraModified = false;

        if (insideView && rightClickHeld) {
            float deltaX = mouseX - lastMouseX;
            float deltaY = mouseY - lastMouseY;
            if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
                final float panSensitivity = 0.08f;

                double yawRad = Math.toRadians(rotationPitch);   // rotationPitch = yaw
                double pitchRad = Math.toRadians(rotationYaw);   // rotationYaw = pitch

                Vec3d forward = new Vec3d(
                        Math.cos(pitchRad) * Math.sin(yawRad),
                        Math.sin(pitchRad),
                        Math.cos(pitchRad) * Math.cos(yawRad)
                ).normalize();

                Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
                if (right.lengthSquared() < 1e-6) {
                    right = new Vec3d(1.0, 0.0, 0.0);
                }

                Vec3d up = right.crossProduct(forward).normalize();

                center.x += (float)(-deltaX * right.x * panSensitivity);
                center.y += (float)(-deltaX * right.y * panSensitivity);
                center.z += (float)(-deltaX * right.z * panSensitivity);

                center.x += (float)(-deltaY * up.x * panSensitivity);
                center.y += (float)(-deltaY * up.y * panSensitivity);
                center.z += (float)(-deltaY * up.z * panSensitivity);

                cameraModified = true;
            }
        }
        else if (insideView && leftClickHeld) {
            rotationPitch += (mouseX - lastMouseX);
            rotationPitch %= 360.0f;
            if (rotationPitch < 0) rotationPitch += 360.0f;

            rotationYaw = (float) MathHelper.clamp(
                    rotationYaw + (mouseY - lastMouseY),
                    -89.9f, 89.9f
            );
            cameraModified = true;
        }
        // 更新相机
        if (cameraModified) {
            renderer.setCameraLookAt(
                    center,
                    zoom,
                    Math.toRadians(rotationPitch), // yaw
                    Math.toRadians(rotationYaw)    // pitch
            );
        }

        // 悬停检测（仅当未拖拽时）
        tooltipBlockStack = null;
        this.previewTips = null;
        RayTraceResult rayTraceResult = renderer.getLastTraceResult();

        if (!(leftClickHeld || rightClickHeld) && insideView && rayTraceResult != null &&
                !renderer.world.isAirBlock(rayTraceResult.getBlockPos())) {

            IBlockState blockState = renderer.world.getBlockState(rayTraceResult.getBlockPos());
            ItemStack itemStack = blockState.getBlock().getPickBlock(
                    blockState, rayTraceResult, renderer.world,
                    rayTraceResult.getBlockPos(), minecraft.player
            );

            this.previewTips = previewTooltipFor(rayTraceResult.getBlockPos());
            if (!itemStack.isEmpty()) {
                tooltipBlockStack = itemStack;
            }
        }

        lastRender = System.currentTimeMillis();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

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
        List<String> lines = fontRenderer.listFormattedStringToWidth(localizedName, recipeWidth - 10);
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(lines.get(i), (recipeWidth - fontRenderer.getStringWidth(lines.get(i))) / 2,
                    fontRenderer.FONT_HEIGHT * i, ConfigHolder.client.multiblockPreviewFontColor);
        }
    }

    private void drawMultiblockTier(int recipeWidth) {
        if (supportedChannels.isEmpty()) return;
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        // Channel sliders are drawn below the 3D scene, in the right area
        int sliderStartY = 184 - (supportedChannels.size() * 16 + 6);
        int sliderWidth = recipeWidth - PARTS_WIDTH - 10;
        int sliderX = PARTS_WIDTH + 5;

        for (int i = 0; i < supportedChannels.size(); i++) {
            StructureChannel channel = supportedChannels.get(i);
            String channelName = channel.getName();
            int min = channelRanges[i][0];
            int max = channelRanges[i][1];
            int value = channelValues.getOrDefault(channelName, 0);
            int rowY = sliderStartY + i * 16;

            // Draw channel label (localized)
            String label = I18n.format(channel.getDefaultTooltip());
            fontRenderer.drawString(label, sliderX, rowY, 0x404040);

            // Draw slider track
            int trackX = sliderX;
            int trackY = rowY + fontRenderer.FONT_HEIGHT + 1;
            int trackHeight = 4;
            drawRect(trackX, trackY, trackX + sliderWidth, trackY + trackHeight, 0xFFAAAAAA);

            // Draw slider handle
            int stepCount = getSliderStepCount(min, max);
            int sliderIndex = channelValueToSliderIndex(value, min, max);
            float ratio = stepCount == 0 ? 0.0F : (float) sliderIndex / stepCount;
            int handleX = trackX + (int) (ratio * (sliderWidth - 4));
            drawRect(handleX, trackY - 1, handleX + 4, trackY + trackHeight + 1, 0xFF4488CC);

            // Draw value text (right-aligned)
            String valueText = value == 0 ? "Auto" : String.valueOf(value);
            ItemStack indicator = channel.getIndicatorItem(value);
            if (!indicator.isEmpty() && value > 0) {
                valueText = indicator.getDisplayName();
            }
            fontRenderer.drawString(valueText, sliderX + sliderWidth - fontRenderer.getStringWidth(valueText),
                    rowY, 0x222222);
        }
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }

    private int getSliderTrackY(int channelIdx, int sliderStartY) {
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        return sliderStartY + channelIdx * 16 + fontRenderer.FONT_HEIGHT + 1;
    }

    private int getSliderX() {
        return PARTS_WIDTH + 5;
    }

    private int getSliderWidth(int recipeWidth) {
        return recipeWidth - PARTS_WIDTH - 10;
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
        WorldSceneRenderer renderer = getCurrentRenderer();
        if (renderer == null) {
            return false;
        }
        // Handle channel slider clicks
        if (mouseButton == 0 && !supportedChannels.isEmpty()) {
            int sliderStartY = 184 - (supportedChannels.size() * 16 + 6);
            int sliderX = getSliderX();
            int sliderWidth = getSliderWidth(176);
            for (int i = 0; i < supportedChannels.size(); i++) {
                int trackY = getSliderTrackY(i, sliderStartY);
                // Click area: track region with some vertical tolerance
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth
                        && mouseY >= trackY - 3 && mouseY <= trackY + 7) {
                    int min = channelRanges[i][0];
                    int max = channelRanges[i][1];
                    float ratio = (float) (mouseX - sliderX) / sliderWidth;
                    int newIndex = Math.round(ratio * getSliderStepCount(min, max));
                    int newValue = sliderIndexToChannelValue(newIndex, min, max);
                    setChannelValue(i, newValue);
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
        if (mouseButton == 1) {
            if (renderer.getLastTraceResult() == null) {
                if (this.selected != null) {
                    this.selected = null;
                    // Clear predicates without directly accessing item stacks
                    // JEI will handle clearing the slots when they are re-initialized
                    previewCandidates.clear();
                    this.candidateCycleIndex = 0;
                    this.lastCandidateCycleTime = 0L;
                    return true;
                }
                return false;
            }
            BlockPos selected = renderer.getLastTraceResult().getBlockPos();
            if (!Objects.equals(this.selected, selected)) {
                // Clear old predicates without accessing item stacks directly
                // The item stacks will be properly cleared in setItemStackGroup when new slots are initialized
                previewCandidates.clear();
                this.selected = selected;
                // Reset candidate cycling state for 3D in-place preview
                this.candidateCycleIndex = 0;
                this.lastCandidateCycleTime = 0L;
                previewCandidates.addAll(loadPreviewCandidates(this.selected));
                // Only call setItemStackGroup if we have valid candidates
                if (!previewCandidates.isEmpty()) {
                    setItemStackGroup();
                }
                // Mark FBO dirty so scene re-renders with candidate block cycling
                if (renderer instanceof FBOWorldSceneRenderer fboRenderer) {
                    fboRenderer.markFBODirty();
                }
                return true;
            }
        }
        return false;
    }

    private void setItemStackGroup() {
        if (previewCandidates.isEmpty())
            return;
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IDrawable border = recipeLayout.getRecipeCategory().getBackground();
        int recipeWidth = border.getWidth();
        
        // First, clear all old candidate slots (MAX_PARTS to MAX_PARTS + MAX_CANDIDATES)
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            try {
                itemStackGroup.set(i + MAX_PARTS, ItemStack.EMPTY);
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
            itemStackGroup.init(i + MAX_PARTS, true, slotX, slotY);
            PreviewCandidate candidate = previewCandidates.get(i);
            if (candidate != null && !candidate.getItemCandidates().isEmpty()) {
                itemStackGroup.set(i + MAX_PARTS, candidate.getItemCandidates());
            } else {
                itemStackGroup.set(i + MAX_PARTS, ItemStack.EMPTY);
            }
        }

        // Add tooltip callback with safety checks
        itemStackGroup.addTooltipCallback((slotIndex, input, itemStack, tooltip) -> {
            if (slotIndex >= MAX_PARTS && slotIndex < MAX_PARTS + previewCandidates.size()) {
                int predIndex = slotIndex - MAX_PARTS;
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
            return entry.getTooltip();
        }
        if (entry == null) {
            logMissingTypedPreview(pos, "tooltip");
        }
        return Collections.emptyList();
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
            int sliderStartY = 184 - (supportedChannels.size() * 16 + 6);
            int sliderX = getSliderX();
            int sliderWidth = getSliderWidth(176);
            for (int i = 0; i < supportedChannels.size(); i++) {
                int rowY = sliderStartY + i * 16;
                int trackY = getSliderTrackY(i, sliderStartY);
                if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth
                        && mouseY >= rowY && mouseY <= trackY + 7) {
                    StructureChannel channel = supportedChannels.get(i);
                    int min = channelRanges[i][0];
                    int max = channelRanges[i][1];
                    int value = channelValues.getOrDefault(channel.getName(), 0);
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

    @SuppressWarnings("NewExpressionSideOnly")
    @NotNull
    private MBPattern initializePattern(@NotNull MultiblockShapeInfo shapeInfo) {
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        MultiblockControllerBase controllerBase = null;
        BlockPos controllerBlockPos = null;
        MultiblockControllerBase controllerClassFallback = null;
        BlockPos controllerClassFallbackPos = null;
        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    if (column[z].getTileEntity() instanceof IGregTechTileEntity &&
                            ((IGregTechTileEntity) column[z].getTileEntity())
                                    .getMetaTileEntity() instanceof MultiblockControllerBase) {
                        MultiblockControllerBase previewController =
                                (MultiblockControllerBase) ((IGregTechTileEntity) column[z].getTileEntity())
                                        .getMetaTileEntity();
                        BlockPos pos = new BlockPos(x, y, z);
                        if (controllerBlockPos == null &&
                                controller.metaTileEntityId.equals(previewController.metaTileEntityId)) {
                            controllerBase = previewController;
                            controllerBlockPos = pos;
                        } else if (controllerClassFallbackPos == null &&
                                controller.getClass().isInstance(previewController)) {
                            controllerClassFallback = previewController;
                            controllerClassFallbackPos = pos;
                        }
                    }
                    blockMap.put(new BlockPos(x, y, z), column[z]);
                }
            }
        }
        if (controllerBlockPos == null) {
            controllerBase = controllerClassFallback;
            controllerBlockPos = controllerClassFallbackPos;
        }

        // When using selfPredicateByClass, the preview's controller block comes from the
        // first matching candidate in the registry, which may not be the actual controller
        // for this JEI recipe entry. Replace it with the correct controller instance.
        if (controllerBlockPos != null && controllerBase != null) {
            if (!controller.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
                // Non-parametric controllers with selfPredicateByClass: different MTE IDs
                // sharing the same class (e.g. LargeMiner, LargeBoiler, LargeTurbine)
                replaceControllerInPreview(blockMap, controllerBlockPos, controllerBase);
            }
        }

        TrackedDummyWorld world = new TrackedDummyWorld();
        // FBO renderer: offscreen rendering with dirty-flag caching
        // Resolution dynamically based on typical JEI preview area (~200x200 scaled pixels → 400x400 native)
        FBOWorldSceneRenderer worldSceneRenderer = new FBOWorldSceneRenderer(world, 512, 512);

        worldSceneRenderer.setClearColor(ConfigHolder.client.multiblockPreviewColor);
        world.addBlocks(blockMap);

        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);

        // Internal block culling: remove fully-enclosed blocks for medium/large structures
        int totalBlocks = world.renderedBlocks.size();
        if (totalBlocks > 50) {
            worldSceneRenderer.setCullInternalBlocks(true);
        }

        worldSceneRenderer.addRenderedBlocks(world.renderedBlocks);
        worldSceneRenderer.setOnLookingAt(ray -> {});

        // TESR optimization: limit tile entity rendering for large structures
        int blockCount = worldSceneRenderer.renderedBlocks.size();
        if (blockCount > 100) {
            // Large structures: only render controller TESR, skip all others
            worldSceneRenderer.setTileEntityFilter(te ->
                    te instanceof IGregTechTileEntity gtte &&
                            gtte.getMetaTileEntity() instanceof MultiblockControllerBase);
            worldSceneRenderer.setHitTestInterval(5);
        } else if (blockCount > 50) {
            // Medium structures: cap TESR count and add distance culling
            worldSceneRenderer.setMaxTileEntityRenderers(8);
            worldSceneRenderer.setMaxTileEntityRenderDistance(16.0);
            worldSceneRenderer.setHitTestInterval(3);
        }
        // Small structures (<= 50 blocks): no limits

        worldSceneRenderer.setAfterWorldRender(renderer -> {
            BlockPos look = worldSceneRenderer.getLastTraceResult() == null ? null :
                    worldSceneRenderer.getLastTraceResult().getBlockPos();
            if (look != null && look.equals(selected)) {
                renderBlockOverLay(selected, 200, 75, 75);
            } else {
                renderBlockOverLay(look, 150, 150, 150);
                renderBlockOverLay(selected, 255, 0, 0);
            }
            // Render candidate block cycling at the selected position (GT5 style in-place preview)
            if (selected != null && !previewCandidates.isEmpty()) {
                renderCandidateBlockAtPosition(world, selected);
            }
        });
        world.updateEntities();
        world.setRenderFilter(worldSceneRenderer.renderedBlocks::contains);

        Map<BlockPos, StructureElementPreviewEntry> previewEntries = new HashMap<>();
        Map<BlockPos, StructureElementPreviewEntry> sourcePreviewEntries =
                controller.buildStructurePreviewEntries(channelValues);
        sourcePreviewEntries.forEach((previewPos, entry) -> {
            if (blockMap.containsKey(previewPos)) {
                previewEntries.put(previewPos, entry);
            }
        });
        logTypedPreviewEntrySource(controllerBase, blockMap.size(), sourcePreviewEntries.size(),
                previewEntries.size());

        List<ItemStack> sortedParts = gatherStructureBlocks(worldSceneRenderer.world, blockMap).stream()
                .sorted((one, two) -> {
                    if (one.isController) return -1;
                    if (two.isController) return +1;
                    if (one.isTile && !two.isTile) return -1;
                    if (two.isTile && !one.isTile) return +1;
                    if (one.blockId != two.blockId) return two.blockId - one.blockId;
                    return two.amount - one.amount;
                }).map(PartInfo::getItemStack).collect(Collectors.toList());

        return new MBPattern(worldSceneRenderer, sortedParts, previewEntries);
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
        private FBOWorldSceneRenderer renderer;
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
            renderer = new FBOWorldSceneRenderer(world, 512, 512);
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
            renderer.setOnLookingAt(ray -> {});

            if (renderer.renderedBlocks.size() > 100) {
                renderer.setTileEntityFilter(te ->
                        te instanceof IGregTechTileEntity gtte &&
                                gtte.getMetaTileEntity() instanceof MultiblockControllerBase);
                renderer.setHitTestInterval(5);
            } else if (renderer.renderedBlocks.size() > 50) {
                renderer.setMaxTileEntityRenderers(8);
                renderer.setMaxTileEntityRenderDistance(16.0);
                renderer.setHitTestInterval(3);
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

    private static final class PreviewCandidate {

        @NotNull
        private final BlockInfo[] blockCandidates;
        @NotNull
        private final List<ItemStack> itemCandidates;
        @NotNull
        private final List<String> tooltip;

        private PreviewCandidate(@NotNull BlockInfo[] blockCandidates,
                                 @NotNull List<ItemStack> itemCandidates,
                                 @NotNull List<String> tooltip) {
            this.blockCandidates = blockCandidates;
            this.itemCandidates = itemCandidates;
            this.tooltip = tooltip;
        }

        @NotNull
        private static PreviewCandidate fromGroup(@NotNull StructureElementPreviewEntry entry,
                                                  @NotNull StructureElementPreview.CandidateGroup group) {
            BlockInfo[] infos = group.getCandidates();
            List<String> tooltip = new ArrayList<>(entry.getTooltip());
            tooltip.addAll(group.getTooltip());
            return new PreviewCandidate(infos, itemCandidatesFrom(infos), tooltip);
        }

        private boolean hasCandidates() {
            return !itemCandidates.isEmpty();
        }

        @NotNull
        private BlockInfo[] getBlockCandidates() {
            return blockCandidates;
        }

        @NotNull
        private List<ItemStack> getItemCandidates() {
            return itemCandidates;
        }

        @NotNull
        private List<String> getTooltip() {
            return tooltip;
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
