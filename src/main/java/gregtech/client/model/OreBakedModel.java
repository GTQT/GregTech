package gregtech.client.model;

import gregtech.api.GTValues;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.material.info.MaterialIconType;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.util.GTUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = GTValues.MODID, value = Side.CLIENT)
public class OreBakedModel implements IBakedModel {

    private static final Map<Entry, ModelResourceLocation> ENTRIES = new Object2ObjectOpenHashMap<>();

    public static ModelResourceLocation registerOreEntry(StoneType stoneType, Material material) {
        return ENTRIES.computeIfAbsent(
                new Entry(stoneType, material.getMaterialIconSet(),
                        material.getProperty(PropertyKey.ORE).isEmissive(), false),
                Entry::getModelId);
    }

    public static ModelResourceLocation registerLeanOreEntry(StoneType stoneType, Material material) {
        return ENTRIES.computeIfAbsent(
                new Entry(stoneType, material.getMaterialIconSet(),
                        material.getProperty(PropertyKey.ORE).isEmissive(), true),
                Entry::getModelId);
    }

    protected final StoneType stoneType;
    protected final IBakedModel overlay;

    private IBakedModel baseModel;

    public OreBakedModel(StoneType stoneType, IBakedModel overlay) {
        this.stoneType = stoneType;
        this.overlay = overlay;
    }

    protected IBakedModel getBaseModel() {
        if (this.baseModel == null) {
            return this.baseModel = Minecraft.getMinecraft().blockRenderDispatcher
                    .getModelForState(this.stoneType.stone.get());
        }
        return this.baseModel;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> quads = new ArrayList<>(getBaseModel().getQuads(null, side, 0));
        quads.addAll(this.overlay.getQuads(null, side, rand));
        return quads;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return getBaseModel().isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return getBaseModel().isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return getBaseModel().getParticleTexture();
    }

    @NotNull
    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    @SuppressWarnings("deprecation")
    @NotNull
    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return getBaseModel().getItemCameraTransforms();
    }

    @Override
    public boolean isAmbientOcclusion(@NotNull IBlockState state) {
        return getBaseModel().isAmbientOcclusion(state);
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        for (Map.Entry<Entry, ModelResourceLocation> e : ENTRIES.entrySet()) {
            MaterialIconType iconType = e.getKey().lean ? MaterialIconType.oreLean : MaterialIconType.ore;
            event.getMap().registerSprite(iconType.getBlockTexturePath(e.getKey().iconSet));
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        Map<ResourceLocation, IBakedModel> overlayCache = new Object2ObjectOpenHashMap<>();

        for (Map.Entry<Entry, ModelResourceLocation> e : ENTRIES.entrySet()) {
            MaterialIconType iconType = e.getKey().lean ? MaterialIconType.oreLean : MaterialIconType.ore;
            IBakedModel overlay = overlayCache.computeIfAbsent(
                    iconType.getBlockTexturePath(e.getKey().iconSet),
                    tex -> new ModelFactory(ModelFactory.ModelTemplate.ORE_OVERLAY)
                            .addSprite("texture", tex)
                            .bake());
            event.getModelRegistry().putObject(e.getValue(), e.getKey().emissive ?
                    new EmissiveOreBakedModel(e.getKey().stoneType, overlay) :
                    new OreBakedModel(e.getKey().stoneType, overlay));
        }
    }

    private static final class Entry {

        private final StoneType stoneType;
        private final MaterialIconSet iconSet;
        private final boolean emissive;
        private final boolean lean;

        private final int hash;

        private Entry(StoneType stoneType, MaterialIconSet iconSet, boolean emissive, boolean lean) {
            this.stoneType = stoneType;
            this.iconSet = iconSet;
            this.emissive = emissive;
            this.lean = lean;

            this.hash = Objects.hash(stoneType.name, iconSet.name, emissive, lean);
        }

        public ModelResourceLocation getModelId() {
            String prefix = lean ? "lean_ore_" : "ore_";
            return new ModelResourceLocation(GTUtility.gregtechId(
                    prefix + this.stoneType.name + "_" + this.iconSet.name + (this.emissive ? "_emissive" : "")), "");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return this.stoneType.name.equals(entry.stoneType.name) &&
                    this.iconSet.name.equals(entry.iconSet.name) &&
                    this.emissive == entry.emissive &&
                    this.lean == entry.lean;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "stoneType=" + stoneType.name + ", iconSet=" + iconSet.name +
                    ", emissive=" + emissive + ", lean=" + lean;
        }
    }
}
