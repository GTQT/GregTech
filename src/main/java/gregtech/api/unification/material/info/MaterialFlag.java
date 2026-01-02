package gregtech.api.unification.material.info;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.util.GTLog;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MaterialFlag {

    private static final Set<MaterialFlag> FLAG_REGISTRY = new HashSet<>();

    private final String name;

    private final Set<MaterialFlag> requiredFlags;
    private final Set<PropertyKey<?>> requiredProperties;

    private MaterialFlag(String name, Set<MaterialFlag> requiredFlags, Set<PropertyKey<?>> requiredProperties) {
        this.name = name;
        this.requiredFlags = requiredFlags;
        this.requiredProperties = requiredProperties;
        FLAG_REGISTRY.add(this);
    }

    protected Set<MaterialFlag> verifyFlag(Material material) {
        requiredProperties.forEach(key -> {
            if (!material.hasProperty(key)) {
                GTLog.logger.warn("Material {} does not have required property {} for flag {}!",
                        material.getUnlocalizedName(), key.toString(), this.name);
            }
        });

        Set<MaterialFlag> thisAndDependencies = new HashSet<>(requiredFlags);
        thisAndDependencies.addAll(requiredFlags.stream()
                .map(f -> f.verifyFlag(material))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()));

        return thisAndDependencies;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public static MaterialFlag getByName(String name) {
        return FLAG_REGISTRY.stream().filter(f -> f.toString().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static List<MaterialFlag> getFlagListByName(ArrayList<String> names)
    {
        return names.stream().map(MaterialFlag::getByName).collect(Collectors.toList());
    }

    public static boolean checkMaterialHasFlag(Material material, List<MaterialFlag> whiteList,
                                               List<MaterialFlag> blackList) {
        // 如果黑名单不为空，并且材料有任何一个黑名单标志，则返回false
        if (blackList != null && !blackList.isEmpty() && material.hasAnyOfFlags(blackList)) {
            return false;
        }
        // 如果白名单不为空，则材料必须包含所有白名单标志，否则返回false
        if (whiteList != null && !whiteList.isEmpty() && !material.hasFlags(whiteList)) {
            return false;
        }
        // 两种情况都通过，返回true
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaterialFlag that = (MaterialFlag) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static class Builder {

        final String name;

        final Set<MaterialFlag> requiredFlags = new ObjectOpenHashSet<>();
        final Set<PropertyKey<?>> requiredProperties = new ObjectOpenHashSet<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder requireFlags(MaterialFlag... flags) {
            requiredFlags.addAll(Arrays.asList(flags));
            return this;
        }

        public Builder requireProps(PropertyKey<?>... propertyKeys) {
            requiredProperties.addAll(Arrays.asList(propertyKeys));
            return this;
        }

        public MaterialFlag build() {
            return new MaterialFlag(name, requiredFlags, requiredProperties);
        }
    }
}
