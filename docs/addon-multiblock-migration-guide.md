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

## Optional: Request-Based Build Results

Legacy auto-build methods remain available, but request-based build APIs now return
`StructureBuildResult`. Addons that drive builders or custom tools can use this result
to show stable survival-build feedback:

- `getPlacementBudget()` counts cells that still needed a placement decision in this call.
- `getRemainingPlacementBudget()` is the unresolved budget after placed cells are counted.
- `hasPartialPlacement()` means this call placed at least one budgeted cell and left more work.
- `requiresResume()` means running the same build request again can continue from already-placed cells.
- `getRequiredItems()`, `getConsumedItems()`, and `getMissingItems()` summarize survival item accounting.

Already-valid cells are reported as existing cells and do not consume placement budget.
If a survival build selects a candidate but item consumption fails after placement, the just-placed
block is rolled back and the result records missing/unavailable instead of consumed.

```java
StructureBuildResult result = controller.getOrCreateStructureRuntime().buildSingle(
        StructureOperationRequest.build(
                player,
                controller,
                StructureOrientation.fromController(controller),
                channelValues,
                skipHatches,
                triggerStack));

if (result.requiresResume()) {
    // Show result.getMissingItems() or ask the player to run the builder again.
}
```

## Optional: Typed Hint, Preview, and Iterate Results

The runtime also exposes typed results for non-build operations:

- `StructureHintResult` now includes rendered/skipped/failed hint-render counts.
- `StructurePreviewResult` wraps single-template and multi-piece previews behind one outcome type.
- `StructureIterateResult` wraps read-only structure iteration; single-template iteration exposes
  block info, while multi-piece iteration exposes the formed position set.

For custom structure elements, prefer overriding `spawnHintWithResult(...)` when the element can
decide that a trigger or context path did not actually render anything:

```java
@NotNull
@Override
public StructureHintRenderResult spawnHintWithResult(
        World world, BlockPos pos, @NotNull ItemStack trigger) {
    if (!canHandle(trigger)) {
        return StructureHintRenderResult.skipped(StructureHintRenderResult.Source.TRIGGER);
    }
    renderHint(world, pos);
    return StructureHintRenderResult.rendered(StructureHintRenderResult.Source.TRIGGER);
}
```

The old `spawnHint(...)` methods are still supported. Their default typed result is `RENDERED`, so
existing elements do not need an immediate migration.

### Orientation-Native Requests

New runtime/evaluator APIs take `StructureOrientation` instead of separate
`front/up/flipped` parameters:

```java
StructureOrientation orientation = StructureOrientation.fromController(controller);

StructureCheckResult check = controller.getOrCreateStructureRuntime().check(
        StructureOperationRequest.check(
                controller.getWorld(),
                controller.getPos(),
                orientation,
                true,
                null,
                controller));
```

Legacy `BlockPattern` / `BlockPatternTemplate` overloads that accept
`front/up/flipped` remain as compatibility facades. They convert to
`StructureOrientation` at the API edge before entering the runtime path. New
addon code should construct `StructureOrientation` once and reuse it for check,
build, hint, preview placement, iteration, AABB, and piece-center calculations.

---

## Optional: Lifecycle State and Scheduling Policy

Formation lifecycle state is now owned by the controller's `StructureRuntime`.
On the server side, `isStructureFormed()` reads the runtime lifecycle snapshot
when one is available. The controller fields for formed state, part lists, and
ability instances remain for networking, legacy accessors, and old callbacks,
but they are projections of the committed runtime state rather than independent
state owners.

Addon controllers should not directly mutate `structureFormed`,
`multiblockParts`, or `multiblockAbilities`. Let the normal structure check and
server-thread committer publish state, then read parts through
`getMultiblockParts()`, abilities through `getAbilities(...)`, and typed formed
data from `formStructure(FormedStructureView)`.

`formStructure(PatternMatchContext)` is still supported through the compatibility
bridge. New controllers can override `formStructure(FormedStructureView)` when
they need typed operation state, formed metadata, or channel values without
depending on mutable legacy context.

Controllers can also override `getStructureSchedulerPolicy()` to choose how the
structure is checked. The default policy preserves previous behavior: first-tick
live check, event-driven checks for formed structures when enabled, async
precheck for unformed structures when supported, and polling fallback. Custom
policies can disable event-driven checks for polling-only machines, opt out of
async precheck, or make a controller event-driven-only without changing world
dirty-index storage.

### Declaring Incremental Dependencies

Incremental rechecks are only used when the compiled structure definition is
eligible. New direct elements should declare the non-local inputs that can change
their match or contribution result:

```java
@NotNull
@Override
public Set<StructureDependency> getDependencies() {
    return Collections.singleton(
            StructureDependency.piece("coil", PieceDependencyAspect.CONTRIBUTION_VALUE));
}
```

Use a piece dependency when the element reads a previous piece's typed
contribution, repeat count, center, or activation result. Use a standard
external dependency when the element reads controller state that is not tied to a
world block change:

```java
@NotNull
@Override
public Set<StructureDependency> getDependencies() {
    return Collections.singleton(StructureExternalDependencies.controllerMode());
}
```

The built-in external dependency helpers are:

- `StructureExternalDependencies.controllerMode()`
- `StructureExternalDependencies.channelValues()`
- `StructureExternalDependencies.configuration()`
- `StructureExternalDependencies.upgrades()`

Elements that depend on callbacks, lazy suppliers, or old predicate-shaped
side effects should remain opaque through `getIncrementalSupport()`. An opaque
element keeps the structure on the conservative active/full fallback path rather
than allowing incorrect reuse of a clean piece.

New direct elements should be explicit even when the dependency set is empty:
declare `StructureIncrementalSupport`, return `Collections.emptySet()` from
`getDependencies()`, and expose diagnostic/build candidates through
`getPreview()`. `PatternMatchContext` should only appear in compatibility
methods such as old callbacks, old placement/tooling APIs, or an optional
`toPredicate()` view.

Composite direct elements must aggregate child dependencies and child
`StructureIncrementalSupport`. For example, a chain/alternative element that
contains one opaque child must report opaque support for the whole composite,
and must expose every child `getDependencies()` entry.

### Notifying External State Changes

Controllers that expose structure-affecting modes, config switches, channels, or
upgrades should connect those values to the runtime snapshot hooks:

```java
@Override
protected Object getStructureControllerModeValue() {
    return mode;
}

public void setMode(MyMode mode) {
    if (this.mode != mode) {
        this.mode = mode;
        notifyStructureControllerModeChanged();
    }
}
```

Use the matching notify method for the dependency kind:

- `notifyStructureControllerModeChanged()`
- `notifyStructureChannelsChanged()`
- `notifyStructureConfigChanged()`
- `notifyStructureUpgradesChanged()`

The controller base already snapshots common scheduling configuration and the
core working-enabled mode. Core controllers also snapshot common config such as
voiding mode, distinct/batch/recipe-lock/energy-warning toggles, generator
overflow mode, advanced thread count, MultiMap recipe-map selection, Large
Boiler throttle/type, and Godforge upgrade/ring/renderer state.
Addon-specific modes and upgrade/config state should override the corresponding
value hook in the owning controller or owning abstract machine family and call
the notify method when the value changes. Do not move machine-private fields
into a shared base snapshot unless every subclass genuinely owns that state.
The scheduler also compares snapshots before consuming an event-driven dirty
lease, but explicit notification wakes the scheduler immediately through
`enqueueDirtyRoots(...)`.

---

## Optional: Structure Failure Diagnostics

Multiblock controllers now keep the latest structured formation failure on their
`StructureRuntime`. Addons that need developer-facing diagnostics can read it from
`controller.getStructureRuntime().getLastFailure()` after ensuring the runtime exists.
Player-facing missing ability summaries are still available through
`controller.getMissingStructureAbilities()`.

`StructureFailureTrace` reports the failure kind, operation path, orientation, piece/cell,
world position, expected/actual values, missing abilities, operation-owned ability counts,
progress depth, and whether the failed orientation was flipped. Common failures such as
missing abilities, count limits, unsupported capabilities, assembly rejection, and commit
rejection are represented in the same trace object instead of only as a generic mismatch or
legacy pattern error.

For in-game debugging, use the development command on the controller block:

```text
/gt structure_trace <x> <y> <z>
```

The command prints the controller runtime shape, formed state, and the current last failure.
This is usually the quickest way to see which piece/cell/world position failed and what the
checker expected to find there.

When writing new direct elements, prefer recording ability and count information through the
operation collector/state. That keeps diagnostics consistent with the checker and avoids
re-scanning parts from controller or legacy state.

---

## Migrating to StructureDefinition and Direct Elements

`FactoryBlockPattern` and `TraceabilityPredicate` remain available as compatibility
facades, but new addon code should declare structures through `StructureDefinition`
or `DeclarativePatternBuilder` and implement custom cells as direct
`IStructureElement` instances.

Use these replacements when moving custom structures:

| Old API | New API |
|---------|---------|
| `FactoryBlockPattern.start().aisle(...).where(...).buildTemplate()` | `StructureDefinition.builder()` or `DeclarativePatternBuilder` |
| `TraceabilityPredicate` custom matcher | `IStructureElement.check(StructureEvaluationContext)` |
| `TraceabilityPredicate.addTooltips(...)` | `IStructureElement.addPreviewTooltip(...)` |
| predicate candidates / `setPreviewCount(...)` / channel metadata | `IStructureElement.getPreview()` returning `StructureElementPreview` |
| legacy hint-only rendering | `spawnHint(World, BlockPos, ItemStack)` or `spawnHint(StructureEvaluationContext)` |

Custom direct elements no longer need to implement `toPredicate()` for runtime
matching, preview, or build candidate selection. If old tooling still needs a
predicate-shaped view, `toPredicate()` can return one, but it is now a
compatibility view rather than the execution model.

Internally, `PieceTemplate` stores compiled elements as the canonical cell data.
Legacy predicate arrays exposed by `BlockPatternTemplate.getBlockMatches()` are
projected through `PieceTemplateLegacyView`. Old addons can keep calling
`getBlockMatches()` during the migration window, while new addons should put
preview candidates, channel metadata, and tooltip text on direct element APIs
(`getPreview()` and `addPreviewTooltip(...)`) instead of rebuilding that
information through `TraceabilityPredicate`.

JEI now follows the same rule. The multiblock page resolves candidates and
tooltip text from `StructureElementPreview` / `addPreviewTooltip(...)` first.
Its cached `MBPattern` exposes typed preview accessors and only builds legacy
predicate-map entries for preview positions not already covered by direct
preview entries. Legacy predicates adapted through
`StructureElementPreview.fromPredicate(...)` carry their per-candidate tooltip
text on the preview candidate group itself, so JEI does not need to materialize
a predicate-shaped template view for normal tooltip/candidate display. This
means new direct elements should treat `toPredicate()` as optional compatibility
only, not as the path that makes JEI cycling or tooltips work.

Channel discovery and channel ranges are also typed-first. Put selector metadata
on `StructureElementPreview.CandidateGroup.channel(...)`; JEI/projector channel
UI will use the preview group candidate count for `[0, maxCandidate]` ranges
before falling back to an individual element's optional legacy predicate view.
Direct elements with preview channel metadata are not asked for `toPredicate()`
just to populate channel UI.

Projector and structure-error diagnostics also prefer direct preview metadata.
When a failed cell has a `StructureElementPreviewEntry`, `PatternError#getCandidates()`
uses its `StructureElementPreview` candidates before falling back to legacy
predicate candidates on old cells that have no typed preview entry. New direct
elements should therefore expose diagnostic candidates through `getPreview()`
even if they do not provide `toPredicate()`.

```java
private static final class MyTieredElement implements IStructureElement<Object> {
    private final BlockInfo[] candidates;
    private final StructureElementPreview preview;

    private MyTieredElement(BlockInfo... candidates) {
        this.candidates = candidates;
        this.preview = StructureElementPreview.builder()
                .common(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                        .channel("my_tier")
                        .build())
                .build();
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        for (BlockInfo candidate : candidates) {
            if (context.getBlockState() == candidate.getBlockState()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return candidates;
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public void addPreviewTooltip(List<String> tooltip) {
        tooltip.add("myaddon.multiblock.preview.my_tier");
    }
}
```

For count-limited elements, expose the same limits through both the direct
runtime hooks and the preview metadata:

```java
@Override
public int getMinGlobalCount() {
    return 4;
}

@Override
public StructureElementPreview getPreview() {
    return StructureElementPreview.builder()
            .limited(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                    .global(4, -1)
                    .previewCount(4)
                    .build())
            .build();
}
```

Legacy declarations are adapted into the same metadata shape internally, so old
addons keep working. The important migration rule is one-way compatibility:
legacy declarations may enter V3 through adapters, but new direct elements
should not depend on `TraceabilityPredicate` for matching, preview, build, or
hint behavior.

`formStructure(PatternMatchContext)` also remains supported. New controllers may
override `formStructure(FormedStructureView)` to read typed operation state and
channel values directly; the default typed callback still projects a legacy
`PatternMatchContext` for existing overrides. Internally, no-session fixed
template checks now execute through a `StructureMatchSession` and only produce
that legacy context at the compatibility boundary.

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

### `buildTemplate()` vs `buildStructureDefinition()`

`DeclarativePatternBuilder` exposes two compile methods that return different views of
the same underlying compiled result:

| Method | Return Type | Use when |
|--------|-------------|----------|
| `buildStructureDefinition()` | `StructureDefinition` | Your machine uses named pieces, conditional pieces, or multi-piece composition |
| `buildTemplate()` | `BlockPatternTemplate` | Your machine uses a single structure piece (the common case) |

Both methods share the same internal compilation path; `buildTemplate()` is a convenience
view that extracts the primary piece's template from the compiled `StructureDefinition`.
Multi-piece machines that call `buildTemplate()` will receive an `IllegalStateException`
— use `buildStructureDefinition()` for those.

```java
// Single-piece (most machines) — use buildTemplate()
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("gregtech:my_machine", () ->
                DeclarativePatternBuilder.start()
                        .aisle("XXX", "XSX", "XXX")
                        .where('S', selfPredicate())
                        .where('X', casing(...))
                        .buildTemplate()  // returns BlockPatternTemplate
        );

// Multi-piece (DistillationTower, etc.) — use buildStructureDefinition()
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
        .register("gregtech:distillation_tower", () ->
                DeclarativePatternBuilder.start()
                        .aisle("XXX", "XSX", "XXX")
                        .piece("main", "XXX", "XSX", "XXX")
                        .repeatablePiece("middle", "XYX", "YYY", "XYX", 1, 11)
                        .end()
                        .buildStructureDefinition()  // returns StructureDefinition
        );
```

> **Migration note:** Prior to v1.9, `buildTemplate()` internally used the L1
> `FactoryBlockPattern` path, while `buildStructureDefinition()` used the L2
> `StructureDefinition` path. As of v1.9, both share the L2 path. The legacy `build()`
> method (returning `BlockPattern`) has been removed.
>
> **Behavior change:** `buildTemplate()` now builds **all** pieces defined on the
> builder (consistent with `buildStructureDefinition()`), instead of only the most
> recently added piece. This change is a semantic fix — most callers never noticed the
> old "currentPiece only" behavior because they added a single piece.

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
import gregtech.api.pattern.PieceDependencyAspect;              // optional incremental dependencies
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.StructureDependency;                // optional incremental dependencies
import gregtech.api.pattern.StructureExternalDependencies;      // optional external state dependencies
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
- [ ] (Optional) Add `IStructureElement.getDependencies()` for direct elements that read prior piece or controller state
- [ ] (Optional) Override structure external state snapshot hooks and call the matching `notifyStructure...Changed()` method

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
