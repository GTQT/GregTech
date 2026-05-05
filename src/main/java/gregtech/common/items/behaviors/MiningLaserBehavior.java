package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.common.entities.GTMiningLaserEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MiningLaserBehavior implements IItemBehaviour {

    private static final String MODE_TAG = "MiningLaserMode";

    private static final int MODE_MINING = 0;
    private static final int MODE_LOW_FOCUS = 1;
    private static final int MODE_LONG_RANGE = 2;
    private static final int MODE_HORIZONTAL = 3;
    private static final int MODE_SUPER_HEAT = 4;
    private static final int MODE_SCATTER = 5;
    private static final int MODE_EXPLOSIVE = 6;
    private static final int MODE_3X3 = 7;
    private static final int MODE_COUNT = 8;

    private static final double LASER_SPEED = 1.0D;
    private static final long[] ENERGY_COSTS = {
            1250L,
            100L,
            5000L,
            3000L,
            2500L,
            10000L,
            5000L,
            3000L
    };

    public MiningLaserBehavior() {}

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;
        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    private static Vec3d normalize(Vec3d vec) {
        double length = vec.length();
        return length < 1.0E-7D ? Vec3d.ZERO : new Vec3d(vec.x / length, vec.y / length, vec.z / length);
    }

    private int getMode(ItemStack stack) {
        if (!stack.hasTagCompound() || stack.getTagCompound() == null) return MODE_MINING;
        int mode = stack.getTagCompound().getInteger(MODE_TAG);
        return mode >= 0 && mode < MODE_COUNT ? mode : MODE_MINING;
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().setInteger(MODE_TAG, Math.floorMod(mode, MODE_COUNT));
        }
    }

    private void nextMode(ItemStack stack, EntityPlayer player) {
        int newMode = (getMode(stack) + 1) % MODE_COUNT;
        setMode(stack, newMode);

        if (!player.world.isRemote) {
            player.sendMessage(new TextComponentTranslation(
                    "behavior.mining_laser.mode_switched",
                    new TextComponentTranslation(getModeTranslationKey(newMode)),
                    ENERGY_COSTS[newMode]
            ));
        }
    }

    private String getModeTranslationKey(int mode) {
        switch (mode) {
            case MODE_MINING:
                return "behavior.mining_laser.mode.mining";
            case MODE_LOW_FOCUS:
                return "behavior.mining_laser.mode.low_focus";
            case MODE_LONG_RANGE:
                return "behavior.mining_laser.mode.long_range";
            case MODE_HORIZONTAL:
                return "behavior.mining_laser.mode.horizontal";
            case MODE_SUPER_HEAT:
                return "behavior.mining_laser.mode.super_heat";
            case MODE_SCATTER:
                return "behavior.mining_laser.mode.scatter";
            case MODE_EXPLOSIVE:
                return "behavior.mining_laser.mode.explosive";
            case MODE_3X3:
                return "behavior.mining_laser.mode.3x3";
            default:
                return "behavior.mining_laser.mode.unknown";
        }
    }

    private String getModeDescriptionKey(int mode) {
        switch (mode) {
            case MODE_MINING:
                return "behavior.mining_laser.mode.mining.description";
            case MODE_LOW_FOCUS:
                return "behavior.mining_laser.mode.low_focus.description";
            case MODE_LONG_RANGE:
                return "behavior.mining_laser.mode.long_range.description";
            case MODE_HORIZONTAL:
                return "behavior.mining_laser.mode.horizontal.description";
            case MODE_SUPER_HEAT:
                return "behavior.mining_laser.mode.super_heat.description";
            case MODE_SCATTER:
                return "behavior.mining_laser.mode.scatter.description";
            case MODE_EXPLOSIVE:
                return "behavior.mining_laser.mode.explosive.description";
            case MODE_3X3:
                return "behavior.mining_laser.mode.3x3.description";
            default:
                return "";
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (player.isSneaking()) {
            nextMode(stack, player);
            return success(stack);
        }

        int mode = getMode(stack);
        if (mode == MODE_HORIZONTAL || mode == MODE_3X3) {
            return pass(stack);
        }

        if (world.isRemote) {
            return success(stack);
        }

        if (!drainEnergy(stack, ENERGY_COSTS[mode], true)) {
            player.sendMessage(new TextComponentTranslation("behavior.mining_laser.insufficient_energy", ENERGY_COSTS[mode]));
            return fail(stack);
        }

        boolean shot = false;
        switch (mode) {
            case MODE_MINING:
                shot = shootLaser(world, player, player.getLookVec(), Float.POSITIVE_INFINITY, 5.0F, Integer.MAX_VALUE,
                        false, false);
                break;
            case MODE_LOW_FOCUS:
                shot = shootLaser(world, player, player.getLookVec(), 4.0F, 5.0F, 1, false, false);
                break;
            case MODE_LONG_RANGE:
                shot = shootLaser(world, player, player.getLookVec(), Float.POSITIVE_INFINITY, 20.0F, Integer.MAX_VALUE,
                        false, false);
                break;
            case MODE_SUPER_HEAT:
                shot = shootLaser(world, player, player.getLookVec(), Float.POSITIVE_INFINITY, 8.0F, Integer.MAX_VALUE,
                        false, true);
                break;
            case MODE_SCATTER:
                shootScatter(world, player);
                shot = true;
                break;
            case MODE_EXPLOSIVE:
                shot = shootLaser(world, player, player.getLookVec(), Float.POSITIVE_INFINITY, 12.0F, Integer.MAX_VALUE,
                        true, false);
                break;
            default:
                break;
        }

        if (shot) {
            drainEnergy(stack, ENERGY_COSTS[mode], false);
            player.swingArm(hand);
            playShotSound(world, player, mode);
            return success(stack);
        }

        return fail(stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (player.isSneaking()) {
            nextMode(stack, player);
            return EnumActionResult.SUCCESS;
        }

        int mode = getMode(stack);
        if (mode != MODE_HORIZONTAL && mode != MODE_3X3) {
            return EnumActionResult.PASS;
        }

        if (world.isRemote) {
            return EnumActionResult.PASS;
        }

        Vec3d direction = normalize(player.getLookVec());
        if (direction == Vec3d.ZERO) {
            return EnumActionResult.FAIL;
        }

        double vertical = Math.abs(direction.y);
        boolean mostlyHorizontal = vertical < 1.0D / Math.sqrt(2.0D);
        boolean mostlyVertical = !mostlyHorizontal;

        if (mode == MODE_HORIZONTAL && mostlyVertical) {
            player.sendMessage(new TextComponentTranslation("behavior.mining_laser.steep_angle"));
            return EnumActionResult.FAIL;
        }

        if (!drainEnergy(stack, ENERGY_COSTS[mode], true)) {
            player.sendMessage(new TextComponentTranslation("behavior.mining_laser.insufficient_energy", ENERGY_COSTS[mode]));
            return EnumActionResult.FAIL;
        }

        Vec3d start;
        if (mostlyHorizontal) {
            direction = normalize(new Vec3d(direction.x, 0.0D, direction.z));
            start = new Vec3d(player.posX, pos.getY() + 0.5D, player.posZ).add(direction.scale(0.2D));
        } else {
            direction = normalize(new Vec3d(0.0D, direction.y, 0.0D));
            start = new Vec3d(pos.getX() + 0.5D, player.posY + player.getEyeHeight(), pos.getZ() + 0.5D)
                    .add(direction.scale(0.2D));
        }

        boolean shot;
        if (mode == MODE_HORIZONTAL) {
            shot = shootLaser(world, start, direction, player, Float.POSITIVE_INFINITY, 5.0F, Integer.MAX_VALUE,
                    false, false);
        } else {
            shot = shoot3x3(world, player, start, direction, mostlyHorizontal);
        }

        if (shot) {
            drainEnergy(stack, ENERGY_COSTS[mode], false);
            player.swingArm(hand);
            playShotSound(world, player, mode);
            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.FAIL;
    }

    private void shootScatter(World world, EntityPlayer player) {
        Vec3d look = normalize(player.getLookVec());
        Vec3d right = look.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.length() < 1.0E-4D) {
            double yaw = Math.toRadians(player.rotationYaw) - Math.PI / 2.0D;
            right = new Vec3d(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        } else {
            right = normalize(right);
        }
        Vec3d up = normalize(right.crossProduct(look));
        Vec3d base = look.scale(8.0D);

        for (int r = -2; r <= 2; r++) {
            for (int u = -2; u <= 2; u++) {
                Vec3d dir = normalize(base.add(right.scale(r)).add(up.scale(u)));
                shootLaser(world, player, dir, Float.POSITIVE_INFINITY, 12.0F, Integer.MAX_VALUE, false, false);
            }
        }
    }

    private boolean shoot3x3(World world, EntityPlayer player, Vec3d start, Vec3d direction, boolean horizontal) {
        Vec3d right;
        Vec3d up;
        if (horizontal) {
            up = new Vec3d(0.0D, 1.0D, 0.0D);
            EnumFacing facing = player.getHorizontalFacing();
            right = facing.getAxis() == EnumFacing.Axis.Z ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
            up = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        boolean anyShot = false;
        for (int r = -1; r <= 1; r++) {
            for (int u = -1; u <= 1; u++) {
                Vec3d offsetStart = start.add(right.scale(r)).add(up.scale(u));
                anyShot |= shootLaser(world, offsetStart, direction, player, Float.POSITIVE_INFINITY, 5.0F,
                        Integer.MAX_VALUE, false, false);
            }
        }
        return anyShot;
    }

    private boolean shootLaser(World world, EntityPlayer player, Vec3d direction, float range, float power,
                               int blockBreaks, boolean explosive, boolean smelt) {
        Vec3d start = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ)
                .add(normalize(direction).scale(0.2D));
        return shootLaser(world, start, direction, player, range, power, blockBreaks, explosive, smelt);
    }

    private boolean shootLaser(World world, Vec3d start, Vec3d direction, EntityPlayer player, float range, float power,
                               int blockBreaks, boolean explosive, boolean smelt) {
        Vec3d normalized = normalize(direction);
        if (normalized == Vec3d.ZERO) return false;
        GTMiningLaserEntity entity = new GTMiningLaserEntity(world, start, normalized.scale(LASER_SPEED), player, range,
                power, blockBreaks, explosive, smelt);
        return world.spawnEntity(entity);
    }

    private void playShotSound(World world, EntityPlayer player, int mode) {
        float pitch;
        switch (mode) {
            case MODE_LOW_FOCUS:
                pitch = 1.8F;
                break;
            case MODE_LONG_RANGE:
                pitch = 0.8F;
                break;
            case MODE_EXPLOSIVE:
                pitch = 0.6F;
                break;
            case MODE_SCATTER:
            case MODE_3X3:
                pitch = 1.2F;
                break;
            default:
                pitch = 1.0F;
                break;
        }
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_FIREWORK_BLAST,
                SoundCategory.PLAYERS, 0.5F, pitch);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        int mode = getMode(itemStack);
        String modeName = I18n.format(getModeTranslationKey(mode));
        long energyCost = ENERGY_COSTS[mode];

        lines.add(TextFormatting.GOLD + I18n.format("behavior.mining_laser.tooltip.current_mode", modeName));
        lines.add(TextFormatting.GRAY + I18n.format(getModeDescriptionKey(mode)));
        lines.add(TextFormatting.GREEN + I18n.format("behavior.mining_laser.tooltip.energy_cost", energyCost));
        lines.add(TextFormatting.AQUA + I18n.format("behavior.mining_laser.tooltip.mode_switch"));

        IElectricItem electricItem = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem != null) {
            long charge = electricItem.getCharge();
            long maxCharge = electricItem.getMaxCharge();
            double percentage = maxCharge <= 0 ? 0.0D : (double) charge / maxCharge * 100.0D;

            TextFormatting chargeColor = percentage > 75.0D ? TextFormatting.GREEN :
                    percentage > 25.0D ? TextFormatting.YELLOW : TextFormatting.RED;
            lines.add(TextFormatting.BLUE + I18n.format("behavior.mining_laser.tooltip.energy",
                    chargeColor + String.valueOf(charge), String.valueOf(maxCharge), String.format("%.1f", percentage)));
            lines.add(TextFormatting.LIGHT_PURPLE + I18n.format("behavior.mining_laser.tooltip.available_uses",
                    energyCost <= 0 ? 0 : charge / energyCost));
        }

        lines.add(TextFormatting.DARK_GRAY + I18n.format("behavior.mining_laser.tooltip.all_modes"));
        for (int i = 0; i < MODE_COUNT; i++) {
            String prefix = i == mode ? TextFormatting.GREEN + "> " : TextFormatting.GRAY + "  ";
            lines.add(prefix + I18n.format(getModeTranslationKey(i)) + TextFormatting.DARK_GRAY + " (" + ENERGY_COSTS[i] + " EU)");
        }
    }
}
