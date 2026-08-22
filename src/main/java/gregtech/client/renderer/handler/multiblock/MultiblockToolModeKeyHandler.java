package gregtech.client.renderer.handler.multiblock;

import gregtech.client.renderer.handler.GhostBlockRenderer;
import gregtech.common.items.behaviors.MultiblockToolBehavior;
import gregtech.common.network.NetworkHandler;
import gregtech.common.network.multiblock.SwitchMultiblockToolModePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;

public final class MultiblockToolModeKeyHandler {
    private static final KeyBinding SWITCH_MODE = new KeyBinding(
            "gregtech.key.multiblock_tool.switch_mode", Keyboard.KEY_M, "key.categories.gregtech");
    private static boolean initialized;

    private MultiblockToolModeKeyHandler() {}

    public static synchronized void init() {
        if (initialized) return;
        ClientRegistry.registerKeyBinding(SWITCH_MODE);
        MinecraftForge.EVENT_BUS.register(new MultiblockToolModeKeyHandler());
        initialized = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (SWITCH_MODE.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.currentScreen != null) continue;
            EnumHand hand;
            if (MultiblockToolBehavior.isMultiblockTool(mc.player.getHeldItemMainhand())) {
                hand = EnumHand.MAIN_HAND;
            } else if (MultiblockToolBehavior.isMultiblockTool(mc.player.getHeldItemOffhand())) {
                hand = EnumHand.OFF_HAND;
            } else {
                continue;
            }
            GhostBlockRenderer.resetGhostRender();
            NetworkHandler.INSTANCE.sendToServer(new SwitchMultiblockToolModePacket(hand));
        }
    }
}
