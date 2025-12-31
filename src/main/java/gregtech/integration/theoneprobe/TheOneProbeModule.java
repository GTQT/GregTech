package gregtech.integration.theoneprobe;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.theoneprobe.provider.AEMultiblockHatchProvider;
import gregtech.integration.theoneprobe.provider.ActiveTransformerInfoProvider;
import gregtech.integration.theoneprobe.provider.BatteryBufferInfoProvider;
import gregtech.integration.theoneprobe.provider.BlockOreInfoProvider;
import gregtech.integration.theoneprobe.provider.ControllableInfoProvider;
import gregtech.integration.theoneprobe.provider.ConverterInfoProvider;
import gregtech.integration.theoneprobe.provider.CoverInfoProvider;
import gregtech.integration.theoneprobe.provider.DiodeInfoProvider;
import gregtech.integration.theoneprobe.provider.ElectricContainerInfoProvider;
import gregtech.integration.theoneprobe.provider.HeatPipeInfoProvider;
import gregtech.integration.theoneprobe.provider.LDPipeProvider;
import gregtech.integration.theoneprobe.provider.LampInfoProvider;
import gregtech.integration.theoneprobe.provider.LaserContainerInfoProvider;
import gregtech.integration.theoneprobe.provider.MaintenanceInfoProvider;
import gregtech.integration.theoneprobe.provider.MultiRecipeMapInfoProvider;
import gregtech.integration.theoneprobe.provider.MultiblockInfoProvider;
import gregtech.integration.theoneprobe.provider.MultiblockModeProvider;
import gregtech.integration.theoneprobe.provider.MultiblockThreadProvider;
import gregtech.integration.theoneprobe.provider.PrimitivePumpInfoProvider;
import gregtech.integration.theoneprobe.provider.QuantumStorageProvider;
import gregtech.integration.theoneprobe.provider.RecipeLogicInfoProvider;
import gregtech.integration.theoneprobe.provider.RubberLogInfoProvider;
import gregtech.integration.theoneprobe.provider.SteamBoilerInfoProvider;
import gregtech.integration.theoneprobe.provider.TransformerInfoProvider;
import gregtech.integration.theoneprobe.provider.WorkableInfoProvider;
import gregtech.integration.theoneprobe.provider.debug.DebugPipeNetInfoProvider;
import gregtech.integration.theoneprobe.provider.debug.DebugTickTimeProvider;
import gregtech.modules.GregTechModules;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.ITheOneProbe;

@GregTechModule(
                moduleID = GregTechModules.MODULE_TOP,
                containerID = GTValues.MODID,
                modDependencies = Mods.Names.THE_ONE_PROBE,
                name = "GregTech TheOneProbe Integration",
                description = "TheOneProbe Integration Module")
public class TheOneProbeModule extends IntegrationSubmodule {

    @Override
    public void init(FMLInitializationEvent event) {
        getLogger().info("TheOneProbe found. Enabling integration...");
        ITheOneProbe oneProbe = TheOneProbe.theOneProbeImp;
        oneProbe.registerProvider(new ElectricContainerInfoProvider());
        oneProbe.registerProvider(new HeatPipeInfoProvider());
        oneProbe.registerProvider(new MultiblockThreadProvider());
        oneProbe.registerProvider(new WorkableInfoProvider());
        oneProbe.registerProvider(new ControllableInfoProvider());
        oneProbe.registerProvider(new TransformerInfoProvider());
        oneProbe.registerProvider(new DiodeInfoProvider());
        oneProbe.registerProvider(new MultiblockInfoProvider());
        oneProbe.registerProvider(new MaintenanceInfoProvider());
        oneProbe.registerProvider(new MultiRecipeMapInfoProvider());
        oneProbe.registerProvider(new ConverterInfoProvider());
        oneProbe.registerProvider(new MultiblockModeProvider());
        oneProbe.registerProvider(new RecipeLogicInfoProvider());
        oneProbe.registerProvider(new SteamBoilerInfoProvider());
        oneProbe.registerProvider(new PrimitivePumpInfoProvider());
        oneProbe.registerProvider(new CoverInfoProvider());
        oneProbe.registerProvider(new BlockOreInfoProvider());
        oneProbe.registerProvider(new RubberLogInfoProvider());
        oneProbe.registerProvider(new LampInfoProvider());
        oneProbe.registerProvider(new LDPipeProvider());
        oneProbe.registerProvider(new LaserContainerInfoProvider());
        oneProbe.registerProvider(new QuantumStorageProvider());
        oneProbe.registerProvider(new ActiveTransformerInfoProvider());
        oneProbe.registerProvider(new BatteryBufferInfoProvider());
        oneProbe.registerProvider(new AEMultiblockHatchProvider());

        // Dev environment debug providers
        oneProbe.registerProvider(new DebugPipeNetInfoProvider());
        oneProbe.registerProvider(new DebugTickTimeProvider());
    }
}
