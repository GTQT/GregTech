package gregtech.client.renderer.handler.multiblock;

import gregtech.api.util.GTLog;
import gregtech.common.items.behaviors.MultiblockToolBehavior;
import gregtech.common.items.behaviors.multiblock.MultiblockToolMode;
import gregtech.common.network.NetworkHandler;
import gregtech.common.network.multiblock.RotateMoverPreviewPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;

import java.util.UUID;

public final class MoverRotationKeyHandler {
    private static final KeyBinding ROTATE = new KeyBinding(
            "gregtech.key.multiblock_tool.rotate", Keyboard.KEY_R, "key.categories.gregtech");
    private static boolean initialized;

    private MoverRotationKeyHandler() {
    }

    public static synchronized void init() {
        if (initialized) return;
        ClientRegistry.registerKeyBinding(ROTATE);
        MinecraftForge.EVENT_BUS.register(new MoverRotationKeyHandler());
        initialized = true;
        GTLog.logger.info("Registered multiblock mover rotation key: {}",
                ROTATE.getKeyDescription());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (ROTATE.isPressed()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            UUID session = MultiblockMoverPreviewRenderer.getSessionId();
            if (session == null || minecraft.player == null || !isHoldingMover(minecraft)) continue;
            int direction = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? -1 : 1;
            GTLog.logger.info("Sending multiblock mover rotation request for session {}; direction={}",
                    session, direction);
            NetworkHandler.INSTANCE.sendToServer(new RotateMoverPreviewPacket(session, direction));
        }
    }

    private static boolean isHoldingMover(Minecraft minecraft) {
        ItemStack main = minecraft.player.getHeldItemMainhand();
        ItemStack off = minecraft.player.getHeldItemOffhand();
        return MultiblockToolBehavior.isMultiblockTool(main)
                && MultiblockToolMode.get(main) == MultiblockToolMode.MOVE
                || MultiblockToolBehavior.isMultiblockTool(off)
                && MultiblockToolMode.get(off) == MultiblockToolMode.MOVE;
    }
}
