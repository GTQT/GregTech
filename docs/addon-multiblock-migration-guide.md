# Addon Migration Guide: Multiblock Structure System

**Applies to:** GregTech CEu v1.9.0+
**Deprecated API removal target:** v2.10

## Overview

The multiblock structure system has been refactored to separate **immutable templates** from
**per-instance mutable state**. The old `BlockPattern` class (which bundled both together) is now
deprecated and scheduled for removal in v2.10.

### Architecture Changes

| Concept | Old (deprecated) | New |
|---------|------------------|-----|
| Pattern definition | `BlockPattern` (template + state combined) | `BlockPatternTemplate` (immutable, shared) |
| Per-instance state | `BlockPattern` fields (`cache`, `formedRepetitionCount`) | `MultiblockState` (one per controller instance) |
| Builder output | `FactoryBlockPattern.build()` → `BlockPattern` | `FactoryBlockPattern.buildTemplate()` → `BlockPatternTemplate` |
| Controller override | `createStructurePattern()` → `BlockPattern` | `createStructureTemplate()` → `BlockPatternTemplate` |
| Template caching | Manual or none | `SoftTemplate`, `TemplatePool` |
| High-level builder | N/A | `DeclarativePatternBuilder` (optional, recommended) |

**Key benefit:** Multiple controller instances of the same machine type now share a single
`BlockPatternTemplate`, significantly reducing memory usage. Each instance only holds its own
lightweight `MultiblockState`.

---

## Migration Steps

### Step 1: Change `createStructurePattern()` → `createStructureTemplate()`

**Before (deprecated):**
```java
@Override
protected BlockPattern createStructurePattern() {
    return FactoryBlockPattern.start()
            .aisle("XXX", "XSX", "XXX")
            .aisle("XXX", "X#X", "XXX")
            .aisle("XXX", "XXX", "XXX")
            .where('S', selfPredicate())
            .where('#', air())
            .where('X', states(getCasingState())
                    .or(autoAbilities()))
            .build();  // returns BlockPattern
}
```

**After (new API):**
```java
@Override
protected BlockPatternTemplate createStructureTemplate() {
    return FactoryBlockPattern.start()
            .aisle("XXX", "XSX", "XXX")
            .aisle("XXX", "X#X", "XXX")
            .aisle("XXX", "XXX", "XXX")
            .where('S', selfPredicate())
            .where('#', air())
            .where('X', states(getCasingState())
                    .or(autoAbilities()))
            .buildTemplate();  // returns BlockPatternTemplate
}
```

**Changes:**
1. Method name: `createStructurePattern()` → `createStructureTemplate()`
2. Return type: `BlockPattern` → `BlockPatternTemplate`
3. Builder call: `.build()` → `.buildTemplate()`

### Step 2: Add Static Template Caching (Recommended)

Templates are immutable and can be safely shared. Use `SoftTemplate` (via `TemplatePool` or
standalone) to avoid recreating the template every time `createStructureTemplate()` is called.

**Option A: `SoftTemplate` + `TemplatePool` (recommended)**
```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("myaddon:my_machine", () ->
                FactoryBlockPattern.start()
                        .aisle("XXX", "XSX", "XXX")
                        .aisle("XXX", "X#X", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .where('S', selfPredicate())
                        .where('#', air())
                        .where('X', states(getCasingState()).or(autoAbilities()))
                        .buildTemplate()
        );

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATE.get();
}
```

`SoftTemplate` uses `SoftReference` internally — templates are GC'd when no controller holds a
strong reference and JVM is under memory pressure. They are transparently re-created on next access.
Ideal for addons with many machine types where most are rarely placed.

> **Note:** `LazyTemplate` is deprecated. It permanently retains templates in memory and cannot
> release them. Use `SoftTemplate` instead — for core machines that should never be evicted,
> simply hold a strong static reference to the `SoftTemplate` instance.

**Option B: Multi-variant machines (e.g. tiered machines)**
```java
// For machines with multiple tiers/variants sharing the same class
private static final SoftTemplate[] TEMPLATES = new SoftTemplate[3];
static {
    for (int i = 0; i < 3; i++) {
        final int tier = i;
        TEMPLATES[i] = TemplatePool.getInstance().register(
                "myaddon:my_machine/" + tier,
                () -> buildTemplateForTier(tier)
        );
    }
}

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATES[tier].get();
}
```

### Step 3: Update References to BlockPattern Fields

If your code accesses `structurePattern` fields directly, update to use the new accessors:

| Old (deprecated) | New |
|-------------------|-----|
| `structurePattern.cache` | `multiblockState.cache` |
| `structurePattern.formedRepetitionCount` | `multiblockState.formedRepetitionCount` |
| `structurePattern.aisleRepetitions` | `patternTemplate.getAisleRepetitions()` |
| `structurePattern.structureDir` | `patternTemplate.getStructureDir()` |
| `structurePattern.getError()` | `multiblockState.getError()` |
| `structurePattern.clearCache()` | `multiblockState.clearCache()` |
| `structurePattern.checkPatternFastAt(...)` | `multiblockState.checkPatternFastAt(...)` |
| `structurePattern.autoBuild(...)` | `multiblockState.autoBuild(...)` |

The fields `patternTemplate` and `multiblockState` are `protected` on `MultiblockControllerBase`.
External code can use `getPatternTemplate()` and `getMultiblockState()`.

### Step 4: Update DistillationTowerLogicHandler (if used)

If your addon subclasses `DistillationTowerLogicHandler`:

**Before:**
```java
handler.determineLayerCount(this.structurePattern);
```

**After:**
```java
handler.determineLayerCount(this.multiblockState);
```

---

## Optional: DeclarativePatternBuilder

For new multiblocks, consider using `DeclarativePatternBuilder` instead of raw
`FactoryBlockPattern`. It provides:

- **Automatic minimum casing counts** — no manual `setMinGlobalLimited()` needed
- **Declarative hatch placement** — `withHatches()` / `withOptionalHatches()`
- **Tiered casing support** — `tieredCasing()` with `ICasingGroup` + `StructureChannel`
- **Auto-generated tooltip descriptions** — structure info appears in item tooltips

### Example: Before vs After

**Before (FactoryBlockPattern):**
```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("gregtech:my_machine", () ->
                FactoryBlockPattern.start()
                        .aisle("XXX", "CCC", "CCC", "XXX")
                        .aisle("XXX", "C#C", "C#C", "XMX")
                        .aisle("XSX", "CCC", "CCC", "XXX")
                        .where('S', selfPredicate())
                        .where('#', air())
                        .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                        .where('X', states(getCasingState()).setMinGlobalLimited(9)
                                .or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(MultiblockAbility.MAINTENANCE_HATCH).setExactLimit(1))
                                .or(abilities(MultiblockAbility.IMPORT_ITEMS).setMaxGlobalLimited(4))
                                .or(abilities(MultiblockAbility.EXPORT_ITEMS).setMaxGlobalLimited(4)))
                        .where('C', heatingCoils())
                        .buildTemplate()
        );
```

**After (DeclarativePatternBuilder):**
```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("gregtech:electric_blast_furnace", () ->
                DeclarativePatternBuilder.start()
                        .aisle("XXX", "CCC", "CCC", "XXX")
                        .aisle("XXX", "C#C", "C#C", "XMX")
                        .aisle("XSX", "CCC", "CCC", "XXX")
                        .where('S', selfPredicate(gregtechId("electric_blast_furnace")))
                        .where('#', air())
                        .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                        .casing('X', CasingDefinition.simple(getCasingState(), "casing.key"))
                            .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
                            .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                            .withOptionalHatches(MultiblockAbility.IMPORT_ITEMS, 4)
                            .withOptionalHatches(MultiblockAbility.EXPORT_ITEMS, 4)
                        .tieredCasing('C', GTCasingGroups.heatingCoils())
                            .withChannel(GTStructureChannels.HEATING_COIL)
                        .buildTemplate()
        );
```

### Creating Custom Casing Groups (for addons)

```java
// Define tiered casings
ICasingGroup myCoils = CasingDefinition.tieredGroup(
        "myaddon_coils",                              // unique group ID
        "myaddon.casing_group.coils",                 // translation key
        true,                                          // requires uniform tier
        CasingDefinition.tiered(coilState1, "myaddon.coil.tier1", 1),
        CasingDefinition.tiered(coilState2, "myaddon.coil.tier2", 2),
        CasingDefinition.tiered(coilState3, "myaddon.coil.tier3", 3)
);

// Use in builder
.tieredCasing('C', myCoils)
```

### Creating Custom Structure Channels (for addons)

```java
public enum MyChannels implements StructureChannel {
    MY_COIL("myaddon_coil", "myaddon.structure_channel.coil");

    private final String name;
    private final String tooltip;

    MyChannels(String name, String tooltip) {
        this.name = name;
        this.tooltip = tooltip;
    }

    @Override
    public @NotNull String getName() { return name; }

    @Override
    public @NotNull String getDefaultTooltip() { return tooltip; }
}

// Use in builder
.tieredCasing('C', myCoils).withChannel(MyChannels.MY_COIL)

// Read in formStructure()
@Override
protected void formStructure(PatternMatchContext context) {
    super.formStructure(context);
    int coilTier = MyChannels.MY_COIL.getValue(context);
}
```

---

## Backward Compatibility

The deprecated APIs are fully functional throughout v1.9.x and v2.x until v2.10.
You do NOT need to migrate immediately. The old code will continue to work:

- `createStructurePattern()` overrides are called by the default `createStructureTemplate()`
  which extracts the template via `.getTemplate()`
- `structurePattern` is automatically populated with a compat wrapper in
  `reinitializeStructurePattern()`
- All `BlockPattern` methods delegate to the underlying `BlockPatternTemplate` + `MultiblockState`

However, migrating will:
1. Suppress deprecation warnings in your build
2. Reduce memory usage (shared templates)
3. Enable access to new features (channels, declarative builder, auto-generated tooltips)
4. Prepare your addon for v2.10 where `BlockPattern` will be removed

---

## Quick Reference: Import Changes

```java
// Remove (deprecated)
import gregtech.api.pattern.BlockPattern;

// Add (new)
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;  // optional
import gregtech.api.pattern.casing.CasingDefinition;           // optional
import gregtech.api.pattern.casing.ICasingGroup;               // optional
import gregtech.api.pattern.casing.StructureChannel;            // optional
```

---

## Migration Checklist

- [ ] Replace `createStructurePattern()` with `createStructureTemplate()`
- [ ] Change `.build()` to `.buildTemplate()` in pattern builders
- [ ] Add static template caching (`SoftTemplate` via `TemplatePool`)
- [ ] Update `BlockPattern` field accesses to use `patternTemplate` / `multiblockState`
- [ ] Update `DistillationTowerLogicHandler` calls if applicable
- [ ] Remove `import gregtech.api.pattern.BlockPattern` when no longer needed
- [ ] (Optional) Migrate to `DeclarativePatternBuilder` for new multiblocks
- [ ] (Optional) Define custom `ICasingGroup` and `StructureChannel` for tiered structures

---
---

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
    protected BlockPatternTemplate createStructureTemplate() {
        return MY_TEMPLATES.get(getVariant()).get();
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
