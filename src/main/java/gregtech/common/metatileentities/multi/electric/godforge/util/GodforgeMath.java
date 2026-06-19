package gregtech.common.metatileentities.multi.electric.godforge.util;

import java.math.BigInteger;

import gregtech.common.metatileentities.multi.electric.godforge.module.MTEBaseModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;

public class GodforgeMath {

    public static int getRandomIntInRange(int min, int max) {
        return (int) (Math.random() * (max - min)) + min;
    }

    public static double calculateFuelConsumption(ForgeOfGodsData data) {
        double upgradeFactor = 1;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.STEM)) {
            upgradeFactor = 0.8;
        }

        int fuelFactor = data.getFuelConsumptionFactor();
        int fuelType = data.getSelectedFuelType();

        if (fuelType == 0) {
            return fuelFactor * 300 * Math.pow(1.15, fuelFactor) * upgradeFactor;
        } else if (fuelType == 1) {
            return fuelFactor * 2 * Math.pow(1.08, fuelFactor) * upgradeFactor;
        } else return fuelFactor / 25f * upgradeFactor;
    }

    public static int calculateStartupFuelConsumption(ForgeOfGodsData data) {
        int fuelFactor = data.getFuelConsumptionFactor();
        double value = Math.max(fuelFactor * 25 * Math.pow(1.2, fuelFactor), 1);
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    public static int calculateMaxFuelFactor(ForgeOfGodsData data) {
        int fuelCap = 5;
        int fuelType = data.getSelectedFuelType();
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.TSE)) {

            if (fuelType == 0) {
                fuelCap = data.isUpgradeActive(ForgeOfGodsUpgrade.STEM)
                    ? ForgeOfGodsData.MAX_RESIDUE_FACTOR_DISCOUNTED
                    : ForgeOfGodsData.MAX_RESIDUE_FACTOR;
            } else if (fuelType == 1) {
                fuelCap = data.isUpgradeActive(ForgeOfGodsUpgrade.STEM)
                    ? ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR_DISCOUNTED
                    : ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR;
            } else {
                fuelCap = Integer.MAX_VALUE;
            }
        } else {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.GEM)) {
                fuelCap += data.getUpgrades()
                    .getTotalActiveUpgrades();
            }
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.CFCE)) {
                fuelCap *= 1.2;
            }
        }
        return Math.max(fuelCap, 1);
    }

    public static int calculateEffectiveFuelFactor(int fuelFactor) {
        if (fuelFactor <= 43) {
            return fuelFactor;
        } else {
            return 43 + (int) Math.floor(Math.pow((fuelFactor - 43), 0.4));
        }
    }

    public static void calculateMaxHeatForModules(MTEBaseModule module, ForgeOfGodsData data) {
        calculateMaxHeatForModules(module, data, data.getFuelConsumptionFactor());
    }

    public static void calculateMaxHeatForModules(MTEBaseModule module, ForgeOfGodsData data, int fuelFactor) {
        double logBase = 1.5;
        int baseHeat = 12601;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.SEFCP)) {
            if (module instanceof MTESmeltingModule) {
                logBase = 1.12;
            } else {
                logBase = 1.18;
            }
        }
        int recipeHeat = baseHeat + (int) (Math.log(fuelFactor) / Math.log(logBase) * 1000);
        module.setHeatForOC(calculateOverclockHeat(module, data, recipeHeat));
        module.setHeat(recipeHeat);
    }

    public static int calculateOverclockHeat(MTEBaseModule module, ForgeOfGodsData data, Integer recipeHeat) {
        int actualHeat;
        double exponent;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.NDPE)) {
            if (module instanceof MTESmeltingModule) {
                exponent = 0.85;
            } else {
                exponent = 0.8;
            }
            if (recipeHeat > 30000) {
                actualHeat = (int) Math.floor(30000 + Math.pow(recipeHeat - 30000, exponent));
            } else {
                actualHeat = recipeHeat;
            }
        } else if (data.isUpgradeActive(ForgeOfGodsUpgrade.CNTI)) {
            actualHeat = Math.min(recipeHeat, 30000);
        } else {
            actualHeat = Math.min(recipeHeat, 15000);
        }
        return actualHeat;
    }

    public static void calculateSpeedBonusForModules(MTEBaseModule module, ForgeOfGodsData data) {
        double speedBonus = 1;

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.IGCC)) {
            speedBonus = Math.pow(module.getHeat(), -0.01);
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.DOR)) {
            if (module instanceof MTEPlasmaModule) {
                speedBonus /= Math.pow(module.getCalculatedMaxParallel(), 0.02);
            } else {
                speedBonus /= Math.pow(module.getCalculatedMaxParallel(), 0.012);
            }
        }

        if (module instanceof MTEExoticModule) {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.PA)) {
                speedBonus = Math.sqrt(speedBonus);
            } else {
                speedBonus = 1;
            }
        }

        module.setSpeedBonus(speedBonus);
    }

    public static void calculateMaxParallelForModules(MTEBaseModule module, ForgeOfGodsData data) {
        calculateMaxParallelForModules(module, data, data.getFuelConsumptionFactor());
    }

    public static void calculateMaxParallelForModules(MTEBaseModule module, ForgeOfGodsData data, int fuelFactor) {
        int baseParallel = 0;
        float fuelFactorMultiplier = 1;
        float heatMultiplier = 1;
        float upgradeAmountMultiplier = 1;
        int node53 = 1;
        boolean isMoltenOrSmeltingWithUpgrade = false;

        if (module instanceof MTESmeltingModule) {
            baseParallel = 1024;
        }
        if (module instanceof MTEMoltenModule) {
            baseParallel = 512;
        }
        if (module instanceof MTEPlasmaModule) {
            baseParallel = 384;
        }
        if (module instanceof MTEExoticModule) {
            baseParallel = 64;
        }

        if (module instanceof MTEMoltenModule
            || (module instanceof MTESmeltingModule && data.isUpgradeActive(ForgeOfGodsUpgrade.DOP))) {
            isMoltenOrSmeltingWithUpgrade = true;
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.CTCDD)) {
            node53 = 2;
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.SA)) {
            fuelFactorMultiplier = 1 + calculateEffectiveFuelFactor(fuelFactor) / 15f;
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.TCT)) {
                if (isMoltenOrSmeltingWithUpgrade) {
                    fuelFactorMultiplier *= 3;
                } else {
                    fuelFactorMultiplier *= 2;
                }
            }
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.EPEC)) {
            if (isMoltenOrSmeltingWithUpgrade) {
                heatMultiplier = 1 + module.getHeat() / 15000f;
            } else {
                heatMultiplier = 1 + module.getHeat() / 25000f;
            }
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.POS)) {
            if (isMoltenOrSmeltingWithUpgrade) {
                upgradeAmountMultiplier = 1 + data.getUpgrades()
                    .getTotalActiveUpgrades() / 5f;
            } else {
                upgradeAmountMultiplier = 1 + data.getUpgrades()
                    .getTotalActiveUpgrades() / 8f;
            }
        }

        float totalBonuses = node53 * fuelFactorMultiplier * heatMultiplier * upgradeAmountMultiplier;

        if (module instanceof MTEExoticModule) {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.PA)) {
                totalBonuses = (float) Math.sqrt(totalBonuses);
            } else {
                totalBonuses = 1;
            }
        }

        int maxParallel = (int) (baseParallel * totalBonuses);

        module.setCalculatedMaxParallel(maxParallel);
    }

    public static void calculateEnergyDiscountForModules(MTEBaseModule module, ForgeOfGodsData data) {
        double fillRatioDiscount = 1;
        double maxBatteryDiscount = 1;

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.REC)) {
            maxBatteryDiscount = 1 - (1 - Math.pow(1.05, -0.05 * data.getMaxBatteryCharge())) / 20;
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.IMKG)) {
            double fillRatioMinusZeroPointFive = (double) data.getInternalBattery() / data.getMaxBatteryCharge() - 0.5;
            if (module instanceof MTEPlasmaModule) {
                fillRatioDiscount = 1
                    - (fillRatioMinusZeroPointFive * fillRatioMinusZeroPointFive * (-0.6) + 0.15);
            } else {
                fillRatioDiscount = 1
                    - (fillRatioMinusZeroPointFive * fillRatioMinusZeroPointFive * (-0.6) + 0.15) * 2 / 3;
            }
        }

        if (module instanceof MTEExoticModule) {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.PA)) {
                fillRatioDiscount = Math.sqrt(fillRatioDiscount);
                maxBatteryDiscount = Math.sqrt(maxBatteryDiscount);
            } else {
                fillRatioDiscount = 1;
                maxBatteryDiscount = 1;
            }
        }

        module.setEnergyDiscount((float) (fillRatioDiscount * maxBatteryDiscount));
    }

    public static void calculateProcessingVoltageForModules(MTEBaseModule module, ForgeOfGodsData data) {
        calculateProcessingVoltageForModules(module, data, data.getFuelConsumptionFactor());
    }

    public static void calculateProcessingVoltageForModules(MTEBaseModule module, ForgeOfGodsData data,
                                                            int fuelFactor) {
        long voltage = 2_000_000_000;

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.GISS)) {
            voltage += calculateEffectiveFuelFactor(fuelFactor) * 100_000_000L;
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.NGMS)) {
            voltage *= (long) Math.pow(4, data.getFormedRingAmount());
        }

        module.setProcessingVoltage(voltage);
    }

    public static void setMiscModuleParameters(MTEBaseModule module, ForgeOfGodsData data) {
        int plasmaTier = 0;
        double overclockTimeFactor = 2;

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.EE)) {
            plasmaTier = 2;
        } else if (data.isUpgradeActive(ForgeOfGodsUpgrade.SEDS)) {
            plasmaTier = 1;
        }

        if (data.isUpgradeActive(ForgeOfGodsUpgrade.GGEBE)) {
            if (module instanceof MTEPlasmaModule) {
                overclockTimeFactor = 2.3;
            } else {
                overclockTimeFactor = 2.15;
            }
            if (module instanceof MTEExoticModule) {
                if (data.isUpgradeActive(ForgeOfGodsUpgrade.PA)) {
                    overclockTimeFactor = 2 + (overclockTimeFactor - 2) * (overclockTimeFactor - 2);
                } else {
                    overclockTimeFactor = 2;
                }
            }
        }

        module.setUpgrade83(data.isUpgradeActive(ForgeOfGodsUpgrade.IMKG));
        module.setMultiStepPlasma(data.isUpgradeActive(ForgeOfGodsUpgrade.TPTP));
        module.setPlasmaTier(plasmaTier);
        module.setMagmatterCapable(data.isUpgradeActive(ForgeOfGodsUpgrade.EE));
        module.setVoltageConfig(data.isUpgradeActive(ForgeOfGodsUpgrade.TBF));
        module.setOverclockTimeFactor(overclockTimeFactor);
    }

    public static boolean allowModuleConnection(MTEBaseModule module, ForgeOfGodsData data) {
        if (!data.isUpgradeActive(ForgeOfGodsUpgrade.START)) {
            return false;
        }

        if (module instanceof MTEMoltenModule && data.isUpgradeActive(ForgeOfGodsUpgrade.FDIM)) {
            return true;
        }

        if (module instanceof MTEPlasmaModule && data.isUpgradeActive(ForgeOfGodsUpgrade.GPCI)) {
            return true;
        }

        if (module instanceof MTEExoticModule) {
            MTEExoticModule exoticizer = (MTEExoticModule) module;
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.QGPIU) && !exoticizer.isMagmatterModeOn()) {
                return true;
            }
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.EE) && exoticizer.isMagmatterModeOn()) {
                return true;
            }
        }

        return module instanceof MTESmeltingModule;
    }

    public static boolean factorChangeDuringRecipeAntiCheese(MTEBaseModule module) {
        if (module instanceof MTESmeltingModule || module instanceof MTEMoltenModule) {
            return module.getCurrentRecipeHeat() > module.getHeat();
        } else {
            return false;
        }
    }

    public static void queryMilestoneStats(MTEBaseModule module, ForgeOfGodsData data) {
        data.setTotalPowerConsumed(
            data.getTotalPowerConsumed()
                .add(module.getPowerTally()));
        module.setPowerTally(BigInteger.ZERO);
        data.setTotalRecipesProcessed(data.getTotalRecipesProcessed() + module.getRecipeTally());
        module.setRecipeTally(0);
        module.setInversionConfig(data.isInversion());
    }

    private static double getBigIntegerRatio(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0) {
            return 0;
        }
        double directRatio = numerator.doubleValue() / denominator.doubleValue();
        if (Double.isFinite(directRatio)) {
            return directRatio;
        }
        return Math.exp(logBigInteger(numerator) - logBigInteger(denominator));
    }

    private static double logBigInteger(BigInteger value) {
        if (value.signum() <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        int bitLength = value.bitLength();
        if (bitLength < 1024) {
            return Math.log(value.doubleValue());
        }
        int shift = bitLength - 53;
        long topBits = value.shiftRight(shift)
            .longValue();
        return Math.log(topBits) + shift * Math.log(2);
    }

    public static void determineChargeMilestone(ForgeOfGodsData data) {
        if (!data.isInversion()) {
            double total = getBigIntegerRatio(
                data.getTotalPowerConsumed(),
                BigInteger.valueOf(ForgeOfGodsData.POWER_MILESTONE_CONSTANT));
            float charge = (float) (Math.max(
                Math.log(total) / ForgeOfGodsData.POWER_LOG_CONSTANT + 1,
                0) / 7);
            data.setPowerMilestonePercentage(charge);
            data.setInvertedPowerMilestonePercentage(0);
            data.setMilestoneProgress(0, (int) Math.floor(data.getPowerMilestonePercentage() * 7));
            return;
        }

        float rawProgress = (float) ((getBigIntegerRatio(
            data.getTotalPowerConsumed(),
            ForgeOfGodsData.POWER_MILESTONE_T7_CONSTANT) - 1) / 7);
        int closestRelevantSeven = (int) Math.floor(rawProgress);
        float actualProgress = rawProgress - closestRelevantSeven;
        data.setMilestoneProgress(0, 7 + (int) Math.floor(rawProgress * 7));

        if (closestRelevantSeven % 2 == 0) {
            data.setInvertedPowerMilestonePercentage(actualProgress);
            data.setPowerMilestonePercentage(1 - actualProgress);
        } else {
            data.setPowerMilestonePercentage(actualProgress);
            data.setInvertedPowerMilestonePercentage(1 - actualProgress);
        }
    }

    public static void determineConversionMilestone(ForgeOfGodsData data) {
        if (!data.isInversion()) {
            long total = data.getTotalRecipesProcessed();
            double raw = Math.log(total * 1f / ForgeOfGodsData.RECIPE_MILESTONE_CONSTANT)
                / ForgeOfGodsData.RECIPE_LOG_CONSTANT + 1;
            data.setRecipeMilestonePercentage((float) Math.max(raw, 0) / 7);
            data.setMilestoneProgress(1, (int) Math.floor(data.getRecipeMilestonePercentage() * 7));
            return;
        }

        float rawProgress = (((float) data.getTotalRecipesProcessed()
            / ForgeOfGodsData.RECIPE_MILESTONE_T7_CONSTANT) - 1) / 7;
        int closestRelevantSeven = (int) Math.floor(rawProgress);
        float actualProgress = rawProgress - closestRelevantSeven;
        data.setMilestoneProgress(1, 7 + (int) Math.floor(rawProgress * 7));

        if (closestRelevantSeven % 2 == 0) {
            data.setInvertedRecipeMilestonePercentage(actualProgress);
            data.setRecipeMilestonePercentage(1 - actualProgress);
        } else {
            data.setRecipeMilestonePercentage(actualProgress);
            data.setInvertedRecipeMilestonePercentage(1 - actualProgress);
        }
    }

    public static void determineCatalystMilestone(ForgeOfGodsData data) {
        if (!data.isInversion()) {
            long total = data.getTotalFuelConsumed();
            double raw = Math.log(total * 1f / ForgeOfGodsData.FUEL_MILESTONE_CONSTANT)
                / ForgeOfGodsData.FUEL_LOG_CONSTANT + 1;
            data.setFuelMilestonePercentage((float) Math.max(raw, 0) / 7);
            data.setMilestoneProgress(2, (int) Math.floor(data.getFuelMilestonePercentage() * 7));
            return;
        }

        float rawProgress = (((float) data.getTotalFuelConsumed()
            / ForgeOfGodsData.FUEL_MILESTONE_T7_CONSTANT) - 1) / 7;
        int closestRelevantSeven = (int) Math.floor(rawProgress);
        float actualProgress = rawProgress - closestRelevantSeven;
        data.setMilestoneProgress(2, 7 + (int) Math.floor(rawProgress * 7));

        if ((closestRelevantSeven % 2) == 0) {
            data.setInvertedFuelMilestonePercentage(actualProgress);
            data.setFuelMilestonePercentage(1 - actualProgress);
        } else {
            data.setFuelMilestonePercentage(actualProgress);
            data.setInvertedFuelMilestonePercentage(1 - actualProgress);
        }
    }

    public static void determineCompositionMilestone(ForgeOfGodsData data) {
        if (!data.isInversion()) {
            data.setStructureMilestonePercentage(data.getTotalExtensionsBuilt() / 7.0f);
            return;
        }

        float rawProgress = (data.getTotalExtensionsBuilt() - 7) / 7f;
        int closestRelevantSeven = (int) Math.floor(rawProgress);
        float actualProgress = rawProgress - closestRelevantSeven;

        if ((closestRelevantSeven % 2) == 0) {
            data.setInvertedStructureMilestonePercentage(actualProgress);
            data.setStructureMilestonePercentage(1 - actualProgress);
        } else {
            data.setStructureMilestonePercentage(actualProgress);
            data.setInvertedStructureMilestonePercentage(1 - actualProgress);
        }
    }
}
