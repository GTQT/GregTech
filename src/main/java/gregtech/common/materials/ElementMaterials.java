package gregtech.common.materials;

import gregtech.api.fluids.FluidBuilder;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.SCElements;

import static gregtech.api.unification.material.info.MaterialIconSet.METALLIC;
import static gregtech.api.unification.material.info.MaterialIconSet.SHINY;
import static gregtech.api.unification.material.SCMaterials.*;
import static gregtech.api.unification.material.properties.SCMaterialsFlag.GENERATE_PELLETS;
import static gregtech.api.util.SCUtility.scId;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加靶丸。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
/*
 * Ranges 24000-24499
 */
public class ElementMaterials {
    private static int startId = 24000;

    public static int getStartID() {
        return startId++;
    }

    public static void register() {
        Radium225 = new Material.Builder(getStartID(), scId("radium_225"))
                .ingot().fluid().dust()
                .color(0xE0E0E0)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Ra225)
                .build();


        Radium226 = new Material.Builder(getStartID(), scId("radium_226"))
                .ingot().fluid().dust()
                .color(0xD3D3D3)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Ra226)
                .build();

        Protactinium231 = new Material.Builder(getStartID(), scId("protactinium_231"))
                .ingot().fluid().dust()
                .color(0x708090)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pa231)
                .build();

        Protactinium233 = new Material.Builder(getStartID(), scId("protactinium_233"))
                .ingot().fluid().dust()
                .color(0x778899)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pa233)
                .build();

        Uranium232 = new Material.Builder(getStartID(), scId("uranium_232"))
                .ingot().fluid().dust()
                .color(0x2F4F4F)
                .flags(GENERATE_PELLETS)
                .element(SCElements.U232)
                .build();

        Uranium233 = new Material.Builder(getStartID(), scId("uranium_233"))
                .ingot().fluid().dust()
                .color(0x3B3B3B)
                .flags(GENERATE_PELLETS)
                .element(SCElements.U233)
                .build();

        Uranium234 = new Material.Builder(getStartID(), scId("uranium_234"))
                .ingot().fluid().dust()
                .color(0x90EE90)
                .flags(GENERATE_PELLETS)
                .element(SCElements.U234)
                .build();

        Uranium236 = new Material.Builder(getStartID(), scId("uranium_236"))
                .ingot().fluid().dust()
                .color(0x8FBC8F)
                .flags(GENERATE_PELLETS)
                .element(SCElements.U236)
                .build();

        Uranium237 = new Material.Builder(getStartID(), scId("uranium_237"))
                .ingot().fluid().dust()
                .color(0x7CFC00)
                .flags(GENERATE_PELLETS)
                .element(SCElements.U237)
                .build();

        Uranium239 = new Material.Builder(getStartID(), scId("uranium_239"))
                .ingot().dust().fluid()
                .color(0x46FA46).iconSet(SHINY)
                .element(SCElements.U239)
                .flags(GENERATE_PELLETS)
                .build();

        Neptunium235 = new Material.Builder(getStartID(), scId("neptunium_235"))
                .ingot().dust().fluid()
                .color(0x284D7B).iconSet(METALLIC)
                .element(SCElements.Np235)
                .flags(GENERATE_PELLETS)
                .build();

        Neptunium236 = new Material.Builder(getStartID(), scId("neptunium_236"))
                .ingot().dust().fluid()
                .color(0x284D7B).iconSet(METALLIC)
                .element(SCElements.Np236)
                .flags(GENERATE_PELLETS)
                .build();

        Neptunium237 = new Material.Builder(getStartID(), scId("neptunium_237"))
                .ingot().dust().fluid()
                .color(0x284D7B).iconSet(METALLIC)
                .element(SCElements.Np237)
                .flags(GENERATE_PELLETS)
                .build();

        Neptunium238 = new Material.Builder(getStartID(), scId("neptunium_238"))
                .ingot().dust().fluid()
                .color(0x284D7B).iconSet(METALLIC)
                .element(SCElements.Np238)
                .flags(GENERATE_PELLETS)
                .build();

        Neptunium239 = new Material.Builder(getStartID(), scId("neptunium_239"))
                .ingot().dust().fluid()
                .color(0x284D7B).iconSet(METALLIC)
                .element(SCElements.Np239)
                .flags(GENERATE_PELLETS)
                .build();

        Plutonium236 = new Material.Builder(getStartID(), scId("plutonium_236"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pu236)
                .build();

        Plutonium237 = new Material.Builder(getStartID(), scId("plutonium_237"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pu237)
                .build();

        Plutonium238 = new Material.Builder(getStartID(), scId("plutonium_238"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pu238)
                .build();

        Plutonium240 = new Material.Builder(getStartID(), scId("plutonium_240"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pu240)
                .build();

        Plutonium242 = new Material.Builder(getStartID(), scId("plutonium_242"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .flags(GENERATE_PELLETS)
                .color(0xF03232).iconSet(METALLIC)
                .element(SCElements.Pu242)
                .build();

        Plutonium243 = new Material.Builder(getStartID(), scId("plutonium_243"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Pu243)
                .build();

        Plutonium244 = new Material.Builder(getStartID(), scId("plutonium_244"))
                .ingot().dust()
                .liquid(new FluidBuilder().temperature(913))
                .flags(GENERATE_PELLETS)
                .color(0xF03232).iconSet(METALLIC)
                .element(SCElements.Pu244)
                .build();

        Thorium228 = new Material.Builder(getStartID(), scId("thorium_228"))
                .ingot().fluid().dust()
                .color(0xFF8C00)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Th228)
                .build();

        Thorium229 = new Material.Builder(getStartID(), scId("thorium_229"))
                .ingot().fluid().dust()
                .color(0xFFD700)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Th229)
                .build();

        Thorium230 = new Material.Builder(getStartID(), scId("thorium_230"))
                .ingot().fluid().dust()
                .color(0xFFA500)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Th230)
                .build();

        Thorium232 = new Material.Builder(getStartID(), scId("thorium_232"))
                .ingot().fluid().dust()
                .color(0xB8860B)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Th232)
                .build();

        Thorium233 = new Material.Builder(getStartID(), scId("thorium_233"))
                .ingot().fluid().dust()
                .color(0xCD853F)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Th233)
                .build();

        Americium240 = new Material.Builder(getStartID(), scId("americium_240"))
                .ingot().fluid().dust()
                .color(0x008B8B)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Am240)
                .build();

        Americium241 = new Material.Builder(getStartID(), scId("americium_241"))
                .ingot().fluid().dust()
                .color(0x00868B)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Am241)
                .build();

        Americium242 = new Material.Builder(getStartID(), scId("americium_242"))
                .ingot().fluid().dust()
                .color(0x008B45)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Am242)
                .build();

        Americium243 = new Material.Builder(getStartID(), scId("americium_243"))
                .ingot().fluid().dust()
                .color(0x006400)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Am243)
                .build();

        Curium242 = new Material.Builder(getStartID(), scId("curium_242"))
                .ingot().fluid().dust()
                .color(0x4169E1) // 皇家蓝
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm242)
                .build();

        Curium243 = new Material.Builder(getStartID(), scId("curium_243"))
                .ingot().fluid().dust()
                .color(0x0000FF)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm243)
                .build();

        Curium244 = new Material.Builder(getStartID(), scId("curium_244"))
                .ingot().fluid().dust()
                .color(0x1E90FF)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm244)
                .build();

        Curium245 = new Material.Builder(getStartID(), scId("curium_245"))
                .ingot().fluid().dust()
                .color(0x0000EE)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm245)
                .build();

        Curium246 = new Material.Builder(getStartID(), scId("curium_246"))
                .ingot().fluid().dust()
                .color(0x0000CD)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm246)
                .build();

        Curium247 = new Material.Builder(getStartID(), scId("curium_247"))
                .ingot().fluid().dust()
                .color(0x0000AA)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm247)
                .build();

        Curium248 = new Material.Builder(getStartID(), scId("curium_248"))
                .ingot().fluid().dust()
                .color(0x000080)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm248)
                .build();

        Curium250 = new Material.Builder(getStartID(), scId("curium_250"))
                .ingot().fluid().dust()
                .color(0x191970)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cm250)
                .build();

        Berkelium249 = new Material.Builder(getStartID(), scId("berkelium_249"))
                .ingot().fluid().dust()
                .color(0x696969)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Bk249)
                .build();

        Californium249 = new Material.Builder(getStartID(), scId("californium_249"))
                .ingot().fluid().dust()
                .color(0x708090)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cf249)
                .build();

        Californium252 = new Material.Builder(getStartID(), scId("californium_252"))
                .ingot().fluid().dust()
                .color(0x2F4F4F)
                .flags(GENERATE_PELLETS)
                .element(SCElements.Cf252)
                .build();
    }
}
