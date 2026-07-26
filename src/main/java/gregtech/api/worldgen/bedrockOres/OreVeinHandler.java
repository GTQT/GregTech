package gregtech.api.worldgen.bedrockOres;

import gregtech.api.GTValues;
import gregtech.api.util.random.XoShiRo256PlusPlusRandom;
import gregtech.api.worldgen.bedrockFluids.ChunkPosDimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class OreVeinHandler {

    private OreVeinHandler() {}

    public static final LinkedHashMap<VeinType, Integer> veinList = new LinkedHashMap<>();
    private static final Map<Integer, Integer> totalWeightMap = new HashMap<>();
    public static final HashMap<ChunkPosDimension, OreVeinWorldEntry> veinCache = new HashMap<>();

    public static final int VEIN_CHUNK_SIZE = 8;
    public static final int MAXIMUM_VEIN_OPERATIONS = 100_000;

    @Nullable
    public static OreVeinWorldEntry getOreVeinWorldEntry(@NotNull World world, int chunkX, int chunkZ) {
        if (world.isRemote) return null;

        ChunkPosDimension coords = new ChunkPosDimension(
                world.provider.getDimension(),
                getVeinCoord(chunkX),
                getVeinCoord(chunkZ));

        OreVeinWorldEntry worldEntry = veinCache.get(coords);
        if (worldEntry == null) {
            VeinType type = null;

            int query = world.getChunk(getVeinCoord(chunkX), getVeinCoord(chunkZ))
                    .getRandomWithSeed(90210).nextInt();

            int totalWeight = getTotalWeight(world.provider.getDimension());
            if (totalWeight > 0) {
                int w = Math.abs(query % totalWeight);
                for (Map.Entry<VeinType, Integer> entry : veinList.entrySet()) {
                    if (entry.getKey().isAllowedInDimension(world.provider.getDimension())) {
                        w -= entry.getValue();
                        if (w < 0) {
                            type = entry.getKey();
                            break;
                        }
                    }
                }
            }

            Random random = new XoShiRo256PlusPlusRandom(
                    31L * 31 * chunkX + chunkZ * 31L + Long.hashCode(world.getSeed()));

            List<OreEntry> selectedOres = Collections.emptyList();
            int oreYield = 0;
            if (type != null) {
                // 从矿物池中确定性抽取 2~4 种
                List<OreEntry> pool = new ArrayList<>(type.getOrePool());
                if (!pool.isEmpty()) {
                    Collections.shuffle(pool, random);
                    int count = type.getMinOreTypes()
                            + random.nextInt(type.getMaxOreTypes() - type.getMinOreTypes() + 1);
                    count = Math.min(count, pool.size());
                    selectedOres = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        OreEntry base = pool.get(i);
                        int perturbedWeight = Math.max(1,
                                (int)(base.weight * (0.7 + random.nextDouble() * 0.6)));
                        selectedOres.add(new OreEntry(base.oreName, perturbedWeight));
                    }
                    veinList.put(type, type.getWeight()); // ensure weight is tracked
                }
                // 产量 = min~max 之间随机
                if (type.getMaxYield() - type.getMinYield() <= 0) {
                    oreYield = type.getMinYield();
                } else {
                    oreYield = type.getMinYield()
                            + random.nextInt(type.getMaxYield() - type.getMinYield());
                }
                oreYield = Math.max(1, oreYield);
            }

            worldEntry = new OreVeinWorldEntry(type, selectedOres, oreYield, type != null ? type.getMaxOperations() : 0);
            veinCache.put(coords, worldEntry);
        }
        return worldEntry;
    }

    public static int getTotalWeight(int dimensionId) {
        if (totalWeightMap.containsKey(dimensionId)) {
            return totalWeightMap.get(dimensionId);
        }
        int totalWeight = 0;
        for (Map.Entry<VeinType, Integer> entry : veinList.entrySet()) {
            if (entry.getKey().isAllowedInDimension(dimensionId)) {
                totalWeight += entry.getValue();
            }
        }
        totalWeightMap.put(dimensionId, totalWeight);
        return totalWeight;
    }

    public static void addOreDeposit(VeinType type) {
        veinList.put(type, type.getWeight());
    }

    public static void recalculateChances() {
        totalWeightMap.clear();
    }

    @Nullable
    public static VeinType getVeinInChunk(World world, int chunkX, int chunkZ) {
        OreVeinWorldEntry info = getOreVeinWorldEntry(world, chunkX, chunkZ);
        if (info == null) return null;
        return info.getType();
    }

    public static int getOreYield(World world, int chunkX, int chunkZ) {
        OreVeinWorldEntry info = getOreVeinWorldEntry(world, chunkX, chunkZ);
        if (info == null) return 0;
        return info.getOreYield();
    }

    public static int getOperationsRemaining(World world, int chunkX, int chunkZ) {
        OreVeinWorldEntry info = getOreVeinWorldEntry(world, chunkX, chunkZ);
        if (info == null) return 0;
        return info.getOperationsRemaining();
    }

    public static List<OreEntry> getOresInChunk(World world, int chunkX, int chunkZ) {
        OreVeinWorldEntry info = getOreVeinWorldEntry(world, chunkX, chunkZ);
        if (info == null) return Collections.emptyList();
        return info.getOres();
    }

    public static void depleteVein(World world, int chunkX, int chunkZ, int amount, boolean ignoreVeinStats) {
        OreVeinWorldEntry info = getOreVeinWorldEntry(world, chunkX, chunkZ);
        if (info == null) return;

        if (ignoreVeinStats) {
            info.decreaseOperations(amount);
            BedrockOreVeinSaveData.setDirty();
            return;
        }

        VeinType definition = info.getType();
        if (definition == null || definition.getDepletionChance() == 0) return;

        if (definition.getDepletionChance() >= 100 ||
                GTValues.RNG.nextInt(100) < definition.getDepletionChance()) {
            info.decreaseOperations(definition.getDepletionAmount());
            BedrockOreVeinSaveData.setDirty();
        }
    }

    public static int getVeinCoord(int chunkCoord) {
        return Math.floorDiv(chunkCoord, VEIN_CHUNK_SIZE);
    }

    // ── Inner class: OreVeinWorldEntry ─────────────────────────────

    public static class OreVeinWorldEntry {

        private VeinType vein;
        private final List<OreEntry> ores = new ArrayList<>();
        private int oreYield;
        private int operationsRemaining;
        private int totalWeight;

        public OreVeinWorldEntry(VeinType vein, List<OreEntry> selectedOres,
                                  int oreYield, int operationsRemaining) {
            this.vein = vein;
            this.ores.addAll(selectedOres);
            this.oreYield = oreYield;
            this.operationsRemaining = operationsRemaining;
            this.totalWeight = selectedOres.stream().mapToInt(o -> o.weight).sum();
        }

        private OreVeinWorldEntry() {}

        @Nullable
        public VeinType getType() { return vein; }

        public List<OreEntry> getOres() { return Collections.unmodifiableList(ores); }

        public int getOreYield() {
            if (vein != null && operationsRemaining <= 0) {
                return Math.max(1, (int)(oreYield * vein.getDepletedYield()));
            }
            return oreYield;
        }

        public int getTotalWeight() { return totalWeight; }

        public int getOperationsRemaining() { return operationsRemaining; }

        public boolean isDepleted() { return operationsRemaining <= 0; }

        public void decreaseOperations(int amount) {
            operationsRemaining = Math.max(0, operationsRemaining - amount);
        }

        public OreEntry pickOre(int rand) {
            if (ores.isEmpty() || totalWeight <= 0) return null;
            int cursor = rand % totalWeight;
            for (OreEntry ore : ores) {
                cursor -= ore.weight;
                if (cursor < 0) return ore;
            }
            return ores.get(ores.size() - 1);
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("oreYield", oreYield);
            tag.setInteger("opsRemaining", operationsRemaining);
            tag.setInteger("totalWeight", totalWeight);
            if (vein != null) {
                tag.setString("veinTypeId", vein.id);
            }
            return tag;
        }

        @NotNull
        public static OreVeinWorldEntry readFromNBT(@NotNull NBTTagCompound tag) {
            OreVeinWorldEntry info = new OreVeinWorldEntry();
            info.oreYield = tag.getInteger("oreYield");
            info.operationsRemaining = tag.getInteger("opsRemaining");
            info.totalWeight = tag.getInteger("totalWeight");
            if (tag.hasKey("veinTypeId")) {
                info.vein = VeinRegistry.get(tag.getString("veinTypeId"));
            }
            return info;
        }
    }
}
