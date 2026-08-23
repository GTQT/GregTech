package gregtech.api.worldgen.config;

import gregtech.api.util.GTLog;
import gregtech.api.util.Mods;
import gregtech.api.worldgen.WorldgenDefinitions;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidSpringGenerator;
import gregtech.api.worldgen.generator.WorldGeneratorImpl;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.lang.reflect.Field;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

public class WorldGenRegistry {

    public static final WorldGenRegistry INSTANCE = new WorldGenRegistry();

    private final Int2ObjectMap<String> namedDimensions = new Int2ObjectOpenHashMap<>();

    private final List<OreDepositDefinition> registeredVeinDefinitions = new java.util.ArrayList<>();
    private final List<BedrockFluidDepositDefinition> registeredBedrockVeinDefinitions = new java.util.ArrayList<>();
    private final Map<WorldProvider, WorldOreVeinCache> oreVeinCache = new WeakHashMap<>();

    private WorldGenRegistry() {}

    private class WorldOreVeinCache {

        private final List<OreDepositDefinition> worldVeins;
        private final Map<Biome, List<Entry<Integer, OreDepositDefinition>>> biomeVeins = new HashMap<>();

        public WorldOreVeinCache(WorldProvider worldProvider) {
            this.worldVeins = registeredVeinDefinitions.stream()
                    .filter(definition -> definition.getDimensionFilter().test(worldProvider))
                    .collect(Collectors.toList());
        }

        private List<Entry<Integer, OreDepositDefinition>> getBiomeEntry(Biome biome) {
            if (biomeVeins.containsKey(biome))
                return biomeVeins.get(biome);
            List<Entry<Integer, OreDepositDefinition>> result = worldVeins.stream()
                    .map(vein -> new SimpleEntry<>(vein.getWeight() + vein.getBiomeWeightModifier().apply(biome), vein))
                    .filter(entry -> entry.getKey() > 0)
                    .collect(Collectors.toList());
            biomeVeins.put(biome, result);
            return result;
        }
    }

    public List<Entry<Integer, OreDepositDefinition>> getCachedBiomeVeins(WorldProvider provider, Biome biome) {
        if (oreVeinCache.containsKey(provider))
            return oreVeinCache.get(provider).getBiomeEntry(biome);
        WorldOreVeinCache worldOreVeinCache = new WorldOreVeinCache(provider);
        oreVeinCache.put(provider, worldOreVeinCache);
        return worldOreVeinCache.getBiomeEntry(biome);
    }

    /**
     * 初始化矿脉注册表：注册世界生成器与所有默认定义（纯代码，无 JSON/config 文件）
     */
    public void initializeRegistry() {
        GameRegistry.registerWorldGenerator(WorldGeneratorImpl.INSTANCE, 1);
        GameRegistry.registerWorldGenerator(BedrockFluidSpringGenerator.INSTANCE, 0);
        MinecraftForge.ORE_GEN_BUS.register(WorldGeneratorImpl.class);
        registerNamedDimensions();
        WorldgenDefinitions.registerAll(this);
        if (Mods.GalacticraftCore.isModLoaded()) {
            try {
                Class<?> transformerHooksClass = Class.forName("micdoodle8.mods.galacticraft.core.TransformerHooks");
                Field otherModGeneratorsWhitelistField = transformerHooksClass
                        .getDeclaredField("otherModGeneratorsWhitelist");
                otherModGeneratorsWhitelistField.setAccessible(true);
                List<IWorldGenerator> otherModGeneratorsWhitelist = (List<IWorldGenerator>) otherModGeneratorsWhitelistField
                        .get(null);
                otherModGeneratorsWhitelist.add(WorldGeneratorImpl.INSTANCE);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                GTLog.logger.fatal("Failed to inject world generator into Galacticraft's whitelist.", e);
            }
        }
    }

    private void registerNamedDimensions() {
        namedDimensions.put(0, "Overworld");
        namedDimensions.put(1, "End");
        namedDimensions.put(-1, "Nether");
    }

    /**
     * 代码注册普通矿脉定义（addon 直接传入已构建完成的定义）
     */
    public void addVeinDefinitions(OreDepositDefinition definition) {
        registeredVeinDefinitions.add(definition);
    }

    /**
     * 代码注册基岩流体矿脉定义（addon 直接传入已构建完成的定义）
     */
    public void addVeinDefinitions(BedrockFluidDepositDefinition definition) {
        registeredBedrockVeinDefinitions.add(definition);
    }

    public static List<OreDepositDefinition> getOreDeposits() {
        return Collections.unmodifiableList(INSTANCE.registeredVeinDefinitions);
    }

    public static List<BedrockFluidDepositDefinition> getBedrockVeinDeposits() {
        return Collections.unmodifiableList(INSTANCE.registeredBedrockVeinDefinitions);
    }

    public static Int2ObjectMap<String> getNamedDimensions() {
        return INSTANCE.namedDimensions;
    }
}
