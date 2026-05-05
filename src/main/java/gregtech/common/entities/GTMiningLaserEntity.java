package gregtech.common.entities;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.registry.IThrowableEntity;

import java.util.ArrayList;
import java.util.List;

public class GTMiningLaserEntity extends Entity implements IThrowableEntity {

    private static final float EXPLOSION_POWER = 5.0F;
    private static final float DROP_CHANCE = 0.9F;

    public float range;
    public float power;
    public int blockBreaks;
    public boolean explosive;
    public boolean smelt;
    private EntityLivingBase owner;
    private int ticksInAir;

    public GTMiningLaserEntity(World world) {
        super(world);
        setSize(0.8F, 0.8F);
        ignoreFrustumCheck = true;
    }

    public GTMiningLaserEntity(World world, Vec3d start, Vec3d motion, EntityLivingBase owner, float range, float power,
                               int blockBreaks, boolean explosive, boolean smelt) {
        this(world);
        this.owner = owner;
        setPosition(start.x, start.y, start.z);
        setLaserHeading(motion.x, motion.y, motion.z);
        this.range = range;
        this.power = power;
        this.blockBreaks = blockBreaks;
        this.explosive = explosive;
        this.smelt = smelt;
    }

    @Override
    protected void entityInit() {}

    private void setLaserHeading(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-7D) {
            setDead();
            return;
        }
        motionX = x / length;
        motionY = y / length;
        motionZ = z / length;
        prevRotationYaw = rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
        prevRotationPitch = rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, Math.sqrt(motionX * motionX + motionZ * motionZ)));
    }

    @Override
    public void setVelocity(double x, double y, double z) {
        setLaserHeading(x, y, z);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        spawnTrailParticles();

        if (world.isRemote) {
            setPosition(posX + motionX, posY + motionY, posZ + motionZ);
            return;
        }

        if (range < 1.0F || power <= 0.0F || blockBreaks <= 0) {
            if (explosive) {
                explode();
            }
            setDead();
            return;
        }

        ticksInAir++;
        Vec3d oldPosition = new Vec3d(posX, posY, posZ);
        Vec3d newPosition = oldPosition.add(motionX, motionY, motionZ);
        RayTraceResult blockHit = world.rayTraceBlocks(oldPosition, newPosition, false, true, false);
        if (blockHit != null) {
            newPosition = blockHit.hitVec;
        }

        Entity hitEntity = findHitEntity(oldPosition, newPosition);
        RayTraceResult result = hitEntity == null ? blockHit : new RayTraceResult(hitEntity);

        if (result != null && result.typeOfHit != RayTraceResult.Type.MISS) {
            if (explosive) {
                explode();
                setDead();
                return;
            }

            if (result.typeOfHit == RayTraceResult.Type.ENTITY) {
                if (!hitEntity(result.entityHit)) {
                    if (blockHit != null) {
                        hitBlock(blockHit.getBlockPos(), blockHit.sideHit);
                    } else {
                        power -= 0.5F;
                    }
                }
            } else if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
                if (!hitBlock(result.getBlockPos(), result.sideHit)) {
                    power -= 0.5F;
                }
            }
        } else {
            power -= 0.5F;
        }

        setPosition(posX + motionX, posY + motionY, posZ + motionZ);
        range -= Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (isInWater()) {
            setDead();
        }
    }

    private Entity findHitEntity(Vec3d oldPosition, Vec3d newPosition) {
        Entity hitEntity = null;
        double hitDistance = 0.0D;
        AxisAlignedBB searchBox = getEntityBoundingBox().expand(motionX, motionY, motionZ).grow(1.0D);

        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, searchBox)) {
            if (!entity.canBeCollidedWith() || entity == owner && ticksInAir < 5) {
                continue;
            }

            AxisAlignedBB hitBox = entity.getEntityBoundingBox().grow(0.3D);
            RayTraceResult intercept = hitBox.calculateIntercept(oldPosition, newPosition);
            if (intercept == null) {
                continue;
            }

            double distance = oldPosition.distanceTo(intercept.hitVec);
            if (distance < hitDistance || hitDistance == 0.0D) {
                hitEntity = entity;
                hitDistance = distance;
            }
        }

        return hitEntity;
    }

    private boolean hitEntity(Entity entity) {
        int damage = (int) power;
        if (damage > 0) {
            entity.setFire(damage * (smelt ? 2 : 1));
            DamageSource source = new EntityDamageSourceIndirect("arrow", this, owner).setProjectile();
            entity.attackEntityFrom(source, damage);
        }

        if (entity instanceof MultiPartEntityPart &&
                ((MultiPartEntityPart) entity).parent instanceof EntityDragon) {
            EntityDragon dragon = (EntityDragon) ((MultiPartEntityPart) entity).parent;
            if (dragon.getHealth() <= 0.0F) {
                setDead();
            }
        }

        setDead();
        return true;
    }

    private boolean hitBlock(BlockPos pos, EnumFacing side) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.isAir(state, world, pos) || isGlassLike(block)) {
            return false;
        }

        if (owner instanceof EntityPlayer) {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, (EntityPlayer) owner);
            if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event) || event.getResult() == Event.Result.DENY) {
                setDead();
                return true;
            }
        }

        float hardness = state.getBlockHardness(world, pos);
        if (hardness < 0.0F) {
            setDead();
            return true;
        }

        power -= hardness / 1.5F;
        if (power < 0.0F) {
            return true;
        }

        List<ItemStack> replacements = new ArrayList<>();
        boolean dropBlock = true;

        if (state.getMaterial() == Material.TNT) {
            block.onBlockExploded(world, pos, new Explosion(world, this,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 1.0F, false, true));
            dropBlock = false;
        } else if (smelt) {
            if (state.getMaterial() == Material.ICE) {
                dropBlock = false;
            } else {
                for (ItemStack drop : block.getDrops(world, pos, state, 0)) {
                    ItemStack result = FurnaceRecipes.instance().getSmeltingResult(drop);
                    if (!result.isEmpty()) {
                        ItemStack replacement = result.copy();
                        replacement.setCount(result.getCount() * drop.getCount());
                        replacements.add(replacement);
                    }
                }
                dropBlock = replacements.isEmpty();
            }
        }

        if (dropBlock) {
            block.dropBlockAsItemWithChance(world, pos, state, DROP_CHANCE, 0);
        }

        world.setBlockToAir(pos);
        for (ItemStack replacement : replacements) {
            dropStack(pos, replacement);
            power = 0.0F;
        }

        if (world.rand.nextInt(10) == 0 && state.getMaterial().getCanBurn()) {
            world.setBlockState(pos, Blocks.FIRE.getDefaultState());
        }

        blockBreaks--;
        return true;
    }

    private boolean isGlassLike(Block block) {
        return block == Blocks.GLASS ||
                block == Blocks.STAINED_GLASS ||
                block == Blocks.GLASS_PANE ||
                block == Blocks.STAINED_GLASS_PANE;
    }

    private void dropStack(BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        entityItem.setDefaultPickupDelay();
        world.spawnEntity(entityItem);
    }

    private void explode() {
        world.createExplosion(this, posX, posY, posZ, EXPLOSION_POWER, true);
    }

    private void spawnTrailParticles() {
        if (!world.isRemote) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            double progress = i / 4.0D;
            double x = posX - motionX * progress;
            double y = posY - motionY * progress;
            double z = posZ - motionZ * progress;
            if (smelt) {
                world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
            } else {
                world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 1.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setFloat("Range", range);
        compound.setFloat("Power", power);
        compound.setInteger("BlockBreaks", blockBreaks);
        compound.setBoolean("Explosive", explosive);
        compound.setBoolean("Smelt", smelt);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        range = compound.getFloat("Range");
        power = compound.getFloat("Power");
        blockBreaks = compound.getInteger("BlockBreaks");
        explosive = compound.getBoolean("Explosive");
        smelt = compound.getBoolean("Smelt");
    }

    @Override
    public Entity getThrower() {
        return owner;
    }

    @Override
    public void setThrower(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            owner = (EntityLivingBase) entity;
        }
    }
}
