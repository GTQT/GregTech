package gregtech.integration.tic.materials;

import gregtech.api.GregTechAPI;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.properties.ToolProperty;
import gregtech.integration.tic.api.HarvestLevels;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.LanguageMap;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelFluid;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;

import com.google.common.collect.ImmutableMap;
import slimeknights.tconstruct.library.MaterialIntegration;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.ExtraMaterialStats;
import slimeknights.tconstruct.library.materials.HandleMaterialStats;
import slimeknights.tconstruct.library.materials.HeadMaterialStats;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static gregtech.api.GTValues.MODID;

/**
 * Registers GT tool materials as TiC materials and manages the fluid-block integration list.
 *
 * <p>
 * If TiC already knows a material with the same name, GT stats are merged (max of each stat). Otherwise a new TiC
 * material is created from scratch.
 */
public final class ToolMaterialRegistrar {

    /** TiC harvest level colors for levels beyond Cobalt (4). */
    private static final TextFormatting[] EXTRA_LEVEL_COLORS = {
            TextFormatting.DARK_AQUA,    // level 5
            TextFormatting.LIGHT_PURPLE, // level 6
            TextFormatting.WHITE,        // level 7+
    };

    private static final List<MaterialIntegration> integrations = new ArrayList<>();

    /** Fluids for which we asked TiC to register a fluid block (used to fix model loading). */
    private static final List<Fluid> integratedFluids = new ArrayList<>();

    /** Item MRL → Fluid; populated during ModelRegistryEvent, consumed during ModelBakeEvent. */
    private static final Map<ModelResourceLocation, Fluid> fluidItemModels = new LinkedHashMap<>();

    /** Lang key → GT Material; re-injected after resource reloads (F3+T or language change). */
    private static final Map<String, Material> translationSources = new LinkedHashMap<>();

    private ToolMaterialRegistrar() {}

    /** Registers all eligible GT materials as TiC materials and their fluid blocks. */
    public static void register(IForgeRegistry<Block> blockRegistry) {
        for (Material gtMaterial : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!gtMaterial.hasProperty(PropertyKey.TOOL)) continue;
            if (!gtMaterial.hasProperty(PropertyKey.INGOT) && !gtMaterial.hasProperty(PropertyKey.GEM)) continue;

            slimeknights.tconstruct.library.materials.Material existing = TinkerRegistry
                    .getMaterial(gtMaterial.getName());

            if (!existing.identifier.equals(
                    slimeknights.tconstruct.library.materials.Material.UNKNOWN.identifier)) {
                mergeMaterial(existing, gtMaterial);
            } else {
                registerMaterial(gtMaterial, blockRegistry);
            }
        }

        registerHarvestLevelNames();
    }

    /** Registers TiC fluid block models; must be called after {@link #register}. */
    public static void registerFluidModels() {
        for (MaterialIntegration integration : integrations) {
            integration.registerFluidModel();
        }
    }

    /**
     * Overrides TiC's FluidStateMapper for GT fluid blocks to avoid MissingVariantException. Block state mapper →
     * empty; item mesh → per-fluid MRL injected during ModelBakeEvent.
     */
    @SideOnly(Side.CLIENT)
    public static void suppressFluidBlockModels() {
        // iron variant used as Forge placeholder to avoid "no ItemMeshDefinition" warnings
        ModelResourceLocation ironFallback = new ModelResourceLocation("tconstruct:fluid_block", "iron");
        for (Fluid fluid : integratedFluids) {
            Block block = fluid.getBlock();
            if (block == null) continue;
            net.minecraftforge.client.model.ModelLoader.setCustomStateMapper(
                    block, b -> ImmutableMap.of());
            Item item = Item.getItemFromBlock(block);
            if (item == Items.AIR) continue;
            ModelResourceLocation mrl = new ModelResourceLocation(
                    MODID + ":fluid_" + fluid.getName(), "inventory");
            net.minecraftforge.client.model.ModelLoader.registerItemVariants(item, ironFallback);
            net.minecraftforge.client.model.ModelLoader.setCustomMeshDefinition(item, s -> mrl);
            fluidItemModels.put(mrl, fluid);
        }
    }

    /**
     * Registers a model loader that intercepts tconstruct:fluid_block#&lt;name&gt; variants for any registered Forge
     * fluid, returning ModelFluid instead of failing on MissingVariantException. Covers both registerMaterial() and
     * mergeMaterial() paths, and TiC Antique's own new materials.
     */
    @SideOnly(Side.CLIENT)
    public static void registerFluidBlockModelLoader() {
        ModelLoaderRegistry.registerLoader(new ICustomModelLoader() {

            @Override
            public void onResourceManagerReload(IResourceManager resourceManager) {}

            @Override
            public boolean accepts(ResourceLocation modelLocation) {
                if (!(modelLocation instanceof ModelResourceLocation mrl)) return false;
                return "tconstruct".equals(mrl.getNamespace()) && "fluid_block".equals(mrl.getPath()) &&
                        FluidRegistry.getFluid(mrl.getVariant()) != null;
            }

            @Override
            public IModel loadModel(ResourceLocation modelLocation) {
                Fluid fluid = FluidRegistry.getFluid(((ModelResourceLocation) modelLocation).getVariant());
                return new ModelFluid(fluid);
            }
        });
    }

    /**
     * Bakes per-fluid colored models via ModelFluid and injects them into the model registry. Uses fluid.getColor()
     * baked into vertex data, avoiding the need for IItemColor.
     */
    @SideOnly(Side.CLIENT)
    public static void injectFluidItemModels(IRegistry<ModelResourceLocation, IBakedModel> registry) {
        java.util.function.Function<ResourceLocation, TextureAtlasSprite> textureGetter = loc -> net.minecraft.client.Minecraft
                .getMinecraft()
                .getTextureMapBlocks().getAtlasSprite(loc.toString());
        for (Map.Entry<ModelResourceLocation, Fluid> entry : fluidItemModels.entrySet()) {
            IBakedModel model = new ModelFluid(entry.getValue()).bake(
                    TRSRTransformation.identity(),
                    DefaultVertexFormats.ITEM,
                    textureGetter);
            registry.putObject(entry.getKey(), model);
        }
    }

    /** Tracks a fluid for which an elastic-material integration will register a fluid block. */
    static void trackIntegratedFluid(Fluid fluid) {
        integratedFluids.add(fluid);
    }

    /** Merges GT stats into an existing TiC material, keeping the max of each stat. */
    private static void mergeMaterial(slimeknights.tconstruct.library.materials.Material ticMaterial,
                                      Material gtMaterial) {
        ToolProperty toolProp = gtMaterial.getProperty(PropertyKey.TOOL);

        // Head stats
        HeadMaterialStats existingHead = ticMaterial.getStats("head");
        int ticHL = MaterialStatCalc.mapHarvestLevel(toolProp.getToolHarvestLevel());
        if (existingHead != null) {
            TinkerRegistry.addMaterialStats(ticMaterial, new HeadMaterialStats(
                    Math.max(existingHead.durability, toolProp.getToolDurability()),
                    Math.max(existingHead.miningspeed, toolProp.getToolSpeed()),
                    Math.max(existingHead.attack, toolProp.getToolAttackDamage()),
                    Math.max(existingHead.harvestLevel, ticHL)));
        } else {
            TinkerRegistry.addMaterialStats(ticMaterial, new HeadMaterialStats(
                    toolProp.getToolDurability(), toolProp.getToolSpeed(),
                    toolProp.getToolAttackDamage(), ticHL));
        }

        // Handle stats
        HandleMaterialStats existingHandle = ticMaterial.getStats("handle");
        float gtHandleMod = MaterialStatCalc.calcHandleModifier(toolProp);
        int gtHandleDur = MaterialStatCalc.calcHandleDurability(toolProp);
        if (existingHandle != null) {
            TinkerRegistry.addMaterialStats(ticMaterial, new HandleMaterialStats(
                    Math.max(existingHandle.modifier, gtHandleMod),
                    Math.max(existingHandle.durability, gtHandleDur)));
        } else {
            TinkerRegistry.addMaterialStats(ticMaterial,
                    new HandleMaterialStats(gtHandleMod, gtHandleDur));
        }

        // Extra stats
        ExtraMaterialStats existingExtra = ticMaterial.getStats("extra");
        int gtExtra = MaterialStatCalc.calcExtraDurability(toolProp.getToolDurability());
        if (existingExtra != null) {
            TinkerRegistry.addMaterialStats(ticMaterial,
                    new ExtraMaterialStats(Math.max(existingExtra.extraDurability, gtExtra)));
        } else {
            TinkerRegistry.addMaterialStats(ticMaterial, new ExtraMaterialStats(gtExtra));
        }

        // Bow stats — lower drawspeed is faster (better)
        var existingBow = (slimeknights.tconstruct.library.materials.BowMaterialStats) ticMaterial.getStats("bow");
        var gtBow = MaterialStatCalc.calcBowStats(toolProp);
        if (existingBow != null) {
            TinkerRegistry.addMaterialStats(ticMaterial,
                    new slimeknights.tconstruct.library.materials.BowMaterialStats(
                            Math.min(existingBow.drawspeed, gtBow.drawspeed),
                            Math.max(existingBow.range, gtBow.range),
                            Math.max(existingBow.bonusDamage, gtBow.bonusDamage)));
        } else {
            TinkerRegistry.addMaterialStats(ticMaterial, gtBow);
        }

        // Arrow shaft stats
        var existingShaft = (slimeknights.tconstruct.library.materials.ArrowShaftMaterialStats) ticMaterial
                .getStats("shaft");
        var gtShaft = MaterialStatCalc.calcShaftStats(toolProp);
        if (existingShaft != null) {
            TinkerRegistry.addMaterialStats(ticMaterial,
                    new slimeknights.tconstruct.library.materials.ArrowShaftMaterialStats(
                            Math.max(existingShaft.modifier, gtShaft.modifier),
                            Math.max(existingShaft.bonusAmmo, gtShaft.bonusAmmo)));
        } else {
            TinkerRegistry.addMaterialStats(ticMaterial, gtShaft);
        }

        if (ticHL > 4) {
            HarvestLevels.registerIfAbsent(ticHL, gtMaterial.getLocalizedName());
        }

        MaterialTraitApplier.applyTraits(ticMaterial, gtMaterial, toolProp);
    }

    private static void registerMaterial(Material gtMaterial, IForgeRegistry<Block> blockRegistry) {
        String identifier = MODID + "." + gtMaterial.getName();
        ToolProperty toolProp = gtMaterial.getProperty(PropertyKey.TOOL);

        slimeknights.tconstruct.library.materials.Material ticMaterial = new slimeknights.tconstruct.library.materials.Material(
                identifier, gtMaterial.getMaterialRGB(), true);

        injectTranslation(identifier, gtMaterial);

        int durability = toolProp.getToolDurability();
        int ticHarvestLevel = MaterialStatCalc.mapHarvestLevel(toolProp.getToolHarvestLevel());

        if (ticHarvestLevel > 4) {
            HarvestLevels.registerIfAbsent(ticHarvestLevel, gtMaterial.getLocalizedName());
        }

        TinkerRegistry.addMaterialStats(ticMaterial,
                new HeadMaterialStats(durability, toolProp.getToolSpeed(),
                        toolProp.getToolAttackDamage(), ticHarvestLevel),
                new HandleMaterialStats(MaterialStatCalc.calcHandleModifier(toolProp),
                        MaterialStatCalc.calcHandleDurability(toolProp)),
                new ExtraMaterialStats(MaterialStatCalc.calcExtraDurability(durability)),
                MaterialStatCalc.calcBowStats(toolProp),
                MaterialStatCalc.calcShaftStats(toolProp));

        MaterialTraitApplier.applyTraits(ticMaterial, gtMaterial, toolProp);

        String oreSuffix = gtMaterial.toCamelCaseString();
        Fluid fluid = getFluid(gtMaterial);

        if (gtMaterial.hasProperty(PropertyKey.INGOT) && oreSuffix != null) {
            ticMaterial.addCommonItems(oreSuffix);
            ticMaterial.addItem("bolt" + oreSuffix, 1,
                    slimeknights.tconstruct.library.materials.Material.VALUE_Ingot / 4);
        } else if (gtMaterial.hasProperty(PropertyKey.GEM) && oreSuffix != null) {
            ticMaterial.addItem("gem" + oreSuffix, 1,
                    slimeknights.tconstruct.library.materials.Material.VALUE_Ingot);
            ticMaterial.addItem("block" + oreSuffix, 1,
                    slimeknights.tconstruct.library.materials.Material.VALUE_Block);
        }

        MaterialIntegration integration;
        if (fluid != null && oreSuffix != null) {
            integration = new MaterialIntegration(ticMaterial, fluid, oreSuffix);
            integratedFluids.add(fluid);
        } else if (gtMaterial.hasProperty(PropertyKey.INGOT) && oreSuffix != null) {
            integration = new MaterialIntegration("ingot" + oreSuffix, ticMaterial, null, null);
            integration.setRepresentativeItem("ingot" + oreSuffix);
        } else if (gtMaterial.hasProperty(PropertyKey.GEM) && oreSuffix != null) {
            integration = new MaterialIntegration("gem" + oreSuffix, ticMaterial, null, null);
            integration.setRepresentativeItem("gem" + oreSuffix);
        } else {
            integration = new MaterialIntegration(ticMaterial);
        }

        TinkerRegistry.integrate(integration);
        integration.preInit();
        integration.registerFluidBlock(blockRegistry);
        integrations.add(integration);
    }

    private static void registerHarvestLevelNames() {
        // Override TiC names (0–4) with GT naming convention for consistent tooltips
        Map<Integer, String> ticNames = slimeknights.tconstruct.library.utils.HarvestLevels.harvestLevelNames;
        ticNames.put(0, TextFormatting.GOLD + "Wood");
        ticNames.put(1, TextFormatting.DARK_GRAY + "Stone");
        ticNames.put(2, TextFormatting.GRAY + "Iron");
        ticNames.put(3, TextFormatting.AQUA + "Diamond");
        // Level 4 (Cobalt) — no vanilla equivalent, keep TiC's name

        HarvestLevels.getNames().forEach((level, name) -> {
            int colorIndex = Math.min(level - 5, EXTRA_LEVEL_COLORS.length - 1);
            ticNames.put(level, EXTRA_LEVEL_COLORS[colorIndex] + name);
        });
    }

    static void injectTranslation(String ticIdentifier, Material gtMaterial) {
        String key = "material." + ticIdentifier + ".name";
        translationSources.put(key, gtMaterial);
        doInject(key, gtMaterial.getLocalizedName());
    }

    /** Re-injects all cached material name translations after a resource reload (F3+T). */
    public static void reinjectTranslations() {
        translationSources.forEach((key, mat) -> doInject(key, mat.getLocalizedName()));
    }

    private static void doInject(String key, String value) {
        String entry = key + "=" + value + "\n";
        LanguageMap.inject(new ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8)));
    }

    static Fluid getFluid(Material material) {
        if (!material.hasProperty(PropertyKey.FLUID)) return null;
        Fluid fluid = material.getFluid();
        return (FluidRegistry.isFluidRegistered(fluid)) ? fluid : null;
    }

    static List<MaterialIntegration> getIntegrations() {
        return integrations;
    }
}
