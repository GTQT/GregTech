package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WindMeterBehavior implements IItemBehaviour {

    private static final String MODE_TAG = "WindMeterMode"; // 0 = Simple, 1 = Detailed
    private static final long SIMPLE_MODE_ENERGY_COST = 30; // EU
    private static final long DETAILED_MODE_ENERGY_COST = 120; // EU

    public WindMeterBehavior() {
    }

    private int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        if (stack.getTagCompound() != null) {
            return stack.getTagCompound().getInteger(MODE_TAG);
        }
        return 0;
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        }
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().setInteger(MODE_TAG, mode);
        }
    }

    private boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    private String getModeName(int mode) {
        return mode == 0 ?
                new TextComponentTranslation("behavior.wind_meter.mode.simple").getUnformattedText() :
                new TextComponentTranslation("behavior.wind_meter.mode.detailed").getUnformattedText();
    }

    private void toggleMode(ItemStack stack, EntityPlayer player) {
        int currentMode = getMode(stack);
        int newMode = (currentMode + 1) % 2;
        setMode(stack, newMode);

        if (!player.world.isRemote) {
            String modeName = getModeName(newMode);
            long energyCost = getEnergyCostForMode(newMode);
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.mode_switched",
                    modeName,
                    energyCost
            ));
        }
    }

    private long getEnergyCostForMode(int mode) {
        return mode == 0 ? SIMPLE_MODE_ENERGY_COST : DETAILED_MODE_ENERGY_COST;
    }

    // 计算风力效率（与风力发电机相同的算法）
    public WindData calculateWindData(World world, BlockPos pos) {
        double heightEfficiency = calculateHeightEfficiency(pos);
        double weatherEfficiency = calculateWeatherEfficiency(world);
        double biomeEfficiency = calculateBiomeEfficiency(world, pos);

        double totalEfficiency = heightEfficiency * weatherEfficiency * biomeEfficiency;

        return new WindData(heightEfficiency, weatherEfficiency, biomeEfficiency, totalEfficiency);
    }

    private double calculateHeightEfficiency(BlockPos pos) {
        int yLevel = pos.getY();
        // 高度效率：每50格+10%，最高300%（3.0倍）
        return Math.min(3.0, 1.0 + (yLevel / 50.0) * 0.1);
    }

    private double calculateWeatherEfficiency(World world) {
        if (world.isThundering()) {
            return 1.3; // 雷暴雨 +30%
        } else if (world.isRaining()) {
            return 1.15; // 雨天 +15%
        }
        return 1.0; // 晴天 基础效率
    }

    private double calculateBiomeEfficiency(World world, BlockPos pos) {
        Biome biome = world.getBiome(pos);
        String biomeName = biome.getBiomeName().toLowerCase();

        // 平原、海洋、山地等开阔地形有更高效率
        if (biomeName.contains("plain") || biomeName.contains("ocean") ||
                biomeName.contains("sea") || biomeName.contains("mountain") ||
                biomeName.contains("hill") || biomeName.contains("plateau")) {
            return 1.1; // 开阔地形 +10%
        }

        // 森林、丛林等封闭地形效率较低
        if (biomeName.contains("forest") || biomeName.contains("jungle") ||
                biomeName.contains("wood") || biomeName.contains("taiga")) {
            return 0.9; // 封闭地形 -10%
        }

        return 1.0; // 其他地形基础效率
    }

    private void tryMeasureWind(World world, EntityPlayer player, BlockPos pos, ItemStack stack) {
        int mode = getMode(stack);
        long energyCost = getEnergyCostForMode(mode);

        // 检查并消耗能量
        if (drainEnergy(stack, energyCost, true)) {
            drainEnergy(stack, energyCost, false);
        } else {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentTranslation(
                        "behavior.wind_meter.insufficient_energy",
                        energyCost
                ));
            }
            return;
        }

        WindData data = calculateWindData(world, pos);
        displayWindInfo(player, pos, data, mode);
    }

    private void displayWindInfo(EntityPlayer player, BlockPos pos, WindData data, int mode) {
        if (mode == 0) {
            // 简单模式
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.header.simple"));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.total_efficiency",
                    String.format("%.1f", data.getTotalEfficiency() * 100)
            ));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.position",
                    pos.getX(), pos.getY(), pos.getZ()
            ));
        } else {
            // 详细模式
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.header.detailed"));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.total_efficiency",
                    String.format("%.1f", data.getTotalEfficiency() * 100)
            ));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.height_efficiency",
                    String.format("%.1f", (data.getHeightEfficiency() - 1.0) * 100),
                    pos.getY()
            ));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.weather_efficiency",
                    String.format("%.1f", (data.getWeatherEfficiency() - 1.0) * 100)
            ));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.biome_efficiency",
                    String.format("%.1f", (data.getBiomeEfficiency() - 1.0) * 100)
            ));
            player.sendMessage(new TextComponentTranslation(
                    "behavior.wind_meter.position_detailed",
                    pos.getX(), pos.getY(), pos.getZ()
            ));
        }

        // 给出建议
        if (data.getTotalEfficiency() > 2.0) {
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.rating.excellent"));
        } else if (data.getTotalEfficiency() > 1.5) {
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.rating.good"));
        } else if (data.getTotalEfficiency() > 1.0) {
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.rating.average"));
        } else {
            player.sendMessage(new TextComponentTranslation("behavior.wind_meter.rating.poor"));
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);

        if (player.isSneaking()) {
            // Shift+右键切换模式
            toggleMode(heldItem, player);
            return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
        } else {
            // 普通右键测量风力
            if (!world.isRemote) {
                BlockPos pos = player.getPosition();
                tryMeasureWind(world, player, pos, heldItem);
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, net.minecraft.util.EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);

        if (player.isSneaking()) {
            // Shift+右键切换模式
            toggleMode(heldItem, player);
            return EnumActionResult.SUCCESS;
        } else {
            // 测量指定位置的风力
            if (!world.isRemote) {
                tryMeasureWind(world, player, pos, heldItem);
            }
            return EnumActionResult.SUCCESS;
        }
    }

    // 添加工具提示
    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        int mode = getMode(itemStack);
        String modeName = getModeName(mode);
        String otherModeName = getModeName((mode + 1) % 2);
        long currentEnergyCost = getEnergyCostForMode(mode);
        long otherEnergyCost = getEnergyCostForMode((mode + 1) % 2);

        lines.add(new TextComponentTranslation("behavior.wind_meter.tooltip.use1").getUnformattedText());
        lines.add(new TextComponentTranslation("behavior.wind_meter.tooltip.use2").getUnformattedText());
        lines.add(new TextComponentTranslation("behavior.wind_meter.tooltip.use3").getUnformattedText());
        lines.add(new TextComponentTranslation(
                "behavior.wind_meter.tooltip.current_mode",
                modeName
        ).getUnformattedText());
        lines.add(new TextComponentTranslation(
                "behavior.wind_meter.tooltip.current_energy_cost",
                currentEnergyCost
        ).getUnformattedText());
        lines.add(new TextComponentTranslation(
                "behavior.wind_meter.tooltip.other_mode",
                otherModeName,
                otherEnergyCost
        ).getUnformattedText());
        lines.add(new TextComponentTranslation("behavior.wind_meter.tooltip.purpose").getUnformattedText());

        // 显示能量信息
        IElectricItem electricItem = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem != null) {
            long charge = electricItem.getCharge();
            long maxCharge = electricItem.getMaxCharge();
            double percentage = (double) charge / maxCharge * 100;

            TextFormatting chargeColor;
            if (percentage > 75) chargeColor = TextFormatting.GREEN;
            else if (percentage > 25) chargeColor = TextFormatting.YELLOW;
            else chargeColor = TextFormatting.RED;

            lines.add(new TextComponentTranslation(
                    "behavior.wind_meter.tooltip.energy",
                    chargeColor.toString() + charge,
                    maxCharge,
                    String.format("%.1f", percentage)
            ).getUnformattedText());

            // 计算可用次数
            int availableUses = (int) (charge / currentEnergyCost);
            lines.add(new TextComponentTranslation(
                    "behavior.wind_meter.tooltip.available_uses",
                    availableUses
            ).getUnformattedText());
        }
    }

    // 风力数据容器类
    public static class WindData {
        private final double heightEfficiency;
        private final double weatherEfficiency;
        private final double biomeEfficiency;
        private final double totalEfficiency;

        public WindData(double heightEfficiency, double weatherEfficiency, double biomeEfficiency, double totalEfficiency) {
            this.heightEfficiency = heightEfficiency;
            this.weatherEfficiency = weatherEfficiency;
            this.biomeEfficiency = biomeEfficiency;
            this.totalEfficiency = totalEfficiency;
        }

        public double getHeightEfficiency() { return heightEfficiency; }
        public double getWeatherEfficiency() { return weatherEfficiency; }
        public double getBiomeEfficiency() { return biomeEfficiency; }
        public double getTotalEfficiency() { return totalEfficiency; }
    }
}
