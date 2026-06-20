package gregicality.multiblocks.api.unification;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.material.properties.BlastProperty;

import static gregicality.multiblocks.api.unification.GCYMMaterials.*;
import static gregicality.multiblocks.api.utils.GCYMUtil.gcymId;
import static gregtech.api.GTValues.*;
import static gregtech.api.unification.material.Materials.*;

public final class GCYMFirstDegreeMaterials {

    static int startID = 0;

    private GCYMFirstDegreeMaterials() {
    }

    public static int getStartID() {
        return startID++;
    }

    public static void init() {
        Stellite = new Material.Builder(getStartID(), gcymId("stellite"))
                .ingot().fluid()
                .color(0xDEDEFF).iconSet(MaterialIconSet.METALLIC)
                .components(Materials.Cobalt, 7, Materials.Chrome, 7, Materials.Manganese, 4, Materials.Titanium, 2)
                .blast(b -> b.temp(4585, BlastProperty.GasTier.HIGH).blastStats(VA[EV]))
                .mix(VA[EV], 80)
                .build();

        WatertightSteel = new Material.Builder(getStartID(), gcymId("watertight_steel"))
                .ingot().fluid()
                .color(0x355D6A).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 12, Carbon, 2, Manganese, 1, Silicon, 2, Phosphorus, 1, Sulfur, 1, Aluminium, 4)
                .blast(b -> b.temp(3850, BlastProperty.GasTier.MID).blastStats(VA[EV], 800))
                .build();

        MaragingSteel250 = new Material.Builder(getStartID(), gcymId("maraging_steel_250"))
                .ingot().fluid()
                .color(0x9083C0).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Molybdenum, 1, Titanium, 1, Nickel, 4, Cobalt, 2)
                .mix(VA[EV], 1200)
                .blast(2685)
                .build();

        MaragingSteel300 = new Material.Builder(getStartID(), gcymId("maraging_steel_300"))
                .ingot().fluid()
                .color(0x637087).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Titanium, 1, Aluminium, 1, Nickel, 4, Cobalt, 2)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 1000))
                .build();

        MaragingSteel350 = new Material.Builder(getStartID(), gcymId("maraging_steel_350"))
                .ingot().fluid()
                .color(0x7985B7).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Aluminium, 1, Molybdenum, 1, Nickel, 4, Cobalt, 2)
                .fluidPipeProperties(2500, 32000, true)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 1000))
                .build();

        HastelloyC276 = new Material.Builder(getStartID(), gcymId("hastelloy_c_276"))
                .ingot().fluid()
                .color(0xCF3939).iconSet(MaterialIconSet.METALLIC)
                .components(Nickel, 32, Molybdenum, 8, Chrome, 7, Tungsten, 1, Cobalt, 1, Copper, 1)
                .blast(b -> b.temp(4625, BlastProperty.GasTier.MID))
                .build();

        HastelloyX = new Material.Builder(getStartID(), gcymId("hastelloy_x"))
                .ingot().fluid()
                .color(0x6BA3E3).iconSet(MaterialIconSet.METALLIC)
                .components(Nickel, 24, Chrome, 11, Iron, 9, Molybdenum, 4, Manganese, 1, Silicon, 1)
                .blast(b -> b.temp(4200, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 900))
                .build();

        HastelloyN = new Material.Builder(getStartID(), gcymId("hastelloy_n"))
                .ingot().fluid()
                .color(0x9C97C4).iconSet(MaterialIconSet.METALLIC)
                .components(Yttrium, 4, Molybdenum, 4, Chrome, 2, Titanium, 2, Nickel, 15)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 800))
                .build();

        HastelloyW = new Material.Builder(getStartID(), gcymId("hastelloy_w"))
                .ingot().fluid()
                .color(0xC0B6D0).iconSet(MaterialIconSet.METALLIC)
                .components(Iron, 3, Cobalt, 1, Molybdenum, 12, Chrome, 3, Nickel, 31)
                .blast(b -> b.temp(3800, BlastProperty.GasTier.MID)
                        .blastStats(VA[HV], 600))
                .build();

        Trinaquadalloy = new Material.Builder(getStartID(), gcymId("trinaquadalloy"))
                .ingot().fluid()
                .color(0x281832).iconSet(MaterialIconSet.BRIGHT)
                .components(Trinium, 6, Naquadah, 2, Carbon, 1)
                .blast(b -> b.temp(8747, BlastProperty.GasTier.HIGHER)
                        .blastStats(VA[ZPM], 1200))
                .build();

        Zeron100 = new Material.Builder(getStartID(), gcymId("zeron_100"))
                .ingot().fluid()
                .color(0x325A8C).iconSet(MaterialIconSet.METALLIC)
                .components(Chrome, 13, Nickel, 3, Molybdenum, 2, Copper, 10, Tungsten, 2, Steel, 20)
                .blast(b -> b.temp(3693, BlastProperty.GasTier.MID)
                        .blastStats(VA[LuV]))
                .build();

        TitaniumCarbide = new Material.Builder(getStartID(), gcymId("titanium_carbide"))
                .ingot().fluid()
                .color(0xB20B3A).iconSet(MaterialIconSet.METALLIC)
                .components(Titanium, 1, Carbon, 1)
                .blast(b -> b.temp(3430, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 1000))
                .build();

        TantalumCarbide = new Material.Builder(getStartID(), gcymId("tantalum_carbide"))
                .ingot().fluid()
                .color(0x56566A).iconSet(MaterialIconSet.METALLIC)
                .components(Tantalum, 1, Carbon, 1)
                .blast(b -> b.temp(4120, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 1200))
                .build();

        MolybdenumDisilicide = new Material.Builder(getStartID(), gcymId("molybdenum_disilicide"))
                .ingot().fluid()
                .color(0x6A5BA3).iconSet(MaterialIconSet.METALLIC)
                .components(Molybdenum, 1, Silicon, 2)
                .blast(b -> b.temp(2300, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 800))
                .build();
    }
}
