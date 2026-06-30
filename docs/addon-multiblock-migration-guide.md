# Addon Migration Guide: RecipeMap Trait & Parametric Multiblock System

**Applies to:** GregTech CEu v1.9.0+
**Deprecated API removal target:** v2.10

## Overview

The multiblock recipe system has been refactored to use **composition over inheritance**.
Recipe processing capability is now expressed via the `IRecipeMapHolder` interface and
composable `RecipeAbilityManager`, rather than requiring inheritance from
`RecipeMapMultiblockController` or `FuelMultiblockController`.

### Architecture Changes

| Concept | Old (deprecated) | New |
|---------|------------------|-----|
| Recipe holder contract | `RecipeMapMultiblockController` (concrete class) | `IRecipeMapHolder` (interface) |
| Fuel multiblock base | `FuelMultiblockController` | `ParametricFuelController<V>` + `IGenerator` |
| Ability management | Inline in RMMC `initializeAbilities()` | `RecipeAbilityManager` (composable) |
| Variant multiblocks | Multiple MTE IDs per variant | Single MTE ID + `ParametricMultiblockController<V>` |
| Distinct bus check | `instanceof RecipeMapMultiblockController` | `instanceof IDistinctBusController` |
| Recipe capability check | `instanceof RecipeMapMultiblockController` | `instanceof IRecipeMapHolder` |
| Controllable check | `instanceof RecipeMapMultiblockController` | `instanceof IControllable` |
| Generator check | `instanceof FuelMultiblockController` | `instanceof IGenerator` |

### Class Hierarchy (New)

```
MultiblockWithDisplayBase
├── RecipeMapMultiblockController (implements IRecipeMapHolder) — still supported
│   └── FuelMultiblockController (@Deprecated) — use ParametricFuelController instead
└── ParametricMultiblockController<V>
    ├── ParametricRecipeMapController<V> (implements IRecipeMapHolder)
    │   └── ParametricFuelController<V> (implements IGenerator)
    └── (non-recipe PMC subclasses, e.g. MultiblockTank)
```

---

## Breaking Changes: `instanceof` Checks

If your addon uses `instanceof` checks against GT multiblock classes, update them:

```java
// ❌ Old — will not match ParametricRecipeMapController subclasses
if (mte instanceof RecipeMapMultiblockController rmmc) {
    rmmc.getInputInventory();
}

// ✅ New — matches both RMMC and Parametric variants
if (mte instanceof IRecipeMapHolder holder) {
    holder.getInputInventory();
}
```

### Full `instanceof` Migration Table

| Purpose | Old | New |
|---------|-----|-----|
| Access recipe inventories | `instanceof RecipeMapMultiblockController` | `instanceof IRecipeMapHolder` |
| Check distinct bus mode | `instanceof RecipeMapMultiblockController` | `instanceof IDistinctBusController` |
| Check working enabled | `instanceof RecipeMapMultiblockController` | `instanceof IControllable` |
| Detect generators/fuel | `instanceof FuelMultiblockController` | `instanceof IGenerator` |
| Access multiblock abilities | `(RecipeMapMultiblockController) mte` | `(MultiblockControllerBase) mte` |

---

## Breaking Changes: MTE ID Consolidation

The following multiblocks have been consolidated from multiple IDs to single IDs with NBT variants:

| Multiblock | Old IDs | New ID | Variant System |
|-----------|---------|--------|----------------|
| Large Turbine | 1010, 1011, 1012 | 1010 | `LargeTurbineType` NBT variant |
| Large Combustion Engine | 1007, 1008 | 1007 | `LargeCombustionEngineType` NBT variant |
| Large Boiler | 1013, 1014, 1015, 1016 | 1013 | `BoilerType` NBT variant |

**Impact on addons:**
- If your addon hardcodes MTE IDs (e.g., for recipe catalyst lookups), update to use the new single ID
- The `MetaTileEntities` field references (`LARGE_STEAM_TURBINE`, `LARGE_GAS_TURBINE`, etc.) still exist but now all point to the **same MTE instance**
- Use `metaTileEntity.getVariant()` (on PMC subclasses) to distinguish variants at runtime

---

## Deprecated Classes

### `FuelMultiblockController` — @Deprecated

**Replacement:** `ParametricFuelController<V>` for variant fuel multiblocks, or implement
`IGenerator` + `IRecipeMapHolder` on any `MultiblockWithDisplayBase` subclass.

```java
// ❌ Old
public class MyGenerator extends FuelMultiblockController { ... }

// ✅ New (with variants)
public class MyGenerator extends ParametricFuelController<MyGeneratorType> {
    public MyGenerator(ResourceLocation id) {
        super(id, MyGeneratorType.class, MyGeneratorType.DEFAULT,
              RecipeMaps.MY_FUEL_MAP, GTValues.EV);
    }
}

// ✅ New (without variants, single type)
// Still use RecipeMapMultiblockController + IGenerator manually
```

### `MultiblockFuelRecipeLogic` Old Constructors — @Deprecated

```java
// ❌ Old
new MultiblockFuelRecipeLogic(fuelMultiblockController)

// ✅ New (general-purpose)
new MultiblockFuelRecipeLogic(myMetaTileEntity, recipeMap)
// where myMetaTileEntity implements both IRecipeMapHolder and IGenerator
```

---

## New APIs for Addon Authors

### `IRecipeMapHolder` Interface

The core contract for any multiblock that processes recipes:

```java
public interface IRecipeMapHolder {
    @Nullable RecipeMap<?> getRecipeMap();
    @NotNull AbstractRecipeLogic getRecipeMapWorkable();
    @NotNull IItemHandlerModifiable getInputInventory();
    @NotNull IItemHandlerModifiable getOutputInventory();
    @NotNull IMultipleTankHandler getInputFluidInventory();
    @NotNull IMultipleTankHandler getOutputFluidInventory();
    @Nullable IEnergyContainer getEnergyContainer();
    boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess);
    void refreshAllBeforeConsumption();
}
```

Both `RecipeMapMultiblockController` and `ParametricRecipeMapController` implement this interface.

### `RecipeAbilityManager`

A composable helper that manages recipe-related abilities (inventories, tanks, energy):

```java
// In your multiblock constructor
this.abilityManager = new RecipeAbilityManager(this);

// In formStructure()
abilityManager.initialize(allowSameFluidFill);

// In invalidateStructure()
abilityManager.reset();

// Delegate IRecipeMapHolder methods
@Override
public IItemHandlerModifiable getInputInventory() {
    return abilityManager.getInputInventory();
}
```

### `ParametricRecipeMapController<V>`

Base class for variant multiblocks that process standard recipes:

```java
public class MyMultiblock extends ParametricRecipeMapController<MyType> {

    public MyMultiblock(ResourceLocation id) {
        super(id, MyType.class, MyType.DEFAULT, RecipeMaps.MY_RECIPES);
    }

    @Override
    protected RecipeMap<?> getRecipeMapForVariant(@NotNull MyType variant) {
        return variant.getRecipeMap(); // different variants can use different recipe maps
    }

    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return MY_DEFINITIONS.get(getVariant());
    }
}
```

### `ParametricFuelController<V>`

Base class for variant fuel/generator multiblocks:

```java
public class MyTurbine extends ParametricFuelController<TurbineType> {

    public MyTurbine(ResourceLocation id) {
        super(id, TurbineType.class, TurbineType.STEAM,
              RecipeMaps.STEAM_TURBINE_FUELS, GTValues.HV);
    }

    @Override
    public boolean isDynamoFull() {
        return getEnergyContainer().getEnergyCanBeInserted() < recipeMapWorkable.getRecipeEUt();
    }
}
```

### `MultiblockRecipeLogic` — New General Constructor

```java
// Works with any MetaTileEntity that implements IRecipeMapHolder
public <T extends MetaTileEntity & IRecipeMapHolder> MultiblockRecipeLogic(T tileEntity, RecipeMap<?> recipeMap)
```

This allows `MultiblockRecipeLogic` to be used with `ParametricRecipeMapController` subclasses
that don't inherit from `RecipeMapMultiblockController`.

---

## Backward Compatibility

The deprecated APIs are fully functional throughout v1.9.x and v2.x until v2.10:

- `FuelMultiblockController` still works — existing subclasses compile and run fine
- `RecipeMapMultiblockController` is NOT deprecated — it remains the recommended base for
  non-variant recipe multiblocks
- Old `MultiblockFuelRecipeLogic` constructors still work
- The `LARGE_TURBINES` / `LARGE_COMBUSTION_ENGINES` / `LARGE_BOILERS` EnumMaps still exist
- Legacy field references (`LARGE_STEAM_TURBINE`, `LARGE_GAS_TURBINE`, etc.) still exist

---

## Migration Checklist (RecipeMap Trait)

- [ ] Replace `instanceof RecipeMapMultiblockController` with appropriate interface checks
- [ ] Replace `instanceof FuelMultiblockController` with `instanceof IGenerator`
- [ ] Update hardcoded MTE IDs for consolidated multiblocks (Turbine/LCE/Boiler)
- [ ] If subclassing `FuelMultiblockController`, consider migrating to `ParametricFuelController`
- [ ] If using `MultiblockFuelRecipeLogic` with old constructor, consider the new generic one
- [ ] Update OC/TOP integration code to use `IRecipeMapHolder` instead of concrete class

---

## Quick Reference: Import Changes

```java
// New interfaces (use these for instanceof checks)
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.capability.IGenerator;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IControllable;

// New base classes (for variant multiblocks)
import gregtech.api.metatileentity.multiblock.ParametricMultiblockController;
import gregtech.api.metatileentity.multiblock.ParametricRecipeMapController;
import gregtech.api.metatileentity.multiblock.ParametricFuelController;
import gregtech.api.metatileentity.multiblock.RecipeAbilityManager;

// Still valid (not deprecated)
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.capability.impl.MultiblockRecipeLogic;

// Deprecated (will be removed in v2.10)
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
```
