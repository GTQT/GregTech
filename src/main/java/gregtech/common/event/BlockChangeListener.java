package gregtech.common.event;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.multiblock.AsyncStructureChecker;
import gregtech.api.metatileentity.multiblock.MultiblockWorldData;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Listens for block changes in the world and notifies the multiblock registry.
 * This enables event-driven structure checking instead of periodic polling.
 *
 * Also drives the async structure checker lifecycle (P2):
 * - Starts/stops with server
 * - Prepares snapshots and processes results each tick
 *
 * Covers the following scenarios:
 * - Player breaking blocks (BreakEvent)
 * - Player/machine placing blocks (PlaceEvent)
 * - Block neighbor notifications (NeighborNotifyEvent) - covers pistons, fluids, etc.
 * - World unload cleanup
 */
@Mod.EventBusSubscriber(modid = GTValues.MODID)
public class BlockChangeListener {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        World world = (World) event.getWorld();
        if (world.isRemote) return;
        MultiblockWorldData.get(world).onBlockChanged(event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.PlaceEvent event) {
        World world = (World) event.getWorld();
        if (world.isRemote) return;
        MultiblockWorldData.get(world).onBlockChanged(event.getPos());
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = (World) event.getWorld();
        if (world.isRemote) return;

        // The source block changed
        MultiblockWorldData data = MultiblockWorldData.get(world);
        data.onBlockChanged(event.getPos());

        // Also check notified neighbors (covers pistons moving blocks, etc.)
        BlockPos sourcePos = event.getPos();
        for (EnumFacing facing : event.getNotifiedSides()) {
            data.onBlockChanged(sourcePos.offset(facing));
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = (World) event.getWorld();
        if (world.isRemote) return;
        MultiblockWorldData.remove(world);
        AsyncStructureChecker.getInstance().clearWorld(world);
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = (World) event.getWorld();
        if (world.isRemote) return;
        // Only start async checker if enabled in config
        if (gregtech.common.ConfigHolder.machines.enableAsyncStructureCheck) {
            AsyncStructureChecker.getInstance().start();
        }
    }

    /**
     * Server tick handler for async structure checking (P2).
     * Prepares snapshots at the start of each tick and processes results at the end.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        AsyncStructureChecker checker = AsyncStructureChecker.getInstance();
        if (!checker.isRunning()) return;

        if (event.phase == TickEvent.Phase.START) {
            // Prepare block state snapshots for async checking on main thread
            checker.prepareSnapshots();
        } else {
            // Process results from async checks on main thread
            checker.processResults();
        }
    }
}
