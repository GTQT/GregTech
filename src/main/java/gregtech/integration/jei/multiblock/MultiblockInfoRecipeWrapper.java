package gregtech.integration.jei.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.ParametricMultiblockController;
import gregtech.api.metatileentity.registry.MBPattern;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;
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
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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
    private static ItemStack tooltipBlockStack;
    private static long lastRender;
    private static MultiblockInfoRecipeWrapper lastWrapper;
    private final MultiblockControllerBase controller;
    private final Map<GuiButton, Runnable> buttons = new HashMap<>();
    private final List<ItemStack> allItemStackInputs = new ArrayList<>();
    private final GuiButton nextLayerButton;
    private final List<TraceabilityPredicate.SimplePredicate> predicates;
    private final Map<String, Integer> channelValues = new HashMap<>();
    private final List<StructureChannel> supportedChannels;
    private final int[][] channelRanges; // [channelIdx][0=min, 1=max]
    private MBPattern[] patterns;
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
    private List<String> predicateTips;
    private BlockPos selected;
    private TraceabilityPredicate father;
    // Candidate cycling state for 3D in-place rendering
    private int candidateCycleIndex = 0;
    private long lastCandidateCycleTime = 0L;
    // Channel slider state
    private int draggingChannelIdx = -1;
    private int hoveredChannelIdx = -1;

    @SuppressWarnings("NewExpressionSideOnly")
    public MultiblockInfoRecipeWrapper(@NotNull MultiblockControllerBase controller) {
        this.controller = controller;
        this.supportedChannels = controller.getSupportedChannels();
        // Precompute ranges from the pattern template
        this.channelRanges = new int[supportedChannels.size()][];
        for (int i = 0; i < supportedChannels.size(); i++) {
            channelRanges[i] = controller.getChannelRange(supportedChannels.get(i));
        }

        Set<ItemStack> drops = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        this.patterns = controller.getMatchingShapes(channelValues).stream()
                .map(it -> initializePattern(it, drops))
                .toArray(MBPattern[]::new);
        allItemStackInputs.addAll(drops);
        this.nextLayerButton = new GuiButton(0, 176 - (ICON_SIZE + RIGHT_PADDING), LAYER_BUTTON_Y, ICON_SIZE,
                ICON_SIZE, "");

        this.buttons.put(nextLayerButton, this::toggleNextLayer);
        this.predicates = new ArrayList<>();
        GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);
    }

    @NotNull
    private static Collection<PartInfo> gatherStructureBlocks(World world, @NotNull Map<BlockPos, BlockInfo> blocks,
                                                              Set<ItemStack> parts) {
        Map<ItemStack, PartInfo> partsMap = new Object2ObjectOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        for (Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            ItemStack stack = ItemStack.EMPTY;

            // first check if the block is a GT machine
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof IGregTechTileEntity) {
                MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
                // For parametric controllers, include variant NBT so the correct
                // variant item is shown in the JEI parts list
                if (mte instanceof ParametricMultiblockController<?> parametric) {
                    stack = getParametricStackForm(parametric);
                } else {
                    stack = mte.getStackForm();
                }
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

            // if we got a stack, add it to the set and map
            if (!stack.isEmpty()) {
                parts.add(stack);

                PartInfo partInfo = partsMap.get(stack);
                if (partInfo == null) {
                    partInfo = new PartInfo(stack, entry.getValue());
                    partsMap.put(stack, partInfo);
                }
                partInfo.amount++;
            }
        }
        return partsMap.values();
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

        // Collect all candidate BlockInfo from all predicates
        List<BlockInfo> allCandidateBlocks = new ArrayList<>();
        for (TraceabilityPredicate.SimplePredicate predicate : predicates) {
            if (predicate.candidates != null) {
                BlockInfo[] infos = predicate.candidates.get();
                for (BlockInfo info : infos) {
                    if (info.getBlockState().getBlock() != net.minecraft.init.Blocks.AIR) {
                        allCandidateBlocks.add(info);
                    }
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
        ingredients.setInputs(VanillaTypes.ITEM, allItemStackInputs);
        ingredients.setOutput(VanillaTypes.ITEM, getControllerStack());
    }

    /**
     * Returns the correct ItemStack for this controller instance.
     * For {@link ParametricMultiblockController}, includes variant NBT so JEI can
     * distinguish between different variant recipes.
     */
    @NotNull
    private ItemStack getControllerStack() {
        if (controller instanceof ParametricMultiblockController<?> parametric) {
            return getParametricStackForm(parametric);
        }
        return controller.getStackForm();
    }

    /**
     * Helper method to capture the wildcard type parameter of ParametricMultiblockController,
     * allowing getStackForm(V) to accept the result of getVariant().
     */
    @NotNull
    private static <V extends Enum<V>> ItemStack getParametricStackForm(
            @NotNull ParametricMultiblockController<V> parametric) {
        return parametric.getStackForm(parametric.getVariant());
    }

    /**
     * Replaces the controller MTE in the preview block map with a copy that carries
     * the correct variant from {@link #controller}. This ensures the JEI 3D preview
     * renders the variant-specific controller model/texture instead of the default one.
     */
    @SuppressWarnings("unchecked")
    private <V extends Enum<V>> void replaceControllerVariantInPreview(
            @NotNull Map<BlockPos, BlockInfo> blockMap,
            @NotNull BlockPos controllerPos,
            @NotNull MultiblockControllerBase previewController) {
        ParametricMultiblockController<V> source = (ParametricMultiblockController<V>) controller;
        ParametricMultiblockController<V> target = (ParametricMultiblockController<V>) previewController;
        V desiredVariant = source.getVariant();
        if (desiredVariant == target.getVariant()) return;

        target.setVariant(desiredVariant);
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(target);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(target.getFrontFacing());
        blockMap.put(controllerPos, new BlockInfo(
                target.getBlock().getDefaultState(), holder));
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

    public void setRecipeLayout(RecipeLayout layout, IGuiHelper guiHelper) {
        this.recipeLayout = layout;

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, SLOT_SIZE, SLOT_SIZE)
                .setTextureSize(SLOT_SIZE, SLOT_SIZE).build();
        this.infoIcon = guiHelper.drawableBuilder(GuiTextures.INFO_ICON.imageLocation, 0, 0, ICON_SIZE, ICON_SIZE)
                .setTextureSize(ICON_SIZE, ICON_SIZE).build();

        IDrawable border = layout.getRecipeCategory().getBackground();
        preparePlaceForParts(border.getHeight());
        if (Mouse.getEventDWheel() == 0 || lastWrapper != this) {
            selected = null;
            this.predicates.clear();
            this.father = null;
            lastWrapper = this;
            this.nextLayerButton.x = border.getWidth() - (ICON_SIZE + RIGHT_PADDING);
            this.nextLayerButton.y = LAYER_BUTTON_Y;
            Vector3f size = ((TrackedDummyWorld) getCurrentRenderer().world).getSize();
            float max = Math.max(Math.max(Math.max(size.x, size.y), size.z), 1);
            this.zoom = (float) (3.5 * Math.sqrt(max));
            this.rotationYaw = 20.0f;
            this.rotationPitch = 50f;
            setNextLayer(-1);
        } else {
            zoom = (float) MathHelper.clamp(zoom + (Mouse.getEventDWheel() < 0 ? 0.5 : -0.5), 3, 999);
            setNextLayer(getLayerIndex());
            if (predicates != null && predicates.size() > 0) {
                setItemStackGroup();
            }
        }
        if (getCurrentRenderer() != null) {
            TrackedDummyWorld world = (TrackedDummyWorld) getCurrentRenderer().world;
            resetCenter(world);
        }
        updateParts();
    }

    public WorldSceneRenderer getCurrentRenderer() {
        return patterns[0].getSceneRenderer();
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    private void toggleNextLayer() {
        WorldSceneRenderer renderer = getCurrentRenderer();
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
            resetCenter(world);
            renderer.disableClipPlanes();
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
        }
    }

    private void resetCenter(TrackedDummyWorld world) {
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
        if (layerIndex != -1) {
            center.y = minPos.y + layerIndex + 0.5f;
        }
        getCurrentRenderer().setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    private void updateChannelValue(int channelIndex, int delta) {
        if (channelIndex < 0 || channelIndex >= supportedChannels.size()) return;
        String channelName = supportedChannels.get(channelIndex).getName();
        int min = channelRanges[channelIndex][0];
        int max = channelRanges[channelIndex][1];
        int current = channelValues.getOrDefault(channelName, 0);
        int newValue = Math.max(0, Math.min(max, current + delta));
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
        int max = channelRanges[channelIndex][1];
        int clamped = Math.max(0, Math.min(max, value));
        if (clamped == 0) {
            channelValues.remove(channelName);
        } else {
            channelValues.put(channelName, clamped);
        }
        regeneratePatterns();
    }

    private void regeneratePatterns() {
        Set<ItemStack> drops = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        this.patterns = controller.getMatchingShapes(channelValues).stream()
                .map(it -> initializePattern(it, drops))
                .toArray(MBPattern[]::new);
        allItemStackInputs.clear();
        allItemStackInputs.addAll(drops);
        // Update the global pattern cache so tooltips reflect the current channel state
        GregTechAPI.addPatterns(controller.metaTileEntityId, patterns);
        setNextLayer(-1);
        updateParts();
        getCurrentRenderer().setCameraLookAt(center, zoom, Math.toRadians(rotationPitch),
                Math.toRadians(rotationYaw));
        if (this.selected != null) {
            this.selected = null;
            for (int i = 0; i < predicates.size(); i++) {
                recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
            }
            predicates.clear();
            this.father = null;
        }
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
    @Override
    public void drawInfo(@NotNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        WorldSceneRenderer renderer = getCurrentRenderer();
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
        for (int i = 0; i < predicates.size() && i < MAX_CANDIDATES; i++) {
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
        this.predicateTips = null;
        RayTraceResult rayTraceResult = renderer.getLastTraceResult();

        if (!(leftClickHeld || rightClickHeld) && insideView && rayTraceResult != null &&
                !renderer.world.isAirBlock(rayTraceResult.getBlockPos())) {

            IBlockState blockState = renderer.world.getBlockState(rayTraceResult.getBlockPos());
            ItemStack itemStack = blockState.getBlock().getPickBlock(
                    blockState, rayTraceResult, renderer.world,
                    rayTraceResult.getBlockPos(), minecraft.player
            );

            TraceabilityPredicate predicates = patterns[0].getPredicateMap()
                    .get(rayTraceResult.getBlockPos());
            if (predicates != null) {
                BlockWorldState worldState = new BlockWorldState();
                worldState.update(renderer.world, rayTraceResult.getBlockPos(), new PatternMatchContext(),
                        new HashMap<>(), new HashMap<>(), predicates);

                // 优先匹配common predicates
                for (TraceabilityPredicate.SimplePredicate common : predicates.common) {
                    if (common.test(worldState)) {
                        predicateTips = common.getToolTips(predicates);
                        break;
                    }
                }
                // 未匹配则尝试limited predicates
                if (predicateTips == null) {
                    for (TraceabilityPredicate.SimplePredicate limit : predicates.limited) {
                        if (limit.test(worldState)) {
                            predicateTips = limit.getToolTips(predicates);
                            break;
                        }
                    }
                }
            }
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
            int range = Math.max(1, max);
            float ratio = (float) value / range;
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

    @Override
    public boolean handleClick(@NotNull Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
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
                    int max = channelRanges[i][1];
                    float ratio = (float) (mouseX - sliderX) / sliderWidth;
                    int newValue = Math.round(ratio * max);
                    setChannelValue(i, newValue);
                    draggingChannelIdx = i;
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
            if (getCurrentRenderer().getLastTraceResult() == null) {
                if (this.selected != null) {
                    this.selected = null;
                    for (int i = 0; i < predicates.size(); i++) {
                        recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
                    }
                    predicates.clear();
                    this.father = null;
                    this.candidateCycleIndex = 0;
                    this.lastCandidateCycleTime = 0L;
                    return true;
                }
                return false;
            }
            BlockPos selected = getCurrentRenderer().getLastTraceResult().getBlockPos();
            if (!Objects.equals(this.selected, selected)) {
                for (int i = 0; i < predicates.size(); i++) {
                    recipeLayout.getItemStacks().set(i + MAX_PARTS, ItemStack.EMPTY);
                }
                predicates.clear();
                this.father = null;
                this.selected = selected;
                // Reset candidate cycling state for 3D in-place preview
                this.candidateCycleIndex = 0;
                this.lastCandidateCycleTime = 0L;
                TraceabilityPredicate predicate = patterns[0].getPredicateMap().get(this.selected);
                if (predicate != null) {
                    predicates.addAll(predicate.common);
                    predicates.addAll(predicate.limited);
                    predicates.removeIf(p -> p.candidates == null);
                    this.father = predicate;
                    setItemStackGroup();
                }
                // Mark FBO dirty so scene re-renders with candidate block cycling
                if (getCurrentRenderer() instanceof FBOWorldSceneRenderer fboRenderer) {
                    fboRenderer.markFBODirty();
                }
                return true;
            }
        }
        return false;
    }

    private void setItemStackGroup() {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IDrawable border = recipeLayout.getRecipeCategory().getBackground();
        int recipeWidth = border.getWidth();
        // Place candidate slots on the right side (no overlap with left parts panel)
        int count = Math.min(predicates.size(), MAX_CANDIDATES);
        for (int i = 0; i < count; i++) {
            int col = i / CANDIDATES_PER_COL;
            int row = i % CANDIDATES_PER_COL;
            int slotX = recipeWidth - RIGHT_PADDING - (col + 1) * SLOT_SIZE;
            int slotY = row * SLOT_SIZE + CANDIDATE_SLOT_START_Y;
            itemStackGroup.init(i + MAX_PARTS, true, slotX, slotY);
            itemStackGroup.set(i + MAX_PARTS, predicates.get(i).getCandidates());
        }

        itemStackGroup.addTooltipCallback((slotIndex, input, itemStack, tooltip) -> {
            if (slotIndex >= MAX_PARTS && slotIndex < MAX_PARTS + predicates.size()) {
                tooltip.addAll(predicates.get(slotIndex - MAX_PARTS).getToolTips(father));
            }
        });
    }

    @NotNull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
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
            if (predicateTips != null) {
                tooltip.addAll(predicateTips);
            }
            return tooltip;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("NewExpressionSideOnly")
    @NotNull
    private MBPattern initializePattern(@NotNull MultiblockShapeInfo shapeInfo, @NotNull Set<ItemStack> parts) {
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        MultiblockControllerBase controllerBase = null;
        BlockPos controllerBlockPos = null;
        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    if (column[z].getTileEntity() instanceof IGregTechTileEntity &&
                            ((IGregTechTileEntity) column[z].getTileEntity())
                                    .getMetaTileEntity() instanceof MultiblockControllerBase) {
                        controllerBase = (MultiblockControllerBase) ((IGregTechTileEntity) column[z].getTileEntity())
                                .getMetaTileEntity();
                        controllerBlockPos = new BlockPos(x, y, z);
                    }
                    blockMap.put(new BlockPos(x, y, z), column[z]);
                }
            }
        }

        // When using selfPredicateByClass, the preview's controller block comes from the
        // first matching candidate in the registry, which may not be the actual controller
        // for this JEI recipe entry. Replace it with the correct controller instance.
        if (controllerBlockPos != null && controllerBase != null) {
            if (controller instanceof ParametricMultiblockController<?>
                    && controllerBase instanceof ParametricMultiblockController<?>) {
                // Parametric controllers: same MTE ID but different variant stored in NBT
                replaceControllerVariantInPreview(blockMap, controllerBlockPos, controllerBase);
            } else if (!controller.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
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
            if (selected != null && !predicates.isEmpty()) {
                renderCandidateBlockAtPosition(world, selected);
            }
        });
        world.updateEntities();
        world.setRenderFilter(worldSceneRenderer.renderedBlocks::contains);

        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        if (controllerBase != null) {
            gregtech.api.pattern.MultiblockState state = controllerBase.getMultiblockState();
            if (state == null) {
                controllerBase.reinitializeStructurePattern();
                state = controllerBase.getMultiblockState();
            }
            if (state != null) {
                state.cache.forEach((pos, blockInfo) -> predicateMap
                        .put(BlockPos.fromLong(pos), (TraceabilityPredicate) blockInfo.getInfo()));
            }
        }

        List<ItemStack> sortedParts = gatherStructureBlocks(worldSceneRenderer.world, blockMap, parts).stream()
                .sorted((one, two) -> {
                    if (one.isController) return -1;
                    if (two.isController) return +1;
                    if (one.isTile && !two.isTile) return -1;
                    if (two.isTile && !one.isTile) return +1;
                    if (one.blockId != two.blockId) return two.blockId - one.blockId;
                    return two.amount - one.amount;
                }).map(PartInfo::getItemStack).collect(Collectors.toList());

        return new MBPattern(worldSceneRenderer, sortedParts, predicateMap);
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
