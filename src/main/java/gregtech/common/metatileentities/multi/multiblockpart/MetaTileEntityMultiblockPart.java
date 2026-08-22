package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IColorChannelPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOrientedCubeRenderer;
import gregtech.client.renderer.texture.cube.VisualStateRenderer;
import gregtech.client.renderer.texture.custom.FireboxActiveRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import static gregtech.api.capability.GregtechDataCodes.SYNC_CONTROLLER;

public abstract class MetaTileEntityMultiblockPart extends MetaTileEntity
        implements IMultiblockPart, ITieredMetaTileEntity {

    private final int tier;
    protected ICubeRenderer hatchTexture = null;
    private BlockPos controllerPos;
    private MultiblockControllerBase controllerTile;

    // Move multiblock parts from the generic machines tab into their own tab
    {
        creativeTabs.add(GTCreativeTabs.TAB_GREGTECH_MULTIBLOCK_PARTS);
        creativeTabs.remove(GTCreativeTabs.TAB_GREGTECH_MACHINES);
    }

    public MetaTileEntityMultiblockPart(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId);
        this.tier = tier;
        initializeInventory();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(getBaseTexture().getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        ICubeRenderer baseTexture = getBaseTexture();
        IVertexOperation[] basePipeline = pipeline;

        boolean colorChannelPart = this instanceof IColorChannelPart part && part.showColorChannelPatch();
        if (!colorChannelPart) {
            pipeline = ArrayUtils.add(pipeline,
                    new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        }

        if (baseTexture instanceof FireboxActiveRenderer || baseTexture instanceof SimpleOrientedCubeRenderer) {
            baseTexture.renderOriented(renderState, translation, pipeline, getFrontFacing());
        } else {
            baseTexture.render(renderState, translation, pipeline);
        }

        if (colorChannelPart && isPainted()) {
            renderColorChannelIndicator(renderState, translation, basePipeline);
        }
    }

    @SideOnly(Side.CLIENT)
    protected void renderColorChannelIndicator(CCRenderState renderState, Matrix4 translation,
                                               IVertexOperation[] pipeline) {
        IVertexOperation[] ops = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        Textures.COLOR_CHANNEL_INDICATOR_COLORED.renderSided(getFrontFacing(), renderState,
                RenderUtil.adjustTrans(translation, getFrontFacing(), 1), ops);
        Textures.COLOR_CHANNEL_INDICATOR_FRAME.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    public int getTier() {
        return tier;
    }

    public MultiblockControllerBase getController() {
        if (getWorld() != null && getWorld().isRemote) { // check this only clientside
            if (controllerTile == null && controllerPos != null) {
                this.controllerTile = (MultiblockControllerBase) GTUtility.getMetaTileEntity(getWorld(), controllerPos);
            }
        }
        if (controllerTile != null && (controllerTile.getHolder() == null ||
                !controllerTile.isValid() ||
                !(getWorld().isRemote || controllerTile.getMultiblockParts().contains(this)))) {
            // tile can become invalid for many reasons, and can also forgot to remove us once we aren't in structure
            // anymore
            // so check it here to prevent bugs with dangling controller reference and wrong texture
            this.controllerTile = null;
        }
        return controllerTile;
    }

    private void setController(MultiblockControllerBase controller1) {
        this.controllerTile = controller1;
        if (!getWorld().isRemote) {
            writeCustomData(SYNC_CONTROLLER, writer -> {
                writer.writeBoolean(controllerTile != null);
                if (controllerTile != null) {
                    writer.writeBlockPos(controllerTile.getPos());
                }
            });
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IBlockState getCasingBlock() {
        MultiblockControllerBase controller = getController();
        if (controller != null) {
            return controller.getCasingBlock(this);
        }
        return null;
    }

    public ICubeRenderer getBaseTexture() {
        MultiblockControllerBase controller = getController();
        if (controller != null) {
            IBlockState casing = controller.getCasingBlock(this);
            if (casing != null) return this.hatchTexture = new VisualStateRenderer(casing);
            return this.hatchTexture = controller.getBaseTexture(this);
        } else if (this.hatchTexture != null && !(this.hatchTexture instanceof VisualStateRenderer)) {
            // 结构失效：VisualStateRenderer 绑定 casing 外观与 SOLID 渲染层，
            // 失效后 MTE 改在 CUTOUT_MIPPED 层渲染，继续复用会导致面丢失（透明），必须回退。
            if (hatchTexture != Textures.getInactiveTexture(hatchTexture)) {
                return this.hatchTexture = Textures.getInactiveTexture(hatchTexture);
            }
            return this.hatchTexture;
        } else {
            return Textures.VOLTAGE_CASINGS[tier];
        }
    }

    public boolean shouldRenderOverlay() {
        MultiblockControllerBase controller = getController();
        return controller == null || controller.shouldRenderOverlay(this);
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return true;
    }

    @Override
    public void setPaintingColor(int paintingColor, @Nullable EnumFacing side) {
        super.setPaintingColor(paintingColor, side);
        // 染色变化影响颜色通道分组,立即通知控制器重查配方(喷涂本身不触发仓输入通知)
        if (getWorld() != null && !getWorld().isRemote) {
            MultiblockControllerBase controller = getController();
            if (controller instanceof RecipeMapMultiblockController recipeMapController) {
                recipeMapController.getRecipeMapWorkable().forceRecipeRecheck();
            }
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        MultiblockControllerBase controller = getController();
        buf.writeBoolean(controller != null);
        if (controller != null) {
            buf.writeBlockPos(controller.getPos());
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        if (buf.readBoolean()) {
            this.controllerPos = buf.readBlockPos();
            this.controllerTile = null;
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == SYNC_CONTROLLER) {
            if (buf.readBoolean()) {
                this.controllerPos = buf.readBlockPos();
                this.controllerTile = null;
            } else {
                this.controllerPos = null;
                this.controllerTile = null;
            }
            scheduleRenderUpdate();
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        MultiblockControllerBase controller = getController();
        if (!getWorld().isRemote && controller != null) {
            controller.invalidateStructure();
        }
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        setController(controllerBase);
        scheduleRenderUpdate();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        setController(null);
        scheduleRenderUpdate();
    }

    @Override
    public boolean isAttachedToMultiBlock() {
        return getController() != null;
    }

    @Override
    public int getDefaultPaintingColor() {
        return !isAttachedToMultiBlock() && hatchTexture == null ? super.getDefaultPaintingColor() : 0xFFFFFF;
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        MultiblockControllerBase controllerBase = getController();
        if (controllerBase == null) return super.getIsWeatherOrTerrainResistant();
        return controllerBase.isMultiblockPartWeatherResistant(this);
    }
}
