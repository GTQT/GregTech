package gregtech.api.worldgen.config;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldConfigUtils {

    private WorldConfigUtils() {}

    public static Predicate<WorldProvider> predicateIsSurfaceWorld() {
        return WorldProvider::isSurfaceWorld;
    }

    public static Predicate<WorldProvider> predicateIsNether() {
        return wp -> wp instanceof WorldProviderHell;
    }

    public static Predicate<WorldProvider> predicateIsEnd() {
        return wp -> wp instanceof WorldProviderEnd;
    }

    public static Predicate<WorldProvider> predicateDimension(int dimension) {
        return provider -> provider.getDimension() == dimension;
    }

    public static Predicate<WorldProvider> predicateDimensionName(String name) {
        return provider -> name.equalsIgnoreCase(provider.getDimensionType().getName());
    }

    /**
     * 按生物群系字典标签构建权重修正，规则与原 biome_dictionary JSON 一致
     *
     * @param dictionaryModifiers 字典标签名(如 "ocean")→ 权重
     */
    public static Function<Biome, Integer> biomeWeightModifierDictionary(Map<String, Integer> dictionaryModifiers) {
        HashMap<Type, Integer> backedMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : dictionaryModifiers.entrySet()) {
            String tagName = entry.getKey();
            Type type = resolveBiomeDictionaryType(tagName);
            if (type == null)
                throw new IllegalArgumentException("Couldn't find biome dictionary tag " + tagName);
            backedMap.put(type, entry.getValue());
        }
        return biome -> {
            int totalModifier = 0;
            for (Map.Entry<Type, Integer> entry : backedMap.entrySet()) {
                if (BiomeDictionary.hasType(biome, entry.getKey())) {
                    totalModifier += entry.getValue();
                }
            }
            return totalModifier;
        };
    }

    /**
     * 解析生物群系字典标签，兼容大小写与命名空间前缀差异：
     * 传入 "sandy" / "SANDY" / "overworld/sandy" / "OVERWORLD/SANDY" 均可匹配
     */
    private static Type resolveBiomeDictionaryType(String tagName) {
        // 剥离可选的前缀（如 "overworld/sandy" → "sandy"）
        String suffix = tagName.substring(tagName.lastIndexOf('/') + 1);
        Map<String, Type> byName = ReflectionHelper.getPrivateValue(BiomeDictionary.Type.class, null, "byName");
        if (byName != null) {
            for (Map.Entry<String, Type> entry : byName.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase(tagName) || key.equalsIgnoreCase(suffix)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 按生物群系注册名构建权重修正，规则与原 biome_map JSON 一致
     *
     * @param biomeMap 生物群系注册名(如 "minecraft:ocean")→ 权重
     */
    public static Function<Biome, Integer> biomeWeightModifierMap(Map<String, Integer> biomeMap) {
        HashMap<Biome, Integer> backedMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : biomeMap.entrySet()) {
            ResourceLocation biomeName = new ResourceLocation(entry.getKey());
            Biome biome = GameRegistry.findRegistry(Biome.class).getValue(biomeName);
            if (biome == null)
                throw new IllegalArgumentException("Couldn't find biome with name " + biomeName);
            backedMap.put(biome, entry.getValue());
        }
        return biome -> backedMap.getOrDefault(biome, 0);
    }
}
