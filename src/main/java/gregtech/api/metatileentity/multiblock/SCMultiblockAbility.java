package gregtech.api.metatileentity.multiblock;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import net.minecraftforge.items.IItemHandlerModifiable;
import gregtech.api.capability.ICoolantHandler;
import gregtech.api.capability.IFuelRodHandler;
import gregtech.api.capability.INuclearExtend;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityControlRodPort;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityModeratorPort;

@SuppressWarnings("InstantiationOfUtilityClass")
public class SCMultiblockAbility {

    public static final MultiblockAbility<IFuelRodHandler> IMPORT_FUEL_ROD = new MultiblockAbility<>("import_fuel_rod", IFuelRodHandler.class);
    public static final MultiblockAbility<IItemHandlerModifiable> EXPORT_FUEL_ROD = new MultiblockAbility<>("export_fuel_rod", IItemHandlerModifiable.class);
    public static final MultiblockAbility<ICoolantHandler> IMPORT_COOLANT = new MultiblockAbility<>("import_coolant", ICoolantHandler.class);
    public static final MultiblockAbility<ICoolantHandler> EXPORT_COOLANT = new MultiblockAbility<>("export_coolant", ICoolantHandler.class);

    public static final MultiblockAbility<INuclearExtend> REACTOR_EXTEND_HATCH = new MultiblockAbility<>("reactor_extend_hatch", INuclearExtend.class);

    public static final MultiblockAbility<MetaTileEntityControlRodPort> CONTROL_ROD_PORT = new MultiblockAbility<>("control_rod_port", MetaTileEntityControlRodPort.class);

    public static final MultiblockAbility<MetaTileEntityModeratorPort> MODERATOR_PORT = new MultiblockAbility<>("moderator_port", MetaTileEntityModeratorPort.class);
}
