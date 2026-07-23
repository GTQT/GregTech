package gregtech.integration.tconstruct.village;

import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

/**
 * Central registration point for GT × TiC village structures.
 *
 * <p>
 * Call {@link #register()} during mod init (after both GT and TiC are loaded).
 * This registers structure components with {@link MapGenStructureIO} and
 * village creation handlers with the {@link VillagerRegistry}.
 *
 * <p>
 * <b>Structures added to villages:</b>
 * <ul>
 *   <li>{@link GTVillageSmeltery} — small brick smeltery building</li>
 * </ul>
 */
public final class GTVillageStructures {

    private static boolean registered = false;

    private GTVillageStructures() {}

    /**
     * Register all GT village structures. Safe to call multiple times —
     * only the first call has any effect.
     */
    public static void register() {
        if (registered) return;
        registered = true;

        // Smeltery building
        VillagerRegistry.instance().registerVillageCreationHandler(
                new GTVillageSmeltery.CreationHandler());
        MapGenStructureIO.registerStructureComponent(
                GTVillageSmeltery.class, "gregtech:tic_smeltery_village");
    }
}
