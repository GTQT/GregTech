package gtqt.api.util;

import appeng.api.implementations.ICraftingPatternItem;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class PatternUtils {
    public static void adjustPatternMultipliers(ItemStack patternStack, int divideFactor, int multiplyFactor) {
        if (patternStack.isEmpty() || !(patternStack.getItem() instanceof ICraftingPatternItem)) return;

        NBTTagCompound nbt = patternStack.getTagCompound();
        if (nbt == null) return;

        // 统一收集需要处理的NBT列表
        List<NBTTagList> processingLists = new ArrayList<>(4);

        // 添加基础标签
        processingLists.add(nbt.getTagList("in", 10));
        processingLists.add(nbt.getTagList("out", 10));

        // 添加AE2FC专用标签
        if (nbt.hasKey("Inputs", 9)) {
            processingLists.add(nbt.getTagList("Inputs", 10));
        }
        if (nbt.hasKey("Outputs", 9)) {
            processingLists.add(nbt.getTagList("Outputs", 10));
        }

        // 执行倍除操作
        if (divideFactor > 1) {
            boolean canDivide = processingLists.stream()
                    .allMatch(list -> canSafelyDivide(list, divideFactor));

            if (canDivide) {
                processingLists.forEach(list ->
                        modifyPatternCount(list, divideFactor, Operation.DIVIDE));
            }
        }

        // 执行倍乘操作
        if (multiplyFactor > 1) {
            processingLists.forEach(list ->
                    modifyPatternCount(list, multiplyFactor, Operation.MULTIPLY));
        }

        // 更新所有stackSize标签
        processingLists.forEach(PatternUtils::updateStackSizeTags);
    }
    // 检查列表是否可以安全倍除
    private static boolean canSafelyDivide(NBTTagList list, int divisor) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String countTag = resolveCountTag(tag);
            if (countTag == null) continue;
            long count = tag.getLong(countTag);
            if (count % divisor != 0) {
                return false;
            }
        }
        return true;
    }

    // 修改模式数量
    private static void modifyPatternCount(NBTTagList list, int factor, Operation op) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String countTag = resolveCountTag(tag);
            if (countTag == null) continue;
            long original = tag.getLong(countTag);
            long updated = original;

            switch (op) {
                case DIVIDE:
                    if (original >= factor) {
                        updated = Math.max(1L, original / factor);
                    }
                    break;
                case MULTIPLY:
                    if (original > 0 && original <= Long.MAX_VALUE / factor) {
                        updated = original * factor;
                    }
                    break;
            }
            setCount(tag, countTag, updated);
        }
    }

    // 更新stackSize标签
    private static void updateStackSizeTags(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String countTag = resolveCountTag(tag);
            if (!"Count".equals(countTag)) continue;
            long count = tag.getLong(countTag);
            if (count > 64 && count <= Integer.MAX_VALUE) {
                tag.setInteger("stackSize", (int) count);
            } else {
                tag.removeTag("stackSize");
            }
        }
    }

    private static String resolveCountTag(NBTTagCompound tag) {
        if (tag.hasKey("Cnt")) return "Cnt";
        if (tag.hasKey("Count")) return "Count";
        return null;
    }

    private static void setCount(NBTTagCompound tag, String countTag, long count) {
        if ("Cnt".equals(countTag)) {
            tag.setLong(countTag, count);
        } else {
            tag.setInteger(countTag, (int) Math.min(Integer.MAX_VALUE, count));
        }
    }

    enum Operation {
        DIVIDE,
        MULTIPLY
    }
}
