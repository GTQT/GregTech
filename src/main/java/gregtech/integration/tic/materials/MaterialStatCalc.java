package gregtech.integration.tic.materials;

import gregtech.api.unification.material.properties.ToolProperty;

import slimeknights.tconstruct.library.materials.ArrowShaftMaterialStats;
import slimeknights.tconstruct.library.materials.BowMaterialStats;

/**
 * Pure stat-calculation helpers for converting GT tool properties into TiC material stat values.
 */
final class MaterialStatCalc {

    private MaterialStatCalc() {}

    /**
     * Maps GT harvest level to TiC harvest level.
     *
     * <p>
     * Passes through the GT level directly so that TiC tools made from GT materials have the same mining capability as
     * their GT counterparts. (e.g. GT Titanium can mine obsidian → TiC Titanium can mine obsidian)
     */
    static int mapHarvestLevel(int gtLevel) {
        return gtLevel;
    }

    static float calcHandleModifier(ToolProperty toolProp) {
        float modifier = 0.5f + (toolProp.getToolDurability() / 2000.0f);
        return Math.max(0.1f, Math.min(modifier, 2.0f));
    }

    static int calcHandleDurability(ToolProperty toolProp) {
        return (int) (toolProp.getToolDurability() * 0.1f);
    }

    static int calcExtraDurability(int durability) {
        return (int) (durability * 0.15f);
    }

    /**
     * Calculates arrow shaft stats from GT tool properties. Reference (TiC native): wood modifier=1.0/bonus=0.0,
     * prismarine modifier=1.5/bonus=0.5.
     */
    static ArrowShaftMaterialStats calcShaftStats(ToolProperty toolProp) {
        float attack = toolProp.getToolAttackDamage();
        float modifier = Math.max(0.5f, Math.min(3.0f, 0.8f + attack * 0.04f));
        int bonusAmmo = Math.min(10, (int) (attack / 5f));
        return new ArrowShaftMaterialStats(modifier, bonusAmmo);
    }

    /**
     * Calculates bow stats from GT tool properties. Reference (TiC native): iron drawspeed=0.5, range=1.5,
     * bonusDamage=7.
     */
    static BowMaterialStats calcBowStats(ToolProperty toolProp) {
        float speed = toolProp.getToolSpeed();
        float attack = toolProp.getToolAttackDamage();
        float drawspeed = Math.max(0.2f, Math.min(1.5f, 1.0f / (1.0f + speed * 0.1f)));
        float range = Math.max(0.4f, Math.min(3.0f, 0.5f + speed * 0.15f));
        float bonusDamage = Math.max(0f, Math.min(15f, attack * 1.2f));
        return new BowMaterialStats(drawspeed, range, bonusDamage);
    }
}
