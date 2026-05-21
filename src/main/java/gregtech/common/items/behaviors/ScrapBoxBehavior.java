package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ScrapBoxBehavior implements IItemBehaviour {

    private static final Map<ItemStack, Double> PRIZE_PROBABILITIES = new LinkedHashMap<>();

    static {
        addPrize(new ItemStack(Items.BLAZE_ROD), 0.08);
        addPrize(new ItemStack(Items.NETHERBRICK), 3.87);
        addPrize(new ItemStack(Items.COOKED_PORKCHOP), 1.74);
        addPrize(new ItemStack(Items.GOLDEN_HELMET), 0.02);
        addPrize(new ItemStack(Items.WOODEN_SHOVEL), 1.93);
        addPrize(new ItemStack(Items.CAKE), 0.97);
        addPrize(new ItemStack(Items.LEATHER), 1.93);
        addPrize(new ItemStack(Items.APPLE), 2.90);
        addPrize(new ItemStack(Blocks.IRON_ORE), 0.97);
        addPrize(new ItemStack(Items.SIGN), 1.93);
        addPrize(new ItemStack(Items.WOODEN_SWORD), 1.93);
        addPrize(new ItemStack(Items.COOKED_BEEF), 1.74);
        addPrize(new ItemStack(Items.DIAMOND), 0.19);
        addPrize(new ItemStack(Items.BONE), 1.93);
        addPrize(new ItemStack(Items.ENDER_PEARL), 0.15);
        addPrize(new ItemStack(Items.REDSTONE), 1.74);
        addPrize(new ItemStack(Items.MINECART), 0.02);
        addPrize(new ItemStack(Blocks.DIRT), 9.67);
        addPrize(new ItemStack(Items.BREAD), 2.90);
        addPrize(new ItemStack(Items.STICK), 7.74);
        addPrize(new ItemStack(Items.WOODEN_PICKAXE), 1.93);
        addPrize(new ItemStack(Items.ROTTEN_FLESH), 3.87);
        addPrize(new ItemStack(Blocks.GRASS), 5.80);
        addPrize(new ItemStack(Items.COOKED_CHICKEN), 1.74);
        addPrize(new ItemStack(Blocks.GOLD_ORE), 0.97);
        addPrize(new ItemStack(Items.GLOWSTONE_DUST), 1.55);
        addPrize(new ItemStack(Items.EMERALD), 0.10);
        addPrize(new ItemStack(Blocks.PUMPKIN), 1.74);
        addPrize(new ItemStack(Blocks.GRAVEL), 5.80);
        addPrize(new ItemStack(Items.WOODEN_HOE), 9.69);
        addPrize(new ItemStack(Items.SLIME_BALL), 1.16);
        addPrize(new ItemStack(Items.FEATHER), 1.93);
        addPrize(new ItemStack(Items.EGG), 1.55);
        addPrize(new ItemStack(Blocks.SOUL_SAND), 1.93);

        addPrize(OreDictUnifier.get(OrePrefix.dust, Materials.Tin), 1.55);
        addPrize(OreDictUnifier.get(OrePrefix.dust, Materials.Iron), 1.35);
        addPrize(OreDictUnifier.get(OrePrefix.dust, Materials.Gold), 1.35);
        addPrize(OreDictUnifier.get(OrePrefix.dust, Materials.Copper), 1.55);

        addPrize(OreDictUnifier.get(OrePrefix.ore, Materials.Iron), 1.35);
        addPrize(OreDictUnifier.get(OrePrefix.ore, Materials.Gold), 1.35);
        addPrize(OreDictUnifier.get(OrePrefix.ore, Materials.Silver), 1.35);
        addPrize(OreDictUnifier.get(OrePrefix.ore, Materials.Copper), 1.35);

        addPrize(OreDictUnifier.get(OrePrefix.ingot, Materials.Rubber), 1.55);

        addPrize(ItemStack.EMPTY, 0.04);
    }

    private final Random random = new Random();

    private static void addPrize(ItemStack stack, double probability) {
        PRIZE_PROBABILITIES.put(stack, probability);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) {
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }

            ItemStack prize = getRandomPrize().copy();

            if (!prize.isEmpty()) {
                player.dropItem(prize, false);

            } else {
                // 未中奖提示
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "很遗憾，这次没有中奖！"));
            }
        }

        return ActionResult.newResult(net.minecraft.util.EnumActionResult.SUCCESS, stack);
    }

    private ItemStack getRandomPrize() {
        double randomValue = random.nextDouble() * 100.0;
        double cumulativeProbability = 0.0;

        for (Map.Entry<ItemStack, Double> entry : PRIZE_PROBABILITIES.entrySet()) {
            cumulativeProbability += entry.getValue();
            if (randomValue <= cumulativeProbability) {
                return entry.getKey();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add("右键打开获得随机物品");
        lines.add("可能是珍贵的资源，也可能是普通的材料");
        lines.add("总共有 " + (PRIZE_PROBABILITIES.size() - 1) + " 种可能的奖品");
    }
}
