package gregtech.api.worldgen.vein;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class VeinHelper {

    private VeinHelper() {}

    public static boolean hasVein(World world, BlockPos pos) {
        OreVeinHandler.OreVeinWorldEntry entry = OreVeinHandler.getOreVeinWorldEntry(
                world, pos.getX() >> 4, pos.getZ() >> 4);
        return entry != null && entry.getType() != null;
    }

    @Nullable
    public static OreVeinHandler.OreVeinWorldEntry getVeinEntry(World world, BlockPos pos) {
        return OreVeinHandler.getOreVeinWorldEntry(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int scanVeinsAround(World world, BlockPos center, int radius) {
        if (world.isRemote) return 0;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                OreVeinHandler.OreVeinWorldEntry data = OreVeinHandler.getOreVeinWorldEntry(
                        world, cx + dx, cz + dz);
                if (data != null && data.getType() != null) count++;
            }
        }
        return count;
    }

    public static List<ItemStack> extractOres(OreVeinHandler.OreVeinWorldEntry entry, Random rand) {
        if (entry == null || entry.getType() == null || entry.getTotalWeight() <= 0) {
            return Collections.emptyList();
        }
        int count = entry.getOreYield();
        if (count <= 0) return Collections.emptyList();

        Map<String, Integer> tally = new LinkedHashMap<>();
        int totalWeight = entry.getTotalWeight();

        for (int i = 0; i < count; i++) {
            OreEntry ore = entry.pickOre(rand.nextInt(totalWeight));
            if (ore != null) {
                tally.merge(ore.oreName, 1, Integer::sum);
            }
        }

        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            ItemStack template = oreNameToItemStack(e.getKey());
            if (template.isEmpty()) continue;
            int remaining = e.getValue();
            int maxStack = template.getMaxStackSize();
            while (remaining > 0) {
                int batch = Math.min(remaining, maxStack);
                ItemStack stack = template.copy();
                stack.setCount(batch);
                result.add(stack);
                remaining -= batch;
            }
        }
        return result;
    }

    public static ItemStack oreNameToItemStack(String oreName) {
        String[] parts = oreName.split(":");
        int meta = 0;
        String domain, path;
        if (parts.length == 3) {
            domain = parts[0];
            path = parts[1];
            try { meta = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { meta = 0; }
        } else if (parts.length == 2) {
            domain = parts[0];
            path = parts[1];
        } else {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = new ResourceLocation(domain, path);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null && block != Blocks.AIR) {
            Item item = Item.getItemFromBlock(block);
            if (item != Items.AIR) return new ItemStack(item, 1, meta);
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item != null && item != Items.AIR) return new ItemStack(item, 1, meta);
        return ItemStack.EMPTY;
    }

    public static List<OreWeightEntry> getOresWithWeightsInDimension(int dimensionId) {
        Map<String, Integer> weightMap = new LinkedHashMap<>();
        for (VeinType type : VeinRegistry.getList()) {
            if (!type.isAllowedInDimension(dimensionId)) continue;
            for (OreEntry ore : type.getOrePool()) {
                weightMap.merge(ore.oreName, ore.weight, Integer::sum);
            }
        }
        List<OreWeightEntry> result = new ArrayList<>(weightMap.size());
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            ItemStack stack = oreNameToItemStack(entry.getKey());
            if (!stack.isEmpty()) result.add(new OreWeightEntry(entry.getValue(), stack));
        }
        return result;
    }

    public static List<ItemStack> pickOresByWeight(List<OreWeightEntry> oreWeights, int count, Random rand) {
        if (oreWeights == null || oreWeights.isEmpty() || count <= 0) return Collections.emptyList();
        int totalWeight = oreWeights.stream().mapToInt(e -> e.weight).sum();
        if (totalWeight <= 0) return Collections.emptyList();
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int roll = rand.nextInt(totalWeight);
            int cursor = 0;
            for (OreWeightEntry entry : oreWeights) {
                cursor += entry.weight;
                if (roll < cursor) {
                    tally.merge(entry.stack.getItem().getRegistryName().toString(), 1, Integer::sum);
                    break;
                }
            }
        }
        return buildStacks(tally, oreWeights);
    }

    public static List<ItemStack> pickDistinctOresByWeight(List<OreWeightEntry> oreWeights, int count, Random rand) {
        if (oreWeights == null || oreWeights.isEmpty() || count <= 0) return Collections.emptyList();
        List<OreWeightEntry> pool = new ArrayList<>(oreWeights);
        List<ItemStack> result = new ArrayList<>(Math.min(count, pool.size()));
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            int total = pool.stream().mapToInt(e -> e.weight).sum();
            if (total <= 0) break;
            int roll = rand.nextInt(total);
            int cursor = 0;
            OreWeightEntry picked = null;
            for (Iterator<OreWeightEntry> it = pool.iterator(); it.hasNext(); ) {
                OreWeightEntry e = it.next();
                cursor += e.weight;
                if (roll < cursor) { picked = e; it.remove(); break; }
            }
            if (picked != null) { ItemStack s = picked.stack.copy(); s.setCount(1); result.add(s); }
        }
        return result;
    }

    private static List<ItemStack> buildStacks(Map<String, Integer> tally, List<OreWeightEntry> source) {
        Map<String, OreWeightEntry> tmplMap = new HashMap<>();
        for (OreWeightEntry e : source) {
            String key = e.stack.getItem().getRegistryName().toString();
            if (!tmplMap.containsKey(key)) tmplMap.put(key, e);
        }
        List<ItemStack> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tally.entrySet()) {
            OreWeightEntry tmpl = tmplMap.get(entry.getKey());
            if (tmpl == null) continue;
            int remaining = entry.getValue();
            int maxStack = tmpl.stack.getMaxStackSize();
            while (remaining > 0) {
                int batch = Math.min(remaining, maxStack);
                ItemStack stack = tmpl.stack.copy();
                stack.setCount(batch);
                result.add(stack);
                remaining -= batch;
            }
        }
        return result;
    }

    public static class OreWeightEntry {
        public final int weight;
        public final ItemStack stack;
        public OreWeightEntry(int weight, ItemStack stack) { this.weight = weight; this.stack = stack; }
    }
}
