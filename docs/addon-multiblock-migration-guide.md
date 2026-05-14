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
| Template caching | Manual or none | `LazyTemplate`, `SoftTemplate`, `TemplatePool` |
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

Templates are immutable and can be safely shared. Use `LazyTemplate` or `SoftTemplate` to avoid
recreating the template every time `createStructureTemplate()` is called.

**Option A: `LazyTemplate` — for core machines (never evicted)**
```java
private static final LazyTemplate TEMPLATE = LazyTemplate.of(() ->
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

**Option B: `SoftTemplate` + `TemplatePool` — for many machine types (GC-friendly)**
```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("myaddon:my_machine", () ->
                FactoryBlockPattern.start()
                        .aisle(...)
                        .where(...)
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

**Option C: Multi-variant machines (e.g. tiered machines)**
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
private static final LazyTemplate TEMPLATE = LazyTemplate.of(() ->
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
import gregtech.api.pattern.LazyTemplate;          // or SoftTemplate
import gregtech.api.pattern.TemplatePool;           // if using SoftTemplate
import gregtech.api.pattern.casing.DeclarativePatternBuilder;  // optional
import gregtech.api.pattern.casing.CasingDefinition;           // optional
import gregtech.api.pattern.casing.ICasingGroup;               // optional
import gregtech.api.pattern.casing.StructureChannel;            // optional
```

---

## Migration Checklist

- [ ] Replace `createStructurePattern()` with `createStructureTemplate()`
- [ ] Change `.build()` to `.buildTemplate()` in pattern builders
- [ ] Add static template caching (`LazyTemplate` or `SoftTemplate`)
- [ ] Update `BlockPattern` field accesses to use `patternTemplate` / `multiblockState`
- [ ] Update `DistillationTowerLogicHandler` calls if applicable
- [ ] Remove `import gregtech.api.pattern.BlockPattern` when no longer needed
- [ ] (Optional) Migrate to `DeclarativePatternBuilder` for new multiblocks
- [ ] (Optional) Define custom `ICasingGroup` and `StructureChannel` for tiered structures
