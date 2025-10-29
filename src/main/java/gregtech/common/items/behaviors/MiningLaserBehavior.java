package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

import static gregtech.api.GTValues.*;

public class MiningLaserBehavior implements IItemBehaviour {

    // 300k EU
    private static final String MODE_TAG = "MiningLaserMode";

    // 模式定义
    private static final int MODE_MINING = 0;        // 采矿模式
    private static final int MODE_LOW_FOCUS = 1;     // 低聚焦模式
    private static final int MODE_LONG_RANGE = 2;    // 远距模式
    private static final int MODE_HORIZONTAL = 3;    // 水平模式
    private static final int MODE_SUPER_HEAT = 4;    // 超级热线模式
    private static final int MODE_SCATTER = 5;       // 散射模式
    private static final int MODE_EXPLOSIVE = 6;     // 爆破模式
    private static final int MODE_3X3 = 7;           // 3×3模式

    // 能量消耗
    private static final long[] ENERGY_COSTS = {
            VA[LV] * 10L,    // 采矿模式: 320 EU
            VA[LV] * 2L,     // 低聚焦模式: 64 EU
            VA[LV] * 15L,    // 远距模式: 480 EU
            VA[LV] * 15L,    // 水平模式: 480 EU
            VA[MV] * 5L,     // 超级热线模式: 2000 EU
            VA[MV] * 10L,    // 散射模式: 4000 EU
            VA[HV] * 5L,     // 爆破模式: 8000 EU
            VA[LV] * 30L     // 3×3模式: 960 EU
    };

    // 模式名称
    private static final String[] MODE_NAMES = {
            "采矿模式", "低聚焦模式", "远距模式", "水平模式",
            "超级热线模式", "散射模式", "爆破模式", "3×3模式"
    };

    // 模式描述
    private static final String[] MODE_DESCRIPTIONS = {
            "发射激光挖掘一条直线上的方块",
            "短距离低能耗模式，可能点燃方块",
            "长距离高伤害模式",
            "只能水平发射的远距模式",
            "将矿石烧制成成品",
            "3×3范围内5×5散射发射",
            "产生爆炸，具有穿甲效果",
            "3×3断面向前挖掘"
    };

    private final Random random = new Random();

    public MiningLaserBehavior() {}

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;
        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    private int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return MODE_MINING;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getInteger(MODE_TAG) : MODE_MINING;
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.setInteger(MODE_TAG, mode % MODE_NAMES.length);
        }
    }

    private void nextMode(ItemStack stack, EntityPlayer player) {
        int currentMode = getMode(stack);
        int newMode = (currentMode + 1) % MODE_NAMES.length;
        setMode(stack, newMode);

        if (!player.world.isRemote) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "采矿镭射枪模式: " + TextFormatting.YELLOW + MODE_NAMES[newMode] +
                            TextFormatting.GRAY + " (" + ENERGY_COSTS[newMode] + " EU/次)"
            ));
        }
    }

    private boolean isUnbreakableBlock(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 检查黑曜石、防爆石等高硬度方块
        if (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK || block.getBlockHardness(state, world, pos) < 0) {
            return true;
        }

        // 检查爆炸抗性
        return block.getExplosionResistance(world, pos, null, null) > 1000;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (player.isSneaking()) {
            // Shift+右键切换模式
            nextMode(stack, player);
            return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
        }

        int mode = getMode(stack);
        long energyCost = ENERGY_COSTS[mode];

        // 检查能量
        if (!drainEnergy(stack, energyCost, true)) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "能量不足! 需要 " + energyCost + " EU"
                ));
            }
            return ActionResult.newResult(EnumActionResult.FAIL, stack);
        }

        // 执行不同模式的功能
        boolean success = switch (mode) {
            case MODE_MINING -> useMiningMode(world, player);
            case MODE_LOW_FOCUS -> useLowFocusMode(world, player);
            case MODE_LONG_RANGE -> useLongRangeMode(world, player);
            case MODE_HORIZONTAL -> useHorizontalMode(world, player);
            case MODE_SUPER_HEAT -> useSuperHeatMode(world, player);
            case MODE_SCATTER -> useScatterMode(world, player);
            case MODE_EXPLOSIVE -> useExplosiveMode(world, player);
            case MODE_3X3 -> use3x3Mode(world, player);
            default -> false;
        };

        if (success) {
            // 消耗能量
            drainEnergy(stack, energyCost, false);
            player.swingArm(hand);

            // 播放音效
            if (!world.isRemote) {
                world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_FIREWORK_BLAST, SoundCategory.PLAYERS, 0.5f, 1.5f);
            }
        }

        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    private boolean useMiningMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 16.0);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos pos = rayTrace.getBlockPos();
        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d lookVec = player.getLookVec();

        int maxDistance = 16; // 最大挖掘距离
        int actualDistance = 0;

        // 沿着视线方向挖掘
        for (int i = 0; i < maxDistance; i++) {
            BlockPos currentPos = new BlockPos(
                    pos.getX() + lookVec.x * i,
                    pos.getY() + lookVec.y * i,
                    pos.getZ() + lookVec.z * i
            );

            if (world.isAirBlock(currentPos)) continue;

            if (isUnbreakableBlock(world, currentPos)) {
                // 遇到无法破坏的方块就停止
                actualDistance = i;
                break;
            }

            if (breakBlock(world, currentPos, player)) {
                actualDistance = i + 1;
            } else {
                break;
            }
        }

        // 生成激光粒子效果 - 减少粒子数量
        if (world.isRemote && actualDistance > 0) {
            spawnLaserBeam(world, startPos, lookVec, actualDistance, EnumParticleTypes.REDSTONE, 0.8f, 0.1f, 0.1f, 2);
        }

        return actualDistance > 0;
    }

    private boolean useLowFocusMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 8.0); // 短距离
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos pos = rayTrace.getBlockPos();
        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d endPos = rayTrace.hitVec;

        if (breakBlock(world, pos, player)) {
            // 生成橙色激光粒子效果 - 减少粒子数量
            if (world.isRemote) {
                spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.REDSTONE, 1.0f, 0.5f, 0.0f, 1);
            }

            // 低概率点燃方块
            if (random.nextFloat() < 0.1f) {
                BlockPos firePos = pos.offset(rayTrace.sideHit);
                if (world.isAirBlock(firePos) && world.isSideSolid(pos, rayTrace.sideHit.getOpposite())) {
                    world.setBlockState(firePos, Blocks.FIRE.getDefaultState());

                    // 生成少量火焰粒子
                    if (world.isRemote) {
                        for (int i = 0; i < 3; i++) {
                            double x = firePos.getX() + 0.5 + (random.nextDouble() - 0.5);
                            double y = firePos.getY() + 0.5 + (random.nextDouble() - 0.5);
                            double z = firePos.getZ() + 0.5 + (random.nextDouble() - 0.5);
                            world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0, 0.1, 0);
                        }
                    }
                }
            }
            return true;
        }

        return false;
    }

    private boolean useLongRangeMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 64.0); // 长距离
        if (rayTrace == null) return false;

        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d endPos = rayTrace.hitVec;

        if (rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = rayTrace.getBlockPos();
            if (breakBlock(world, pos, player)) {
                // 生成红色长距离激光粒子 - 减少粒子数量
                if (world.isRemote) {
                    spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.REDSTONE, 1.0f, 0.0f, 0.0f, 1);
                }
                return true;
            }
        } else if (rayTrace.typeOfHit == RayTraceResult.Type.ENTITY) {
            // 对实体造成高伤害
            Entity entity = rayTrace.entityHit;
            if (entity instanceof EntityLivingBase) {
                entity.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), 19.0f);

                // 生成少量伤害粒子
                if (world.isRemote) {
                    spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.CRIT, 1.0f, 0.0f, 0.0f, 1);
                }
                return true;
            }
        }

        return false;
    }

    private boolean useHorizontalMode(World world, EntityPlayer player) {
        // 检查角度是否过陡
        if (Math.abs(player.rotationPitch) > 30) {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "采矿激光枪瞄准角度过陡"
                ));
            }
            return false;
        }

        RayTraceResult rayTrace = rayTrace(world, player, false, 64.0);
        if (rayTrace == null) return false;

        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d endPos = rayTrace.hitVec;

        // 生成蓝色水平激光粒子 - 减少粒子数量
        if (world.isRemote) {
            spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.REDSTONE, 0.0f, 0.0f, 1.0f, 1);
        }

        if (rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = rayTrace.getBlockPos();
            return breakBlock(world, pos, player);
        } else if (rayTrace.typeOfHit == RayTraceResult.Type.ENTITY) {
            Entity entity = rayTrace.entityHit;
            if (entity instanceof EntityLivingBase) {
                entity.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), 19.0f);
                return true;
            }
        }

        return false;
    }

    private boolean useSuperHeatMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 16.0);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos pos = rayTrace.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d endPos = rayTrace.hitVec;

        // 获取烧制结果
        ItemStack smeltingResult = FurnaceRecipes.instance().getSmeltingResult(new ItemStack(block));
        if (!smeltingResult.isEmpty()) {
            // 生成火焰激光粒子 - 减少粒子数量
            if (world.isRemote) {
                spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.FLAME, 1.0f, 0.5f, 0.0f, 2);

                // 在目标位置生成少量火焰粒子
                for (int i = 0; i < 4; i++) {
                    double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
                    double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
                    double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
                    world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0, 0.1, 0);
                }
            }

            // 破坏原方块
            world.setBlockToAir(pos);

            // 给予烧制后的物品
            if (!world.isRemote) {
                EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        smeltingResult.copy());
                world.spawnEntity(entityItem);
            }
            return true;
        }

        return false;
    }

    private boolean useScatterMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 16.0);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos centerPos = rayTrace.getBlockPos();
        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d lookVec = player.getLookVec();
        int range = 2; // 3x3范围

        boolean minedAny = false;

        // 在3x3范围内散射挖掘
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int distance = 0; distance < 5; distance++) {
                    BlockPos targetPos = new BlockPos(
                            centerPos.getX() + x + lookVec.x * distance,
                            centerPos.getY() + lookVec.y * distance,
                            centerPos.getZ() + z + lookVec.z * distance
                    );

                    if (breakBlock(world, targetPos, player)) {
                        minedAny = true;
                    } else {
                        break;
                    }
                }
            }
        }

        // 生成少量散射粒子效果
        if (minedAny && world.isRemote) {
            for (int i = 0; i < 3; i++) {
                int x = random.nextInt(5) - 2;
                int z = random.nextInt(5) - 2;

                Vec3d particleStart = new Vec3d(
                        startPos.x + (x * 0.1),
                        startPos.y,
                        startPos.z + (z * 0.1)
                );
                Vec3d particleEnd = new Vec3d(
                        centerPos.getX() + 0.5 + (x * 0.5),
                        centerPos.getY() + 0.5,
                        centerPos.getZ() + 0.5 + (z * 0.5)
                );
                spawnLaserBeam(world, particleStart, particleEnd,
                        EnumParticleTypes.REDSTONE, 0.5f, 0.5f, 1.0f, 1);
            }
        }

        return minedAny;
    }

    private boolean useExplosiveMode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 32.0);
        if (rayTrace == null) return false;

        BlockPos explosionPos;
        if (rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
            explosionPos = rayTrace.getBlockPos();
        } else {
            explosionPos = new BlockPos(rayTrace.hitVec);
        }

        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d endPos = rayTrace.hitVec;

        // 生成爆炸预兆粒子 - 减少粒子数量
        if (world.isRemote) {
            spawnLaserBeam(world, startPos, endPos, EnumParticleTypes.SMOKE_LARGE, 0.3f, 0.3f, 0.3f, 1);

            // 在爆炸位置生成少量警告粒子
            for (int i = 0; i < 8; i++) {
                double x = explosionPos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2;
                double y = explosionPos.getY() + 0.5 + (random.nextDouble() - 0.5) * 2;
                double z = explosionPos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2;
                world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0, 0.1, 0);
            }
        }

        if (!world.isRemote) {
            // 创建爆炸
            world.createExplosion(player, explosionPos.getX(), explosionPos.getY(), explosionPos.getZ(), 4.0f, true);

            // 对附近实体造成伤害（穿甲效果）
            List<Entity> entities = world.getEntitiesWithinAABB(Entity.class,
                    new AxisAlignedBB(explosionPos).grow(8.0));

            for (Entity entity : entities) {
                if (entity instanceof EntityLivingBase && entity != player) {
                    // 100点伤害，无视部分护甲
                    entity.attackEntityFrom(net.minecraft.util.DamageSource.causeExplosionDamage(player), 100.0f);
                }
            }
        }

        return true;
    }

    private boolean use3x3Mode(World world, EntityPlayer player) {
        RayTraceResult rayTrace = rayTrace(world, player, false, 16.0);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos centerPos = rayTrace.getBlockPos();
        Vec3d startPos = getPlayerEyesPos(player);
        Vec3d lookVec = player.getLookVec();
        int range = 1; // 3x3范围

        boolean minedAny = false;

        // 向前挖掘3x3的隧道
        for (int distance = 0; distance < 8; distance++) {
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    BlockPos targetPos = new BlockPos(
                            centerPos.getX() + x + lookVec.x * distance,
                            centerPos.getY() + y + lookVec.y * distance,
                            centerPos.getZ() + lookVec.z * distance
                    );

                    if (breakBlock(world, targetPos, player)) {
                        minedAny = true;
                    }
                }
            }
        }

        // 生成少量绿色3x3挖掘粒子
        if (minedAny && world.isRemote) {
            for (int i = 0; i < 2; i++) {
                int x = random.nextInt(3) - 1;
                int y = random.nextInt(3) - 1;

                Vec3d particleStart = new Vec3d(
                        startPos.x + (x * 0.2),
                        startPos.y + (y * 0.2),
                        startPos.z
                );
                Vec3d particleEnd = new Vec3d(
                        centerPos.getX() + 0.5 + (x * 0.5),
                        centerPos.getY() + 0.5 + (y * 0.5),
                        centerPos.getZ() + 0.5
                );
                spawnLaserBeam(world, particleStart, particleEnd,
                        EnumParticleTypes.VILLAGER_HAPPY, 0.0f, 1.0f, 0.0f, 1);
            }
        }

        return minedAny;
    }

    private boolean breakBlock(World world, BlockPos pos, EntityPlayer player) {
        if (world.isAirBlock(pos)) return false;
        if (isUnbreakableBlock(world, pos)) return false;

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 获取掉落物
        List<ItemStack> drops = block.getDrops(world, pos, state, 0);

        // 给予玩家掉落物
        if (!drops.isEmpty() && !world.isRemote) {
            for (ItemStack drop : drops) {
                if (!player.inventory.addItemStackToInventory(drop)) {
                    EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            drop);
                    world.spawnEntity(entityItem);
                }
            }
            player.openContainer.detectAndSendChanges();
        }

        // 破坏方块
        if (!world.isRemote) {
            world.setBlockToAir(pos);
        } else {
            // 客户端破坏粒子效果
            world.playEvent(2001, pos, Block.getStateId(state));
        }

        return true;
    }

    // 新的粒子效果生成方法 - 只生成1-4条粒子射线
    private void spawnLaserBeam(World world, Vec3d startPos, Vec3d endPos,
                                EnumParticleTypes particleType, float r, float g, float b, int beamCount) {
        Vec3d direction = endPos.subtract(startPos);
        double distance = direction.length();

        // 限制光束数量在1-4之间
        beamCount = Math.min(4, Math.max(1, beamCount));

        for (int beam = 0; beam < beamCount; beam++) {
            // 每条光束使用3-6个粒子
            int particleCount = 3 + random.nextInt(4);

            for (int i = 0; i < particleCount; i++) {
                double progress = (double) i / particleCount;
                // 添加轻微的随机偏移，让光束看起来更自然
                double offsetX = (random.nextDouble() - 0.5) * 0.1;
                double offsetY = (random.nextDouble() - 0.5) * 0.1;
                double offsetZ = (random.nextDouble() - 0.5) * 0.1;

                double x = startPos.x + direction.x * progress + offsetX;
                double y = startPos.y + direction.y * progress + offsetY;
                double z = startPos.z + direction.z * progress + offsetZ;

                if (particleType == EnumParticleTypes.REDSTONE) {
                    world.spawnParticle(particleType, x, y, z, r, g, b);
                } else {
                    world.spawnParticle(particleType, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    private void spawnLaserBeam(World world, Vec3d startPos, Vec3d lookVec, int distance,
                                EnumParticleTypes particleType, float r, float g, float b, int beamCount) {
        Vec3d endPos = startPos.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
        spawnLaserBeam(world, startPos, endPos, particleType, r, g, b, beamCount);
    }

    // 获取玩家眼睛位置
    private Vec3d getPlayerEyesPos(EntityPlayer player) {
        return new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        int mode = getMode(itemStack);
        String modeName = MODE_NAMES[mode];
        String modeDescription = MODE_DESCRIPTIONS[mode];
        long energyCost = ENERGY_COSTS[mode];

        lines.add(TextFormatting.GOLD + "当前模式: " + TextFormatting.YELLOW + modeName);
        lines.add(TextFormatting.GRAY + modeDescription);
        lines.add(TextFormatting.GREEN + "模式耗电: " + TextFormatting.WHITE + energyCost + " EU/次");
        lines.add(TextFormatting.AQUA + "Shift+右键切换模式");

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

            lines.add(TextFormatting.BLUE + "能量: " + chargeColor + charge + TextFormatting.GRAY + " / " +
                    TextFormatting.BLUE + maxCharge + " EU " + TextFormatting.GRAY + "(" +
                    String.format("%.1f", percentage) + "%)");

            // 计算可用次数
            int availableUses = (int) (charge / energyCost);
            lines.add(TextFormatting.LIGHT_PURPLE + "可用次数: " + TextFormatting.WHITE + availableUses + " 次");
        }

        // 显示所有模式列表
        lines.add(TextFormatting.DARK_GRAY + "--- 所有模式 ---");
        for (int i = 0; i < MODE_NAMES.length; i++) {
            String prefix = i == mode ? TextFormatting.GREEN + "▶ " : TextFormatting.GRAY + "  ";
            lines.add(prefix + MODE_NAMES[i] + TextFormatting.DARK_GRAY + " (" + ENERGY_COSTS[i] + " EU)");
        }
    }

    // 改进的射线追踪方法，支持距离参数
    private RayTraceResult rayTrace(World worldIn, EntityPlayer playerIn, boolean useLiquids, double distance) {
        float pitch = playerIn.rotationPitch;
        float yaw = playerIn.rotationYaw;
        Vec3d eyesPos = getPlayerEyesPos(playerIn);
        float f2 = net.minecraft.util.math.MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f3 = net.minecraft.util.math.MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f4 = -net.minecraft.util.math.MathHelper.cos(-pitch * 0.017453292F);
        float f5 = net.minecraft.util.math.MathHelper.sin(-pitch * 0.017453292F);
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        Vec3d endPos = eyesPos.add((double) f6 * distance, (double) f5 * distance, (double) f7 * distance);
        return worldIn.rayTraceBlocks(eyesPos, endPos, useLiquids, !useLiquids, false);
    }
}
