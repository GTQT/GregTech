package gregtech.api.mui.widget.side;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.fakeworld.ArraySchema;
import com.cleanroommc.modularui.utils.fakeworld.BlockHighlight;
import com.cleanroommc.modularui.utils.fakeworld.BlockInfo;
import com.cleanroommc.modularui.utils.fakeworld.ISchema;
import com.cleanroommc.modularui.utils.fakeworld.RenderWorld;
import com.cleanroommc.modularui.utils.fakeworld.SchemaRenderer;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * 侧面总览的场景渲染器：机器本身 + 六个正交邻居，输出面画橙/蓝环，选中面画绿框，悬停面画白框。
 */
@SideOnly(Side.CLIENT)
final class SideSceneRenderer extends SchemaRenderer {

    private static final int ITEM_COLOR = 0xFFFF6E0F;
    private static final int ITEM_IDLE_COLOR = 0x8FFF6E0F;
    private static final int FLUID_COLOR = 0xFF00B4FF;
    private static final int FLUID_IDLE_COLOR = 0x8F00B4FF;
    private static final int SELECTED_COLOR = 0xFF00FF00;
    private static final int HOVER_COLOR = 0xFFFFFFFF;

    // BlockHighlight 的厚度是世界单位。2.2 镜头距离、60° FOV、约 80px 高的场景里
    // 一个方块约 31px，所以 0.032 约等于 1px，0.064 约等于 2px。
    private static final float ITEM_THICKNESS = 0.032f;
    private static final float FLUID_THICKNESS = 0.064f;
    private static final float SELECTED_THICKNESS = 0.064f;
    private static final float HOVER_THICKNESS = 0.032f;

    /** schema 自己坐标系里的中心格。 */
    static final BlockPos SCHEMA_CENTER = new BlockPos(1, 1, 1);

    /**
     * MUI 的 {@link com.cleanroommc.modularui.utils.fakeworld.BaseSchemaRenderer} 会把固定尺寸 FBO
     * 直接拉伸到控件区域。场景很矮时固定 16:9 会被纵向压扁，所以这里按控件宽高比重建 FBO。
     */
    private static final int MAX_FBO_SIZE = 960;

    private final Framebuffer framebuffer;
    private final RefreshableSchema schema;
    private final Supplier<SideState> stateSupplier;
    private final Supplier<EnumFacing> selectedSupplier;

    private final BlockHighlight itemBorder = new BlockHighlight(ITEM_COLOR, false, ITEM_THICKNESS);
    private final BlockHighlight fluidBorder = new BlockHighlight(FLUID_COLOR, false, FLUID_THICKNESS);
    private final BlockHighlight selectedBorder = new BlockHighlight(SELECTED_COLOR, false, SELECTED_THICKNESS);
    private final BlockHighlight hoverBorder = new BlockHighlight(HOVER_COLOR, false, HOVER_THICKNESS);

    private @Nullable RayTraceResult lastResult;
    private int lastFrameKey = Integer.MIN_VALUE;

    private SideSceneRenderer(@NotNull RefreshableSchema schema,
                              @NotNull Supplier<SideState> stateSupplier,
                              @NotNull Supplier<EnumFacing> selectedSupplier,
                              @NotNull Framebuffer framebuffer) {
        super(schema, framebuffer);
        this.framebuffer = framebuffer;
        this.schema = schema;
        this.stateSupplier = stateSupplier;
        this.selectedSupplier = selectedSupplier;
        rayTracing(true);
        afterRender(renderer -> {
            // 朝向/输出面变化时 TE 的模型也会变，不能只比较 blockstate。
            SideState state = this.stateSupplier.get();
            int frameKey = frameKey(state);
            if (frameKey != this.lastFrameKey) {
                this.lastFrameKey = frameKey;
                this.schema.refresh(true);
            } else {
                this.schema.refresh();
            }
            this.drawGhostNeighbors();
            this.drawOverlays(state);
        });
    }

    @Override
    public void draw(@NotNull GuiContext context, int x, int y, int width, int height, @NotNull WidgetTheme widgetTheme) {
        this.ensureFramebufferSize(width, height);
        super.draw(context, x, y, width, height, widgetTheme);
    }

    private void ensureFramebufferSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float aspect = (float) width / (float) height;
        int fboWidth;
        int fboHeight;
        if (aspect >= 1.0f) {
            fboWidth = MAX_FBO_SIZE;
            fboHeight = Math.max(16, Math.round(MAX_FBO_SIZE / aspect));
        } else {
            fboHeight = MAX_FBO_SIZE;
            fboWidth = Math.max(16, Math.round(MAX_FBO_SIZE * aspect));
        }
        if (this.framebuffer.framebufferWidth == fboWidth && this.framebuffer.framebufferHeight == fboHeight) {
            return;
        }
        if (this.framebuffer.framebufferTexture >= 0) {
            this.framebuffer.deleteFramebuffer();
        }
        this.framebuffer.createFramebuffer(fboWidth, fboHeight);
    }

    private static int frameKey(@NotNull SideState state) {
        int key = state.front.ordinal();
        key = key * 7 + (state.itemFace == null ? 6 : state.itemFace.ordinal());
        key = key * 7 + (state.fluidFace == null ? 6 : state.fluidFace.ordinal());
        // 自动输出状态也会影响机器 TESR 的输出口模型。
        key = key * 2 + (state.itemAuto ? 1 : 0);
        key = key * 2 + (state.fluidAuto ? 1 : 0);
        return key;
    }

    static @NotNull SideSceneRenderer create(@NotNull World world, @NotNull BlockPos machinePos,
                                             @NotNull Supplier<SideState> stateSupplier,
                                             @NotNull Supplier<EnumFacing> selectedSupplier) {
        RefreshableSchema schema = new RefreshableSchema(world, machinePos);
        schema.refresh();
        return new SideSceneRenderer(schema, stateSupplier, selectedSupplier, new Framebuffer(16, 16, true));
    }

    @Override
    public int getClearColor() {
        return 0xFF222222;
    }

    @Override
    protected void onSuccessfulRayTrace(@NotNull RayTraceResult result) {
        this.lastResult = result;
    }

    @Override
    protected void onRayTraceFailed() {
        this.lastResult = null;
    }

    private void drawOverlays(@NotNull SideState state) {
        var camera = getCamera().getPos();

        // renderWorld() 结束时关闭了混合；输出面的半透明“手动”颜色必须重新开启混合才生效。
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);

        this.itemBorder.setFrameThickness(ITEM_THICKNESS);
        if (state.itemFace != null) {
            this.itemBorder.setColor(state.itemAuto ? ITEM_COLOR : ITEM_IDLE_COLOR);
            this.itemBorder.renderHighlight(SCHEMA_CENTER, state.itemFace, camera);
        }

        this.fluidBorder.setFrameThickness(FLUID_THICKNESS);
        if (state.fluidFace != null) {
            this.fluidBorder.setColor(state.fluidAuto ? FLUID_COLOR : FLUID_IDLE_COLOR);
            this.fluidBorder.renderHighlight(SCHEMA_CENTER, state.fluidFace, camera);
        }

        EnumFacing selected = this.selectedSupplier.get();
        if (selected != null) {
            this.selectedBorder.renderHighlight(SCHEMA_CENTER, selected, camera);
        }

        RayTraceResult result = this.lastResult;
        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK &&
                SCHEMA_CENTER.equals(result.getBlockPos()) && result.sideHit != selected) {
            this.hoverBorder.renderHighlight(SCHEMA_CENTER, result.sideHit, camera);
        }

        GlStateManager.disableBlend();
    }

    /**
     * GTM 把六个邻居用加法混合画成幽灵。MUI 的主渲染循环不能按块改 alpha，所以主场景只画机器本身，
     * 这里在同一个 FBO 里用 BlockRendererDispatcher 补画邻居，深度测试保证只有挡在机器前面的邻居可见。
     * 邻居如果是 MTE，方块模型通常不可见，必须再补画一次 TESR。
     */
    private void drawGhostNeighbors() {
        ArraySchema all = this.schema.all();
        if (all == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        RenderWorld ghostWorld = new RenderWorld(all);
        mc.entityRenderer.disableLightmap();
        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        // 方块模型顶点的 alpha 一般是 1，用常量 alpha 才能稳定做出 28% 的幽灵效果。
        GL14.glBlendColor(1.0F, 1.0F, 1.0F, 0.28F);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.28F);

        List<TileEntity> tesr = new java.util.ArrayList<>();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        try {
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos pos = SCHEMA_CENTER.offset(facing);
                IBlockState state = all.getWorld().getBlockState(pos);
                if (state.getBlock() == Blocks.AIR) continue;
                state = state.getActualState(all.getWorld(), pos);
                TileEntity tile = all.getWorld().getTileEntity(pos);
                if (tile != null && !tile.isInvalid() &&
                        TileEntityRendererDispatcher.instance.getRenderer(tile.getClass()) != null) {
                    if (!tile.getPos().equals(pos)) tile.setPos(pos.toImmutable());
                    tesr.add(tile);
                }
                mc.getBlockRendererDispatcher().renderBlock(state, pos, ghostWorld, buffer);
            }
        } finally {
            Tessellator.getInstance().draw();
            // 方块模型画完后再画 MTE 的 TESR，否则会被邻居的方块模型盖住。
            GlStateManager.tryBlendFuncSeparate(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            for (TileEntity tile : tesr) {
                TileEntityRendererDispatcher.instance.render(tile,
                        tile.getPos().getX(), tile.getPos().getY(), tile.getPos().getZ(), 0);
            }
            ForgeHooksClient.setRenderLayer(oldLayer);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    /**
     * 7 块快照：中心 + 六个正交邻居。比 3x3x3 少了 20 个对角线块，和 GTM 的侧面总览一致，
     * 也避免射线检测打到看不见的对角线方块。
     */
    static final class RefreshableSchema implements ISchema {

        private final World world;
        private final BlockPos center;
        private final IBlockState[] states = new IBlockState[7];
        private @Nullable ArraySchema current;
        private @Nullable ArraySchema centerOnly;

        RefreshableSchema(@NotNull World world, @NotNull BlockPos center) {
            this.world = world;
            this.center = center;
        }

        boolean refresh() {
            return refresh(false);
        }

        boolean refresh(boolean force) {
            if (!this.world.isBlockLoaded(this.center)) return false;
            boolean changed = force || this.current == null;
            for (int i = 0; i < 7; i++) {
                BlockPos pos = i == 0 ? this.center : this.center.offset(EnumFacing.VALUES[i - 1]);
                IBlockState state = this.world.isBlockLoaded(pos) ? this.world.getBlockState(pos) :
                        Blocks.AIR.getDefaultState();
                if (this.states[i] != state) {
                    this.states[i] = state;
                    changed = true;
                }
            }
            if (changed) {
                BlockInfo[][][] blocks = snapshot(this.world, this.center);
                this.current = new ArraySchema(blocks);
                // 主渲染用一个只有中心块的 schema：否则 BlockRendererDispatcher 会把
                // 与邻居 MTE 接壤的那一面当成“被遮挡面”直接剔除。
                BlockInfo[][][] centerBlocks = new BlockInfo[3][3][3];
                centerBlocks[1][1][1] = blocks[1][1][1];
                this.centerOnly = new ArraySchema(centerBlocks);
            }
            return changed;
        }

        private static @NotNull BlockInfo[][][] snapshot(@NotNull World world, @NotNull BlockPos center) {
            BlockInfo[][][] blocks = new BlockInfo[3][3][3];
            blocks[1][1][1] = BlockInfo.of(world, center);
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos pos = center.offset(facing);
                if (!world.isBlockLoaded(pos)) continue;
                blocks[pos.getX() - center.getX() + 1]
                        [pos.getY() - center.getY() + 1]
                        [pos.getZ() - center.getZ() + 1] = BlockInfo.of(world, pos);
            }
            return blocks;
        }

        @Nullable
        ArraySchema all() {
            return this.current;
        }

        @NotNull
        @Override
        public Iterator<Pair<BlockPos, BlockInfo>> iterator() {
            if (this.centerOnly == null) return java.util.Collections.emptyIterator();
            // 主场景只画机器本身；六个邻居由 drawGhostNeighbors() 以半透明方式补画。
            return this.centerOnly.iterator();
        }

        @Override
        public World getWorld() {
            if (this.centerOnly != null) return this.centerOnly.getWorld();
            return this.world;
        }

        @Override
        public Vec3d getFocus() {
            if (this.current == null) {
                // ArraySchema 的坐标是 0..2 的本地坐标，这里不能退回世界坐标。
                return new Vec3d(1.5D, 1.5D, 1.5D);
            }
            return this.current.getFocus();
        }

        @Override
        public BlockPos getOrigin() {
            return this.current == null ? BlockPos.ORIGIN : this.current.getOrigin();
        }

        @Override
        public void setRenderFilter(@Nullable BiPredicate<BlockPos, BlockInfo> renderFilter) {
            if (this.current != null) this.current.setRenderFilter(renderFilter);
            if (this.centerOnly != null) this.centerOnly.setRenderFilter(renderFilter);
        }

        @Override
        public @Nullable BiPredicate<BlockPos, BlockInfo> getRenderFilter() {
            return this.current == null ? null : this.current.getRenderFilter();
        }
    }
}
