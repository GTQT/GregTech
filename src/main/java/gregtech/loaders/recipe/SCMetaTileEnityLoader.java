package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.api.unification.material.SCMaterials;
import gregtech.common.blocks.BlockFissionCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;

import static gregtech.common.blocks.BlockMetalCasing.MetalCasingType.*;
import static gregtech.loaders.recipe.CraftingComponent.*;
import static gregtech.loaders.recipe.CraftingComponent.CABLE;
import static gregtech.loaders.recipe.MetaTileEntityLoader.registerMachineRecipe;

public class SCMetaTileEnityLoader {

    public static void init() {
        ModHandler.addShapedRecipe(true, "fission_reactor", MetaTileEntities.FISSION_REACTOR.getStackForm(), "CSC",
                "RHR", "CWC",
                'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.EV),
                'H', MetaBlocks.FISSION_CASING.getItemVariant(BlockFissionCasing.FissionCasingType.REACTOR_VESSEL),
                'S', SENSOR.getIngredient(GTValues.EV),
                'R', new UnificationEntry(OrePrefix.rotor, Materials.Steel),
                'W', CABLE_QUAD.getIngredient(GTValues.EV));


        ModHandler.addShapedRecipe(true, "heat_exchanger", MetaTileEntities.HEAT_EXCHANGER.getStackForm(),
                "FFF",
                "PCP", "FFF",
                'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV),
                'P', new UnificationEntry(OrePrefix.pipeLargeFluid, SCMaterials.Inconel),
                'F', MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID));


        ModHandler.addShapedRecipe(true, "gas_centrifuge", MetaTileEntities.GAS_CENTRIFUGE.getStackForm(), "FFF",
                "WRW", "CCC",
                'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.EV),
                'R', new UnificationEntry(OrePrefix.rotor, Materials.Titanium),
                'F', MetaBlocks.METAL_CASING.getItemVariant(PTFE_INERT_CASING),
                'W', CABLE_QUAD.getIngredient(GTValues.EV));

        ModHandler.addShapedRecipe(true, "spent_fuel_pool", MetaTileEntities.SPENT_FUEL_POOL.getStackForm(), "PFP",
                "PCP", "PFP",
                'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV),
                'F', MetaBlocks.METAL_CASING.getItemVariant(STAINLESS_CLEAN),
                'P', new UnificationEntry(OrePrefix.plate, Materials.StainlessSteel));

        registerMachineRecipe(MetaTileEntities.DECAY_CHAMBER, "EWE", "CMC", "EWE", 'M', HULL, 'E', SENSOR, 'C', CIRCUIT, 'W', CABLE);

        registerMachineRecipe(MetaTileEntities.RTG, "EWE", "CMC", "EWE", 'M', HULL, 'E', FIELD_GENERATOR, 'C', CIRCUIT, 'W', CABLE);
    }
}
