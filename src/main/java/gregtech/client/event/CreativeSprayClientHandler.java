package gregtech.client.event;

import gregtech.api.color.ColoredBlockContainer;
import gregtech.common.items.behaviors.spray.CreativeSprayBehavior;
import gregtech.core.network.packets.PacketItemMouseEvent;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.RayTracer;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public final class CreativeSprayClientHandler {

    private CreativeSprayClientHandler() {}

    public static void handleMouseEvent(@NotNull MouseEvent event, @NotNull EntityPlayerSP playerClient,
                                        @NotNull EnumHand hand, @NotNull ItemStack sprayCan,
                                        @NotNull CreativeSprayBehavior behavior) {
        if (event.getButton() != 2 || !event.isButtonstate()) return;

        event.setCanceled(true);
        if (tryCopyColor(playerClient, hand, sprayCan, behavior)) return;

        PacketItemMouseEvent.toServer(buf -> buf.writeByte(0), hand);
    }

    private static boolean tryCopyColor(@NotNull EntityPlayerSP playerClient, @NotNull EnumHand hand,
                                        @NotNull ItemStack sprayCan,
                                        @NotNull CreativeSprayBehavior behavior) {
        RayTraceResult rayTrace = RayTracer.retrace(playerClient);
        if (rayTrace == null || rayTrace.typeOfHit != RayTraceResult.Type.BLOCK) return false;

        World world = playerClient.world;
        BlockPos pos = rayTrace.getBlockPos();
        EnumFacing facing = rayTrace.sideHit;
        ColoredBlockContainer container = ColoredBlockContainer.getContainer(world, pos, facing, playerClient);
        if (container == null) return false;

        return switch (behavior.getColorMode(sprayCan)) {
            case DYE, PREFER_DYE -> {
                if (tryCopyDyeColor(container, world, pos, facing, playerClient, hand, sprayCan, behavior)) {
                    yield true;
                }

                yield tryCopyARGBColor(container, world, pos, facing, playerClient, hand, sprayCan, behavior);
            }
            case ARGB, PREFER_ARGB -> {
                if (tryCopyARGBColor(container, world, pos, facing, playerClient, hand, sprayCan, behavior)) {
                    yield true;
                }

                yield tryCopyDyeColor(container, world, pos, facing, playerClient, hand, sprayCan, behavior);
            }
        };
    }

    private static boolean tryCopyDyeColor(@NotNull ColoredBlockContainer container, @NotNull World world,
                                           @NotNull BlockPos pos, @NotNull EnumFacing facing,
                                           @NotNull EntityPlayerSP playerClient, @NotNull EnumHand hand,
                                           @NotNull ItemStack sprayCan,
                                           @NotNull CreativeSprayBehavior behavior) {
        EnumDyeColor blockColor = container.getColor(world, pos, facing, playerClient);
        if (blockColor == null || blockColor == behavior.getColor(sprayCan)) return false;

        behavior.setColor(sprayCan, blockColor);
        PacketItemMouseEvent.toServer(buf -> buf
                .writeByte(2)
                .writeByte(blockColor.ordinal()), hand);
        return true;
    }

    private static boolean tryCopyARGBColor(@NotNull ColoredBlockContainer container, @NotNull World world,
                                            @NotNull BlockPos pos, @NotNull EnumFacing facing,
                                            @NotNull EntityPlayerSP playerClient, @NotNull EnumHand hand,
                                            @NotNull ItemStack sprayCan,
                                            @NotNull CreativeSprayBehavior behavior) {
        int blockColor = container.getColorInt(world, pos, facing, playerClient);
        if (blockColor == -1 || blockColor == behavior.getColorInt(sprayCan)) return false;

        behavior.setColor(sprayCan, blockColor);
        PacketItemMouseEvent.toServer(buf -> buf
                .writeByte(1)
                .writeInt(blockColor), hand);
        return true;
    }
}
