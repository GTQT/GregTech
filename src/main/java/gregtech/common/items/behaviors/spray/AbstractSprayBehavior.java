package gregtech.common.items.behaviors.spray;

import gregtech.api.color.ColorMode;
import gregtech.api.color.ColorModeSupport;
import gregtech.api.color.ColoredBlockContainer;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.common.ConfigHolder;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractSprayBehavior implements IItemBehaviour {

    /**
     * Get the color of the spray can. <br/>
     * {@code null} = solvent
     */
    public abstract @Nullable EnumDyeColor getColor(@NotNull ItemStack sprayCan);

    public int getColorInt(@NotNull ItemStack sprayCan) {
        EnumDyeColor color = getColor(sprayCan);
        return color == null ? -1 : color.colorValue;
    }

    public @Range(from = -1, to = 15) int getColorOrdinal(@NotNull ItemStack sprayCan) {
        EnumDyeColor color = getColor(sprayCan);
        return color == null ? -1 : color.ordinal();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canSpray(@NotNull ItemStack sprayCan) {
        return true;
    }

    public void onSpray(@NotNull EntityPlayer player, @NotNull ItemStack sprayCan) {
        //
    }

    public boolean hasSpraySound(@NotNull ItemStack sprayCan) {
        return true;
    }

    public @NotNull SoundEvent getSpraySound(@NotNull ItemStack sprayCan) {
        return GTSoundEvents.SPRAY_CAN_TOOL;
    }

    public int getMaximumSprayLength(@NotNull ItemStack sprayCan) {
        return ConfigHolder.tools.maxRecursiveSprayLength;
    }

    public static @Nullable AbstractSprayBehavior getSprayCanBehavior(@NotNull ItemStack sprayCan) {
        if (!(sprayCan.getItem() instanceof MetaItem<?>metaItem)) return null;

        for (IItemBehaviour behaviour : metaItem.getBehaviours(sprayCan)) {
            if (behaviour instanceof AbstractSprayBehavior sprayBehavior) {
                return sprayBehavior;
            }
        }

        return null;
    }

    public static boolean isSprayCan(@NotNull ItemStack stack) {
        return getSprayCanBehavior(stack) != null;
    }

    /**
     * Call from your items
     * {@link Item#onItemUseFirst(EntityPlayer, World, BlockPos, EnumFacing, float, float, float, EnumHand)}
     * or the meta item equivalent to check if block is sprayable early enough in the click handling chain.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static @NotNull EnumActionResult handleExternalSpray(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                                @NotNull World world, @NotNull BlockPos pos,
                                                                @NotNull EnumFacing facing) {
        return handleExternalSpray(player, world, pos, facing, player.getHeldItem(hand));
    }

    /**
     * Call from your items
     * {@link Item#onItemUseFirst(EntityPlayer, World, BlockPos, EnumFacing, float, float, float, EnumHand)}
     * or the meta item equivalent to check if block is sprayable early enough in the click handling chain.
     */
    public static @NotNull EnumActionResult handleExternalSpray(@NotNull EntityPlayer player,
                                                                @NotNull World world, @NotNull BlockPos pos,
                                                                @NotNull EnumFacing facing,
                                                                @NotNull ItemStack sprayCan) {
        AbstractSprayBehavior sprayBehavior = getSprayCanBehavior(sprayCan);
        if (sprayBehavior == null) {
            return EnumActionResult.PASS;
        } else {
            return sprayBehavior.spray(player, world, pos, facing, sprayCan);
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack sprayCan = player.getHeldItem(hand);
        EnumActionResult result = spray(player, world, pos, side, sprayCan);
        if (hasSpraySound(sprayCan) && result == EnumActionResult.SUCCESS) {
            world.playSound(null, player.posX, player.posY, player.posZ, getSpraySound(sprayCan), SoundCategory.PLAYERS,
                    1.0f, 1.0f);
        }
        return result;
    }

    @SuppressWarnings("ConstantValue")
    protected @NotNull EnumActionResult spray(@NotNull EntityPlayer player, @NotNull World world, @NotNull BlockPos pos,
                                              @NotNull EnumFacing facing, @NotNull ItemStack sprayCan) {
        if (!canSpray(sprayCan)) {
            return EnumActionResult.PASS;
        } else if (!player.canPlayerEdit(pos, facing, sprayCan)) {
            return EnumActionResult.FAIL;
        }

        if (player.isSneaking()) {
            if (world.getBlockState(pos).getBlock() instanceof BlockPipe<?, ?, ?>blockPipe) {
                IPipeTile<?, ?> firstPipe = blockPipe.getPipeTileEntity(world, pos);
                int color = getColorInt(sprayCan);
                if (firstPipe != null && canPipeBePainted(firstPipe, color)) {
                    if (world.isRemote) return EnumActionResult.SUCCESS;
                    traversePipes(firstPipe, player, sprayCan, color);
                    return EnumActionResult.SUCCESS;
                }
            }
        }

        ColoredBlockContainer colorContainer = ColoredBlockContainer.getContainer(world, pos, facing, player);
        if (colorContainer == null) {
            return EnumActionResult.PASS;
        }

        ColorModeSupport containerColorMode = colorContainer.getSupportedColorMode();
        ColorMode sprayColorMode = getColorMode(sprayCan);
        if (!containerColorMode.supportsMode(sprayColorMode)) {
            if (!world.isRemote) {
                player.sendStatusMessage(containerColorMode.getErrorText(), true);
            }

            return EnumActionResult.FAIL;
        }

        return switch (sprayColorMode) {
            case DYE -> colorContainer.setColor(world, pos, facing, player, getColor(sprayCan));
            case ARGB -> colorContainer.setColor(world, pos, facing, player, getColorInt(sprayCan));
            case PREFER_DYE -> {
                EnumActionResult result = null;
                if (containerColorMode.supportsMode(ColorMode.DYE)) {
                    result = colorContainer.setColor(world, pos, facing, player, getColor(sprayCan));
                } else if (result != EnumActionResult.SUCCESS && containerColorMode.supportsMode(ColorMode.ARGB)) {
                    result = colorContainer.setColor(world, pos, facing, player, getColorInt(sprayCan));
                } else if (result == null) {
                    throw new IllegalStateException(
                            "Container mode didn't support either color mode, this shouldn't be possible!");
                }

                yield result;
            }
            case PREFER_ARGB -> {
                EnumActionResult result = null;
                if (containerColorMode.supportsMode(ColorMode.ARGB)) {
                    result = colorContainer.setColor(world, pos, facing, player, getColorInt(sprayCan));
                } else if (result != EnumActionResult.SUCCESS && containerColorMode.supportsMode(ColorMode.DYE)) {
                    result = colorContainer.setColor(world, pos, facing, player, getColor(sprayCan));
                } else if (result == null) {
                    throw new IllegalStateException(
                            "Container mode didn't support either color mode, this shouldn't be possible!");
                }

                yield result;
            }
        };
    }

    public abstract @NotNull ColorMode getColorMode(@NotNull ItemStack sprayCan);

    protected void traversePipes(@NotNull IPipeTile<?, ?> startPipe,
                                 @NotNull EntityPlayer player, @NotNull ItemStack sprayCan, int color) {
        int maxLength = getMaximumSprayLength(sprayCan);
        Set<BlockPos> visited = new HashSet<>();
        Deque<IPipeTile<?, ?>> queue = new ArrayDeque<>();
        queue.add(startPipe);
        visited.add(startPipe.getPipePos());

        int count = 0;
        while (!queue.isEmpty() && count < maxLength && canSpray(sprayCan)) {
            IPipeTile<?, ?> current = queue.poll();
            if (!canPipeBePainted(current, color)) continue;

            current.setPaintingColor(color);
            onSpray(player, sprayCan);
            count++;

            for (EnumFacing side : EnumFacing.VALUES) {
                if (!current.isConnected(side)) continue;
                BlockPos neighborPos = current.getPipePos().offset(side);
                if (visited.contains(neighborPos)) continue;
                visited.add(neighborPos);
                if (current.getNeighbor(side) instanceof IPipeTile<?, ?>neighbor) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private static boolean canPipeBePainted(@NotNull IPipeTile<?, ?> pipeTile, int color) {
        return pipeTile.isPainted() ? pipeTile.getPaintingColor() != color : color != -1;
    }
}
