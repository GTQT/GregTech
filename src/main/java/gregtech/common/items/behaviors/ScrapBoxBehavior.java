package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ScrapBoxBehavior implements IItemBehaviour {

    // 直接使用ItemStack和概率的映射
    private static final Map<ItemStack, Double> PRIZE_PROBABILITIES = new LinkedHashMap<>();
    private final Random random = new Random();

    // 静态初始化块，在类加载时构建奖品表
    static {
        // 原版Minecraft物品
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

        // GregTech物品 - 这里需要替换为实际的GregTech物品实例
        // addPrize(MetaItems.TIN_CAN_FULL.getStackForm(), 2.90);
        // addPrize(MetaItems.COAL_DUST.getStackForm(), 1.55);
        // addPrize(MetaItems.TIN_DUST.getStackForm(), 1.55);
        // addPrize(MetaItems.IRON_DUST.getStackForm(), 1.35);
        // addPrize(MetaItems.TIN_ORE.getStackForm(), 1.35);
        // addPrize(MetaItems.GOLD_DUST.getStackForm(), 1.35);
        // addPrize(MetaItems.COPPER_DUST.getStackForm(), 1.55);
        // addPrize(MetaItems.SINGLE_USE_BATTERY.getStackForm(), 1.35);
        // addPrize(MetaItems.COPPER_ORE.getStackForm(), 1.35);
        // addPrize(MetaItems.RUBBER.getStackForm(), 1.55);

        // 临时用原版物品替代GregTech物品作为示例
        addPrize(new ItemStack(Items.BOWL), 2.90); // 替代锡罐
        addPrize(new ItemStack(Items.COAL), 1.55); // 替代煤粉
        addPrize(new ItemStack(Items.IRON_INGOT), 1.55); // 替代锡粉
        addPrize(new ItemStack(Items.IRON_NUGGET), 1.35); // 替代铁粉
        addPrize(new ItemStack(Items.GOLD_INGOT), 1.35); // 替代锡矿石
        addPrize(new ItemStack(Items.GOLD_NUGGET), 1.35); // 替代金粉
        addPrize(new ItemStack(Items.BRICK), 1.55); // 替代铜粉
        addPrize(new ItemStack(Items.SNOWBALL), 1.35); // 替代一次性电池
        addPrize(new ItemStack(Items.CLAY_BALL), 1.35); // 替代铜矿石
        addPrize(new ItemStack(Items.STRING), 1.55); // 替代橡胶

        // 未中奖（空堆栈）
        addPrize(ItemStack.EMPTY, 0.04);
    }

    private static void addPrize(ItemStack stack, double probability) {
        PRIZE_PROBABILITIES.put(stack, probability);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!world.isRemote) {
            // 消耗一个废料箱
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }

            // 抽奖
            ItemStack prize = getRandomPrize().copy(); // 创建副本以避免修改原始堆栈

            if (!prize.isEmpty()) {
                // 给玩家奖品
                if (!player.inventory.addItemStackToInventory(prize)) {
                    // 如果背包满了，掉落在地上
                    player.dropItem(prize, false);
                }

                // 可以添加成功音效
                // world.playSound(null, player.posX, player.posY, player.posZ,
                //     SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F,
                //     ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            } else {
                // 未中奖提示
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "很遗憾，这次没有中奖！"));
            }
        }

        return ActionResult.newResult(net.minecraft.util.EnumActionResult.SUCCESS, stack);
    }

    /**
     * 根据概率表随机获取奖品
     */
    private ItemStack getRandomPrize() {
        double randomValue = random.nextDouble() * 100.0;
        double cumulativeProbability = 0.0;

        for (Map.Entry<ItemStack, Double> entry : PRIZE_PROBABILITIES.entrySet()) {
            cumulativeProbability += entry.getValue();
            if (randomValue <= cumulativeProbability) {
                return entry.getKey();
            }
        }

        // 默认返回空（未中奖）
        return ItemStack.EMPTY;
    }

    @Override
    public void onUpdate(ItemStack itemStack, Entity entity) {
        // 可以在这里添加一些视觉效果
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add("右键打开获得随机物品");
        lines.add("可能是珍贵的资源，也可能是普通的材料");
        lines.add("总共有 " + (PRIZE_PROBABILITIES.size() - 1) + " 种可能的奖品"); // 减去未中奖项
    }

    /**
     * 获取奖品列表（用于调试或显示）
     */
    public static Map<ItemStack, Double> getPrizeProbabilities() {
        return Collections.unmodifiableMap(PRIZE_PROBABILITIES);
    }
}
