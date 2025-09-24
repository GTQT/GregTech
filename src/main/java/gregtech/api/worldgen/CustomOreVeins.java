package gregtech.api.worldgen;

import java.io.IOException;

import gregtech.api.util.GTLog;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.worldgen.config.BedrockFluidDepositDefinition;
import gregtech.api.worldgen.config.IWorldgenDefinition;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;

public class CustomOreVeins {

    public static void init() throws IOException {
        GTLog.logger.info("Registering ore veins...");

        WorldGenRegistry worldGenRegistry = WorldGenRegistry.INSTANCE;
        OreDepositBuilder.definitionBuilder("copper")
                .translationKey("gregtech.vein.copper")
                .weight(25)
                .density(0.05f)
                .minHeight(10)
                .maxHeight(80)
                .dimensionFilter("the_end")
                .layeredGeneration(20, 24)
                .layeredFill(Materials.Iron, Materials.Tin, Materials.Cooperite,
                        Materials.Copper)
                .buildAndRegister(worldGenRegistry);

        worldGenRegistry.reinitializeRegisteredVeins();
    }

    public static abstract class DepositBuilder<BuilderType, DefinitionType extends IWorldgenDefinition> {

        protected final JsonObject json = new JsonObject();

        public BuilderType translationKey(@NotNull String translationKey) {
            json.addProperty("name", translationKey);
            return getThis();
        }

        public BuilderType weight(int weight) {
            json.addProperty("weight", weight);
            return getThis();
        }

        public BuilderType dimensionFilter(@NotNull String @NotNull... names) {
            JsonArray parent = new JsonArray();
            for (String name : names) {
                parent.add("name:" + name);
            }

            json.add("dimension_filter", parent);
            return getThis();
        }

        public BuilderType dimensionFilter(int @NotNull... ids) {
            JsonArray parent = new JsonArray();
            for (int id : ids) {
                parent.add("dimension_id:" + id);
            }

            json.add("dimension_filter", parent);
            return getThis();
        }

        public BuilderType overworldOnly() {
            JsonArray parent = new JsonArray();
            parent.add("is_surface_world");

            json.add("dimension_filter", parent);
            return getThis();
        }

        public BuilderType netherOnly() {
            JsonArray parent = new JsonArray();
            parent.add("is_nether");

            json.add("dimension_filter", parent);
            return getThis();
        }

        public abstract BuilderType getThis();

        public void verifyProperties() {
            if (!json.has("weight")) {
                throw new IllegalStateException("DepositBuilder doesn't have a weight!");
            }
        }

        public abstract @NotNull DefinitionType createDefinition();

        public @NotNull DefinitionType build() {
            verifyProperties();
            DefinitionType definition = createDefinition();
            definition.initializeFromConfig(json);
            return definition;
        }

        public void buildAndRegister(@NotNull WorldGenRegistry registry) {
            IWorldgenDefinition definition = build();
            if (definition instanceof OreDepositDefinition oreDeposit) {
                registry.addVeinDefinitions(oreDeposit);
            } else if (definition instanceof BedrockFluidDepositDefinition bedrockFluid) {
                registry.addVeinDefinitions(bedrockFluid);
            }
        }

        public static @NotNull String getMaterialResourceLocation(@NotNull Material material) {
            return material.getResourceLocation().toString();
        }
    }

    public static final class OreDepositBuilder extends DepositBuilder<OreDepositBuilder, OreDepositDefinition> {

        private final String name;

        public static OreDepositBuilder definitionBuilder(@NotNull String name) {
            return new OreDepositBuilder(name);
        }

        private OreDepositBuilder(@NotNull String name) {
            this.name = name;
        }

        public OreDepositBuilder density(float density) {
            json.addProperty("density", density);
            return getThis();
        }

        public OreDepositBuilder minHeight(int minHeight) {
            json.addProperty("min_height", minHeight);
            return getThis();
        }

        public OreDepositBuilder maxHeight(int maxHeight) {
            json.addProperty("max_height", maxHeight);
            return getThis();
        }

        public OreDepositBuilder surfaceRock(@NotNull Material material) {
            JsonObject parent = new JsonObject();
            parent.addProperty("type", "surface_rock");
            parent.addProperty("material", DepositBuilder.getMaterialResourceLocation(material));

            json.add("vein_populator", parent);
            return getThis();
        }

        public OreDepositBuilder layeredGeneration(int radiusMin, int radiusMax) {
            JsonObject parent = new JsonObject();
            parent.addProperty("type", "layered");

            JsonObject radii = new JsonObject();
            radii.addProperty("min", radiusMin);
            radii.addProperty("max", radiusMax);
            parent.add("radius", radii);

            json.add("generator", parent);
            return getThis();
        }

        public OreDepositBuilder layeredFill(@NotNull Material primaryMaterial, @NotNull Material secondaryMaterial,
                                             @NotNull Material betweenMaterial, @NotNull Material sporadicMaterial) {
            JsonObject parent = new JsonObject();
            parent.addProperty("type", "layered");

            JsonArray fillMaterials = new JsonArray();

            JsonObject primary = new JsonObject();
            primary.addProperty("primary", "ore:" + DepositBuilder.getMaterialResourceLocation(primaryMaterial));
            fillMaterials.add(primary);

            JsonObject secondary = new JsonObject();
            secondary.addProperty("secondary", "ore:" + DepositBuilder.getMaterialResourceLocation(secondaryMaterial));
            fillMaterials.add(secondary);

            JsonObject between = new JsonObject();
            between.addProperty("between", "ore:" + DepositBuilder.getMaterialResourceLocation(betweenMaterial));
            fillMaterials.add(between);

            JsonObject sporadic = new JsonObject();
            sporadic.addProperty("sporadic", "ore:" + DepositBuilder.getMaterialResourceLocation(sporadicMaterial));
            fillMaterials.add(sporadic);

            parent.add("values", fillMaterials);

            json.add("filler", parent);
            return getThis();
        }

        @Override
        public OreDepositBuilder getThis() {
            return this;
        }

        @Override
        public void verifyProperties() {
            super.verifyProperties();
            if (!json.has("density")) {
                throw new IllegalStateException("OreDepositBuilder doesn't have a density!");
            } else if (!json.has("filler")) {
                throw new IllegalStateException("OreDepositBuilder doesn't have a filler!");
            } else if (!json.has("generator")) {
                throw new IllegalStateException("OreDepositBuilder doesn't have a generator!");
            }
        }

        @Override
        public @NotNull OreDepositDefinition createDefinition() {
            return new OreDepositDefinition(name);
        }
    }
}
